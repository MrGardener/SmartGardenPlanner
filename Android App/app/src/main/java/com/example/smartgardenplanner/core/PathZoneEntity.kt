package com.example.smartgardenplanner.core

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [NEW] A rectangular or point-based "no-plant" zone (path/walkway) within a plot. Requested
 * feature: a way to mark an area where nothing should be planted, independent of any seed's
 * exclusion radius, with support for both a straight rectangle (drag) and a curved/angled
 * polyline path (tap a sequence of points) with an adjustable width.
 *
 * For pathType == "RECTANGLE": xM/yM/widthM/heightM describe the rectangle as before.
 * For pathType == "POLYLINE": pointsJson holds the point sequence ("x1,y1;x2,y2;..." in
 * plot-relative meters) and widthM is reused as the path's stroke width; xM/yM/heightM are
 * unused (zeroed) for this type.
 *
 * @ColumnInfo defaults are set explicitly from the start — see the PlotEntity/PlantedNodeEntity
 * comments for why that matters (a real crash was caused by skipping this once already).
 */
@Entity(
    tableName = "path_zones",
    foreignKeys = [
        ForeignKey(
            entity = PlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["plotId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("plotId")]
)
data class PathZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plotId: Long,
    val xM: Float,
    val yM: Float,
    val widthM: Float,
    val heightM: Float,
    @ColumnInfo(defaultValue = "")
    val label: String = "",
    @ColumnInfo(defaultValue = "RECTANGLE")
    val pathType: String = "RECTANGLE", // [NEW] "RECTANGLE" | "POLYLINE"
    val pointsJson: String? = null       // [NEW] "x1,y1;x2,y2;..." for POLYLINE type only
)
