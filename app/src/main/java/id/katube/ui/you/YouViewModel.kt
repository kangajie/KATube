package id.katube.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.katube.data.auth.AuthRepository
import id.katube.data.model.PlaylistItem
import id.katube.data.model.VideoItem
import id.katube.data.sync.YouTubeSyncRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

import id.katube.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class YouViewModel @Inject constructor(
    private val syncRepository: YouTubeSyncRepository,
    private val authRepository: AuthRepository,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val watchHistory: StateFlow<List<VideoItem>> = syncRepository.watchHistory
    val userPlaylists: StateFlow<List<PlaylistItem>> = syncRepository.userPlaylists
    val isSyncing: StateFlow<Boolean> = syncRepository.isSyncing
    val syncStatus: StateFlow<String> = syncRepository.syncStatus
    val watchHistoryEnabled: StateFlow<Boolean> = preferencesRepository.watchHistoryEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true,
        )

    private var lastHistoryRefreshTime = 0L

    fun sync() {
        viewModelScope.launch {
            syncRepository.syncAll()
        }
    }

    fun refreshHistory(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastHistoryRefreshTime < 30_000L) {
            return
        }
        lastHistoryRefreshTime = now
        viewModelScope.launch {
            val account = authRepository.account.first()
            if (account.isLoggedIn && account.cookies.isNotBlank()) {
                syncRepository.fetchWatchHistory(account.cookies)
            }
        }
    }

    suspend fun refreshSuspend() {
        val account = authRepository.account.first()
        if (account.isLoggedIn) {
            syncRepository.syncAll()
        }
    }
}
