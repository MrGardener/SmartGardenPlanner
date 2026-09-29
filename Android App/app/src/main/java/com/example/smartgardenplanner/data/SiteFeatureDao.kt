package com.example.smartgardenplanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.smartgardenplanner.core.SiteFeatureEntity

/** Sun/shade, flood and slope areas and shade-casting barriers on a plot (FR-003 to FR-006). */
@Dao
interface SiteFeatureDao {
    @Insert
    suspend fun insert(feature: SiteFeatureEntity): Long

    @Update
    suspend fun update(feature: SiteFeatureEntity)

    @Delete
    suspend fun delete(feature: SiteFeatureEntity)

    @Query("SELECT * FROM site_features WHERE plotId = :plotId")
    suspend fun getByPlotId(plotId: Long): List<SiteFeatureEntity>

    @Insert
    suspend fun insertAll(features: List<SiteFeatureEntity>): List<Long>

    @Query("DELETE FROM site_features WHERE plotId = :plotId")
    suspend fun deleteAllForPlot(plotId: Long)
}
