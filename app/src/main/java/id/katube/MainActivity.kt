package id.katube

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import id.katube.data.notification.RecommendationNotificationManager
import id.katube.ui.navigation.KATubeAppScaffold
import id.katube.ui.settings.ThemeMode
import id.katube.ui.theme.KATubeTheme
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Satu-satunya Activity. Semua navigasi dikendalikan via Compose Navigation (Fase 01).
 * Mengamati tema tersimpan di DataStore secara reaktif.
 * Pemutaran audio tetap berjalan di latar belakang saat beralih aplikasi/tekan tombol Home.
 */
@androidx.media3.common.util.UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var recommendationManager: RecommendationNotificationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isInitialDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            statusBarStyle = if (isInitialDark) {
                androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                androidx.activity.SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                )
            },
            navigationBarStyle = if (isInitialDark) {
                androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                androidx.activity.SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                )
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        handleIntent(intent)
        // Layar HP tetap menyala selama berada di dalam aplikasi, dan mati normal saat keluar
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Dukungan cutout layar penuh (notch/poni kamera) pada Android 9 (API 28) ke atas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Izin notifikasi runtime (Android 13+) agar status bar icon & kontrol pemutar muncul
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            val themeMode by mainViewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            androidx.compose.runtime.DisposableEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = if (isDark) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                    window.isStatusBarContrastEnforced = false
                }
                onDispose {}
            }

            KATubeTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    KATubeAppScaffold(
                        currentThemeMode = themeMode,
                        onThemeModeChange = { mainViewModel.setThemeMode(it) },
                        mainViewModel = mainViewModel,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val isOpenWatch = intent.getBooleanExtra("open_watch_screen", false)
        val directVideoId = intent.getStringExtra(RecommendationNotificationManager.EXTRA_VIDEO_ID)
            ?: intent.data?.getQueryParameter("v")
            ?: intent.data?.lastPathSegment

        // Bersihkan intent agar tidak terpanggil ulang saat pengguna kembali masuk ke aplikasi
        setIntent(Intent(this, MainActivity::class.java))

        if (isOpenWatch) {
            // Ketika mengklik notifikasi pemutar, SELALU buka video yang sedang aktif diputar saat ini juga!
            val currentlyPlayingId = mainViewModel.playerController.currentStreamBundle.value?.videoId
                ?: mainViewModel.playerController.player?.currentMediaItem?.mediaId
                ?: intent?.getStringExtra("open_video_id")

            if (!currentlyPlayingId.isNullOrBlank() && !currentlyPlayingId.equals("watch", ignoreCase = true)) {
                mainViewModel.triggerNavigateToVideo(currentlyPlayingId)
            } else {
                mainViewModel.triggerNavigateToWatch()
            }
        } else if (!directVideoId.isNullOrBlank() && !directVideoId.equals("watch", ignoreCase = true)) {
            mainViewModel.triggerNavigateToVideo(directVideoId)
        }
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.playerController.setAppForegroundState(inForeground = true, inPip = false)
    }

    override fun onStop() {
        super.onStop()
        if (::recommendationManager.isInitialized) {
            // Jadwalkan notifikasi rekomendasi setelah pengguna meninggalkan aplikasi (misal 2 jam)
            recommendationManager.scheduleDelayedRecommendation(120L)
        }

        // Cek preferensi pemutaran di latar belakang (backgroundPlayMode) dari Pengaturan
        val isPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        mainViewModel.playerController.setAppForegroundState(inForeground = false, inPip = isPip)

        if (!isPip && mainViewModel.playerController.isPlaying.value) {
            val allowBackground = shouldAllowBackgroundPlay()
            if (!allowBackground) {
                mainViewModel.playerController.pause()
            }
        }
    }

    private fun shouldAllowBackgroundPlay(): Boolean {
        val mode = runCatching {
            kotlinx.coroutines.runBlocking {
                mainViewModel.preferencesRepository.backgroundPlayMode.first()
            }
        }.getOrDefault("Selalu aktif")

        return when (mode) {
            "Nonaktif" -> false
            "Hanya headset" -> {
                val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)?.any {
                        it.type in listOf(
                            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                            android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                        )
                    } == true
                } else {
                    @Suppress("DEPRECATION")
                    (audioManager?.isWiredHeadsetOn == true || audioManager?.isBluetoothA2dpOn == true)
                }
            }
            else -> true
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        mainViewModel.playerController.setAppForegroundState(inForeground = !isInPictureInPictureMode, inPip = isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Jangan pernah memunculkan pop-up PiP otomatis saat keluar/meninggalkan aplikasi.
        // Cukup keluar normal ke beranda HP sementara pemutar audio tetap berjalan di latar belakang.
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Jangan alihkan otomatis ke pop-up PiP saat keluar, biarkan keluar normal ke beranda HP
        super.onBackPressed()
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPip = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
            if (hasPip) {
                try {
                    val paramsBuilder = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        paramsBuilder.setAutoEnterEnabled(true)
                    }
                    enterPictureInPictureMode(paramsBuilder.build())
                } catch (e: Exception) {
                    // Penanganan aman untuk perangkat spesifikasi rendah atau ROM tanpa dukungan PiP aktif
                }
            }
        }
    }
}
