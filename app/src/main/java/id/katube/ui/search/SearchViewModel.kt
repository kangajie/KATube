package id.katube.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.katube.data.extractor.YouTubeRepository
import id.katube.data.model.VideoItem
import id.katube.data.preferences.UserPreferencesRepository
import id.katube.data.sync.YouTubeSyncRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchResultState {
    object Idle : SearchResultState
    object Loading : SearchResultState
    data class Success(val videos: List<VideoItem>) : SearchResultState
    data class Error(val message: String) : SearchResultState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val syncRepository: YouTubeSyncRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _resultState = MutableStateFlow<SearchResultState>(SearchResultState.Idle)
    val resultState: StateFlow<SearchResultState> = _resultState.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    val searchHistory: StateFlow<List<String>> = preferencesRepository.searchHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val searchHistoryEnabled: StateFlow<Boolean> = preferencesRepository.searchHistoryEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true,
        )

    private val searchVideos = mutableListOf<VideoItem>()
    private var searchPageIndex = 0
    private var isCurrentlyLoadingMore = false
    private var debounceJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _suggestions.value = emptyList()
            _resultState.value = SearchResultState.Idle
            return
        }

        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(300) // Debounce 300ms untuk autocomplete
            val result = repository.getSearchSuggestions(newQuery)
            _suggestions.value = result.getOrDefault(emptyList())
        }
    }

    fun search(queryToSearch: String) {
        val trimmed = queryToSearch.trim()
        if (trimmed.isEmpty()) return

        _query.value = trimmed
        _suggestions.value = emptyList()

        // Catat pencarian ke riwayat lokal & sinkronkan ke server YouTube jika diaktifkan di Pengaturan
        viewModelScope.launch {
            if (preferencesRepository.searchHistoryEnabled.first()) {
                preferencesRepository.addSearchQuery(trimmed)
                syncRepository.recordSearchQuery(trimmed)
            }
        }

        viewModelScope.launch {
            _resultState.value = SearchResultState.Loading
            searchVideos.clear()
            searchPageIndex = 0

            val result = repository.search(trimmed)
            result.fold(
                onSuccess = { videos ->
                    searchVideos.addAll(videos.distinctBy { it.id })
                    _resultState.value = SearchResultState.Success(searchVideos.toList())
                },
                onFailure = { error ->
                    _resultState.value = SearchResultState.Error(
                        error.message ?: "Gagal mencari video"
                    )
                }
            )
        }
    }

    fun deleteHistoryItem(item: String) {
        viewModelScope.launch {
            preferencesRepository.removeSearchQuery(item)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            preferencesRepository.clearSearchHistory()
        }
    }

    fun loadMore() {
        val currentQ = _query.value.trim()
        if (currentQ.isEmpty() || isCurrentlyLoadingMore || _resultState.value !is SearchResultState.Success) return

        isCurrentlyLoadingMore = true
        _isLoadingMore.value = true

        viewModelScope.launch {
            searchPageIndex++
            val suffix = when (searchPageIndex % 4) {
                1 -> "terbaru"
                2 -> "populer"
                3 -> "viral"
                else -> "lengkap"
            }
            val result = repository.search("$currentQ $suffix")
            result.onSuccess { newVideos ->
                val filtered = newVideos.filter { newVid ->
                    newVid.id.isNotEmpty() && searchVideos.none { it.id == newVid.id }
                }
                if (filtered.isNotEmpty()) {
                    searchVideos.addAll(filtered)
                    _resultState.value = SearchResultState.Success(searchVideos.toList())
                }
            }
            _isLoadingMore.value = false
            isCurrentlyLoadingMore = false
        }
    }

    fun removeVideo(videoId: String) {
        searchVideos.removeAll { it.id == videoId }
        val current = _resultState.value
        if (current is SearchResultState.Success) {
            _resultState.value = SearchResultState.Success(searchVideos.toList())
        }
    }

    fun clearQuery() {
        _query.value = ""
        _suggestions.value = emptyList()
        _resultState.value = SearchResultState.Idle
    }
}
