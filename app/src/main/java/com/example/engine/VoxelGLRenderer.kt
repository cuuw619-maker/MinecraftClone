package com.example.engine

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

enum class TimeOfDayMode(val displayName: String) {
    AUTO("День / Ночь"),
    DAY("День"),
    SUNSET("Закат"),
    NIGHT("Ночь")
}

class VoxelGLRenderer(
    private val world: VoxelWorld,
    private val player: PlayerController,
    val particles: ParticleSystem,
    private val isPanoramaMode: Boolean = false,
    private val onHudUpdate: (HudSnapshot) -> Unit = {}
) : GLSurfaceView.Renderer {

    @Volatile var renderRadiusChunks: Int = 3
    @Volatile var isPaused: Boolean = false
    @Volatile var timeOfDayMode: TimeOfDayMode = TimeOfDayMode.AUTO
    @Volatile var latestRaycastHit: RaycastHit? = null
        private set

    private class ChunkGpuBuffer(
        var opaqueVboId: Int,
        var opaqueVertexCount: Int,
        var waterVboId: Int,
        var waterVertexCount: Int
    )

    private val gpuChunks = ConcurrentHashMap<Long, ChunkGpuBuffer>()
    private val mvpMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    private var worldProgram = 0
    private var colorProgram = 0
    private var atlasTextureId = 0
    private var uploadedAtlasVersion = -1

    // World shader locations
    private var uMvpLoc = 0
    private var uCamPosLoc = 0
    private var uFogStartLoc = 0
    private var uFogEndLoc = 0
    private var uSkyColorLoc = 0
    private var uAmbientLightLoc = 0
    private var uAtlasLoc = 0
    private var aPosLoc = 0
    private var aUvLoc = 0
    private var aLightLoc = 0

    // Unlit color shader (sky sun/moon/clouds + wireframe outline)
    private var uColorMvpLoc = 0
    private var uSolidColorLoc = 0
    private var aColorPosLoc = 0

    private val selectionLineBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(24 * 3 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private val quadBoxBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(36 * 3 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private var lastFrameNs = System.nanoTime()
    private var lastHudReportMs = 0L
    private var dayTimeSeconds = 18f // Start in bright morning/noon
    private var cloudDrift = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        worldProgram = createProgram(WORLD_VERTEX_SHADER, WORLD_FRAGMENT_SHADER)
        uMvpLoc = GLES20.glGetUniformLocation(worldProgram, "uMVP")
        uCamPosLoc = GLES20.glGetUniformLocation(worldProgram, "uCamPos")
        uFogStartLoc = GLES20.glGetUniformLocation(worldProgram, "uFogStart")
        uFogEndLoc = GLES20.glGetUniformLocation(worldProgram, "uFogEnd")
        uSkyColorLoc = GLES20.glGetUniformLocation(worldProgram, "uSkyColor")
        uAmbientLightLoc = GLES20.glGetUniformLocation(worldProgram, "uAmbientLight")
        uAtlasLoc = GLES20.glGetUniformLocation(worldProgram, "uAtlas")
        aPosLoc = GLES20.glGetAttribLocation(worldProgram, "aPos")
        aUvLoc = GLES20.glGetAttribLocation(worldProgram, "aTexCoord")
        aLightLoc = GLES20.glGetAttribLocation(worldProgram, "aLight")

        colorProgram = createProgram(SOLID_VERTEX_SHADER, SOLID_FRAGMENT_SHADER)
        uColorMvpLoc = GLES20.glGetUniformLocation(colorProgram, "uMVP")
        uSolidColorLoc = GLES20.glGetUniformLocation(colorProgram, "uColor")
        aColorPosLoc = GLES20.glGetAttribLocation(colorProgram, "aPos")

        uploadAtlasTexture()
        gpuChunks.clear()
        world.getLoadedChunkEntries().forEach { it.value.isDirty = true }
        lastFrameNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projMatrix, 0, 72f, ratio, 0.1f, 180f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = System.nanoTime()
        val dt = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = nowNs

        // Check if user imported or switched texture pack
        val currentAtlasVer = TextureAtlasProvider.atlasVersion.value
        if (currentAtlasVer != uploadedAtlasVersion) {
            uploadAtlasTexture()
        }

        if (isPanoramaMode) {
            // Smooth 360-degree scenic orbit for main menu panorama
            val orbitSpeed = 7.5f
            player.yaw = (player.yaw + dt * orbitSpeed) % 360f
            val rad = Math.toRadians(player.yaw.toDouble())
            player.x = 8.5f + sin(rad).toFloat() * 10f
            player.z = 8.5f - cos(rad).toFloat() * 10f
            val groundBelow = world.getSurfaceHeight(floor(player.x).toInt(), floor(player.z).toInt())
            val targetY = maxOf(34f, groundBelow + 10f) + sin(rad * 2.0).toFloat() * 1.2f
            player.y += (targetY - player.y) * (dt * 2.5f).coerceAtMost(1f)
            player.pitch = -15f
            cloudDrift = (cloudDrift + dt * 1.6f) % 128f
            dayTimeSeconds = 22f // Golden late-morning light for crisp panorama
        } else if (!isPaused) {
            player.update(dt, world)
            particles.updateAndBuildBuffer(dt, world)
            cloudDrift = (cloudDrift + dt * 1.4f) % 128f
            if (timeOfDayMode == TimeOfDayMode.AUTO) {
                dayTimeSeconds = (dayTimeSeconds + dt) % DAY_CYCLE_SECONDS
            }
        }

        val radius = renderRadiusChunks.coerceIn(2, 5)
        val playerCx = VoxelWorld.floorDiv(floor(player.x).toInt(), Chunk.CHUNK_SIZE)
        val playerCz = VoxelWorld.floorDiv(floor(player.z).toInt(), Chunk.CHUNK_SIZE)

        for (dz in -radius..radius) {
            for (dx in -radius..radius) {
                world.getOrCreateChunk(playerCx + dx, playerCz + dz)
            }
        }

        val removed = world.pruneFarChunks(playerCx, playerCz, radius + 1)
        for (key in removed) {
            gpuChunks.remove(key)?.let { buf ->
                GLES20.glDeleteBuffers(2, intArrayOf(buf.opaqueVboId, buf.waterVboId), 0)
            }
        }

        val dirtyEntries = world.getLoadedChunkEntries()
            .filter { it.value.isDirty }
            .sortedBy {
                val dcX = it.value.cx - playerCx
                val dcZ = it.value.cz - playerCz
                dcX * dcX + dcZ * dcZ
            }
            .take(3)

        for ((key, chunk) in dirtyEntries) {
            val mesh = ChunkMeshBuilder.buildMesh(chunk, world)
            chunk.isDirty = false
            uploadChunkMesh(key, mesh)
        }

        val ex = player.eyeX()
        val ey = player.eyeY()
        val ez = player.eyeZ()
        val (lx, ly, lz) = player.lookVector()

        Matrix.setLookAtM(
            viewMatrix, 0,
            ex, ey, ez,
            ex + lx, ey + ly, ez + lz,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        val hit = if (isPanoramaMode) null else world.raycast(ex, ey, ez, lx, ly, lz, 6.5f)
        latestRaycastHit = hit

        // Compute sky color & sun angle
        val phase = when (timeOfDayMode) {
            TimeOfDayMode.AUTO -> (dayTimeSeconds / DAY_CYCLE_SECONDS) * (2f * PI.toFloat())
            TimeOfDayMode.DAY -> PI.toFloat() * 0.5f
            TimeOfDayMode.SUNSET -> PI.toFloat() * 0.96f
            TimeOfDayMode.NIGHT -> PI.toFloat() * 1.5f
        }
        val sunHeight = sin(phase) // +1 noon, 0 horizon, -1 midnight
        val dayFactor = ((sunHeight + 0.25f) / 0.65f).coerceIn(0f, 1f)
        val sunsetFactor = (1f - kotlin.math.abs(sunHeight) * 3.5f).coerceIn(0f, 1f)

        val skyR = lerp(0.06f, 0.54f, dayFactor) + sunsetFactor * 0.34f
        val skyG = lerp(0.09f, 0.77f, dayFactor) + sunsetFactor * 0.12f
        val skyB = lerp(0.18f, 0.96f, dayFactor) - sunsetFactor * 0.16f
        val ambientLight = lerp(0.36f, 1.0f, dayFactor)

        val timeLabel = when {
            sunHeight > 0.22f -> "День"
            sunHeight < -0.18f -> "Ночь"
            else -> "Закат"
        }

        val nowMs = System.currentTimeMillis()
        if (!isPanoramaMode && nowMs - lastHudReportMs >= 160L) {
            lastHudReportMs = nowMs
            onHudUpdate(
                HudSnapshot(
                    x = floor(player.x).toInt(),
                    y = floor(player.y).toInt(),
                    z = floor(player.z).toInt(),
                    isFlying = player.isFlying,
                    targetBlockName = hit?.blockType?.displayName,
                    timeLabel = timeLabel
                )
            )
        }

        GLES20.glClearColor(skyR.coerceIn(0f, 1f), skyG.coerceIn(0f, 1f), skyB.coerceIn(0f, 1f), 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Pass 1: Sun, Moon, and Drifting Clouds
        drawSkyObjects(ex, ey, ez, phase, dayFactor, skyR, skyG, skyB)

        // Pass 2: Opaque / Cutout World Geometry
        GLES20.glUseProgram(worldProgram)
        GLES20.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform3f(uCamPosLoc, ex, ey, ez)
        val maxRenderDist = radius * Chunk.CHUNK_SIZE.toFloat()
        GLES20.glUniform1f(uFogStartLoc, (maxRenderDist * 0.56f).coerceAtLeast(18f))
        GLES20.glUniform1f(uFogEndLoc, (maxRenderDist * 0.98f).coerceAtLeast(32f))
        GLES20.glUniform3f(uSkyColorLoc, skyR.coerceIn(0f, 1f), skyG.coerceIn(0f, 1f), skyB.coerceIn(0f, 1f))
        GLES20.glUniform1f(uAmbientLightLoc, ambientLight)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTextureId)
        GLES20.glUniform1i(uAtlasLoc, 0)

        val strideBytes = ChunkMeshBuilder.FLOATS_PER_VERTEX * 4
        GLES20.glEnableVertexAttribArray(aPosLoc)
        GLES20.glEnableVertexAttribArray(aUvLoc)
        GLES20.glEnableVertexAttribArray(aLightLoc)

        for (gpu in gpuChunks.values) {
            if (gpu.opaqueVertexCount == 0) continue
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gpu.opaqueVboId)
            GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, strideBytes, 0)
            GLES20.glVertexAttribPointer(aUvLoc, 2, GLES20.GL_FLOAT, false, strideBytes, 3 * 4)
            GLES20.glVertexAttribPointer(aLightLoc, 1, GLES20.GL_FLOAT, false, strideBytes, 5 * 4)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, gpu.opaqueVertexCount)
        }

        // Pass 3: 3D Block Break/Place Particles
        val pVerts = particles.activeVertexCount
        if (pVerts > 0) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            val pb = particles.vertexBuffer
            pb.position(0)
            GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, strideBytes, pb)
            pb.position(3)
            GLES20.glVertexAttribPointer(aUvLoc, 2, GLES20.GL_FLOAT, false, strideBytes, pb)
            pb.position(5)
            GLES20.glVertexAttribPointer(aLightLoc, 1, GLES20.GL_FLOAT, false, strideBytes, pb)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, pVerts)
        }

        // Pass 4: Translucent Water Meshes
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        for (gpu in gpuChunks.values) {
            if (gpu.waterVertexCount == 0) continue
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gpu.waterVboId)
            GLES20.glVertexAttribPointer(aPosLoc, 3, GLES20.GL_FLOAT, false, strideBytes, 0)
            GLES20.glVertexAttribPointer(aUvLoc, 2, GLES20.GL_FLOAT, false, strideBytes, 3 * 4)
            GLES20.glVertexAttribPointer(aLightLoc, 1, GLES20.GL_FLOAT, false, strideBytes, 5 * 4)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, gpu.waterVertexCount)
        }

        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glDisableVertexAttribArray(aPosLoc)
        GLES20.glDisableVertexAttribArray(aUvLoc)
        GLES20.glDisableVertexAttribArray(aLightLoc)

        // Pass 5: Targeted Block Wireframe
        if (hit != null) {
            drawSelectionOutline(hit.blockPos)
        }
    }

    private fun drawSkyObjects(
        ex: Float,
        ey: Float,
        ez: Float,
        phase: Float,
        dayFactor: Float,
        skyR: Float,
        skyG: Float,
        skyB: Float
    ) {
        GLES20.glUseProgram(colorProgram)
        GLES20.glUniformMatrix4fv(uColorMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glEnableVertexAttribArray(aColorPosLoc)

        // Draw Sun & Moon without depth writes so they sit behind terrain
        GLES20.glDepthMask(false)
        val orbitDist = 95f
        val sunX = ex + cos(phase) * orbitDist
        val sunY = ey + sin(phase) * orbitDist
        val sunZ = ez - 24f
        drawSolidBox(sunX, sunY, sunZ, 6.5f, 4.5f, 6.5f, 1.0f, 0.94f, 0.56f, 1.0f)

        val moonX = ex - cos(phase) * orbitDist
        val moonY = ey - sin(phase) * orbitDist
        val moonZ = ez + 24f
        drawSolidBox(moonX, moonY, moonZ, 5.2f, 3.8f, 5.2f, 0.85f, 0.90f, 1.0f, 1.0f)
        GLES20.glDepthMask(true)

        // Drifting 3D clouds at Y=55
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val cloudBrightness = lerp(0.32f, 0.98f, dayFactor)
        val cr = (cloudBrightness * 0.85f + skyR * 0.15f).coerceIn(0f, 1f)
        val cg = (cloudBrightness * 0.88f + skyG * 0.12f).coerceIn(0f, 1f)
        val cb = (cloudBrightness * 0.92f + skyB * 0.08f).coerceIn(0f, 1f)

        val gridCenterX = floor(ex / 24f).toInt()
        val gridCenterZ = floor(ez / 24f).toInt()
        for (gx in (gridCenterX - 3)..(gridCenterX + 3)) {
            for (gz in (gridCenterZ - 3)..(gridCenterZ + 3)) {
                val hash = ((gx * 73856093) xor (gz * 19349663)) and 7
                if (hash < 4) {
                    val cx = gx * 24f + cloudDrift - 64f
                    val wrappedCx = ex + (((cx - ex + 96f) % 192f + 192f) % 192f - 96f)
                    val cz = gz * 24f + ((hash - 2) * 3f)
                    drawSolidBox(wrappedCx, 55f, cz, 12f + hash * 2f, 1.6f, 7f + hash * 1.5f, cr, cg, cb, 0.82f)
                }
            }
        }

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisableVertexAttribArray(aColorPosLoc)
    }

    private fun drawSolidBox(
        cx: Float,
        cy: Float,
        cz: Float,
        sx: Float,
        sy: Float,
        sz: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        val x0 = cx - sx * 0.5f; val x1 = cx + sx * 0.5f
        val y0 = cy - sy * 0.5f; val y1 = cy + sy * 0.5f
        val z0 = cz - sz * 0.5f; val z1 = cz + sz * 0.5f

        val verts = floatArrayOf(
            // Top
            x0, y1, z0, x0, y1, z1, x1, y1, z1, x0, y1, z0, x1, y1, z1, x1, y1, z0,
            // Bottom
            x0, y0, z1, x0, y0, z0, x1, y0, z0, x0, y0, z1, x1, y0, z0, x1, y0, z1,
            // South
            x0, y1, z1, x0, y0, z1, x1, y0, z1, x0, y1, z1, x1, y0, z1, x1, y1, z1,
            // North
            x1, y1, z0, x1, y0, z0, x0, y0, z0, x1, y1, z0, x0, y0, z0, x0, y1, z0,
            // East
            x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z1, x1, y0, z0, x1, y1, z0,
            // West
            x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z0, x0, y0, z1, x0, y1, z1
        )
        quadBoxBuffer.clear()
        quadBoxBuffer.put(verts)
        quadBoxBuffer.position(0)

        GLES20.glUniform4f(uSolidColorLoc, r, g, b, a)
        GLES20.glVertexAttribPointer(aColorPosLoc, 3, GLES20.GL_FLOAT, false, 0, quadBoxBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36)
    }

    private fun uploadChunkMesh(key: Long, mesh: ChunkMeshResult) {
        val existing = gpuChunks[key]
        val opaqueVbo: Int
        val waterVbo: Int
        if (existing != null) {
            opaqueVbo = existing.opaqueVboId
            waterVbo = existing.waterVboId
        } else {
            val ids = IntArray(2)
            GLES20.glGenBuffers(2, ids, 0)
            opaqueVbo = ids[0]
            waterVbo = ids[1]
        }

        val opaqueCount = uploadVboData(opaqueVbo, mesh.opaqueVertices)
        val waterCount = uploadVboData(waterVbo, mesh.waterVertices)

        if (existing != null) {
            existing.opaqueVertexCount = opaqueCount
            existing.waterVertexCount = waterCount
        } else {
            gpuChunks[key] = ChunkGpuBuffer(opaqueVbo, opaqueCount, waterVbo, waterCount)
        }
    }

    private fun uploadVboData(vboId: Int, vertices: FloatArray): Int {
        val count = vertices.size / ChunkMeshBuilder.FLOATS_PER_VERTEX
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        if (vertices.isNotEmpty()) {
            val buf = ByteBuffer
                .allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertices)
            buf.position(0)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, buf, GLES20.GL_STATIC_DRAW)
        } else {
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 0, null, GLES20.GL_STATIC_DRAW)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return count
    }

    private fun drawSelectionOutline(pos: BlockPos) {
        val pad = 0.005f
        val x0 = pos.x - pad; val y0 = pos.y - pad; val z0 = pos.z - pad
        val x1 = pos.x + 1f + pad; val y1 = pos.y + 1f + pad; val z1 = pos.z + 1f + pad

        val lines = floatArrayOf(
            x0, y0, z0, x1, y0, z0, x1, y0, z0, x1, y0, z1,
            x1, y0, z1, x0, y0, z1, x0, y0, z1, x0, y0, z0,
            x0, y1, z0, x1, y1, z0, x1, y1, z0, x1, y1, z1,
            x1, y1, z1, x0, y1, z1, x0, y1, z1, x0, y1, z0,
            x0, y0, z0, x0, y1, z0, x1, y0, z0, x1, y1, z0,
            x1, y0, z1, x1, y1, z1, x0, y0, z1, x0, y1, z1
        )
        selectionLineBuffer.clear()
        selectionLineBuffer.put(lines)
        selectionLineBuffer.position(0)

        GLES20.glUseProgram(colorProgram)
        GLES20.glUniformMatrix4fv(uColorMvpLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(uSolidColorLoc, 0.06f, 0.08f, 0.08f, 0.92f)
        GLES20.glLineWidth(3.5f)
        GLES20.glEnableVertexAttribArray(aColorPosLoc)
        GLES20.glVertexAttribPointer(aColorPosLoc, 3, GLES20.GL_FLOAT, false, 0, selectionLineBuffer)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 24)
        GLES20.glDisableVertexAttribArray(aColorPosLoc)
    }

    private fun uploadAtlasTexture() {
        if (atlasTextureId == 0) {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            atlasTextureId = ids[0]
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, TextureAtlasProvider.atlasBitmap, 0)
        uploadedAtlasVersion = TextureAtlasProvider.atlasVersion.value
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun createProgram(vsSource: String, fsSource: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSource)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSource)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    companion object {
        private const val DAY_CYCLE_SECONDS = 120f

        private const val WORLD_VERTEX_SHADER = """
            uniform mat4 uMVP;
            uniform vec3 uCamPos;
            attribute vec3 aPos;
            attribute vec2 aTexCoord;
            attribute float aLight;
            varying vec2 vTexCoord;
            varying float vLight;
            varying float vDist;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
                vTexCoord = aTexCoord;
                vLight = aLight;
                vDist = distance(uCamPos, aPos);
            }
        """

        private const val WORLD_FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uAtlas;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform vec3 uSkyColor;
            uniform float uAmbientLight;
            varying vec2 vTexCoord;
            varying float vLight;
            varying float vDist;
            void main() {
                vec4 tex = texture2D(uAtlas, vTexCoord);
                if (tex.a < 0.08) discard;
                float effectiveLight = (vLight > 1.5) ? 1.15 : min(vLight * uAmbientLight, 1.12);
                vec3 lit = tex.rgb * effectiveLight;
                float fogFactor = clamp((vDist - uFogStart) / max(uFogEnd - uFogStart, 1.0), 0.0, 1.0);
                vec3 finalColor = mix(lit, uSkyColor, fogFactor);
                gl_FragColor = vec4(finalColor, tex.a);
            }
        """

        private const val SOLID_VERTEX_SHADER = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            void main() {
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val SOLID_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """
    }
}
