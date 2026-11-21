package com.example.data

import kotlinx.coroutines.flow.Flow

data class LoadedWorldData(
    val world: WorldEntity,
    val deltas: List<BlockDeltaEntity>
)

class WorldRepository(private val worldDao: WorldDao) {

    val worldsFlow: Flow<List<WorldEntity>> = worldDao.observeWorlds()

    suspend fun createWorld(
        name: String,
        seed: Long,
        terrainType: String,
        spawnY: Float
    ): WorldEntity {
        val now = System.currentTimeMillis()
        val entity = WorldEntity(
            name = name.trim().ifEmpty { "Новый мир" },
            seed = seed,
            terrainType = terrainType,
            playerX = 8.5f,
            playerY = spawnY,
            playerZ = 8.5f,
            playerYaw = 45f,
            playerPitch = -14f,
            isFlying = false,
            selectedSlot = 0,
            hotbarCsv = WorldEntity.DEFAULT_HOTBAR_CSV,
            editedBlocksCount = 0,
            createdAt = now,
            lastPlayedAt = now
        )
        val id = worldDao.insertWorld(entity)
        return entity.copy(id = id)
    }

    suspend fun loadWorld(worldId: Long): LoadedWorldData? {
        val world = worldDao.getWorldById(worldId) ?: return null
        val deltas = worldDao.getBlockDeltas(worldId)
        return LoadedWorldData(world = world, deltas = deltas)
    }

    suspend fun saveBlockDelta(worldId: Long, x: Int, y: Int, z: Int, blockId: Int) {
        worldDao.upsertBlockDelta(
            BlockDeltaEntity(
                worldId = worldId,
                x = x,
                y = y,
                z = z,
                blockId = blockId
            )
        )
    }

    suspend fun saveWorldProgress(
        worldId: Long,
        x: Float,
        y: Float,
        z: Float,
        yaw: Float,
        pitch: Float,
        isFlying: Boolean,
        selectedSlot: Int,
        hotbarIds: List<Int>
    ) {
        val count = worldDao.countDeltas(worldId)
        worldDao.updateWorldProgress(
            worldId = worldId,
            x = x,
            y = y,
            z = z,
            yaw = yaw,
            pitch = pitch,
            isFlying = isFlying,
            selectedSlot = selectedSlot,
            hotbarCsv = hotbarIds.joinToString(","),
            editedCount = count,
            lastPlayedAt = System.currentTimeMillis()
        )
    }

    suspend fun deleteWorld(worldId: Long) {
        worldDao.deleteWorldById(worldId)
    }
}
