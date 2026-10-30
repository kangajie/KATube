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
        enableEdgeToEdge()
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
        val videoId = intent?.getStringExtra(RecommendationNotificationManager.EXTRA_VIDEO_ID)
            ?: intent?.getStringExtra("open_video_id")
            ?: intent?.data?.getQueryParameter("v")
            ?: intent?.data?.lastPathSegment
        if (!videoId.isNullOrBlank() && !videoId.equals("watch", ignoreCase = true)) {
            mainViewModel.triggerNavigateToVideo(videoId)
        } else if (intent?.getBooleanExtra("open_watch_screen", false) == true) {
            mainViewModel.triggerNavigateToWatch()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::recommendationManager.isInitialized) {
            // Jadwalkan notifikasi rekomendasi setelah pengguna meninggalkan aplikasi (misal 2 jam)
            recommendationManager.scheduleDelayedRecommendation(120L)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (mainViewModel.playerController.isPlaying.value && !mainViewModel.playerController.isAudioOnly.value) {
            enterPipMode()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (mainViewModel.playerController.isPlaying.value && !mainViewModel.playerController.isAudioOnly.value) {
            val hasPip = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)
            if (hasPip) {
                enterPipMode()
                return
            }
        }
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
