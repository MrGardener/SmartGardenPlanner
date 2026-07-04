package com.example.smartgardenplanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.smartgardenplanner.core.PlantedNodeEntity

@Dao
interface PlantedNodeDao {
    @Insert
    suspend fun insert(node: PlantedNodeEntity)

    @Query("SELECT * FROM planted_nodes")
    suspend fun getAllNodes(): List<PlantedNodeEntity>
}