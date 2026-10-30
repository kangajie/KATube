package id.katube.core.network

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Implementasi [Downloader] NewPipeExtractor yang menggunakan OkHttp.
 * Dipasang di [id.katube.KATubeApp] lewat NewPipe.init().
 */
class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val url = request.url()
        val isCacheable = url.contains("visitor_id") || url.contains("sw.js") || url.contains("/base.js")

        if (isCacheable) {
            val cached = staticResponseCache[url]
            if (cached != null && (System.currentTimeMillis() - cached.timestamp < 3600_000L)) {
                return cached.response
            }
        }

        val body = request.dataToSend()?.toRequestBody()
        val reqBuilder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        request.headers().forEach { (name, values) ->
            reqBuilder.removeHeader(name)
            values.forEach { reqBuilder.addHeader(name, it) }
        }

        // Pastikan User-Agent selalu Google Chrome modern agar tidak ditimpa oleh default NewPipeExtractor (Firefox)
        reqBuilder.removeHeader("User-Agent")
        reqBuilder.addHeader("User-Agent", USER_AGENT)

        if (currentCookies.isNotBlank() && reqBuilder.build().header("Cookie").isNullOrBlank()) {
            reqBuilder.addHeader("Cookie", currentCookies)
        }

        client.newCall(reqBuilder.build()).execute().use { res ->
            if (res.code == 429) {
                throw ReCaptchaException("Rate limit / reCAPTCHA terdeteksi", request.url())
            }
            val response = Response(
                res.code,
                res.message,
                res.headers.toMultimap(),
                res.body?.string(),
                res.request.url.toString()
            )
            if (isCacheable && res.code == 200) {
                staticResponseCache[url] = CachedResponse(response)
            }
            return response
        }
    }

    companion object {
        /**
         * Cookie sesi akun YouTube aktif yang disinkronkan dari AuthRepository.
         */
        @Volatile
        var currentCookies: String = ""

        /**
         * User-Agent Chrome modern agar terbebas dari penalti delay 5 detik dan throttling bandwidth dari YouTube/googlevideo CDN.
         */
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

        private class CachedResponse(
            val response: Response,
            val timestamp: Long = System.currentTimeMillis()
        )

        private val staticResponseCache = java.util.concurrent.ConcurrentHashMap<String, CachedResponse>()
    }
}
