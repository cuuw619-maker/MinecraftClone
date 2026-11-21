package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.WorldEntity
import com.example.engine.TexturePackInfo
import com.example.engine.TimeOfDayMode
import com.example.ui.theme.VoxelAmber
import com.example.ui.theme.VoxelEmerald
import com.example.ui.theme.VoxelRuby
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class MenuOverlay {
    HOME,
    WORLDS,
    TEXTURE_PACKS,
    SETTINGS
}

@Composable
fun WorldsMenuScreen(
    worlds: List<WorldEntity>,
    settings: GameSettings,
    texturePacks: List<TexturePackInfo>,
    activePackId: String,
    statusBanner: String?,
    onClearBanner: () -> Unit,
    onPlayWorld: (Long) -> Unit,
    onQuickStart: () -> Unit,
    onCreateWorld: (name: String, seed: String, terrainType: String) -> Unit,
    onDeleteWorld: (Long) -> Unit,
    onSelectTexturePack: (String) -> Unit,
    onDeleteTexturePack: (String) -> Unit,
    onImportZipTexturePack: (android.net.Uri) -> Unit,
    onUpdateSettings: (Float, Int, Boolean, ControlScheme, TimeOfDayMode, Float, Float) -> Unit
) {
    var activeOverlay by remember { mutableStateOf(MenuOverlay.HOME) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var worldToDelete by remember { mutableStateOf<WorldEntity?>(null) }

    val activePackName = remember(texturePacks, activePackId) {
        texturePacks.firstOrNull { it.id == activePackId }?.name ?: "Стандартный"
    }

    LaunchedEffect(statusBanner, activeOverlay) {
        if (statusBanner != null && activeOverlay != MenuOverlay.TEXTURE_PACKS) {
            delay(2600L)
            onClearBanner()
        }
    }

    BackHandler(enabled = activeOverlay != MenuOverlay.HOME) {
        if (activeOverlay == MenuOverlay.TEXTURE_PACKS) {
            onClearBanner()
        }
        activeOverlay = MenuOverlay.HOME
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (activeOverlay == MenuOverlay.HOME) 0.25f else 0.68f,
        label = "panorama_scrim_alpha"
    )

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Live 3D Voxel Panorama Backdrop
            PanoramaBackdrop(modifier = Modifier.fillMaxSize())

            // 2. Gradient + Dynamic Scrim for crisp contrast over moving terrain
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = (scrimAlpha + 0.36f).coerceAtMost(0.88f)),
                                Color.Black.copy(alpha = scrimAlpha),
                                Color.Black.copy(alpha = (scrimAlpha + 0.14f).coerceAtMost(0.84f))
                            )
                        )
                    )
            )

            // 3. Active Menu Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                when (activeOverlay) {
                    MenuOverlay.HOME -> {
                        PanoramaHomeContent(
                            worlds = worlds,
                            settings = settings,
                            activePackName = activePackName,
                            onOpenWorlds = { activeOverlay = MenuOverlay.WORLDS },
                            onQuickStart = onQuickStart,
                            onPlayWorld = onPlayWorld,
                            onCreateWorldClick = { showCreateDialog = true },
                            onOpenTexturePacks = { activeOverlay = MenuOverlay.TEXTURE_PACKS },
                            onOpenSettings = { activeOverlay = MenuOverlay.SETTINGS }
                        )
                    }

                    MenuOverlay.WORLDS -> {
                        WorldsOverlayPanel(
                            worlds = worlds,
                            onBack = { activeOverlay = MenuOverlay.HOME },
                            onQuickStart = onQuickStart,
                            onCreateWorldClick = { showCreateDialog = true },
                            onPlayWorld = onPlayWorld,
                            onRequestDelete = { worldToDelete = it }
                        )
                    }

                    MenuOverlay.SETTINGS -> {
                        MenuSettingsOverlayPanel(
                            settings = settings,
                            onUpdateSettings = onUpdateSettings,
                            onBack = { activeOverlay = MenuOverlay.HOME }
                        )
                    }

                    MenuOverlay.TEXTURE_PACKS -> Unit
                }

                AnimatedVisibility(
                    visible = statusBanner != null && activeOverlay != MenuOverlay.TEXTURE_PACKS,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = statusBanner.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (activeOverlay == MenuOverlay.TEXTURE_PACKS) {
                TexturePackManagerDialog(
                    packs = texturePacks,
                    activePackId = activePackId,
                    statusMessage = statusBanner,
                    onSelectPack = onSelectTexturePack,
                    onDeletePack = onDeleteTexturePack,
                    onImportZipUri = onImportZipTexturePack,
                    onDismiss = {
                        onClearBanner()
                        activeOverlay = MenuOverlay.HOME
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateWorldDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, seed, terrain ->
                showCreateDialog = false
                onCreateWorld(name, seed, terrain)
            }
        )
    }

    worldToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { worldToDelete = null },
            title = { Text("Удалить мир?") },
            text = { Text("Мир «${target.name}» и все построенные блоки будут удалены с устройства.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteWorld(target.id)
                        worldToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VoxelRuby),
                    modifier = Modifier.testTag("confirm_delete_world_button")
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { worldToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_world_button")
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
private fun PanoramaHomeContent(
    worlds: List<WorldEntity>,
    settings: GameSettings,
    activePackName: String,
    onOpenWorlds: () -> Unit,
    onQuickStart: () -> Unit,
    onPlayWorld: (Long) -> Unit,
    onCreateWorldClick: () -> Unit,
    onOpenTexturePacks: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val latestWorld = worlds.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Minecraft-inspired title emblem & vertical button stack over live 3D panorama
        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                ) {
                    Text(
                        text = "VOXELBOX 3.0",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderZip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = activePackName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White
            )
            Text(
                text = stringResource(R.string.subtitle_creative_sandbox),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f)
            )

            Spacer(Modifier.height(18.dp))

            Column(
                modifier = Modifier.widthIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExpressiveActionButton(
                    text = stringResource(R.string.btn_play),
                    onClick = onOpenWorlds,
                    height = 54.dp,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    leadingIcon = {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("play_menu_button")
                )

                ExpressiveActionButton(
                    text = "Быстрый старт",
                    onClick = onQuickStart,
                    height = 48.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    leadingIcon = {
                        Icon(Icons.Filled.RocketLaunch, contentDescription = null, modifier = Modifier.size(19.dp))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_start_button")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveActionButton(
                        text = "Текстуры",
                        onClick = onOpenTexturePacks,
                        height = 48.dp,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        borderColor = VoxelAmber.copy(alpha = 0.65f),
                        leadingIcon = {
                            Icon(
                                Icons.Filled.FolderZip,
                                contentDescription = null,
                                tint = VoxelAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("menu_texture_packs_button")
                    )

                    ExpressiveActionButton(
                        text = stringResource(R.string.btn_settings),
                        onClick = onOpenSettings,
                        height = 48.dp,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        borderColor = MaterialTheme.colorScheme.outline,
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("menu_settings_button")
                    )
                }
            }
        }

        // Right Column: Translucent M3 Expressive Quick-Resume & Control Summary Card
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            Card(
                modifier = Modifier.widthIn(max = 360.dp),
                shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VoxelIcons.BlockGridMenu(
                            tint = MaterialTheme.colorScheme.primary,
                            size = 20.dp
                        )
                        Text(
                            text = if (latestWorld != null) "Последний мир" else "Готово к стройке",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (latestWorld != null) {
                        val isFlat = latestWorld.terrainType == WorldEntity.TERRAIN_FLAT
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFlat) Icons.Filled.Layers else Icons.Filled.Landscape,
                                    contentDescription = null,
                                    tint = if (isFlat) VoxelAmber else VoxelEmerald,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = latestWorld.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Сид ${latestWorld.seed} • Правок: ${latestWorld.editedBlocksCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        ExpressiveActionButton(
                            text = "Продолжить мир",
                            onClick = { onPlayWorld(latestWorld.id) },
                            height = 48.dp,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            leadingIcon = {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("resume_latest_world_button")
                        )
                    } else {
                        Text(
                            text = "Живая 3D-панорама мира уже работает. Создайте свой мир или запустите мгновенный старт.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onCreateWorldClick,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("new_world_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_new_world))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Управление: ${settings.controlScheme.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Миров: ${worlds.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldsOverlayPanel(
    worlds: List<WorldEntity>,
    onBack: () -> Unit,
    onQuickStart: () -> Unit,
    onCreateWorldClick: () -> Unit,
    onPlayWorld: (Long) -> Unit,
    onRequestDelete: (WorldEntity) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 820.dp)
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("worlds_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.btn_back)
                            )
                        }
                        Text(
                            text = "${stringResource(R.string.label_worlds)} (${worlds.size})",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onCreateWorldClick,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("new_world_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_new_world))
                        }

                        Button(
                            onClick = onQuickStart,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("worlds_quick_start_button")
                        ) {
                            Icon(Icons.Filled.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Быстрый старт")
                        }
                    }
                }

                if (worlds.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyWorldsCard(
                            onCreateClick = onCreateWorldClick,
                            onQuickStartClick = onQuickStart
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(worlds, key = { it.id }) { world ->
                            WorldCard(
                                world = world,
                                onPlay = { onPlayWorld(world.id) },
                                onRequestDelete = { onRequestDelete(world) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuSettingsOverlayPanel(
    settings: GameSettings,
    onUpdateSettings: (Float, Int, Boolean, ControlScheme, TimeOfDayMode, Float, Float) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 780.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("settings_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.btn_back)
                            )
                        }
                        Text(
                            text = "Настройки управления и мира",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Button(
                        onClick = onBack,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Готово")
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Left column: Control Scheme, Time of Day, Haptics
                    Column(
                        modifier = Modifier
                            .weight(0.48f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_control_scheme),
                            style = MaterialTheme.typography.labelLarge
                        )
                        ExpressiveSegmentedGroup(
                            options = ControlScheme.entries,
                            selected = settings.controlScheme,
                            labelProvider = { it.displayName },
                            onSelect = { scheme ->
                                onUpdateSettings(
                                    settings.sensitivity,
                                    settings.renderDistance,
                                    settings.hapticsEnabled,
                                    scheme,
                                    settings.timeOfDayMode,
                                    settings.controlScale,
                                    settings.controlOpacity
                                )
                            }
                        )

                        Text(
                            text = stringResource(R.string.settings_time_mode),
                            style = MaterialTheme.typography.labelLarge
                        )
                        ExpressiveSegmentedGroup(
                            options = TimeOfDayMode.entries,
                            selected = settings.timeOfDayMode,
                            labelProvider = { it.displayName },
                            onSelect = { mode ->
                                onUpdateSettings(
                                    settings.sensitivity,
                                    settings.renderDistance,
                                    settings.hapticsEnabled,
                                    settings.controlScheme,
                                    mode,
                                    settings.controlScale,
                                    settings.controlOpacity
                                )
                            },
                            iconProvider = { mode ->
                                if (mode == TimeOfDayMode.DAY) {
                                    Icon(Icons.Filled.WbSunny, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_haptics),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = settings.hapticsEnabled,
                                onCheckedChange = {
                                    onUpdateSettings(
                                        settings.sensitivity,
                                        settings.renderDistance,
                                        it,
                                        settings.controlScheme,
                                        settings.timeOfDayMode,
                                        settings.controlScale,
                                        settings.controlOpacity
                                    )
                                }
                            )
                        }
                    }

                    // Right column: Sliders for Scale, Opacity, Sensitivity, Render Distance
                    Column(
                        modifier = Modifier
                            .weight(0.52f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.settings_control_scale)}: ${(settings.controlScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = settings.controlScale,
                            onValueChange = {
                                onUpdateSettings(
                                    settings.sensitivity,
                                    settings.renderDistance,
                                    settings.hapticsEnabled,
                                    settings.controlScheme,
                                    settings.timeOfDayMode,
                                    it,
                                    settings.controlOpacity
                                )
                            },
                            valueRange = 0.75f..1.35f
                        )

                        Text(
                            text = "${stringResource(R.string.settings_control_opacity)}: ${(settings.controlOpacity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = settings.controlOpacity,
                            onValueChange = {
                                onUpdateSettings(
                                    settings.sensitivity,
                                    settings.renderDistance,
                                    settings.hapticsEnabled,
                                    settings.controlScheme,
                                    settings.timeOfDayMode,
                                    settings.controlScale,
                                    it
                                )
                            },
                            valueRange = 0.35f..1.0f
                        )

                        Text(
                            text = "${stringResource(R.string.settings_sensitivity)}: ${(settings.sensitivity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = settings.sensitivity,
                            onValueChange = {
                                onUpdateSettings(
                                    it,
                                    settings.renderDistance,
                                    settings.hapticsEnabled,
                                    settings.controlScheme,
                                    settings.timeOfDayMode,
                                    settings.controlScale,
                                    settings.controlOpacity
                                )
                            },
                            valueRange = 0.08f..0.48f
                        )

                        Text(
                            text = "${stringResource(R.string.settings_render_distance)}: ${settings.renderDistance}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = settings.renderDistance.toFloat(),
                            onValueChange = {
                                onUpdateSettings(
                                    settings.sensitivity,
                                    it.roundToInt(),
                                    settings.hapticsEnabled,
                                    settings.controlScheme,
                                    settings.timeOfDayMode,
                                    settings.controlScale,
                                    settings.controlOpacity
                                )
                            },
                            valueRange = 2f..5f,
                            steps = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWorldsCard(
    onCreateClick: () -> Unit,
    onQuickStartClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.GridView,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp)
        )
        Text(
            text = stringResource(R.string.label_empty_worlds),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Биомы, водные озёра, пещеры, руды и смена дня и ночи уже готовы к исследованию.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCreateClick, modifier = Modifier.testTag("empty_create_world_button")) {
                Text("Настроить мир")
            }
            Button(onClick = onQuickStartClick, modifier = Modifier.testTag("empty_quick_start_button")) {
                Text("Начать игру")
            }
        }
    }
}

@Composable
private fun WorldCard(
    world: WorldEntity,
    onPlay: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val isFlat = world.terrainType == WorldEntity.TERRAIN_FLAT

    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        modifier = Modifier.clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isFlat) VoxelAmber.copy(alpha = 0.16f) else VoxelEmerald.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFlat) Icons.Filled.Layers else Icons.Filled.Landscape,
                    contentDescription = null,
                    tint = if (isFlat) VoxelAmber else VoxelEmerald,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = world.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${if (isFlat) "Плоский" else "Биомы"} • Сид ${world.seed}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Правок: ${world.editedBlocksCount} • ${dateFormat.format(Date(world.lastPlayedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onRequestDelete,
                modifier = Modifier.testTag("delete_world_${world.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.btn_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onPlay,
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("play_world_${world.id}")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.btn_play))
            }
        }
    }
}

@Composable
private fun CreateWorldDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, seed: String, terrainType: String) -> Unit
) {
    var name by remember { mutableStateOf("Мой мир") }
    var seed by remember { mutableStateOf("") }
    var terrainType by remember { mutableStateOf(WorldEntity.TERRAIN_HILLS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.btn_new_world)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_world_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("world_name_input")
                )
                OutlinedTextField(
                    value = seed,
                    onValueChange = { seed = it },
                    label = { Text(stringResource(R.string.label_world_seed)) },
                    placeholder = { Text("Например: 42190") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("world_seed_input")
                )
                Text(
                    text = stringResource(R.string.label_terrain_type),
                    style = MaterialTheme.typography.labelLarge
                )
                ExpressiveSegmentedGroup(
                    options = listOf(WorldEntity.TERRAIN_HILLS, WorldEntity.TERRAIN_FLAT),
                    selected = terrainType,
                    labelProvider = {
                        if (it == WorldEntity.TERRAIN_HILLS) "Биомы и пещеры" else "Плоский мир"
                    },
                    onSelect = { terrainType = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, seed, terrainType) },
                modifier = Modifier.testTag("create_world_confirm_button")
            ) {
                Text(stringResource(R.string.btn_create))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("create_world_cancel_button")
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
