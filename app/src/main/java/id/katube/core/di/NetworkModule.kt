package id.katube.core.di

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.katube.core.network.OkHttpDownloader
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Modul Hilt untuk dependensi level jaringan.
 * OkHttpClient dibuat satu instance (Singleton) dan dibagikan ke seluruh app.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val connectionPool = okhttp3.ConnectionPool(32, 5, TimeUnit.MINUTES)
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 20
        }
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = Cache(cacheDir, 50L * 1024 * 1024) // 50 MB HTTP Cache

        return OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
            .dispatcher(dispatcher)
            .cache(cache)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpDownloader(client: OkHttpClient): OkHttpDownloader =
        OkHttpDownloader(client)

    @Provides
    @Singleton
    @javax.inject.Named("streamingClient")
    fun provideStreamingOkHttpClient(): OkHttpClient {
        val connectionPool = okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES)
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        return OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .dispatcher(dispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Gunakan HTTP/1.1 khusus untuk streaming media GoogleVideo (YouTube).
            // HTTP/1.1 membuka koneksi TCP terpisah dan independen untuk stream video dan stream audio,
            // menghindari throttling multiplexing dan stall flow-control (WINDOW_UPDATE) pada googlevideo.com.
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .build()
    }
}
