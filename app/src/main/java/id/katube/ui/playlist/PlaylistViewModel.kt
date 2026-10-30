package id.katube.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import id.katube.data.extractor.YouTubeRepository
import id.katube.data.model.PlaylistItem
import id.katube.data.sync.YouTubeSyncRepository
import id.katube.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PlaylistUiState {
    object Loading : PlaylistUiState
    data class Success(val playlist: PlaylistItem) : PlaylistUiState
    data class Error(val message: String) : PlaylistUiState
}

@UnstableApi
@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: YouTubeRepository,
    private val syncRepository: YouTubeSyncRepository,
    val playerController: PlayerController,
) : ViewModel() {

    private val playlistId: String = checkNotNull(savedStateHandle["playlistId"])

    private val _uiState = MutableStateFlow<PlaylistUiState>(PlaylistUiState.Loading)
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    init {
        loadPlaylist()
    }

    fun loadPlaylist() {
        viewModelScope.launch {
            _uiState.value = PlaylistUiState.Loading

            val cleanId = playlistId
                .substringAfter("list=")
                .substringBefore("&")
                .removePrefix("VL")
                .trim()

            // 1. Jika ini adalah playlist akun (WL = Tonton Nanti, LL = Disukai, atau playlist buatan pengguna):
            // Gunakan langsung syncRepository karena NewPipeExtractor selalu gagal (404/Private) tanpa akun
            val isUserPlaylist = syncRepository.userPlaylists.value.any {
                it.id.removePrefix("VL").equals(cleanId, ignoreCase = true)
            }
            if (isUserPlaylist || cleanId.equals("WL", ignoreCase = true) || cleanId.equals("LL", ignoreCase = true) || cleanId.startsWith("RD")) {
                syncRepository.fetchPlaylistDetails(cleanId).fold(
                    onSuccess = { playlist ->
                        _uiState.value = PlaylistUiState.Success(playlist)
                    },
                    onFailure = { error ->
                        // Fallback ke repository jika diperlukan
                        val result = repository.getPlaylist(cleanId)
                        result.fold(
                            onSuccess = { pl -> _uiState.value = PlaylistUiState.Success(pl) },
                            onFailure = { _uiState.value = PlaylistUiState.Error(error.message ?: "Gagal memuat playlist") }
                        )
                    }
                )
                return@launch
            }

            // 2. Untuk playlist lain (PL... publik):
            // Coba NewPipeExtractor terlebih dahulu. Jika gagal, fallback ke syncRepository
            val result = repository.getPlaylist(cleanId)
            result.fold(
                onSuccess = { playlist ->
                    if (playlist.videos.isEmpty()) {
                        val syncRes = syncRepository.fetchPlaylistDetails(cleanId)
                        if (syncRes.isSuccess && syncRes.getOrNull()?.videos?.isNotEmpty() == true) {
                            _uiState.value = PlaylistUiState.Success(syncRes.getOrNull()!!)
                            return@fold
                        }
                    }
                    _uiState.value = PlaylistUiState.Success(playlist)
                },
                onFailure = { extractorError ->
                    val syncRes = syncRepository.fetchPlaylistDetails(cleanId)
                    syncRes.fold(
                        onSuccess = { playlist ->
                            _uiState.value = PlaylistUiState.Success(playlist)
                        },
                        onFailure = {
                            _uiState.value = PlaylistUiState.Error(
                                extractorError.message ?: "Gagal memuat playlist"
                            )
                        }
                    )
                }
            )
        }
    }

    fun playAll(): String? {
        val playlist = (_uiState.value as? PlaylistUiState.Success)?.playlist ?: return null
        if (playlist.videos.isNotEmpty()) {
            playerController.setPlaylistQueue(playlist, startIndex = 0)
            return playlist.videos.first().id
        }
        return null
    }

    fun playAt(index: Int) {
        val playlist = (_uiState.value as? PlaylistUiState.Success)?.playlist ?: return
        if (index in playlist.videos.indices) {
            playerController.setPlaylistQueue(playlist, startIndex = index)
        }
    }

    fun playInBackground(videoId: String) {
        playerController.playInBackground(videoId)
    }

    fun removeVideoFromPlaylist(videoId: String) {
        val current = (_uiState.value as? PlaylistUiState.Success)?.playlist ?: return
        val updatedVideos = current.videos.filter { it.id != videoId }
        _uiState.value = PlaylistUiState.Success(
            current.copy(
                videos = updatedVideos,
                streamCount = updatedVideos.size.toLong()
            )
        )
        if (playerController.currentPlaylist.value?.id == current.id) {
            playerController.removeVideoFromQueue(videoId)
        }
        viewModelScope.launch {
            syncRepository.removeVideoFromPlaylist(current.id, videoId)
        }
    }
}
