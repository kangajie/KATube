package id.katube.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.katube.data.auth.AuthRepository
import id.katube.data.model.ChannelItem
import id.katube.data.model.VideoItem
import id.katube.data.sync.YouTubeSyncRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubscriptionsViewModel @Inject constructor(
    private val syncRepository: YouTubeSyncRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = authRepository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val subscribedChannels: StateFlow<List<ChannelItem>> = syncRepository.subscribedChannels
    val subscriptionFeed: StateFlow<List<VideoItem>> = syncRepository.subscriptionFeed
    val isSyncing: StateFlow<Boolean> = syncRepository.isSyncing

    fun sync() {
        viewModelScope.launch {
            syncRepository.syncAll()
        }
    }

    suspend fun refreshSuspend() {
        val account = authRepository.account.first()
        if (account.isLoggedIn) {
            syncRepository.syncAll()
        }
    }
}
