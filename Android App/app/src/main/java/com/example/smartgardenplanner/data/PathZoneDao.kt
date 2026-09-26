package com.example.smartgardenplanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.smartgardenplanner.core.PathZoneEntity

/**
 * [UPDATED] Added update() and deleteAllForPlot() — requested feature "modify a path later when
 * selected" needs write access beyond insert-only, and deleteAllForPlot() is needed for the
 * unified undo/redo snapshot restore (see CanvasSnapshot in MainActivity.kt).
 */
@Dao
interface PathZoneDao {
    @Insert
    suspend fun insert(zone: PathZoneEntity): Long

    @Insert
    suspend fun insertAll(zones: List<PathZoneEntity>): List<Long>

    @Update
    suspend fun update(zone: PathZoneEntity)

    @Delete
    suspend fun delete(zone: PathZoneEntity)

    @Query("SELECT * FROM path_zones WHERE plotId = :plotId")
    suspend fun getByPlotId(plotId: Long): List<PathZoneEntity>

    @Query("DELETE FROM path_zones WHERE plotId = :plotId")
    suspend fun deleteAllForPlot(plotId: Long)
}
