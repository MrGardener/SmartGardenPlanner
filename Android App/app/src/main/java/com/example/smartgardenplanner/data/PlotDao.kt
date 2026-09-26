package com.example.smartgardenplanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.smartgardenplanner.core.PlotEntity

/**
 * [UPDATED] Added update()/delete()/getById() — T2-FUN-070 requires Update and Delete/Archive
 * for previously recorded plots, which the original DAO (insert + getAllPlots only) didn't support.
 */
@Dao
interface PlotDao {
    @Insert
    suspend fun insert(plot: PlotEntity): Long

    @Update
    suspend fun update(plot: PlotEntity)

    @Delete
    suspend fun delete(plot: PlotEntity)

    @Query("SELECT * FROM plots")
    suspend fun getAllPlots(): List<PlotEntity>

    @Query("SELECT * FROM plots WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PlotEntity?
}
