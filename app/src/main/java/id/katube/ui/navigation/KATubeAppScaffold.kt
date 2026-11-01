package id.katube.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.katube.MainViewModel
import id.katube.R
import id.katube.data.model.StreamBundle
import id.katube.player.PlayerController
import id.katube.ui.channel.ChannelScreen
import id.katube.ui.components.KATubeBottomNav
import id.katube.ui.components.KATubeTopBar
import id.katube.ui.components.MiniPlayer
import id.katube.ui.home.HomeScreen
import id.katube.ui.playlist.PlaylistScreen
import id.katube.ui.search.SearchScreen
import id.katube.ui.settings.SettingsScreen
import id.katube.ui.settings.ThemeMode
import id.katube.ui.shorts.ShortsScreen
import id.katube.ui.subscriptions.SubscriptionsScreen
import id.katube.ui.theme.Gold
import id.katube.ui.watch.WatchScreen
import id.katube.ui.you.YouScreen

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun KATubeAppScaffold(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val playerController = mainViewModel.playerController
    val currentStreamBundle by playerController.currentStreamBundle.collectAsState()
    val isMiniPlayerVisible by playerController.isMiniPlayerVisible.collectAsState()
    val account by mainViewModel.account.collectAsState()
    val isLoggedIn by mainViewModel.isLoggedIn.collectAsState()

    var onTopBarBackAction by remember { mutableStateOf<(() -> Boolean)?>(null) }

    val notifications by mainViewModel.notifications.collectAsState()
    val isLoadingNotifications by mainViewModel.isLoadingNotifications.collectAsState()
    var showNotificationsSheet by remember { mutableStateOf(false) }

    val isWatchScreen = currentRoute?.startsWith("watch/") == true

    // 4 Tab utama yang menampilkan Bottom Navigation & TopBar utama: Beranda, Shorts, Subscriptions, Anda
    val isMainTab = currentRoute in listOf(
        Screen.Home.route,
        Screen.Shorts.route,
        Screen.Subscriptions.route,
        Screen.Library.route
    )

    // Tombol Back pada tab utama: Keluar langsung ke beranda HP tanpa memunculkan pop-up PiP mengambang, pemutar audio tetap berjalan lancar di latar belakang
    val scaffoldContext = androidx.compose.ui.platform.LocalContext.current
    androidx.activity.compose.BackHandler(enabled = isMainTab && currentRoute == Screen.Home.route && currentStreamBundle != null) {
        val act = scaffoldContext as? id.katube.MainActivity
        act?.moveTaskToBack(true)
    }

    val pendingNavVideoId by mainViewModel.pendingNavigationVideoId.collectAsState()

    // Menavigasi langsung ke layar pemutar (WatchScreen) saat notifikasi bar / lock screen diklik
    LaunchedEffect(pendingNavVideoId, currentStreamBundle) {
        val targetId = pendingNavVideoId
        if (!targetId.isNullOrEmpty()) {
            val resolvedId = if (targetId == "ACTIVE_PLAYER") {
                currentStreamBundle?.videoId ?: playerController.currentStreamBundle.value?.videoId ?: playerController.playerState.value?.currentMediaItem?.mediaId
            } else {
                targetId
            }

            if (!resolvedId.isNullOrEmpty()) {
                mainViewModel.consumePendingNavigation()
                val currentWatchId = currentRoute?.removePrefix("watch/")
                if (!isWatchScreen || currentWatchId != resolvedId) {
                    navController.navigate(Screen.Watch.createRoute(resolvedId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    }


    // Tentukan judul untuk TopAppBar
    val topBarTitle = when (currentRoute) {
        Screen.Settings.route -> stringResource(R.string.action_settings)
        Screen.Search.route -> stringResource(R.string.action_search)
        else -> null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (currentRoute != Screen.Search.route && !isWatchScreen && currentRoute != Screen.Shorts.route) {
                KATubeTopBar(
                    canNavigateBack = !isMainTab,
                    title = topBarTitle,
                    isLoggedIn = isLoggedIn,
                    avatarUrl = account.avatarUrl,
                    onNavigateBack = {
                        val handled = onTopBarBackAction?.invoke() ?: false
                        if (!handled) {
                            navController.popBackStack()
                        }
                    },
                    onSearchClick = { navController.navigate(Screen.Search.route) },
                    onNotificationClick = {
                        showNotificationsSheet = true
                        mainViewModel.loadNotifications()
                    },
                    onAccountClick = { navController.navigate(Screen.Library.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                )
            }
        },
        bottomBar = {
            Column {
                // Mini Player mengambang di atas Bottom Nav (sembunyikan saat layar Watch atau Shorts aktif)
                if ((isMiniPlayerVisible || currentStreamBundle != null) && currentStreamBundle != null && !isWatchScreen && currentRoute != Screen.Shorts.route) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiniPlayerHost(
                            playerController = playerController,
                            currentStreamBundle = currentStreamBundle!!,
                            onNavigateToWatch = { videoId ->
                                navController.navigate(Screen.Watch.createRoute(videoId)) {
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier.widthIn(max = 680.dp),
                        )
                    }
                }

                if (isMainTab) {
                    KATubeBottomNav(
                        currentRoute = currentRoute,
                        avatarUrl = account.avatarUrl,
                        isLoggedIn = isLoggedIn,
                        onNavigateToRoute = { route ->
                            navController.navigate(route) {
                                popUpTo(Screen.Home.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onReselectRoute = { route ->
                            mainViewModel.onBottomBarReselect(route)
                        }
                    )
                } else if (!isWatchScreen && currentRoute != Screen.Shorts.route) {
                    // Ketika di luar tab utama (seperti Search, Channel, Settings),
                    // selalu sediakan spacer navigasi bawah dengan warna background aplikasi agar tidak transparan
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Spacer(modifier = Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onVideoClick = { videoId ->
                        val qIdx = mainViewModel.playerController.queue.value.indexOfFirst { it.id == videoId }
                        if (qIdx >= 0) mainViewModel.playerController.setCurrentIndex(qIdx)
                        mainViewModel.playerController.prepareForNewVideo(videoId)
                        navController.navigate(Screen.Watch.createRoute(videoId)) {
                            popUpTo(Screen.Watch.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    mainViewModel = mainViewModel,
                )
            }
            composable(Screen.Shorts.route) {
                ShortsScreen(
                    onVideoClick = { videoId ->
                        val qIdx = mainViewModel.playerController.queue.value.indexOfFirst { it.id == videoId }
                        if (qIdx >= 0) mainViewModel.playerController.setCurrentIndex(qIdx)
                        mainViewModel.playerController.prepareForNewVideo(videoId)
                        navController.navigate(Screen.Watch.createRoute(videoId)) {
                            popUpTo(Screen.Watch.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    mainViewModel = mainViewModel,
                )
            }
            composable(Screen.Subscriptions.route) {
                SubscriptionsScreen(
                    onNavigateToSearch = { _ ->
                        navController.navigate(Screen.Search.route)
                    },
                    onVideoClick = { videoId ->
                        val qIdx = mainViewModel.playerController.queue.value.indexOfFirst { it.id == videoId }
                        if (qIdx >= 0) mainViewModel.playerController.setCurrentIndex(qIdx)
                        mainViewModel.playerController.prepareForNewVideo(videoId)
                        navController.navigate(Screen.Watch.createRoute(videoId)) {
                            popUpTo(Screen.Watch.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onChannelClick = { channelId ->
                        navController.navigate(Screen.Channel.createRoute(channelId))
                    },
                    mainViewModel = mainViewModel,
                )
            }
            composable(Screen.Library.route) {
                YouScreen(
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onVideoClick = { videoId ->
                        val qIdx = mainViewModel.playerController.queue.value.indexOfFirst { it.id == videoId }
                        if (qIdx >= 0) mainViewModel.playerController.setCurrentIndex(qIdx)
                        mainViewModel.playerController.prepareForNewVideo(videoId)
                        navController.navigate(Screen.Watch.createRoute(videoId)) {
                            popUpTo(Screen.Watch.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onPlaylistClick = { playlistId ->
                        navController.navigate(Screen.Playlist.createRoute(playlistId))
                    },
                    mainViewModel = mainViewModel,
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onVideoClick = { videoId ->
                        val qIdx = mainViewModel.playerController.queue.value.indexOfFirst { it.id == videoId }
                        if (qIdx >= 0) mainViewModel.playerController.setCurrentIndex(qIdx)
                        mainViewModel.playerController.prepareForNewVideo(videoId)
                        navController.navigate(Screen.Watch.createRoute(videoId)) {
                            popUpTo(Screen.Watch.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = onThemeModeChange,
                    onNavigateBack = { navController.popBackStack() },
                    onInterceptBack = { handler -> onTopBarBackAction = handler },
                )
            }
            composable(
                route = Screen.Watch.route,
                arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
                enterTransition = {
                    slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing))
                },
                popEnterTransition = {
                    slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing))
                }
            ) { backStackEntry ->
                val videoId = backStackEntry.arguments?.getString("videoId").orEmpty()
                WatchScreen(
                    videoId = videoId,
                    onNavigateBack = {
                        mainViewModel.playerController.showMiniPlayer()
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.Home.route)
                        }
                    },
                    onRelatedVideoClick = { relatedId ->
                        val qIdx = mainViewModel.playerController.queue.value.indexOfFirst { it.id == relatedId }
                        if (qIdx >= 0) mainViewModel.playerController.setCurrentIndex(qIdx)
                        mainViewModel.playerController.prepareForNewVideo(relatedId)
                        navController.navigate(Screen.Watch.createRoute(relatedId)) {
                            popUpTo(Screen.Watch.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onChannelClick = { channelId ->
                        mainViewModel.playerController.showMiniPlayer()
                        navController.navigate(Screen.Channel.createRoute(channelId))
                    }
                )
            }
            composable(
                route = Screen.Channel.route,
                arguments = listOf(navArgument("channelId") { type = NavType.StringType })
            ) { backStackEntry ->
                val rawChannelId = backStackEntry.arguments?.getString("channelId").orEmpty()
                val channelId = try {
                    java.net.URLDecoder.decode(rawChannelId, "UTF-8")
                } catch (_: Exception) {
                    rawChannelId
                }
                ChannelScreen(
                    channelId = channelId,
                    onBackClick = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.Home.route)
                        }
                    },
                    onVideoClick = { clickedVideoId ->
                        val qIdx = mainViewModel.playerController.queue.value.indexOfFirst { it.id == clickedVideoId }
                        if (qIdx >= 0) mainViewModel.playerController.setCurrentIndex(qIdx)
                        mainViewModel.playerController.prepareForNewVideo(clickedVideoId)
                        navController.navigate(Screen.Watch.createRoute(clickedVideoId)) {
                            popUpTo(Screen.Watch.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onPlaylistClick = { playlistId ->
                        navController.navigate(Screen.Playlist.createRoute(playlistId))
                    }
                )
            }
            composable(
                route = Screen.Playlist.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId").orEmpty()
                PlaylistScreen(
                    playlistId = playlistId,
                    onVideoClick = { clickedVideoId ->
                        mainViewModel.playerController.prepareForNewVideo(clickedVideoId)
                        navController.navigate(Screen.Watch.createRoute(clickedVideoId)) {
                            popUpTo(Screen.Watch.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }

    if (showNotificationsSheet) {
        val context = androidx.compose.ui.platform.LocalContext.current
        ModalBottomSheet(
            onDismissRequest = { showNotificationsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notifikasi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { showNotificationsSheet = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isLoggedIn) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Gold,
                                modifier = Modifier.size(52.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Masuk dengan Akun Google",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Masuk untuk melihat notifikasi video terbaru dari channel yang Anda subscribe di YouTube.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.Button(
                                onClick = {
                                    showNotificationsSheet = false
                                    context.startActivity(android.content.Intent(context, id.katube.ui.auth.GoogleLoginActivity::class.java))
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Gold,
                                    contentColor = Color.Black,
                                ),
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Text("Masuk Sekarang", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (isLoadingNotifications) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = Gold,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                } else if (notifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Belum ada notifikasi baru",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    ) {
                        items(notifications) { notif ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!notif.videoId.isNullOrEmpty()) {
                                            showNotificationsSheet = false
                                            val qIdx = mainViewModel.playerController.queue.value.indexOfFirst { it.id == notif.videoId }
                                            if (qIdx >= 0) mainViewModel.playerController.setCurrentIndex(qIdx)
                                            mainViewModel.playerController.prepareForNewVideo(notif.videoId)
                                            navController.navigate(Screen.Watch.createRoute(notif.videoId)) {
                                                popUpTo(Screen.Watch.route) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!notif.avatarUrl.isNullOrEmpty()) {
                                    coil.compose.AsyncImage(
                                        model = notif.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Gold),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = notif.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (notif.timeText.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = notif.timeText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                if (!notif.thumbnailUrl.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.width(10.dp))
                                    coil.compose.AsyncImage(
                                        model = notif.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(width = 64.dp, height = 36.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                }
                            }
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    val targetVideoForPlaylist by mainViewModel.targetVideoForPlaylist.collectAsState()
    val userPlaylists by mainViewModel.userPlaylists.collectAsState()

    if (targetVideoForPlaylist != null) {
        val targetVideo = targetVideoForPlaylist!!
        id.katube.ui.components.AddToPlaylistSheet(
            video = targetVideo,
            isLoggedIn = isLoggedIn,
            playlists = userPlaylists,
            onDismiss = { mainViewModel.closeAddToPlaylist() },
            onSelectPlaylist = { plId, plName ->
                mainViewModel.addVideoToPlaylist(plId, plName, targetVideo) { _, msg ->
                    android.widget.Toast.makeText(scaffoldContext, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
                mainViewModel.closeAddToPlaylist()
            },
            onNavigateToLogin = {
                navController.navigate(Screen.Library.route)
            }
        )
    }
}

/**
 * Host terisolasi khusus untuk MiniPlayer.
 * Mengisolasi pengumpulan state berfrekuensi tinggi (currentPositionMs, durationMs, bufferedPositionMs, playbackState)
 * agar root KATubeAppScaffold dan NavHost TIDAK ikut ter-recompose setiap 200 ms saat video berjalan!
 */
@Composable
private fun MiniPlayerHost(
    playerController: PlayerController,
    currentStreamBundle: StreamBundle,
    onNavigateToWatch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val player by playerController.playerState.collectAsState()
    val isAudioOnly by playerController.isAudioOnly.collectAsState()
    val isPlaying by playerController.isPlaying.collectAsState()
    val playbackState by playerController.playbackState.collectAsState()
    val currentPositionMs by playerController.currentPositionMs.collectAsState()
    val durationMs by playerController.durationMs.collectAsState()
    val bufferedPositionMs by playerController.bufferedPositionMs.collectAsState()

    MiniPlayer(
        bundle = currentStreamBundle,
        player = player,
        isAudioOnly = isAudioOnly,
        isPlaying = isPlaying,
        isBuffering = !isPlaying && (playbackState == androidx.media3.common.Player.STATE_BUFFERING),
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        bufferedPositionMs = bufferedPositionMs,
        onPrevious = { playerController.playPreviousInQueue() },
        onNext = {
            if (playerController.queue.value.isNotEmpty()) {
                playerController.playNextInQueue()
            } else {
                playerController.playAutoplayNext()
            }
        },
        onTogglePlay = { playerController.togglePlayPause() },
        onClose = { playerController.dismissMiniPlayer() },
        onClick = { onNavigateToWatch(currentStreamBundle.videoId) },
        modifier = modifier,
    )
}
