package id.katube.ui.shorts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.katube.data.extractor.YouTubeRepository
import id.katube.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ShortsUiState {
    object Loading : ShortsUiState
    data class Success(val shorts: List<VideoItem>) : ShortsUiState
    data class Error(val message: String) : ShortsUiState
}

@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val syncRepository: id.katube.data.sync.YouTubeSyncRepository,
    private val authRepository: id.katube.data.auth.AuthRepository,
    private val preferencesRepository: id.katube.data.preferences.UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShortsUiState>(ShortsUiState.Loading)
    val uiState: StateFlow<ShortsUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _preferredQuality = MutableStateFlow(0) // 0 = Otomatis (Auto)
    val preferredQuality: StateFlow<Int> = _preferredQuality.asStateFlow()

    fun setPreferredQuality(height: Int) {
        _preferredQuality.value = height
        viewModelScope.launch {
            try {
                preferencesRepository.setDefaultQuality(height)
            } catch (_: Exception) {}
        }
    }

    private val _likedShorts = MutableStateFlow<Set<String>>(emptySet())
    val likedShorts: StateFlow<Set<String>> = _likedShorts.asStateFlow()

    private val _unlikedShorts = MutableStateFlow<Set<String>>(emptySet())
    val unlikedShorts: StateFlow<Set<String>> = _unlikedShorts.asStateFlow()

    private val _dislikedShorts = MutableStateFlow<Set<String>>(emptySet())
    val dislikedShorts: StateFlow<Set<String>> = _dislikedShorts.asStateFlow()

    private val _unDislikedShorts = MutableStateFlow<Set<String>>(emptySet())
    val unDislikedShorts: StateFlow<Set<String>> = _unDislikedShorts.asStateFlow()

    private val _subscribedSet = MutableStateFlow<Set<String>>(emptySet())
    val subscribedSet: StateFlow<Set<String>> = _subscribedSet.asStateFlow()

    private val _unsubscribedSet = MutableStateFlow<Set<String>>(emptySet())
    val unsubscribedSet: StateFlow<Set<String>> = _unsubscribedSet.asStateFlow()

    fun isLiked(videoId: String): Boolean {
        if (_unlikedShorts.value.contains(videoId)) return false
        return _likedShorts.value.contains(videoId) || syncRepository.isLikedLocally(videoId)
    }

    fun isDisliked(videoId: String): Boolean {
        if (_unDislikedShorts.value.contains(videoId)) return false
        return _dislikedShorts.value.contains(videoId) || syncRepository.isDislikedLocally(videoId)
    }

    fun isSubscribed(channelIdOrName: String): Boolean {
        if (_unsubscribedSet.value.contains(channelIdOrName)) return false
        return _subscribedSet.value.contains(channelIdOrName) || syncRepository.isSubscribedLocally(channelIdOrName)
    }

    fun toggleLike(videoId: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val current = isLiked(videoId)
        val target = !current
        if (target) {
            _likedShorts.value = _likedShorts.value + videoId
            _unlikedShorts.value = _unlikedShorts.value - videoId
            _dislikedShorts.value = _dislikedShorts.value - videoId
            _unDislikedShorts.value = _unDislikedShorts.value + videoId
        } else {
            _likedShorts.value = _likedShorts.value - videoId
            _unlikedShorts.value = _unlikedShorts.value + videoId
        }
        viewModelScope.launch {
            if (target) {
                syncRepository.likeVideo(videoId)
                onResult?.invoke(true, "Ditambahkan ke Video yang disukai")
            } else {
                syncRepository.removeLikeOrDislike(videoId)
                onResult?.invoke(true, "Dihapus dari Video yang disukai")
            }
        }
    }

    fun toggleDislike(videoId: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val current = isDisliked(videoId)
        val target = !current
        if (target) {
            _dislikedShorts.value = _dislikedShorts.value + videoId
            _unDislikedShorts.value = _unDislikedShorts.value - videoId
            _likedShorts.value = _likedShorts.value - videoId
            _unlikedShorts.value = _unlikedShorts.value + videoId
        } else {
            _dislikedShorts.value = _dislikedShorts.value - videoId
            _unDislikedShorts.value = _unDislikedShorts.value + videoId
        }
        viewModelScope.launch {
            if (target) {
                syncRepository.dislikeVideo(videoId)
                onResult?.invoke(true, "Shorts tidak disukai")
            } else {
                syncRepository.removeLikeOrDislike(videoId)
                onResult?.invoke(true, "Batal tidak suka")
            }
        }
    }

    fun toggleSubscribe(channelIdOrUrl: String, channelName: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val current = isSubscribed(channelIdOrUrl)
        val target = !current
        if (target) {
            _subscribedSet.value = _subscribedSet.value + channelIdOrUrl + channelName
            _unsubscribedSet.value = _unsubscribedSet.value - channelIdOrUrl - channelName
        } else {
            _subscribedSet.value = _subscribedSet.value - channelIdOrUrl - channelName
            _unsubscribedSet.value = _unsubscribedSet.value + channelIdOrUrl + channelName
        }
        viewModelScope.launch {
            if (target) {
                syncRepository.subscribeChannel(channelIdOrUrl, channelName = channelName)
                onResult?.invoke(true, "Berlangganan $channelName")
            } else {
                syncRepository.unsubscribeChannel(channelIdOrUrl)
                onResult?.invoke(true, "Batal berlangganan $channelName")
            }
        }
    }

    private val _shortsComments = MutableStateFlow<Map<String, List<id.katube.data.model.CommentItem>>>(emptyMap())
    val shortsComments: StateFlow<Map<String, List<id.katube.data.model.CommentItem>>> = _shortsComments.asStateFlow()

    private val _isLoadingComments = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isLoadingComments: StateFlow<Map<String, Boolean>> = _isLoadingComments.asStateFlow()

    fun loadComments(videoId: String) {
        if (_shortsComments.value.containsKey(videoId)) return
        _isLoadingComments.value = _isLoadingComments.value + (videoId to true)
        viewModelScope.launch {
            repository.getComments(videoId).fold(
                onSuccess = { list ->
                    _shortsComments.value = _shortsComments.value + (videoId to list)
                    _isLoadingComments.value = _isLoadingComments.value + (videoId to false)
                },
                onFailure = {
                    _isLoadingComments.value = _isLoadingComments.value + (videoId to false)
                }
            )
        }
    }

    fun addComment(videoId: String, text: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val account = authRepository.account.first()
            val authorName = if (account.isLoggedIn && account.displayName.isNotBlank()) {
                account.displayName
            } else {
                "Anda"
            }
            val authorAvatar = if (account.isLoggedIn) account.avatarUrl else null
            val newComment = id.katube.data.model.CommentItem(
                id = "user_shorts_${System.currentTimeMillis()}",
                authorName = authorName,
                authorAvatarUrl = authorAvatar,
                text = trimmed,
                uploadDateText = "Baru saja",
                likeCount = 0,
                isUserComment = true
            )
            val currentList = _shortsComments.value[videoId].orEmpty()
            _shortsComments.value = _shortsComments.value + (videoId to (listOf(newComment) + currentList))
            syncRepository.postComment(videoId, trimmed)
            onResult?.invoke(true, "Komentar berhasil dikirim")
        }
    }

    fun deleteComment(videoId: String, commentId: String, onResult: ((Boolean, String) -> Unit)? = null) {
        if (commentId.isEmpty()) return
        val currentList = _shortsComments.value[videoId].orEmpty()
        _shortsComments.value = _shortsComments.value + (videoId to currentList.filter { it.id != commentId })
        viewModelScope.launch {
            syncRepository.deleteComment(commentId)
            onResult?.invoke(true, "Komentar dihapus")
        }
    }

    private val allShorts = mutableListOf<VideoItem>()
    private var currentTopicIndex = 0
    private var isCurrentlyLoadingMore = false

    private val shortsTopics = listOf(
        "#shorts viral indonesia",
        "Shorts trending indonesia",
        "Shorts populer fyp indonesia",
        "Shorts lucu ngakak viral",
        "Shorts musik hits indonesia",
        "Shorts fakta unik dunia",
        "Shorts gaming seru indonesia",
        "Shorts komedi standup indonesia",
        "Shorts edukasi sains menarik",
        "Shorts animasi 3d viral",
        "Shorts kuliner street food indonesia",
        "Shorts motivasi hidup inspiratif",
        "YouTube Shorts FYP viral",
        "#shorts terbaru hari ini",
        "Shorts trik kreatif life hacks",
        "Shorts teknologi gadget terbaru",
        "Shorts sepak bola momen terbaik",
        "Shorts hewan lucu kucing anjing",
        "Shorts bakat menyanyi indonesia",
        "Shorts travel wisata alam indonesia",
        "Shorts sulap keren ilusi",
        "Shorts film bioskop cuplikan",
        "Shorts otomotif modifikasi motor mobil",
        "Shorts eksperimen sains seru",
        "Shorts sejarah misteri dunia",
        "Shorts tutorial masak mudah",
        "Shorts podcast seru potongan",
        "Shorts horor misteri indonesia",
        "Shorts kerajinan tangan diy unik",
        "Shorts aksi luar biasa manusia",
    )

    init {
        viewModelScope.launch {
            loadInitialShortsInternal()
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
            loadInitialShortsInternal(isRefresh = true)
        } finally {
            _isRefreshing.value = false
        }
    }

    private suspend fun loadInitialShortsInternal(isRefresh: Boolean = false) {
        if (!isRefresh) {
            _uiState.value = ShortsUiState.Loading
        }
        currentTopicIndex = (0 until shortsTopics.size).random()
        val initialTopic = shortsTopics[currentTopicIndex]

        val result = repository.search(initialTopic)
        result.fold(
            onSuccess = { items ->
                val filtered = items.distinctBy { it.id }.filter { it.id.isNotEmpty() }
                if (filtered.isNotEmpty()) {
                    allShorts.clear()
                    allShorts.addAll(filtered)
                    _uiState.value = ShortsUiState.Success(allShorts.toList())

                    // Segera muat batch kedua di latar belakang agar daftar awal langsung melimpah
                    viewModelScope.launch {
                        loadMoreBatch()
                    }
                } else if (!isRefresh) {
                    _uiState.value = ShortsUiState.Error("Tidak ada video Shorts ditemukan")
                }
                Unit
            },
            onFailure = { error ->
                if (!isRefresh || allShorts.isEmpty()) {
                    _uiState.value = ShortsUiState.Error(
                        error.message ?: "Gagal memuat video Shorts"
                    )
                }
                Unit
            }
        )
    }

    /**
     * Memuat video Shorts selanjutnya secara kontinu tanpa batas (Infinite Scroll) saat pengguna menggulir ke bawah.
     */
    fun loadMore() {
        if (isCurrentlyLoadingMore || _uiState.value !is ShortsUiState.Success) return
        viewModelScope.launch {
            loadMoreBatch()
        }
    }

    private suspend fun loadMoreBatch() {
        if (isCurrentlyLoadingMore) return
        isCurrentlyLoadingMore = true
        _isLoadingMore.value = true

        try {
            val newlyAdded = mutableListOf<VideoItem>()
            var attempts = 0
            val maxAttempts = 6 // Coba hingga 6 topik berbeda untuk memastikan batch berikutnya selalu terisi

            while (newlyAdded.size < 12 && attempts < maxAttempts) {
                attempts++
                currentTopicIndex = (currentTopicIndex + 1) % shortsTopics.size
                val nextTopic = shortsTopics[currentTopicIndex]

                val result = repository.search(nextTopic)
                result.getOrNull()?.let { items ->
                    val filtered = items.filter { item ->
                        item.id.isNotEmpty() &&
                        allShorts.none { it.id == item.id } &&
                        newlyAdded.none { it.id == item.id }
                    }
                    newlyAdded.addAll(filtered)
                }
            }

            if (newlyAdded.isNotEmpty()) {
                allShorts.addAll(newlyAdded)
                _uiState.value = ShortsUiState.Success(allShorts.toList())
            }
        } catch (_: Exception) {
            // Abaikan kesalahan jaringan sementara agar pengguna tetap bisa melihat shorts yang ada
        } finally {
            _isLoadingMore.value = false
            isCurrentlyLoadingMore = false
        }
    }

    suspend fun getShortStreamInfo(videoId: String): Result<id.katube.data.model.StreamBundle> {
        val targetQuality = if (_preferredQuality.value > 0) {
            _preferredQuality.value
        } else {
            try {
                preferencesRepository.defaultQuality.first()
            } catch (_: Exception) {
                0
            }
        }
        return repository.getStreamInfo(videoId, maxHeight = targetQuality)
    }
}
