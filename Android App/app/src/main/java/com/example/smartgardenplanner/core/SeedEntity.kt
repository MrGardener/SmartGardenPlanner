package com.example.smartgardenplanner.core

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [NEW] The botanical/crop-intelligence dictionary table. Previously, MainActivity.kt attempted to
 * INSERT raw seed rows via hand-written SQL against a guessed table name ("seeds"/"SeedEntity"/"seed")
 * that did not exist as a Room entity anywhere in the project — every one of those inserts silently
 * failed. This entity is the fix: it satisfies T2-DAT-060/065, T2-DAT-020/030/040/050, and is the
 * parent side of the new PlantedNodeEntity.seedCode foreign key.
 *
 * botanicalCode is the primary key and is what PlantedNodeEntity.seedCode references.
 *
 * [UPDATED] Added plantType, lifecycle, hardinessZoneMin/Max, and catalogTier — supporting the
 * tiered Basic/Standard/Pro seed catalog (Basic=100, Standard=250, Pro=600+ varieties spanning
 * vegetables, fruit, herbs, flowers, and ornamentals, zones 3-11). See data/SeedCatalogLoader.kt
 * for how these get bulk-imported from bundled asset files, and ui/SettingsScreen.kt's Catalog
 * section for the tier picker.
 */
@Entity(tableName = "seeds")
data class SeedEntity(
    @PrimaryKey val botanicalCode: String,          // e.g. "SOL-LYC"
    val commonName: String,                          // e.g. "Tomato"
    val botanicalFamily: String,                      // e.g. "Solanaceae" — T2-DAT-065 anchor
    val exclusionRadiusM: Float,                      // spacing radius used by companion/collision checks
    val germinationDays: Int = 10,                    // expected days to sprout, drives HLR-DAT-080 alerts
    val daysToHarvest: Int = 60,                      // T2-DAT-010 harvest date calc
    val companionCodes: String = "",                  // comma-separated botanicalCode list of good companions
    val antagonistCodes: String = "",                 // comma-separated botanicalCode list of plants to avoid nearby
    val pestNotes: String = "",                       // T2-DAT-020
    val careNotes: String = "",                       // T2-DAT-040
    val fastTrackAlternateCode: String? = null,        // Plan-B path 1: short-maturity substitute
    val nurseryTransplantSuitable: Boolean = true,      // Plan-B path 2: transplant-from-nursery-start viable
    val catchCropAlternateCode: String? = null,         // Plan-B path 3: alternate catch-crop suggestion
    val colorHex: String? = null,                       // user-chosen canvas color override, e.g. "#FF6B35"
    @androidx.room.ColumnInfo(defaultValue = "VEGETABLE")
    val plantType: String = "VEGETABLE",                // [NEW] VEGETABLE | FRUIT | HERB | FLOWER | ORNAMENTAL
    @androidx.room.ColumnInfo(defaultValue = "ANNUAL")
    val lifecycle: String = "ANNUAL",                   // [NEW] ANNUAL | PERENNIAL
    @androidx.room.ColumnInfo(defaultValue = "3")
    val hardinessZoneMin: Int = 3,                      // [NEW] USDA zone range this variety grows in
    @androidx.room.ColumnInfo(defaultValue = "11")
    val hardinessZoneMax: Int = 11,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val isCustom: Boolean = false                       // [NEW] true for user-added varieties (Encyclopedia
                                                          // "Add Variety"), false for bundled catalog entries —
                                                          // lets a tier switch clear/reload catalog data without
                                                          // touching anything the user personally created.
)
