package id.katube.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import id.katube.core.util.CommentUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.katube.core.util.TimeAgoUtils
import id.katube.data.model.VideoItem
import id.katube.ui.theme.Gold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onPlayInBackground: (VideoItem) -> Unit = {},
    onPlayInPopup: (VideoItem) -> Unit = {},
    onPlayNext: (VideoItem) -> Unit = {},
    onAddToQueue: (VideoItem) -> Unit = {},
    onAddToPlaylist: (VideoItem) -> Unit = {},
    onAddToWatchLater: (VideoItem) -> Unit = {},
    onNotInterested: (VideoItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 16.dp),
    ) {
        // Thumbnail 16:9 dengan Badge Durasi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val optimalThumb = remember(video.thumbnailUrl, video.highResThumbnailUrl) {
                video.thumbnailUrl ?: video.highResThumbnailUrl
            }
            AsyncImage(
                model = optimalThumb,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )

            // Badge durasi atau Mix di kanan bawah
            if (video.isMix) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = Color(0xDD000000),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = video.uploadDateText ?: "MIX",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else if (video.isLive || video.formattedDuration.equals("LIVE", ignoreCase = true)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = Color(0xFFCC0000),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else if (video.formattedDuration.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = Color(0xCC000000),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = video.formattedDuration,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Tanda Pemutaran Terakhir (Progress Bar Kuning Keemasan)
            if (video.watchProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.5f))
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

        Spacer(modifier = Modifier.height(10.dp))

        // Info Video: Avatar + Judul + Metadata + Menu Titik 3
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Avatar Channel 36dp
            val cleanAvatar = remember(video.uploaderAvatarUrl) {
                CommentUtils.sanitizeImageUrl(video.uploaderAvatarUrl)
            }
            if (!cleanAvatar.isNullOrEmpty()) {
                AsyncImage(
                    model = cleanAvatar,
                    contentDescription = video.uploaderName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else if (video.isMix) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = "Mix",
                        tint = Gold,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = video.uploaderName.firstOrNull()?.uppercase() ?: "K",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Kolom Judul & Metadata
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Metadata: Channel - Views - Waktu
                val metaText = remember(video.uploaderName, video.formattedViews, video.uploadDateText) {
                    val formattedTimeAgo = TimeAgoUtils.format(null, video.uploadDateText)
                    listOfNotNull(
                        video.uploaderName.takeIf { it.isNotEmpty() },
                        video.formattedViews.takeIf { it.isNotEmpty() },
                        formattedTimeAgo.takeIf { it.isNotEmpty() }
                    ).joinToString(" • ")
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Tombol Opsi Titik Tiga (Buka Bottom Sheet persis seperti Halaman 2 Referensi)
            IconButton(
                onClick = { showBottomSheet = true },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Opsi",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    // Modal Bottom Sheet Opsi Video (Sesuai Referensi PDF Halaman 2)
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                // Header BottomSheet: Judul & Saluran
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = video.uploaderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                // 1. Mulai putar di latar belakang
                BottomSheetActionItem(
                    icon = Icons.Default.Headphones,
                    text = "Mulai putar di latar belakang",
                    onClick = {
                        showBottomSheet = false
                        onPlayInBackground(video)
                        Toast.makeText(context, "Memutar audio di latar belakang...", Toast.LENGTH_SHORT).show()
                    }
                )

                // 2. Mulai putar di popup
                BottomSheetActionItem(
                    icon = Icons.Default.PictureInPictureAlt,
                    text = "Mulai putar di popup",
                    onClick = {
                        showBottomSheet = false
                        onPlayInPopup(video)
                    }
                )

                // 3. Putar berikutnya
                BottomSheetActionItem(
                    icon = Icons.Default.PlaylistPlay,
                    text = "Putar berikutnya",
                    onClick = {
                        showBottomSheet = false
                        onPlayNext(video)
                        Toast.makeText(context, "Akan diputar berikutnya", Toast.LENGTH_SHORT).show()
                    }
                )

                // 4. Tambahkan ke antrean
                BottomSheetActionItem(
                    icon = Icons.Default.PlaylistAdd,
                    text = "Tambahkan ke antrean",
                    onClick = {
                        showBottomSheet = false
                        onAddToQueue(video)
                        Toast.makeText(context, "Ditambahkan ke antrean", Toast.LENGTH_SHORT).show()
                    }
                )

                // 5. Tambahkan Ke Playlist
                BottomSheetActionItem(
                    icon = Icons.Default.PlaylistAddCheck,
                    text = "Tambahkan Ke Playlist",
                    onClick = {
                        showBottomSheet = false
                        onAddToPlaylist(video)
                    }
                )

                // 6. Tambahkan untuk ditonton nanti
                BottomSheetActionItem(
                    icon = Icons.Default.AccessTime,
                    text = "Tambahkan untuk ditonton nanti",
                    onClick = {
                        showBottomSheet = false
                        onAddToWatchLater(video)
                    }
                )

                // 7. Tidak Suka
                BottomSheetActionItem(
                    icon = Icons.Default.ThumbDown,
                    text = "Tidak Suka",
                    onClick = {
                        showBottomSheet = false
                        onNotInterested(video)
                        Toast.makeText(context, "Video disembunyikan", Toast.LENGTH_SHORT).show()
                    }
                )

                // 8. Bagikan
                BottomSheetActionItem(
                    icon = Icons.Default.Share,
                    text = "Bagikan",
                    onClick = {
                        showBottomSheet = false
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "${video.title}\nhttps://www.youtube.com/watch?v=${video.id}")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Bagikan Video"))
                    }
                )

                // 9. Lapor
                BottomSheetActionItem(
                    icon = Icons.Default.Flag,
                    text = "Lapor",
                    onClick = {
                        showBottomSheet = false
                        onNotInterested(video)
                        Toast.makeText(context, "Laporan terkirim. Video disembunyikan.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomSheetActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
        )
    }
}
