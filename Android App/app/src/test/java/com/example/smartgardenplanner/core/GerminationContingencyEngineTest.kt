package com.example.smartgardenplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class GerminationContingencyEngineTest {

    private val engine = GerminationContingencyEngine()

    private val tomato = SeedEntity(
        botanicalCode = "SOL-LYC", commonName = "Tomato", botanicalFamily = "Solanaceae",
        exclusionRadiusM = 1.25f, germinationDays = 7,
        fastTrackAlternateCode = "SOL-LYC-CHERRY", nurseryTransplantSuitable = true,
        catchCropAlternateCode = "PL-BAS"
    )
    private val cherryTomato = SeedEntity(
        botanicalCode = "SOL-LYC-CHERRY", commonName = "Cherry Tomato", botanicalFamily = "Solanaceae",
        exclusionRadiusM = 0.9f, germinationDays = 6
    )
    private val basil = SeedEntity(
        botanicalCode = "PL-BAS", commonName = "Basil", botanicalFamily = "Lamiaceae",
        exclusionRadiusM = 0.4f, germinationDays = 6
    )
    private val dictionary = listOf(tomato, cherryTomato, basil)

    @Test
    fun isGerminationOverdue_trueWhenElapsedExceedsWindow() {
        val plantedNineDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(9)
        val node = PlantedNodeEntity(plotId = 1, seedCode = "SOL-LYC", coordinateXM = 0f, coordinateYM = 0f, datePlantedEpochMillis = plantedNineDaysAgo)

        assertTrue(engine.isGerminationOverdue(node, tomato))
    }

    @Test
    fun isGerminationOverdue_falseWithinWindow() {
        val plantedToday = System.currentTimeMillis()
        val node = PlantedNodeEntity(plotId = 1, seedCode = "SOL-LYC", coordinateXM = 0f, coordinateYM = 0f, datePlantedEpochMillis = plantedToday)

        assertFalse(engine.isGerminationOverdue(node, tomato))
    }

    @Test
    fun isGerminationOverdue_falseWhenAlreadyResolved() {
        val plantedNineDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(9)
        val node = PlantedNodeEntity(
            plotId = 1, seedCode = "SOL-LYC", coordinateXM = 0f, coordinateYM = 0f,
            datePlantedEpochMillis = plantedNineDaysAgo, germinationFlagResolved = true
        )

        assertFalse(engine.isGerminationOverdue(node, tomato))
    }

    @Test
    fun buildContingencyOptions_returnsAllThreePathsWhenDataAvailable() {
        val options = engine.buildContingencyOptions(tomato, dictionary)

        assertEquals(3, options.size)
        assertTrue(options.any { it is GerminationContingencyEngine.ContingencyOption.FastTrackVariety })
        assertTrue(options.any { it is GerminationContingencyEngine.ContingencyOption.NurseryTransplant })
        assertTrue(options.any { it is GerminationContingencyEngine.ContingencyOption.CatchCrop })
    }

    @Test
    fun buildContingencyOptions_omitsPathsWithMissingData() {
        val minimalSeed = SeedEntity(
            botanicalCode = "PL-MAR", commonName = "Marigold", botanicalFamily = "Asteraceae",
            exclusionRadiusM = 0.6f, nurseryTransplantSuitable = false
        )

        val options = engine.buildContingencyOptions(minimalSeed, dictionary)

        assertTrue(options.isEmpty())
    }
}
