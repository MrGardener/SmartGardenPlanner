package com.example.smartgardenplanner.core

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "plots")
data class PlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lengthM: Float,
    val widthM: Float,
    val description: String = ""
)