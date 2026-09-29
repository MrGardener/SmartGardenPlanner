package com.example.smartgardenplanner.core

/**
 * Current time and the device's UTC offset, for the Android (JVM) build. The browser planner has its own copy
 * of this object (web/src/PlatformClock.kt). Everything else in core is platform-neutral (see Portable.kt).
 */
object PlatformClock {
    fun nowMillis(): Long = System.currentTimeMillis()
    fun localOffsetMillis(atMillis: Long): Long = java.util.TimeZone.getDefault().getOffset(atMillis).toLong()
}
