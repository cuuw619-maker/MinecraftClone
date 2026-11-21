package com.example.engine

data class ChunkMeshResult(
    val opaqueVertices: FloatArray,
    val waterVertices: FloatArray
)

object ChunkMeshBuilder {

    // Vertex format: x, y, z, u, v, light (6 floats per vertex)
    const val FLOATS_PER_VERTEX = 6

    private class FloatAccumulator(initialCapacity: Int = 16384) {
        var data = FloatArray(initialCapacity)
        var size = 0

        fun addVertex(x: Float, y: Float, z: Float, u: Float, v: Float, light: Float) {
            if (size + FLOATS_PER_VERTEX > data.size) {
                data = data.copyOf(data.size * 2)
            }
            data[size++] = x
            data[size++] = y
            data[size++] = z
            data[size++] = u
            data[size++] = v
            data[size++] = light
        }

        fun toTightArray(): FloatArray = data.copyOf(size)
    }

    fun buildMesh(chunk: Chunk, world: VoxelWorld): ChunkMeshResult {
        val opaque = FloatAccumulator()
        val water = FloatAccumulator(2048)
        val baseX = chunk.cx * Chunk.CHUNK_SIZE
        val baseZ = chunk.cz * Chunk.CHUNK_SIZE

        for (y in 0 until Chunk.WORLD_HEIGHT) {
            for (lz in 0 until Chunk.CHUNK_SIZE) {
                val wz = baseZ + lz
                for (lx in 0 until Chunk.CHUNK_SIZE) {
                    val block = chunk.getBlock(lx, y, lz)
                    if (block == BlockType.AIR) continue

                    val wx = baseX + lx
                    val fx = wx.toFloat()
                    val fy = y.toFloat()
                    val fz = wz.toFloat()

                    if (block == BlockType.WATER) {
                        val topNeighbor = getNeighbor(chunk, world, lx, y + 1, lz, wx, y + 1, wz)
                        val waterTopY = if (topNeighbor == BlockType.WATER) fy + 1f else fy + 0.88f

                        if (topNeighbor != BlockType.WATER && !topNeighbor.isOpaque) {
                            emitTopFace(water, fx, waterTopY - 1f, fz, block.topTile, 1.0f)
                        }
                        if (y > 0) {
                            val bot = getNeighbor(chunk, world, lx, y - 1, lz, wx, y - 1, wz)
                            if (bot != BlockType.WATER && !bot.isOpaque) {
                                emitBottomFace(water, fx, fy, fz, block.bottomTile, 0.72f)
                            }
                        }
                        val south = getNeighbor(chunk, world, lx, y, lz + 1, wx, y, wz + 1)
                        if (south != BlockType.WATER && !south.isOpaque) {
                            emitSideSouth(water, fx, fy, waterTopY, fz, block.sideTile, 0.86f)
                        }
                        val north = getNeighbor(chunk, world, lx, y, lz - 1, wx, y, wz - 1)
                        if (north != BlockType.WATER && !north.isOpaque) {
                            emitSideNorth(water, fx, fy, waterTopY, fz, block.sideTile, 0.86f)
                        }
                        val east = getNeighbor(chunk, world, lx + 1, y, lz, wx + 1, y, wz)
                        if (east != BlockType.WATER && !east.isOpaque) {
                            emitSideEast(water, fx, fy, waterTopY, fz, block.sideTile, 0.78f)
                        }
                        val west = getNeighbor(chunk, world, lx - 1, y, lz, wx - 1, y, wz)
                        if (west != BlockType.WATER && !west.isOpaque) {
                            emitSideWest(water, fx, fy, waterTopY, fz, block.sideTile, 0.78f)
                        }
                        continue
                    }

                    // Glow lamp encodes a high light value (> 1.4) so shader keeps it emissive at night
                    val selfLitBoost = if (block == BlockType.GLOW_LAMP) 2.2f else 1.0f

                    if (shouldRenderOpaqueFace(block, getNeighbor(chunk, world, lx, y + 1, lz, wx, y + 1, wz))) {
                        emitTopFace(opaque, fx, fy, fz, block.topTile, 1.0f * selfLitBoost)
                    }
                    if (y > 0 && shouldRenderOpaqueFace(block, getNeighbor(chunk, world, lx, y - 1, lz, wx, y - 1, wz))) {
                        emitBottomFace(opaque, fx, fy, fz, block.bottomTile, 0.58f * selfLitBoost)
                    }
                    if (shouldRenderOpaqueFace(block, getNeighbor(chunk, world, lx, y, lz + 1, wx, y, wz + 1))) {
                        emitSideSouth(opaque, fx, fy, fy + 1f, fz, block.sideTile, 0.86f * selfLitBoost)
                    }
                    if (shouldRenderOpaqueFace(block, getNeighbor(chunk, world, lx, y, lz - 1, wx, y, wz - 1))) {
                        emitSideNorth(opaque, fx, fy, fy + 1f, fz, block.sideTile, 0.86f * selfLitBoost)
                    }
                    if (shouldRenderOpaqueFace(block, getNeighbor(chunk, world, lx + 1, y, lz, wx + 1, y, wz))) {
                        emitSideEast(opaque, fx, fy, fy + 1f, fz, block.sideTile, 0.74f * selfLitBoost)
                    }
                    if (shouldRenderOpaqueFace(block, getNeighbor(chunk, world, lx - 1, y, lz, wx - 1, y, wz))) {
                        emitSideWest(opaque, fx, fy, fy + 1f, fz, block.sideTile, 0.74f * selfLitBoost)
                    }
                }
            }
        }
        return ChunkMeshResult(opaque.toTightArray(), water.toTightArray())
    }

    private fun getNeighbor(
        chunk: Chunk,
        world: VoxelWorld,
        lx: Int,
        y: Int,
        lz: Int,
        wx: Int,
        wy: Int,
        wz: Int
    ): BlockType {
        if (y < 0 || y >= Chunk.WORLD_HEIGHT) return BlockType.AIR
        if (lx in 0 until Chunk.CHUNK_SIZE && lz in 0 until Chunk.CHUNK_SIZE) {
            return chunk.getBlock(lx, y, lz)
        }
        return world.getBlock(wx, wy, wz)
    }

    private fun shouldRenderOpaqueFace(current: BlockType, neighbor: BlockType): Boolean {
        if (neighbor == BlockType.AIR || neighbor.isTranslucent) return true
        if (current.isCutout && neighbor == current) return false
        return !neighbor.isOpaque
    }

    private fun uMin(tile: Int): Float = ((tile % TextureAtlasProvider.ATLAS_GRID).toFloat() / TextureAtlasProvider.ATLAS_GRID) + 0.0005f
    private fun uMax(tile: Int): Float = (((tile % TextureAtlasProvider.ATLAS_GRID) + 1).toFloat() / TextureAtlasProvider.ATLAS_GRID) - 0.0005f
    private fun vMin(tile: Int): Float = ((tile / TextureAtlasProvider.ATLAS_GRID).toFloat() / TextureAtlasProvider.ATLAS_GRID) + 0.0005f
    private fun vMax(tile: Int): Float = (((tile / TextureAtlasProvider.ATLAS_GRID) + 1).toFloat() / TextureAtlasProvider.ATLAS_GRID) - 0.0005f

    private fun emitTopFace(acc: FloatAccumulator, x: Float, y: Float, z: Float, tile: Int, l: Float) {
        val u0 = uMin(tile); val u1 = uMax(tile); val v0 = vMin(tile); val v1 = vMax(tile)
        val yp = y + 1f
        acc.addVertex(x, yp, z, u0, v0, l)
        acc.addVertex(x, yp, z + 1f, u0, v1, l)
        acc.addVertex(x + 1f, yp, z + 1f, u1, v1, l)

        acc.addVertex(x, yp, z, u0, v0, l)
        acc.addVertex(x + 1f, yp, z + 1f, u1, v1, l)
        acc.addVertex(x + 1f, yp, z, u1, v0, l)
    }

    private fun emitBottomFace(acc: FloatAccumulator, x: Float, y: Float, z: Float, tile: Int, l: Float) {
        val u0 = uMin(tile); val u1 = uMax(tile); val v0 = vMin(tile); val v1 = vMax(tile)
        acc.addVertex(x, y, z + 1f, u0, v0, l)
        acc.addVertex(x, y, z, u0, v1, l)
        acc.addVertex(x + 1f, y, z, u1, v1, l)

        acc.addVertex(x, y, z + 1f, u0, v0, l)
        acc.addVertex(x + 1f, y, z, u1, v1, l)
        acc.addVertex(x + 1f, y, z + 1f, u1, v0, l)
    }

    private fun emitSideSouth(acc: FloatAccumulator, x: Float, y0: Float, y1: Float, z: Float, tile: Int, l: Float) {
        val u0 = uMin(tile); val u1 = uMax(tile); val v0 = vMin(tile); val v1 = vMax(tile)
        val zp = z + 1f
        acc.addVertex(x, y1, zp, u0, v0, l)
        acc.addVertex(x, y0, zp, u0, v1, l)
        acc.addVertex(x + 1f, y0, zp, u1, v1, l)

        acc.addVertex(x, y1, zp, u0, v0, l)
        acc.addVertex(x + 1f, y0, zp, u1, v1, l)
        acc.addVertex(x + 1f, y1, zp, u1, v0, l)
    }

    private fun emitSideNorth(acc: FloatAccumulator, x: Float, y0: Float, y1: Float, z: Float, tile: Int, l: Float) {
        val u0 = uMin(tile); val u1 = uMax(tile); val v0 = vMin(tile); val v1 = vMax(tile)
        acc.addVertex(x + 1f, y1, z, u0, v0, l)
        acc.addVertex(x + 1f, y0, z, u0, v1, l)
        acc.addVertex(x, y0, z, u1, v1, l)

        acc.addVertex(x + 1f, y1, z, u0, v0, l)
        acc.addVertex(x, y0, z, u1, v1, l)
        acc.addVertex(x, y1, z, u1, v0, l)
    }

    private fun emitSideEast(acc: FloatAccumulator, x: Float, y0: Float, y1: Float, z: Float, tile: Int, l: Float) {
        val u0 = uMin(tile); val u1 = uMax(tile); val v0 = vMin(tile); val v1 = vMax(tile)
        val xp = x + 1f
        acc.addVertex(xp, y1, z + 1f, u0, v0, l)
        acc.addVertex(xp, y0, z + 1f, u0, v1, l)
        acc.addVertex(xp, y0, z, u1, v1, l)

        acc.addVertex(xp, y1, z + 1f, u0, v0, l)
        acc.addVertex(xp, y0, z, u1, v1, l)
        acc.addVertex(xp, y1, z, u1, v0, l)
    }

    private fun emitSideWest(acc: FloatAccumulator, x: Float, y0: Float, y1: Float, z: Float, tile: Int, l: Float) {
        val u0 = uMin(tile); val u1 = uMax(tile); val v0 = vMin(tile); val v1 = vMax(tile)
        acc.addVertex(x, y1, z, u0, v0, l)
        acc.addVertex(x, y0, z, u0, v1, l)
        acc.addVertex(x, y0, z + 1f, u1, v1, l)

        acc.addVertex(x, y1, z, u0, v0, l)
        acc.addVertex(x, y0, z + 1f, u1, v1, l)
        acc.addVertex(x, y1, z + 1f, u1, v0, l)
    }
}
