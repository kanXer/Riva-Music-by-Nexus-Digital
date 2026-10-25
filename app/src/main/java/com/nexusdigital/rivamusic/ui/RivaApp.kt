package com.nexusdigital.rivamusic.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.nexusdigital.rivamusic.data.UserProfile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusdigital.rivamusic.RivaViewModel
import com.nexusdigital.rivamusic.data.DownloadRepository
import com.nexusdigital.rivamusic.data.DownloadStatus
import com.nexusdigital.rivamusic.data.DownloadRepository.Companion.CACHE_TTL_MS
import com.nexusdigital.rivamusic.data.Playlist
import com.nexusdigital.rivamusic.data.LyricLine
import com.nexusdigital.rivamusic.data.RepeatMode
import com.nexusdigital.rivamusic.data.Track
import com.nexusdigital.rivamusic.playback.PlayerUiState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.key
import com.nexusdigital.rivamusic.theme.*
import com.nexusdigital.rivamusic.LyricsUi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RivaApp(viewModel: RivaViewModel = viewModel()) {
    val homeSections by viewModel.homeSections.collectAsStateWithLifecycle()
    val isLoadingHome by viewModel.isLoadingHome.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchFilter by viewModel.searchFilter.collectAsStateWithLifecycle()
    val searchItems by viewModel.searchItems.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    val isSuggesting by viewModel.isSuggesting.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val searchCollection by viewModel.searchCollection.collectAsStateWithLifecycle()
    val isOpeningCollection by viewModel.isOpeningCollection.collectAsStateWithLifecycle()
    val liked by viewModel.liked.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val nowMs = remember { System.currentTimeMillis() }
    val cachedTracks = remember(downloads, nowMs) {
        downloads.filter { it.isCached && nowMs - it.cachedAt <= CACHE_TTL_MS }.map { it.track }
    }
    // Offline (Saved) section holds only manually saved tracks — auto-cached
    // songs live exclusively in the Cached mix for their 30-day window.
    val manualDownloads = remember(downloads) { downloads.filterNot { it.isCached } }
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val trendingTracks by viewModel.trendingTracks.collectAsStateWithLifecycle()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val selectedRegion by viewModel.selectedRegion.collectAsStateWithLifecycle()
    val selectedLanguages by viewModel.selectedLanguages.collectAsStateWithLifecycle()
    val showLanguagePrompt by viewModel.showLanguagePrompt.collectAsStateWithLifecycle()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
    val sleepRemainingSeconds by viewModel.sleepRemainingSeconds.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val isResolving by viewModel.isResolving.collectAsStateWithLifecycle()
    val queueNow by viewModel.queueTracks.collectAsStateWithLifecycle()
    val lyricsNow by viewModel.lyrics.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val showAuthPrompt by viewModel.showAuthPrompt.collectAsStateWithLifecycle()

    // "Saved offline" only counts manual saves; 30-day auto caches still show
    // the promote (⬇) option until permanently saved.
    val downloadedIds = remember(downloads) {
        downloads.filterNot { it.isCached }.map { it.track.id }.toSet()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // App hamesha Home par khule; full player sirf tab dikhe jab user
    // khud koi song/collection play kare (ya mini-player tap kare).
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylist by rememberSaveable { mutableStateOf(false) }
    var showImportPlaylist by rememberSaveable { mutableStateOf(false) }
    val isImportingPlaylist by viewModel.isImportingPlaylist.collectAsStateWithLifecycle()
    var playlistTrackToAdd by remember { mutableStateOf<Track?>(null) }
    var showSleepTimer by rememberSaveable { mutableStateOf(false) }
    var showProfileSheet by rememberSaveable { mutableStateOf(false) }
    var showOfflineSheet by rememberSaveable { mutableStateOf(false) }
    var showRecentSheet by rememberSaveable { mutableStateOf(false) }
    var showTrendingSheet by rememberSaveable { mutableStateOf(false) }
    var showCachedSheet by rememberSaveable { mutableStateOf(false) }
    var showAllPlaylists by rememberSaveable { mutableStateOf(false) }
    var showLikedSheet by rememberSaveable { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var selectedArtist by remember { mutableStateOf<Pair<String, List<Track>>?>(null) }

    val context = LocalContext.current

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        runCatching {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (!idToken.isNullOrBlank()) {
                viewModel.signInWithGoogle(idToken) { success, error ->
                    if (!success && error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Sign in failed: No Google ID Token", Toast.LENGTH_SHORT).show()
            }
        }.onFailure { e ->
            Toast.makeText(context, "Google Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerGoogleSignIn() {
        val client = viewModel.getGoogleSignInClient()
        googleSignInLauncher.launch(client.signInIntent)
    }

    // Song select karne par hi full player kholo. Ye helper home/search/library
    // aur collection sheets — sab jagah use hota hai.
    fun playSelectedTrack(
        track: Track,
        queue: List<Track> = listOf(track),
        isPlaylist: Boolean = false,
    ) {
        viewModel.playTrack(track, queue, isPlaylist)
        showPlayer = true
    }

    // Back pe pehle top-most dialog/sheet/player band karo, phir Home tab
    // par jao. Home par back hi app exit karega.
    BackHandler(
        enabled = showProfileSheet || showLanguagePrompt || showSleepTimer || showCreatePlaylist ||
            showImportPlaylist || playlistTrackToAdd != null || showPlayer ||
            showAllPlaylists || showOfflineSheet || showCachedSheet ||
            showRecentSheet || showTrendingSheet || showLikedSheet ||
            selectedPlaylist != null || selectedArtist != null ||
            searchCollection != null || isOpeningCollection || selectedTab != 0,
    ) {
        when {
            showProfileSheet -> showProfileSheet = false
            showLanguagePrompt -> viewModel.dismissLanguageSelector()
            showSleepTimer -> showSleepTimer = false
            showCreatePlaylist -> showCreatePlaylist = false
            showImportPlaylist -> showImportPlaylist = false
            playlistTrackToAdd != null -> playlistTrackToAdd = null
            isOpeningCollection -> Unit
            showPlayer -> showPlayer = false
            showAllPlaylists -> showAllPlaylists = false
            selectedPlaylist != null -> selectedPlaylist = null
            selectedArtist != null -> selectedArtist = null
            searchCollection != null -> viewModel.closeSearchCollection()
            showOfflineSheet -> showOfflineSheet = false
            showCachedSheet -> showCachedSheet = false
            showRecentSheet -> showRecentSheet = false
            showTrendingSheet -> showTrendingSheet = false
            showLikedSheet -> showLikedSheet = false
            selectedTab != 0 -> selectedTab = 0
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // App khule to net check: net connection na mile to direct offline songs list kholo
    LaunchedEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(network)
        val hasNet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        if (!hasNet) {
            selectedTab = 2 // Library screen
            showPlayer = false
            showOfflineSheet = true
        }
    }

    // System dark/light switch par PURA UI re-read karo — warna skipped
    // composables purane theme colors dikhate rehte hain.
    val systemDark = isSystemInDarkTheme()
    key(systemDark) {
        if (currentUser == null) {
            WelcomeAuthScreen(
                onGoogleSignInClick = { triggerGoogleSignIn() },
                onGuestSignInSubmit = { name ->
                    viewModel.signInAsGuest(name) {}
                },
            )
        } else {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BgOuter1, Ink, BgDeep),
                ),
            ),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = currentTrack != null,
                        enter = slideInVertically { it / 2 } + fadeIn(),
                        exit = slideOutVertically { it / 2 } + fadeOut(),
                    ) {
                        currentTrack?.let { track ->
                            MiniPlayer(
                                track = track,
                                state = playerState,
                                isResolving = isResolving,
                                onOpen = { showPlayer = true },
                                onToggle = viewModel::togglePlayPause,
                            )
                        }
                    }
                    RivaBottomBar(
                        selectedTab = selectedTab,
                        onSelect = { selectedTab = it },
                    )
                }
            },
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    0 -> HomeScreen(
                        isLoading = isLoadingHome,
                        sections = homeSections,
                        recent = recent,
                        currentId = currentTrack?.id ?: playerState.mediaId,
                        downloadStatuses = downloadProgress,
                        downloadedIds = downloadedIds,
                        selectedRegion = selectedRegion,
                        selectedLanguages = selectedLanguages,
                        userProfile = currentUser,
                        onOpenProfile = { showProfileSheet = true },
                        onOpenLanguages = { viewModel.openLanguageSelector() },
                        onOpenTrending = { showTrendingSheet = true },
                        onPlay = { track, queue -> playSelectedTrack(track, queue) },
                        onDownload = viewModel::download,
                        onSearch = {
                            viewModel.setSearchQuery(it)
                            selectedTab = 1
                        },
                        onExplore = { selectedTab = 1 },
                        onRefresh = { viewModel.loadHome(force = true) },
                    )
                    1 -> SearchScreen(
                        query = searchQuery,
                        onQueryChange = viewModel::setSearchQuery,
                        onSubmit = { viewModel.submitSearch(it) },
                        onClear = viewModel::clearSearch,
                        isSearching = isSearching,
                        isSuggesting = isSuggesting,
                        filter = searchFilter,
                        onFilterChange = viewModel::setSearchFilter,
                        tracks = searchResults,
                        items = searchItems,
                        suggestions = searchSuggestions,
                        history = searchHistory,
                        onRemoveHistory = viewModel::removeSearchHistory,
                        onClearHistory = viewModel::clearSearchHistory,
                        currentId = currentTrack?.id ?: playerState.mediaId,
                        likedIds = likedIds,
                        downloadStatuses = downloadProgress,
                        downloadedIds = downloadedIds,
                        onPlay = { track, queue -> playSelectedTrack(track, queue) },
                        onDownload = viewModel::download,
                        onToggleLike = viewModel::toggleLiked,
                        onOpenItem = viewModel::openSearchItem,
                    )
                    else -> LibraryScreen(
                        downloads = manualDownloads,
                        progress = downloadProgress,
                        cachedTracks = cachedTracks,
                        cachedBytes = downloads
                            .filter { it.isCached && nowMs - it.cachedAt <= CACHE_TTL_MS }
                            .sumOf { it.sizeBytes },
                        recent = recent,
                        trendingTracks = trendingTracks,
                        playlists = playlists,
                        liked = liked,
                        currentId = currentTrack?.id ?: playerState.mediaId,
                        onPlayTrack = { track, queue -> playSelectedTrack(track, queue) },
                        onPlayLiked = { shuffle ->
                            if (liked.isNotEmpty()) {
                                viewModel.playLiked(shuffle)
                                showPlayer = true
                            }
                        },
                        onOpenLiked = { showLikedSheet = true },
                        onToggleLike = viewModel::toggleLiked,
                        onDownload = viewModel::download,
                        onDeleteDownload = viewModel::deleteDownload,
                        onPlayPlaylist = viewModel::playPlaylist,
                        onDeletePlaylist = viewModel::deletePlaylist,
                        onNewPlaylist = { showCreatePlaylist = true },
                        onImportPlaylist = { showImportPlaylist = true },
                        onOpenPlaylist = { playlist -> selectedPlaylist = playlist },
                        onOpenAllPlaylists = { showAllPlaylists = true },
                        onOpenDownloaded = { showOfflineSheet = true },
                        onOpenRecent = { showRecentSheet = true },
                        onOpenCached = { showCachedSheet = true },
                        onOpenTrending = { showTrendingSheet = true },
                        onPlayOffline = { shuffle -> viewModel.playOffline(shuffle) },
                        onPlayCached = { shuffle -> viewModel.playCached(shuffle) },
                        onOpenArtist = { name, tracks -> selectedArtist = name to tracks },
                        onSearch = { selectedTab = 1 },
                        userProfile = currentUser,
                        onOpenProfile = { showProfileSheet = true },
                    )
                }
            }
        }
    }

    val sheetTrack = currentTrack
    if (showPlayer) {
        if (sheetTrack != null) {
            PlayerSheet(
                track = sheetTrack,
                state = playerState,
                isResolving = isResolving,
                downloadedIds = downloadedIds,
                downloadStatuses = downloadProgress,
                likedIds = likedIds,
                queue = queueNow,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                sleepTimerMinutes = sleepTimerMinutes,
                sleepRemainingSeconds = sleepRemainingSeconds,
                onDismiss = { showPlayer = false },
                onSearch = {
                    showPlayer = false
                    selectedTab = 1
                },
                onToggle = viewModel::togglePlayPause,
                onNext = viewModel::playNext,
                onPrevious = viewModel::playPrevious,
                onSeek = viewModel::seekTo,
                onDownloadTrack = viewModel::download,
                onPlayPage = { page -> viewModel.playReelPage(page) },
                onViewOpened = { viewModel.ensureRelatedForSingle() },
                lyrics = lyricsNow,
                onLyrics = viewModel::requestLyrics,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
                onSleepTimer = { showSleepTimer = true },
                onAddToPlaylist = { playlistTrackToAdd = sheetTrack },
                onToggleLike = viewModel::toggleLiked,
                onCancelSleepTimer = { viewModel.setSleepTimer(null) },
            )
        } else {
            PlayerLoadingSheet(
                onDismiss = { showPlayer = false },
                onSearch = {
                    showPlayer = false
                    selectedTab = 1
                },
            )
        }
    }

    if (showLanguagePrompt) {
        LanguageSelectionDialog(
            initialRegion = selectedRegion,
            initialSelected = selectedLanguages,
            onDismiss = { viewModel.dismissLanguageSelector() },
            onSave = { region, chosen ->
                viewModel.updatePreferences(region, chosen)
            },
        )
    }

    if (showCreatePlaylist) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylist = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showCreatePlaylist = false
            },
        )
    }

    val addTrack = playlistTrackToAdd
    if (addTrack != null) {
        AddToPlaylistDialog(
            track = addTrack,
            playlists = playlists,
            onDismiss = { playlistTrackToAdd = null },
            onAdd = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, addTrack)
                playlistTrackToAdd = null
            },
            onCreate = { name ->
                viewModel.createPlaylistWithTrack(name, addTrack)
                playlistTrackToAdd = null
            },
        )
    }

    if (showSleepTimer) {
        SleepTimerDialog(
            currentMinutes = sleepTimerMinutes,
            onDismiss = { showSleepTimer = false },
            onSelect = { minutes ->
                viewModel.setSleepTimer(minutes)
                showSleepTimer = false
            },
        )
    }

    if (showOfflineSheet) {
        val offlineTracks = manualDownloads.map { it.track }
        val totalBytes = manualDownloads.sumOf { it.sizeBytes }
        if (offlineTracks.isEmpty()) {
            showOfflineSheet = false
        } else {
            CollectionSheet(
                onDismiss = { showOfflineSheet = false },
                title = "Offline playlist",
                subtitle = "${offlineTracks.size} tracks • ${formatBytes(totalBytes)}",
                tracks = offlineTracks,
                coverUrl = offlineTracks.first().thumbnailUrl,
                selectedTrackId = currentTrack?.id ?: playerState.mediaId,
                downloadStatuses = downloadProgress,
                downloadedIds = downloadedIds,
                onDownload = viewModel::download,
                onRemoveOffline = viewModel::deleteDownload,
                onPlayAll = {
                    viewModel.playAll(offlineTracks, shuffleEnabled, isPlaylist = true)
                },
                onShuffle = { viewModel.playAll(offlineTracks, true, isPlaylist = true) },
                onTrackPlay = { track ->
                    showOfflineSheet = false
                    playSelectedTrack(track, offlineTracks, isPlaylist = true)
                },
                onRemoveTrack = {},
                showRemove = false,
            )
        }
    }

    if (showCachedSheet) {
        val cacheEntries = downloads.filter { it.isCached && nowMs - it.cachedAt <= CACHE_TTL_MS }
        val cacheTrackList = cacheEntries.map { it.track }
        if (cacheTrackList.isEmpty()) {
            showCachedSheet = false
        } else {
            CollectionSheet(
                onDismiss = { showCachedSheet = false },
                title = "Cached mix",
                subtitle = "Tap ⬇ to keep offline forever • else 30 days",
                tracks = cacheTrackList,
                coverUrl = cacheTrackList.first().thumbnailUrl,
                selectedTrackId = currentTrack?.id ?: playerState.mediaId,
                downloadStatuses = emptyMap(),
                downloadedIds = emptySet(),
                onDownload = viewModel::promoteCachedToOffline,
                onRemoveOffline = viewModel::deleteDownload,
                onPlayAll = { viewModel.playAll(cacheTrackList, shuffleEnabled, isPlaylist = true) },
                onShuffle = { viewModel.playAll(cacheTrackList, true, isPlaylist = true) },
                onTrackPlay = { track ->
                    showCachedSheet = false
                    playSelectedTrack(track, cacheTrackList, isPlaylist = true)
                },
                onRemoveTrack = {},
                showRemove = false,
            )
        }
    }

    val detailPlaylist = selectedPlaylist
    if (detailPlaylist != null) {
        val playlist = playlists.firstOrNull { it.id == detailPlaylist.id } ?: detailPlaylist
        CollectionSheet(
            onDismiss = { selectedPlaylist = null },
            title = playlist.name,
            subtitle = if (playlist.tracks.isEmpty()) "Kuch add nahi hua" else "${playlist.tracks.size} tracks",
            tracks = playlist.tracks,
            coverUrl = playlist.coverUrl,
            selectedTrackId = currentTrack?.id ?: playerState.mediaId,
            downloadStatuses = downloadProgress,
            downloadedIds = downloadedIds,
            onDownload = viewModel::download,
            onRemoveOffline = {},
            onPlayAll = { viewModel.playPlaylist(playlist) },
            onShuffle = {
                val shuffled = playlist.tracks.shuffled()
                if (shuffled.isNotEmpty()) {
                    viewModel.playAll(shuffled, true, isPlaylist = true)
                }
            },
            onTrackPlay = { track ->
                selectedPlaylist = null
                playSelectedTrack(track, playlist.tracks, isPlaylist = true)
            },
            onRemoveTrack = { viewModel.removeTrackFromPlaylist(playlist.id, it) },
            showRemove = true,
            onDeletePlaylist = {
                viewModel.deletePlaylist(playlist.id)
                selectedPlaylist = null
            },
        )
    }

    if (showRecentSheet) {
        if (recent.isEmpty()) {
            showRecentSheet = false
        } else {
            CollectionSheet(
                onDismiss = { showRecentSheet = false },
                title = "Recently played",
                subtitle = "${recent.size} tracks",
                tracks = recent,
                coverUrl = recent.firstOrNull()?.thumbnailUrl,
                selectedTrackId = currentTrack?.id ?: playerState.mediaId,
                downloadStatuses = downloadProgress,
                downloadedIds = downloadedIds,
                onDownload = viewModel::download,
                onRemoveOffline = viewModel::deleteDownload,
                onPlayAll = { viewModel.playAll(recent, shuffleEnabled, isPlaylist = true) },
                onShuffle = { viewModel.playAll(recent, true, isPlaylist = true) },
                onTrackPlay = { track ->
                    showRecentSheet = false
                    playSelectedTrack(track, recent, isPlaylist = true)
                },
                onRemoveTrack = {},
                showRemove = false,
            )
        }
    }

    if (showTrendingSheet) {
        if (trendingTracks.isEmpty()) {
            showTrendingSheet = false
        } else {
            CollectionSheet(
                onDismiss = { showTrendingSheet = false },
                title = "Trending in ${selectedRegion.name}",
                subtitle = "${trendingTracks.size} hot tracks • ${selectedRegion.flag}",
                tracks = trendingTracks,
                coverUrl = trendingTracks.firstOrNull()?.thumbnailUrl,
                selectedTrackId = currentTrack?.id ?: playerState.mediaId,
                downloadStatuses = downloadProgress,
                downloadedIds = downloadedIds,
                onDownload = viewModel::download,
                onRemoveOffline = viewModel::deleteDownload,
                onPlayAll = { viewModel.playAll(trendingTracks, shuffleEnabled, isPlaylist = true) },
                onShuffle = { viewModel.playAll(trendingTracks, true, isPlaylist = true) },
                onTrackPlay = { track ->
                    showTrendingSheet = false
                    playSelectedTrack(track, trendingTracks, isPlaylist = true)
                },
                onRemoveTrack = {},
                showRemove = false,
            )
        }
    }

    if (showLikedSheet) {
        val likedTracks = liked.map { it.track }
        if (likedTracks.isEmpty()) {
            showLikedSheet = false
        } else {
            CollectionSheet(
                onDismiss = { showLikedSheet = false },
                title = "Liked songs",
                subtitle = "${likedTracks.size} songs • saved to your library",
                tracks = likedTracks,
                coverUrl = likedTracks.firstOrNull()?.thumbnailUrl,
                selectedTrackId = currentTrack?.id ?: playerState.mediaId,
                downloadStatuses = downloadProgress,
                downloadedIds = downloadedIds,
                onDownload = viewModel::download,
                onRemoveOffline = viewModel::deleteDownload,
                onPlayAll = { viewModel.playLiked(false) },
                onShuffle = { viewModel.playLiked(true) },
                onTrackPlay = { track ->
                    showLikedSheet = false
                    playSelectedTrack(track, likedTracks, isPlaylist = true)
                },
                onRemoveTrack = viewModel::unlikeTrack,
                showRemove = true,
            )
        }
    }

    val artistDetail = selectedArtist
    if (artistDetail != null) {
        val (artistName, artistTracks) = artistDetail
        CollectionSheet(
            onDismiss = { selectedArtist = null },
            title = artistName,
            subtitle = "${artistTracks.size} songs in your library",
            tracks = artistTracks,
            coverUrl = artistTracks.firstOrNull()?.thumbnailUrl,
            selectedTrackId = currentTrack?.id ?: playerState.mediaId,
            downloadStatuses = downloadProgress,
            downloadedIds = downloadedIds,
            onDownload = viewModel::download,
            onRemoveOffline = viewModel::deleteDownload,
            onPlayAll = {
                viewModel.playAll(artistTracks, shuffleEnabled, isPlaylist = true)
            },
            onShuffle = { viewModel.playAll(artistTracks, true, isPlaylist = true) },
            onTrackPlay = { track ->
                selectedArtist = null
                playSelectedTrack(track, artistTracks, isPlaylist = true)
            },
            onRemoveTrack = {},
            showRemove = false,
        )
    }

    val openedCollection = searchCollection
    if (openedCollection != null) {
        CollectionSheet(
            onDismiss = viewModel::closeSearchCollection,
            title = openedCollection.title,
            subtitle = openedCollection.subtitle,
            tracks = openedCollection.tracks,
            coverUrl = openedCollection.tracks.firstOrNull()?.thumbnailUrl,
            selectedTrackId = currentTrack?.id ?: playerState.mediaId,
            downloadStatuses = downloadProgress,
            downloadedIds = downloadedIds,
            onDownload = viewModel::download,
            onRemoveOffline = viewModel::deleteDownload,
            onPlayAll = {
                viewModel.playAll(openedCollection.tracks, shuffleEnabled, isPlaylist = true)
            },
            onShuffle = {
                viewModel.playAll(openedCollection.tracks, true, isPlaylist = true)
            },
            onTrackPlay = { track ->
                viewModel.closeSearchCollection()
                playSelectedTrack(track, openedCollection.tracks, isPlaylist = true)
            },
            onRemoveTrack = {},
            showRemove = false,
        )
    }

    if (showImportPlaylist) {
        ImportPlaylistDialog(
            isLoading = isImportingPlaylist,
            onDismiss = { showImportPlaylist = false },
            onImport = { urlOrId ->
                viewModel.importYouTubePlaylist(urlOrId) { success ->
                    if (success) {
                        showImportPlaylist = false
                    }
                }
            },
        )
    }

    if (showAllPlaylists) {
        val offlineTracks = manualDownloads.map { it.track }
        val cacheEntries = downloads.filter { it.isCached && nowMs - it.cachedAt <= CACHE_TTL_MS }
        val cacheTrackList = cacheEntries.map { it.track }
        AllPlaylistsSheet(
            onDismiss = { showAllPlaylists = false },
            playlists = playlists,
            offlineTracks = offlineTracks,
            recentTracks = recent,
            trendingTracks = trendingTracks,
            cachedTracks = cacheTrackList,
            onOpenPlaylist = { playlist ->
                showAllPlaylists = false
                selectedPlaylist = playlist
            },
            onOpenOffline = {
                showAllPlaylists = false
                showOfflineSheet = true
            },
            onOpenRecent = {
                showAllPlaylists = false
                showRecentSheet = true
            },
            onOpenTrending = {
                showAllPlaylists = false
                showTrendingSheet = true
            },
            onOpenCached = {
                showAllPlaylists = false
                showCachedSheet = true
            },
            onPlayPlaylist = viewModel::playPlaylist,
            onPlayOffline = { viewModel.playOffline(false) },
            onPlayRecent = {
                if (recent.isNotEmpty()) {
                    viewModel.playTrack(recent.first(), recent, isPlaylist = true)
                }
            },
            onPlayTrending = {
                if (trendingTracks.isNotEmpty()) {
                    viewModel.playTrack(trendingTracks.first(), trendingTracks, isPlaylist = true)
                }
            },
            onPlayCached = { viewModel.playCached(false) },
            onDeletePlaylist = viewModel::deletePlaylist,
            onNewPlaylist = {
                showAllPlaylists = false
                showCreatePlaylist = true
            },
            onImportPlaylist = {
                showAllPlaylists = false
                showImportPlaylist = true
            },
        )
    }

    if (isOpeningCollection) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    color = Lime,
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Opening on YouTube Music...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                )
            }
        }
    }

    if (showProfileSheet && currentUser != null) {
        ProfileBottomSheet(
            userProfile = currentUser!!,
            onDismiss = { showProfileSheet = false },
            onGoogleSignIn = {
                showProfileSheet = false
                triggerGoogleSignIn()
            },
            onChangeLanguages = {
                viewModel.openLanguageSelector()
            },
            onSignOut = {
                showProfileSheet = false
                viewModel.signOut(viewModel.getGoogleSignInClient()) {}
            },
        )
    }

    if (showAuthPrompt) {
        OfflineAuthPromptDialog(
            onDismiss = { viewModel.closeAuthPrompt() },
            onSignInGoogle = {
                viewModel.closeAuthPrompt()
                triggerGoogleSignIn()
            },
        )
    }
        }
    }
}

@Composable
private fun RivaBottomBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    NavigationBar(
        containerColor = InkSoft.copy(alpha = 0.97f),
        tonalElevation = 0.dp,
    ) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Ink,
            selectedTextColor = Lime,
            indicatorColor = Lime,
            unselectedIconColor = TextFaint,
            unselectedTextColor = TextFaint,
        )
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onSelect(0) },
            icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
            label = { Text("Home") },
            colors = colors,
        )
        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onSelect(1) },
            icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            label = { Text("Search") },
            colors = colors,
        )
        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onSelect(2) },
            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
            label = { Text("Library") },
            colors = colors,
        )
    }
}

@Composable
private fun MiniPlayer(
    track: Track,
    state: PlayerUiState,
    isResolving: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val duration = state.durationMs.takeIf { it > 0L } ?: track.durationMs
    val progress = if (duration > 0L) {
        (state.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelElevated)
            .clickable(onClick = onOpen),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = Lime,
            trackColor = PanelBorder,
        )
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(url = track.thumbnailUrl, size = 45.dp, corner = 13.dp)
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isResolving) "Connecting to stream..." else track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isResolving) Lime else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Lime,
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = Lime,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSheet(
    track: Track,
    state: PlayerUiState,
    isResolving: Boolean,
    downloadedIds: Set<String>,
    downloadStatuses: Map<String, DownloadStatus>,
    likedIds: Set<String>,
    queue: List<Track>,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    sleepTimerMinutes: Int?,
    sleepRemainingSeconds: Long,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onPlayPage: (Int) -> Unit,
    onViewOpened: () -> Unit,
    lyrics: Map<String, LyricsUi>,
    onLyrics: (Track) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSleepTimer: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLike: (Track) -> Unit,
    onCancelSleepTimer: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val durationMs = state.durationMs.takeIf { it > 0L } ?: track.durationMs
    val isDownloaded = downloadedIds.contains(track.id)
    val downloadStatus = downloadStatuses[track.id]

    val isSingleRepeat = repeatMode == RepeatMode.ONE
    val pagerPages = if (isSingleRepeat) {
        listOf(track, track, track)
    } else {
        queue.ifEmpty { listOf(track) }
    }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = if (isSingleRepeat) 1 else pagerPages.indexOfFirst { it.id == track.id }.coerceAtLeast(0),
        pageCount = { pagerPages.size },
    )

    val coroutineScope = rememberCoroutineScope()

    // Player khulne pe single-track ho to related chain prefetch (swipe ready).
    LaunchedEffect(Unit) { onViewOpened() }

    // Follow external track changes (auto next, notification, open from list).
    LaunchedEffect(track.id, isSingleRepeat, pagerPages.size) {
        if (isSingleRepeat) {
            if (pagerState.currentPage != 1 && !pagerState.isScrollInProgress) {
                pagerState.scrollToPage(1)
            }
        } else {
            val target = pagerPages.indexOfFirst { it.id == track.id }
            if (target >= 0 && target != pagerState.currentPage) {
                if (pagerState.currentPage == pagerPages.lastIndex && target == 0) {
                    pagerState.animateScrollToPage(0)
                } else if (pagerState.currentPage == 0 && target == pagerPages.lastIndex) {
                    pagerState.animateScrollToPage(pagerPages.lastIndex)
                } else if (!pagerState.isScrollInProgress) {
                    pagerState.scrollToPage(target)
                }
            }
        }
    }
    // Swipe settle -> INDEX based play (agar single repeat ho to same song replay).
    LaunchedEffect(track.id, isSingleRepeat, pagerPages) {
        if (isSingleRepeat) {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                if (page != 1) {
                    onPlayPage(1)
                    pagerState.scrollToPage(1)
                }
            }
        } else {
            snapshotFlow { pagerState.settledPage }.collect { page ->
                val pageTrack = pagerPages.getOrNull(page) ?: return@collect
                if (pageTrack.id != track.id) {
                    onPlayPage(page)
                }
            }
        }
    }

    val loopScrollConnection = remember(pagerState, pagerPages.size, repeatMode) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y < -35f && repeatMode == RepeatMode.ALL && pagerPages.size > 1) {
                    if (pagerState.currentPage == pagerPages.lastIndex) {
                        coroutineScope.launch {
                            onPlayPage(0)
                            pagerState.animateScrollToPage(0)
                        }
                        return available
                    }
                } else if (available.y > 35f && repeatMode == RepeatMode.ALL && pagerPages.size > 1) {
                    if (pagerState.currentPage == 0) {
                        coroutineScope.launch {
                            val lastIdx = pagerPages.lastIndex
                            onPlayPage(lastIdx)
                            pagerState.animateScrollToPage(lastIdx)
                        }
                        return available
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (available.y < -150f && repeatMode == RepeatMode.ALL && pagerPages.size > 1) {
                    if (pagerState.currentPage == pagerPages.lastIndex) {
                        onPlayPage(0)
                        pagerState.animateScrollToPage(0)
                        return available
                    }
                } else if (available.y > 150f && repeatMode == RepeatMode.ALL && pagerPages.size > 1) {
                    if (pagerState.currentPage == 0) {
                        val lastIdx = pagerPages.lastIndex
                        onPlayPage(lastIdx)
                        pagerState.animateScrollToPage(lastIdx)
                        return available
                    }
                }
                return Velocity.Zero
            }
        }
    }

    // Full-screen reel: pager POORI screen cover karta hai AUR har cheez page
    // ke andar hai — art, slider, play/pause, next/prev, sab swipe ke saath
    // slide karte hain (controls upar jate hain reel transition ke saath).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(PlayerTop, PlayerMid, Ink),
                ),
            ),
    ) {
        androidx.compose.foundation.pager.VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(loopScrollConnection),
        ) { page ->
            val pageTrack = pagerPages.getOrNull(page) ?: track
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Close player",
                            tint = TextPrimary,
                        )
                    }
                    Text(
                        text = "NOW PLAYING",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextFaint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onSearch) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = TextPrimary,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Artwork pe tap -> lyrics; phir tap -> wapas artwork.
                    var showLyrics by remember(pageTrack.id) { mutableStateOf(false) }
                    val lyric = lyrics[pageTrack.id]
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth(0.78f)
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .clickable {
                                if (!showLyrics) onLyrics(pageTrack)
                                showLyrics = !showLyrics
                            },
                    ) {
                        if (showLyrics && lyric != null) {
                            when {
                                lyric.synced && lyric.syncedLines.isNotEmpty() -> {
                                    // REALTIME synced: position ke saath line
                                    // highlight + auto-scroll (YTM jaisa).
                                    val listState = rememberLazyListState()
                                    val activeIndex = lyric.syncedLines.count {
                                        it.startMs <= state.positionMs + 350L
                                    } - 1
                                    LaunchedEffect(activeIndex) {
                                        if (activeIndex > 2) {
                                            listState.animateScrollToItem(activeIndex - 3)
                                        }
                                    }
                                    androidx.compose.foundation.lazy.LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        items(lyric.syncedLines.size) { index ->
                                            val line = lyric.syncedLines[index]
                                            val isActive = index == activeIndex
                                            val isPast = index < activeIndex
                                            Text(
                                                text = line.text,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = when {
                                                    isActive -> Lime
                                                    isPast -> TextFaint
                                                    else -> TextSecondary
                                                },
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 9.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = "LYRICS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextFaint,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp),
                                    )
                                }
                                lyric.loading -> Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(30.dp),
                                        color = Lime,
                                    )
                                }
                                lyric.failed || (!lyric.synced && lyric.lines.isEmpty()) -> Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = "Lyrics nahi mile is gaane ke liye",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextFaint,
                                    )
                                }
                                else -> Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    lyric.lines.forEachIndexed { index, line ->
                                        Text(
                                            text = line.ifBlank { " " },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (index % 2 == 0) TextPrimary else TextSecondary,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                    }
                                    Text(
                                        text = "LYRICS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextFaint,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                        } else {
                            TrackArtwork(
                                url = pageTrack.thumbnailUrl,
                                modifier = Modifier.fillMaxSize(),
                                corner = 30.dp,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = pageTrack.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pageTrack.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DownloadAction(
                            track = pageTrack,
                            status = downloadStatuses[pageTrack.id],
                            downloaded = downloadedIds.contains(pageTrack.id),
                            onDownload = { onDownloadTrack(pageTrack) },
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = { onToggleLike(pageTrack) }, modifier = Modifier.size(40.dp)) {
                            val isLiked = likedIds.contains(pageTrack.id)
                            Icon(
                                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (isLiked) {
                                    "Remove from Liked songs"
                                } else {
                                    "Add to Liked songs"
                                },
                                tint = if (isLiked) Coral else TextPrimary,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(onClick = { onAddToPlaylist() }, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = "Add to playlist",
                                tint = TextPrimary,
                            )
                        }
                    }
                }

                // Controls page ke andar hi — swipe pe pura page (controls
                // samet) angdaal hoke slide hota hai.
                PlayerSliderSection(
                    positionMs = state.positionMs,
                    durationMs = durationMs,
                    onSeek = onSeek,
                )

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = TextPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Lime)
                            .clickable(enabled = !isResolving, onClick = onToggle),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isResolving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Ink,
                                strokeWidth = 2.5.dp,
                            )
                        } else {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Ink,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                    }
                    IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = TextPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeControl(
                        icon = Icons.Rounded.Shuffle,
                        label = if (shuffleEnabled) "Shuffle on" else "Shuffle",
                        active = shuffleEnabled,
                        onClick = onToggleShuffle,
                    )
                    Spacer(Modifier.width(16.dp))
                    ModeControl(
                        icon = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        label = when (repeatMode) {
                            RepeatMode.OFF -> "Repeat"
                            RepeatMode.ALL -> "Repeat all"
                            RepeatMode.ONE -> "Repeat one"
                        },
                        active = repeatMode != RepeatMode.OFF,
                        onClick = onCycleRepeat,
                    )
                    Spacer(Modifier.width(16.dp))
                    ModeControl(
                        icon = Icons.Rounded.Timer,
                        label = sleepTimerMinutes?.let { "Sleep ${formatDuration(sleepRemainingSeconds * 1000L)}" } ?: "Sleep timer",
                        active = sleepTimerMinutes != null,
                        onClick = onSleepTimer,
                    )
                }

                if (sleepTimerMinutes != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Sleep timer: ${formatDuration(sleepRemainingSeconds * 1000L)} left • tap to cancel",
                        style = MaterialTheme.typography.labelSmall,
                        color = Lime,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onCancelSleepTimer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Riva Music by Nexus Digital",
                    style = MaterialTheme.typography.labelSmall,
                    color = Violet.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ModeControl(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Lime else TextFaint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) Lime else TextSecondary,
        )
    }
}

@Composable
private fun SleepTimerDialog(
    currentMinutes: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit,
) {
    val options = listOf(5, 10, 15, 30, 45, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = {
            Text(
                text = "Sleep timer",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = "Music pauses automatically after the selected time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = if (currentMinutes == minutes) Lime else TextFaint,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "$minutes minutes",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (currentMinutes == minutes) Lime else TextPrimary,
                        )
                    }
                }
                if (currentMinutes != null) {
                    TextButton(onClick = { onSelect(null) }) {
                        Text("Turn off timer", color = TextSecondary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Lime)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSliderSection(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    var sliderValue by remember(durationMs) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(positionMs, durationMs, dragging) {
        if (!dragging) {
            sliderValue = positionMs.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f))
        }
    }

    val maxVal = durationMs.toFloat().coerceAtLeast(1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Slider(
            value = sliderValue,
            onValueChange = {
                dragging = true
                sliderValue = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(sliderValue.toLong())
            },
            valueRange = 0f..maxVal,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(if (dragging) 16.dp else 12.dp)
                        .clip(CircleShape)
                        .background(Lime),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(5.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Lime,
                        inactiveTrackColor = PanelBorder.copy(alpha = 0.5f),
                    ),
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                )
            },
            colors = SliderDefaults.colors(
                thumbColor = Lime,
                activeTrackColor = Lime,
                inactiveTrackColor = PanelBorder.copy(alpha = 0.5f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
        ) {
            Text(
                text = formatDuration(sliderValue.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PlayerLoadingSheet(
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(PlayerTop, PlayerMid, Ink),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Close player",
                        tint = TextPrimary,
                    )
                }
                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextFaint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSearch) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = TextPrimary,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(0.78f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Panel),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Lime,
                        strokeWidth = 3.dp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Loading music...",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Nexus Digital",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Riva Music by Nexus Digital",
                style = MaterialTheme.typography.labelSmall,
                color = Violet.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}
