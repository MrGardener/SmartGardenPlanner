package com.example.smartgardenplanner.core

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planted_nodes")
data class PlantedNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plotId: Long,
    val seedCode: String,
    val coordinateXM: Float,
    val coordinateYM: Float
)