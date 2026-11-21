package com.example

import com.example.data.WorldEntity
import com.example.engine.BlockType
import com.example.engine.ChunkMeshBuilder
import com.example.engine.ParticleSystem
import com.example.engine.PlayerController
import com.example.engine.VoxelWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoxelEngineUnitTest {

    @Test
    fun flatWorldHasExpectedLayersAndBedrock() {
        val world = VoxelWorld(1L, 12345L, WorldEntity.TERRAIN_FLAT)
        val chunk = world.getOrCreateChunk(0, 0)
        assertEquals(BlockType.BEDROCK, chunk.getBlock(4, 0, 4))
        assertEquals(BlockType.STONE, chunk.getBlock(4, 5, 4))
        assertEquals(BlockType.DIRT, chunk.getBlock(4, 15, 4))
        assertEquals(BlockType.GRASS, chunk.getBlock(4, 16, 4))
        assertEquals(BlockType.AIR, chunk.getBlock(4, 17, 4))
    }

    @Test
    fun raycastHitsPlacedBlockWithCorrectNormal() {
        val world = VoxelWorld(1L, 42L, WorldEntity.TERRAIN_FLAT)
        world.getOrCreateChunk(0, 0)
        world.setBlock(8, 18, 8, BlockType.BRICKS)

        val hit = world.raycast(
            originX = 8.5f,
            originY = 18.5f,
            originZ = 11.5f,
            dirX = 0f,
            dirY = 0f,
            dirZ = -1f,
            maxDistance = 6f
        )

        assertNotNull(hit)
        assertEquals(8, hit!!.blockPos.x)
        assertEquals(18, hit.blockPos.y)
        assertEquals(8, hit.blockPos.z)
        assertEquals(0, hit.normalX)
        assertEquals(0, hit.normalY)
        assertEquals(1, hit.normalZ)
        assertEquals(BlockType.BRICKS, hit.blockType)
    }

    @Test
    fun playerLandsOnFlatGroundAndTogglesFlight() {
        val world = VoxelWorld(1L, 99L, WorldEntity.TERRAIN_FLAT)
        world.getOrCreateChunk(0, 0)

        val player = PlayerController(
            initialX = 8.5f,
            initialY = 20f,
            initialZ = 8.5f,
            initialFlying = false
        )

        repeat(120) {
            player.update(0.016f, world)
        }
        assertTrue(player.onGround)
        assertEquals(17f, player.y, 0.05f)

        assertFalse(player.isFlying)
        player.toggleFly()
        assertTrue(player.isFlying)
        player.jumpHeld = true
        repeat(20) {
            player.update(0.016f, world)
        }
        assertTrue(player.y > 17.5f)
    }

    @Test
    fun chunkMeshBuilderGeneratesOpaqueAndWaterMeshes() {
        val world = VoxelWorld(1L, 77L, WorldEntity.TERRAIN_HILLS)
        val chunk = world.getOrCreateChunk(0, 0)
        val mesh = ChunkMeshBuilder.buildMesh(chunk, world)
        assertTrue(mesh.opaqueVertices.isNotEmpty())
        assertEquals(0, mesh.opaqueVertices.size % ChunkMeshBuilder.FLOATS_PER_VERTEX)
        assertEquals(0, mesh.waterVertices.size % ChunkMeshBuilder.FLOATS_PER_VERTEX)
    }

    @Test
    fun particleSystemSpawnsAndUpdatesVertices() {
        val world = VoxelWorld(1L, 77L, WorldEntity.TERRAIN_FLAT)
        world.getOrCreateChunk(0, 0)
        val particles = ParticleSystem()
        particles.queueBreakBurst(8, 17, 8, BlockType.GRASS)
        particles.updateAndBuildBuffer(0.016f, world)
        assertTrue(particles.activeVertexCount > 0)
    }
}
