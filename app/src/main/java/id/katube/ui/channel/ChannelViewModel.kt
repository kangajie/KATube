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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearch() {
        _isSearchActive.value = !_isSearchActive.value
        if (!_isSearchActive.value) {
            _searchQuery.value = ""
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    val isSubscribed: StateFlow<Boolean> = syncRepository.subscribedChannels
        .map { list ->
            val cleanTarget = if (channelId.startsWith("http")) channelId.substringAfterLast("/") else channelId
            list.any {
                it.id == cleanTarget || it.name.equals(cleanTarget, ignoreCase = true) || it.url.contains(cleanTarget)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var loadJob: kotlinx.coroutines.Job? = null

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

    fun removeVideo(videoId: String) {
        val current = _uiState.value
        if (current is ChannelUiState.Success) {
            val updatedVideos = current.channel.videos.filter { it.id != videoId }
            _uiState.value = ChannelUiState.Success(current.channel.copy(videos = updatedVideos))
        }
    }

    fun loadChannel(targetId: String = channelId, forceReload: Boolean = false) {
        val idToLoad = targetId.ifEmpty { channelId }
        if (idToLoad.isBlank()) return

        val currentState = _uiState.value
        if (!forceReload && currentState is ChannelUiState.Success && (currentState.channel.id == idToLoad || currentState.channel.url.contains(idToLoad))) {
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (_uiState.value !is ChannelUiState.Success) {
                _uiState.value = ChannelUiState.Loading
            }
            val result = repository.getChannel(idToLoad)
            result.fold(
                onSuccess = { channel ->
                    _uiState.value = ChannelUiState.Success(channel)
                },
                onFailure = { error ->
                    if (_uiState.value !is ChannelUiState.Success) {
                        _uiState.value = ChannelUiState.Error(
                            error.message ?: "Gagal memuat profil channel"
                        )
                    }
                }
            )
        }
    }
}
