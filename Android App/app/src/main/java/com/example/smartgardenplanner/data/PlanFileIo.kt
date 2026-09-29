package com.example.smartgardenplanner.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads and writes plan files chosen with the system file picker (FR-029). */
object PlanFileIo {

    private const val MAX_BYTES = 20 * 1024 * 1024

    suspend fun write(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("Can't open the file for writing")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Can't open the file")
        input.use { stream ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16384)
            while (true) {
                val n = stream.read(chunk)
                if (n < 0) break
                buffer.write(chunk, 0, n)
                if (buffer.size() > MAX_BYTES) throw java.io.IOException("The file is larger than 20 MB")
            }
            String(buffer.toByteArray(), Charsets.UTF_8)
        }
    }

    fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    /** A safe file name for a plot, e.g. "Back yard.sgp.json". */
    fun fileName(base: String): String = base.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "garden" }.take(60) + ".sgp.json"
}
