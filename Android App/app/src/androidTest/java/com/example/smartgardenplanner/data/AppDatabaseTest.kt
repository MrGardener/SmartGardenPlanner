package com.example.smartgardenplanner.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.smartgardenplanner.core.PlantedNodeEntity
import com.example.smartgardenplanner.core.PlotEntity
import com.example.smartgardenplanner.core.RealSecurityKeyManager
import com.example.smartgardenplanner.core.SeedEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FIXED] This file previously contained a verbatim copy of AppDatabase.kt itself — same
 * package, same class name, zero @Test functions. That would (at best) contribute nothing,
 * and very likely cause a duplicate-class build failure once the androidTest classpath merges
 * with the main classpath. This is now a real instrumented test exercising the actual database.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val keyManager = RealSecurityKeyManager().apply { initializeKeyStore() }
        db = AppDatabase.buildInMemoryForTest(context, keyManager)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndRetrievePlot_returnsCommittedRecord() = runBlocking {
        val plotId = db.plotDao().insert(
            PlotEntity(name = "Backyard Bed", lengthM = 3.0f, widthM = 2.0f)
        )

        val plots = db.plotDao().getAllPlots()
        assertEquals(1, plots.size)
        assertEquals("Backyard Bed", plots.first().name)
        assertTrue(plotId > 0)
    }

    @Test
    fun deletingPlot_cascadesToPlantedNodes() = runBlocking {
        db.seedDao().insert(
            SeedEntity(botanicalCode = "TEST-001", commonName = "Test Plant", botanicalFamily = "Testaceae", exclusionRadiusM = 0.5f)
        )
        val plotId = db.plotDao().insert(PlotEntity(name = "Cascade Test", lengthM = 1.0f, widthM = 1.0f))
        db.plantedNodeDao().insert(
            PlantedNodeEntity(plotId = plotId, seedCode = "TEST-001", coordinateXM = 0.5f, coordinateYM = 0.5f)
        )

        assertEquals(1, db.plantedNodeDao().getByPlotId(plotId).size)

        val plot = db.plotDao().getById(plotId)
        assertNotNull(plot)
        db.plotDao().delete(plot!!)

        assertEquals(0, db.plantedNodeDao().getByPlotId(plotId).size)
    }

    @Test
    fun seedDictionary_seedsInsertSuccessfully() = runBlocking {
        db.seedDao().insert(
            SeedEntity(botanicalCode = "SOL-LYC", commonName = "Tomato", botanicalFamily = "Solanaceae", exclusionRadiusM = 1.25f)
        )
        val seed = db.seedDao().getByCode("SOL-LYC")
        assertNotNull(seed)
        assertEquals("Tomato", seed?.commonName)
    }

    /** LLR-DB-010/040: the opened database is schema 12 with every table and foreign keys on. */
    @Test
    fun schema12_hasEveryTableAndForeignKeysOn() {
        val sql = db.openHelper.writableDatabase
        sql.query("PRAGMA user_version").use { c -> c.moveToFirst(); assertEquals(12, c.getInt(0)) }
        val tables = mutableSetOf<String>()
        sql.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c -> while (c.moveToNext()) tables += c.getString(0) }
        for (t in listOf("plots", "planted_nodes", "seeds", "climate_zones", "path_zones", "site_features", "care_log",
                "nutrition_facts", "planting_history", "app_configurations")) assertTrue("table $t", t in tables)
        sql.query("PRAGMA foreign_keys").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
    }
}
