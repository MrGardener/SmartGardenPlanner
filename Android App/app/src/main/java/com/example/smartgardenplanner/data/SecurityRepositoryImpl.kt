package com.example.smartgardenplanner.data

class SecurityRepositoryImpl(private val configDao: AppConfigDao) : SecurityRepository {

    override suspend fun fetchConfig(key: String): AppConfig? {
        return configDao.getConfigByKey(key)
    }

    override suspend fun saveConfig(key: String, value: String) {
        val configRecord = AppConfig(configKey = key, configValue = value)
        configDao.saveConfig(configRecord)
    }

    override suspend fun deleteConfig(key: String) {
        configDao.removeConfig(key)
    }
}