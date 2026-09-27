package com.example.smartgardenplanner.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * Pure-Kotlin replacements for JVM-only library calls, so the planning core compiles for Android (JVM) and for
 * the browser planner (Kotlin/JS) and gives the same results on both (T2-PLT-030, docs/CROSS_PLATFORM_PLAN.md).
 * The only platform-specific piece is [PlatformClock] (current time and local UTC offset), which each build
 * supplies in its own source file.
 */

/** Fixed-decimal text, rounding half away from zero, e.g. 2.25.fmt(1) = "2.3". Same as "%.Nf" for display values. */
fun Double.fmt(decimals: Int): String {
    if (isNaN()) return "NaN"
    if (isInfinite()) return if (this > 0) "Infinity" else "-Infinity"
    val d = decimals.coerceIn(0, 9)
    val factor = 10.0.pow(d)
    val scaled = floor(abs(this) * factor + 0.5).toLong()
    val whole = scaled / factor.toLong()
    val frac = scaled % factor.toLong()
    val sign = if (this < 0 && scaled != 0L) "-" else ""
    return if (d == 0) "$sign$whole" else "$sign$whole." + frac.toString().padStart(d, '0')
}

fun Float.fmt(decimals: Int): String = toDouble().fmt(decimals)

/** Calendar arithmetic without java.time/java.util (proleptic Gregorian, UTC). */
object CivilDate {
    const val DAY_MS = 86_400_000L

    /** Days since 1970-01-01 for a civil date (H. Hinnant's algorithm). */
    fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    /** (year, month 1–12, day) for days since 1970-01-01. */
    fun civilFromDays(days: Long): Triple<Int, Int, Int> {
        val z = days + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + (if (m <= 2) 1 else 0)).toInt()
        return Triple(y, m, d)
    }

    private fun floorDiv(a: Long, b: Long): Long { val q = a / b; return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q }

    /** Day of the year (1–366) of [millis] in local time. */
    fun dayOfYear(millis: Long, offsetMillis: Long = PlatformClock.localOffsetMillis(millis)): Int {
        val days = floorDiv(millis + offsetMillis, DAY_MS)
        val (y, _, _) = civilFromDays(days)
        return (days - daysFromCivil(y, 1, 1) + 1).toInt()
    }

    /** Local calendar year of [millis]. */
    fun year(millis: Long, offsetMillis: Long = PlatformClock.localOffsetMillis(millis)): Int =
        civilFromDays(floorDiv(millis + offsetMillis, DAY_MS)).first

    /** Local midnight at the start of the given date, as epoch millis. */
    fun localMidnight(year: Int, month: Int, day: Int, offsetMillis: Long): Long =
        daysFromCivil(year, month, day) * DAY_MS - offsetMillis

    /** "2026-09-27T12:00:00Z" for [millis]. */
    fun isoUtc(millis: Long): String {
        val days = floorDiv(millis, DAY_MS)
        val msOfDay = millis - days * DAY_MS
        val (y, m, d) = civilFromDays(days)
        val secs = msOfDay / 1000
        fun two(v: Long) = v.toString().padStart(2, '0')
        return "${y.toString().padStart(4, '0')}-${two(m.toLong())}-${two(d.toLong())}T${two(secs / 3600)}:${two(secs / 60 % 60)}:${two(secs % 60)}Z"
    }

    private val ISO = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})(\\.\\d+)?Z$")

    /** Parses [isoUtc]'s format (fractional seconds allowed). Returns null for anything else or an invalid date. */
    fun parseIsoUtc(text: String?): Long? {
        val m = ISO.matchEntire(text?.trim() ?: return null) ?: return null
        val (y, mo, d, h, mi, s) = m.destructured
        val year = y.toInt(); val month = mo.toInt(); val day = d.toInt()
        val hour = h.toInt(); val minute = mi.toInt(); val second = s.toInt()
        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null
        val days = daysFromCivil(year, month, day)
        if (civilFromDays(days) != Triple(year, month, day)) return null // e.g. 31 February
        return days * DAY_MS + ((hour * 60 + minute) * 60 + second) * 1000L
    }
}

/** application/x-www-form-urlencoded encoding (same output as java.net.URLEncoder with UTF-8). */
fun urlEncode(text: String): String {
    val sb = StringBuilder()
    for (b in text.encodeToByteArray()) {
        val c = (b.toInt() and 0xFF)
        val ch = c.toChar()
        when {
            ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '*' -> sb.append(ch)
            ch == ' ' -> sb.append('+')
            else -> { sb.append('%'); sb.append("0123456789ABCDEF"[c shr 4]); sb.append("0123456789ABCDEF"[c and 15]) }
        }
    }
    return sb.toString()
}
