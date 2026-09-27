package com.example.smartgardenplanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.smartgardenplanner.core.ClimateZoneEntity
import com.example.smartgardenplanner.core.CareLogEntity
import com.example.smartgardenplanner.core.NutritionEntity
import com.example.smartgardenplanner.core.PathZoneEntity
import com.example.smartgardenplanner.core.SiteFeatureEntity
import com.example.smartgardenplanner.core.PlantedNodeEntity
import com.example.smartgardenplanner.core.PlotEntity
import com.example.smartgardenplanner.core.SeedEntity
import com.example.smartgardenplanner.core.SecurityKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
 *  - A key/file mismatch (for example after restoring a backup onto another phone) no longer deletes
 *    the database silently: getInstance() throws DataUnreadableException, the app explains the
 *    situation, and only on the user's confirmation are the files renamed aside (setAsideUnreadableData).
 */
@Database(
    entities = [
        AppConfig::class,
        PlotEntity::class,
        PlantedNodeEntity::class,
        SeedEntity::class,
        ClimateZoneEntity::class,
        PathZoneEntity::class,
        SiteFeatureEntity::class,
        CareLogEntity::class,
        NutritionEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun configDao(): AppConfigDao
    abstract fun plotDao(): PlotDao
    abstract fun plantedNodeDao(): PlantedNodeDao
    abstract fun seedDao(): SeedDao
    abstract fun climateZoneDao(): ClimateZoneDao
    abstract fun pathZoneDao(): PathZoneDao
    abstract fun siteFeatureDao(): SiteFeatureDao
    abstract fun careLogDao(): CareLogDao
    abstract fun nutritionDao(): NutritionDao

    companion object {
        private const val DB_NAME = "smart_garden_secure_vault.db"
        private const val KEY_FILE_NAME = "sgp_db_key.enc"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Opens the encrypted database. Throws [DataUnreadableException] when the stored data can't be
         * decrypted with the key available on this device (e.g. after a backup was restored onto another
         * phone). Nothing is deleted here: the caller decides, with the user's confirmation, whether to set
         * the unreadable files aside (T2-SEC-050, HLR-PROT-040). The previous version silently deleted the
         * database in this situation.
         */
        fun getInstance(context: Context, keyManager: SecurityKeyManager): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    loadSqlCipherLibrary()
                    val instance = try {
                        buildAndVerify(context, keyManager)
                    } catch (e: android.database.sqlite.SQLiteException) {
                        throw DataUnreadableException("database could not be opened with the stored key", e)
                    } catch (e: java.security.GeneralSecurityException) {
                        throw DataUnreadableException("database key unavailable or invalid", e)
                    }
                    INSTANCE = instance
                    instance
                }
            }
        }

        private fun buildAndVerify(context: Context, keyManager: SecurityKeyManager): AppDatabase {
            val passphrase = keyManager.getDatabasePassphrase(context)
            val factory = SupportOpenHelperFactory(passphrase)

            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(*ALL_MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

            // Open now (Room opens lazily), so a wrong key surfaces here rather than on a later query.
            try {
                instance.openHelper.writableDatabase
            } catch (e: RuntimeException) {
                instance.close()
                throw e
            }
            return instance
        }

        /**
         * "Start with empty data": renames the unreadable database files and key file with a
         * `.unreadable-<utc millis>` suffix, so they are kept on the device rather than deleted.
         */
        fun setAsideUnreadableData(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                val suffix = ".unreadable-" + System.currentTimeMillis()
                val dbFile = context.getDatabasePath(DB_NAME)
                listOf(
                    dbFile,
                    File(dbFile.path + "-wal"),
                    File(dbFile.path + "-shm"),
                    File(dbFile.path + "-journal"),
                    File(context.filesDir, KEY_FILE_NAME)
                ).forEach { file ->
                    if (file.exists()) file.renameTo(File(file.path + suffix))
                }
            }
        }

        /** SQLCipher for Android ships its native library as "sqlcipher"; it must be loaded before first use. */
        private fun loadSqlCipherLibrary() {
            System.loadLibrary("sqlcipher")
        }

        /** Test-only: allows instrumented tests to build a fresh in-memory instance without the singleton cache. */
        fun buildInMemoryForTest(context: Context, keyManager: SecurityKeyManager): AppDatabase {
            loadSqlCipherLibrary()
            val passphrase = keyManager.getDatabasePassphrase(context)
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .openHelperFactory(factory)
                .allowMainThreadQueries()
                .build()
        }
    }
}

/** The stored data can't be decrypted on this device; see [AppDatabase.getInstance]. */
class DataUnreadableException(message: String, cause: Throwable) : Exception(message, cause)
