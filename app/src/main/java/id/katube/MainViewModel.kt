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

    fun playMix(video: id.katube.data.model.VideoItem, onStart: (String) -> Unit) {
        val mixPlaylistId = video.playlistId ?: if (video.id.startsWith("RD")) video.id else null
        val targetVideoId = if (video.id.length == 11 && !video.id.startsWith("RD")) video.id else ""

        if (targetVideoId.isNotBlank()) {
            onStart(targetVideoId)
        }

        if (!mixPlaylistId.isNullOrEmpty()) {
            viewModelScope.launch {
                syncRepository.fetchPlaylistDetails(mixPlaylistId).onSuccess { playlist ->
                    if (playlist.videos.isNotEmpty()) {
                        val playId = if (targetVideoId.isBlank()) playlist.videos.first().id else targetVideoId
                        val startIdx = playlist.videos.indexOfFirst { it.id == playId }.coerceAtLeast(0)
                        playerController.setPlaylistQueue(playlist, startIdx)
                        if (targetVideoId.isBlank()) {
                            onStart(playId)
                        }
                    }
                }
            }
        }
    }
}
