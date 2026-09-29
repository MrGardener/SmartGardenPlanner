package com.example.smartgardenplanner.data

import android.content.Context
import com.example.smartgardenplanner.core.FrostStation
import com.example.smartgardenplanner.core.GrowingSeason
import com.example.smartgardenplanner.core.PlotEntity
import com.example.smartgardenplanner.core.Season

/** NOAA 1991–2020 frost dates by station (FR-054), from the bundled frost_stations.txt; read once. */
object FrostLookup {
    @Volatile private var stations: List<FrostStation>? = null

    fun stations(context: Context): List<FrostStation> =
        stations ?: synchronized(this) {
            stations ?: try {
                context.applicationContext.assets.open("frost_stations.txt").bufferedReader().readLines().mapNotNull { GrowingSeason.parseStation(it) }
            } catch (e: Exception) { emptyList() }.also { stations = it }
        }

    fun season(context: Context, plot: PlotEntity?): Season? = plot?.let { GrowingSeason.seasonAt(stations(context), it.latitude, it.longitude) }
}
