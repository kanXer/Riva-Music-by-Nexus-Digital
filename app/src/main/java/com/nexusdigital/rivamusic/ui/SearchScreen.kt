package com.nexusdigital.rivamusic.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusdigital.rivamusic.data.DownloadStatus
import com.nexusdigital.rivamusic.data.SearchFilter
import com.nexusdigital.rivamusic.data.SearchItem
import com.nexusdigital.rivamusic.data.SearchItemType
import com.nexusdigital.rivamusic.data.SearchSuggestions
import com.nexusdigital.rivamusic.data.Track
import com.nexusdigital.rivamusic.theme.Coral
import com.nexusdigital.rivamusic.theme.Ink
import com.nexusdigital.rivamusic.theme.Lime
import com.nexusdigital.rivamusic.theme.Panel
import com.nexusdigital.rivamusic.theme.PanelBorder
import com.nexusdigital.rivamusic.theme.PanelElevated
import com.nexusdigital.rivamusic.theme.Sky
import com.nexusdigital.rivamusic.theme.TextFaint
import com.nexusdigital.rivamusic.theme.TextPrimary
import com.nexusdigital.rivamusic.theme.TextSecondary
import com.nexusdigital.rivamusic.theme.Violet

/**
 * Metrolist-style music search:
 *  - live query suggestions from YouTube Music
 *  - recommended songs while typing
 *  - Songs / Albums / Artists / Playlists filters
 *  - recent search history
 */
@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean,
    isSuggesting: Boolean,
    filter: SearchFilter,
    onFilterChange: (SearchFilter) -> Unit,
    tracks: List<Track>,
    items: List<SearchItem>,
    suggestions: SearchSuggestions,
    history: List<String>,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    currentId: String?,
    likedIds: Set<String>,
    downloadStatuses: Map<String, DownloadStatus>,
    downloadedIds: Set<String>,
    onPlay: (Track, List<Track>) -> Unit,
    onDownload: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onOpenItem: (SearchItem) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                onQueryChange(spokenText)
                onSubmit(spokenText)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search songs or artists...")
            }
            try {
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Voice search is not supported on this device", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission is required for voice search", Toast.LENGTH_SHORT).show()
        }
    }

    val launchVoiceSearch = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search songs or artists...")
            }
            try {
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Voice search is not supported on this device", Toast.LENGTH_SHORT).show()
            }
        } else {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    val showRecommendations = filter == SearchFilter.SONGS
    val recommendedTracks = if (showRecommendations) {
        suggestions.items.mapNotNull { it.track }.distinctBy { it.id }
    } else {
        emptyList()
    }
    val recommendedIds = recommendedTracks.map { it.id }.toSet()
    val nonSongSuggestions = if (showRecommendations) {
        suggestions.items.filter { it.type != SearchItemType.SONG && it.type != SearchItemType.VIDEO }
    } else {
        emptyList()
    }

    val visibleItems = if (filter == SearchFilter.SONGS) {
        emptyList()
    } else {
        items
    }
    val visibleTracks = if (filter == SearchFilter.SONGS) {
        tracks.filterNot { it.id in recommendedIds }
    } else {
        emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 18.dp),
            ) {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "Songs, artists, albums and playlists.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Search Riva...", color = TextFaint) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Lime)
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (query.isNotBlank()) {
                            IconButton(onClick = {
                                onClear()
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = TextSecondary)
                            }
                        }
                        IconButton(onClick = launchVoiceSearch) {
                            Icon(
                                Icons.Rounded.Mic,
                                contentDescription = "Voice Search",
                                tint = Lime,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    onSubmit(query)
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Panel,
                    unfocusedContainerColor = Panel,
                    focusedBorderColor = Lime.copy(alpha = 0.7f),
                    unfocusedBorderColor = PanelBorder,
                    cursorColor = Lime,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
            )
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SearchFilter.entries.toList(), key = { it.name }) { option ->
                    SearchFilterChip(
                        label = option.label,
                        selected = option == filter,
                        onClick = { onFilterChange(option) },
                    )
                }
            }
        }

        if (isSearching || isSuggesting) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(CircleShape),
                    color = Lime,
                    trackColor = PanelBorder,
                )
            }
        }

        if (query.isBlank()) {
            if (history.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Recent searches",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearHistory) {
                            Text("Clear", color = Lime)
                        }
                    }
                }
                items(history.take(8), key = { "history-$it" }) { entry ->
                    SearchHistoryRow(
                        query = entry,
                        onClick = {
                            focusManager.clearFocus()
                            onSubmit(entry)
                        },
                        onRemove = { onRemoveHistory(entry) },
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 10.dp),
                ) {
                    Text(
                        text = "Start with a vibe",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(
                            listOf("Arijit Singh", "Lo-Fi", "Bollywood", "Pop", "Chill", "Workout", "Indie", "Romantic"),
                            key = { it },
                        ) { mood ->
                            MoodChip(label = mood, onClick = {
                                focusManager.clearFocus()
                                onSubmit(mood)
                            })
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    SearchHintCard()
                }
            }
        } else {
            if (suggestions.queries.isNotEmpty()) {
                item {
                    Text(
                        text = "Suggestions",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
                    )
                }
                items(suggestions.queries.take(6), key = { "suggestion-$it" }) { suggestion ->
                    SearchSuggestionRow(
                        text = suggestion,
                        onClick = {
                            focusManager.clearFocus()
                            onSubmit(suggestion)
                        },
                    )
                }
            }

            if (recommendedTracks.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Recommended songs",
                        subtitle = "Based on \"${query.trim()}\"",
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(recommendedTracks, key = { "recommended-${it.id}" }) { track ->
                            TrackCard(
                                track = track,
                                isCurrent = currentId == track.id,
                                isDownloaded = downloadedIds.contains(track.id),
                                downloadStatus = downloadStatuses[track.id],
                                onPlay = { onPlay(track, recommendedTracks) },
                                onDownload = { onDownload(track) },
                            )
                        }
                    }
                }
            }

            if (nonSongSuggestions.isNotEmpty()) {
                item {
                    Text(
                        text = "Explore",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
                    )
                }
                items(nonSongSuggestions.take(3), key = { "suggest-${it.type}-${it.id}" }) { item ->
                    SearchItemRow(item = item, onClick = { onOpenItem(item) })
                }
            }

            if (visibleTracks.isNotEmpty()) {
                item {
                    SearchResultHeader(label = "Songs", count = visibleTracks.size)
                }
                items(visibleTracks, key = { "track-${it.id}" }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = currentId == track.id,
                        isPlaying = currentId == track.id,
                        isDownloaded = downloadedIds.contains(track.id),
                        downloadStatus = downloadStatuses[track.id],
                        onPlay = { onPlay(track, visibleTracks) },
                        onDownload = { onDownload(track) },
                        modifier = Modifier.padding(horizontal = 14.dp),
                        isLiked = track.id in likedIds,
                        onToggleLike = { onToggleLike(track) },
                    )
                }
            }

            if (visibleItems.isNotEmpty()) {
                item {
                    SearchResultHeader(label = filter.label, count = visibleItems.size)
                }
                items(visibleItems, key = { "item-${it.type}-${it.id}" }) { item ->
                    SearchItemRow(item = item, onClick = { onOpenItem(item) })
                }
            }

            val hasSuggestionContent = suggestions.queries.isNotEmpty() ||
                recommendedTracks.isNotEmpty() ||
                nonSongSuggestions.isNotEmpty()
            if (!isSearching && !hasSuggestionContent && visibleTracks.isEmpty() && visibleItems.isEmpty()) {
                item {
                    EmptyState(
                        icon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = Sky)
                        },
                        title = "No matches yet",
                        description = "Try a different spelling, an artist name, or a mood like \"lofi\".",
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Lime else Panel)
            .border(1.dp, if (selected) Lime else PanelBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Ink else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun SearchSuggestionRow(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = TextFaint,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(13.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.NorthWest,
            contentDescription = null,
            tint = TextFaint,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun SearchHistoryRow(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 22.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.History,
            contentDescription = null,
            tint = TextFaint,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(13.dp))
        Text(
            text = query,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove $query from history",
                tint = TextFaint,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun SearchResultHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count results",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SearchItemRow(
    item: SearchItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(
            url = item.thumbnailUrl,
            size = 58.dp,
            corner = if (item.type == SearchItemType.ARTIST) 29.dp else 14.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.subtitle.ifBlank { item.type.name.lowercase().replaceFirstChar { it.uppercase() } },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(PanelElevated)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = item.type.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Lime,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextFaint,
            modifier = Modifier.size(22.dp),
        )
    }
}

private val SearchItemType.label: String
    get() = when (this) {
        SearchItemType.SONG -> "Song"
        SearchItemType.VIDEO -> "Video"
        SearchItemType.ALBUM -> "Album"
        SearchItemType.ARTIST -> "Artist"
        SearchItemType.PLAYLIST -> "Playlist"
    }

@Composable
private fun SearchHintCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Panel, PanelElevated, Panel),
                ),
            )
            .border(1.dp, PanelBorder, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Lime.copy(alpha = 0.12f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Lime)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = "Search the whole catalogue",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = "Find songs, albums, artists and playlists. Heart anything you love to build your library.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
