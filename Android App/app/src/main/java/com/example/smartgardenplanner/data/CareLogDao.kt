package com.example.smartgardenplanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.smartgardenplanner.core.CareLogEntity

/** Care tasks marked as done (FR-019). */
@Dao
interface CareLogDao {
    @Insert
    suspend fun insert(entry: CareLogEntity): Long

    @Query("SELECT * FROM care_log WHERE plotId = :plotId ORDER BY doneAtEpochMillis DESC LIMIT 200")
    suspend fun getByPlotId(plotId: Long): List<CareLogEntity>
}
