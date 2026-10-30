package id.katube.data.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import dagger.hilt.android.qualifiers.ApplicationContext
import id.katube.MainActivity
import id.katube.R
import id.katube.data.extractor.YouTubeRepository
import id.katube.data.local.history.WatchHistoryDao
import id.katube.data.local.history.WatchHistoryEntity
import id.katube.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pengelola Notifikasi Rekomendasi Pintar KATube.
 * Menganalisis genre/topik video yang paling sering diputar pengguna,
 * lalu mengirimkan notifikasi rekomendasi video baru sesuai preferensi tersebut.
 *
 * Sesuai permintaan:
 * 1. Menampilkan notifikasi visual dengan thumbnail ber-overlay tombol putar dan judul video.
 * 2. Menggunakan ikon resmi KATube di status bar atas (sejajar jam & baterai).
 * 3. Tidak disimpan di dalam daftar ikon lonceng aplikasi (hanya notifikasi sistem).
 * 4. Otomatis terhapus (autoCancel) begitu diklik pengguna dan langsung memutar videonya.
 */
@Singleton
class RecommendationNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: WatchHistoryDao,
    private val repository: YouTubeRepository,
    private val preferencesRepository: UserPreferencesRepository,
) {

    companion object {
        const val CHANNEL_ID = "katube_recommendations"
        const val NOTIFICATION_ID = 2001
        const val ACTION_SHOW_RECOMMENDATION = "id.katube.action.SHOW_RECOMMENDATION"
        const val EXTRA_VIDEO_ID = "open_video_id"
        private var lastShownNotificationTime: Long = 0L
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * Konfigurasi saluran notifikasi rekomendasi dengan tingkat kepentingan DEFAULT
     * agar ikon KATube pasti muncul di bar atas (status bar) sejajar dengan jam & baterai.
     */
    fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_recommendations),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_recommendations_desc)
                enableLights(true)
                lightColor = Color.parseColor("#F2B705") // Emas khas KATube
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Menganalisis riwayat tontonan untuk menentukan genre/topik yang paling disukai pengguna.
     */
    suspend fun detectFavoriteGenreQuery(): String = withContext(Dispatchers.IO) {
        val recentHistory = try {
            historyDao.getRecent(35)
        } catch (_: Exception) {
            emptyList()
        }

        if (recentHistory.isEmpty()) {
            return@withContext "podcast horor misteri terbaru"
        }

        // 1. Hitung frekuensi artis / channel yang paling sering diputar
        val channelCounts = mutableMapOf<String, Int>()
        for (item in recentHistory) {
            val uploader = item.uploaderName.trim()
            if (uploader.isNotBlank()) {
                channelCounts[uploader] = (channelCounts[uploader] ?: 0) + 1
            }
        }
        val topChannel = channelCounts.maxByOrNull { it.value }

        // Jika pengguna memutar channel yang sama minimal 3 kali, jadikan prioritas utama
        if (topChannel != null && topChannel.value >= 3) {
            return@withContext "${topChannel.key} terbaru"
        }

        // 2. Klasifikasi frekuensi genre berdasarkan kata kunci di judul video
        val genreScores = mutableMapOf(
            "horor" to 0,
            "musik" to 0,
            "podcast" to 0,
            "religi" to 0,
            "gaming" to 0,
            "komedi" to 0,
            "berita" to 0
        )

        for (item in recentHistory) {
            val text = (item.title + " " + item.uploaderName).lowercase()

            if (text.contains("horor") || text.contains("horror") || text.contains("misteri") ||
                text.contains("hantu") || text.contains("santet") || text.contains("menjelma") ||
                text.contains("ki geni") || text.contains("lentera malam") || text.contains("malam mencekam") ||
                text.contains("rjl") || text.contains("prasodjo") || text.contains("pocong") || text.contains("jin")
            ) {
                genreScores["horor"] = (genreScores["horor"] ?: 0) + 3
            }

            if (text.contains("lagu") || text.contains("music") || text.contains("musik") ||
                text.contains("dangdut") || text.contains("koplo") || text.contains("cover") ||
                text.contains("lirik") || text.contains("song") || text.contains("remix") ||
                text.contains("full album") || text.contains("pop") || text.contains("akustik")
            ) {
                genreScores["musik"] = (genreScores["musik"] ?: 0) + 3
            }

            if (text.contains("podcast") || text.contains("bincang") || text.contains("curhat") ||
                text.contains("close the door") || text.contains("wendi cagur") || text.contains("denny sumargo") ||
                text.contains("podkesmas") || text.contains("wawancara")
            ) {
                genreScores["podcast"] = (genreScores["podcast"] ?: 0) + 2
            }

            if (text.contains("ceramah") || text.contains("kajian") || text.contains("sholawat") ||
                text.contains("ustadz") || text.contains("dakwah") || text.contains("habib") ||
                text.contains("quran") || text.contains("islam") || text.contains("ngaji")
            ) {
                genreScores["religi"] = (genreScores["religi"] ?: 0) + 3
            }

            if (text.contains("gaming") || text.contains("gameplay") || text.contains("mobile legends") ||
                text.contains("mlbb") || text.contains("minecraft") || text.contains("roblox") ||
                text.contains("free fire") || text.contains("windah")
            ) {
                genreScores["gaming"] = (genreScores["gaming"] ?: 0) + 3
            }

            if (text.contains("lucu") || text.contains("komedi") || text.contains("stand up") ||
                text.contains("sketsa") || text.contains("vlog") || text.contains("ngakak")
            ) {
                genreScores["komedi"] = (genreScores["komedi"] ?: 0) + 2
            }

            if (text.contains("berita") || text.contains("news") || text.contains("viral") ||
                text.contains("politik") || text.contains("update hari ini")
            ) {
                genreScores["berita"] = (genreScores["berita"] ?: 0) + 2
            }
        }

        val topGenre = genreScores.filter { it.value > 0 }.maxByOrNull { it.value }?.key

        return@withContext when (topGenre) {
            "horor" -> "podcast horor misteri terbaru"
            "musik" -> "lagu indonesia terbaru"
            "podcast" -> "podcast indonesia terbaru"
            "religi" -> "ceramah kajian terbaru"
            "gaming" -> "gaming indonesia terbaru"
            "komedi" -> "video komedi lucu terbaru"
            "berita" -> "berita viral terkini"
            else -> {
                // Jika belum spesifik, gunakan artis/channel teratas atau topik video terakhir
                val lastItem = recentHistory.firstOrNull()
                if (lastItem != null && lastItem.uploaderName.isNotBlank()) {
                    "${lastItem.uploaderName} terbaru"
                } else {
                    "podcast horor misteri terbaru"
                }
            }
        }
    }

    /**
     * Mengambil rekomendasi video terbaru sesuai genre favorit pengguna dan
     * menampilkannya sebagai notifikasi sistem Android.
     */
    suspend fun fetchAndPostRecommendation(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val isEnabled = preferencesRepository.notifDailyRecommendation.firstOrNull() ?: true
        if (!isEnabled && !force) return@withContext false

        val now = System.currentTimeMillis()
        if (!force && (now - lastShownNotificationTime) < (3 * 60 * 60 * 1000L)) {
            return@withContext false
        }

        setupNotificationChannel()

        val query = detectFavoriteGenreQuery()
        val recentWatchedIds = try {
            historyDao.getRecent(50).map { it.videoId }.toSet()
        } catch (_: Exception) {
            emptySet()
        }

        val searchResult = repository.search(query)
        val candidate = searchResult.getOrNull()?.firstOrNull { video ->
            video.id.isNotEmpty() &&
            !recentWatchedIds.contains(video.id) &&
            video.title.isNotBlank()
        } ?: searchResult.getOrNull()?.firstOrNull { it.id.isNotEmpty() && it.title.isNotBlank() }

        if (candidate == null) return@withContext false

        // Unduh thumbnail video menggunakan Coil
        val thumbUrl = candidate.thumbnailUrl
        var originalBitmap: Bitmap? = null
        if (!thumbUrl.isNullOrEmpty()) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(thumbUrl)
                    .allowHardware(false)
                    .build()
                val result = (Coil.imageLoader(context).execute(req) as? SuccessResult)?.drawable
                originalBitmap = (result as? BitmapDrawable)?.bitmap
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Buat bitmap dengan badge tombol Play di tengah thumbnail (seperti tampilan Go VideoTube di screenshot)
        val finalLargeIcon = originalBitmap?.let { drawPlayOverlay(it) }

        // Siapkan PendingIntent untuk membuka video secara langsung saat notifikasi diklik
        val videoUri = android.net.Uri.parse("https://www.youtube.com/watch?v=${candidate.id}")
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = videoUri
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_VIDEO_ID, candidate.id)
            putExtra("auto_play", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            candidate.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelTitle = if (candidate.uploaderName.isNotBlank()) candidate.uploaderName else "KATube"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_katube) // Ikon resmi yang muncul di status bar atas (sejajar jam)
            .setContentTitle(candidate.title)
            .setContentText("$channelTitle • Disarankan untuk Anda")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true) // Otomatis terhapus begitu diklik oleh pengguna
            .setContentIntent(pendingIntent)
            .setColor(Color.parseColor("#F2B705")) // Aksen warna emas resmi KATube

        if (finalLargeIcon != null) {
            builder.setLargeIcon(finalLargeIcon)
            // BigPictureStyle untuk tampilan diperluas dengan thumbnail besar
            val bigStyle = NotificationCompat.BigPictureStyle()
                .bigPicture(originalBitmap ?: finalLargeIcon)
                .setSummaryText(candidate.title)
            builder.setStyle(bigStyle)
        }

        withContext(Dispatchers.Main) {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }
        lastShownNotificationTime = System.currentTimeMillis()

        return@withContext true
    }

    /**
     * Menggambar lingkaran semi-transparan dan segitiga ikon Play putih di tengah gambar
     * persis seperti gaya notifikasi video Go VideoTube / YouTube.
     */
    private fun drawPlayOverlay(src: Bitmap): Bitmap {
        return try {
            val bitmap = src.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bitmap)

            val cx = bitmap.width / 2f
            val cy = bitmap.height / 2f
            val radius = bitmap.width.coerceAtMost(bitmap.height) * 0.18f

            // Lingkaran bayangan semi-transparan
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(140, 0, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, radius, shadowPaint)

            // Segitiga putih play
            val playPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            val playSize = radius * 0.85f
            val path = Path().apply {
                moveTo(cx - playSize * 0.4f, cy - playSize * 0.55f)
                lineTo(cx + playSize * 0.6f, cy)
                lineTo(cx - playSize * 0.4f, cy + playSize * 0.55f)
                close()
            }
            canvas.drawPath(path, playPaint)

            bitmap
        } catch (_: Exception) {
            src
        }
    }

    /**
     * Menjadwalkan notifikasi rekomendasi otomatis setiap hari (siang & malam).
     * Bekerja secara otomatis pada waktu-waktu terbaik (12:00 dan 19:00 WIB) setiap harinya.
     */
    fun scheduleDailyRecommendations() {
        val am = alarmManager ?: return

        // Slot 1: Jam 12:00 Siang (Waktu rehat makan siang)
        scheduleDailySlot(am, requestCode = 9101, hour = 12, minute = 0)

        // Slot 2: Jam 19:00 Malam (Waktu santai malam)
        scheduleDailySlot(am, requestCode = 9102, hour = 19, minute = 0)
    }

    private fun scheduleDailySlot(am: AlarmManager, requestCode: Int, hour: Int, minute: Int) {
        val intent = Intent(context, RecommendationNotificationReceiver::class.java).apply {
            action = ACTION_SHOW_RECOMMENDATION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1) // Jika waktu hari ini sudah lewat, jadwalkan untuk besok
            }
        }

        try {
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Membatalkan seluruh jadwal notifikasi harian (jika dinonaktifkan pengguna di pengaturan).
     */
    fun cancelDailyRecommendations() {
        val am = alarmManager ?: return
        listOf(9101, 9102, 9001, 9002).forEach { reqCode ->
            val intent = Intent(context, RecommendationNotificationReceiver::class.java).apply {
                action = ACTION_SHOW_RECOMMENDATION
            }
            val pi = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }
    }

    /**
     * Kompatibilitas mundur dengan inisialisasi lama.
     */
    fun schedulePeriodicRecommendation() {
        scheduleDailyRecommendations()
    }

    /**
     * Menjadwalkan notifikasi rekomendasi setelah pengguna tidak aktif (misal 2 jam setelah keluar aplikasi).
     */
    fun scheduleDelayedRecommendation(delayMinutes: Long = 120L) {
        val am = alarmManager ?: return
        val intent = Intent(context, RecommendationNotificationReceiver::class.java).apply {
            action = ACTION_SHOW_RECOMMENDATION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = SystemClock.elapsedRealtime() + (delayMinutes * 60 * 1000L)
        try {
            am.set(
                AlarmManager.ELAPSED_REALTIME,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
