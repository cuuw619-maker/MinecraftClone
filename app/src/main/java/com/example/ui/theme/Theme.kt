package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val VoxelFallbackColorScheme = darkColorScheme(
    primary = VoxelEmerald,
    onPrimary = VoxelBackground,
    primaryContainer = VoxelEmeraldDark,
    onPrimaryContainer = VoxelEmerald,
    secondary = VoxelAmber,
    onSecondary = VoxelBackground,
    secondaryContainer = VoxelAmberDark,
    onSecondaryContainer = VoxelAmber,
    tertiary = VoxelSky,
    onTertiary = VoxelBackground,
    error = VoxelRuby,
    onError = VoxelTextPrimary,
    errorContainer = VoxelRubyDark,
    onErrorContainer = VoxelRuby,
    background = VoxelBackground,
    onBackground = VoxelTextPrimary,
    surface = VoxelSurface,
    onSurface = VoxelTextPrimary,
    surfaceVariant = VoxelSurfaceHigh,
    onSurfaceVariant = VoxelTextMuted,
    outline = VoxelOutline
)

private val VoxelExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VoxelBoxTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        VoxelFallbackColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VoxelExpressiveShapes,
        content = content
    )
}
