package com.example.smartgardenplanner.core

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A care task the user marked as done (FR-019): WATER or FERTILIZE for a plot, at a time. */
@Entity(
    tableName = "care_log",
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
data class CareLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plotId: Long,
    val taskType: String,
    val doneAtEpochMillis: Long
)
