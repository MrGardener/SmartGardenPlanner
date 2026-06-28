package com.example.smartgardenplanner

import android.app.Application
import com.example.smartgardenplanner.data.AppConfig
import com.example.smartgardenplanner.data.SecurityRepository
import com.example.smartgardenplanner.ui.StorageViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * A highly performant Fake implementation of our contract layer.
 * Bypasses the need for real C++ databases, device hardware, or active emulators.
 */
class FakeSecurityRepository : SecurityRepository {
    private val memoryStorage = mutableMapOf<String, AppConfig>()

    override suspend fun fetchConfig(key: String): AppConfig? {
        return memoryStorage[key]
    }

    override suspend fun saveConfig(key: String, value: String) {
        memoryStorage[key] = AppConfig(configKey = key, configValue = value)
    }

    override suspend fun deleteConfig(key: String) {
        memoryStorage.remove(key)
    }
}

// FIXED: Removed the obsolete @OptIn(ExperimentalCoroutinesScope::class) annotation block
class StorageViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeSecurityRepository
    private lateinit var viewModel: StorageViewModel
    private val mockApplication = mock(Application::class.java)

    @Before
    fun setUp() {
        // Redirect the Android architecture Main thread loop to our local test dispatcher context
        Dispatchers.setMain(testDispatcher)

        fakeRepository = FakeSecurityRepository()
        // Inject the fake contract into our architecture constructor!
        viewModel = StorageViewModel(mockApplication, fakeRepository)
    }

    @After
    fun tearDown() {
        // Reset the thread loops context to prevent system leaking across separate runs
        Dispatchers.resetMain()
    }

    @Test
    fun saveConfiguration_updatesTargetStateFlow_instantly() = runTest {
        // Execute a business action layer rule task
        viewModel.saveConfiguration("encryption_sync_flag", "VERIFIED_TRUE")

        // Force the coroutine thread event loops to finish processing internal steps
        advanceUntilIdle()

        // Assert that the state machine updated its public variable backings perfectly
        val observedState = viewModel.targetConfigState.value
        assertNotNull("The internal state backing shouldn't remain null after saving", observedState)
        assertEquals("encryption_sync_flag", observedState?.configKey)
        assertEquals("VERIFIED_TRUE", observedState?.configValue)
    }

    @Test
    fun deleteConfiguration_clearsTargetStateFlow_automatically() = runTest {
        // Pre-populate the repository with data state criteria
        fakeRepository.saveConfig("temporary_wipe_key", "DISPOSABLE_DATA")

        // Load it into the ViewModel
        viewModel.loadConfiguration("temporary_wipe_key")
        advanceUntilIdle()
        assertNotNull(viewModel.targetConfigState.value)

        // Fire the deletion routine
        viewModel.deleteConfiguration("temporary_wipe_key")
        advanceUntilIdle()

        // Verify that the view model state machine cleared out tracking lines cleanly
        assertNull("State configuration flow parameter target should register null after wipe", viewModel.targetConfigState.value)
    }
}