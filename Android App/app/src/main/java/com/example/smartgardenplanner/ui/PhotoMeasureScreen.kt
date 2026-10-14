package com.example.smartgardenplanner.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.smartgardenplanner.core.DistanceFormatter
import com.example.smartgardenplanner.core.DistanceUnit
import com.example.smartgardenplanner.core.PhotoMeasure
import com.example.smartgardenplanner.core.PhotoMeasureResult
import com.example.smartgardenplanner.core.PhotoMeasurement
import com.example.smartgardenplanner.core.PixelPoint
import com.example.smartgardenplanner.core.ReferenceObject
import com.example.smartgardenplanner.tr
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Reads a photo for measuring (LLR-PMEAS-050): at most [maxSide] px on its longest side, turned upright from its EXIF
 * orientation, in memory only. Null when it can't be decoded.
 */
fun decodeMeasurePhoto(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap? = try {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val w = info.size.width; val h = info.size.height
        val scale = min(1.0, maxSide.toDouble() / max(w, h))
        decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
} catch (e: Exception) {
    null
} catch (e: OutOfMemoryError) {
    null
}

/**
 * Measure a plot from a photo (T2-FUN-025, HLR-MEAS-020, LLR-PMEAS-050): tap the four corners of a reference of known
 * size lying on the ground, then the plot's corners; pinch to zoom for precise taps. Shows the size with its ± range and
 * warnings, and hands the measurement back with "Use these sizes".
 */
@Composable
fun PhotoMeasureDialog(photo: ImageBitmap, unit: DistanceUnit, onUse: (PhotoMeasurement) -> Unit, onClose: () -> Unit) {
    var reference by remember { mutableStateOf(ReferenceObject.RAISED_BED_4X8) }
    var refMenu by remember { mutableStateOf(false) }
    var customLong by remember { mutableStateOf("") }
    var customShort by remember { mutableStateOf("") }
    val refTaps = remember { mutableStateListOf<PixelPoint>() }
    val plotTaps = remember { mutableStateListOf<PixelPoint>() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current.density

    fun fitScale(): Float = if (box.width == 0) 1f else min(box.width / photo.width.toFloat(), box.height / photo.height.toFloat()) * zoom
    fun origin(s: Float) = Offset((box.width - photo.width * s) / 2f + pan.x, (box.height - photo.height * s) / 2f + pan.y)

    val sizeM: Pair<Double, Double>? = if (reference == ReferenceObject.CUSTOM) {
        val l = DistanceFormatter.parseToMeters(customLong, unit)?.toDouble(); val s = DistanceFormatter.parseToMeters(customShort, unit)?.toDouble()
        if (l != null && s != null && l > 0 && s > 0) max(l, s) to min(l, s) else null
    } else reference.longM to reference.shortM

    val result: PhotoMeasureResult? = if (refTaps.size == 4 && plotTaps.size >= 2 && sizeM != null)
        PhotoMeasure.measure(refTaps.toList(), sizeM.first, sizeM.second, plotTaps.toList(), photo.width, photo.height,
            tapErrorPx = (8f * density / fitScale()).toDouble())
    else null

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Measure from a photo"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(tr(when {
                    refTaps.size < 4 -> "Step 1: tap the 4 corners of the reference, in order around it, starting along a long side (${refTaps.size} of 4)."
                    plotTaps.isEmpty() -> "Step 2: tap the plot's corners in order around it. Two points measure one distance."
                    else -> "Keep tapping the plot's corners, or read the result below (${plotTaps.size} points)."
                }), fontSize = 13.sp)
                Box {
                    OutlinedButton(onClick = { refMenu = true }) { Text(tr("Reference: ") + tr(reference.label)) }
                    DropdownMenu(expanded = refMenu, onDismissRequest = { refMenu = false }) {
                        ReferenceObject.entries.forEach { r ->
                            DropdownMenuItem(text = { Text(tr(r.label)) }, onClick = { reference = r; refMenu = false })
                        }
                    }
                }
                if (reference == ReferenceObject.CUSTOM) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(customLong, { customLong = it.take(12) }, label = { Text(tr("Long side (${unit.suffix})")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(customShort, { customShort = it.take(12) }, label = { Text(tr("Short side (${unit.suffix})")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()
                        .onSizeChanged { box = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                zoom = (zoom * zoomChange).coerceIn(1f, 8f)
                                pan += panChange
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { tap ->
                                val s = fitScale(); val o = origin(s)
                                val x = ((tap.x - o.x) / s).toDouble(); val y = ((tap.y - o.y) / s).toDouble()
                                if (x in 0.0..photo.width.toDouble() && y in 0.0..photo.height.toDouble()) {
                                    if (refTaps.size < 4) refTaps.add(PixelPoint(x, y)) else if (plotTaps.size < PhotoMeasure.MAX_CORNERS) plotTaps.add(PixelPoint(x, y))
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val s = fitScale(); val o = origin(s)
                        drawImage(photo, dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
                            dstSize = IntSize((photo.width * s).roundToInt(), (photo.height * s).roundToInt()))
                        fun screen(p: PixelPoint) = Offset(o.x + p.x.toFloat() * s, o.y + p.y.toFloat() * s)
                        fun shape(points: List<PixelPoint>, color: Color, closed: Boolean) {
                            for (i in points.indices) {
                                if (i > 0) drawLine(color, screen(points[i - 1]), screen(points[i]), strokeWidth = 3f * density)
                                drawCircle(Color.White, radius = 7f * density, center = screen(points[i]))
                                drawCircle(color, radius = 7f * density, center = screen(points[i]), style = Stroke(width = 3f * density))
                            }
                            if (closed && points.size > 2) drawLine(color, screen(points.last()), screen(points.first()), strokeWidth = 3f * density)
                        }
                        shape(refTaps, Color(0xFF2563EB), refTaps.size == 4)
                        shape(plotTaps, Color(0xFFEA580C), plotTaps.size >= 3)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = refTaps.isNotEmpty(), onClick = { if (plotTaps.isNotEmpty()) plotTaps.removeAt(plotTaps.lastIndex) else refTaps.removeAt(refTaps.lastIndex) }) { Text(tr("Undo last tap")) }
                    TextButton(enabled = refTaps.isNotEmpty(), onClick = { refTaps.clear(); plotTaps.clear() }) { Text(tr("Start over")) }
                    TextButton(enabled = zoom != 1f || pan != Offset.Zero, onClick = { zoom = 1f; pan = Offset.Zero }) { Text(tr("Fit photo")) }
                }
                Column(modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
                    when (result) {
                        null -> if (reference == ReferenceObject.CUSTOM && sizeM == null) Text(tr("Type the reference's long and short sides."), fontSize = 13.sp)
                        is PhotoMeasureResult.Failed -> Text(tr(result.reason), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        is PhotoMeasureResult.Ok -> {
                            Text(tr(PhotoMeasure.describe(result.measurement, unit)), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            result.measurement.warnings.forEach { Text(tr("⚠ $it"), fontSize = 12.sp, color = Color(0xFFB45309)) }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text(tr("Close")) }
                    val ok = (result as? PhotoMeasureResult.Ok)?.measurement
                    Button(enabled = ok != null, onClick = { ok?.let(onUse) }) {
                        Text(tr(if (ok != null && ok.widthM == 0.0) "Use as the length" else "Use these sizes"))
                    }
                }
            }
        }
    }
}
