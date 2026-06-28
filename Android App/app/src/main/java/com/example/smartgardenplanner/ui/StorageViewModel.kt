package com.example.smartgardenplanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartgardenplanner.data.AppConfig
import com.example.smartgardenplanner.data.AppDatabase
import com.example.smartgardenplanner.data.SecurityRepository
import com.example.smartgardenplanner.data.SecurityRepositoryImpl
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StorageViewModel(
    application: Application,
    private val repository: SecurityRepository
) : AndroidViewModel(application) {

    // FIX: This secondary constructor is exactly what Android's default factory needs!
    constructor(application: Application) : this(
        application = application,
        repository = SecurityRepositoryImpl(AppDatabase.getInstance(application).configDao())
    )

    private val _targetConfigState = MutableStateFlow<AppConfig?>(null)
    val targetConfigState: StateFlow<AppConfig?> = _targetConfigState.asStateFlow()

    private val _loadEventChannel = MutableSharedFlow<AppConfig?>()
    val loadEventChannel: SharedFlow<AppConfig?> = _loadEventChannel.asSharedFlow()

    fun loadConfiguration(key: String) {
        viewModelScope.launch {
            try {
                val result = repository.fetchConfig(key)
                _targetConfigState.value = result
                _loadEventChannel.emit(result)
            } catch (e: Exception) {
                _targetConfigState.value = null
                _loadEventChannel.emit(null)
            }
        }
    }

    fun saveConfiguration(key: String, value: String) {
        viewModelScope.launch {
            repository.saveConfig(key, value)
            val updatedRecord = repository.fetchConfig(key)
            _targetConfigState.value = updatedRecord
        }
    }

    fun deleteConfiguration(key: String) {
        viewModelScope.launch {
            repository.deleteConfig(key)
            if (_targetConfigState.value?.configKey == key) {
                _targetConfigState.value = null
            }
            _loadEventChannel.emit(null)
        }
    }
}