package com.example.smartgardenplanner.core

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Site conditions marked on a plot (schema 8):
 *  - FULL_SUN / PART_SHADE / FULL_SHADE areas (FR-005)
 *  - FLOOD areas, with the months they flood (FR-004)
 *  - SLOPE areas, with downhill direction and grade (FR-003)
 *  - TREE / FENCE / WALL / BUILDING barriers with a height, which cast shade (FR-006)
 *
 * Areas are polygons (3+ points). A TREE is one point (the trunk) with [radiusM] as its crown radius.
 * FENCE / WALL / BUILDING are polylines of 2+ points.
 */
@Entity(
    tableName = "site_features",
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
data class SiteFeatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plotId: Long,
    val featureType: String,
    @ColumnInfo(defaultValue = "")
    val pointsJson: String = "",
    @ColumnInfo(defaultValue = "")
    val label: String = "",
    @ColumnInfo(defaultValue = "0")
    val heightM: Float = 0f,             // barriers
    @ColumnInfo(defaultValue = "0")
    val radiusM: Float = 0f,             // tree crown radius
    @ColumnInfo(defaultValue = "0")
    val slopeDirectionDeg: Float = 0f,   // downhill direction, compass degrees
    @ColumnInfo(defaultValue = "0")
    val slopeGradePct: Float = 0f,
    @ColumnInfo(defaultValue = "")
    val floodMonths: String = ""         // e.g. "3,4,5" (March to May)
)

/** Feature types stored in [SiteFeatureEntity.featureType]. */
enum class SiteFeatureType(val label: String, val isArea: Boolean, val isBarrier: Boolean) {
    FULL_SUN("Full sun (6+ h)", true, false),
    PART_SHADE("Part shade (3–6 h)", true, false),
    FULL_SHADE("Full shade (< 3 h)", true, false),
    FLOOD("Seasonal flooding", true, false),
    SLOPE("Slope", true, false),
    TREE("Tree", false, true),
    FENCE("Fence / hedge", false, true),
    WALL("Wall", false, true),
    BUILDING("Building", false, true);

    companion object {
        fun of(name: String): SiteFeatureType? = entries.firstOrNull { it.name == name }
    }
}
