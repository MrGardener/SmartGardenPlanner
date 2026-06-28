package com.example.smartgardenplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smartgardenplanner.ui.StorageViewModel
import com.example.smartgardenplanner.ui.theme.SmartGardenPlannerTheme
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {

    private val viewModel: StorageViewModel by viewModels()

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContent {
            SmartGardenPlannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SecureStorageDashboard(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureStorageDashboard(viewModel: StorageViewModel) {
    val currentConfigState by viewModel.targetConfigState.collectAsState()

    var inputKey by remember { mutableStateOf("garden_sync_token") }
    var inputValue by remember { mutableStateOf("") }

    // FIX: Listens to the event stream channel and forces the input box text to match the database state
    LaunchedEffect(viewModel.loadEventChannel) {
        viewModel.loadEventChannel.collect { loadedConfig ->
            inputValue = loadedConfig?.configValue ?: ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Hardware-Encrypted Storage",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = inputKey,
            onValueChange = { inputKey = it },
            label = { Text("Configuration Key") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = inputValue,
            onValueChange = { inputValue = it },
            label = { Text("Configuration Value") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.saveConfiguration(inputKey, inputValue) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Securely")
            }

            Button(
                onClick = { viewModel.loadConfiguration(inputKey) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Load Key")
            }
        }

        ElevatedButton(
            onClick = { viewModel.deleteConfiguration(inputKey) },
            colors = ButtonDefaults.elevatedButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Wipe Config", color = MaterialTheme.colorScheme.onErrorContainer)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Live Vault Context State:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (currentConfigState != null) {
                    Text("Key: ${currentConfigState?.configKey}", style = MaterialTheme.typography.bodyLarge)
                    Text("Value: ${currentConfigState?.configValue}", style = MaterialTheme.typography.bodyLarge)
                    Text("Timestamp: ${currentConfigState?.lastUpdatedTimestamp}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("No records loaded. Query the storage vault or insert a payload.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}