package com.example.smartgardenplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartgardenplanner.core.SeedEntity
import com.example.smartgardenplanner.core.SgpExecutors
import com.example.smartgardenplanner.data.AppDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [NEW] Screen Node 4 (Encyclopedia), previously documented (IDD/ICD, ConOps) but entirely
 * absent from the code. Implements T2-DAT-020/030/040/050/060 as a browsable botanical-dictionary
 * view over the `seeds` table (see core/SeedEntity.kt, data/SeedDao.kt).
 *
 * [UPDATED] Added a "+" FAB to add custom varieties, and an edit action in the detail dialog to
 * change an existing variety's spacing (and other fields) — previously the Encyclopedia was
 * read-only with no way to add or modify anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncyclopediaScreen(
    database: AppDatabase,
    onNavigateBack: () -> Unit
) {
    var seedList by remember { mutableStateOf<List<SeedEntity>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSeed by remember { mutableStateOf<SeedEntity?>(null) }
    var editingSeed by remember { mutableStateOf<SeedEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // [NEW] Distance-unit-aware display for spacing figures below — the Add/Edit form itself
    // stays in meters (already labeled "(m)" explicitly) to avoid two-way unit parsing in a
    // form; only the read-only list/detail text respects the unit setting.
    var distanceUnit by remember { mutableStateOf(com.example.smartgardenplanner.core.DistanceUnit.METERS) }
    LaunchedEffect(Unit) {
        withContext(SgpExecutors.dbDispatcher) {
            distanceUnit = com.example.smartgardenplanner.data.SettingsRepository(
                com.example.smartgardenplanner.data.SecurityRepositoryImpl(database.configDao())
            ).load().distanceUnit
        }
    }

    suspend fun reload() {
        withContext(SgpExecutors.dbDispatcher) {
            seedList = database.seedDao().getAllSeeds()
        }
    }

    LaunchedEffect(Unit) { reload() }

    val filteredSeeds = if (searchQuery.isBlank()) {
        seedList
    } else {
        seedList.filter {
            it.commonName.contains(searchQuery, ignoreCase = true) ||
                it.botanicalFamily.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Botanical Encyclopedia", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { isCreatingNew = true },
                text = { Text("Add Variety") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by name or family") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredSeeds.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        "No botanical records found. Tap \"Add Variety\" to create one.",
                        color = Color.Gray,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filteredSeeds) { seed ->
                        Card(
                            onClick = { selectedSeed = seed },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                // Color swatch matching the same color this seed will render as on the Canvas.
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(VegetableColorPalette.colorFor(seed))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(seed.commonName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(seed.botanicalFamily, fontSize = 12.sp, color = Color.LightGray)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Spacing: ${com.example.smartgardenplanner.core.DistanceFormatter.format(seed.exclusionRadiusM, distanceUnit)} • Germination: ~${seed.germinationDays}d • Harvest: ~${seed.daysToHarvest}d",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedSeed?.let { seed ->
        SeedDetailDialog(
            seed = seed,
            distanceUnit = distanceUnit,
            dictionary = seedList,
            onDismiss = { selectedSeed = null },
            onEdit = {
                editingSeed = seed
                selectedSeed = null
            }
        )
    }

    if (editingSeed != null || isCreatingNew) {
        SeedEditDialog(
            existing = editingSeed,
            onDismiss = {
                editingSeed = null
                isCreatingNew = false
            },
            onSave = { seed ->
                scope.launch {
                    withContext(SgpExecutors.dbDispatcher) {
                        if (editingSeed != null) {
                            database.seedDao().update(seed)
                        } else {
                            database.seedDao().insert(seed)
                        }
                    }
                    reload()
                    editingSeed = null
                    isCreatingNew = false
                }
            }
        )
    }
}

@Composable
private fun SeedDetailDialog(seed: SeedEntity, distanceUnit: com.example.smartgardenplanner.core.DistanceUnit, dictionary: List<SeedEntity>, onDismiss: () -> Unit, onEdit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(seed.commonName, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Family: ${seed.botanicalFamily}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Spacing (exclusion radius): ${com.example.smartgardenplanner.core.DistanceFormatter.format(seed.exclusionRadiusM, distanceUnit)}")
                Text("Germination window: ~${seed.germinationDays} days")
                Text("Days to harvest: ~${seed.daysToHarvest} days")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Care: ${seed.careNotes.ifBlank { "No notes on file." }}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pests: ${seed.pestNotes.ifBlank { "No notes on file." }}")
                Spacer(modifier = Modifier.height(8.dp))
                // [FIXED — FR-008] Was raw codes ("BAS,MAR,CAR"), meaningless to a human reader.
                // Resolved to real species names via CompanionNameResolver.
                Text("Good companions: ${com.example.smartgardenplanner.core.CompanionNameResolver.resolveToDisplayNames(seed.companionCodes, dictionary)}")
                Text("Avoid nearby: ${com.example.smartgardenplanner.core.CompanionNameResolver.resolveToDisplayNames(seed.antagonistCodes, dictionary)}")
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * [NEW] Add/edit form. When `existing` is null this creates a brand-new SeedEntity; otherwise
 * it edits the given one in place (including the previously-unreachable spacing/exclusion
 * radius field the user specifically asked to be able to change).
 *
 * [UPDATED] Added companion/antagonist/pest fields, which were previously silently carried over
 * from `existing` (or left blank on creation) with no way to actually view or set them from this
 * form — a real gap: creating a new variety had no way to enter its companions at all. Also
 * added a color swatch picker, since automatic color assignment has no collision protection
 * (two unrelated varieties can land on the same or a visually indistinguishable color).
 */
@Composable
private fun SeedEditDialog(
    existing: SeedEntity?,
    onDismiss: () -> Unit,
    onSave: (SeedEntity) -> Unit
) {
    var botanicalCode by remember { mutableStateOf(existing?.botanicalCode ?: "") }
    var commonName by remember { mutableStateOf(existing?.commonName ?: "") }
    var botanicalFamily by remember { mutableStateOf(existing?.botanicalFamily ?: "") }
    var exclusionRadiusText by remember { mutableStateOf((existing?.exclusionRadiusM ?: 0.5f).toString()) }
    var germinationDaysText by remember { mutableStateOf((existing?.germinationDays ?: 10).toString()) }
    var daysToHarvestText by remember { mutableStateOf((existing?.daysToHarvest ?: 60).toString()) }
    var careNotes by remember { mutableStateOf(existing?.careNotes ?: "") }
    var companionCodes by remember { mutableStateOf(existing?.companionCodes ?: "") } // [NEW]
    var antagonistCodes by remember { mutableStateOf(existing?.antagonistCodes ?: "") } // [NEW]
    var pestNotes by remember { mutableStateOf(existing?.pestNotes ?: "") } // [NEW]
    var selectedColor by remember {
        mutableStateOf(existing?.colorHex?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() })
    }

    val radiusValid = exclusionRadiusText.toFloatOrNull()?.let { it > 0f } == true
    val codeValid = botanicalCode.isNotBlank()
    val nameValid = commonName.isNotBlank()
    val isValid = radiusValid && codeValid && nameValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Variety" else "Edit ${existing.commonName}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = botanicalCode,
                    onValueChange = { if (existing == null) botanicalCode = it.uppercase().replace(" ", "-") },
                    label = { Text("Code (unique, e.g. VEG-001)") },
                    enabled = existing == null, // the code is the primary key; don't allow changing it on edit
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = commonName,
                    onValueChange = { commonName = it },
                    label = { Text("Common name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = botanicalFamily,
                    onValueChange = { botanicalFamily = it },
                    label = { Text("Botanical family") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = exclusionRadiusText,
                    onValueChange = { exclusionRadiusText = it },
                    label = { Text("Spacing / exclusion radius (m)") },
                    isError = exclusionRadiusText.isNotEmpty() && !radiusValid,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = germinationDaysText,
                        onValueChange = { germinationDaysText = it },
                        label = { Text("Germination (days)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = daysToHarvestText,
                        onValueChange = { daysToHarvestText = it },
                        label = { Text("Harvest (days)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = companionCodes,
                    onValueChange = { companionCodes = it },
                    label = { Text("Good companions (comma-separated codes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = antagonistCodes,
                    onValueChange = { antagonistCodes = it },
                    label = { Text("Avoid nearby (comma-separated codes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pestNotes,
                    onValueChange = { pestNotes = it },
                    label = { Text("Pest notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = careNotes,
                    onValueChange = { careNotes = it },
                    label = { Text("Care notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                // [NEW] Manual color override — resolves the "two different vegetables ended up
                // the same color" report, since automatic hash-based assignment has no collision
                // protection.
                Text("Canvas color", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Gray)
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(8),
                    modifier = Modifier.heightIn(max = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(VegetableColorPalette.PRESET_SWATCHES) { swatch ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .then(
                                    if (selectedColor == swatch) Modifier.border(2.dp, androidx.compose.ui.graphics.Color.White, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = swatch }
                        )
                    }
                    item {
                        // "Auto" option: clear the override and fall back to the deterministic hash color.
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Color.DarkGray)
                                .then(
                                    if (selectedColor == null) Modifier.border(2.dp, androidx.compose.ui.graphics.Color.White, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = null },
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text("A", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val result = SeedEntity(
                        botanicalCode = botanicalCode,
                        commonName = commonName,
                        botanicalFamily = botanicalFamily.ifBlank { "Unclassified" },
                        exclusionRadiusM = exclusionRadiusText.toFloatOrNull() ?: 0.5f,
                        germinationDays = germinationDaysText.toIntOrNull() ?: 10,
                        daysToHarvest = daysToHarvestText.toIntOrNull() ?: 60,
                        companionCodes = companionCodes,
                        antagonistCodes = antagonistCodes,
                        pestNotes = pestNotes,
                        careNotes = careNotes,
                        fastTrackAlternateCode = existing?.fastTrackAlternateCode,
                        nurseryTransplantSuitable = existing?.nurseryTransplantSuitable ?: true,
                        catchCropAlternateCode = existing?.catchCropAlternateCode,
                        colorHex = selectedColor?.let { VegetableColorPalette.toHex(it) },
                        // [NEW] Anything created or edited through this form is marked custom —
                        // protects it from being wiped if the user later switches catalog tiers
                        // (Settings → Catalog), which only reloads bundled (non-custom) entries.
                        isCustom = true
                    )
                    onSave(result)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}
