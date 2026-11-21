package com.example.engine

import com.example.data.BlockDeltaEntity
import com.example.data.WorldEntity
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor

data class BlockPos(val x: Int, val y: Int, val z: Int)

data class RaycastHit(
    val blockPos: BlockPos,
    val normalX: Int,
    val normalY: Int,
    val normalZ: Int,
    val blockType: BlockType
)

enum class BiomeType {
    PLAINS_FOREST,
    DESERT,
    SNOWY_TAIGA,
    STONY_PEAKS
}

class VoxelWorld(
    val worldId: Long,
    val seed: Long,
    val terrainType: String
) {
    private val heightNoise = SimplexNoise(seed)
    private val tempNoise = SimplexNoise(seed + 4111L)
    private val moistureNoise = SimplexNoise(seed + 9973L)
    private val caveNoise = SimplexNoise(seed + 21911L)

    private val chunks = ConcurrentHashMap<Long, Chunk>()
    private val deltas = ConcurrentHashMap<BlockPos, Byte>()

    fun loadDeltas(savedDeltas: List<BlockDeltaEntity>) {
        deltas.clear()
        for (d in savedDeltas) {
            deltas[BlockPos(d.x, d.y, d.z)] = d.blockId.toByte()
        }
        chunks.values.forEach { it.isDirty = true }
    }

    fun getChunk(cx: Int, cz: Int): Chunk? = chunks[chunkKey(cx, cz)]

    fun getOrCreateChunk(cx: Int, cz: Int): Chunk {
        val key = chunkKey(cx, cz)
        return chunks[key] ?: synchronized(this) {
            chunks[key] ?: generateChunk(cx, cz).also { chunks[key] = it }
        }
    }

    fun getLoadedChunkEntries(): List<Map.Entry<Long, Chunk>> = chunks.entries.toList()

    fun pruneFarChunks(centerCx: Int, centerCz: Int, keepRadius: Int): List<Long> {
        val removed = mutableListOf<Long>()
        val it = chunks.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val c = entry.value
            if (abs(c.cx - centerCx) > keepRadius || abs(c.cz - centerCz) > keepRadius) {
                removed.add(entry.key)
                it.remove()
            }
        }
        return removed
    }

    fun getBlock(wx: Int, wy: Int, wz: Int): BlockType {
        if (wy < 0 || wy >= Chunk.WORLD_HEIGHT) return BlockType.AIR
        val cx = floorDiv(wx, Chunk.CHUNK_SIZE)
        val cz = floorDiv(wz, Chunk.CHUNK_SIZE)
        val chunk = chunks[chunkKey(cx, cz)] ?: return BlockType.AIR
        val lx = floorMod(wx, Chunk.CHUNK_SIZE)
        val lz = floorMod(wz, Chunk.CHUNK_SIZE)
        return chunk.getBlock(lx, wy, lz)
    }

    fun setBlock(wx: Int, wy: Int, wz: Int, type: BlockType): Boolean {
        if (wy < 0 || wy >= Chunk.WORLD_HEIGHT) return false
        val pos = BlockPos(wx, wy, wz)
        deltas[pos] = type.id

        val cx = floorDiv(wx, Chunk.CHUNK_SIZE)
        val cz = floorDiv(wz, Chunk.CHUNK_SIZE)
        val lx = floorMod(wx, Chunk.CHUNK_SIZE)
        val lz = floorMod(wz, Chunk.CHUNK_SIZE)

        chunks[chunkKey(cx, cz)]?.let {
            it.setBlock(lx, wy, lz, type)
            it.isDirty = true
        }
        if (lx == 0) chunks[chunkKey(cx - 1, cz)]?.isDirty = true
        if (lx == Chunk.CHUNK_SIZE - 1) chunks[chunkKey(cx + 1, cz)]?.isDirty = true
        if (lz == 0) chunks[chunkKey(cx, cz - 1)]?.isDirty = true
        if (lz == Chunk.CHUNK_SIZE - 1) chunks[chunkKey(cx, cz + 1)]?.isDirty = true
        return true
    }

    fun getBiome(wx: Int, wz: Int): BiomeType {
        if (terrainType == WorldEntity.TERRAIN_FLAT) return BiomeType.PLAINS_FOREST
        val temp = tempNoise.fbm2D(wx.toFloat(), wz.toFloat(), octaves = 2, persistence = 0.5f, scale = 0.008f)
        val moist = moistureNoise.fbm2D(wx.toFloat(), wz.toFloat(), octaves = 2, persistence = 0.5f, scale = 0.009f)
        return when {
            temp > 0.24f -> BiomeType.DESERT
            temp < -0.22f -> BiomeType.SNOWY_TAIGA
            moist < -0.22f -> BiomeType.STONY_PEAKS
            else -> BiomeType.PLAINS_FOREST
        }
    }

    fun getSurfaceHeight(wx: Int, wz: Int): Int {
        if (terrainType == WorldEntity.TERRAIN_FLAT) return 16
        val biome = getBiome(wx, wz)
        val base = heightNoise.fbm2D(wx.toFloat(), wz.toFloat(), octaves = 3, persistence = 0.52f, scale = 0.02f)
        val detail = moistureNoise.fbm2D(wx.toFloat() + 200f, wz.toFloat() - 200f, octaves = 2, persistence = 0.5f, scale = 0.055f)
        val raw = when (biome) {
            BiomeType.DESERT -> 20f + base * 7f + detail * 2.5f
            BiomeType.SNOWY_TAIGA -> 22f + base * 12f + detail * 4f
            BiomeType.STONY_PEAKS -> 28f + abs(base) * 19f + detail * 5f
            BiomeType.PLAINS_FOREST -> 20f + base * 11f + detail * 3.5f
        }
        return raw.toInt().coerceIn(10, 48)
    }

    private fun generateChunk(cx: Int, cz: Int): Chunk {
        val chunk = Chunk(cx, cz)
        val baseX = cx * Chunk.CHUNK_SIZE
        val baseZ = cz * Chunk.CHUNK_SIZE

        for (lx in 0 until Chunk.CHUNK_SIZE) {
            val wx = baseX + lx
            for (lz in 0 until Chunk.CHUNK_SIZE) {
                val wz = baseZ + lz
                val topY = getSurfaceHeight(wx, wz)
                val biome = getBiome(wx, wz)
                val nearWater = terrainType == WorldEntity.TERRAIN_HILLS && topY <= SEA_LEVEL + 1

                chunk.setBlock(lx, 0, lz, BlockType.BEDROCK)

                for (y in 1 until topY) {
                    if (terrainType == WorldEntity.TERRAIN_HILLS && y in 2..(topY - 3) && topY > SEA_LEVEL) {
                        val c1 = caveNoise.noise3D(wx * 0.085f, y * 0.11f, wz * 0.085f)
                        val c2 = caveNoise.noise3D(wx * 0.045f + 90f, y * 0.06f, wz * 0.045f - 90f)
                        if (abs(c1) < 0.11f && abs(c2) < 0.19f) {
                            continue
                        }
                    }

                    val block = when {
                        y >= topY - 3 -> when {
                            biome == BiomeType.DESERT || nearWater -> BlockType.SAND
                            biome == BiomeType.STONY_PEAKS && topY >= 31 -> BlockType.STONE
                            else -> BlockType.DIRT
                        }
                        else -> generateStoneOrOre(wx, y, wz)
                    }
                    chunk.setBlock(lx, y, lz, block)
                }

                val surfaceBlock = when {
                    terrainType == WorldEntity.TERRAIN_FLAT -> BlockType.GRASS
                    nearWater -> BlockType.SAND
                    biome == BiomeType.DESERT -> BlockType.SAND
                    biome == BiomeType.SNOWY_TAIGA -> BlockType.SNOW_GRASS
                    biome == BiomeType.STONY_PEAKS && topY >= 37 -> BlockType.SNOW_BLOCK
                    biome == BiomeType.STONY_PEAKS && topY >= 30 -> BlockType.COBBLESTONE
                    else -> BlockType.GRASS
                }
                chunk.setBlock(lx, topY, lz, surfaceBlock)

                // Fill water lakes up to SEA_LEVEL
                if (terrainType == WorldEntity.TERRAIN_HILLS && topY < SEA_LEVEL) {
                    for (wy in (topY + 1)..SEA_LEVEL) {
                        chunk.setBlock(lx, wy, lz, BlockType.WATER)
                    }
                }
            }
        }

        // Cross-chunk trees (Oak in Plains, Spruce in Snowy Taiga)
        if (terrainType == WorldEntity.TERRAIN_HILLS) {
            for (twx in (baseX - 2) until (baseX + Chunk.CHUNK_SIZE + 2)) {
                for (twz in (baseZ - 2) until (baseZ + Chunk.CHUNK_SIZE + 2)) {
                    if (shouldSpawnTree(twx, twz)) {
                        val groundY = getSurfaceHeight(twx, twz)
                        val biome = getBiome(twx, twz)
                        if (groundY > SEA_LEVEL + 1 && (biome == BiomeType.PLAINS_FOREST || biome == BiomeType.SNOWY_TAIGA)) {
                            if (biome == BiomeType.SNOWY_TAIGA) {
                                placeSpruceTreeInChunk(chunk, baseX, baseZ, twx, groundY + 1, twz)
                            } else {
                                placeOakTreeInChunk(chunk, baseX, baseZ, twx, groundY + 1, twz)
                            }
                        }
                    }
                }
            }
        }

        // Apply saved user modifications in this chunk
        for ((pos, blockId) in deltas) {
            if (floorDiv(pos.x, Chunk.CHUNK_SIZE) == cx && floorDiv(pos.z, Chunk.CHUNK_SIZE) == cz) {
                val lx = floorMod(pos.x, Chunk.CHUNK_SIZE)
                val lz = floorMod(pos.z, Chunk.CHUNK_SIZE)
                chunk.setBlock(lx, pos.y, lz, BlockType.fromByte(blockId))
            }
        }
        chunk.isDirty = true
        return chunk
    }

    private fun generateStoneOrOre(wx: Int, y: Int, wz: Int): BlockType {
        if (terrainType == WorldEntity.TERRAIN_FLAT) return BlockType.STONE
        val oreRoll = hash3D(wx / 2, y / 2, wz / 2, seed + 555L) % 100
        if (y <= 14 && oreRoll == 0) return BlockType.DIAMOND_ORE
        if (y <= 30 && oreRoll in 1..4) return BlockType.IRON_ORE
        if (y <= 42 && oreRoll in 5..10) return BlockType.COAL_ORE
        return BlockType.STONE
    }

    private fun shouldSpawnTree(wx: Int, wz: Int): Boolean {
        val cellX = floorDiv(wx, 6)
        val cellZ = floorDiv(wz, 6)
        val h = hash2D(cellX, cellZ, seed)
        if ((h % 100) >= 36) return false
        val targetLx = (h shr 8) % 6
        val targetLz = (h shr 12) % 6
        return floorMod(wx, 6) == targetLx && floorMod(wz, 6) == targetLz
    }

    private fun placeOakTreeInChunk(
        chunk: Chunk,
        chunkBaseX: Int,
        chunkBaseZ: Int,
        tx: Int,
        baseY: Int,
        tz: Int
    ) {
        val trunkHeight = 4 + (hash2D(tx, tz, seed + 19L) % 2)
        for (dy in (trunkHeight - 2)..(trunkHeight + 1)) {
            val radius = if (dy >= trunkHeight) 1 else 2
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (abs(dx) == radius && abs(dz) == radius && (dy == trunkHeight + 1 || (dx + dz) % 2 == 0)) {
                        continue
                    }
                    val lx = (tx + dx) - chunkBaseX
                    val lz = (tz + dz) - chunkBaseZ
                    val wy = baseY + dy
                    if (lx in 0 until Chunk.CHUNK_SIZE && lz in 0 until Chunk.CHUNK_SIZE && wy in 1 until Chunk.WORLD_HEIGHT) {
                        if (chunk.getBlock(lx, wy, lz) == BlockType.AIR) {
                            chunk.setBlock(lx, wy, lz, BlockType.OAK_LEAVES)
                        }
                    }
                }
            }
        }
        for (dy in 0 until trunkHeight) {
            val lx = tx - chunkBaseX
            val lz = tz - chunkBaseZ
            val wy = baseY + dy
            if (lx in 0 until Chunk.CHUNK_SIZE && lz in 0 until Chunk.CHUNK_SIZE && wy in 1 until Chunk.WORLD_HEIGHT) {
                chunk.setBlock(lx, wy, lz, BlockType.OAK_LOG)
            }
        }
    }

    private fun placeSpruceTreeInChunk(
        chunk: Chunk,
        chunkBaseX: Int,
        chunkBaseZ: Int,
        tx: Int,
        baseY: Int,
        tz: Int
    ) {
        val trunkHeight = 5 + (hash2D(tx, tz, seed + 37L) % 2)
        for (dy in 2..(trunkHeight + 1)) {
            val radius = when {
                dy == trunkHeight + 1 -> 0
                dy >= trunkHeight - 1 -> 1
                else -> 2
            }
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (radius == 2 && abs(dx) == 2 && abs(dz) == 2) continue
                    val lx = (tx + dx) - chunkBaseX
                    val lz = (tz + dz) - chunkBaseZ
                    val wy = baseY + dy
                    if (lx in 0 until Chunk.CHUNK_SIZE && lz in 0 until Chunk.CHUNK_SIZE && wy in 1 until Chunk.WORLD_HEIGHT) {
                        if (chunk.getBlock(lx, wy, lz) == BlockType.AIR) {
                            chunk.setBlock(lx, wy, lz, BlockType.SPRUCE_LEAVES)
                        }
                    }
                }
            }
        }
        for (dy in 0 until trunkHeight) {
            val lx = tx - chunkBaseX
            val lz = tz - chunkBaseZ
            val wy = baseY + dy
            if (lx in 0 until Chunk.CHUNK_SIZE && lz in 0 until Chunk.CHUNK_SIZE && wy in 1 until Chunk.WORLD_HEIGHT) {
                chunk.setBlock(lx, wy, lz, BlockType.SPRUCE_LOG)
            }
        }
    }

    fun raycast(
        originX: Float,
        originY: Float,
        originZ: Float,
        dirX: Float,
        dirY: Float,
        dirZ: Float,
        maxDistance: Float = 6.5f
    ): RaycastHit? {
        var x = floor(originX).toInt()
        var y = floor(originY).toInt()
        var z = floor(originZ).toInt()

        val stepX = if (dirX > 0f) 1 else -1
        val stepY = if (dirY > 0f) 1 else -1
        val stepZ = if (dirZ > 0f) 1 else -1

        val tDeltaX = if (dirX != 0f) abs(1f / dirX) else Float.MAX_VALUE
        val tDeltaY = if (dirY != 0f) abs(1f / dirY) else Float.MAX_VALUE
        val tDeltaZ = if (dirZ != 0f) abs(1f / dirZ) else Float.MAX_VALUE

        var tMaxX = if (dirX > 0f) (x + 1f - originX) * tDeltaX else if (dirX < 0f) (originX - x) * tDeltaX else Float.MAX_VALUE
        var tMaxY = if (dirY > 0f) (y + 1f - originY) * tDeltaY else if (dirY < 0f) (originY - y) * tDeltaY else Float.MAX_VALUE
        var tMaxZ = if (dirZ > 0f) (z + 1f - originZ) * tDeltaZ else if (dirZ < 0f) (originZ - z) * tDeltaZ else Float.MAX_VALUE

        var nx = 0
        var ny = 0
        var nz = 0
        var dist = 0f

        while (dist <= maxDistance) {
            val block = getBlock(x, y, z)
            if (block != BlockType.AIR && block != BlockType.WATER && (nx != 0 || ny != 0 || nz != 0)) {
                return RaycastHit(BlockPos(x, y, z), nx, ny, nz, block)
            }

            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                dist = tMaxX
                tMaxX += tDeltaX
                x += stepX
                nx = -stepX
                ny = 0
                nz = 0
            } else if (tMaxY < tMaxZ) {
                dist = tMaxY
                tMaxY += tDeltaY
                y += stepY
                nx = 0
                ny = -stepY
                nz = 0
            } else {
                dist = tMaxZ
                tMaxZ += tDeltaZ
                z += stepZ
                nx = 0
                ny = 0
                nz = -stepZ
            }
            if (y < -4 || y >= Chunk.WORLD_HEIGHT + 4) break
        }
        return null
    }

    companion object {
        const val SEA_LEVEL = 18

        fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

        fun floorDiv(a: Int, b: Int): Int {
            var r = a / b
            if ((a xor b) < 0 && r * b != a) r--
            return r
        }

        fun floorMod(a: Int, b: Int): Int {
            val r = a % b
            return if (r < 0) r + b else r
        }

        private fun hash2D(x: Int, z: Int, seed: Long): Int {
            var h = x * 374761393L + z * 668265263L + seed * 1442695041L
            h = (h xor (h shr 13)) * 1274126177L
            return abs((h xor (h shr 16)).toInt())
        }

        private fun hash3D(x: Int, y: Int, z: Int, seed: Long): Int {
            var h = x * 374761393L + y * 2147483647L + z * 668265263L + seed * 1442695041L
            h = (h xor (h shr 13)) * 1274126177L
            return abs((h xor (h shr 16)).toInt())
        }
    }
}
