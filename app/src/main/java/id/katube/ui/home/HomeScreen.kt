package id.katube.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import id.katube.MainViewModel
import id.katube.ui.components.ChipRow
import id.katube.ui.components.ErrorView
import id.katube.ui.components.SaweriaBannerCard
import id.katube.ui.components.VideoCard
import id.katube.ui.components.VideoCardSkeleton
import id.katube.ui.navigation.Screen
import id.katube.ui.theme.Gold

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun HomeScreen(
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val gridColumns = when {
        screenWidthDp >= 1200 -> 4
        screenWidthDp >= 900 -> 3
        screenWidthDp >= 600 -> 2
        else -> 1
    }

    val gridState = rememberLazyGridState()

    // Pull to refresh setup
    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            viewModel.refreshSuspend()
            gridState.scrollToItem(0)
            pullToRefreshState.endRefresh()
        }
    }

    // Tangani re-tap tab Beranda di Bottom Navigation:
    // Jika tidak di atas, gulir ke atas; jika sudah di atas, refresh konten!
    LaunchedEffect(Unit) {
        mainViewModel.bottomBarReselectEvent.collect { route ->
            if (route == Screen.Home.route) {
                if (gridState.firstVisibleItemIndex > 0) {
                    gridState.animateScrollToItem(0)
                } else {
                    pullToRefreshState.startRefresh()
                }
            }
        }
    }

    // Infinite scroll trigger reaktif dengan snapshotFlow (smart prefetch 4 item sebelum mentok)
    LaunchedEffect(gridState, gridColumns) {
        androidx.compose.runtime.snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - (gridColumns * 4)
        }.collect { shouldLoad ->
            if (shouldLoad && !isLoadingMore) {
                viewModel.loadMore()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Baris Chip Kategori YouTube
        ChipRow(
            categories = viewModel.categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { viewModel.onSelectCategory(it) },
        )

        // Konten Feed Beranda dengan Pull to Refresh
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullToRefreshState.nestedScrollConnection),
        ) {
            when (val state = uiState) {
                is HomeUiState.Loading -> {
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

                is HomeUiState.Error -> {
                    ErrorView(
                        message = state.message,
                        onRetry = { viewModel.refresh() },
                    )
                }

                is HomeUiState.Success -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        // Banner Statis Permanen Dukungan / Iklan Saweria Pengembang (Merentang Penuh)
                        item(key = "static_saweria_banner", span = { GridItemSpan(maxLineSpan) }) {
                            SaweriaBannerCard()
                        }

                        items(
                            items = state.videos,
                            key = { it.id.ifEmpty { it.url } }
                        ) { video ->
                            VideoCard(
                                video = video,
                                onClick = {
                                    mainViewModel.playerController.pendingPreviewItem = video
                                    if (video.isMix && (!video.playlistId.isNullOrEmpty() || video.id.startsWith("RD"))) {
                                        mainViewModel.playMix(video) { targetId ->
                                            onVideoClick(targetId)
                                        }
                                    } else {
                                        onVideoClick(video.id)
                                    }
                                },
                                onPlayInBackground = {
                                    mainViewModel.playerController.playInBackground(video.id, preview = video)
                                },
                                onPlayInPopup = {
                                    mainViewModel.playerController.pendingPreviewItem = video
                                    if (video.isMix && (!video.playlistId.isNullOrEmpty() || video.id.startsWith("RD"))) {
                                        mainViewModel.playMix(video) { targetId ->
                                            onVideoClick(targetId)
                                            (context as? id.katube.MainActivity)?.enterPipMode()
                                        }
                                    } else {
                                        onVideoClick(video.id)
                                        (context as? id.katube.MainActivity)?.enterPipMode()
                                    }
                                },
                                onPlayNext = {
                                    mainViewModel.playerController.addToQueueNext(video)
                                    android.widget.Toast.makeText(context, "Akan diputar berikutnya", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onAddToQueue = {
                                    mainViewModel.playerController.addToQueue(video)
                                    android.widget.Toast.makeText(context, "Ditambahkan ke antrean", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onAddToPlaylist = {
                                    mainViewModel.openAddToPlaylist(video)
                                },
                                onAddToWatchLater = {
                                    mainViewModel.addToWatchLater(video) { _, msg ->
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onNotInterested = {
                                    viewModel.removeVideo(video.id)
                                }
                            )
                        }

                        // Indikator memuat video berikutnya saat scroll tanpa batas (Infinite Scroll)
                        if (isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = Gold,
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp,
                                    )
                                }
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
}
