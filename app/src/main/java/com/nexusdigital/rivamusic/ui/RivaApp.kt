package com.nexusdigital.rivamusic.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.nexusdigital.rivamusic.data.UserProfile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.graphicsLayer
import com.nexusdigital.rivamusic.data.upgradeThumbnailQuality
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
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
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val similarTracks by viewModel.similarTracks.collectAsStateWithLifecycle()
    val isLoadingSimilar by viewModel.isLoadingSimilar.collectAsStateWithLifecycle()
    var showSimilarSheet by rememberSaveable { mutableStateOf(false) }
    var isFullScreen by rememberSaveable { mutableStateOf(false) }

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
        enabled = isFullScreen || showSimilarSheet || showProfileSheet || showLanguagePrompt || showSleepTimer || showCreatePlaylist ||
            showImportPlaylist || playlistTrackToAdd != null || showPlayer ||
            showAllPlaylists || showOfflineSheet || showCachedSheet ||
            showRecentSheet || showTrendingSheet || showLikedSheet ||
            selectedPlaylist != null || selectedArtist != null ||
            searchCollection != null || isOpeningCollection || selectedTab != 0,
    ) {
        when {
            isFullScreen -> isFullScreen = false
            showSimilarSheet -> showSimilarSheet = false
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

    // Appearance theme toggle par pura UI re-render karo
    val systemDark = isSystemInDarkTheme()
    key(isDarkMode) {
        RivaMusicTheme(darkTheme = isDarkMode) {
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
    AnimatedVisibility(
        visible = showPlayer,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(220, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(160)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(200, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(140)),
    ) {
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
                canSeek = true,
                canRotateDisk = currentUser?.isGuest == false,
                isDarkMode = isDarkMode,
                onToggleDarkMode = viewModel::toggleDarkMode,
                onOpenSimilarTracks = { tr ->
                    viewModel.loadSimilarTracks(tr)
                    showSimilarSheet = true
                },
                onOpenFullScreen = { isFullScreen = true },
                onRestrictedAction = { msg -> viewModel.postMessage(msg) },
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

    if (showSimilarSheet && sheetTrack != null) {
        SimilarTracksSheet(
            parentTrack = sheetTrack,
            tracks = similarTracks,
            isLoading = isLoadingSimilar,
            currentTrackId = sheetTrack.id,
            onDismiss = { showSimilarSheet = false },
            onPlayTrack = { tr, list ->
                viewModel.playTrack(tr, list)
            },
        )
    }

    if (isFullScreen && sheetTrack != null) {
        FullScreenPlayerOverlay(
            track = sheetTrack,
            isPlaying = playerState.isPlaying,
            positionMs = playerState.positionMs,
            durationMs = playerState.durationMs.takeIf { it > 0L } ?: sheetTrack.durationMs,
            lyrics = lyricsNow[sheetTrack.id],
            canSeek = true,
            canRotateDisk = currentUser?.isGuest == false,
            onSeek = viewModel::seekTo,
            onRestrictedAction = { msg -> viewModel.postMessage(msg) },
            onTogglePlayPause = viewModel::togglePlayPause,
            onNext = viewModel::playNext,
            onPrevious = viewModel::playPrevious,
            onDismiss = { isFullScreen = false },
        )
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
            isDarkMode = isDarkMode,
            selectedRegion = selectedRegion,
            selectedLanguages = selectedLanguages,
            onToggleDarkMode = viewModel::toggleDarkMode,
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
            unselectedIconColor = if (RivaIsDark) TextFaint else TextSecondary,
            unselectedTextColor = if (RivaIsDark) TextFaint else TextSecondary,
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

private fun Modifier.playerSwipeGesture(
    thresholdPx: Float,
    getThumbnailBounds: () -> Rect,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
): Modifier = pointerInput(onNext, onPrevious, onDismiss) {
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        val bounds = getThumbnailBounds()
        // If the touch started inside the thumbnail / vinyl disk area:
        // Do NOT intercept; allow rotary dragging / spinning
        if (bounds.width > 0f && bounds.contains(down.position)) {
            return@awaitEachGesture
        }

        val isHeaderTouch = down.position.y < 260f

        var totalDragY = 0f
        var totalDragX = 0f
        var hasTriggered = false
        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break

            val deltaY = change.position.y - change.previousPosition.y
            val deltaX = change.position.x - change.previousPosition.x
            totalDragY += deltaY
            totalDragX += deltaX

            if (!hasTriggered && kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX) * 1.25f) {
                if (totalDragY <= -thresholdPx) {
                    hasTriggered = true
                    change.consume()
                    onNext()
                    break
                } else if (totalDragY >= thresholdPx) {
                    hasTriggered = true
                    change.consume()
                    if (isHeaderTouch) {
                        onDismiss()
                    } else {
                        onPrevious()
                    }
                    break
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
    canSeek: Boolean = true,
    canRotateDisk: Boolean = true,
    isDarkMode: Boolean = true,
    onToggleDarkMode: () -> Unit = {},
    onOpenSimilarTracks: (Track) -> Unit = {},
    onOpenFullScreen: () -> Unit = {},
    onRestrictedAction: (String) -> Unit = {},
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
    val context = LocalContext.current
    val durationMs = state.durationMs.takeIf { it > 0L } ?: track.durationMs
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    var thumbnailBounds by remember { mutableStateOf(Rect.Zero) }
    var showLyrics by remember { mutableStateOf(false) }

    LaunchedEffect(track.id, showLyrics) {
        if (showLyrics) {
            onLyrics(track)
        }
    }

    var slideDirection by remember { mutableIntStateOf(1) } // 1: next / up, -1: prev / down
    val handleNext = {
        slideDirection = 1
        onNext()
    }
    val handlePrevious = {
        slideDirection = -1
        onPrevious()
    }

    LaunchedEffect(Unit) { onViewOpened() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(PlayerTop, PlayerMid, Ink),
                ),
            )
            .playerSwipeGesture(
                thresholdPx = swipeThreshold,
                getThumbnailBounds = { thumbnailBounds },
                onNext = handleNext,
                onPrevious = handlePrevious,
                onDismiss = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppleGlassButton(
                    icon = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Close player",
                    onClick = onDismiss,
                    size = 42.dp,
                    iconSize = 24.dp,
                )
                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.weight(1f),
                )
                AppleGlassButton(
                    icon = Icons.Rounded.Search,
                    contentDescription = "Search",
                    onClick = onSearch,
                    size = 42.dp,
                    iconSize = 22.dp,
                )
            }

            // Animated Center & Bottom Presentation (Entire Card Slides Up / Down on Track Change)
            AnimatedContent(
                targetState = track,
                contentKey = { it.id },
                transitionSpec = {
                    if (slideDirection >= 0) {
                        // Next song: out goes UP (-height), in comes from BOTTOM (+height)
                        (slideInVertically(
                            animationSpec = tween(340, easing = FastOutSlowInEasing),
                            initialOffsetY = { height -> height }
                        ) + fadeIn(tween(240))).togetherWith(
                            slideOutVertically(
                                animationSpec = tween(340, easing = FastOutSlowInEasing),
                                targetOffsetY = { height -> -height }
                            ) + fadeOut(tween(200))
                        )
                    } else {
                        // Previous song: out goes DOWN (+height), in comes from TOP (-height)
                        (slideInVertically(
                            animationSpec = tween(340, easing = FastOutSlowInEasing),
                            initialOffsetY = { height -> -height }
                        ) + fadeIn(tween(240))).togetherWith(
                            slideOutVertically(
                                animationSpec = tween(340, easing = FastOutSlowInEasing),
                                targetOffsetY = { height -> height }
                            ) + fadeOut(tween(200))
                        )
                    }
                },
                label = "FullSongTransition",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { pageTrack ->
                val trackDurationMs = if (pageTrack.id == track.id) durationMs else pageTrack.durationMs
                val trackPositionMs = if (pageTrack.id == track.id) state.positionMs else 0L
                val isTrackPlaying = state.isPlaying && pageTrack.id == track.id

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Equal gap above disk from top bar
                    Spacer(Modifier.weight(1f))

                    // Center Content: Vinyl Disk OR Realtime Synced Lyrics
                    AnimatedContent(
                        targetState = showLyrics,
                        transitionSpec = {
                            (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.94f)).togetherWith(
                                fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.94f)
                            )
                        },
                        label = "DiskLyricsToggle",
                        modifier = Modifier
                            .weight(8f, fill = false)
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                            .onGloballyPositioned { coordinates ->
                                thumbnailBounds = coordinates.boundsInRoot()
                            },
                    ) { lyricsMode ->
                        if (!lyricsMode) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                VinylDiskPlayer(
                                    thumbnailUrl = pageTrack.thumbnailUrl,
                                    isPlaying = isTrackPlaying,
                                    canSeek = canRotateDisk,
                                    positionMs = trackPositionMs,
                                    durationMs = trackDurationMs,
                                    onSeek = onSeek,
                                    onRestrictedAction = onRestrictedAction,
                                    onThumbnailClick = {
                                        showLyrics = true
                                        onLyrics(pageTrack)
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else {
                            // Realtime Synced Lyrics Card
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (RivaIsDark) Color.White.copy(alpha = 0.05f) else PanelElevated)
                                    .border(1.dp, if (RivaIsDark) Color.White.copy(alpha = 0.12f) else PanelBorder, RoundedCornerShape(24.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Top header inside Lyrics card
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (RivaIsDark) Color.White.copy(alpha = 0.08f) else PanelBorder)
                                            .clickable { showLyrics = false }
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Lime),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "REALTIME LYRICS",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                        )
                                    }

                                    // Switch back to Vinyl Disk button
                                    AppleGlassButton(
                                        icon = Icons.Rounded.Close,
                                        contentDescription = "Show Vinyl Disk",
                                        onClick = { showLyrics = false },
                                        size = 32.dp,
                                        iconSize = 18.dp,
                                    )
                                }

                                // Synced / Plain Lyrics View
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SyncedLyricsView(
                                        lyrics = lyrics[pageTrack.id],
                                        positionMs = trackPositionMs,
                                        canSeek = canSeek,
                                        onSeek = onSeek,
                                        onRestrictedAction = onRestrictedAction,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }

                    // Equal gap below disk to bottom components
                    Spacer(Modifier.weight(1f))

                    // Below Disk: Title, Gap, Singer, Actions, Mode Controls, Slider, Transport
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = pageTrack.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp)) // Noticeable gap between title & singer
                        Text(
                            text = pageTrack.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))

                        // Apple Music glassy action buttons row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .background(if (RivaIsDark) Color.White.copy(alpha = 0.06f) else PanelElevated)
                                .border(1.dp, if (RivaIsDark) Color.White.copy(alpha = 0.12f) else PanelBorder, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 50 Similar Tracks Button
                            AppleGlassButton(
                                icon = Icons.Rounded.Radio,
                                contentDescription = "50 Similar Tracks",
                                onClick = { onOpenSimilarTracks(pageTrack) },
                                badgeText = "50",
                                size = 38.dp,
                                iconSize = 19.dp,
                                tint = Lime,
                            )

                            // Like Button
                            val isLiked = likedIds.contains(pageTrack.id)
                            AppleGlassButton(
                                icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (isLiked) "Unlike" else "Like",
                                onClick = { onToggleLike(pageTrack) },
                                tint = if (isLiked) Coral else TextPrimary,
                                size = 38.dp,
                                iconSize = 19.dp,
                            )

                            // Download Button
                            DownloadAction(
                                track = pageTrack,
                                status = downloadStatuses[pageTrack.id],
                                downloaded = downloadedIds.contains(pageTrack.id),
                                onDownload = { onDownloadTrack(pageTrack) },
                            )

                            // Add to Playlist Button
                            AppleGlassButton(
                                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = "Add to playlist",
                                onClick = onAddToPlaylist,
                                size = 38.dp,
                                iconSize = 19.dp,
                            )

                            // Appearance Toggle (Dark/Light mode)
                            AppleGlassButton(
                                icon = if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = "Toggle Appearance",
                                onClick = onToggleDarkMode,
                                size = 38.dp,
                                iconSize = 19.dp,
                            )

                            // Fullscreen Button
                            AppleGlassButton(
                                icon = Icons.Rounded.Fullscreen,
                                contentDescription = "Fullscreen",
                                onClick = onOpenFullScreen,
                                size = 38.dp,
                                iconSize = 20.dp,
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Mode Controls Row: Shuffle, Repeat, Sleep Timer (ABOVE Progress bar!)
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (RivaIsDark) Color.White.copy(alpha = 0.06f) else PanelElevated)
                                .border(1.dp, if (RivaIsDark) Color.White.copy(alpha = 0.12f) else PanelBorder, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ModeControl(
                                icon = Icons.Rounded.Shuffle,
                                label = if (shuffleEnabled) "Shuffle on" else "Shuffle",
                                active = shuffleEnabled,
                                onClick = onToggleShuffle,
                            )
                            Spacer(Modifier.width(6.dp))
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
                            Spacer(Modifier.width(6.dp))
                            ModeControl(
                                icon = Icons.Rounded.Timer,
                                label = sleepTimerMinutes?.let { "Sleep ${formatDuration(sleepRemainingSeconds * 1000L)}" } ?: "Sleep",
                                active = sleepTimerMinutes != null,
                                onClick = onSleepTimer,
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // Progress bar & seek slider
                        PlayerSliderSection(
                            positionMs = trackPositionMs,
                            durationMs = trackDurationMs,
                            canSeek = canSeek,
                            onSeek = onSeek,
                            onRestrictedAction = onRestrictedAction,
                        )

                        Spacer(Modifier.height(4.dp))

                        // Transport buttons (Previous, Play/Pause, Next)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppleGlassButton(
                                icon = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous",
                                onClick = handlePrevious,
                                size = 48.dp,
                                iconSize = 26.dp,
                            )
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Lime, Color(0xFF94D82D))
                                        )
                                    )
                                    .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                    .clickable(
                                        enabled = !isResolving,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onToggle,
                                    ),
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
                                        imageVector = if (isTrackPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isTrackPlaying) "Pause" else "Play",
                                        tint = Ink,
                                        modifier = Modifier.size(34.dp),
                                    )
                                }
                            }
                            AppleGlassButton(
                                icon = Icons.Rounded.SkipNext,
                                contentDescription = "Next",
                                onClick = handleNext,
                                size = 48.dp,
                                iconSize = 26.dp,
                            )
                        }

                        if (sleepTimerMinutes != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Sleep timer: ${formatDuration(sleepRemainingSeconds * 1000L)} left • tap to cancel",
                                style = MaterialTheme.typography.labelSmall,
                                color = Lime,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onCancelSleepTimer,
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Riva Music by Nexus Digital",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (RivaIsDark) Violet.copy(alpha = 0.85f) else TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
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
            .background(
                if (active) Lime.copy(alpha = 0.2f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (active) Lime.copy(alpha = 0.45f)
                else Color.White.copy(alpha = 0.08f),
                CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Lime else TextFaint,
            modifier = Modifier.size(17.dp),
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
    canSeek: Boolean = true,
    onSeek: (Long) -> Unit,
    onRestrictedAction: (String) -> Unit = {},
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value = sliderValue,
                onValueChange = {
                    if (canSeek) {
                        dragging = true
                        sliderValue = it
                    }
                },
                onValueChangeFinished = {
                    if (canSeek) {
                        dragging = false
                        onSeek(sliderValue.toLong())
                    }
                },
                valueRange = 0f..maxVal,
                enabled = canSeek,
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(if (dragging) 16.dp else 12.dp)
                            .clip(CircleShape)
                            .background(if (canSeek) Lime else TextFaint),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(5.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = if (canSeek) Lime else TextFaint,
                            inactiveTrackColor = PanelBorder.copy(alpha = 0.5f),
                            disabledActiveTrackColor = TextFaint.copy(alpha = 0.5f),
                            disabledInactiveTrackColor = PanelBorder.copy(alpha = 0.3f),
                        ),
                        drawStopIndicator = null,
                        thumbTrackGapSize = 0.dp,
                    )
                },
                colors = SliderDefaults.colors(
                    thumbColor = if (canSeek) Lime else TextFaint,
                    activeTrackColor = if (canSeek) Lime else TextFaint,
                    inactiveTrackColor = PanelBorder.copy(alpha = 0.5f),
                    disabledThumbColor = TextFaint,
                    disabledActiveTrackColor = TextFaint.copy(alpha = 0.5f),
                    disabledInactiveTrackColor = PanelBorder.copy(alpha = 0.3f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
            )
            if (!canSeek) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            onRestrictedAction("Sign in with Google to seek music! 🔒 Guest users listen to full song.")
                        }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(sliderValue.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
            )
            if (!canSeek) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "🔒 Seeking locked (Sign in to unlock)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Coral,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }
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
                color = if (RivaIsDark) Violet.copy(alpha = 0.85f) else TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}
