package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Custom crisp vector / pixel-inspired HUD icons (zero emojis).
 */
object VoxelIcons {

    @Composable
    fun Pickaxe(
        tint: Color = Color.White,
        size: Dp = 26.dp,
        modifier: Modifier = Modifier
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            // Handle (diagonal bottom-left to top-right)
            drawLine(
                color = Color(0xFFD6A86E),
                start = Offset(w * 0.18f, h * 0.84f),
                end = Offset(w * 0.72f, h * 0.30f),
                strokeWidth = w * 0.12f,
                cap = StrokeCap.Square
            )
            // Pickaxe curved blocky head
            val headPath = Path().apply {
                moveTo(w * 0.24f, h * 0.22f)
                lineTo(w * 0.56f, h * 0.14f)
                lineTo(w * 0.86f, h * 0.44f)
                lineTo(w * 0.78f, h * 0.76f)
                lineTo(w * 0.66f, h * 0.66f)
                lineTo(w * 0.70f, h * 0.44f)
                lineTo(w * 0.56f, h * 0.30f)
                lineTo(w * 0.34f, h * 0.34f)
                close()
            }
            drawPath(path = headPath, color = tint)
        }
    }

    @Composable
    fun BlockPlace(
        tint: Color = Color.White,
        accent: Color = Color(0xFF4CE07A),
        size: Dp = 26.dp,
        modifier: Modifier = Modifier
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            // Isometric cube: Top
            val top = Path().apply {
                moveTo(w * 0.5f, h * 0.12f)
                lineTo(w * 0.84f, h * 0.30f)
                lineTo(w * 0.5f, h * 0.48f)
                lineTo(w * 0.16f, h * 0.30f)
                close()
            }
            // Left face
            val left = Path().apply {
                moveTo(w * 0.16f, h * 0.30f)
                lineTo(w * 0.5f, h * 0.48f)
                lineTo(w * 0.5f, h * 0.86f)
                lineTo(w * 0.16f, h * 0.68f)
                close()
            }
            // Right face
            val right = Path().apply {
                moveTo(w * 0.5f, h * 0.48f)
                lineTo(w * 0.84f, h * 0.30f)
                lineTo(w * 0.84f, h * 0.68f)
                lineTo(w * 0.5f, h * 0.86f)
                close()
            }
            drawPath(top, color = tint)
            drawPath(left, color = tint.copy(alpha = 0.72f))
            drawPath(right, color = tint.copy(alpha = 0.52f))
            // Crisp plus badge in top-right
            val px = w * 0.78f
            val py = h * 0.22f
            val r = w * 0.12f
            drawLine(accent, Offset(px - r, py), Offset(px + r, py), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
            drawLine(accent, Offset(px, py - r), Offset(px, py + r), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun WingsFly(
        tint: Color = Color.White,
        size: Dp = 22.dp,
        modifier: Modifier = Modifier
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val leftWing = Path().apply {
                moveTo(w * 0.46f, h * 0.54f)
                lineTo(w * 0.10f, h * 0.24f)
                lineTo(w * 0.14f, h * 0.52f)
                lineTo(w * 0.26f, h * 0.66f)
                lineTo(w * 0.46f, h * 0.68f)
                close()
            }
            val rightWing = Path().apply {
                moveTo(w * 0.54f, h * 0.54f)
                lineTo(w * 0.90f, h * 0.24f)
                lineTo(w * 0.86f, h * 0.52f)
                lineTo(w * 0.74f, h * 0.66f)
                lineTo(w * 0.54f, h * 0.68f)
                close()
            }
            drawPath(leftWing, color = tint)
            drawPath(rightWing, color = tint)
            // Center feather core
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.44f, h * 0.36f),
                size = Size(w * 0.12f, h * 0.42f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }

    @Composable
    fun JumpChevron(
        tint: Color = Color.White,
        pointingDown: Boolean = false,
        size: Dp = 26.dp,
        modifier: Modifier = Modifier
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                if (!pointingDown) {
                    moveTo(w * 0.22f, h * 0.62f)
                    lineTo(w * 0.50f, h * 0.28f)
                    lineTo(w * 0.78f, h * 0.62f)
                } else {
                    moveTo(w * 0.22f, h * 0.38f)
                    lineTo(w * 0.50f, h * 0.72f)
                    lineTo(w * 0.78f, h * 0.38f)
                }
            }
            drawPath(
                path = path,
                color = tint,
                style = Stroke(width = w * 0.14f, cap = StrokeCap.Square, join = StrokeJoin.Miter)
            )
            if (!pointingDown) {
                drawLine(
                    color = tint.copy(alpha = 0.75f),
                    start = Offset(w * 0.30f, h * 0.78f),
                    end = Offset(w * 0.70f, h * 0.78f),
                    strokeWidth = w * 0.11f,
                    cap = StrokeCap.Square
                )
            }
        }
    }

    @Composable
    fun BlockGridMenu(
        tint: Color = Color.White,
        size: Dp = 20.dp,
        modifier: Modifier = Modifier
    ) {
        Canvas(modifier = modifier.size(size)) {
            val w = this.size.width
            val cell = w * 0.22f
            val gap = w * 0.08f
            val start = (w - (cell * 3 + gap * 2)) * 0.5f
            for (row in 0..2) {
                for (col in 0..2) {
                    drawRect(
                        color = tint,
                        topLeft = Offset(start + col * (cell + gap), start + row * (cell + gap)),
                        size = Size(cell, cell)
                    )
                }
            }
        }
    }
}
