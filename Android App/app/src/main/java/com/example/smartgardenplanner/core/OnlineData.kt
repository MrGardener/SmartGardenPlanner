package com.example.smartgardenplanner.core

/**
 * Pure (no Android, no network) part of the optional online features (FR-026): the allow-listed hosts,
 * request URLs, and parsing of the responses. The actual HTTP call is made by data/NetworkGateway.kt, only
 * while the user's "Online features" switch is on.
 */
object OnlineData {

    /** The only hosts the app will ever contact. */
    val ALLOWED_HOSTS = setOf("api.open-meteo.com", "archive-api.open-meteo.com", "api.nal.usda.gov", "phzmapi.org")

    fun isAllowed(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        val host = url.removePrefix("https://").substringBefore("/").substringBefore("?").substringBefore(":")
        return host in ALLOWED_HOSTS
    }

    private fun coord(v: Double) = "%.4f".format(java.util.Locale.US, v)

    /** FR-019: rain yesterday and today (mm) from Open-Meteo. */
    fun rainUrl(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${coord(lat)}&longitude=${coord(lon)}" +
            "&daily=precipitation_sum&past_days=1&forecast_days=1&timezone=auto"

    /** FR-007: daily sunshine duration for a whole calendar year from the Open-Meteo archive. */
    fun sunshineUrl(lat: Double, lon: Double, year: Int): String =
        "https://archive-api.open-meteo.com/v1/archive?latitude=${coord(lat)}&longitude=${coord(lon)}" +
            "&start_date=$year-01-01&end_date=$year-12-31&daily=sunshine_duration&timezone=auto"

    /** FR-020: USDA FoodData Central search for a raw food. */
    fun fdcSearchUrl(foodName: String, apiKey: String): String {
        val q = java.net.URLEncoder.encode("$foodName raw", "UTF-8")
        val key = java.net.URLEncoder.encode(apiKey.ifBlank { "DEMO_KEY" }, "UTF-8")
        return "https://api.nal.usda.gov/fdc/v1/foods/search?query=$q&dataType=Foundation,SR%20Legacy&pageSize=1&api_key=$key"
    }

    /** FR-028: USDA hardiness zone for a US ZIP (PRISM 2023 data via phzmapi.org, a free static API). */
    fun zoneUrl(zip: String): String = "https://phzmapi.org/${zip.filter { it.isDigit() }.take(5)}.json"

    /** Zone label such as "7a" from a phzmapi.org response, or null. */
    fun parseZone(json: String): String? {
        val root = MiniJson.parse(json) as? Map<*, *> ?: return null
        val zone = (root["zone"] as? String)?.trim() ?: return null
        return zone.takeIf { HardinessZones.number(it) != null }
    }

    /** Sum of the daily precipitation values, or null when the response has none. */
    fun parseRainMm(json: String): Double? {
        val root = MiniJson.parse(json) as? Map<*, *> ?: return null
        val daily = root["daily"] as? Map<*, *> ?: return null
        val values = (daily["precipitation_sum"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() } ?: return null
        return if (values.isEmpty()) null else values.sum()
    }

    /** Average hours of sunshine per day for each month (index 0 = January), or null when unusable. */
    fun parseMonthlySunshineHours(json: String): List<Double>? {
        val root = MiniJson.parse(json) as? Map<*, *> ?: return null
        val daily = root["daily"] as? Map<*, *> ?: return null
        val dates = daily["time"] as? List<*> ?: return null
        val secs = daily["sunshine_duration"] as? List<*> ?: return null
        if (dates.size != secs.size || dates.isEmpty()) return null
        val sums = DoubleArray(12)
        val counts = IntArray(12)
        for (i in dates.indices) {
            val month = (dates[i] as? String)?.substring(5, 7)?.toIntOrNull() ?: continue
            val s = (secs[i] as? Number)?.toDouble() ?: continue
            sums[month - 1] += s / 3600.0
            counts[month - 1]++
        }
        if (counts.all { it == 0 }) return null
        return (0 until 12).map { if (counts[it] == 0) 0.0 else sums[it] / counts[it] }
    }

    data class FdcResult(val fdcId: Long, val description: String, val nutrients: Nutrients)

    /** First food in an FDC search response, with the nutrients the app shows. */
    fun parseFdcSearch(json: String): FdcResult? {
        val root = MiniJson.parse(json) as? Map<*, *> ?: return null
        val food = (root["foods"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return null
        val id = (food["fdcId"] as? Number)?.toLong() ?: return null
        val description = food["description"] as? String ?: ""
        val values = mutableMapOf<String, Float>()
        for (n in food["foodNutrients"] as? List<*> ?: emptyList<Any>()) {
            val m = n as? Map<*, *> ?: continue
            val number = m["nutrientNumber"]?.toString() ?: continue
            val value = (m["value"] as? Number)?.toFloat() ?: continue
            values.putIfAbsent(number, value)
        }
        val energy = values["208"] ?: return null
        return FdcResult(
            id, description,
            Nutrients(
                energyKcal = energy,
                proteinG = values["203"] ?: 0f,
                carbsG = values["205"] ?: 0f,
                fiberG = values["291"] ?: 0f,
                vitaminAUg = values["320"] ?: 0f,
                vitaminCMg = values["401"] ?: 0f,
                potassiumMg = values["306"] ?: 0f,
                ironMg = values["303"] ?: 0f,
                calciumMg = values["301"] ?: 0f
            )
        )
    }
}

/** Minimal JSON reader (objects, arrays, strings, numbers, booleans, null) so parsing is testable on the JVM. */
object MiniJson {

    fun parse(text: String): Any? = try {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (p.pos != text.length) null else v
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        null
    }

    private class Parser(val s: String) {
        var pos = 0

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipWs()
            require(pos < s.length) { "unexpected end" }
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("bad char $c")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            require(s.startsWith(word, pos)) { "bad literal" }
            pos += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            pos++
            skipWs()
            if (s[pos] == '}') { pos++; return m }
            while (true) {
                skipWs()
                val k = str()
                skipWs()
                require(s[pos] == ':') { "expected :" }
                pos++
                m[k] = value()
                skipWs()
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return m }
                    else -> throw IllegalArgumentException("expected , or }")
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            pos++
            skipWs()
            if (s[pos] == ']') { pos++; return l }
            while (true) {
                l += value()
                skipWs()
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return l }
                    else -> throw IllegalArgumentException("expected , or ]")
                }
            }
        }

        private fun str(): String {
            require(s[pos] == '"') { "expected string" }
            pos++
            val sb = StringBuilder()
            while (true) {
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[pos++]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> { sb.append(s.substring(pos, pos + 4).toInt(16).toChar()); pos += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] in "+-.eE")) pos++
            return s.substring(start, pos).toDouble()
        }
    }
}
