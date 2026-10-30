package id.katube.ui.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.katube.data.extractor.YouTubeRepository
import id.katube.data.model.ChannelItem
import id.katube.data.sync.YouTubeSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChannelUiState {
    object Loading : ChannelUiState
    data class Success(val channel: ChannelItem) : ChannelUiState
    data class Error(val message: String) : ChannelUiState
}

@HiltViewModel
class ChannelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: YouTubeRepository,
    private val syncRepository: YouTubeSyncRepository,
) : ViewModel() {

    private val rawChannelId: String = savedStateHandle.get<String>("channelId").orEmpty()
    val channelId: String = try {
        java.net.URLDecoder.decode(rawChannelId, "UTF-8")
    } catch (_: Exception) {
        rawChannelId
    }

    private val _uiState = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    val isSubscribed: StateFlow<Boolean> = syncRepository.subscribedChannels
        .map { list ->
            val cleanTarget = if (channelId.startsWith("http")) channelId.substringAfterLast("/") else channelId
            list.any {
                it.id == cleanTarget || it.name.equals(cleanTarget, ignoreCase = true) || it.url.contains(cleanTarget)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        if (channelId.isNotBlank()) {
            loadChannel()
        }
    }

    fun toggleSubscribe(onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val currentlySubscribed = isSubscribed.value
            val successState = _uiState.value as? ChannelUiState.Success
            val channelName = successState?.channel?.name ?: channelId
            val avatarUrl = successState?.channel?.avatarUrl
            if (currentlySubscribed) {
                syncRepository.unsubscribeChannel(channelId)
                onResult?.invoke(false, "Berhenti berlangganan $channelName")
            } else {
                syncRepository.subscribeChannel(channelId, channelName = channelName, channelAvatar = avatarUrl)
                onResult?.invoke(true, "Berlangganan $channelName")
            }
        }
    }

    fun loadChannel(targetId: String = channelId) {
        val idToLoad = targetId.ifEmpty { channelId }
        if (idToLoad.isBlank()) return
        viewModelScope.launch {
            _uiState.value = ChannelUiState.Loading
            val result = repository.getChannel(idToLoad)
            result.fold(
                onSuccess = { channel ->
                    _uiState.value = ChannelUiState.Success(channel)
                },
                onFailure = { error ->
                    _uiState.value = ChannelUiState.Error(
                        error.message ?: "Gagal memuat profil channel"
                    )
                }
            )
        }
    }
}
