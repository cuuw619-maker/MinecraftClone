package com.example.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.floor
import kotlin.random.Random

class ParticleSystem {

    private data class Particle(
        var x: Float,
        var y: Float,
        var z: Float,
        var vx: Float,
        var vy: Float,
        var vz: Float,
        var size: Float,
        var age: Float,
        var maxAge: Float,
        var u0: Float,
        var v0: Float,
        var u1: Float,
        var v1: Float,
        var light: Float
    )

    private data class SpawnRequest(
        val wx: Int,
        val wy: Int,
        val wz: Int,
        val tileIndex: Int,
        val count: Int,
        val isPlacePuff: Boolean
    )

    private val pendingSpawns = ConcurrentLinkedQueue<SpawnRequest>()
    private val particles = ArrayList<Particle>(MAX_PARTICLES)
    private val vertexData = FloatArray(MAX_PARTICLES * 36 * ChunkMeshBuilder.FLOATS_PER_VERTEX)

    val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(vertexData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    var activeVertexCount: Int = 0
        private set

    fun queueBreakBurst(wx: Int, wy: Int, wz: Int, blockType: BlockType) {
        val tile = if (blockType.sideTile >= 0) blockType.sideTile else 2
        pendingSpawns.add(SpawnRequest(wx, wy, wz, tile, count = 18, isPlacePuff = false))
    }

    fun queuePlaceBurst(wx: Int, wy: Int, wz: Int, blockType: BlockType) {
        val tile = if (blockType.topTile >= 0) blockType.topTile else 2
        pendingSpawns.add(SpawnRequest(wx, wy, wz, tile, count = 10, isPlacePuff = true))
    }

    fun updateAndBuildBuffer(dtRaw: Float, world: VoxelWorld) {
        val dt = dtRaw.coerceIn(0.001f, 0.05f)

        while (true) {
            val req = pendingSpawns.poll() ?: break
            spawnFromRequest(req)
        }

        var idx = 0
        while (idx < particles.size) {
            val p = particles[idx]
            p.age += dt
            if (p.age >= p.maxAge) {
                particles.removeAt(idx)
                continue
            }

            p.vy -= 18f * dt
            val nextX = p.x + p.vx * dt
            val nextY = p.y + p.vy * dt
            val nextZ = p.z + p.vz * dt

            if (world.getBlock(floor(nextX).toInt(), floor(nextY).toInt(), floor(nextZ).toInt()).isSolid) {
                p.vy = -p.vy * 0.25f
                p.vx *= 0.65f
                p.vz *= 0.65f
            } else {
                p.x = nextX
                p.y = nextY
                p.z = nextZ
            }
            idx++
        }

        if (particles.isEmpty()) {
            activeVertexCount = 0
            return
        }

        var floatPos = 0
        for (p in particles) {
            val lifeRatio = 1f - (p.age / p.maxAge)
            val half = p.size * (0.45f + 0.55f * lifeRatio) * 0.5f
            floatPos = emitParticleCube(floatPos, p, half)
        }

        activeVertexCount = floatPos / ChunkMeshBuilder.FLOATS_PER_VERTEX
        vertexBuffer.clear()
        vertexBuffer.put(vertexData, 0, floatPos)
        vertexBuffer.position(0)
    }

    private fun spawnFromRequest(req: SpawnRequest) {
        val tileU0 = (req.tileIndex % TextureAtlasProvider.ATLAS_GRID).toFloat() / TextureAtlasProvider.ATLAS_GRID
        val tileV0 = (req.tileIndex / TextureAtlasProvider.ATLAS_GRID).toFloat() / TextureAtlasProvider.ATLAS_GRID
        val tileSpan = 1f / TextureAtlasProvider.ATLAS_GRID
        val subSpan = tileSpan * 0.28f

        repeat(req.count) {
            if (particles.size >= MAX_PARTICLES) {
                particles.removeAt(0)
            }
            val offU = Random.nextFloat() * (tileSpan - subSpan)
            val offV = Random.nextFloat() * (tileSpan - subSpan)

            val px = req.wx + 0.15f + Random.nextFloat() * 0.7f
            val py = req.wy + 0.15f + Random.nextFloat() * 0.7f
            val pz = req.wz + 0.15f + Random.nextFloat() * 0.7f

            val speedScale = if (req.isPlacePuff) 1.5f else 2.8f
            val vx = (Random.nextFloat() - 0.5f) * speedScale
            val vy = (if (req.isPlacePuff) 1.2f else 2.4f) + Random.nextFloat() * 2.2f
            val vz = (Random.nextFloat() - 0.5f) * speedScale

            particles.add(
                Particle(
                    x = px,
                    y = py,
                    z = pz,
                    vx = vx,
                    vy = vy,
                    vz = vz,
                    size = if (req.isPlacePuff) 0.10f else (0.12f + Random.nextFloat() * 0.08f),
                    age = 0f,
                    maxAge = if (req.isPlacePuff) 0.35f else (0.45f + Random.nextFloat() * 0.25f),
                    u0 = tileU0 + offU,
                    v0 = tileV0 + offV,
                    u1 = tileU0 + offU + subSpan,
                    v1 = tileV0 + offV + subSpan,
                    light = if (req.isPlacePuff) 1.1f else 0.95f
                )
            )
        }
    }

    private fun emitParticleCube(startIdx: Int, p: Particle, h: Float): Int {
        var i = startIdx
        val x0 = p.x - h; val x1 = p.x + h
        val y0 = p.y - h; val y1 = p.y + h
        val z0 = p.z - h; val z1 = p.z + h
        val u0 = p.u0; val v0 = p.v0; val u1 = p.u1; val v1 = p.v1

        fun v(x: Float, y: Float, z: Float, u: Float, v: Float, l: Float) {
            vertexData[i++] = x
            vertexData[i++] = y
            vertexData[i++] = z
            vertexData[i++] = u
            vertexData[i++] = v
            vertexData[i++] = l
        }

        // Top
        val lt = p.light
        v(x0, y1, z0, u0, v0, lt); v(x0, y1, z1, u0, v1, lt); v(x1, y1, z1, u1, v1, lt)
        v(x0, y1, z0, u0, v0, lt); v(x1, y1, z1, u1, v1, lt); v(x1, y1, z0, u1, v0, lt)
        // Bottom
        val lb = p.light * 0.65f
        v(x0, y0, z1, u0, v0, lb); v(x0, y0, z0, u0, v1, lb); v(x1, y0, z0, u1, v1, lb)
        v(x0, y0, z1, u0, v0, lb); v(x1, y0, z0, u1, v1, lb); v(x1, y0, z1, u1, v0, lb)
        // South
        val ls = p.light * 0.88f
        v(x0, y1, z1, u0, v0, ls); v(x0, y0, z1, u0, v1, ls); v(x1, y0, z1, u1, v1, ls)
        v(x0, y1, z1, u0, v0, ls); v(x1, y0, z1, u1, v1, ls); v(x1, y1, z1, u1, v0, ls)
        // North
        v(x1, y1, z0, u0, v0, ls); v(x1, y0, z0, u0, v1, ls); v(x0, y0, z0, u1, v1, ls)
        v(x1, y1, z0, u0, v0, ls); v(x0, y0, z0, u1, v1, ls); v(x0, y1, z0, u1, v0, ls)
        // East
        val le = p.light * 0.78f
        v(x1, y1, z1, u0, v0, le); v(x1, y0, z1, u0, v1, le); v(x1, y0, z0, u1, v1, le)
        v(x1, y1, z1, u0, v0, le); v(x1, y0, z0, u1, v1, le); v(x1, y1, z0, u1, v0, le)
        // West
        v(x0, y1, z0, u0, v0, le); v(x0, y0, z0, u0, v1, le); v(x0, y0, z1, u1, v1, le)
        v(x0, y1, z0, u0, v0, le); v(x0, y0, z1, u1, v1, le); v(x0, y1, z1, u1, v0, le)

        return i
    }

    companion object {
        private const val MAX_PARTICLES = 240
    }
}
