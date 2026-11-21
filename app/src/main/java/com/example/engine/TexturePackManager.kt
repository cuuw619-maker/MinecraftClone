package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class TexturePackInfo(
    val id: String,
    val name: String,
    val description: String,
    val isBuiltIn: Boolean,
    val matchedTileCount: Int,
    val directoryPath: String?
)

object TexturePackManager {
    const val DEFAULT_PACK_ID = "default"
    const val ASHEN_PACK_ID = "ashen_16x"
    private const val PACKS_DIR = "resource_packs"

    private val bundledPackOrder = listOf(
        "ashen_16x" to "Ashen 16x",
        "stay_true" to "Stay True 1.21.5",
        "default" to "Стандартный VoxelBox",
        "tzp_zombie" to "Tissou's Zombie Pack"
    )

    fun getPacksRoot(context: Context): File {
        return File(context.filesDir, PACKS_DIR).apply { mkdirs() }
    }

    fun getExternalPackDirectory(context: Context, packId: String): File? {
        if (bundledPackOrder.any { it.first == packId }) return null
        val dir = File(getPacksRoot(context), packId)
        return if (dir.exists() && dir.isDirectory) dir else null
    }

    fun isBundledAssetPack(context: Context, packId: String): Boolean {
        val entries = runCatching { context.assets.list("resource_packs")?.toSet().orEmpty() }.getOrDefault(emptySet())
        return entries.contains(packId)
    }

    fun listPacks(context: Context): List<TexturePackInfo> {
        val list = mutableListOf<TexturePackInfo>()
        val assetPackIds = runCatching { context.assets.list("resource_packs")?.toSet().orEmpty() }.getOrDefault(emptySet())

        for ((id, fallbackName) in bundledPackOrder) {
            if (!assetPackIds.contains(id)) continue
            val matched = TextureAtlasProvider.countMatchedTilesInAssets(context, id)
            val metaDesc = readAssetMcMetaDescription(context, id)
            val description = when {
                id == "tzp_zombie" -> "Пак текстур мобов/зомби (0 блоков)"
                metaDesc != null -> metaDesc
                else -> "Встроенная структура PNG"
            }
            list.add(
                TexturePackInfo(
                    id = id,
                    name = fallbackName,
                    description = description,
                    isBuiltIn = true,
                    matchedTileCount = matched,
                    directoryPath = null
                )
            )
        }

        val root = getPacksRoot(context)
        val dirs = root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() }.orEmpty()
        for (dir in dirs) {
            val metaFile = File(dir, ".voxelbox_meta.json")
            val packMcMeta = dir.walkTopDown().firstOrNull { it.isFile && it.name.equals("pack.mcmeta", true) }
            val displayName = readMetaField(metaFile, "name") ?: dir.name
            val desc = readMcMetaDescription(packMcMeta) ?: "Распакованный ZIP-архив"
            val matched = TextureAtlasProvider.countMatchedTilesInDir(dir)
            list.add(
                TexturePackInfo(
                    id = dir.name,
                    name = displayName,
                    description = desc,
                    isBuiltIn = false,
                    matchedTileCount = matched,
                    directoryPath = dir.absolutePath
                )
            )
        }
        return list
    }

    fun importZipPack(context: Context, uri: Uri): Result<TexturePackInfo> {
        return runCatching {
            val rawName = resolveFileName(context, uri)
                ?.substringBeforeLast('.')
                ?.trim()
                ?.ifEmpty { "custom_pack" }
                ?: "pack_${System.currentTimeMillis()}"

            val safeId = rawName
                .lowercase()
                .replace(Regex("[^a-z0-9_\\-]"), "_")
                .trim('_')
                .ifEmpty { "pack" } + "_" + (System.currentTimeMillis() % 10000L)

            val targetDir = File(getPacksRoot(context), safeId).apply { mkdirs() }
            val canonicalTarget = targetDir.canonicalPath

            var extractedFiles = 0
            context.contentResolver.openInputStream(uri)?.use { rawStream ->
                ZipInputStream(BufferedInputStream(rawStream)).use { zip ->
                    val buffer = ByteArray(8192)
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val outFile = File(targetDir, name)
                        if (!outFile.canonicalPath.startsWith(canonicalTarget + File.separator)) {
                            throw SecurityException("Небезопасный путь в архиве: $name")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else if (
                            name.endsWith(".png", ignoreCase = true) ||
                            name.endsWith(".mcmeta", ignoreCase = true)
                        ) {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count <= 0) break
                                    fos.write(buffer, 0, count)
                                }
                            }
                            extractedFiles++
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Не удалось открыть ZIP-файл")

            if (extractedFiles == 0) {
                targetDir.deleteRecursively()
                error("В архиве не найдено PNG-текстур")
            }

            val matched = TextureAtlasProvider.countMatchedTilesInDir(targetDir)
            if (matched == 0) {
                targetDir.deleteRecursively()
                error("В архиве нет текстур блоков (только мобы/интерфейс)")
            }

            File(targetDir, ".voxelbox_meta.json").writeText(
                JSONObject().put("name", rawName).toString()
            )

            val packMcMeta = targetDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("pack.mcmeta", true) }
            TexturePackInfo(
                id = safeId,
                name = rawName,
                description = readMcMetaDescription(packMcMeta) ?: "Распаковано текстур: $matched",
                isBuiltIn = false,
                matchedTileCount = matched,
                directoryPath = targetDir.absolutePath
            )
        }
    }

    fun deletePack(context: Context, packId: String) {
        if (bundledPackOrder.any { it.first == packId }) return
        File(getPacksRoot(context), packId).deleteRecursively()
    }

    private fun resolveFileName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: uri.lastPathSegment
    }

    private fun readMetaField(file: File, key: String): String? {
        if (!file.exists()) return null
        return runCatching { JSONObject(file.readText()).optString(key).takeIf { it.isNotBlank() } }.getOrNull()
    }

    private fun readMcMetaDescription(file: File?): String? {
        if (file == null || !file.exists()) return null
        return parsePackDescription(file.readText())
    }

    private fun readAssetMcMetaDescription(context: Context, packId: String): String? {
        return runCatching {
            context.assets.open("resource_packs/$packId/pack.mcmeta").bufferedReader().use {
                parsePackDescription(it.readText())
            }
        }.getOrNull()
    }

    private fun parsePackDescription(rawJson: String): String? {
        return runCatching {
            val clean = JSONObject(rawJson).optJSONObject("pack")?.optString("description") ?: return null
            clean.replace(Regex("§[0-9a-fk-or]"), "").replace("\n", " ").trim().takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
