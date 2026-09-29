package com.example.smartgardenplanner.core

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Nutrition values per 100 g of the edible part (FR-020). Starts from the bundled snapshot in
 * [CropReference]; a refresh from USDA FoodData Central (when online features are on) overwrites a row
 * with source = "USDA FDC <id>".
 */
@Entity(tableName = "nutrition_facts")
data class NutritionEntity(
    @PrimaryKey val speciesKey: String,
    val energyKcal: Float,
    val proteinG: Float,
    val carbsG: Float,
    val fiberG: Float,
    val vitaminAUg: Float,
    val vitaminCMg: Float,
    val potassiumMg: Float,
    val ironMg: Float,
    val calciumMg: Float,
    val source: String,
    val updatedEpochMillis: Long
)
