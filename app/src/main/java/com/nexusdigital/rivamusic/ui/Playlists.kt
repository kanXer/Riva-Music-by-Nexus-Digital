package com.nexusdigital.rivamusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusdigital.rivamusic.data.Playlist
import com.nexusdigital.rivamusic.data.DownloadStatus
import com.nexusdigital.rivamusic.data.Track
import com.nexusdigital.rivamusic.theme.*

@Composable
fun PlaylistCard(
    playlist: Playlist,
    onOpenDetail: () -> Unit,
    onPlayAll: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    showDelete: Boolean = true,
) {
    Column(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onOpenDetail),
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(22.dp)),
        ) {
            if (playlist.coverUrl != null) {
                TrackArtwork(
                    url = playlist.coverUrl,
                    size = 150.dp,
                    corner = 22.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(
                            Brush.linearGradient(listOf(Panel, CardMid, Panel)),
                            RoundedCornerShape(22.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = Violet,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Ink.copy(alpha = 0.78f)),
                            startY = 40f,
                        ),
                        RoundedCornerShape(22.dp),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(11.dp)
                    .size(38.dp)
                    .background(Lime, CircleShape)
                    .clickable(onClick = onPlayAll),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = "Play ${playlist.name}",
                    tint = Ink,
                    modifier = Modifier.size(21.dp),
                )
            }
            if (showDelete) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .background(Ink.copy(alpha = 0.55f), CircleShape),
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete ${playlist.name}",
                        tint = TextPrimary,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (playlist.tracks.isEmpty()) "Empty playlist" else "${playlist.tracks.size} tracks",
            style = MaterialTheme.typography.bodyMedium,
            color = if (playlist.tracks.isEmpty()) TextFaint else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun NewPlaylistCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(Panel, RoundedCornerShape(22.dp))
                .border(1.dp, PanelBorder, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Lime.copy(alpha = 0.13f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Lime)
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    text = "New playlist",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Create Playlist",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Build your own mix",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun ImportPlaylistCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(150.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF38080C), Color(0xFF1F080A), Panel)
                    ),
                    RoundedCornerShape(22.dp),
                )
                .border(1.dp, Color(0xFFFF3344).copy(alpha = 0.45f), RoundedCornerShape(22.dp)),
        ) {
            // YouTube Play Badge in center
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 52.dp, height = 36.dp)
                    .background(Color(0xFFFF0000), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }

            // Top-end download indicator
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Bottom badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(9.dp)
                    .background(Color(0xFFFF0000).copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFFF3344).copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "YOUTUBE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF6B6B),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Import YouTube",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "From playlist link",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFFF6B6B),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun ImportPlaylistDialog(
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var urlOrId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        containerColor = Panel,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "Import YouTube Playlist",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "YouTube ya YouTube Music playlist ka link ya ID paste karein:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                OutlinedTextField(
                    value = urlOrId,
                    onValueChange = { urlOrId = it },
                    placeholder = { Text("https://youtube.com/playlist?list=...", color = TextFaint) },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                if (isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Lime,
                            strokeWidth = 2.5.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Importing playlist...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Lime,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading && urlOrId.isNotBlank(),
                onClick = { onImport(urlOrId) },
            ) {
                Text("Import", color = if (!isLoading && urlOrId.isNotBlank()) Lime else TextFaint)
            }
        },
        dismissButton = {
            if (!isLoading) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        },
    )
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = {
            Text(
                text = "New playlist",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = "Give it a name. You can add streamed and offline songs later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(48) },
                    placeholder = { Text("Late night drives") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim()) },
            ) {
                Text("Create", color = if (name.isNotBlank()) Lime else TextFaint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

@Composable
fun AddToPlaylistDialog(
    track: Track,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(playlists.isEmpty()) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = {
            Text(
                text = if (creating) "New playlist" else "Add to playlist",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(14.dp))
                if (creating) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(48) },
                        placeholder = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onAdd(playlist.id) }
                                    .padding(vertical = 11.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(Violet.copy(alpha = 0.16f), RoundedCornerShape(11.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = Violet,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(11.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${playlist.tracks.size} tracks",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextFaint,
                                    )
                                }
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = Lime,
                                )
                            }
                        }
                        item {
                            TextButton(onClick = { creating = true }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = Lime)
                                Spacer(Modifier.width(6.dp))
                                Text("New playlist", color = Lime)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { onCreate(name.trim()) },
                ) {
                    Text("Create & add", color = if (name.isNotBlank()) Lime else TextFaint)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Done", color = Lime)
                }
            }
        },
        dismissButton = {
            if (creating && playlists.isNotEmpty()) {
                TextButton(onClick = { creating = false }) {
                    Text("Back", color = TextSecondary)
                }
            } else if (creating && playlists.isEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionSheet(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String,
    tracks: List<Track>,
    coverUrl: String?,
    selectedTrackId: String?,
    downloadStatuses: Map<String, DownloadStatus>,
    downloadedIds: Set<String>,
    onDownload: (Track) -> Unit,
    onRemoveOffline: (String) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onTrackPlay: (Track) -> Unit,
    onRemoveTrack: (String) -> Unit,
    showRemove: Boolean,
    onDeletePlaylist: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Panel,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = "Delete Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Kya aap \"$title\" playlist delete karna chahte hain?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePlaylist?.invoke()
                    },
                ) {
                    Text("Delete", color = Danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.89f)
                .background(Ink)
                .navigationBarsPadding()
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = Lime,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                if (onDeletePlaylist != null) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete Playlist",
                            tint = Danger,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = TextPrimary,
                    )
                }
            }

            Spacer(Modifier.height(15.dp))
            val allDownloaded = tracks.isNotEmpty() && tracks.all { downloadedIds.contains(it.id) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(
                    label = "Play",
                    icon = Icons.Rounded.PlayArrow,
                    enabled = tracks.isNotEmpty(),
                    onClick = onPlayAll,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    label = "Shuffle",
                    icon = Icons.Rounded.Shuffle,
                    enabled = tracks.isNotEmpty(),
                    onClick = onShuffle,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    label = if (allDownloaded) "Offline" else "Make Offline",
                    icon = if (allDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                    enabled = tracks.isNotEmpty() && !allDownloaded,
                    onClick = {
                        tracks.filter { !downloadedIds.contains(it.id) }.forEach { onDownload(it) }
                    },
                    modifier = Modifier.weight(1f),
                    secondary = allDownloaded,
                )
            }

            Spacer(Modifier.height(16.dp))
            if (tracks.isEmpty()) {
                EmptyState(
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = TextFaint,
                        )
                    },
                    title = "Kuch add nahi hua",
                    description = "Search me kisi song pe playlist icon dabao. New songs here jayenge.",
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(tracks, key = { it.id }) { track ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TrackRow(
                                track = track,
                                isCurrent = selectedTrackId == track.id,
                                isPlaying = selectedTrackId == track.id,
                                isDownloaded = downloadedIds.contains(track.id),
                                downloadStatus = downloadStatuses[track.id],
                                onPlay = { onTrackPlay(track) },
                                onDownload = { onDownload(track) },
                                modifier = Modifier.weight(1f),
                            )
                            if (showRemove) {
                                IconButton(onClick = { onRemoveTrack(track.id) }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Remove ${track.title}",
                                        tint = TextFaint,
                                    )
                                }
                            }
                            if (!showRemove && downloadedIds.contains(track.id)) {
                                IconButton(onClick = { onRemoveOffline(track.id) }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove ${track.title} from offline list",
                                        tint = Coral.copy(alpha = 0.8f),
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

@Composable
private fun PillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
) {
    val container = when {
        !enabled -> Panel.copy(alpha = 0.5f)
        secondary -> Panel
        else -> Lime
    }
    val contentTint = when {
        !enabled -> TextFaint
        secondary -> TextPrimary
        else -> Ink
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .border(1.dp, PanelBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentTint,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentTint,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllPlaylistsSheet(
    onDismiss: () -> Unit,
    playlists: List<Playlist>,
    offlineTracks: List<Track>,
    recentTracks: List<Track>,
    trendingTracks: List<Track>,
    cachedTracks: List<Track>,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenOffline: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenTrending: () -> Unit,
    onOpenCached: () -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onPlayOffline: () -> Unit,
    onPlayRecent: () -> Unit,
    onPlayTrending: () -> Unit,
    onPlayCached: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onNewPlaylist: () -> Unit,
    onImportPlaylist: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val totalCount = playlists.size +
        (if (offlineTracks.isNotEmpty()) 1 else 0) +
        (if (trendingTracks.isNotEmpty()) 1 else 0) +
        (if (recentTracks.isNotEmpty()) 1 else 0) +
        (if (cachedTracks.isNotEmpty()) 1 else 0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .background(Ink)
                .navigationBarsPadding()
                .padding(top = 18.dp, start = 18.dp, end = 18.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "All Playlists",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$totalCount collections available",
                        style = MaterialTheme.typography.labelLarge,
                        color = Lime,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Action buttons row: New Playlist & Import YouTube
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Lime)
                        .clickable {
                            onDismiss()
                            onNewPlaylist()
                        }
                        .padding(vertical = 12.dp, horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            tint = Ink,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "New Playlist",
                            style = MaterialTheme.typography.labelLarge,
                            color = Ink,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Panel)
                        .border(1.dp, Color(0xFFFF3344).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable {
                            onDismiss()
                            onImportPlaylist()
                        }
                        .padding(vertical = 12.dp, horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = Color(0xFFFF4E4E),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Import YT",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFFF4E4E),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Playlists list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // User and Imported Playlists (at the top before offline)
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistItemRow(
                        title = playlist.name,
                        subtitle = if (playlist.tracks.isEmpty()) "Empty playlist" else "${playlist.tracks.size} tracks",
                        coverUrl = playlist.coverUrl,
                        placeholderIcon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        iconTint = Lime,
                        onClick = {
                            onDismiss()
                            onOpenPlaylist(playlist)
                        },
                        onPlay = { onPlayPlaylist(playlist) },
                        onDelete = { onDeletePlaylist(playlist.id) },
                        showDelete = true,
                    )
                }

                // Built-in playlists
                if (offlineTracks.isNotEmpty()) {
                    item(key = "all_offline") {
                        PlaylistItemRow(
                            title = "Offline playlist",
                            subtitle = "${offlineTracks.size} tracks • Downloaded",
                            coverUrl = offlineTracks.firstOrNull()?.thumbnailUrl,
                            placeholderIcon = Icons.Rounded.Download,
                            iconTint = Lime,
                            onClick = {
                                onDismiss()
                                onOpenOffline()
                            },
                            onPlay = onPlayOffline,
                        )
                    }
                }

                if (trendingTracks.isNotEmpty()) {
                    item(key = "all_trending") {
                        PlaylistItemRow(
                            title = "Suggested Trending",
                            subtitle = "${trendingTracks.size} tracks • Popular music",
                            coverUrl = trendingTracks.firstOrNull()?.thumbnailUrl,
                            placeholderIcon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            iconTint = Coral,
                            onClick = {
                                onDismiss()
                                onOpenTrending()
                            },
                            onPlay = onPlayTrending,
                        )
                    }
                }

                if (recentTracks.isNotEmpty()) {
                    item(key = "all_recent") {
                        PlaylistItemRow(
                            title = "Recently played",
                            subtitle = "${recentTracks.size} tracks • History",
                            coverUrl = recentTracks.firstOrNull()?.thumbnailUrl,
                            placeholderIcon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            iconTint = Violet,
                            onClick = {
                                onDismiss()
                                onOpenRecent()
                            },
                            onPlay = onPlayRecent,
                        )
                    }
                }

                if (cachedTracks.isNotEmpty()) {
                    item(key = "all_cached") {
                        PlaylistItemRow(
                            title = "Cached mix",
                            subtitle = "${cachedTracks.size} tracks • Auto-cached",
                            coverUrl = cachedTracks.firstOrNull()?.thumbnailUrl,
                            placeholderIcon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            iconTint = Sky,
                            onClick = {
                                onDismiss()
                                onOpenCached()
                            },
                            onPlay = onPlayCached,
                        )
                    }
                }

                if (totalCount == 0) {
                    item {
                        EmptyState(
                            icon = {
                                Icon(
                                    Icons.AutoMirrored.Rounded.PlaylistPlay,
                                    contentDescription = null,
                                    tint = Lime,
                                    modifier = Modifier.size(48.dp),
                                )
                            },
                            title = "No Playlists Yet",
                            description = "Create a custom playlist or import one from YouTube to get started.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistItemRow(
    title: String,
    subtitle: String,
    coverUrl: String?,
    placeholderIcon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    showDelete: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (coverUrl != null) {
            TrackArtwork(
                url = coverUrl,
                size = 52.dp,
                corner = 12.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(CardMid, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    placeholderIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Quick play button
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Lime.copy(alpha = 0.16f), CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Play $title",
                tint = Lime,
                modifier = Modifier.size(20.dp),
            )
        }

        if (showDelete && onDelete != null) {
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete $title",
                    tint = TextFaint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
