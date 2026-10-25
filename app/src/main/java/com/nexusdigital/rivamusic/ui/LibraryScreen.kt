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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.nexusdigital.rivamusic.data.DownloadedTrack
import com.nexusdigital.rivamusic.data.LikedTrack
import com.nexusdigital.rivamusic.data.Playlist
import com.nexusdigital.rivamusic.data.Track
import com.nexusdigital.rivamusic.data.UserProfile
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.nexusdigital.rivamusic.theme.CardMid
import com.nexusdigital.rivamusic.theme.Coral
import com.nexusdigital.rivamusic.theme.Ink
import com.nexusdigital.rivamusic.theme.Lime
import com.nexusdigital.rivamusic.theme.Panel
import com.nexusdigital.rivamusic.theme.PanelBorder
import com.nexusdigital.rivamusic.theme.PanelElevated
import com.nexusdigital.rivamusic.theme.SectionABottom
import com.nexusdigital.rivamusic.theme.SectionAMid
import com.nexusdigital.rivamusic.theme.SectionATop
import com.nexusdigital.rivamusic.theme.SectionBTop
import com.nexusdigital.rivamusic.theme.SectionDTop
import com.nexusdigital.rivamusic.theme.Sky
import com.nexusdigital.rivamusic.theme.TextFaint
import com.nexusdigital.rivamusic.theme.TextPrimary
import com.nexusdigital.rivamusic.theme.TextSecondary
import com.nexusdigital.rivamusic.theme.Violet

private enum class LibrarySection(val label: String) {
    ALL("All"),
    LIKED("Liked"),
    SONGS("Songs"),
    PLAYLISTS("Playlists"),
    ARTISTS("Artists"),
    DOWNLOADED("Downloaded"),
}

private enum class LibrarySort(val label: String) {
    RECENT("Recently added"),
    TITLE("Title A-Z"),
    ARTIST("Artist A-Z"),
}

private data class LibraryArtist(
    val name: String,
    val tracks: List<Track>,
) {
    val artwork: String? get() = tracks.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
}

/**
 * Metrolist-inspired library: liked songs, derived artists, playlists,
 * downloaded songs, recent history, in-library search, filters and sorting.
 */
@Composable
fun LibraryScreen(
    downloads: List<DownloadedTrack>,
    progress: Map<String, DownloadStatus>,
    cachedTracks: List<Track>,
    cachedBytes: Long,
    recent: List<Track>,
    trendingTracks: List<Track>,
    playlists: List<Playlist>,
    liked: List<LikedTrack>,
    currentId: String?,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayLiked: (Boolean) -> Unit,
    onOpenLiked: () -> Unit,
    onToggleLike: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onNewPlaylist: () -> Unit,
    onImportPlaylist: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenAllPlaylists: () -> Unit,
    onOpenDownloaded: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenCached: () -> Unit,
    onOpenTrending: () -> Unit,
    onPlayOffline: (Boolean) -> Unit,
    onPlayCached: (Boolean) -> Unit,
    onOpenArtist: (String, List<Track>) -> Unit,
    onSearch: () -> Unit,
    userProfile: UserProfile? = null,
    onOpenProfile: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    var sectionName by rememberSaveable { mutableStateOf(LibrarySection.ALL.name) }
    var sortName by rememberSaveable { mutableStateOf(LibrarySort.RECENT.name) }
    var query by rememberSaveable { mutableStateOf("") }

    val section = LibrarySection.entries.firstOrNull { it.name == sectionName } ?: LibrarySection.ALL
    val sort = LibrarySort.entries.firstOrNull { it.name == sortName } ?: LibrarySort.RECENT

    val downloadedTracks = remember(downloads) { downloads.map { it.track } }
    val downloadedIdSet = remember(downloadedTracks) { downloadedTracks.map { it.id }.toSet() }
    val likedIdSet = remember(liked) { liked.map { it.track.id }.toSet() }
    val likedTracks = remember(liked) { liked.map { it.track } }
    val librarySongs = remember(likedTracks, downloadedTracks) {
        (likedTracks + downloadedTracks).distinctBy { it.id }
    }
    val artists = remember(librarySongs) {
        librarySongs
            .groupBy { it.artist }
            .map { (name, tracks) -> LibraryArtist(name, tracks.sortedBy { it.title.lowercase() }) }
            .sortedBy { it.name.lowercase() }
    }

    fun matches(track: Track): Boolean =
        query.isBlank() ||
            track.title.contains(query, ignoreCase = true) ||
            track.artist.contains(query, ignoreCase = true)

    fun sortTracks(tracks: List<Track>): List<Track> = when (sort) {
        LibrarySort.RECENT -> tracks
        LibrarySort.TITLE -> tracks.sortedBy { it.title.lowercase() }
        LibrarySort.ARTIST -> tracks.sortedWith(
            compareBy({ it.artist.lowercase() }, { it.title.lowercase() }),
        )
    }

    val filteredLiked = when (sort) {
        LibrarySort.RECENT -> liked
            .filter { matches(it.track) }
            .sortedByDescending { it.likedAt }
        else -> liked.filter { matches(it.track) }
    }
    val filteredLikedTracks = sortTracks(filteredLiked.map { it.track })
    val filteredDownloads = downloads
        .filter { matches(it.track) }
        .let { entries ->
            when (sort) {
                LibrarySort.RECENT -> entries
                LibrarySort.TITLE -> entries.sortedBy { it.track.title.lowercase() }
                LibrarySort.ARTIST -> entries.sortedBy { it.track.artist.lowercase() }
            }
        }
    val filteredRecent = sortTracks(recent.filter { matches(it) })
    val filteredSongs = sortTracks(librarySongs.filter { matches(it) })
    val filteredPlaylists = playlists
        .filter { playlist ->
            query.isBlank() ||
                playlist.name.contains(query, ignoreCase = true) ||
                playlist.tracks.any { matches(it) }
        }
        .let { list ->
            when (sort) {
                LibrarySort.TITLE -> list.sortedBy { it.name.lowercase() }
                LibrarySort.RECENT -> list.sortedByDescending { it.createdAt }
                LibrarySort.ARTIST -> list.sortedBy { it.name.lowercase() }
            }
        }
    val filteredArtists = artists.filter { artist ->
        query.isBlank() ||
            artist.name.contains(query, ignoreCase = true) ||
            artist.tracks.any { matches(it) }
    }

    val activeDownloads = progress.valueCount()
    val hasContent = liked.isNotEmpty() || downloads.isNotEmpty() || recent.isNotEmpty() ||
        playlists.isNotEmpty() || artists.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Library",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "Everything you saved, liked and played.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Panel)
                                .border(1.dp, PanelBorder, CircleShape)
                                .clickable(onClick = onSearch),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = "Search music",
                                tint = Lime,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        if (userProfile != null) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Panel)
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
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Search your library...", color = TextFaint) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Lime)
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear library search", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
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

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LibrarySection.entries.toList(), key = { it.name }) { option ->
                    LibraryChip(
                        label = option.label,
                        selected = option == section,
                        onClick = { sectionName = option.name },
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (section) {
                        LibrarySection.ALL -> "${liked.size} liked • ${downloads.size} offline • ${playlists.size} playlists"
                        LibrarySection.LIKED -> "${filteredLikedTracks.size} liked songs"
                        LibrarySection.SONGS -> "${filteredSongs.size} songs"
                        LibrarySection.PLAYLISTS -> "${filteredPlaylists.size} playlists"
                        LibrarySection.ARTISTS -> "${filteredArtists.size} artists"
                        LibrarySection.DOWNLOADED -> "${filteredDownloads.size} offline songs"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                LibrarySortChip(
                    sort = sort,
                    onSelect = { sortName = it.name },
                )
            }
        }

        when (section) {
            LibrarySection.ALL -> {
                item {
                    LibraryStorageCard(
                        offlineCount = downloads.size,
                        offlineBytes = downloads.sumOf { it.sizeBytes },
                        cachedCount = cachedTracks.size,
                        cachedBytes = cachedBytes,
                        activeCount = activeDownloads,
                    )
                }

                item {
                    LikedSongsHero(
                        count = liked.size,
                        coverUrl = liked.firstOrNull()?.track?.thumbnailUrl,
                        onOpen = onOpenLiked,
                        onPlay = { onPlayLiked(false) },
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
                            title = "Downloads",
                            subtitle = if (downloads.isEmpty()) "No offline songs" else "${downloads.size} tracks",
                            icon = Icons.Rounded.Download,
                            accentColor = Lime,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenDownloaded,
                            onPlay = { onPlayOffline(false) },
                            hasTracks = downloads.isNotEmpty(),
                        )
                        LibraryQuickCard(
                            title = "Recently played",
                            subtitle = if (recent.isEmpty()) "Nothing yet" else "${recent.size} tracks",
                            icon = Icons.Rounded.History,
                            accentColor = Violet,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenRecent,
                            onPlay = { if (recent.isNotEmpty()) onPlayTrack(recent.first(), recent) },
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
                                    .clip(CircleShape)
                                    .background(Panel)
                                    .border(1.dp, PanelBorder, CircleShape)
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
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(filteredPlaylists, key = { it.id }) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onOpenDetail = { onOpenPlaylist(playlist) },
                                onPlayAll = { onPlayPlaylist(playlist) },
                                onDelete = { onDeletePlaylist(playlist.id) },
                            )
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
                        item(key = "__import_playlist__") {
                            ImportPlaylistCard(onClick = onImportPlaylist)
                        }
                        item(key = "__new_playlist__") {
                            NewPlaylistCard(onClick = onNewPlaylist)
                        }
                    }
                }

                if (filteredArtists.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Artists",
                            subtitle = "Artists from your liked and downloaded songs",
                            action = "See all",
                            onAction = { sectionName = LibrarySection.ARTISTS.name },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(filteredArtists, key = { "artist-bubble-${it.name}" }) { artist ->
                                ArtistBubble(
                                    artist = artist,
                                    onClick = { onOpenArtist(artist.name, artist.tracks) },
                                )
                            }
                        }
                    }
                }

                if (trendingTracks.isNotEmpty() && query.isBlank()) {
                    item {
                        SectionHeader(
                            title = "Trending now",
                            subtitle = "Popular songs you might like",
                            action = "See all",
                            onAction = onOpenTrending,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(trendingTracks, key = { "trending-${it.id}" }) { track ->
                                TrackCard(
                                    track = track,
                                    isCurrent = currentId == track.id,
                                    isDownloaded = downloadedIdSet.contains(track.id),
                                    downloadStatus = progress[track.id],
                                    onPlay = { onPlayTrack(track, trendingTracks) },
                                    onDownload = { onDownload(track) },
                                )
                            }
                        }
                    }
                }

                if (filteredRecent.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Recently played",
                            subtitle = "Pick up where you left off",
                            action = "See all",
                            onAction = onOpenRecent,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    items(filteredRecent.take(5), key = { "recent-${it.id}" }) { track ->
                        TrackRow(
                            track = track,
                            isCurrent = currentId == track.id,
                            isPlaying = currentId == track.id,
                            isDownloaded = downloadedIdSet.contains(track.id),
                            downloadStatus = progress[track.id],
                            onPlay = { onPlayTrack(track, filteredRecent) },
                            onDownload = { onDownload(track) },
                            modifier = Modifier.padding(horizontal = 14.dp),
                            isLiked = track.id in likedIdSet,
                            onToggleLike = { onToggleLike(track) },
                        )
                    }
                }

                if (filteredDownloads.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Downloaded songs",
                            subtitle = "Ready for offline listening",
                            action = "See all",
                            onAction = onOpenDownloaded,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    items(filteredDownloads.take(5), key = { "download-${it.track.id}" }) { download ->
                        DownloadedSongRow(
                            track = download.track,
                            currentId = currentId,
                            progress = progress[download.track.id],
                            isLiked = download.track.id in likedIdSet,
                            onPlay = { onPlayTrack(download.track, filteredDownloads.map { it.track }) },
                            onToggleLike = { onToggleLike(download.track) },
                            onDelete = { onDeleteDownload(download.track.id) },
                        )
                    }
                }

                if (!hasContent) {
                    item {
                        LibraryEmptyState(onSearch = onSearch)
                    }
                } else if (query.isNotBlank() &&
                    filteredLikedTracks.isEmpty() &&
                    filteredDownloads.isEmpty() &&
                    filteredRecent.isEmpty() &&
                    filteredPlaylists.isEmpty() &&
                    filteredArtists.isEmpty()
                ) {
                    item {
                        EmptyState(
                            icon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Sky) },
                            title = "Nothing in your library",
                            description = "No saved song, playlist or artist matches \"${query.trim()}\".",
                        )
                    }
                }
            }

            LibrarySection.LIKED -> {
                item {
                    LikedSongsHero(
                        count = filteredLikedTracks.size,
                        coverUrl = liked.firstOrNull()?.track?.thumbnailUrl,
                        onOpen = onOpenLiked,
                        onPlay = { onPlayLiked(false) },
                    )
                }
                if (filteredLikedTracks.isEmpty()) {
                    item {
                        EmptyState(
                            icon = { Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = Coral) },
                            title = "No liked songs yet",
                            description = "Tap the heart next to any song to build your Liked songs collection.",
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
                                    Text("Find songs")
                                }
                            },
                        )
                    }
                } else {
                    items(filteredLikedTracks, key = { "liked-${it.id}" }) { track ->
                        TrackRow(
                            track = track,
                            isCurrent = currentId == track.id,
                            isPlaying = currentId == track.id,
                            isDownloaded = downloadedIdSet.contains(track.id),
                            downloadStatus = progress[track.id],
                            onPlay = { onPlayTrack(track, filteredLikedTracks) },
                            onDownload = { onDownload(track) },
                            modifier = Modifier.padding(horizontal = 14.dp),
                            isLiked = track.id in likedIdSet,
                            onToggleLike = { onToggleLike(track) },
                        )
                    }
                }
            }

            LibrarySection.SONGS -> {
                if (filteredSongs.isEmpty()) {
                    item {
                        EmptyState(
                            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = Lime) },
                            title = if (query.isBlank()) "No songs in your library" else "No matching songs",
                            description = if (query.isBlank()) {
                                "Like a song or download it for offline listening and it will appear here."
                            } else {
                                "Try a different title or artist name."
                            },
                            action = if (query.isBlank()) {
                                {
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
                                }
                            } else {
                                null
                            },
                        )
                    }
                } else {
                    items(filteredSongs, key = { "song-${it.id}" }) { track ->
                        TrackRow(
                            track = track,
                            isCurrent = currentId == track.id,
                            isPlaying = currentId == track.id,
                            isDownloaded = downloadedIdSet.contains(track.id),
                            downloadStatus = progress[track.id],
                            onPlay = { onPlayTrack(track, filteredSongs) },
                            onDownload = { onDownload(track) },
                            modifier = Modifier.padding(horizontal = 14.dp),
                            isLiked = track.id in likedIdSet,
                            onToggleLike = { onToggleLike(track) },
                        )
                    }
                }
            }

            LibrarySection.PLAYLISTS -> {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LibraryActionButton(
                            label = "New playlist",
                            icon = Icons.Rounded.Add,
                            container = Lime,
                            content = Ink,
                            modifier = Modifier.weight(1f),
                            onClick = onNewPlaylist,
                        )
                        LibraryActionButton(
                            label = "Import YT",
                            icon = Icons.Rounded.Download,
                            container = Panel,
                            content = TextPrimary,
                            modifier = Modifier.weight(1f),
                            onClick = onImportPlaylist,
                        )
                    }
                }
                if (filteredPlaylists.isEmpty()) {
                    item {
                        EmptyState(
                            icon = {
                                Icon(
                                    Icons.AutoMirrored.Rounded.PlaylistPlay,
                                    contentDescription = null,
                                    tint = Violet,
                                )
                            },
                            title = if (query.isBlank()) "No playlists yet" else "No matching playlists",
                            description = if (query.isBlank()) {
                                "Create a playlist or import one from YouTube to get started."
                            } else {
                                "Try a different playlist name."
                            },
                        )
                    }
                } else {
                    items(filteredPlaylists, key = { it.id }) { playlist ->
                        LibraryPlaylistRow(
                            playlist = playlist,
                            onOpen = { onOpenPlaylist(playlist) },
                            onPlay = { onPlayPlaylist(playlist) },
                            onDelete = { onDeletePlaylist(playlist.id) },
                        )
                    }
                }
            }

            LibrarySection.ARTISTS -> {
                if (filteredArtists.isEmpty()) {
                    item {
                        EmptyState(
                            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = Violet) },
                            title = if (query.isBlank()) "No artists yet" else "No matching artists",
                            description = "Artists appear automatically from the songs you like and download.",
                            action = if (query.isBlank()) {
                                {
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
                                }
                            } else {
                                null
                            },
                        )
                    }
                } else {
                    items(filteredArtists.chunked(2), key = { row -> "artists-${row.first().name}" }) { rowArtists ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowArtists.forEach { artist ->
                                ArtistGridCard(
                                    artist = artist,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onOpenArtist(artist.name, artist.tracks) },
                                )
                            }
                            if (rowArtists.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            LibrarySection.DOWNLOADED -> {
                item {
                    LibraryStorageCard(
                        offlineCount = downloads.size,
                        offlineBytes = downloads.sumOf { it.sizeBytes },
                        cachedCount = cachedTracks.size,
                        cachedBytes = cachedBytes,
                        activeCount = activeDownloads,
                    )
                }
                if (filteredDownloads.isEmpty()) {
                    item {
                        EmptyState(
                            icon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = Lime) },
                            title = if (query.isBlank()) "No downloads yet" else "No matching downloads",
                            description = "Tap the download icon on a song to save it for offline listening.",
                            action = if (query.isBlank()) {
                                {
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
                                }
                            } else {
                                null
                            },
                        )
                    }
                } else {
                    items(filteredDownloads, key = { it.track.id }) { download ->
                        DownloadedSongRow(
                            track = download.track,
                            currentId = currentId,
                            progress = progress[download.track.id],
                            isLiked = download.track.id in likedIdSet,
                            onPlay = { onPlayTrack(download.track, filteredDownloads.map { it.track }) },
                            onToggleLike = { onToggleLike(download.track) },
                            onDelete = { onDeleteDownload(download.track.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryChip(
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
private fun LibrarySortChip(
    sort: LibrarySort,
    onSelect: (LibrarySort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Panel)
                .border(1.dp, PanelBorder, CircleShape)
                .clickable { expanded = true }
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sort: ${sort.label}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Sort library",
                tint = TextFaint,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = PanelElevated,
        ) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (option == sort) Lime else TextPrimary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryStorageCard(
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
                    listOf(SectionDTop, CardMid, Panel),
                ),
            )
            .border(1.dp, PanelBorder, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Lime.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = Lime,
                modifier = Modifier.size(27.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${formatBytes(totalBytes)} in storage",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = "$offlineCount offline • $cachedCount cached",
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
private fun LikedSongsHero(
    count: Int,
    coverUrl: String?,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(SectionATop, SectionAMid, SectionABottom),
                ),
            )
            .border(1.dp, PanelBorder, RoundedCornerShape(26.dp))
            .clickable(onClick = onOpen)
            .padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (coverUrl != null) {
            TrackArtwork(url = coverUrl, size = 62.dp, corner = 18.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(Lime.copy(alpha = 0.15f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Lime)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Liked songs",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (count == 0) "Heart songs you love" else "$count songs • your favourites",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (count == 0) Panel.copy(alpha = 0.6f) else Lime)
                .clickable(enabled = count > 0, onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Play liked songs",
                tint = if (count == 0) TextFaint else Ink,
                modifier = Modifier.size(25.dp),
            )
        }
    }
}

@Composable
private fun LibraryQuickCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
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
                    listOf(SectionBTop, Panel, Panel),
                ),
            )
            .border(1.dp, PanelBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(15.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accentColor.copy(alpha = 0.16f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(23.dp),
                )
            }
            if (hasTracks) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(accentColor, CircleShape)
                        .clip(CircleShape)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "Play $title",
                        tint = Ink,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(13.dp))
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

@Composable
private fun ArtistBubble(
    artist: LibraryArtist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(98.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TrackArtwork(
            url = artist.artwork,
            size = 84.dp,
            corner = 42.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${artist.tracks.size} songs",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistGridCard(
    artist: LibraryArtist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .border(1.dp, PanelBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork(
            url = artist.artwork,
            size = 56.dp,
            corner = 28.dp,
        )
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${artist.tracks.size} songs",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LibraryPlaylistRow(
    playlist: Playlist,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .border(1.dp, PanelBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playlist.coverUrl != null) {
            TrackArtwork(url = playlist.coverUrl, size = 56.dp, corner = 14.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Violet.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = Violet,
                    modifier = Modifier.size(25.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (playlist.tracks.isEmpty()) "Empty playlist" else "${playlist.tracks.size} tracks",
                style = MaterialTheme.typography.bodyMedium,
                color = if (playlist.tracks.isEmpty()) TextFaint else TextSecondary,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Lime.copy(alpha = 0.16f), CircleShape)
                .clip(CircleShape)
                .clickable(enabled = playlist.tracks.isNotEmpty(), onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Play ${playlist.name}",
                tint = if (playlist.tracks.isEmpty()) TextFaint else Lime,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Delete ${playlist.name}",
                tint = TextFaint,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun DownloadedSongRow(
    track: Track,
    currentId: String?,
    progress: DownloadStatus?,
    isLiked: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackRow(
            track = track,
            isCurrent = currentId == track.id,
            isPlaying = currentId == track.id,
            isDownloaded = true,
            downloadStatus = progress,
            onPlay = onPlay,
            onDownload = {},
            modifier = Modifier.weight(1f),
            isLiked = isLiked,
            onToggleLike = onToggleLike,
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Rounded.DeleteOutline,
                contentDescription = "Delete ${track.title} from downloads",
                tint = Coral.copy(alpha = 0.85f),
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun LibraryActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .border(1.dp, PanelBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LibraryEmptyState(onSearch: () -> Unit) {
    EmptyState(
        icon = {
            Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = Lime)
        },
        title = "Your library is quiet",
        description = "Like songs or download them for offline listening and they will appear here.",
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

private fun Map<String, DownloadStatus>.valueCount(): Int =
    values.count { it is DownloadStatus.Downloading }
