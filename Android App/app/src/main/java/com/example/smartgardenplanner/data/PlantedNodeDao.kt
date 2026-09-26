package com.example.smartgardenplanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.smartgardenplanner.core.PlantedNodeEntity

/**
 * [UPDATED] Added update()/delete()/getByPlotId() — T2-FUN-070 (Update/Delete) and T2-INT-010
 * (recalculate overlays for a specific plot) both need per-plot node queries, which weren't
 * previously exposed.
 * [UPDATED] Added insertAll()/deleteAllForPlot() to support the new auto-populate feature and
 * to give the Canvas screen a real, correct DAO path for undo/redo sync instead of hand-rolled
 * raw SQL (which never persisted datePlantedEpochMillis/germinationFlagResolved on reload).
 */
@Dao
interface PlantedNodeDao {
    @Insert
    suspend fun insert(node: PlantedNodeEntity): Long

    @Insert
    suspend fun insertAll(nodes: List<PlantedNodeEntity>): List<Long>

    @Update
    suspend fun update(node: PlantedNodeEntity)

    @Delete
    suspend fun delete(node: PlantedNodeEntity)

    @Query("SELECT * FROM planted_nodes")
    suspend fun getAllNodes(): List<PlantedNodeEntity>

    @Query("SELECT * FROM planted_nodes WHERE plotId = :plotId")
    suspend fun getByPlotId(plotId: Long): List<PlantedNodeEntity>

    @Query("DELETE FROM planted_nodes WHERE plotId = :plotId")
    suspend fun deleteAllForPlot(plotId: Long)

    @Query("UPDATE planted_nodes SET germinationFlagResolved = 1 WHERE id = :nodeId")
    suspend fun markGerminationResolved(nodeId: Long)
}
