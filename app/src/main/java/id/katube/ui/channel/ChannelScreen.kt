package id.katube.ui.channel

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import id.katube.MainViewModel
import id.katube.R
import id.katube.data.model.ChannelItem
import id.katube.data.model.PlaylistItem
import id.katube.data.model.VideoItem
import id.katube.ui.components.ErrorView
import id.katube.ui.components.VideoCard
import id.katube.ui.components.VideoCardSkeleton
import id.katube.ui.theme.Gold

@Composable
fun ChannelScreen(
    channelId: String,
    onVideoClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit = {},
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isSubscribed by viewModel.isSubscribed.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchActive by viewModel.isSearchActive.collectAsState()

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val gridColumns = when {
        screenWidthDp >= 1200 -> 4
        screenWidthDp >= 900 -> 3
        screenWidthDp >= 600 -> 2
        else -> 1
    }

    LaunchedEffect(channelId) {
        if (uiState !is ChannelUiState.Success) {
            viewModel.loadChannel()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val state = uiState) {
            is ChannelUiState.Loading -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                ) {
                    items(gridColumns * 4) {
                        VideoCardSkeleton()
                    }
                }
            }

            is ChannelUiState.Error -> {
                ErrorView(
                    message = state.message,
                    onRetry = { viewModel.loadChannel() },
                )
            }

            is ChannelUiState.Success -> {
                ChannelContent(
                    channel = state.channel,
                    isSubscribed = isSubscribed,
                    searchQuery = searchQuery,
                    isSearchActive = isSearchActive,
                    gridColumns = gridColumns,
                    onToggleSubscribe = {
                        viewModel.toggleSubscribe { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onToggleSearch = { viewModel.toggleSearch() },
                    onClearSearch = { viewModel.clearSearch() },
                    onVideoClick = { video ->
                        mainViewModel.playerController.pendingPreviewItem = video
                        onVideoClick(video.id)
                    },
                    onPlaylistClick = onPlaylistClick,
                    onRemoveVideo = { viewModel.removeVideo(it) },
                    mainViewModel = mainViewModel,
                )
            }
        }
    }
}

@Composable
private fun ChannelContent(
    channel: ChannelItem,
    isSubscribed: Boolean,
    searchQuery: String,
    isSearchActive: Boolean,
    gridColumns: Int,
    onToggleSubscribe: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onRemoveVideo: (String) -> Unit = {},
    mainViewModel: MainViewModel,
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Video", "Playlist", "Tentang")

    // Filter daftar video berdasarkan query pencarian channel
    val displayVideos = remember(channel.videos, searchQuery) {
        if (searchQuery.isBlank()) {
            channel.videos
        } else {
            channel.videos.filter {
                it.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Filter daftar playlist berdasarkan query pencarian channel
    val displayPlaylists = remember(channel.playlists, searchQuery) {
        if (searchQuery.isBlank()) {
            channel.playlists
        } else {
            channel.playlists.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // 1. Banner Channel (jika ada)
        if (!channel.bannerUrl.isNullOrEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AsyncImage(
                    model = channel.bannerUrl,
                    contentDescription = "Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3.5f / 1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        // 2. Header Profil Channel
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Avatar 76dp bulat resmi
                val cleanAvatar = remember(channel.avatarUrl) {
                    channel.avatarUrl?.trim()?.let { url ->
                        when {
                            url.startsWith("//") -> "https:$url"
                            url.startsWith("http://") -> "https://" + url.substringAfter("http://")
                            else -> url
                        }
                    }
                }
                if (!cleanAvatar.isNullOrEmpty()) {
                    AsyncImage(
                        model = cleanAvatar,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = channel.name.firstOrNull()?.uppercase() ?: "K",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                if (channel.formattedSubscribers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = channel.formattedSubscribers,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Baris Tombol Aksi: Langganan & Search
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    val subButtonColors = if (isSubscribed) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = MaterialTheme.colorScheme.background,
                        )
                    }

                    Button(
                        onClick = onToggleSubscribe,
                        colors = subButtonColors,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(38.dp),
                    ) {
                        if (isSubscribed) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Disubscribe",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.action_subscribe),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Tombol Search Channel
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (isSearchActive) Gold.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            ),
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Cari di Channel",
                            modifier = Modifier.size(18.dp),
                            tint = if (isSearchActive) Gold else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // 3. Search Bar Interaktif (Muncul saat tombol search aktif)
        if (isSearchActive) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "Cari video atau playlist channel ini...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Gold,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = onClearSearch) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Gold,
                            cursorColor = Gold,
                            focusedLeadingIconColor = Gold,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }
        }

        // 4. Tab Bar (Video, Playlist, Tentang)
        item(span = { GridItemSpan(maxLineSpan) }) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Gold,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Gold,
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == index) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 5. Konten Sesuai Tab
        when (selectedTab) {
            0 -> {
                // TAB 0: VIDEO
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val countText = if (searchQuery.isNotBlank()) {
                        "Ditemukan ${displayVideos.size} video untuk \"$searchQuery\""
                    } else {
                        "${channel.videos.size} video dimuat"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                if (displayVideos.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "Tidak ada video yang cocok dengan \"$searchQuery\""
                                } else {
                                    "Tidak ada video di channel ini."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(
                        items = displayVideos,
                        key = { it.id.ifEmpty { it.url } }
                    ) { video ->
                        val videoWithAvatar = remember(video, channel.avatarUrl) {
                            val channelCleanAvatar = id.katube.core.util.CommentUtils.sanitizeImageUrl(channel.avatarUrl)
                            val finalAvatar = video.uploaderAvatarUrl?.takeIf { it.isNotBlank() } ?: channelCleanAvatar
                            if (!finalAvatar.isNullOrBlank() && video.uploaderAvatarUrl.isNullOrBlank()) {
                                video.copy(uploaderAvatarUrl = finalAvatar)
                            } else {
                                video
                            }
                        }
                        VideoCard(
                            video = videoWithAvatar,
                            onClick = {
                                onVideoClick(video)
                            },
                            onPlayNext = {
                                mainViewModel.playerController.addToQueueNext(video)
                                Toast.makeText(context, "Akan diputar berikutnya", Toast.LENGTH_SHORT).show()
                            },
                            onAddToQueue = {
                                mainViewModel.playerController.addToQueue(video)
                                Toast.makeText(context, "Ditambahkan ke antrean", Toast.LENGTH_SHORT).show()
                            },
                            onPlayInBackground = {
                                mainViewModel.playerController.playInBackground(video.id, preview = video)
                            },
                            onPlayInPopup = {
                                onVideoClick(video)
                                (context as? id.katube.MainActivity)?.enterPipMode()
                            },
                            onAddToPlaylist = {
                                mainViewModel.openAddToPlaylist(video)
                            },
                            onAddToWatchLater = {
                                mainViewModel.addToWatchLater(video) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onNotInterested = {
                                onRemoveVideo(video.id)
                            },
                        )
                    }
                }
            }

            1 -> {
                // TAB 1: PLAYLIST
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val countText = if (searchQuery.isNotBlank()) {
                        "Ditemukan ${displayPlaylists.size} playlist untuk \"$searchQuery\""
                    } else {
                        "${channel.playlists.size} playlist dimuat"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                if (displayPlaylists.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    "Tidak ada playlist yang cocok dengan \"$searchQuery\""
                                } else {
                                    "Tidak ada playlist di channel ini."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(
                        items = displayPlaylists,
                        key = { it.id.ifEmpty { it.url } }
                    ) { playlist ->
                        ChannelPlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id) }
                        )
                    }
                }
            }

            else -> {
                // TAB 2: TENTANG
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Deskripsi Channel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Gold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = channel.description?.takeIf { it.isNotBlank() } ?: "Tidak ada deskripsi tersedia.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                lineHeight = 22.sp,
                            )
                            if (channel.formattedSubscribers.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Jumlah Pelanggan: ${channel.formattedSubscribers}",
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

/**
 * Kartu Playlist khusus di Channel dengan overlay badge jumlah video YouTube style.
 */
@Composable
private fun ChannelPlaylistCard(
    playlist: PlaylistItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!playlist.thumbnailUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Overlay Badge Jumlah Video Khas YouTube Playlist
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (playlist.streamCount > 0) "${playlist.streamCount} video" else "Playlist",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = (playlist.uploaderName ?: "Channel") + " • Playlist",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
