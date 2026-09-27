package com.example.smartgardenplanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.smartgardenplanner.core.PlantingHistoryEntity

/** Plants of finished seasons, per plot (FR-033). */
@Dao
interface PlantingHistoryDao {
    @Query("SELECT * FROM planting_history WHERE plotId = :plotId ORDER BY seasonYear DESC")
    suspend fun getByPlotId(plotId: Long): List<PlantingHistoryEntity>

    /** Variety codes of every past planting on every plot, for "what you usually plant" (FR-034). */
    @Query("SELECT seedCode FROM planting_history")
    suspend fun allCodes(): List<String>

    @Insert
    suspend fun insertAll(entries: List<PlantingHistoryEntity>): List<Long>

    @Query("DELETE FROM planting_history WHERE plotId = :plotId")
    suspend fun deleteAllForPlot(plotId: Long)
}
