package id.katube.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import id.katube.core.network.OkHttpDownloader
import id.katube.data.auth.AuthRepository
import id.katube.data.sync.YouTubeSyncRepository
import id.katube.ui.theme.Gold
import id.katube.ui.theme.KATubeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Halaman Login Akun YouTube / Google persis seperti Go VideoTube (GoTube).
 * Tampilan bersih murni: hanya ada tombol kembali dan judul di atas, tanpa tombol manual yang membingungkan.
 * Sistem secara otomatis mendeteksi keberhasilan login begitu pengguna selesai memasukkan email & kata sandi
 * dan halaman dialihkan kembali ke YouTube dengan cookie sesi lengkap.
 */
@AndroidEntryPoint
class GoogleLoginActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var syncRepository: YouTubeSyncRepository

    // URL sign-in resmi Google dengan alur redirect langsung ke YouTube Mobile/Web
    private val loginUrl = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https%3A%2F%2Fm.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            KATubeTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LoginContent(
                        loginUrl = loginUrl,
                        onClose = { finish() },
                        onLoginSuccess = { cookies ->
                            handleSuccessfulLogin(cookies)
                        }
                    )
                }
            }
        }
    }

    private fun handleSuccessfulLogin(cookies: String) {
        lifecycleScope.launch {
            // 1. Simpan sesi awal dan aktifkan cookie downloader
            authRepository.saveLogin(
                email = "Akun YouTube Terhubung",
                displayName = "Pengguna YouTube",
                cookies = cookies,
            )
            OkHttpDownloader.currentCookies = cookies

            // 2. Ambil profil terlebih dahulu (nama asli, @handle, foto) sebelum menutup
            try {
                syncRepository.syncAccountProfile(cookies)
            } catch (_: Exception) {}

            // 3. Muat beranda personal langsung agar tampilan beranda langsung berubah sesuai akun
            try {
                syncRepository.fetchHomeFeed(cookies)
            } catch (_: Exception) {}

            // 4. Jalankan sinkronisasi menyeluruh di latar belakang
            syncRepository.syncAll()

            Toast.makeText(this@GoogleLoginActivity, "Berhasil masuk ke akun YouTube!", Toast.LENGTH_SHORT).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun LoginContent(
        loginUrl: String,
        onClose: () -> Unit,
        onLoginSuccess: (String) -> Unit,
    ) {
        var progress by remember { mutableFloatStateOf(0f) }
        var isSuccessHandled by remember { mutableStateOf(false) }
        var isFinalizing by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        Column(modifier = Modifier.fillMaxSize()) {
            // Header TopBar Bersih Ala Go VideoTube: Hanya tombol Kembali dan Judul
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White,
                    )
                }
                Text(
                    text = "Masuk ke Akun YouTube",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            // Indikator Loading Halaman
            if (progress < 1.0f && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = Gold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }

            // WebView Login Google / YouTube
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true

                            // User-Agent Chrome Android resmi: Menghapus "; wv" dan "Version/4.0"
                            // agar Google tidak memblokir dengan peringatan "This browser or app may not be secure"
                            val originalUa = settings.userAgentString.orEmpty()
                            val cleanUa = if (originalUa.contains("; wv")) {
                                originalUa.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+\\s*"), "")
                            } else {
                                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                            }
                            settings.userAgentString = cleanUa

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                }
                            }

                            // Fungsi deteksi otomatis keberhasilan login setelah redirect selesai ke YouTube
                            fun checkAndVerify(url: String?) {
                                if (isSuccessHandled) return
                                val safeUrl = url.orEmpty().lowercase()

                                // 1. DILARANG KERAS menganggap berhasil jika URL masih di alur autentikasi Google atau penukaran cookie:
                                if (safeUrl.contains("accounts.google.com") ||
                                    safeUrl.contains("checkcookie") ||
                                    safeUrl.contains("servicelogin") ||
                                    safeUrl.contains("/signin") ||
                                    safeUrl.contains("action_handle_signin")
                                ) {
                                    return
                                }

                                // 2. Browser harus sudah mendarat di domain YouTube (misal m.youtube.com atau www.youtube.com)
                                if (!safeUrl.contains("youtube.com")) {
                                    return
                                }

                                // 3. Ambil dan validasi cookie autentikasi YouTube
                                cookieManager.flush()
                                val ytCookies = cookieManager.getCookie("https://www.youtube.com").orEmpty()
                                val mYtCookies = cookieManager.getCookie("https://m.youtube.com").orEmpty()
                                val gCookies = cookieManager.getCookie("https://accounts.google.com").orEmpty()
                                val dotGCookies = cookieManager.getCookie("https://google.com").orEmpty()

                                // Token otentikasi YouTube mutlak: LOGIN_INFO atau (SAPISID dan SID)
                                val hasLoginInfo = ytCookies.contains("LOGIN_INFO") || mYtCookies.contains("LOGIN_INFO")
                                val hasYtSapisidAndSid = (ytCookies.contains("SAPISID") || mYtCookies.contains("SAPISID")) &&
                                        (ytCookies.contains("SID") || mYtCookies.contains("SID"))

                                if (hasLoginInfo || hasYtSapisidAndSid) {
                                    isSuccessHandled = true
                                    isFinalizing = true
                                    val merged = mergeCookieStrings(dotGCookies, gCookies, mYtCookies, ytCookies)
                                    onLoginSuccess(merged)
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val targetUrl = request?.url?.toString().orEmpty()

                                    // Blokir intent scheme ke aplikasi YouTube resmi atau browser eksternal agar tetap di WebView
                                    if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                                        return true
                                    }

                                    checkAndVerify(targetUrl)
                                    return false
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    checkAndVerify(url)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    checkAndVerify(url)
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    checkAndVerify(url)
                                }
                            }

                            // Pengecekan berkala otomatis setiap 1 detik saat proses login sedang berjalan
                            scope.launch {
                                while (!isSuccessHandled && isActive) {
                                    delay(1000)
                                    checkAndVerify(this@apply.url)
                                }
                            }

                            loadUrl(loginUrl)
                        }
                    }
                )

                // Overlay Loading saat profil sedang dihubungkan
                if (isFinalizing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            CircularProgressIndicator(
                                color = Gold,
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 3.dp,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Menghubungkan Akun YouTube...",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Menyinkronkan profil, riwayat, dan playlist",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun mergeCookieStrings(vararg cookieSources: String?): String {
        val map = linkedMapOf<String, String>()
        for (source in cookieSources) {
            if (source.isNullOrBlank()) continue
            val pairs = source.split(";")
            for (pair in pairs) {
                val trimmed = pair.trim()
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx > 0) {
                    val key = trimmed.substring(0, eqIdx).trim()
                    val value = trimmed.substring(eqIdx + 1).trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        map[key] = value
                    }
                }
            }
        }
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}
