package com.example.smartgardenplanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartgardenplanner.core.PlotEntity
import com.example.smartgardenplanner.core.ZipTable
import com.example.smartgardenplanner.data.AppDatabase
import com.example.smartgardenplanner.data.OnlineResult
import com.example.smartgardenplanner.data.ZipLookup
import kotlinx.coroutines.launch
import java.util.Locale

private val DIRECTIONS = listOf("N" to 0f, "NE" to 45f, "E" to 90f, "SE" to 135f, "S" to 180f, "SW" to 225f, "W" to 270f, "NW" to 315f)

fun compassName(deg: Float): String = DIRECTIONS[(((deg % 360f + 360f) % 360f + 22.5f) / 45f).toInt() % 8].first

/** Eight compass buttons for "which way does the top edge of the plot face?" (FR-028). */
@Composable
fun CompassChips(bearing: Float?, onSelect: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(DIRECTIONS.take(4), DIRECTIONS.drop(4)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (name, deg) ->
                    FilterChip(selected = bearing == deg, onClick = { onSelect(deg) }, label = { Text(name, fontSize = 12.sp) })
                }
            }
        }
    }
}

/**
 * Plot direction and location (FR-028). Asks which compass direction the plot's top edge (as drawn on the
 * screen) faces, and the ZIP code. The ZIP gives the latitude/longitude offline (bundled table) and the
 * hardiness zone from the offline starter table or, with Online features on, from phzmapi.org.
 */
@Composable
fun PlotDirectionDialog(
    plot: PlotEntity,
    database: AppDatabase,
    onlineEnabled: Boolean,
    onSave: (PlotEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bearing by remember(plot.id) { mutableStateOf<Float?>(if (plot.orientationSet) plot.northBearingDeg else null) }
    var fine by remember(plot.id) { mutableStateOf(plot.northBearingDeg) }
    var zip by remember(plot.id) { mutableStateOf(plot.locationZip ?: "") }
    var lat by remember(plot.id) { mutableStateOf(plot.latitude) }
    var lon by remember(plot.id) { mutableStateOf(plot.longitude) }
    var zone by remember(plot.id) { mutableStateOf(plot.hardinessZone) }
    var note by remember(plot.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plot direction and location") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Stand at the bottom edge of the plot (as drawn on screen) and look across to the top edge. Which way are you facing? Use a phone compass if unsure.", fontSize = 12.sp)
                CompassChips(bearing) { bearing = it; fine = it }
                if (bearing != null) {
                    Text("Top edge faces ${compassName(fine)} (${fine.toInt()}°)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Slider(value = fine, onValueChange = { fine = it; bearing = it }, valueRange = 0f..355f, steps = 70)
                }
                OutlinedTextField(
                    value = zip,
                    onValueChange = { zip = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("ZIP code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(enabled = ZipTable.isValidZip(zip), onClick = {
                    scope.launch {
                        val loc = ZipLookup.location(context, zip)
                        if (loc != null) { lat = loc.latitude; lon = loc.longitude }
                        val zoneResult = ZipLookup.zone(database, zip, onlineEnabled)
                        if (zoneResult is OnlineResult.Success) zone = zoneResult.value
                        note = buildString {
                            append(if (loc != null) "Location: ${"%.3f".format(Locale.US, loc.latitude)}, ${"%.3f".format(Locale.US, loc.longitude)} (${loc.state}). " else "ZIP not in the offline table. ")
                            append(
                                when (zoneResult) {
                                    is OnlineResult.Success -> "Zone ${zoneResult.value}."
                                    OnlineResult.Disabled -> "Zone: pick it on Plot insights → Site, or turn on Online features to look it up."
                                    is OnlineResult.Failure -> zoneResult.message
                                }
                            )
                        }
                    }
                }) { Text("Look up ZIP") }
                note?.let { Text(it, fontSize = 11.sp, color = Color(0xFF0EA5E9)) }
                if (bearing == null) Text("Choose a direction to continue. Without it, sun and planting calculations would assume the top edge faces north.", fontSize = 11.sp, color = Color(0xFFEAB308))
            }
        },
        confirmButton = {
            TextButton(enabled = bearing != null, onClick = {
                onSave(
                    plot.copy(
                        northBearingDeg = fine,
                        orientationSet = true,
                        locationZip = zip.takeIf { ZipTable.isValidZip(it) } ?: plot.locationZip,
                        latitude = lat,
                        longitude = lon,
                        hardinessZone = zone,
                        lastModifiedTimestamp = System.currentTimeMillis()
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
