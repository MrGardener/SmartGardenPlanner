package com.example.smartgardenplanner.core

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [UPDATED] Added datePlantedEpochMillis (LLR-DAT-170: auto-populate from host clock if empty),
 * and a real ForeignKey to SeedEntity.botanicalCode (T2-DAT-065 / LLR-DAT-190) instead of a bare
 * unconstrained String seedCode. ON DELETE CASCADE from PlotEntity is now explicit (HLR-DAT-110).
 *
 * [FIXED] Added @ColumnInfo(defaultValue = ...) on the new fields — same class of bug as
 * PlotEntity (see that file's comment): a plain Kotlin default value is not reflected in the
 * SQL schema Room generates for a freshly-created table, only in the migration's hand-written
 * SQL. Adding these explicitly keeps a fresh install and a migrated install schema-identical.
 */
@Entity(
    tableName = "planted_nodes",
    foreignKeys = [
        ForeignKey(
            entity = PlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["plotId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SeedEntity::class,
            parentColumns = ["botanicalCode"],
            childColumns = ["seedCode"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("plotId"), Index("seedCode")]
)
data class PlantedNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plotId: Long,
    val seedCode: String,
    val coordinateXM: Float,
    val coordinateYM: Float,
    @ColumnInfo(defaultValue = "0")
    val datePlantedEpochMillis: Long = System.currentTimeMillis(), // [NEW] LLR-DAT-170
    @ColumnInfo(defaultValue = "0")
    val germinationFlagResolved: Boolean = false                   // [NEW] tracks whether a Plan-B was already resolved
)
