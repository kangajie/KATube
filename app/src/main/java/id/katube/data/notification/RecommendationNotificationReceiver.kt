package id.katube.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receiver sistem untuk menerima pemicu AlarmManager dan
 * menampilkan notifikasi rekomendasi video sesuai genre favorit pengguna.
 */
@AndroidEntryPoint
class RecommendationNotificationReceiver : BroadcastReceiver() {

    @Inject
    lateinit var recommendationManager: RecommendationNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.MY_PACKAGE_REPLACED" -> {
                // Saat HP selesai restart atau aplikasi diperbarui, aktifkan kembali jadwal harian otomatis
                try {
                    recommendationManager.scheduleDailyRecommendations()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            RecommendationNotificationManager.ACTION_SHOW_RECOMMENDATION -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        recommendationManager.fetchAndPostRecommendation()
                        // Pastikan jadwal hari berikutnya tetap berjalan
                        recommendationManager.scheduleDailyRecommendations()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
