package id.katube.core.util

import java.io.IOException

/**
 * Hirarki exception terpusat untuk KATube.
 */
sealed class KATubeException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class NetworkError(message: String = "Koneksi internet bermasalah. Periksa jaringan Anda.", cause: Throwable? = null)
        : KATubeException(message, cause)

    class RateLimit(message: String = "YouTube membatasi permintaan sementara (reCAPTCHA/429). Coba lagi nanti.", cause: Throwable? = null)
        : KATubeException(message, cause)

    class ContentUnavailable(message: String = "Video atau konten ini tidak tersedia atau dihapus.", cause: Throwable? = null)
        : KATubeException(message, cause)

    class ParsingError(message: String = "Gagal memproses data dari YouTube. Pembaruan extractor mungkin diperlukan.", cause: Throwable? = null)
        : KATubeException(message, cause)

    class Unknown(message: String = "Terjadi kesalahan tidak terduga.", cause: Throwable? = null)
        : KATubeException(message, cause)
}
