package com.nexusdigital.rivamusic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.nexusdigital.rivamusic.data.AuthRepository
import com.nexusdigital.rivamusic.data.CloudTrack
import com.nexusdigital.rivamusic.data.DownloadRepository
import com.nexusdigital.rivamusic.data.DownloadStatus
import com.nexusdigital.rivamusic.data.FirestoreSyncRepository
import com.nexusdigital.rivamusic.data.HomeSection
import com.nexusdigital.rivamusic.data.LibraryRepository
import com.nexusdigital.rivamusic.data.LikedTrack
import com.nexusdigital.rivamusic.data.LyricLine
import com.nexusdigital.rivamusic.data.MusicRepository
import com.nexusdigital.rivamusic.data.Playlist
import com.nexusdigital.rivamusic.data.PlaylistRepository
import com.nexusdigital.rivamusic.data.RecentRepository
import com.nexusdigital.rivamusic.data.RepeatMode
import com.nexusdigital.rivamusic.data.SearchCollection
import com.nexusdigital.rivamusic.data.SearchFilter
import com.nexusdigital.rivamusic.data.SearchHistoryRepository
import com.nexusdigital.rivamusic.data.SearchItem
import com.nexusdigital.rivamusic.data.SearchSuggestions
import com.nexusdigital.rivamusic.data.StreamResolution
import com.nexusdigital.rivamusic.data.Track
import com.nexusdigital.rivamusic.data.UserProfile
import com.nexusdigital.rivamusic.playback.PlaybackEvent
import com.nexusdigital.rivamusic.playback.PlaybackService
import com.nexusdigital.rivamusic.playback.PlayerConnection
import com.nexusdigital.rivamusic.playback.PlayerUiState
import com.nexusdigital.rivamusic.ui.AVAILABLE_REGIONS
import com.nexusdigital.rivamusic.ui.MusicRegion
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class RivaViewModel(app: Application) : AndroidViewModel(app) {
    private val music = MusicRepository()
    private val downloadsRepo = DownloadRepository(app)
    private val recentRepo = RecentRepository(app)
    private val playlistRepo = PlaylistRepository(app)
    private val libraryRepo = LibraryRepository(app)
    private val searchHistoryRepo = SearchHistoryRepository(app)
    private val player = PlayerConnection(app)
    private val authRepo = AuthRepository(app)
    val currentUser: StateFlow<UserProfile?> = authRepo.currentUser
    private val firestoreSync = FirestoreSyncRepository()
    private val _showAuthPrompt = MutableStateFlow(false)
    val showAuthPrompt: StateFlow<Boolean> = _showAuthPrompt.asStateFlow()
    private val homePrefs = app.getSharedPreferences("riva_home_cache", android.content.Context.MODE_PRIVATE)
    private val playerPrefs = app.getSharedPreferences("riva_player_prefs", android.content.Context.MODE_PRIVATE)
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    // Fast in-memory LRU search cache (instant repeated / backspaced searches)
    private val searchCache = object : LinkedHashMap<String, List<Track>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Track>>?): Boolean {
            return size > 30
        }
    }

    val playerState: StateFlow<PlayerUiState> = player.state
    val playlists: StateFlow<List<Playlist>> = playlistRepo.playlists
    val liked: StateFlow<List<LikedTrack>> = libraryRepo.liked
    val likedIds: StateFlow<Set<String>> = libraryRepo.likedIds
    val searchHistory: StateFlow<List<String>> = searchHistoryRepo.history
    val downloads: StateFlow<List<com.nexusdigital.rivamusic.data.DownloadedTrack>> =
        downloadsRepo.downloads
    val downloadProgress: StateFlow<Map<String, DownloadStatus>> = downloadsRepo.progress
    val recent: StateFlow<List<Track>> = recentRepo.recent

    private val _homeSections = MutableStateFlow<List<HomeSection>>(emptyList())
    val homeSections: StateFlow<List<HomeSection>> = _homeSections.asStateFlow()

    private val _trendingTracks = MutableStateFlow<List<Track>>(emptyList())
    val trendingTracks: StateFlow<List<Track>> = _trendingTracks.asStateFlow()

    private val _isLoadingHome = MutableStateFlow(false)
    val isLoadingHome: StateFlow<Boolean> = _isLoadingHome.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults: StateFlow<List<Track>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchFilter = MutableStateFlow(SearchFilter.SONGS)
    val searchFilter: StateFlow<SearchFilter> = _searchFilter.asStateFlow()

    private val _searchItems = MutableStateFlow<List<SearchItem>>(emptyList())
    val searchItems: StateFlow<List<SearchItem>> = _searchItems.asStateFlow()

    private val _searchSuggestions = MutableStateFlow(SearchSuggestions())
    val searchSuggestions: StateFlow<SearchSuggestions> = _searchSuggestions.asStateFlow()

    private val _isSuggesting = MutableStateFlow(false)
    val isSuggesting: StateFlow<Boolean> = _isSuggesting.asStateFlow()

    private val _searchCollection = MutableStateFlow<SearchCollection?>(null)
    val searchCollection: StateFlow<SearchCollection?> = _searchCollection.asStateFlow()

    private val _isOpeningCollection = MutableStateFlow(false)
    val isOpeningCollection: StateFlow<Boolean> = _isOpeningCollection.asStateFlow()

    private val itemSearchCache = object : LinkedHashMap<String, List<SearchItem>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SearchItem>>?): Boolean {
            return size > 20
        }
    }
    private val suggestionCache = object : LinkedHashMap<String, SearchSuggestions>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SearchSuggestions>?): Boolean {
            return size > 30
        }
    }
    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null

    private val initialRecent = recentRepo.recent.value
    private val _currentTrack = MutableStateFlow<Track?>(initialRecent.firstOrNull())
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isResolving = MutableStateFlow(false)
    val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _isImportingPlaylist = MutableStateFlow(false)
    val isImportingPlaylist: StateFlow<Boolean> = _isImportingPlaylist.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _shuffleEnabled = MutableStateFlow(playerPrefs.getBoolean(KEY_SHUFFLE_ENABLED, false))
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val initialQueue = if (_shuffleEnabled.value && initialRecent.size > 1) {
        listOf(initialRecent.first()) + initialRecent.drop(1).shuffled()
    } else {
        initialRecent
    }
    private val queue = MutableStateFlow<List<Track>>(initialQueue)
    val queueTracks: StateFlow<List<Track>> = queue.asStateFlow()
    private var baseQueue: List<Track> = initialRecent
    private var queueIndex = 0
    private var resolveJob: Job? = null
    private var autoSkips = 0

    private val _repeatMode = MutableStateFlow(
        runCatching {
            RepeatMode.valueOf(playerPrefs.getString(KEY_REPEAT_MODE, RepeatMode.OFF.name) ?: RepeatMode.OFF.name)
        }.getOrDefault(RepeatMode.OFF)
    )
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _selectedRegion = MutableStateFlow<MusicRegion>(
        run {
            val code = homePrefs.getString(KEY_SELECTED_REGION, "IN") ?: "IN"
            AVAILABLE_REGIONS.firstOrNull { it.code == code } ?: AVAILABLE_REGIONS.first()
        }
    )
    val selectedRegion: StateFlow<MusicRegion> = _selectedRegion.asStateFlow()

    private val _selectedLanguages = MutableStateFlow<Set<String>>(
        homePrefs.getStringSet(KEY_SELECTED_LANGUAGES, null) ?: setOf("Hindi", "English")
    )
    val selectedLanguages: StateFlow<Set<String>> = _selectedLanguages.asStateFlow()

    private val _showLanguagePrompt = MutableStateFlow(
        !homePrefs.getBoolean(KEY_LANGUAGES_CONFIGURED, false)
    )
    val showLanguagePrompt: StateFlow<Boolean> = _showLanguagePrompt.asStateFlow()

    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    private val _sleepRemainingSeconds = MutableStateFlow(0L)
    val sleepRemainingSeconds: StateFlow<Long> = _sleepRemainingSeconds.asStateFlow()

    private var sleepTimerJob: Job? = null

    init {
        music.setRegion(_selectedRegion.value.code)
        if (currentUser.value != null && !currentUser.value!!.isGuest) {
            syncCloudOfflineTracks()
        }
        // Instant Home Screen: immediately show cached sections if available
        val cached = loadCachedHome()
        if (cached.isNotEmpty()) {
            _homeSections.value = cached
            _trendingTracks.value = cached.firstOrNull { it.id == "trending" }?.tracks ?: emptyList()
        }
        loadHome()
        observeSearch()
        viewModelScope.launch {
            _searchFilter.drop(1).collect { filter ->
                val query = _searchQuery.value.trim()
                if (query.isNotBlank()) performSearch(query, filter)
            }
        }
        viewModelScope.launch {
            player.events.collect { event ->
                when (event) {
                    PlaybackEvent.Ended -> handleTrackEnded()
                    PlaybackEvent.SkipNext -> playNext()
                    PlaybackEvent.SkipPrevious -> playPrevious()
                    is PlaybackEvent.Error -> handlePlaybackError(event.message)
                }
            }
        }
    }

    /** Track ids already retried via streaming fallback so we never loop. */
    private val streamedFallback = mutableSetOf<String>()

    private fun handlePlaybackError(message: String) {
        _messages.tryEmit(message)
        val track = _currentTrack.value ?: return
        val brokenLocal = track.localPath != null &&
            (message.contains("Source error", ignoreCase = true) ||
                message.contains("FileNotFound", ignoreCase = true) ||
                message.contains("not found", ignoreCase = true))
        if (brokenLocal && streamedFallback.add(track.id)) {
            // Stale offline copy (file gone) — drop entry and fall back to streaming.
            viewModelScope.launch {
                downloadsRepo.removeBrokenEntry(track.id)
                _messages.tryEmit("Offline copy missing — streaming instead")
                startTrack(track.copy(localPath = null))
            }
        }
    }

    fun loadHome(force: Boolean = false) {
        if (_isLoadingHome.value && !force) return
        viewModelScope.launch {
            _isLoadingHome.value = true
            val chosen = _selectedLanguages.value.ifEmpty { setOf("Hindi", "English") }
            val region = _selectedRegion.value
            val specs = mutableListOf<HomeSectionSpec>()

            // 1. Trending in selected region (e.g. "Trending songs in India")
            val trendingTitle = when (region.code) {
                "IN" -> "Trending songs in India"
                "WW" -> "Trending songs Worldwide"
                else -> "Trending songs in ${region.name}"
            }
            val trendingQuery = when (region.code) {
                "IN" -> "trending songs in india"
                "WW" -> "global trending songs top hits"
                else -> "trending songs in ${region.name}"
            }
            val trendingChartId = if (region.code == "IN") "VLPL4fGSI1pDJn40WjZ6utkIuj2rNg-7iGsq" else null
            specs.add(
                HomeSectionSpec(
                    id = "trending",
                    title = trendingTitle,
                    subtitle = "Top viral hits in ${region.name} • YouTube Music",
                    query = trendingQuery,
                    chartBrowseId = trendingChartId,
                )
            )

            // 2. Dedicated language sections
            for (lang in chosen.take(4)) {
                val spec = when (lang.lowercase()) {
                    "hindi" -> HomeSectionSpec("hindi", "Bollywood & Hindi Hits", "Top Hindi chartbusters", "bollywood hits", "VLPL4fGSI1pDJn5RgLW0Sb_zECecWdH_4zOX")
                    "english" -> HomeSectionSpec("english", "Global Pop Hits", "Top international hits", "pop english hits", "VLPL4fGSI1pDJn49TUu37nJoN2QTeYuRwmNv")
                    "punjabi" -> HomeSectionSpec("punjabi", "Punjabi Bangers", "Bhangra beats & high bass", "punjabi hits", "VLPL4fGSI1pDJn5JXkyIohg2RstsbL2SnRew")
                    "bhojpuri" -> HomeSectionSpec("bhojpuri", "Bhojpuri Dhamaka", "Desi vibes & festive party", "bhojpuri hits", "VLPL4fGSI1pDJn4ivDqrsepD3tvHsp0KTDRM")
                    "haryanvi" -> HomeSectionSpec("haryanvi", "Haryanvi Hits", "Desi swag & folk beats", "haryanvi hits", "VLPL4fGSI1pDJn4tiNLMZVGGt2Kghgw__2u0")
                    "tamil" -> HomeSectionSpec("tamil", "Tamil Blockbusters", "Kollywood chartbusters", "tamil hits", "VLPL4fGSI1pDJn4WX22qg1Po7qKOwOb4H6Sk")
                    "telugu" -> HomeSectionSpec("telugu", "Telugu Chartbusters", "Tollywood sensations", "telugu hits", "VLPL4fGSI1pDJn5ALuqpEj_YZ8mEyw9WN8jd")
                    "malayalam" -> HomeSectionSpec("malayalam", "Malayalam Melodies", "Soulful God's own sounds", "malayalam hits songs")
                    "kannada" -> HomeSectionSpec("kannada", "Kannada Hits", "Sandalwood chartbusters", "kannada hits songs")
                    "bengali" -> HomeSectionSpec("bengali", "Bengali Melodies", "Sweet rhythms & modern hits", "bengali hits songs")
                    "marathi" -> HomeSectionSpec("marathi", "Marathi Dhamaal", "Energetic & folk vibes", "marathi hits songs")
                    "gujarati" -> HomeSectionSpec("gujarati", "Gujarati Garba & Hits", "Dhol beats & festive folk", "gujarati hits songs")
                    "rajasthani" -> HomeSectionSpec("rajasthani", "Rajasthani Folk & Hits", "Desert melodies & dance", "rajasthani hits songs")
                    "urdu" -> HomeSectionSpec("urdu", "Urdu Ghazals & Hits", "Poetic & soulful melodies", "urdu hits songs")
                    else -> HomeSectionSpec(lang.lowercase(), "$lang Hits", "Top tracks in $lang", "$lang hits songs")
                }
                specs.add(spec)
            }

            // 3. Relax / Chill section
            val primaryLang = chosen.firstOrNull() ?: "hindi"
            specs.add(
                HomeSectionSpec(
                    id = "chill",
                    title = "Chill & Focus",
                    subtitle = "Low-key beats for deep work",
                    query = "$primaryLang lofi acoustic songs",
                )
            )

            val loaded = coroutineScope {
                specs.map { spec ->
                    async {
                        var tracks: List<Track> = emptyList()
                        if (!spec.chartBrowseId.isNullOrBlank()) {
                            tracks = runCatching {
                                music.fetchMusicCollection(spec.chartBrowseId, spec.title).tracks
                                    .filter { music.isRealSong(it) }
                                    .take(20)
                            }.getOrElse { emptyList() }
                        }
                        if (tracks.isEmpty()) {
                            tracks = runCatching {
                                music.searchMusicTracks(spec.query, 20)
                                    .filter { music.isRealSong(it) }
                            }.getOrElse { emptyList() }
                        }
                        spec to tracks
                    }
                }.awaitAll()
            }

            val loadedSections = loaded.mapNotNull { (spec, tracks) ->
                val unique = tracks.distinctBy { it.id }
                if (unique.isEmpty()) {
                    null
                } else {
                    HomeSection(
                        id = spec.id,
                        title = spec.title,
                        subtitle = spec.subtitle,
                        tracks = unique,
                    )
                }
            }
            if (loadedSections.isNotEmpty()) {
                _homeSections.value = loadedSections
                val trending = loadedSections.firstOrNull { it.id == "trending" }?.tracks ?: emptyList()
                if (trending.isNotEmpty()) {
                    _trendingTracks.value = trending
                }
                runCatching {
                    homePrefs.edit().putString(KEY_HOME_SECTIONS, json.encodeToString(loadedSections)).apply()
                }
            }
            _isLoadingHome.value = false
        }
    }

    fun updatePreferences(region: MusicRegion, languages: Set<String>) {
        val chosen = languages.ifEmpty { setOf("Hindi", "English") }
        _selectedRegion.value = region
        _selectedLanguages.value = chosen
        music.setRegion(region.code)
        homePrefs.edit()
            .putString(KEY_SELECTED_REGION, region.code)
            .putStringSet(KEY_SELECTED_LANGUAGES, chosen)
            .putBoolean(KEY_LANGUAGES_CONFIGURED, true)
            .apply()
        _showLanguagePrompt.value = false
        _messages.tryEmit("Region: ${region.flag} ${region.name} • ${chosen.joinToString(", ")}")
        loadHome(force = true)
    }

    fun updateLanguages(languages: Set<String>) {
        updatePreferences(_selectedRegion.value, languages)
    }

    fun openLanguageSelector() {
        _showLanguagePrompt.value = true
    }

    fun dismissLanguageSelector() {
        _showLanguagePrompt.value = false
        homePrefs.edit().putBoolean(KEY_LANGUAGES_CONFIGURED, true).apply()
    }

    private fun loadCachedHome(): List<HomeSection> {
        val raw = homePrefs.getString(KEY_HOME_SECTIONS, null) ?: return emptyList()
        return runCatching {
            val list = json.decodeFromString<List<HomeSection>>(raw)
            list.map { sec ->
                sec.copy(tracks = sec.tracks.filter { music.isRealSong(it) })
            }.filter { it.tracks.isNotEmpty() }
        }.getOrElse { emptyList() }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun submitSearch(query: String = _searchQuery.value) {
        val clean = query.trim()
        val previous = _searchQuery.value
        _searchQuery.value = query
        if (clean.isBlank()) return
        searchHistoryRepo.add(clean)
        // When the text actually changed the debounced observer will run the
        // search; only run it here for an explicit re-submit of the same query.
        if (previous == query) {
            performSearch(clean, _searchFilter.value)
        }
    }

    fun setSearchFilter(filter: SearchFilter) {
        _searchFilter.value = filter
    }

    fun removeSearchHistory(query: String) = searchHistoryRepo.remove(query)

    fun clearSearchHistory() = searchHistoryRepo.clear()

    fun clearSearch() {
        searchJob?.cancel()
        suggestionsJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _searchItems.value = emptyList()
        _searchSuggestions.value = SearchSuggestions()
        _isSearching.value = false
        _isSuggesting.value = false
    }

    fun toggleLiked(track: Track) {
        val nowLiked = libraryRepo.toggle(track)
        _messages.tryEmit(
            if (nowLiked) "Added to Liked songs" else "Removed from Liked songs",
        )
    }

    fun likeTrack(track: Track) {
        libraryRepo.like(track)
        _messages.tryEmit("Added to Liked songs")
    }

    fun unlikeTrack(trackId: String) {
        libraryRepo.unlike(trackId)
        _messages.tryEmit("Removed from Liked songs")
    }

    fun isLiked(trackId: String): Boolean = libraryRepo.isLiked(trackId)

    fun playLiked(shuffle: Boolean = _shuffleEnabled.value) {
        val tracks = liked.value.map { it.track }
        if (tracks.isEmpty()) {
            _messages.tryEmit("Liked songs empty hain — heart icon dabao")
            return
        }
        playAll(tracks, shuffle, isPlaylist = true)
    }

    fun openSearchItem(item: SearchItem) {
        val song = item.track
        if (song != null) {
            playTrack(song, _searchResults.value.ifEmpty { listOf(song) }, isPlaylist = true)
            return
        }
        val browseId = item.browseId ?: item.id
        if (_isOpeningCollection.value) return
        viewModelScope.launch {
            _isOpeningCollection.value = true
            try {
                _searchCollection.value = music.fetchMusicCollection(browseId, item.title)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _messages.tryEmit("Could not open ${item.title}: ${t.message}")
            } finally {
                _isOpeningCollection.value = false
            }
        }
    }

    fun closeSearchCollection() {
        _searchCollection.value = null
    }

    private fun observeSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(260)
                .distinctUntilChanged()
                .collectLatest { query ->
                    val clean = query.trim()
                    if (clean.isBlank()) {
                        _searchResults.value = emptyList()
                        _searchItems.value = emptyList()
                        _searchSuggestions.value = SearchSuggestions()
                        _isSearching.value = false
                        _isSuggesting.value = false
                        return@collectLatest
                    }
                    loadSuggestions(clean)
                    performSearch(clean, _searchFilter.value)
                }
        }
    }

    private fun performSearch(query: String, filter: SearchFilter) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            val cacheKey = "${filter.name}:${query.lowercase()}"
            try {
                if (filter == SearchFilter.SONGS) {
                    val cached = synchronized(searchCache) { searchCache[cacheKey] }
                    if (cached != null) {
                        _searchResults.value = cached
                        _searchItems.value = emptyList()
                        return@launch
                    }
                    val musicTracks = runCatching { music.searchMusicTracks(query, 30) }
                        .getOrDefault(emptyList())
                        .filter { music.isRealSong(it) }
                    val tracks = if (musicTracks.isNotEmpty()) {
                        musicTracks
                    } else {
                        // Fallback to the long-standing WEB search if YT Music
                        // returns nothing for this query.
                        runCatching { music.search(query, 24) }.getOrDefault(emptyList())
                            .filter { music.isRealSong(it) }
                    }
                    synchronized(searchCache) { searchCache[cacheKey] = tracks }
                    _searchResults.value = tracks
                    _searchItems.value = emptyList()
                } else {
                    val cached = synchronized(itemSearchCache) { itemSearchCache[cacheKey] }
                    val items = cached ?: music.searchMusicItems(query, filter, 30).also { fresh ->
                        synchronized(itemSearchCache) { itemSearchCache[cacheKey] = fresh }
                    }
                    _searchItems.value = items
                    _searchResults.value = emptyList()
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _messages.tryEmit("Search failed: ${t.message}")
            } finally {
                _isSearching.value = false
            }
        }
    }

    private fun loadSuggestions(query: String) {
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch {
            _isSuggesting.value = true
            try {
                val normalized = query.lowercase()
                val cached = synchronized(suggestionCache) { suggestionCache[normalized] }
                val data = cached ?: music.searchSuggestions(query).also { fresh ->
                    synchronized(suggestionCache) { suggestionCache[normalized] = fresh }
                }
                val historyMatches = searchHistoryRepo.history.value
                    .filter { it.contains(query, ignoreCase = true) && !it.equals(query, ignoreCase = true) }
                    .take(2)
                val queries = (historyMatches + data.queries)
                    .filterNot { it.equals(query, ignoreCase = true) }
                    .distinctBy { it.lowercase() }
                    .take(8)
                _searchSuggestions.value = data.copy(queries = queries)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _searchSuggestions.value = SearchSuggestions()
            } finally {
                _isSuggesting.value = false
            }
        }
    }

    private var isPlaylistMode: Boolean = false

    fun playTrack(track: Track, tracks: List<Track> = listOf(track), isPlaylist: Boolean = false) {
        isPlaylistMode = isPlaylist
        val list = tracks.ifEmpty { listOf(track) }
        baseQueue = list
        if (_shuffleEnabled.value) {
            val shuffled = listOf(track) + list.filterNot { it.id == track.id }.shuffled()
            queue.value = shuffled
            queueIndex = 0
            startTrack(shuffled.first())
        } else {
            val index = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            queue.value = list
            queueIndex = index
            startTrack(list[index])
        }
    }

    fun playAll(tracks: List<Track>, shuffle: Boolean = _shuffleEnabled.value, isPlaylist: Boolean = false) {
        if (tracks.isEmpty()) {
            _messages.tryEmit("Nothing to play here yet")
            return
        }
        isPlaylistMode = isPlaylist
        val shouldShuffle = shuffle || _shuffleEnabled.value
        playTrack(if (shouldShuffle) tracks.random() else tracks.first(), tracks, isPlaylist = isPlaylist)
    }

    fun playOffline(shuffle: Boolean = _shuffleEnabled.value) {
        // Offline = manually saved tracks only; auto-cached songs use playCached().
        val tracks = downloads.value.filterNot { it.isCached }.map { it.track }
        playAll(tracks, shuffle, isPlaylist = true)
    }

    fun playCached(shuffle: Boolean = _shuffleEnabled.value) {
        val fresh = downloads.value.filter { downloadsRepo.isFreshCache(it) }.map { it.track }
        if (fresh.isEmpty()) {
            _messages.tryEmit("No cached songs yet — play something first")
            return
        }
        playAll(fresh, shuffle, isPlaylist = true)
    }

    /**
     * Single-track queue me reels swipe ke liye related pages pehle se add karo.
     * (Swipe turant kaam kare, user ko wait na karna pade)
     */
    fun ensureRelatedForSingle() {
        if (_repeatMode.value == RepeatMode.ONE || isPlaylistMode) return
        if (queue.value.size > 1) return
        val cur = _currentTrack.value ?: return
        viewModelScope.launch {
            val chain = relatedChain(cur)
            if (chain.isNotEmpty() && queue.value.size <= 1 && !isPlaylistMode && _repeatMode.value != RepeatMode.ONE) {
                val merged = listOf(cur) + chain
                queue.value = merged
                baseQueue = merged
            }
        }
    }

    /**
     * App open: Do not auto-play audio. Audio only plays when user explicitly taps play.
     */
    fun autoStartOnLaunch() {
        // No-op: user requested songs should never play on app startup.
    }

    /**
     * Lyrics cache: trackId -> lines. Tap on artwork toggles; load once.
     */
    private val _lyrics = MutableStateFlow<Map<String, LyricsUi>>(emptyMap())
    val lyrics: StateFlow<Map<String, LyricsUi>> = _lyrics.asStateFlow()

    fun requestLyrics(track: Track) {
        val existing = _lyrics.value[track.id]
        if (existing != null && !existing.loading) return
        if (existing != null) return // already loading — synced/plain dono
        viewModelScope.launch {
            _lyrics.value = _lyrics.value + (track.id to LyricsUi(loading = true))

            // Pahle TIME-SYNCED (LRCLIB/LRC) — realtime highlight ke liye.
            val timed = runCatching { music.timedLyrics(track) }.getOrNull()
            if (!timed.isNullOrEmpty()) {
                android.util.Log.d("RivaLyrics", "synced ${track.id}: ${timed.size} lines")
                _lyrics.value = _lyrics.value + (track.id to LyricsUi(syncedLines = timed, synced = true))
                return@launch
            }

            val result = runCatching { music.lyrics(track) }
            result.onFailure {
                android.util.Log.e("RivaLyrics", "fail ${track.id}: ${it.message}")
            }
            result.onSuccess {
                android.util.Log.d("RivaLyrics", "ok ${track.id}: ${it.lines.size} lines (plain)")
            }
            _lyrics.value = _lyrics.value + when {
                result.isSuccess -> {
                    val lines = result.getOrThrow().lines
                    track.id to LyricsUi(lines = lines, failed = lines.isEmpty())
                }
                else -> track.id to LyricsUi(failed = true)
            }
        }
    }

    fun playNext() {
        if (_repeatMode.value == RepeatMode.ONE) {
            player.replay()
            return
        }
        val list = queue.value
        if (list.isEmpty()) return
        if (queueIndex + 1 < list.size) {
            queueIndex += 1
            startTrack(list[queueIndex])
            return
        }
        // If repeat all is on, loop back to the beginning of the queue
        if (_repeatMode.value == RepeatMode.ALL) {
            queueIndex = 0
            startTrack(list[0])
            return
        }
        // In playlist mode, strictly loop within the playlist and do NOT inject outside songs
        if (isPlaylistMode) {
            queueIndex = 0
            startTrack(list[0])
            return
        }
        // Queue khatam: related songs (radio) append karo.
        viewModelScope.launch {
            val cur = _currentTrack.value ?: return@launch
            val existing = list.map { it.id }.toSet()
            val chain = relatedChain(cur).filterNot { it.id in existing }
            val extended = if (chain.isNotEmpty()) list + chain else null
            if (extended != null) {
                val toAdd = if (_shuffleEnabled.value) chain.shuffled() else chain
                val updated = list + toAdd
                queue.value = updated
                baseQueue = list + chain
                queueIndex += 1
                startTrack(updated[queueIndex])
            } else {
                queueIndex = 0
                startTrack(list[0])
            }
        }
    }

    /**
     * Reels swipe navigation (loop-bug fix): swipe settle page index -> us
     * queue position ko hi play karo. Queue reset NAHI hoti, repeat-one
     * explicit swipe ko override nahi karta, aur same page re-settle kabhi
     * song restart nahi karta.
     */
    fun playReelPage(index: Int) {
        if (_repeatMode.value == RepeatMode.ONE) {
            player.replay()
            return
        }
        val list = queue.value
        val target = list.getOrNull(index) ?: return
        val cur = _currentTrack.value
        if (cur != null && target.id == cur.id) {
            if (_repeatMode.value == RepeatMode.ALL || isPlaylistMode) {
                player.replay()
            }
            return
        }
        queueIndex = index
        startTrack(target)
    }

    /**
     * Radio chain: SAME vibe, DIFFERENT song.
     * Root cause of 'ek hi song alag channel se repeat' — title+artist query
     * wahi song ke slowdown/cover/lyrics-video versions laata tha. Ab:
     *  1) mood/type keywords (sad/lofi/party/romantic...) + artist se search
     *  2) title-token overlap se same-song variants filter out
     */
    private suspend fun relatedChain(track: Track): List<Track> {
        val curWords = titleTokens(track.title)
        val curKey = curWords.take(3).toSet()
        val mood = moodOf("${track.title} ${track.artist}")
        val queries = buildList {
            add("best " + (mood + listOf(track.artist, "songs")).joinToString(" "))
            add("trending ${mood.joinToString(" ")} songs 2026".trim())
            // Fallback: title-ref search (purana behaviour), same-song filter ke saath.
            add("${track.title.take(40)} ${track.artist}")
        }
        val seen = mutableSetOf(track.id)
        for (query in queries) {
            val found = runCatching { music.searchMusicTracks(query, 25) }
                .getOrDefault(emptyList())
                .ifEmpty { runCatching { music.search(query, 25) }.getOrDefault(emptyList()) }
                .asSequence()
                .filter { it.id !in seen && music.isRealSong(it) }
                .filter { cand -> titleTokens(cand.title).intersect(curKey).size < 2 }
                .filter { it.id != track.id }
                .distinctBy { it.id }
                .take(10)
                .toList()
            found.forEach { seen.add(it.id) }
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    /** Meaningful title tokens (brackets/labels/pipe-junk हटा के). */
    private fun titleTokens(title: String): Set<String> {
        val cleaned = title.lowercase()
            .replace(Regex("\\(.*?\\)"), " ")
            .replace(Regex("\\[.*?\\]"), " ")
            .replace(Regex("\\|.*"), " ")
            .replace(Regex("lyrics|official|video|audio|hd|4k|full song|feat"), " ")
        return cleaned.split(Regex("[^a-z0-9\\u0900-\\u097F]+"))
            .filter { it.length > 2 && it !in setOf("song", "music", "hindi", "from") }
            .take(8)
            .toSet()
    }

    /** Title/artist se mood/type guess — sad ko sad, lofi ko lofi. */
    private fun moodOf(text: String): List<String> {
        val l = text.lowercase()
        return when {
            listOf("sad", "dukh", "dard", "broken", "alone", "rola").any { l.contains(it) } -> listOf("sad")
            listOf("party", "dance", "club", "nonstop", "dhol").any { l.contains(it) } -> listOf("party")
            listOf("romantic", "love", "pyaar", "ishq", "wedding").any { l.contains(it) } -> listOf("romantic")
            listOf("lofi", "chill", "slowed", "study", "focus", "sleep", "rain").any { l.contains(it) } -> listOf("lofi")
            else -> emptyList()
        }
    }

    private fun handleTrackEnded() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> player.replay()
            RepeatMode.ALL -> playNext()
            RepeatMode.OFF -> {
                val list = queue.value
                if (queueIndex + 1 < list.size) {
                    queueIndex += 1
                    startTrack(list[queueIndex])
                } else if (isPlaylistMode && list.isNotEmpty()) {
                    queueIndex = 0
                    startTrack(list[0])
                }
            }
        }
    }

    fun playPrevious() {
        if (_repeatMode.value == RepeatMode.ONE) {
            player.replay()
            return
        }
        if (playerState.value.positionMs > 3_000L) {
            player.seekTo(0)
            return
        }
        val list = queue.value
        if (list.isEmpty()) return
        queueIndex = if (queueIndex - 1 < 0) list.lastIndex else queueIndex - 1
        startTrack(list[queueIndex])
    }

    fun toggleShuffle() {
        setShuffle(!_shuffleEnabled.value)
    }

    fun setShuffle(enabled: Boolean) {
        if (_shuffleEnabled.value == enabled) return
        _shuffleEnabled.value = enabled
        playerPrefs.edit().putBoolean(KEY_SHUFFLE_ENABLED, enabled).apply()
        val current = _currentTrack.value
        if (current == null || baseQueue.isEmpty()) return
        if (enabled) {
            val rest = baseQueue.filterNot { it.id == current.id }.shuffled()
            queue.value = listOf(current) + rest
            queueIndex = 0
        } else {
            queue.value = baseQueue
            queueIndex = baseQueue.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        }
        _messages.tryEmit(if (enabled) "Shuffle on" else "Shuffle off")
    }

    fun cycleRepeatMode() {
        val next = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = next
        playerPrefs.edit().putString(KEY_REPEAT_MODE, next.name).apply()
        _messages.tryEmit(
            when (next) {
                RepeatMode.OFF -> "Repeat off"
                RepeatMode.ALL -> "Repeat playlist"
                RepeatMode.ONE -> "Repeat one song"
            },
        )
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (minutes == null || minutes <= 0) {
            _sleepTimerMinutes.value = null
            _sleepRemainingSeconds.value = 0L
            _messages.tryEmit("Sleep timer off")
            return
        }

        _sleepTimerMinutes.value = minutes
        _sleepRemainingSeconds.value = minutes * 60L
        _messages.tryEmit("Sleep timer set for $minutes min")
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes * 60L
            while (remaining > 0L) {
                delay(1_000L)
                remaining -= 1L
                _sleepRemainingSeconds.value = remaining
            }
            player.pause()
            _sleepTimerMinutes.value = null
            _sleepRemainingSeconds.value = 0L
            _messages.tryEmit("Sleep timer ended. Sweet dreams.")
        }
    }

    fun togglePlayPause() = player.togglePlayPause()

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun openAuthPrompt() { _showAuthPrompt.value = true }
    fun closeAuthPrompt() { _showAuthPrompt.value = false }
    fun getGoogleSignInClient(): GoogleSignInClient = authRepo.getGoogleSignInClient()

    fun signInWithGoogle(idToken: String, onResult: (Boolean, String?) -> Unit) {
        authRepo.signInWithGoogleIdToken(idToken) { result ->
            result.onSuccess { profile ->
                _showAuthPrompt.value = false
                _messages.tryEmit("Welcome, ${profile.displayName}!")
                syncCloudOfflineTracks()
                if (!homePrefs.getBoolean(KEY_LANGUAGES_CONFIGURED, false)) {
                    _showLanguagePrompt.value = true
                }
                onResult(true, null)
            }.onFailure { err ->
                _messages.tryEmit("Login failed: ${err.message}")
                onResult(false, err.message)
            }
        }
    }

    fun signInAsGuest(name: String, onComplete: () -> Unit) {
        authRepo.signInAsGuest(name) { profile ->
            _showAuthPrompt.value = false
            _messages.tryEmit("Continuing as ${profile.displayName}")
            if (!homePrefs.getBoolean(KEY_LANGUAGES_CONFIGURED, false)) {
                _showLanguagePrompt.value = true
            }
            onComplete()
        }
    }

    fun signOut(googleSignInClient: GoogleSignInClient?, onComplete: () -> Unit) {
        authRepo.signOut(googleSignInClient) {
            _messages.tryEmit("Signed out")
            _showAuthPrompt.value = true
            onComplete()
        }
    }

    fun syncCloudOfflineTracks() {
        val user = currentUser.value ?: return
        if (user.isGuest) return
        viewModelScope.launch {
            try {
                val cloudTracks = firestoreSync.fetchOfflineTracks(user.uid)
                if (cloudTracks.isEmpty()) return@launch
                val currentLocalIds = downloads.value.filterNot { it.isCached }.map { it.track.id }.toSet()
                var added = 0

                for (cloud in cloudTracks) {
                    if (!currentLocalIds.contains(cloud.id)) {
                        val track = Track(
                            id = cloud.id,
                            title = cloud.title,
                            artist = cloud.artist,
                            thumbnailUrl = cloud.thumbnailUrl,
                            durationSeconds = cloud.durationSeconds,
                        )
                        runCatching {
                            if (!cloud.audioUrl.isNullOrBlank()) {
                                downloadsRepo.download(
                                    track,
                                    StreamResolution(
                                        url = cloud.audioUrl,
                                        mimeType = "audio/mp4",
                                        itag = 140,
                                        userAgent = PlaybackService.USER_AGENT,
                                        headers = emptyMap(),
                                    )
                                )
                                added++
                            } else {
                                val res = music.resolveAudio(track)
                                downloadsRepo.download(track, res)
                                added++
                            }
                        }
                    }
                }
                if (added > 0) {
                    _messages.tryEmit("Restored $added songs from your cloud account")
                }
            } catch (_: Exception) {}
        }
    }

    fun download(track: Track) {
        val user = currentUser.value
        if (user == null || user.isGuest) {
            _showAuthPrompt.value = true
            _messages.tryEmit("Sign in with Google to save offline music")
            return
        }
        val entry = downloads.value.firstOrNull { it.track.id == track.id }
        if (entry != null && !entry.isCached) {
            _messages.tryEmit("Already saved for offline listening")
            return
        }
        if (entry != null && entry.isCached) {
            // Cached copy exists — promote it to a permanent save instead of downloading again.
            if (downloadsRepo.promoteToManual(track.id)) {
                _messages.tryEmit("Saved offline permanently")
                viewModelScope.launch {
                    firestoreSync.saveOfflineTrack(user.uid, track)
                }
            }
            return
        }
        viewModelScope.launch {
            try {
                val resolution = music.resolveAudio(track)
                downloadsRepo.download(track, resolution)
                firestoreSync.saveOfflineTrack(user.uid, track, resolution.url)
            } catch (t: Throwable) {
                _messages.tryEmit("Download failed: ${t.message}")
            }
        }
    }

    /**
     * Promotes an auto-cached (30-day) file to a permanent manual offline save.
     * Falls back to a fresh manual download when the cached file is unusable.
     */
    fun promoteCachedToOffline(track: Track) {
        val user = currentUser.value
        if (user == null || user.isGuest) {
            _showAuthPrompt.value = true
            _messages.tryEmit("Sign in with Google to save offline music")
            return
        }
        viewModelScope.launch {
            if (downloadsRepo.promoteToManual(track.id)) {
                _messages.tryEmit("Saved offline permanently")
                firestoreSync.saveOfflineTrack(user.uid, track)
            } else {
                downloadsRepo.delete(track.id)
                download(track)
            }
        }
    }

    fun deleteDownload(trackId: String) {
        downloadsRepo.delete(trackId)
        val uid = currentUser.value?.uid
        if (uid != null && !uid.startsWith("guest")) {
            viewModelScope.launch {
                firestoreSync.removeOfflineTrack(uid, trackId)
            }
        }
    }

    fun clearFinishedDownloadStatus(trackId: String) {
        downloadsRepo.clearCompletedStatus(trackId)
    }

    fun createPlaylist(name: String): String {
        val playlist = playlistRepo.create(name)
        _messages.tryEmit("Playlist \"${playlist.name}\" created")
        return playlist.id
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        val playlist = playlists.value.firstOrNull { it.id == playlistId } ?: return
        if (playlistRepo.addTrack(playlistId, track)) {
            _messages.tryEmit("Added to ${playlist.name}")
        } else {
            _messages.tryEmit("Already in ${playlist.name}")
        }
    }

    fun createPlaylistWithTrack(name: String, track: Track) {
        val playlist = playlistRepo.create(name)
        playlistRepo.addTrack(playlist.id, track)
        _messages.tryEmit("Playlist \"${playlist.name}\" created")
    }

    fun importYouTubePlaylist(urlOrId: String, onDone: ((Boolean) -> Unit)? = null) {
        val clean = urlOrId.trim()
        if (clean.isBlank()) {
            _messages.tryEmit("Link ya Playlist ID daalein")
            onDone?.invoke(false)
            return
        }
        viewModelScope.launch {
            _isImportingPlaylist.value = true
            try {
                _messages.tryEmit("YouTube playlist fetch ho rahi hai...")
                val (title, tracks) = music.fetchPlaylist(clean)
                val playlist = playlistRepo.createWithTracks(title, tracks)
                _messages.tryEmit("Playlist \"${playlist.name}\" (${tracks.size} songs) import ho gayi!")
                onDone?.invoke(true)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _messages.tryEmit("Import failed: ${t.message ?: "Unknown error"}")
                onDone?.invoke(false)
            } finally {
                _isImportingPlaylist.value = false
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        playlistRepo.delete(playlistId)
        _messages.tryEmit("Playlist deleted")
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistRepo.removeTrack(playlistId, trackId)
    }

    fun playPlaylist(playlist: Playlist) {
        if (playlist.tracks.isEmpty()) {
            _messages.tryEmit("Playlist is empty")
            return
        }
        isPlaylistMode = true
        playAll(playlist.tracks, _shuffleEnabled.value, isPlaylist = true)
    }

    fun isDownloaded(trackId: String): Boolean = downloadsRepo.isDownloaded(trackId)

    fun localPath(trackId: String): String? = downloadsRepo.localPath(trackId)

    private fun startTrack(track: Track) {
        resolveJob?.cancel()
        _currentTrack.value = track
        _isResolving.value = true
        resolveJob = viewModelScope.launch {
            try {
                val local = downloadsRepo.localPath(track.id)
                if (local != null) {
                    val localTrack = track.copy(localPath = local)
                    _currentTrack.value = localTrack
                    player.play(localTrack, local)
                } else {
                    val resolution = music.resolveAudio(track)
                    player.play(track, resolution.url)
                    // Auto-cache: recently played tracks get saved for offline replay
                    // for the next 30 days.
                    downloadsRepo.autoCache(track, resolution)
                }
                recentRepo.add(track)
                autoSkips = 0
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                android.util.Log.e("RivaPlay", "resolve fail ${track.id}: ${t.message}")
                val message = t.message.orEmpty()
                val blockedByYouTube = message.contains("confirm you", ignoreCase = true) ||
                    message.contains("not a bot", ignoreCase = true)
                if (blockedByYouTube && autoSkips < 5 && queue.value.size > 1) {
                    // Compilation/blocked video — queue me agla gaana khud chala do.
                    autoSkips += 1
                    _messages.tryEmit("Ye video YouTube ne block kiya — agla gaana laga rahe hain")
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(300)
                        playNext()
                    }
                } else if (blockedByYouTube) {
                    _messages.tryEmit(
                        "YouTube ne ye video is network pe block kiya hai — doosra gaana try karein",
                    )
                } else {
                    _messages.tryEmit("Could not play: ${t.message}")
                }
            } finally {
                _isResolving.value = false
            }
        }
    }

    override fun onCleared() {
        resolveJob?.cancel()
        sleepTimerJob?.cancel()
        player.release()
        super.onCleared()
    }

    private data class HomeSectionSpec(
        val id: String,
        val title: String,
        val subtitle: String,
        val query: String,
        val chartBrowseId: String? = null,
    )

    companion object {
        private const val KEY_HOME_SECTIONS = "home_sections"
        private const val KEY_SHUFFLE_ENABLED = "shuffle_enabled"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val KEY_SELECTED_REGION = "selected_region"
        private const val KEY_SELECTED_LANGUAGES = "selected_languages"
        private const val KEY_LANGUAGES_CONFIGURED = "languages_configured"
    }
}

/**
 * Lyrics cache entry: trackId -> lines (artwork tap toggles; one-time load).
 */
data class LyricsUi(
    val lines: List<String> = emptyList(),
    val loading: Boolean = false,
    val failed: Boolean = false,
    val syncedLines: List<LyricLine> = emptyList(),
    val synced: Boolean = false,
)
