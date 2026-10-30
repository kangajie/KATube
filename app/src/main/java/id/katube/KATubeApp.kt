package id.katube

import android.app.Application
import okhttp3.OkHttpClient
import dagger.hilt.android.HiltAndroidApp
import id.katube.core.network.OkHttpDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Application class — titik masuk utama.
 * Inisialisasi NewPipeExtractor di sini agar siap sebelum Activity manapun dibuat.
 */
@HiltAndroidApp
class KATubeApp : Application() {

    // OkHttpClient disuntik Hilt setelah @HiltAndroidApp aktif
    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var recommendationNotificationManager: id.katube.data.notification.RecommendationNotificationManager

    override fun onCreate() {
        super.onCreate()
        initNewPipe()
        initCoilCache()
        initRecommendationNotifications()
    }

    private fun initNewPipe() {
        val client = if (::okHttpClient.isInitialized) {
            okHttpClient
        } else {
            val dispatcher = okhttp3.Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 20
            }
            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        NewPipe.init(
            OkHttpDownloader(client),
            Localization("id", "ID"),
            ContentCountry("ID")
        )
    }

    private fun initCoilCache() {
        val client = if (::okHttpClient.isInitialized) okHttpClient else OkHttpClient()
        val imageLoader = coil.ImageLoader.Builder(this)
            .okHttpClient(client)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB Cache Disk Thumbnail
                    .build()
            }
            .respectCacheHeaders(false)
            .build()

        coil.Coil.setImageLoader(imageLoader)
    }

    private fun initRecommendationNotifications() {
        if (::recommendationNotificationManager.isInitialized) {
            try {
                recommendationNotificationManager.setupNotificationChannel()
                recommendationNotificationManager.scheduleDailyRecommendations()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
