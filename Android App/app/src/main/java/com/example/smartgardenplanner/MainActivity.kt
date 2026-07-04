package com.example.smartgardenplanner

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- EXPLICIT COMPLIANCE IMPORTS: PREVENT COUPLING RESOLUTION FAILURES ---
import com.example.smartgardenplanner.core.PlotEntity
import com.example.smartgardenplanner.core.PlantedNodeEntity
import com.example.smartgardenplanner.core.SgpExecutors
import com.example.smartgardenplanner.core.RealSecurityKeyManager
import com.example.smartgardenplanner.core.SecurityKeyManager
import com.example.smartgardenplanner.core.SecurityAuditLogger
import com.example.smartgardenplanner.core.SensorMeasurementEngine
import com.example.smartgardenplanner.core.BoundedHistoryStack

import com.example.smartgardenplanner.data.AppDatabase
import com.example.smartgardenplanner.ui.StorageViewModel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

// Navigation state enum to route between the screens described in the IDD
enum class SgpScreen {
    DASHBOARD,
    CREATOR,
    CANVAS
}

class MainActivity : ComponentActivity() {
    private lateinit var keyManager: RealSecurityKeyManager
    private lateinit var auditLogger: SecurityAuditLogger
    private lateinit var database: AppDatabase
    private lateinit var sensorEngine: SensorMeasurementEngine
    private lateinit var viewModel: StorageViewModel
    private lateinit var secureVault: SecureConfigVault

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Initialize High-Integrity Cryptographic Enclave (StrongBox)
        keyManager = RealSecurityKeyManager()
        keyManager.initializeKeyStore()

        // 2. Initialize Diagnostic logging & encrypted SQLite local database
        auditLogger = SecurityAuditLogger(applicationContext)

        // --- SECURE INJECTION FIXED ---
        database = AppDatabase.getInstance(applicationContext, keyManager)
        sensorEngine = SensorMeasurementEngine(applicationContext)
        secureVault = SecureConfigVault(keyManager, applicationContext)

        // 3. Pre-populate database seed tables to satisfy database Foreign Key constraints
        CoroutineScope(SgpExecutors.dbDispatcher).launch {
            try {
                val db = database.openHelper.writableDatabase

                // Inspect master catalog to resolve actual Seed table representation dynamically
                val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
                val tableNames = mutableListOf<String>()
                while (tableCursor.moveToNext()) {
                    tableNames.add(tableCursor.getString(0))
                }
                tableCursor.close()

                val seedTable = when {
                    tableNames.contains("seeds") -> "seeds"
                    tableNames.contains("SeedEntity") -> "SeedEntity"
                    tableNames.contains("seed") -> "seed"
                    else -> "seeds"
                }

                db.execSQL("INSERT OR IGNORE INTO $seedTable (botanicalCode, common_name, botanical_family, exclusion_radius_m) VALUES ('SOL-LYC', 'Tomato', 'Solanaceae', 1.25)")
                db.execSQL("INSERT OR IGNORE INTO $seedTable (botanicalCode, common_name, botanical_family, exclusion_radius_m) VALUES ('PL-BAS', 'Basil', 'Lamiaceae', 0.40)")
                db.execSQL("INSERT OR IGNORE INTO $seedTable (botanicalCode, common_name, botanical_family, exclusion_radius_m) VALUES ('PL-MAR', 'Marigold', 'Asteraceae', 0.60)")
                auditLogger.appendLog("DATABASE_INIT: Seeds lookup data pre-populated in table $seedTable.")
            } catch (e: Exception) {
                auditLogger.appendLog("DATABASE_INIT_ERROR: Pre-population failed. ${e.message}")
            }
        }

        // 4. Instantiate storage View-Model passing KeyManager dependency
        viewModel = StorageViewModel(application, keyManager)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF10B981), // Emerald Green
                    background = Color(0xFF0F172A), // Deep Navy Slate
                    surface = Color(0xFF1E293B)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigationContainer(viewModel, sensorEngine, database, auditLogger, secureVault)
                }
            }
        }
    }
}

@Composable
fun AppNavigationContainer(
    viewModel: StorageViewModel,
    sensorEngine: SensorMeasurementEngine,
    database: AppDatabase,
    auditLogger: SecurityAuditLogger,
    secureVault: SecureConfigVault
) {
    var currentScreen by remember { mutableStateOf(SgpScreen.DASHBOARD) }
    var selectedPlotId by remember { mutableStateOf(-1L) }

    when (currentScreen) {
        SgpScreen.DASHBOARD -> {
            DashboardScreen(
                database = database,
                secureVault = secureVault,
                onNavigateToCreator = { currentScreen = SgpScreen.CREATOR },
                onSelectPlot = { plotId ->
                    selectedPlotId = plotId
                    currentScreen = SgpScreen.CANVAS
                }
            )
        }
        SgpScreen.CREATOR -> {
            CreatorScreen(
                database = database,
                auditLogger = auditLogger,
                onNavigateBack = { currentScreen = SgpScreen.DASHBOARD },
                onWorkspaceInitialized = { plotId ->
                    selectedPlotId = plotId
                    currentScreen = SgpScreen.CANVAS
                }
            )
        }
        SgpScreen.CANVAS -> {
            CanvasWorkspaceScreen(
                plotId = selectedPlotId,
                database = database,
                sensorEngine = sensorEngine,
                onNavigateBack = { currentScreen = SgpScreen.DASHBOARD }
            )
        }
    }
}

// =====================================================================
// SCREEN NODE 1: DASHBOARD LANDING ROOT WITH SECURE STORAGE VAULT
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    database: AppDatabase,
    secureVault: SecureConfigVault,
    onNavigateToCreator: () -> Unit,
    onSelectPlot: (Long) -> Unit
) {
    var plotList by remember { mutableStateOf<List<PlotEntity>>(emptyList()) }
    var showVaultConfig by remember { mutableStateOf(false) }

    // Read stored plots from encrypted SQLite in database thread context
    LaunchedEffect(Unit) {
        withContext(SgpExecutors.dbDispatcher) {
            val db = database.openHelper.readableDatabase

            // Query master catalog to resolve actual table name representation securely
            val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
            val tableNames = mutableListOf<String>()
            while (tableCursor.moveToNext()) {
                tableNames.add(tableCursor.getString(0))
            }
            tableCursor.close()

            val plotTable = when {
                tableNames.contains("plots") -> "plots"
                tableNames.contains("PlotEntity") -> "PlotEntity"
                tableNames.contains("plot") -> "plot"
                else -> "plots"
            }

            val cursor = db.query("SELECT * FROM $plotTable")
            val list = mutableListOf<PlotEntity>()

            val idCol = cursor.columnNames.indexOfFirst { it.equals("id", ignoreCase = true) }
            val nameCol = cursor.columnNames.indexOfFirst { it.equals("name", ignoreCase = true) }
            val lengthCol = cursor.columnNames.indexOfFirst { it.equals("lengthM", ignoreCase = true) || it.equals("length_m", ignoreCase = true) }
            val widthCol = cursor.columnNames.indexOfFirst { it.equals("widthM", ignoreCase = true) || it.equals("width_m", ignoreCase = true) }
            val descCol = cursor.columnNames.indexOfFirst { it.equals("description", ignoreCase = true) }

            while (cursor.moveToNext()) {
                list.add(
                    PlotEntity(
                        id = cursor.getLong(idCol),
                        name = cursor.getString(nameCol),
                        lengthM = cursor.getFloat(lengthCol),
                        widthM = cursor.getFloat(widthCol),
                        description = if (descCol != -1) cursor.getString(descCol) else ""
                    )
                )
            }
            cursor.close()
            plotList = list
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Garden Planner", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { showVaultConfig = !showVaultConfig }) {
                        Icon(Icons.Default.Settings, contentDescription = "Secure Key-Value Vault Configurations", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreator,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color(0xFF0F172A),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Create New Plot", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Animated Configuration Vault interface
            AnimatedVisibility(visible = showVaultConfig) {
                HardwareSecureVaultCard(secureVault)
            }

            Text("Active Plots", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            if (plotList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No agricultural designs committed.", color = Color.Gray, fontSize = 14.sp)
                        Text("Tap button to create a new layout.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(plotList) { plot: PlotEntity ->
                        Card(
                            onClick = { onSelectPlot(plot.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(plot.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Physical Boundaries: ${plot.lengthM}m × ${plot.widthM}m",
                                    fontSize = 12.sp,
                                    color = Color.LightGray
                                )
                                if (plot.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(plot.description, fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// INTEGRATED SUB-COMPONENT: HARDWARE VAULT COMPONENT CARD
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareSecureVaultCard(secureVault: SecureConfigVault) {
    var inputKey by remember { mutableStateOf("garden_sync_token") }
    var inputValue by remember { mutableStateOf("") }
    var loadedKey by remember { mutableStateOf("") }
    var loadedValue by remember { mutableStateOf("") }
    var lastUpdatedTimestamp by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🔐 Hardware-Encrypted Storage Vault",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = inputKey,
                onValueChange = { newValue -> inputKey = newValue },
                label = { Text("Configuration Key") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = inputValue,
                onValueChange = { newValue -> inputValue = newValue },
                label = { Text("Configuration Value") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (inputKey.isNotBlank() && inputValue.isNotBlank()) {
                            secureVault.saveConfig(inputKey, inputValue)
                            loadedKey = inputKey
                            loadedValue = inputValue
                            lastUpdatedTimestamp = System.currentTimeMillis().toString()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Securely", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        if (inputKey.isNotBlank()) {
                            val result = secureVault.loadConfig(inputKey)
                            loadedKey = inputKey
                            if (result != null) {
                                loadedValue = result
                                inputValue = result
                                lastUpdatedTimestamp = System.currentTimeMillis().toString()
                            } else {
                                loadedValue = "[Record Not Found]"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Load Key", fontSize = 12.sp)
                }
            }

            ElevatedButton(
                onClick = {
                    if (inputKey.isNotBlank()) {
                        secureVault.deleteConfig(inputKey)
                        loadedKey = inputKey
                        loadedValue = "[DELETED/WIPED]"
                        inputValue = ""
                    }
                },
                colors = ButtonDefaults.elevatedButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Wipe Config", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text("Live Vault Context State:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                if (loadedKey.isNotEmpty()) {
                    Text("Key: $loadedKey", style = MaterialTheme.typography.bodyMedium)
                    Text("Value: $loadedValue", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    if (lastUpdatedTimestamp.isNotEmpty()) {
                        Text("Timestamp: $lastUpdatedTimestamp", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                } else {
                    Text("No records loaded. Query the storage vault or insert a payload.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}

// =====================================================================
// SCREEN NODE 2: CREATOR PARAMETER WORKSPACE INGESTION LAYER
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorScreen(
    database: AppDatabase,
    auditLogger: SecurityAuditLogger,
    onNavigateBack: () -> Unit,
    onWorkspaceInitialized: (Long) -> Unit
) {
    var plotName by remember { mutableStateOf("") }
    var plotLength by remember { mutableStateOf("") }
    var plotWidth by remember { mutableStateOf("") }
    var scaleEngineSelection by remember { mutableStateOf("Manual Dimensions Entry") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val regexValidator = remember { Regex("^[0-9]+(\\.[0-9]+)?$") }

    val isInputValid = plotName.isNotBlank() &&
            plotLength.matches(regexValidator) &&
            plotWidth.matches(regexValidator) &&
            (plotLength.toFloatOrNull() ?: 0f) in 0.05f..1000.0f &&
            (plotWidth.toFloatOrNull() ?: 0f) in 0.05f..1000.0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Initialize Plot Configuration") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = plotName,
                onValueChange = { newValue -> plotName = newValue },
                label = { Text("Agricultural Plot Designation Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = plotLength,
                onValueChange = { newValue -> plotLength = newValue },
                label = { Text("Real-World Length Dimension (Meters)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                isError = plotLength.isNotEmpty() && (!plotLength.matches(regexValidator) || (plotLength.toFloatOrNull() ?: 0f) < 0.05f || (plotLength.toFloatOrNull() ?: 0f) > 1000.0f)
            )

            OutlinedTextField(
                value = plotWidth,
                onValueChange = { newValue -> plotWidth = newValue },
                label = { Text("Real-World Width Dimension (Meters)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                isError = plotWidth.isNotEmpty() && (!plotWidth.matches(regexValidator) || (plotWidth.toFloatOrNull() ?: 0f) < 0.05f || (plotWidth.toFloatOrNull() ?: 0f) > 1000.0f)
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = scaleEngineSelection,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Scale Engine Computing Source") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dropdownExpanded = true }
                )
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DropdownMenuItem(
                        text = { Text("Manual Dimensions Entry") },
                        onClick = {
                            scaleEngineSelection = "Manual Dimensions Entry"
                            dropdownExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Device IMU Sensor Measuring") },
                        onClick = {
                            scaleEngineSelection = "Device IMU Sensor Measuring"
                            dropdownExpanded = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1.0f))

            Button(
                onClick = {
                    if (isInputValid) {
                        CoroutineScope(SgpExecutors.dbDispatcher).launch {
                            val db = database.openHelper.writableDatabase

                            // Query actual SQL database schema dynamically to align with compiler schema
                            val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
                            val tableNames = mutableListOf<String>()
                            while (tableCursor.moveToNext()) {
                                tableNames.add(tableCursor.getString(0))
                            }
                            tableCursor.close()

                            val plotTable = when {
                                tableNames.contains("plots") -> "plots"
                                tableNames.contains("PlotEntity") -> "PlotEntity"
                                tableNames.contains("plot") -> "plot"
                                else -> "plots"
                            }

                            val testCursor = db.query("SELECT * FROM $plotTable LIMIT 1")
                            val lengthCol = testCursor.columnNames.firstOrNull { it.equals("lengthM", ignoreCase = true) || it.equals("length_m", ignoreCase = true) } ?: "lengthM"
                            val widthCol = testCursor.columnNames.firstOrNull { it.equals("widthM", ignoreCase = true) || it.equals("width_m", ignoreCase = true) } ?: "widthM"
                            testCursor.close()

                            val stmt = db.compileStatement(
                                "INSERT INTO $plotTable (name, $lengthCol, $widthCol, description) VALUES (?, ?, ?, ?)"
                            )
                            stmt.bindString(1, plotName)
                            stmt.bindDouble(2, plotLength.toDouble())
                            stmt.bindDouble(3, plotWidth.toDouble())
                            stmt.bindString(4, "")
                            val plotId = stmt.executeInsert()
                            stmt.close()

                            withContext(Dispatchers.Main) {
                                onWorkspaceInitialized(plotId)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isInputValid,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Initialize Spatial Workspace", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            }
        }
    }
}

// =====================================================================
// SCREEN NODE 3: INTERACTIVE CANVAS VIEWPORT
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasWorkspaceScreen(
    plotId: Long,
    database: AppDatabase,
    sensorEngine: SensorMeasurementEngine,
    onNavigateBack: () -> Unit
) {
    var activePlot by remember { mutableStateOf<PlotEntity?>(null) }
    var nodesState by remember { mutableStateOf<List<PlantedNodeEntity>>(emptyList()) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val undoStack = remember { BoundedHistoryStack<List<PlantedNodeEntity>>(25) }
    val redoStack = remember { BoundedHistoryStack<List<PlantedNodeEntity>>(25) }

    var activeSeedCode by remember { mutableStateOf("SOL-LYC") } // Default to Tomato
    var activeExclusionRadius by remember { mutableStateOf(1.25f) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Read stored plots and associated node structures from SQLite locally
    LaunchedEffect(plotId) {
        withContext(SgpExecutors.dbDispatcher) {
            val db = database.openHelper.readableDatabase

            // Query master catalogs to dynamically map compiler tables and columns securely
            val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
            val tableNames = mutableListOf<String>()
            while (tableCursor.moveToNext()) {
                tableNames.add(tableCursor.getString(0))
            }
            tableCursor.close()

            val plotTable = when {
                tableNames.contains("plots") -> "plots"
                tableNames.contains("PlotEntity") -> "PlotEntity"
                tableNames.contains("plot") -> "plot"
                else -> "plots"
            }
            val nodeTable = when {
                tableNames.contains("planted_nodes") -> "planted_nodes"
                tableNames.contains("PlantedNodeEntity") -> "PlantedNodeEntity"
                tableNames.contains("planted_node") -> "planted_node"
                else -> "planted_nodes"
            }

            // 1. Load Plot Details
            val plotCursor = db.query("SELECT * FROM $plotTable WHERE id = $plotId")
            if (plotCursor.moveToFirst()) {
                val idCol = plotCursor.columnNames.indexOfFirst { it.equals("id", ignoreCase = true) }
                val nameCol = plotCursor.columnNames.indexOfFirst { it.equals("name", ignoreCase = true) }
                val lengthCol = plotCursor.columnNames.indexOfFirst { it.equals("lengthM", ignoreCase = true) || it.equals("length_m", ignoreCase = true) }
                val widthCol = plotCursor.columnNames.indexOfFirst { it.equals("widthM", ignoreCase = true) || it.equals("width_m", ignoreCase = true) }
                val descCol = plotCursor.columnNames.indexOfFirst { it.equals("description", ignoreCase = true) }

                activePlot = PlotEntity(
                    id = plotCursor.getLong(idCol),
                    name = plotCursor.getString(nameCol),
                    lengthM = plotCursor.getFloat(lengthCol),
                    widthM = plotCursor.getFloat(widthCol),
                    description = if (descCol != -1) plotCursor.getString(descCol) else ""
                )
            }
            plotCursor.close()

            // 2. Load Placed Coordinate Nodes with dynamic column mapping to prevent crashes
            val testNodeCursor = db.query("SELECT * FROM $nodeTable LIMIT 1")
            val plotIdColName = testNodeCursor.columnNames.firstOrNull { it.equals("plot_id", ignoreCase = true) || it.equals("plotId", ignoreCase = true) } ?: "plotId"
            testNodeCursor.close()

            val nodeCursor = db.query("SELECT * FROM $nodeTable WHERE $plotIdColName = $plotId")
            val list = mutableListOf<PlantedNodeEntity>()

            val nodeIdIdx = nodeCursor.columnNames.indexOfFirst { it.equals("id", ignoreCase = true) }
            val plotIdIdx = nodeCursor.columnNames.indexOfFirst { it.equals("plot_id", ignoreCase = true) || it.equals("plotId", ignoreCase = true) }
            val seedCodeIdx = nodeCursor.columnNames.indexOfFirst { it.equals("seed_code", ignoreCase = true) || it.equals("seedCode", ignoreCase = true) }
            val coordXIdx = nodeCursor.columnNames.indexOfFirst { it.equals("coordinate_x", ignoreCase = true) || it.equals("coordinateXM", ignoreCase = true) || it.equals("coordinate_x_m", ignoreCase = true) }
            val coordYIdx = nodeCursor.columnNames.indexOfFirst { it.equals("coordinate_y", ignoreCase = true) || it.equals("coordinateYM", ignoreCase = true) || it.equals("coordinate_y_m", ignoreCase = true) }

            while (nodeCursor.moveToNext()) {
                list.add(
                    PlantedNodeEntity(
                        id = nodeCursor.getLong(nodeIdIdx),
                        plotId = nodeCursor.getLong(plotIdIdx),
                        seedCode = nodeCursor.getString(seedCodeIdx),
                        coordinateXM = nodeCursor.getFloat(coordXIdx),
                        coordinateYM = nodeCursor.getFloat(coordYIdx)
                    )
                )
            }
            nodeCursor.close()

            withContext(Dispatchers.Main) {
                nodesState = list
                undoStack.clear()
                redoStack.clear()
                undoStack.push(list)
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message: String ->
            snackbarHostState.showSnackbar(message)
            snackbarMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(activePlot?.name?.let { "Workspace UI Editor" } ?: "Loading Layout...") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (undoStack.size() > 1) {
                                val current = undoStack.pop()
                                if (current != null) {
                                    redoStack.push(current)
                                }
                                val previous = undoStack.toList().lastOrNull() ?: emptyList()
                                nodesState = previous

                                // Sync back to the DB inside SgpExecutors thread pools
                                CoroutineScope(SgpExecutors.dbDispatcher).launch {
                                    val db = database.openHelper.writableDatabase

                                    val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
                                    val tableNames = mutableListOf<String>()
                                    while (tableCursor.moveToNext()) {
                                        tableNames.add(tableCursor.getString(0))
                                    }
                                    tableCursor.close()

                                    val nodeTable = when {
                                        tableNames.contains("planted_nodes") -> "planted_nodes"
                                        tableNames.contains("PlantedNodeEntity") -> "PlantedNodeEntity"
                                        tableNames.contains("planted_node") -> "planted_node"
                                        else -> "planted_nodes"
                                    }

                                    val testCursor = db.query("SELECT * FROM $nodeTable LIMIT 1")
                                    val plotIdCol = testCursor.columnNames.firstOrNull { it.equals("plot_id", ignoreCase = true) || it.equals("plotId", ignoreCase = true) } ?: "plotId"
                                    val seedCodeCol = testCursor.columnNames.firstOrNull { it.equals("seed_code", ignoreCase = true) || it.equals("seedCode", ignoreCase = true) } ?: "seedCode"
                                    val coordXCol = testCursor.columnNames.firstOrNull { it.equals("coordinate_x", ignoreCase = true) || it.equals("coordinateXM", ignoreCase = true) || it.equals("coordinate_x_m", ignoreCase = true) } ?: "coordinateXM"
                                    val coordYCol = testCursor.columnNames.firstOrNull { it.equals("coordinate_y", ignoreCase = true) || it.equals("coordinateYM", ignoreCase = true) || it.equals("coordinate_y_m", ignoreCase = true) } ?: "coordinateYM"
                                    testCursor.close()

                                    db.execSQL("DELETE FROM $nodeTable WHERE $plotIdCol = $plotId")

                                    previous.forEach { node: PlantedNodeEntity ->
                                        val stmt = db.compileStatement(
                                            "INSERT INTO $nodeTable ($plotIdCol, $seedCodeCol, $coordXCol, $coordYCol) VALUES (?, ?, ?, ?)"
                                        )
                                        stmt.bindLong(1, plotId)
                                        stmt.bindString(2, node.seedCode)
                                        stmt.bindDouble(3, node.coordinateXM.toDouble())
                                        stmt.bindDouble(4, node.coordinateYM.toDouble())
                                        stmt.executeInsert()
                                        stmt.close()
                                    }
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Undo", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = {
                            val nextState = redoStack.pop()
                            if (nextState != null) {
                                undoStack.push(nextState)
                                nodesState = nextState

                                // Sync back to the DB inside SgpExecutors thread pools
                                CoroutineScope(SgpExecutors.dbDispatcher).launch {
                                    val db = database.openHelper.writableDatabase

                                    val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
                                    val tableNames = mutableListOf<String>()
                                    while (tableCursor.moveToNext()) {
                                        tableNames.add(tableCursor.getString(0))
                                    }
                                    tableCursor.close()

                                    val nodeTable = when {
                                        tableNames.contains("planted_nodes") -> "planted_nodes"
                                        tableNames.contains("PlantedNodeEntity") -> "PlantedNodeEntity"
                                        tableNames.contains("planted_node") -> "planted_node"
                                        else -> "planted_nodes"
                                    }

                                    val testCursor = db.query("SELECT * FROM $nodeTable LIMIT 1")
                                    val plotIdCol = testCursor.columnNames.firstOrNull { it.equals("plot_id", ignoreCase = true) || it.equals("plotId", ignoreCase = true) } ?: "plotId"
                                    val seedCodeCol = testCursor.columnNames.firstOrNull { it.equals("seed_code", ignoreCase = true) || it.equals("seedCode", ignoreCase = true) } ?: "seedCode"
                                    val coordXCol = testCursor.columnNames.firstOrNull { it.equals("coordinate_x", ignoreCase = true) || it.equals("coordinateXM", ignoreCase = true) || it.equals("coordinate_x_m", ignoreCase = true) } ?: "coordinateXM"
                                    val coordYCol = testCursor.columnNames.firstOrNull { it.equals("coordinate_y", ignoreCase = true) || it.equals("coordinateYM", ignoreCase = true) || it.equals("coordinate_y_m", ignoreCase = true) } ?: "coordinateYM"
                                    testCursor.close()

                                    db.execSQL("DELETE FROM $nodeTable WHERE $plotIdCol = $plotId")

                                    nextState.forEach { node: PlantedNodeEntity ->
                                        val stmt = db.compileStatement(
                                            "INSERT INTO $nodeTable ($plotIdCol, $seedCodeCol, $coordXCol, $coordYCol) VALUES (?, ?, ?, ?)"
                                        )
                                        stmt.bindLong(1, plotId)
                                        stmt.bindString(2, node.seedCode)
                                        stmt.bindDouble(3, node.coordinateXM.toDouble())
                                        stmt.bindDouble(4, node.coordinateYM.toDouble())
                                        stmt.executeInsert()
                                        stmt.close()
                                    }
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Redo", fontSize = 12.sp)
                    }
                }
            )
        }
    ) { paddingValues ->
        activePlot?.let { state: PlotEntity ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Plot Layout Size: ${state.lengthM}m × ${state.widthM}m", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Active Plantings: ${nodesState.size} nodes placed", color = Color.Gray, fontSize = 11.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxWidth()
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                        .background(Color(0xFF070B14))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = { offset: Offset ->
                                        val realXM = (offset.x / size.width) * state.lengthM
                                        val realYM = (offset.y / size.height) * state.widthM

                                        var spacingViolation = false
                                        nodesState.forEach { existingNode: PlantedNodeEntity ->
                                            val existingRadius = when (existingNode.seedCode) {
                                                "SOL-LYC" -> 1.25f
                                                "PL-BAS" -> 0.40f
                                                "PL-MAR" -> 0.60f
                                                else -> 0.50f
                                            }
                                            val distance = Math.sqrt(
                                                Math.pow((realXM - existingNode.coordinateXM).toDouble(), 2.0) +
                                                        Math.pow((realYM - existingNode.coordinateYM).toDouble(), 2.0)
                                            ).toFloat()

                                            // Explicit Float casting stops operator resolution errors in Kotlin loops
                                            if (distance < (activeExclusionRadius.toFloat() + existingRadius.toFloat())) {
                                                spacingViolation = true
                                            }
                                        }

                                        if (spacingViolation) {
                                            snackbarMessage = "CRITICAL INTERFACE BOUNDARY INTRUSION VIOLATION. Spacing conflict detected!"
                                        } else {
                                            CoroutineScope(SgpExecutors.dbDispatcher).launch {
                                                val db = database.openHelper.writableDatabase

                                                val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table'")
                                                val tableNames = mutableListOf<String>()
                                                while (tableCursor.moveToNext()) {
                                                    tableNames.add(tableCursor.getString(0))
                                                }
                                                tableCursor.close()

                                                val nodeTable = when {
                                                    tableNames.contains("planted_nodes") -> "planted_nodes"
                                                    tableNames.contains("PlantedNodeEntity") -> "PlantedNodeEntity"
                                                    tableNames.contains("planted_node") -> "planted_node"
                                                    else -> "planted_nodes"
                                                }

                                                val testCursor = db.query("SELECT * FROM $nodeTable LIMIT 1")
                                                val plotIdCol = testCursor.columnNames.firstOrNull { it.equals("plot_id", ignoreCase = true) || it.equals("plotId", ignoreCase = true) } ?: "plotId"
                                                val seedCodeCol = testCursor.columnNames.firstOrNull { it.equals("seed_code", ignoreCase = true) || it.equals("seedCode", ignoreCase = true) } ?: "seedCode"
                                                val coordXCol = testCursor.columnNames.firstOrNull { it.equals("coordinate_x", ignoreCase = true) || it.equals("coordinateXM", ignoreCase = true) || it.equals("coordinate_x_m", ignoreCase = true) } ?: "coordinateXM"
                                                val coordYCol = testCursor.columnNames.firstOrNull { it.equals("coordinate_y", ignoreCase = true) || it.equals("coordinateYM", ignoreCase = true) || it.equals("coordinate_y_m", ignoreCase = true) } ?: "coordinateYM"
                                                testCursor.close()

                                                val stmt = db.compileStatement(
                                                    "INSERT INTO $nodeTable ($plotIdCol, $seedCodeCol, $coordXCol, $coordYCol) VALUES (?, ?, ?, ?)"
                                                )
                                                stmt.bindLong(1, plotId)
                                                stmt.bindString(2, activeSeedCode)
                                                stmt.bindDouble(3, realXM.toDouble())
                                                stmt.bindDouble(4, realYM.toDouble())
                                                stmt.executeInsert()
                                                stmt.close()

                                                val nodeCursor = db.query("SELECT * FROM $nodeTable WHERE $plotIdCol = $plotId")
                                                val updatedList = mutableListOf<PlantedNodeEntity>()

                                                val nodeIdIdx = nodeCursor.columnNames.indexOfFirst { it.equals("id", ignoreCase = true) }
                                                val plotIdIdx = nodeCursor.columnNames.indexOfFirst { it.equals("plot_id", ignoreCase = true) || it.equals("plotId", ignoreCase = true) }
                                                val seedCodeIdx = nodeCursor.columnNames.indexOfFirst { it.equals("seed_code", ignoreCase = true) || it.equals("seedCode", ignoreCase = true) }
                                                val coordXIdx = nodeCursor.columnNames.indexOfFirst { it.equals("coordinate_x", ignoreCase = true) || it.equals("coordinateXM", ignoreCase = true) || it.equals("coordinate_x_m", ignoreCase = true) }
                                                val coordYIdx = nodeCursor.columnNames.indexOfFirst { it.equals("coordinate_y", ignoreCase = true) || it.equals("coordinateYM", ignoreCase = true) || it.equals("coordinate_y_m", ignoreCase = true) }

                                                while (nodeCursor.moveToNext()) {
                                                    updatedList.add(
                                                        PlantedNodeEntity(
                                                            id = nodeCursor.getLong(nodeIdIdx),
                                                            plotId = nodeCursor.getLong(plotIdIdx),
                                                            seedCode = nodeCursor.getString(seedCodeIdx),
                                                            coordinateXM = nodeCursor.getFloat(coordXIdx),
                                                            coordinateYM = nodeCursor.getFloat(coordYIdx)
                                                        )
                                                    )
                                                }
                                                nodeCursor.close()

                                                withContext(Dispatchers.Main) {
                                                    nodesState = updatedList
                                                    undoStack.push(updatedList)
                                                    redoStack.clear()
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        val canvasW = size.width
                        val canvasH = size.height

                        val scaleX = canvasW / state.lengthM
                        val scaleY = canvasH / state.widthM

                        val stepX = scaleX
                        var gridX = 0f
                        while (gridX < canvasW) {
                            drawLine(
                                color = Color(0xFF1E293B),
                                start = Offset(gridX, 0f),
                                end = Offset(gridX, canvasH),
                                strokeWidth = 1f
                            )
                            gridX += stepX
                        }

                        val stepY = scaleY
                        var gridY = 0f
                        while (gridY < canvasH) {
                            drawLine(
                                color = Color(0xFF1E293B),
                                start = Offset(0f, gridY),
                                end = Offset(canvasW, gridY),
                                strokeWidth = 1f
                            )
                            gridY += stepY
                        }

                        nodesState.forEach { node: PlantedNodeEntity ->
                            val centerOffset = Offset(
                                x = node.coordinateXM * scaleX,
                                y = node.coordinateYM * scaleY
                            )

                            val exclusionRadiusM = when (node.seedCode) {
                                "SOL-LYC" -> 1.25f
                                "PL-BAS" -> 0.40f
                                "PL-MAR" -> 0.60f
                                else -> 0.50f
                            }
                            val exclusionRadiusPx = exclusionRadiusM * scaleX

                            drawCircle(
                                color = Color(0x30EF4444),
                                radius = exclusionRadiusPx,
                                center = centerOffset
                            )
                            drawCircle(
                                color = Color(0x80EF4444),
                                radius = exclusionRadiusPx,
                                center = centerOffset,
                                style = Stroke(width = 2f)
                            )

                            drawCircle(
                                color = when (node.seedCode) {
                                    "SOL-LYC" -> Color(0xFFF87171)
                                    "PL-BAS" -> Color(0xFF34D399)
                                    else -> Color(0xFFFBBF24)
                                },
                                radius = 10f,
                                center = centerOffset
                            )
                        }
                    }

                    Text(
                        text = "Double-tap Canvas frame to drop coordinate pins",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Select Plant Entity to Place:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                activeSeedCode = "SOL-LYC"
                                activeExclusionRadius = 1.25f
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeSeedCode == "SOL-LYC") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Tomato (1.25m)", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                activeSeedCode = "PL-BAS"
                                activeExclusionRadius = 0.40f
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeSeedCode == "PL-BAS") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Basil (0.4m)", fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                activeSeedCode = "PL-MAR"
                                activeExclusionRadius = 0.60f
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeSeedCode == "PL-MAR") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Marigold (0.6m)", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// INTEGRATED ENCRYPTION VAULT HELPER CLASS
// =====================================================================
class SecureConfigVault(private val keyManager: SecurityKeyManager, private val context: Context) {
    fun saveConfig(key: String, value: String) {
        val file = File(context.filesDir, "config_$key.enc")
        val cipher = keyManager.getCipherEncryptMode()
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        file.writeBytes(iv + ciphertext)
    }

    fun loadConfig(key: String): String? {
        val file = File(context.filesDir, "config_$key.enc")
        if (!file.exists()) return null
        val fileBytes = file.readBytes()
        if (fileBytes.size < 12) return null
        val iv = fileBytes.copyOfRange(0, 12)
        val ciphertext = fileBytes.copyOfRange(12, fileBytes.size)
        val cipher = keyManager.getCipherDecryptMode(iv)
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    fun deleteConfig(key: String): Boolean {
        val file = File(context.filesDir, "config_$key.enc")
        return if (file.exists()) file.delete() else false
    }
}