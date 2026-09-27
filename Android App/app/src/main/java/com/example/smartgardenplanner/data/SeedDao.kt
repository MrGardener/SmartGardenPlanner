package com.example.smartgardenplanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.smartgardenplanner.core.SeedEntity

/**
 * [UPDATED] Added update()/delete() — requested features "add your own varieties" and "edit
 * the space each vegetable uses" both require writing back to an existing SeedEntity, which
 * this DAO didn't support before (insert-only).
 */
@Dao
interface SeedDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(seed: SeedEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(seeds: List<SeedEntity>)

    @Update
    suspend fun update(seed: SeedEntity)

    @Delete
    suspend fun delete(seed: SeedEntity)

    @Query("SELECT * FROM seeds")
    suspend fun getAllSeeds(): List<SeedEntity>

    // [NEW] Deletes only bundled catalog entries (isCustom = 0), never anything the user
    // personally added or edited — this is what lets a tier switch safely wipe-and-reload the
    // catalog without touching user customizations.
    @Query("DELETE FROM seeds WHERE isCustom = 0")
    suspend fun deleteCatalogSeeds()

    @Query("SELECT COUNT(*) FROM seeds WHERE isCustom = 0")
    suspend fun catalogSeedCount(): Int

    // --- Tier switch support (DW-0801, T2-DAT-200). Upsert updates rows in place instead of deleting
    // them, so the ON DELETE RESTRICT foreign key from planted_nodes is never triggered.
    @Upsert
    suspend fun upsertAll(seeds: List<SeedEntity>)

    @Query("SELECT botanicalCode FROM seeds WHERE isCustom = 0")
    suspend fun catalogCodes(): List<String>

    @Query("SELECT botanicalCode FROM seeds WHERE isCustom = 1")
    suspend fun customCodes(): List<String>

    @Query("SELECT DISTINCT seedCode FROM planted_nodes")
    suspend fun plantedCodes(): List<String>

    @Query("DELETE FROM seeds WHERE isCustom = 0 AND botanicalCode IN (:codes)")
    suspend fun deleteCatalogSeedsByCode(codes: List<String>)

    @Query("SELECT * FROM seeds WHERE botanicalCode = :code LIMIT 1")
    suspend fun getByCode(code: String): SeedEntity?

    @Query("SELECT COUNT(*) FROM seeds")
    suspend fun count(): Int
}
