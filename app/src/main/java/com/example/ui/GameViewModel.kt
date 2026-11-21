package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BlockDeltaEntity
import com.example.data.VoxelDatabase
import com.example.data.WorldEntity
import com.example.data.WorldRepository
import com.example.engine.BlockType
import com.example.engine.Chunk
import com.example.engine.HudSnapshot
import com.example.engine.ParticleSystem
import com.example.engine.PlayerController
import com.example.engine.TextureAtlasProvider
import com.example.engine.TexturePackInfo
import com.example.engine.TexturePackManager
import com.example.engine.TimeOfDayMode
import com.example.engine.VoxelGLRenderer
import com.example.engine.VoxelWorld
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class ControlScheme(val displayName: String) {
    JOYSTICK("Джойстик"),
    DPAD("Крестовина")
}

data class GameSettings(
    val sensitivity: Float = 0.22f,
    val renderDistance: Int = 3,
    val hapticsEnabled: Boolean = true,
    val controlScheme: ControlScheme = ControlScheme.JOYSTICK,
    val timeOfDayMode: TimeOfDayMode = TimeOfDayMode.AUTO,
    val controlScale: Float = 1.0f,
    val controlOpacity: Float = 0.86f
)

data class GameSession(
    val worldId: Long,
    val worldName: String,
    val terrainType: String,
    val seed: Long,
    val voxelWorld: VoxelWorld,
    val player: PlayerController,
    val renderer: VoxelGLRenderer,
    val hotbarBlocks: List<BlockType>,
    val selectedSlot: Int,
    val hud: HudSnapshot
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("voxelbox_prefs", Context.MODE_PRIVATE)
    private val repository = WorldRepository(VoxelDatabase.getInstance(application).worldDao())

    val worlds: StateFlow<List<WorldEntity>> = repository.worldsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _settings = MutableStateFlow(
        GameSettings(
            sensitivity = prefs.getFloat("sensitivity", 0.22f),
            renderDistance = prefs.getInt("render_distance", 3),
            hapticsEnabled = prefs.getBoolean("haptics", true),
            controlScheme = runCatching {
                ControlScheme.valueOf(prefs.getString("control_scheme", ControlScheme.JOYSTICK.name)!!)
            }.getOrDefault(ControlScheme.JOYSTICK),
            timeOfDayMode = runCatching {
                TimeOfDayMode.valueOf(prefs.getString("time_mode", TimeOfDayMode.AUTO.name)!!)
            }.getOrDefault(TimeOfDayMode.AUTO),
            controlScale = prefs.getFloat("control_scale", 1.0f),
            controlOpacity = prefs.getFloat("control_opacity", 0.86f)
        )
    )
    val settings: StateFlow<GameSettings> = _settings.asStateFlow()

    private val _activePackId = MutableStateFlow(
        prefs.getString("active_pack_id", TexturePackManager.ASHEN_PACK_ID)
            ?: TexturePackManager.ASHEN_PACK_ID
    )
    val activePackId: StateFlow<String> = _activePackId.asStateFlow()

    private val _texturePacks = MutableStateFlow<List<TexturePackInfo>>(emptyList())
    val texturePacks: StateFlow<List<TexturePackInfo>> = _texturePacks.asStateFlow()

    private val _activeSession = MutableStateFlow<GameSession?>(null)
    val activeSession: StateFlow<GameSession?> = _activeSession.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val savedId = _activePackId.value
            val extDir = TexturePackManager.getExternalPackDirectory(context, savedId)
            val validId = when {
                extDir != null -> savedId
                TexturePackManager.isBundledAssetPack(context, savedId) -> savedId
                else -> TexturePackManager.ASHEN_PACK_ID
            }
            if (validId != savedId) {
                _activePackId.value = validId
                prefs.edit().putString("active_pack_id", validId).apply()
            }
            TextureAtlasProvider.initialize(context, validId, extDir)
            _texturePacks.value = TexturePackManager.listPacks(context)
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun importZipTexturePack(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val result = TexturePackManager.importZipPack(context, uri)
            result.onSuccess { pack ->
                _texturePacks.value = TexturePackManager.listPacks(context)
                activatePackInternal(pack.id)
                _statusBanner.value = "Пак «${pack.name}» подключён (${pack.matchedTileCount} текстур)"
            }.onFailure { err ->
                _statusBanner.value = err.message ?: "Ошибка распаковки ZIP"
            }
        }
    }

    fun selectTexturePack(packId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            activatePackInternal(packId)
            val name = _texturePacks.value.firstOrNull { it.id == packId }?.name ?: "Пак"
            _statusBanner.value = "Активен: $name"
        }
    }

    fun deleteTexturePack(packId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            TexturePackManager.deletePack(context, packId)
            if (_activePackId.value == packId) {
                activatePackInternal(TexturePackManager.DEFAULT_PACK_ID)
            }
            _texturePacks.value = TexturePackManager.listPacks(context)
        }
    }

    private fun activatePackInternal(packId: String) {
        val context = getApplication<Application>()
        val extDir = TexturePackManager.getExternalPackDirectory(context, packId)
        val resolvedId = when {
            extDir != null -> packId
            TexturePackManager.isBundledAssetPack(context, packId) -> packId
            else -> TexturePackManager.DEFAULT_PACK_ID
        }
        TextureAtlasProvider.initialize(context, resolvedId, extDir)
        _activePackId.value = resolvedId
        prefs.edit().putString("active_pack_id", resolvedId).apply()
    }

    fun updateSettings(
        sensitivity: Float = _settings.value.sensitivity,
        renderDistance: Int = _settings.value.renderDistance,
        hapticsEnabled: Boolean = _settings.value.hapticsEnabled,
        controlScheme: ControlScheme = _settings.value.controlScheme,
        timeOfDayMode: TimeOfDayMode = _settings.value.timeOfDayMode,
        controlScale: Float = _settings.value.controlScale,
        controlOpacity: Float = _settings.value.controlOpacity
    ) {
        val clamped = GameSettings(
            sensitivity = sensitivity.coerceIn(0.08f, 0.50f),
            renderDistance = renderDistance.coerceIn(2, 5),
            hapticsEnabled = hapticsEnabled,
            controlScheme = controlScheme,
            timeOfDayMode = timeOfDayMode,
            controlScale = controlScale.coerceIn(0.75f, 1.35f),
            controlOpacity = controlOpacity.coerceIn(0.35f, 1.0f)
        )
        _settings.value = clamped
        prefs.edit()
            .putFloat("sensitivity", clamped.sensitivity)
            .putInt("render_distance", clamped.renderDistance)
            .putBoolean("haptics", clamped.hapticsEnabled)
            .putString("control_scheme", clamped.controlScheme.name)
            .putString("time_mode", clamped.timeOfDayMode.name)
            .putFloat("control_scale", clamped.controlScale)
            .putFloat("control_opacity", clamped.controlOpacity)
            .apply()

        _activeSession.value?.let { session ->
            session.player.lookSensitivity = clamped.sensitivity
            session.renderer.renderRadiusChunks = clamped.renderDistance
            session.renderer.timeOfDayMode = clamped.timeOfDayMode
        }
    }

    fun createAndPlayWorld(name: String, seedInput: String, terrainType: String) {
        viewModelScope.launch {
            val seed = seedInput.trim().toLongOrNull()
                ?: if (seedInput.isBlank()) Random.nextLong(100_000L, 999_999_999L) else seedInput.hashCode().toLong()
            val world = withContext(Dispatchers.IO) {
                val tempWorld = VoxelWorld(0L, seed, terrainType)
                val spawnY = tempWorld.getSurfaceHeight(8, 8).coerceAtLeast(VoxelWorld.SEA_LEVEL) + 2.2f
                repository.createWorld(name, seed, terrainType, spawnY)
            }
            launchWorldEntity(world, emptyList())
        }
    }

    fun quickStartWorld() {
        val number = (worlds.value.size + 1)
        createAndPlayWorld(
            name = "Мир #$number",
            seedInput = "",
            terrainType = WorldEntity.TERRAIN_HILLS
        )
    }

    fun openWorld(worldId: Long) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) { repository.loadWorld(worldId) } ?: return@launch
            launchWorldEntity(loaded.world, loaded.deltas)
        }
    }

    fun deleteWorld(worldId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteWorld(worldId)
        }
    }

    private fun launchWorldEntity(
        worldEntity: WorldEntity,
        deltas: List<BlockDeltaEntity>
    ) {
        val voxelWorld = VoxelWorld(
            worldId = worldEntity.id,
            seed = worldEntity.seed,
            terrainType = worldEntity.terrainType
        ).apply {
            loadDeltas(deltas)
        }

        val player = PlayerController(
            initialX = worldEntity.playerX,
            initialY = worldEntity.playerY,
            initialZ = worldEntity.playerZ,
            initialYaw = worldEntity.playerYaw,
            initialPitch = worldEntity.playerPitch,
            initialFlying = worldEntity.isFlying
        ).apply {
            lookSensitivity = _settings.value.sensitivity
        }

        val hotbarBlocks = parseHotbar(worldEntity.hotbarCsv)
        val selectedSlot = worldEntity.selectedSlot.coerceIn(0, hotbarBlocks.lastIndex)

        val renderer = VoxelGLRenderer(
            world = voxelWorld,
            player = player,
            particles = ParticleSystem(),
            onHudUpdate = { snapshot ->
                _activeSession.update { current ->
                    if (current?.worldId == worldEntity.id) {
                        current.copy(hud = snapshot)
                    } else {
                        current
                    }
                }
            }
        ).apply {
            renderRadiusChunks = _settings.value.renderDistance
            timeOfDayMode = _settings.value.timeOfDayMode
        }

        _activeSession.value = GameSession(
            worldId = worldEntity.id,
            worldName = worldEntity.name,
            terrainType = worldEntity.terrainType,
            seed = worldEntity.seed,
            voxelWorld = voxelWorld,
            player = player,
            renderer = renderer,
            hotbarBlocks = hotbarBlocks,
            selectedSlot = selectedSlot,
            hud = HudSnapshot(
                x = worldEntity.playerX.toInt(),
                y = worldEntity.playerY.toInt(),
                z = worldEntity.playerZ.toInt(),
                isFlying = worldEntity.isFlying,
                targetBlockName = null
            )
        )
    }

    fun setPaused(paused: Boolean) {
        _activeSession.value?.renderer?.isPaused = paused
    }

    fun selectHotbarSlot(index: Int) {
        _activeSession.update { session ->
            session?.copy(selectedSlot = index.coerceIn(0, session.hotbarBlocks.lastIndex))
        }
    }

    fun assignBlockToSelectedSlot(block: BlockType) {
        _activeSession.update { session ->
            if (session == null) return@update null
            val existingIndex = session.hotbarBlocks.indexOf(block)
            if (existingIndex >= 0) {
                session.copy(selectedSlot = existingIndex)
            } else {
                val updated = session.hotbarBlocks.toMutableList().apply {
                    this[session.selectedSlot] = block
                }
                session.copy(hotbarBlocks = updated)
            }
        }
    }

    fun breakTargetBlock(): Boolean {
        val session = _activeSession.value ?: return false
        val hit = session.renderer.latestRaycastHit ?: return false
        if (!hit.blockType.isBreakable) return false

        val pos = hit.blockPos
        val brokenType = hit.blockType
        val changed = session.voxelWorld.setBlock(pos.x, pos.y, pos.z, BlockType.AIR)
        if (changed) {
            session.renderer.particles.queueBreakBurst(pos.x, pos.y, pos.z, brokenType)
            viewModelScope.launch(Dispatchers.IO) {
                repository.saveBlockDelta(session.worldId, pos.x, pos.y, pos.z, BlockType.AIR.id.toInt())
            }
        }
        return changed
    }

    fun placeSelectedBlock(): Boolean {
        val session = _activeSession.value ?: return false
        val hit = session.renderer.latestRaycastHit ?: return false
        val blockToPlace = session.hotbarBlocks.getOrNull(session.selectedSlot) ?: return false

        val tx = hit.blockPos.x + hit.normalX
        val ty = hit.blockPos.y + hit.normalY
        val tz = hit.blockPos.z + hit.normalZ
        if (ty !in 1 until Chunk.WORLD_HEIGHT) return false
        if (session.player.intersectsBlock(tx, ty, tz)) return false
        val existing = session.voxelWorld.getBlock(tx, ty, tz)
        if (existing != BlockType.AIR && existing != BlockType.WATER) return false

        val changed = session.voxelWorld.setBlock(tx, ty, tz, blockToPlace)
        if (changed) {
            session.renderer.particles.queuePlaceBurst(tx, ty, tz, blockToPlace)
            viewModelScope.launch(Dispatchers.IO) {
                repository.saveBlockDelta(session.worldId, tx, ty, tz, blockToPlace.id.toInt())
            }
        }
        return changed
    }

    fun saveCurrentWorld(showToast: Boolean = true) {
        val session = _activeSession.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveWorldProgress(
                worldId = session.worldId,
                x = session.player.x,
                y = session.player.y,
                z = session.player.z,
                yaw = session.player.yaw,
                pitch = session.player.pitch,
                isFlying = session.player.isFlying,
                selectedSlot = session.selectedSlot,
                hotbarIds = session.hotbarBlocks.map { it.id.toInt() }
            )
            if (showToast) {
                _statusBanner.value = "Мир сохранён"
            }
        }
    }

    fun exitToMenu() {
        val session = _activeSession.value ?: return
        _activeSession.value = null
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveWorldProgress(
                worldId = session.worldId,
                x = session.player.x,
                y = session.player.y,
                z = session.player.z,
                yaw = session.player.yaw,
                pitch = session.player.pitch,
                isFlying = session.player.isFlying,
                selectedSlot = session.selectedSlot,
                hotbarIds = session.hotbarBlocks.map { it.id.toInt() }
            )
        }
    }

    private fun parseHotbar(csv: String): List<BlockType> {
        val parsed = csv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .map { BlockType.fromId(it) }
            .filter { it != BlockType.AIR && it != BlockType.BEDROCK && it != BlockType.WATER }
        return if (parsed.size == 9) parsed else listOf(
            BlockType.GRASS,
            BlockType.DIRT,
            BlockType.STONE,
            BlockType.COBBLESTONE,
            BlockType.OAK_LOG,
            BlockType.OAK_PLANKS,
            BlockType.SAND,
            BlockType.BRICKS,
            BlockType.GLASS
        )
    }
}
