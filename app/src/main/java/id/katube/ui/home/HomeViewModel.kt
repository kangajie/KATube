package id.katube.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.katube.data.auth.AuthRepository
import id.katube.data.extractor.YouTubeRepository
import id.katube.data.model.VideoItem
import id.katube.data.sync.YouTubeSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(val videos: List<VideoItem>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val authRepository: AuthRepository,
    private val syncRepository: YouTubeSyncRepository,
) : ViewModel() {

    val categories = listOf(
        "Semua",
        "Live/Siaran Langsung",
        "Musik",
        "Gaming",
        "Berita",
        "Podcasts",
        "Edukasi",
        "Teknologi",
        "Hiburan",
    )

    private val _selectedCategory = MutableStateFlow("Semua")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val allVideos = mutableListOf<VideoItem>()
    private val categoryCache = mutableMapOf<String, List<VideoItem>>()
    private var currentPageIndex = 0
    private var isCurrentlyLoadingMore = false
    private var homeContinuationToken: String? = null
    private var refreshRotationIndex = 0

    init {
        viewModelScope.launch {
            authRepository.account.collect {
                // Saat login status berubah, otomatis muat ulang beranda
                loadCategoryInternal(_selectedCategory.value)
            }
        }

        // Pantau pembaruan real-time feed beranda personal dari repositori sinkronisasi
        viewModelScope.launch {
            syncRepository.homeFeed.collect { feed ->
                val account = authRepository.account.first()
                if (account.isLoggedIn && _selectedCategory.value == "Semua" && feed.isNotEmpty()) {
                    if (allVideos.isEmpty()) {
                        allVideos.clear()
                        allVideos.addAll(feed)
                        _uiState.value = HomeUiState.Success(allVideos.toList())
                    }
                }
            }
        }
    }

    fun removeVideo(videoId: String) {
        val current = _uiState.value
        if (current is HomeUiState.Success) {
            _uiState.value = HomeUiState.Success(current.videos.filter { it.id != videoId })
        }
    }

    fun onSelectCategory(category: String) {
        if (_selectedCategory.value != category) {
            _selectedCategory.value = category
            viewModelScope.launch {
                loadCategoryInternal(category)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshSuspend()
        }
    }

    suspend fun refreshSuspend() {
        _isRefreshing.value = true
        try {
            loadCategoryInternal(_selectedCategory.value, isRefresh = true)
        } finally {
            _isRefreshing.value = false
        }
    }

    private suspend fun loadCategoryInternal(category: String, isRefresh: Boolean = false) {
        // Cek in-memory cache kategori: jika ada data dan bukan pull-to-refresh, tampilkan langsung instan 0 ms
        val cachedCategory = categoryCache[category]
        if (!isRefresh && !cachedCategory.isNullOrEmpty()) {
            allVideos.clear()
            allVideos.addAll(cachedCategory)
            _uiState.value = HomeUiState.Success(allVideos.toList())
            return
        }

        if (!isRefresh && allVideos.isEmpty()) {
            _uiState.value = HomeUiState.Loading
        }
        currentPageIndex = 0

        val account = authRepository.account.first()
        val isUserLoggedIn = account.isLoggedIn && account.cookies.isNotBlank()

        if (isUserLoggedIn && category == "Semua") {
            if (!isRefresh && allVideos.isNotEmpty()) {
                _uiState.value = HomeUiState.Success(allVideos.toList())
                return
            }

            // Saat refresh, gunakan continuation token yang ada untuk memuat video rekomendasi halaman berikutnya
            // sehingga tampilan beranda berganti dengan video-video baru (persis seperti YouTube resmi saat ditarik/pull to refresh)
            val tokenToUse = if (isRefresh && homeContinuationToken != null) homeContinuationToken else null
            var syncResult = syncRepository.fetchHomeFeed(account.cookies, continuationToken = tokenToUse)
            var pair = syncResult.getOrNull()
            var feedVideos = pair?.first.orEmpty().filter { it.id.isNotEmpty() }

            // Jika token kelanjutan gagal/kosong, minta feed baru dari YouTube
            if (feedVideos.isEmpty()) {
                syncResult = syncRepository.fetchHomeFeed(account.cookies, continuationToken = null)
                pair = syncResult.getOrNull()
                feedVideos = pair?.first.orEmpty().filter { it.id.isNotEmpty() }
            }

            homeContinuationToken = pair?.second

            if (feedVideos.isNotEmpty()) {
                // Jika saat refresh YouTube mengembalikan sebagian video yang sama, acak urutan atau prioritaskan video segar
                val finalFeed = if (isRefresh && allVideos.isNotEmpty()) {
                    val freshVideos = feedVideos.filter { fv -> allVideos.none { it.id == fv.id } }
                    if (freshVideos.isNotEmpty()) {
                        freshVideos + feedVideos.filter { fv -> freshVideos.none { it.id == fv.id } }
                    } else {
                        feedVideos.shuffled()
                    }
                } else {
                    feedVideos
                }

                allVideos.clear()
                allVideos.addAll(finalFeed)
                categoryCache[category] = allVideos.toList()
                _uiState.value = HomeUiState.Success(allVideos.toList())
                return
            } else {
                // Fallback jika koneksi gagal: tampilkan cache beranda lokal akun jika ada
                val cached = syncRepository.homeFeed.value
                if (cached.isNotEmpty()) {
                    allVideos.clear()
                    allVideos.addAll(if (isRefresh) cached.shuffled() else cached)
                    _uiState.value = HomeUiState.Success(allVideos.toList())
                    return
                }
            }
        }

        // Default Stateless NewPipe / Kategori / Mode Refresh
        val result = if (category == "Semua") {
            if (isRefresh) {
                refreshRotationIndex++
                val refreshTopics = listOf(
                    "Trending Musik & Video Terkini Indonesia",
                    "Viral FYP Indonesia Hari Ini",
                    "Kabar & Hiburan Populer Indonesia",
                    "Podcasts Populer & Obrolan Seru",
                    "Gaming Seru Indonesia Terpopuler",
                    "Video Lucu & Komedi Viral",
                    "Berita Hangat Terkini Indonesia",
                    "Fakta Menarik & Sains Populer",
                    "Review Gadget & Teknologi Terbaru",
                    "Kuliner Viral & Wisata Indonesia",
                    "Lagu Hits Pop & Akustik Terbaru",
                    "Film Pendek & Web Series Indonesia",
                )
                val topic = refreshTopics[refreshRotationIndex % refreshTopics.size]
                repository.search(topic)
            } else {
                val trendingRes = repository.getTrending()
                if (trendingRes.isFailure || trendingRes.getOrNull().isNullOrEmpty()) {
                    repository.search("lagu pop indonesia terbaru viral")
                } else {
                    trendingRes
                }
            }
        } else {
            if (isRefresh) {
                refreshRotationIndex++
                val topic = getNextFeedTopic(category, refreshRotationIndex)
                repository.search(topic)
            } else {
                repository.search(category)
            }
        }

        result.fold(
            onSuccess = { videos ->
                val distinct = videos.distinctBy { it.id }.filter { it.id.isNotEmpty() }
                if (distinct.isNotEmpty()) {
                    allVideos.clear()
                    allVideos.addAll(distinct)
                    categoryCache[category] = allVideos.toList()
                    _uiState.value = HomeUiState.Success(allVideos.toList())
                }
            },
            onFailure = { error ->
                if (!isRefresh || allVideos.isEmpty()) {
                    _uiState.value = HomeUiState.Error(
                        error.message ?: "Gagal memuat video dari YouTube"
                    )
                }
            }
        )
    }

    /**
     * Memuat video secara kontinu tanpa batas (Infinite Scroll) saat pengguna menggulir ke bawah.
     */
    fun loadMore() {
        if (isCurrentlyLoadingMore || _uiState.value !is HomeUiState.Success) return

        isCurrentlyLoadingMore = true
        _isLoadingMore.value = true

        viewModelScope.launch {
            val account = authRepository.account.first()
            var loadedNew = false
            if (account.isLoggedIn && account.cookies.isNotBlank() && _selectedCategory.value == "Semua" && homeContinuationToken != null) {
                val continuationResult = syncRepository.fetchHomeFeed(account.cookies, homeContinuationToken)
                continuationResult.onSuccess { pair ->
                    homeContinuationToken = pair.second
                    val newVideos = pair.first.filter { newVid ->
                        newVid.id.isNotEmpty() && allVideos.none { it.id == newVid.id }
                    }
                    if (newVideos.isNotEmpty()) {
                        allVideos.addAll(newVideos)
                        _uiState.value = HomeUiState.Success(allVideos.toList())
                        loadedNew = true
                    }
                }
                continuationResult.onFailure {
                    homeContinuationToken = null
                }
            }

            if (!loadedNew) {
                currentPageIndex++
                val topic = getNextFeedTopic(_selectedCategory.value, currentPageIndex)
                val result = repository.search(topic)

                result.onSuccess { newVideos ->
                    val filtered = newVideos.filter { newVid ->
                        newVid.id.isNotEmpty() && allVideos.none { it.id == newVid.id }
                    }
                    if (filtered.isNotEmpty()) {
                        allVideos.addAll(filtered)
                        _uiState.value = HomeUiState.Success(allVideos.toList())
                    }
                }
            }

            _isLoadingMore.value = false
            isCurrentlyLoadingMore = false
        }
    }

    private fun getNextFeedTopic(category: String, page: Int): String {
        val topics = when (category) {
            "Semua" -> listOf(
                "Viral Indonesia",
                "Musik Populer",
                "Podcasts Terpopuler",
                "Gaming Indonesia",
                "Video Populer Hari Ini",
                "Berita Hangat Terkini",
                "Shorts Terpopuler",
                "Hiburan & Komedi Indonesia",
                "Fakta Unik Dunia",
                "Teknologi Indonesia Terkini",
                "Film Pendek Indonesia",
                "Vlog Wisata & Kuliner Indonesia",
            )
            "Musik" -> listOf(
                "Musik Indonesia Terpopuler",
                "Lagu Pop Viral",
                "Lagu Akustik Santai",
                "Musik Remix Viral",
                "Top Hits Indonesia",
                "Lagu Galau Indonesia",
                "Dangdut Koplo Terbaru",
            )
            "Gaming" -> listOf(
                "Gaming Indonesia",
                "Mobile Legends Indonesia",
                "Minecraft Indonesia",
                "Free Fire Indonesia",
                "Roblox Indonesia",
                "Game Horor Indonesia",
                "Mabar Game Populer",
            )
            "Berita" -> listOf(
                "Berita Hari Ini",
                "Breaking News Indonesia",
                "Kabar Dunia Terkini",
                "Liputan Khusus Berita",
                "Kabar Politik & Hukum",
                "Berita Ekonomi Terkini",
            )
            "Podcasts" -> listOf(
                "Podcast Indonesia",
                "Podcast Obrolan Santai",
                "Podcast Horor Indonesia",
                "Podcast Bisnis & Edukasi",
                "Podcast Wawancara Tokoh",
            )
            "Edukasi" -> listOf(
                "Edukasi Sains Indonesia",
                "Fakta Unik Sejarah",
                "Belajar Bahasa Inggris",
                "Tutorial Komputer & Coding",
                "Dokumenter Pengetahuan",
            )
            "Teknologi" -> listOf(
                "Review Gadget Indonesia",
                "Smartphone Terbaru",
                "Teknologi AI Terbaru",
                "Tips & Trik Android",
                "Laptop & Komputer Gaming",
            )
            else -> listOf(
                "$category Populer",
                "$category Terbaru",
                "$category Viral Indonesia",
                "$category Rekomendasi",
            )
        }
        return topics[(page - 1) % topics.size]
    }
}
