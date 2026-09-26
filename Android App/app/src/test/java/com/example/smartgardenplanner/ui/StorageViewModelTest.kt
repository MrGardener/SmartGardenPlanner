package com.example.smartgardenplanner.ui

import android.app.Application
import com.example.smartgardenplanner.data.AppConfig
import com.example.smartgardenplanner.data.SecurityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * [FIXED] This file previously contained a verbatim copy of StorageViewModel.kt itself — same
 * package, same class name, zero @Test functions. This is now a real unit test using a
 * hand-written fake SecurityRepository, avoiding the need for a real Room/Keystore/Application
 * stack in a plain JVM unit test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StorageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeSecurityRepository : SecurityRepository {
        private val store = mutableMapOf<String, String>()

        override suspend fun fetchConfig(key: String): AppConfig? {
            val value = store[key] ?: return null
            return AppConfig(configKey = key, configValue = value)
        }

        override suspend fun saveConfig(key: String, value: String) {
            store[key] = value
        }

        override suspend fun deleteConfig(key: String) {
            store.remove(key)
        }
    }

    private lateinit var viewModel: StorageViewModel
    private lateinit var fakeRepository: FakeSecurityRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeSecurityRepository()
        val mockApplication = mock(Application::class.java)
        viewModel = StorageViewModel(application = mockApplication, repository = fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveThenLoadConfiguration_returnsSavedValue() = runTest(testDispatcher) {
        viewModel.saveConfiguration("garden_sync_token", "abc123")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("abc123", viewModel.targetConfigState.value?.configValue)

        viewModel.loadConfiguration("garden_sync_token")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("abc123", viewModel.targetConfigState.value?.configValue)
    }

    @Test
    fun loadConfiguration_missingKey_resultsInNullState() = runTest(testDispatcher) {
        viewModel.loadConfiguration("does_not_exist")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.targetConfigState.value)
    }

    @Test
    fun deleteConfiguration_clearsState() = runTest(testDispatcher) {
        viewModel.saveConfiguration("temp_key", "temp_value")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("temp_value", viewModel.targetConfigState.value?.configValue)

        viewModel.deleteConfiguration("temp_key")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.targetConfigState.value)
    }
}
