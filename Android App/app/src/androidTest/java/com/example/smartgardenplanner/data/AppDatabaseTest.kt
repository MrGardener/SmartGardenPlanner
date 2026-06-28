package com.example.smartgardenplanner.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartgardenplanner.security.SecurityKeyManager
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@androidx.room.Database(entities = [AppConfig::class], version = 1, exportSchema = false)
abstract class TestDatabase : RoomDatabase() {
    abstract fun configDao(): AppConfigDao
}

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: TestDatabase
    private lateinit var configDao: AppConfigDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // FIX: Force-loads the native C++ SQLCipher driver binaries into memory
        System.loadLibrary("sqlcipher")

        // Fetch the passphrase bytes
        val passphrase = SecurityKeyManager.getDatabasePassphrase()
        val factory = SupportOpenHelperFactory(passphrase)

        // Build the secured database
        db = Room.inMemoryDatabaseBuilder(context, TestDatabase::class.java)
            .openHelperFactory(factory)
            .build()

        configDao = db.configDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun testSecureDatabaseWriteAndRead() = runBlocking {
        val sampleConfig = AppConfig(
            configKey = "secure_encryption_sync_token",
            configValue = "ACTIVE_STATUS_VERIFIED"
        )

        configDao.saveConfig(sampleConfig)

        val retrievedConfig = configDao.getConfigByKey("secure_encryption_sync_token")

        assertNotNull("Retrieved configuration should not be null", retrievedConfig)
        assertEquals("ACTIVE_STATUS_VERIFIED", retrievedConfig?.configValue)
    }
}