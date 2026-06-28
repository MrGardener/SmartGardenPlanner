package com.example.smartgardenplanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_configurations")
data class AppConfig(
    @PrimaryKey val configKey: String,
    val configValue: String,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)