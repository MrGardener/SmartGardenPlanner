package com.example.smartgardenplanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.smartgardenplanner.core.PlotEntity

@Dao
interface PlotDao {
    @Insert
    suspend fun insert(plot: PlotEntity)

    @Query("SELECT * FROM plots")
    suspend fun getAllPlots(): List<PlotEntity>
}