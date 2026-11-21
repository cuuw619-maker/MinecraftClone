package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "worlds")
data class WorldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val seed: Long,
    val terrainType: String = TERRAIN_HILLS,
    val playerX: Float = 8.5f,
    val playerY: Float = 30.0f,
    val playerZ: Float = 8.5f,
    val playerYaw: Float = 45.0f,
    val playerPitch: Float = -15.0f,
    val isFlying: Boolean = false,
    val selectedSlot: Int = 0,
    val hotbarCsv: String = DEFAULT_HOTBAR_CSV,
    val editedBlocksCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TERRAIN_HILLS = "HILLS"
        const val TERRAIN_FLAT = "FLAT"
        const val DEFAULT_HOTBAR_CSV = "1,2,3,4,5,6,8,9,10"
    }
}
