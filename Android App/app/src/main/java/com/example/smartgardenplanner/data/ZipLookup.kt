package com.example.smartgardenplanner.data

import android.content.Context
import com.example.smartgardenplanner.core.OnlineData
import com.example.smartgardenplanner.core.ZipLocation
import com.example.smartgardenplanner.core.ZipTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ZIP code lookups (FR-028, T2-FUN-090): location offline from the bundled ZIP table; hardiness zone offline
 * from the bundled 2023 USDA/PRISM ZIP table, with the online service (phzmapi.org) only as a fallback for ZIPs
 * missing from it, when the Online features switch is on.
 */
object ZipLookup {

    @Volatile
    private var lines: List<String>? = null

    @Volatile
    private var zoneLines: List<String>? = null

    private fun table(context: Context): List<String> =
        lines ?: synchronized(this) {
            lines ?: context.applicationContext.assets.open("zip_locations.txt").bufferedReader().readLines()
                .filter { it.length >= 5 }.also { lines = it }
        }

    private fun zoneTable(context: Context): List<String> =
        zoneLines ?: synchronized(this) {
            zoneLines ?: context.applicationContext.assets.open("zip_zones.txt").bufferedReader().readLines()
                .filter { it.length >= 7 }.also { zoneLines = it }
        }

    /** Zone from the bundled 2023 USDA/PRISM ZIP table (offline, 40,502 ZIPs), or null. */
    suspend fun offlineZone(context: Context, zip: String): String? = withContext(Dispatchers.IO) {
        ZipTable.findZone(zoneTable(context), zip.trim())
    }

    suspend fun location(context: Context, zip: String): ZipLocation? = withContext(Dispatchers.IO) {
        ZipTable.find(table(context), zip.trim())
    }

    /** Zone from the bundled USDA table, then the starter table, then online if allowed. */
    suspend fun zone(context: Context, database: AppDatabase, zip: String, onlineEnabled: Boolean): OnlineResult<String> {
        val z = zip.trim()
        offlineZone(context, z)?.let { return OnlineResult.Success(it) }
        database.climateZoneDao().getByZip(z)?.let { return OnlineResult.Success(it.hardinessZone) }
        return when (val r = NetworkGateway.getText(OnlineData.zoneUrl(z), onlineEnabled)) {
            is OnlineResult.Success -> OnlineData.parseZone(r.value)?.let { OnlineResult.Success(it) } ?: OnlineResult.Failure("No zone found for ZIP $z.")
            is OnlineResult.Failure -> if (r.message.contains("404")) OnlineResult.Failure("No zone found for ZIP $z.") else r
            OnlineResult.Disabled -> OnlineResult.Disabled
        }
    }
}
