@file:kotlin.OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package id.katube.ui.shorts

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import id.katube.MainViewModel
import id.katube.core.network.OkHttpDownloader
import id.katube.core.util.CommentUtils
import id.katube.core.util.TimeAgoUtils
import id.katube.data.model.CommentItem
import id.katube.data.model.StreamBundle
import id.katube.data.model.VideoItem
import id.katube.data.model.VideoQuality
import id.katube.ui.components.ErrorView
import id.katube.ui.navigation.Screen
import id.katube.ui.theme.Gold

@Composable
fun ShortsScreen(
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShortsViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    val likedShorts by viewModel.likedShorts.collectAsState()
    val unlikedShorts by viewModel.unlikedShorts.collectAsState()
    val dislikedShorts by viewModel.dislikedShorts.collectAsState()
    val unDislikedShorts by viewModel.unDislikedShorts.collectAsState()
    val subscribedSet by viewModel.subscribedSet.collectAsState()
    val unsubscribedSet by viewModel.unsubscribedSet.collectAsState()
    val shortsComments by viewModel.shortsComments.collectAsState()
    val isLoadingComments by viewModel.isLoadingComments.collectAsState()
    var activeCommentsVideoId by remember { mutableStateOf<String?>(null) }

    // Setup Pemutar ExoPlayer khusus Shorts dengan buffering stabil & hemat memori
    val exoPlayer = remember(context) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 8_000,
                /* maxBufferMs = */ 20_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
            }
    }

    val httpDataSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent(OkHttpDownloader.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(15000)
    }
    val dataSourceFactory = remember(context) {
        DefaultDataSource.Factory(context, httpDataSourceFactory)
    }
    val progressiveFactory = remember(dataSourceFactory) {
        ProgressiveMediaSource.Factory(dataSourceFactory)
    }
    val hlsFactory = remember(dataSourceFactory) {
        HlsMediaSource.Factory(dataSourceFactory)
    }

    var isCurrentShortPlaying by remember { mutableStateOf(false) }
    var isCurrentShortBuffering by remember { mutableStateOf(true) }

    val preferredQualitySetting by viewModel.preferredQuality.collectAsState()
    var currentStreamBundle by remember { mutableStateOf<StreamBundle?>(null) }
    var isAutoQuality by remember { mutableStateOf(preferredQualitySetting <= 0) }
    var selectedQualityHeight by remember { mutableIntStateOf(preferredQualitySetting) }
    var activeQualityHeight by remember { mutableIntStateOf(0) }
    var showQualityMenu by remember { mutableStateOf(false) }

    val changeShortQuality: (VideoQuality) -> Unit = { quality ->
        val bundle = currentStreamBundle
        if (bundle != null) {
            val savedPos = exoPlayer.currentPosition
            if (quality.height <= 0) {
                isAutoQuality = true
                selectedQualityHeight = 0
                viewModel.setPreferredQuality(0)
                val autoTargetHeight = id.katube.core.util.NetworkUtils.getOptimalAutoResolution(context)
                val autoStream = bundle.availableQualities
                    .filter { it.height in 1..autoTargetHeight && it.url.isNotEmpty() }
                    .maxByOrNull { it.height }
                    ?: bundle.availableQualities.filter { it.height > 0 && it.url.isNotEmpty() }.minByOrNull { it.height }

                val newVideoUrl = autoStream?.url ?: bundle.selectedVideoUrl
                activeQualityHeight = autoStream?.height ?: bundle.selectedHeight
                val audioUrl = bundle.selectedAudioUrl

                val newMediaSource: MediaSource = if (!newVideoUrl.isNullOrEmpty() && !audioUrl.isNullOrEmpty()) {
                    val videoSource = progressiveFactory.createMediaSource(MediaItem.fromUri(newVideoUrl))
                    val audioSource = progressiveFactory.createMediaSource(MediaItem.fromUri(audioUrl))
                    MergingMediaSource(videoSource, audioSource)
                } else {
                    val targetUri = newVideoUrl ?: audioUrl
                    progressiveFactory.createMediaSource(MediaItem.fromUri(targetUri))
                }

                exoPlayer.setMediaSource(newMediaSource)
                exoPlayer.seekTo(savedPos)
                exoPlayer.prepare()
                exoPlayer.play()
            } else {
                isAutoQuality = false
                selectedQualityHeight = quality.height
                activeQualityHeight = quality.height
                viewModel.setPreferredQuality(quality.height)

                val newVideoUrl = quality.url.ifEmpty { bundle.selectedVideoUrl }
                val audioUrl = bundle.selectedAudioUrl

                val newMediaSource: MediaSource = if (!newVideoUrl.isNullOrEmpty() && !audioUrl.isNullOrEmpty()) {
                    val videoSource = progressiveFactory.createMediaSource(MediaItem.fromUri(newVideoUrl))
                    val audioSource = progressiveFactory.createMediaSource(MediaItem.fromUri(audioUrl))
                    MergingMediaSource(videoSource, audioSource)
                } else {
                    val targetUri = newVideoUrl ?: audioUrl
                    progressiveFactory.createMediaSource(MediaItem.fromUri(targetUri))
                }

                exoPlayer.setMediaSource(newMediaSource)
                exoPlayer.seekTo(savedPos)
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    // Pantau status pemutar Shorts & bersihkan saat keluar dari layar Shorts
    DisposableEffect(exoPlayer) {
        // Jeda pemutaran video utama saat membuka layar Shorts agar tidak terjadi tabrakan suara
        mainViewModel.playerController.pauseIfPlaying()

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isCurrentShortPlaying = isPlaying
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isCurrentShortBuffering = (playbackState == Player.STATE_BUFFERING)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Pull-to-refresh setup
    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            viewModel.refreshSuspend()
            pullToRefreshState.endRefresh()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .nestedScroll(pullToRefreshState.nestedScrollConnection),
    ) {
        when (val state = uiState) {
            is ShortsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Gold)
                }
            }

            is ShortsUiState.Error -> {
                ErrorView(
                    message = state.message,
                    onRetry = { viewModel.refresh() }
                )
            }

            is ShortsUiState.Success -> {
                val pagerState = rememberPagerState(pageCount = { state.shorts.size })

                // Tangani re-tap tab Shorts di Bottom Navigation:
                // Jika sedang tidak di short pertama, gulir ke awal; jika sudah di awal, refresh!
                LaunchedEffect(Unit) {
                    mainViewModel.bottomBarReselectEvent.collect { route ->
                        if (route == Screen.Shorts.route) {
                            if (pagerState.currentPage > 0) {
                                pagerState.animateScrollToPage(0)
                            } else {
                                pullToRefreshState.startRefresh()
                            }
                        }
                    }
                }

                // Putar video Shorts saat ini secara otomatis (Auto-running without clicking play)
                LaunchedEffect(pagerState.currentPage, state.shorts) {
                    val currentShort = state.shorts.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
                    isCurrentShortBuffering = true
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()

                    // Jeda pemutar reguler agar audio tidak bentrok
                    mainViewModel.playerController.pauseIfPlaying()

                    val result = viewModel.getShortStreamInfo(currentShort.id)
                    result.onSuccess { bundle ->
                        currentStreamBundle = bundle

                        // Tentukan stream video berdasarkan preferensi kualitas (Auto atau Kualitas tertentu)
                        val chosenStream: VideoQuality? = if (!isAutoQuality && selectedQualityHeight > 0) {
                            bundle.availableQualities
                                .filter { it.height in 1..selectedQualityHeight && it.url.isNotEmpty() }
                                .maxByOrNull { it.height }
                                ?: bundle.availableQualities.filter { it.height > 0 && it.url.isNotEmpty() }.minByOrNull { it.height }
                        } else {
                            val autoTargetHeight = id.katube.core.util.NetworkUtils.getOptimalAutoResolution(context)
                            bundle.availableQualities
                                .filter { it.height in 1..autoTargetHeight && it.url.isNotEmpty() }
                                .maxByOrNull { it.height }
                        }

                        val videoUrl = chosenStream?.url ?: bundle.selectedVideoUrl
                        activeQualityHeight = chosenStream?.height ?: bundle.selectedHeight
                        val audioUrl = bundle.selectedAudioUrl
                        val isLive = bundle.isLive
                        val hlsUrl = bundle.hlsUrl

                        val mediaSource: MediaSource = if (isLive && !hlsUrl.isNullOrEmpty()) {
                            hlsFactory.createMediaSource(MediaItem.fromUri(hlsUrl))
                        } else if (!videoUrl.isNullOrEmpty() && !audioUrl.isNullOrEmpty()) {
                            val videoSource = progressiveFactory.createMediaSource(MediaItem.fromUri(videoUrl))
                            val audioSource = progressiveFactory.createMediaSource(MediaItem.fromUri(audioUrl))
                            MergingMediaSource(videoSource, audioSource)
                        } else {
                            val targetUri = videoUrl ?: audioUrl
                            progressiveFactory.createMediaSource(MediaItem.fromUri(targetUri))
                        }

                        exoPlayer.playWhenReady = true
                        exoPlayer.setMediaSource(mediaSource)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }.onFailure {
                        isCurrentShortBuffering = false
                    }
                }

                // Infinite scroll tanpa batas: saat mendekati 5 video terakhir, otomatis muat batch Shorts berikutnya
                LaunchedEffect(pagerState.currentPage, state.shorts.size) {
                    val totalPages = state.shorts.size
                    val currentPage = pagerState.currentPage
                    if (totalPages > 0 && currentPage >= totalPages - 5) {
                        viewModel.loadMore()
                    }
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { page ->
                        val item = state.shorts.getOrNull(page)
                        if (item != null) "${item.id}_$page" else page
                    }
                ) { page ->
                    val shortItem = state.shorts[page]
                    val isCurrentPage = page == pagerState.currentPage

                    val isLiked = !unlikedShorts.contains(shortItem.id) && (likedShorts.contains(shortItem.id) || viewModel.isLiked(shortItem.id))
                    val isDisliked = !unDislikedShorts.contains(shortItem.id) && (dislikedShorts.contains(shortItem.id) || viewModel.isDisliked(shortItem.id))
                    val channelKey = shortItem.uploaderUrl ?: shortItem.uploaderName
                    val isSubscribed = !unsubscribedSet.contains(channelKey) && !unsubscribedSet.contains(shortItem.uploaderName) && (subscribedSet.contains(channelKey) || subscribedSet.contains(shortItem.uploaderName) || viewModel.isSubscribed(channelKey))

                    FullShortsPage(
                        item = shortItem,
                        isCurrentPage = isCurrentPage,
                        player = if (isCurrentPage) exoPlayer else null,
                        isPlaying = isCurrentShortPlaying,
                        isBuffering = isCurrentShortBuffering,
                        isLiked = isLiked,
                        isDisliked = isDisliked,
                        isSubscribed = isSubscribed,
                        onTogglePlayPause = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        onLikeClick = {
                            viewModel.toggleLike(shortItem.id) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDislikeClick = {
                            viewModel.toggleDislike(shortItem.id) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSubscribeClick = {
                            viewModel.toggleSubscribe(channelKey, shortItem.uploaderName) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCommentClick = {
                            activeCommentsVideoId = shortItem.id
                            viewModel.loadComments(shortItem.id)
                        },
                        onVideoClick = onVideoClick,
                    )
                }

                // Overlay Bilah Atas Shorts (Judul di Kiri & Pilihan Kualitas Video di Kanan)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Shorts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )

                    // Tombol Kualitas Video Shorts (Badge + Dropdown Menu di Pojok Kanan Atas)
                    val activeHeight = activeQualityHeight
                    val badgeLabel = if (isAutoQuality) {
                        if (activeHeight > 0) "Auto (${activeHeight}p)" else "Auto"
                    } else {
                        if (activeHeight > 0) "${activeHeight}p" else "Auto"
                    }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x77000000),
                            border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showQualityMenu = true },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = "Pilih Kualitas Video",
                                    tint = Gold,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = badgeLabel,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showQualityMenu,
                            onDismissRequest = { showQualityMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            val availableQualities = currentStreamBundle?.availableQualities.orEmpty()
                            if (availableQualities.isNotEmpty()) {
                                availableQualities.forEach { q ->
                                    val isSelected = if (q.height <= 0) {
                                        isAutoQuality
                                    } else {
                                        !isAutoQuality && q.height == activeHeight
                                    }
                                    val itemLabel = if (q.height <= 0) {
                                        if (isSelected && activeHeight > 0) {
                                            "Otomatis (${activeHeight}p)"
                                        } else {
                                            "Otomatis (Rekomendasi)"
                                        }
                                    } else {
                                        q.label
                                    }

                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(
                                                    text = itemLabel,
                                                    color = if (isSelected) Gold else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 14.sp,
                                                )
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Gold,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            showQualityMenu = false
                                            changeShortQuality(q)
                                        }
                                    )
                                }
                            } else {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Memuat kualitas...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                        )
                                    },
                                    onClick = { showQualityMenu = false }
                                )
                            }
                        }
                    }
                }

                // Indikator memuat batch baru di ujung bawah pager
                if (isLoadingMore) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                    ) {
                        CircularProgressIndicator(
                            color = Gold,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }

        // Modal Bottom Sheet Komentar Shorts ala YouTube
        val commentVideoId = activeCommentsVideoId
        if (commentVideoId != null) {
            val comments = shortsComments[commentVideoId].orEmpty()
            val isCommentsLoading = isLoadingComments[commentVideoId] == true
            var newCommentText by remember(commentVideoId) { mutableStateOf("") }
            var commentSortByTop by remember(commentVideoId) { mutableStateOf(true) }

            ModalBottomSheet(
                onDismissRequest = { activeCommentsVideoId = null },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f)
                        .padding(horizontal = 16.dp),
                ) {
                    // Header Komentar: Judul & Tombol Tutup X
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Komentar ${if (comments.isNotEmpty()) "(${comments.size})" else ""}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        IconButton(onClick = { activeCommentsVideoId = null }) {
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
                    if (isCommentsLoading && comments.isEmpty()) {
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
                                text = "Belum ada komentar untuk Shorts ini",
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
                                ShortsCommentRowItem(
                                    comment = comment,
                                    onDelete = {
                                        viewModel.deleteComment(commentVideoId, comment.id) { _, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Bar Input Komentar Bawah: "Tulis komentar publik..."
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                                    val textToSend = newCommentText
                                    newCommentText = ""
                                    viewModel.addComment(commentVideoId, textToSend) { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
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

        // Indikator Pull to Refresh hanya muncul ketika user menarik layar atau sedang refresh
        if (pullToRefreshState.isRefreshing || pullToRefreshState.progress > 0f) {
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = Gold,
            )
        }
    }
}

@Composable
private fun FullShortsPage(
    item: VideoItem,
    isCurrentPage: Boolean,
    player: Player?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isLiked: Boolean,
    isDisliked: Boolean,
    isSubscribed: Boolean,
    onTogglePlayPause: () -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onVideoClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (isWideScreen) {
                        Modifier
                            .fillMaxHeight()
                            .widthIn(max = 500.dp)
                            .clip(RoundedCornerShape(16.dp))
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                .background(Color.Black)
                .pointerInput(isLiked) {
                    detectTapGestures(
                        onTap = { onTogglePlayPause() },
                        onDoubleTap = {
                            if (!isLiked) {
                                onLikeClick()
                            }
                            scope.launch {
                                heartScale.snapTo(0.3f)
                                heartAlpha.snapTo(1f)
                                launch {
                                    heartScale.animateTo(
                                        targetValue = 1.35f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                                launch {
                                    heartAlpha.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 650)
                                    )
                                }
                            }
                        }
                    )
                },
        ) {
        // 1. Thumbnail Dasar (Ditampilkan saat buffering atau saat bukan halaman aktif)
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // 2. Video Player Aktif (Hanya dipasang pada halaman yang sedang aktif)
        if (isCurrentPage && player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        this.player = player
                    }
                },
                update = { view ->
                    view.player = player
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Animasi Pop-up Hati saat Double Tap (Ala YouTube Shorts Asli)
        if (heartAlpha.value > 0.01f) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.Red.copy(alpha = heartAlpha.value),
                modifier = Modifier
                    .size(96.dp)
                    .scale(heartScale.value)
                    .align(Alignment.Center)
            )
        }

        // 3. Gradient Bayangan Atas dan Bawah untuk Kontras Teks
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x99000000),
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xDD000000)
                        )
                    )
                )
        )

        // 4. Indikator Tengah: Buffering Spinner atau Ikon Jeda saat Dijeda Pengguna
        if (isCurrentPage && isBuffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(54.dp)
                    .background(Color(0x66000000), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Gold,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                )
            }
        } else if (isCurrentPage && !isPlaying) {
            // Tampilkan ikon play HANYA ketika video sedang dijeda oleh ketukan pengguna
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(54.dp)
                    .background(Color(0x66000000), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Putar",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        // 5. Kontrol Aksi Vertikal Kanan ala Shorts YouTube
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Tombol Like
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0x55000000), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Suka",
                        tint = if (isLiked) Gold else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isLiked) "Disukai" else item.formattedViews.ifEmpty { "Suka" },
                    color = if (isLiked) Gold else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            // Tombol Dislike
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onDislikeClick,
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0x55000000), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isDisliked) Icons.Default.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Tidak Suka",
                        tint = if (isDisliked) Gold else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Dislike", color = if (isDisliked) Gold else Color.White, fontSize = 11.sp)
            }

            // Tombol Komentar
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onCommentClick,
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0x55000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Comment,
                        contentDescription = "Komentar",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Komentar", color = Color.White, fontSize = 11.sp)
            }

            // Tombol Bagikan
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/shorts/${item.id}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Bagikan Shorts"))
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0x55000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Bagikan",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Bagikan", color = Color.White, fontSize = 11.sp)
            }
        }

        // 6. Info Saluran & Judul di Kiri Bawah
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 76.dp, bottom = 24.dp),
        ) {
            // Avatar & Nama Saluran + Tombol Langganan
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!item.uploaderAvatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = item.uploaderAvatarUrl,
                        contentDescription = item.uploaderName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Gold, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.uploaderName.firstOrNull()?.uppercase() ?: "K",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "@${item.uploaderName}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    onClick = onSubscribeClick,
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSubscribed) Color(0x44FFFFFF) else Gold,
                    contentColor = if (isSubscribed) Color.White else Color.Black,
                    modifier = Modifier.height(30.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (isSubscribed) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Disubscribe",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        } else {
                            Text(
                                text = "Langganan",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Judul Shorts (dapat diklik untuk membuka player reguler)
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                modifier = Modifier.clickable { onVideoClick(item.id) },
            )
        }
    }
    }
}

@Composable
private fun ShortsCommentRowItem(
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

            // Like / Dislike
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ThumbUp,
                        contentDescription = "Suka",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    if (comment.likeCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = comment.formattedLikes,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Outlined.ThumbDown,
                    contentDescription = "Tidak Suka",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
