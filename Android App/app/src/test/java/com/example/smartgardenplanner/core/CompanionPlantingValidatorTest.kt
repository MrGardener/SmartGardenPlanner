package com.example.smartgardenplanner.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionPlantingValidatorTest {

    private val validator = CompanionPlantingValidator()

    private val tomato = SeedEntity(
        botanicalCode = "SOL-LYC", commonName = "Tomato", botanicalFamily = "Solanaceae",
        exclusionRadiusM = 1.25f, antagonistCodes = "BRA" // [UPDATED] prefix, not full code — see below
    )
    private val cabbage = SeedEntity(
        botanicalCode = "BRA-OLE", commonName = "Cabbage", botanicalFamily = "Brassicaceae",
        exclusionRadiusM = 1.0f, antagonistCodes = "SOL"
    )
    private val basil = SeedEntity(
        botanicalCode = "PL-BAS", commonName = "Basil", botanicalFamily = "Lamiaceae",
        exclusionRadiusM = 0.4f
    )
    // [NEW] A second Tomato cultivar, to verify antagonist matching works across cultivars of the
    // same species (the actual real-world use case for the tiered catalog's cultivar-level codes).
    private val cherryTomato = SeedEntity(
        botanicalCode = "SOL-002", commonName = "Cherry Tomato", botanicalFamily = "Solanaceae",
        exclusionRadiusM = 0.9f, antagonistCodes = "BRA"
    )

    private val seedLookup: (String) -> SeedEntity? = { code ->
        listOf(tomato, cherryTomato, cabbage, basil).find { it.botanicalCode == code }
    }

    @Test
    fun validatePlacement_flagsSpacingViolationWhenTooClose() {
        val existing = PlantedNodeEntity(id = 1, plotId = 1, seedCode = "PL-BAS", coordinateXM = 0f, coordinateYM = 0f)
        val candidate = PlantedNodeEntity(id = 2, plotId = 1, seedCode = "PL-BAS", coordinateXM = 0.1f, coordinateYM = 0f)

        val result = validator.validatePlacement(candidate, basil, listOf(existing), seedLookup)

        assertFalse(result.isValid)
        assertTrue(result.spacingViolations.contains(1L))
    }

    @Test
    fun validatePlacement_passesWhenFarEnoughApart() {
        val existing = PlantedNodeEntity(id = 1, plotId = 1, seedCode = "PL-BAS", coordinateXM = 0f, coordinateYM = 0f)
        val candidate = PlantedNodeEntity(id = 2, plotId = 1, seedCode = "PL-BAS", coordinateXM = 5f, coordinateYM = 5f)

        val result = validator.validatePlacement(candidate, basil, listOf(existing), seedLookup)

        assertTrue(result.isValid)
        assertTrue(result.spacingViolations.isEmpty())
        assertTrue(result.antagonistViolations.isEmpty())
    }

    @Test
    fun validatePlacement_flagsAntagonistConflictNearby() {
        val existing = PlantedNodeEntity(id = 1, plotId = 1, seedCode = "BRA-OLE", coordinateXM = 0f, coordinateYM = 0f)
        // Outside the direct spacing circle but within the wider antagonist-conflict radius.
        val candidate = PlantedNodeEntity(id = 2, plotId = 1, seedCode = "SOL-LYC", coordinateXM = 2.0f, coordinateYM = 0f)

        val result = validator.validatePlacement(candidate, tomato, listOf(existing), seedLookup)

        assertFalse(result.isValid)
        assertTrue(result.antagonistViolations.contains(1L))
    }

    @Test
    fun validatePlacement_antagonistMatchingIsSpeciesPrefixBased_notExactCode() {
        // [NEW] Real scenario: the tiered catalog has hundreds of distinct cultivar-level codes
        // (SOL-001, SOL-002, ...) sharing a small set of species prefixes. A Cherry Tomato
        // cultivar (SOL-002) must still be flagged as a Cabbage antagonist even though its exact
        // code was never listed anywhere — only the "SOL"/"BRA" prefixes are.
        val existing = PlantedNodeEntity(id = 1, plotId = 1, seedCode = "BRA-OLE", coordinateXM = 0f, coordinateYM = 0f)
        val candidate = PlantedNodeEntity(id = 2, plotId = 1, seedCode = "SOL-002", coordinateXM = 2.0f, coordinateYM = 0f)

        val result = validator.validatePlacement(candidate, cherryTomato, listOf(existing), seedLookup)

        assertFalse(result.isValid)
        assertTrue(result.antagonistViolations.contains(1L))
    }

    @Test
    fun validatePlacement_ignoresSelf() {
        val candidate = PlantedNodeEntity(id = 1, plotId = 1, seedCode = "PL-BAS", coordinateXM = 0f, coordinateYM = 0f)

        val result = validator.validatePlacement(candidate, basil, listOf(candidate), seedLookup)

        assertTrue(result.isValid)
    }
}
