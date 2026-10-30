package id.katube.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.katube.data.local.history.WatchHistoryDao
import id.katube.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val historyDao: WatchHistoryDao,
    private val recommendationManager: id.katube.data.notification.RecommendationNotificationManager,
) : ViewModel() {


    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val defaultQuality: StateFlow<Int> = preferencesRepository.defaultQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val defaultSpeed: StateFlow<Float> = preferencesRepository.defaultSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val defaultAudioOnly: StateFlow<Boolean> = preferencesRepository.defaultAudioOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoplayNext: StateFlow<Boolean> = preferencesRepository.autoplayNext
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val contentCountry: StateFlow<String> = preferencesRepository.contentCountry
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ID")

    val seekDuration: StateFlow<Int> = preferencesRepository.seekDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)

    val popupQuality: StateFlow<Int> = preferencesRepository.popupQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cellularMaxQuality: StateFlow<Int> = preferencesRepository.cellularMaxQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val videoFormat: StateFlow<String> = preferencesRepository.videoFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "WebM")

    val audioFormat: StateFlow<String> = preferencesRepository.audioFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "WebM")

    val subtitleMode: StateFlow<String> = preferencesRepository.subtitleMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Otomatis")

    val backgroundPlayMode: StateFlow<String> = preferencesRepository.backgroundPlayMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Selalu aktif")

    val resumePlayback: StateFlow<Boolean> = preferencesRepository.resumePlayback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val resumeCallInterrupt: StateFlow<Boolean> = preferencesRepository.resumeCallInterrupt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val volumeGesture: StateFlow<Boolean> = preferencesRepository.volumeGesture
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val brightnessGesture: StateFlow<Boolean> = preferencesRepository.brightnessGesture
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val fastSeek: StateFlow<Boolean> = preferencesRepository.fastSeek
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appLanguage: StateFlow<String> = preferencesRepository.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Bahasa Indonesia")

    val restrictedMode: StateFlow<Boolean> = preferencesRepository.restrictedMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val notifNewVideos: StateFlow<Boolean> = preferencesRepository.notifNewVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notifTrending: StateFlow<Boolean> = preferencesRepository.notifTrending
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notifBackgroundPlay: StateFlow<Boolean> = preferencesRepository.notifBackgroundPlay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val notifDailyRecommendation: StateFlow<Boolean> = preferencesRepository.notifDailyRecommendation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val watchHistoryEnabled: StateFlow<Boolean> = preferencesRepository.watchHistoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val searchHistoryEnabled: StateFlow<Boolean> = preferencesRepository.searchHistoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setDefaultQuality(height: Int) {
        viewModelScope.launch {
            preferencesRepository.setDefaultQuality(height)
        }
    }

    fun setPopupQuality(height: Int) {
        viewModelScope.launch {
            preferencesRepository.setPopupQuality(height)
        }
    }

    fun setCellularMaxQuality(height: Int) {
        viewModelScope.launch {
            preferencesRepository.setCellularMaxQuality(height)
        }
    }

    fun setVideoFormat(format: String) {
        viewModelScope.launch {
            preferencesRepository.setVideoFormat(format)
        }
    }

    fun setAudioFormat(format: String) {
        viewModelScope.launch {
            preferencesRepository.setAudioFormat(format)
        }
    }

    fun setSubtitleMode(mode: String) {
        viewModelScope.launch {
            preferencesRepository.setSubtitleMode(mode)
        }
    }

    fun setBackgroundPlayMode(mode: String) {
        viewModelScope.launch {
            preferencesRepository.setBackgroundPlayMode(mode)
        }
    }

    fun setResumePlayback(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setResumePlayback(enabled)
        }
    }

    fun setResumeCallInterrupt(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setResumeCallInterrupt(enabled)
        }
    }

    fun setVolumeGesture(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setVolumeGesture(enabled)
        }
    }

    fun setBrightnessGesture(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBrightnessGesture(enabled)
        }
    }

    fun setFastSeek(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setFastSeek(enabled)
        }
    }

    fun setAppLanguage(lang: String) {
        viewModelScope.launch {
            preferencesRepository.setAppLanguage(lang)
        }
    }

    fun setRestrictedMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setRestrictedMode(enabled)
        }
    }

    fun setNotifNewVideos(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotifNewVideos(enabled)
        }
    }

    fun setNotifTrending(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotifTrending(enabled)
        }
    }

    fun setNotifBackgroundPlay(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotifBackgroundPlay(enabled)
        }
    }

    fun setNotifDailyRecommendation(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotifDailyRecommendation(enabled)
            if (enabled) {
                recommendationManager.scheduleDailyRecommendations()
            } else {
                recommendationManager.cancelDailyRecommendations()
            }
        }
    }

    fun setWatchHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setWatchHistoryEnabled(enabled)
        }
    }

    fun setSearchHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSearchHistoryEnabled(enabled)
        }
    }

    fun setDefaultSpeed(speed: Float) {
        viewModelScope.launch {
            preferencesRepository.setDefaultSpeed(speed)
        }
    }

    fun setDefaultAudioOnly(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDefaultAudioOnly(enabled)
        }
    }

    fun setAutoplayNext(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoplayNext(enabled)
        }
    }

    fun setContentCountry(countryCode: String) {
        viewModelScope.launch {
            preferencesRepository.setContentCountry(countryCode)
        }
    }

    fun setSeekDuration(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setSeekDuration(seconds)
        }
    }

    fun clearCache(onCleared: (Long) -> Unit) {
        viewModelScope.launch {
            val freedBytes = preferencesRepository.clearCache()
            onCleared(freedBytes)
        }
    }

    fun clearWatchHistory(onCleared: () -> Unit) {
        viewModelScope.launch {
            historyDao.deleteAll()
            onCleared()
        }
    }
}
