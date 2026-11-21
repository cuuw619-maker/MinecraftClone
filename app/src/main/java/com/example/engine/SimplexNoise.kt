package com.example.engine

import kotlin.math.floor

class SimplexNoise(seed: Long) {
    private val perm = IntArray(512)

    init {
        val base = IntArray(256) { it }
        var state = seed xor 0x5DEECE66DL
        for (i in 255 downTo 1) {
            state = state * 6364136223846793005L + 1442695040888963407L
            val j = ((state ushr 16).toInt() and 0x7FFFFFFF) % (i + 1)
            val tmp = base[i]
            base[i] = base[j]
            base[j] = tmp
        }
        for (i in 0 until 512) {
            perm[i] = base[i and 255]
        }
    }

    private fun fade(t: Float): Float = t * t * t * (t * (t * 6f - 15f) + 10f)

    private fun lerp(t: Float, a: Float, b: Float): Float = a + t * (b - a)

    private fun grad(hash: Int, x: Float, y: Float): Float {
        return when (hash and 7) {
            0 -> x + y
            1 -> -x + y
            2 -> x - y
            3 -> -x - y
            4 -> x
            5 -> -x
            6 -> y
            else -> -y
        }
    }

    private fun grad3(hash: Int, x: Float, y: Float, z: Float): Float {
        val h = hash and 15
        val u = if (h < 8) x else y
        val v = if (h < 4) y else if (h == 12 || h == 14) x else z
        return (if ((h and 1) == 0) u else -u) + (if ((h and 2) == 0) v else -v)
    }

    fun noise2D(x: Float, y: Float): Float {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255
        val xf = x - floor(x)
        val yf = y - floor(y)

        val u = fade(xf)
        val v = fade(yf)

        val aa = perm[perm[xi] + yi]
        val ab = perm[perm[xi] + yi + 1]
        val ba = perm[perm[xi + 1] + yi]
        val bb = perm[perm[xi + 1] + yi + 1]

        val x1 = lerp(u, grad(aa, xf, yf), grad(ba, xf - 1f, yf))
        val x2 = lerp(u, grad(ab, xf, yf - 1f), grad(bb, xf - 1f, yf - 1f))
        return lerp(v, x1, x2)
    }

    fun noise3D(x: Float, y: Float, z: Float): Float {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255
        val zi = floor(z).toInt() and 255
        val xf = x - floor(x)
        val yf = y - floor(y)
        val zf = z - floor(z)

        val u = fade(xf)
        val v = fade(yf)
        val w = fade(zf)

        val a = perm[xi] + yi
        val aa = perm[a] + zi
        val ab = perm[a + 1] + zi
        val b = perm[xi + 1] + yi
        val ba = perm[b] + zi
        val bb = perm[b + 1] + zi

        val x1 = lerp(u, grad3(perm[aa], xf, yf, zf), grad3(perm[ba], xf - 1f, yf, zf))
        val x2 = lerp(u, grad3(perm[ab], xf, yf - 1f, zf), grad3(perm[bb], xf - 1f, yf - 1f, zf))
        val y1 = lerp(v, x1, x2)

        val x3 = lerp(u, grad3(perm[aa + 1], xf, yf, zf - 1f), grad3(perm[ba + 1], xf - 1f, yf, zf - 1f))
        val x4 = lerp(u, grad3(perm[ab + 1], xf, yf - 1f, zf - 1f), grad3(perm[bb + 1], xf - 1f, yf - 1f, zf - 1f))
        val y2 = lerp(v, x3, x4)

        return lerp(w, y1, y2)
    }

    fun fbm2D(x: Float, y: Float, octaves: Int = 3, persistence: Float = 0.5f, scale: Float = 0.03f): Float {
        var total = 0f
        var frequency = scale
        var amplitude = 1f
        var maxValue = 0f
        for (i in 0 until octaves) {
            total += noise2D(x * frequency, y * frequency) * amplitude
            maxValue += amplitude
            amplitude *= persistence
            frequency *= 2f
        }
        return total / maxValue
    }
}
