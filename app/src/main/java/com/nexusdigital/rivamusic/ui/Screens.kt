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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusdigital.rivamusic.data.DownloadStatus
import com.nexusdigital.rivamusic.data.DownloadedTrack
import com.nexusdigital.rivamusic.data.HomeSection
import com.nexusdigital.rivamusic.data.Playlist
import com.nexusdigital.rivamusic.data.Track
import com.nexusdigital.rivamusic.data.UserProfile
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.nexusdigital.rivamusic.theme.*
import java.time.LocalTime

@Composable
fun HomeScreen(
    isLoading: Boolean,
    sections: List<HomeSection>,
    recent: List<Track>,
    currentId: String?,
    downloadStatuses: Map<String, DownloadStatus>,
    downloadedIds: Set<String>,
    selectedRegion: MusicRegion = AVAILABLE_REGIONS.first(),
    selectedLanguages: Set<String> = emptySet(),
    userProfile: UserProfile? = null,
    onOpenProfile: () -> Unit = {},
    onOpenLanguages: () -> Unit = {},
    onOpenTrending: () -> Unit = {},
    onPlay: (Track, List<Track>) -> Unit,
    onDownload: (Track) -> Unit,
    onSearch: (String) -> Unit,
    onExplore: () -> Unit,
    onRefresh: () -> Unit,
) {
    val hour = remember { LocalTime.now().hour }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            HomeHeader(
                greeting = greeting,
                isLoading = isLoading,
                onRefresh = onRefresh,
                selectedRegion = selectedRegion,
                selectedLanguages = selectedLanguages,
                userProfile = userProfile,
                onOpenProfile = onOpenProfile,
                onOpenLanguages = onOpenLanguages,
                onSearchClick = onExplore,
            )
        }
        item {
            HeroCard(onExplore = onExplore)
        }
        item {
            val defaultMoods = listOf("Lo-Fi", "Pop", "Chill", "Workout", "Party")
            val displayChips = (selectedLanguages.toList() + defaultMoods).distinct()
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(displayChips) { mood ->
                    val isSelectedLang = selectedLanguages.contains(mood)
                    MoodChip(
                        label = mood,
                        accent = if (isSelectedLang) Lime.copy(alpha = 0.18f) else Panel,
                        onClick = { onSearch(mood) },
                    )
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                SectionBlock(
                    title = "Recently played",
                    subtitle = "Pick up where you left off",
                    tracks = recent,
                    currentId = currentId,
                    downloadStatuses = downloadStatuses,
                    downloadedIds = downloadedIds,
                    onPlay = onPlay,
                    onDownload = onDownload,
                )
            }
        }
        if (isLoading && sections.isEmpty()) {
            item { HomeSkeleton() }
        }
        sections.forEach { section ->
            item(key = "section-${section.id}") {
                SectionBlock(
                    title = section.title,
                    subtitle = section.subtitle,
                    tracks = section.tracks,
                    currentId = currentId,
                    downloadStatuses = downloadStatuses,
                    downloadedIds = downloadedIds,
                    showRank = section.id == "trending",
                    onPlay = onPlay,
                    onDownload = onDownload,
                    action = "See all",
                    onAction = {
                        if (section.id == "trending") {
                            onOpenTrending()
                        } else {
                            onSearch(section.title)
                        }
                    },
                )
            }
        }
        if (!isLoading && sections.isEmpty()) {
            item {
                EmptyState(
                    icon = {
                        Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = Coral)
                    },
                    title = "Couldn't load the feed",
                    description = "Check your connection and try again. YouTube sometimes asks for a quick retry.",
                    action = {
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Lime,
                                contentColor = Ink,
                            ),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    selectedRegion: MusicRegion,
    selectedLanguages: Set<String>,
    userProfile: UserProfile? = null,
    onOpenProfile: () -> Unit = {},
    onOpenLanguages: () -> Unit,
    onSearchClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Brand Logo & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                BrandMark(size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Riva",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Text(
                        text = "by Nexus Digital",
                        style = MaterialTheme.typography.labelSmall,
                        color = Lime,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }

            // Right Actions: Region Pill, Search Button, Refresh Button, Profile Avatar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Region Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(PanelElevated)
                        .border(1.dp, PanelBorder, RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenLanguages)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selectedRegion.flag,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = selectedRegion.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Change region",
                        tint = TextFaint,
                        modifier = Modifier.size(16.dp),
                    )
                }

                // Search Icon Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PanelElevated)
                        .border(1.dp, PanelBorder, CircleShape)
                        .clickable(onClick = onSearchClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                // Refresh Icon Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PanelElevated)
                        .border(1.dp, PanelBorder, CircleShape)
                        .clickable(onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Lime,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Profile Avatar Button
                if (userProfile != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PanelElevated)
                            .border(1.dp, if (!userProfile.isGuest) Lime else PanelBorder, CircleShape)
                            .clickable(onClick = onOpenProfile),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!userProfile.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = userProfile.photoUrl,
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                text = userProfile.displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = Lime,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // Greeting and tagline
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            val personalGreeting = if (userProfile != null) "$greeting, ${userProfile.displayName}" else greeting
            Text(
                text = personalGreeting,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Text(
                text = "What do you want to hear?",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HeroCard(onExplore: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(188.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(SectionATop, SectionAMid, SectionABottom),
                ),
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Lime.copy(alpha = 0.28f), Color.Transparent),
                    ),
                    radius = size.minDimension * 0.85f,
                    center = Offset(size.width * 0.88f, size.height * 0.18f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Coral.copy(alpha = 0.22f), Color.Transparent),
                    ),
                    radius = size.minDimension * 0.7f,
                    center = Offset(size.width * 0.08f, size.height * 1.05f),
                )
            }
            .padding(22.dp),
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Headphones, contentDescription = null, tint = Lime)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "RIVA SELECTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = Lime,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Your day,\nyour soundtrack.",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Stream it. Save it. Own the moment.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Lime)
                    .clickable(onClick = onExplore)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Explore music",
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = Ink,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionBlock(
    title: String,
    subtitle: String?,
    tracks: List<Track>,
    currentId: String?,
    downloadStatuses: Map<String, DownloadStatus>,
    downloadedIds: Set<String>,
    onPlay: (Track, List<Track>) -> Unit,
    onDownload: (Track) -> Unit,
    showRank: Boolean = false,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            action = action,
            onAction = onAction,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(13.dp))
        LazyRow(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            itemsIndexed(tracks, key = { _, it -> it.id }) { index, track ->
                TrackCard(
                    track = track,
                    isCurrent = currentId == track.id,
                    isDownloaded = downloadedIds.contains(track.id),
                    downloadStatus = downloadStatuses[track.id],
                    rank = if (showRank) index + 1 else null,
                    onPlay = { onPlay(track, tracks) },
                    onDownload = { onDownload(track) },
                )
            }
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .width(190.dp)
                .height(24.dp)
                .background(Panel, RoundedCornerShape(8.dp)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(3) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier
                            .size(150.dp)
                            .background(PanelElevated, RoundedCornerShape(22.dp)),
                    )
                    Box(
                        Modifier
                            .width(112.dp)
                            .height(13.dp)
                            .background(Panel, RoundedCornerShape(6.dp)),
                    )
                    Box(
                        Modifier
                            .width(76.dp)
                            .height(10.dp)
                            .background(Panel, RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun LegacySearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    results: List<Track>,
    currentId: String?,
    downloadStatuses: Map<String, DownloadStatus>,
    downloadedIds: Set<String>,
    onPlay: (Track, List<Track>) -> Unit,
    onDownload: (Track) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    text = "Find a song, artist, mood or memory.",
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
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
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

        if (isSearching) {
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
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp),
                ) {
                    Text(
                        text = "Start with a vibe",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(listOf("Arijit Singh", "Lo-Fi", "Bollywood", "Pop", "Chill", "Workout", "Indie", "Romantic")) { mood ->
                            MoodChip(label = mood, onClick = { onQueryChange(mood) })
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    SearchTipCard()
                }
            }
        } else if (!isSearching && results.isEmpty()) {
            item {
                EmptyState(
                    icon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = Sky)
                    },
                    title = "No matches yet",
                    description = "Try a different spelling, an artist name, or a mood like \"lofi\".",
                )
            }
        } else {
            item {
                Text(
                    text = "${results.size} results",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 2.dp),
                )
            }
            items(results, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = currentId == track.id,
                    isPlaying = currentId == track.id,
                    isDownloaded = downloadedIds.contains(track.id),
                    downloadStatus = downloadStatuses[track.id],
                    onPlay = { onPlay(track, results) },
                    onDownload = { onDownload(track) },
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchTipCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Panel)
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
                text = "Tap any result to stream",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = "Hit the download icon to keep a track offline. Saved songs live in Library.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun LegacyLibraryScreen(
    downloads: List<DownloadedTrack>,
    progress: Map<String, DownloadStatus>,
    cachedTracks: List<Track>,
    cachedBytes: Long,
    recent: List<Track>,
    trendingTracks: List<Track>,
    playlists: List<Playlist>,
    currentId: String?,
    onPlayDownloaded: (Track, List<Track>) -> Unit,
    onPlayRecent: (Track, List<Track>) -> Unit,
    onPlayTrending: (Track, List<Track>) -> Unit,
    onOpenTrending: () -> Unit,
    onPlayOffline: (Boolean) -> Unit,
    onPlayCached: (Boolean) -> Unit,
    onOpenCached: () -> Unit,
    onDownload: (Track) -> Unit,
    onDelete: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onNewPlaylist: () -> Unit,
    onImportPlaylist: () -> Unit,
    onOpenOffline: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenAllPlaylists: () -> Unit,
    onSearch: () -> Unit,
) {
    val active = progress.filterValues { it is DownloadStatus.Downloading }
    val totalBytes = downloads.sumOf { it.sizeBytes }
    val downloadedTracks = downloads.map { it.track }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 18.dp),
            ) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "Your playlists, offline stash and history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        item {
            StorageStatsCard(
                offlineCount = downloads.size,
                offlineBytes = totalBytes,
                cachedCount = cachedTracks.size,
                cachedBytes = cachedBytes,
                activeCount = active.size,
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LibraryQuickCard(
                    title = "Offline songs",
                    subtitle = if (downloads.isEmpty()) "No saved songs" else "${downloads.size} tracks",
                    icon = Icons.Rounded.Download,
                    accentColor = Lime,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenOffline,
                    onPlay = { if (downloads.isNotEmpty()) onPlayOffline(false) },
                    hasTracks = downloads.isNotEmpty(),
                )

                LibraryQuickCard(
                    title = "Recently played",
                    subtitle = if (recent.isEmpty()) "No recent tracks" else "${recent.size} tracks",
                    icon = Icons.Rounded.History,
                    accentColor = Violet,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenRecent,
                    onPlay = { if (recent.isNotEmpty()) onPlayRecent(recent.first(), recent) },
                    hasTracks = recent.isNotEmpty(),
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Your collections and imported playlists",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Panel)
                            .border(1.dp, PanelBorder, RoundedCornerShape(50))
                            .clickable(onClick = onOpenAllPlaylists)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "View all",
                            style = MaterialTheme.typography.labelMedium,
                            color = Lime,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Lime, CircleShape)
                            .clip(CircleShape)
                            .clickable(onClick = onNewPlaylist),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "New playlist",
                            tint = Ink,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // User-created & imported playlists
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onOpenDetail = { onOpenPlaylist(playlist) },
                        onPlayAll = { onPlayPlaylist(playlist) },
                        onDelete = { onDeletePlaylist(playlist.id) },
                    )
                }
                item(key = "__import_playlist__") {
                    ImportPlaylistCard(onClick = onImportPlaylist)
                }
                item(key = "__new_playlist__") {
                    NewPlaylistCard(onClick = onNewPlaylist)
                }
                if (trendingTracks.isNotEmpty()) {
                    item(key = "__trending_mix__") {
                        PlaylistCard(
                            playlist = Playlist(
                                id = "__trending_mix__",
                                name = "Suggested Trending",
                                tracks = trendingTracks,
                            ),
                            onOpenDetail = onOpenTrending,
                            onPlayAll = { onPlayTrending(trendingTracks.first(), trendingTracks) },
                            onDelete = {},
                            showDelete = false,
                        )
                    }
                }
                if (cachedTracks.isNotEmpty()) {
                    item(key = "__cached_mix__") {
                        PlaylistCard(
                            playlist = Playlist(
                                id = "__cached_mix__",
                                name = "Cached mix",
                                tracks = cachedTracks,
                            ),
                            onOpenDetail = onOpenCached,
                            onPlayAll = { onPlayCached(false) },
                            onDelete = {},
                            showDelete = false,
                        )
                    }
                }
            }
        }

        if (trendingTracks.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Suggested Trending",
                    subtitle = "Popular songs you might like",
                    action = "Play all",
                    onAction = { onPlayTrending(trendingTracks.first(), trendingTracks) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(trendingTracks, key = { "trend-card-${it.id}" }) { track ->
                        TrackCard(
                            track = track,
                            isCurrent = currentId == track.id,
                            isDownloaded = downloads.any { it.track.id == track.id },
                            downloadStatus = progress[track.id],
                            onPlay = { onPlayTrending(track, trendingTracks) },
                            onDownload = { onDownload(track) },
                        )
                    }
                }
            }
        }

        if (active.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Downloading",
                    subtitle = "${active.size} in progress",
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            items(active.entries.toList(), key = { it.key }) { (trackId, status) ->
                val track = findTrack(trackId, downloads, recent)
                if (track != null) {
                    DownloadingRow(track = track, status = status as DownloadStatus.Downloading)
                }
            }
        }

        if (downloads.isEmpty() && recent.isEmpty() && active.isEmpty() && playlists.isEmpty()) {
            item {
                EmptyState(
                    icon = {
                        Icon(Icons.Rounded.OfflinePin, contentDescription = null, tint = Lime)
                    },
                    title = "Your library is quiet",
                    description = "Search for a track and tap the download icon. It will show up here, ready for offline listening.",
                    action = {
                        Button(
                            onClick = onSearch,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Lime,
                                contentColor = Ink,
                            ),
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Find music")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun OfflineMixCard(
    tracks: List<Track>,
    totalBytes: Long,
    onOpen: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(SectionBTop, SectionBMid, SectionBBottom),
                ),
            )
            .border(1.dp, PanelBorder, RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tracks.firstOrNull()?.thumbnailUrl != null) {
            TrackArtwork(
                url = tracks.first().thumbnailUrl,
                size = 68.dp,
                corner = 18.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(Lime.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.OfflinePin, contentDescription = null, tint = Lime)
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Offline playlist",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = "${tracks.size} tracks • ${formatBytes(totalBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Lime)
                        .clickable(onClick = onPlayAll)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Ink,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Play all",
                            style = MaterialTheme.typography.labelMedium,
                            color = Ink,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, PanelBorder, CircleShape)
                        .clickable(onClick = onShuffle)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = Lime,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Shuffle",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CachedMixCard(
    tracks: List<Track>,
    onOpen: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(SectionCTop, SectionCMid, SectionCBottom),
                ),
            )
            .border(1.dp, PanelBorder, RoundedCornerShape(24.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tracks.firstOrNull()?.thumbnailUrl != null) {
            TrackArtwork(
                url = tracks.first().thumbnailUrl,
                size = 68.dp,
                corner = 18.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(Lime.copy(alpha = 0.12f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = Lime)
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Cached mix",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = "${tracks.size} recent • offline for 30 days",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Lime)
                        .clickable(onClick = onPlayAll)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Ink,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Play all",
                            style = MaterialTheme.typography.labelMedium,
                            color = Ink,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .border(1.dp, PanelBorder, CircleShape)
                        .clickable(onClick = onShuffle)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = Lime,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Shuffle",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageStatsCard(
    offlineCount: Int,
    offlineBytes: Long,
    cachedCount: Int,
    cachedBytes: Long,
    activeCount: Int,
) {
    val totalBytes = offlineBytes + cachedBytes
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(SectionDTop, SectionDMid, SectionDBottom),
                ),
            )
            .border(1.dp, PanelBorder, RoundedCornerShape(24.dp))
            .padding(19.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(Lime.copy(alpha = 0.12f), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = Lime, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(15.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatBytes(totalBytes)} in storage",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = "$offlineCount offline • $cachedCount cached (30 days)",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (activeCount > 0) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Lime,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = TextFaint)
        }
    }
}

@Composable
private fun DownloadingRow(track: Track, status: DownloadStatus.Downloading) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(url = track.thumbnailUrl, size = 52.dp, corner = 13.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { status.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
                color = Lime,
                trackColor = PanelBorder,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${(status.progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = Lime,
        )
    }
}

private fun findTrack(
    id: String,
    downloads: List<DownloadedTrack>,
    recent: List<Track>,
): Track? = downloads.firstOrNull { it.track.id == id }?.track
    ?: recent.firstOrNull { it.id == id }

@Composable
private fun LibraryQuickCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    hasTracks: Boolean,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(SectionBTop, SectionBMid, Panel),
                ),
            )
            .border(1.dp, PanelBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accentColor.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp),
                )
            }

            if (hasTracks) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(accentColor, CircleShape)
                        .clip(CircleShape)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Play $title",
                        tint = Ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = if (hasTracks) TextSecondary else TextFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
