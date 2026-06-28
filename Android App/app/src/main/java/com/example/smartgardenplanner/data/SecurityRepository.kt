package com.example.smartgardenplanner.data

import kotlinx.coroutines.flow.Flow

interface SecurityRepository {
    suspend fun fetchConfig(key: String): AppConfig?
    suspend fun saveConfig(key: String, value: String)
    suspend fun deleteConfig(key: String)
}