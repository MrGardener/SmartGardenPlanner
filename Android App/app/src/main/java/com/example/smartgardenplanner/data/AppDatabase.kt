package com.example.smartgardenplanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.smartgardenplanner.security.SecurityKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [AppConfig::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun configDao(): AppConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Force load the native crypto binaries before building the schema helper factory
                System.loadLibrary("sqlcipher")

                val passphrase = SecurityKeyManager.getDatabasePassphrase()
                val factory = SupportOpenHelperFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_garden_secure_vault.db"
                )
                    .openHelperFactory(factory) // Locks down the database file with AES-256 encryption
                    .fallbackToDestructiveMigration() // Safely handles version changes during prototyping
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}