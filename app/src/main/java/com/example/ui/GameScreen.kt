package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.engine.BlockType
import com.example.engine.TextureAtlasProvider
import com.example.engine.TexturePackInfo
import com.example.engine.TimeOfDayMode
import com.example.engine.VoxelGLSurfaceView
import com.example.ui.theme.VoxelAmber
import com.example.ui.theme.VoxelEmerald
import com.example.ui.theme.VoxelRuby
import com.example.ui.theme.VoxelSky
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun GameScreen(
    session: GameSession,
    settings: GameSettings,
    texturePacks: List<TexturePackInfo>,
    activePackId: String,
    statusBanner: String?,
    onClearBanner: () -> Unit,
    onSetPaused: (Boolean) -> Unit,
    onSelectHotbarSlot: (Int) -> Unit,
    onAssignBlock: (BlockType) -> Unit,
    onBreakBlock: () -> Boolean,
    onPlaceBlock: () -> Boolean,
    onUpdateSettings: (Float, Int, Boolean, ControlScheme, TimeOfDayMode, Float, Float) -> Unit,
    onSelectTexturePack: (String) -> Unit,
    onDeleteTexturePack: (String) -> Unit,
    onImportZipTexturePack: (android.net.Uri) -> Unit,
    onSaveWorld: () -> Unit,
    onAutoSave: () -> Unit,
    onExitToMenu: () -> Unit
) {
    var showInventory by remember { mutableStateOf(false) }
    var showPauseMenu by remember { mutableStateOf(false) }
    var showTexturePacks by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val atlasVersion by TextureAtlasProvider.atlasVersion.collectAsStateWithLifecycle()

    LaunchedEffect(showPauseMenu, showInventory, showTexturePacks) {
        onSetPaused(showPauseMenu || showInventory || showTexturePacks)
    }

    LaunchedEffect(statusBanner) {
        if (statusBanner != null && !showTexturePacks) {
            delay(2200L)
            onClearBanner()
        }
    }

    DisposableEffect(session.worldId, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                onAutoSave()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onSetPaused(false)
        }
    }

    BackHandler {
        when {
            showTexturePacks -> showTexturePacks = false
            showInventory -> showInventory = false
            showPauseMenu -> showPauseMenu = false
            else -> showPauseMenu = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. OpenGL ES 3D Viewport
        VoxelGLSurfaceView(
            renderer = session.renderer,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Touch Camera Look Surface
        LookTouchPad(
            onLookDelta = { dx, dy -> session.player.addLookDelta(dx, dy) },
            modifier = Modifier.fillMaxSize()
        )

        // 3. Center Crosshair
        Crosshair(modifier = Modifier.align(Alignment.Center))

        // 4. Top Telemetry & Pause Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudPill(
                    text = "X:${session.hud.x} Y:${session.hud.y} Z:${session.hud.z}",
                    accent = VoxelEmerald
                )
                HudPill(
                    text = session.hud.timeLabel,
                    accent = VoxelSky
                )
                session.hud.targetBlockName?.let { blockName ->
                    HudPill(
                        text = blockName,
                        accent = VoxelAmber
                    )
                }
            }

            Surface(
                onClick = { showPauseMenu = true },
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.54f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.26f)),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("pause_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = stringResource(R.string.btn_pause),
                        tint = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = statusBanner != null && !showTexturePacks,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 60.dp)
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
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // 5. Landscape Bottom HUD: Left Joystick/D-Pad, Center Hotbar, Right Action Cluster
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            val moveModifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 8.dp)

            if (settings.controlScheme == ControlScheme.JOYSTICK) {
                VirtualJoystickControl(
                    onMoveVector = { fwd, strafe ->
                        session.player.moveForward = fwd
                        session.player.moveStrafe = strafe
                    },
                    controlScale = settings.controlScale,
                    controlOpacity = settings.controlOpacity,
                    modifier = moveModifier
                )
            } else {
                DPadControl(
                    onMoveVector = { fwd, strafe ->
                        session.player.moveForward = fwd
                        session.player.moveStrafe = strafe
                    },
                    controlScale = settings.controlScale,
                    controlOpacity = settings.controlOpacity,
                    modifier = moveModifier
                )
            }

            RightActionCluster(
                isFlying = session.hud.isFlying,
                hapticsEnabled = settings.hapticsEnabled,
                controlScale = settings.controlScale,
                controlOpacity = settings.controlOpacity,
                onBreak = onBreakBlock,
                onPlace = onPlaceBlock,
                onJumpPress = { session.player.onJumpPressed() },
                onJumpRelease = { session.player.onJumpReleased() },
                onDescendPress = { session.player.descendHeld = true },
                onDescendRelease = { session.player.descendHeld = false },
                onToggleFly = { session.player.toggleFly() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 8.dp)
            )

            HotbarRow(
                hotbarBlocks = session.hotbarBlocks,
                selectedSlot = session.selectedSlot,
                atlasVersion = atlasVersion,
                controlScale = settings.controlScale,
                controlOpacity = settings.controlOpacity,
                onSelectSlot = onSelectHotbarSlot,
                onOpenInventory = { showInventory = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 6. Block Picker Inventory
        if (showInventory) {
            BlockPickerOverlay(
                atlasVersion = atlasVersion,
                onSelectBlock = { block ->
                    onAssignBlock(block)
                    showInventory = false
                },
                onDismiss = { showInventory = false }
            )
        }

        // 7. Pause & M3 Expressive Settings Overlay
        if (showPauseMenu) {
            PauseMenuOverlay(
                worldName = session.worldName,
                seed = session.seed,
                settings = settings,
                onUpdateSettings = onUpdateSettings,
                onResume = { showPauseMenu = false },
                onSaveNow = onSaveWorld,
                onOpenTexturePacks = { showTexturePacks = true },
                onSaveAndExit = {
                    showPauseMenu = false
                    onExitToMenu()
                }
            )
        }

        // 8. ZIP Texture Pack Manager
        if (showTexturePacks) {
            TexturePackManagerDialog(
                packs = texturePacks,
                activePackId = activePackId,
                statusMessage = statusBanner,
                onSelectPack = onSelectTexturePack,
                onDeletePack = onDeleteTexturePack,
                onImportZipUri = onImportZipTexturePack,
                onDismiss = {
                    onClearBanner()
                    showTexturePacks = false
                }
            )
        }
    }
}

@Composable
private fun Crosshair(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(18.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.85f))
        )
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(16.dp)
                .background(Color.White.copy(alpha = 0.85f))
        )
    }
}

@Composable
private fun HudPill(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.50f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PauseMenuOverlay(
    worldName: String,
    seed: Long,
    settings: GameSettings,
    onUpdateSettings: (Float, Int, Boolean, ControlScheme, TimeOfDayMode, Float, Float) -> Unit,
    onResume: () -> Unit,
    onSaveNow: () -> Unit,
    onOpenTexturePacks: () -> Unit,
    onSaveAndExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = MaterialTheme.shapes.extraLarge,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    Column {
                        Text(text = worldName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Сид: $seed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onResume, modifier = Modifier.testTag("close_pause_button")) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.btn_resume))
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Left column: Settings, Control Switch, Scale & Opacity
                    Column(
                        modifier = Modifier
                            .weight(0.56f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.settings_control_scheme), style = MaterialTheme.typography.labelLarge)
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

                        Text(stringResource(R.string.settings_time_mode), style = MaterialTheme.typography.labelLarge)
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.settings_haptics), style = MaterialTheme.typography.bodyMedium)
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
                                },
                                modifier = Modifier.testTag("haptics_switch")
                            )
                        }
                    }

                    // Right column: Primary M3 Expressive Pause Actions
                    Column(
                        modifier = Modifier.weight(0.44f),
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                    ) {
                        ExpressiveActionButton(
                            text = stringResource(R.string.btn_resume),
                            onClick = onResume,
                            height = 50.dp,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            leadingIcon = {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("resume_game_button")
                        )

                        ExpressiveActionButton(
                            text = "Текстур-паки (.ZIP)",
                            onClick = onOpenTexturePacks,
                            height = 48.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            borderColor = VoxelAmber.copy(alpha = 0.65f),
                            leadingIcon = {
                                Icon(Icons.Filled.FolderZip, contentDescription = null, tint = VoxelAmber, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_atlas_preview_button")
                        )

                        ExpressiveActionButton(
                            text = stringResource(R.string.btn_save_world),
                            onClick = onSaveNow,
                            height = 48.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            borderColor = MaterialTheme.colorScheme.outline,
                            leadingIcon = {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_world_button")
                        )

                        ExpressiveActionButton(
                            text = stringResource(R.string.btn_save_and_exit),
                            onClick = onSaveAndExit,
                            height = 48.dp,
                            containerColor = VoxelRuby,
                            contentColor = Color.White,
                            borderColor = Color.White.copy(alpha = 0.2f),
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_and_exit_button")
                        )
                    }
                }
            }
        }
    }
}
