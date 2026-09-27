package com.example.smartgardenplanner.data

import android.content.Context
import com.example.smartgardenplanner.core.OnlineData
import com.example.smartgardenplanner.core.ZipLocation
import com.example.smartgardenplanner.core.ZipTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ZIP code lookups (FR-028, T2-FUN-090): location offline from the bundled table; hardiness zone from the
 * bundled starter table, or online (phzmapi.org) when the Online features switch is on.
 */
object ZipLookup {

    @Volatile
    private var lines: List<String>? = null

    private fun table(context: Context): List<String> =
        lines ?: synchronized(this) {
            lines ?: context.applicationContext.assets.open("zip_locations.txt").bufferedReader().readLines()
                .filter { it.length >= 5 }.also { lines = it }
        }

    suspend fun location(context: Context, zip: String): ZipLocation? = withContext(Dispatchers.IO) {
        ZipTable.find(table(context), zip.trim())
    }

    /** Zone from the offline starter table, else online if allowed. */
    suspend fun zone(database: AppDatabase, zip: String, onlineEnabled: Boolean): OnlineResult<String> {
        val z = zip.trim()
        database.climateZoneDao().getByZip(z)?.let { return OnlineResult.Success(it.hardinessZone) }
        return when (val r = NetworkGateway.getText(OnlineData.zoneUrl(z), onlineEnabled)) {
            is OnlineResult.Success -> OnlineData.parseZone(r.value)?.let { OnlineResult.Success(it) } ?: OnlineResult.Failure("No zone found for ZIP $z.")
            is OnlineResult.Failure -> if (r.message.contains("404")) OnlineResult.Failure("No zone found for ZIP $z.") else r
            OnlineResult.Disabled -> OnlineResult.Disabled
        }
    }
}
