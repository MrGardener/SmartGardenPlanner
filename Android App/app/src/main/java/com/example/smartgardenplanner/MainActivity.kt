package com.example.smartgardenplanner

import android.app.Activity
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.smartgardenplanner.core.LayoutPalette
import androidx.compose.ui.graphics.toArgb
import com.example.smartgardenplanner.core.PlantingHistoryEntity
import com.example.smartgardenplanner.core.PlantingLayout
import com.example.smartgardenplanner.core.Seasons
import com.example.smartgardenplanner.core.CropRotation
import com.example.smartgardenplanner.core.VarietyCatalogTraits
import com.example.smartgardenplanner.core.RotationPlanner
import com.example.smartgardenplanner.core.SeasonPlan
import com.example.smartgardenplanner.core.ShadeDay
import com.example.smartgardenplanner.core.ShadeTools
import com.example.smartgardenplanner.core.Irrigation
import com.example.smartgardenplanner.core.WaterSource
import com.example.smartgardenplanner.core.SunBand
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
import com.example.smartgardenplanner.core.AutoPlanner
import com.example.smartgardenplanner.core.AutoPlanResult
import com.example.smartgardenplanner.core.PlantRequest
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
    // New bundled varieties (e.g. the spring onions added 2026-09-27) reach existing installs: insert any
    // entries of the active tier that are missing. IGNORE keeps every existing row, custom or not, unchanged.
    try {
        val tierName = com.example.smartgardenplanner.data.SettingsRepository(
            com.example.smartgardenplanner.data.SecurityRepositoryImpl(database.configDao())
        ).load().catalogTier
        val tier = com.example.smartgardenplanner.data.CatalogTier.entries.firstOrNull { it.name == tierName }
            ?: com.example.smartgardenplanner.data.CatalogTier.BASIC
        if (database.seedDao().count() < tier.varietyCount) {
            database.seedDao().insertAll(com.example.smartgardenplanner.data.SeedCatalogLoader(context).loadTier(tier))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        auditLogger.appendLog("CATALOG: top-up skipped (${e.javaClass.simpleName}).")
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
        applyLanguage(context, startupSettings.language)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        auditLogger.appendLog("REMINDERS: could not schedule (${e.javaClass.simpleName}).")
    }

    AppServices(database, auditLogger, SensorMeasurementEngine(context))
}

/** Loads the dictionary for [code] from assets/i18n (FR-062); English, or a missing file, means no translation. */
fun applyLanguage(context: Context, code: String) {
    val dict = if (code == "en") emptyMap() else try {
        context.assets.open("i18n/$code.txt").bufferedReader().use { com.example.smartgardenplanner.core.I18n.parse(it.readLines()) }
    } catch (e: java.io.IOException) { emptyMap() }
    com.example.smartgardenplanner.core.I18n.use(if (dict.isEmpty()) "en" else code, dict)
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
        Text(tr("Your garden data can't be read on this device"), style = MaterialTheme.typography.headlineSmall)
        Text(
            tr("The saved data is encrypted with a key that isn't available on this phone. This usually " +
                "happens after restoring a backup onto a new or reset phone."),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            tr("You can start with empty data. The unreadable files are kept (renamed), not deleted."),
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = { confirming = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("Start with empty data")) }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(tr("Close app")) }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(tr("Start with empty data?")) },
            text = { Text(tr("Your plots and plants won't be shown. The old files stay on the phone, renamed.")) },
            confirmButton = { TextButton(onClick = { confirming = false; onStartEmpty() }) { Text(tr("Start empty")) } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(tr("Cancel")) } }
        )
    }
}

@Composable
fun StartupFailedScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(tr("The app couldn't start"), style = MaterialTheme.typography.headlineSmall)
        Text(tr(message), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(tr("Try again")) }
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

    // FR-044: before first use, say plainly that the planner is an aid and results aren't guaranteed.
    val disclaimerScope = rememberCoroutineScope()
    var showDisclaimer by remember { mutableStateOf(false) }
    val disclaimerSettings = remember { com.example.smartgardenplanner.data.SettingsRepository(com.example.smartgardenplanner.data.SecurityRepositoryImpl(database.configDao())) }
    LaunchedEffect(Unit) {
        showDisclaimer = try { !withContext(SgpExecutors.dbDispatcher) { disclaimerSettings.load().disclaimerAccepted } } catch (e: CancellationException) { throw e } catch (e: Exception) { true }
    }
    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(tr(com.example.smartgardenplanner.core.Disclaimer.TITLE)) },
            text = { Text(tr(com.example.smartgardenplanner.core.Disclaimer.TEXT + "\n\nYou can read this again in Settings.")) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclaimer = false
                    disclaimerScope.launch {
                        try { withContext(SgpExecutors.dbDispatcher) { disclaimerSettings.save(disclaimerSettings.load().copy(disclaimerAccepted = true)) } } catch (e: CancellationException) { throw e } catch (e: Exception) {}
                    }
                }) { Text(tr("I understand")) }
            }
        )
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
    var fileMessage by remember { mutableStateOf<String?>(null) }
    val dashContext = LocalContext.current
    val dashScope = rememberCoroutineScope()
    suspend fun reloadPlots() {
        plotList = withContext(SgpExecutors.dbDispatcher) { database.plotDao().getAllPlots() }
    }
    // FR-029: open a plan file (made on this or another device) as new plots; save all plots to one file.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            dashScope.launch {
                fileMessage = try {
                    val text = com.example.smartgardenplanner.data.PlanFileIo.read(dashContext, uri)
                    val report = withContext(SgpExecutors.dbDispatcher) { com.example.smartgardenplanner.data.PlanFileRepository(database, filesDir = dashContext.filesDir).import(text) }
                    reloadPlots()
                    if (report.plotsImported == 0) "Couldn't open the file: " + report.messages.joinToString(" ")
                    else "Opened ${report.plotsImported} plot(s) with ${report.plantsImported} plants." + if (report.messages.isNotEmpty()) " " + report.messages.joinToString(" ") else ""
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "Couldn't open the file (${e.javaClass.simpleName}). Nothing was changed."
                }
            }
        }
    }
    val exportAllLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            dashScope.launch {
                fileMessage = try {
                    val text = withContext(SgpExecutors.dbDispatcher) {
                        com.example.smartgardenplanner.data.PlanFileRepository(database, filesDir = dashContext.filesDir).export(plotList.map { it.id }, com.example.smartgardenplanner.data.PlanFileIo.appVersion(dashContext))
                    }
                    com.example.smartgardenplanner.data.PlanFileIo.write(dashContext, uri, text)
                    "Saved ${plotList.size} plot(s) to the file."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "Couldn't save the file (${e.javaClass.simpleName})."
                }
            }
        }
    }

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
                title = { Text(tr("Smart Garden Planner"), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    com.example.smartgardenplanner.ui.OnlineBadge(onlineOn)
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Open plan file", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(enabled = plotList.isNotEmpty(), onClick = { exportAllLauncher.launch("my-garden.sgp.json") }) {
                        Icon(Icons.Default.Save, contentDescription = "Save all plots to a file", tint = MaterialTheme.colorScheme.primary)
                    }
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
                text = { Text(tr("Create New Plot"), fontWeight = FontWeight.Bold) }
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
            Text(tr("Active Plots"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            loadError?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            fileMessage?.let { Text(tr(it), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.clickable { fileMessage = null }) }

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
                        Text(tr("No agricultural designs committed."), color = Color.Gray, fontSize = 14.sp)
                        Text(tr("Tap button to create a new layout."), color = Color.Gray, fontSize = 12.sp)
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
                                    tr("Physical Boundaries: ${plot.lengthM}m × ${plot.widthM}m" +
                                        (if (com.example.smartgardenplanner.core.PlotShape.outline(plot).isNotEmpty()) " • custom outline" else "") +
                                        (plot.hardinessZone?.let { " • zone $it" } ?: "")),
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
    var yardPests by remember { mutableStateOf<Set<com.example.smartgardenplanner.core.Pest>>(emptySet()) }
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
    // FR-028: direction and ZIP asked up front (both optional here; the layout keeps asking until set).
    var topFaces by remember { mutableStateOf<Float?>(null) }
    var zipCode by remember { mutableStateOf("") }
    val creatorContext = LocalContext.current

    val isInputValid = plotName.isNotBlank() &&
            plotLength.matches(regexValidator) &&
            plotWidth.matches(regexValidator) &&
            (plotLength.toFloatOrNull() ?: 0f) in minDimDisplay..maxDimDisplay &&
            (plotWidth.toFloatOrNull() ?: 0f) in minDimDisplay..maxDimDisplay

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Initialize Plot Configuration")) },
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = plotName,
                onValueChange = { newValue -> plotName = newValue.take(200) },
                label = { Text(tr("Agricultural Plot Designation Name")) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = plotLength,
                onValueChange = { newValue -> plotLength = newValue },
                label = { Text(tr("Real-World Length Dimension ($unitSuffix)")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                isError = plotLength.isNotEmpty() && (!plotLength.matches(regexValidator) || (plotLength.toFloatOrNull() ?: 0f) < minDimDisplay || (plotLength.toFloatOrNull() ?: 0f) > maxDimDisplay)
            )

            OutlinedTextField(
                value = plotWidth,
                onValueChange = { newValue -> plotWidth = newValue },
                label = { Text(tr("Real-World Width Dimension ($unitSuffix)")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                isError = plotWidth.isNotEmpty() && (!plotWidth.matches(regexValidator) || (plotWidth.toFloatOrNull() ?: 0f) < minDimDisplay || (plotWidth.toFloatOrNull() ?: 0f) > maxDimDisplay)
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = scaleEngineSelection,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(tr("Scale Engine Computing Source")) },
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
                        text = { Text(tr("Manual Dimensions Entry")) },
                        onClick = {
                            scaleEngineSelection = "Manual Dimensions Entry"
                            dropdownExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Device IMU Sensor Measuring")) },
                        onClick = {
                            scaleEngineSelection = "Device IMU Sensor Measuring"
                            dropdownExpanded = false
                        }
                    )
                }
            }

            Text(tr("Which way does the top edge of the plot face?"), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(tr("Stand at the bottom edge and look across the plot. Used to place tall plants where they won't shade others, and for sun and shade."), fontSize = 11.sp, color = Color.Gray)
            com.example.smartgardenplanner.ui.CompassChips(topFaces) { topFaces = it }
            Text(tr("What pests or animals do you see regularly in your yard?"), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(tr("Tick all that apply. Plot insights → Care then shows how to keep them away (fencing and more) and which plants they go for. You can change this later."), fontSize = 11.sp, color = Color.Gray)
            com.example.smartgardenplanner.ui.PestChips(yardPests) { yardPests = it }
            OutlinedTextField(
                value = zipCode,
                onValueChange = { zipCode = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text(tr("ZIP code (optional)")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            saveError?.let { Text(tr(it), color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

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
                                val zip = zipCode.takeIf { com.example.smartgardenplanner.core.ZipTable.isValidZip(it) }
                                val location = zip?.let { com.example.smartgardenplanner.data.ZipLookup.location(creatorContext, it) }
                                val plotId = withContext(SgpExecutors.dbDispatcher) {
                                    val zone = zip?.let { com.example.smartgardenplanner.data.ZipLookup.offlineZone(creatorContext, it) ?: database.climateZoneDao().getByZip(it)?.hardinessZone }
                                    database.plotDao().insert(
                                        PlotEntity(
                                            name = plotName.trim(),
                                            lengthM = lengthMeters,
                                            widthM = widthMeters,
                                            createdTimestamp = now,
                                            lastModifiedTimestamp = now,
                                            northBearingDeg = topFaces ?: 0f,
                                            orientationSet = topFaces != null,
                                            locationZip = zip,
                                            latitude = location?.latitude,
                                            longitude = location?.longitude,
                                            hardinessZone = zone,
                                            pests = com.example.smartgardenplanner.core.Pest.encode(yardPests)
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
                Text(tr("Initialize Spatial Workspace"), fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
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

enum class CanvasMode { PLACE_NODE, DRAW_PATH, SELECT_AREA, OUTLINE, SITE_AREA, BARRIER, PHOTO } // OUTLINE: FR-002, SITE_AREA: FR-003/004/005, BARRIER: FR-006, PHOTO: FR-046
enum class AreaSelectSubMode { RECTANGLE, POLYGON } // [NEW — FR-001]
enum class PathDrawSubMode { RECTANGLE, POINTS }

/** [NEW] Unified undo/redo snapshot covering BOTH nodes and path zones together. Previously
 * undo/redo only tracked planted nodes — drawing or deleting a path zone never pushed anything,
 * so Undo silently did nothing for path edits. Two independent stacks (one per entity type)
 * driven by a single pair of Undo/Redo buttons would itself be ambiguous (which stack should
 * "Undo" advance if the last action was a path draw vs. a node placement?), so this snapshots
 * both together as one atomic unit of history. */
data class CanvasSnapshot(
    val nodes: List<PlantedNodeEntity>,
    val paths: List<PathZoneEntity>,
    val features: List<SiteFeatureEntity>, // trees, fences, walls, buildings, sun/flood/slope areas
        val boundaryJson: String?,             // plot outline (FR-002)
    val history: List<PlantingHistoryEntity> // finished seasons (FR-033), so "Start a new season" can be undone
)

/** FR-062: interface text in the chosen language (English when there's no translation). */
fun tr(text: String): String = com.example.smartgardenplanner.core.I18n.tr(text)

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
    // FR-056: Plan B for a plant that died.
    var planBNode by remember { mutableStateOf<PlantedNodeEntity?>(null) }
    // FR-061: a whole group — rearrange its rows, or move it by dragging any of its plants in Move Mode.
    var rearrangeIds by remember { mutableStateOf<Set<Long>?>(null) }
    var groupMoveIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
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
    var movingSiteFeature by remember { mutableStateOf<SiteFeatureEntity?>(null) }
    // FR-027 "Plan an area for me": choose area -> list plants -> preview -> accept.
    var planForMe by remember { mutableStateOf(false) }
    var autoPlanArea by remember { mutableStateOf<List<PlotPoint>?>(null) }
    var planPreview by remember { mutableStateOf<AutoPlanResult?>(null) }
    var planRunning by remember { mutableStateOf(false) }
    // FR-060: layouts worked out for the proposal, the one shown, and how to get another.
    var planOptions by remember { mutableStateOf<List<Pair<String, AutoPlanResult>>>(emptyList()) }
    var planOptionIndex by remember { mutableStateOf(0) }
    var planMore by remember { mutableStateOf<(suspend () -> Pair<String, AutoPlanResult>?)?>(null) }
    // FR-063: plants already in the area that the proposal replaces when kept; FR-064: card folded.
    var planReplace by remember { mutableStateOf(false) }
    var planReplaceIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var previewFolded by remember { mutableStateOf(false) }
        val planRows = remember { mutableStateListOf<Pair<String, Int>>() }
        // FR-043: varieties marked "most important" in that list.
        val planPriority = remember { mutableStateListOf<String>() }
        // FR-047: clump arrangement chosen per variety (plants per row).
        val planShapes = remember { mutableStateMapOf<String, List<Int>>() }
    // FR-033 season history of this plot; FR-034 variety codes planted on any plot or season ("what you usually plant").
    var historyState by remember { mutableStateOf<List<PlantingHistoryEntity>>(emptyList()) }
    var historyYear by remember { mutableStateOf<Int?>(null) }
    var allPlantedCodes by remember { mutableStateOf<List<String>>(emptyList()) }
        var showNewSeasonDialog by remember { mutableStateOf(false) }
    // FR-036: variety pointed out on the layout from the legend, and the outline corner being moved (FR-002).
        var findCode by remember { mutableStateOf<String?>(null) }
    // FR-037: season shown read-only, next-season planning, multi-season rotation plan.
    var viewSeasonYear by remember { mutableStateOf<Int?>(null) }
    var nextSeasonMode by remember { mutableStateOf(false) }
    var rotationPlans by remember { mutableStateOf<List<SeasonPlan>>(emptyList()) }
    var rotationIndex by remember { mutableStateOf(0) }
    // FR-048: what the rotation plan was made from, and variety changes (year → from code → to seed).
    var rotationBase by remember { mutableStateOf<List<PlantRequest>>(emptyList()) }
    var rotationFirst by remember { mutableStateOf(0) }
    var rotationCount by remember { mutableStateOf(5) }
    var rotationChanges by remember { mutableStateOf<Map<Int, Map<String, SeedEntity>>>(emptyMap()) }
    var showRotationChange by remember { mutableStateOf(false) }
    var showRotationDialog by remember { mutableStateOf(false) }
    // FR-041: duplicate the plot as a template.
    var showDuplicateDialog by remember { mutableStateOf(false) }
    // FR-046: satellite photo under the plot.
    var showPhotoDialog by remember { mutableStateOf(false) }
    // FR-051: legend "Replace…" — every plant of this variety to another variety in one step.
    var replaceFromCode by remember { mutableStateOf<String?>(null) }
    var photoBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var photoVersion by remember { mutableIntStateOf(0) }
    var photoCalibrating by remember { mutableStateOf(false) }
    val photoPoints = remember { mutableStateListOf<Offset>() }
    var photoDrag by remember { mutableStateOf(Offset.Zero) }
    var photoAddress by remember { mutableStateOf("") }
    // FR-038: shade on a chosen day, whole day (null) or at a solar hour, with plants casting shade.
    var shadeDay by remember { mutableStateOf(ShadeDay.SEASON) }
    // FR-054: frost dates for the plot's location (nearest NOAA station).
    var growingSeason by remember { mutableStateOf<com.example.smartgardenplanner.core.Season?>(null) }
    var shadeHour by remember { mutableStateOf<Double?>(null) }
    var shadePlants by remember { mutableStateOf(true) }
    var shadowGrid by remember { mutableStateOf<Pair<Int, BooleanArray>?>(null) }
    // FR-039: irrigation water map.
    var showWater by remember { mutableStateOf(false) }
    var waterGrid by remember { mutableStateOf<Pair<Int, Array<WaterSource>>?>(null) }
    var movingOutlineCorner by remember { mutableStateOf<Int?>(null) }
    // FR-029: save this plot as a portable plan file (.sgp.json).
    val canvasContext = LocalContext.current
    val exportScope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            exportScope.launch {
                try {
                    val text = withContext(SgpExecutors.dbDispatcher) {
                        com.example.smartgardenplanner.data.PlanFileRepository(database, filesDir = canvasContext.filesDir).export(listOf(plotId), com.example.smartgardenplanner.data.PlanFileIo.appVersion(canvasContext))
                    }
                    com.example.smartgardenplanner.data.PlanFileIo.write(canvasContext, uri, text)
                    snackbarMessage = "Plan file saved. Open it on another device with Plot list → Open plan file."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    snackbarMessage = "Couldn't save the file (${e.javaClass.simpleName})."
                }
            }
        }
    }
    var showDirectionDialog by remember { mutableStateOf(false) }
    var showSiteFeatures by remember { mutableStateOf(true) }
    var showShade by remember { mutableStateOf(false) }
    var shadeGrid by remember { mutableStateOf<Pair<Int, FloatArray>?>(null) }

    // Back while path/area points are being drawn asks before discarding them (T2-ENV-040).
    BackHandler(enabled = inProgressPoints.isNotEmpty()) { showDiscardDialog = true }

    // [NEW] Drag-to-reposition support. Locked (off) by default, as requested, so it can't cause
    // an accidental move — must be explicitly enabled via the lock/unlock button in the top bar.
    var moveModeEnabled by remember { mutableStateOf(false) }
    // A group move (FR-061) lasts until Move Mode is turned off; then single plants move one at a time again.
    LaunchedEffect(moveModeEnabled) { if (!moveModeEnabled) groupMoveIds = emptySet() }
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

    // FR-046: the photo is loaded from the app's files; its placement lives on the plot row.
    LaunchedEffect(activePlot?.latitude, activePlot?.longitude) {
        val plot = activePlot
        growingSeason = withContext(Dispatchers.IO) { com.example.smartgardenplanner.data.FrostLookup.season(canvasContext, plot) }
    }
    LaunchedEffect(plotId, photoVersion) {
        photoBitmap = withContext(Dispatchers.IO) { com.example.smartgardenplanner.data.BackdropStore.load(canvasContext.filesDir, plotId) }
    }
    // FR-050: open the plot's address (or its coordinates) in Google Maps; a typed address is kept with the plot.
    fun openInMaps(query: String) {
        val plot = activePlot ?: return
        val q = query.trim()
        val url = com.example.smartgardenplanner.core.Backdrop.googleMapsUrl(plot.latitude, plot.longitude, q.ifBlank { null })
        if (url == null) { snackbarMessage = "Type the plot's address first (menu → Satellite photo…)."; return }
        if (q.isNotEmpty() && q != plot.address && !q.all { it.isDigit() }) {
            val updated = plot.copy(address = q.take(200))
            launchSafely { withContext(SgpExecutors.dbDispatcher) { database.plotDao().update(updated) }; activePlot = updated }
        }
        try { canvasContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        catch (e: Exception) { snackbarMessage = "No app can open Google Maps on this device." }
    }
    fun savePhotoPlacement(update: (com.example.smartgardenplanner.core.Backdrop) -> com.example.smartgardenplanner.core.Backdrop?) {
        val plot = activePlot ?: return
        val current = com.example.smartgardenplanner.core.Backdrop.parse(plot.backdropJson) ?: return
        val updated = plot.copy(backdropJson = update(current)?.encode(), lastModifiedTimestamp = System.currentTimeMillis())
        launchSafely {
            withContext(SgpExecutors.dbDispatcher) { database.plotDao().update(updated) }
            activePlot = updated
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val plot = activePlot
        if (uri != null && plot != null) launchSafely {
            val size = withContext(Dispatchers.IO) { com.example.smartgardenplanner.data.BackdropStore.importFromUri(canvasContext, uri, plotId) }
            if (size == null) { snackbarMessage = "That picture couldn't be read. Try a PNG or JPEG screenshot."; return@launchSafely }
            val updated = plot.copy(backdropJson = com.example.smartgardenplanner.core.Backdrop.fresh(plot, size.first, size.second).encode(), lastModifiedTimestamp = System.currentTimeMillis())
            withContext(SgpExecutors.dbDispatcher) { database.plotDao().update(updated) }
            activePlot = updated
            photoVersion++
            canvasMode = CanvasMode.PHOTO; photoCalibrating = true; photoPoints.clear()
            snackbarMessage = "Photo added. Set its scale: tap two points on the photo whose real distance you know (the Google Maps scale bar, or both ends of a fence)."
        }
    }

    // Replaces the plot's plants, paths, site features and outline with a snapshot in one transaction,
    // keeping row ids (DW-0802). Obstacles, areas and the outline are part of undo (T2-FUN-190).
    suspend fun restoreSnapshot(snapshot: CanvasSnapshot) {
        val restoredPlot = withContext(SgpExecutors.dbDispatcher) {
            database.withTransaction {
                database.plantedNodeDao().deleteAllForPlot(plotId)
                if (snapshot.nodes.isNotEmpty()) database.plantedNodeDao().insertAll(snapshot.nodes)
                database.pathZoneDao().deleteAllForPlot(plotId)
                if (snapshot.paths.isNotEmpty()) database.pathZoneDao().insertAll(snapshot.paths)
                database.siteFeatureDao().deleteAllForPlot(plotId)
                                if (snapshot.features.isNotEmpty()) database.siteFeatureDao().insertAll(snapshot.features)
                database.plantingHistoryDao().deleteAllForPlot(plotId)
                if (snapshot.history.isNotEmpty()) database.plantingHistoryDao().insertAll(snapshot.history)
                val plot = database.plotDao().getById(plotId)
                if (plot != null && plot.boundaryJson != snapshot.boundaryJson) {
                    plot.copy(boundaryJson = snapshot.boundaryJson).also { database.plotDao().update(it) }
                } else plot
            }
        }
                siteFeatures = snapshot.features
        historyState = snapshot.history
        if (restoredPlot != null) activePlot = restoredPlot
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

    fun snapshotNow() = CanvasSnapshot(nodesState, pathZonesState, siteFeatures, activePlot?.boundaryJson, historyState)
    val seedMap = remember(seedDictionary) { seedDictionary.associateBy { it.botanicalCode } }
    fun seedFor(code: String): SeedEntity? = seedMap[code]

    // Guilds apply only when switched on and the tier allows it (FR-009).
    val activeGuilds = PlotInsightsLoader.activeGuilds(settings)

    // FR-055: sun is judged over the plot's growing season (its frost dates), not on today's date.
    fun plotContext(plot: PlotEntity, nodes: List<PlantedNodeEntity> = nodesState): PlotContext =
        PlotContext(plot, nodes, siteFeatures, { code -> seedFor(code) }, activeGuilds, effectiveEnforceCompanionRules,
            SunlightEngine.dayOfYear(System.currentTimeMillis()),
            com.example.smartgardenplanner.core.GrowingSeason.sunDays(plot.latitude ?: SunlightEngine.DEFAULT_LATITUDE, growingSeason))

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

    // FR-048: (re)works out the rotation plan from its list and the user's variety changes, then shows year [index].
    fun replanRotation(index: Int) {
        val plot = activePlot ?: return
        val now = Seasons.currentSeason(nodesState, historyState)
        val history = historyState + (if (nodesState.isNotEmpty()) Seasons.archive(plotId, nodesState, now) { seedFor(it) } else emptyList())
        val ctx = plotContext(plot, emptyList())
        val base = rotationBase; val first = rotationFirst; val count = rotationCount; val changes = rotationChanges
        planRunning = true
        launchSafely {
            val plans = try { withContext(Dispatchers.Default) {
                RotationPlanner.planSeasons(ctx, PlotShape.effectiveOutline(plot), base, history, first, count, marginMultiplier = settings.spacingMarginMultiplier, orientationKnown = plot.orientationSet, changes = changes)
            } } finally { planRunning = false }
            rotationPlans = plans
            rotationIndex = index.coerceIn(0, (plans.size - 1).coerceAtLeast(0))
            autoPlanArea = PlotShape.effectiveOutline(plot)
            planPreview = plans.getOrNull(rotationIndex)?.result
        }
    }

    // Moves an obstacle or area so its anchor (a tree's trunk, otherwise the centre of its points) lands on
    // (x, y), keeping every point inside the plot. Undoable.
    fun moveSiteFeature(feature: SiteFeatureEntity, x: Float, y: Float) {
        val plot = activePlot ?: return
        val pts = PlotGeometry.parsePoints(feature.pointsJson)
        if (pts.isEmpty()) return
        val anchorX = if (feature.featureType == SiteFeatureType.TREE.name) pts[0].x else pts.map { it.x }.average().toFloat()
        val anchorY = if (feature.featureType == SiteFeatureType.TREE.name) pts[0].y else pts.map { it.y }.average().toFloat()
        val dx = (x - anchorX).coerceIn(-pts.minOf { it.x }, plot.lengthM - pts.maxOf { it.x })
        val dy = (y - anchorY).coerceIn(-pts.minOf { it.y }, plot.widthM - pts.maxOf { it.y })
        val moved = feature.copy(pointsJson = PlotGeometry.serializePoints(pts.map { PlotPoint(it.x + dx, it.y + dy) }))
        launchSafely {
            withContext(SgpExecutors.dbDispatcher) { database.siteFeatureDao().update(moved) }
            reloadFeatures()
            undoStack.push(snapshotNow())
            redoStack.clear()
            snackbarMessage = "Moved."
        }
    }

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
            undoStack.push(snapshotNow())
            redoStack.clear()
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
            val history = database.plantingHistoryDao().getByPlotId(plotId)
            val codesEverywhere = database.plantedNodeDao().getAllNodes().map { it.seedCode } + database.plantingHistoryDao().allCodes()
            seedDictionary = database.seedDao().getAllSeeds()
            val loadedSettings = settingsRepository.load() // [NEW]

            withContext(Dispatchers.Main) {
                settings = loadedSettings // [NEW]
                undoStack.updateLimit(loadedSettings.undoHistoryDepth) // [NEW]
                redoStack.updateLimit(loadedSettings.undoHistoryDepth) // [NEW]
                nodesState = list
                pathZonesState = paths
                                siteFeatures = features
                historyState = history
                allPlantedCodes = codesEverywhere
                undoStack.clear()
                redoStack.clear()
                undoStack.push(CanvasSnapshot(list, paths, features, activePlot?.boundaryJson, history))
            }
        }
    }

    // Estimated direct sun today across the plot (FR-006), computed off the main thread.
        LaunchedEffect(showShade, siteFeatures, activePlot, shadeDay, shadeHour, shadePlants, nodesState) {
        val plot = activePlot
        if (!showShade || plot == null) { shadeGrid = null; shadowGrid = null; return@LaunchedEffect }
        val lat = plot.latitude ?: SunlightEngine.DEFAULT_LATITUDE
        val day = shadeDay.dayOfYear(lat, SunlightEngine.dayOfYear(System.currentTimeMillis()), com.example.smartgardenplanner.core.GrowingSeason.midSeasonDay(lat, growingSeason))
        val cols = 30
        val rows = (cols * plot.widthM / plot.lengthM).toInt().coerceIn(4, 60)
        // FR-038: obstacles, plus planted crops at their mature height when switched on.
        val barriers = siteFeatures.mapNotNull { com.example.smartgardenplanner.core.Barrier.from(it) } +
            (if (shadePlants) ShadeTools.plantBarriers(nodesState, { seedFor(it) }) else emptyList())
        val hour = shadeHour
        withContext(Dispatchers.Default) {
            if (hour != null) {
                val g = ShadeTools.shadowGridAt(plot.lengthM, plot.widthM, cols, rows, lat, day, plot.northBearingDeg, barriers, hour)
                withContext(Dispatchers.Main) { shadowGrid = cols to g; shadeGrid = null }
            } else {
                val g = SunlightEngine.sunHoursGrid(plot.lengthM, plot.widthM, cols, rows, lat, day, plot.northBearingDeg, barriers)
                withContext(Dispatchers.Main) { shadeGrid = cols to g; shadowGrid = null }
            }
        }
    }
    LaunchedEffect(showWater, siteFeatures, activePlot) {
        val plot = activePlot
        waterGrid = if (showWater && plot != null) withContext(Dispatchers.Default) {
            val cols = 40
            val rows = (cols * plot.widthM / plot.lengthM).toInt().coerceIn(4, 80)
            cols to Irrigation.grid(plot, siteFeatures.filter { SiteFeatureType.of(it.featureType)?.isIrrigation == true }, cols, rows)
        } else null
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message: String ->
            snackbarHostState.showSnackbar(tr(message))
            snackbarMessage = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(activePlot?.name ?: tr("Loading Layout..."), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                            Text(tr("Canvas mode"), fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text(tr(if (canvasMode == CanvasMode.PLACE_NODE) "✓ Place plants" else "Place plants")) },
                                onClick = { canvasMode = CanvasMode.PLACE_NODE; showOptionsMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(tr(if (canvasMode == CanvasMode.DRAW_PATH) "✓ Draw / edit no-plant path" else "Draw / edit no-plant path")) },
                                onClick = { canvasMode = CanvasMode.DRAW_PATH; showOptionsMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(tr(if (canvasMode == CanvasMode.SELECT_AREA) "✓ Select area to auto-populate" else "Select area to auto-populate")) },
                                onClick = { canvasMode = CanvasMode.SELECT_AREA; showOptionsMenu = false }
                            )
                            if (canvasMode == CanvasMode.DRAW_PATH) {
                                Divider()
                                Text(tr("Path style"), fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text(tr(if (pathSubMode == PathDrawSubMode.RECTANGLE) "✓ Straight (drag rectangle)" else "Straight (drag rectangle)")) },
                                    onClick = { pathSubMode = PathDrawSubMode.RECTANGLE; inProgressPoints = emptyList(); showOptionsMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(tr(if (pathSubMode == PathDrawSubMode.POINTS) "✓ Curved (tap points)" else "Curved (tap points)")) },
                                    onClick = { pathSubMode = PathDrawSubMode.POINTS; showOptionsMenu = false }
                                )
                            }
                            // [NEW — FR-001] Area-select shape submenu, same pattern as Path style above.
                            if (canvasMode == CanvasMode.SELECT_AREA) {
                                Divider()
                                Text(tr("Area shape"), fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text(tr(if (areaSubMode == AreaSelectSubMode.RECTANGLE) "✓ Rectangle (drag)" else "Rectangle (drag)")) },
                                    onClick = { areaSubMode = AreaSelectSubMode.RECTANGLE; inProgressPoints = emptyList(); showOptionsMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(tr(if (areaSubMode == AreaSelectSubMode.POLYGON) "✓ Custom shape (tap points)" else "Custom shape (tap points)" + if (!Feature.isEnabled(Feature.POLYGON_AREA_SELECT, settings.currentAppTier())) " (Standard+)" else "")) },
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
                            Text(tr("Site tools"), fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            val tierNow = settings.currentAppTier()
                            fun lockLabel(f: Feature) = if (Feature.isEnabled(f, tierNow)) "" else " (${f.tierLabel}+)"
                            DropdownMenuItem(
                                text = { Text(tr((if (canvasMode == CanvasMode.OUTLINE) "✓ " else "") + "Draw plot outline" + lockLabel(Feature.POLYGON_PLOT_SHAPE))) },
                                onClick = {
                                    if (Feature.isEnabled(Feature.POLYGON_PLOT_SHAPE, tierNow)) {
                                        canvasMode = CanvasMode.OUTLINE; inProgressPoints = emptyList()
                                        snackbarMessage = "Tap the plot's corners in order, then Finish Outline."
                                    } else snackbarMessage = "Custom plot outlines need the Pro catalog tier (Settings → Catalog)."
                                    showOptionsMenu = false
                                }
                            )
                            if (activePlot?.boundaryJson != null) {
                                DropdownMenuItem(text = { Text(tr("Delete outline (back to the full rectangle)")) }, onClick = { saveOutline(null); movingOutlineCorner = null; showOptionsMenu = false; snackbarMessage = "Outline deleted. Undo brings it back." })
                            }
                            DropdownMenuItem(
                                text = { Text(tr((if (canvasMode == CanvasMode.SITE_AREA) "✓ " else "") + "Mark sun / shade / flood / slope area" + lockLabel(Feature.SUN_SHADE_ZONES))) },
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
                                        text = { Text(tr("   " + (if (siteAreaType == type) "✓ " else "") + type.label + lockLabel(needed)), fontSize = 13.sp) },
                                        onClick = {
                                            if (Feature.isEnabled(needed, tierNow)) { siteAreaType = type; inProgressPoints = emptyList() }
                                            else snackbarMessage = "${type.label} areas need the ${needed.tierLabel} catalog tier."
                                            showOptionsMenu = false
                                        }
                                    )
                                }
                            }
                            DropdownMenuItem(
                                text = { Text(tr((if (canvasMode == CanvasMode.BARRIER) "✓ " else "") + "Place tree / fence / wall / building" + lockLabel(Feature.SUNLIGHT_BARRIERS))) },
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
                                        text = { Text(tr("   " + (if (barrierType == type) "✓ " else "") + type.label), fontSize = 13.sp) },
                                        onClick = { barrierType = type; inProgressPoints = emptyList(); showOptionsMenu = false }
                                    )
                                }
                            }
                            DropdownMenuItem(text = { Text(tr("Plot direction and ZIP…")) }, onClick = { showOptionsMenu = false; showDirectionDialog = true })
                            DropdownMenuItem(
                                text = { Text(tr("Save this plot as a file…")) },
                                onClick = {
                                    showOptionsMenu = false
                                    exportLauncher.launch(com.example.smartgardenplanner.data.PlanFileIo.fileName(activePlot?.name ?: "garden"))
                                }
                            )
                            DropdownMenuItem(text = { Text(tr("Plot insights (site, harmony, care, food)…")) }, onClick = { showOptionsMenu = false; onOpenInsights() })
                            // FR-033: close this season; plants become history, the fence, buildings, trees, paths and areas stay.
                                                        DropdownMenuItem(
                                text = { Text(tr("Start a new season (empty)…")) },
                                enabled = nodesState.isNotEmpty(),
                                onClick = { showOptionsMenu = false; showNewSeasonDialog = true }
                            )
                            // FR-037: re-plan the whole plot for next year with the same crops, rotated.
                            DropdownMenuItem(
                                text = { Text(tr("Plan next season (rotate)…")) },
                                enabled = nodesState.isNotEmpty() || historyState.isNotEmpty(),
                                onClick = {
                                    showOptionsMenu = false
                                    activePlot?.let { plot ->
                                        nextSeasonMode = true
                                        planRows.clear()
                                        RotationPlanner.lastList(nodesState, historyState) { seedFor(it) }.forEach { planRows.add(it.seed.botanicalCode to it.count) }
                                        planForMe = true
                                        autoPlanArea = PlotShape.effectiveOutline(plot)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(tr("Rotation plan for several seasons…")) },
                                enabled = nodesState.isNotEmpty() || historyState.isNotEmpty(),
                                onClick = { showOptionsMenu = false; showRotationDialog = true }
                            )
                            val viewYears = Seasons.years(historyState)
                            if (viewYears.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(tr("Season shown: " + (viewSeasonYear?.let { "$it (read only)" } ?: "${Seasons.currentSeason(nodesState, historyState)} (planning)") + "  (tap to change)")) },
                                    onClick = {
                                        val options = listOf<Int?>(null) + viewYears
                                        viewSeasonYear = options[(options.indexOf(viewSeasonYear) + 1) % options.size]
                                        planPreview = null
                                    }
                                )
                            }
                            // FR-041: copy this plot as a template.
                            DropdownMenuItem(text = { Text(tr("Duplicate this plot…")) }, onClick = { showOptionsMenu = false; showDuplicateDialog = true })
                            // FR-046: a satellite photo under the plot, to trace trees, fences and buildings.
                            DropdownMenuItem(text = { Text(tr((if (canvasMode == CanvasMode.PHOTO) "✓ " else "") + "Satellite photo…")) }, onClick = { showOptionsMenu = false; showPhotoDialog = true })
                            DropdownMenuItem(text = { Text(tr("Open in Google Maps" + (activePlot?.address?.let { " ($it)" } ?: ""))) }, onClick = {
                                showOptionsMenu = false
                                val a = activePlot?.address
                                if (a.isNullOrBlank() && activePlot?.latitude == null) showPhotoDialog = true else openInMaps(a.orEmpty())
                            })
                            // FR-039: irrigation.
                            Text(tr("Irrigation"), fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            listOf(SiteFeatureType.SPRINKLER, SiteFeatureType.DRIP_LINE, SiteFeatureType.HOSE_BIB).forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(tr((if (canvasMode == CanvasMode.BARRIER && barrierType == type) "✓ " else "") + "Place " + type.label.lowercase()), fontSize = 13.sp) },
                                    onClick = { canvasMode = CanvasMode.BARRIER; barrierType = type; inProgressPoints = emptyList(); showOptionsMenu = false }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(tr((if (showWater) "✓ " else "") + "Show water map (what gets watered)")) },
                                onClick = { showWater = !showWater; showOptionsMenu = false }
                            )
                            Divider()
                            // [NEW] Wires WeedMaskGeometryEngine and IrrigationRouteCalculator into the
                            // UI for the first time — both existed as tested engines with nothing calling them.
                            Text(tr("Overlays"), fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text(tr(if (showSiteFeatures) "✓ Show site areas and barriers" else "Show site areas and barriers")) },
                                onClick = { showSiteFeatures = !showSiteFeatures; showOptionsMenu = false }
                            )
                            DropdownMenuItem(
                                                                text = { Text(tr((if (showShade) "✓ " else "") + "Show sun and shade" + lockLabel(Feature.SUNLIGHT_BARRIERS))) },
                                onClick = {
                                    if (Feature.isEnabled(Feature.SUNLIGHT_BARRIERS, tierNow)) showShade = !showShade
                                    else snackbarMessage = "Shade estimates need the Pro catalog tier (Settings → Catalog)."
                                    showOptionsMenu = false
                                }
                            )
                                                        DropdownMenuItem(
                                text = { Text(tr((if (settings.showPlantLabels) "✓ " else "") + "Show plant names (sweet/hot, cherry/large…)")) },
                                onClick = {
                                    val updated = settings.copy(showPlantLabels = !settings.showPlantLabels)
                                    settings = updated
                                    launchSafely { withContext(SgpExecutors.dbDispatcher) { settingsRepository.save(updated) } }
                                    showOptionsMenu = false
                                }
                            )
                            val seasonYears = Seasons.years(historyState)
                            if (seasonYears.isNotEmpty()) {
                                // FR-033: tap to cycle through past seasons (shown dashed under this season's plants).
                                DropdownMenuItem(
                                    text = { Text(tr("Past season on layout: " + (historyYear?.toString() ?: "none") + "  (tap to change)")) },
                                    onClick = {
                                        val options = listOf<Int?>(null) + seasonYears
                                        historyYear = options[(options.indexOf(historyYear) + 1) % options.size]
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(tr(if (showWeedMask) "✓ Show weed-risk mask" else "Show weed-risk mask")) },
                                onClick = { showWeedMask = !showWeedMask; showOptionsMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(tr(if (showIrrigationRoute) "✓ Show irrigation route" else "Show irrigation route")) },
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
                                tr(if (placedSeedCodes.isEmpty()) "Legend (nothing placed yet)" else "Legend (this plot)"),
                                fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                                                        if (placedSeedCodes.isNotEmpty()) Text(tr("Tap a variety to find it on the layout"), fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp))
                            placedSeedCodes.mapNotNull { seedFor(it) }.forEach { seed ->
                                val count = nodesState.count { it.seedCode == seed.botanicalCode }
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(12.dp).background(VegetableColorPalette.colorFor(seed), shape = androidx.compose.foundation.shape.CircleShape))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(tr((if (findCode == seed.botanicalCode) "◉ " else "") + "$count × ${seed.commonName}"), fontSize = 12.sp)
                                                VarietyCatalogTraits.of(seed)?.let { Text(tr(it.details), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary) }
                                            }
                                        }
                                    },
                                    onClick = {
                                        findCode = if (findCode == seed.botanicalCode) null else seed.botanicalCode
                                        showOptionsMenu = false
                                        if (findCode != null) snackbarMessage = "Showing $count × ${seed.commonName} (circled in orange)."
                                    },
                                    // FR-051: change all plants of this variety at once.
                                    trailingIcon = {
                                        TextButton(onClick = { showOptionsMenu = false; replaceFromCode = seed.botanicalCode }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(tr("Replace…"), fontSize = 11.sp) }
                                    }
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
                    ) { Text(tr("Undo"), fontSize = 12.sp) }
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
                    ) { Text(tr("Redo"), fontSize = 12.sp) }
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
                                tr("Plot Layout Size: ${DistanceFormatter.format(state.lengthM, settings.distanceUnit)} × ${DistanceFormatter.format(state.widthM, settings.distanceUnit)}"),
                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // [NEW] Quick unit toggle right here — no need to open Settings just to
                            // switch between meters and inches.
                            Text(
                                tr(if (settings.distanceUnit == DistanceUnit.METERS) "[in]" else "[m]"),
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
                        Text(tr("Active Plantings: ${nodesState.size} nodes placed"), color = Color.Gray, fontSize = 11.sp)
                        // FR-028: the plot's compass direction drives sun, shade and planting direction; ask until it's set.
                        if (!state.orientationSet) {
                            Text(
                                tr("⚠ Set which way the plot faces (needed for sun and shade)"),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEAB308),
                                modifier = Modifier.clickable { showDirectionDialog = true }.padding(vertical = 2.dp)
                            )
                        } else {
                            Text(
                                tr("Top edge faces ${com.example.smartgardenplanner.ui.compassName(state.northBearingDeg)}" + (state.locationZip?.let { " • ZIP $it" } ?: "")),
                                fontSize = 11.sp, color = Color.Gray,
                                modifier = Modifier.clickable { showDirectionDialog = true }
                            )
                        }
                        // FR-009: highly visible guild state; tap to switch (Pro).
                        if (Feature.isEnabled(Feature.INTERPLANTING_GUILDS, settings.currentAppTier())) {
                            val guildColor = if (settings.guildsEnabled) Color(0xFF10B981) else Color(0xFFF97316)
                            Text(
                                tr(if (settings.guildsEnabled) "GUILDS ON — partners may be planted closer" else "GUILDS OFF — tap to turn on"),
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
                            Text(tr("Now placing: ${active.commonName} (${DistanceFormatter.format(active.exclusionRadiusM, settings.distanceUnit)} radius)"), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    AssistChip(
                        onClick = { showOptionsMenu = true },
                        label = {
                            Text(
                                tr(when (canvasMode) {
                                    CanvasMode.PLACE_NODE -> "Placing plants"
                                    CanvasMode.DRAW_PATH -> if (pathSubMode == PathDrawSubMode.RECTANGLE) "Drawing straight path" else "Drawing curved path"
                                        CanvasMode.SELECT_AREA -> "Selecting area"
                                        CanvasMode.OUTLINE -> "Drawing plot outline"
                                        CanvasMode.SITE_AREA -> "Marking: ${siteAreaType.label}"
                                        CanvasMode.BARRIER -> "Placing: ${barrierType.label}"
                                        CanvasMode.PHOTO -> if (photoCalibrating) "Setting photo scale" else "Moving the photo"
                                    }),
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
                            Text(tr(if (active != null) "Change Variety (${active.commonName})" else "Choose a Variety to Place"))
                        }
                        // FR-027: the app decides where everything goes.
                        Button(
                            onClick = {
                                                                planForMe = true
                                // FR-034: start from the last list used, else the varieties this gardener usually plants.
                                if (planRows.isEmpty()) {
                                    val remembered = settings.lastPlanRows.filter { seedFor(it.first) != null }
                                    if (remembered.isNotEmpty()) planRows.addAll(remembered)
                                    else Seasons.usualVarieties(allPlantedCodes, emptyList(), { seedFor(it) }, 4).forEach { planRows.add(it.first.botanicalCode to 3) }
                                }
                                canvasMode = CanvasMode.SELECT_AREA
                                inProgressPoints = emptyList()
                                snackbarMessage = "Drag over the area you want planted (or tap its corners in Custom shape mode)."
                            },
                            modifier = Modifier.fillMaxWidth()
                                                ) { Text(tr("✨ Plan an area for me")) }
                        // FR-040: fill the whole plot this year.
                        OutlinedButton(
                            onClick = {
                                activePlot?.let { plot ->
                                    if (planRows.isEmpty()) {
                                        val remembered = settings.lastPlanRows.filter { seedFor(it.first) != null }
                                        if (remembered.isNotEmpty()) planRows.addAll(remembered)
                                        else Seasons.usualVarieties(allPlantedCodes, emptyList(), { seedFor(it) }, 4).forEach { planRows.add(it.first.botanicalCode to 3) }
                                    }
                                    planForMe = true
                                    autoPlanArea = PlotShape.effectiveOutline(plot)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(tr("Fill the whole plot…")) }
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
                                    .background(Color(LayoutPalette.PAPER))
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

                                                                                                                if (viewSeasonYear != null) { snackbarMessage = "You are looking at season $viewSeasonYear (read only). Switch back to the planning season in the menu."; return@detectTapGestures }
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
                                                            // FR-032: planting is never blocked for rotation, but the gardener is told.
                                                            val rotationHit = CropRotation.conflict(realXM, realYM, candidateSeed, historyState, Seasons.currentSeason(nodesState, historyState))
                                                            launchSafely {
                                                                withContext(SgpExecutors.dbDispatcher) { database.plantedNodeDao().insert(candidateNode) }
                                                                reloadNodes()
                                                                undoStack.push(snapshotNow())
                                                                redoStack.clear()
                                                                allPlantedCodes = allPlantedCodes + candidateNode.seedCode
                                                                rotationHit?.let { snackbarMessage = "Rotation note: " + CropRotation.warning(it, CropReference.speciesName(candidateSeed)) }
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
                                                        if (viewSeasonYear != null) { snackbarMessage = "You are looking at season $viewSeasonYear (read only)."; return@detectTapGestures }
                                                        movingSiteFeature?.let { moving ->
                                                            moveSiteFeature(moving, realXM, realYM)
                                                            movingSiteFeature = null
                                                            return@detectTapGestures
                                                        }
                                                        // FR-002: edit an existing outline — tap a corner, then tap where it should go.
                                                        val currentOutline = activePlot?.let { PlotShape.outline(it) }.orEmpty()
                                                        if (canvasMode == CanvasMode.OUTLINE && inProgressPoints.isEmpty() && currentOutline.size >= 3) {
                                                            val moving = movingOutlineCorner
                                                            if (moving != null) {
                                                                val moved = currentOutline.toMutableList().also { it[moving] = PlotPoint(realXM.coerceIn(0f, state.lengthM), realYM.coerceIn(0f, state.widthM)) }
                                                                movingOutlineCorner = null
                                                                saveOutline(moved.map { Offset(it.x, it.y) })
                                                                return@detectTapGestures
                                                            }
                                                            val grab = (0.12f * kotlin.math.max(state.lengthM, state.widthM) / 4f).coerceIn(0.25f, 1.5f)
                                                            val nearest = currentOutline.indices.minByOrNull { i -> (currentOutline[i].x - realXM) * (currentOutline[i].x - realXM) + (currentOutline[i].y - realYM) * (currentOutline[i].y - realYM) }
                                                            if (nearest != null && kotlin.math.hypot((currentOutline[nearest].x - realXM).toDouble(), (currentOutline[nearest].y - realYM).toDouble()) <= grab) {
                                                                movingOutlineCorner = nearest
                                                                snackbarMessage = "Corner selected. Tap where it should go."
                                                                return@detectTapGestures
                                                            }
                                                        }
                                                        if (inProgressPoints.isEmpty() && canvasMode != CanvasMode.OUTLINE) {
                                                            val wantBarrier = canvasMode == CanvasMode.BARRIER
                                                                                                                        val hit = siteFeatures.firstOrNull { f -> SiteFeatureType.of(f.featureType)?.let { it.isBarrier || it.isIrrigation } == wantBarrier && featureHit(f, realXM, realYM) }
                                                            if (hit != null) {
                                                                editingSiteFeature = hit
                                                                return@detectTapGestures
                                                            }
                                                        }
                                                                                                                if (canvasMode == CanvasMode.BARRIER && (barrierType == SiteFeatureType.TREE || barrierType == SiteFeatureType.SPRINKLER || barrierType == SiteFeatureType.HOSE_BIB)) {
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
                                                                        undoStack.push(snapshotNow())
                                                                        redoStack.clear()
                                                                    }
                                                                } else if (canvasMode == CanvasMode.SELECT_AREA) {
                                                                    if (planForMe) {
                                                                        autoPlanArea = listOf(PlotPoint(xMin, yMin), PlotPoint(xMin + wM, yMin), PlotPoint(xMin + wM, yMin + hM), PlotPoint(xMin, yMin + hM))
                                                                    } else {
                                                                        pendingAreaSelection = androidx.compose.ui.geometry.Rect(xMin, yMin, xMin + wM, yMin + hM)
                                                                    }
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
                                                        // FR-061: dragging a plant of the group being moved moves the whole group.
                                                        if (id != null && preview != null && id in groupMoveIds) {
                                                            val node = nodesState.find { it.id == id }
                                                            val plot = activePlot
                                                            if (node != null && plot != null) {
                                                                val dx = (preview.x / size.width) * state.lengthM - node.coordinateXM
                                                                val dy = (preview.y / size.height) * state.widthM - node.coordinateYM
                                                                val group = nodesState.filter { it.id in groupMoveIds }
                                                                val moved = com.example.smartgardenplanner.core.GroupTools.moved(group, dx, dy)
                                                                val others = nodesState.filter { it.id !in groupMoveIds }
                                                                val bad = com.example.smartgardenplanner.core.GroupTools.problems(moved, others, plot, { seedFor(it) }, settings.spacingMarginMultiplier, effectiveEnforceCompanionRules, activeGuilds) +
                                                                    moved.count { n -> isInsidePath(n.coordinateXM, n.coordinateYM, seedFor(n.seedCode)?.exclusionRadiusM ?: 0.3f) }
                                                                if (bad > 0) snackbarMessage = tr("Can't move the group there: $bad plant(s) would be outside the plot, on a path, or too close to other plants.")
                                                                else launchSafely {
                                                                    withContext(SgpExecutors.dbDispatcher) { database.withTransaction { moved.forEach { database.plantedNodeDao().update(it) } } }
                                                                    reloadNodes(); undoStack.push(snapshotNow()); redoStack.clear()
                                                                    snackbarMessage = tr("Moved ${moved.size} plants. Future rotation plans start from where they are now.")
                                                                }
                                                            }
                                                            draggingNodeId = null; dragPreviewOffset = null
                                                            return@detectDragGestures
                                                        }
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
                                                                                                                                                val rotationHit = CropRotation.conflict(realXM, realYM, seed, historyState, Seasons.currentSeason(nodesState, historyState))
                                                                        launchSafely {
                                                                            withContext(SgpExecutors.dbDispatcher) { database.plantedNodeDao().update(candidate) }
                                                                            reloadNodes()
                                                                            undoStack.push(snapshotNow())
                                                                            redoStack.clear()
                                                                            rotationHit?.let { snackbarMessage = "Moved. Rotation note: " + CropRotation.warning(it, CropReference.speciesName(seed)) }
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
                                        // FR-046: satellite photo — two taps set the scale; otherwise dragging moves the photo.
                                        .pointerInput(canvasMode, photoCalibrating, zoomPanModeEnabled) {
                                            if (canvasMode == CanvasMode.PHOTO && !zoomPanModeEnabled) {
                                                if (photoCalibrating) {
                                                    detectTapGestures(onTap = { offset ->
                                                        if (photoPoints.size < 2) photoPoints.add(Offset((offset.x / size.width) * state.lengthM, (offset.y / size.height) * state.widthM))
                                                    })
                                                } else {
                                                    detectDragGestures(
                                                        onDrag = { change, amount ->
                                                            change.consume()
                                                            photoDrag += Offset(amount.x / size.width * state.lengthM, amount.y / size.height * state.widthM)
                                                        },
                                                        onDragEnd = {
                                                            val d = photoDrag
                                                            photoDrag = Offset.Zero
                                                            savePhotoPlacement { it.moved(d.x, d.y) }
                                                        },
                                                        onDragCancel = { photoDrag = Offset.Zero }
                                                    )
                                                }
                                            }
                                        }
                                ) {
                                    val canvasW = size.width
                                    val canvasH = size.height
                                    val scaleX = canvasW / state.lengthM
                                    val scaleY = canvasH / state.widthM

                                    // FR-046: satellite photo under everything, placed in plot metres.
                                    val photo = photoBitmap
                                    val placement = com.example.smartgardenplanner.core.Backdrop.parse(state.backdropJson)
                                    if (photo != null && placement != null && placement.visible) {
                                        val nc = drawContext.canvas.nativeCanvas
                                        nc.save()
                                        nc.scale(scaleX, scaleY)
                                        nc.translate(placement.xM + photoDrag.x, placement.yM + photoDrag.y)
                                        nc.rotate(placement.rotationDeg, placement.widthM / 2f, placement.heightM / 2f)
                                        nc.drawBitmap(photo, null, android.graphics.RectF(0f, 0f, placement.widthM, placement.heightM),
                                            android.graphics.Paint().apply { alpha = (placement.opacity * 255).toInt(); isFilterBitmap = true })
                                        nc.restore()
                                    }
                                    if (canvasMode == CanvasMode.PHOTO) photoPoints.forEach { p ->
                                        drawCircle(Color(0xFF10B981), radius = 9f, center = Offset(p.x * scaleX, p.y * scaleY))
                                    }
                                    if (photoPoints.size == 2) drawLine(Color(0xFF10B981), Offset(photoPoints[0].x * scaleX, photoPoints[0].y * scaleY), Offset(photoPoints[1].x * scaleX, photoPoints[1].y * scaleY), strokeWidth = 4f)

                                    var gridX = 0f
                                    while (gridX < canvasW) {
                                        drawLine(color = Color(LayoutPalette.GRID), start = Offset(gridX, 0f), end = Offset(gridX, canvasH), strokeWidth = 1f)
                                        gridX += scaleX
                                    }
                                    var gridY = 0f
                                    while (gridY < canvasH) {
                                        drawLine(color = Color(LayoutPalette.GRID), start = Offset(0f, gridY), end = Offset(canvasW, gridY), strokeWidth = 1f)
                                        gridY += scaleY
                                    }

                                    // No-plant path zones
                                    pathZonesState.forEach { zone ->
                                        if (zone.pathType == "POLYLINE") {
                                            val points = parsePoints(zone.pointsJson).map { Offset(it.x * scaleX, it.y * scaleY) }
                                            val strokeWidthPx = zone.widthM * scaleX
                                            for (i in 0 until points.size - 1) {
                                                drawLine(
                                                    color = Color(LayoutPalette.PATH_FILL), start = points[i], end = points[i + 1],
                                                    strokeWidth = strokeWidthPx, cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            }
                                        } else {
                                            val rectTopLeft = Offset(zone.xM * scaleX, zone.yM * scaleY)
                                            val rectSize = androidx.compose.ui.geometry.Size(zone.widthM * scaleX, zone.heightM * scaleY)
                                            drawRect(color = Color(LayoutPalette.PATH_FILL), topLeft = rectTopLeft, size = rectSize)
                                            drawRect(color = Color(LayoutPalette.PATH_EDGE), topLeft = rectTopLeft, size = rectSize, style = Stroke(width = 2f))
                                        }
                                    }

                                    // FR-006 overlay: estimated direct sun today, in the shared SunBand colours
                                    // (yellow = full sun, blue = part shade, indigo = shade) on the light layout.
                                    shadeGrid?.let { (cols, grid) ->
                                        val rows = grid.size / cols
                                        val cellW = canvasW / cols
                                        val cellH = canvasH / rows
                                        for (r in 0 until rows) for (c in 0 until cols) {
                                                                                        val band = SunBand.of(grid[r * cols + c])
                                            drawRect(Color(band.overlayArgb), topLeft = Offset(c * cellW, r * cellH), size = androidx.compose.ui.geometry.Size(cellW + 1f, cellH + 1f))
                                        }
                                    }
                                    // FR-038: shade at the chosen time of day.
                                    shadowGrid?.let { (cols, grid) ->
                                        val rows = grid.size / cols
                                        val cellW = canvasW / cols
                                        val cellH = canvasH / rows
                                        for (r in 0 until rows) for (c in 0 until cols) if (grid[r * cols + c]) {
                                            drawRect(Color(0x8C312E81), topLeft = Offset(c * cellW, r * cellH), size = androidx.compose.ui.geometry.Size(cellW + 1f, cellH + 1f))
                                        }
                                    }
                                    // FR-039: where sprinklers, drip lines and hoses reach.
                                    waterGrid?.let { (cols, grid) ->
                                        val rows = grid.size / cols
                                        val cellW = canvasW / cols
                                        val cellH = canvasH / rows
                                        for (i in grid.indices) {
                                            val src = grid[i]
                                            if (src == WaterSource.MANUAL) continue
                                            drawRect(Color(src.argb), topLeft = Offset((i % cols) * cellW, (i / cols) * cellH), size = androidx.compose.ui.geometry.Size(cellW + 1f, cellH + 1f))
                                        }
                                    }

                                    // FR-003/004/005/006: marked site areas and shade-casting barriers.
                                    if (showSiteFeatures) {
                                        siteFeatures.forEach { f ->
                                            val type = SiteFeatureType.of(f.featureType) ?: return@forEach
                                            val pts = PlotGeometry.parsePoints(f.pointsJson).map { Offset(it.x * scaleX, it.y * scaleY) }
                                            if (pts.isEmpty()) return@forEach
                                            if (type.isArea && pts.size >= 3) {
                                                val (fill, edge) = LayoutPalette.area(type).let { Color(it.first) to Color(it.second) }
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
                                                                                        } else if (type.isIrrigation) {
                                                val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                                                when (type) {
                                                    SiteFeatureType.SPRINKLER -> {
                                                        val r = (if (f.radiusM > 0f) f.radiusM else Irrigation.DEFAULT_THROW_M) * scaleX
                                                        val arc = if (f.slopeGradePct <= 0f || f.slopeGradePct >= 360f) 360f else f.slopeGradePct
                                                        if (arc >= 360f) drawCircle(Color(0xFF2563EB), radius = r, center = pts[0], style = Stroke(width = 2f, pathEffect = dash))
                                                        else {
                                                            // Compass bearing → canvas angle (0° = +x, clockwise): bearing − plot bearing − 90.
                                                            val start = f.slopeDirectionDeg - (activePlot?.northBearingDeg ?: 0f) - 90f - arc / 2f
                                                            drawArc(Color(0x183B82F6), start, arc, true, topLeft = Offset(pts[0].x - r, pts[0].y - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r))
                                                            drawArc(Color(0xFF2563EB), start, arc, true, topLeft = Offset(pts[0].x - r, pts[0].y - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = Stroke(width = 2f, pathEffect = dash))
                                                        }
                                                        drawCircle(Color(0xFF2563EB), radius = 9f, center = pts[0])
                                                    }
                                                    SiteFeatureType.DRIP_LINE -> for (i in 0 until pts.size - 1) {
                                                        drawLine(Color(0xFF0369A1), pts[i], pts[i + 1], strokeWidth = 5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2f, 10f)), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                                    }
                                                    else -> {
                                                        val len = (if (f.radiusM > 0f) f.radiusM else Irrigation.DEFAULT_HOSE_M) * scaleX
                                                        drawCircle(Color(0x996366F1), radius = len, center = pts[0], style = Stroke(width = 1.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 12f))))
                                                        drawRect(Color(0xFF6366F1), topLeft = Offset(pts[0].x - 9f, pts[0].y - 9f), size = androidx.compose.ui.geometry.Size(18f, 18f))
                                                    }
                                                }
                                            } else if (type == SiteFeatureType.TREE) {
                                                if (shadeGrid == null) drawCircle(Color(LayoutPalette.TREE_FILL), radius = f.radiusM.coerceAtLeast(0.2f) * scaleX, center = pts[0])
                                                drawCircle(Color(LayoutPalette.TREE_EDGE), radius = f.radiusM.coerceAtLeast(0.2f) * scaleX, center = pts[0], style = Stroke(width = 2f))
                                                drawCircle(Color(LayoutPalette.TRUNK), radius = 7f, center = pts[0])
                                            } else if (type.isBarrier) {
                                                val barrierColor = when (type) {
                                                    SiteFeatureType.FENCE -> Color(LayoutPalette.FENCE)
                                                    SiteFeatureType.WALL -> Color(LayoutPalette.WALL)
                                                    else -> Color(LayoutPalette.BUILDING_EDGE)
                                                }
                                                val closed = type == SiteFeatureType.BUILDING && pts.size >= 3
                                                if (closed) {
                                                    val body = androidx.compose.ui.graphics.Path().apply {
                                                        moveTo(pts[0].x, pts[0].y)
                                                        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                                                        close()
                                                    }
                                                    drawPath(body, Color(LayoutPalette.BUILDING_FILL))
                                                }
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
                                            drawPath(outside, Color(LayoutPalette.OUTSIDE_OUTLINE))
                                                                                        drawPath(outlinePath, Color(LayoutPalette.BORDER), style = Stroke(width = 3f))
                                            if (canvasMode == CanvasMode.OUTLINE && inProgressPoints.isEmpty()) outline.forEachIndexed { i, v ->
                                                drawCircle(Color.White, radius = 14f, center = v)
                                                drawCircle(if (movingOutlineCorner == i) Color(0xFFF97316) else Color(LayoutPalette.BORDER), radius = 14f, center = v, style = Stroke(width = 4f))
                                            }
                                        }
                                    }

                                    // FR-028: compass rose (top-right) with four arrowheads and N/E/S/W, turned to the plot's
                                    // direction. North is red once the direction is set, grey with "N?" until then.
                                    activePlot?.let { plot ->
                                        val r = 62f
                                        val cx = canvasW - r - 14f
                                        val cy = r + 14f
                                        drawCircle(Color(0xE6FFFFFF), radius = r * 1.1f, center = Offset(cx, cy))
                                        drawCircle(Color(LayoutPalette.BORDER), radius = r * 1.1f, center = Offset(cx, cy), style = Stroke(width = 2f))
                                        val letterPaint = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
                                        listOf(0f to "N", 90f to "E", 180f to "S", 270f to "W").forEach { (deg, letter) ->
                                            val a = Math.toRadians((deg - plot.northBearingDeg).toDouble())
                                            fun pt(dist: Float, side: Float) = Offset(
                                                cx + (kotlin.math.sin(a) * dist + kotlin.math.cos(a) * side).toFloat(),
                                                cy + (-kotlin.math.cos(a) * dist + kotlin.math.sin(a) * side).toFloat()
                                            )
                                            val north = letter == "N"
                                            val ink = if (north && plot.orientationSet) Color(0xFFDC2626) else if (north) Color.Gray else Color(LayoutPalette.INK)
                                            val tip = pt(r * 1.02f, 0f); val base = pt(r * 0.6f, 0f)
                                            val left = androidx.compose.ui.graphics.Path().apply { moveTo(tip.x, tip.y); pt(r * 0.52f, -r * 0.2f).let { lineTo(it.x, it.y) }; lineTo(base.x, base.y); close() }
                                            val right = androidx.compose.ui.graphics.Path().apply { moveTo(tip.x, tip.y); pt(r * 0.52f, r * 0.2f).let { lineTo(it.x, it.y) }; lineTo(base.x, base.y); close() }
                                            drawPath(left, ink)
                                            drawPath(right, Color.White)
                                            drawPath(right, ink, style = Stroke(width = 2f))
                                            val lp = pt(r * 0.34f, 0f)
                                            letterPaint.color = ink.toArgb()
                                            letterPaint.textSize = if (north) 30f else 24f
                                            drawContext.canvas.nativeCanvas.drawText(if (north && !plot.orientationSet) "N?" else letter, lp.x, lp.y + letterPaint.textSize * 0.36f, letterPaint)
                                        }
                                    }

                                                                        // FR-039: plants no sprinkler, drip line or hose reaches get a red dashed ring.
                                    if (showWater && viewSeasonYear == null) {
                                        val irrigation = siteFeatures.filter { SiteFeatureType.of(it.featureType)?.isIrrigation == true }
                                        if (irrigation.isNotEmpty()) activePlot?.let { plot ->
                                            Irrigation.plants(plot, nodesState, irrigation) { seedFor(it) }.filter { it.source == WaterSource.MANUAL }.forEach { pw ->
                                                drawCircle(Color(0xFFDC2626), radius = (pw.seed?.exclusionRadiusM ?: 0.3f) * scaleX + 8f, center = Offset(pw.node.coordinateXM * scaleX, pw.node.coordinateYM * scaleY),
                                                    style = Stroke(width = 4f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
                                            }
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

                                    // FR-027 preview: faded circles where the planner would put each plant.
                                    planPreview?.placed?.forEach { p ->
                                        val c = Offset(p.x * scaleX, p.y * scaleY)
                                        val colour = VegetableColorPalette.colorFor(p.seed)
                                        drawCircle(colour.copy(alpha = 0.25f), radius = p.seed.exclusionRadiusM * scaleX, center = c)
                                        drawCircle(colour.copy(alpha = 0.9f), radius = p.seed.exclusionRadiusM * scaleX, center = c, style = Stroke(width = 2f))
                                        drawCircle(colour, radius = 6f, center = c)
                                    }
                                                                        // FR-035: where vines are expected to run (toward the sun), dashed area + arrow.
                                    planPreview?.guides?.forEach { g ->
                                        val path = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(g.area[0].x * scaleX, g.area[0].y * scaleY)
                                            g.area.drop(1).forEach { lineTo(it.x * scaleX, it.y * scaleY) }
                                            close()
                                        }
                                        drawPath(path, Color(0x1416A34A))
                                        drawPath(path, Color(0xFF16A34A), style = Stroke(width = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                                        val from = Offset(g.from.x * scaleX, g.from.y * scaleY); val to = Offset(g.to.x * scaleX, g.to.y * scaleY)
                                        drawLine(Color(0xFF16A34A), from, to, strokeWidth = 5f)
                                        val ang = kotlin.math.atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
                                        listOf(2.6, -2.6).forEach { d -> drawLine(Color(0xFF16A34A), to, Offset(to.x + (18 * kotlin.math.cos(ang + d)).toFloat(), to.y + (18 * kotlin.math.sin(ang + d)).toFloat()), strokeWidth = 5f) }
                                    }
                                    autoPlanArea?.let { a ->
                                        if (a.size >= 3) {
                                            val areaPath = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(a[0].x * scaleX, a[0].y * scaleY)
                                                for (i in 1 until a.size) lineTo(a[i].x * scaleX, a[i].y * scaleY)
                                                close()
                                            }
                                            drawPath(areaPath, Color(0xFF10B981), style = Stroke(width = 3f))
                                        }
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

                                                                        // FR-033: a past season, dashed and faded, under this season's plants.
                                    historyYear?.let { year ->
                                        val ghostPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(87, 83, 78); textSize = 22f; isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; textSkewX = -0.2f }
                                        historyState.filter { it.seasonYear == year }.forEach { h ->
                                            val c = Offset(h.coordinateXM * scaleX, h.coordinateYM * scaleY)
                                            drawCircle(Color(0xFF57534E), radius = h.radiusM * scaleX, center = c, style = Stroke(width = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
                                            if (settings.showPlantLabels) drawContext.canvas.nativeCanvas.drawText(h.speciesName, c.x, c.y + 8f, ghostPaint)
                                        }
                                    }
                                    // FR-031: short names under plants ("Bell red", "Cherry red", "Spring"), with a halo so they read over shade.
                                    val labelHalo = android.graphics.Paint().apply { color = android.graphics.Color.rgb(245, 241, 230); textSize = 24f; isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; style = android.graphics.Paint.Style.STROKE; strokeWidth = 6f }
                                    val labelPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(41, 37, 36); textSize = 24f; isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
                                                                        // FR-037: a past season shown read-only, drawn like plants.
                                    viewSeasonYear?.let { year ->
                                        historyState.filter { it.seasonYear == year }.forEach { h ->
                                            val c = Offset(h.coordinateXM * scaleX, h.coordinateYM * scaleY)
                                            val sd = seedFor(h.seedCode)
                                            val col = VegetableColorPalette.colorFor(sd)
                                            drawCircle(col.copy(alpha = 0.22f), radius = h.radiusM * scaleX, center = c)
                                            drawCircle(col, radius = h.radiusM * scaleX, center = c, style = Stroke(width = 2f))
                                            drawCircle(sd?.let { VarietyCatalogTraits.dotArgb(it) }?.let { Color(it) } ?: col, radius = 10f, center = c)
                                            val tag = sd?.let { VarietyCatalogTraits.of(it)?.tag ?: CropReference.speciesName(it) } ?: h.speciesName
                                            drawContext.canvas.nativeCanvas.drawText(tag, c.x, c.y + 34f, labelHalo)
                                            drawContext.canvas.nativeCanvas.drawText(tag, c.x, c.y + 34f, labelPaint)
                                        }
                                    }
                                    val replacingSeason = viewSeasonYear != null || (planPreview != null && (nextSeasonMode || rotationPlans.isNotEmpty()))
                                    if (!replacingSeason) nodesState.forEach { node: PlantedNodeEntity ->
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
                                        val found = findCode != null && node.seedCode == findCode
                                        val alpha = if (isDragging) 0.6f else if (findCode != null && !found) 0.25f else 1.0f
                                        if (found) drawCircle(Color(0xFFF97316), radius = exclusionRadiusPx + 10f, center = centerOffset, style = Stroke(width = 6f))

                                        drawCircle(color = VegetableColorPalette.exclusionRingColorFor(seed).copy(alpha = VegetableColorPalette.exclusionRingColorFor(seed).alpha * alpha), radius = exclusionRadiusPx, center = centerOffset)
                                        drawCircle(color = baseColor.copy(alpha = 0.8f * alpha), radius = exclusionRadiusPx, center = centerOffset, style = Stroke(width = 2f))
                                                                                // Centre dot in the ripe fruit colour when known (red vs yellow bell pepper), FR-031.
                                        val dotColor = seed?.let { VarietyCatalogTraits.dotArgb(it) }?.let { Color(it) } ?: baseColor
                                        drawCircle(color = dotColor.copy(alpha = alpha), radius = 10f, center = centerOffset)
                                        drawCircle(color = Color(LayoutPalette.INK).copy(alpha = alpha), radius = 10f, center = centerOffset, style = Stroke(width = 1.5f))
                                                                                if ((settings.showPlantLabels || found) && seed != null && !isDragging) {
                                            val tag = VarietyCatalogTraits.of(seed)?.tag ?: CropReference.speciesName(seed)
                                            drawContext.canvas.nativeCanvas.drawText(tag, centerOffset.x, centerOffset.y + 34f, labelHalo)
                                            drawContext.canvas.nativeCanvas.drawText(tag, centerOffset.x, centerOffset.y + 34f, labelPaint)
                                        }

                                        if (!isDragging && seed != null && germinationEngine.isGerminationOverdue(node, seed)) {
                                            drawCircle(color = Color(0xFFEF4444), radius = 16f, center = centerOffset, style = Stroke(width = 3f))
                                        }
                                    }
                                }
                                } // closes the inner scrollable Box wrapping the Canvas

                                if (showShade && activePlot != null) {
                                    // Legend and controls for the shade overlay (FR-006, FR-038), same as the web planner.
                                    val lat = activePlot?.latitude ?: SunlightEngine.DEFAULT_LATITUDE
                                    val day = shadeDay.dayOfYear(lat, SunlightEngine.dayOfYear(System.currentTimeMillis()), com.example.smartgardenplanner.core.GrowingSeason.midSeasonDay(lat, growingSeason))
                                    val (rise, set) = ShadeTools.sunriseSunset(lat, day)
                                    Column(
                                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                                            .background(Color(0xE6FFFFFF), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(tr(shadeDay.label), fontSize = 11.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable { shadeDay = ShadeDay.entries[(shadeDay.ordinal + 1) % ShadeDay.entries.size] })
                                            Text(tr(if (shadeHour == null) "Whole day" else "At ${ShadeTools.clock(shadeHour!!)}"), fontSize = 11.sp, color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable { shadeHour = if (shadeHour == null) 9.0.coerceIn(rise, set) else null })
                                            Text(tr((if (shadePlants) "☑" else "☐") + " plants' shade"), fontSize = 11.sp, color = Color(0xFF1D4ED8),
                                                modifier = Modifier.clickable { shadePlants = !shadePlants })
                                        }
                                        val hour = shadeHour
                                        if (hour != null) {
                                            val sp = SunlightEngine.position(lat, day, hour)
                                            Slider(value = hour.toFloat(), onValueChange = { shadeHour = (kotlin.math.round(it * 4f) / 4f).toDouble() }, valueRange = rise.toFloat()..set.toFloat(), modifier = Modifier.width(240.dp))
                                            Text(tr("${ShadeTools.clock(hour)} solar time • sun ${com.example.smartgardenplanner.ui.compassName(sp.azimuthDeg.toFloat())}, ${sp.elevationDeg.toInt()}° up • dark = shade now"), fontSize = 10.sp, color = Color(LayoutPalette.INK))
                                        } else {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(tr("Sun over the day:"), fontSize = 11.sp, color = Color(LayoutPalette.INK), fontWeight = FontWeight.Bold)
                                                SunBand.entries.forEach { band ->
                                                    Box(Modifier.size(12.dp).background(Color(LayoutPalette.PAPER)).background(Color(band.overlayArgb)).border(1.dp, Color(LayoutPalette.BORDER)))
                                                    Text(tr(band.label), fontSize = 10.sp, color = Color(LayoutPalette.INK))
                                                }
                                            }
                                        }
                                        Text(tr("Tap the blue words to change the day, the time or plants' shade."), fontSize = 9.sp, color = Color.Gray)
                                    }
                                }
                                                                Column(modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)) {
                                    viewSeasonYear?.let { y ->
                                        Button(onClick = { viewSeasonYear = null }, modifier = Modifier.padding(bottom = 4.dp)) {
                                            Text(tr("Looking back at $y (read only) — tap to return to planning"), fontSize = 12.sp)
                                        }
                                    }
                                    if (showWater) {
                                        val manual = activePlot?.let { plot -> Irrigation.plants(plot, nodesState, siteFeatures.filter { SiteFeatureType.of(it.featureType)?.isIrrigation == true }) { seedFor(it) }.count { it.source == WaterSource.MANUAL } } ?: 0
                                        Text(tr(if (siteFeatures.none { SiteFeatureType.of(it.featureType)?.isIrrigation == true }) "Water map: no sprinklers, drip lines or taps drawn yet (menu → Irrigation)."
                                            else if (manual == 0) "Water map: every plant is reached." else "Water map: $manual plant(s) circled in red need a watering can."),
                                            fontSize = 11.sp, color = Color(0xFF1D4ED8), modifier = Modifier.background(Color(0xE6FFFFFF), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                                    }
                                    findCode?.let { code ->
                                        val name = seedFor(code)?.commonName ?: code
                                        Button(onClick = { findCode = null }, modifier = Modifier.padding(bottom = 4.dp)) {
                                            Text(tr("Showing ${nodesState.count { it.seedCode == code }} × $name — tap to clear"), fontSize = 12.sp)
                                        }
                                    }
                                    planPreview?.let { preview ->
                                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                            Column(modifier = Modifier.padding(10.dp).heightIn(max = if (previewFolded) 48.dp else 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(tr(when {
                                                        rotationPlans.isNotEmpty() -> "Rotation plan: ${rotationPlans.getOrNull(rotationIndex)?.year} (${rotationIndex + 1} of ${rotationPlans.size})"
                                                        nextSeasonMode -> "Next season's plan: ${preview.placed.size} plants"
                                                        else -> "Planting plan: ${preview.placed.size} plants"
                                                    }), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                                    // FR-064: fold the card to see more of the plot.
                                                    TextButton(onClick = { previewFolded = !previewFolded }, contentPadding = PaddingValues(0.dp)) { Text(tr(if (previewFolded) "+" else "−"), fontSize = 18.sp) }
                                                }
                                                // FR-060: other layouts for the same list.
                                                if (rotationPlans.isEmpty() && planOptions.isNotEmpty()) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        OutlinedButton(enabled = planOptionIndex > 0, onClick = { planOptionIndex--; planPreview = planOptions[planOptionIndex].second }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(tr("◀ Option"), fontSize = 11.sp) }
                                                        Text(tr("Option ${planOptionIndex + 1}: ${planOptions[planOptionIndex].first}"), fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                                        OutlinedButton(enabled = !planRunning, onClick = {
                                                            if (planOptionIndex + 1 < planOptions.size) { planOptionIndex++; planPreview = planOptions[planOptionIndex].second }
                                                            else { val more = planMore; if (more != null) launchSafely {
                                                                planRunning = true
                                                                val next = try { more() } finally { planRunning = false }
                                                                if (next != null) { planOptions = planOptions + next; planOptionIndex = planOptions.lastIndex; planPreview = next.second }
                                                                else snackbarMessage = tr("No other layout is different from the ones shown.")
                                                            } }
                                                        }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(tr(if (planRunning) "Working…" else "Option ▶"), fontSize = 11.sp) }
                                                    }
                                                    activePlot?.let { plot -> Text(tr(com.example.smartgardenplanner.core.AutoPlanner.summarize(plotContext(plot), preview)), fontSize = 11.sp, color = Color.Gray) }
                                                    if (planReplaceIds.isNotEmpty()) Text(tr("Keeping this plan replaces the ${planReplaceIds.size} plants already in this area."), fontSize = 11.sp, color = Color(0xFFB45309))
                                                }
                                                rotationPlans.getOrNull(rotationIndex)?.summary?.forEach { Text(tr("• $it"), fontSize = 11.sp) }
                                                                                                preview.placed.groupBy { it.seed.botanicalCode }.forEach { (_, list) ->
                                                    Text(tr("• ${VarietyCatalogTraits.displayName(list.first().seed)} × ${list.size}"), fontSize = 11.sp)
                                                }
                                                Text(tr("Nothing is planted until you tap Plant them."), fontSize = 11.sp, color = Color.Gray)
                                                preview.notes.forEach { Text(tr(it), fontSize = 11.sp, color = Color.LightGray) }
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                                                        Button(enabled = preview.placed.isNotEmpty(), onClick = {
                                                        val chosen = rotationPlans.firstOrNull()?.result ?: preview
                                                        val nodes = chosen.placed.map { PlantedNodeEntity(plotId = plotId, seedCode = it.seed.botanicalCode, coordinateXM = it.x, coordinateYM = it.y) }
                                                        // FR-037: next season / rotation: close this season (plants → history) and plant the new one.
                                                        val newSeason = nextSeasonMode || rotationPlans.isNotEmpty()
                                                        val archived = if (newSeason && nodesState.isNotEmpty()) Seasons.archive(plotId, nodesState, Seasons.currentSeason(nodesState, historyState)) { seedFor(it) } else emptyList()
                                                        val replaceIds = planReplaceIds
                                                        val replaceNodes = nodesState.filter { it.id in replaceIds }
                                                        launchSafely {
                                                            withContext(SgpExecutors.dbDispatcher) {
                                                                database.withTransaction {
                                                                    if (newSeason) {
                                                                        if (archived.isNotEmpty()) database.plantingHistoryDao().insertAll(archived)
                                                                        database.plantedNodeDao().deleteAllForPlot(plotId)
                                                                    }
                                                                    // FR-063: "start from a blank area" — the plants that were in it go.
                                                                    if (!newSeason && replaceIds.isNotEmpty()) replaceNodes.forEach { database.plantedNodeDao().delete(it) }
                                                                    database.plantedNodeDao().insertAll(nodes)
                                                                }
                                                            }
                                                            if (newSeason) historyState = withContext(SgpExecutors.dbDispatcher) { database.plantingHistoryDao().getByPlotId(plotId) }
                                                            reloadNodes()
                                                            undoStack.push(snapshotNow())
                                                            redoStack.clear()
                                                                                                                        snackbarMessage = "Planted ${nodes.size} plants. Undo removes them all."
                                                            allPlantedCodes = allPlantedCodes + nodes.map { it.seedCode }
                                                        }
                                                                                                                planPreview = null; autoPlanArea = null; planForMe = false; canvasMode = CanvasMode.PLACE_NODE
                                                        nextSeasonMode = false; rotationPlans = emptyList()
                                                    }) { Text(tr(when { rotationPlans.isNotEmpty() -> "Use ${rotationPlans.first().year} now"; nextSeasonMode -> "Start next season"; else -> "Plant them" }), fontSize = 12.sp) }
                                                    if (rotationPlans.isNotEmpty()) {
                                                        OutlinedButton(onClick = { rotationIndex = (rotationIndex - 1).coerceAtLeast(0); planPreview = rotationPlans[rotationIndex].result }) { Text(tr("◀"), fontSize = 12.sp) }
                                                        OutlinedButton(onClick = { rotationIndex = (rotationIndex + 1).coerceAtMost(rotationPlans.lastIndex); planPreview = rotationPlans[rotationIndex].result }) { Text(tr("▶"), fontSize = 12.sp) }
                                                        OutlinedButton(onClick = { showRotationChange = true }) { Text(tr("Change a variety…"), fontSize = 12.sp) }
                                                    }
                                                                                                        // Back to the list for the same area, with everything as it was chosen.
                                                                                                        if (rotationPlans.isEmpty()) OutlinedButton(onClick = { planPreview = null }) { Text(tr("Change selections"), fontSize = 12.sp) }
                                                    // Drops only the proposal: the plot and the list stay as they are (the list is remembered).
                                                    TextButton(onClick = {
                                                                                                                planPreview = null; autoPlanArea = null; planForMe = false; canvasMode = CanvasMode.PLACE_NODE
                                                        nextSeasonMode = false; rotationPlans = emptyList()
                                                        snackbarMessage = "Proposal discarded. Nothing on the plot changed; your list is kept for next time."
                                                    }) { Text(tr("Discard"), fontSize = 12.sp) }
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        text = tr(when {
                                            zoomPanModeEnabled -> "Zoom/Pan Mode: use +/- to zoom, drag to pan • tap the zoom icon to turn this off (${"%.1f".format(zoomScale)}x)"
                                            planPreview != null -> "Preview: faded circles show where the plants would go"
                                            planForMe && canvasMode == CanvasMode.SELECT_AREA && areaSubMode == AreaSelectSubMode.POLYGON -> "Plan for me: tap the corners of the area to plant (3+), then Finish Area"
                                            planForMe && canvasMode == CanvasMode.SELECT_AREA -> "Plan for me: drag over the area to plant"
                                            canvasMode == CanvasMode.PLACE_NODE && moveModeEnabled -> "Move Mode: drag a plant to reposition it, or tap the lock icon to turn this off"
                                            canvasMode == CanvasMode.PLACE_NODE -> "Double-tap to plant • Tap an existing plant for details, editing, or recovery"
                                            canvasMode == CanvasMode.DRAW_PATH && pathSubMode == PathDrawSubMode.POINTS -> "Tap to add points • tap an existing path to edit it"
                                            canvasMode == CanvasMode.DRAW_PATH -> "Drag to mark a no-plant path • tap an existing path to edit it"
                                            canvasMode == CanvasMode.SELECT_AREA && areaSubMode == AreaSelectSubMode.POLYGON -> "Tap to add points (need at least 3) to outline a custom area"
                                            canvasMode == CanvasMode.OUTLINE -> "Tap the plot's corners in order (${inProgressPoints.size} so far, need 3+), then Finish Outline"
                                            canvasMode == CanvasMode.SITE_AREA -> "${siteAreaType.label}: tap corners (need 3+), then Finish • tap an existing area to edit it"
                                            canvasMode == CanvasMode.BARRIER && barrierType == SiteFeatureType.TREE -> "Tap where the tree trunk is • tap an existing barrier to edit it"
                                            canvasMode == CanvasMode.BARRIER -> "${barrierType.label}: tap points along it (2+), then Finish • tap an existing barrier to edit it"
                                            canvasMode == CanvasMode.PHOTO && photoCalibrating -> "Set scale: tap two points on the photo whose real distance you know (${photoPoints.size} of 2)"
                                            canvasMode == CanvasMode.PHOTO -> "Drag to move the satellite photo into line with the plot • menu → Satellite photo… for scale, turn and see-through"
                                            else -> "Drag to select an area to auto-populate"
                                        }),
                                        color = if (zoomPanModeEnabled) Color(0xFF0EA5E9) else if (moveModeEnabled) Color(0xFFEF4444) else Color.LightGray, fontSize = 11.sp
                                    )
                                    if (canvasMode == CanvasMode.DRAW_PATH && pathSubMode == PathDrawSubMode.POINTS && inProgressPoints.size >= 2) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { pendingPolylineWidth = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                                Text(tr("Finish Path"), fontSize = 11.sp)
                                            }
                                            OutlinedButton(onClick = { inProgressPoints = emptyList() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                                Text(tr("Cancel"), fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    // [NEW — FR-001] "Finish Area" appears once >=3 points exist, matching the
                                    // pattern's own described behavior: a 3rd point doesn't auto-close by
                                    // itself (a 4th+ tap keeps adding points instead), but the button is
                                    // available from that point on to close the loop whenever the user is done.
                                    val siteFinishReady = when (canvasMode) {
                                        CanvasMode.OUTLINE, CanvasMode.SITE_AREA -> inProgressPoints.size >= 3
                                                                                CanvasMode.BARRIER -> barrierType != SiteFeatureType.TREE && barrierType != SiteFeatureType.SPRINKLER && barrierType != SiteFeatureType.HOSE_BIB && inProgressPoints.size >= 2
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
                                                Text(tr(if (canvasMode == CanvasMode.OUTLINE) "Finish Outline" else "Finish (${inProgressPoints.size} points)"), fontSize = 11.sp)
                                            }
                                            OutlinedButton(onClick = { inProgressPoints = emptyList() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                                Text(tr("Cancel"), fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    if (canvasMode == CanvasMode.SELECT_AREA && areaSubMode == AreaSelectSubMode.POLYGON && inProgressPoints.size >= 3) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    if (planForMe) autoPlanArea = toPlotPoints(inProgressPoints) else pendingPolygonSelection = inProgressPoints
                                                    inProgressPoints = emptyList()
                                                },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text(tr("Finish Area (${inProgressPoints.size} points)"), fontSize = 11.sp)
                                            }
                                            OutlinedButton(onClick = { inProgressPoints = emptyList() }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                                Text(tr("Cancel"), fontSize = 11.sp)
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
            title = { Text(tr("Discard unfinished shape?")) },
            text = { Text(tr("The points you've tapped for this path or area will be lost.")) },
            confirmButton = { TextButton(onClick = { inProgressPoints = emptyList(); showDiscardDialog = false }) { Text(tr("Discard")) } },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text(tr("Keep drawing")) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    autoPlanArea?.let { area ->
        if (planPreview == null && !showDirectionDialog) {
            AutoPlanRequestDialog(
                seedDictionary = seedDictionary,
                                rows = planRows,
                priority = planPriority,
                shapes = planShapes,
                inAreaCount = if (nextSeasonMode) 0 else nodesState.count { PlotGeometry.pointInPolygon(it.coordinateXM, it.coordinateYM, area) },
                replaceExisting = planReplace,
                onReplaceChange = { planReplace = it },
                checksFor = { reqs ->
                    val plot = activePlot
                    if (plot == null) emptyList() else {
                        val nowSeason = Seasons.currentSeason(nodesState, historyState)
                        val hist = if (nextSeasonMode && nodesState.isNotEmpty()) historyState + Seasons.archive(plotId, nodesState, nowSeason) { seedFor(it) } else historyState
                        val yr = if (nextSeasonMode && nodesState.isNotEmpty()) nowSeason + 1 else nowSeason
                        com.example.smartgardenplanner.core.PlanChecks.check(plotContext(plot, if (nextSeasonMode) emptyList() else nodesState), area, reqs, hist, yr,
                            com.example.smartgardenplanner.core.Pest.parse(plot.pests), settings.spacingMarginMultiplier)
                    }
                },
                usual = Seasons.usualVarieties(allPlantedCodes, emptyList(), { seedFor(it) }),
                layout = settings.planLayoutEnum,
                onLayoutChange = { l ->
                    val updated = settings.copy(planLayout = l.name)
                    settings = updated
                    launchSafely { withContext(SgpExecutors.dbDispatcher) { settingsRepository.save(updated) } }
                },
                                hasHistory = historyState.isNotEmpty(),
                title = if (nextSeasonMode) "Plan next season with crop rotation" else if (area == activePlot?.let { PlotShape.effectiveOutline(it) }) "Fill the whole plot" else "What do you want to plant here?",
                onHowManyFit = { reqs ->
                    val plot = activePlot
                    if (plot != null) launchSafely {
                        val ctx = plotContext(plot, if (nextSeasonMode) emptyList() else nodesState)
                        val paths = pathZonesState
                        val fit = withContext(Dispatchers.Default) {
                            RotationPlanner.howManyFit(ctx, area, reqs, { x, y, r ->
                                paths.any { zone -> if (zone.pathType == "POLYLINE") distanceToPolyline(x, y, parsePoints(zone.pointsJson)) < (zone.widthM / 2f + r) else circleIntersectsRect(x, y, r, zone.xM, zone.yM, zone.widthM, zone.heightM) }
                            }, settings.spacingMarginMultiplier)
                        }
                        fit.forEach { f -> val i = planRows.indexOfFirst { it.first == f.seed.botanicalCode }; if (i >= 0) planRows[i] = f.seed.botanicalCode to f.count }
                        snackbarMessage = "About ${fit.sumOf { it.count }} plants fit: " + fit.joinToString(", ") { "${it.count} ${CropReference.speciesName(it.seed)}" }
                    }
                },
                conflictFor = pickerConflict,
                orientationSet = activePlot?.orientationSet == true,
                running = planRunning,
                onSetDirection = { showDirectionDialog = true },
                onPlan = { requests ->
                                        val plot = activePlot ?: return@AutoPlanRequestDialog
                    planRunning = true
                    val remembered = settings.copy(lastPlanList = com.example.smartgardenplanner.core.AppSettings.encodePlanRows(planRows.toList()))
                    settings = remembered
                                        // FR-037: planning next season plans an empty plot, with this season's plants counted as history.
                    val nowSeason = Seasons.currentSeason(nodesState, historyState)
                    val history = if (nextSeasonMode && nodesState.isNotEmpty()) historyState + Seasons.archive(plotId, nodesState, nowSeason) { seedFor(it) } else historyState
                    val season = if (nextSeasonMode && nodesState.isNotEmpty()) nowSeason + 1 else nowSeason
                    // FR-063: plants already in the area are kept (planned around) or replaced ("start from a blank area").
                    val inArea = if (nextSeasonMode) emptyList() else nodesState.filter { PlotGeometry.pointInPolygon(it.coordinateXM, it.coordinateYM, area) }
                    val replaceIds = if (planReplace) inArea.map { it.id }.toSet() else emptySet()
                    val planNodes = if (nextSeasonMode) emptyList() else nodesState.filter { it.id !in replaceIds }
                    launchSafely {
                                                val context = plotContext(plot, planNodes)
                        val paths = pathZonesState
                        val blocked: (Float, Float, Float) -> Boolean = { x, y, r ->
                            paths.any { zone ->
                                if (zone.pathType == "POLYLINE") distanceToPolyline(x, y, parsePoints(zone.pointsJson)) < (zone.widthM / 2f + r)
                                else circleIntersectsRect(x, y, r, zone.xM, zone.yM, zone.widthM, zone.heightM)
                            }
                        }
                        val margin = settings.spacingMarginMultiplier
                        val firstVariant = if (remembered.planLayoutEnum == PlantingLayout.ROWS) AutoPlanner.VARIANT_COUNT - 1 else 0
                        val result = try { withContext(Dispatchers.Default) {
                            AutoPlanner.planVariant(firstVariant, context, area, requests, blocked, margin, plot.orientationSet, history, season)
                        } } finally { planRunning = false }
                        // FR-060: more layouts on request ("Option ▶"), skipping ones that come out the same.
                        val tried = mutableSetOf(firstVariant)
                        val seen = mutableSetOf(AutoPlanner.signature(result))
                        planOptions = listOf(AutoPlanner.variantLabel(firstVariant) to result)
                        planOptionIndex = 0
                        planReplaceIds = replaceIds
                        planMore = {
                            withContext(Dispatchers.Default) {
                                var found: Pair<String, AutoPlanResult>? = null
                                for (v in 0 until AutoPlanner.VARIANT_COUNT) {
                                    if (v in tried) continue
                                    tried += v
                                    val r = AutoPlanner.planVariant(v, context, area, requests, blocked, margin, plot.orientationSet, history, season)
                                    if (r.placed.isNotEmpty() && seen.add(AutoPlanner.signature(r))) { found = AutoPlanner.variantLabel(v) to r; break }
                                }
                                found
                            }
                        }
                                                planPreview = result
                        withContext(SgpExecutors.dbDispatcher) { settingsRepository.save(remembered) }
                    }
                },
                                onDismiss = {
                    autoPlanArea = null; planForMe = false; canvasMode = CanvasMode.PLACE_NODE; nextSeasonMode = false
                    if (planRows.isNotEmpty()) snackbarMessage = "Plan canceled. Your list is kept for next time."
                }
            )
        }
    }

            // FR-037: plan several seasons in a row and look at them one year at a time.
    if (showRotationDialog) {
        var seasonsText by remember { mutableStateOf("5") }
        val base = RotationPlanner.lastList(nodesState, historyState) { seedFor(it) }
        AlertDialog(
            onDismissRequest = { showRotationDialog = false },
            title = { Text(tr("Rotation plan")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Uses the same list every year (" + base.joinToString(", ") { "${it.count} ${CropReference.speciesName(it.seed)}" } + ") and plans the whole plot season after season, so no crop goes where its family grew the year before."), fontSize = 13.sp)
                    OutlinedTextField(value = seasonsText, onValueChange = { seasonsText = it.filter(Char::isDigit).take(2) }, singleLine = true,
                        label = { Text(tr("How many seasons (1–${RotationPlanner.MAX_SEASONS})")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Text(tr("Nothing changes until you choose to use the first year. While looking at the plan, “Change a variety…” swaps a variety from that year on."), fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(enabled = base.isNotEmpty() && !planRunning && (seasonsText.toIntOrNull() ?: 0) in 1..RotationPlanner.MAX_SEASONS, onClick = {
                    showRotationDialog = false
                    val now = Seasons.currentSeason(nodesState, historyState)
                    rotationBase = base
                    rotationFirst = if (nodesState.isNotEmpty()) now + 1 else now
                    rotationCount = seasonsText.toInt()
                    rotationChanges = emptyMap()
                    replanRotation(0)
                }) { Text(tr(if (planRunning) "Planning…" else "Make the plan")) }
            },
            dismissButton = { TextButton(onClick = { showRotationDialog = false }) { Text(tr("Cancel")) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // FR-048: swap a variety from the year being shown onward.
    if (showRotationChange) {
        val year = rotationPlans.getOrNull(rotationIndex)?.year ?: rotationFirst
        val listNow = RotationPlanner.requestsFor(rotationBase, rotationChanges, year)
        var fromCode by remember { mutableStateOf(listNow.firstOrNull()?.seed?.botanicalCode) }
        var picking by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showRotationChange = false },
            title = { Text(tr("Change a variety from $year on")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("Tap the variety to replace, then choose the new one. $year and every later year use it (same number of plants), and the plan is worked out again with crop rotation."), fontSize = 12.sp)
                    listNow.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { fromCode = r.seed.botanicalCode }) {
                            RadioButton(selected = fromCode == r.seed.botanicalCode, onClick = { fromCode = r.seed.botanicalCode })
                            Text(tr("${r.count} × ${r.seed.commonName}"), fontSize = 13.sp)
                        }
                    }
                    if (rotationChanges.isNotEmpty()) Text(tr("Changes so far: " + rotationChanges.entries.sortedBy { it.key }.flatMap { (y, m) -> m.map { (f, t) -> "from $y ${seedFor(f)?.commonName ?: f} → ${t.commonName}" } }.joinToString("; ")), fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = { TextButton(enabled = fromCode != null, onClick = { picking = true }) { Text(tr("Choose new variety…")) } },
            dismissButton = {
                Row {
                    if (rotationChanges.isNotEmpty()) TextButton(onClick = { showRotationChange = false; rotationChanges = emptyMap(); replanRotation(rotationIndex) }) { Text(tr("Clear changes")) }
                    TextButton(onClick = { showRotationChange = false }) { Text(tr("Cancel")) }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
        if (picking) {
            VarietyPickerDialog(
                seedDictionary = seedDictionary,
                conflictFor = pickerConflict,
                onSelect = { toCode ->
                    picking = false; showRotationChange = false
                    val from = fromCode; val to = seedFor(toCode)
                    if (from != null && to != null) {
                        rotationChanges = rotationChanges + (year to ((rotationChanges[year] ?: emptyMap()) + (from to to)))
                        replanRotation(rotationIndex)
                        snackbarMessage = "From $year: ${seedFor(from)?.commonName} → ${to.commonName}. The plan was worked out again."
                    }
                },
                onDismiss = { picking = false }
            )
        }
    }

    // FR-041: duplicate this plot (site, and optionally plants and history), like duplicating a browser tab.
    if (showDuplicateDialog) {
        var copyName by remember { mutableStateOf((activePlot?.name ?: "Plot") + " (copy)") }
        var copyPlants by remember { mutableStateOf(true) }
        var copyHistory by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text(tr("Duplicate this plot")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("The copy keeps the size, direction, ZIP, soil, outline, fences, buildings, trees, paths, areas and irrigation."), fontSize = 13.sp)
                    OutlinedTextField(value = copyName, onValueChange = { copyName = it.take(200) }, label = { Text(tr("Name of the copy")) }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = copyPlants, onCheckedChange = { copyPlants = it }); Text(tr("Copy this season's ${nodesState.size} plants"), fontSize = 13.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = copyHistory, onCheckedChange = { copyHistory = it }); Text(tr("Copy the history (${Seasons.years(historyState).size} seasons)"), fontSize = 13.sp) }
                }
            },
            confirmButton = {
                TextButton(enabled = copyName.isNotBlank(), onClick = {
                    val plot = activePlot ?: return@TextButton
                    val name = copyName.trim(); val withPlants = copyPlants; val withHistory = copyHistory
                    val plants = nodesState; val paths = pathZonesState; val features = siteFeatures; val history = historyState
                    showDuplicateDialog = false
                    launchSafely {
                        val newId = withContext(SgpExecutors.dbDispatcher) {
                            database.withTransaction {
                                val now = System.currentTimeMillis()
                                val id = database.plotDao().insert(plot.copy(id = 0, name = name, createdTimestamp = now, lastModifiedTimestamp = now))
                                if (withPlants && plants.isNotEmpty()) database.plantedNodeDao().insertAll(plants.map { it.copy(id = 0, plotId = id) })
                                if (paths.isNotEmpty()) database.pathZoneDao().insertAll(paths.map { it.copy(id = 0, plotId = id) })
                                if (features.isNotEmpty()) database.siteFeatureDao().insertAll(features.map { it.copy(id = 0, plotId = id) })
                                if (withHistory && history.isNotEmpty()) database.plantingHistoryDao().insertAll(history.map { it.copy(id = 0, plotId = id) })
                                id
                            }
                        }
                        withContext(Dispatchers.IO) { com.example.smartgardenplanner.data.BackdropStore.copy(canvasContext.filesDir, plot.id, newId) }
                        snackbarMessage = "Made “$name”. The original is unchanged; open the copy from the plot list."
                    }
                }) { Text(tr("Duplicate")) }
            },
            dismissButton = { TextButton(onClick = { showDuplicateDialog = false }) { Text(tr("Cancel")) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    replaceFromCode?.let { fromCode ->
        VarietyPickerDialog(
            seedDictionary = seedDictionary,
            conflictFor = pickerConflict,
            onSelect = { toCode ->
                replaceFromCode = null
                val to = seedFor(toCode) ?: return@VarietyPickerDialog
                val r = com.example.smartgardenplanner.core.PlantSwap.replaceAll(nodesState, fromCode, to, { seedFor(it) }, activePlot?.hardinessZone,
                    settings.spacingMarginMultiplier, effectiveEnforceCompanionRules, activeGuilds)
                if (r.changed == 0) { snackbarMessage = r.messages.joinToString(" "); return@VarietyPickerDialog }
                val changed = r.nodes.filter { n -> nodesState.any { it.id == n.id && it.seedCode != n.seedCode } }
                launchSafely {
                    withContext(SgpExecutors.dbDispatcher) { database.withTransaction { changed.forEach { database.plantedNodeDao().update(it) } } }
                    reloadNodes()
                    undoStack.push(snapshotNow())
                    redoStack.clear()
                    findCode = null
                    snackbarMessage = r.messages.joinToString(" ") + " Undo reverses it."
                }
            },
            onDismiss = { replaceFromCode = null }
        )
    }

    // FR-046: satellite photo — find the yard on Google Maps, add a screenshot, set scale, move, turn, see-through.
    if (showPhotoDialog) {
        val plot = activePlot
        val placement = plot?.let { com.example.smartgardenplanner.core.Backdrop.parse(it.backdropJson) }?.takeIf { photoBitmap != null }
        if (photoAddress.isBlank()) photoAddress = plot?.address ?: plot?.locationZip.orEmpty()
        AlertDialog(
            onDismissRequest = { showPhotoDialog = false },
            title = { Text(tr("Satellite photo")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (placement == null) {
                        Text(tr("See your real trees, fences and buildings under the plot: open Google Maps in satellite view, zoom in on your yard, take a screenshot, then choose it here."), fontSize = 13.sp)
                        OutlinedTextField(value = photoAddress, onValueChange = { photoAddress = it.take(200) }, label = { Text(tr("Your address")) }, singleLine = true)
                        OutlinedButton(onClick = { openInMaps(photoAddress) }) { Text(tr("Open Google Maps (satellite)")) }
                        Button(onClick = { showPhotoDialog = false; photoPicker.launch("image/*") }) { Text(tr("Choose photo…")) }
                    } else {
                        Text(tr("Photo: ${"%.1f".format(placement.widthM)} m wide."), fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { showPhotoDialog = false; canvasMode = CanvasMode.PHOTO; photoCalibrating = true; photoPoints.clear() }) { Text(tr("Set scale")) }
                            OutlinedButton(onClick = { showPhotoDialog = false; canvasMode = CanvasMode.PHOTO; photoCalibrating = false; photoPoints.clear() }) { Text(tr("Move")) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { savePhotoPlacement { it.copy(visible = !it.visible) } }) { Text(tr(if (placement.visible) "Hide" else "Show")) }
                            OutlinedButton(onClick = {
                                val p = activePlot ?: return@OutlinedButton
                                val updated = p.copy(backdropJson = null)
                                showPhotoDialog = false
                                if (canvasMode == CanvasMode.PHOTO) canvasMode = CanvasMode.PLACE_NODE
                                launchSafely {
                                    withContext(SgpExecutors.dbDispatcher) { database.plotDao().update(updated) }
                                    withContext(Dispatchers.IO) { com.example.smartgardenplanner.data.BackdropStore.delete(canvasContext.filesDir, plotId) }
                                    activePlot = updated; photoVersion++
                                    snackbarMessage = "Photo removed."
                                }
                            }) { Text(tr("Remove"), color = MaterialTheme.colorScheme.error) }
                        }
                        var opacity by remember(placement.opacity) { mutableFloatStateOf(placement.opacity) }
                        Text(tr("See-through: ${(opacity * 100).toInt()}%"), fontSize = 12.sp)
                        Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..1f, onValueChangeFinished = { val o = opacity; savePhotoPlacement { it.copy(opacity = o) } })
                        // FR-049: slider and number box in step; + clockwise, − counter-clockwise, 180° at most either way.
                        var turn by remember(placement.rotationDeg) { mutableFloatStateOf(placement.rotationDeg) }
                        var turnText by remember(placement.rotationDeg) { mutableStateOf(kotlin.math.round(placement.rotationDeg).toInt().toString()) }
                        Text(tr("Turn: ${com.example.smartgardenplanner.core.Backdrop.describeTurn(turn)} (+ clockwise, − counterclockwise)"), fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(value = turn, onValueChange = { turn = kotlin.math.round(it); turnText = turn.toInt().toString() }, valueRange = -180f..180f,
                                onValueChangeFinished = { val t = turn; savePhotoPlacement { it.turned(t) } }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = turnText, onValueChange = { v ->
                                turnText = v.filter { c -> c.isDigit() || c == '-' }.take(4)
                                turnText.toIntOrNull()?.takeIf { it in -180..180 }?.let { turn = it.toFloat() }
                            }, singleLine = true, modifier = Modifier.width(80.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            TextButton(onClick = { val t = turn; savePhotoPlacement { it.turned(t) } }) { Text(tr("Set")) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPhotoDialog = false }) { Text(tr("Close")) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
    if (canvasMode == CanvasMode.PHOTO && photoCalibrating && photoPoints.size == 2) {
        var distanceText by remember { mutableStateOf("") }
        val a = photoPoints[0]; val b = photoPoints[1]
        val shown = kotlin.math.sqrt(((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)).toDouble())
        AlertDialog(
            onDismissRequest = { photoPoints.clear() },
            title = { Text(tr("Set the photo's scale")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("On the photo these points are ${"%.2f".format(shown)} m apart at the current scale. How far apart are they really?"), fontSize = 13.sp)
                    OutlinedTextField(value = distanceText, onValueChange = { distanceText = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, label = { Text(tr("Real distance, m (1 ft = 0.3048 m)")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val m = distanceText.toFloatOrNull()
                    val current = activePlot?.let { com.example.smartgardenplanner.core.Backdrop.parse(it.backdropJson) }
                    val cal = if (m != null && current != null) current.calibrate(PlotPoint(a.x, a.y), PlotPoint(b.x, b.y), m) else null
                    if (cal == null) { snackbarMessage = "Enter a distance between 0.1 and 2000 m, with the two points apart."; photoPoints.clear(); return@TextButton }
                    photoPoints.clear(); photoCalibrating = false
                    savePhotoPlacement { cal }
                    snackbarMessage = "Scale set: the photo is ${"%.1f".format(cal.widthM)} m wide. Drag it to line up with the plot; turn it from menu → Satellite photo…"
                }) { Text(tr("Set scale")) }
            },
            dismissButton = { TextButton(onClick = { photoPoints.clear() }) { Text(tr("Cancel")) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // FR-033: close the season. Plants move to history; fence, walls, buildings, trees, paths, areas and outline stay.
    if (showNewSeasonDialog) {
        val season = Seasons.currentSeason(nodesState, historyState)
        var yearText by remember(season) { mutableStateOf(season.toString()) }
        AlertDialog(
            onDismissRequest = { showNewSeasonDialog = false },
            title = { Text(tr("Start a new season")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("The fence, walls, buildings, trees, paths, sun/shade areas and outline stay. This season's ${nodesState.size} plant(s) move into the plot's history."), fontSize = 13.sp)
                    Text(tr("History stays visible (menu → Past season on layout, and Plot insights → Harmony) and is used for crop-rotation advice and by Plan an area for me. It travels in plan files. Undo reverses this."), fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(value = yearText, onValueChange = { yearText = it.filter(Char::isDigit).take(4) }, label = { Text(tr("Season being closed")) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nodesState.isNotEmpty() && (yearText.toIntOrNull() ?: 0) in 1900..3000,
                    onClick = {
                        val year = yearText.toInt()
                        val archived = Seasons.archive(plotId, nodesState, year) { seedFor(it) }
                        showNewSeasonDialog = false
                        launchSafely {
                            withContext(SgpExecutors.dbDispatcher) {
                                database.withTransaction {
                                    database.plantingHistoryDao().insertAll(archived)
                                    database.plantedNodeDao().deleteAllForPlot(plotId)
                                }
                            }
                            historyState = withContext(SgpExecutors.dbDispatcher) { database.plantingHistoryDao().getByPlotId(plotId) }
                            reloadNodes()
                            historyYear = year
                            undoStack.push(snapshotNow())
                            redoStack.clear()
                            snackbarMessage = "Season $year closed: ${archived.size} plants kept as history (dashed). Plan ${year + 1} with rotation in mind."
                        }
                    }
                ) { Text(tr("Start new season")) }
            },
            dismissButton = { TextButton(onClick = { showNewSeasonDialog = false }) { Text(tr("Cancel")) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showDirectionDialog) {
        activePlot?.let { plot ->
            com.example.smartgardenplanner.ui.PlotDirectionDialog(
                plot = plot,
                database = database,
                onlineEnabled = settings.onlineFeaturesEnabled,
                onSave = { updated ->
                    launchSafely {
                        withContext(SgpExecutors.dbDispatcher) { database.plotDao().update(updated) }
                        activePlot = updated
                        snackbarMessage = "Saved: top edge faces ${com.example.smartgardenplanner.ui.compassName(updated.northBearingDeg)}."
                    }
                    showDirectionDialog = false
                },
                onDismiss = { showDirectionDialog = false }
            )
        }
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
                radiusM = when (type) {
                    SiteFeatureType.TREE -> 2f
                    SiteFeatureType.SPRINKLER -> Irrigation.DEFAULT_THROW_M
                    SiteFeatureType.DRIP_LINE -> Irrigation.DEFAULT_DRIP_HALF_WIDTH_M
                    SiteFeatureType.HOSE_BIB -> Irrigation.DEFAULT_HOSE_M
                    else -> 0f
                },
                slopeGradePct = if (type == SiteFeatureType.SPRINKLER) 360f else 0f
            ),
            isNew = true,
            onSave = { feature ->
                launchSafely {
                    withContext(SgpExecutors.dbDispatcher) { database.siteFeatureDao().insert(feature) }
                        reloadFeatures()
                        undoStack.push(snapshotNow())
                        redoStack.clear()
                    }
                    pendingSiteShape = null
            },
            onDelete = null,
            onMove = null,
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
                    undoStack.push(snapshotNow())
                    redoStack.clear()
                }
                editingSiteFeature = null
            },
            onDelete = {
                    launchSafely {
                        withContext(SgpExecutors.dbDispatcher) { database.siteFeatureDao().delete(feature) }
                        reloadFeatures()
                        undoStack.push(snapshotNow())
                        redoStack.clear()
                    }
                    editingSiteFeature = null
                },
                onMove = {
                    // Next tap on the plot moves this obstacle/area there (obstacles can be relocated).
                    movingSiteFeature = feature
                    editingSiteFeature = null
                    snackbarMessage = "Tap where the ${SiteFeatureType.of(feature.featureType)?.label?.lowercase() ?: "item"} should go."
                },
                onDismiss = { editingSiteFeature = null }
        )
    }

    // [NEW] Width-input dialog after finishing a points-mode path.
    if (pendingPolylineWidth) {
        var widthText by remember { mutableStateOf("0.5") }
        AlertDialog(
            onDismissRequest = { pendingPolylineWidth = false },
            title = { Text(tr("Path Width")) },
            text = {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { widthText = it },
                    label = { Text(tr("Width (m)")) },
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
                        undoStack.push(snapshotNow())
                        redoStack.clear()
                    }
                    inProgressPoints = emptyList()
                    pendingPolylineWidth = false
                }) { Text(tr("Save Path")) }
            },
            dismissButton = { TextButton(onClick = { pendingPolylineWidth = false }) { Text(tr("Cancel")) } },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // [NEW] Edit an existing path zone — requested feature: "I should also be able to modify it
    // later when selected." Supports width adjustment (polyline) and delete (both types).
    editingPathZone?.let { zone ->
        var widthText by remember(zone.id) { mutableStateOf(zone.widthM.toString()) }
        AlertDialog(
            onDismissRequest = { editingPathZone = null },
            title = { Text(tr(if (zone.pathType == "POLYLINE") "Edit Path" else "No-Plant Area")) },
            text = {
                Column {
                    if (zone.pathType == "POLYLINE") {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { widthText = it },
                            label = { Text(tr("Width (m)")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(tr("${"%.2f".format(zone.widthM)}m × ${"%.2f".format(zone.heightM)}m rectangle."))
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
                            undoStack.push(snapshotNow())
                            redoStack.clear()
                        }
                        editingPathZone = null
                    }) { Text(tr("Save")) }
                } else {
                    TextButton(onClick = { editingPathZone = null }) { Text(tr("Close")) }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        launchSafely {
                            withContext(SgpExecutors.dbDispatcher) { database.pathZoneDao().delete(zone) }
                            reloadPaths()
                            undoStack.push(snapshotNow())
                            redoStack.clear()
                        }
                        editingPathZone = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) { Text(tr("Delete")) }
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
                title = { Text(tr("Germination Failed — Recovery Plan")) },
                text = {
                    Column {
                        Text(tr("This ${seed.commonName} was planted more than ${seed.germinationDays} days ago with no recorded germination. Choose how to reuse this spot:"))
                        Spacer(modifier = Modifier.height(12.dp))
                        if (options.isEmpty()) {
                            Text(tr("No fallback options are on file for this variety."), color = Color.Gray)
                        }
                        // FR-056: varieties that catch up with the plants that survived.
                        TextButton(onClick = { planBNode = node; germinationDialogNode = null }, modifier = Modifier.fillMaxWidth()) { Text(tr("Plan B: varieties ready with the others…"), fontSize = 12.sp) }
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
                                        undoStack.push(snapshotNow())
                                        redoStack.clear()
                                    }
                                    germinationDialogNode = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(tr(label), fontSize = 12.sp) }
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
                    }) { Text(tr("Keep as-is")) }
                },
                dismissButton = { TextButton(onClick = { germinationDialogNode = null }) { Text(tr("Close")) } },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }

    rearrangeIds?.let { ids ->
        val group = nodesState.filter { it.id in ids }
        val plot = activePlot
        if (group.size < 2 || plot == null) { rearrangeIds = null } else {
            val others = nodesState.filter { it.id !in ids }
            val options = remember(ids) { com.example.smartgardenplanner.core.ClumpShapes.options(group.size) }
            AlertDialog(
                onDismissRequest = { rearrangeIds = null },
                title = { Text(tr("Group of ${group.size} plants")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        OutlinedButton(onClick = {
                            groupMoveIds = ids; moveModeEnabled = true; rearrangeIds = null
                            snackbarMessage = tr("Move Mode: drag any plant of the group to move all ${group.size}. Turn Move Mode off when done.")
                        }, modifier = Modifier.fillMaxWidth()) { Text(tr("Move the whole group")) }
                        Text(tr("Or lay it out as other rows and columns (it stays centered where it is):"), fontSize = 12.sp, color = Color.Gray)
                        options.forEach { o ->
                            val moved = com.example.smartgardenplanner.core.GroupTools.rearranged(group, o.rows, { seedFor(it) }, settings.spacingMarginMultiplier)
                            val bad = moved?.let { com.example.smartgardenplanner.core.GroupTools.problems(it, others, plot, { c -> seedFor(c) }, settings.spacingMarginMultiplier, effectiveEnforceCompanionRules, activeGuilds) } ?: 1
                            OutlinedButton(enabled = moved != null && bad == 0, onClick = {
                                val m = moved ?: return@OutlinedButton
                                rearrangeIds = null
                                launchSafely {
                                    withContext(SgpExecutors.dbDispatcher) { database.withTransaction { m.forEach { database.plantedNodeDao().update(it) } } }
                                    reloadNodes(); undoStack.push(snapshotNow()); redoStack.clear()
                                    snackbarMessage = tr("Rearranged ${m.size} plants as ${o.label}. Undo reverses it.")
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(tr(o.label + if (bad > 0) " — " + tr("doesn't fit here") else ""), fontSize = 12.sp)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { rearrangeIds = null }) { Text(tr("Close")) } },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }

    planBNode?.let { node ->
        val seed = seedFor(node.seedCode)
        if (seed == null) { planBNode = null } else {
            val today = SunlightEngine.dayOfYear(System.currentTimeMillis())
            val planted = SunlightEngine.dayOfYear(node.datePlantedEpochMillis)
            val opts = remember(node, growingSeason) { com.example.smartgardenplanner.core.BackupPlanner.options(seed, planted, today, seedDictionary, growingSeason) }
            val sameDay = nodesState.filter { it.seedCode == node.seedCode && it.datePlantedEpochMillis / 86_400_000L == node.datePlantedEpochMillis / 86_400_000L }
            var replaceAll by remember(node) { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { planBNode = null },
                title = { Text(tr("Plan B for ${seed.commonName}")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(tr("The plants that survived should be ready around ${com.example.smartgardenplanner.core.GrowingSeason.date(planted + seed.daysToHarvest)}" +
                            (growingSeason?.takeIf { !it.frostFree }?.let { " (first frost around ${com.example.smartgardenplanner.core.GrowingSeason.date(it.firstFrost)})" } ?: "") +
                            ". Tap one to plant it today in this spot:"), fontSize = 13.sp)
                        if (opts.isEmpty()) Text(tr("Nothing in the catalog would be ready in time this season. A quick catch crop (radishes, lettuce) or leaving the spot for next season are the options."), fontSize = 12.sp, color = Color(0xFFB45309))
                        if (sameDay.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = replaceAll, onCheckedChange = { replaceAll = it })
                            Text(tr("Replace all ${sameDay.size} planted the same day"), fontSize = 12.sp)
                        }
                        opts.forEach { o ->
                            OutlinedButton(onClick = {
                                val ids = if (replaceAll) sameDay.map { it.id }.toSet() else setOf(node.id)
                                val now = System.currentTimeMillis()
                                val updated = nodesState.filter { it.id in ids }.map { it.copy(seedCode = o.seed.botanicalCode, datePlantedEpochMillis = now, germinationFlagResolved = false) }
                                planBNode = null
                                launchSafely {
                                    withContext(SgpExecutors.dbDispatcher) { database.withTransaction { updated.forEach { database.plantedNodeDao().update(it) } } }
                                    reloadNodes()
                                    undoStack.push(snapshotNow())
                                    redoStack.clear()
                                    snackbarMessage = "Plan B: ${updated.size} × ${o.seed.commonName} planted today. Undo reverses it."
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Column { Text(tr(o.seed.commonName), fontSize = 13.sp); Text(tr(o.reason), fontSize = 11.sp, color = Color.Gray) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { planBNode = null }) { Text(tr("Close")) } },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }

    infoDialogNode?.let { node ->
        val seed = seedFor(node.seedCode)
        AlertDialog(
            onDismissRequest = { infoDialogNode = null },
            title = { Text(tr(seed?.commonName ?: node.seedCode)) },
            text = {
                Column {
                                        seed?.let { VarietyCatalogTraits.of(it) }?.let { Text(tr(it.details), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) }
                    val plantedDate = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(node.datePlantedEpochMillis))
                    Text(tr("Planted: $plantedDate"))
                    if (seed != null) {
                        val harvestMillis = node.datePlantedEpochMillis + seed.daysToHarvest.toLong() * 86_400_000L
                        val harvestDate = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(harvestMillis))
                        Text(tr("Expected harvest: ~$harvestDate"))
                        Text(tr("Spacing: ${seed.exclusionRadiusM}m"))
                        val crop = CropReference.forSeed(seed)
                        if (crop.yieldKgPerPlant > 0f) Text(tr("Typical yield: ~${"%.2f".format(crop.yieldKgPerPlant)} kg per plant"))
                        crop.nutrients?.let { n -> Text(tr("Per 100 g: ${n.energyKcal.toInt()} kcal, vit C ${n.vitaminCMg} mg, protein ${n.proteinG} g"), fontSize = 12.sp) }
                        Text(tr("${crop.feeding.label} • ${crop.sun.label} • pH ${crop.phMin}–${crop.phMax}"), fontSize = 12.sp, color = Color.Gray)
                        HardinessZones.describe(seed, activePlot?.hardinessZone)?.let { Text(tr(it), fontSize = 12.sp, color = Color(0xFFEAB308)) }
                        val guilds = GuildCatalog.guildsFor(seed, activeGuilds)
                        if (guilds.isNotEmpty()) Text(tr("Guild: ${guilds.joinToString { it.name }}"), fontSize = 12.sp, color = Color(0xFF10B981))
                        // FR-023/024: vendor slot (placeholder until a real vendor is linked).
                        val vendor = VendorRegistry.effectiveVendor(settings.preferredVendorId, Feature.isEnabled(Feature.VENDOR_TARGETING, settings.currentAppTier()))
                        val link = VendorRegistry.purchaseLink(seed, vendor)
                        Text(tr(if (link != null) "Buy seeds: ${vendor.displayName}" else "Buy seeds: ${vendor.displayName} — links not available yet"), fontSize = 11.sp, color = Color.Gray)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { infoDialogNode = null }) { Text(tr("Close")) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { changeVarietyNode = node; infoDialogNode = null }) { Text(tr("Change Variety")) }
                    TextButton(onClick = { planBNode = node; infoDialogNode = null }) { Text(tr("Plan B")) }
                    TextButton(onClick = {
                        val g = com.example.smartgardenplanner.core.GroupTools.groupOf(nodesState, node) { seedFor(it) }
                        infoDialogNode = null
                        rearrangeIds = g.map { it.id }.toSet()
                    }) { Text(tr("Group…")) }
                    TextButton(
                        onClick = {
                            launchSafely {
                                withContext(SgpExecutors.dbDispatcher) { database.plantedNodeDao().delete(node) }
                                reloadNodes()
                                undoStack.push(snapshotNow())
                                redoStack.clear()
                            }
                            infoDialogNode = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                    ) { Text(tr("Delete")) }
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
                        undoStack.push(snapshotNow())
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
            title = { Text(tr("Auto-populate Area")) },
            text = {
                Column {
                    Text(tr("Area: ${"%.2f".format(area.width)}m × ${"%.2f".format(area.height)}m"))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(tr("Variety"), fontSize = 12.sp, color = Color.Gray)
                    // [FIXED] Was a flat radio-button LazyColumn over the entire seed dictionary —
                    // unusable now that the catalog can hold up to 2,936 entries. Reuses the same
                    // 3-step picker used everywhere else a variety needs choosing.
                    OutlinedButton(onClick = { showAutoPopVarietyPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr(chosenSeed?.commonName ?: "Choose a Variety"))
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
                    Text(tr("Pattern"), fontSize = 12.sp, color = Color.Gray)
                    Row {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chosenPattern = AutoPopulateEngine.PackingPattern.LINE }) {
                            RadioButton(selected = chosenPattern == AutoPopulateEngine.PackingPattern.LINE, onClick = { chosenPattern = AutoPopulateEngine.PackingPattern.LINE })
                            Text(tr("Lines"), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chosenPattern = AutoPopulateEngine.PackingPattern.HEXAGON }) {
                            RadioButton(selected = chosenPattern == AutoPopulateEngine.PackingPattern.HEXAGON, onClick = { chosenPattern = AutoPopulateEngine.PackingPattern.HEXAGON })
                            Text(tr("Hexagon (denser)"), fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(tr("≈ $previewCount plants will be placed"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        if (localPoints.isEmpty() && autoPopulateEngine.estimateCount(area.width, area.height, seed.exclusionRadiusM * 2f * settings.spacingMarginMultiplier, chosenPattern) > com.example.smartgardenplanner.core.AutoPopulateEngine.MAX_POINTS) {
                            snackbarMessage = "That's more than ${com.example.smartgardenplanner.core.AutoPopulateEngine.MAX_POINTS} plants. Choose a smaller area or a variety with more spacing."
                            return@TextButton
                        }
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
                            undoStack.push(snapshotNow())
                            redoStack.clear()
                        }
                        pendingAreaSelection = null
                        // [FIXED] Real report: after populating, the screen stayed in SELECT_AREA mode,
                        // where the tap handler that opens the edit/delete dialog is disabled entirely —
                        // so the newly-placed plants looked "un-editable." Switching back to PLACE_NODE
                        // (which is also where tap-to-edit lives) fixes that directly.
                        canvasMode = CanvasMode.PLACE_NODE
                    }
                ) { Text(tr("Populate")) }
            },
            dismissButton = { TextButton(onClick = { pendingAreaSelection = null }) { Text(tr("Cancel")) } },
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
            title = { Text(tr("Auto-populate Custom Area")) },
            text = {
                Column {
                    Text(tr("${polygon.size}-point shape, ${"%.2f".format(boundingWidth)}m × ${"%.2f".format(boundingHeight)}m bounding box"))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(tr("Variety"), fontSize = 12.sp, color = Color.Gray)
                    OutlinedButton(onClick = { showAutoPopVarietyPicker2 = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr(chosenSeed?.commonName ?: "Choose a Variety"))
                    }
                    RecommendForArea(
                        enabled = Feature.isEnabled(Feature.RECOMMEND_AND_AUTOPOPULATE, settings.currentAppTier()),
                        compute = {
                            activePlot?.let { plot -> RecommendationEngine.recommend(seedDictionary, plotContext(plot), toPlotPoints(polygon), limit = 5) } ?: emptyList()
                        },
                        onPick = { code -> chosenSeedCode = code }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(tr("Pattern"), fontSize = 12.sp, color = Color.Gray)
                    Row {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chosenPattern = AutoPopulateEngine.PackingPattern.LINE }) {
                            RadioButton(selected = chosenPattern == AutoPopulateEngine.PackingPattern.LINE, onClick = { chosenPattern = AutoPopulateEngine.PackingPattern.LINE })
                            Text(tr("Lines"), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chosenPattern = AutoPopulateEngine.PackingPattern.HEXAGON }) {
                            RadioButton(selected = chosenPattern == AutoPopulateEngine.PackingPattern.HEXAGON, onClick = { chosenPattern = AutoPopulateEngine.PackingPattern.HEXAGON })
                            Text(tr("Hexagon (denser)"), fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(tr("≈ $previewCount plants will be placed"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        val allPoints = autoPopulateEngine.generatePositions(boundingWidth, boundingHeight, seed.exclusionRadiusM * 2f * settings.spacingMarginMultiplier, chosenPattern)
                        val localPoints = allPoints.filter { point -> pointInPolygon(minX + point.xM, minY + point.yM, polygon) }
                        if (allPoints.isEmpty() && autoPopulateEngine.estimateCount(boundingWidth, boundingHeight, seed.exclusionRadiusM * 2f * settings.spacingMarginMultiplier, chosenPattern) > com.example.smartgardenplanner.core.AutoPopulateEngine.MAX_POINTS) {
                            snackbarMessage = "That's more than ${com.example.smartgardenplanner.core.AutoPopulateEngine.MAX_POINTS} plants. Choose a smaller area or a variety with more spacing."
                            return@TextButton
                        }
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
                            undoStack.push(snapshotNow())
                            redoStack.clear()
                        }
                        pendingPolygonSelection = null
                        canvasMode = CanvasMode.PLACE_NODE
                    }
                ) { Text(tr("Populate")) }
            },
            dismissButton = { TextButton(onClick = { pendingPolygonSelection = null }) { Text(tr("Cancel")) } },
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
                    tr(when (step) {
                        0 -> "Choose a Category"
                        1 -> "Choose a ${selectedCategory?.lowercase()?.replaceFirstChar { it.uppercase() }} Species"
                        else -> "Choose a Cultivar"
                    })
                )
                if (step > 0) {
                    TextButton(
                        onClick = { if (step == 2) { step = 1; selectedSpeciesPrefix = null } else { step = 0; selectedCategory = null } },
                        contentPadding = PaddingValues(0.dp)
                    ) { Text(tr("← Back"), fontSize = 12.sp) }
                }
            }
        },
        text = {
            Column {
                if (step >= 1) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(tr("Search")) },
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
                                Text(tr(category.lowercase().replaceFirstChar { it.uppercase() }), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(tr("$count"), fontSize = 12.sp, color = Color.Gray)
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
                                Text(tr(name), fontSize = 14.sp)
                                Text(tr("$count cultivar${if (count == 1) "" else "s"}"), fontSize = 12.sp, color = Color.Gray)
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
                                                                        Text(tr(cultivarNameOf(seed)), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    VarietyCatalogTraits.of(seed)?.let { Text(tr(it.details), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary) }
                                    conflict?.let { Text(tr(it), fontSize = 10.sp, color = Color(0xFFEF4444)) }
                                    Text(
                                        tr("spacing ${seed.exclusionRadiusM}m • germinates ~${seed.germinationDays}d • harvest ~${seed.daysToHarvest}d"),
                                        fontSize = 10.sp, color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
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
        // FR-057: numbers only on the ticks; the unit is written once, at the start of the ruler.
        val shown = com.example.smartgardenplanner.core.DistanceFormatter.metersToDisplay(meter, unit)
        val value = if (tickIntervalM < 1f) String.format(java.util.Locale.US, "%.2f", shown) else kotlin.math.round(shown).toInt().toString()
        val label = if (meter == 0f) "$value ${unit.suffix.trim()}" else value
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
        Text(tr("Recommend for this area: Pro tier"), fontSize = 11.sp, color = Color.Gray)
        return
    }
    TextButton(onClick = { recs = compute() }, contentPadding = PaddingValues(0.dp)) { Text(tr("Recommend for this area"), fontSize = 12.sp) }
    recs?.let { list ->
        if (list.isEmpty()) Text(tr("Nothing in the catalog suits this spot."), fontSize = 11.sp, color = Color.Gray)
        list.forEach { r ->
            Column(modifier = Modifier.fillMaxWidth().clickable { onPick(r.seed.botanicalCode) }.padding(vertical = 3.dp)) {
                Text(tr(r.seed.commonName), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Text(tr(r.reasons.take(2).joinToString(" • ")), fontSize = 10.sp, color = Color.Gray)
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
    onMove: (() -> Unit)?,
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
        title = { Text(tr((if (isNew) "New: " else "") + type.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it.take(40) }, label = { Text(tr("Label (optional)")) }, singleLine = true)
                if (type.isBarrier) {
                    OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text(tr("Height (m)")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                if (type == SiteFeatureType.TREE) {
                    OutlinedTextField(value = radius, onValueChange = { radius = it }, label = { Text(tr("Crown radius (m)")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                if (type == SiteFeatureType.SLOPE) {
                    OutlinedTextField(value = grade, onValueChange = { grade = it }, label = { Text(tr("Grade (%) — 1 m drop over 10 m is 10 %")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Text(tr("Downhill direction (compass)"), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        directions.forEach { (name, deg) ->
                            FilterChip(selected = direction == deg, onClick = { direction = deg }, label = { Text(tr(name), fontSize = 10.sp) })
                        }
                    }
                }
                                if (type == SiteFeatureType.FLOOD) {
                    OutlinedTextField(value = months, onValueChange = { months = it }, label = { Text(tr("Months it floods, e.g. 3,4,5")) }, singleLine = true)
                }
                // FR-039: irrigation settings.
                if (type.isIrrigation) {
                    OutlinedTextField(value = radius, onValueChange = { radius = it }, singleLine = true,
                        label = { Text(tr(when (type) { SiteFeatureType.SPRINKLER -> "How far it throws water (m)"; SiteFeatureType.DRIP_LINE -> "Wetted strip each side (m)"; else -> "Hose length (m)" })) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                if (type == SiteFeatureType.SPRINKLER) {
                    Text(tr("Pattern"), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf("Full" to 360f, "¾" to 270f, "½" to 180f, "¼" to 90f).forEach { (name, arc) ->
                            FilterChip(selected = (grade.toFloatOrNull() ?: 360f) == arc, onClick = { grade = arc.toInt().toString() }, label = { Text(tr(name), fontSize = 11.sp) })
                        }
                    }
                    Text(tr("For a part circle, which way the middle of the spray points"), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        directions.forEach { (name, deg) ->
                            FilterChip(selected = direction == deg, onClick = { direction = deg }, label = { Text(tr(name), fontSize = 10.sp) })
                        }
                    }
                }
                if (type == SiteFeatureType.FULL_SUN || type == SiteFeatureType.PART_SHADE || type == SiteFeatureType.FULL_SHADE) {
                    Text(tr("Plants that need more sun than this area gets are flagged in the harmony report and left out of suggestions for it."), fontSize = 11.sp, color = Color.Gray)
                }
                error?.let { Text(tr(it), color = Color(0xFFEF4444), fontSize = 12.sp) }
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
                    type == SiteFeatureType.SPRINKLER && (r == null || r < 0.5f || r > 30f) -> "Enter a throw radius between 0.5 and 30 m."
                    type == SiteFeatureType.DRIP_LINE && (r == null || r < 0.05f || r > 2f) -> "Enter a wetted width between 0.05 and 2 m."
                    type == SiteFeatureType.HOSE_BIB && (r == null || r < 1f || r > 60f) -> "Enter a hose length between 1 and 60 m."
                    type == SiteFeatureType.FLOOD && monthList.any { it.toIntOrNull() == null || it.toInt() !in 1..12 } -> "Months are numbers 1–12, separated by commas."
                    else -> null
                }
                if (error == null) {
                    onSave(
                        initial.copy(
                            label = label.trim(),
                            heightM = h ?: 0f,
                            radiusM = r ?: 0f,
                                                        slopeGradePct = if (type == SiteFeatureType.SPRINKLER) (g ?: 360f) else (g ?: 0f),
                            slopeDirectionDeg = direction,
                            floodMonths = monthList.joinToString(",")
                        )
                    )
                }
            }) { Text(tr("Save")) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))) { Text(tr("Delete")) }
                }
                if (onMove != null) {
                    TextButton(onClick = onMove) { Text(tr("Move")) }
                }
                TextButton(onClick = onDismiss) { Text(tr("Cancel")) }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}


/** FR-027: the list of plants to place ("what and how many"). The app works out where. */
@Composable
private fun AutoPlanRequestDialog(
    seedDictionary: List<SeedEntity>,
        rows: androidx.compose.runtime.snapshots.SnapshotStateList<Pair<String, Int>>,
    priority: androidx.compose.runtime.snapshots.SnapshotStateList<String>,
    shapes: androidx.compose.runtime.snapshots.SnapshotStateMap<String, List<Int>>,
    inAreaCount: Int,
    replaceExisting: Boolean,
    onReplaceChange: (Boolean) -> Unit,
    checksFor: (List<PlantRequest>) -> List<com.example.smartgardenplanner.core.PlanCheck>,
    usual: List<Pair<SeedEntity, Int>>,
    layout: PlantingLayout,
    onLayoutChange: (PlantingLayout) -> Unit,
        hasHistory: Boolean,
    title: String,
    onHowManyFit: (List<PlantRequest>) -> Unit,
    conflictFor: (SeedEntity) -> String?,
    orientationSet: Boolean,
    running: Boolean,
    onSetDirection: () -> Unit,
    onPlan: (List<PlantRequest>) -> Unit,
    onDismiss: () -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    val byCode = remember(seedDictionary) { seedDictionary.associateBy { it.botanicalCode } }
    fun requests() = rows.mapNotNull { (c, n) -> byCode[c]?.let { PlantRequest(it, n, c in priority, shapes[c]?.takeIf { sh -> sh.sum() == n }) } }
    // FR-043: checks before planning, recomputed (off the main thread) as the list changes.
    var checks by remember { mutableStateOf<List<com.example.smartgardenplanner.core.PlanCheck>>(emptyList()) }
    val rowsKey = rows.toList(); val priorityKey = priority.toList()
    LaunchedEffect(rowsKey, priorityKey) {
        kotlinx.coroutines.delay(250)
        val reqs = requests()
        checks = try { withContext(Dispatchers.Default) { checksFor(reqs) } } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
                title = { Text(tr(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Add each plant and how many. The app decides where each one goes: tall plants behind short ones, sun lovers in the sun, pollinators near the crops that need them, similar watering needs together. Tap ☆ on the plants that matter most: they're placed first, in the sunniest spots."), fontSize = 12.sp)
                if (!orientationSet) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("⚠ The plot's direction isn't set, so north is assumed to be the top edge."), fontSize = 11.sp, color = Color(0xFFEAB308), modifier = Modifier.weight(1f))
                        TextButton(onClick = onSetDirection) { Text(tr("Set it")) }
                    }
                }
                                if (hasHistory) Text(tr("Past seasons on this plot are used for crop rotation: crops are kept away from where their family grew recently."), fontSize = 11.sp, color = Color.Gray)
                // FR-063: keep the plants already in this area, or plan it from blank.
                if (inAreaCount > 0) {
                    Text(tr("$inAreaCount plants are already in this area"), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onReplaceChange(false) }) {
                        RadioButton(selected = !replaceExisting, onClick = { onReplaceChange(false) }); Text(tr("Keep them where they are and plan around them"), fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onReplaceChange(true) }) {
                        RadioButton(selected = replaceExisting, onClick = { onReplaceChange(true) }); Text(tr("Start from a blank area: the new plan replaces them when you keep it"), fontSize = 12.sp)
                    }
                }
                // FR-034: what this gardener usually plants, one tap to add.
                if (usual.isNotEmpty()) {
                    Text(tr("What you usually plant"), fontSize = 11.sp, color = Color.Gray)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        usual.forEach { (s, _) ->
                            OutlinedButton(
                                onClick = {
                                    val i = rows.indexOfFirst { it.first == s.botanicalCode }
                                    if (i >= 0) rows[i] = s.botanicalCode to rows[i].second + 1 else rows.add(s.botanicalCode to 3)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) { Text(tr("+ ${CropReference.speciesName(s)}"), fontSize = 11.sp) }
                        }
                    }
                }
                Column(modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                    if (rows.isEmpty()) Text(tr("No plants yet. Tap Add a plant."), fontSize = 12.sp, color = Color.Gray)
                    rows.forEachIndexed { i, (code, count) ->
                        val seed = byCode[code]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val starred = code in priority
                            TextButton(onClick = { if (starred) priority.remove(code) else priority.add(code) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(32.dp)) {
                                Text(tr(if (starred) "★" else "☆"), fontSize = 18.sp, color = if (starred) Color(0xFFD97706) else Color.Gray)
                            }
                            Box(modifier = Modifier.size(10.dp).background(VegetableColorPalette.colorFor(seed), androidx.compose.foundation.shape.CircleShape))
                            Spacer(Modifier.width(6.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                Text(tr(seed?.commonName ?: code), fontSize = 12.sp)
                                seed?.let { VarietyCatalogTraits.of(it) }?.let { Text(tr(it.details), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary) }
                            }
                            TextButton(onClick = { if (count > 1) rows[i] = code to count - 1 else rows.removeAt(i) }, contentPadding = PaddingValues(0.dp)) { Text(tr("−"), fontSize = 16.sp) }
                            Text(tr("$count"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { rows[i] = code to (count + 1).coerceAtMost(500) }, contentPadding = PaddingValues(0.dp)) { Text(tr("+"), fontSize = 16.sp) }
                            TextButton(onClick = { rows[i] = code to (count + 5).coerceAtMost(500) }, contentPadding = PaddingValues(0.dp)) { Text(tr("+5"), fontSize = 11.sp) }
                        }
                        // FR-047: how this clump is laid out, chosen before planting.
                        if (layout == PlantingLayout.CLUMPS && seed != null && count > 1) {
                            var open by remember(code) { mutableStateOf(false) }
                            val chosen = shapes[code]?.takeIf { it.sum() == count }
                            Box(modifier = Modifier.padding(start = 48.dp)) {
                                TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                    Text(tr("Arrange: " + (chosen?.let { com.example.smartgardenplanner.core.ClumpShapes.label(it) } ?: "planner chooses") + " ▾"), fontSize = 11.sp)
                                }
                                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                    val opts = com.example.smartgardenplanner.core.ClumpShapes.options(count)
                                    DropdownMenuItem(text = { Text(tr("Let the planner choose (${opts.first().label})"), fontSize = 12.sp) }, onClick = { shapes.remove(code); open = false })
                                    opts.forEach { o -> DropdownMenuItem(text = { Text(tr((if (chosen == o.rows) "✓ " else "") + o.label), fontSize = 12.sp) }, onClick = { shapes[code] = o.rows; open = false }) }
                                    val tidy = com.example.smartgardenplanner.core.ClumpShapes.nearbyTidy(count).take(3)
                                    if (tidy.isNotEmpty()) {
                                        Divider()
                                        Text(tr("Neater counts"), fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp))
                                        tidy.forEach { t -> DropdownMenuItem(text = { Text(tr("${t.count} = ${t.label}"), fontSize = 12.sp) }, onClick = { rows[i] = code to t.count; shapes[code] = t.rows; open = false }) }
                                    }
                                }
                            }
                        }
                    }
                }
                                                Row {
                    TextButton(onClick = { picking = true }) { Text(tr("+ Add a plant")) }
                    // FR-040: keep the list's proportions and scale to what the area holds.
                    TextButton(enabled = rows.isNotEmpty(), onClick = { onHowManyFit(rows.mapNotNull { (c, n) -> byCode[c]?.let { PlantRequest(it, n.coerceAtLeast(1)) } }) }) { Text(tr("How many fit?")) }
                }
                if (checks.isNotEmpty()) {
                    Text(tr("Checks before planning"), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Column(modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                        checks.forEach { c ->
                            Text(
                                tr((when (c.severity) { com.example.smartgardenplanner.core.Severity.HIGH -> "⚠ "; com.example.smartgardenplanner.core.Severity.MEDIUM -> "• "; else -> "✓ " }) + c.text),
                                fontSize = 11.sp,
                                color = when (c.severity) { com.example.smartgardenplanner.core.Severity.HIGH -> MaterialTheme.colorScheme.error; com.example.smartgardenplanner.core.Severity.MEDIUM -> Color(0xFFB45309); else -> Color.Gray }
                            )
                        }
                    }
                }
                // FR-032: clumps (default) or rows.
                Text(tr("How should each crop be arranged?"), fontSize = 11.sp, color = Color.Gray)
                PlantingLayout.entries.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onLayoutChange(l) }) {
                        RadioButton(selected = layout == l, onClick = { onLayoutChange(l) })
                        Text(tr(l.label), fontSize = 13.sp)
                    }
                }
                Text(tr(layout.description), fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            TextButton(
                enabled = rows.any { it.second > 0 } && !running,
                onClick = { onPlan(requests()) }
            ) { Text(tr(if (running) "Planning…" else "Plan it")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } },
        containerColor = MaterialTheme.colorScheme.surface
    )
    if (picking) {
        VarietyPickerDialog(
            seedDictionary = seedDictionary,
            conflictFor = conflictFor,
            onSelect = { code ->
                val i = rows.indexOfFirst { it.first == code }
                if (i >= 0) rows[i] = code to rows[i].second + 1 else rows.add(code to 4)
                picking = false
            },
            onDismiss = { picking = false }
        )
    }
}
