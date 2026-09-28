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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.smartgardenplanner.core.AppSettings
import com.example.smartgardenplanner.core.CarePreference
import com.example.smartgardenplanner.core.GuildCatalog
import com.example.smartgardenplanner.core.VendorRegistry
import com.example.smartgardenplanner.data.CareReminderWorker
import com.example.smartgardenplanner.core.AppTier
import com.example.smartgardenplanner.core.Feature
import com.example.smartgardenplanner.core.currentAppTier
import com.example.smartgardenplanner.core.SgpExecutors
import com.example.smartgardenplanner.data.AppDatabase
import com.example.smartgardenplanner.data.CatalogTier
import com.example.smartgardenplanner.data.SeedCatalogLoader
import com.example.smartgardenplanner.data.SettingsRepository
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
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
    var tierMessage by remember { mutableStateOf<String?>(null) }
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
    // Tier switch (DW-0801, T2-DAT-200), in one transaction:
    //  - bundled entries of the new tier are inserted or updated in place (never delete-then-insert,
    //    which violated the planted_nodes foreign key and crashed the app once anything was planted);
    //  - entries the user created or edited (isCustom = 1) are never touched;
    //  - bundled entries not in the new tier are deleted only if nothing on any plot uses them.
    fun switchTier(tier: CatalogTier) {
        isSwitchingTier = true
        scope.launch {
            try {
                val kept = withContext(SgpExecutors.dbDispatcher) {
                    val newSeeds = SeedCatalogLoader(context).loadTier(tier)
                    val seedDao = database.seedDao()
                    database.withTransaction {
                        val customCodes = seedDao.customCodes().toSet()
                        val planted = seedDao.plantedCodes().toSet()
                        val newCodes = newSeeds.map { it.botanicalCode }.toSet()
                        seedDao.upsertAll(newSeeds.filter { it.botanicalCode !in customCodes })
                        val notInTier = seedDao.catalogCodes().filter { it !in newCodes }
                        notInTier.filter { it !in planted }.chunked(500).forEach { seedDao.deleteCatalogSeedsByCode(it) }
                        catalogSeedCount = seedDao.catalogSeedCount()
                        notInTier.count { it in planted }
                    }
                }
                update(settings.copy(catalogTier = tier.name))
                tierMessage = if (kept > 0) {
                    "Switched to ${tier.name}. $kept varieties from the previous tier were kept because they are planted."
                } else {
                    "Switched to ${tier.name}."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tierMessage = "Couldn't switch the catalog (${e.javaClass.simpleName}). Nothing was changed."
            } finally {
                isSwitchingTier = false
            }
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
                            CareReminderWorker.schedule(context, false)
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
            SettingsSection(title = "About this planner") {
                Text(com.example.smartgardenplanner.core.Disclaimer.TEXT, fontSize = 12.sp)
            }
            SettingsSection(title = "Language", note = "Texts without a translation stay in English.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.example.smartgardenplanner.core.I18n.LANGUAGES.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang.code,
                            onClick = {
                                if (settings.language != lang.code) {
                                    val chosen = settings.copy(language = lang.code)
                                    settings = chosen
                                    // Saved before the screen is rebuilt in the new language.
                                    scope.launch {
                                        withContext(SgpExecutors.dbDispatcher) { settingsRepository.save(chosen) }
                                        com.example.smartgardenplanner.applyLanguage(context, lang.code)
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                }
                            },
                            label = { Text(lang.name) }
                        )
                    }
                }
            }
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
                tierMessage?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
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

            // FR-026: master switch for network access, off by default.
            SettingsSection(
                title = "Online features",
                note = "Off by default: the app then never connects to the internet. When on, only these services are used, and only when you ask: Open-Meteo (rain for reminders, last year's sunshine) and USDA FoodData Central (nutrition refresh). Only the plot's approximate location or a food name is sent. Everything else keeps working offline."
            ) {
                SwitchSetting(
                    label = "Allow online features",
                    description = if (settings.onlineFeaturesEnabled) "ON — the app may contact the services above when a feature needs them. A 'Connecting…' bar shows whenever it does." else "OFF — no network connections are made.",
                    checked = settings.onlineFeaturesEnabled,
                    onChange = { update(settings.copy(onlineFeaturesEnabled = it)) }
                )
                var keyText by remember(settings.usdaApiKey) { mutableStateOf(settings.usdaApiKey) }
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it.trim() },
                    label = { Text("USDA FoodData Central API key") },
                    supportingText = { Text("DEMO_KEY works but is rate-limited. A free personal key is available from api.data.gov.", fontSize = 10.sp) },
                    singleLine = true,
                    enabled = settings.onlineFeaturesEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
                if (keyText != settings.usdaApiKey) {
                    TextButton(onClick = { update(settings.copy(usdaApiKey = keyText.ifBlank { "DEMO_KEY" })) }) { Text("Save key") }
                }
            }

            // FR-009: interplanting guilds, Pro, with a clear on/off state.
            val guildsAllowed = Feature.isEnabled(Feature.INTERPLANTING_GUILDS, settings.currentAppTier())
            SettingsSection(
                title = "Interplanting guilds",
                note = "Guild members may be planted closer together than their normal spacing, and don't trigger antagonist warnings with each other. The canvas shows a GUILDS ON / GUILDS OFF badge so you always know which rules apply."
            ) {
                SwitchSetting(
                    label = "Use guilds" + if (!guildsAllowed) " (Pro only)" else "",
                    description = if (guildsAllowed) (if (settings.guildsEnabled) "ON — guild spacing applies." else "OFF — normal spacing for every plant.") else "Switch to the Pro catalog tier (Settings → Catalog) to use guilds.",
                    checked = guildsAllowed && settings.guildsEnabled,
                    enabled = guildsAllowed,
                    onChange = { update(settings.copy(guildsEnabled = it)) }
                )
                GuildCatalog.ALL.forEach { g ->
                    Column {
                        Text(g.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(g.members.joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        Text(g.description, fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            // FR-016/017/018/019: household, care style and reminders.
            val remindersAllowed = Feature.isEnabled(Feature.CARE_REMINDERS, settings.currentAppTier())
            val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) tierMessage = "Reminders are on, but notifications are blocked. Allow them in Android settings to see reminders."
            }
            SettingsSection(title = "Household & care") {
                SliderSetting(
                    label = "Household size",
                    description = "People the garden should help feed. Used by the homestead list and yield estimates.",
                    value = settings.householdSize.toFloat(),
                    range = 1f..12f,
                    steps = 10,
                    valueLabel = { "${it.toInt()} people" },
                    onChange = { update(settings.copy(householdSize = it.toInt())) }
                )
                Text("Fertilizer and pest control style", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.carePreferenceEnum == CarePreference.ORGANIC,
                        onClick = { update(settings.copy(carePreference = CarePreference.ORGANIC.name)) },
                        label = { Text("Organic") }
                    )
                    FilterChip(
                        selected = settings.carePreferenceEnum == CarePreference.CONVENTIONAL,
                        onClick = { update(settings.copy(carePreference = CarePreference.CONVENTIONAL.name)) },
                        label = { Text("Conventional") }
                    )
                }
                SwitchSetting(
                    label = "Daily care reminders" + if (!remindersAllowed) " (Pro only)" else "",
                    description = if (remindersAllowed) "A daily notification when watering or fertilizing is due. With online features on and a plot location set, watering is skipped after rain." else "Switch to the Pro catalog tier to get reminders.",
                    checked = remindersAllowed && settings.careRemindersEnabled,
                    enabled = remindersAllowed,
                    onChange = { on ->
                        update(settings.copy(careRemindersEnabled = on))
                        CareReminderWorker.schedule(context, on)
                        if (on && android.os.Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
                SliderSetting(
                    label = "Rain that counts as watering",
                    description = "If at least this much rain fell yesterday and today (online features on), the watering reminder is skipped.",
                    value = settings.rainSkipThresholdMm,
                    range = 1f..25f,
                    steps = 23,
                    valueLabel = { "${it.toInt()} mm" },
                    onChange = { update(settings.copy(rainSkipThresholdMm = it)) }
                )
            }

            // FR-023/024: vendor slots (placeholders) and preferred vendor (Pro).
            val vendorChoice = Feature.isEnabled(Feature.VENDOR_TARGETING, settings.currentAppTier())
            SettingsSection(
                title = "Seed vendor",
                note = "Purchase links are placeholders for now: no vendor website is linked yet. Your choice is kept for when real links are added." + if (!vendorChoice) " Choosing a vendor needs the Pro tier." else ""
            ) {
                VendorRegistry.VENDORS.forEach { v ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = VendorRegistry.effectiveVendor(settings.preferredVendorId, vendorChoice).id == v.id,
                            enabled = vendorChoice,
                            onClick = { update(settings.copy(preferredVendorId = v.id)) }
                        )
                        Text(v.displayName, fontSize = 13.sp, color = if (vendorChoice) Color.Unspecified else Color.Gray)
                    }
                }
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
        Text(com.example.smartgardenplanner.tr(title), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
