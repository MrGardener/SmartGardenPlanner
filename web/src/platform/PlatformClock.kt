package com.example.smartgardenplanner.core

import kotlin.js.Date

/** Current time and local UTC offset for the browser build (the Android build has its own copy in core/). */
object PlatformClock {
    fun nowMillis(): Long = Date().getTime().toLong()
    fun localOffsetMillis(atMillis: Long): Long = (-Date(atMillis.toDouble()).getTimezoneOffset() * 60_000.0).toLong()
}
