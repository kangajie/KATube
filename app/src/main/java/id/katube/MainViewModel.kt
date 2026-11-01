package id.katube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import id.katube.data.auth.AuthRepository
import id.katube.data.model.GoogleAccount
import id.katube.data.model.KATubeNotification
import id.katube.data.preferences.UserPreferencesRepository
import id.katube.player.PlayerController
import id.katube.ui.settings.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@UnstableApi
@HiltViewModel
class MainViewModel @Inject constructor(
    val playerController: PlayerController,
    val preferencesRepository: UserPreferencesRepository,
    private val authRepository: AuthRepository,
    private val syncRepository: id.katube.data.sync.YouTubeSyncRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeMode.SYSTEM,
        )

    val account: StateFlow<GoogleAccount> = authRepository.account
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GoogleAccount(),
        )

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    private val _notifications = MutableStateFlow<List<KATubeNotification>>(emptyList())
    val notifications: StateFlow<List<KATubeNotification>> = _notifications.asStateFlow()

    private val _isLoadingNotifications = MutableStateFlow(false)
    val isLoadingNotifications: StateFlow<Boolean> = _isLoadingNotifications.asStateFlow()

    fun loadNotifications() {
        viewModelScope.launch {
            _isLoadingNotifications.value = true
            try {
                val list = syncRepository.fetchNotifications()
                _notifications.value = list
            } catch (_: Exception) {
                _notifications.value = emptyList()
            } finally {
                _isLoadingNotifications.value = false
            }
        }
    }

    init {
        viewModelScope.launch {
            authRepository.account.collect { acc ->
                id.katube.core.network.OkHttpDownloader.currentCookies = if (acc.isLoggedIn) acc.cookies else ""
                if (acc.isLoggedIn && acc.cookies.isNotBlank() && (acc.avatarUrl.isNullOrEmpty() || acc.displayName == "Pengguna YouTube")) {
                    try {
                        syncRepository.syncAccountProfile(acc.cookies)
                    } catch (_: Exception) {}
                }
            }
        }

        // Hubungkan progres pemutaran PlayerController ke repositori sinkronisasi riwayat YouTube
        playerController.onPlaybackProgress = { videoId, posMs, durMs, isEnded ->
            syncRepository.reportPlaybackProgress(videoId, posMs, durMs, isEnded)
        }
    }

    private val _bottomBarReselectEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val bottomBarReselectEvent: SharedFlow<String> = _bottomBarReselectEvent.asSharedFlow()

    fun onBottomBarReselect(route: String) {
        _bottomBarReselectEvent.tryEmit(route)
    }

    private val _pendingNavigationVideoId = MutableStateFlow<String?>(null)
    val pendingNavigationVideoId: StateFlow<String?> = _pendingNavigationVideoId.asStateFlow()

    fun triggerNavigateToWatch() {
        val currentId = playerController.currentStreamBundle.value?.videoId
            ?: playerController.playerState.value?.currentMediaItem?.mediaId
        if (!currentId.isNullOrEmpty()) {
            _pendingNavigationVideoId.value = currentId
        } else {
            _pendingNavigationVideoId.value = "ACTIVE_PLAYER"
        }
    }

    fun triggerNavigateToVideo(videoId: String) {
        if (videoId.isNotBlank()) {
            _pendingNavigationVideoId.value = videoId
        }
    }

    fun consumePendingNavigation() {
        _pendingNavigationVideoId.value = null
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    val userPlaylists: StateFlow<List<id.katube.data.model.PlaylistItem>> = syncRepository.userPlaylists

    private val _targetVideoForPlaylist = MutableStateFlow<id.katube.data.model.VideoItem?>(null)
    val targetVideoForPlaylist: StateFlow<id.katube.data.model.VideoItem?> = _targetVideoForPlaylist.asStateFlow()

    fun openAddToPlaylist(video: id.katube.data.model.VideoItem) {
        _targetVideoForPlaylist.value = video
        if (isLoggedIn.value) {
            viewModelScope.launch {
                val acc = account.value
                if (acc.isLoggedIn && acc.cookies.isNotBlank()) {
                    syncRepository.fetchUserPlaylists(acc.cookies)
                }
            }
        }
    }

    fun closeAddToPlaylist() {
        _targetVideoForPlaylist.value = null
    }

    fun addVideoToPlaylist(playlistId: String, playlistName: String, video: id.katube.data.model.VideoItem, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = syncRepository.addVideoToPlaylist(playlistId, video.id)
            res.fold(
                onSuccess = {
                    onResult(true, "Ditambahkan ke $playlistName")
                },
                onFailure = { err ->
                    onResult(false, err.message ?: "Gagal menambahkan ke playlist")
                }
            )
        }
    }

    fun addToWatchLater(video: id.katube.data.model.VideoItem, onResult: (Boolean, String) -> Unit) {
        if (!isLoggedIn.value) {
            onResult(false, "Silakan login ke akun Google untuk menyimpan ke Tonton Nanti")
            return
        }
        addVideoToPlaylist("WL", "Tonton Nanti", video, onResult)
    }

    fun playMix(video: id.katube.data.model.VideoItem, onStart: (String) -> Unit) {
        val mixPlaylistId = video.playlistId ?: if (video.id.startsWith("RD") || video.id.startsWith("PL")) video.id else null
        val seedVideoId = if (video.id.length == 11 && !video.id.startsWith("RD") && !video.id.startsWith("PL")) video.id else ""

        // Bersihkan preview agar tidak menampilkan judul Mix generik atau uploader gabungan ("Alan Walker dan lainnya")
        val isWrapperTitle = video.title.startsWith("Mix -", ignoreCase = true) || video.title.startsWith("Mix –", ignoreCase = true)
        val isWrapperUploader = video.uploaderName.contains("dan lainnya", ignoreCase = true) || video.uploaderName.contains("& others", ignoreCase = true)
        val safePreview = if (seedVideoId.isNotEmpty() && !isWrapperTitle && !isWrapperUploader) {
            video.copy(isMix = false, playlistId = null)
        } else {
            null
        }

        if (seedVideoId.isNotBlank() && safePreview != null) {
            playerController.prepareForNewVideo(seedVideoId, previewItem = safePreview)
            onStart(seedVideoId)
        }

        viewModelScope.launch {
            val playlistIdToFetch = mixPlaylistId ?: if (seedVideoId.isNotEmpty()) "RD$seedVideoId" else null
            if (!playlistIdToFetch.isNullOrEmpty()) {
                val result = syncRepository.fetchPlaylistDetails(playlistIdToFetch, seedVideoId = seedVideoId.ifBlank { null })
                result.fold(
                    onSuccess = { playlist ->
                        // Pastikan seluruh video di dalam antrean Mix adalah video satuan murni (bukan mix wrapper)
                        val cleanVideos = playlist.videos
                            .filter {
                                it.id.length == 11 &&
                                !it.id.startsWith("RD") &&
                                !it.isMix &&
                                !it.title.startsWith("Mix -", ignoreCase = true) &&
                                !it.title.startsWith("Mix –", ignoreCase = true) &&
                                !it.uploaderName.contains("dan lainnya", ignoreCase = true) &&
                                !it.uploaderName.contains("& others", ignoreCase = true)
                            }
                            .map { it.copy(isMix = false, playlistId = null) }
                        val cleanPlaylist = playlist.copy(videos = cleanVideos)

                        if (cleanPlaylist.videos.isNotEmpty()) {
                            val targetVideo = if (seedVideoId.isNotBlank()) {
                                cleanPlaylist.videos.firstOrNull { it.id == seedVideoId } ?: cleanPlaylist.videos.first()
                            } else {
                                cleanPlaylist.videos.first()
                            }
                            val startIdx = cleanPlaylist.videos.indexOfFirst { it.id == targetVideo.id }.coerceAtLeast(0)
                            playerController.setPlaylistQueue(cleanPlaylist, startIdx)
                            playerController.prepareForNewVideo(targetVideo.id, previewItem = targetVideo)
                            onStart(targetVideo.id)
                        } else if (seedVideoId.isNotBlank()) {
                            playerController.prepareForNewVideo(seedVideoId, previewItem = safePreview)
                            onStart(seedVideoId)
                        }
                    },
                    onFailure = {
                        if (seedVideoId.isNotBlank()) {
                            playerController.prepareForNewVideo(seedVideoId, previewItem = safePreview)
                            onStart(seedVideoId)
                        }
                    }
                )
            } else if (seedVideoId.isNotBlank()) {
                playerController.prepareForNewVideo(seedVideoId, previewItem = safePreview)
                onStart(seedVideoId)
            }
        }
    }
}
