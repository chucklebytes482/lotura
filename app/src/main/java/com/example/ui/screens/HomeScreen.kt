package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.SyncPairEntity
import com.example.data.secure.AppPrefs
import com.example.ui.components.BrandIcons
import com.example.ui.components.CompactSearchBar
import com.example.ui.components.SelectionActionBar
import com.example.ui.components.StatusPill
import com.example.ui.components.formatRelativeTime
import com.example.ui.theme.AmberPending
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.providerBrandColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    pairs: List<SyncPairEntity>,
    appPrefs: AppPrefs,
    syncingPairIds: Set<Long>,
    isOnline: Boolean,
    hasStoragePermission: Boolean,
    onRequestStoragePermission: () -> Unit,
    onOpenDriveSheet: () -> Unit,
    onOpenWebGuide: () -> Unit,
    onOpenPairDetail: (Long) -> Unit,
    onSyncPair: (Long) -> Unit,
    onDeleteSelectedPairs: (List<SyncPairEntity>) -> Unit,
    onAddPairClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()

    BackHandler(enabled = selectionMode) {
        selectedIds = emptySet()
    }

    val filteredPairs = remember(pairs, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            pairs
        } else {
            pairs.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.localPath.contains(q, ignoreCase = true) ||
                    it.driveFolderName.contains(q, ignoreCase = true) ||
                    it.githubRepo.contains(q, ignoreCase = true) ||
                    it.githubSubfolder.contains(q, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .testTag("home_pair_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Prioritas #1: Kartu Status Akun (tanpa jadwal & tanpa tombol sinkron global)
            item(key = "drive_status_card") {
                AccountQuickStatusCard(
                    appPrefs = appPrefs,
                    isOnline = isOnline,
                    onClick = onOpenDriveSheet
                )
            }

            // Prioritas #2: Web Panduan (GitHub Pages)
            item(key = "web_guide_card") {
                WebGuideCard(onClick = onOpenWebGuide)
            }

            if (!hasStoragePermission) {
                item(key = "storage_permission_banner") {
                    StoragePermissionBanner(onGrantClick = onRequestStoragePermission)
                }
            }

            if (selectionMode) {
                item(key = "selection_bar") {
                    SelectionActionBar(
                        selectedCount = selectedIds.size,
                        onSelectAll = { selectedIds = filteredPairs.map { it.id }.toSet() },
                        onMoveSelected = null,
                        onDeleteSelected = {
                            val toDelete = pairs.filter { it.id in selectedIds }
                            onDeleteSelectedPairs(toDelete)
                            selectedIds = emptySet()
                        },
                        onClose = { selectedIds = emptySet() }
                    )
                }
            } else {
                item(key = "search_bar") {
                    CompactSearchBar(
                        query = searchQuery,
                        placeholder = "Cari folder...",
                        onQueryChange = { searchQuery = it },
                        testTag = "home_search_input"
                    )
                }
            }

            if (filteredPairs.isEmpty()) {
                item(key = "empty_pairs") {
                    EmptyPairsCard(
                        hasSearchQuery = searchQuery.isNotBlank(),
                        onAddPairClick = onAddPairClick
                    )
                }
            } else {
                items(filteredPairs, key = { it.id }) { pair ->
                    val isSelected = pair.id in selectedIds
                    val isSyncing = pair.id in syncingPairIds
                    SyncPairCard(
                        pair = pair,
                        isSelected = isSelected,
                        selectionMode = selectionMode,
                        isSyncing = isSyncing,
                        onSyncClick = { onSyncPair(pair.id) },
                        onClick = {
                            if (selectionMode) {
                                selectedIds = if (isSelected) selectedIds - pair.id else selectedIds + pair.id
                            } else {
                                onOpenPairDetail(pair.id)
                            }
                        },
                        onLongClick = {
                            selectedIds = if (isSelected) selectedIds - pair.id else selectedIds + pair.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountQuickStatusCard(
    appPrefs: AppPrefs,
    isOnline: Boolean,
    onClick: () -> Unit
) {
    val driveBrand = providerBrandColors(isGithub = false)
    val dotColor = when {
        !appPrefs.driveConnected -> AmberPending
        !isOnline -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    val subtitleText = when {
        !appPrefs.driveConnected -> "Belum terhubung"
        !isOnline -> "Offline"
        else -> appPrefs.googleEmail.ifBlank { "Terhubung" }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("home_drive_status_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(driveBrand.container),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = BrandIcons.GoogleDrive,
                    contentDescription = "Status Google Drive",
                    tint = if (appPrefs.driveConnected) driveBrand.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Google Drive",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusPill(
                label = if (appPrefs.driveConnected) "Aktif" else "Off",
                dotColor = dotColor
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Buka status akun Google Drive",
                tint = driveBrand.accent
            )
        }
    }
}

@Composable
private fun WebGuideCard(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("home_web_guide_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Web Panduan",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Web Panduan",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "GitHub Pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Buka Web Panduan",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StoragePermissionBanner(onGrantClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AmberPending.copy(alpha = 0.14f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AmberPending.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .testTag("storage_permission_banner")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Izin Akses File",
                tint = AmberPending
            )
            Text(
                text = "Izin Akses Semua File",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onGrantClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("grant_storage_permission_button")
            ) {
                Text("Aktifkan")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SyncPairCard(
    pair: SyncPairEntity,
    isSelected: Boolean,
    selectionMode: Boolean,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isGithub = pair.isGithubPair
    val brand = providerBrandColors(isGithub = isGithub)
    val cardShape = RoundedCornerShape(18.dp)
    val borderColor = if (isSelected) brand.accent else brand.border
    val cardColor = if (isSelected) brand.container.copy(alpha = 0.45f) else brand.cardSurface

    Surface(
        shape = cardShape,
        color = cardColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isSelected) 2.dp else 1.dp, borderColor, cardShape)
            .clip(cardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("pair_card_${pair.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Garis aksen sisi kiri yang tegas untuk membedakan Drive (Biru-Cyan) vs GitHub (Ungu-Violet)
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(brand.accent)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(brand.container)
                            .border(1.dp, brand.accent.copy(alpha = 0.45f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Terpilih",
                                tint = brand.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (isGithub) BrandIcons.GitHub else BrandIcons.GoogleDrive,
                                contentDescription = if (isGithub) "GitHub" else "Google Drive",
                                tint = brand.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = pair.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = brand.container,
                                modifier = Modifier
                                    .border(1.dp, brand.accent.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                                    .testTag("pair_provider_badge_${pair.id}")
                            ) {
                                Text(
                                    text = if (isGithub) "GitHub" else "Google Drive",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = brand.onContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            if (isGithub) {
                                val ghLabel = buildString {
                                    append("${pair.githubOwner}/${pair.githubRepo}")
                                    if (pair.githubSubfolder.isNotBlank()) {
                                        append(" / ${pair.githubSubfolder}")
                                    }
                                }
                                Text(
                                    text = ghLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = brand.accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                        }
                    }

                    FilledTonalIconButton(
                        onClick = onSyncClick,
                        enabled = !isSyncing && !selectionMode,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = brand.container,
                            contentColor = brand.accent
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .border(1.dp, brand.accent.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                            .testTag("pair_sync_button_${pair.id}")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = brand.accent
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sinkronkan ${pair.name}"
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isSyncing) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = brand.container.copy(alpha = 0.72f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                brand.accent.copy(alpha = 0.45f),
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("pair_syncing_notice_${pair.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = brand.accent
                            )
                            Text(
                                text = "Tunggu hingga selesai agar sinkronisasi berhasil",
                                style = MaterialTheme.typography.labelMedium,
                                color = brand.onContainer
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSyncing) {
                            "Sedang menyinkronkan..."
                        } else {
                            pair.lastError?.take(48) ?: pair.lastStatus
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isSyncing -> brand.accent
                            !pair.lastError.isNullOrBlank() -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    val latestTime = maxOf(pair.lastDriveSyncAt, pair.lastGithubSyncAt)
                    Text(
                        text = formatRelativeTime(latestTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPairsCard(
    hasSearchQuery: Boolean,
    onAddPairClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .testTag("empty_pairs_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasSearchQuery) Icons.Default.FolderOpen else Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = if (hasSearchQuery) "Tidak ditemukan" else "Belum ada pasangan",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!hasSearchQuery) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAddPairClick,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("empty_add_pair_button")
                ) {
                    Text("Tambah Pasangan", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
