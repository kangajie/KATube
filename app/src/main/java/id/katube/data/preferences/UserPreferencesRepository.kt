package id.katube.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import coil.Coil
import dagger.hilt.android.qualifiers.ApplicationContext
import id.katube.ui.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "katube_settings")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_QUALITY = intPreferencesKey("default_quality")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val DEFAULT_AUDIO_ONLY = booleanPreferencesKey("default_audio_only")
        val CONTENT_COUNTRY = stringPreferencesKey("content_country")
        val AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
        val SEEK_DURATION = intPreferencesKey("seek_duration")
        val POPUP_QUALITY = intPreferencesKey("popup_quality")
        val CELLULAR_MAX_QUALITY = intPreferencesKey("cellular_max_quality")
        val VIDEO_FORMAT = stringPreferencesKey("video_format")
        val AUDIO_FORMAT = stringPreferencesKey("audio_format")
        val SUBTITLE_MODE = stringPreferencesKey("subtitle_mode")
        val BACKGROUND_PLAY_MODE = stringPreferencesKey("background_play_mode")
        val RESUME_PLAYBACK = booleanPreferencesKey("resume_playback")
        val RESUME_CALL_INTERRUPT = booleanPreferencesKey("resume_call_interrupt")
        val VOLUME_GESTURE = booleanPreferencesKey("volume_gesture")
        val BRIGHTNESS_GESTURE = booleanPreferencesKey("brightness_gesture")
        val FAST_SEEK = booleanPreferencesKey("fast_seek")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val RESTRICTED_MODE = booleanPreferencesKey("restricted_mode")
        val NOTIF_NEW_VIDEOS = booleanPreferencesKey("notif_new_videos")
        val NOTIF_TRENDING = booleanPreferencesKey("notif_trending")
        val NOTIF_BACKGROUND_PLAY = booleanPreferencesKey("notif_background_play")
        val NOTIF_DAILY_RECOMMENDATION = booleanPreferencesKey("notif_daily_recommendation")
        val WATCH_HISTORY_ENABLED = booleanPreferencesKey("watch_history_enabled")
        val SEARCH_HISTORY_ENABLED = booleanPreferencesKey("search_history_enabled")
        val SEARCH_HISTORY_JSON = stringPreferencesKey("search_history_json")
    }

    val searchHistory: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.SEARCH_HISTORY_JSON].orEmpty()
        if (raw.isBlank()) {
            emptyList()
        } else {
            try {
                val array = org.json.JSONArray(raw)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val s = array.optString(i).trim()
                    if (s.isNotEmpty()) list.add(s)
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val seekDuration: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SEEK_DURATION] ?: 10
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(raw)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val defaultQuality: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEFAULT_QUALITY] ?: 0
    }

    val defaultSpeed: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEFAULT_SPEED] ?: 1.0f
    }

    val defaultAudioOnly: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEFAULT_AUDIO_ONLY] ?: false
    }

    val autoplayNext: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AUTOPLAY_NEXT] ?: true
    }

    val contentCountry: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CONTENT_COUNTRY] ?: "ID"
    }

    val popupQuality: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.POPUP_QUALITY] ?: 0
    }

    val cellularMaxQuality: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CELLULAR_MAX_QUALITY] ?: 0
    }

    val videoFormat: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.VIDEO_FORMAT] ?: "WebM"
    }

    val audioFormat: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.AUDIO_FORMAT] ?: "WebM"
    }

    val subtitleMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SUBTITLE_MODE] ?: "Otomatis"
    }

    val backgroundPlayMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BACKGROUND_PLAY_MODE] ?: "Selalu aktif"
    }

    val resumePlayback: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.RESUME_PLAYBACK] ?: true
    }

    val resumeCallInterrupt: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.RESUME_CALL_INTERRUPT] ?: true
    }

    val volumeGesture: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.VOLUME_GESTURE] ?: true
    }

    val brightnessGesture: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BRIGHTNESS_GESTURE] ?: true
    }

    val fastSeek: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FAST_SEEK] ?: false
    }

    val appLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.APP_LANGUAGE] ?: "Bahasa Indonesia"
    }

    val restrictedMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.RESTRICTED_MODE] ?: false
    }

    val notifNewVideos: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NOTIF_NEW_VIDEOS] ?: true
    }

    val notifTrending: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NOTIF_TRENDING] ?: true
    }

    val notifBackgroundPlay: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NOTIF_BACKGROUND_PLAY] ?: true
    }

    val notifDailyRecommendation: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NOTIF_DAILY_RECOMMENDATION] ?: true
    }

    val watchHistoryEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.WATCH_HISTORY_ENABLED] ?: true
    }

    val searchHistoryEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SEARCH_HISTORY_ENABLED] ?: true
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setDefaultQuality(height: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_QUALITY] = height
        }
    }

    suspend fun setPopupQuality(height: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.POPUP_QUALITY] = height
        }
    }

    suspend fun setCellularMaxQuality(height: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CELLULAR_MAX_QUALITY] = height
        }
    }

    suspend fun setVideoFormat(format: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIDEO_FORMAT] = format
        }
    }

    suspend fun setAudioFormat(format: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUDIO_FORMAT] = format
        }
    }

    suspend fun setSubtitleMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SUBTITLE_MODE] = mode
        }
    }

    suspend fun setBackgroundPlayMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKGROUND_PLAY_MODE] = mode
        }
    }

    suspend fun setResumePlayback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RESUME_PLAYBACK] = enabled
        }
    }

    suspend fun setResumeCallInterrupt(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RESUME_CALL_INTERRUPT] = enabled
        }
    }

    suspend fun setVolumeGesture(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VOLUME_GESTURE] = enabled
        }
    }

    suspend fun setBrightnessGesture(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BRIGHTNESS_GESTURE] = enabled
        }
    }

    suspend fun setFastSeek(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FAST_SEEK] = enabled
        }
    }

    suspend fun setAppLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LANGUAGE] = lang
        }
    }

    suspend fun setRestrictedMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RESTRICTED_MODE] = enabled
        }
    }

    suspend fun setNotifNewVideos(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIF_NEW_VIDEOS] = enabled
        }
    }

    suspend fun setNotifTrending(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIF_TRENDING] = enabled
        }
    }

    suspend fun setNotifBackgroundPlay(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIF_BACKGROUND_PLAY] = enabled
        }
    }

    suspend fun setNotifDailyRecommendation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIF_DAILY_RECOMMENDATION] = enabled
        }
    }

    suspend fun setWatchHistoryEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WATCH_HISTORY_ENABLED] = enabled
        }
    }

    suspend fun setSearchHistoryEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEARCH_HISTORY_ENABLED] = enabled
        }
    }

    suspend fun setDefaultSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_SPEED] = speed
        }
    }

    suspend fun setDefaultAudioOnly(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_AUDIO_ONLY] = enabled
        }
    }

    suspend fun setAutoplayNext(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTOPLAY_NEXT] = enabled
        }
    }

    suspend fun setSeekDuration(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEEK_DURATION] = seconds
        }
    }

    suspend fun setContentCountry(countryCode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONTENT_COUNTRY] = countryCode
        }
        try {
            val downloader = NewPipe.getDownloader()
            NewPipe.init(
                downloader,
                Localization("id", countryCode),
                ContentCountry(countryCode)
            )
        } catch (ignored: Exception) {}
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearCache(): Long {
        var freedBytes = 0L
        try {
            val imageLoader = Coil.imageLoader(context)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()

            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                val files = cacheDir.listFiles()
                files?.forEach { file ->
                    // Jangan hapus paksa subfolder http_cache OkHttp saat soket jaringan aktif
                    if (!file.name.equals("http_cache", ignoreCase = true)) {
                        freedBytes += file.length()
                        file.deleteRecursively()
                    }
                }
            }
        } catch (ignored: Exception) {}
        return freedBytes
    }

    suspend fun addSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { preferences ->
            val isEnabled = preferences[PreferencesKeys.SEARCH_HISTORY_ENABLED] ?: true
            if (!isEnabled) return@edit
            val raw = preferences[PreferencesKeys.SEARCH_HISTORY_JSON].orEmpty()
            val currentList = mutableListOf<String>()
            if (raw.isNotBlank()) {
                try {
                    val array = org.json.JSONArray(raw)
                    for (i in 0 until array.length()) {
                        val s = array.optString(i).trim()
                        if (s.isNotEmpty() && !s.equals(trimmed, ignoreCase = true)) {
                            currentList.add(s)
                        }
                    }
                } catch (_: Exception) {}
            }
            // Tambahkan di paling atas (terbaru)
            currentList.add(0, trimmed)
            // Batasi maksimal 40 riwayat pencarian
            val trimmedList = currentList.take(40)
            val newArray = org.json.JSONArray()
            trimmedList.forEach { newArray.put(it) }
            preferences[PreferencesKeys.SEARCH_HISTORY_JSON] = newArray.toString()
        }
    }

    suspend fun removeSearchQuery(query: String) {
        val trimmed = query.trim()
        context.dataStore.edit { preferences ->
            val raw = preferences[PreferencesKeys.SEARCH_HISTORY_JSON].orEmpty()
            if (raw.isBlank()) return@edit
            val currentList = mutableListOf<String>()
            try {
                val array = org.json.JSONArray(raw)
                for (i in 0 until array.length()) {
                    val s = array.optString(i).trim()
                    if (s.isNotEmpty() && !s.equals(trimmed, ignoreCase = true)) {
                        currentList.add(s)
                    }
                }
            } catch (_: Exception) {}
            val newArray = org.json.JSONArray()
            currentList.forEach { newArray.put(it) }
            preferences[PreferencesKeys.SEARCH_HISTORY_JSON] = newArray.toString()
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEARCH_HISTORY_JSON] = "[]"
        }
    }
}
