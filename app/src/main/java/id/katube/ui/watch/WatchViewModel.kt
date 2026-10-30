package id.katube.ui.watch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import id.katube.core.util.NetworkUtils
import id.katube.data.extractor.YouTubeRepository
import id.katube.data.model.CommentItem
import id.katube.data.model.PlaylistItem
import id.katube.data.model.StreamBundle
import id.katube.data.model.VideoItem
import id.katube.data.model.VideoQuality
import id.katube.data.preferences.UserPreferencesRepository
import id.katube.player.PlayerController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WatchUiState {
    data class Loading(val previewVideo: VideoItem? = null) : WatchUiState
    data class Success(val bundle: StreamBundle) : WatchUiState
    data class Error(val message: String) : WatchUiState
}

@UnstableApi
@HiltViewModel
class WatchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: YouTubeRepository,
    val playerController: PlayerController,
    private val preferencesRepository: UserPreferencesRepository,
    private val syncRepository: id.katube.data.sync.YouTubeSyncRepository,
    private val authRepository: id.katube.data.auth.AuthRepository,
) : ViewModel() {

    private var currentVideoId: String = checkNotNull(savedStateHandle["videoId"])
    fun getCurrentVideoId(): String = currentVideoId

    private val _uiState = MutableStateFlow<WatchUiState>(WatchUiState.Loading())
    val uiState: StateFlow<WatchUiState> = _uiState.asStateFlow()

    private val _comments = MutableStateFlow<List<CommentItem>>(emptyList())
    val comments: StateFlow<List<CommentItem>> = _comments.asStateFlow()

    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments: StateFlow<Boolean> = _isLoadingComments.asStateFlow()

    private val _isLiked = MutableStateFlow(false)
    val isLiked: StateFlow<Boolean> = _isLiked.asStateFlow()

    private val _isDisliked = MutableStateFlow(false)
    val isDisliked: StateFlow<Boolean> = _isDisliked.asStateFlow()

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    private val _uploaderSubscriberText = MutableStateFlow<String?>(null)
    val uploaderSubscriberText: StateFlow<String?> = _uploaderSubscriberText.asStateFlow()

    private val _channelAvatarUrl = MutableStateFlow<String?>(null)
    val channelAvatarUrl: StateFlow<String?> = _channelAvatarUrl.asStateFlow()

    private val _commentTeaser = MutableStateFlow<Pair<String, String?>?>(null)
    val commentTeaser: StateFlow<Pair<String, String?>?> = _commentTeaser.asStateFlow()

    private val _commentCountText = MutableStateFlow<String?>(null)
    val commentCountText: StateFlow<String?> = _commentCountText.asStateFlow()

    private val _videoViewsText = MutableStateFlow<String?>(null)
    val videoViewsText: StateFlow<String?> = _videoViewsText.asStateFlow()

    private val _videoUploadDateText = MutableStateFlow<String?>(null)
    val videoUploadDateText: StateFlow<String?> = _videoUploadDateText.asStateFlow()

    private val _relatedVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val relatedVideos: StateFlow<List<VideoItem>> = _relatedVideos.asStateFlow()

    private val _isLoadingMoreRelated = MutableStateFlow(false)
    val isLoadingMoreRelated: StateFlow<Boolean> = _isLoadingMoreRelated.asStateFlow()

    val userPlaylists: StateFlow<List<PlaylistItem>> = syncRepository.userPlaylists

    val account: StateFlow<id.katube.data.model.GoogleAccount> = authRepository.account
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), id.katube.data.model.GoogleAccount())

    private var nextRelatedContinuation: String? = null
    private fun checkSubscribedLocally(uploaderUrl: String?, uploaderName: String?): Boolean {
        val url = uploaderUrl.orEmpty()
        val name = uploaderName.orEmpty()
        val channelId = if (url.isNotBlank()) url.substringAfterLast("/") else ""
        return (channelId.isNotEmpty() && syncRepository.isSubscribedLocally(channelId)) ||
               (name.isNotEmpty() && syncRepository.isSubscribedLocally(name)) ||
               (url.isNotEmpty() && syncRepository.isSubscribedLocally(url))
    }
    private var currentChannelId: String = ""
    private var currentUploaderUrl: String = ""
    private var currentUploaderName: String = ""
    private var watchNextJob: Job? = null
    private var activeArtistKeyword: String? = null
    private var autoDiscoveryJob: Job? = null
    private val sessionPlayedVideoIds = mutableSetOf<String>()

    val seekDuration: StateFlow<Int> = preferencesRepository.seekDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)

    val volumeGesture: StateFlow<Boolean> = preferencesRepository.volumeGesture
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val brightnessGesture: StateFlow<Boolean> = preferencesRepository.brightnessGesture
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val currentPlaylist = playerController.currentPlaylist
    val queue = playerController.queue
    val currentIndex = playerController.currentIndex
    val isShuffleEnabled = playerController.isShuffleEnabled
    val repeatMode = playerController.repeatMode

    fun playQueueIndex(index: Int) {
        val q = playerController.queue.value
        if (index in q.indices) {
            playerController.setCurrentIndex(index)
            loadVideo(q[index].id, addToHistory = true)
        }
    }

    fun toggleShuffle() {
        playerController.toggleShuffle()
    }

    fun toggleRepeatMode() {
        playerController.toggleRepeatMode()
    }

    fun setSeekDuration(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setSeekDuration(seconds)
        }
    }

    // Cache prefetch video berikutnya agar saat autoplay atau klik Next langsung instan tanpa buffering
    private var prefetchedNextBundle: StreamBundle? = null
    private var prefetchedVideoId: String? = null
    private var loadVideoJob: Job? = null
    private var prefetchJob: Job? = null
    private var deferredSecondaryTasksJob: Job? = null
    private var commentsJob: Job? = null

    init {
        viewModelScope.launch {
            val autoplay = preferencesRepository.autoplayNext.first()
            playerController.isAutoplayEnabled.value = autoplay
        }

        // Amati perubahan lagu yang diputar oleh PlayerController (termasuk dari autoplay background/lock screen)
        viewModelScope.launch {
            playerController.currentStreamBundle.collect { activeBundle ->
                if (activeBundle != null && activeBundle.videoId != currentVideoId && activeBundle.videoId.isNotEmpty()) {
                    currentVideoId = activeBundle.videoId
                    val hasPlayableMedia = !activeBundle.selectedVideoUrl.isNullOrEmpty() ||
                        activeBundle.selectedAudioUrl.isNotEmpty() ||
                        !activeBundle.hlsUrl.isNullOrEmpty()
                    if (hasPlayableMedia) {
                        _uiState.value = WatchUiState.Success(activeBundle)
                    } else {
                        _uiState.value = WatchUiState.Loading(
                            id.katube.data.model.VideoItem(
                                id = activeBundle.videoId,
                                url = "https://www.youtube.com/watch?v=${activeBundle.videoId}",
                                title = activeBundle.title,
                                uploaderName = activeBundle.uploaderName,
                                uploaderAvatarUrl = activeBundle.uploaderAvatarUrl,
                                thumbnailUrl = activeBundle.thumbnailUrl,
                                durationSeconds = activeBundle.durationSeconds,
                            )
                        )
                    }
                    _relatedVideos.value = activeBundle.relatedVideos
                    _uploaderSubscriberText.value = activeBundle.uploaderSubscriberText
                    currentUploaderUrl = activeBundle.uploaderUrl.orEmpty()
                    currentUploaderName = activeBundle.uploaderName
                    sessionPlayedVideoIds.add(activeBundle.videoId)

                    // Pulihkan status suka, tidak suka, dan subscribe untuk video baru ini
                    _isLiked.value = syncRepository.isLikedLocally(activeBundle.videoId)
                    _isDisliked.value = syncRepository.isDislikedLocally(activeBundle.videoId)
                    _isSubscribed.value = checkSubscribedLocally(activeBundle.uploaderUrl, activeBundle.uploaderName)

                    // Reset & muat komentar serta metadata video baru ini seketika
                    _comments.value = emptyList()
                    loadComments()

                    watchNextJob?.cancel()
                    watchNextJob = launch {
                        syncRepository.fetchWatchNextRelated(activeBundle.videoId).onSuccess { data ->
                            if (!data.viewCountText.isNullOrBlank()) {
                                _videoViewsText.value = data.viewCountText
                            }
                            if (!data.uploadDateText.isNullOrBlank()) {
                                _videoUploadDateText.value = data.uploadDateText
                            }
                            if (!data.commentCountText.isNullOrBlank()) {
                                _commentCountText.value = data.commentCountText
                            }
                            if (!data.commentTeaserText.isNullOrBlank()) {
                                _commentTeaser.value = data.commentTeaserText to data.commentTeaserAvatar
                            }
                            if (!data.viewCountText.isNullOrBlank()) {
                        _videoViewsText.value = data.viewCountText
                    }
                    if (!data.uploadDateText.isNullOrBlank()) {
                        _videoUploadDateText.value = data.uploadDateText
                    }
                    if (!data.commentCountText.isNullOrBlank()) {
                        _commentCountText.value = data.commentCountText
                    }
                    if (!data.commentTeaserText.isNullOrBlank()) {
                        _commentTeaser.value = data.commentTeaserText to data.commentTeaserAvatar
                    }
                    if (!data.subscriberText.isNullOrBlank()) {
                                _uploaderSubscriberText.value = data.subscriberText
                            }
                            if (!data.channelId.isNullOrEmpty()) {
                                currentChannelId = data.channelId
                            }
                            val localSub = checkSubscribedLocally(activeBundle.uploaderUrl, activeBundle.uploaderName) ||
                                (!data.channelId.isNullOrEmpty() && syncRepository.isSubscribedLocally(data.channelId))
                            _isSubscribed.value = (data.isSubscribed == true) || localSub

                            val isLocalLiked = syncRepository.isLikedLocally(activeBundle.videoId)
                            val isLocalDisliked = syncRepository.isDislikedLocally(activeBundle.videoId)
                            if (isLocalLiked || data.likeStatus == "LIKE") {
                                _isLiked.value = true
                                _isDisliked.value = false
                            } else if (isLocalDisliked || data.likeStatus == "DISLIKE") {
                                _isLiked.value = false
                                _isDisliked.value = true
                            }
                            if (data.relatedVideos.isNotEmpty()) {
                                _relatedVideos.value = (activeBundle.relatedVideos + data.relatedVideos).distinctBy { it.id }
                                playerController.updateRelatedVideos(data.relatedVideos)
                            }
                            nextRelatedContinuation = data.nextContinuation
                        }
                    }

                    syncRepository.recordLocalWatchHistory(id.katube.data.model.VideoItem(
                        id = activeBundle.videoId,
                        url = "https://www.youtube.com/watch?v=${activeBundle.videoId}",
                        title = activeBundle.title,
                        uploaderName = activeBundle.uploaderName,
                        uploaderAvatarUrl = activeBundle.uploaderAvatarUrl,
                        uploaderUrl = activeBundle.uploaderUrl,
                        durationSeconds = activeBundle.durationSeconds,
                        viewCount = activeBundle.viewCount,
                        uploadDateText = activeBundle.uploadDateText,
                        thumbnailUrl = activeBundle.thumbnailUrl,
                    ))
                }
            }
        }

        val activeBundle = playerController.currentStreamBundle.value
        if (activeBundle != null && activeBundle.videoId == currentVideoId && (playerController.player != null || !activeBundle.selectedVideoUrl.isNullOrEmpty())) {
            // Video ini sedang aktif diputar! Tampilkan UI langsung dan pulihkan status Like, Dislike, Subscribe & Avatar
            _uiState.value = WatchUiState.Success(activeBundle)
            currentUploaderName = activeBundle.uploaderName
            currentUploaderUrl = activeBundle.uploaderUrl.orEmpty()
            currentChannelId = activeBundle.uploaderUrl?.substringAfterLast("/").orEmpty()
            _isLiked.value = syncRepository.isLikedLocally(currentVideoId)
            _isDisliked.value = syncRepository.isDislikedLocally(currentVideoId)
            _isSubscribed.value = checkSubscribedLocally(activeBundle.uploaderUrl, activeBundle.uploaderName)
            _relatedVideos.value = activeBundle.relatedVideos
            _uploaderSubscriberText.value = activeBundle.uploaderSubscriberText
            if (!activeBundle.uploaderAvatarUrl.isNullOrEmpty()) {
                _channelAvatarUrl.value = activeBundle.uploaderAvatarUrl
            }
            if (_comments.value.isEmpty()) {
                loadComments()
            }
        } else {
            // Rekam video ke riwayat pemutaran lokal dan YouTube
            loadVideo(currentVideoId, addToHistory = true)
        }
    }

    private suspend fun getEffectiveMaxHeight(): Int {
        val defaultMaxHeight = preferencesRepository.defaultQuality.first()
        val cellularMaxHeight = preferencesRepository.cellularMaxQuality.first()
        val isCellular = NetworkUtils.isCellular(playerController.context)
        return if (isCellular && cellularMaxHeight > 0) {
            if (defaultMaxHeight <= 0 || defaultMaxHeight > cellularMaxHeight) cellularMaxHeight else defaultMaxHeight
        } else {
            defaultMaxHeight
        }
    }

    fun loadVideo(id: String = currentVideoId, addToHistory: Boolean = true, previewItem: VideoItem? = null) {
        val activeBundle = playerController.currentStreamBundle.value
        val hasPlayableMedia = activeBundle != null && (
            !activeBundle.selectedVideoUrl.isNullOrEmpty() ||
            activeBundle.selectedAudioUrl.isNotEmpty() ||
            !activeBundle.hlsUrl.isNullOrEmpty()
        )
        if (activeBundle != null && activeBundle.videoId == id && playerController.player != null && hasPlayableMedia) {
            currentVideoId = id
            _uiState.value = WatchUiState.Success(activeBundle)
            if (_relatedVideos.value.isEmpty()) {
                _relatedVideos.value = activeBundle.relatedVideos
            }
            if (!activeBundle.uploaderAvatarUrl.isNullOrEmpty() && _channelAvatarUrl.value.isNullOrEmpty()) {
                _channelAvatarUrl.value = activeBundle.uploaderAvatarUrl
            }
            _uploaderSubscriberText.value = activeBundle.uploaderSubscriberText
            currentUploaderUrl = activeBundle.uploaderUrl.orEmpty()
            currentUploaderName = activeBundle.uploaderName

            // Pulihkan status suka & tidak suka langsung dari repositori
            if (syncRepository.isLikedLocally(id)) {
                _isLiked.value = true
                _isDisliked.value = false
            } else if (syncRepository.isDislikedLocally(id)) {
                _isLiked.value = false
                _isDisliked.value = true
            } else {
                _isLiked.value = false
                _isDisliked.value = false
            }

            // Pulihkan status langganan saluran
            _isSubscribed.value = checkSubscribedLocally(activeBundle.uploaderUrl, activeBundle.uploaderName)

            // Sinkronkan data sekunder lanjutan dari YouTube di latar belakang tanpa menginterupsi pemutaran aktif
            watchNextJob?.cancel()
            watchNextJob = viewModelScope.launch {
                syncRepository.fetchWatchNextRelated(id).onSuccess { data ->
                    if (!data.viewCountText.isNullOrBlank()) {
                        _videoViewsText.value = data.viewCountText
                    }
                    if (!data.uploadDateText.isNullOrBlank()) {
                        _videoUploadDateText.value = data.uploadDateText
                    }
                    if (!data.commentCountText.isNullOrBlank()) {
                        _commentCountText.value = data.commentCountText
                    }
                    if (!data.commentTeaserText.isNullOrBlank()) {
                        _commentTeaser.value = data.commentTeaserText to data.commentTeaserAvatar
                    }
                    if (!data.subscriberText.isNullOrBlank()) {
                        _uploaderSubscriberText.value = data.subscriberText
                    }
                    if (!data.channelId.isNullOrEmpty()) {
                        currentChannelId = data.channelId
                    }
                    val localSub = checkSubscribedLocally(activeBundle.uploaderUrl, activeBundle.uploaderName) ||
                        (!data.channelId.isNullOrEmpty() && syncRepository.isSubscribedLocally(data.channelId))
                    _isSubscribed.value = (data.isSubscribed == true) || localSub

                    val isLocalLiked = syncRepository.isLikedLocally(id)
                    val isLocalDisliked = syncRepository.isDislikedLocally(id)
                    if (isLocalLiked || data.likeStatus == "LIKE") {
                        _isLiked.value = true
                        _isDisliked.value = false
                    } else if (isLocalDisliked || data.likeStatus == "DISLIKE") {
                        _isLiked.value = false
                        _isDisliked.value = true
                    }
                    if (data.relatedVideos.isNotEmpty()) {
                        _relatedVideos.value = (activeBundle.relatedVideos + data.relatedVideos).distinctBy { it.id }
                        playerController.updateRelatedVideos(data.relatedVideos)
                    }
                    nextRelatedContinuation = data.nextContinuation
                }
            }

            loadComments()
            return
        }

        // Hentikan seketika pemutaran video sebelumnya agar audio langsung diam (0 ms) & jaringan tidak tersedot download video lama
        playerController.prepareForNewVideo(id)

        if (addToHistory && currentVideoId.isNotEmpty() && currentVideoId != id) {
            playerController.recordHistory(currentVideoId)
        }
        currentVideoId = id
        loadVideoJob?.cancel()
        deferredSecondaryTasksJob?.cancel()
        watchNextJob?.cancel()
        commentsJob?.cancel()
        autoDiscoveryJob?.cancel()
        prefetchJob?.cancel()
        _isLoadingComments.value = false
        _comments.value = emptyList()

        _isLiked.value = syncRepository.isLikedLocally(id)
        _isDisliked.value = syncRepository.isDislikedLocally(id)
        val candidateSub = previewItem?.uploaderUrl ?: previewItem?.uploaderName
        _isSubscribed.value = if (!candidateSub.isNullOrBlank()) syncRepository.isSubscribedLocally(candidateSub) else false
        _uploaderSubscriberText.value = null
        _channelAvatarUrl.value = previewItem?.uploaderAvatarUrl
        _commentTeaser.value = null
        _commentCountText.value = null
        _videoViewsText.value = previewItem?.formattedViews?.takeIf { it.isNotEmpty() }?.let { "$it ditonton" }
        _videoUploadDateText.value = previewItem?.uploadDateText
        if (previewItem == null) {
            _relatedVideos.value = emptyList()
        }
        nextRelatedContinuation = null
        currentChannelId = ""

        // Tampilkan state Loading dengan preview instan agar user merasakan feedback responsif seketika (0 ms!)
        _uiState.value = WatchUiState.Loading(previewItem)

        // Segera muat komentar dan metadata lanjutan YouTube secara paralel (persis YouTube resmi)
        // Request JSON ringan (~20KB) ini langsung menyajikan antarmuka lengkap tanpa menunggu buffer video
        loadComments()
        watchNextJob?.cancel()
        watchNextJob = viewModelScope.launch {
            syncRepository.fetchWatchNextRelated(id).onSuccess { data ->
                if (!data.subscriberText.isNullOrBlank()) {
                    _uploaderSubscriberText.value = data.subscriberText
                }
                if (!data.channelId.isNullOrEmpty()) {
                    currentChannelId = data.channelId
                }
                if (!data.channelAvatarUrl.isNullOrEmpty()) {
                    _channelAvatarUrl.value = data.channelAvatarUrl
                }
                if (!data.commentTeaserText.isNullOrEmpty()) {
                    _commentTeaser.value = data.commentTeaserText to data.commentTeaserAvatar
                }
                val localSub = checkSubscribedLocally(previewItem?.uploaderUrl, previewItem?.uploaderName) ||
                    (!data.channelId.isNullOrEmpty() && syncRepository.isSubscribedLocally(data.channelId))
                _isSubscribed.value = (data.isSubscribed == true) || localSub

                val isLocalLiked = syncRepository.isLikedLocally(id)
                val isLocalDisliked = syncRepository.isDislikedLocally(id)
                if (isLocalLiked || data.likeStatus == "LIKE") {
                    _isLiked.value = true
                    _isDisliked.value = false
                } else if (isLocalDisliked || data.likeStatus == "DISLIKE") {
                    _isLiked.value = false
                    _isDisliked.value = true
                }
                if (data.relatedVideos.isNotEmpty()) {
                    _relatedVideos.value = (_relatedVideos.value + data.relatedVideos).distinctBy { it.id }
                    playerController.updateRelatedVideos(data.relatedVideos)
                }
                nextRelatedContinuation = data.nextContinuation
            }.onFailure {
                // Fallback jika fetchWatchNextRelated gagal: cari video terkait berdasarkan judul/artis agar daftar rekomendasi tidak kosong
                if (_relatedVideos.value.isEmpty()) {
                    val query = activeArtistKeyword ?: currentUploaderName.takeIf { it.isNotBlank() } ?: "lagu pop indonesia"
                    repository.search(query).onSuccess { list ->
                        val fallbackItems = list.filter { it.id != id }
                        if (fallbackItems.isNotEmpty()) {
                            _relatedVideos.value = fallbackItems
                            playerController.updateRelatedVideos(fallbackItems)
                        }
                    }
                }
            }
        }

        // 1. Jika bundle video ini sudah diprefetch sebelumnya, putar langsung tanpa delay ekstraksi!
        if (prefetchedNextBundle != null && prefetchedVideoId == id) {
            val bundle = prefetchedNextBundle!!
            prefetchedNextBundle = null
            prefetchedVideoId = null
            loadVideoJob = viewModelScope.launch {
                val effectiveMaxHeight = getEffectiveMaxHeight()
                playerController.setIsAutoQuality(effectiveMaxHeight <= 0)
                val shouldResume = preferencesRepository.resumePlayback.first()
                val savedProgressMs = if (shouldResume) {
                    syncRepository.getSavedPlaybackPosition(bundle.videoId, bundle.durationSeconds)
                } else 0L
                val durSec = bundle.durationSeconds
                val isCompleted = durSec > 10L && (
                    savedProgressMs >= (durSec - 10) * 1000L ||
                    savedProgressMs >= (durSec * 1000L * 0.90f).toLong()
                )
                val finalResumeMs = if (savedProgressMs > 2500L && !isCompleted && (durSec <= 0L || savedProgressMs < (durSec - 5) * 1000L)) {
                    savedProgressMs
                } else 0L
                applyAndPlayBundle(bundle, addToHistory = addToHistory, startPositionMs = finalResumeMs)
            }
            return
        }

        // 2. Jika ada di cache in-memory PlayerController, putar seketika 0 ms!
        val cachedBundle = playerController.streamBundleCache[id]
        if (cachedBundle != null) {
            loadVideoJob = viewModelScope.launch {
                val effectiveMaxHeight = getEffectiveMaxHeight()
                playerController.setIsAutoQuality(effectiveMaxHeight <= 0)
                val shouldResume = preferencesRepository.resumePlayback.first()
                val savedProgressMs = if (shouldResume) {
                    syncRepository.getSavedPlaybackPosition(cachedBundle.videoId, cachedBundle.durationSeconds)
                } else 0L
                val durSec = cachedBundle.durationSeconds
                val isCompleted = durSec > 10L && (
                    savedProgressMs >= (durSec - 10) * 1000L ||
                    savedProgressMs >= (durSec * 1000L * 0.90f).toLong()
                )
                val finalResumeMs = if (savedProgressMs > 2500L && !isCompleted && (durSec <= 0L || savedProgressMs < (durSec - 5) * 1000L)) {
                    savedProgressMs
                } else 0L
                applyAndPlayBundle(cachedBundle, addToHistory = addToHistory, startPositionMs = finalResumeMs)
            }
            return
        }

        // 3. Muat via network dengan alokasi penuh: semua job latar belakang lain dibatalkan agar tidak bersaing
        prefetchJob?.cancel()
        loadVideoJob = viewModelScope.launch {
            val effectiveMaxHeight = getEffectiveMaxHeight()
            playerController.setIsAutoQuality(effectiveMaxHeight <= 0)

            val result = playerController.preloadStream(currentVideoId).await()
            if (!isActive || currentVideoId != id) return@launch
            result.fold(
                onSuccess = { bundle ->
                    if (!isActive || currentVideoId != id) return@fold
                    val shouldResume = preferencesRepository.resumePlayback.first()
                    val savedProgressMs = if (shouldResume) {
                        syncRepository.getSavedPlaybackPosition(bundle.videoId, bundle.durationSeconds)
                    } else 0L

                    val durSec = bundle.durationSeconds
                    val isCompleted = durSec > 10L && (
                        savedProgressMs >= (durSec - 10) * 1000L ||
                        savedProgressMs >= (durSec * 1000L * 0.90f).toLong()
                    )
                    val finalResumeMs = if (savedProgressMs > 2500L && !isCompleted && (durSec <= 0L || savedProgressMs < (durSec - 5) * 1000L)) {
                        savedProgressMs
                    } else 0L

                    applyAndPlayBundle(bundle, addToHistory = addToHistory, startPositionMs = finalResumeMs)
                },
                onFailure = { error ->
                    if (isActive && currentVideoId == id) {
                        _uiState.value = WatchUiState.Error(
                            error.message ?: "Gagal memuat video ini"
                        )
                    }
                }
            )
        }
    }

    private fun applyAndPlayBundle(bundle: StreamBundle, addToHistory: Boolean = true, startPositionMs: Long = 0L) {
        _uiState.value = WatchUiState.Success(bundle)
        playerController.playVideo(bundle, addToHistory = addToHistory, startPositionMs = startPositionMs)

        _relatedVideos.value = bundle.relatedVideos
        _uploaderSubscriberText.value = bundle.uploaderSubscriberText
        currentUploaderUrl = bundle.uploaderUrl.orEmpty()
        currentUploaderName = bundle.uploaderName
        nextRelatedContinuation = null

        // 1. Cek status like & dislike langsung dari cache lokal syncRepository agar instan saat re-play video
        if (syncRepository.isLikedLocally(bundle.videoId)) {
            _isLiked.value = true
            _isDisliked.value = false
        } else if (syncRepository.isDislikedLocally(bundle.videoId)) {
            _isLiked.value = false
            _isDisliked.value = true
        } else {
            _isLiked.value = false
            _isDisliked.value = false
        }

        // Cek status langganan awal dari sinkronisasi akun
        _isSubscribed.value = checkSubscribedLocally(bundle.uploaderUrl, bundle.uploaderName)

        val playedVideo = id.katube.data.model.VideoItem(
            id = bundle.videoId,
            url = "https://www.youtube.com/watch?v=${bundle.videoId}",
            title = bundle.title,
            uploaderName = bundle.uploaderName,
            uploaderAvatarUrl = bundle.uploaderAvatarUrl,
            uploaderUrl = bundle.uploaderUrl,
            durationSeconds = bundle.durationSeconds,
            viewCount = bundle.viewCount,
            uploadDateText = bundle.uploadDateText,
            thumbnailUrl = bundle.thumbnailUrl,
            playbackPositionMs = startPositionMs,
            playbackPercent = if (bundle.durationSeconds > 0) (startPositionMs.toFloat() / (bundle.durationSeconds * 1000f)).coerceIn(0f, 1f) else 0f
        )
        syncRepository.recordLocalWatchHistory(playedVideo)

        viewModelScope.launch {
            val defaultSpeed = preferencesRepository.defaultSpeed.first()
            val defaultAudioOnly = preferencesRepository.defaultAudioOnly.first()
            if (defaultAudioOnly) {
                playerController.setAudioOnly(true)
            }
            if (defaultSpeed != 1.0f) {
                playerController.setPlaybackSpeed(defaultSpeed)
            }

            // Jaminan penyesuaian seek kedua jika player lambat merespons timeline
            if (startPositionMs > 0L) {
                kotlinx.coroutines.delay(350)
                if (kotlin.math.abs(playerController.currentPositionMs.value - startPositionMs) > 2500L) {
                    playerController.seekTo(startPositionMs)
                }
            }
        }

        sessionPlayedVideoIds.add(bundle.videoId)

        // Deteksi artis aktif dari judul atau channel untuk menjaga konsistensi putar otomatis
        val detectedArtist = extractArtistKeyword(bundle.title, bundle.uploaderName)
        if (!detectedArtist.isNullOrBlank()) {
            activeArtistKeyword = detectedArtist
        }

        // Pastikan komentar termuat jika sebelumnya belum terambil
        if (_comments.value.isEmpty() && !_isLoadingComments.value) {
            loadComments()
        }

        // Prefetch video berikutnya HANYA jika buffer video yang sedang diputar sudah tebal (> 15 detik)
        // dan pemutar dalam kondisi stabil (READY & bermain)
        deferredSecondaryTasksJob?.cancel()
        deferredSecondaryTasksJob = viewModelScope.launch {
            while (isActive) {
                val isPlaying = playerController.isPlaying.value
                val pos = playerController.currentPositionMs.value
                val buf = playerController.bufferedPositionMs.value
                val state = playerController.playbackState.value

                if (isPlaying && pos >= 2_000L && (buf - pos >= 15_000L) && state == androidx.media3.common.Player.STATE_READY) {
                    break
                }
                delay(1000)
            }

            if (!isActive) return@launch

            val bestCandidate = pickBestNextAutoplayCandidate()?.id
            if (!bestCandidate.isNullOrEmpty()) {
                val effectiveMaxHeight = getEffectiveMaxHeight()
                repository.getStreamInfo(bestCandidate, maxHeight = effectiveMaxHeight).onSuccess { nextBundle ->
                    prefetchedNextBundle = nextBundle
                    prefetchedVideoId = bestCandidate
                    playerController.cacheStreamBundle(nextBundle)
                }
            }
        }
    }

    fun playNextVideo() {
        if (playerController.repeatMode.value == id.katube.player.RepeatMode.ONE) {
            playerController.seekTo(0L)
            playerController.play()
            return
        }

        // 1. Cek antrean playlist aktif terlebih dahulu
        val q = playerController.queue.value
        val nextIndex = playerController.currentIndex.value + 1
        if (q.isNotEmpty() && nextIndex in q.indices) {
            playerController.setCurrentIndex(nextIndex)
            loadVideo(q[nextIndex].id, addToHistory = true, previewItem = q[nextIndex])
            return
        }

        // 2. Jika ada bundle yang sudah diprefetch, putar seketika (0 ms)
        val prefetched = prefetchedNextBundle
        if (prefetched != null) {
            loadVideo(prefetched.videoId, addToHistory = true)
            return
        }

        // 3. Pilih kandidat terbaik untuk putar otomatis
        val nextCandidate = pickBestNextAutoplayCandidate()
        if (nextCandidate != null && isValidVideoId(nextCandidate.id)) {
            loadVideo(nextCandidate.id, addToHistory = true, previewItem = nextCandidate)
            return
        }

        playerController.playNextInQueue()
    }

    /**
     * Memilih kandidat video berikutnya untuk putar otomatis yang paling relevan:
     * 1. Mengutamakan lagu dari artis yang sama (misal Dini Kurnia)
     * 2. Mengutamakan rilis/unggah terbaru
     * 3. Mencegah pemutaran ulang lagu yang sudah diputar di sesi ini
     */
    private fun pickBestNextAutoplayCandidate(): VideoItem? {
        val currentSuccess = _uiState.value as? WatchUiState.Success
        val allCandidates = (_relatedVideos.value + (currentSuccess?.bundle?.relatedVideos.orEmpty()) + (playerController.currentStreamBundle.value?.relatedVideos.orEmpty()))
            .filter { isValidVideoId(it.id) && it.id != currentVideoId }
            .distinctBy { it.id }

        if (allCandidates.isEmpty()) return null

        val playedHistory = playerController.playbackHistory.toSet() + sessionPlayedVideoIds
        val activeArtist = activeArtistKeyword?.trim()?.lowercase().orEmpty()

        if (activeArtist.length >= 3) {
            val scored = allCandidates.map { video ->
                val titleLower = video.title.lowercase()
                val uploaderLower = video.uploaderName.lowercase()
                var score = 0

                // 1. Kecocokan Artis (+10.000)
                val matchesArtist = titleLower.contains(activeArtist) || uploaderLower.contains(activeArtist)
                if (matchesArtist) {
                    score += 10_000
                    if (titleLower.contains(activeArtist)) score += 2_000
                }

                // 2. Kebaruan Video (Freshness Score +0 sampai +1000)
                score += calculateFreshnessScore(video.uploadDateText)

                // 3. Penalti jika sudah diputar baru-baru ini (-50.000)
                if (playedHistory.contains(video.id)) {
                    score -= 50_000
                }

                video to score
            }.sortedByDescending { it.second }

            // Ambil kandidat dengan skor tertinggi yang belum pernah diputar (skor > -40_000)
            val bestCandidate = scored.firstOrNull { it.second > -40_000 }?.first
            if (bestCandidate != null) return bestCandidate
        }

        // Fallback jika tidak ada artis yang cocok atau semua sudah diputar:
        // Pilih kandidat pertama yang belum pernah diputar di riwayat sesi ini
        return allCandidates.firstOrNull { !playedHistory.contains(it.id) }
            ?: allCandidates.firstOrNull()
    }

    private fun calculateFreshnessScore(uploadDateText: String?): Int {
        if (uploadDateText.isNullOrBlank()) return 50
        val text = uploadDateText.lowercase()
        return when {
            text.contains("detik") || text.contains("second") ||
            text.contains("menit") || text.contains("minute") ||
            text.contains("jam") || text.contains("hour") -> 1000
            text.contains("hari") || text.contains("day") -> {
                val num = text.filter { it.isDigit() }.toIntOrNull() ?: 1
                (900 - (num * 10)).coerceAtLeast(600)
            }
            text.contains("minggu") || text.contains("week") -> {
                val num = text.filter { it.isDigit() }.toIntOrNull() ?: 1
                (550 - (num * 20)).coerceAtLeast(400)
            }
            text.contains("bulan") || text.contains("month") -> {
                val num = text.filter { it.isDigit() }.toIntOrNull() ?: 1
                (350 - (num * 15)).coerceAtLeast(100)
            }
            text.contains("tahun") || text.contains("year") -> {
                val num = text.filter { it.isDigit() }.toIntOrNull() ?: 1
                (80 - (num * 10)).coerceAtLeast(0)
            }
            else -> 50
        }
    }

    /**
     * Mengekstrak nama artis/penyanyi dari judul lagu atau nama uploader.
     */
    private fun extractArtistKeyword(title: String, uploader: String): String? {
        val cleanTitle = title
            .replace(Regex("""(?i)\[.*?\]|\(.*?\)|【.*?】"""), "")
            .replace(Regex("""(?i)\b(official|music|video|lirik|lyric|audio|clip|klip|mv|live|karaoke|full album|hq|hd|4k)\b"""), "")
            .trim()

        val parts = cleanTitle.split(Regex("""\s*[-–|:]\s*""")).map { it.trim() }.filter { it.isNotEmpty() }
        var artist: String? = null

        val labelKeywords = listOf(
            "aneka safari", "perdana record", "fortuna", "mega record", "khatulistiwa",
            "pelita utama", "sumber kencana", "global musik", "chgb record", "aglis",
            "teta record", "samudra record", "bw record", "dasa studio", "sakura", "music", "records"
        )

        if (parts.size >= 2) {
            val p0 = parts[0]
            val isP0Label = labelKeywords.any { p0.contains(it, ignoreCase = true) }
            artist = if (isP0Label && parts.size >= 3) {
                parts[1]
            } else if (isP0Label) {
                parts[1]
            } else {
                p0
            }
        } else if (uploader.isNotBlank()) {
            val cleanUploader = uploader
                .replace(Regex("""(?i)\b(official|music|channel|records?|record|entertainment|studio|media|production|tv|topic)\b"""), "")
                .trim()
            if (cleanUploader.length >= 3) {
                artist = cleanUploader
            }
        }

        if (artist != null) {
            artist = artist.split(Regex("""(?i)\s+(?:ft\.?|feat\.?|x|with)\s+"""))[0].trim()
            if (artist.length >= 3 && !artist.equals("lagu", ignoreCase = true)) {
                return artist
            }
        }
        return null
    }

    private fun isValidVideoId(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        return id.length == 11 && !id.startsWith("RD") && !id.startsWith("PL") && !id.startsWith("VL") && !id.startsWith("UC")
    }

    fun playRelatedVideo(nextId: String) {
        val preview = _relatedVideos.value.find { it.id == nextId }
        playerController.clearPlaylistQueue()
        loadVideo(nextId, addToHistory = true, previewItem = preview)
    }

    fun playInBackground(videoId: String) {
        playerController.playInBackground(videoId)
    }

    fun removeVideoFromPlaylist(video: VideoItem, onComplete: ((Boolean) -> Unit)? = null) {
        val playlist = currentPlaylist.value ?: return
        val wasCurrentPlaying = playerController.queue.value.getOrNull(playerController.currentIndex.value)?.id == video.id

        // 1. Hapus dari queue pemutar seketika
        playerController.removeVideoFromQueue(video.id)

        // 2. Jika video yang dihapus adalah video yang sedang diputar, putar video berikutnya di indeks saat ini
        if (wasCurrentPlaying) {
            val q = playerController.queue.value
            val curIdx = playerController.currentIndex.value
            if (q.isNotEmpty() && curIdx in q.indices) {
                loadVideo(q[curIdx].id, addToHistory = true, previewItem = q[curIdx])
            }
        }

        // 3. Kirim penghapusan ke server YouTube via Innertube
        viewModelScope.launch {
            val res = syncRepository.removeVideoFromPlaylist(playlist.id, video.id)
            onComplete?.invoke(res.isSuccess)
        }
    }

    fun playPreviousVideo() {
        if (playerController.repeatMode.value == id.katube.player.RepeatMode.ONE) {
            playerController.seekTo(0L)
            playerController.play()
            return
        }

        // Standar YouTube: jika video sudah berjalan lebih dari 3 detik, putar ulang dari awal (0 ms latency)
        val currentPos = playerController.currentPositionMs.value
        if (currentPos > 3000L) {
            playerController.seekTo(0L)
            playerController.play()
            return
        }

        // Jika di awal (< 3 detik), putar video sebelumnya dari riwayat
        val prevId = playerController.popPreviousVideoId()
        if (prevId != null) {
            val preview = _relatedVideos.value.find { it.id == prevId }
            loadVideo(prevId, addToHistory = false, previewItem = preview)
            return
        }

        // Jika ada antrean playlist aktif, mundur ke video sebelumnya di playlist
        val q = playerController.queue.value
        val prevIndex = playerController.currentIndex.value - 1
        if (q.isNotEmpty() && prevIndex in q.indices) {
            playerController.setCurrentIndex(prevIndex)
            loadVideo(q[prevIndex].id, addToHistory = false, previewItem = q[prevIndex])
            return
        }

        // Fallback: putar ulang dari detik ke-0
        playerController.seekTo(0L)
        playerController.play()
    }

    fun loadComments() {
        if (currentVideoId.isEmpty()) return
        commentsJob?.cancel()
        commentsJob = viewModelScope.launch {
            _isLoadingComments.value = true
            repository.getComments(currentVideoId).fold(
                onSuccess = { list ->
                    _comments.value = list
                    _isLoadingComments.value = false
                },
                onFailure = {
                    _isLoadingComments.value = false
                }
            )
        }
    }

    fun addComment(text: String, onResult: ((Boolean, String) -> Unit)? = null) {
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
            val newComment = CommentItem(
                id = "user_${System.currentTimeMillis()}",
                authorName = authorName,
                authorAvatarUrl = authorAvatar,
                text = trimmed,
                uploadDateText = "Baru saja",
                likeCount = 0,
                isUserComment = true
            )
            _comments.value = listOf(newComment) + _comments.value
            syncRepository.postComment(currentVideoId, trimmed)
            onResult?.invoke(true, "Komentar berhasil dikirim")
        }
    }

    fun deleteComment(commentId: String, onResult: ((Boolean, String) -> Unit)? = null) {
        if (commentId.isEmpty()) return
        _comments.value = _comments.value.filter { it.id != commentId }
        viewModelScope.launch {
            syncRepository.deleteComment(commentId)
            onResult?.invoke(true, "Komentar dihapus")
        }
    }

    fun toggleAutoplay() {
        val next = !playerController.isAutoplayEnabled.value
        playerController.isAutoplayEnabled.value = next
        viewModelScope.launch {
            preferencesRepository.setAutoplayNext(next)
        }
    }

    fun selectQuality(quality: VideoQuality) {
        playerController.selectQuality(quality)
        val current = (_uiState.value as? WatchUiState.Success)?.bundle ?: return
        val updated = playerController.currentStreamBundle.value ?: current
        _uiState.value = WatchUiState.Success(updated)
    }

    fun toggleAudioOnly() {
        val current = playerController.isAudioOnly.value
        playerController.setAudioOnly(!current)
    }

    fun loadMoreRelatedVideos() {
        val token = nextRelatedContinuation ?: return
        if (_isLoadingMoreRelated.value) return
        _isLoadingMoreRelated.value = true
        viewModelScope.launch {
            val result = syncRepository.fetchWatchNextRelated(currentVideoId, token)
            result.onSuccess { data ->
                nextRelatedContinuation = data.nextContinuation
                if (data.relatedVideos.isNotEmpty()) {
                    _relatedVideos.value = (_relatedVideos.value + data.relatedVideos).distinctBy { it.id }
                    playerController.updateRelatedVideos(data.relatedVideos)
                }
            }
            _isLoadingMoreRelated.value = false
        }
    }

    fun toggleLike(onResult: ((Boolean, String) -> Unit)? = null) {
        val videoId = currentVideoId
        if (videoId.isEmpty()) return
        val current = _isLiked.value
        val target = !current
        _isLiked.value = target
        if (target) _isDisliked.value = false

        viewModelScope.launch {
            val res = if (target) {
                syncRepository.likeVideo(videoId)
            } else {
                syncRepository.removeLikeOrDislike(videoId)
            }
            res.fold(
                onSuccess = {
                    onResult?.invoke(true, if (target) "Ditambahkan ke Video yang disukai" else "Dihapus dari Video yang disukai")
                },
                onFailure = { err ->
                    _isLiked.value = current
                    onResult?.invoke(false, err.message ?: "Gagal memperbarui status suka")
                }
            )
        }
    }

    fun toggleDislike(onResult: ((Boolean, String) -> Unit)? = null) {
        val videoId = currentVideoId
        if (videoId.isEmpty()) return
        val current = _isDisliked.value
        val target = !current
        _isDisliked.value = target
        if (target) _isLiked.value = false

        viewModelScope.launch {
            val res = if (target) {
                syncRepository.dislikeVideo(videoId)
            } else {
                syncRepository.removeLikeOrDislike(videoId)
            }
            res.fold(
                onSuccess = {
                    onResult?.invoke(true, if (target) "Video tidak disukai" else "Batal tidak suka")
                },
                onFailure = { err ->
                    _isDisliked.value = current
                    onResult?.invoke(false, err.message ?: "Gagal memperbarui status tidak suka")
                }
            )
        }
    }

    fun toggleSubscribe(onResult: ((Boolean, String) -> Unit)? = null) {
        val targetChannel = currentChannelId.ifEmpty { currentUploaderUrl.ifEmpty { currentUploaderName } }
        if (targetChannel.isEmpty()) return
        val current = _isSubscribed.value
        val target = !current
        _isSubscribed.value = target

        viewModelScope.launch {
            val res = if (target) {
                syncRepository.subscribeChannel(
                    channelIdOrUrl = targetChannel,
                    channelName = currentUploaderName,
                    channelAvatar = _channelAvatarUrl.value
                )
            } else {
                syncRepository.unsubscribeChannel(targetChannel)
            }
            res.fold(
                onSuccess = {
                    val name = currentUploaderName.ifEmpty { "channel" }
                    val msg = if (target) "Berlangganan $name" else "Batal berlangganan $name"
                    onResult?.invoke(true, msg)
                },
                onFailure = { err ->
                    _isSubscribed.value = current
                    onResult?.invoke(false, err.message ?: "Gagal mengubah status langganan")
                }
            )
        }
    }

    fun addVideoToPlaylist(playlistId: String, playlistName: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val videoId = currentVideoId
        if (videoId.isEmpty()) return
        viewModelScope.launch {
            val res = syncRepository.addVideoToPlaylist(playlistId, videoId)
            res.fold(
                onSuccess = {
                    onResult?.invoke(true, "Ditambahkan ke $playlistName")
                },
                onFailure = { err ->
                    onResult?.invoke(false, err.message ?: "Gagal menambahkan ke playlist")
                }
            )
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        playerController.setPlaybackSpeed(speed)
    }

    override fun onCleared() {
        super.onCleared()
        autoDiscoveryJob?.cancel()
        prefetchJob?.cancel()
        watchNextJob?.cancel()
        val pos = playerController.currentPositionMs.value
        val dur = playerController.durationMs.value
        if (currentVideoId.isNotEmpty() && pos > 1000L) {
            syncRepository.reportPlaybackProgress(currentVideoId, pos, dur, isPausedOrEnded = true)
        }
    }
}
