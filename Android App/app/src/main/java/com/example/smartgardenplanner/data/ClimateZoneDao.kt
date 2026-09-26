package com.example.smartgardenplanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.smartgardenplanner.core.ClimateZoneEntity

@Dao
interface ClimateZoneDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(zones: List<ClimateZoneEntity>)

    @Query("SELECT * FROM climate_zones WHERE zipCode = :zip LIMIT 1")
    suspend fun getByZip(zip: String): ClimateZoneEntity?

    @Query("SELECT COUNT(*) FROM climate_zones")
    suspend fun count(): Int
}
