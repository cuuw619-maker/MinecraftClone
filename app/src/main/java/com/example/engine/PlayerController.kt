package com.example.engine

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

data class HudSnapshot(
    val x: Int,
    val y: Int,
    val z: Int,
    val isFlying: Boolean,
    val targetBlockName: String?,
    val timeLabel: String = "День"
)

class PlayerController(
    initialX: Float = 8.5f,
    initialY: Float = 30f,
    initialZ: Float = 8.5f,
    initialYaw: Float = 45f,
    initialPitch: Float = -14f,
    initialFlying: Boolean = false
) {
    @Volatile var x: Float = initialX
    @Volatile var y: Float = initialY
    @Volatile var z: Float = initialZ
    @Volatile var yaw: Float = initialYaw
    @Volatile var pitch: Float = initialPitch
    @Volatile var isFlying: Boolean = initialFlying

    var vx: Float = 0f
    var vy: Float = 0f
    var vz: Float = 0f
    var onGround: Boolean = false

    @Volatile var moveForward: Float = 0f
    @Volatile var moveStrafe: Float = 0f
    @Volatile var jumpHeld: Boolean = false
    @Volatile var descendHeld: Boolean = false
    @Volatile var lookSensitivity: Float = 0.22f

    private var lastJumpTapMs: Long = 0L

    fun addLookDelta(dx: Float, dy: Float) {
        yaw = (yaw + dx * lookSensitivity) % 360f
        if (yaw < 0f) yaw += 360f
        pitch = (pitch - dy * lookSensitivity).coerceIn(-89f, 89f)
    }

    fun onJumpPressed() {
        val now = System.currentTimeMillis()
        if (now - lastJumpTapMs <= 280L) {
            toggleFly()
            lastJumpTapMs = 0L
        } else {
            lastJumpTapMs = now
        }
        jumpHeld = true
    }

    fun onJumpReleased() {
        jumpHeld = false
    }

    fun toggleFly() {
        isFlying = !isFlying
        if (isFlying && vy < 0f) {
            vy = 0f
        }
    }

    fun eyeX(): Float = x
    fun eyeY(): Float = y + EYE_HEIGHT
    fun eyeZ(): Float = z

    fun lookVector(): Triple<Float, Float, Float> {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val cosP = cos(pitchRad)
        val lx = (sin(yawRad) * cosP).toFloat()
        val ly = sin(pitchRad).toFloat()
        val lz = (-cos(yawRad) * cosP).toFloat()
        return Triple(lx, ly, lz)
    }

    fun intersectsBlock(bx: Int, by: Int, bz: Int): Boolean {
        val minX = x - HALF_WIDTH
        val maxX = x + HALF_WIDTH
        val minY = y
        val maxY = y + PLAYER_HEIGHT
        val minZ = z - HALF_WIDTH
        val maxZ = z + HALF_WIDTH
        return maxX > bx + EPS && minX < bx + 1f - EPS &&
            maxY > by + EPS && minY < by + 1f - EPS &&
            maxZ > bz + EPS && minZ < bz + 1f - EPS
    }

    fun update(dtRaw: Float, world: VoxelWorld) {
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        val yawRad = Math.toRadians(yaw.toDouble())
        val fwdX = sin(yawRad).toFloat()
        val fwdZ = -cos(yawRad).toFloat()
        val rightX = cos(yawRad).toFloat()
        val rightZ = sin(yawRad).toFloat()

        var wishX = fwdX * moveForward + rightX * moveStrafe
        var wishZ = fwdZ * moveForward + rightZ * moveStrafe
        val len = sqrt(wishX * wishX + wishZ * wishZ)
        if (len > 1f) {
            wishX /= len
            wishZ /= len
        }

        val inWater = world.getBlock(floor(x).toInt(), floor(y + 0.5f).toInt(), floor(z).toInt()) == BlockType.WATER
        val speed = when {
            isFlying -> FLY_SPEED
            inWater -> WALK_SPEED * 0.65f
            else -> WALK_SPEED
        }
        vx = wishX * speed
        vz = wishZ * speed

        if (isFlying) {
            var targetVy = 0f
            if (jumpHeld) targetVy += FLY_VERTICAL_SPEED
            if (descendHeld) targetVy -= FLY_VERTICAL_SPEED
            vy += (targetVy - vy) * (dt * 12f).coerceAtMost(1f)
        } else if (inWater) {
            vy = if (jumpHeld) 3.4f else (vy - 6.5f * dt).coerceAtLeast(-2.6f)
        } else {
            if (jumpHeld && onGround) {
                vy = JUMP_VELOCITY
                onGround = false
            }
            vy = (vy - GRAVITY * dt).coerceAtLeast(-MAX_FALL_SPEED)
        }

        onGround = false
        val newY = y + vy * dt
        val collidedY = resolveAxisY(newY, world)
        if (collidedY) {
            if (vy < 0f && isFlying) {
                vy = 0f
            } else {
                if (vy < 0f) onGround = true
                vy = 0f
            }
        }

        resolveAxisX(x + vx * dt, world)
        resolveAxisZ(z + vz * dt, world)

        if (y < 1f) {
            y = 1f
            vy = 0f
            onGround = true
        }
    }

    private fun resolveAxisY(targetY: Float, world: VoxelWorld): Boolean {
        val minX = floor(x - HALF_WIDTH + EPS).toInt()
        val maxX = floor(x + HALF_WIDTH - EPS).toInt()
        val minZ = floor(z - HALF_WIDTH + EPS).toInt()
        val maxZ = floor(z + HALF_WIDTH - EPS).toInt()

        if (targetY < y) {
            val checkY = floor(targetY).toInt()
            for (bx in minX..maxX) {
                for (bz in minZ..maxZ) {
                    if (world.getBlock(bx, checkY, bz).isSolid) {
                        y = checkY + 1f
                        return true
                    }
                }
            }
        } else if (targetY > y) {
            val checkY = floor(targetY + PLAYER_HEIGHT).toInt()
            for (bx in minX..maxX) {
                for (bz in minZ..maxZ) {
                    if (world.getBlock(bx, checkY, bz).isSolid) {
                        y = checkY - PLAYER_HEIGHT - EPS
                        return true
                    }
                }
            }
        }
        y = targetY
        return false
    }

    private fun resolveAxisX(targetX: Float, world: VoxelWorld) {
        val minY = floor(y + EPS).toInt()
        val maxY = floor(y + PLAYER_HEIGHT - EPS).toInt()
        val minZ = floor(z - HALF_WIDTH + EPS).toInt()
        val maxZ = floor(z + HALF_WIDTH - EPS).toInt()

        if (targetX > x) {
            val checkX = floor(targetX + HALF_WIDTH).toInt()
            for (by in minY..maxY) {
                for (bz in minZ..maxZ) {
                    if (world.getBlock(checkX, by, bz).isSolid) {
                        x = checkX - HALF_WIDTH - EPS
                        return
                    }
                }
            }
        } else if (targetX < x) {
            val checkX = floor(targetX - HALF_WIDTH).toInt()
            for (by in minY..maxY) {
                for (bz in minZ..maxZ) {
                    if (world.getBlock(checkX, by, bz).isSolid) {
                        x = checkX + 1f + HALF_WIDTH + EPS
                        return
                    }
                }
            }
        }
        x = targetX
    }

    private fun resolveAxisZ(targetZ: Float, world: VoxelWorld) {
        val minY = floor(y + EPS).toInt()
        val maxY = floor(y + PLAYER_HEIGHT - EPS).toInt()
        val minX = floor(x - HALF_WIDTH + EPS).toInt()
        val maxX = floor(x + HALF_WIDTH - EPS).toInt()

        if (targetZ > z) {
            val checkZ = floor(targetZ + HALF_WIDTH).toInt()
            for (by in minY..maxY) {
                for (bx in minX..maxX) {
                    if (world.getBlock(bx, by, checkZ).isSolid) {
                        z = checkZ - HALF_WIDTH - EPS
                        return
                    }
                }
            }
        } else if (targetZ < z) {
            val checkZ = floor(targetZ - HALF_WIDTH).toInt()
            for (by in minY..maxY) {
                for (bx in minX..maxX) {
                    if (world.getBlock(bx, by, checkZ).isSolid) {
                        z = checkZ + 1f + HALF_WIDTH + EPS
                        return
                    }
                }
            }
        }
        z = targetZ
    }

    companion object {
        private const val HALF_WIDTH = 0.28f
        private const val PLAYER_HEIGHT = 1.78f
        private const val EYE_HEIGHT = 1.62f
        private const val WALK_SPEED = 5.0f
        private const val FLY_SPEED = 10.0f
        private const val FLY_VERTICAL_SPEED = 6.8f
        private const val GRAVITY = 24f
        private const val JUMP_VELOCITY = 8.4f
        private const val MAX_FALL_SPEED = 28f
        private const val EPS = 0.002f
    }
}
