package id.katube.data.auth

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import id.katube.data.model.GoogleAccount
import id.katube.data.preferences.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: id.katube.data.local.history.WatchHistoryDao,
) {
    private object PreferencesKeys {
        val IS_LOGGED_IN = booleanPreferencesKey("auth_is_logged_in")
        val EMAIL = stringPreferencesKey("auth_email")
        val DISPLAY_NAME = stringPreferencesKey("auth_display_name")
        val HANDLE = stringPreferencesKey("auth_handle")
        val AVATAR_URL = stringPreferencesKey("auth_avatar_url")
        val COOKIES = stringPreferencesKey("auth_cookies")
    }

    val account: Flow<GoogleAccount> = context.dataStore.data.map { preferences ->
        val isLoggedIn = preferences[PreferencesKeys.IS_LOGGED_IN] ?: false
        if (isLoggedIn) {
            GoogleAccount(
                email = preferences[PreferencesKeys.EMAIL].orEmpty(),
                displayName = preferences[PreferencesKeys.DISPLAY_NAME].orEmpty().ifEmpty { "Pengguna Google" },
                handle = preferences[PreferencesKeys.HANDLE],
                avatarUrl = preferences[PreferencesKeys.AVATAR_URL],
                cookies = preferences[PreferencesKeys.COOKIES].orEmpty(),
                isLoggedIn = true,
            )
        } else {
            GoogleAccount(isLoggedIn = false)
        }
    }

    val isLoggedIn: Flow<Boolean> = account.map { it.isLoggedIn }

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            account.collect { acc ->
                id.katube.core.network.OkHttpDownloader.currentCookies = if (acc.isLoggedIn) acc.cookies else ""
            }
        }
    }

    suspend fun saveLogin(
        email: String,
        displayName: String,
        handle: String? = null,
        avatarUrl: String? = null,
        cookies: String = "",
    ) {
        if (cookies.isNotBlank()) {
            id.katube.core.network.OkHttpDownloader.currentCookies = cookies
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_LOGGED_IN] = true
            preferences[PreferencesKeys.EMAIL] = email
            preferences[PreferencesKeys.DISPLAY_NAME] = displayName
            if (handle != null) preferences[PreferencesKeys.HANDLE] = handle
            if (avatarUrl != null) preferences[PreferencesKeys.AVATAR_URL] = avatarUrl
            preferences[PreferencesKeys.COOKIES] = cookies
        }
    }

    suspend fun logout() {
        id.katube.core.network.OkHttpDownloader.currentCookies = ""
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_LOGGED_IN] = false
            preferences.remove(PreferencesKeys.EMAIL)
            preferences.remove(PreferencesKeys.DISPLAY_NAME)
            preferences.remove(PreferencesKeys.HANDLE)
            preferences.remove(PreferencesKeys.AVATAR_URL)
            preferences.remove(PreferencesKeys.COOKIES)
        }
        try {
            historyDao.deleteAll()
        } catch (_: Exception) {}
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (ignored: Exception) {}
    }
}
