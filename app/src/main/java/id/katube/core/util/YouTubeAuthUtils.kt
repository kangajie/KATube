package id.katube.core.util

import java.security.MessageDigest

/**
 * Utilitas untuk autentikasi sesi YouTube Innertube.
 * Menghasilkan token SAPISIDHASH yang mengizinkan request API bertindak atas nama akun pengguna.
 */
object YouTubeAuthUtils {

    /**
     * Menghitung nilai header SAPISIDHASH yang dibutuhkan oleh API internal YouTube (Innertube).
     * Format: SAPISIDHASH <unix_timestamp>_<sha1(timestamp + " " + sapisid + " " + origin)>
     */
    fun generateSapisidHash(cookies: String, origin: String = "https://www.youtube.com"): String? {
        val sapisid = extractCookieValue(cookies, "SAPISID")
        val sapisid3p = extractCookieValue(cookies, "__Secure-3PAPISID")
        val sapisid1p = extractCookieValue(cookies, "__Secure-1PAPISID")

        val effective = sapisid ?: sapisid3p ?: sapisid1p ?: return null
        val timestamp = System.currentTimeMillis() / 1000L

        val hash = sha1Hex("$timestamp $effective $origin")
        val sb = StringBuilder("SAPISIDHASH ${timestamp}_$hash")

        if (!sapisid1p.isNullOrBlank()) {
            val hash1p = sha1Hex("$timestamp $sapisid1p $origin")
            sb.append(" SAPISID1PHASH ${timestamp}_$hash1p")
        }
        if (!sapisid3p.isNullOrBlank()) {
            val hash3p = sha1Hex("$timestamp $sapisid3p $origin")
            sb.append(" SAPISID3PHASH ${timestamp}_$hash3p")
        }

        return sb.toString()
    }

    /**
     * Mengekstrak nilai cookie tertentu dari string cookie gabungan.
     */
    fun extractCookieValue(cookies: String, cookieName: String): String? {
        if (cookies.isBlank()) return null
        val parts = cookies.split(";")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("$cookieName=")) {
                return trimmed.substringAfter("$cookieName=").trim().trim('"')
            }
        }
        return null
    }

    private fun sha1Hex(text: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
