package id.katube.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import id.katube.data.model.StreamBundle
import id.katube.ui.theme.Gold

import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    bundle: StreamBundle,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onClose: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    player: Player? = null,
    isAudioOnly: Boolean = false,
    isBuffering: Boolean = false,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    bufferedPositionMs: Long = 0L,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    val miniDragAnimatable = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .offset { IntOffset(0, miniDragAnimatable.value.roundToInt()) }
            .alpha(1f - (abs(miniDragAnimatable.value) / 500f).coerceIn(0f, 0.35f))
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .draggable(
                orientation = Orientation.Vertical,
                onDragStarted = { },
                onDragStopped = { velocity ->
                    if (velocity < -300f || miniDragAnimatable.value < -25f) {
                        // Geser ke atas: langsung buka layar penuh tontonan selaras dengan transisi slide-up
                        onClick()
                        coroutineScope.launch {
                            miniDragAnimatable.animateTo(
                                targetValue = -250f,
                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                            )
                            miniDragAnimatable.snapTo(0f)
                        }
                    } else if (velocity > 500f || miniDragAnimatable.value > 40f) {
                        // Geser ke bawah: ciutkan dan tutup mini player
                        coroutineScope.launch {
                            miniDragAnimatable.animateTo(
                                targetValue = 200f,
                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                            )
                            onClose()
                            miniDragAnimatable.snapTo(0f)
                        }
                    } else {
                        // Pantulkan kembali ke posisi awal secara halus
                        coroutineScope.launch {
                            miniDragAnimatable.animateTo(
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
                    coroutineScope.launch {
                        miniDragAnimatable.snapTo(miniDragAnimatable.value + delta)
                    }
                }
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail Mini 16:9 / Video Aktif
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.85f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (!isAudioOnly && player != null && player.playbackState != Player.STATE_IDLE && player.currentMediaItem != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                this.player = player
                            }
                        },
                        update = { view ->
                            if (view.player != player) {
                                view.player = player
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = bundle.thumbnailUrl ?: bundle.uploaderAvatarUrl,
                        contentDescription = bundle.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Judul & Channel
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = bundle.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = bundle.uploaderName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 1. Tombol Previous (Video Sebelumnya)
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Sebelumnya",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }

            // 2. Tombol Play / Pause / Buffering Spinner
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(36.dp),
            ) {
                if (isBuffering && !isPlaying) {
                    CircularProgressIndicator(
                        color = Gold,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Jeda" else "Putar",
                        tint = Gold,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            // 3. Tombol Next (Video Berikutnya)
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Berikutnya",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }

            // 4. Tombol Tutup Mini Player
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Tutup",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Garis Progress Playback Mini Bar di Bagian Dasar (Ala YouTube)
        if (durationMs > 0L) {
            val playedFraction = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            val bufferedFraction = (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.White.copy(alpha = 0.15f)),
            ) {
                if (bufferedFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(bufferedFraction)
                            .background(Color.White.copy(alpha = 0.35f)),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(playedFraction)
                        .background(Gold),
                )
            }
        }
    }
}
