package com.example.smartgardenplanner.data

import com.example.smartgardenplanner.core.NutritionEntity
import com.example.smartgardenplanner.core.OnlineData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Outcome of an online request (FR-026). */
sealed interface OnlineResult<out T> {
    data class Success<T>(val value: T) : OnlineResult<T>
    /** The "Online features" switch is off; nothing was sent. */
    data object Disabled : OnlineResult<Nothing>
    data class Failure(val message: String) : OnlineResult<Nothing>
}

/**
 * The single place the app talks to the network (FR-026, option A: one master switch, off by default).
 *  - Every call checks the switch first; when it's off, no connection is attempted.
 *  - Only HTTPS requests to [OnlineData.ALLOWED_HOSTS] are made. No personal data is sent: only the plot's
 *    latitude/longitude (rounded to 4 decimals) or a food name.
 *  - [inFlight] drives the on-screen "Connecting…" indicator, so a live call is never silent.
 */
object NetworkGateway {

    private val _inFlight = MutableStateFlow(0)
    val inFlight: StateFlow<Int> = _inFlight.asStateFlow()

    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val TIMEOUT_MS = 12_000

    suspend fun getText(url: String, onlineEnabled: Boolean): OnlineResult<String> {
        if (!onlineEnabled) return OnlineResult.Disabled
        if (!OnlineData.isAllowed(url)) return OnlineResult.Failure("Blocked: not an approved data service.")
        return withContext(Dispatchers.IO) {
            _inFlight.value = _inFlight.value + 1
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    instanceFollowRedirects = false
                    setRequestProperty("Accept", "application/json")
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    OnlineResult.Failure(if (code == 429) "The service is busy (rate limit). Try again later." else "The service answered with error $code.")
                } else {
                    val bytes = connection.inputStream.use { input ->
                        val buffer = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            buffer.write(chunk, 0, n)
                            if (buffer.size() > MAX_BYTES) throw java.io.IOException("Response too large")
                        }
                        buffer.toByteArray()
                    }
                    OnlineResult.Success(String(bytes, Charsets.UTF_8))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OnlineResult.Failure("No connection (${e.javaClass.simpleName}). Check that the phone is online.")
            } finally {
                connection?.disconnect()
                _inFlight.value = (_inFlight.value - 1).coerceAtLeast(0)
            }
        }
    }
}

/** The online data sources: rain (FR-019), sunshine history (FR-007) and nutrition refresh (FR-020). */
class OnlineSources(private val database: AppDatabase) {

    private val config = SecurityRepositoryImpl(database.configDao())

    suspend fun rainMm(lat: Double, lon: Double, onlineEnabled: Boolean): OnlineResult<Double> =
        when (val r = NetworkGateway.getText(OnlineData.rainUrl(lat, lon), onlineEnabled)) {
            is OnlineResult.Success -> OnlineData.parseRainMm(r.value)?.let { OnlineResult.Success(it) } ?: OnlineResult.Failure("Unexpected weather data.")
            is OnlineResult.Failure -> r
            OnlineResult.Disabled -> OnlineResult.Disabled
        }

    /** Last year's average daily sunshine per month for a plot; cached so it is fetched once per year. */
    suspend fun monthlySunshine(plotId: Long, lat: Double, lon: Double, onlineEnabled: Boolean, forceRefresh: Boolean = false): OnlineResult<List<Double>> {
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 1
        val cacheKey = "sunshine.$plotId"
        val cached = cachedSunshine(plotId)
        if (!forceRefresh && cached != null && cached.first == year && cached.third == "%.3f,%.3f".format(java.util.Locale.US, lat, lon)) {
            return OnlineResult.Success(cached.second)
        }
        return when (val r = NetworkGateway.getText(OnlineData.sunshineUrl(lat, lon, year), onlineEnabled)) {
            is OnlineResult.Success -> {
                val months = OnlineData.parseMonthlySunshineHours(r.value) ?: return OnlineResult.Failure("Unexpected sunshine data.")
                val loc = "%.3f,%.3f".format(java.util.Locale.US, lat, lon)
                config.saveConfig(cacheKey, "$year|$loc|" + months.joinToString(",") { "%.2f".format(java.util.Locale.US, it) })
                OnlineResult.Success(months)
            }
            is OnlineResult.Failure -> r
            OnlineResult.Disabled -> OnlineResult.Disabled
        }
    }

    /** Cached (year, monthly hours, "lat,lon") for a plot, or null. Readable while offline. */
    suspend fun cachedSunshine(plotId: Long): Triple<Int, List<Double>, String>? {
        val raw = config.fetchConfig("sunshine.$plotId")?.configValue ?: return null
        val parts = raw.split("|")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val months = parts[2].split(",").mapNotNull { it.toDoubleOrNull() }
        if (months.size != 12) return null
        return Triple(year, months, parts[1])
    }

    /** Refreshes one species' nutrition from USDA FoodData Central and stores it. */
    suspend fun refreshNutrition(speciesKey: String, displayName: String, apiKey: String, onlineEnabled: Boolean): OnlineResult<NutritionEntity> =
        when (val r = NetworkGateway.getText(OnlineData.fdcSearchUrl(displayName, apiKey), onlineEnabled)) {
            is OnlineResult.Success -> {
                val food = OnlineData.parseFdcSearch(r.value)
                if (food == null) {
                    OnlineResult.Failure("No USDA match for $displayName.")
                } else {
                    val n = food.nutrients
                    val entity = NutritionEntity(
                        speciesKey, n.energyKcal, n.proteinG, n.carbsG, n.fiberG, n.vitaminAUg, n.vitaminCMg,
                        n.potassiumMg, n.ironMg, n.calciumMg, "USDA FDC ${food.fdcId}: ${food.description}", System.currentTimeMillis()
                    )
                    database.nutritionDao().upsert(entity)
                    OnlineResult.Success(entity)
                }
            }
            is OnlineResult.Failure -> r
            OnlineResult.Disabled -> OnlineResult.Disabled
        }
}
