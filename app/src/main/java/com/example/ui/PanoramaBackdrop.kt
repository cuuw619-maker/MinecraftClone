package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.data.WorldEntity
import com.example.engine.ParticleSystem
import com.example.engine.PlayerController
import com.example.engine.TimeOfDayMode
import com.example.engine.VoxelGLRenderer
import com.example.engine.VoxelGLSurfaceView
import com.example.engine.VoxelWorld

@Composable
fun PanoramaBackdrop(
    modifier: Modifier = Modifier
) {
    val renderer = remember {
        val world = VoxelWorld(
            worldId = -1L,
            seed = 774219L,
            terrainType = WorldEntity.TERRAIN_HILLS
        )
        val player = PlayerController(
            initialX = 8.5f,
            initialY = 31.0f,
            initialZ = 8.5f,
            initialYaw = 25f,
            initialPitch = -13f,
            initialFlying = true
        )
        VoxelGLRenderer(
            world = world,
            player = player,
            particles = ParticleSystem(),
            isPanoramaMode = true
        ).apply {
            renderRadiusChunks = 3
            timeOfDayMode = TimeOfDayMode.DAY
        }
    }

    VoxelGLSurfaceView(
        renderer = renderer,
        modifier = modifier
    )
}
