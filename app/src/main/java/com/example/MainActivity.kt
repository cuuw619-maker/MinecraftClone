package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.GameScreen
import com.example.ui.GameViewModel
import com.example.ui.WorldsMenuScreen
import com.example.ui.theme.VoxelBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VoxelBoxTheme {
                val gameViewModel: GameViewModel = viewModel()
                val worlds by gameViewModel.worlds.collectAsStateWithLifecycle()
                val activeSession by gameViewModel.activeSession.collectAsStateWithLifecycle()
                val settings by gameViewModel.settings.collectAsStateWithLifecycle()
                val texturePacks by gameViewModel.texturePacks.collectAsStateWithLifecycle()
                val activePackId by gameViewModel.activePackId.collectAsStateWithLifecycle()
                val statusBanner by gameViewModel.statusBanner.collectAsStateWithLifecycle()

                val session = activeSession
                if (session == null) {
                    WorldsMenuScreen(
                        worlds = worlds,
                        settings = settings,
                        texturePacks = texturePacks,
                        activePackId = activePackId,
                        statusBanner = statusBanner,
                        onClearBanner = gameViewModel::clearStatusBanner,
                        onPlayWorld = gameViewModel::openWorld,
                        onQuickStart = gameViewModel::quickStartWorld,
                        onCreateWorld = gameViewModel::createAndPlayWorld,
                        onDeleteWorld = gameViewModel::deleteWorld,
                        onSelectTexturePack = gameViewModel::selectTexturePack,
                        onDeleteTexturePack = gameViewModel::deleteTexturePack,
                        onImportZipTexturePack = gameViewModel::importZipTexturePack,
                        onUpdateSettings = gameViewModel::updateSettings
                    )
                } else {
                    GameScreen(
                        session = session,
                        settings = settings,
                        texturePacks = texturePacks,
                        activePackId = activePackId,
                        statusBanner = statusBanner,
                        onClearBanner = gameViewModel::clearStatusBanner,
                        onSetPaused = gameViewModel::setPaused,
                        onSelectHotbarSlot = gameViewModel::selectHotbarSlot,
                        onAssignBlock = gameViewModel::assignBlockToSelectedSlot,
                        onBreakBlock = gameViewModel::breakTargetBlock,
                        onPlaceBlock = gameViewModel::placeSelectedBlock,
                        onUpdateSettings = gameViewModel::updateSettings,
                        onSelectTexturePack = gameViewModel::selectTexturePack,
                        onDeleteTexturePack = gameViewModel::deleteTexturePack,
                        onImportZipTexturePack = gameViewModel::importZipTexturePack,
                        onSaveWorld = { gameViewModel.saveCurrentWorld(showToast = true) },
                        onAutoSave = { gameViewModel.saveCurrentWorld(showToast = false) },
                        onExitToMenu = gameViewModel::exitToMenu
                    )
                }
            }
        }
    }
}
