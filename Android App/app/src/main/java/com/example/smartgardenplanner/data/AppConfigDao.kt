package com.example.smartgardenplanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_configurations WHERE configKey = :key LIMIT 1")
    suspend fun getConfigByKey(key: String): AppConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: AppConfig)

    @Query("DELETE FROM app_configurations WHERE configKey = :key")
    suspend fun removeConfig(key: String)
}