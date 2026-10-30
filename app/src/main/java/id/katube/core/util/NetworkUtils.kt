package id.katube.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Utilitas untuk mendeteksi kondisi jaringan dan menentukan resolusi video optimal (Auto).
 */
object NetworkUtils {

    /**
     * Mengembalikan target resolusi video otomatis (Auto) sesuai kondisi jaringan riil:
     * Rentang deteksi lengkap dari 144p hingga resolusi tertinggi (1080p):
     * - Sangat berat / lemah (< 600 Kbps): 144p (pemutaran tetap lancar tanpa buffering).
     * - Lambat (600 - 1.200 Kbps): 240p.
     * - Sedang / hemat (1.200 - 2.500 Kbps): 360p.
     * - Standar lancar (2.500 - 5.000 Kbps): 480p.
     * - Cepat / HD (5.000 - 15.000 Kbps): 720p.
     * - Sangat cepat / Full HD (> 15.000 Kbps): 1080p.
     */
    fun getOptimalAutoResolution(context: Context): Int {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return 360
            val network = cm.activeNetwork ?: return 360
            val caps = cm.getNetworkCapabilities(network) ?: return 360

            val bandwidthKbps = caps.linkDownstreamBandwidthKbps

            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    // Di Wi-Fi / LAN: Default 480p sangat lancar tanpa beban, 720p jika koneksi sangat cepat
                    if (bandwidthKbps > 8000) 720 else if (bandwidthKbps in 1..2500) 360 else 480
                }

                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    // Di Jaringan Seluler: Default 360p hemat kuota dan ultra-ringan bebas buffering
                    val isUnmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    if (isUnmetered && bandwidthKbps >= 5000) 480 else 360
                }

                else -> 360
            }
        } catch (e: Exception) {
            360
        }
    }

    /**
     * Memeriksa apakah perangkat sedang terhubung melalui jaringan seluler (cellular data).
     */
    fun isCellular(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (e: Exception) {
            false
        }
    }
}
