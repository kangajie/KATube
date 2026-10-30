package id.katube.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.ui.graphics.vector.ImageVector
import id.katube.R

/**
 * Definisi rute navigasi aplikasi KATube.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Shorts : Screen("shorts")
    object Subscriptions : Screen("subscriptions")
    object Library : Screen("library")
    object Search : Screen("search")
    object Settings : Screen("settings")

    object Watch : Screen("watch/{videoId}") {
        fun createRoute(videoId: String): String = "watch/$videoId"
    }

    object Channel : Screen("channel/{channelId}") {
        fun createRoute(channelId: String): String {
            val raw = channelId.trim()
            val clean = when {
                raw.contains("youtube.com/channel/") -> raw.substringAfter("youtube.com/channel/").substringBefore("/").substringBefore("?")
                raw.contains("youtube.com/@") -> "@" + raw.substringAfter("youtube.com/@").substringBefore("/").substringBefore("?")
                raw.contains("youtube.com/c/") -> raw.substringAfter("youtube.com/c/").substringBefore("/").substringBefore("?")
                raw.contains("youtube.com/user/") -> raw.substringAfter("youtube.com/user/").substringBefore("/").substringBefore("?")
                raw.contains("/channel/") -> raw.substringAfter("/channel/").substringBefore("/").substringBefore("?")
                raw.contains("/@") -> "@" + raw.substringAfter("/@").substringBefore("/").substringBefore("?")
                raw.startsWith("/") -> raw.trimStart('/')
                else -> raw
            }
            val encoded = try {
                java.net.URLEncoder.encode(clean, "UTF-8")
            } catch (_: Exception) {
                clean
            }
            return "channel/$encoded"
        }
    }

    object Playlist : Screen("playlist/{playlistId}") {
        fun createRoute(playlistId: String): String = "playlist/$playlistId"
    }
}

/**
 * Item untuk Bottom Navigation Bar (Beranda, Shorts, Subscription, Anda).
 */
data class BottomNavItem(
    val screen: Screen,
    val titleRes: Int,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null,
    val selectedDrawableRes: Int? = null,
    val unselectedDrawableRes: Int? = null,
)

val bottomNavItems = listOf(
    BottomNavItem(
        screen = Screen.Home,
        titleRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    BottomNavItem(
        screen = Screen.Shorts,
        titleRes = R.string.nav_shorts,
        selectedDrawableRes = R.drawable.ic_shorts_filled,
        unselectedDrawableRes = R.drawable.ic_shorts,
    ),
    BottomNavItem(
        screen = Screen.Subscriptions,
        titleRes = R.string.nav_subscriptions,
        selectedIcon = Icons.Filled.Subscriptions,
        unselectedIcon = Icons.Outlined.Subscriptions,
    ),
    BottomNavItem(
        screen = Screen.Library,
        titleRes = R.string.nav_you,
        selectedIcon = Icons.Filled.AccountCircle,
        unselectedIcon = Icons.Outlined.AccountCircle,
    ),
)
