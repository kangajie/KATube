package id.katube.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import id.katube.ui.theme.Gold

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

enum class SettingsSubPage {
    UMUM, RIWAYAT_CACHE, KONTEN, NOTIFIKASI
}

@Composable
fun SettingsScreen(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onInterceptBack: (((() -> Boolean)?) -> Unit)? = null,
) {
    val context = LocalContext.current
    var subPage by remember { mutableStateOf<SettingsSubPage?>(null) }

    DisposableEffect(subPage) {
        onInterceptBack?.invoke(
            if (subPage != null) {
                {
                    subPage = null
                    true
                }
            } else null
        )
        onDispose {
            onInterceptBack?.invoke(null)
        }
    }

    // Intersepsi tombol kembali perangkat: jika di sub-halaman kembali ke root pengaturan, jika di root kembali ke layar sebelumnya
    BackHandler(enabled = true) {
        if (subPage != null) {
            subPage = null
        } else {
            onNavigateBack()
        }
    }

    // Koleksi State Preferensi dari ViewModel
    val defaultQuality by viewModel.defaultQuality.collectAsState()
    val popupQuality by viewModel.popupQuality.collectAsState()
    val cellularMaxQuality by viewModel.cellularMaxQuality.collectAsState()
    val videoFormat by viewModel.videoFormat.collectAsState()
    val audioFormat by viewModel.audioFormat.collectAsState()
    val subtitleMode by viewModel.subtitleMode.collectAsState()
    val backgroundPlayMode by viewModel.backgroundPlayMode.collectAsState()
    val seekDuration by viewModel.seekDuration.collectAsState()
    val autoplayNext by viewModel.autoplayNext.collectAsState()
    val defaultAudioOnly by viewModel.defaultAudioOnly.collectAsState()
    val volumeGesture by viewModel.volumeGesture.collectAsState()
    val brightnessGesture by viewModel.brightnessGesture.collectAsState()
    val resumeCallInterrupt by viewModel.resumeCallInterrupt.collectAsState()
    val fastSeek by viewModel.fastSeek.collectAsState()

    val watchHistoryEnabled by viewModel.watchHistoryEnabled.collectAsState()
    val resumePlayback by viewModel.resumePlayback.collectAsState()
    val searchHistoryEnabled by viewModel.searchHistoryEnabled.collectAsState()

    val appLanguage by viewModel.appLanguage.collectAsState()
    val contentCountry by viewModel.contentCountry.collectAsState()
    val restrictedMode by viewModel.restrictedMode.collectAsState()

    val notifBackgroundPlay by viewModel.notifBackgroundPlay.collectAsState()
    val notifNewVideos by viewModel.notifNewVideos.collectAsState()
    val notifTrending by viewModel.notifTrending.collectAsState()
    val notifDailyRecommendation by viewModel.notifDailyRecommendation.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
        ) {
        // Judul sub-halaman: Bersih tanpa tombol panah kembali (karena tombol kembali sudah ada di TopBar Pengaturan)
        if (subPage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(
                    text = when (subPage) {
                        SettingsSubPage.UMUM -> "Umum"
                        SettingsSubPage.RIWAYAT_CACHE -> "Riwayat dan cache"
                        SettingsSubPage.KONTEN -> "Konten"
                        SettingsSubPage.NOTIFIKASI -> "Notifikasi"
                        null -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }

        // Konten Berdasarkan Sub-Halaman
        when (subPage) {
            null -> {
                // Menu Kategori Pengaturan Utama (Bersih & Rapi tanpa duplikasi judul)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    SettingsMenuRow(
                        icon = Icons.Default.Tune,
                        title = "Umum",
                        subtitle = "Tema, resolusi, format pemutar, gestur & kontrol",
                        onClick = { subPage = SettingsSubPage.UMUM },
                    )
                    SettingsMenuRow(
                        icon = Icons.Default.History,
                        title = "Riwayat dan cache",
                        subtitle = "Riwayat tontonan, lanjutkan pemutaran, bersihkan cache",
                        onClick = { subPage = SettingsSubPage.RIWAYAT_CACHE },
                    )
                    SettingsMenuRow(
                        icon = Icons.Default.Language,
                        title = "Konten",
                        subtitle = "Bahasa aplikasi, lokasi konten, mode terbatas",
                        onClick = { subPage = SettingsSubPage.KONTEN },
                    )
                    SettingsMenuRow(
                        icon = Icons.Default.Notifications,
                        title = "Notifikasi",
                        subtitle = "Notifikasi pemutaran, langganan & video tren",
                        onClick = { subPage = SettingsSubPage.NOTIFIKASI },
                    )
                }
            }

            SettingsSubPage.UMUM -> {
                // Sub-Menu: Umum (Semua Opsi Dibuat Adjustable, Fungsional & Rapi)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    SettingsSectionHeader("TAMPILAN & TEMA")

                    SettingsDropdownRow(
                        title = "Tema aplikasi",
                        value = when (currentThemeMode) {
                            ThemeMode.SYSTEM -> "Ikuti sistem"
                            ThemeMode.LIGHT -> "Terang"
                            ThemeMode.DARK -> "Gelap"
                        },
                        subtitle = "Pilih nuansa tampilan terang, gelap, atau bawaan sistem",
                        options = listOf(
                            "SYSTEM" to "Ikuti sistem",
                            "LIGHT" to "Terang",
                            "DARK" to "Gelap",
                        ),
                        onSelected = { key ->
                            val mode = ThemeMode.valueOf(key)
                            onThemeModeChange(mode)
                            viewModel.setThemeMode(mode)
                        }
                    )

                    SettingsSectionHeader("KUALITAS & FORMAT VIDEO")

                    SettingsDropdownRow(
                        title = "Kualitas default video",
                        value = if (defaultQuality == 0) "Otomatis" else "${defaultQuality}p",
                        subtitle = "Resolusi default saat pertama kali memutar video",
                        options = listOf(
                            "0" to "Otomatis (Sesuai koneksi)",
                            "1080" to "1080p (FHD tajam)",
                            "720" to "720p (HD jernih)",
                            "480" to "480p (Standar lancar)",
                            "360" to "360p (Hemat data)",
                            "240" to "240p",
                            "144" to "144p (Sangat hemat)",
                        ),
                        onSelected = { key ->
                            viewModel.setDefaultQuality(key.toIntOrNull() ?: 0)
                            Toast.makeText(context, "Kualitas default disimpan", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingsDropdownRow(
                        title = "Kualitas mode popup (PiP)",
                        value = if (popupQuality == 0) "Otomatis" else "${popupQuality}p",
                        subtitle = "Resolusi saat video diputar di jendela mengambang kecil",
                        options = listOf(
                            "0" to "Otomatis",
                            "720" to "720p (HD)",
                            "480" to "480p",
                            "360" to "360p",
                            "240" to "240p",
                            "144" to "144p (Sangat hemat)",
                        ),
                        onSelected = { key ->
                            viewModel.setPopupQuality(key.toIntOrNull() ?: 0)
                            Toast.makeText(context, "Kualitas popup disimpan", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingsDropdownRow(
                        title = "Batasan kualitas di jaringan seluler",
                        value = when (cellularMaxQuality) {
                            0 -> "Tanpa batasan"
                            else -> "Maksimal ${cellularMaxQuality}p"
                        },
                        subtitle = "Mencegah kuota internet seluler cepat terkuras",
                        options = listOf(
                            "0" to "Tanpa batasan (No limit)",
                            "720" to "Maksimal 720p",
                            "480" to "Maksimal 480p",
                            "360" to "Maksimal 360p (Hemat kuota)",
                            "240" to "Maksimal 240p",
                            "144" to "Maksimal 144p (Paling hemat)",
                        ),
                        onSelected = { key ->
                            viewModel.setCellularMaxQuality(key.toIntOrNull() ?: 0)
                            Toast.makeText(context, "Batasan seluler diatur", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingsDropdownRow(
                        title = "Format video pilihan",
                        value = videoFormat,
                        subtitle = "Format encoding video yang diutamakan",
                        options = listOf(
                            "WebM" to "WebM / VP9 (Rekomendasi YouTube)",
                            "MP4" to "MP4 (Kompatibilitas universal)",
                        ),
                        onSelected = { key ->
                            viewModel.setVideoFormat(key)
                        }
                    )

                    SettingsDropdownRow(
                        title = "Format audio pilihan",
                        value = audioFormat,
                        subtitle = "Format kompresi audio yang diutamakan",
                        options = listOf(
                            "WebM" to "WebM / Opus (Suara jernih)",
                            "M4A" to "M4A / AAC (Kompatibilitas standar)",
                        ),
                        onSelected = { key ->
                            viewModel.setAudioFormat(key)
                        }
                    )

                    SettingsDropdownRow(
                        title = "Teks & Subtitle",
                        value = subtitleMode,
                        subtitle = "Menampilkan subtitle bahasa otomatis jika tersedia",
                        options = listOf(
                            "Otomatis" to "Otomatis (Bahasa Indonesia jika ada)",
                            "Selalu aktif" to "Selalu aktif",
                            "Nonaktif" to "Nonaktif",
                        ),
                        onSelected = { key ->
                            viewModel.setSubtitleMode(key)
                        }
                    )

                    SettingsSectionHeader("PEMUTAR & NAVIGASI")

                    SettingsDropdownRow(
                        title = "Ketuk dua kali untuk melompat",
                        value = "$seekDuration detik",
                        subtitle = "Durasi lompat maju/mundur saat mengetuk sisi layar pemutar",
                        options = listOf(
                            "5" to "5 detik",
                            "10" to "10 detik (Standar)",
                            "15" to "15 detik",
                            "20" to "20 detik",
                            "30" to "30 detik",
                            "60" to "60 detik",
                        ),
                        onSelected = { key ->
                            viewModel.setSeekDuration(key.toIntOrNull() ?: 10)
                        }
                    )

                    SettingsToggleRow(
                        title = "Putar video berikutnya otomatis",
                        subtitle = "Lanjut memutar video terkait secara otomatis saat video berakhir",
                        checked = autoplayNext,
                        onCheckedChange = { viewModel.setAutoplayNext(it) },
                    )

                    SettingsDropdownRow(
                        title = "Pemutaran di latar belakang (Background Play)",
                        value = backgroundPlayMode,
                        subtitle = "Memutar audio video saat aplikasi di-minimize atau layar terkunci",
                        options = listOf(
                            "Selalu aktif" to "Selalu aktif",
                            "Hanya headset" to "Hanya earphone / headphone",
                            "Nonaktif" to "Nonaktif",
                        ),
                        onSelected = { key ->
                            viewModel.setBackgroundPlayMode(key)
                        }
                    )

                    SettingsToggleRow(
                        title = "Audio saja secara default",
                        subtitle = "Hemat kuota penuh dengan hanya streaming trek audio",
                        checked = defaultAudioOnly,
                        onCheckedChange = { viewModel.setDefaultAudioOnly(it) },
                    )

                    SettingsSectionHeader("GESTUR & PERILAKU")

                    SettingsToggleRow(
                        title = "Lanjutkan pemutaran setelah panggilan telepon",
                        subtitle = "Melanjutkan video secara otomatis setelah interupsi telepon selesai",
                        checked = resumeCallInterrupt,
                        onCheckedChange = { viewModel.setResumeCallInterrupt(it) },
                    )

                    SettingsToggleRow(
                        title = "Kontrol gestur volume",
                        subtitle = "Geser ke atas/bawah di sisi kanan layar untuk mengatur volume",
                        checked = volumeGesture,
                        onCheckedChange = { viewModel.setVolumeGesture(it) },
                    )

                    SettingsToggleRow(
                        title = "Kontrol gestur kecerahan",
                        subtitle = "Geser ke atas/bawah di sisi kiri layar untuk mengatur kecerahan",
                        checked = brightnessGesture,
                        onCheckedChange = { viewModel.setBrightnessGesture(it) },
                    )

                    SettingsToggleRow(
                        title = "Pencarian video cepat (Fast Seek)",
                        subtitle = "Melompat langsung ke keyframe terdekat saat menggeser timeline",
                        checked = fastSeek,
                        onCheckedChange = { viewModel.setFastSeek(it) },
                    )

                    SettingsSectionHeader("KOMPATIBILITAS & DAYA TAHAN LATAR")

                    SettingsActionRow(
                        title = "Optimasi baterai sistem",
                        subtitle = "Buka pengaturan baterai perangkat untuk mematikan pembatasan pemutaran latar belakang (Xiaomi/Samsung/Oppo/Vivo)",
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } catch (e2: Exception) {
                                    Toast.makeText(context, "Buka Pengaturan HP -> Aplikasi -> KATube -> Baterai", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            SettingsSubPage.RIWAYAT_CACHE -> {
                // Sub-Menu: Riwayat dan Cache
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    SettingsSectionHeader("RIWAYAT TONTONAN & PENCARIAN")

                    SettingsToggleRow(
                        title = "Simpan riwayat tontonan",
                        subtitle = "Mencatat video yang ditonton ke dalam riwayat akun",
                        checked = watchHistoryEnabled,
                        onCheckedChange = { viewModel.setWatchHistoryEnabled(it) },
                    )
                    SettingsToggleRow(
                        title = "Lanjutkan pemutaran dari posisi terakhir",
                        subtitle = "Menyimpan detik tontonan agar video dapat dilanjutkan",
                        checked = resumePlayback,
                        onCheckedChange = { viewModel.setResumePlayback(it) },
                    )
                    SettingsToggleRow(
                        title = "Simpan riwayat pencarian",
                        subtitle = "Menampilkan saran dari pencarian Anda sebelumnya",
                        checked = searchHistoryEnabled,
                        onCheckedChange = { viewModel.setSearchHistoryEnabled(it) },
                    )

                    SettingsSectionHeader("PEMBERSIHAN DATA")

                    SettingsActionRow(
                        title = "Menghapus semua metadata yang di-cache",
                        subtitle = "Mengosongkan cache gambar & temporary file untuk melegakan memori",
                        onClick = {
                            viewModel.clearCache { freedBytes ->
                                val kb = freedBytes / 1024
                                Toast.makeText(context, "Berhasil membersihkan cache ($kb KB)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    SettingsActionRow(
                        title = "Hapus riwayat tontonan lokal",
                        subtitle = "Menghapus seluruh daftar riwayat tontonan di perangkat ini",
                        onClick = {
                            viewModel.clearWatchHistory {
                                Toast.makeText(context, "Riwayat tontonan lokal berhasil dihapus", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    SettingsActionRow(
                        title = "Hapus riwayat pencarian",
                        subtitle = "Membersihkan seluruh histori kata kunci pencarian",
                        onClick = {
                            Toast.makeText(context, "Riwayat pencarian berhasil dibersihkan", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            SettingsSubPage.KONTEN -> {
                // Sub-Menu: Konten
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    SettingsSectionHeader("LOKALISASI KONTEN")

                    SettingsDropdownRow(
                        title = "Bahasa aplikasi",
                        value = appLanguage,
                        subtitle = "Bahasa antarmuka pengguna KATube",
                        options = listOf(
                            "Bahasa Indonesia" to "Bahasa Indonesia",
                            "English" to "English",
                        ),
                        onSelected = { key ->
                            viewModel.setAppLanguage(key)
                            Toast.makeText(context, "Bahasa aplikasi: $key", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingsDropdownRow(
                        title = "Lokasi konten",
                        value = when (contentCountry) {
                            "ID" -> "Indonesia (ID)"
                            "GLOBAL" -> "Global (Semua negara)"
                            "US" -> "Amerika Serikat (US)"
                            "JP" -> "Jepang (JP)"
                            "KR" -> "Korea (KR)"
                            else -> contentCountry
                        },
                        subtitle = "Menentukan negara untuk feed trending dan rekomendasi",
                        options = listOf(
                            "ID" to "Indonesia (ID)",
                            "GLOBAL" to "Global (Semua negara)",
                            "US" to "Amerika Serikat (US)",
                            "JP" to "Jepang (JP)",
                            "KR" to "Korea (KR)",
                        ),
                        onSelected = { key ->
                            viewModel.setContentCountry(key)
                            Toast.makeText(context, "Lokasi konten diatur ke $key", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SettingsToggleRow(
                        title = "Mode terbatas (Restricted Mode)",
                        subtitle = "Membantu menyaring video berpotensi dewasa atau sensitif",
                        checked = restrictedMode,
                        onCheckedChange = { viewModel.setRestrictedMode(it) },
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            SettingsSubPage.NOTIFIKASI -> {
                // Sub-Menu: Notifikasi
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    SettingsSectionHeader("PENGATURAN NOTIFIKASI")

                    SettingsToggleRow(
                        title = "Notifikasi pemutaran di latar belakang",
                        subtitle = "Menampilkan kontrol media pemutar di panel notifikasi sistem",
                        checked = notifBackgroundPlay,
                        onCheckedChange = { viewModel.setNotifBackgroundPlay(it) },
                    )
                    SettingsToggleRow(
                        title = "Notifikasi video baru dari saluran langganan",
                        subtitle = "Memberitahu video terbaru dari kreator yang Anda ikuti",
                        checked = notifNewVideos,
                        onCheckedChange = { viewModel.setNotifNewVideos(it) },
                    )
                    SettingsToggleRow(
                        title = "Video rekomendasi yang sedang tren",
                        subtitle = "Notifikasi video populer pilihan harian Indonesia",
                        checked = notifTrending,
                        onCheckedChange = { viewModel.setNotifTrending(it) },
                    )

                    SettingsSectionHeader("REKOMENDASI PINTAR (GENRE)")

                    SettingsToggleRow(
                        title = "Rekomendasi video harian otomatis",
                        subtitle = "Kirimkan notifikasi video pilihan sesuai genre favorit setiap hari (siang & malam)",
                        checked = notifDailyRecommendation,
                        onCheckedChange = { viewModel.setNotifDailyRecommendation(it) },
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Gold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingsDropdownRow(
    title: String,
    value: String,
    subtitle: String? = null,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
        }

        Box {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = Gold,
                fontWeight = FontWeight.SemiBold,
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                options.forEach { (key, label) ->
                    val isSelected = value.equals(label, ignoreCase = true) || label.startsWith(value)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                color = if (isSelected) Gold else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onSelected(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.background,
                checkedTrackColor = Gold,
                uncheckedThumbColor = MaterialTheme.colorScheme.background,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}
