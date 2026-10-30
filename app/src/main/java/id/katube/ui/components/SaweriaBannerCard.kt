package id.katube.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.katube.R
import id.katube.ui.theme.Gold
import java.io.File
import java.io.FileOutputStream

/**
 * Kartu Banner Statis Apresiasi & Dukungan "SAWERIN DULU GUYS" karya KangAjieDev.
 * Menggantikan slot iklan bawaan YouTube dengan QR Saweria resmi pengembang.
 * Pengguna dapat mengetuk untuk melihat tampilan penuh atau mengunduh QR langsung ke Galeri.
 */
@Composable
fun SaweriaBannerCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showFullDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF141414),
        ),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            // Baris Header: Ikon Hati/Kopi + Judul + Badge Resmi Permanen
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Gold.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "SAWERIN DULU GUYS ☕",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Gold,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = "Dukung KangAjieDev agar KATube tetap bebas iklan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }

                // Badge Resmi Statis (Permanen tanpa tombol tutup)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Gold.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, Gold.copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = "RESMI",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Gold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pratinjau Gambar Banner QR Saweria (Dapat Diklik untuk Perbesar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showFullDialog = true },
            ) {
                Image(
                    painter = painterResource(id = R.drawable.banner_saweria_qr),
                    contentDescription = "QR Code Saweria",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )

                // Label Bantuan di Bagian Bawah Gambar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xBB000000))
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Ketuk untuk memperbesar & download QR",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }

    // Dialog Modal Layar Penuh Tampilan QR Code
    if (showFullDialog) {
        Dialog(
            onDismissRequest = { showFullDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF161616),
                border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Header Dialog
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Scan QR Code Saweria ☕",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Gold,
                        )
                        IconButton(
                            onClick = { showFullDialog = false },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Tutup",
                                tint = Color.White,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Gambar QR Ukuran Besar & Jernih
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.banner_saweria_qr),
                            contentDescription = "QR Code Saweria Lengkap",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.matchParentSize(),
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Screenshot layar ini atau unduh QR untuk di-scan lewat aplikasi m-Banking / e-Wallet (BCA, Mandiri, BRI, GoPay, OVO, DANA, ShopeePay)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCCCCCC),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tombol Download / Simpan QR ke Galeri
                    Button(
                        onClick = {
                            saveQrToGallery(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download / Simpan QR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Menyimpan gambar QR Code Saweria langsung ke folder Gambar / Galeri perangkat.
 */
private fun saveQrToGallery(context: Context) {
    try {
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.banner_saweria_qr)
        if (bitmap == null) {
            Toast.makeText(context, "Gambar QR tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }

        val filename = "KATube_Saweria_QR_${System.currentTimeMillis()}.jpg"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KATube")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
                Toast.makeText(context, "QR Code berhasil disimpan ke Galeri Foto!", Toast.LENGTH_LONG).show()
                return
            }
        } else {
            @Suppress("DEPRECATION")
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val katubeDir = File(picturesDir, "KATube")
            if (!katubeDir.exists()) katubeDir.mkdirs()
            val file = File(katubeDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                data = Uri.fromFile(file)
            }
            context.sendBroadcast(intent)
            Toast.makeText(context, "QR Code berhasil disimpan ke Galeri Foto!", Toast.LENGTH_LONG).show()
            return
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
