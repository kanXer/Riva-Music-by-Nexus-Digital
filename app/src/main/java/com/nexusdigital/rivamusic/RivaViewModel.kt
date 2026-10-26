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
import kotlinx.coroutines.Dispatchers
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
        homePrefs.getStringSet(KEY_SELECTED_LANGUAGES, null) ?: setOf("Hindi")
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

    private val _isDarkMode = MutableStateFlow(
        homePrefs.getBoolean("is_dark_mode", false)
    )
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        val next = !_isDarkMode.value
        _isDarkMode.value = next
        homePrefs.edit().putBoolean("is_dark_mode", next).apply()
    }

    fun setDarkMode(dark: Boolean) {
        _isDarkMode.value = dark
        homePrefs.edit().putBoolean("is_dark_mode", dark).apply()
    }

    private val _similarTracks = MutableStateFlow<List<Track>>(emptyList())
    val similarTracks: StateFlow<List<Track>> = _similarTracks.asStateFlow()

    private val _isLoadingSimilar = MutableStateFlow(false)
    val isLoadingSimilar: StateFlow<Boolean> = _isLoadingSimilar.asStateFlow()

    fun loadSimilarTracks(track: Track) {
        viewModelScope.launch {
            _isLoadingSimilar.value = true
            val userLangs = _selectedLanguages.value.ifEmpty { setOf("Hindi") }
            val primaryLang = userLangs.firstOrNull() ?: "Hindi"
            val radio = runCatching { music.fetchRadioTracks(track.id, 40) }.getOrDefault(emptyList())
                .filter { music.isRealSong(it) && isMatchingSelectedLanguages(it, userLangs) }
            val searchResults = runCatching { music.searchMusicTracks("$primaryLang ${track.artist} hits", 30) }
                .getOrDefault(emptyList())
                .filter { music.isRealSong(it) && isMatchingSelectedLanguages(it, userLangs) }
            _similarTracks.value = (radio + searchResults).distinctBy { it.id }.take(50)
            _isLoadingSimilar.value = false
        }
    }

    private var sleepTimerJob: Job? = null

    init {
        music.setRegion(_selectedRegion.value.code)
        if (currentUser.value != null && !currentUser.value!!.isGuest) {
            syncCloudOfflineTracks()
            syncCloudPreferences()
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
        // Auto-cache all recently played tracks so everything in recent history is cached offline
        viewModelScope.launch(Dispatchers.IO) {
            recentRepo.recent.collect { list ->
                list.forEach { tr ->
                    if (!downloadsRepo.isDownloaded(tr.id)) {
                        downloadsRepo.cacheTrackIfNeeded(tr, music)
                    }
                }
            }
        }
        viewModelScope.launch {
            player.events.collect { event ->
                when (event) {
                    PlaybackEvent.Ended -> handleTrackEnded()
                    PlaybackEvent.SkipNext -> playNext()
                    PlaybackEvent.SkipPrevious -> playPrevious()
                    PlaybackEvent.Stop -> handlePlaybackStopped()
                    is PlaybackEvent.Error -> handlePlaybackError(event.message)
                }
            }
        }
    }

    /** Track ids already retried via streaming fallback so we never loop. */
    private val streamedFallback = mutableSetOf<String>()

    fun postMessage(msg: String) {
        _messages.tryEmit(msg)
    }

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
            val chosen = _selectedLanguages.value.ifEmpty { setOf("Hindi") }
            val region = _selectedRegion.value
            val primaryLang = chosen.firstOrNull() ?: "Hindi"
            val specs = mutableListOf<HomeSectionSpec>()

            fun chartIdForLanguage(lang: String): String? = when (lang.lowercase()) {
                "hindi" -> "VLPL4fGSI1pDJn5RgLW0Sb_zECecWdH_4zOX"
                "punjabi" -> "VLPL4fGSI1pDJn5JXkyIohg2RstsbL2SnRew"
                "english" -> "VLPL4fGSI1pDJn49TUu37nJoN2QTeYuRwmNv"
                "bhojpuri" -> "VLPL4fGSI1pDJn4ivDqrsepD3tvHsp0KTDRM"
                "haryanvi" -> "VLPL4fGSI1pDJn4tiNLMZVGGt2Kghgw__2u0"
                "tamil" -> "VLPL4fGSI1pDJn4WX22qg1Po7qKOwOb4H6Sk"
                "telugu" -> "VLPL4fGSI1pDJn5ALuqpEj_YZ8mEyw9WN8jd"
                else -> null
            }

            // 1. Trending strictly in selected languages
            val trendingTitle = if (chosen.size == 1) {
                "Trending ${chosen.first()} Songs"
            } else {
                "Trending Hits (${chosen.joinToString(", ")})"
            }

            val trendingChartId = if (chosen.size == 1) chartIdForLanguage(chosen.first()) else null
            val trendingQuery = if (chosen.size == 1) {
                "trending ${chosen.first().lowercase()} songs top hits"
            } else {
                "${chosen.joinToString(" ")} trending songs top hits"
            }

            specs.add(
                HomeSectionSpec(
                    id = "trending",
                    title = trendingTitle,
                    subtitle = "Top chartbusters in ${chosen.joinToString(", ")}",
                    query = trendingQuery,
                    chartBrowseId = trendingChartId,
                )
            )

            // 2. Dedicated language sections
            for (lang in chosen.take(6)) {
                val chartId = chartIdForLanguage(lang)
                val spec = when (lang.lowercase()) {
                    "hindi" -> HomeSectionSpec("hindi", "Bollywood & Hindi Hits", "Top Hindi chartbusters", "bollywood hindi hits", chartId)
                    "english" -> HomeSectionSpec("english", "Global Pop Hits", "Top international hits", "pop english hits", chartId)
                    "punjabi" -> HomeSectionSpec("punjabi", "Punjabi Bangers", "Bhangra beats & high bass", "punjabi hits", chartId)
                    "bhojpuri" -> HomeSectionSpec("bhojpuri", "Bhojpuri Dhamaka", "Desi vibes & festive party", "bhojpuri hits", chartId)
                    "haryanvi" -> HomeSectionSpec("haryanvi", "Haryanvi Hits", "Desi swag & folk beats", "haryanvi hits", chartId)
                    "tamil" -> HomeSectionSpec("tamil", "Tamil Blockbusters", "Kollywood chartbusters", "tamil hits", chartId)
                    "telugu" -> HomeSectionSpec("telugu", "Telugu Chartbusters", "Tollywood sensations", "telugu hits", chartId)
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
            specs.add(
                HomeSectionSpec(
                    id = "chill",
                    title = "Chill & Focus",
                    subtitle = "Low-key beats in ${chosen.joinToString(", ")}",
                    query = "${chosen.joinToString(" ")} lofi acoustic songs",
                )
            )

            val loaded = coroutineScope {
                specs.map { spec ->
                    async {
                        val targetLangs = when (spec.id) {
                            "trending", "chill" -> chosen
                            else -> setOf(spec.id.replaceFirstChar { it.uppercase() })
                        }
                        var tracks: List<Track> = emptyList()

                        if (spec.id == "trending" && chosen.size > 1) {
                            val multi = mutableListOf<Track>()
                            for (l in chosen) {
                                val cid = chartIdForLanguage(l)
                                val lTracks = runCatching {
                                    if (!cid.isNullOrBlank()) {
                                        music.fetchMusicCollection(cid, "Trending $l").tracks
                                            .filter { music.isRealSong(it) && isMatchingSelectedLanguages(it, setOf(l)) }
                                            .take(15)
                                    } else {
                                        music.searchMusicTracks("trending ${l.lowercase()} songs", 20)
                                            .filter { music.isRealSong(it) && isMatchingSelectedLanguages(it, setOf(l)) }
                                            .take(15)
                                    }
                                }.getOrElse { emptyList() }
                                multi.addAll(lTracks)
                            }
                            tracks = multi.distinctBy { it.id }.take(30)
                        } else {
                            if (!spec.chartBrowseId.isNullOrBlank()) {
                                tracks = runCatching {
                                    music.fetchMusicCollection(spec.chartBrowseId, spec.title).tracks
                                        .filter { music.isRealSong(it) && isMatchingSelectedLanguages(it, targetLangs) }
                                        .take(25)
                                }.getOrElse { emptyList() }
                            }
                            if (tracks.size < 12) {
                                val searched = runCatching {
                                    music.searchMusicTracks(spec.query, 30)
                                        .filter { music.isRealSong(it) && isMatchingSelectedLanguages(it, targetLangs) }
                                }.getOrElse { emptyList() }
                                tracks = (tracks + searched).distinctBy { it.id }.take(25)
                            }
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
        val chosen = languages.ifEmpty { setOf("Hindi") }
        _selectedRegion.value = region
        _selectedLanguages.value = chosen
        music.setRegion(region.code)
        homePrefs.edit()
            .putString(KEY_SELECTED_REGION, region.code)
            .putStringSet(KEY_SELECTED_LANGUAGES, chosen)
            .putBoolean(KEY_LANGUAGES_CONFIGURED, true)
            .remove(KEY_HOME_SECTIONS)
            .apply()
        _showLanguagePrompt.value = false
        _messages.tryEmit("Region: ${region.flag} ${region.name} • ${chosen.joinToString(", ")}")

        val user = currentUser.value
        if (user != null && !user.isGuest) {
            viewModelScope.launch(Dispatchers.IO) {
                firestoreSync.saveUserPreferences(user.uid, region.code, chosen)
            }
        }

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
        val chosen = _selectedLanguages.value.ifEmpty { setOf("Hindi") }
        return runCatching {
            val list = json.decodeFromString<List<HomeSection>>(raw)
            list.mapNotNull { sec ->
                val filtered = sec.tracks.filter { music.isRealSong(it) && isMatchingSelectedLanguages(it, chosen) }
                if (filtered.isEmpty()) null else sec.copy(tracks = filtered)
            }
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
        val userLangs = _selectedLanguages.value.ifEmpty { setOf("Hindi") }
        val rawList = tracks.ifEmpty { listOf(track) }
        val filteredList = if (!isPlaylist || (rawList.size > 1 && !rawList.all { isMatchingSelectedLanguages(it, userLangs) })) {
            val matching = rawList.filter { isMatchingSelectedLanguages(it, userLangs) }
            if (matching.isNotEmpty()) {
                if (matching.any { it.id == track.id }) matching else listOf(track) + matching.filter { it.id != track.id }
            } else rawList
        } else {
            rawList
        }
        baseQueue = filteredList
        if (_shuffleEnabled.value) {
            val shuffled = listOf(track) + filteredList.filterNot { it.id == track.id }.shuffled()
            queue.value = shuffled
            queueIndex = 0
            startTrack(shuffled.first())
        } else {
            val index = filteredList.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            queue.value = filteredList
            queueIndex = index
            startTrack(filteredList[index])
        }
    }

    fun playAll(tracks: List<Track>, shuffle: Boolean = _shuffleEnabled.value, isPlaylist: Boolean = false) {
        if (tracks.isEmpty()) {
            _messages.tryEmit("Nothing to play here yet")
            return
        }
        val userLangs = _selectedLanguages.value.ifEmpty { setOf("Hindi") }
        val validTracks = if (isPlaylist) tracks else tracks.filter { isMatchingSelectedLanguages(it, userLangs) }
        val toPlay = validTracks.ifEmpty { tracks }
        isPlaylistMode = isPlaylist
        val shouldShuffle = shuffle || _shuffleEnabled.value
        playTrack(if (shouldShuffle) toPlay.random() else toPlay.first(), toPlay, isPlaylist = isPlaylist)
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
        if (existing != null && !existing.loading && !existing.failed) return
        if (existing?.loading == true) return // already loading
        viewModelScope.launch(Dispatchers.IO) {
            _lyrics.value = _lyrics.value + (track.id to LyricsUi(loading = true))

            // Plain lyrics ko concurrently launch karo as instant fallback
            val plainDeferred = async {
                runCatching { music.lyrics(track) }.getOrNull()
            }

            // Realtime time-synced lyrics (LRCLIB ~600ms, phir BetterLyrics)
            val timed = runCatching { music.timedLyrics(track) }.getOrNull()
            if (!timed.isNullOrEmpty()) {
                android.util.Log.d("RivaLyrics", "synced ${track.id}: ${timed.size} lines")
                _lyrics.value = _lyrics.value + (track.id to LyricsUi(syncedLines = timed, synced = true))
                return@launch
            }

            // Agar synced nahi mila, plain lyrics ready milengi
            val plain = plainDeferred.await()
            val lines = plain?.lines.orEmpty()
            _lyrics.value = _lyrics.value + (track.id to LyricsUi(
                lines = lines,
                failed = lines.isEmpty(),
            ))
        }
    }

    fun playNext() {
        if (_repeatMode.value == RepeatMode.ONE) {
            player.replay()
            return
        }
        val userLangs = _selectedLanguages.value.ifEmpty { setOf("Hindi") }
        val list = queue.value
        if (list.isEmpty()) return

        var nextIdx = queueIndex + 1
        while (nextIdx < list.size) {
            val candidate = list[nextIdx]
            if (isMatchingSelectedLanguages(candidate, userLangs)) {
                queueIndex = nextIdx
                startTrack(candidate)
                return
            }
            nextIdx++
        }

        // If repeat all is on, loop back to the beginning of the queue
        if (_repeatMode.value == RepeatMode.ALL) {
            val firstMatching = list.indexOfFirst { isMatchingSelectedLanguages(it, userLangs) }
            if (firstMatching >= 0) {
                queueIndex = firstMatching
                startTrack(list[firstMatching])
                return
            }
        }

        // In playlist mode, strictly loop within the playlist and do NOT inject outside songs
        if (isPlaylistMode) {
            val firstMatching = list.indexOfFirst { isMatchingSelectedLanguages(it, userLangs) }
            if (firstMatching >= 0) {
                queueIndex = firstMatching
                startTrack(list[firstMatching])
                return
            }
        }

        // Queue khatam: related songs strictly matching selected languages append karo.
        viewModelScope.launch {
            val cur = _currentTrack.value ?: return@launch
            val existing = list.map { it.id }.toSet()
            val chain = relatedChain(cur).filterNot { it.id in existing }
            if (chain.isNotEmpty()) {
                val toAdd = if (_shuffleEnabled.value) chain.shuffled() else chain
                val updated = list + toAdd
                queue.value = updated
                baseQueue = list + chain
                queueIndex = list.size
                startTrack(updated[queueIndex])
            } else {
                val fallbackIdx = list.indexOfFirst { isMatchingSelectedLanguages(it, userLangs) }.coerceAtLeast(0)
                queueIndex = fallbackIdx
                startTrack(list[fallbackIdx])
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
    /**
     * Strictly verifies if a candidate track matches the user's chosen languages
     * and filters out foreign/unwanted language songs (e.g. Persian/Farsi, Arabic, Russian, Turkish, etc.)
     * as well as unselected Indian regional languages and Western songs if English is not selected.
     */
    private fun isMatchingSelectedLanguages(candidate: Track, userLanguages: Set<String>): Boolean {
        val title = candidate.title
        val artist = candidate.artist
        val fullText = "$title $artist".lowercase()

        // 1. Foreign script checks:
        // Arabic / Persian / Farsi / Urdu characters
        val hasArabicOrPersianScript = title.any {
            it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\u08A0'..'\u08FF' ||
            it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF'
        } || artist.any {
            it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\u08A0'..'\u08FF' ||
            it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF'
        }
        if (hasArabicOrPersianScript && "Urdu" !in userLanguages) {
            return false // Farsi / Arabic / Persian song detected without Urdu selected
        }

        // Cyrillic (Russian, etc.)
        if (title.any { it in '\u0400'..'\u04FF' } || artist.any { it in '\u0400'..'\u04FF' }) {
            return false
        }

        // CJK / Hangul / Thai / Hebrew / Greek
        if (title.any {
                it in '\u4E00'..'\u9FFF' || it in '\uAC00'..'\uD7AF' || it in '\u3040'..'\u30FF' ||
                it in '\u0E00'..'\u0E7F' || it in '\u0590'..'\u05FF' || it in '\u0370'..'\u03FF'
            } || artist.any {
                it in '\u4E00'..'\u9FFF' || it in '\uAC00'..'\uD7AF' || it in '\u3040'..'\u30FF' ||
                it in '\u0E00'..'\u0E7F' || it in '\u0590'..'\u05FF' || it in '\u0370'..'\u03FF'
            }) {
            return false
        }

        // 2. Explicit foreign language keywords in title or artist (transliterated):
        val foreignKeywords = listOf(
            "farsi", "persian", "iran", "irani", "ahang", "ahangi", "taraneh", "ghadimi",
            "aramesh", "shadmehr", "aghili", "ebi", "moein", "googoosh", "sasy", "tataloo", "chavoshi",
            "mohsen chavoshi", "mohsen yeganeh", "yeganeh", "khosravi", "sirvan khosravi", "xaniar",
            "reza sadeghi", "aron afshar", "garsha rezaei", "behnam bani", "macan band", "evan band",
            "babak jahanbakhsh", "alireza talischi", "meysam ebrahimi", "kasra zahedi", "ali yasini",
            "ragheb", "sina derakhshande", "shervin hajipour", "hoorosh", "puzzle band", "chaartaar",
            "sogand", "zedbazi", "mehrad hidden", "homayoun shajarian", "shajarian", "hayedeh", "mahasti",
            "homeyra", "kourosh yaghmaei", "yaghmaei", "pouya", "dariush", "siavash ghomayshi",
            "radio javan", "pmc music", "avang", "taraneh records", "persian music", "persian pop",
            "persian remix", "ahang jadid", "ahang farsi", "remix farsi", "asheghaneh", "delbar", "delam",
            "beman", "tohi", "arash", "kamran hooman", "shahram shabpareh", "sattar", "bijan mortazavi",
            "farhad mehrad", "vigen", "habib", "morvarid", "masih arash", "ashvan", "naser zeynali",
            "mohammad alizadeh", "farzad farzin", "saman jalili", "reza bahram", "ali zand vakili",
            "hojat ashrafzadeh", "arash ap", "haamim", "hichkas", "fadaei", "shayea", "bahram",
            "putak", "sohrab mj", "wantons", "koorosh", "sami beigi", "jamal jamaloo", "jamal kudu",
            "turkce", "turkish", "türkçe", "tarkan", "sezen aksu", "reynmen", "ezhel", "murat boz",
            "hadise", "mustafa sandal", "ebru gundes", "ibrahim tatlises", "mor ve otesi", "madrigal",
            "dedubluman", "canozan", "emre aydin", "ziynet sali", "edis", "zeynep bastik", "bilal sonses",
            "buray", "sagopa kajmer", "lvbel c5", "semicenk", "kurtulus kus", "burak bulut",
            "arabic", "khaleeji", "dabke", "rotana", "amr diab", "nancy ajram", "elissa", "tamer hosny",
            "sherine", "mohamed ramadan", "saad lamjarred", "balqees", "hussain al jassmi",
            "majid al mohandis", "assala", "ragheb alama", "kadim al sahir", "cheb khaled",
            "mahraganat", "aghani", "shaabi", "habibi", "habibtak", "tamally maak", "cairokee", "wegz",
            "afghan", "pashto", "tajik", "kurdish", "azeri", "azerbaijan", "russian", "morgenshtern",
            "kizaru", "jah khalib", "anna asti", "jony", "rauf faik",
            "k-pop", "kpop", "j-pop", "jpop", "c-pop", "anime", "bts", "blackpink", "twice", "stray kids",
            "newjeans", "aespa", "ive", "le sserafim", "reggaeton", "bachata", "bad bunny", "j balvin",
            "ozuna", "daddy yankee", "maluma", "karol g", "rauw alejandro", "pesopluma", "shakira",
            "francais", "gims", "stromae", "maneskin"
        )
        for (kw in foreignKeywords) {
            if (kw.contains(" ")) {
                if (fullText.contains(kw)) return false
            } else {
                if (fullText.contains(Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE))) {
                    return false
                }
            }
        }

        // 3. Indian language scripts:
        val hasGurmukhi = title.any { it in '\u0A00'..'\u0A7F' } || artist.any { it in '\u0A00'..'\u0A7F' }
        if (hasGurmukhi && "Punjabi" !in userLanguages) return false

        val hasTamil = title.any { it in '\u0B80'..'\u0BFF' } || artist.any { it in '\u0B80'..'\u0BFF' }
        if (hasTamil && "Tamil" !in userLanguages) return false

        val hasTelugu = title.any { it in '\u0C00'..'\u0C7F' } || artist.any { it in '\u0C00'..'\u0C7F' }
        if (hasTelugu && "Telugu" !in userLanguages) return false

        val hasKannada = title.any { it in '\u0C80'..'\u0CFF' } || artist.any { it in '\u0C80'..'\u0CFF' }
        if (hasKannada && "Kannada" !in userLanguages) return false

        val hasMalayalam = title.any { it in '\u0D00'..'\u0D7F' } || artist.any { it in '\u0D00'..'\u0D7F' }
        if (hasMalayalam && "Malayalam" !in userLanguages) return false

        val hasBengali = title.any { it in '\u0980'..'\u09FF' } || artist.any { it in '\u0980'..'\u09FF' }
        if (hasBengali && "Bengali" !in userLanguages) return false

        val hasGujarati = title.any { it in '\u0A80'..'\u0AFF' } || artist.any { it in '\u0A80'..'\u0AFF' }
        if (hasGujarati && "Gujarati" !in userLanguages) return false

        val hasOdia = title.any { it in '\u0B00'..'\u0B7F' } || artist.any { it in '\u0B00'..'\u0B7F' }
        if (hasOdia && "Odia" !in userLanguages) return false

        // Devanagari script: Hindi, Marathi, Bhojpuri, Rajasthani, Haryanvi
        val hasDevanagari = title.any { it in '\u0900'..'\u097F' } || artist.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari && !userLanguages.any { it in listOf("Hindi", "Marathi", "Bhojpuri", "Rajasthani", "Haryanvi") }) {
            return false
        }

        fun matchesAny(keywords: List<String>): Boolean {
            return keywords.any { kw ->
                if (kw.contains(" ")) fullText.contains(kw)
                else fullText.contains(Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE))
            }
        }

        // 4. Regional Indian languages transliterated in Latin:
        if ("Punjabi" !in userLanguages) {
            val punjabiMarkers = listOf(
                "punjabi", "bhangra", "giddha", "jatt", "jaat", "kudi", "gabru", "pendu", "munde",
                "soorma", "boliyan", "sardar", "sardarni", "pagg", "shada", "yaari", "geetan",
                "patiala", "amritsar", "chandigarh", "ludhiana", "bamb", "mutiyar", "chobbar", "jatti",
                "asla", "hathyar", "jhumke", "surma", "pind", "majha", "malwa", "doaba",
                "karan aujla", "diljit dosanjh", "ap dhillon", "sidhu moose wala", "sidhu moosewala",
                "shubh", "amrit maan", "jordan sandhu", "mankirt aulakh", "jassie gill", "tarsem jassar",
                "nimrat khaira", "sunanda sharma", "jasmine sandlas", "gurdas maan", "babbu maan",
                "jazzy b", "garry sandhu", "gippy grewal", "amrinder gill", "parmish verma",
                "arjan dhillon", "cheema y", "gurinder gill", "wazir patar", "tegi pannu", "chani nattan",
                "hustinder", "prem dhillon", "korala maan", "shivjot", "kulwinder billa", "rajvir jawanda",
                "prabh gill", "khan bhaini", "r nait", "gurnam bhullar", "ammy virk", "balkar sidhu",
                "harbhajan mann", "manmohan waris", "kamal heer", "surjit bindrakhia", "kuldeep manak",
                "chamkila", "amar singh chamkila", "speed records", "white hill music", "geet mp3",
                "saga hits", "jass records", "apna punjab"
            )
            if (matchesAny(punjabiMarkers)) return false
        }

        if ("Bhojpuri" !in userLanguages) {
            val bhojpuriMarkers = listOf(
                "bhojpuri", "choli", "lahanga", "bhatar", "raja ji", "saiyaan", "devar", "kamariya",
                "bhojpurya", "pawan singh", "khesari lal", "dinesh lal", "nirahua", "manoj tiwari",
                "ritesh pandey", "arvind akela kallu", "samar singh", "ankush raja", "pramod premi",
                "shilpi raj", "antra singh priyanka", "akshara singh", "kalpana patowary",
                "priyanka singh", "neelkamal singh", "gunjan singh", "wave music", "srf music",
                "worldwide records bhojpuri", "aadishakti films", "yashi films"
            )
            if (matchesAny(bhojpuriMarkers)) return false
        }

        if ("Haryanvi" !in userLanguages) {
            val haryanviMarkers = listOf(
                "haryanvi", "ragni", "chori", "chora", "daman", "diler", "ghunghat", "sapna choudhary",
                "renuka panwar", "masoom sharma", "gulzaar chhaniwala", "diler kharkiya", "ajay hooda",
                "ruchika jangid", "sumit goswami", "amit saini rohtakiya", "kd desirock", "md desirock",
                "surender romio", "anu kadyan", "raju punjabi", "vishvajeet choudhary", "uttar kumar",
                "mor music", "sonotek", "vats records", "nav haryanvi"
            )
            if (matchesAny(haryanviMarkers)) return false
        }

        if ("Tamil" !in userLanguages) {
            val tamilMarkers = listOf(
                "tamil", "kollywood", "kuthu", "anirudh ravichander", "yuvan shankar raja", "harris jayaraj",
                "ilaiyaraaja", "d. imman", "santhosh narayanan", "g.v. prakash", "hiphop tamizha",
                "sam c.s.", "sean roldan", "think music india", "sony music south", "sivakarthikeyan",
                "kamal haasan", "rajinikanth"
            )
            if (matchesAny(tamilMarkers)) return false
        }

        if ("Telugu" !in userLanguages) {
            val teluguMarkers = listOf(
                "telugu", "tollywood", "s. thaman", "thaman s", "devi sri prasad", "dsp",
                "m.m. keeravaani", "keeravani", "ram miriyala", "anurag kulkarni", "mangli",
                "rahul sipligunj", "penchal das", "mickey j meyer", "vivek sagar", "chaitan bharadwaj",
                "aditya music", "lahari music"
            )
            if (matchesAny(teluguMarkers)) return false
        }

        if ("Malayalam" !in userLanguages) {
            val malayalamMarkers = listOf(
                "malayalam", "mollywood", "sushin shyam", "jakes bejoy", "shaan rahman",
                "hesham abdul wahab", "vidyasagar", "deepak dev", "k.j. yesudas", "vineeth sreenivasan",
                "sithara krishnakumar", "job kurian", "manorama music", "muzik247"
            )
            if (matchesAny(malayalamMarkers)) return false
        }

        if ("Kannada" !in userLanguages) {
            val kannadaMarkers = listOf(
                "kannada", "sandalwood", "ravi basrur", "charan raj", "arjun janya",
                "ajaneesh loknath", "v. harikrishna", "vijay prakash", "sanjith hegde",
                "raghu dixit", "anand audio"
            )
            if (matchesAny(kannadaMarkers)) return false
        }

        if ("Bengali" !in userLanguages) {
            val bengaliMarkers = listOf(
                "bengali", "bangla", "rabindra sangeet", "anupam roy", "rupam islam", "somlata",
                "iman chakraborty", "nachiketa", "shilajit", "svf music"
            )
            if (matchesAny(bengaliMarkers)) return false
        }

        if ("Marathi" !in userLanguages) {
            val marathiMarkers = listOf(
                "marathi", "lavani", "povada", "ajay-atul", "ajay atul", "swapnil bandodkar",
                "avadhoot gupte", "bela shende", "anand shinde", "adarsh shinde", "mahesh kale",
                "zee music marathi"
            )
            if (matchesAny(marathiMarkers)) return false
        }

        if ("Gujarati" !in userLanguages) {
            val gujaratiMarkers = listOf(
                "gujarati", "garba", "dandiya", "dayro", "kirtidan gadhvi", "kinjal dave",
                "geeta rabari", "jignesh kaviraj", "osman mir", "aditya gadhvi", "jigardan gadhavi"
            )
            if (matchesAny(gujaratiMarkers)) return false
        }

        if ("Rajasthani" !in userLanguages) {
            val rajasthaniMarkers = listOf(
                "rajasthani", "marwadi", "ghoomar", "mame khan", "seema mishra", "prakash mali",
                "chotu singh rawna"
            )
            if (matchesAny(rajasthaniMarkers)) return false
        }

        // 5. English / Western filtering when "English" is NOT selected:
        if ("English" !in userLanguages) {
            val westernArtists = listOf(
                "taylor swift", "ed sheeran", "justin bieber", "drake", "the weeknd", "billie eilish",
                "ariana grande", "bruno mars", "dua lipa", "post malone", "rihanna", "eminem",
                "katy perry", "maroon 5", "coldplay", "imagine dragons", "adele", "beyonce",
                "harry styles", "selena gomez", "charlie puth", "shawn mendes", "sam smith", "sia",
                "david guetta", "calvin harris", "alan walker", "marshmello", "chainsmokers", "avicii",
                "lady gaga", "miley cyrus", "kendrick lamar", "travis scott", "kanye west",
                "olivia rodrigo", "sabrina carpenter", "sza", "lana del rey", "camila cabello",
                "bebe rexha", "jason derulo", "pitbull", "cardi b", "nicki minaj", "lil nas x",
                "one direction", "queen", "the beatles", "michael jackson", "linkin park", "green day",
                "martin garrix", "tiesto", "kygo", "zedd", "skrillex", "diplo", "clean bandit",
                "alessia cara", "halsey", "conan gray", "troye sivan", "lauv", "jeremy zucker"
            )
            if (matchesAny(westernArtists)) return false

            val hasIndianIndicator = hasDevanagari || hasGurmukhi || hasTamil || hasTelugu ||
                    hasKannada || hasMalayalam || hasBengali || hasGujarati || hasOdia ||
                    matchesAny(listOf(
                        "t-series", "tseries", "zee music", "sony music india", "yrf", "saregama",
                        "tips official", "venus", "desi music factory", "vyrl", "white hill",
                        "jjust music", "arijit singh", "atif aslam", "neha kakkar", "shreya ghoshal",
                        "jubin nautiyal", "sonu nigam", "kumar sanu", "udit narayan", "alka yagnik",
                        "lata mangeshkar", "kishore kumar", "mohammed rafi", "mukesh", "asha bhosle",
                        "sunidhi chauhan", "mohit chauhan", "kk", "rahat fateh ali khan", "vishal mishra",
                        "darshan raval", "armaan malik", "badshah", "honey singh", "yo yo honey singh",
                        "mika singh", "sachin-jigar", "sachin jigar", "pritam", "vishal-shekhar",
                        "vishal shekhar", "shankar-ehsaan-loy", "amit trivedi", "mithoon", "tanishk bagchi",
                        "salim-sulaiman", "salim sulaiman", "ankit tiwari", "stebin ben", "jasleen royal",
                        "shilpa rao", "sachet tandon", "parampara tandon", "tulsi kumar", "palak muchhal",
                        "monali thakur", "benny dayal", "lucky ali", "papon", "javed ali", "sukhwinder singh",
                        "himesh reshammiya", "tony kakkar", "dhvani bhanushali", "shaan", "adnan sami",
                        "jagjit singh", "anuradha paudwal", "kavita krishnamurthy", "pankaj udhas",
                        "zaeden", "anuv jain", "prateek kuhad", "king", "mc stan", "emiway bantai",
                        "raftaar", "divine", "ikka", "seedhe maut", "krsna", "kr\$na",
                        "pyar", "pyaar", "dil", "ishq", "mohabbat", "tum", "hum", "meri", "mera",
                        "tere", "teri", "zindagi", "sanam", "jaan", "deewana", "duniya", "ankhon",
                        "naina", "saath", "raat", "khuda", "rabba", "kabhi", "tu", "hai", "kuch",
                        "barsaat", "yaad", "geet", "gaana", "tera", "humdard", "humsafar", "chura",
                        "aashiqui", "kesariya", "channa", "mereya", "tujhe", "kitna", "chahne", "lage",
                        "pal", "hawa", "rooh", "dhadkan", "musafir", "chal", "kaise", "jeena",
                        "saaware", "kaho", "bewafa", "judaai", "sufi", "ghazal", "bhajan", "aarti",
                        "bollywood", "shayari", "gerua", "hawayein", "tum hi ho", "raabta", "soch na sake",
                        "kabira", "subhanallah", "ilahi", "agar tum saath ho", "tera yaar hoon main",
                        "o maahi", "lut gaye", "chaleya", "jawan", "pathaan", "satranga", "pehle bhi main"
                    ))
            if (!hasIndianIndicator) return false
        }

        // 6. If ONLY "English" is selected: reject tracks with Indian regional scripts/artists/labels
        if (userLanguages.size == 1 && "English" in userLanguages) {
            val hasIndianMarker = hasDevanagari || hasGurmukhi || hasTamil || hasTelugu ||
                    hasKannada || hasMalayalam || hasBengali || hasGujarati || hasOdia ||
                    matchesAny(listOf(
                        "t-series", "tseries", "zee music", "sony music india", "yrf", "saregama",
                        "tips official", "speed records", "white hill", "arijit singh", "neha kakkar",
                        "shreya ghoshal", "jubin nautiyal", "sonu nigam", "kumar sanu", "udit narayan",
                        "alka yagnik", "lata mangeshkar", "kishore kumar", "mohammed rafi", "badshah",
                        "honey singh", "pritam", "bollywood", "punjabi", "bhojpuri", "haryanvi"
                    ))
            if (hasIndianMarker) return false
        }

        return true
    }

    private suspend fun relatedChain(track: Track): List<Track> {
        val userLangs = _selectedLanguages.value.ifEmpty { setOf("Hindi") }
        val collected = mutableListOf<Track>()
        val seen = mutableSetOf(track.id)

        // 1. YouTube Music Automix / Radio queue, strictly filtered for user's selected languages
        val radio = runCatching { music.fetchRadioTracks(track.id, 40) }.getOrDefault(emptyList())
        val matchingRadio = radio.filter {
            it.id != track.id && music.isRealSong(it) && isMatchingSelectedLanguages(it, userLangs)
        }
        matchingRadio.forEach {
            if (seen.add(it.id)) collected.add(it)
        }

        // 2. Targeted search using the selected languages & artist/mood to guarantee 100% matching language
        val curWords = titleTokens(track.title)
        val curKey = curWords.take(3).toSet()
        val mood = moodOf("${track.title} ${track.artist}")

        val queries = buildList {
            for (lang in userLangs) {
                add("$lang ${track.artist} hits")
                if (mood.isNotEmpty()) {
                    add("$lang ${mood.joinToString(" ")} songs".trim())
                }
                add("best $lang ${track.artist} songs")
                add("top $lang hit songs")
                add("$lang ${track.title.take(30)} ${track.artist}")
            }
        }

        for (query in queries) {
            if (collected.size >= 25) break
            val found = runCatching { music.searchMusicTracks(query, 25) }
                .getOrDefault(emptyList())
                .ifEmpty { runCatching { music.search(query, 25) }.getOrDefault(emptyList()) }
                .asSequence()
                .filter { it.id !in seen && music.isRealSong(it) && isMatchingSelectedLanguages(it, userLangs) }
                .filter { cand -> titleTokens(cand.title).intersect(curKey).size < 2 }
                .filter { it.id != track.id }
                .distinctBy { it.id }
                .take(10)
                .toList()
            found.forEach {
                if (seen.add(it.id)) collected.add(it)
            }
        }
        return collected
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
            RepeatMode.ALL, RepeatMode.OFF -> playNext()
        }
    }

    private fun handlePlaybackStopped() {
        player.pause()
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
                syncCloudPreferences()
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

    fun syncCloudPreferences() {
        val user = currentUser.value ?: return
        if (user.isGuest) return
        viewModelScope.launch(Dispatchers.IO) {
            val remote = firestoreSync.fetchUserPreferences(user.uid)
            if (remote != null) {
                val (regionCode, remoteLangs) = remote
                val matchingRegion = AVAILABLE_REGIONS.firstOrNull { it.code == regionCode } ?: _selectedRegion.value
                val validLangs = remoteLangs.ifEmpty { setOf("Hindi") }
                _selectedRegion.value = matchingRegion
                _selectedLanguages.value = validLangs
                music.setRegion(matchingRegion.code)
                homePrefs.edit()
                    .putString(KEY_SELECTED_REGION, matchingRegion.code)
                    .putStringSet(KEY_SELECTED_LANGUAGES, validLangs)
                    .putBoolean(KEY_LANGUAGES_CONFIGURED, true)
                    .remove(KEY_HOME_SECTIONS)
                    .apply()
                _showLanguagePrompt.value = false
                loadHome(force = true)
            } else {
                val currentReg = _selectedRegion.value.code
                val currentLangs = _selectedLanguages.value.ifEmpty { setOf("Hindi") }
                firestoreSync.saveUserPreferences(user.uid, currentReg, currentLangs)
            }
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
        val entry = downloads.value.firstOrNull { it.track.id == track.id }
        if (entry != null && !entry.isCached) {
            _messages.tryEmit("Already saved for offline listening")
            return
        }
        if (entry != null && entry.isCached) {
            // Cached copy exists — promote it to a permanent save instead of downloading again.
            if (downloadsRepo.promoteToManual(track.id)) {
                _messages.tryEmit("Saved offline permanently")
                if (user != null && !user.isGuest) {
                    viewModelScope.launch {
                        firestoreSync.saveOfflineTrack(user.uid, track)
                    }
                }
            }
            return
        }
        viewModelScope.launch {
            try {
                _messages.tryEmit("Downloading \"${track.title}\"...")
                val resolution = music.resolveAudio(track)
                downloadsRepo.download(track, resolution)
                if (user != null && !user.isGuest) {
                    firestoreSync.saveOfflineTrack(user.uid, track, resolution.url)
                }
                _messages.tryEmit("Downloaded \"${track.title}\"")
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
        viewModelScope.launch {
            if (downloadsRepo.promoteToManual(track.id)) {
                _messages.tryEmit("Saved offline permanently")
                if (user != null && !user.isGuest) {
                    firestoreSync.saveOfflineTrack(user.uid, track)
                }
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
        // Prefetch lyrics immediately so they display with zero delay on tap
        requestLyrics(track)
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
