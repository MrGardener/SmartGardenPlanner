package com.example.smartgardenplanner

import android.app.Activity
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

// --- EXPLICIT COMPLIANCE IMPORTS: PREVENT COUPLING RESOLUTION FAILURES ---
import com.example.smartgardenplanner.core.PlotEntity
import com.example.smartgardenplanner.core.PlantedNodeEntity
import com.example.smartgardenplanner.core.PathZoneEntity
import com.example.smartgardenplanner.core.SeedEntity
import com.example.smartgardenplanner.core.SgpExecutors
import com.example.smartgardenplanner.core.RealSecurityKeyManager
import com.example.smartgardenplanner.core.SecurityKeyManager
import com.example.smartgardenplanner.core.SecurityAuditLogger
import com.example.smartgardenplanner.core.SensorMeasurementEngine
import com.example.smartgardenplanner.core.BoundedHistoryStack
import com.example.smartgardenplanner.core.AutoPopulateEngine
import com.example.smartgardenplanner.core.WeedMaskGeometryEngine
import com.example.smartgardenplanner.core.DistanceFormatter
import com.example.smartgardenplanner.core.DistanceUnit
import com.example.smartgardenplanner.core.IrrigationRouteCalculator
import com.example.smartgardenplanner.core.CompanionPlantingValidator
import com.example.smartgardenplanner.core.Feature
import com.example.smartgardenplanner.core.currentAppTier
import com.example.smartgardenplanner.core.GerminationContingencyEngine
import com.example.smartgardenplanner.ui.VegetableColorPalette
import com.example.smartgardenplanner.core.CropReference
import com.example.smartgardenplanner.core.GuildCatalog
import com.example.smartgardenplanner.core.HardinessZones
import com.example.smartgardenplanner.core.PlotContext
import com.example.smartgardenplanner.core.PlotGeometry
import com.example.smartgardenplanner.core.PlotPoint
import com.example.smartgardenplanner.core.PlotShape
import com.example.smartgardenplanner.core.Recommendation
import com.example.smartgardenplanner.core.RecommendationEngine
import com.example.smartgardenplanner.core.SiteFeatureEntity
import com.example.smartgardenplanner.core.SiteFeatureType
import com.example.smartgardenplanner.core.SunlightEngine
import com.example.smartgardenplanner.core.VendorRegistry
import com.example.smartgardenplanner.data.PlotInsightsLoader

import com.example.smartgardenplanner.data.AppDatabase
import com.example.smartgardenplanner.data.DataUnreadableException
import androidx.room.withTransaction

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Navigation state enum to route between the screens described in the IDD
// [FIXED] ENCYCLOPEDIA added — this was documented as Screen Node 4 (IDD/ICD, ConOps) but had
// never actually been added to this enum, so the 4th screen simply didn't exist in the app.
enum class SgpScreen {
    DASHBOARD,
    CREATOR,
    CANVAS,
    ENCYCLOPEDIA,
    SETTINGS,
    INSIGHTS // plot insights: site, harmony, suggestions, care, food (roadmap features)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Required behaviour at targetSdk 35+; every screen uses Scaffold, which applies the insets.
        enableEdgeToEdge()

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
                    AppRoot()
                }
            }
        }
    }
}

/** Long-lived services, created once at start-up off the main thread (DW-0606). */
class AppServices(
    val database: AppDatabase,
    val auditLogger: SecurityAuditLogger,
    val sensorEngine: SensorMeasurementEngine
)

private sealed interface StartupState {
    data object Loading : StartupState
    data class Ready(val services: AppServices) : StartupState
    /** The stored data can't be decrypted on this device, e.g. after restoring a backup (T2-SEC-050). */
    data object Unreadable : StartupState
    data class Failed(val message: String) : StartupState
}

/**
 * Keystore set-up, database opening and first-run seeding. These used to run on the main thread in
 * onCreate(), which blocked the first frame (StrongBox key generation and SQLCipher key derivation are slow).
 */
private suspend fun startServices(context: Context): AppServices = withContext(Dispatchers.IO) {
    val keyManager = RealSecurityKeyManager()
    keyManager.initializeKeyStore()
    val auditLogger = SecurityAuditLogger(context)
    val database = AppDatabase.getInstance(context, keyManager)

    if (database.seedDao().count() == 0) {
        val basicSeeds = com.example.smartgardenplanner.data.SeedCatalogLoader(context)
            .loadTier(com.example.smartgardenplanner.data.CatalogTier.BASIC)
        database.seedDao().insertAll(basicSeeds)
        auditLogger.appendLog("DATABASE_INIT: Seed dictionary pre-populated (${basicSeeds.size} records, Basic tier).")
    }
    if (database.climateZoneDao().count() == 0) {
        database.climateZoneDao().insertAll(com.example.smartgardenplanner.data.SeedDataset.starterClimateZones)
    }

    // Keep the daily reminder job in step with the setting (FR-019).
    try {
        val startupSettings = com.example.smartgardenplanner.data.SettingsRepository(
            com.example.smartgardenplanner.data.SecurityRepositoryImpl(database.configDao())
        ).load()
        com.example.smartgardenplanner.data.CareReminderWorker.schedule(context, startupSettings.careRemindersEnabled)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        auditLogger.appendLog("REMINDERS: could not schedule (${e.javaClass.simpleName}).")
    }

    AppServices(database, auditLogger, SensorMeasurementEngine(context))
}

@Composable
fun AppRoot() {
    val appContext = LocalContext.current.applicationContext
    val activity = LocalContext.current as? Activity
    var state by remember { mutableStateOf<StartupState>(StartupState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(attempt) {
        state = try {
            StartupState.Ready(startServices(appContext))
        } catch (e: CancellationException) {
            throw e
        } catch (e: DataUnreadableException) {
            withContext(Dispatchers.IO) {
                SecurityAuditLogger(appContext).appendLog("KEY_LOSS: stored data unreadable on this device (${e.message})")
            }
            StartupState.Unreadable
        } catch (e: Exception) {
            StartupState.Failed(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""))
        }
    }

    when (val current = state) {
        StartupState.Loading -> Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        is StartupState.Ready -> AppNavigationContainer(
            sensorEngine = current.services.sensorEngine,
            database = current.services.database,
            auditLogger = current.services.auditLogger
        )
        StartupState.Unreadable -> DataUnreadableScreen(
            onStartEmpty = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabase.setAsideUnreadableData(appContext)
                        SecurityAuditLogger(appContext).appendLog("DATA_RESET: unreadable data set aside; starting with empty data")
                    }
                    state = StartupState.Loading
                    attempt++
                }
            },
            onClose = { activity?.finish() }
        )
        is StartupState.Failed -> StartupFailedScreen(
            message = current.message,
            onRetry = { state = StartupState.Loading; attempt++ }
        )
    }
}

/** Shown instead of crashing when the stored data can't be decrypted (T2-SEC-050, HLR-PROT-040). */
@Composable
fun DataUnreadableScreen(onStartEmpty: () -> Unit, onClose: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text("Your garden data can't be read on this device", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The saved data is encrypted with a key that isn't available on this phone. This usually " +
                "happens after restoring a backup onto a new or reset phone.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "You can start with empty data. The unreadable files are kept (renamed), not deleted.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = { confirming = true }, modifier = Modifier.fillMaxWidth()) { Text("Start with empty data") }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close app") }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Start with empty data?") },
            text = { Text("Your plots and plants won't be shown. The old files stay on the phone, renamed.") },
            confirmButton = { TextButton(onClick = { confirming = false; onStartEmpty() }) { Text("Start empty") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun StartupFailedScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text("The app couldn't start", style = MaterialTheme.typography.headlineSmall)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
    }
}

@Composable
fun AppNavigationContainer(
    sensorEngine: SensorMeasurementEngine,
    database: AppDatabase,
    auditLogger: SecurityAuditLogger
) {
    // Saved across rotation and process death (T2-ENV-010/020, partial until Phase 3).
    var currentScreen by rememberSaveable { mutableStateOf(SgpScreen.DASHBOARD) }
    var selectedPlotId by rememberSaveable { mutableStateOf(-1L) }

    // The system Back action returns to the plot list; on the plot list it leaves the app (T2-ENV-040).
    BackHandler(enabled = currentScreen != SgpScreen.DASHBOARD) {
        currentScreen = if (currentScreen == SgpScreen.INSIGHTS) SgpScreen.CANVAS else SgpScreen.DASHBOARD
    }

    when (currentScreen) {
        SgpScreen.DASHBOARD -> {
            DashboardScreen(
                database = database,
                onNavigateToCreator = { currentScreen = SgpScreen.CREATOR },
                onNavigateToEncyclopedia = { currentScreen = SgpScreen.ENCYCLOPEDIA },
                onNavigateToSettings = { currentScreen = SgpScreen.SETTINGS },
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
                onNavigateBack = { currentScreen = SgpScreen.DASHBOARD },
                onOpenInsights = { currentScreen = SgpScreen.INSIGHTS }
            )
        }
        SgpScreen.INSIGHTS -> {
            com.example.smartgardenplanner.ui.PlotInsightsScreen(
                plotId = selectedPlotId,
                database = database,
                onNavigateBack = { currentScreen = SgpScreen.CANVAS }
            )
        }
        SgpScreen.ENCYCLOPEDIA -> {
            com.example.smartgardenplanner.ui.EncyclopediaScreen(
                database = database,
                onNavigateBack = { currentScreen = SgpScreen.DASHBOARD }
            )
        }
        SgpScreen.SETTINGS -> {
            com.example.smartgardenplanner.ui.SettingsScreen(
                settingsRepository = com.example.smartgardenplanner.data.SettingsRepository(
                    com.example.smartgardenplanner.data.SecurityRepositoryImpl(database.configDao())
                ),
                database = database,
                onNavigateBack = { currentScreen = SgpScreen.DASHBOARD }
            )
        }
    }
}

// =====================================================================
// SCREEN NODE 1: DASHBOARD LANDING ROOT
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    database: AppDatabase,
    onNavigateToCreator: () -> Unit,
    onNavigateToEncyclopedia: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSelectPlot: (Long) -> Unit
) {
    var plotList by remember { mutableStateOf<List<PlotEntity>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var onlineOn by remember { mutableStateOf(false) }

    // Reads through the DAO. The previous version guessed table and column names with raw SQL (DW-0704).
    LaunchedEffect(Unit) {
        try {
            onlineOn = withContext(SgpExecutors.dbDispatcher) {
                com.example.smartgardenplanner.data.SettingsRepository(
                    com.example.smartgardenplanner.data.SecurityRepositoryImpl(database.configDao())
                ).load().onlineFeaturesEnabled
            }
            plotList = withContext(SgpExecutors.dbDispatcher) { database.plotDao().getAllPlots() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError = "Couldn't read your plots (${e.javaClass.simpleName}). Try again; if it keeps happening, restart the app."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart Garden Planner", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    com.example.smartgardenplanner.ui.OnlineBadge(onlineOn)
                    IconButton(onClick = onNavigateToEncyclopedia) {
                        Icon(Icons.Default.Search, contentDescription = "Botanical Encyclopedia", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Tune, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
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
            Text("Active Plots", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            loadError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

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
                                    "Physical Boundaries: ${plot.lengthM}m × ${plot.widthM}m" +
                                        (if (com.example.smartgardenplanner.core.PlotShape.outline(plot).isNotEmpty()) " • custom outline" else "") +
                                        (plot.hardinessZone?.let { " • zone $it" } ?: ""),
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

    // [NEW] Plot dimension bounds now come from Settings instead of being hardcoded — see
    // core/AppSettings.kt / ui/SettingsScreen.kt "Plot Validation" section.
    var settings by remember { mutableStateOf(com.example.smartgardenplanner.core.AppSettings.DEFAULT) }
    LaunchedEffect(Unit) {
        withContext(SgpExecutors.dbDispatcher) {
            settings = com.example.smartgardenplanner.data.SettingsRepository(
                com.example.smartgardenplanner.data.SecurityRepositoryImpl(database.configDao())
            ).load()
        }
    }
    val minDim = settings.minPlotDimensionM
    val maxDim = settings.maxPlotDimensionM
    // [NEW] Bounds shown/validated in whatever unit is currently selected — the text fields hold
    // the raw typed value in that display unit; conversion back to meters happens only once, at
    // the point of actually saving the plot (see the Initialize Workspace button below).
    val minDimDisplay = com.example.smartgardenplanner.core.DistanceFormatter.metersToDisplay(minDim, settings.distanceUnit)
    val maxDimDisplay = com.example.smartgardenplanner.core.DistanceFormatter.metersToDisplay(maxDim, settings.distanceUnit)
    val unitSuffix = settings.distanceUnit.suffix

    val regexValidator = remember { Regex("^[0-9]+(\\.[0-9]+)?$") }
    val creatorScope = rememberCoroutineScope()
    var saveError by remember { mutableStateOf<String?>(null) }

    val isInputValid = plotName.isNotBlank() &&
            plotLength.matches(regexValidator) &&
            plotWidth.matches(regexValidator) &&
            (plotLength.toFloatOrNull() ?: 0f) in minDimDisplay..maxDimDisplay &&
            (plotWidth.toFloatOrNull() ?: 0f) in minDimDisplay..maxDimDisplay

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
                label = { Text("Real-World Length Dimension ($unitSuffix)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                isError = plotLength.isNotEmpty() && (!plotLength.matches(regexValidator) || (plotLength.toFloatOrNull() ?: 0f) < minDimDisplay || (plotLength.toFloatOrNull() ?: 0f) > maxDimDisplay)
            )

            OutlinedTextField(
                value = plotWidth,
                onValueChange = { newValue -> plotWidth = newValue },
                label = { Text("Real-World Width Dimension ($unitSuffix)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                isError = plotWidth.isNotEmpty() && (!plotWidth.matches(regexValidator) || (plotWidth.toFloatOrNull() ?: 0f) < minDimDisplay || (plotWidth.toFloatOrNull() ?: 0f) > maxDimDisplay)
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

            saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

            Button(
                onClick = {
                    if (isInputValid) {
                        creatorScope.launch {
                            try {
                                // Text fields hold the typed value in the display unit; storage is always meters.
                                val lengthMeters = com.example.smartgardenplanner.core.DistanceFormatter.parseToMeters(plotLength, settings.distanceUnit)
                                val widthMeters = com.example.smartgardenplanner.core.DistanceFormatter.parseToMeters(plotWidth, settings.distanceUnit)
                                if (lengthMeters == null || widthMeters == null) return@launch
                                val now = System.currentTimeMillis()
                                val plotId = withContext(SgpExecutors.dbDispatcher) {
                                    database.plotDao().insert(
                                        PlotEntity(
                                            name = plotName.trim(),
                                            lengthM = lengthMeters,
                                            widthM = widthMeters,
                                            createdTimestamp = now,
                                            lastModifiedTimestamp = now
                                        )
                                    )
                                }
                                onWorkspaceInitialized(plotId)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                saveError = "Couldn't save the plot. Nothing was changed. (${e.javaClass.simpleName})"
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

// [FIXED] This must sit above the @OptIn/@Composable annotations, not between them and the
// function — Kotlin attaches the nearest annotations to the nearest following declaration, so
// having the enum in between caused @Composable to attach to the enum (invalid target) and left
// CanvasWorkspaceScreen itself unannotated, which cascaded into every composable call inside it
// failing to compile.
/**
 * [NEW] Proper circle-vs-rectangle intersection test (closest-point method), used to fix a real
 * bug: the previous path-zone exclusion check only tested whether a node's bare CENTER point
 * fell inside a path rectangle, ignoring the node's own exclusion radius entirely. A large-radius
 * plant placed just outside a path's edge could still visually overlap into it, since its radius
 * extends past its center — confirmed in a screenshot showing exactly this overlap.
 */
private fun circleIntersectsRect(cx: Float, cy: Float, radius: Float, rectX: Float, rectY: Float, rectW: Float, rectH: Float): Boolean {
    val closestX = cx.coerceIn(rectX, rectX + rectW)
    val closestY = cy.coerceIn(rectY, rectY + rectH)
    val dx = cx - closestX
    val dy = cy - closestY
    return (dx * dx + dy * dy) < (radius * radius)
}

/**
 * [NEW — FR-001] Standard ray-casting point-in-polygon test, used to fill a custom-shaped
 * (non-rectangular) auto-populate area: candidates are first generated across the polygon's
 * bounding rectangle (reusing the existing rectangle-fill engine unchanged), then filtered down
 * to only the ones actually inside the drawn shape via this test.
 */
private fun pointInPolygon(px: Float, py: Float, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        if ((pi.y > py) != (pj.y > py) &&
            px < (pj.x - pi.x) * (py - pi.y) / (pj.y - pi.y) + pi.x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

enum class CanvasMode { PLACE_NODE, DRAW_PATH, SELECT_AREA, OUTLINE, SITE_AREA, BARRIER } // OUTLINE: FR-002, SITE_AREA: FR-003/004/005, BARRIER: FR-006
enum class AreaSelectSubMode { RECTANGLE, POLYGON } // [NEW — FR-001]
enum class PathDrawSubMode { RECTANGLE, POINTS }

/** [NEW] Unified undo/redo snapshot covering BOTH nodes and path zones together. Previously
 * undo/redo only tracked planted nodes — drawing or deleting a path zone never pushed anything,
 * so Undo silently did nothing for path edits. Two independent stacks (one per entity type)
 * driven by a single pair of Undo/Redo buttons would itself be ambiguous (which stack should
 * "Undo" advance if the last action was a path draw vs. a node placement?), so this snapshots
 * both together as one atomic unit of history. */
data class CanvasSnapshot(val nodes: List<PlantedNodeEntity>, val paths: List<PathZoneEntity>)

/** [NEW] "x1,y1;x2,y2;..." <-> List<Offset> (meters) for POLYLINE path zones. */
private fun parsePoints(json: String?): List<Offset> {
    if (json.isNullOrBlank()) return emptyList()
    return json.split(";").mapNotNull { pair ->
        val parts = pair.split(",")
        if (parts.size == 2) {
            val x = parts[0].toFloatOrNull()
            val y = parts[1].toFloatOrNull()
            if (x != null && y != null) Offset(x, y) else null
        } else null
    }
}

private fun serializePoints(points: List<Offset>): String {
    return points.joinToString(";") { "${it.x},${it.y}" }
}

/** Minimum distance (meters) from a point to the nearest segment of a polyline. */
private fun distanceToPolyline(px: Float, py: Float, points: List<Offset>): Float {
    if (points.isEmpty()) return Float.MAX_VALUE
    if (points.size == 1) {
        val dx = px - points[0].x
        val dy = py - points[0].y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    var minDist = Float.MAX_VALUE
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lengthSq = abx * abx + aby * aby
        val t = if (lengthSq == 0f) 0f else (((px - a.x) * abx + (py - a.y) * aby) / lengthSq).coerceIn(0f, 1f)
        val closestX = a.x + t * abx
        val closestY = a.y + t * aby
        val dx = px - closestX
        val dy = py - closestY
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist < minDist) minDist = dist
    }
    return minDist
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasWorkspaceScreen(
    plotId: Long,
    database: AppDatabase,
    sensorEngine: SensorMeasurementEngine,
    onNavigateBack: () -> Unit,
    onOpenInsights: () -> Unit = {}
) {
    // [REWRITTEN] This screen previously drove every read/write through hand-rolled raw SQL that
    // re-guessed table/column names on every single operation. This uses the real Room DAOs
    // (PlotDao/PlantedNodeDao/SeedDao/PathZoneDao).
    var activePlot by remember { mutableStateOf<PlotEntity?>(null) }
    var nodesState by remember { mutableStateOf<List<PlantedNodeEntity>>(emptyList()) }
    var pathZonesState by remember { mutableStateOf<List<PathZoneEntity>>(emptyList()) }
    var seedDictionary by remember { mutableStateOf<List<SeedEntity>>(emptyList()) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    // [FIXED] Was BoundedHistoryStack<List<PlantedNodeEntity>> — nodes only. Now snapshots both
    // nodes and path zones together so Undo/Redo works uniformly for every canvas edit.
    val undoStack = remember { BoundedHistoryStack<CanvasSnapshot>(25) }
    val redoStack = remember { BoundedHistoryStack<CanvasSnapshot>(25) }

    var activeSeedCode by remember { mutableStateOf("SOL-LYC") } // Default to Tomato
    var canvasMode by remember { mutableStateOf(CanvasMode.PLACE_NODE) }
    var pathSubMode by remember { mutableStateOf(PathDrawSubMode.RECTANGLE) } // [NEW]
    var areaSubMode by remember { mutableStateOf(AreaSelectSubMode.RECTANGLE) } // [NEW — FR-001]
    var showOptionsMenu by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var pendingAreaSelection by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var pendingPolygonSelection by remember { mutableStateOf<List<Offset>?>(null) } // [NEW — FR-001]
    var germinationDialogNode by remember { mutableStateOf<PlantedNodeEntity?>(null) }
    var infoDialogNode by remember { mutableStateOf<PlantedNodeEntity?>(null) }
    var changeVarietyNode by remember { mutableStateOf<PlantedNodeEntity?>(null) }
    var inProgressPoints by remember { mutableStateOf<List<Offset>>(emptyList()) } // [NEW] points-mode path being drawn
    var pendingPolylineWidth by remember { mutableStateOf(false) } // [NEW] show width dialog after "Finish Path"
    var editingPathZone by remember { mutableStateOf<PathZoneEntity?>(null) } // [NEW] tap-to-edit existing path
    var showVarietyPicker by remember { mutableStateOf(false) } // [NEW] 3-step Category -> Species -> Cultivar picker
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Site conditions and outline (FR-002 to FR-006).
    var siteFeatures by remember { mutableStateOf<List<SiteFeatureEntity>>(emptyList()) }
    var siteAreaType by remember { mutableStateOf(SiteFeatureType.FULL_SUN) }
    var barrierType by remember { mutableStateOf(SiteFeatureType.TREE) }
    var pendingSiteShape by remember { mutableStateOf<List<Offset>?>(null) }
    var editingSiteFeature by remember { mutableStateOf<SiteFeatureEntity?>(null) }
    var showSiteFeatures by remember { mutableStateOf(true) }
    var showShade by remember { mutableStateOf(false) }
    var shadeGrid by remember { mutableStateOf<Pair<Int, FloatArray>?>(null) }

    // Back while path/area points are being drawn asks before discarding them (T2-ENV-040).
    BackHandler(enabled = inProgressPoints.isNotEmpty()) { showDiscardDialog = true }

    // [NEW] Drag-to-reposition support. Locked (off) by default, as requested, so it can't cause
    // an accidental move — must be explicitly enabled via the lock/unlock button in the top bar.
    var moveModeEnabled by remember { mutableStateOf(false) }
    var draggingNodeId by remember { mutableStateOf<Long?>(null) }
    var dragPreviewOffset by remember { mutableStateOf<Offset?>(null) }

    // [NEW] Overlay toggles for the weed mask and irrigation route engines — both were fully
    // implemented and unit-tested but had literally nothing in the UI calling them.
    var showWeedMask by remember { mutableStateOf(false) }
    var showIrrigationRoute by remember { mutableStateOf(false) }
    val weedMaskEngine = remember { WeedMaskGeometryEngine() }
    val irrigationEngine = remember { IrrigationRouteCalculator() }

    // [NEW] Loaded settings — see core/AppSettings.kt / data/SettingsRepository.kt / ui/SettingsScreen.kt.
    var settings by remember { mutableStateOf(com.example.smartgardenplanner.core.AppSettings.DEFAULT) }
    val settingsRepository = remember {
        com.example.smartgardenplanner.data.SettingsRepository(
            com.example.smartgardenplanner.data.SecurityRepositoryImpl(database.configDao())
        )
    }

    // [NEW] Zoom/pan. Same mutual-exclusion pattern as Move Mode, deliberately — layering a
    // simultaneous pinch/pan detector on top of the existing single-touch tap/drag detectors
    // is a well-known source of gesture-conflict bugs in Compose that would only surface at
    // runtime, not at compile time. A dedicated toggle sidesteps that risk entirely.
    var zoomPanModeEnabled by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableStateOf(1f) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Every database write from this screen goes through here, so a storage failure shows a message
    // instead of crashing the app (T2-ERR-010 / DW-0705).
    fun launchSafely(block: suspend CoroutineScope.() -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarMessage = "Couldn't save the change (${e.javaClass.simpleName}). Reopen the plot to see what was saved."
            }
        }
    }

    // Replaces the plot's plants and paths with a snapshot in one transaction, keeping row ids (DW-0802).
    suspend fun restoreSnapshot(snapshot: CanvasSnapshot) {
        withContext(SgpExecutors.dbDispatcher) {
            database.withTransaction {
                database.plantedNodeDao().deleteAllForPlot(plotId)
                if (snapshot.nodes.isNotEmpty()) database.plantedNodeDao().insertAll(snapshot.nodes)
                database.pathZoneDao().deleteAllForPlot(plotId)
                if (snapshot.paths.isNotEmpty()) database.pathZoneDao().insertAll(snapshot.paths)
            }
        }
    }
    val validator = remember { CompanionPlantingValidator() }
    // [NEW — FR-012 defense-in-depth] Same tier gate as the Settings UI, applied here too: if the
    // stored setting is "off" but the current tier isn't Pro (e.g. downgraded after previously
    // disabling it on Pro), companion rules stay enforced regardless of the stored value.
    val effectiveEnforceCompanionRules = if (Feature.isEnabled(Feature.COMPANION_RULE_TOGGLE, settings.currentAppTier())) {
        settings.enforceCompanionAntagonistRules
    } else {
        true
    }
    val germinationEngine = remember { GerminationContingencyEngine() }
    val autoPopulateEngine = remember { AutoPopulateEngine() }

    val seedMap = remember(seedDictionary) { seedDictionary.associateBy { it.botanicalCode } }
    fun seedFor(code: String): SeedEntity? = seedMap[code]

    // Guilds apply only when switched on and the tier allows it (FR-009).
    val activeGuilds = PlotInsightsLoader.activeGuilds(settings)

    fun plotContext(plot: PlotEntity, nodes: List<PlantedNodeEntity> = nodesState): PlotContext =
        PlotContext(plot, nodes, siteFeatures, { code -> seedFor(code) }, activeGuilds, effectiveEnforceCompanionRules,
            SunlightEngine.dayOfYear(System.currentTimeMillis()))

    // FR-010: grey out varieties that clash with what's planted, or won't survive the zone (Standard).
    val pickerConflict: (SeedEntity) -> String? = { candidate ->
        val plot = activePlot
        if (plot != null && Feature.isEnabled(Feature.GREY_OUT_INCOMPATIBLE, settings.currentAppTier())) {
            // One node per species is enough for the check and keeps the picker fast.
            RecommendationEngine.conflictReason(candidate, plotContext(plot, nodesState.distinctBy { it.seedCode.substringBefore("-") }))
        } else null
    }

    suspend fun reloadFeatures() {
        val list = database.siteFeatureDao().getByPlotId(plotId)
        withContext(Dispatchers.Main) { siteFeatures = list }
    }

    fun featureHit(f: SiteFeatureEntity, x: Float, y: Float): Boolean {
        val type = SiteFeatureType.of(f.featureType) ?: return false
        val pts = PlotGeometry.parsePoints(f.pointsJson)
        return when {
            type.isArea -> PlotGeometry.pointInPolygon(x, y, pts)
            type == SiteFeatureType.TREE -> PlotGeometry.distanceToPolyline(x, y, pts) < maxOf(f.radiusM, 0.4f)
            else -> PlotGeometry.distanceToPolyline(x, y, pts) < 0.4f
        }
    }

    fun toPlotPoints(points: List<Offset>) = points.map { PlotPoint(it.x, it.y) }

    fun saveOutline(points: List<Offset>?) {
        val plot = activePlot ?: return
        val outline = points?.let { toPlotPoints(it) }
        if (outline != null) {
            val problem = PlotGeometry.validateOutline(outline)
            if (problem != null) {
                snackbarMessage = problem
                return
            }
        }
        val updated = plot.copy(
            boundaryJson = outline?.let { PlotGeometry.serializePoints(it) },
            lastModifiedTimestamp = System.currentTimeMillis()
        )
        launchSafely {
            withContext(SgpExecutors.dbDispatcher) { database.plotDao().update(updated) }
            activePlot = updated
            val outside = nodesState.count { !PlotShape.contains(updated, it.coordinateXM, it.coordinateYM) }
            snackbarMessage = if (outline == null) "Outline reset to the full rectangle."
            else if (outside > 0) "Outline saved. $outside plant(s) are now outside it — see Plot insights → Harmony."
            else "Outline saved."
        }
    }

    suspend fun reloadNodes() {
        val list = database.plantedNodeDao().getByPlotId(plotId)
        withContext(Dispatchers.Main) { nodesState = list }
    }

    suspend fun reloadPaths() {
        val list = database.pathZoneDao().getByPlotId(plotId)
        withContext(Dispatchers.Main) { pathZonesState = list }
    }

    fun isInsidePath(xM: Float, yM: Float, extraRadiusM: Float): Boolean {
        return pathZonesState.any { zone ->
            if (zone.pathType == "POLYLINE") {
                distanceToPolyline(xM, yM, parsePoints(zone.pointsJson)) < (zone.widthM / 2f + extraRadiusM)
            } else {
                circleIntersectsRect(xM, yM, extraRadiusM, zone.xM, zone.yM, zone.widthM, zone.heightM)
            }
        }
    }

    // Initial load: real DAO reads instead of raw SQL with column-name guessing.
    LaunchedEffect(plotId) {
        withContext(SgpExecutors.dbDispatcher) {
            activePlot = database.plotDao().getById(plotId)
            val list = database.plantedNodeDao().getByPlotId(plotId)
            val paths = database.pathZoneDao().getByPlotId(plotId)
            val features = database.siteFeatureDao().getByPlotId(plotId)
            seedDictionary = database.seedDao().getAllSeeds()
            val loadedSettings = settingsRepository.load() // [NEW]

            withContext(Dispatchers.Main) {
                settings = loadedSettings // [NEW]
                undoStack.updateLimit(loadedSettings.undoHistoryDepth) // [NEW]
                redoStack.updateLimit(loadedSettings.undoHistoryDepth) // [NEW]
                nodesState = list
                pathZonesState = paths
                siteFeatures = features
                undoStack.clear()
                redoStack.clear()
                undoStack.push(CanvasSnapshot(list, paths))
            }
        }
    }

    // Estimated direct sun today across the plot (FR-006), computed off the main thread.
    LaunchedEffect(showShade, siteFeatures, activePlot) {
        val plot = activePlot
        shadeGrid = if (showShade && plot != null) {
            withContext(Dispatchers.Default) {
                val cols = 30
                val rows = (cols * plot.widthM / plot.lengthM).toInt().coerceIn(4, 60)
                val barriers = siteFeatures.mapNotNull { com.example.smartgardenplanner.core.Barrier.from(it) }
                cols to SunlightEngine.sunHoursGrid(
                    plot.lengthM, plot.widthM, cols, rows, plot.latitude ?: SunlightEngine.DEFAULT_LATITUDE,
                    SunlightEngine.dayOfYear(System.currentTimeMillis()), plot.northBearingDeg, barriers
                )
            }
        } else null
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
                title = { Text(activePlot?.name ?: "Loading Layout...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // [NEW] Move Mode lock/unlock — off (locked) by default so plants can never be
                    // repositioned by accident; must be explicitly enabled to drag-reposition.
                    IconButton(onClick = {
                        moveModeEnabled = !moveModeEnabled
                        if (moveModeEnabled) {
                            zoomPanModeEnabled = false // [NEW] mutually exclusive with Zoom/Pan
                            snackbarMessage = "Move Mode on — drag a plant to reposition it."
                        }
                    }) {
                        Icon(
                            if (moveModeEnabled) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = if (moveModeEnabled) "Move Mode: unlocked (drag plants to reposition)" else "Move Mode: locked",
                            tint = if (moveModeEnabled) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // [FIXED] The zoom +/-/reset buttons used to live here in the top bar, which meant
                    // the whole button row visibly shifted position every time the Reset button
                    // appeared/disappeared. Moved to a fixed floating cluster in the bottom-right
                    // corner of the canvas (Google Maps-style) below — this toggle now only switches
                    // the mode on/off and never changes size itself, so nothing here shifts.
                    IconButton(onClick = {
                        zoomPanModeEnabled = !zoomPanModeEnabled
                        if (zoomPanModeEnabled) {
                            moveModeEnabled = false // mutually exclusive with Move Mode
                            snackbarMessage = "Zoom/Pan mode on — use the on-screen +/- to zoom, drag to pan."
                        }
                    }) {
                        Icon(
                            Icons.Default.ZoomIn,
                            contentDescription = if (zoomPanModeEnabled) "Zoom/Pan: on" else "Zoom/Pan: off",
                            tint = if (zoomPanModeEnabled) Color(0xFF0EA5E9) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Default.Menu, contentDescription = "Canvas options")
                        }
                        DropdownMenu(expanded = showOptionsMenu, onDismissRequest = { showOptionsMenu = false }) {
                            Text("Canvas mode", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text(if (canvasMode == CanvasMode.PLACE_NODE) "✓ Place plants" else "Place plants") },
                                onClick = { canvasMode = CanvasMode.PLACE_NODE; showOptionsMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (canvasMode == CanvasMode.DRAW_PATH) "✓ Draw / edit no-plant path" else "Draw / edit no-plant path") },
                                onClick = { canvasMode = CanvasMode.DRAW_PATH; showOptionsMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (canvasMode == CanvasMode.SELECT_AREA) "✓ Select area to auto-populate" else "Select area to auto-populate") },
                                onClick = { canvasMode = CanvasMode.SELECT_AREA; showOptionsMenu = false }
                            )
                            if (canvasMode == CanvasMode.DRAW_PATH) {
                                Divider()
                                Text("Path style", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text(if (pathSubMode == PathDrawSubMode.RECTANGLE) "✓ Straight (drag rectangle)" else "Straight (drag rectangle)") },
                                    onClick = { pathSubMode = PathDrawSubMode.RECTANGLE; inProgressPoints = emptyList(); showOptionsMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (pathSubMode == PathDrawSubMode.POINTS) "✓ Curved (tap points)" else "Curved (tap points)") },
                                    onClick = { pathSubMode = PathDrawSubMode.POINTS; showOptionsMenu = false }
                                )
                            }
                            // [NEW — FR-001] Area-select shape submenu, same pattern as Path style above.
                            if (canvasMode == CanvasMode.SELECT_AREA) {
                                Divider()
                                Text("Area shape", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text(if (areaSubMode == AreaSelectSubMode.RECTANGLE) "✓ Rectangle (drag)" else "Rectangle (drag)") },
                                    onClick = { areaSubMode = AreaSelectSubMode.RECTANGLE; inProgressPoints = emptyList(); showOptionsMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (areaSubMode == AreaSelectSubMode.POLYGON) "✓ Custom shape (tap points)" else "Custom shape (tap points)" + if (!Feature.isEnabled(Feature.POLYGON_AREA_SELECT, settings.currentAppTier())) " (Standard+)" else "") },
                                    onClick = {
                                        if (Feature.isEnabled(Feature.POLYGON_AREA_SELECT, settings.currentAppTier())) {
                                            areaSubMode = AreaSelectSubMode.POLYGON
                                        } else {
                                            snackbarMessage = "Custom-shaped areas need the Standard or Pro catalog tier (Settings → Catalog)."
                                        }
                                        showOptionsMenu = false
                                    }
                                )
                            }
                            Divider()
                            // Site tools (FR-002 to FR-006) and plot insights.
                            Text("Site tools", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            val tierNow = settings.currentAppTier()
                            fun lockLabel(f: Feature) = if (Feature.isEnabled(f, tierNow)) "" else " (${f.tierLabel}+)"
                            DropdownMenuItem(
                                text = { Text((if (canvasMode == CanvasMode.OUTLINE) "✓ " else "") + "Draw plot outline" + lockLabel(Feature.POLYGON_PLOT_SHAPE)) },
                                onClick = {
                                    if (Feature.isEnabled(Feature.POLYGON_PLOT_SHAPE, tierNow)) {
                                        canvasMode = CanvasMode.OUTLINE; inProgressPoints = emptyList()
                                        snackbarMessage = "Tap the plot's corners in order, then Finish Outline."
                                    } else snackbarMessage = "Custom plot outlines need the Pro catalog tier (Settings → Catalog)."
                                    showOptionsMenu = false
                                }
                            )
                            if (activePlot?.boundaryJson != null) {
                                DropdownMenuItem(text = { Text("Reset outline to rectangle") }, onClick = { saveOutline(null); showOptionsMenu = false })
                            }
                            DropdownMenuItem(
                                text = { Text((if (canvasMode == CanvasMode.SITE_AREA) "✓ " else "") + "Mark sun / shade / flood / slope area" + lockLabel(Feature.SUN_SHADE_ZONES)) },
                                onClick = {
                                    if (Feature.isEnabled(Feature.SUN_SHADE_ZONES, tierNow)) {
                                        canvasMode = CanvasMode.SITE_AREA; inProgressPoints = emptyList()
                                    } else snackbarMessage = "Site areas need the Standard catalog tier (Settings → Catalog)."
                                    showOptionsMenu = false
                                }
                            )
                            if (canvasMode == CanvasMode.SITE_AREA) {
                                SiteFeatureType.entries.filter { it.isArea }.forEach { type ->
                                    val needed = when (type) {
                                        SiteFeatureType.FLOOD -> Feature.FLOODING_ZONES
                                        SiteFeatureType.SLOPE -> Feature.SLOPE_CONFIGURATION
                                        else -> Feature.SUN_SHADE_ZONES
                                    }
                                    DropdownMenuItem(
                                        text = { Text("   " + (if (siteAreaType == type) "✓ " else "") + type.label + lockLabel(needed), fontSize = 13.sp) },
                                        onClick = {
                                            if (Feature.isEnabled(needed, tierNow)) { siteAreaType = type; inProgressPoints = emptyList() }
                                            else snackbarMessage = "${type.label} areas need the ${needed.tierLabel} catalog tier."
                                            showOptionsMenu = false
                                        }
                                    )
                                }
                            }
                            DropdownMenuItem(
                                text = { Text((if (canvasMode == CanvasMode.BARRIER) "✓ " else "") + "Place tree / fence / wall / building" + lockLabel(Feature.SUNLIGHT_BARRIERS)) },
                                onClick = {
                                    if (Feature.isEnabled(Feature.SUNLIGHT_BARRIERS, tierNow)) {
                                        canvasMode = CanvasMode.BARRIER; inProgressPoints = emptyList()
                                    } else snackbarMessage = "Sunlight barriers need the Pro catalog tier (Settings → Catalog)."
                                    showOptionsMenu = false
                                }
                            )
                            if (canvasMode == CanvasMode.BARRIER) {
                                SiteFeatureType.entries.filter { it.isBarrier }.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text("   " + (if (barrierType == type) "✓ " else "") + type.label, fontSize = 13.sp) },
                                        onClick = { barrierType = type; inProgressPoints = emptyList(); showOptionsMenu = false }
                                    )
                                }
                            }
                            DropdownMenuItem(text = { Text("Plot insights (site, harmony, care, food)…") }, onClick = { showOptionsMenu = false; onOpenInsights() })
                            Divider()
                            // [NEW] Wires WeedMaskGeometryEngine and IrrigationRouteCalculator into the
                            // UI for the first time — both existed as tested engines with nothing calling them.
                            Text("Overlays", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text(if (showSiteFeatures) "✓ Show site areas and barriers" else "Show site areas and barriers") },
                                onClick = { showSiteFeatures = !showSiteFeatures; showOptionsMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text((if (showShade) "✓ " else "") + "Show estimated shade (today)" + lockLabel(Feature.SUNLIGHT_BARRIERS)) },
                                onClick = {
                                    if (Feature.isEnabled(Feature.SUNLIGHT_BARRIERS, tierNow)) showShade = !showShade
                                    else snackbarMessage = "Shade estimates need the Pro catalog tier (Settings → Catalog)."
                                    showOptionsMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (showWeedMask) "✓ Show weed-risk mask" else "Show weed-risk mask") },
                                onClick = { showWeedMask = !showWeedMask; showOptionsMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (showIrrigationRoute) "✓ Show irrigation route" else "Show irrigation route") },
                                onClick = { showIrrigationRoute = !showIrrigationRoute; showOptionsMenu = false }
                            )
                            Divider()
                            // [FIXED] Was seedDictionary.forEach over the entire catalog — with up to
                            // 2,936 possible entries, an exhaustive color legend is both unusable and
                            // meaningless (far too many colors to visually distinguish anyway). Bounded
                            // to only the varieties actually placed on this plot so far, which is what
                            // a legend is actually useful for.
                            val placedSeedCodes = nodesState.map { it.seedCode }.distinct()
                            Text(
                                if (placedSeedCodes.isEmpty()) "Legend (nothing placed yet)" else "Legend (this plot)",
                                fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            placedSeedCodes.mapNotNull { seedFor(it) }.forEach { seed ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(10.dp).background(VegetableColorPalette.colorFor(seed), shape = androidx.compose.foundation.shape.CircleShape))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(seed.commonName, fontSize = 12.sp)
                                        }
                                    },
                                    onClick = { showOptionsMenu = false }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (undoStack.size() > 1) {
                                val history = undoStack.toList()
                                val current = history[history.size - 1]
                                val previous = history[history.size - 2]
                                launchSafely {
                                    restoreSnapshot(previous)
                                    undoStack.pop()
                                    redoStack.push(current)
                                    nodesState = previous.nodes
                                    pathZonesState = previous.paths
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Undo", fontSize = 12.sp) }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = {
                            val nextState = redoStack.toList().lastOrNull()
                            if (nextState != null) {
                                launchSafely {
                                    restoreSnapshot(nextState)
                                    redoStack.pop()
                                    undoStack.push(nextState)
                                    nodesState = nextState.nodes
                                    pathZonesState = nextState.paths
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("Redo", fontSize = 12.sp) }
                }
            )
        }
    ) { paddingValues ->
        activePlot?.let { state: PlotEntity ->
            val headerSection: @Composable () -> Unit = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Plot Layout Size: ${DistanceFormatter.format(state.lengthM, settings.distanceUnit)} × ${DistanceFormatter.format(state.widthM, settings.distanceUnit)}",
                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // [NEW] Quick unit toggle right here — no need to open Settings just to
                            // switch between meters and inches.
                            Text(
                                if (settings.distanceUnit == DistanceUnit.METERS) "[in]" else "[m]",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    val newUnit = if (settings.distanceUnit == DistanceUnit.METERS) DistanceUnit.INCHES else DistanceUnit.METERS
                                    val updated = settings.copy(distanceUnit = newUnit)
                                    settings = updated
                                    launchSafely { withContext(SgpExecutors.dbDispatcher) { settingsRepository.save(updated) } }
                                }
                            )
                        }
                        Text("Active Plantings: ${nodesState.size} nodes placed", color = Color.Gray, fontSize = 11.sp)
                        // FR-009: highly visible guild state; tap to switch (Pro).
                        if (Feature.isEnabled(Feature.INTERPLANTING_GUILDS, settings.currentAppTier())) {
                            val guildColor = if (settings.guildsEnabled) Color(0xFF10B981) else Color(0xFFF97316)
                            Text(
                                if (settings.guildsEnabled) "GUILDS ON — partners may be planted closer" else "GUILDS OFF — tap to turn on",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = guildColor,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .border(1.dp, guildColor, RoundedCornerShape(6.dp))
                                    .clickable {
                                        val updated = settings.copy(guildsEnabled = !settings.guildsEnabled)
                                        settings = updated
                                        launchSafely { withContext(SgpExecutors.dbDispatcher) { settingsRepository.save(updated) } }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        // [NEW] Prominent, unambiguous indicator of exactly what will be placed next —
                        // a real report showed the picker's highlight alone wasn't noticeable enough,
                        // leading to placements against the wrong (unintended) variety.
                        val active = seedFor(activeSeedCode)
                        if (canvasMode == CanvasMode.PLACE_NODE && active != null) {
                            Text("Now placing: ${active.commonName} (${DistanceFormatter.format(active.exclusionRadiusM, settings.distanceUnit)} radius)", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    AssistChip(
                        onClick = { showOptionsMenu = true },
                        label = {
                            Text(
                                when (canvasMode) {
                                    CanvasMode.PLACE_NODE -> "Placing plants"
                                    CanvasMode.DRAW_PATH -> if (pathSubMode == PathDrawSubMode.RECTANGLE) "Drawing straight path" else "Drawing curved path"
                                        CanvasMode.SELECT_AREA -> "Selecting area"
                                        CanvasMode.OUTLINE -> "Drawing plot outline"
                                        CanvasMode.SITE_AREA -> "Marking: ${siteAreaType.label}"
                                        CanvasMode.BARRIER -> "Placing: ${barrierType.label}"
                                    },
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }
            val varietyButton: @Composable () -> Unit = {
                if (canvasMode == CanvasMode.PLACE_NODE) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // [FIXED] Replaced the always-visible flat scrolling list — with the catalog now
                        // supporting up to 2,936 varieties (Pro tier), a flat list was no longer usable.
                        // This opens a 3-step Category -> Species -> Cultivar picker instead.
                        OutlinedButton(onClick = { showVarietyPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            val active = seedFor(activeSeedCode)
                            Text(if (active != null) "Change Variety (${active.commonName})" else "Choose a Variety to Place")
                        }
                    }
                }
            }

            val rulerGutterDp = 22.dp
            // The plot is drawn at one scale for both axes (px per metre), so circles stay round and
            // spacing looks the same across and down. The drawing area keeps the plot's aspect ratio
            // and is centred in whatever space is left (portrait or landscape).
            val canvasSection: @Composable (Modifier) -> Unit = { sectionModifier ->
                BoxWithConstraints(modifier = sectionModifier, contentAlignment = Alignment.Center) {
                    val plotAspect = state.lengthM.coerceAtLeast(0.01f) / state.widthM.coerceAtLeast(0.01f)
                    val availW = (maxWidth - rulerGutterDp).coerceAtLeast(1.dp)
                    val availH = (maxHeight - rulerGutterDp).coerceAtLeast(1.dp)
                    val (drawW, drawH) = if (availW / availH > plotAspect) {
                        Pair(availH * plotAspect, availH)
                    } else {
                        Pair(availW, availW / plotAspect)
                    }
                    Column(modifier = Modifier.size(drawW + rulerGutterDp, drawH + rulerGutterDp)) {
                        Row(modifier = Modifier.fillMaxWidth().height(rulerGutterDp)) {
                            Spacer(modifier = Modifier.width(rulerGutterDp))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRuler(axisLengthM = state.lengthM, canvasLengthPx = size.width, horizontal = true, tickIntervalM = settings.rulerTickIntervalM / zoomScale, fontSizePx = settings.rulerFontSizeSp * 2f, unit = settings.distanceUnit)
                                }
                            }
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Box(modifier = Modifier.width(rulerGutterDp).fillMaxHeight()) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRuler(axisLengthM = state.widthM, canvasLengthPx = size.height, horizontal = false, tickIntervalM = settings.rulerTickIntervalM / zoomScale, fontSizePx = settings.rulerFontSizeSp * 2f, unit = settings.distanceUnit)
                                }
                            }

                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                    .background(Color(0xFF070B14))
                                    // [FIXED] Real bug: the scroll modifiers used to live directly on
                                    // THIS BoxWithConstraints — the same one whose maxWidth/maxHeight
                                    // are used to compute the zoomed canvas size. Scrollable layouts
                                    // request relaxed/unbounded constraints in their scroll direction,
                                    // which corrupted that exact measurement the moment Zoom/Pan mode
                                    // turned on, producing a broken canvas size and pushing everything
                                    // (including every planted node) outside the visible viewport —
                                    // this is why plants appeared to "disappear" when zooming. Fix:
                                    // this outer Box no longer scrolls at all; only the inner Box
                                    // wrapping the Canvas below does, so this one's maxWidth/maxHeight
                                    // stay stable regardless of zoom/pan state.
                            ) {
                                val baseWidth = maxWidth
                                val baseHeight = maxHeight
                                // [NEW] Scrollable wrapper, separate from the measuring Box above —
                                // holds ONLY the Canvas, so panning never affects the hint text or the
                                // floating zoom controls below, which stay fixed in the viewport
                                // exactly like Google Maps' zoom controls do.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (zoomPanModeEnabled) {
                                                Modifier
                                                    .horizontalScroll(rememberScrollState())
                                                    .verticalScroll(rememberScrollState())
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                Canvas(
                                    modifier = Modifier
                                        .size(baseWidth * zoomScale, baseHeight * zoomScale)
                                        .pointerInput(canvasMode, pathSubMode, areaSubMode, moveModeEnabled, zoomPanModeEnabled) {
                                            // [FIXED] Gated on !moveModeEnabled: while Move Mode is on, this
                                            // tap detector steps aside entirely so it can't race the new drag
                                            // detector below for the same touch — the two are deliberately
                                            // mutually exclusive rather than both listening at once.
                                            if (canvasMode == CanvasMode.PLACE_NODE && !moveModeEnabled && !zoomPanModeEnabled) {
                                                detectTapGestures(
                                                    onDoubleTap = { offset: Offset ->
                                                        val realXM = (offset.x / size.width) * state.lengthM
                                                        val realYM = (offset.y / size.height) * state.widthM

                                                        val candidateSeed = seedFor(activeSeedCode)
                                                        if (candidateSeed == null) {
                                                            snackbarMessage = "Select a seed variety first."
                                                            return@detectTapGestures
                                                        }

                                                        if (!PlotShape.contains(state, realXM, realYM)) {
                                                            snackbarMessage = "That spot is outside the plot outline."
                                                            return@detectTapGestures
                                                        }

                                                        // FR-014: perennials that won't survive the zone are blocked.
                                                        if (HardinessZones.blocksPlacement(candidateSeed, state.hardinessZone)) {
                                                            snackbarMessage = HardinessZones.describe(candidateSeed, state.hardinessZone) ?: "Not hardy in this zone."
                                                            return@detectTapGestures
                                                        }

                                                        if (isInsidePath(realXM, realYM, candidateSeed.exclusionRadiusM)) {
                                                            snackbarMessage = "That spot overlaps a no-plant path."
                                                            return@detectTapGestures
                                                        }

                                                        val candidateNode = PlantedNodeEntity(
                                                            plotId = plotId,
                                                            seedCode = activeSeedCode,
                                                            coordinateXM = realXM,
                                                            coordinateYM = realYM
                                                        )
                                                        val result = validator.validatePlacement(candidateNode, candidateSeed, nodesState, { code -> seedFor(code) }, settings.spacingMarginMultiplier, effectiveEnforceCompanionRules, activeGuilds)

                                                        if (!result.isValid) {
                                                            val conflictId = (result.spacingViolations + result.antagonistViolations).firstOrNull()
                                                            val conflictNode = nodesState.find { it.id == conflictId }
                                                            val conflictSeed = conflictNode?.let { seedFor(it.seedCode) }
                                                            if (conflictNode != null && conflictSeed != null) {
                                                                val dx = candidateNode.coordinateXM - conflictNode.coordinateXM
                                                                val dy = candidateNode.coordinateYM - conflictNode.coordinateYM
                                                                val actualDist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                                                                val requiredDist = candidateSeed.exclusionRadiusM + conflictSeed.exclusionRadiusM
                                                                snackbarMessage = if (result.antagonistViolations.contains(conflictId)) {
                                                                    "${candidateSeed.commonName} avoids ${conflictSeed.commonName} nearby — move at least ${"%.1f".format(requiredDist * 2)}m away."
                                                                } else {
                                                                    "Too close to ${conflictSeed.commonName}: ${"%.2f".format(actualDist)}m apart, needs ${"%.2f".format(requiredDist)}m."
                                                                }
                                                            } else {
                                                                snackbarMessage = "Spacing conflict: too close to an existing plant."
                                                            }
                                                        } else {
                                                            val newNodes = nodesState + candidateNode
                                                            launchSafely {
                                                                withContext(SgpExecutors.dbDispatcher) { database.plantedNodeDao().insert(candidateNode) }
                                                                reloadNodes()
                                                                undoStack.push(CanvasSnapshot(nodesState, pathZonesState))
                                                                redoStack.clear()
                                                            }
                                                        }
                                                    },
                                                    onTap = { offset ->
                                                        val realXM = (offset.x / size.width) * state.lengthM
                                                        val realYM = (offset.y / size.height) * state.widthM
                                                        val tappedNode = nodesState.minByOrNull {
                                                            val dx = it.coordinateXM - realXM
                                                            val dy = it.coordinateYM - realYM
                                                            dx * dx + dy * dy
                                                        }
                                                        tappedNode?.let { node ->
                                                            val dx = (node.coordinateXM - realXM)
                                                            val dy = (node.coordinateYM - realYM)
                                                            val distM = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                                                            if (distM < 0.4) {
                                                                val seed = seedFor(node.seedCode)
                                                                if (seed != null && germinationEngine.isGerminationOverdue(node, seed)) {
                                                                    germinationDialogNode = node
                                                                } else {
                                                                    infoDialogNode = node
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                            } else if (canvasMode == CanvasMode.DRAW_PATH && pathSubMode == PathDrawSubMode.POINTS && !zoomPanModeEnabled) {
                                                // [NEW] Point-based curved path drawing: each tap adds a point;
                                                // tapping an existing path (when not building a new one) opens edit.
                                                detectTapGestures(
                                                    onTap = { offset ->
                                                        val realXM = (offset.x / size.width) * state.lengthM
                                                        val realYM = (offset.y / size.height) * state.widthM

                                                        if (inProgressPoints.isEmpty()) {
                                                            val tappedZone = pathZonesState.find { zone ->
                                                                if (zone.pathType == "POLYLINE") {
                                                                    distanceToPolyline(realXM, realYM, parsePoints(zone.pointsJson)) < (zone.widthM / 2f + 0.3f)
                                                                } else {
                                                                    realXM in zone.xM..(zone.xM + zone.widthM) && realYM in zone.yM..(zone.yM + zone.heightM)
                                                                }
                                                            }
                                                            if (tappedZone != null) {
                                                                editingPathZone = tappedZone
                                                                return@detectTapGestures
                                                            }
                                                        }
                                                        inProgressPoints = inProgressPoints + Offset(realXM, realYM)
                                                    }
                                                )
                                            } else if (canvasMode == CanvasMode.SELECT_AREA && areaSubMode == AreaSelectSubMode.POLYGON && !zoomPanModeEnabled) {
                                                // [NEW — FR-001] Point-based polygon area select, same interaction
                                                // pattern as the curved no-plant path above: tap to add points, an
                                                // explicit "Finish Area" button closes the loop once >=3 points exist.
                                                detectTapGestures(
                                                    onTap = { offset ->
                                                        val realXM = (offset.x / size.width) * state.lengthM
                                                        val realYM = (offset.y / size.height) * state.widthM
                                                        inProgressPoints = inProgressPoints + Offset(realXM, realYM)
                                                    }
                                                )
                                            } else if ((canvasMode == CanvasMode.OUTLINE || canvasMode == CanvasMode.SITE_AREA || canvasMode == CanvasMode.BARRIER) && !zoomPanModeEnabled) {
                                                // FR-002 to FR-006: tap corners/points. Tapping an existing area or barrier (before starting a new one) edits it.
                                                detectTapGestures(
                                                    onTap = { offset ->
                                                        val realXM = (offset.x / size.width) * state.lengthM
                                                        val realYM = (offset.y / size.height) * state.widthM
                                                        if (inProgressPoints.isEmpty() && canvasMode != CanvasMode.OUTLINE) {
                                                            val wantBarrier = canvasMode == CanvasMode.BARRIER
                                                            val hit = siteFeatures.firstOrNull { f -> SiteFeatureType.of(f.featureType)?.isBarrier == wantBarrier && featureHit(f, realXM, realYM) }
                                                            if (hit != null) {
                                                                editingSiteFeature = hit
                                                                return@detectTapGestures
                                                            }
                                                        }
                                                        if (canvasMode == CanvasMode.BARRIER && barrierType == SiteFeatureType.TREE) {
                                                            pendingSiteShape = listOf(Offset(realXM, realYM))
                                                        } else {
                                                            inProgressPoints = inProgressPoints + Offset(realXM, realYM)
                                                        }
                                                    }
                                                )
                                            } else if (canvasMode == CanvasMode.DRAW_PATH && pathSubMode == PathDrawSubMode.RECTANGLE && !zoomPanModeEnabled) {
                                                detectTapGestures(
                                                    onTap = { offset ->
                                                        val realXM = (offset.x / size.width) * state.lengthM
                                                        val realYM = (offset.y / size.height) * state.widthM
                                                        val tappedZone = pathZonesState.find { zone ->
                                                            zone.pathType != "POLYLINE" && realXM in zone.xM..(zone.xM + zone.widthM) && realYM in zone.yM..(zone.yM + zone.heightM)
                                                        }
                                                        if (tappedZone != null) editingPathZone = tappedZone
                                                    }
                                                )
                                            }
                                        }
                                        .pointerInput(canvasMode, pathSubMode, areaSubMode, zoomPanModeEnabled) {
                                            if (!zoomPanModeEnabled && ((canvasMode == CanvasMode.SELECT_AREA && areaSubMode == AreaSelectSubMode.RECTANGLE) || (canvasMode == CanvasMode.DRAW_PATH && pathSubMode == PathDrawSubMode.RECTANGLE))) {
                                                detectDragGestures(
                                                    onDragStart = { offset -> dragStart = offset; dragCurrent = offset },
                                                    onDrag = { change, _ -> dragCurrent = change.position },
                                                    onDragEnd = {
                                                        val start = dragStart
                                                        val end = dragCurrent
                                                        if (start != null && end != null) {
                                                            val xMin = min(start.x, end.x) / size.width * state.lengthM
                                                            val yMin = min(start.y, end.y) / size.height * state.widthM
                                                            val wM = kotlin.math.abs(end.x - start.x) / size.width * state.lengthM
                                                            val hM = kotlin.math.abs(end.y - start.y) / size.height * state.widthM

                                                            if (wM > 0.05f && hM > 0.05f) {
                                                                if (canvasMode == CanvasMode.DRAW_PATH) {
                                                                    val newZone = PathZoneEntity(plotId = plotId, xM = xMin, yM = yMin, widthM = wM, heightM = hM, pathType = "RECTANGLE")
                                                                    launchSafely {
                                                                        withContext(SgpExecutors.dbDispatcher) { database.pathZoneDao().insert(newZone) }
                                                                        reloadPaths()
                                                                        undoStack.push(CanvasSnapshot(nodesState, pathZonesState))
                                                                        redoStack.clear()
                                                                    }
                                                                } else if (canvasMode == CanvasMode.SELECT_AREA) {
                                                                    pendingAreaSelection = androidx.compose.ui.geometry.Rect(xMin, yMin, xMin + wM, yMin + hM)
                                                                }
                                                            }
                                                        }
                                                        dragStart = null
                                                        dragCurrent = null
                                                    }
                                                )
                                            }
                                        }
                                        // [NEW] Drag-to-reposition, active ONLY when Move Mode is explicitly
                                        // enabled — deliberately exclusive of the tap detector above so the
                                        // two never compete for the same touch sequence.
                                        .pointerInput(canvasMode, moveModeEnabled, zoomPanModeEnabled) {
                                            if (canvasMode == CanvasMode.PLACE_NODE && moveModeEnabled && !zoomPanModeEnabled) {
                                                detectDragGestures(
                                                    onDragStart = { offset ->
                                                        val realXM = (offset.x / size.width) * state.lengthM
                                                        val realYM = (offset.y / size.height) * state.widthM
                                                        val nearest = nodesState.minByOrNull {
                                                            val dx = it.coordinateXM - realXM
                                                            val dy = it.coordinateYM - realYM
                                                            dx * dx + dy * dy
                                                        }
                                                        nearest?.let { candidate ->
                                                            val dx = candidate.coordinateXM - realXM
                                                            val dy = candidate.coordinateYM - realYM
                                                            val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                                                            if (dist < 0.4) {
                                                                draggingNodeId = candidate.id
                                                                dragPreviewOffset = offset
                                                            }
                                                        }
                                                    },
                                                    onDrag = { change, _ ->
                                                        if (draggingNodeId != null) dragPreviewOffset = change.position
                                                    },
                                                    onDragEnd = {
                                                        val id = draggingNodeId
                                                        val preview = dragPreviewOffset
                                                        if (id != null && preview != null) {
                                                            val node = nodesState.find { it.id == id }
                                                            val seed = node?.let { seedFor(it.seedCode) }
                                                            if (node != null && seed != null) {
                                                                val realXM = (preview.x / size.width) * state.lengthM
                                                                val realYM = (preview.y / size.height) * state.widthM
                                                                if (!PlotShape.contains(state, realXM, realYM)) {
                                                                    snackbarMessage = "Can't move there — outside the plot."
                                                                } else if (isInsidePath(realXM, realYM, seed.exclusionRadiusM)) {
                                                                    snackbarMessage = "Can't move there — overlaps a no-plant path."
                                                                } else {
                                                                    val candidate = node.copy(coordinateXM = realXM, coordinateYM = realYM)
                                                                    val neighbors = nodesState.filter { it.id != id }
                                                                    val result = validator.validatePlacement(candidate, seed, neighbors, { code -> seedFor(code) }, settings.spacingMarginMultiplier, effectiveEnforceCompanionRules, activeGuilds)
                                                                    if (!result.isValid) {
                                                                        snackbarMessage = "Can't move there — too close to another plant."
                                                                    } else {
                                                                        launchSafely {
                                                                            withContext(SgpExecutors.dbDispatcher) { database.plantedNodeDao().update(candidate) }
                                                                            reloadNodes()
                                                                            undoStack.push(CanvasSnapshot(nodesState, pathZonesState))
                                                                            redoStack.clear()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        draggingNodeId = null
                                                        dragPreviewOffset = null
                                                    },
                                                    onDragCancel = {
                                                        draggingNodeId = null
                                                        dragPreviewOffset = null
                                                    }
                                                )
                                            }
                                        }
                                ) {
                                    val canvasW = size.width
                                    val canvasH = size.height
                                    val scaleX = canvasW / state.lengthM
                                    val scaleY = canvasH / state.widthM

                                    var gridX = 0f
                                    while (gridX < canvasW) {
                                        drawLine(color = Color(0xFF1E293B), start = Offset(gridX, 0f), end = Offset(gridX, canvasH), strokeWidth = 1f)
                                        gridX += scaleX
                                    }
                                    var gridY = 0f
                                    while (gridY < canvasH) {
                                        drawLine(color = Color(0xFF1E293B), start = Offset(0f, gridY), end = Offset(canvasW, gridY), strokeWidth = 1f)
                                        gridY += scaleY
                                    }

                                    // No-plant path zones
                                    pathZonesState.forEach { zone ->
                                        if (zone.pathType == "POLYLINE") {
                                            val points = parsePoints(zone.pointsJson).map { Offset(it.x * scaleX, it.y * scaleY) }
                                            val strokeWidthPx = zone.widthM * scaleX
                                            for (i in 0 until points.size - 1) {
                                                drawLine(
                                                    color = Color(0x8864748B), start = points[i], end = points[i + 1],
                                                    strokeWidth = strokeWidthPx, cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            }
                                        } else {
                                            val rectTopLeft = Offset(zone.xM * scaleX, zone.yM * scaleY)
                                            val rectSize = androidx.compose.ui.geometry.Size(zone.widthM * scaleX, zone.heightM * scaleY)
                                            drawRect(color = Color(0x552D3748), topLeft = rectTopLeft, size = rectSize)
                                            drawRect(color = Color(0xFF64748B), topLeft = rectTopLeft, size = rectSize, style = Stroke(width = 2f))
                                        }
                                    }

                                    // FR-006 overlay: estimated direct sun today. Darker = less sun.
                                    shadeGrid?.let { (cols, grid) ->
                                        val rows = grid.size / cols
                                        val cellW = canvasW / cols
                                        val cellH = canvasH / rows
                                        for (r in 0 until rows) for (c in 0 until cols) {
                                            val hours = grid[r * cols + c]
                                            val alpha = when {
                                                hours < 3f -> 0.55f
                                                hours < 6f -> 0.3f
                                                else -> 0f
                                            }
                                            if (alpha > 0f) {
                                                drawRect(Color.Black.copy(alpha = alpha), topLeft = Offset(c * cellW, r * cellH), size = androidx.compose.ui.geometry.Size(cellW + 1f, cellH + 1f))
                                            }
                                        }
                                    }

                                    // FR-003/004/005/006: marked site areas and shade-casting barriers.
                                    if (showSiteFeatures) {
                                        siteFeatures.forEach { f ->
                                            val type = SiteFeatureType.of(f.featureType) ?: return@forEach
                                            val pts = PlotGeometry.parsePoints(f.pointsJson).map { Offset(it.x * scaleX, it.y * scaleY) }
                                            if (pts.isEmpty()) return@forEach
                                            if (type.isArea && pts.size >= 3) {
                                                val (fill, edge) = when (type) {
                                                    SiteFeatureType.FULL_SUN -> Color(0x33FACC15) to Color(0xFFFACC15)
                                                    SiteFeatureType.PART_SHADE -> Color(0x3394A3B8) to Color(0xFF94A3B8)
                                                    SiteFeatureType.FULL_SHADE -> Color(0x66334155) to Color(0xFF64748B)
                                                    SiteFeatureType.FLOOD -> Color(0x443B82F6) to Color(0xFF3B82F6)
                                                    else -> Color(0x33A16207) to Color(0xFFD97706)
                                                }
                                                val areaPath = androidx.compose.ui.graphics.Path().apply {
                                                    moveTo(pts[0].x, pts[0].y)
                                                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                                                    close()
                                                }
                                                drawPath(areaPath, fill)
                                                drawPath(areaPath, edge, style = Stroke(width = 2f))
                                                if (type == SiteFeatureType.SLOPE) {
                                                    // Arrow pointing downhill, relative to the plot's orientation.
                                                    val cx = pts.map { it.x }.average().toFloat()
                                                    val cy = pts.map { it.y }.average().toFloat()
                                                    val rel = Math.toRadians((f.slopeDirectionDeg - (activePlot?.northBearingDeg ?: 0f)).toDouble())
                                                    val len = 40f
                                                    val tip = Offset(cx + (kotlin.math.sin(rel) * len).toFloat(), cy - (kotlin.math.cos(rel) * len).toFloat())
                                                    drawLine(edge, Offset(cx, cy), tip, strokeWidth = 4f)
                                                    drawCircle(edge, radius = 6f, center = tip)
                                                }
                                            } else if (type == SiteFeatureType.TREE) {
                                                drawCircle(Color(0x5522C55E), radius = f.radiusM.coerceAtLeast(0.2f) * scaleX, center = pts[0])
                                                drawCircle(Color(0xFF15803D), radius = f.radiusM.coerceAtLeast(0.2f) * scaleX, center = pts[0], style = Stroke(width = 2f))
                                                drawCircle(Color(0xFF78350F), radius = 7f, center = pts[0])
                                            } else if (type.isBarrier) {
                                                val barrierColor = when (type) {
                                                    SiteFeatureType.FENCE -> Color(0xFFA16207)
                                                    SiteFeatureType.WALL -> Color(0xFF9CA3AF)
                                                    else -> Color(0xFFE5E7EB)
                                                }
                                                val closed = type == SiteFeatureType.BUILDING && pts.size >= 3
                                                val segments = pts.size - 1 + (if (closed) 1 else 0)
                                                for (i in 0 until segments) {
                                                    drawLine(barrierColor, pts[i], pts[(i + 1) % pts.size], strokeWidth = 7f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                                }
                                            }
                                        }
                                    }

                                    // FR-002: dim everything outside a custom outline.
                                    activePlot?.let { plot ->
                                        val outline = PlotShape.outline(plot).map { Offset(it.x * scaleX, it.y * scaleY) }
                                        if (outline.size >= 3) {
                                            val outlinePath = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(outline[0].x, outline[0].y)
                                                for (i in 1 until outline.size) lineTo(outline[i].x, outline[i].y)
                                                close()
                                            }
                                            val outside = androidx.compose.ui.graphics.Path().apply {
                                                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                                                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, canvasW, canvasH))
                                                addPath(outlinePath)
                                            }
                                            drawPath(outside, Color(0xAA000000))
                                            drawPath(outlinePath, Color.White, style = Stroke(width = 3f))
                                        }
                                    }

                                    // [NEW] Weed-risk mask: everything NOT covered by a plant's spacing
                                    // radius, using the real Path.Op.DIFFERENCE geometry from
                                    // WeedMaskGeometryEngine (previously computed but never rendered).
                                    if (showWeedMask && nodesState.isNotEmpty()) {
                                        val circles = nodesState.map { node ->
                                            val seed = seedFor(node.seedCode)
                                            WeedMaskGeometryEngine.ExclusionCircle(
                                                centerXPx = node.coordinateXM * scaleX,
                                                centerYPx = node.coordinateYM * scaleY,
                                                radiusPx = (seed?.exclusionRadiusM ?: 0.5f) * scaleX
                                            )
                                        }
                                        val weedPath = weedMaskEngine.calculateResidualWeedZone(canvasW, canvasH, circles)
                                        drawPath(path = weedPath, color = Color(0x33EAB308))
                                    }

                                    // [NEW] Irrigation route: nearest-neighbor drip line connecting every
                                    // planted node, from IrrigationRouteCalculator (previously computed but
                                    // never rendered).
                                    if (showIrrigationRoute && nodesState.size >= 2) {
                                        val route = irrigationEngine.calculateDripRoute(nodesState)
                                        val screenRoute = route.map { Offset(it.xM * scaleX, it.yM * scaleY) }
                                        for (i in 0 until screenRoute.size - 1) {
                                            drawLine(
                                                color = Color(0xFF0EA5E9), start = screenRoute[i], end = screenRoute[i + 1],
                                                strokeWidth = 3f, cap = androidx.compose.ui.graphics.StrokeCap.Round
                                            )
                                        }
                                        screenRoute.forEach { drawCircle(color = Color(0xFF0EA5E9), radius = 4f, center = it) }
                                    }

                                    // In-progress polyline path being built
                                    if (inProgressPoints.isNotEmpty()) {
                                        val screenPoints = inProgressPoints.map { Offset(it.x * scaleX, it.y * scaleY) }
                                        for (i in 0 until screenPoints.size - 1) {
                                            drawLine(color = Color(0xFF10B981), start = screenPoints[i], end = screenPoints[i + 1], strokeWidth = 4f)
                                        }
                                        screenPoints.forEach { drawCircle(color = Color(0xFF10B981), radius = 6f, center = it) }
                                    }

                                    val start = dragStart
                                    val current = dragCurrent
                                    if (start != null && current != null) {
                                        val topLeft = Offset(min(start.x, current.x), min(start.y, current.y))
                                        val previewSize = androidx.compose.ui.geometry.Size(kotlin.math.abs(current.x - start.x), kotlin.math.abs(current.y - start.y))
                                        val previewColor = if (canvasMode == CanvasMode.DRAW_PATH) Color(0x8064748B) else Color(0x8010B981)
                                        drawRect(color = previewColor, topLeft = topLeft, size = previewSize, style = Stroke(width = 3f))
                                    }

                                    nodesState.forEach { node: PlantedNodeEntity ->
                                        // [NEW] While this specific node is being dragged, render it at the
                                        // live touch position instead of its stored coordinates, so the move
                                        // is visible in real time before it's committed on release.
                                        val isDragging = draggingNodeId == node.id
                                        val centerOffset = if (isDragging && dragPreviewOffset != null) {
                                            dragPreviewOffset!!
                                        } else {
                                            Offset(x = node.coordinateXM * scaleX, y = node.coordinateYM * scaleY)
                                        }
                                        val seed = seedFor(node.seedCode)
                                        val exclusionRadiusM = seed?.exclusionRadiusM ?: 0.5f
                                        val exclusionRadiusPx = exclusionRadiusM * scaleX
                                        val baseColor = VegetableColorPalette.colorFor(seed)
                                        val alpha = if (isDragging) 0.6f else 1.0f

                                        drawCircle(color = VegetableColorPalette.exclusionRingColorFor(seed).copy(alpha = VegetableColorPalette.exclusionRingColorFor(seed).alpha * alpha), radius = exclusionRadiusPx, center = centerOffset)
                                        drawCircle(color = baseColor.copy(alpha = 0.8f * alpha), radius = exclusionRadiusPx, center = centerOffset, style = Stroke(width = 2f))
                                        drawCircle(color = baseColor.copy(alpha = alpha), radius = 10f, center = centerOffset)

                                        if (!isDragging && seed != null && germinationEngine.isGerminationOverdue(node, seed)) {
                                            drawCircle(color = Color(0xFFEF4444), radius = 16f, center = centerOffset, style = Stroke(width = 3f))
                                        }
                                    }
                                }
                                } // closes the inner scrollable Box wrapping the Canvas

                                Column(modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)) {
                                    Text(
                                        text = when {
                                            zoomPanModeEnabled -> "Zoom/Pan Mode: use +/- to zoom, drag to pan • tap the zoom icon to turn this off (${"%.1f".format(zoomScale)}x)"
                                            canvasMode == CanvasMode.PLACE_NODE && moveModeEnabled -> "Move Mode: drag a plant to reposition it, or tap the lock icon to turn this off"
                                            canvasMode == CanvasMode.PLACE_NODE -> "Double-tap to plant • Tap an existing plant for details, editing, or recovery"
                                            canvasMode == CanvasMode.DRAW_PATH && pathSubMode == PathDrawSubMode.POINTS -> "Tap to add points • tap an existing path to edit it"
                                            canvasMode == CanvasMode.DRAW_PATH -> "Drag to mark a no-plant path • tap an existing path to edit it"
                                            canvasMode == CanvasMode.SELECT_AREA && areaSubMode == AreaSelectSubMode.POLYGON -> "Tap to add points (need at least 3) to outline a custom area"
                                            canvasMode == CanvasMode.OUTLINE -> "Tap the plot's corners in order (${inProgressPoints.size} so far, need 3+), then Finish Outline"
                                            canvasMode == CanvasMode.SITE_AREA -> "${siteAreaType.label}: tap corners (need 3+), then Finish • tap an existing area to edit it"
                                            canvasMode == CanvasMode.BARRIER && barrierType == SiteFeatureType.TREE -> "Tap where the tree trunk is • tap an existing barrier to edit it"
                                            canvasMode == CanvasMode.BARRIER -> "${barrierType.label}: tap points along it (2+), then Finish • tap an existing barrier to edit it"
                                            else -> "Drag to select an area to auto-populate"
                                        },
                                        color = if (zoomPanModeEnabled) Color(0xFF0EA5E9) else if (moveModeEnabled) Color(0xFFEF4444) else Color.LightGray, fontSize = 11.sp
                                    )
                                    if (canvasMode == CanvasMode.DRAW_PATH && pathSubMode == PathDrawSubMode.POINTS && inProgressPoints.size >= 2) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { pendingPolylineWidth = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                                Text("Finish Path", fontSize = 11.sp)
                                            }
                                            OutlinedButton(onClick = { inProgressPoints = emptyList() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                                Text("Cancel", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    // [NEW — FR-001] "Finish Area" appears once >=3 points exist, matching the
                                    // pattern's own described behavior: a 3rd point doesn't auto-close by
                                    // itself (a 4th+ tap keeps adding points instead), but the button is
                                    // available from that point on to close the loop whenever the user is done.
                                    val siteFinishReady = when (canvasMode) {
                                        CanvasMode.OUTLINE, CanvasMode.SITE_AREA -> inProgressPoints.size >= 3
                                        CanvasMode.BARRIER -> barrierType != SiteFeatureType.TREE && inProgressPoints.size >= 2
                                        else -> false
                                    }
                                    if (siteFinishReady) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    if (canvasMode == CanvasMode.OUTLINE) {
                                                        saveOutline(inProgressPoints)
                                                        if (PlotGeometry.validateOutline(toPlotPoints(inProgressPoints)) == null) {
                                                            inProgressPoints = emptyList()
                                                            canvasMode = CanvasMode.PLACE_NODE
                                                        }
                                                    } else {
                                                        pendingSiteShape = inProgressPoints
                                                        inProgressPoints = emptyList()
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text(if (canvasMode == CanvasMode.OUTLINE) "Finish Outline" else "Finish (${inProgressPoints.size} points)", fontSize = 11.sp)
                                            }
                                            OutlinedButton(onClick = { inProgressPoints = emptyList() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                                Text("Cancel", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    if (canvasMode == CanvasMode.SELECT_AREA && areaSubMode == AreaSelectSubMode.POLYGON && inProgressPoints.size >= 3) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    pendingPolygonSelection = inProgressPoints
                                                    inProgressPoints = emptyList()
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text("Finish Area (${inProgressPoints.size} points)", fontSize = 11.sp)
                                            }
                                            OutlinedButton(onClick = { inProgressPoints = emptyList() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                                Text("Cancel", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                // [NEW] Floating zoom controls, Google Maps-style — fixed position in
                                // the bottom-right corner of the canvas, never shifting other buttons.
                                if (zoomPanModeEnabled) {
                                    Column(
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (zoomScale != 1f) {
                                            FilledIconButton(
                                                onClick = { zoomScale = 1f },
                                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface)
                                            ) {
                                                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset zoom", tint = Color.White)
                                            }
                                        }
                                        Column(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        ) {
                                            IconButton(onClick = { zoomScale = (zoomScale + settings.zoomStep).coerceIn(settings.zoomMin, settings.zoomMax) }) {
                                                Icon(Icons.Default.Add, contentDescription = "Zoom in", tint = Color.White)
                                            }
                                            Divider(modifier = Modifier.width(24.dp))
                                            IconButton(onClick = { zoomScale = (zoomScale - settings.zoomStep).coerceIn(settings.zoomMin, settings.zoomMax) }) {
                                                Icon(Icons.Default.Remove, contentDescription = "Zoom out", tint = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
                if (maxWidth > maxHeight) {
                    // Landscape: canvas on the left, plot info and variety picker in a side panel.
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        canvasSection(Modifier.weight(1f).fillMaxHeight())
                        Column(
                            modifier = Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            headerSection()
                            varietyButton()
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        headerSection()
                        canvasSection(Modifier.weight(1f).fillMaxWidth())
                        varietyButton()
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard unfinished shape?") },
            text = { Text("The points you've tapped for this path or area will be lost.") },
            confirmButton = { TextButton(onClick = { inProgressPoints = emptyList(); showDiscardDialog = false }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("Keep drawing") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // FR-003 to FR-006: details for a new area/barrier, or edit/delete an existing one.
    pendingSiteShape?.let { shape ->
        val type = if (canvasMode == CanvasMode.BARRIER) barrierType else siteAreaType
        SiteFeatureDialog(
            initial = SiteFeatureEntity(
                plotId = plotId,
                featureType = type.name,
                pointsJson = PlotGeometry.serializePoints(toPlotPoints(shape)),
                heightM = if (type == SiteFeatureType.TREE) 6f else if (type.isBarrier) 1.8f else 0f,
                radiusM = if (type == SiteFeatureType.TREE) 2f else 0f
            ),
            isNew = true,
            onSave = { feature ->
                launchSafely {
                    withContext(SgpExecutors.dbDispatcher) { database.siteFeatureDao().insert(feature) }
                    reloadFeatures()
                }
                pendingSiteShape = null
            },
            onDelete = null,
            onDismiss = { pendingSiteShape = null }
        )
    }
    editingSiteFeature?.let { feature ->
        SiteFeatureDialog(
            initial = feature,
            isNew = false,
            onSave = { updated ->
                launchSafely {
                    withContext(SgpExecutors.dbDispatcher) { database.siteFeatureDao().update(updated) }
                    reloadFeatures()
                }
                editingSiteFeature = null
            },
            onDelete = {
                launchSafely {
                    withContext(SgpExecutors.dbDispatcher) { database.siteFeatureDao().delete(feature) }
                    reloadFeatures()
                }
                editingSiteFeature = null
            },
            onDismiss = { editingSiteFeature = null }
        )
    }

    // [NEW] Width-input dialog after finishing a points-mode path.
    if (pendingPolylineWidth) {
        var widthText by remember { mutableStateOf("0.5") }
        AlertDialog(
            onDismissRequest = { pendingPolylineWidth = false },
            title = { Text("Path Width") },
            text = {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { widthText = it },
                    label = { Text("Width (m)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val width = widthText.toFloatOrNull()?.takeIf { it > 0f } ?: 0.5f
                    val newZone = PathZoneEntity(
                        plotId = plotId, xM = 0f, yM = 0f, widthM = width, heightM = 0f,
                        pathType = "POLYLINE", pointsJson = serializePoints(inProgressPoints)
                    )
                    launchSafely {
                        withContext(SgpExecutors.dbDispatcher) { database.pathZoneDao().insert(newZone) }
                        reloadPaths()
                        undoStack.push(CanvasSnapshot(nodesState, pathZonesState))
                        redoStack.clear()
                    }
                    inProgressPoints = emptyList()
                    pendingPolylineWidth = false
                }) { Text("Save Path") }
            },
            dismissButton = { TextButton(onClick = { pendingPolylineWidth = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // [NEW] Edit an existing path zone — requested feature: "I should also be able to modify it
    // later when selected." Supports width adjustment (polyline) and delete (both types).
    editingPathZone?.let { zone ->
        var widthText by remember(zone.id) { mutableStateOf(zone.widthM.toString()) }
        AlertDialog(
            onDismissRequest = { editingPathZone = null },
            title = { Text(if (zone.pathType == "POLYLINE") "Edit Path" else "No-Plant Area") },
            text = {
                Column {
                    if (zone.pathType == "POLYLINE") {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { widthText = it },
                            label = { Text("Width (m)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("${"%.2f".format(zone.widthM)}m × ${"%.2f".format(zone.heightM)}m rectangle.")
                    }
                }
            },
            confirmButton = {
                if (zone.pathType == "POLYLINE") {
                    TextButton(onClick = {
                        val newWidth = widthText.toFloatOrNull()?.takeIf { it > 0f } ?: zone.widthM
                        val updated = zone.copy(widthM = newWidth)
                        launchSafely {
                            withContext(SgpExecutors.dbDispatcher) { database.pathZoneDao().update(updated) }
                            reloadPaths()
                            undoStack.push(CanvasSnapshot(nodesState, pathZonesState))
                            redoStack.clear()
                        }
                        editingPathZone = null
                    }) { Text("Save") }
                } else {
                    TextButton(onClick = { editingPathZone = null }) { Text("Close") }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        launchSafely {
                            withContext(SgpExecutors.dbDispatcher) { database.pathZoneDao().delete(zone) }
                            reloadPaths()
                            undoStack.push(CanvasSnapshot(nodesState, pathZonesState))
                            redoStack.clear()
                        }
                        editingPathZone = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) { Text("Delete") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    germinationDialogNode?.let { node ->
        val seed = seedFor(node.seedCode)
        if (seed != null) {
            val options = germinationEngine.buildContingencyOptions(seed, seedDictionary)
            AlertDialog(
                onDismissRequest = { germinationDialogNode = null },
                title = { Text("Germination Failed — Recovery Plan") },
                text = {
                    Column {
                        Text("This ${seed.commonName} was planted more than ${seed.germinationDays} days ago with no recorded germination. Choose how to reuse this spot:")
                        Spacer(modifier = Modifier.height(12.dp))
                        if (options.isEmpty()) {
                            Text("No fallback options are on file for this variety.", color = Color.Gray)
                        }
                        options.forEach { option ->
                            val label = when (option) {
                                is GerminationContingencyEngine.ContingencyOption.FastTrackVariety -> "Fast-track substitute: ${option.alternateCommonName}"
                                is GerminationContingencyEngine.ContingencyOption.NurseryTransplant -> "Restart as a nursery transplant (${seed.commonName})"
                                is GerminationContingencyEngine.ContingencyOption.CatchCrop -> "Alternate catch-crop: ${option.alternateCommonName}"
                            }
                            TextButton(
                                onClick = {
                                    val newSeedCode = when (option) {
                                        is GerminationContingencyEngine.ContingencyOption.FastTrackVariety -> option.alternateBotanicalCode
                                        is GerminationContingencyEngine.ContingencyOption.NurseryTransplant -> option.originalBotanicalCode
                                        is GerminationContingencyEngine.ContingencyOption.CatchCrop -> option.alternateBotanicalCode
                                    }
                                    val updated = node.copy(seedCode = newSeedCode, datePlantedEpochMillis = System.currentTimeMillis(), germinationFlagResolved = false)
                                    launchSafely {
                                        withContext(SgpExecutors.dbDispatcher) { database.plantedNodeDao().update(updated) }
                                        reloadNodes()
                                        undoStack.push(CanvasSnapshot(nodesState, pathZonesState))
                                        redoStack.clear()
                                    }
                                    germinationDialogNode = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(label, fontSize = 12.sp) }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val updated = node.copy(germinationFlagResolved = true)
                        launchSafely {
                            withContext(SgpExecutors.dbDispatcher) { database.plantedNodeDao().update(updated) }
                            reloadNodes()
                        }
                        germinationDialogNode = null
                    }) { Text("Keep as-is") }
                },
                dismissButton = { TextButton(onClick = { germinationDialogNode = null }) { Text("Close") } },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }

    infoDialogNode?.let { node ->
        val seed = seedFor(node.seedCode)
        AlertDialog(
            onDismissRequest = { infoDialogNode = null },
            title = { Text(seed?.commonName ?: node.seedCode) },
            text = {
                Column {
                    val plantedDate = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(node.datePlantedEpochMillis))
                    Text("Planted: $plantedDate")
                    if (seed != null) {
                        val harvestMillis = node.datePlantedEpochMillis + seed.daysToHarvest.toLong() * 86_400_000L
                        val harvestDate = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(harvestMillis))
                        Text("Expected harvest: ~$harvestDate")
                        Text("Spacing: ${seed.exclusionRadiusM}m")
                        val crop = CropReference.forSeed(seed)
                        if (crop.yieldKgPerPlant > 0f) Text("Typical yield: ~${"%.2f".format(crop.yieldKgPerPlant)} kg per plant")
                        crop.nutrients?.let { n -> Text("Per 100 g: ${n.energyKcal.toInt()} kcal, vit C ${n.vitaminCMg} mg, protein ${n.proteinG} g", fontSize = 12.sp) }
                        Text("${crop.feeding.label} • ${crop.sun.label} • pH ${crop.phMin}–${crop.phMax}", fontSize = 12.sp, color = Color.Gray)
                        HardinessZones.describe(seed, activePlot?.hardinessZone)?.let { Text(it, fontSize = 12.sp, color = Color(0xFFEAB308)) }
                        val guilds = GuildCatalog.guildsFor(seed, activeGuilds)
                        if (guilds.isNotEmpty()) Text("Guild: ${guilds.joinToString { it.name }}", fontSize = 12.sp, color = Color(0xFF10B981))
                        // FR-023/024: vendor slot (placeholder until a real vendor is linked).
                        val vendor = VendorRegistry.effectiveVendor(settings.preferredVendorId, Feature.isEnabled(Feature.VENDOR_TARGETING, settings.currentAppTier()))
                        val link = VendorRegistry.purchaseLink(seed, vendor)
                        Text(if (link != null) "Buy seeds: ${vendor.displayName}" else "Buy seeds: ${vendor.displayName} — links not available yet", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { infoDialogNode = null }) { Text("Close") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { changeVarietyNode = node; infoDialogNode = null }) { Text("Change Variety") }
                    TextButton(
                        onClick = {
                            launchSafely {
                                withContext(SgpExecutors.dbDispatcher) { database.plantedNodeDao().delete(node) }
                                reloadNodes()
                                undoStack.push(CanvasSnapshot(nodesState, pathZonesState))
                                redoStack.clear()
                            }
                            infoDialogNode = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                    ) { Text("Delete") }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // [NEW] 3-step variety picker for choosing what to place next.
    if (showVarietyPicker) {
        VarietyPickerDialog(
            seedDictionary = seedDictionary,
            conflictFor = pickerConflict,
            onSelect = { code ->
                activeSeedCode = code
                showVarietyPicker = false
            },
            onDismiss = { showVarietyPicker = false }
        )
    }

    changeVarietyNode?.let { node ->
        // [FIXED] Was its own flat LazyColumn over the entire seed dictionary — unusable now that
        // the catalog can hold up to 2,936 entries. Reuses the same 3-step picker, with the
        // validation logic (unchanged) now living in the onSelect callback instead of inline in a
        // list item's click handler.
        VarietyPickerDialog(
            seedDictionary = seedDictionary,
            conflictFor = pickerConflict,
            onSelect = { code ->
                val seed = seedFor(code) ?: return@VarietyPickerDialog
                // [FIXED] Previously committed a variety change with ZERO spacing/companion
                // validation against neighboring nodes — a real report: several marigolds
                // were changed to a tomato with no warning despite the new spacing very
                // likely conflicting with nearby plants. Now runs the same validator used
                // for new placements before allowing the change.
                val candidate = node.copy(seedCode = seed.botanicalCode)
                val neighbors = nodesState.filter { it.id != node.id }
                val result = validator.validatePlacement(candidate, seed, neighbors, { c -> seedFor(c) }, settings.spacingMarginMultiplier, effectiveEnforceCompanionRules, activeGuilds)

                if (!result.isValid) {
                    val conflictId = (result.spacingViolations + result.antagonistViolations).firstOrNull()
                    val conflictNode = neighbors.find { it.id == conflictId }
                    val conflictSeed = conflictNode?.let { seedFor(it.seedCode) }
                    snackbarMessage = if (conflictSeed != null) {
                        "Can't switch to ${seed.commonName}: too close to ${conflictSeed.commonName}. Move or remove it first."
                    } else {
                        "Can't switch to ${seed.commonName}: spacing conflict with a neighboring plant."
                    }
                    // Deliberately does NOT close the dialog or commit — user can pick a different variety.
                } else {
                    val updated = node.copy(seedCode = seed.botanicalCode, datePlantedEpochMillis = System.currentTimeMillis(), germinationFlagResolved = false)
                    launchSafely {
                        withContext(SgpExecutors.dbDispatcher) { database.plantedNodeDao().update(updated) }
                        reloadNodes()
                        undoStack.push(CanvasSnapshot(nodesState, pathZonesState))
                        redoStack.clear()
                    }
                    changeVarietyNode = null
                }
            },
            onDismiss = { changeVarietyNode = null }
        )
    }

    pendingAreaSelection?.let { area ->
        var chosenSeedCode by remember {
            mutableStateOf(
                if (seedDictionary.any { it.botanicalCode == activeSeedCode }) activeSeedCode
                else seedDictionary.firstOrNull()?.botanicalCode ?: ""
            )
        }
        var chosenPattern by remember { mutableStateOf(AutoPopulateEngine.PackingPattern.LINE) }
        var showAutoPopVarietyPicker by remember { mutableStateOf(false) } // [NEW]
        val chosenSeed = seedFor(chosenSeedCode)
        val previewCount = chosenSeed?.let {
            autoPopulateEngine.estimateCount(area.width, area.height, it.exclusionRadiusM * 2f * settings.spacingMarginMultiplier, chosenPattern)
        } ?: 0

        AlertDialog(
            onDismissRequest = { pendingAreaSelection = null },
            title = { Text("Auto-populate Area") },
            text = {
                Column {
                    Text("Area: ${"%.2f".format(area.width)}m × ${"%.2f".format(area.height)}m")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Variety", fontSize = 12.sp, color = Color.Gray)
                    // [FIXED] Was a flat radio-button LazyColumn over the entire seed dictionary —
                    // unusable now that the catalog can hold up to 2,936 entries. Reuses the same
                    // 3-step picker used everywhere else a variety needs choosing.
                    OutlinedButton(onClick = { showAutoPopVarietyPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(chosenSeed?.commonName ?: "Choose a Variety")
                    }
                    RecommendForArea(
                        enabled = Feature.isEnabled(Feature.RECOMMEND_AND_AUTOPOPULATE, settings.currentAppTier()),
                        compute = {
                            activePlot?.let { plot ->
                                RecommendationEngine.recommend(
                                    seedDictionary, plotContext(plot),
                                    listOf(PlotPoint(area.left, area.top), PlotPoint(area.right, area.top), PlotPoint(area.right, area.bottom), PlotPoint(area.left, area.bottom)),
                                    limit = 5
                                )
                            } ?: emptyList()
                        },
                        onPick = { code -> chosenSeedCode = code }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pattern", fontSize = 12.sp, color = Color.Gray)
                    Row {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chosenPattern = AutoPopulateEngine.PackingPattern.LINE }) {
                            RadioButton(selected = chosenPattern == AutoPopulateEngine.PackingPattern.LINE, onClick = { chosenPattern = AutoPopulateEngine.PackingPattern.LINE })
                            Text("Lines", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chosenPattern = AutoPopulateEngine.PackingPattern.HEXAGON }) {
                            RadioButton(selected = chosenPattern == AutoPopulateEngine.PackingPattern.HEXAGON, onClick = { chosenPattern = AutoPopulateEngine.PackingPattern.HEXAGON })
                            Text("Hexagon (denser)", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("≈ $previewCount plants will be placed", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = chosenSeed != null,
                    onClick = {
                        val seed = chosenSeed ?: return@TextButton
                        if (HardinessZones.blocksPlacement(seed, activePlot?.hardinessZone)) {
                            snackbarMessage = HardinessZones.describe(seed, activePlot?.hardinessZone) ?: "Not hardy in this zone."
                            return@TextButton
                        }
                        val localPoints = autoPopulateEngine.generatePositions(area.width, area.height, seed.exclusionRadiusM * 2f * settings.spacingMarginMultiplier, chosenPattern)
                        // [FIXED] Real bug: this previously only checked generated points against
                        // no-plant paths, never against plants that already existed OUTSIDE the
                        // selected area. That let auto-populate silently create spacing violations
                        // (confirmed report: a plant placed this way turned out too close to another
                        // of the same variety, only caught later when trying to Change Variety, which
                        // does validate). Now folds through candidates, validating each against both
                        // pre-existing nodes AND the ones just accepted earlier in this same batch.
                        val newNodes = localPoints.fold(emptyList<PlantedNodeEntity>()) { accepted, point ->
                            val absX = area.left + point.xM
                            val absY = area.top + point.yM
                            if ((activePlot?.let { !PlotShape.contains(it, absX, absY) } ?: false) || isInsidePath(absX, absY, seed.exclusionRadiusM)) {
                                accepted
                            } else {
                                val candidate = PlantedNodeEntity(plotId = plotId, seedCode = seed.botanicalCode, coordinateXM = absX, coordinateYM = absY)
                                val result = validator.validatePlacement(candidate, seed, nodesState + accepted, { code -> seedFor(code) }, settings.spacingMarginMultiplier, effectiveEnforceCompanionRules, activeGuilds)
                                if (result.isValid) accepted + candidate else accepted
                            }
                        }
                        if (newNodes.size < localPoints.size) {
                            snackbarMessage = "Placed ${newNodes.size} of ${localPoints.size} — the rest conflicted with existing plants or paths."
                        }
                        launchSafely {
                            withContext(SgpExecutors.dbDispatcher) {
                                if (newNodes.isNotEmpty()) database.plantedNodeDao().insertAll(newNodes)
                            }
                            reloadNodes()
                            undoStack.push(CanvasSnapshot(nodesState, pathZonesState))
                            redoStack.clear()
                        }
                        pendingAreaSelection = null
                        // [FIXED] Real report: after populating, the screen stayed in SELECT_AREA mode,
                        // where the tap handler that opens the edit/delete dialog is disabled entirely —
                        // so the newly-placed plants looked "un-editable." Switching back to PLACE_NODE
                        // (which is also where tap-to-edit lives) fixes that directly.
                        canvasMode = CanvasMode.PLACE_NODE
                    }
                ) { Text("Populate") }
            },
            dismissButton = { TextButton(onClick = { pendingAreaSelection = null }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface
        )

        if (showAutoPopVarietyPicker) {
            VarietyPickerDialog(
                seedDictionary = seedDictionary,
                conflictFor = pickerConflict,
                onSelect = { code -> chosenSeedCode = code; showAutoPopVarietyPicker = false },
                onDismiss = { showAutoPopVarietyPicker = false }
            )
        }
    }

    // [NEW — FR-001] Custom-shape (polygon) auto-populate dialog — same flow as the rectangle
    // version above, but candidates are generated across the polygon's bounding box and then
    // filtered to only those actually inside the drawn shape via pointInPolygon().
    pendingPolygonSelection?.let { polygon ->
        val minX = polygon.minOf { it.x }
        val maxX = polygon.maxOf { it.x }
        val minY = polygon.minOf { it.y }
        val maxY = polygon.maxOf { it.y }
        val boundingWidth = maxX - minX
        val boundingHeight = maxY - minY

        var chosenSeedCode by remember {
            mutableStateOf(
                if (seedDictionary.any { it.botanicalCode == activeSeedCode }) activeSeedCode
                else seedDictionary.firstOrNull()?.botanicalCode ?: ""
            )
        }
        var chosenPattern by remember { mutableStateOf(AutoPopulateEngine.PackingPattern.LINE) }
        var showAutoPopVarietyPicker2 by remember { mutableStateOf(false) }
        val chosenSeed = seedFor(chosenSeedCode)
        val previewCount = chosenSeed?.let {
            autoPopulateEngine.generatePositions(boundingWidth, boundingHeight, it.exclusionRadiusM * 2f * settings.spacingMarginMultiplier, chosenPattern)
                .count { point -> pointInPolygon(minX + point.xM, minY + point.yM, polygon) }
        } ?: 0

        AlertDialog(
            onDismissRequest = { pendingPolygonSelection = null },
            title = { Text("Auto-populate Custom Area") },
            text = {
                Column {
                    Text("${polygon.size}-point shape, ${"%.2f".format(boundingWidth)}m × ${"%.2f".format(boundingHeight)}m bounding box")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Variety", fontSize = 12.sp, color = Color.Gray)
                    OutlinedButton(onClick = { showAutoPopVarietyPicker2 = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(chosenSeed?.commonName ?: "Choose a Variety")
                    }
                    RecommendForArea(
                        enabled = Feature.isEnabled(Feature.RECOMMEND_AND_AUTOPOPULATE, settings.currentAppTier()),
                        compute = {
                            activePlot?.let { plot -> RecommendationEngine.recommend(seedDictionary, plotContext(plot), toPlotPoints(polygon), limit = 5) } ?: emptyList()
                        },
                        onPick = { code -> chosenSeedCode = code }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Pattern", fontSize = 12.sp, color = Color.Gray)
                    Row {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chosenPattern = AutoPopulateEngine.PackingPattern.LINE }) {
                            RadioButton(selected = chosenPattern == AutoPopulateEngine.PackingPattern.LINE, onClick = { chosenPattern = AutoPopulateEngine.PackingPattern.LINE })
                            Text("Lines", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chosenPattern = AutoPopulateEngine.PackingPattern.HEXAGON }) {
                            RadioButton(selected = chosenPattern == AutoPopulateEngine.PackingPattern.HEXAGON, onClick = { chosenPattern = AutoPopulateEngine.PackingPattern.HEXAGON })
                            Text("Hexagon (denser)", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("≈ $previewCount plants will be placed", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = chosenSeed != null,
                    onClick = {
                        val seed = chosenSeed ?: return@TextButton
                        if (HardinessZones.blocksPlacement(seed, activePlot?.hardinessZone)) {
                            snackbarMessage = HardinessZones.describe(seed, activePlot?.hardinessZone) ?: "Not hardy in this zone."
                            return@TextButton
                        }
                        val localPoints = autoPopulateEngine.generatePositions(boundingWidth, boundingHeight, seed.exclusionRadiusM * 2f * settings.spacingMarginMultiplier, chosenPattern)
                            .filter { point -> pointInPolygon(minX + point.xM, minY + point.yM, polygon) }
                        val newNodes = localPoints.fold(emptyList<PlantedNodeEntity>()) { accepted, point ->
                            val absX = minX + point.xM
                            val absY = minY + point.yM
                            if ((activePlot?.let { !PlotShape.contains(it, absX, absY) } ?: false) || isInsidePath(absX, absY, seed.exclusionRadiusM)) {
                                accepted
                            } else {
                                val candidate = PlantedNodeEntity(plotId = plotId, seedCode = seed.botanicalCode, coordinateXM = absX, coordinateYM = absY)
                                val result = validator.validatePlacement(candidate, seed, nodesState + accepted, { code -> seedFor(code) }, settings.spacingMarginMultiplier, effectiveEnforceCompanionRules, activeGuilds)
                                if (result.isValid) accepted + candidate else accepted
                            }
                        }
                        if (newNodes.size < localPoints.size) {
                            snackbarMessage = "Placed ${newNodes.size} of ${localPoints.size} — the rest conflicted with existing plants or paths."
                        }
                        launchSafely {
                            withContext(SgpExecutors.dbDispatcher) {
                                if (newNodes.isNotEmpty()) database.plantedNodeDao().insertAll(newNodes)
                            }
                            reloadNodes()
                            undoStack.push(CanvasSnapshot(nodesState, pathZonesState))
                            redoStack.clear()
                        }
                        pendingPolygonSelection = null
                        canvasMode = CanvasMode.PLACE_NODE
                    }
                ) { Text("Populate") }
            },
            dismissButton = { TextButton(onClick = { pendingPolygonSelection = null }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surface
        )

        if (showAutoPopVarietyPicker2) {
            VarietyPickerDialog(
                seedDictionary = seedDictionary,
                conflictFor = pickerConflict,
                onSelect = { code -> chosenSeedCode = code; showAutoPopVarietyPicker2 = false },
                onDismiss = { showAutoPopVarietyPicker2 = false }
            )
        }
    }
}

/**
 * [NEW] 3-step Category -> Species -> Cultivar picker, replacing the flat scrolling lists that
 * were used everywhere a variety needed to be chosen. Necessary now that the catalog can hold up
 * to 2,936 entries (Pro tier) — a flat list of that size is unusable. Species are derived by
 * grouping botanicalCode by its prefix (e.g. every "TOM-###" code belongs to the "Tomato"
 * species group); cultivar names are the part of commonName after " - " (e.g. "Brandywine" from
 * "Tomato - Brandywine").
 */
@Composable
private fun VarietyPickerDialog(
    seedDictionary: List<SeedEntity>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    conflictFor: (SeedEntity) -> String? = { null } // FR-010: reason a variety is greyed out, or null
) {
    var step by remember { mutableStateOf(0) } // 0 = category, 1 = species, 2 = cultivar
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedSpeciesPrefix by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    fun speciesPrefixOf(code: String) = code.substringBefore("-")
    fun speciesNameOf(seed: SeedEntity) = seed.commonName.substringBefore(" - ")
    fun cultivarNameOf(seed: SeedEntity) = seed.commonName.substringAfter(" - ", seed.commonName)

    val categories = remember(seedDictionary) {
        seedDictionary.groupingBy { it.plantType }.eachCount().toList().sortedByDescending { it.second }
    }
    val speciesInCategory = remember(seedDictionary, selectedCategory) {
        selectedCategory?.let { cat ->
            seedDictionary.filter { it.plantType == cat }
                .groupBy { speciesPrefixOf(it.botanicalCode) }
                .map { (prefix, seeds) -> Triple(prefix, speciesNameOf(seeds.first()), seeds.size) }
                .sortedBy { it.second }
        } ?: emptyList()
    }
    val cultivarsInSpecies = remember(seedDictionary, selectedSpeciesPrefix) {
        selectedSpeciesPrefix?.let { prefix ->
            seedDictionary.filter { speciesPrefixOf(it.botanicalCode) == prefix }.sortedBy { cultivarNameOf(it) }
        } ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    when (step) {
                        0 -> "Choose a Category"
                        1 -> "Choose a ${selectedCategory?.lowercase()?.replaceFirstChar { it.uppercase() }} Species"
                        else -> "Choose a Cultivar"
                    }
                )
                if (step > 0) {
                    TextButton(
                        onClick = { if (step == 2) { step = 1; selectedSpeciesPrefix = null } else { step = 0; selectedCategory = null } },
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("← Back", fontSize = 12.sp) }
                }
            }
        },
        text = {
            Column {
                if (step >= 1) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (step) {
                        0 -> items(categories) { (category, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCategory = category; step = 1; searchQuery = "" }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(category.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("$count", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        1 -> items(speciesInCategory.filter { it.second.contains(searchQuery, ignoreCase = true) }) { (prefix, name, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedSpeciesPrefix = prefix; step = 2; searchQuery = "" }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(name, fontSize = 14.sp)
                                Text("$count cultivar${if (count == 1) "" else "s"}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        else -> items(cultivarsInSpecies.filter { cultivarNameOf(it).contains(searchQuery, ignoreCase = true) }) { seed ->
                            val conflict = conflictFor(seed)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = conflict == null) { onSelect(seed.botanicalCode) }
                                    .alpha(if (conflict == null) 1f else 0.4f)
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(12.dp).background(VegetableColorPalette.colorFor(seed), androidx.compose.foundation.shape.CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(cultivarNameOf(seed), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    conflict?.let { Text(it, fontSize = 10.sp, color = Color(0xFFEF4444)) }
                                    Text(
                                        "spacing ${seed.exclusionRadiusM}m • germinates ~${seed.germinationDays}d • harvest ~${seed.daysToHarvest}d",
                                        fontSize = 10.sp, color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

/** Draws simple meter tick marks + labels along one edge of the canvas. */
/** [UPDATED] Tick interval and font size are now configurable (Settings) instead of hardcoded. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRuler(
    axisLengthM: Float,
    canvasLengthPx: Float,
    horizontal: Boolean,
    tickIntervalM: Float = 1.0f,
    fontSizePx: Float = 20f,
    unit: com.example.smartgardenplanner.core.DistanceUnit = com.example.smartgardenplanner.core.DistanceUnit.METERS
) {
    if (axisLengthM <= 0f || tickIntervalM <= 0f) return
    val pxPerMeter = canvasLengthPx / axisLengthM
    var meter = 0f
    while (meter <= axisLengthM) {
        val pos = meter * pxPerMeter
        // [UPDATED] Labels now go through DistanceFormatter, so the ruler respects the
        // Meters/Inches setting instead of always showing "Nm".
        val label = com.example.smartgardenplanner.core.DistanceFormatter.format(
            meter, unit, decimals = if (tickIntervalM < 1f) 2 else 0
        )
        if (horizontal) {
            drawLine(Color.Gray, Offset(pos, size.height * 0.4f), Offset(pos, size.height), strokeWidth = 1.5f)
            drawContext.canvas.nativeCanvas.drawText(
                label, pos + 2f, size.height * 0.7f,
                android.graphics.Paint().apply { color = android.graphics.Color.LTGRAY; textSize = fontSizePx }
            )
        } else {
            drawLine(Color.Gray, Offset(size.width * 0.4f, pos), Offset(size.width, pos), strokeWidth = 1.5f)
            drawContext.canvas.nativeCanvas.drawText(
                label, 2f, pos + 8f,
                android.graphics.Paint().apply { color = android.graphics.Color.LTGRAY; textSize = fontSizePx * 0.9f }
            )
        }
        meter += tickIntervalM
    }
}


/** FR-015: "Recommend for this area" inside the auto-populate dialogs (Pro). */
@Composable
private fun RecommendForArea(enabled: Boolean, compute: () -> List<Recommendation>, onPick: (String) -> Unit) {
    var recs by remember { mutableStateOf<List<Recommendation>?>(null) }
    if (!enabled) {
        Text("Recommend for this area: Pro tier", fontSize = 11.sp, color = Color.Gray)
        return
    }
    TextButton(onClick = { recs = compute() }, contentPadding = PaddingValues(0.dp)) { Text("Recommend for this area", fontSize = 12.sp) }
    recs?.let { list ->
        if (list.isEmpty()) Text("Nothing in the catalog suits this spot.", fontSize = 11.sp, color = Color.Gray)
        list.forEach { r ->
            Column(modifier = Modifier.fillMaxWidth().clickable { onPick(r.seed.botanicalCode) }.padding(vertical = 3.dp)) {
                Text(r.seed.commonName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Text(r.reasons.take(2).joinToString(" • "), fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

/** Details for a site area or barrier (FR-003 to FR-006): label, slope, flood months, height, crown radius. */
@Composable
private fun SiteFeatureDialog(
    initial: SiteFeatureEntity,
    isNew: Boolean,
    onSave: (SiteFeatureEntity) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val type = SiteFeatureType.of(initial.featureType) ?: SiteFeatureType.FULL_SUN
    var label by remember(initial.id) { mutableStateOf(initial.label) }
    var height by remember(initial.id) { mutableStateOf(if (initial.heightM > 0f) initial.heightM.toString() else "") }
    var radius by remember(initial.id) { mutableStateOf(if (initial.radiusM > 0f) initial.radiusM.toString() else "") }
    var grade by remember(initial.id) { mutableStateOf(if (initial.slopeGradePct > 0f) initial.slopeGradePct.toString() else "") }
    var direction by remember(initial.id) { mutableStateOf(initial.slopeDirectionDeg) }
    var months by remember(initial.id) { mutableStateOf(initial.floodMonths) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }
    val directions = listOf("N" to 0f, "NE" to 45f, "E" to 90f, "SE" to 135f, "S" to 180f, "SW" to 225f, "W" to 270f, "NW" to 315f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (isNew) "New: " else "") + type.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it.take(40) }, label = { Text("Label (optional)") }, singleLine = true)
                if (type.isBarrier) {
                    OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Height (m)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                if (type == SiteFeatureType.TREE) {
                    OutlinedTextField(value = radius, onValueChange = { radius = it }, label = { Text("Crown radius (m)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                if (type == SiteFeatureType.SLOPE) {
                    OutlinedTextField(value = grade, onValueChange = { grade = it }, label = { Text("Grade (%) — 1 m drop over 10 m is 10 %") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Text("Downhill direction (compass)", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        directions.forEach { (name, deg) ->
                            FilterChip(selected = direction == deg, onClick = { direction = deg }, label = { Text(name, fontSize = 10.sp) })
                        }
                    }
                }
                if (type == SiteFeatureType.FLOOD) {
                    OutlinedTextField(value = months, onValueChange = { months = it }, label = { Text("Months it floods, e.g. 3,4,5") }, singleLine = true)
                }
                if (type == SiteFeatureType.FULL_SUN || type == SiteFeatureType.PART_SHADE || type == SiteFeatureType.FULL_SHADE) {
                    Text("Plants that need more sun than this area gets are flagged in the harmony report and left out of suggestions for it.", fontSize = 11.sp, color = Color.Gray)
                }
                error?.let { Text(it, color = Color(0xFFEF4444), fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val h = height.toFloatOrNull()
                val r = radius.toFloatOrNull()
                val g = grade.toFloatOrNull()
                val monthList = months.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                error = when {
                    type.isBarrier && (h == null || h <= 0f || h > 100f) -> "Enter a height between 0 and 100 m."
                    type == SiteFeatureType.TREE && (r == null || r <= 0f || r > 30f) -> "Enter a crown radius between 0 and 30 m."
                    type == SiteFeatureType.SLOPE && (g == null || g < 0f || g > 100f) -> "Enter a grade between 0 and 100 %."
                    type == SiteFeatureType.FLOOD && monthList.any { it.toIntOrNull() == null || it.toInt() !in 1..12 } -> "Months are numbers 1–12, separated by commas."
                    else -> null
                }
                if (error == null) {
                    onSave(
                        initial.copy(
                            label = label.trim(),
                            heightM = h ?: 0f,
                            radiusM = r ?: 0f,
                            slopeGradePct = g ?: 0f,
                            slopeDirectionDeg = direction,
                            floodMonths = monthList.joinToString(",")
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
