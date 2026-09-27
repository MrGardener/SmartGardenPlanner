package com.example.smartgardenplanner.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.smartgardenplanner.core.NutritionEntity

/** Nutrition values refreshed from USDA FoodData Central (FR-020). */
@Dao
interface NutritionDao {
    @Upsert
    suspend fun upsert(entry: NutritionEntity)

    @Query("SELECT * FROM nutrition_facts")
    suspend fun getAll(): List<NutritionEntity>
}
