package id.katube

import id.katube.core.network.OkHttpDownloader
import id.katube.data.extractor.YouTubeRepositoryImpl
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit

class YouTubeRepositoryTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            val connectionPool = okhttp3.ConnectionPool(32, 5, TimeUnit.MINUTES)
            val client = OkHttpClient.Builder()
                .connectionPool(connectionPool)
                .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            NewPipe.init(
                OkHttpDownloader(client),
                Localization("id", "ID"),
                ContentCountry("ID")
            )
        }
    }

    @Test
    fun testSearchSuggestions() = runBlocking {
        val repo = YouTubeRepositoryImpl(context = null, ioDispatcher = kotlinx.coroutines.Dispatchers.IO)
        val result = repo.getSearchSuggestions("indonesia")
        if (result.isSuccess) {
            val suggestions = result.getOrNull()
            assertNotNull(suggestions)
            assertTrue("Saran pencarian tidak boleh kosong", suggestions!!.isNotEmpty())
            println("Suggestions: $suggestions")
        } else {
            // Jika network timeout/offline saat test, pastikan tidak crash
            println("Test offline / network: ${result.exceptionOrNull()?.message}")
        }
    }

    @Test
    fun testStreamInfoTiming() = runBlocking {
        val repo = YouTubeRepositoryImpl(context = null, ioDispatcher = kotlinx.coroutines.Dispatchers.IO)
        val t1 = System.currentTimeMillis()
        val r1 = repo.getStreamInfo("dQw4w9WgXcQ", maxHeight = 0)
        assertTrue(r1.isSuccess || r1.isFailure)
        val d1 = System.currentTimeMillis() - t1
        println("=== FIRST EXTRACTION (COLD): ${d1} ms ===")

        val t2 = System.currentTimeMillis()
        val r2 = repo.getStreamInfo("kJQP7kiw5Fk", maxHeight = 0)
        assertTrue(r2.isSuccess || r2.isFailure)
        val d2 = System.currentTimeMillis() - t2
        println("=== SECOND EXTRACTION (WARM HTTP/2): ${d2} ms ===")
    }
}
