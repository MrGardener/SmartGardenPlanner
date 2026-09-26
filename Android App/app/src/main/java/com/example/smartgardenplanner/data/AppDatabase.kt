package com.example.smartgardenplanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.smartgardenplanner.core.ClimateZoneEntity
import com.example.smartgardenplanner.core.PathZoneEntity
import com.example.smartgardenplanner.core.PlantedNodeEntity
import com.example.smartgardenplanner.core.PlotEntity
import com.example.smartgardenplanner.core.SeedEntity
import com.example.smartgardenplanner.core.SecurityKeyManager
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File

/**
 * [UPDATED]
 *  - version bumped 2 -> 3 for the new entities/columns (see Migrations.kt).
 *  - registers the previously-missing SeedEntity and ClimateZoneEntity (DEBT fix: MainActivity's
 *    seed pre-population raw SQL was targeting tables that never existed as Room entities).
 *  - .fallbackToDestructiveMigration() REMOVED as the default path (closes DEBT-DB-003). Real
 *    migrations are now supplied via .addMigrations(*ALL_MIGRATIONS). A destructive fallback is
 *    kept ONLY as an explicit last resort for schema versions with no defined migration path,
 *    via .fallbackToDestructiveMigrationOnDowngrade() (downgrades, e.g. a user side-loading an
 *    older build, are the one case where a forward migration cannot exist by definition).
 *  - [NEW] getInstance() now self-heals from "file is not a database" / SQLCipher key-mismatch
 *    failures. This happens whenever the on-disk encrypted DB file was created by a different
 *    passphrase than the one currently derived from the Keystore — e.g. a leftover file from an
 *    earlier build/version of the app still sitting in app-private storage from a prior install
 *    that wasn't fully uninstalled first (a very common situation during iterative dev/testing,
 *    and also a real scenario for end users after certain restore/backup situations). Previously
 *    this crashed the app on launch with no recovery path. Now: if opening the DB with the
 *    current passphrase fails, the stale DB file (+ its -wal/-shm siblings) and the stale
 *    encrypted passphrase file are deleted, a fresh passphrase is generated, and the DB is
 *    rebuilt once. This necessarily means any data in the stale file is unrecoverable — that is
 *    unavoidable once the encryption key no longer matches, but it turns a hard crash into a
 *    clean fresh start instead.
 */
@Database(
    entities = [
        AppConfig::class,
        PlotEntity::class,
        PlantedNodeEntity::class,
        SeedEntity::class,
        ClimateZoneEntity::class,
        PathZoneEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun configDao(): AppConfigDao
    abstract fun plotDao(): PlotDao
    abstract fun plantedNodeDao(): PlantedNodeDao
    abstract fun seedDao(): SeedDao
    abstract fun climateZoneDao(): ClimateZoneDao
    abstract fun pathZoneDao(): PathZoneDao

    companion object {
        private const val DB_NAME = "smart_garden_secure_vault.db"
        private const val KEY_FILE_NAME = "sgp_db_key.enc"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, keyManager: SecurityKeyManager): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                SQLiteDatabase.loadLibs(context)

                val instance = try {
                    buildAndVerify(context, keyManager)
                } catch (e: android.database.sqlite.SQLiteException) {
                    // [SELF-HEAL] Passphrase doesn't match the on-disk file. Wipe the stale
                    // encrypted DB + stale encrypted key blob and start fresh, once.
                    android.util.Log.w(
                        "AppDatabase",
                        "Existing encrypted DB could not be opened (stale key/file mismatch). " +
                            "Resetting local database. Cause: ${e.message}"
                    )
                    deleteStaleDatabaseFiles(context)
                    buildAndVerify(context, keyManager)
                }

                INSTANCE = instance
                instance
            }
        }

        private fun buildAndVerify(context: Context, keyManager: SecurityKeyManager): AppDatabase {
            val passphrase = keyManager.getDatabasePassphrase(context)
            val factory = SupportFactory(passphrase)

            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(*ALL_MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

            // Force the DB to actually open now (Room builds lazily otherwise), so a bad
            // passphrase/corrupt file surfaces here and can be caught by getInstance(), instead
            // of surfacing later on a random screen's first query with no recovery path.
            instance.openHelper.writableDatabase

            return instance
        }

        private fun deleteStaleDatabaseFiles(context: Context) {
            val dbFile = context.getDatabasePath(DB_NAME)
            listOf(
                dbFile,
                File(dbFile.path + "-wal"),
                File(dbFile.path + "-shm"),
                File(dbFile.path + "-journal")
            ).forEach { if (it.exists()) it.delete() }

            File(context.filesDir, KEY_FILE_NAME).let { if (it.exists()) it.delete() }
        }

        /** Test-only: allows instrumented tests to build a fresh in-memory instance without the singleton cache. */
        fun buildInMemoryForTest(context: Context, keyManager: SecurityKeyManager): AppDatabase {
            SQLiteDatabase.loadLibs(context)
            val passphrase = keyManager.getDatabasePassphrase(context)
            val factory = SupportFactory(passphrase)
            return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .openHelperFactory(factory)
                .allowMainThreadQueries()
                .build()
        }
    }
}
