package com.example.smartgardenplanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartgardenplanner.core.AppSettings
import com.example.smartgardenplanner.core.AppTier
import com.example.smartgardenplanner.core.Feature
import com.example.smartgardenplanner.core.currentAppTier
import com.example.smartgardenplanner.core.SgpExecutors
import com.example.smartgardenplanner.data.AppDatabase
import com.example.smartgardenplanner.data.CatalogTier
import com.example.smartgardenplanner.data.SeedCatalogLoader
import com.example.smartgardenplanner.data.SettingsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [NEW] Screen Node 5 — Settings. Every value here was previously a hardcoded constant somewhere
 * in the app; this is the single place to change any of them, organized into sections so nothing
 * requires hunting through the whole list. Changes save immediately per-field (no separate
 * "Save" step) so the Canvas screen picks them up the next time it loads.
 *
 * [UPDATED] Added a Catalog section for the tiered Basic/Standard/Pro seed catalog — needs
 * database access (unlike every other setting, which is a pure value) since switching tiers
 * means actually replacing the bundled seed rows, not just saving a preference.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    database: AppDatabase,
    onNavigateBack: () -> Unit
) {
    var settings by remember { mutableStateOf(AppSettings.DEFAULT) }
    var loaded by remember { mutableStateOf(false) }
    var catalogSeedCount by remember { mutableStateOf(0) }
    var isSwitchingTier by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        withContext(SgpExecutors.dbDispatcher) {
            settings = settingsRepository.load()
            catalogSeedCount = database.seedDao().catalogSeedCount()
        }
        loaded = true
    }

    fun update(newSettings: AppSettings) {
        settings = newSettings
        scope.launch {
            withContext(SgpExecutors.dbDispatcher) { settingsRepository.save(newSettings) }
        }
    }

    // [NEW] Switches the active catalog tier: deletes all non-custom (bundled) seed rows and
    // reloads the new tier's set from its bundled asset file. Anything the user personally added
    // or edited (isCustom = true) is untouched, since deleteCatalogSeeds() only targets isCustom = 0.
    fun switchTier(tier: CatalogTier) {
        isSwitchingTier = true
        scope.launch {
            withContext(SgpExecutors.dbDispatcher) {
                val loader = SeedCatalogLoader(context)
                val newSeeds = loader.loadTier(tier)
                database.seedDao().deleteCatalogSeeds()
                database.seedDao().insertAll(newSeeds)
                catalogSeedCount = database.seedDao().catalogSeedCount()
            }
            update(settings.copy(catalogTier = tier.name))
            isSwitchingTier = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            withContext(SgpExecutors.dbDispatcher) { settingsRepository.resetToDefaults() }
                            settings = AppSettings.DEFAULT
                        }
                    }) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset all to defaults")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection(title = "Units") {
                Text("Distance unit", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Everything is still stored in meters internally — this only changes how numbers are shown and entered.", fontSize = 11.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    FilterChip(
                        selected = settings.distanceUnit == com.example.smartgardenplanner.core.DistanceUnit.METERS,
                        onClick = { update(settings.copy(distanceUnit = com.example.smartgardenplanner.core.DistanceUnit.METERS)) },
                        label = { Text("Meters") }
                    )
                    FilterChip(
                        selected = settings.distanceUnit == com.example.smartgardenplanner.core.DistanceUnit.INCHES,
                        onClick = { update(settings.copy(distanceUnit = com.example.smartgardenplanner.core.DistanceUnit.INCHES)) },
                        label = { Text("Inches") }
                    )
                }
            }

            SettingsSection(title = "Catalog", note = "Basic/Standard/Pro control how many bundled seed varieties are loaded. Anything you've personally added or edited in the Encyclopedia is never affected by this.") {
                Text("Current: ${catalogSeedCount} bundled varieties (${settings.catalogTier})", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                CatalogTier.values().forEach { tier ->
                    val isActive = settings.catalogTier == tier.name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tier.displayName, fontSize = 13.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                        }
                        if (isActive) {
                            Text("Active", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            TextButton(enabled = !isSwitchingTier, onClick = { switchTier(tier) }) {
                                Text(if (isSwitchingTier) "Switching..." else "Switch")
                            }
                        }
                    }
                }
            }

            SettingsSection(title = "Spacing & Placement") {
                SliderSetting(
                    label = "Spacing margin",
                    description = "How strict plant-to-plant spacing enforcement is. 1.0x is the standard non-overlap rule (two plants' spacing circles just touch). Lower it to allow tighter placement than that; raise it to be more conservative.",
                    value = settings.spacingMarginMultiplier,
                    range = 0.3f..2.0f,
                    steps = 16,
                    valueLabel = { "${"%.2f".format(it)}x" },
                    onChange = { update(settings.copy(spacingMarginMultiplier = it)) }
                )
                // [UPDATED — FR-012] Restricted to Pro tier, per explicit instruction. Below Pro,
                // this always shows as locked-on (companion rules stay enforced) with a clear
                // explanation rather than silently hiding the control.
                val companionToggleEnabled = Feature.isEnabled(Feature.COMPANION_RULE_TOGGLE, settings.currentAppTier())
                SwitchSetting(
                    label = "Enforce companion/antagonist rules" + if (!companionToggleEnabled) " (Pro only)" else "",
                    description = if (companionToggleEnabled) {
                        "When off, plants that dislike each other's company are still allowed nearby (spacing radius is still enforced)."
                    } else {
                        "Always enforced on Basic/Standard. Switch to the Pro catalog tier (Settings → Catalog) to make this configurable."
                    },
                    checked = if (companionToggleEnabled) settings.enforceCompanionAntagonistRules else true,
                    enabled = companionToggleEnabled,
                    onChange = { update(settings.copy(enforceCompanionAntagonistRules = it)) }
                )
            }

            SettingsSection(title = "Canvas Display") {
                SliderSetting(
                    label = "Ruler text size",
                    description = "Font size of the meter labels along the ruler edges.",
                    value = settings.rulerFontSizeSp,
                    range = 8f..28f,
                    steps = 19,
                    valueLabel = { "${it.toInt()}sp" },
                    onChange = { update(settings.copy(rulerFontSizeSp = it)) }
                )
                SliderSetting(
                    label = "Ruler tick spacing",
                    description = "Distance in meters between ruler tick marks.",
                    value = settings.rulerTickIntervalM,
                    range = 0.25f..5f,
                    steps = 18,
                    valueLabel = { "${"%.2f".format(it)}m" },
                    onChange = { update(settings.copy(rulerTickIntervalM = it)) }
                )
                SliderSetting(
                    label = "Minimum zoom",
                    description = "How far out you can zoom on the canvas.",
                    value = settings.zoomMin,
                    range = 0.1f..1f,
                    steps = 8,
                    valueLabel = { "${"%.2f".format(it)}x" },
                    onChange = { if (it < settings.zoomMax) update(settings.copy(zoomMin = it)) }
                )
                SliderSetting(
                    label = "Maximum zoom",
                    description = "How far in you can zoom on the canvas.",
                    value = settings.zoomMax,
                    range = 1f..8f,
                    steps = 13,
                    valueLabel = { "${"%.2f".format(it)}x" },
                    onChange = { if (it > settings.zoomMin) update(settings.copy(zoomMax = it)) }
                )
                SliderSetting(
                    label = "Zoom step",
                    description = "How much each tap of the +/- zoom buttons changes the zoom level.",
                    value = settings.zoomStep,
                    range = 0.05f..1f,
                    steps = 18,
                    valueLabel = { "${"%.2f".format(it)}x" },
                    onChange = { update(settings.copy(zoomStep = it)) }
                )
                SliderSetting(
                    label = "Undo history depth",
                    description = "How many steps back Undo/Redo can go on the canvas.",
                    value = settings.undoHistoryDepth.toFloat(),
                    range = 5f..100f,
                    steps = 18,
                    valueLabel = { "${it.toInt()} steps" },
                    onChange = { update(settings.copy(undoHistoryDepth = it.toInt())) }
                )
            }

            SettingsSection(title = "Plot Validation") {
                SliderSetting(
                    label = "Minimum plot dimension",
                    description = "Smallest length/width allowed when creating a plot.",
                    value = settings.minPlotDimensionM,
                    range = 0.01f..5f,
                    steps = 24,
                    valueLabel = { "${"%.2f".format(it)}m" },
                    onChange = { if (it < settings.maxPlotDimensionM) update(settings.copy(minPlotDimensionM = it)) }
                )
                SliderSetting(
                    label = "Maximum plot dimension",
                    description = "Largest length/width allowed when creating a plot.",
                    value = settings.maxPlotDimensionM,
                    range = 10f..2000f,
                    steps = 19,
                    valueLabel = { "${it.toInt()}m" },
                    onChange = { if (it > settings.minPlotDimensionM) update(settings.copy(maxPlotDimensionM = it)) }
                )
            }

            SettingsSection(title = "Sensors & Hardware", note = "These aren't connected to any active screen yet (camera/sensor UI is still being built), but are ready for when they are.") {
                SliderSetting(
                    label = "Tilt abort threshold",
                    description = "Maximum device tilt during IMU distance measurement before it aborts and asks you to recalibrate.",
                    value = settings.tiltAbortDegrees,
                    range = 1f..20f,
                    steps = 18,
                    valueLabel = { "${it.toInt()}°" },
                    onChange = { update(settings.copy(tiltAbortDegrees = it)) }
                )
                SliderSetting(
                    label = "Low-light threshold",
                    description = "Ambient light level (lux) below which a low-light warning is shown during camera capture.",
                    value = settings.lowLightLuxThreshold,
                    range = 1f..50f,
                    steps = 48,
                    valueLabel = { "${it.toInt()} lux" },
                    onChange = { update(settings.copy(lowLightLuxThreshold = it)) }
                )
                SliderSetting(
                    label = "GPS accuracy gate",
                    description = "GPS readings less accurate than this (in meters) are discarded.",
                    value = settings.gpsAccuracyGateMeters,
                    range = 3f..100f,
                    steps = 96,
                    valueLabel = { "${it.toInt()}m" },
                    onChange = { update(settings.copy(gpsAccuracyGateMeters = it)) }
                )
                SliderSetting(
                    label = "Storage floor",
                    description = "New photo captures are blocked once free device storage drops below this percentage.",
                    value = settings.storageFloorPercent,
                    range = 1f..25f,
                    steps = 23,
                    valueLabel = { "${it.toInt()}%" },
                    onChange = { update(settings.copy(storageFloorPercent = it)) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        note?.let {
            Text(it, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        }
        if (note == null) Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        Divider(modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun SliderSetting(
    label: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Float) -> String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(valueLabel(value), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Text(description, fontSize = 11.sp, color = Color.Gray)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true // [NEW — supports tier-locked settings like FR-012]
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (enabled) Color.Unspecified else Color.Gray)
            Text(description, fontSize = 11.sp, color = Color.Gray)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
