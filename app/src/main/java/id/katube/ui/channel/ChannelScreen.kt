package id.katube.ui.channel

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import id.katube.MainViewModel
import id.katube.R
import id.katube.data.model.ChannelItem
import id.katube.data.model.VideoItem
import id.katube.ui.components.ErrorView
import id.katube.ui.components.VideoCard
import id.katube.ui.components.VideoCardSkeleton
import id.katube.ui.theme.Gold

@Composable
fun ChannelScreen(
    channelId: String,
    onVideoClick: (String) -> Unit,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isSubscribed by viewModel.isSubscribed.collectAsState()

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val gridColumns = when {
        screenWidthDp >= 1200 -> 4
        screenWidthDp >= 900 -> 3
        screenWidthDp >= 600 -> 2
        else -> 1
    }

    LaunchedEffect(channelId) {
        viewModel.loadChannel()
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
                    contentPadding = PaddingValues(bottom = 24.dp),
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
                    gridColumns = gridColumns,
                    onToggleSubscribe = {
                        viewModel.toggleSubscribe { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onVideoClick = { video ->
                        mainViewModel.playerController.pendingPreviewItem = video
                        onVideoClick(video.id)
                    },
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
    gridColumns: Int,
    onToggleSubscribe: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    mainViewModel: MainViewModel,
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Video", "Tentang")

    LazyVerticalGrid(
        columns = GridCells.Fixed(gridColumns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // Banner Channel (jika ada)
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
        }

        // Header Profil Channel
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Avatar 72dp bulat
                if (!channel.avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = channel.avatarUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = channel.name.firstOrNull()?.uppercase() ?: "K",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
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

                // Tombol Langganan (1:1 YouTube Asli)
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
            }
        }

        // Tab Bar
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
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Konten Tab
        if (selectedTab == 0) {
            // Tab Video
            items(
                items = channel.videos,
                key = { it.id.ifEmpty { it.url } }
            ) { video ->
                VideoCard(
                    video = video,
                    onClick = {
                        onVideoClick(video)
                    },
                    onPlayNext = {
                        mainViewModel.playerController.addToQueueNext(video)
                    },
                    onAddToQueue = {
                        mainViewModel.playerController.addToQueue(video)
                    },
                    onPlayInBackground = {
                        mainViewModel.playerController.playInBackground(video.id)
                    },
                    onPlayInPopup = {
                        onVideoClick(video)
                        (context as? id.katube.MainActivity)?.enterPipMode()
                    },
                )
            }
        } else {
            // Tab Tentang
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
                    }
                }
            }
        }
    }
}
