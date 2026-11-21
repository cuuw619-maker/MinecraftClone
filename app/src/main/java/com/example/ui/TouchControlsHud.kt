package com.example.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.engine.BlockType
import com.example.engine.TextureAtlasProvider
import com.example.ui.theme.VoxelAmber
import com.example.ui.theme.VoxelEmerald
import com.example.ui.theme.VoxelRuby
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Multi-touch friendly camera look surface that tracks its own pointer ID
 * without stealing gestures already consumed by HUD controls.
 */
@Composable
fun LookTouchPad(
    onLookDelta: (dx: Float, dy: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                var prevPos = down.position
                while (true) {
                    val event = awaitPointerEvent()
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!pointer.pressed) break
                    if (!pointer.isConsumed) {
                        val delta = pointer.position - prevPos
                        prevPos = pointer.position
                        if (delta.x != 0f || delta.y != 0f) {
                            pointer.consume()
                            onLookDelta(delta.x, delta.y)
                        }
                    } else {
                        prevPos = pointer.position
                    }
                }
            }
        }
    )
}

@Composable
fun VirtualJoystickControl(
    onMoveVector: (forward: Float, strafe: Float) -> Unit,
    controlScale: Float = 1.0f,
    controlOpacity: Float = 0.86f,
    modifier: Modifier = Modifier
) {
    val scale = controlScale.coerceIn(0.75f, 1.35f)
    val opacity = controlOpacity.coerceIn(0.35f, 1.0f)
    val outerSize = 148.dp * scale
    val knobSize = 56.dp * scale
    val maxRadiusPx = with(LocalDensity.current) { (46.dp * scale).toPx() }
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val primaryTint = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(outerSize)
            .graphicsLayer { alpha = opacity }
            .testTag("virtual_joystick")
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.28f),
                        Color.Black.copy(alpha = 0.54f)
                    )
                )
            )
            .border(
                width = 2.dp,
                color = if (isDragging) primaryTint.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .pointerInput(maxRadiusPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isDragging = true

                    fun updateStick(px: Float, py: Float) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        var dx = px - cx
                        var dy = py - cy
                        val dist = hypot(dx, dy)
                        if (dist > maxRadiusPx && dist > 0f) {
                            val factor = maxRadiusPx / dist
                            dx *= factor
                            dy *= factor
                        }
                        knobX = dx
                        knobY = dy

                        val nx = (dx / maxRadiusPx).coerceIn(-1f, 1f)
                        val ny = (-dy / maxRadiusPx).coerceIn(-1f, 1f)
                        val deadzone = 0.14f
                        val fwd = if (kotlin.math.abs(ny) < deadzone) 0f else ny
                        val str = if (kotlin.math.abs(nx) < deadzone) 0f else nx
                        onMoveVector(fwd, str)
                    }

                    updateStick(down.position.x, down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer != null && pointer.pressed) {
                            pointer.consume()
                            updateStick(pointer.position.x, pointer.position.y)
                        }
                    } while (event.changes.any { it.id == down.id && it.pressed })

                    isDragging = false
                    knobX = 0f
                    knobY = 0f
                    onMoveVector(0f, 0f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(6.dp * scale)
                .align(Alignment.TopCenter)
                .padding(top = 12.dp * scale)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.34f))
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(knobX.roundToInt(), knobY.roundToInt()) }
                .size(knobSize)
                .clip(CircleShape)
                .background(
                    if (isDragging) primaryTint.copy(alpha = 0.8f)
                    else Color.White.copy(alpha = 0.28f)
                )
                .border(
                    width = 2.dp,
                    color = if (isDragging) Color.White else Color.White.copy(alpha = 0.58f),
                    shape = CircleShape
                )
        )
    }
}

@Composable
fun DPadControl(
    onMoveVector: (forward: Float, strafe: Float) -> Unit,
    controlScale: Float = 1.0f,
    controlOpacity: Float = 0.86f,
    modifier: Modifier = Modifier
) {
    val scale = controlScale.coerceIn(0.75f, 1.35f)
    val opacity = controlOpacity.coerceIn(0.35f, 1.0f)
    val padSize = 148.dp * scale
    val cellSize = 48.dp * scale
    val centerSize = 42.dp * scale
    val iconSize = 26.dp * scale

    var activeForward by remember { mutableFloatStateOf(0f) }
    var activeStrafe by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .size(padSize)
            .graphicsLayer { alpha = opacity }
            .testTag("dpad_control")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    fun updateFromPos(x: Float, y: Float) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val nx = (x - cx) / (size.width / 2f)
                        val ny = (y - cy) / (size.height / 2f)
                        val fwd = when {
                            ny < -0.25f -> 1f
                            ny > 0.25f -> -1f
                            else -> 0f
                        }
                        val str = when {
                            nx > 0.25f -> 1f
                            nx < -0.25f -> -1f
                            else -> 0f
                        }
                        activeForward = fwd
                        activeStrafe = str
                        onMoveVector(fwd, str)
                    }
                    updateFromPos(down.position.x, down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer != null && pointer.pressed) {
                            pointer.consume()
                            updateFromPos(pointer.position.x, pointer.position.y)
                        }
                    } while (event.changes.any { it.id == down.id && it.pressed })
                    activeForward = 0f
                    activeStrafe = 0f
                    onMoveVector(0f, 0f)
                }
            }
    ) {
        DPadCell(
            icon = Icons.Filled.KeyboardArrowUp,
            isActive = activeForward > 0f,
            contentDescription = "Вперёд",
            cellSize = cellSize,
            iconSize = iconSize,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        DPadCell(
            icon = Icons.Filled.KeyboardArrowDown,
            isActive = activeForward < 0f,
            contentDescription = "Назад",
            cellSize = cellSize,
            iconSize = iconSize,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        DPadCell(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            isActive = activeStrafe < 0f,
            contentDescription = "Влево",
            cellSize = cellSize,
            iconSize = iconSize,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        DPadCell(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            isActive = activeStrafe > 0f,
            contentDescription = "Вправо",
            cellSize = cellSize,
            iconSize = iconSize,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        Box(
            modifier = Modifier
                .size(centerSize)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.28f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun DPadCell(
    icon: ImageVector,
    isActive: Boolean,
    contentDescription: String,
    cellSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(cellSize)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) activeColor.copy(alpha = 0.58f)
                else Color.Black.copy(alpha = 0.44f)
            )
            .border(
                1.dp,
                if (isActive) activeColor else Color.White.copy(alpha = 0.24f),
                RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun HoldRepeatActionButton(
    label: String,
    accentColor: Color,
    hapticsEnabled: Boolean,
    testTag: String,
    buttonSize: Dp = 62.dp,
    onAction: () -> Boolean,
    iconContent: @Composable (Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "action_btn_scale"
    )

    LaunchedEffect(pressed) {
        if (pressed) {
            if (onAction() && hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            delay(250L)
            while (pressed) {
                if (onAction() && hapticsEnabled) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                delay(175L)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .testTag(testTag)
                .clip(CircleShape)
                .background(
                    if (pressed) accentColor.copy(alpha = 0.78f)
                    else Color.Black.copy(alpha = 0.50f)
                )
                .border(
                    width = 2.dp,
                    color = if (pressed) Color.White else accentColor.copy(alpha = 0.90f),
                    shape = CircleShape
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        pressed = true
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { if (it.id == down.id) it.consume() }
                        } while (event.changes.any { it.id == down.id && it.pressed })
                        pressed = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            iconContent(buttonSize * 0.45f)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.94f)
        )
    }
}

@Composable
fun HoldStateButton(
    isHighlighted: Boolean = false,
    accentColor: Color = VoxelEmerald,
    testTag: String,
    buttonSize: Dp = 54.dp,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    iconContent: @Composable (Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "hold_btn_scale"
    )

    Box(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    pressed -> accentColor.copy(alpha = 0.72f)
                    isHighlighted -> accentColor.copy(alpha = 0.36f)
                    else -> Color.Black.copy(alpha = 0.48f)
                }
            )
            .border(
                width = 1.5.dp,
                color = if (pressed || isHighlighted) accentColor else Color.White.copy(alpha = 0.30f),
                shape = RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    onPress()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { if (it.id == down.id) it.consume() }
                    } while (event.changes.any { it.id == down.id && it.pressed })
                    pressed = false
                    onRelease()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        iconContent(buttonSize * 0.46f)
    }
}

@Composable
fun RightActionCluster(
    isFlying: Boolean,
    hapticsEnabled: Boolean,
    controlScale: Float = 1.0f,
    controlOpacity: Float = 0.86f,
    onBreak: () -> Boolean,
    onPlace: () -> Boolean,
    onJumpPress: () -> Unit,
    onJumpRelease: () -> Unit,
    onDescendPress: () -> Unit,
    onDescendRelease: () -> Unit,
    onToggleFly: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = controlScale.coerceIn(0.75f, 1.35f)
    val opacity = controlOpacity.coerceIn(0.35f, 1.0f)
    val mainButtonSize = 62.dp * scale
    val descendButtonSize = 48.dp * scale
    val flyToggleHeight = (48.dp * scale).coerceAtLeast(44.dp)
    val primaryTint = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.graphicsLayer { alpha = opacity },
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp * scale)
    ) {
        // Top row: Fly toggle + Descend (when flying)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp * scale),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFlying) {
                HoldStateButton(
                    accentColor = VoxelAmber,
                    testTag = "fly_down_button",
                    buttonSize = descendButtonSize,
                    onPress = onDescendPress,
                    onRelease = onDescendRelease,
                    iconContent = { iconSize ->
                        VoxelIcons.JumpChevron(
                            tint = Color.White,
                            pointingDown = true,
                            size = iconSize
                        )
                    }
                )
            }
            Surface(
                onClick = onToggleFly,
                shape = RoundedCornerShape(16.dp),
                color = if (isFlying) primaryTint.copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.50f),
                border = BorderStroke(
                    1.5.dp,
                    if (isFlying) primaryTint else Color.White.copy(alpha = 0.28f)
                ),
                modifier = Modifier
                    .height(flyToggleHeight)
                    .testTag("toggle_fly_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp * scale),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VoxelIcons.WingsFly(
                        tint = if (isFlying) primaryTint else Color.White,
                        size = 20.dp * scale
                    )
                    Text(
                        text = if (isFlying) "Полёт" else "Ходьба",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }

        // Ergonomic curved cluster for landscape thumb reach: Break, Place, Jump
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp * scale),
            verticalAlignment = Alignment.Bottom
        ) {
            HoldRepeatActionButton(
                label = "Ломать",
                accentColor = VoxelRuby,
                hapticsEnabled = hapticsEnabled,
                testTag = "break_block_button",
                buttonSize = mainButtonSize,
                onAction = onBreak,
                iconContent = { iconSize ->
                    VoxelIcons.Pickaxe(tint = Color.White, size = iconSize)
                }
            )
            HoldRepeatActionButton(
                label = "Ставить",
                accentColor = primaryTint,
                hapticsEnabled = hapticsEnabled,
                testTag = "place_block_button",
                buttonSize = mainButtonSize,
                onAction = onPlace,
                iconContent = { iconSize ->
                    VoxelIcons.BlockPlace(
                        tint = Color.White,
                        accent = primaryTint,
                        size = iconSize
                    )
                },
                modifier = Modifier.padding(bottom = 14.dp * scale)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HoldStateButton(
                    isHighlighted = isFlying,
                    accentColor = primaryTint,
                    testTag = "jump_up_button",
                    buttonSize = mainButtonSize,
                    onPress = onJumpPress,
                    onRelease = onJumpRelease,
                    iconContent = { iconSize ->
                        VoxelIcons.JumpChevron(
                            tint = Color.White,
                            pointingDown = false,
                            size = iconSize
                        )
                    }
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isFlying) "Вверх" else "Прыжок",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.94f)
                )
            }
        }
    }
}

@Composable
fun HotbarRow(
    hotbarBlocks: List<BlockType>,
    selectedSlot: Int,
    atlasVersion: Int,
    controlScale: Float = 1.0f,
    controlOpacity: Float = 0.86f,
    onSelectSlot: (Int) -> Unit,
    onOpenInventory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = controlScale.coerceIn(0.75f, 1.35f)
    val opacity = controlOpacity.coerceIn(0.35f, 1.0f)
    val slotSize = (38.dp * scale).coerceIn(32.dp, 46.dp)
    val iconSize = (26.dp * scale).coerceIn(22.dp, 32.dp)
    val primaryTint = MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.graphicsLayer { alpha = opacity },
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.60f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            hotbarBlocks.forEachIndexed { index, block ->
                val selected = index == selectedSlot
                val bitmap = remember(block, atlasVersion) {
                    TextureAtlasProvider.getBlockPreviewBitmap(block).asImageBitmap()
                }
                Box(
                    modifier = Modifier
                        .size(slotSize)
                        .testTag("hotbar_slot_$index")
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) primaryTint.copy(alpha = 0.36f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) primaryTint else Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectSlot(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = block.displayName,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(slotSize)
                    .testTag("open_inventory_button")
                    .clip(RoundedCornerShape(10.dp))
                    .background(VoxelAmber.copy(alpha = 0.24f))
                    .border(1.dp, VoxelAmber.copy(alpha = 0.78f), RoundedCornerShape(10.dp))
                    .clickable { onOpenInventory() },
                contentAlignment = Alignment.Center
            ) {
                VoxelIcons.BlockGridMenu(
                    tint = Color.White,
                    size = (20.dp * scale).coerceIn(16.dp, 26.dp)
                )
            }
        }
    }
}

@Composable
fun BlockPickerOverlay(
    atlasVersion: Int,
    onSelectBlock: (BlockType) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(0.88f)
                .padding(16.dp)
                .clickable(enabled = false, onClick = {}),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Выбор блока (все ${BlockType.placeableBlocks.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_inventory_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 92.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(220.dp)
                ) {
                    items(BlockType.placeableBlocks, key = { it.id }) { block ->
                        val bmp = remember(block, atlasVersion) {
                            TextureAtlasProvider.getBlockPreviewBitmap(block).asImageBitmap()
                        }
                        Surface(
                            onClick = { onSelectBlock(block) },
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                            modifier = Modifier.testTag("pick_block_${block.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = block.displayName,
                                    filterQuality = FilterQuality.None,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = block.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
