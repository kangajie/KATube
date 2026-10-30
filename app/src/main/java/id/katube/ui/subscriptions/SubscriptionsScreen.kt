package id.katube.ui.subscriptions

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import id.katube.ui.auth.GoogleLoginActivity
import id.katube.ui.components.VideoCard
import id.katube.ui.navigation.Screen
import id.katube.ui.theme.Gold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onNavigateToSearch: (String) -> Unit = {},
    onVideoClick: (String) -> Unit = {},
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val subscribedChannels by viewModel.subscribedChannels.collectAsState()
    val subscriptionFeed by viewModel.subscriptionFeed.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val gridColumns = when {
        screenWidthDp >= 1200 -> 4
        screenWidthDp >= 900 -> 3
        screenWidthDp >= 600 -> 2
        else -> 1
    }

    val gridState = rememberLazyGridState()

    // Pull-to-refresh setup
    val pullToRefreshState = rememberPullToRefreshState()
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            viewModel.refreshSuspend()
            pullToRefreshState.endRefresh()
        }
    }

    // Auto-sync data saluran langganan saat tab Langganan dibuka
    LaunchedEffect(Unit) {
        if (isLoggedIn) {
            viewModel.sync()
        }
    }

    // Tangani re-tap tab Langganan di Bottom Navigation:
    // Jika tidak di atas, gulir ke atas; jika sudah di atas, refresh konten!
    LaunchedEffect(Unit) {
        mainViewModel.bottomBarReselectEvent.collect { route ->
            if (route == Screen.Subscriptions.route) {
                if (isLoggedIn && gridState.firstVisibleItemIndex > 0) {
                    gridState.animateScrollToItem(0)
                } else {
                    pullToRefreshState.startRefresh()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(pullToRefreshState.nestedScrollConnection),
    ) {
        if (isLoggedIn) {
            // TAMPILAN TERHUBUNG AKUN GOOGLE & YOUTUBE DENGAN GRID ADAPTIF SEMUA PERANGKAT
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
            // Header Baris Saluran Terlanggan
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Saluran Langganan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }

                    if (subscribedChannels.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(subscribedChannels, key = { it.id }) { channel ->
                                Column(
                                    modifier = Modifier
                                        .width(68.dp)
                                        .clickable { onChannelClick(channel.id) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (!channel.avatarUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = channel.avatarUrl,
                                            contentDescription = channel.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = channel.name.firstOrNull()?.uppercase() ?: "S",
                                                fontWeight = FontWeight.Bold,
                                                color = Gold,
                                                fontSize = 20.sp,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = channel.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    } else if (isSyncing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Menyinkronkan saluran YouTube Anda...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Feed Video Terbaru dari Saluran Langganan
            if (subscriptionFeed.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Video Terbaru",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(subscriptionFeed, key = { it.id }) { video ->
                    VideoCard(
                        video = video,
                        onClick = {
                            mainViewModel.playerController.pendingPreviewItem = video
                            onVideoClick(video.id)
                        },
                        onPlayInBackground = {
                            mainViewModel.playerController.playInBackground(video.id)
                        },
                        onPlayInPopup = {
                            mainViewModel.playerController.pendingPreviewItem = video
                            onVideoClick(video.id)
                        },
                        onPlayNext = {
                            mainViewModel.playerController.addToQueueNext(video)
                            android.widget.Toast.makeText(context, "Akan diputar berikutnya", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onAddToQueue = {
                            mainViewModel.playerController.addToQueue(video)
                            android.widget.Toast.makeText(context, "Ditambahkan ke antrean", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            } else if (!isSyncing) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Belum ada video di feed langganan",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Gold,
                                modifier = Modifier.clickable { viewModel.sync() },
                            ) {
                                Text(
                                    text = "Sinkronkan Sekarang",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // TAMPILAN JELAJAH KETIKA BELUM LOGIN (STATELESS)
        StatelessSubscriptionsView(
            onNavigateToSearch = onNavigateToSearch,
            onLoginClick = {
                context.startActivity(Intent(context, GoogleLoginActivity::class.java))
            },
            modifier = Modifier.fillMaxSize(),
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatelessSubscriptionsView(
    onNavigateToSearch: (String) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categories = listOf(
        Triple("Musik", Icons.Default.MusicNote, "Lagu pop, dangdut, akustik & rilis terbaru"),
        Triple("Gaming", Icons.Default.Gamepad, "Live stream & konten gameplay terpopuler"),
        Triple("Berita", Icons.Default.Tv, "Kabar terkini nasional dan internasional"),
        Triple("Hiburan", Icons.Default.Movie, "Komedi, film pendek, dan vlog seru"),
        Triple("Edukasi", Icons.AutoMirrored.Filled.MenuBook, "Sains, teknologi, tutorial dan wawasan baru"),
        Triple("Podcast", Icons.Default.Podcasts, "Obrolan mendalam dan inspiratif"),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
        // Header
        Text(
            text = stringResource(R.string.nav_subscriptions),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Banner Hubungkan Akun Google / YouTube
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onLoginClick),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Gold),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = "Login",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Masuk ke Akun Google / YouTube",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Gold,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Sinkronkan saluran yang Anda subscribe dan lihat video terbarunya di sini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Baris Cari Saluran Cepat
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToSearch("") }
                .padding(vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Cari saluran atau video favorit...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Kategori Pilihan",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            categories.forEach { (title, icon, desc) ->
                CategoryCard(
                    title = title,
                    icon = icon,
                    description = desc,
                    onClick = { onNavigateToSearch(title) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    }
}

@Composable
private fun CategoryCard(
    title: String,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Gold.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
            )
        }
    }
}
