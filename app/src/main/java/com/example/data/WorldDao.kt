package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldDao {
    @Query("SELECT * FROM worlds ORDER BY lastPlayedAt DESC")
    fun observeWorlds(): Flow<List<WorldEntity>>

    @Query("SELECT * FROM worlds WHERE id = :worldId LIMIT 1")
    suspend fun getWorldById(worldId: Long): WorldEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorld(world: WorldEntity): Long

    @Update
    suspend fun updateWorld(world: WorldEntity)

    @Query("DELETE FROM worlds WHERE id = :worldId")
    suspend fun deleteWorldById(worldId: Long)

    @Query("SELECT * FROM block_deltas WHERE worldId = :worldId")
    suspend fun getBlockDeltas(worldId: Long): List<BlockDeltaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlockDelta(delta: BlockDeltaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlockDeltas(deltas: List<BlockDeltaEntity>)

    @Query("SELECT COUNT(*) FROM block_deltas WHERE worldId = :worldId")
    suspend fun countDeltas(worldId: Long): Int

    @Query(
        """
        UPDATE worlds SET 
            playerX = :x,
            playerY = :y,
            playerZ = :z,
            playerYaw = :yaw,
            playerPitch = :pitch,
            isFlying = :isFlying,
            selectedSlot = :selectedSlot,
            hotbarCsv = :hotbarCsv,
            editedBlocksCount = :editedCount,
            lastPlayedAt = :lastPlayedAt
        WHERE id = :worldId
        """
    )
    suspend fun updateWorldProgress(
        worldId: Long,
        x: Float,
        y: Float,
        z: Float,
        yaw: Float,
        pitch: Float,
        isFlying: Boolean,
        selectedSlot: Int,
        hotbarCsv: String,
        editedCount: Int,
        lastPlayedAt: Long
    )
}
