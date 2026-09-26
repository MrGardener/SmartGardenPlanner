package com.example.smartgardenplanner.core

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [NEW] Static offline climate lookup table (T2-DAT-080, HLR-DAT-130). Maps a 5-digit ZIP code
 * to a USDA Hardiness Zone and last/first frost day-of-year estimates. Shipped as a small seed
 * dataset (see data/ClimateZoneSeedData.kt); this is deliberately NOT GPS-bounding-box based
 * (HLR-DAT-140) yet — ZIP lookup is the minimum viable slice needed to unblock T2-DAT-090/100.
 */
@Entity(tableName = "climate_zones")
data class ClimateZoneEntity(
    @PrimaryKey val zipCode: String,
    val hardinessZone: String,          // e.g. "7a"
    val lastFrostDayOfYear: Int,        // approximate last spring frost, day-of-year (1-366)
    val firstFrostDayOfYear: Int        // approximate first fall frost, day-of-year (1-366)
)
