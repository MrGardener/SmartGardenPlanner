package com.example.smartgardenplanner.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.smartgardenplanner.core.AppSettings
import com.example.smartgardenplanner.core.CareLogEntity
import com.example.smartgardenplanner.core.CarePlanner
import com.example.smartgardenplanner.core.CarePreference
import com.example.smartgardenplanner.core.CareTaskType
import com.example.smartgardenplanner.core.CropReference
import com.example.smartgardenplanner.core.Feature
import com.example.smartgardenplanner.core.FoodPlanner
import com.example.smartgardenplanner.core.HardinessZones
import com.example.smartgardenplanner.core.HarmonyAnalyzer
import com.example.smartgardenplanner.core.NutritionEntity
import com.example.smartgardenplanner.core.Nutrients
import com.example.smartgardenplanner.core.PlotEntity
import com.example.smartgardenplanner.core.PlotShape
import com.example.smartgardenplanner.core.RecommendationEngine
import com.example.smartgardenplanner.core.Severity
import com.example.smartgardenplanner.core.SgpExecutors
import com.example.smartgardenplanner.core.SiteFeatureType
import com.example.smartgardenplanner.core.SoilAnalyzer
import com.example.smartgardenplanner.core.SoilProfile
import com.example.smartgardenplanner.core.SunlightEngine
import com.example.smartgardenplanner.core.currentAppTier
import com.example.smartgardenplanner.data.AppDatabase
import com.example.smartgardenplanner.data.NetworkGateway
import com.example.smartgardenplanner.data.OnlineResult
import com.example.smartgardenplanner.data.OnlineSources
import com.example.smartgardenplanner.data.PlotInsightsLoader
import com.example.smartgardenplanner.data.PlotSnapshot
import com.example.smartgardenplanner.data.SecurityRepositoryImpl
import com.example.smartgardenplanner.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val COMPASS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

private fun compass(deg: Float): String = COMPASS[(((deg % 360f + 360f) % 360f + 22.5f) / 45f).toInt() % 8]

private fun formatDate(millis: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))

/**
 * Plot insights: site details and soil (FR-013/014, FR-003 to FR-007), garden harmony (FR-011),
 * suggestions (FR-014), care plans and reminders (FR-017 to FR-019), and food (FR-016, FR-020 to FR-022).
 * Features above the user's tier show what they are and how to unlock them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotInsightsScreen(plotId: Long, database: AppDatabase, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository(SecurityRepositoryImpl(database.configDao())) }
    val online = remember { OnlineSources(database) }
    var settings by remember { mutableStateOf(AppSettings.DEFAULT) }
    var snapshot by remember { mutableStateOf<PlotSnapshot?>(null) }
    var careLog by remember { mutableStateOf<List<CareLogEntity>>(emptyList()) }
    var nutritionRows by remember { mutableStateOf<Map<String, NutritionEntity>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    val inFlight by NetworkGateway.inFlight.collectAsState()

    suspend fun reload() {
        try {
            withContext(SgpExecutors.dbDispatcher) {
                val s = settingsRepository.load()
                val snap = PlotInsightsLoader.load(database, plotId, s)
                val log = database.careLogDao().getByPlotId(plotId)
                val nut = database.nutritionDao().getAll().associateBy { it.speciesKey }
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    settings = s
                    snapshot = snap
                    careLog = log
                    nutritionRows = nut
                    if (snap == null) error = "This plot no longer exists."
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "Couldn't read this plot (${e.javaClass.simpleName})."
        }
    }

    fun launchSafely(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Couldn't save (${e.javaClass.simpleName})."
            }
        }
    }

    fun savePlot(updated: PlotEntity, done: String = "Saved.") = launchSafely {
        withContext(SgpExecutors.dbDispatcher) { database.plotDao().update(updated.copy(lastModifiedTimestamp = System.currentTimeMillis())) }
        reload()
        message = done
    }

    fun saveSettings(updated: AppSettings) = launchSafely {
        settings = updated
        withContext(SgpExecutors.dbDispatcher) { settingsRepository.save(updated) }
        reload()
    }

    LaunchedEffect(plotId) { reload() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); message = null }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(snapshot?.context?.plot?.name?.let { "Insights: $it" } ?: "Plot insights", maxLines = 1) },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                    actions = { OnlineBadge(settings.onlineFeaturesEnabled) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                if (inFlight > 0) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Connecting to an online data service…", fontSize = 11.sp, color = Color(0xFF0EA5E9), modifier = Modifier.padding(horizontal = 16.dp))
                }
                @Suppress("DEPRECATION")
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background) {
                    listOf("Site", "Harmony", "Suggest", "Care", "Food").forEachIndexed { i, label ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                    }
                }
            }
        }
    ) { padding ->
        val snap = snapshot
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (snap == null) {
                if (error == null) CircularProgressIndicator()
                return@Column
            }
            when (tab) {
                0 -> SiteTab(snap, settings, online, context, ::savePlot, { message = it }, scope) { z ->
                    // Offline starter table first, then phzmapi.org when Online features are on (FR-028).
                    (com.example.smartgardenplanner.data.ZipLookup.zone(database, z, settings.onlineFeaturesEnabled) as? OnlineResult.Success)?.value
                }
                1 -> HarmonyTab(snap, settings)
                2 -> SuggestTab(snap, settings)
                3 -> CareTab(snap, settings, careLog, online, ::saveSettings, { type ->
                    launchSafely {
                        withContext(SgpExecutors.dbDispatcher) {
                            database.careLogDao().insert(CareLogEntity(plotId = plotId, taskType = type.name, doneAtEpochMillis = System.currentTimeMillis()))
                        }
                        reload()
                        message = if (type == CareTaskType.WATER) "Marked as watered." else "Marked as fertilized."
                    }
                }, scope)
                else -> FoodTab(snap, settings, nutritionRows, online, { reloadMsg ->
                    scope.launch { reload(); reloadMsg?.let { message = it } }
                }, scope)
            }
        }
    }
}

@Composable
fun OnlineBadge(on: Boolean) {
    val color = if (on) Color(0xFF0EA5E9) else Color.Gray
    Text(
        if (on) "ONLINE ON" else "OFFLINE",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .padding(end = 12.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun Section(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            subtitle?.let { Text(it, fontSize = 11.sp, color = Color.Gray) }
            content()
        }
    }
}

/** Shows a locked feature: what it does and which tier unlocks it. Returns true when unlocked. */
@Composable
private fun gate(feature: Feature, settings: AppSettings, what: String): Boolean {
    if (Feature.isEnabled(feature, settings.currentAppTier())) return true
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("$what needs the ${feature.tierLabel} tier. Change it in Settings → Catalog.", fontSize = 12.sp, color = Color.Gray)
    }
    return false
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

private fun lastKnownLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return try {
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
    } catch (e: SecurityException) {
        null
    }
}

// ============================================================== Site

@Composable
private fun SiteTab(
    snap: PlotSnapshot,
    settings: AppSettings,
    online: OnlineSources,
    context: Context,
    savePlot: (PlotEntity, String) -> Unit,
    say: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    lookupZip: suspend (String) -> String?
) {
    val plot = snap.context.plot

    Section("Location and climate", "Used for winter hardiness, sun angles and weather. Nothing leaves the phone unless online features are on.") {
        var zoneMenu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hardiness zone: ", fontSize = 13.sp)
            Box {
                OutlinedButton(onClick = { zoneMenu = true }) { Text(plot.hardinessZone ?: "Not set") }
                DropdownMenu(expanded = zoneMenu, onDismissRequest = { zoneMenu = false }) {
                    DropdownMenuItem(text = { Text("Not set") }, onClick = { zoneMenu = false; savePlot(plot.copy(hardinessZone = null), "Zone cleared.") })
                    HardinessZones.LABELS.forEach { z ->
                        DropdownMenuItem(text = { Text("Zone $z") }, onClick = { zoneMenu = false; savePlot(plot.copy(hardinessZone = z), "Zone set to $z.") })
                    }
                }
            }
        }
        var zip by remember(plot.id) { mutableStateOf(plot.locationZip ?: "") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = zip, onValueChange = { zip = it.take(10) }, label = { Text("ZIP code", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch {
                    val loc = com.example.smartgardenplanner.data.ZipLookup.location(context, zip)
                    val zone = if (com.example.smartgardenplanner.core.ZipTable.isValidZip(zip)) lookupZip(zip) else null
                    val updated = plot.copy(
                        locationZip = zip.ifBlank { null },
                        latitude = loc?.latitude ?: plot.latitude,
                        longitude = loc?.longitude ?: plot.longitude,
                        hardinessZone = zone ?: plot.hardinessZone
                    )
                    savePlot(updated, buildString {
                        append(if (loc != null) "Location set from ZIP $zip. " else "ZIP not in the offline location table. ")
                        append(if (zone != null) "Zone $zone." else if (settings.onlineFeaturesEnabled) "Zone not found: pick it from the list." else "Pick the zone from the list, or turn on Online features to look it up.")
                    })
                }
            }) { Text("Look up") }
        }

        var lat by remember(plot.id, plot.latitude) { mutableStateOf(plot.latitude?.toString() ?: "") }
        var lon by remember(plot.id, plot.longitude) { mutableStateOf(plot.longitude?.toString() ?: "") }
        val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) say("Location permission denied. Enter latitude and longitude by hand.")
            else {
                val loc = lastKnownLocation(context)
                if (loc != null) { lat = "%.4f".format(Locale.US, loc.latitude); lon = "%.4f".format(Locale.US, loc.longitude) }
                else say("The phone has no recent location. Open a maps app once, or type the coordinates.")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Latitude", lat, { lat = it }, Modifier.weight(1f))
            NumberField("Longitude", lon, { lon = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val loc = lastKnownLocation(context)
                    if (loc != null) { lat = "%.4f".format(Locale.US, loc.latitude); lon = "%.4f".format(Locale.US, loc.longitude) }
                    else say("The phone has no recent location. Open a maps app once, or type the coordinates.")
                } else {
                    locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }) { Text("Use my location") }
            Button(onClick = {
                val la = lat.toDoubleOrNull()
                val lo = lon.toDoubleOrNull()
                if ((lat.isNotBlank() && (la == null || la !in -90.0..90.0)) || (lon.isNotBlank() && (lo == null || lo !in -180.0..180.0))) {
                    say("Latitude must be −90 to 90 and longitude −180 to 180.")
                } else {
                    savePlot(plot.copy(latitude = la, longitude = lo), "Location saved.")
                }
            }) { Text("Save") }
        }

        var bearing by remember(plot.id, plot.northBearingDeg) { mutableStateOf(plot.northBearingDeg) }
        Text("Top edge of the plot faces: ${compass(bearing)} (${bearing.toInt()}°)", fontSize = 13.sp)
        Text("Stand at the bottom edge looking across the plot and check a compass. Used for shade estimates.", fontSize = 11.sp, color = Color.Gray)
        Slider(value = bearing, onValueChange = { bearing = it }, valueRange = 0f..345f, steps = 22,
            onValueChangeFinished = { savePlot(plot.copy(northBearingDeg = bearing, orientationSet = true), "Orientation saved.") })
    }

    Section("Soil", "FR-013: record a soil test or estimate, and get advice on improving it.") {
        if (gate(Feature.SOIL_TEST, settings, "Soil records")) {
            var sand by remember(plot.id) { mutableStateOf(plot.soilSandPct?.toString() ?: "") }
            var silt by remember(plot.id) { mutableStateOf(plot.soilSiltPct?.toString() ?: "") }
            var clay by remember(plot.id) { mutableStateOf(plot.soilClayPct?.toString() ?: "") }
            var om by remember(plot.id) { mutableStateOf(plot.soilOrganicPct?.toString() ?: "") }
            var ph by remember(plot.id) { mutableStateOf(plot.soilPh?.toString() ?: "") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField("Sand %", sand, { sand = it }, Modifier.weight(1f))
                NumberField("Silt %", silt, { silt = it }, Modifier.weight(1f))
                NumberField("Clay %", clay, { clay = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField("Organic matter %", om, { om = it }, Modifier.weight(1f))
                NumberField("pH", ph, { ph = it }, Modifier.weight(1f))
            }
            Button(onClick = {
                val s = sand.toFloatOrNull(); val si = silt.toFloatOrNull(); val c = clay.toFloatOrNull()
                val o = om.toFloatOrNull(); val p = ph.toFloatOrNull()
                val textureGiven = listOf(sand, silt, clay).count { it.isNotBlank() }
                val problem = when {
                    textureGiven in 1..2 -> "Enter all three of sand, silt and clay, or none."
                    textureGiven == 3 && (s == null || si == null || c == null) -> "Sand, silt and clay must be numbers."
                    textureGiven == 3 -> SoilAnalyzer.validateTexture(s!!, si!!, c!!)
                    om.isNotBlank() && (o == null || o < 0f || o > 100f) -> "Organic matter must be 0–100 %."
                    ph.isNotBlank() && (p == null || p < 3f || p > 10f) -> "pH must be between 3 and 10."
                    else -> null
                }
                if (problem != null) say(problem)
                else savePlot(plot.copy(soilSandPct = s, soilSiltPct = si, soilClayPct = c, soilOrganicPct = o, soilPh = p), "Soil saved.")
            }) { Text("Save soil") }
            SoilAnalyzer.guidance(SoilProfile.of(plot)).forEach { Text("• $it", fontSize = 12.sp) }
        }
    }

    Section("Sunlight", "FR-005/006/007: marked sun and shade areas, barriers, and sunshine for the location.") {
        if (gate(Feature.HISTORICAL_SUNLIGHT, settings, "Sunlight estimates")) {
            val ctx = snap.context
            val lat = ctx.latitude
            val barriers = ctx.barriers
            if (plot.latitude == null) Text("No location set, so ${SunlightEngine.DEFAULT_LATITUDE.toInt()}° N is assumed.", fontSize = 11.sp, color = Color(0xFFEAB308))
            val cx = plot.lengthM / 2f
            val cy = plot.widthM / 2f
            val today = SunlightEngine.directSunHours(cx, cy, lat, ctx.dayOfYear, plot.northBearingDeg, barriers)
            val summer = SunlightEngine.directSunHours(cx, cy, lat, SunlightEngine.midsummerDay(lat), plot.northBearingDeg, barriers)
            Text("Plot centre, clear sky: ~${"%.1f".format(today)} h direct sun today, ~${"%.1f".format(summer)} h at midsummer.", fontSize = 13.sp)
            Text("${barriers.size} barrier${if (barriers.size == 1) "" else "s"} and ${ctx.areaFeatures.size} marked area${if (ctx.areaFeatures.size == 1) "" else "s"} on this plot. Add them on the canvas: menu → Site tools.", fontSize = 11.sp, color = Color.Gray)

            var measured by remember(plot.id) { mutableStateOf<List<Double>?>(null) }
            var measuredYear by remember(plot.id) { mutableStateOf<Int?>(null) }
            LaunchedEffect(plot.id) {
                online.cachedSunshine(plot.id)?.let { measured = it.second; measuredYear = it.first }
            }
            val daylight = SunlightEngine.monthlyDayLength(lat)
            Text(if (measured != null) "Month: daylight / measured sunshine ($measuredYear)" else "Month: daylight hours (clear-sky maximum)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            MONTHS.forEachIndexed { i, m ->
                val sun = measured?.getOrNull(i)
                Text("$m: ${"%.1f".format(daylight[i])} h" + (sun?.let { " / ${"%.1f".format(it)} h sunshine" } ?: ""), fontSize = 12.sp)
            }
            val la = plot.latitude
            val lo = plot.longitude
            OutlinedButton(
                enabled = la != null && lo != null,
                onClick = {
                    scope.launch {
                        when (val r = online.monthlySunshine(plot.id, la!!, lo!!, settings.onlineFeaturesEnabled, forceRefresh = true)) {
                            is OnlineResult.Success -> { measured = r.value; measuredYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 1; say("Sunshine history updated.") }
                            OnlineResult.Disabled -> say("Online features are off (Settings → Online features).")
                            is OnlineResult.Failure -> say(r.message)
                        }
                    }
                }
            ) { Text(if (la == null || lo == null) "Set a location to get sunshine history" else "Get last year's sunshine (online)") }
        }
    }

    Section("Slopes and flooding", "FR-003/004: marked on the canvas (menu → Site tools).") {
        val areas = snap.context.areaFeatures.filter { it.featureType == SiteFeatureType.SLOPE.name || it.featureType == SiteFeatureType.FLOOD.name }
        if (!Feature.isEnabled(Feature.SLOPE_CONFIGURATION, settings.currentAppTier())) {
            gate(Feature.SLOPE_CONFIGURATION, settings, "Slope and flood areas")
        } else if (areas.isEmpty()) {
            Text("None marked.", fontSize = 12.sp, color = Color.Gray)
        } else {
            areas.forEach { a ->
                if (a.featureType == SiteFeatureType.SLOPE.name) {
                    val advice = when {
                        a.slopeGradePct >= 15f -> "Steep: terrace it or use it for perennials and ground cover; plant rows across the slope."
                        a.slopeGradePct >= 5f -> "Plant rows across the slope (along the contour) and mulch to stop soil washing away."
                        else -> "Gentle: fine for most beds; the low side stays wetter."
                    }
                    Text("Slope${if (a.label.isNotBlank()) " '${a.label}'" else ""}: ${"%.0f".format(a.slopeGradePct)} %, downhill to the ${compass(a.slopeDirectionDeg)}. $advice", fontSize = 12.sp)
                } else {
                    val months = a.floodMonths.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..12 }.joinToString(", ") { MONTHS[it - 1] }
                    Text("Floods${if (a.label.isNotBlank()) " '${a.label}'" else ""}${if (months.isNotBlank()) " in $months" else ""}. Only flood-tolerant plants are suggested there; use raised beds for others.", fontSize = 12.sp)
                }
            }
        }
    }
    Text("Plot area: ${"%.1f".format(PlotShape.areaM2(plot))} m²" + if (PlotShape.outline(plot).isNotEmpty()) " (custom outline)" else "", fontSize = 12.sp, color = Color.Gray)
}

// ============================================================== Harmony

@Composable
private fun HarmonyTab(snap: PlotSnapshot, settings: AppSettings) {
    Section("Garden harmony", "FR-011: what's planted, what clashes, and what to do about it.") {
        if (!gate(Feature.HARMONY_REPORT, settings, "The harmony report")) return@Section
        val report = remember(snap) { HarmonyAnalyzer.analyze(snap.context, snap.catalog, settings.spacingMarginMultiplier) }
        val scoreColor = when {
            report.score >= 80 -> Color(0xFF10B981)
            report.score >= 50 -> Color(0xFFEAB308)
            else -> Color(0xFFEF4444)
        }
        Text("Harmony score: ${report.score}/100", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = scoreColor)
        Text(if (snap.context.guilds.isNotEmpty()) "Guilds are ON: guild partners are allowed closer together." else "Guilds are OFF: normal spacing for all plants.", fontSize = 11.sp, color = if (snap.context.guilds.isNotEmpty()) Color(0xFF10B981) else Color.Gray)
        if (report.plantCounts.isEmpty()) Text("Nothing planted yet.", fontSize = 12.sp, color = Color.Gray)
        else Text("Planted: " + report.plantCounts.joinToString(", ") { "${it.first} ×${it.second}" }, fontSize = 12.sp)
    }
    val report = remember(snap) { HarmonyAnalyzer.analyze(snap.context, snap.catalog, settings.spacingMarginMultiplier) }
    if (!Feature.isEnabled(Feature.HARMONY_REPORT, settings.currentAppTier())) return
    Section("Issues") {
        if (report.issues.isEmpty()) Text("No problems found.", fontSize = 12.sp, color = Color(0xFF10B981))
        report.issues.forEach { issue ->
            val c = when (issue.severity) { Severity.HIGH -> Color(0xFFEF4444); Severity.MEDIUM -> Color(0xFFEAB308); Severity.LOW -> Color.Gray }
            Row {
                Text(issue.severity.label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c, modifier = Modifier.width(52.dp))
                Text(issue.text, fontSize = 12.sp)
            }
        }
    }
    if (report.goodPairs.isNotEmpty()) {
        Section("Working well together") { report.goodPairs.forEach { Text("✓ $it", fontSize = 12.sp, color = Color(0xFF10B981)) } }
    }
    Section("Recommendations") {
        if (report.recommendations.isEmpty()) Text("Nothing to add.", fontSize = 12.sp, color = Color.Gray)
        report.recommendations.forEach { Text("• $it", fontSize = 12.sp) }
    }
}

// ============================================================== Suggest

@Composable
private fun SuggestTab(snap: PlotSnapshot, settings: AppSettings) {
    Section("Suggested for this plot", "FR-014: based on the hardiness zone, soil, marked sun/shade and flood areas, and what's already planted. To fill an area with a suggestion, use the area tool on the canvas and tap Recommend (Pro).") {
        if (!gate(Feature.ZONE_AWARE_RECOMMENDATIONS, settings, "Suggestions")) return@Section
        var foodOnly by remember { mutableStateOf(true) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = foodOnly, onCheckedChange = { foodOnly = it })
            Text("Food crops only", fontSize = 13.sp)
        }
        if (snap.context.zone == null) Text("Tip: set the hardiness zone on the Site tab for better suggestions.", fontSize = 11.sp, color = Color(0xFFEAB308))
        val recs = remember(snap, foodOnly) { RecommendationEngine.recommend(snap.catalog, snap.context, null, 20, foodOnly) }
        if (recs.isEmpty()) Text("Nothing in the current catalog fits. Try a larger catalog tier.", fontSize = 12.sp, color = Color.Gray)
        recs.forEach { r ->
            Column {
                Text(CropReference.speciesName(r.seed) + " (e.g. ${r.seed.commonName.substringAfter(" - ", r.seed.commonName)})", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(r.reasons.joinToString(" • "), fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

// ============================================================== Care

@Composable
private fun CareTab(
    snap: PlotSnapshot,
    settings: AppSettings,
    careLog: List<CareLogEntity>,
    online: OnlineSources,
    saveSettings: (AppSettings) -> Unit,
    markDone: (CareTaskType) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val plot = snap.context.plot
    Section("Today's care", "FR-019: watering and fertilizing due now. Daily notifications can be switched on in Settings → Household & care.") {
        if (gate(Feature.CARE_REMINDERS, settings, "Care reminders")) {
            var rainMm by remember(plot.id) { mutableStateOf<Double?>(null) }
            var rainNote by remember(plot.id) { mutableStateOf<String?>(null) }
            val tasks = remember(snap, careLog, rainMm) {
                CarePlanner.dueTasks(snap.context, careLog, settings.carePreferenceEnum, System.currentTimeMillis(), rainMm, settings.rainSkipThresholdMm.toDouble())
            }
            if (tasks.isEmpty()) Text("Nothing planted yet.", fontSize = 12.sp, color = Color.Gray)
            tasks.forEach { t ->
                Column {
                    Text(
                        (if (t.isDue) "DUE • " else if (t.rainSkip) "SKIP • " else "Next ${formatDate(t.dueEpochMillis)} • ") + t.title,
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = if (t.isDue) Color(0xFFEAB308) else if (t.rainSkip) Color(0xFF0EA5E9) else Color.Unspecified
                    )
                    Text(t.detail, fontSize = 11.sp, color = Color.Gray)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { markDone(CareTaskType.WATER) }) { Text("Watered") }
                OutlinedButton(onClick = { markDone(CareTaskType.FERTILIZE) }) { Text("Fertilized") }
            }
            val la = plot.latitude
            val lo = plot.longitude
            OutlinedButton(enabled = la != null && lo != null, onClick = {
                scope.launch {
                    when (val r = online.rainMm(la!!, lo!!, settings.onlineFeaturesEnabled)) {
                        is OnlineResult.Success -> { rainMm = r.value; rainNote = "Rain yesterday and today: ${"%.1f".format(r.value)} mm." }
                        OnlineResult.Disabled -> rainNote = "Online features are off, so rain isn't checked."
                        is OnlineResult.Failure -> rainNote = r.message
                    }
                }
            }) { Text(if (la == null || lo == null) "Set a location to check rain" else "Check rain (online)") }
            rainNote?.let { Text(it, fontSize = 11.sp, color = Color(0xFF0EA5E9)) }
            Text("Reminders: " + if (settings.careRemindersEnabled) "ON" else "OFF (Settings → Household & care)", fontSize = 11.sp, color = Color.Gray)
        }
    }

    Section("Fertilizing plan", "FR-017: based on what's planted, when, and the soil.") {
        if (gate(Feature.FERTILIZING_PLAN, settings, "The fertilizing plan")) {
            PreferenceChips(settings, saveSettings)
            val plan = remember(snap, settings.carePreference) { CarePlanner.fertilizingPlan(snap.context, settings.carePreferenceEnum) }
            if (plan.isEmpty()) Text("Nothing planted yet.", fontSize = 12.sp, color = Color.Gray)
            plan.forEach { e ->
                Column {
                    Text("${formatDate(e.dueEpochMillis)} — ${e.species.joinToString(", ")}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(e.action, fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }

    Section("Pest management plan", "FR-018: pests and diseases common to what you grow.") {
        if (gate(Feature.PEST_MANAGEMENT_PLAN, settings, "The pest plan")) {
            PreferenceChips(settings, saveSettings)
            val plan = remember(snap, settings.carePreference) { CarePlanner.pestPlan(snap.context, settings.carePreferenceEnum) }
            if (plan.isEmpty()) Text("No common pests on file for what's planted.", fontSize = 12.sp, color = Color.Gray)
            plan.forEach { p ->
                Column {
                    Text("${p.pest} — ${p.affects.joinToString(", ")}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Prevent: ${p.prevention}", fontSize = 11.sp)
                    Text("Control: ${p.control}", fontSize = 11.sp)
                    Text("Scout: ${p.scouting}", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun PreferenceChips(settings: AppSettings, saveSettings: (AppSettings) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = settings.carePreferenceEnum == CarePreference.ORGANIC, onClick = { saveSettings(settings.copy(carePreference = CarePreference.ORGANIC.name)) }, label = { Text("Organic") })
        FilterChip(selected = settings.carePreferenceEnum == CarePreference.CONVENTIONAL, onClick = { saveSettings(settings.copy(carePreference = CarePreference.CONVENTIONAL.name)) }, label = { Text("Conventional") })
    }
}

// ============================================================== Food

private fun NutritionEntity.toNutrients() = Nutrients(energyKcal, proteinG, carbsG, fiberG, vitaminAUg, vitaminCMg, potassiumMg, ironMg, calciumMg)

@Composable
private fun FoodTab(
    snap: PlotSnapshot,
    settings: AppSettings,
    nutritionRows: Map<String, NutritionEntity>,
    online: OnlineSources,
    reload: (String?) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val ctx = snap.context
    val overrides = nutritionRows.mapValues { it.value.toNutrients() }

    Section("Expected harvest", "FR-022: typical yield per plant for a home garden. Real harvests vary with weather, care and variety.") {
        if (gate(Feature.YIELD_ESTIMATES, settings, "Yield estimates")) {
            val lines = remember(snap) { FoodPlanner.yieldLines(ctx) }
            if (lines.isEmpty()) Text("No food crops planted yet.", fontSize = 12.sp, color = Color.Gray)
            lines.forEach { Text("${it.species}: ${it.plants} × ${"%.2f".format(it.kgPerPlant)} kg = ${"%.1f".format(it.totalKg)} kg", fontSize = 12.sp) }
            if (lines.isNotEmpty()) {
                val total = lines.sumOf { it.totalKg.toDouble() }
                Text("Total ≈ ${"%.1f".format(total)} kg per season (${"%.1f".format(total / settings.householdSize)} kg per person for ${settings.householdSize} people).", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    Section("Nutrition", "FR-020: per 100 g raw, from a bundled USDA snapshot. With online features on, each crop can be refreshed from USDA FoodData Central.") {
        if (gate(Feature.NUTRITION_GUIDE, settings, "The nutrition guide")) {
            val totals = remember(snap, nutritionRows) { FoodPlanner.nutritionTotals(ctx, overrides) }
            if (totals.energyKcal > 0) {
                Text("This plot's season harvest could supply about:", fontSize = 12.sp)
                Text("• ${"%.0f".format(totals.kcalDays)} days of calories, ${"%.0f".format(totals.proteinDays)} days of protein", fontSize = 12.sp)
                Text("• ${"%.0f".format(totals.vitaminCDays)} days of vitamin C, ${"%.0f".format(totals.vitaminADays)} days of vitamin A", fontSize = 12.sp)
                Text("(for one adult: 2000 kcal, 50 g protein, 90 mg vitamin C, 900 µg vitamin A per day)", fontSize = 10.sp, color = Color.Gray)
            }
            val species = ctx.plantedSeeds().distinctBy { CropReference.speciesKey(it) }.filter { CropReference.forSeed(it).isFood }
            if (species.isEmpty()) Text("No food crops planted yet.", fontSize = 12.sp, color = Color.Gray)
            species.forEach { seed ->
                val key = CropReference.speciesKey(seed)
                val row = nutritionRows[key]
                val n = row?.toNutrients() ?: CropReference.forSeed(seed).nutrients ?: return@forEach
                Column {
                    Text(CropReference.speciesName(seed), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("${n.energyKcal.toInt()} kcal • protein ${n.proteinG} g • fibre ${n.fiberG} g • vit C ${n.vitaminCMg} mg • vit A ${n.vitaminAUg} µg • iron ${n.ironMg} mg • calcium ${n.calciumMg} mg • potassium ${n.potassiumMg} mg", fontSize = 11.sp)
                    Text(row?.let { "Source: ${it.source} (updated ${formatDate(it.updatedEpochMillis)})" } ?: "Source: bundled snapshot", fontSize = 10.sp, color = Color.Gray)
                    TextButton(contentPadding = PaddingValues(0.dp), onClick = {
                        scope.launch {
                            when (val r = online.refreshNutrition(key, CropReference.speciesName(seed), settings.usdaApiKey, settings.onlineFeaturesEnabled)) {
                                is OnlineResult.Success -> reload("Updated ${CropReference.speciesName(seed)} from USDA.")
                                OnlineResult.Disabled -> reload("Online features are off (Settings → Online features).")
                                is OnlineResult.Failure -> reload(r.message)
                            }
                        }
                    }) { Text("Refresh from USDA (online)", fontSize = 11.sp) }
                }
            }
        }
    }

    Section("Homestead starter list", "FR-016: a basic balanced set of crops for ${settings.householdSize} people (change in Settings → Household & care), limited to what suits this zone and soil.") {
        if (gate(Feature.HOMESTEAD_STARTER_LIST, settings, "The homestead list")) {
            val items = remember(snap, settings.householdSize) { FoodPlanner.homesteadList(snap.catalog, ctx.zone, ctx.soil, settings.householdSize) }
            if (items.isEmpty()) Text("The current catalog has none of the starter crops. Try a larger catalog tier.", fontSize = 12.sp, color = Color.Gray)
            items.groupBy { it.role }.forEach { (role, list) ->
                Text(role.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                list.forEach { i ->
                    Text("• ${CropReference.speciesName(i.seed)}: ${i.plants} plant${if (i.plants == 1) "" else "s"}" + (if (i.expectedKg > 0f) " (≈ ${"%.0f".format(i.expectedKg)} kg)" else "") + ". ${i.note}", fontSize = 12.sp)
                }
            }
        }
    }

    Section("Recipes from your garden", "FR-021: recipes that use what's planted here.") {
        if (gate(Feature.RECIPE_SUGGESTIONS, settings, "Recipe suggestions")) {
            val keys = ctx.plantedSeeds().map { CropReference.speciesKey(it) }.toSet()
            val matches = remember(keys) { FoodPlanner.recipeMatches(keys) }
            if (matches.isEmpty()) Text("Plant some vegetables or herbs to see recipes.", fontSize = 12.sp, color = Color.Gray)
            matches.take(10).forEach { m ->
                Column {
                    Text(m.recipe.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("From your garden: ${m.grown.joinToString(", ")}" + if (m.missing.isNotEmpty()) " • also uses: ${m.missing.joinToString(", ")}" else "", fontSize = 11.sp, color = Color(0xFF10B981))
                    Text("Pantry: ${m.recipe.pantry}. ${m.recipe.method}", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}
