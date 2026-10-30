package id.katube.ui.playlist

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import id.katube.MainActivity
import id.katube.data.model.PlaylistItem
import id.katube.data.model.VideoItem
import id.katube.ui.components.ErrorView
import id.katube.ui.components.VideoCardSkeleton
import id.katube.ui.theme.Gold

@UnstableApi
@Composable
fun PlaylistScreen(
    playlistId: String,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
    mainViewModel: id.katube.MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val state = uiState) {
            is PlaylistUiState.Loading -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(5) {
                        VideoCardSkeleton()
                    }
                }
            }

            is PlaylistUiState.Error -> {
                ErrorView(
                    message = state.message,
                    onRetry = { viewModel.loadPlaylist() },
                )
            }

            is PlaylistUiState.Success -> {
                PlaylistContent(
                    playlist = state.playlist,
                    onPlayAll = {
                        val firstId = viewModel.playAll()
                        if (firstId != null) {
                            mainViewModel.playerController.pendingPreviewItem = state.playlist.videos.firstOrNull()
                            onVideoClick(firstId)
                        }
                    },
                    onVideoClick = { index, videoId ->
                        viewModel.playAt(index)
                        mainViewModel.playerController.pendingPreviewItem = state.playlist.videos.getOrNull(index)
                        onVideoClick(videoId)
                    },
                    onPlayInBackground = { videoId ->
                        viewModel.playInBackground(videoId)
                    },
                    onRemoveVideo = { videoId ->
                        viewModel.removeVideoFromPlaylist(videoId)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistContent(
    playlist: PlaylistItem,
    onPlayAll: () -> Unit,
    onVideoClick: (Int, String) -> Unit,
    onPlayInBackground: (String) -> Unit,
    onRemoveVideo: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedVideoForMenu by remember { mutableStateOf<VideoItem?>(null) }
    val configuration = LocalConfiguration.current
    val isTabletOrWide = configuration.screenWidthDp >= 600

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp)
        ) {
        // Header Playlist
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Banner / Thumbnail Playlist
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isTabletOrWide) 0.5f else 0.85f)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!playlist.thumbnailUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = playlist.thumbnailUrl,
                            contentDescription = playlist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Spacer(modifier = Modifier.height(4.dp))

                val subtitle = listOfNotNull(
                    playlist.uploaderName,
                    "${playlist.videos.size.ifZero(playlist.streamCount.toInt())} video"
                ).joinToString(" • ")

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Tombol Putar Semua (Play All)
                Button(
                    onClick = onPlayAll,
                    enabled = playlist.videos.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = MaterialTheme.colorScheme.background,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .fillMaxWidth()
                        .height(42.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Putar Semua",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (playlist.videos.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Playlist ini masih kosong",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Daftar Video dalam Playlist
        itemsIndexed(
            items = playlist.videos,
            key = { _, item -> item.id.ifEmpty { item.url } }
        ) { index, video ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onVideoClick(index, video.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Nomor urut
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                )

                // Thumbnail mini 16:9
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AsyncImage(
                        model = video.highResThumbnailUrl ?: video.thumbnailUrl,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (video.isLive || video.formattedDuration.equals("LIVE", ignoreCase = true)) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = if (video.watchProgress > 0f) 5.dp else 4.dp, end = 4.dp)
                                .background(androidx.compose.ui.graphics.Color(0xFFCC0000), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(androidx.compose.ui.graphics.Color.White)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "LIVE",
                                    color = androidx.compose.ui.graphics.Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    } else if (video.formattedDuration.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = if (video.watchProgress > 0f) 5.dp else 4.dp, end = 4.dp)
                                .background(androidx.compose.ui.graphics.Color(0xDD000000), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = video.formattedDuration,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // Tanda Pemutaran Terakhir (Progress Bar Kuning Keemasan)
                    if (video.watchProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomStart)
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(video.watchProgress)
                                    .fillMaxHeight()
                                    .background(Gold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Info Video
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = video.uploaderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Tombol Titik 3 (Pilihan Video)
                IconButton(
                    onClick = { selectedVideoForMenu = video },
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

    // Modal Bottom Sheet Pilihan Menu (Sesuai Referensi Tangkapan Layar: 3 Menu Saja)
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
                            onPlayInBackground(targetVideo.id)
                            Toast.makeText(context, "Memutar audio di latar belakang...", Toast.LENGTH_SHORT).show()
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
                            onVideoClick(0, targetVideo.id)
                            (context as? MainActivity)?.enterPipMode()
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
                            onRemoveVideo(targetVideo.id)
                            Toast.makeText(context, "Dihapus dari ${playlist.name}", Toast.LENGTH_SHORT).show()
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

private fun Int.ifZero(defaultVal: Int): Int = if (this == 0) defaultVal else this
