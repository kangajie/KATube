package id.katube.player

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import javax.inject.Named
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import id.katube.R
import id.katube.core.network.OkHttpDownloader
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.DefaultMediaNotificationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Layanan pemutar latar belakang AndroidX Media3 (MediaSessionService).
 * Memutar video & audio di latar belakang, menangani notifikasi sistem,
 * dan menggabungkan stream terpisah (MergingMediaSource) secara otomatis.
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerController: PlayerController

    @Inject
    @Named("streamingClient")
    lateinit var streamingClient: OkHttpClient

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(streamingClient)
            .setUserAgent(OkHttpDownloader.USER_AGENT)

        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        val progressiveFactory = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory as ExtractorsFactory)
        val hlsFactory = HlsMediaSource.Factory(dataSourceFactory)

        val defaultMediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory as ExtractorsFactory)

        // Custom MediaSource.Factory yang mendukung MergingMediaSource & HLS serta mempertahankan metadata (Title, Artist, Artwork) dan Teks Subtitle
        val customMediaSourceFactory = object : MediaSource.Factory {
            override fun setDrmSessionManagerProvider(drmSessionManagerProvider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider) = this
            override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy) = this
            override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER, C.CONTENT_TYPE_HLS)

            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val extras: Bundle? = mediaItem.mediaMetadata.extras
                val isLive = extras?.getBoolean("is_live", false) == true
                val hlsUrl = extras?.getString("hls_url")
                val videoUrl = extras?.getString("video_url")
                val audioUrl = extras?.getString("audio_url")

                val subUrls = extras?.getStringArrayList("sub_urls").orEmpty()
                val subLangs = extras?.getStringArrayList("sub_langs").orEmpty()
                val subLabels = extras?.getStringArrayList("sub_labels").orEmpty()
                val subFormats = extras?.getStringArrayList("sub_formats").orEmpty()

                val subtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
                for (i in subUrls.indices) {
                    val url = subUrls[i]
                    val lang = subLangs.getOrNull(i).orEmpty()
                    val label = subLabels.getOrNull(i).orEmpty()
                    val format = subFormats.getOrNull(i).orEmpty()
                    val mimeType = if (format.contains("ttml", ignoreCase = true)) {
                        androidx.media3.common.MimeTypes.APPLICATION_TTML
                    } else {
                        androidx.media3.common.MimeTypes.TEXT_VTT
                    }
                    subtitleConfigs.add(
                        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(url))
                            .setMimeType(mimeType)
                            .setLanguage(lang)
                            .setLabel(label)
                            .build()
                    )
                }

                // 1. Live Stream
                if (isLive && !hlsUrl.isNullOrEmpty()) {
                    val liveMediaItem = mediaItem.buildUpon()
                        .setUri(hlsUrl)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .build()
                    return hlsFactory.createMediaSource(liveMediaItem)
                }

                // 2. Gabungkan stream video-only + audio-only jika keduanya tersedia
                // Pertahankan mediaMetadata asli pada videoMediaItem agar player mengetahui Judul, Channel, & Thumbnail
                // clipDurations = true memastikan player langsung STATE_ENDED saat stream audio selesai tanpa macet buffering di akhir
                if (!videoUrl.isNullOrEmpty() && !audioUrl.isNullOrEmpty()) {
                    val videoMediaItem = mediaItem.buildUpon()
                        .setUri(videoUrl)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .build()
                    val audioMediaItem = MediaItem.fromUri(audioUrl)
                    val videoSource = defaultMediaSourceFactory.createMediaSource(videoMediaItem)
                    val audioSource = progressiveFactory.createMediaSource(audioMediaItem)
                    return MergingMediaSource(
                        /* adjustPeriodTimeOffsets = */ false,
                        /* clipDurations = */ false,
                        *arrayOf<MediaSource>(videoSource, audioSource)
                    )
                }

                // 3. Fallback progressive biasa dengan dukungan subtitle
                val singleMediaItem = mediaItem.buildUpon()
                    .setSubtitleConfigurations(subtitleConfigs)
                    .build()
                return defaultMediaSourceFactory.createMediaSource(singleMediaItem)
            }
        }

        // LoadControl satset, ringan, & stabil:
        // - bufferForPlaybackMs = 1.000ms: Video mulai instan dalam 1 detik buffer
        // - bufferForPlaybackAfterRebufferMs = 2.000ms: Pemulihan cepat saat koneksi berfluktuasi
        // - minBufferMs = 15.000ms, maxBufferMs = 50.000ms: Hemat bandwidth memori HP
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ 10_000,
                /* retainBackBufferFromKeyframe = */ true
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(customMediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // Pasang Metadata & Unduh Artwork Thumbnail ke notifikasi sistem jika belum tersedia
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) return
                val metadata = mediaItem.mediaMetadata
                val currentTitle = metadata.title ?: playerController.currentStreamBundle.value?.title
                val currentArtist = metadata.artist ?: playerController.currentStreamBundle.value?.uploaderName

                // Perbarui SessionActivity Intent dengan videoId aktif agar ketika notifikasi status bar diklik, langsung membuka video yang sedang diputar!
                val activeVideoId = mediaItem.mediaId
                if (activeVideoId.isNotBlank()) {
                    val updateIntent = Intent(this@PlaybackService, id.katube.MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("open_watch_screen", true)
                        putExtra("open_video_id", activeVideoId)
                    }
                    val updatePendingIntent = PendingIntent.getActivity(
                        this@PlaybackService,
                        1001,
                        updateIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    mediaSession?.setSessionActivity(updatePendingIntent)
                }

                // Pasang langsung metadata teks lagu baru agar Lock Screen & Notifikasi berganti seketika!
                val immediateMetadata = MediaMetadata.Builder()
                    .setTitle(currentTitle)
                    .setArtist(currentArtist)
                    .setDisplayTitle(metadata.displayTitle ?: currentTitle)
                    .setSubtitle(metadata.subtitle ?: currentArtist)
                    .setArtworkData(metadata.artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .setArtworkUri(metadata.artworkUri)
                    .build()
                player.setPlaylistMetadata(immediateMetadata)

                val artUri = metadata.artworkUri?.toString()
                if (!artUri.isNullOrEmpty() && metadata.artworkData == null) {
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            delay(2500) // Tunda unduhan gambar agar bandwidth awal 100% dipakai pemutaran video
                            val req = coil.request.ImageRequest.Builder(this@PlaybackService)
                                .data(artUri)
                                .allowHardware(false)
                                .build()
                            val res = (coil.Coil.imageLoader(this@PlaybackService).execute(req) as? coil.request.SuccessResult)?.drawable
                            val bmp = (res as? android.graphics.drawable.BitmapDrawable)?.bitmap
                            if (bmp != null) {
                                val stream = java.io.ByteArrayOutputStream()
                                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
                                val byteArray = stream.toByteArray()
                                withContext(Dispatchers.Main) {
                                    val activeItem = player.currentMediaItem
                                    if (activeItem != null && activeItem.mediaId == mediaItem.mediaId) {
                                        val fullMetadata = MediaMetadata.Builder()
                                            .setTitle(currentTitle)
                                            .setArtist(currentArtist)
                                            .setDisplayTitle(metadata.displayTitle ?: currentTitle)
                                            .setSubtitle(metadata.subtitle ?: currentArtist)
                                            .setArtworkData(byteArray, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                            .setArtworkUri(metadata.artworkUri)
                                            .build()
                                        player.setPlaylistMetadata(fullMetadata)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    playerController.handlePlaybackEnded()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                error.printStackTrace()
                // Jangan ganti lagu otomatis di tengah video saat buffering atau gangguan jaringan sementara.
                // Hanya izinkan lanjut jika video memang sudah di detik-detik akhir.
                val activePlayer = mediaSession?.player ?: return
                val pos = activePlayer.currentPosition
                val dur = activePlayer.duration
                val isNearEnd = dur > 10_000L && (pos >= dur - 3_000L || (dur > 0 && pos >= (dur * 0.98f).toLong()))
                if (isNearEnd && playerController.isAutoplayEnabled.value) {
                    serviceScope.launch {
                        kotlinx.coroutines.delay(1000)
                        playerController.handlePlaybackEnded()
                    }
                }
            }
        })

        // ForwardingPlayer: Memastikan tombol Next (>>|) dan Previous (|<<) selalu aktif di Lock Screen & Notifikasi
        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return command == Player.COMMAND_SEEK_TO_NEXT ||
                       command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                       command == Player.COMMAND_SEEK_TO_PREVIOUS ||
                       command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
                       super.isCommandAvailable(command)
            }

            override fun hasNextMediaItem(): Boolean = true

            override fun hasPreviousMediaItem(): Boolean = true

            override fun seekToNext() {
                playerController.playNextInQueue()
            }

            override fun seekToNextMediaItem() {
                playerController.playNextInQueue()
            }

            override fun seekToPrevious() {
                playerController.playPreviousInQueue()
            }

            override fun seekToPreviousMediaItem() {
                playerController.playPreviousInQueue()
            }
        }

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, id.katube.MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_watch_screen", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().build()
                    val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .setAvailablePlayerCommands(playerCommands)
                        .build()
                }

                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int
                ): Int {
                    when (playerCommand) {
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                            playerController.playNextInQueue()
                            return androidx.media3.session.SessionResult.RESULT_SUCCESS
                        }
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                            playerController.playPreviousInQueue()
                            return androidx.media3.session.SessionResult.RESULT_SUCCESS
                        }
                    }
                    return super.onPlayerCommandRequest(session, controller, playerCommand)
                }
            })
            .build()

        val playbackChannelId = "katube_playback_channel_v2"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                notificationManager.deleteNotificationChannel("default_channel_id")
                notificationManager.deleteNotificationChannel("katube_playback_channel")
            } catch (_: Exception) {}

            val channel = android.app.NotificationChannel(
                playbackChannelId,
                getString(R.string.notification_channel_playback),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_playback_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Notification Provider kustom: Judul Video, Nama Channel, Logo Resmi KATube di badge arah panah
        val notificationProvider = object : DefaultMediaNotificationProvider(
            this,
            { 1001 },
            playbackChannelId,
            R.string.notification_channel_playback
        ) {
            override fun getNotificationContentTitle(mediaMetadata: MediaMetadata): CharSequence? {
                val bundleTitle = playerController.currentStreamBundle.value?.title
                if (!bundleTitle.isNullOrBlank()) return bundleTitle

                val itemTitle = mediaSession?.player?.currentMediaItem?.mediaMetadata?.title
                if (!itemTitle.isNullOrBlank()) return itemTitle

                val title = mediaMetadata.title
                if (!title.isNullOrBlank()) return title
                val displayTitle = mediaMetadata.displayTitle
                if (!displayTitle.isNullOrBlank()) return displayTitle
                return getString(R.string.app_name)
            }

            override fun getNotificationContentText(mediaMetadata: MediaMetadata): CharSequence? {
                val bundleUploader = playerController.currentStreamBundle.value?.uploaderName
                if (!bundleUploader.isNullOrBlank()) return bundleUploader

                val itemArtist = mediaSession?.player?.currentMediaItem?.mediaMetadata?.artist
                if (!itemArtist.isNullOrBlank()) return itemArtist

                val artist = mediaMetadata.artist
                if (!artist.isNullOrBlank()) return artist
                val subtitle = mediaMetadata.subtitle
                if (!subtitle.isNullOrBlank()) return subtitle
                return null
            }
        }
        notificationProvider.setSmallIcon(R.drawable.ic_notification_katube)
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // Jika sedang memutar lagu di latar belakang, TETAP JALANKAN layanan!
        // Jangan hentikan layanan saat aplikasi ditutup/di-swipe dari daftar aplikasi baru-baru ini.
        if (player == null || (!player.playWhenReady && player.playbackState != Player.STATE_BUFFERING) || player.playbackState == Player.STATE_IDLE) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
