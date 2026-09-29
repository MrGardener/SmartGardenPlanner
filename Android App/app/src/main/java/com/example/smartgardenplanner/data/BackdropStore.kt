package com.example.smartgardenplanner.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.smartgardenplanner.core.Backdrop
import java.io.File

/**
 * Satellite photos under plots (FR-046), one JPEG per plot in the app's private files folder. The placement is on
 * the plot row (PlotEntity.backdropJson); plan files carry the picture as a data URL.
 */
object BackdropStore {

    fun file(filesDir: File, plotId: Long) = File(filesDir, "backdrop_$plotId.jpg")

    /** Reads a picked image, scales it down to [Backdrop.MAX_IMAGE_PX] and stores it. Returns its size, or null. */
    fun importFromUri(context: Context, uri: Uri, plotId: Long): Pair<Int, Int>? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= Backdrop.MAX_IMAGE_PX) sample *= 2
            val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            raw?.let { save(context.filesDir, plotId, it) }
        }
    } catch (e: Exception) { null }

    private fun save(filesDir: File, plotId: Long, bitmap: Bitmap): Pair<Int, Int> {
        val k = minOf(1f, Backdrop.MAX_IMAGE_PX.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (k < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).toInt().coerceAtLeast(1), (bitmap.height * k).toInt().coerceAtLeast(1), true) else bitmap
        file(filesDir, plotId).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return scaled.width to scaled.height
    }

    fun load(filesDir: File, plotId: Long): Bitmap? = file(filesDir, plotId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun delete(filesDir: File, plotId: Long) { file(filesDir, plotId).delete() }

    fun copy(filesDir: File, fromPlotId: Long, toPlotId: Long) {
        val f = file(filesDir, fromPlotId)
        if (f.exists()) f.copyTo(file(filesDir, toPlotId), overwrite = true)
    }

    fun readDataUrl(filesDir: File, plotId: Long): String? {
        val f = file(filesDir, plotId)
        if (!f.exists()) return null
        val url = "data:image/jpeg;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
        return url.takeIf { Backdrop.isImageDataUrl(it) }
    }

    /** Stores a data URL from a plan file (already checked by the codec) as this plot's photo, re-encoded as JPEG. */
    fun writeDataUrl(filesDir: File, plotId: Long, dataUrl: String): Boolean = try {
        val bytes = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bmp == null) false else { save(filesDir, plotId, bmp); true }
    } catch (e: Exception) { false }
}
