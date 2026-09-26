package com.example.smartgardenplanner.core

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * [UPDATED] Added fields required by T2-FUN-010 (camera image), T2-DAT-120/140 (ownership),
 * and T2-CON-030 (persisted state change timestamp). All new fields are nullable / defaulted
 * so this remains additive for the Room migration (see data/Migrations.kt MIGRATION_2_3).
 *
 * [FIXED] Added @ColumnInfo(defaultValue = ...) on every new non-null field. Room does NOT
 * translate a plain Kotlin default parameter value (e.g. `= "MANUAL"`) into the generated SQL
 * schema — that only affects object construction in Kotlin code. Without an explicit
 * @ColumnInfo default, a freshly-created table (as opposed to one reached via MIGRATION_2_3,
 * which does have real `DEFAULT` clauses in its hand-written SQL) ends up with a plain
 * `NOT NULL` column and no fallback — which is exactly what caused a real crash
 * (`NOT NULL constraint failed: plots.scaleSource`) from a raw SQL INSERT elsewhere in the
 * app that doesn't set these columns explicitly.
 */
@Entity(tableName = "plots")
data class PlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lengthM: Float,
    val widthM: Float,
    val description: String = "",
    val imagePath: String? = null,              // [NEW] T2-FUN-010: path to the captured/downscaled JPEG
    @ColumnInfo(defaultValue = "MANUAL")
    val scaleSource: String = "MANUAL",          // [NEW] "MANUAL" or "IMU_SENSOR" (T2-VAL-012)
    val locationZip: String? = null,             // [NEW] T2-FUN-090 / T2-DAT-080 climate lookup key
    @ColumnInfo(defaultValue = "OWNER")
    val ownerRole: String = "OWNER",             // [NEW] T2-DAT-140: OWNER | CONTRIBUTOR | VIEWER
    @ColumnInfo(defaultValue = "0")
    val createdTimestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val lastModifiedTimestamp: Long = System.currentTimeMillis()
)
