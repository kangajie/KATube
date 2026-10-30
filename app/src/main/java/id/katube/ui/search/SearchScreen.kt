package id.katube.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import id.katube.R
import id.katube.ui.components.ErrorView
import id.katube.ui.components.VideoCard
import id.katube.ui.components.VideoCardSkeleton

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalConfiguration
import id.katube.ui.theme.Gold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    mainViewModel: id.katube.MainViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val resultState by viewModel.resultState.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val gridColumns = when {
        screenWidthDp >= 1200 -> 4
        screenWidthDp >= 900 -> 3
        screenWidthDp >= 600 -> 2
        else -> 1
    }

    val searchGridState = rememberLazyGridState()

    // Infinite scroll trigger untuk pencarian
    val shouldLoadMore by remember(searchGridState, gridColumns) {
        derivedStateOf {
            val totalItems = searchGridState.layoutInfo.totalItemsCount
            val lastVisibleItem = searchGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - (gridColumns * 2)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Search Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }

            TextField(
                value = query,
                onValueChange = { viewModel.onQueryChange(it) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        viewModel.search(query)
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
            )

            if (query.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearQuery() }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Konten: Saran Autocomplete ATAU Hasil Pencarian
        Box(modifier = Modifier.fillMaxSize()) {
            if (suggestions.isNotEmpty() && resultState is SearchResultState.Idle) {
                // Tampilkan Daftar Saran Autocomplete YouTube
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(suggestions) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    keyboardController?.hide()
                                    viewModel.search(suggestion)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { viewModel.onQueryChange(suggestion) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.NorthWest,
                                    contentDescription = "Gunakan kata kunci",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                when (val state = resultState) {
                    is SearchResultState.Idle -> {
                        // Layar awal: Tampilkan Riwayat Pencarian jika ada
                        if (searchHistory.isNotEmpty()) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Riwayat penelusuran",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "Hapus semua",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Gold,
                                            modifier = Modifier
                                                .clickable { viewModel.clearAllHistory() }
                                                .padding(4.dp),
                                        )
                                    }
                                }

                                items(
                                    items = searchHistory,
                                    key = { it }
                                ) { historyItem ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                keyboardController?.hide()
                                                viewModel.search(historyItem)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "Riwayat",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = historyItem,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(
                                            onClick = { viewModel.onQueryChange(historyItem) },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.NorthWest,
                                                contentDescription = "Gunakan kata kunci",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.deleteHistoryItem(historyItem) },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Hapus",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(56.dp),
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Telusuri video di KATube",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    is SearchResultState.Loading -> {
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

                    is SearchResultState.Error -> {
                        ErrorView(
                            message = state.message,
                            onRetry = { viewModel.search(query) },
                        )
                    }

                    is SearchResultState.Success -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns),
                            state = searchGridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            items(
                                items = state.videos,
                                key = { it.id.ifEmpty { it.url } }
                            ) { video ->
                                VideoCard(
                                    video = video,
                                    onClick = {
                                        mainViewModel.playerController.pendingPreviewItem = video
                                        onVideoClick(video.id)
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
                                        mainViewModel.playerController.pendingPreviewItem = video
                                        onVideoClick(video.id)
                                        (context as? id.katube.MainActivity)?.enterPipMode()
                                    },
                                )
                            }

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
            }
        }
    }
}
