package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.abs

data class TileDefinition(
    val index: Int,
    val label: String,
    val primaryName: String,
    val aliases: List<String>
)

/**
 * Builds and serves the active 16x16 grid texture atlas (512x512 px, 32x32 px per tile).
 * Supports both bundled structured asset packs (assets/resource_packs/<id>/...)
 * and user-unpacked ZIP resource packs (filesDir/resource_packs/<id>/...),
 * with automatic Minecraft grayscale biome-tinting and grass_block_side_overlay compositing.
 */
object TextureAtlasProvider {
    const val ATLAS_GRID = 16
    const val TILE_SIZE = 32
    const val ATLAS_PIXEL_SIZE = ATLAS_GRID * TILE_SIZE

    val tileDefinitions: List<TileDefinition> = listOf(
        TileDefinition(0, "Трава (верх)", "grass_top", listOf("grass_block_top", "grass_top")),
        TileDefinition(1, "Трава (бок)", "grass_side", listOf("grass_block_side", "grass_side")),
        TileDefinition(2, "Земля", "dirt", listOf("dirt")),
        TileDefinition(3, "Камень", "stone", listOf("stone")),
        TileDefinition(4, "Булыжник", "cobblestone", listOf("cobblestone")),
        TileDefinition(5, "Срез дерева", "oak_log_top", listOf("oak_log_top", "log_oak_top")),
        TileDefinition(6, "Кора дуба", "oak_log", listOf("oak_log", "log_oak")),
        TileDefinition(7, "Доски", "oak_planks", listOf("oak_planks", "planks_oak")),
        TileDefinition(8, "Листва дуба", "oak_leaves", listOf("oak_leaves", "leaves_oak")),
        TileDefinition(9, "Песок", "sand", listOf("sand")),
        TileDefinition(10, "Кирпич", "bricks", listOf("bricks", "brick")),
        TileDefinition(11, "Стекло", "glass", listOf("glass")),
        TileDefinition(12, "Светильник", "glowstone", listOf("glowstone", "redstone_lamp_on")),
        TileDefinition(13, "Кам. кирпич", "stone_bricks", listOf("stone_bricks", "stonebrick")),
        TileDefinition(14, "Изумруд", "emerald_block", listOf("emerald_block")),
        TileDefinition(15, "Бедрок", "bedrock", listOf("bedrock")),
        TileDefinition(16, "Вода", "water_still", listOf("water_still", "water")),
        TileDefinition(17, "Снег", "snow", listOf("snow", "snow_block")),
        TileDefinition(18, "Снег (бок)", "snow_grass_side", listOf("grass_block_snow", "grass_side_snowed", "snow_grass_side")),
        TileDefinition(19, "Кора ели", "spruce_log", listOf("spruce_log", "log_spruce")),
        TileDefinition(20, "Хвоя ели", "spruce_leaves", listOf("spruce_leaves", "leaves_spruce")),
        TileDefinition(21, "Уголь", "coal_ore", listOf("coal_ore")),
        TileDefinition(22, "Железо", "iron_ore", listOf("iron_ore")),
        TileDefinition(23, "Алмаз", "diamond_ore", listOf("diamond_ore"))
    )

    private val defaultTileCache = mutableMapOf<Int, Bitmap>()
    private val activeTileCache = mutableMapOf<Int, Bitmap>()

    @Volatile
    var atlasBitmap: Bitmap = Bitmap.createBitmap(ATLAS_PIXEL_SIZE, ATLAS_PIXEL_SIZE, Bitmap.Config.ARGB_8888)
        private set

    private val _atlasVersion = MutableStateFlow(0)
    val atlasVersion: StateFlow<Int> = _atlasVersion.asStateFlow()

    private val nearestPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }

    @Synchronized
    fun initialize(context: Context, activePackId: String, activePackDir: File?) {
        if (defaultTileCache.isEmpty()) {
            loadDefaultAssetTiles(context)
        }
        rebuildAtlas(context, activePackId, activePackDir)
    }

    @Synchronized
    fun rebuildAtlas(context: Context, activePackId: String, externalPackDir: File?) {
        val indexedExternal = if (externalPackDir != null && externalPackDir.exists()) {
            indexPngFiles(externalPackDir)
        } else {
            emptyMap()
        }
        val assetBlockDir = "resource_packs/$activePackId/assets/minecraft/textures/block"
        val assetFiles = if (externalPackDir == null && activePackId != TexturePackManager.DEFAULT_PACK_ID) {
            runCatching { context.assets.list(assetBlockDir)?.toSet().orEmpty() }.getOrDefault(emptySet())
        } else {
            emptySet()
        }

        val loader: (String) -> Bitmap? = { name ->
            when {
                indexedExternal.isNotEmpty() -> indexedExternal[name]?.let { decodeRawFile(it) }
                assetFiles.contains("$name.png") -> decodeRawAsset(context, "$assetBlockDir/$name.png")
                else -> null
            }
        }

        val newAtlas = Bitmap.createBitmap(ATLAS_PIXEL_SIZE, ATLAS_PIXEL_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(newAtlas)
        activeTileCache.clear()

        for (def in tileDefinitions) {
            val rawOverride = def.aliases.firstNotNullOfOrNull { alias -> loader(alias) }
            val tile = if (rawOverride != null) {
                processLoadedTile(def.index, rawOverride, loader)
            } else {
                defaultTileCache[def.index] ?: createFallbackTile()
            }

            activeTileCache[def.index] = tile
            val dx = (def.index % ATLAS_GRID) * TILE_SIZE
            val dy = (def.index / ATLAS_GRID) * TILE_SIZE
            canvas.drawBitmap(tile, null, Rect(dx, dy, dx + TILE_SIZE, dy + TILE_SIZE), nearestPaint)
        }

        atlasBitmap = newAtlas
        _atlasVersion.value = _atlasVersion.value + 1
    }

    private fun processLoadedTile(
        tileIndex: Int,
        rawBitmap: Bitmap,
        loader: (String) -> Bitmap?
    ): Bitmap {
        val scaled = scaleNearest(cropTopSquare(rawBitmap))
        return when (tileIndex) {
            0 -> { // grass_top
                if (isLowSaturation(scaled, maxChannelDiff = 18)) {
                    tintBitmap(scaled, 0.56f, 0.88f, 0.34f)
                } else scaled
            }
            1 -> { // grass_side + optional grass_block_side_overlay
                val overlayRaw = loader("grass_block_side_overlay")
                if (overlayRaw != null) {
                    val overlayScaled = scaleNearest(cropTopSquare(overlayRaw))
                    val tintedOverlay = if (isLowSaturation(overlayScaled, maxChannelDiff = 18)) {
                        tintBitmap(overlayScaled, 0.56f, 0.88f, 0.34f)
                    } else overlayScaled
                    val combined = scaled.copy(Bitmap.Config.ARGB_8888, true)
                    Canvas(combined).drawBitmap(tintedOverlay, 0f, 0f, nearestPaint)
                    combined
                } else scaled
            }
            8 -> { // oak_leaves
                if (isLowSaturation(scaled, maxChannelDiff = 18)) {
                    tintBitmap(scaled, 0.48f, 0.82f, 0.30f)
                } else scaled
            }
            16 -> { // water_still
                val tinted = if (isLowSaturation(scaled, maxChannelDiff = 42)) {
                    tintBitmap(scaled, 0.34f, 0.64f, 0.98f)
                } else scaled
                ensureTranslucentAlpha(tinted, 182)
            }
            20 -> { // spruce_leaves
                if (isLowSaturation(scaled, maxChannelDiff = 18)) {
                    tintBitmap(scaled, 0.38f, 0.68f, 0.44f)
                } else scaled
            }
            else -> scaled
        }
    }

    fun getTileBitmap(tileIndex: Int): Bitmap {
        return activeTileCache[tileIndex] ?: defaultTileCache[tileIndex] ?: createFallbackTile()
    }

    fun getBlockPreviewBitmap(blockType: BlockType): Bitmap {
        val tile = when (blockType) {
            BlockType.GRASS, BlockType.SNOW_GRASS -> blockType.sideTile
            else -> blockType.topTile
        }
        return getTileBitmap(tile.coerceAtLeast(0))
    }

    fun countMatchedTilesInDir(packDir: File): Int {
        if (!packDir.exists()) return 0
        val indexed = indexPngFiles(packDir)
        return tileDefinitions.count { def -> def.aliases.any { indexed.containsKey(it) } }
    }

    fun countMatchedTilesInAssets(context: Context, packId: String): Int {
        val assetBlockDir = "resource_packs/$packId/assets/minecraft/textures/block"
        val files = runCatching { context.assets.list(assetBlockDir)?.toSet().orEmpty() }.getOrDefault(emptySet())
        val names = files.map { it.substringBeforeLast('.').lowercase() }.toSet()
        return tileDefinitions.count { def -> def.aliases.any { names.contains(it) } }
    }

    private fun loadDefaultAssetTiles(context: Context) {
        val basePath = "resource_packs/default/assets/minecraft/textures/block"
        for (def in tileDefinitions) {
            val loaded = decodeRawAsset(context, "$basePath/${def.primaryName}.png")
                ?.let { scaleNearest(cropTopSquare(it)) }
            defaultTileCache[def.index] = loaded ?: createFallbackTile()
        }
    }

    private fun indexPngFiles(root: File): Map<String, File> {
        val map = mutableMapOf<String, File>()
        root.walkTopDown()
            .filter { it.isFile && it.extension.equals("png", ignoreCase = true) }
            .forEach { file ->
                val normalized = file.nameWithoutExtension.lowercase()
                val pathLower = file.absolutePath.lowercase()
                val isBlockPath = pathLower.contains("block") || !map.containsKey(normalized)
                if (isBlockPath) {
                    map[normalized] = file
                }
            }
        return map
    }

    private fun decodeRawFile(file: File): Bitmap? {
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun decodeRawAsset(context: Context, assetPath: String): Bitmap? {
        return runCatching {
            context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun cropTopSquare(raw: Bitmap): Bitmap {
        return if (raw.height > raw.width && raw.width > 0) {
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.width)
        } else {
            raw
        }
    }

    private fun scaleNearest(src: Bitmap): Bitmap {
        if (src.width == TILE_SIZE && src.height == TILE_SIZE && src.config == Bitmap.Config.ARGB_8888) {
            return src
        }
        val out = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, null, Rect(0, 0, TILE_SIZE, TILE_SIZE), nearestPaint)
        return out
    }

    private fun isLowSaturation(bmp: Bitmap, maxChannelDiff: Int): Boolean {
        var totalDiff = 0
        var samples = 0
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                val c = bmp.getPixel(x, y)
                if (Color.alpha(c) < 32) continue
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val diff = maxOf(abs(r - g), abs(g - b), abs(r - b))
                totalDiff += diff
                samples++
            }
        }
        if (samples == 0) return false
        return (totalDiff / samples) <= maxChannelDiff
    }

    private fun tintBitmap(src: Bitmap, rMul: Float, gMul: Float, bMul: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                val c = src.getPixel(x, y)
                val a = Color.alpha(c)
                if (a == 0) {
                    out.setPixel(x, y, 0)
                } else {
                    val r = (Color.red(c) * rMul).toInt().coerceIn(0, 255)
                    val g = (Color.green(c) * gMul).toInt().coerceIn(0, 255)
                    val b = (Color.blue(c) * bMul).toInt().coerceIn(0, 255)
                    out.setPixel(x, y, Color.argb(a, r, g, b))
                }
            }
        }
        return out
    }

    private fun ensureTranslucentAlpha(src: Bitmap, targetAlpha: Int): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                val c = src.getPixel(x, y)
                val a = Color.alpha(c)
                if (a == 0) {
                    out.setPixel(x, y, 0)
                } else {
                    out.setPixel(x, y, Color.argb(targetAlpha, Color.red(c), Color.green(c), Color.blue(c)))
                }
            }
        }
        return out
    }

    private fun createFallbackTile(): Bitmap {
        return Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(128, 128, 128))
        }
    }
}
