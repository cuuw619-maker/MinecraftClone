package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.engine.TextureAtlasProvider
import com.example.engine.TexturePackInfo
import com.example.ui.theme.VoxelAmber
import com.example.ui.theme.VoxelEmerald
import com.example.ui.theme.VoxelOutline

@Composable
fun TexturePackManagerDialog(
    packs: List<TexturePackInfo>,
    activePackId: String,
    statusMessage: String?,
    onSelectPack: (String) -> Unit,
    onDeletePack: (String) -> Unit,
    onImportZipUri: (android.net.Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val atlasVersion by TextureAtlasProvider.atlasVersion.collectAsStateWithLifecycle()

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportZipUri(uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.74f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 780.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.86f)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, VoxelOutline),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.btn_texture_atlas),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = statusMessage ?: stringResource(R.string.atlas_info_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (statusMessage != null) VoxelEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            zipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("import_zip_pack_button")
                    ) {
                        Icon(Icons.Filled.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Загрузить .ZIP")
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_texture_packs_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                // Two-column landscape layout: Left = Installed Packs, Right = Live 24-tile Atlas Preview
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(packs, key = { it.id }) { pack ->
                            val isActive = pack.id == activePackId
                            val hasBlocks = pack.matchedTileCount > 0
                            Surface(
                                onClick = { if (hasBlocks) onSelectPack(pack.id) },
                                enabled = hasBlocks,
                                shape = RoundedCornerShape(14.dp),
                                color = if (isActive) VoxelEmerald.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, if (isActive) VoxelEmerald else VoxelOutline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("texture_pack_${pack.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isActive) Icons.Filled.CheckCircle else Icons.Filled.FolderZip,
                                        contentDescription = null,
                                        tint = if (isActive) VoxelEmerald else VoxelAmber,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pack.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${pack.matchedTileCount}/${TextureAtlasProvider.tileDefinitions.size} тайлов • ${pack.description}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (!pack.isBuiltIn) {
                                        IconButton(
                                            onClick = { onDeletePack(pack.id) },
                                            modifier = Modifier.testTag("delete_pack_${pack.id}")
                                        ) {
                                            Icon(
                                                Icons.Filled.DeleteOutline,
                                                contentDescription = stringResource(R.string.btn_delete),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .weight(0.54f)
                            .fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(TextureAtlasProvider.tileDefinitions, key = { it.index }) { def ->
                            val bmp = remember(def.index, atlasVersion) {
                                TextureAtlasProvider.getTileBitmap(def.index).asImageBitmap()
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, VoxelOutline)
                            ) {
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = def.label,
                                        filterQuality = FilterQuality.None,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = def.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
