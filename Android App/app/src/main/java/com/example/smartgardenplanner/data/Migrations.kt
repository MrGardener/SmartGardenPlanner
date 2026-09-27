package com.example.smartgardenplanner.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * [NEW / FIX for DEBT-DB-003] Real, additive Room migrations. Previously AppDatabase.getInstance()
 * called .fallbackToDestructiveMigration() unconditionally, meaning any schema version bump would
 * silently wipe every user's local planting history — flagged as open technical debt in the Dev
 * Log and now confirmed live in the shipped code. This file replaces that with explicit migrations.
 *
 * MIGRATION_2_3 covers the version bump introduced by this revision's entity changes:
 *   - plots: + imagePath, scaleSource, locationZip, ownerRole, createdTimestamp, lastModifiedTimestamp
 *   - planted_nodes: + datePlantedEpochMillis, germinationFlagResolved, and a real FK to seeds.botanicalCode
 *     (SQLite can't ADD a foreign key via ALTER TABLE, so planted_nodes is recreated in-place with
 *     the new schema and its existing rows are copied over before the old table is dropped)
 *   - seeds: new table (the previously-missing botanical dictionary)
 *   - climate_zones: new table (the previously-missing ZIP-based climate lookup)
 *
 * AppDatabase.getInstance() still keeps a destructive fallback ONLY as a last-resort safety net for
 * versions with no migration path defined (e.g. a user somehow on a pre-release schema) — going
 * forward, every version bump this project ships should add a corresponding Migration object here
 * instead of relying on that fallback.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // --- plots: additive columns, safe defaults ---
        db.execSQL("ALTER TABLE plots ADD COLUMN imagePath TEXT")
        db.execSQL("ALTER TABLE plots ADD COLUMN scaleSource TEXT NOT NULL DEFAULT 'MANUAL'")
        db.execSQL("ALTER TABLE plots ADD COLUMN locationZip TEXT")
        db.execSQL("ALTER TABLE plots ADD COLUMN ownerRole TEXT NOT NULL DEFAULT 'OWNER'")
        db.execSQL("ALTER TABLE plots ADD COLUMN createdTimestamp INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE plots ADD COLUMN lastModifiedTimestamp INTEGER NOT NULL DEFAULT 0")

        // --- seeds: new botanical dictionary table ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS seeds (
                botanicalCode TEXT NOT NULL PRIMARY KEY,
                commonName TEXT NOT NULL,
                botanicalFamily TEXT NOT NULL,
                exclusionRadiusM REAL NOT NULL,
                germinationDays INTEGER NOT NULL DEFAULT 10,
                daysToHarvest INTEGER NOT NULL DEFAULT 60,
                companionCodes TEXT NOT NULL DEFAULT '',
                antagonistCodes TEXT NOT NULL DEFAULT '',
                pestNotes TEXT NOT NULL DEFAULT '',
                careNotes TEXT NOT NULL DEFAULT '',
                fastTrackAlternateCode TEXT,
                nurseryTransplantSuitable INTEGER NOT NULL DEFAULT 1,
                catchCropAlternateCode TEXT
            )
            """.trimIndent()
        )

        // --- climate_zones: new static lookup table ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS climate_zones (
                zipCode TEXT NOT NULL PRIMARY KEY,
                hardinessZone TEXT NOT NULL,
                lastFrostDayOfYear INTEGER NOT NULL,
                firstFrostDayOfYear INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // --- planted_nodes: recreate with new columns + real FK to seeds.botanicalCode ---
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS planted_nodes_new (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                plotId INTEGER NOT NULL,
                seedCode TEXT NOT NULL,
                coordinateXM REAL NOT NULL,
                coordinateYM REAL NOT NULL,
                datePlantedEpochMillis INTEGER NOT NULL DEFAULT 0,
                germinationFlagResolved INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(plotId) REFERENCES plots(id) ON DELETE CASCADE,
                FOREIGN KEY(seedCode) REFERENCES seeds(botanicalCode) ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        // Existing rows may reference seedCode values that don't yet exist in the new `seeds`
        // table (since seeds is brand new); the FK is only enforced on NEW writes going forward
        // because SQLite does not retroactively validate rows inserted before the constraint existed.
        db.execSQL(
            """
            INSERT INTO planted_nodes_new (id, plotId, seedCode, coordinateXM, coordinateYM, datePlantedEpochMillis, germinationFlagResolved)
            SELECT id, plotId, seedCode, coordinateXM, coordinateYM, 0, 0 FROM planted_nodes
            """.trimIndent()
        )
        db.execSQL("DROP TABLE planted_nodes")
        db.execSQL("ALTER TABLE planted_nodes_new RENAME TO planted_nodes")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planted_nodes_plotId ON planted_nodes(plotId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_planted_nodes_seedCode ON planted_nodes(seedCode)")
    }
}

/**
 * [NEW] Adds the path_zones table (core/PathZoneEntity.kt) for the "no-plant path" feature.
 * Additive only — no existing columns touched.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS path_zones (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                plotId INTEGER NOT NULL,
                xM REAL NOT NULL,
                yM REAL NOT NULL,
                widthM REAL NOT NULL,
                heightM REAL NOT NULL,
                label TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(plotId) REFERENCES plots(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_path_zones_plotId ON path_zones(plotId)")
    }
}

// [FIXED] Kotlin top-level properties initialize in file order — this was previously placed
// ABOVE the MIGRATION_3_4 declaration it references, which fails to compile ("must be
// initialized") since MIGRATION_3_4 didn't exist yet at that point in the file. Moved below
// both migration declarations.
/** [NEW] Adds SeedEntity.colorHex — the user-assignable canvas color override. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE seeds ADD COLUMN colorHex TEXT")
    }
}

/** [NEW] Adds POLYLINE (point-based, curved) path support alongside the existing RECTANGLE type. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE path_zones ADD COLUMN pathType TEXT NOT NULL DEFAULT 'RECTANGLE'")
        db.execSQL("ALTER TABLE path_zones ADD COLUMN pointsJson TEXT")
    }
}

// [FIXED] Same class of ordering mistake caught and fixed in the previous round — keep
// ALL_MIGRATIONS below every migration it references, always.
/** [NEW] Adds tiered-catalog support: plantType, lifecycle, USDA zone range, and isCustom on seeds. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE seeds ADD COLUMN plantType TEXT NOT NULL DEFAULT 'VEGETABLE'")
        db.execSQL("ALTER TABLE seeds ADD COLUMN lifecycle TEXT NOT NULL DEFAULT 'ANNUAL'")
        db.execSQL("ALTER TABLE seeds ADD COLUMN hardinessZoneMin INTEGER NOT NULL DEFAULT 3")
        db.execSQL("ALTER TABLE seeds ADD COLUMN hardinessZoneMax INTEGER NOT NULL DEFAULT 11")
        // Every pre-existing row (the original 5-seed starter set) predates the tiered catalog
        // system, so mark it custom — a tier switch/reload must never touch or delete it.
        db.execSQL("ALTER TABLE seeds ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 1")
    }
}

// [FIXED] Same class of ordering mistake caught and fixed in earlier rounds — keep
// ALL_MIGRATIONS below every migration it references, always.
/**
 * Schema 8: roadmap features. Adds plot outline, hardiness zone, location, orientation and soil columns
 * to plots, and the site_features (FR-003 to FR-006), care_log (FR-019) and nutrition_facts (FR-020)
 * tables. The SQL matches what Room generates for the entities, including defaults, foreign keys and
 * indices, so Room's schema check passes after the migration.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plots ADD COLUMN boundaryJson TEXT")
        db.execSQL("ALTER TABLE plots ADD COLUMN hardinessZone TEXT")
        db.execSQL("ALTER TABLE plots ADD COLUMN latitude REAL")
        db.execSQL("ALTER TABLE plots ADD COLUMN longitude REAL")
        db.execSQL("ALTER TABLE plots ADD COLUMN northBearingDeg REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE plots ADD COLUMN soilSandPct REAL")
        db.execSQL("ALTER TABLE plots ADD COLUMN soilSiltPct REAL")
        db.execSQL("ALTER TABLE plots ADD COLUMN soilClayPct REAL")
        db.execSQL("ALTER TABLE plots ADD COLUMN soilOrganicPct REAL")
        db.execSQL("ALTER TABLE plots ADD COLUMN soilPh REAL")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `site_features` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`plotId` INTEGER NOT NULL, `featureType` TEXT NOT NULL, `pointsJson` TEXT NOT NULL DEFAULT '', " +
                "`label` TEXT NOT NULL DEFAULT '', `heightM` REAL NOT NULL DEFAULT 0, `radiusM` REAL NOT NULL DEFAULT 0, " +
                "`slopeDirectionDeg` REAL NOT NULL DEFAULT 0, `slopeGradePct` REAL NOT NULL DEFAULT 0, " +
                "`floodMonths` TEXT NOT NULL DEFAULT '', " +
                "FOREIGN KEY(`plotId`) REFERENCES `plots`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_site_features_plotId` ON `site_features` (`plotId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `care_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`plotId` INTEGER NOT NULL, `taskType` TEXT NOT NULL, `doneAtEpochMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`plotId`) REFERENCES `plots`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_care_log_plotId` ON `care_log` (`plotId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `nutrition_facts` (`speciesKey` TEXT NOT NULL, `energyKcal` REAL NOT NULL, " +
                "`proteinG` REAL NOT NULL, `carbsG` REAL NOT NULL, `fiberG` REAL NOT NULL, `vitaminAUg` REAL NOT NULL, " +
                "`vitaminCMg` REAL NOT NULL, `potassiumMg` REAL NOT NULL, `ironMg` REAL NOT NULL, `calciumMg` REAL NOT NULL, " +
                "`source` TEXT NOT NULL, `updatedEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`speciesKey`))"
        )
    }
}

/** Schema 9: records whether the user has set the plot's compass orientation (FR-028). */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plots ADD COLUMN orientationSet INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Schema 10: season history (FR-033). Adds planting_history; the SQL matches what Room generates for
 * PlantingHistoryEntity (no defaults, one nullable column, cascade on plot delete, index on plotId).
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `planting_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`plotId` INTEGER NOT NULL, `seasonYear` INTEGER NOT NULL, `seedCode` TEXT NOT NULL, " +
                "`varietyName` TEXT NOT NULL, `family` TEXT NOT NULL, `rotationGroup` TEXT, " +
                "`coordinateXM` REAL NOT NULL, `coordinateYM` REAL NOT NULL, `radiusM` REAL NOT NULL, " +
                "`datePlantedEpochMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`plotId`) REFERENCES `plots`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_planting_history_plotId` ON `planting_history` (`plotId`)")
    }
}

/** Schema 11: pests seen in the yard (FR-042) and the satellite photo placement (FR-046) on plots. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plots ADD COLUMN pests TEXT")
        db.execSQL("ALTER TABLE plots ADD COLUMN backdropJson TEXT")
    }
}

/** Schema 12: the plot's street address (FR-050), used to open the yard in Google Maps. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plots ADD COLUMN address TEXT")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
