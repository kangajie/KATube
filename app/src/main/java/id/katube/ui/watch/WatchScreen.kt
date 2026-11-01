package id.katube.ui.watch

import android.os.Build
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import id.katube.MainActivity
import id.katube.R
import id.katube.core.util.CommentUtils
import id.katube.core.util.TimeAgoUtils
import id.katube.data.model.CommentItem
import id.katube.data.model.StreamBundle
import id.katube.data.model.VideoItem
import id.katube.data.model.VideoQuality
import id.katube.player.RepeatMode
import id.katube.ui.components.ErrorView
import id.katube.ui.components.VideoCard
import id.katube.ui.components.VideoCardSkeleton
import id.katube.ui.theme.Gold
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun WatchScreen(
    videoId: String,
    onNavigateBack: () -> Unit,
    onRelatedVideoClick: (String) -> Unit = {},
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WatchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val isLoadingComments by viewModel.isLoadingComments.collectAsState()
    val seekDuration by viewModel.seekDuration.collectAsState()
    val volumeGesture by viewModel.volumeGesture.collectAsState()
    val brightnessGesture by viewModel.brightnessGesture.collectAsState()

    val playerController = viewModel.playerController
    val player by playerController.playerState.collectAsState()
    val isPlaying by playerController.isPlaying.collectAsState()
    val playbackState by playerController.playbackState.collectAsState()
    val currentPositionMs by playerController.currentPositionMs.collectAsState()
    val bufferedPositionMs by playerController.bufferedPositionMs.collectAsState()
    val durationMs by playerController.durationMs.collectAsState()
    val isAudioOnly by playerController.isAudioOnly.collectAsState()
    val playbackSpeed by playerController.playbackSpeed.collectAsState()
    val isAutoplayEnabled by playerController.isAutoplayEnabled.collectAsState()
    val sleepTimerMinutes by playerController.sleepTimerMinutes.collectAsState()
    val repeatMode by playerController.repeatMode.collectAsState()
    val currentStreamBundle by playerController.currentStreamBundle.collectAsState()
    val isAutoQuality by playerController.isAutoQuality.collectAsState()
    val currentPlaylist by viewModel.currentPlaylist.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val isLiked by viewModel.isLiked.collectAsState()
    val isDisliked by viewModel.isDisliked.collectAsState()
    val isSubscribed by viewModel.isSubscribed.collectAsState()
    val uploaderSubscriberText by viewModel.uploaderSubscriberText.collectAsState()
    val channelAvatarUrl by viewModel.channelAvatarUrl.collectAsState()
    val commentTeaser by viewModel.commentTeaser.collectAsState()
    val commentCountText by viewModel.commentCountText.collectAsState()
    val videoViewsText by viewModel.videoViewsText.collectAsState()
    val videoUploadDateText by viewModel.videoUploadDateText.collectAsState()
    val userAccount by viewModel.account.collectAsState()
    val relatedVideos by viewModel.relatedVideos.collectAsState()
    val isLoadingMoreRelated by viewModel.isLoadingMoreRelated.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isInPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        activity?.isInPictureInPictureMode == true
    } else false
    val coroutineScope = rememberCoroutineScope()
    var targetPlaylistVideo by remember { mutableStateOf<VideoItem?>(null) }

    var isPlaylistQueueExpanded by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showPlayerMenuSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSeekDurationSheet by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showRewindRipple by remember { mutableStateOf(false) }
    var showForwardRipple by remember { mutableStateOf(false) }
    val playerDragAnimatable = remember { Animatable(0f) }

    // Jika videoId berganti (misal saat notifikasi rekomendasi diklik), otomatis putar video tersebut
    LaunchedEffect(videoId) {
        playerDragAnimatable.snapTo(0f)
        if (videoId.isNotBlank() && videoId != "ACTIVE_PLAYER") {
            val activePlayingId = playerController.currentStreamBundle.value?.videoId
                ?: playerController.playerState.value?.currentMediaItem?.mediaId
            if (activePlayingId == videoId && viewModel.uiState.value is WatchUiState.Success) {
                // Video ini SUDAH sedang aktif diputar oleh PlayerController! Jangan reload ulang dari awal!
            } else if (viewModel.getCurrentVideoId() != videoId) {
                val preview = playerController.pendingPreviewItem?.takeIf { it.id == videoId }
                playerController.pendingPreviewItem = null
                viewModel.loadVideo(videoId, addToHistory = true, previewItem = preview)
            }
        }
    }

    // State untuk gestur kecerahan layar di lanskap (Sisi Kiri)
    val initialBrightness = remember {
        try {
            val cur = activity?.window?.attributes?.screenBrightness ?: -1f
            if (cur in 0f..1f) cur else {
                val sysBright = android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    128
                )
                (sysBright / 255f).coerceIn(0.05f, 1f)
            }
        } catch (e: Exception) {
            0.5f
        }
    }
    var currentBrightness by remember { mutableFloatStateOf(initialBrightness) }
    var brightnessIndicatorPercent by remember { mutableIntStateOf((initialBrightness * 100).toInt()) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var brightnessHideJob by remember { mutableStateOf<Job?>(null) }

    // State untuk gestur volume media di lanskap (Sisi Kanan)
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVolume by remember {
        mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    var volumeIndicatorPercent by remember {
        mutableIntStateOf((currentVolume * 100 / maxVolume).coerceIn(0, 100))
    }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var volumeHideJob by remember { mutableStateOf<Job?>(null) }
    var volumeDragAccumulator by remember { mutableFloatStateOf(0f) }

    // Jika antrean playlist sedang terbuka, tombol back menciutkannya kembali
    BackHandler(enabled = isPlaylistQueueExpanded) {
        isPlaylistQueueExpanded = false
    }

    // Immersive mode: sembunyikan status bar & navigasi saat mode Lanskap / Layar Penuh
    LaunchedEffect(isLandscape) {
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isLandscape) {
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                val lp = window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                val lp = window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            // Pastikan MiniPlayer selalu aktif saat WatchScreen ditutup
            playerController.showMiniPlayer()
        }
    }

    // Tombol Back saat lanskap: keluar dari layar penuh ke mode portrait (seperti YouTube)
    BackHandler(enabled = isLandscape) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    // Tombol Back fisik/sistem saat potret: navigasi kembali dan ciutkan ke miniplayer (persis YouTube resmi)
    BackHandler(enabled = !isLandscape && !isPlaylistQueueExpanded) {
        onNavigateBack()
    }

    LaunchedEffect(showRewindRipple) {
        if (showRewindRipple) {
            delay(650)
            showRewindRipple = false
        }
    }
    LaunchedEffect(showForwardRipple) {
        if (showForwardRipple) {
            delay(650)
            showForwardRipple = false
        }
    }

    // Auto-hide controls setelah 3.5 detik (hanya saat pemutaran berjalan normal dan tidak sedang buffering)
    LaunchedEffect(showControls, isPlaying, isScreenLocked, playbackState) {
        if (showControls && isPlaying && !isScreenLocked && playbackState != androidx.media3.common.Player.STATE_BUFFERING) {
            delay(3500)
            showControls = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(
                if (!isLandscape && !isInPip) {
                    Modifier
                        .statusBarsPadding()
                        .navigationBarsPadding()
                } else {
                    Modifier
                }
            )
            .offset { IntOffset(0, playerDragAnimatable.value.coerceAtLeast(0f).roundToInt()) }
            .alpha(1f - (playerDragAnimatable.value / 1200f).coerceIn(0f, 0.4f)),
    ) {
        // 1. Area Pemutar Video: Adaptif 16:9 di Portrait & Layar Penuh (FillMaxSize) di Lanskap
        val playerModifier = if (isLandscape || isInPip) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        }

        Box(
            modifier = playerModifier
                .background(Color.Black)
                .draggable(
                    orientation = Orientation.Vertical,
                    enabled = !isScreenLocked && !isLandscape,
                    onDragStarted = { },
                    onDragStopped = { velocity ->
                        coroutineScope.launch {
                            if (velocity > 450f || playerDragAnimatable.value > 90f) {
                                playerDragAnimatable.animateTo(
                                    targetValue = 1000f,
                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                )
                                onNavigateBack()
                            } else {
                                playerDragAnimatable.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    },
                    state = rememberDraggableState { delta ->
                        if (delta > 0 || playerDragAnimatable.value > 0) {
                            coroutineScope.launch {
                                playerDragAnimatable.snapTo((playerDragAnimatable.value + delta).coerceAtLeast(0f))
                            }
                        }
                    }
                )
                .pointerInput(isScreenLocked) {
                    detectTapGestures(
                        onTap = {
                            if (!isScreenLocked) {
                                showControls = !showControls
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!isScreenLocked) {
                                if (offset.x < size.width / 2) {
                                    playerController.seekBackward(seekDuration)
                                    showRewindRipple = true
                                } else {
                                    playerController.seekForward(seekDuration)
                                    showForwardRipple = true
                                }
                            }
                        }
                    )
                },
        ) {
            // PlayerView Media3
            if (!isAudioOnly && player != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                            subtitleView?.apply {
                                setUserDefaultStyle()
                                setUserDefaultTextSize()
                                setFractionalTextSize(0.053f)
                            }
                        }
                    },
                    update = { view ->
                        if (view.player != player) {
                            view.player = player
                        }
                    },
                    modifier = Modifier.matchParentSize(),
                )
            } else if (isAudioOnly) {
                // Tampilan Mode Audio-Only
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Mode Audio Saja Aktif",
                            style = MaterialTheme.typography.titleMedium,
                            color = Gold,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Menghemat kuota & daya baterai",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Tampilan preview seketika saat video baru sedang dimuat (0 ms visual feedback!)
            val loadingPreview = (uiState as? WatchUiState.Loading)?.previewVideo
            if (loadingPreview != null) {
                AsyncImage(
                    model = loadingPreview.thumbnailUrl,
                    contentDescription = loadingPreview.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }

            // Area Interaksi Gestur Layar Penuh (Kecerahan Kiri & Volume Kanan + Ketuk Ganda Mundur/Maju)
            if (isLandscape && !isScreenLocked) {
                Row(modifier = Modifier.matchParentSize()) {
                    // Sisi Kiri: Gestur Kecerahan Layar & Ketuk Ganda Mundur
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .draggable(
                                orientation = Orientation.Vertical,
                                enabled = brightnessGesture,
                                onDragStarted = {
                                    brightnessHideJob?.cancel()
                                    showBrightnessIndicator = true
                                },
                                onDragStopped = {
                                    brightnessHideJob?.cancel()
                                    brightnessHideJob = coroutineScope.launch {
                                        delay(1200)
                                        showBrightnessIndicator = false
                                    }
                                },
                                state = rememberDraggableState { deltaY ->
                                    val change = -deltaY / 400f
                                    val newBrightness = (currentBrightness + change).coerceIn(0.01f, 1.0f)
                                    currentBrightness = newBrightness
                                    brightnessIndicatorPercent = (newBrightness * 100).toInt().coerceIn(1, 100)
                                    activity?.let { act ->
                                        val lp = act.window.attributes
                                        lp.screenBrightness = newBrightness
                                        act.window.attributes = lp
                                    }
                                }
                            )
                            .pointerInput(seekDuration) {
                                detectTapGestures(
                                    onTap = {
                                        showControls = !showControls
                                    },
                                    onDoubleTap = {
                                        playerController.seekBackward(seekDuration)
                                        showRewindRipple = true
                                    }
                                )
                            }
                    )

                    // Sisi Kanan: Gestur Volume Media & Ketuk Ganda Maju
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .draggable(
                                orientation = Orientation.Vertical,
                                enabled = volumeGesture,
                                onDragStarted = {
                                    volumeDragAccumulator = 0f
                                    volumeHideJob?.cancel()
                                    val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    currentVolume = cur
                                    volumeIndicatorPercent = (cur * 100 / maxVolume).coerceIn(0, 100)
                                    showVolumeIndicator = true
                                },
                                onDragStopped = {
                                    volumeHideJob?.cancel()
                                    volumeHideJob = coroutineScope.launch {
                                        delay(1200)
                                        showVolumeIndicator = false
                                    }
                                },
                                state = rememberDraggableState { deltaY ->
                                    volumeDragAccumulator -= deltaY
                                    val pxPerStep = 25f
                                    if (kotlin.math.abs(volumeDragAccumulator) >= pxPerStep) {
                                        val steps = (volumeDragAccumulator / pxPerStep).toInt()
                                        volumeDragAccumulator -= steps * pxPerStep
                                        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        val nextVol = (cur + steps).coerceIn(0, maxVolume)
                                        if (nextVol != cur) {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                            currentVolume = nextVol
                                        }
                                        volumeIndicatorPercent = (nextVol * 100 / maxVolume).coerceIn(0, 100)
                                    }
                                }
                            )
                            .pointerInput(seekDuration) {
                                detectTapGestures(
                                    onTap = {
                                        showControls = !showControls
                                    },
                                    onDoubleTap = {
                                        playerController.seekForward(seekDuration)
                                        showForwardRipple = true
                                    }
                                )
                            }
                    )
                }
            }

            // Animasi Gelombang Ketuk Ganda (Double Tap 10s Rewind / Forward Ala YouTube)
            androidx.compose.animation.AnimatedVisibility(
                visible = showRewindRipple,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.42f)
                    .align(Alignment.CenterStart),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100))
                        .background(Color(0x55FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$seekDuration detik",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showForwardRipple,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.42f)
                    .align(Alignment.CenterEnd),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100))
                        .background(Color(0x55FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$seekDuration detik",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Indikator HUD Kecerahan Layar (Sisi Kiri di Lanskap)
            androidx.compose.animation.AnimatedVisibility(
                visible = showBrightnessIndicator && isLandscape,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp),
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD1A1A1A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.4f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val brightnessIcon = when {
                            brightnessIndicatorPercent > 66 -> Icons.Default.BrightnessHigh
                            brightnessIndicatorPercent > 33 -> Icons.Default.BrightnessMedium
                            else -> Icons.Default.BrightnessLow
                        }
                        Icon(
                            imageVector = brightnessIcon,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0x44FFFFFF)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(currentBrightness.coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Gold),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$brightnessIndicatorPercent%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Indikator HUD Volume Media (Sisi Kanan di Lanskap)
            androidx.compose.animation.AnimatedVisibility(
                visible = showVolumeIndicator && isLandscape,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp),
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD1A1A1A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.4f)),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val volIcon = if (volumeIndicatorPercent == 0) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp
                        Icon(
                            imageVector = volIcon,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0x44FFFFFF)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth((volumeIndicatorPercent / 100f).coerceIn(0f, 1f))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Gold),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$volumeIndicatorPercent%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Layar terkunci
            if (isScreenLocked) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    IconButton(
                        onClick = {
                            isScreenLocked = false
                            Toast.makeText(context, context.getString(R.string.action_screen_unlocked), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x88000000)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Buka Kunci Layar",
                            tint = Gold,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            } else {
                // Overlay Kontrol Pemutar (Sesuai Referensi PDF Halaman 3)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.matchParentSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color(0x80000000))
                            .draggable(
                                orientation = Orientation.Vertical,
                                enabled = !isScreenLocked && !isLandscape,
                                onDragStarted = { },
                                onDragStopped = { velocity ->
                                    coroutineScope.launch {
                                        if (velocity > 450f || playerDragAnimatable.value > 90f) {
                                            playerDragAnimatable.animateTo(
                                                targetValue = 1000f,
                                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                            )
                                            onNavigateBack()
                                        } else {
                                            playerDragAnimatable.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        }
                                    }
                                },
                                state = rememberDraggableState { delta ->
                                    if (delta > 0 || playerDragAnimatable.value > 0) {
                                        coroutineScope.launch {
                                            playerDragAnimatable.snapTo((playerDragAnimatable.value + delta).coerceAtLeast(0f))
                                        }
                                    }
                                }
                            ),
                    ) {
                        // Top Bar Kontrol: Tombol Turun (v), Kualitas Badge ("144p"), Kecepatan ("1x"), Menu Titik 3
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .align(Alignment.TopCenter),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = {
                                if (isLandscape) {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    coroutineScope.launch {
                                        playerDragAnimatable.animateTo(
                                            targetValue = 1000f,
                                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                        )
                                        onNavigateBack()
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = if (isLandscape) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isLandscape) "Kembali ke mode potret" else "Ciutkan",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // Badge Kualitas Video (Contoh: "Auto (480p)", "720p")
                                (currentStreamBundle ?: (uiState as? WatchUiState.Success)?.bundle)?.let { bundle ->
                                    val activeHeight = bundle.selectedHeight
                                    val badgeLabel = if (isAutoQuality) {
                                        if (activeHeight > 0) "Auto (${activeHeight}p)" else "Auto"
                                    } else {
                                        if (activeHeight > 0) "${activeHeight}p" else "Auto"
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x66000000))
                                            .clickable { showQualityMenu = true }
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = badgeLabel,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }

                                // Badge Kecepatan (Contoh: "1x")
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x66000000))
                                        .clickable { showSpeedMenu = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                // Tombol Titik Tiga Pengaturan Player (Buka BottomSheet Sesuai Halaman 3 Referensi)
                                IconButton(onClick = { showPlayerMenuSheet = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Pengaturan Pemutar",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }

                        // Kontrol Tengah Persis Seperti YouTube: [Sebelumnya] [Play / Pause] [Berikutnya]
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 1. Tombol Video Sebelumnya (SkipPrevious)
                            IconButton(
                                onClick = { viewModel.playPreviousVideo() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x55000000)),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Sebelumnya",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }

                            // 2. Tombol Play / Pause Besar Lingkaran / Indikator Buffering
                            val isBuffering = !isPlaying && ((playbackState == androidx.media3.common.Player.STATE_BUFFERING) || (uiState is WatchUiState.Loading))
                            if (isBuffering) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x66000000)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = Gold,
                                        modifier = Modifier.size(36.dp),
                                        strokeWidth = 3.dp,
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { playerController.togglePlayPause() },
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x88000000)),
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Jeda" else "Putar",
                                        tint = Color.White,
                                        modifier = Modifier.size(40.dp),
                                    )
                                }
                            }

                            // 3. Tombol Video Berikutnya (SkipNext)
                            IconButton(
                                onClick = { viewModel.playNextVideo() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x55000000)),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Berikutnya",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }

                        // Bar Bawah: Waktu Berjalan, Seekbar, Durasi Total, Layar Penuh
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${formatMs(currentPositionMs)} / ${formatMs(durationMs)}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )

                                IconButton(
                                    onClick = {
                                        activity?.let { act ->
                                            if (isLandscape) {
                                                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            } else {
                                                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = if (isLandscape) "Keluar Layar Penuh" else "Layar Penuh",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }

                            YouTubePlayerSeekBar(
                                currentPositionMs = currentPositionMs,
                                bufferedPositionMs = bufferedPositionMs,
                                durationMs = durationMs,
                                onSeek = { targetMs -> playerController.seekTo(targetMs) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            // Indikator Buffering Tengah saat kontrol disembunyikan (Persis seperti YouTube asli)
            val isBufferingStandalone = !isPlaying && ((playbackState == androidx.media3.common.Player.STATE_BUFFERING) || (uiState is WatchUiState.Loading))
            if (!showControls && isBufferingStandalone && !isScreenLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Gold,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }

            // Garis Progress Mini YouTube di Dasar Video (Muncul saat kontrol disembunyikan & video memiliki durasi)
            if (!showControls && durationMs > 0L) {
                val playedFraction = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                val bufferedFraction = (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0x33000000)),
                ) {
                    // Garis Buffer Putih-putih yang mendownload source ke depan
                    if (bufferedFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(bufferedFraction)
                                .background(Color(0x99FFFFFF)),
                        )
                    }
                    // Garis Play Emas KATube
                    if (playedFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(playedFraction)
                                .background(Gold),
                        )
                    }
                }
            }

            // Panel Pemilihan Kualitas Video Langsung di Dalam Video Player (Tidak Turun ke Bawah)
            if (showQualityMenu) {
                (currentStreamBundle ?: (uiState as? WatchUiState.Success)?.bundle)?.let { bundle ->
                    val activeHeight = bundle.selectedHeight
                    val qualityList = remember(bundle.availableQualities) {
                        val nonAuto = bundle.availableQualities
                            .filter { it.height > 0 }
                            .distinctBy { it.height }
                            .sortedByDescending { it.height }
                        if (nonAuto.isNotEmpty()) {
                            listOf(VideoQuality(height = 0, label = "Otomatis", url = "", isMuxed = false)) + nonAuto
                        } else {
                            listOf(
                                VideoQuality(height = 0, label = "Otomatis", url = "", isMuxed = false),
                                VideoQuality(height = 1080, label = "1080p", url = "", isMuxed = false),
                                VideoQuality(height = 720, label = "720p", url = "", isMuxed = false),
                                VideoQuality(height = 480, label = "480p", url = "", isMuxed = false),
                                VideoQuality(height = 360, label = "360p", url = "", isMuxed = false),
                                VideoQuality(height = 240, label = "240p", url = "", isMuxed = false),
                                VideoQuality(height = 144, label = "144p", url = "", isMuxed = false),
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showQualityMenu = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {},
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Kualitas Video",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                    IconButton(
                                        onClick = { showQualityMenu = false },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Tutup",
                                            tint = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 190.dp)
                                ) {
                                    items(qualityList) { q ->
                                        val isSelected = if (q.height <= 0) {
                                            isAutoQuality
                                        } else {
                                            !isAutoQuality && (activeHeight == q.height || (activeHeight == 0 && q.height == 480))
                                        }
                                        val itemLabel = if (q.height <= 0) {
                                            if (activeHeight > 0) "Otomatis (${activeHeight}p)" else "Otomatis"
                                        } else {
                                            "${q.height}p"
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    viewModel.selectQuality(q)
                                                    showQualityMenu = false
                                                    Toast.makeText(
                                                        context,
                                                        if (q.height <= 0) "Kualitas disetel ke Otomatis" else "Kualitas disetel ke ${q.height}p",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Gold,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            } else {
                                                Spacer(modifier = Modifier.width(26.dp))
                                            }
                                            Text(
                                                text = itemLabel,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Gold else Color.White,
                                                fontSize = 14.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Panel Pemilihan Kecepatan Pemutaran Langsung di Dalam Video Player
            if (showSpeedMenu) {
                val speedList = remember { listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f) }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showSpeedMenu = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {},
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Kecepatan Pemutaran",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                IconButton(
                                    onClick = { showSpeedMenu = false },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Tutup",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 190.dp)
                            ) {
                                items(speedList) { sp ->
                                    val isSelected = (playbackSpeed == sp)
                                    val label = if (sp == 1.0f) "Normal" else "${sp}x"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.setPlaybackSpeed(sp)
                                                showSpeedMenu = false
                                                Toast.makeText(context, "Kecepatan disetel ke $label", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Gold,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        } else {
                                            Spacer(modifier = Modifier.width(26.dp))
                                        }
                                        Text(
                                            text = label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Gold else Color.White,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Info Video, Komentar, & Daftar Video Terkait (Hanya tampil saat mode Portrait dan bukan PiP)
        if (!isLandscape && !isInPip) {
            // Bar & Antrean Playlist YouTube / Antrean Kustom
            val hasQueue = (currentPlaylist != null && queue.isNotEmpty()) || queue.size > 1
            if (hasQueue) {
                val qName = currentPlaylist?.name ?: "Daftar Antrean"
                val qUploader = currentPlaylist?.uploaderName?.ifBlank { "${queue.size} video" } ?: "${queue.size} video"
                PlaylistQueueBanner(
                    playlistName = qName,
                    uploaderName = qUploader,
                    currentIndex = currentIndex,
                    totalCount = queue.size,
                    isExpanded = isPlaylistQueueExpanded,
                    onToggleExpand = { isPlaylistQueueExpanded = !isPlaylistQueueExpanded },
                )

                if (isPlaylistQueueExpanded) {
                    PlaylistQueuePanel(
                        queue = queue,
                        currentIndex = currentIndex,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        playlistName = qName,
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onToggleRepeat = { viewModel.toggleRepeatMode() },
                        onVideoClick = { index -> viewModel.playQueueIndex(index) },
                        onPlayInBackground = { video ->
                            viewModel.playInBackground(video.id)
                        },
                        onPlayInPopup = { video ->
                            if (video.id == videoId) {
                                (context as? MainActivity)?.enterPipMode()
                            } else {
                                viewModel.loadVideo(video.id)
                                (context as? MainActivity)?.enterPipMode()
                            }
                        },
                        onRemoveVideo = { video ->
                            viewModel.removeVideoFromPlaylist(video)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (!isPlaylistQueueExpanded) {
                when (val state = uiState) {
                    is WatchUiState.Loading -> {
                        val preview = state.previewVideo
                        if (preview != null) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                item {
                                    Text(
                                        text = preview.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${preview.uploaderName} • ${preview.uploadDateText}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                items(3) {
                                    VideoCardSkeleton()
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(3) {
                                    VideoCardSkeleton()
                                }
                            }
                        }
                    }

                    is WatchUiState.Error -> {
                        ErrorView(
                            message = state.message,
                            onRetry = { viewModel.loadVideo() },
                        )
                    }

                    is WatchUiState.Success -> {
                        WatchContent(
                            bundle = state.bundle,
                            comments = comments,
                            isLoadingComments = isLoadingComments,
                            isAudioOnly = isAudioOnly,
                            isAutoplayEnabled = isAutoplayEnabled,
                            isLiked = isLiked,
                            isDisliked = isDisliked,
                            isSubscribed = isSubscribed,
                            subscriberText = uploaderSubscriberText ?: state.bundle.formattedSubscribers,
                            channelAvatarUrl = channelAvatarUrl,
                            commentTeaser = commentTeaser,
                            commentCountText = commentCountText,
                            videoViewsText = videoViewsText,
                            videoUploadDateText = videoUploadDateText,
                            userAccount = userAccount,
                            relatedVideos = relatedVideos,
                            isLoadingMoreRelated = isLoadingMoreRelated,
                            onLikeClick = {
                                viewModel.toggleLike { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDislikeClick = {
                                viewModel.toggleDislike { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onSubscribeClick = {
                                viewModel.toggleSubscribe { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAddToPlaylistClick = {
                                targetPlaylistVideo = null
                                showAddToPlaylistSheet = true
                            },
                            onAddToPlaylistForVideo = { video ->
                                targetPlaylistVideo = video
                                showAddToPlaylistSheet = true
                            },
                            onAddToWatchLaterForVideo = { video ->
                                viewModel.addVideoToPlaylist("WL", "Tonton Nanti", targetVideoId = video.id) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onRemoveRelatedVideo = { video ->
                                viewModel.removeRelatedVideo(video.id)
                            },
                            onLoadMoreRelated = {
                                viewModel.loadMoreRelatedVideos()
                            },
                            onToggleAudioOnly = { viewModel.toggleAudioOnly() },
                            onToggleAutoplay = { viewModel.toggleAutoplay() },
                            onRelatedVideoClick = { relatedVideo ->
                                playerController.pendingPreviewItem = relatedVideo
                                playerController.prepareForNewVideo(relatedVideo.id)
                                onRelatedVideoClick(relatedVideo.id)
                            },
                            onPlayNext = { relatedVideo ->
                                playerController.addToQueueNext(relatedVideo)
                                Toast.makeText(context, "Akan diputar berikutnya", Toast.LENGTH_SHORT).show()
                            },
                            onAddToQueue = { relatedVideo ->
                                playerController.addToQueue(relatedVideo)
                                Toast.makeText(context, "Ditambahkan ke antrean", Toast.LENGTH_SHORT).show()
                            },
                            onPlayInBackground = { relatedId ->
                                playerController.playInBackground(relatedId)
                            },
                            onChannelClick = onChannelClick,
                            onLoadComments = { viewModel.loadComments() },
                            onAddComment = { text ->
                                viewModel.addComment(text) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDeleteComment = { commentId ->
                                viewModel.deleteComment(commentId) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet Menu Pengaturan Player (Sesuai Referensi PDF Halaman 3 Kanan)
    if (showPlayerMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlayerMenuSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                // 1. Kualitas Video
                (currentStreamBundle ?: (uiState as? WatchUiState.Success)?.bundle)?.let { bundle ->
                    val activeHeight = bundle.selectedHeight
                    val qualitySubtitle = if (isAutoQuality) {
                        "Otomatis (${activeHeight}p)"
                    } else {
                        "${activeHeight}p"
                    }
                    PlayerMenuItem(
                        icon = Icons.Default.HighQuality,
                        title = "Kualitas video",
                        subtitle = qualitySubtitle,
                        onClick = {
                            showPlayerMenuSheet = false
                            showControls = true
                            showQualityMenu = true
                        }
                    )
                }

                // 2. Putar Otomatis
                PlayerMenuItem(
                    icon = Icons.Default.PlayArrow,
                    title = "Putar otomatis",
                    subtitle = if (isAutoplayEnabled) "Menghidupkan" else "Mematikan",
                    onClick = {
                        viewModel.toggleAutoplay()
                        Toast.makeText(context, if (!isAutoplayEnabled) "Putar otomatis aktif" else "Putar otomatis mati", Toast.LENGTH_SHORT).show()
                    }
                )

                // 3. Teks / Subtitle
                val currentSubLang by playerController.selectedSubtitleLanguage.collectAsState()
                val activeBundle = currentStreamBundle ?: (uiState as? WatchUiState.Success)?.bundle
                val currentSubTitle = if (currentSubLang == null || currentSubLang == "OFF") {
                    "Tanpa Teks"
                } else {
                    activeBundle?.availableSubtitles?.firstOrNull { it.languageCode.equals(currentSubLang, ignoreCase = true) }?.displayLabel
                        ?: "Aktif ($currentSubLang)"
                }

                PlayerMenuItem(
                    icon = Icons.Default.Subtitles,
                    title = "Teks",
                    subtitle = currentSubTitle,
                    onClick = {
                        showPlayerMenuSheet = false
                        showSubtitleSheet = true
                    }
                )

                // 4. Ulangi (Loop / Repeat)
                PlayerMenuItem(
                    icon = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    title = "Ulangi",
                    subtitle = when (repeatMode) {
                        RepeatMode.OFF -> "Mematikan"
                        RepeatMode.ONE -> "Ulangi Satu"
                        RepeatMode.ALL -> "Ulangi Semua"
                    },
                    onClick = {
                        playerController.toggleRepeatMode()
                    }
                )

                // 5. Alat Penghitung Waktu (Sleep Timer)
                PlayerMenuItem(
                    icon = Icons.Default.Timer,
                    title = "Alat penghitung waktu",
                    subtitle = if (sleepTimerMinutes != null) "$sleepTimerMinutes menit tersisa" else "Nonaktif",
                    onClick = {
                        showPlayerMenuSheet = false
                        showSleepTimerSheet = true
                    }
                )

                // 6. Ketuk dua kali untuk melompat
                PlayerMenuItem(
                    icon = Icons.Default.FastForward,
                    title = "Ketuk dua kali untuk melompat",
                    subtitle = "$seekDuration detik",
                    onClick = {
                        showPlayerMenuSheet = false
                        showSeekDurationSheet = true
                    }
                )
            }
        }
    }



    // Bottom Sheet: Pilihan Durasi Ketuk Dua Kali (Seek Duration)
    if (showSeekDurationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSeekDurationSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "Ketuk dua kali untuk melompat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                val seekOptions = listOf(5, 10, 15, 20, 30, 60)
                seekOptions.forEach { seconds ->
                    val isSelected = seekDuration == seconds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSeekDuration(seconds)
                                showSeekDurationSheet = false
                                Toast.makeText(context, "Ketuk dua kali: $seconds detik", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "$seconds detik" + if (seconds == 10) " (Standar)" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) Gold else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Terpilih",
                                tint = Gold,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Bottom Sheet: Pilihan Pengatur Waktu Tidur (Sleep Timer) Mulai dari 15 Menit dan Kelipatannya
    if (showSleepTimerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSleepTimerSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "Alat penghitung waktu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                // Opsi mulai dari 15 menit dan kelipatannya
                val timerOptions = listOf(
                    0 to "Nonaktif (Matikan pengatur waktu)",
                    15 to "15 Menit",
                    30 to "30 Menit",
                    45 to "45 Menit",
                    60 to "60 Menit (1 Jam)",
                    75 to "75 Menit (1 Jam 15 Menit)",
                    90 to "90 Menit (1,5 Jam)",
                    120 to "120 Menit (2 Jam)",
                )
                timerOptions.forEach { (minutes, label) ->
                    val isSelected = if (minutes == 0) {
                        sleepTimerMinutes == null
                    } else {
                        sleepTimerMinutes != null && sleepTimerMinutes!! > (minutes - 15) && sleepTimerMinutes!! <= minutes
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (minutes == 0) {
                                    playerController.cancelSleepTimer()
                                    Toast.makeText(context, "Timer tidur dinonaktifkan", Toast.LENGTH_SHORT).show()
                                } else {
                                    playerController.setSleepTimer(minutes)
                                    Toast.makeText(context, "Timer tidur diatur ke $minutes menit", Toast.LENGTH_SHORT).show()
                                }
                                showSleepTimerSheet = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) Gold else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Terpilih",
                                tint = Gold,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Bottom Sheet: Pilihan Teks / Subtitle
    if (showSubtitleSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSubtitleSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "Teks (Subtitle)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )

                val selectedSub by playerController.selectedSubtitleLanguage.collectAsState()
                val activeBundle = currentStreamBundle ?: (uiState as? WatchUiState.Success)?.bundle
                val availableSubs = activeBundle?.availableSubtitles.orEmpty()

                // Opsi: Nonaktif / Tanpa Teks
                val isOffSelected = selectedSub == null || selectedSub == "OFF"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            playerController.setSubtitleLanguage("OFF")
                            showSubtitleSheet = false
                            Toast.makeText(context, "Teks dimatikan", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Nonaktif (Tanpa Teks)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isOffSelected) Gold else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (isOffSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Terpilih",
                            tint = Gold,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                if (availableSubs.isEmpty()) {
                    Text(
                        text = "Tidak ada subtitle yang tersedia untuk video ini",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                    ) {
                        items(availableSubs) { sub ->
                            val isSelected = selectedSub != null && selectedSub != "OFF" && selectedSub.equals(sub.languageCode, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerController.setSubtitleLanguage(sub.languageCode)
                                        showSubtitleSheet = false
                                        Toast.makeText(context, "Teks: ${sub.displayLabel}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = sub.displayLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) Gold else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Terpilih",
                                        tint = Gold,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet Tambahkan ke Playlist (Tonton Nanti & Playlist Pengguna)
    if (showAddToPlaylistSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddToPlaylistSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Simpan ke playlist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = { showAddToPlaylistSheet = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                val effectiveTargetId = targetPlaylistVideo?.id ?: videoId
                // 1. Tonton Nanti (Watch Later "WL")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.addVideoToPlaylist("WL", "Tonton Nanti", targetVideoId = effectiveTargetId) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                            showAddToPlaylistSheet = false
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Tonton Nanti",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Playlist default YouTube",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 2. Playlist Akun Pengguna lainnya (dari syncRepository.userPlaylists)
                val customPlaylists = userPlaylists.filter { !it.id.equals("WL", ignoreCase = true) && !it.id.equals("LL", ignoreCase = true) }
                if (customPlaylists.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(customPlaylists, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addVideoToPlaylist(pl.id, pl.name, targetVideoId = effectiveTargetId) { _, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                        showAddToPlaylistSheet = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = pl.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (pl.streamCount > 0) {
                                        Text(
                                            text = "${pl.streamCount} video",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Gold,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchContent(
    bundle: StreamBundle,
    comments: List<CommentItem>,
    isLoadingComments: Boolean,
    isAudioOnly: Boolean,
    isAutoplayEnabled: Boolean,
    isLiked: Boolean,
    isDisliked: Boolean,
    isSubscribed: Boolean,
    subscriberText: String,
    channelAvatarUrl: String? = null,
    commentTeaser: Pair<String, String?>? = null,
    commentCountText: String? = null,
    videoViewsText: String? = null,
    videoUploadDateText: String? = null,
    userAccount: id.katube.data.model.GoogleAccount? = null,
    relatedVideos: List<VideoItem>,
    isLoadingMoreRelated: Boolean,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onAddToPlaylistForVideo: (VideoItem) -> Unit = {},
    onAddToWatchLaterForVideo: (VideoItem) -> Unit = {},
    onRemoveRelatedVideo: (VideoItem) -> Unit = {},
    onLoadMoreRelated: () -> Unit,
    onToggleAudioOnly: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onRelatedVideoClick: (VideoItem) -> Unit,
    onPlayNext: (VideoItem) -> Unit = {},
    onAddToQueue: (VideoItem) -> Unit = {},
    onPlayInBackground: (String) -> Unit = {},
    onChannelClick: (String) -> Unit,
    onLoadComments: () -> Unit = {},
    onAddComment: (String) -> Unit = {},
    onDeleteComment: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var commentSortByTop by remember { mutableStateOf(true) }
    var newCommentText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 900.dp),
        ) {
        // Judul Video & Toggle Deskripsi
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDescriptionExpanded = !isDescriptionExpanded },
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = bundle.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 22.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (isDescriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Detail",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }

                // Metadata: Jumlah Penonton & Waktu Unggah persis seperti YouTube
                val effectiveViews = videoViewsText?.ifBlank { null }
                    ?: if (bundle.formattedViews.isNotEmpty()) "${bundle.formattedViews} ditonton" else ""

                val isReallyLive = bundle.isLive && bundle.durationSeconds <= 0
                val effectiveDate = if (isReallyLive) {
                    "LIVE"
                } else {
                    val parsedDate = videoUploadDateText?.ifBlank { null }
                        ?: run {
                            val raw = bundle.uploadDateText.orEmpty().trim()
                            val cleanDate = if (raw.equals("LIVE", ignoreCase = true) || raw.equals("Sedang Live", ignoreCase = true) || raw.contains("LIVE", ignoreCase = true)) {
                                ""
                            } else raw
                            TimeAgoUtils.format(null, cleanDate).takeIf { it.isNotBlank() }
                        }
                    parsedDate.orEmpty()
                }

                val metaText = listOf(effectiveViews, effectiveDate).filter { it.isNotEmpty() }.joinToString(" • ")
                    .ifEmpty {
                        if (bundle.durationSeconds > 0) "${bundle.durationSeconds / 60}m ${bundle.durationSeconds % 60}s" else ""
                    }

                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Deskripsi Detail Terbuka
                if (isDescriptionExpanded && !bundle.description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = CommentUtils.cleanCommentText(bundle.description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }

        // Baris Aksi Ala Referensi PDF Halaman 3: Suka, Dislike, Playlist, Background, Pop-up, Bagikan
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 1. Suka / Dislike Combined Pill (Persis YouTube Resmi)
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onLikeClick() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Suka",
                            tint = if (isLiked) Gold else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val likeText = if (bundle.likeCount > 0) {
                            val count = if (isLiked) bundle.likeCount + 1 else bundle.likeCount
                            if (count >= 1_000_000) {
                                String.format(java.util.Locale.US, "%.1f jt", count / 1_000_000.0)
                            } else if (count >= 1_000) {
                                String.format(java.util.Locale.US, "%.1f rb", count / 1000.0)
                            } else {
                                "$count"
                            }
                        } else if (bundle.formattedLikes.isNotEmpty()) {
                            bundle.formattedLikes
                        } else {
                            "Suka"
                        }
                        Text(
                            text = likeText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLiked) Gold else MaterialTheme.colorScheme.onBackground,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    Box(
                        modifier = Modifier
                            .clickable { onDislikeClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isDisliked) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Tidak suka",
                            tint = if (isDisliked) Gold else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // 2. Bagikan (Share)
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, bundle.title)
                            putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=${bundle.videoId}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Bagikan video"))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Bagikan",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Bagikan",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // 3. Simpan ke Playlist (Persis YouTube)
                Button(
                    onClick = onAddToPlaylistClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Simpan",
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Simpan",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // 4. Putar di Latar Belakang / Audio Saja
                Button(
                    onClick = onToggleAudioOnly,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAudioOnly) Gold else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isAudioOnly) Color.Black else MaterialTheme.colorScheme.onBackground,
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = "Latar Belakang",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAudioOnly) "Audio Aktif" else "Latar Belakang",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // 5. Pop-up (PiP)
                Button(
                    onClick = {
                        (context as? MainActivity)?.enterPipMode()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = "Pop-up",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pop-up",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Baris Channel: Avatar + Nama + Tombol Langganan (Sesuai Referensi PDF Halaman 4 Kiri)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val target = bundle.uploaderUrl ?: bundle.uploaderName
                            if (target.isNotEmpty()) onChannelClick(target)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val rawChannelAvatar = channelAvatarUrl?.takeIf { it.isNotBlank() } ?: bundle.uploaderAvatarUrl
                    val effectiveAvatarUrl = remember(rawChannelAvatar) {
                        CommentUtils.sanitizeImageUrl(rawChannelAvatar)
                    }
                    if (!effectiveAvatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = effectiveAvatarUrl,
                            contentDescription = bundle.uploaderName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = bundle.uploaderName.firstOrNull()?.uppercase() ?: "K",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = bundle.uploaderName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subscriberText.ifEmpty { bundle.formattedSubscribers },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Tombol Berlangganan Pres Ringkas ala YouTube
                Button(
                    onClick = onSubscribeClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else Gold,
                        contentColor = if (isSubscribed) MaterialTheme.colorScheme.onBackground else Color.Black,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    if (isSubscribed) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Disubscribe",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    } else {
                        Text(
                            text = "Langganan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // 4. Kartu Pratinjau Komentar (Sesuai Referensi PDF Halaman 4 Kiri)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clickable {
                        showCommentsSheet = true
                        if (comments.isEmpty()) {
                            onLoadComments()
                        }
                    },
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val countDisplay = commentCountText?.ifBlank { null }
                            ?: if (comments.isNotEmpty()) "${comments.size}" else ""
                        Text(
                            text = if (countDisplay.isNotEmpty()) "Komentar $countDisplay" else "Komentar",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = "Buka Komentar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (comments.isNotEmpty()) {
                        val topComment = comments.first()
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val cleanTopAvatar = remember(topComment.authorAvatarUrl) {
                                CommentUtils.sanitizeImageUrl(topComment.authorAvatarUrl)
                            }
                            if (!cleanTopAvatar.isNullOrEmpty()) {
                                AsyncImage(
                                    model = cleanTopAvatar,
                                    contentDescription = topComment.authorName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = topComment.displayAuthorName.removePrefix("@").firstOrNull()?.uppercase() ?: "U",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = topComment.displayAuthorName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = CommentUtils.cleanCommentText(topComment.text).ifBlank { "Lihat komentar..." },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    } else if (commentTeaser != null) {
                        val (teaserText, teaserAvatar) = commentTeaser
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val cleanTeaserAvatar = remember(teaserAvatar) {
                                CommentUtils.sanitizeImageUrl(teaserAvatar)
                            }
                            if (!cleanTeaserAvatar.isNullOrEmpty()) {
                                AsyncImage(
                                    model = cleanTeaserAvatar,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = CommentUtils.cleanCommentText(teaserText).ifBlank { "Lihat komentar..." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else if (isLoadingComments) {
                        Text(
                            text = "Memuat komentar...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = "Tambahkan komentar...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Rekomendasi Video Terkait
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Video Terkait",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleAutoplay() }
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    Text(
                        text = "Putar otomatis",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    AutoplayToggle(
                        checked = isAutoplayEnabled,
                        onCheckedChange = onToggleAutoplay,
                    )
                }
            }
        }

        val effectiveRelated = if (relatedVideos.isNotEmpty()) relatedVideos else bundle.relatedVideos
        if (effectiveRelated.isEmpty()) {
            items(3) {
                VideoCardSkeleton()
            }
        } else {
            itemsIndexed(
                items = effectiveRelated,
                key = { index, item -> item.id.ifEmpty { "${item.url}_$index" } }
            ) { index, relatedVideo ->
                if (index >= effectiveRelated.size - 3) {
                    LaunchedEffect(effectiveRelated.size) {
                        onLoadMoreRelated()
                    }
                }
                VideoCard(
                    video = relatedVideo,
                    onClick = { onRelatedVideoClick(relatedVideo) },
                    onPlayNext = {
                        onPlayNext(relatedVideo)
                    },
                    onAddToQueue = {
                        onAddToQueue(relatedVideo)
                    },
                    onPlayInBackground = {
                        onPlayInBackground(relatedVideo.id)
                    },
                    onPlayInPopup = {
                        onRelatedVideoClick(relatedVideo)
                        (context as? MainActivity)?.enterPipMode()
                    },
                    onAddToPlaylist = {
                        onAddToPlaylistForVideo(relatedVideo)
                    },
                    onAddToWatchLater = {
                        onAddToWatchLaterForVideo(relatedVideo)
                    },
                    onNotInterested = {
                        onRemoveRelatedVideo(relatedVideo)
                    },
                )
            }
        }

        if (isLoadingMoreRelated) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Gold,
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Modal Bottom Sheet Komentar Lengkap (Sesuai Referensi PDF Halaman 4 Kanan)
    if (showCommentsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCommentsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                // Header Komentar: Judul & Tombol Tutup X
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val sheetCountDisplay = commentCountText?.ifBlank { null }
                        ?: if (comments.isNotEmpty()) "${comments.size}" else ""
                    Text(
                        text = if (sheetCountDisplay.isNotEmpty()) "Komentar $sheetCountDisplay" else "Komentar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    IconButton(onClick = { showCommentsSheet = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Filter Chip: Teratas & Terbaru
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (commentSortByTop) Gold else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { commentSortByTop = true }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Teratas",
                            color = if (commentSortByTop) Color.Black else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (!commentSortByTop) Gold else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { commentSortByTop = false }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "Terbaru",
                            color = if (!commentSortByTop) Color.Black else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Daftar Komentar
                if (isLoadingComments && comments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Gold)
                    }
                } else if (comments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Belum ada komentar untuk video ini",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    val sortedComments = remember(comments, commentSortByTop) {
                        if (commentSortByTop) {
                            comments.sortedWith(
                                compareByDescending<CommentItem> { it.isUserComment }
                                    .thenByDescending { it.likeCount }
                            )
                        } else {
                            comments.sortedByDescending { it.isUserComment }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 8.dp),
                    ) {
                        items(sortedComments, key = { it.id }) { comment ->
                            CommentRowItem(
                                comment = comment,
                                onDelete = { onDeleteComment(comment.id) }
                            )
                        }
                    }
                }

                // Bar Input Komentar Bawah: "Tulis komentar publik..."
                // Bar Input Komentar Bawah: Avatar Pengguna + "Tulis komentar publik..." + Tombol Kirim
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(vertical = 10.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val avatarUrl = remember(userAccount?.avatarUrl) {
                        CommentUtils.sanitizeImageUrl(userAccount?.avatarUrl)
                    }
                    if (!avatarUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = userAccount?.displayName ?: "Profil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Gold.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = userAccount?.displayName?.firstOrNull()?.uppercase() ?: "A",
                                fontWeight = FontWeight.Bold,
                                color = Gold,
                                fontSize = 13.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    BasicTextField(
                        value = newCommentText,
                        onValueChange = { newCommentText = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = false,
                        maxLines = 4,
                        decorationBox = { innerTextField ->
                            if (newCommentText.isEmpty()) {
                                Text(
                                    text = "Tulis komentar publik...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    )
                    IconButton(
                        onClick = {
                            if (newCommentText.isNotBlank()) {
                                onAddComment(newCommentText)
                                newCommentText = ""
                            }
                        },
                        enabled = newCommentText.isNotBlank(),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Kirim",
                            tint = if (newCommentText.isNotBlank()) Gold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun CommentRowItem(
    comment: CommentItem,
    onDelete: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val cleanCommentAvatar = remember(comment.authorAvatarUrl) {
            CommentUtils.sanitizeImageUrl(comment.authorAvatarUrl)
        }
        if (!cleanCommentAvatar.isNullOrEmpty()) {
            AsyncImage(
                model = cleanCommentAvatar,
                contentDescription = comment.authorName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = comment.displayAuthorName.removePrefix("@").firstOrNull()?.uppercase() ?: "U",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        var isCommentLiked by remember { mutableStateOf(false) }
        var isCommentDisliked by remember { mutableStateOf(false) }
        var likeDelta by remember { mutableIntStateOf(0) }

        Column(modifier = Modifier.weight(1f)) {
            // Author Name & Upload Date & Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.displayAuthorName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!comment.uploadDateText.isNullOrEmpty()) {
                        val formattedCommentDate = TimeAgoUtils.format(null, comment.uploadDateText)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• $formattedCommentDate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }

                if (comment.isUserComment) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Hapus komentar",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Comment text
            Text(
                text = CommentUtils.cleanCommentText(comment.text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Like / Dislike / Reply
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (isCommentLiked) {
                                isCommentLiked = false
                                likeDelta--
                            } else {
                                isCommentLiked = true
                                isCommentDisliked = false
                                likeDelta++
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isCommentLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Suka",
                        tint = if (isCommentLiked) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    val effectiveCount = (comment.likeCount + likeDelta).coerceAtLeast(0)
                    if (effectiveCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (effectiveCount >= 1000) String.format("%.1f rb", effectiveCount / 1000f) else "$effectiveCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCommentLiked) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (isCommentDisliked) {
                                isCommentDisliked = false
                            } else {
                                isCommentDisliked = true
                                if (isCommentLiked) {
                                    isCommentLiked = false
                                    likeDelta--
                                }
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isCommentDisliked) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Tidak Suka",
                        tint = if (isCommentDisliked) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Text(
                    text = "Balas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * Bar Kompak Antrean Playlist persis di bawah player (Sesuai Referensi Tangkapan Layar YouTube).
 * Menampilkan nama playlist, nama channel dinamis pengguna (contoh: "Mr. Aji CHANNEL"), dan nomor urut "1 / 182".
 */
@Composable
private fun PlaylistQueueBanner(
    playlistName: String,
    uploaderName: String,
    currentIndex: Int,
    totalCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlistName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = uploaderName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${currentIndex + 1} / $totalCount",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Tutup antrean" else "Buka antrean",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Panel Detail Antrean Playlist saat diekspansi (Sesuai Referensi Tangkapan Layar YouTube).
 * Memuat tombol Ulangi (Repeat), Acak (Shuffle), dan daftar video playlist lengkap dengan indikator aktif.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistQueuePanel(
    queue: List<id.katube.data.model.VideoItem>,
    currentIndex: Int,
    isShuffleEnabled: Boolean,
    repeatMode: id.katube.player.RepeatMode,
    playlistName: String,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onVideoClick: (Int) -> Unit,
    onPlayInBackground: (id.katube.data.model.VideoItem) -> Unit,
    onPlayInPopup: (id.katube.data.model.VideoItem) -> Unit,
    onRemoveVideo: (id.katube.data.model.VideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedVideoForMenu by remember { mutableStateOf<id.katube.data.model.VideoItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Bar Aksi: Ulangi (Repeat) & Acak (Shuffle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IconButton(
                onClick = onToggleRepeat,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = when (repeatMode) {
                        id.katube.player.RepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = "Ulangi",
                    tint = if (repeatMode != id.katube.player.RepeatMode.OFF) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }

            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Acak",
                    tint = if (isShuffleEnabled) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

        // Daftar Antrean Video Playlist
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(
                items = queue,
                key = { idx, item -> "$idx-${item.id}" },
            ) { idx, item ->
                val isCurrent = idx == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent)
                        .clickable { onVideoClick(idx) }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Indikator Garis Emas di sebelah kiri video aktif
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isCurrent) Gold else Color.Transparent)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Thumbnail 16:9 Mini dengan Durasi
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (item.isLive || item.formattedDuration.equals("LIVE", ignoreCase = true)) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(Color(0xFFCC0000), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "LIVE",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        } else if (item.formattedDuration.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(Color(0xDD000000), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = item.formattedDuration,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCurrent) Gold else MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.uploaderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Tombol Titik 3 (Pilihan Video dalam Antrean)
                    IconButton(
                        onClick = { selectedVideoForMenu = item },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Pilihan",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet Pilihan Menu Antrean (Sesuai Referensi Tangkapan Layar: 3 Menu Saja)
    if (selectedVideoForMenu != null) {
        val targetVideo = selectedVideoForMenu!!
        ModalBottomSheet(
            onDismissRequest = { selectedVideoForMenu = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                // Header Info Video
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = targetVideo.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = targetVideo.uploaderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )

                // Menu 1: Mulai putar di latar belakang
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedVideoForMenu = null
                            onPlayInBackground(targetVideo)
                            android.widget.Toast.makeText(context, "Memutar audio di latar belakang...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Mulai putar di latar belakang",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Menu 2: Mulai putar di popup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedVideoForMenu = null
                            onPlayInPopup(targetVideo)
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPictureAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Mulai putar di popup",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Menu 3: Hapus dari playlist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedVideoForMenu = null
                            onRemoveVideo(targetVideo)
                            android.widget.Toast.makeText(context, "Dihapus dari $playlistName", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Hapus",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Toggle putar otomatis ringkas dan proporsional ala YouTube
 */
@Composable
private fun AutoplayToggle(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 2.dp,
        animationSpec = tween(durationMillis = 180),
        label = "autoplayThumbOffset"
    )

    Box(
        modifier = modifier
            .width(34.dp)
            .height(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (checked) Gold else Color(0x33FFFFFF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCheckedChange
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(14.dp)
                .clip(CircleShape)
                .background(if (checked) Color.Black else Color.White.copy(alpha = 0.8f))
        )
    }
}

/**
 * Bilah penelusuran (Seekbar) pemutar video persis seperti YouTube:
 * 1. Background Track (Garis abu-abu gelap transparan)
 * 2. Buffer Track (Garis putih semi-transparan yang mendownload source sebelum dimulai / berjalan ke depan)
 * 3. Played Track (Garis progress emas KATube yang sedang berjalan)
 * 4. Scrubber Thumb (Titik bulat emas yang membesar secara halus saat disentuh/digeser)
 * Mendukung ketukan instan (tap to seek) dan penggeseran halus (scrubbing).
 */
@Composable
private fun YouTubePlayerSeekBar(
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val playedFraction = if (isScrubbing) {
        scrubFraction
    } else {
        if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    }
    val bufferedFraction = if (durationMs > 0L) (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val trackHeight by animateDpAsState(
        targetValue = if (isScrubbing) 6.dp else 3.5.dp,
        label = "trackHeight"
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (isScrubbing) 7.dp else 5.5.dp,
        label = "thumbRadius"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(durationMs) {
                detectTapGestures(
                    onPress = { offset ->
                        if (durationMs <= 0L) return@detectTapGestures
                        isScrubbing = true
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        scrubFraction = fraction
                        val targetMs = (fraction * durationMs).toLong()
                        onSeek(targetMs)
                        tryAwaitRelease()
                        isScrubbing = false
                    }
                )
            }
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (durationMs <= 0L) return@detectDragGestures
                        isScrubbing = true
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        scrubFraction = fraction
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (durationMs <= 0L) return@detectDragGestures
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        scrubFraction = fraction
                    },
                    onDragEnd = {
                        if (durationMs > 0L) {
                            val targetMs = (scrubFraction * durationMs).toLong()
                            onSeek(targetMs)
                        }
                        isScrubbing = false
                    },
                    onDragCancel = {
                        isScrubbing = false
                    }
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val totalWidth = maxWidth

        // 1. Jalur Background Track (Abu-abu transparan)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(Color(0x44FFFFFF)),
        )

        // 2. Jalur Buffer ("Putih-putihnya" yang mendownload source ke depan sebelum playback dimulai/sampai)
        if (bufferedFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedFraction)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(Color(0x99FFFFFF)),
            )
        }

        // 3. Jalur Progress Playback (Warna Emas KATube)
        if (playedFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(playedFraction)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(Gold),
            )
        }

        // 4. Scrubber Thumb Bulat Emas
        val thumbOffset = (totalWidth * playedFraction) - thumbRadius
        Box(
            modifier = Modifier
                .offset(x = thumbOffset.coerceAtLeast(0.dp))
                .size(thumbRadius * 2)
                .clip(CircleShape)
                .background(Gold)
        )
    }
}

