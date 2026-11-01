package id.katube.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import id.katube.data.model.StreamBundle
import id.katube.data.model.VideoQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient

@UnstableApi
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext val context: Context,
    private val repository: id.katube.data.extractor.YouTubeRepository,
    private val preferencesRepository: id.katube.data.preferences.UserPreferencesRepository,
    private val syncRepository: id.katube.data.sync.YouTubeSyncRepository,
    @Named("streamingClient") private val streamingClient: OkHttpClient,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var mediaController: MediaController? = null

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private val wakeLock = try {
        powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "katube:player_wakelock"
        )?.apply { setReferenceCounted(false) }
    } catch (_: Exception) {
        null
    }

    private val wifiLock = try {
        @Suppress("DEPRECATION")
        wifiManager?.createWifiLock(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            },
            "katube:player_wifilock"
        )?.apply { setReferenceCounted(false) }
    } catch (_: Exception) {
        null
    }

    var pendingPreviewItem: id.katube.data.model.VideoItem? = null

    @Volatile
    private var isTransitioningAutoplay: Boolean = false

    fun acquireWakeLocks(timeoutMs: Long = 0L) {
        try {
            if (wakeLock?.isHeld != true) {
                if (timeoutMs > 0L) {
                    wakeLock?.acquire(timeoutMs)
                } else {
                    wakeLock?.acquire()
                }
            }
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
            }
        } catch (_: Exception) {}
    }

    fun releaseWakeLocks() {
        if (isTransitioningAutoplay) return
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
            if (wifiLock?.isHeld == true) {
                wifiLock.release()
            }
        } catch (_: Exception) {}
    }

    private fun startPlaybackService() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}
    }

    private val _queue = MutableStateFlow<List<id.katube.data.model.VideoItem>>(emptyList())
    val queue: StateFlow<List<id.katube.data.model.VideoItem>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<id.katube.data.model.PlaylistItem?>(null)
    val currentPlaylist: StateFlow<id.katube.data.model.PlaylistItem?> = _currentPlaylist.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _playerState = MutableStateFlow<Player?>(null)
    val playerState: StateFlow<Player?> = _playerState.asStateFlow()
    val player: Player? get() = _playerState.value

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isAudioOnly = MutableStateFlow(false)
    val isAudioOnly: StateFlow<Boolean> = _isAudioOnly.asStateFlow()

    private val _currentStreamBundle = MutableStateFlow<StreamBundle?>(null)
    val currentStreamBundle: StateFlow<StreamBundle?> = _currentStreamBundle.asStateFlow()

    private val _isMiniPlayerVisible = MutableStateFlow(false)
    val isMiniPlayerVisible: StateFlow<Boolean> = _isMiniPlayerVisible.asStateFlow()

    private val _isAutoQuality = MutableStateFlow(true)
    val isAutoQuality: StateFlow<Boolean> = _isAutoQuality.asStateFlow()

    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    fun setAppForegroundState(inForeground: Boolean, inPip: Boolean = false) {
        _isAppInForeground.value = inForeground
        _isInPipMode.value = inPip
    }

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    val isAutoplayEnabled = MutableStateFlow(true)
    var onPlaybackProgress: ((videoId: String, positionMs: Long, durationMs: Long, isEnded: Boolean) -> Unit)? = null

    private var pendingPlayBundle: Triple<StreamBundle, Boolean, Long>? = null
    @Volatile
    private var pendingSeekPositionMs: Long = 0L

    private var sleepTimerJob: Job? = null
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    private val _selectedSubtitleLanguage = MutableStateFlow<String?>("OFF")
    val selectedSubtitleLanguage: StateFlow<String?> = _selectedSubtitleLanguage.asStateFlow()

    fun setSubtitleLanguage(languageCode: String?) {
        _selectedSubtitleLanguage.value = languageCode
        val controller = mediaController ?: return
        if (languageCode == null || languageCode == "OFF") {
            controller.trackSelectionParameters = controller.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            controller.trackSelectionParameters = controller.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(languageCode)
                .build()
        }
    }

    private var progressTrackingJob: Job? = null

    init {
        connectToService()
        scope.launch {
            preferencesRepository.autoplayNext.collect { enabled ->
                isAutoplayEnabled.value = enabled
            }
        }
        scope.launch {
            preferencesRepository.defaultQuality.collect { quality ->
                _isAutoQuality.value = (quality <= 0)
            }
        }
        scope.launch {
            preferencesRepository.defaultSpeed.collect { speed ->
                if (speed > 0f) {
                    _playbackSpeed.value = speed
                    mediaController?.setPlaybackSpeed(speed)
                }
            }
        }
        scope.launch {
            preferencesRepository.defaultAudioOnly.collect { audioOnly ->
                if (_currentStreamBundle.value == null) {
                    _isAudioOnly.value = audioOnly
                }
            }
        }
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                _playerState.value = controller
                setupPlayerListener(controller)

                // Jika PlaybackService di latar belakang sudah memutar lagu, pulihkan state di UI seketika!
                val activeMedia = controller.currentMediaItem
                val activeId = activeMedia?.mediaId
                if (!activeId.isNullOrEmpty()) {
                    _isPlaying.value = controller.isPlaying
                    _playbackState.value = controller.playbackState
                    _currentPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = controller.duration.coerceAtLeast(0L)
                    _bufferedPositionMs.value = controller.bufferedPosition.coerceAtLeast(0L)
                    _isMiniPlayerVisible.value = true

                    if (_currentStreamBundle.value == null || _currentStreamBundle.value?.videoId != activeId) {
                        scope.launch {
                            val maxHeight = getEffectiveMaxHeight()
                            repository.getStreamInfo(activeId, maxHeight = maxHeight).onSuccess { bundle ->
                                _currentStreamBundle.value = bundle
                            }
                        }
                    }

                    startProgressTracking()
                    if (controller.isPlaying) {
                        acquireWakeLocks()
                    }
                }

                pendingPlayBundle?.let { (b, audio, startPos) ->
                    pendingPlayBundle = null
                    playVideo(b, audio, startPositionMs = startPos)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private var bufferingWatchdogJob: Job? = null

    private fun cancelBufferingWatchdog() {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
    }

    private fun checkBufferingStall(player: Player) {
        cancelBufferingWatchdog()
        if (!player.playWhenReady) return

        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        val isNearEnd = dur > 10_000L && (pos >= dur - 3_000L || (dur > 0 && pos >= (dur * 0.98f).toLong()))

        acquireWakeLocks(30_000L)

        // 1. Pantau jika video macet buffering di awal start/transisi (pos <= 1000ms)
        if (pos <= 1000L && player.playWhenReady) {
            bufferingWatchdogJob = scope.launch {
                delay(3500)
                val ctrl = mediaController
                if (ctrl?.playbackState == Player.STATE_BUFFERING && (ctrl.currentPosition <= 1000L) && ctrl.playWhenReady) {
                    try {
                        // Kickstart decoding pipeline jika sinkronisasi keyframe awal tersendat
                        ctrl.seekTo(150)
                        ctrl.play()
                    } catch (_: Exception) {}
                }
            }
        } else if (isNearEnd) {
            // 2. Pantau jika video habis di detik-detik akhir
            bufferingWatchdogJob = scope.launch {
                delay(1800)
                if (mediaController?.playbackState == Player.STATE_BUFFERING && !isTransitioningAutoplay) {
                    handlePlaybackEnded()
                }
            }
        }
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying || player.playWhenReady) {
                    acquireWakeLocks()
                    startProgressTracking()
                } else {
                    val state = mediaController?.playbackState ?: Player.STATE_IDLE
                    // Tetap pantau tracking saat buffering atau pause agar garis buffer putih terus diperbarui secara realtime
                    if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                        stopProgressTracking()
                    }
                    // Hanya lepas wake lock jika user benar-benar PAUSE (playWhenReady == false) dan tidak sedang transisi
                    if (!isTransitioningAutoplay && !player.playWhenReady) {
                        releaseWakeLocks()
                    }
                    val currentVideoId = _currentStreamBundle.value?.videoId.orEmpty()
                    val pos = _currentPositionMs.value
                    val dur = _durationMs.value
                    if (currentVideoId.isNotEmpty() && pos > 1000L) {
                        onPlaybackProgress?.invoke(currentVideoId, pos, dur, true)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playbackState.value = playbackState
                _durationMs.value = player.duration.coerceAtLeast(0L)
                _bufferedPositionMs.value = player.bufferedPosition.coerceAtLeast(0L)

                if (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY) {
                    startProgressTracking()
                }

                if (playbackState == Player.STATE_READY) {
                    cancelBufferingWatchdog()
                    isTransitioningAutoplay = false
                    val targetSeek = pendingSeekPositionMs
                    if (targetSeek > 0L) {
                        pendingSeekPositionMs = 0L
                        if (kotlin.math.abs(player.currentPosition - targetSeek) > 2000L) {
                            player.seekTo(targetSeek)
                        }
                    }
                } else if (playbackState == Player.STATE_BUFFERING) {
                    checkBufferingStall(player)
                } else if (playbackState == Player.STATE_ENDED) {
                    cancelBufferingWatchdog()
                    stopProgressTracking()
                    handlePlaybackEnded(fromStateEnded = true)
                } else if (playbackState == Player.STATE_IDLE) {
                    cancelBufferingWatchdog()
                    stopProgressTracking()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                error.printStackTrace()
                cancelBufferingWatchdog()
                // Jangan otomatis ganti lagu di tengah video jika ada error jaringan sementara.
                // Hanya izinkan lanjut jika video memang sudah di detik-detik akhir.
                val pos = mediaController?.currentPosition ?: 0L
                val dur = mediaController?.duration ?: 0L
                val isNearEnd = dur > 10_000L && (pos >= dur - 3_000L || (dur > 0 && pos >= (dur * 0.98f).toLong()))
                if (isNearEnd && isAutoplayEnabled.value && !isTransitioningAutoplay) {
                    scope.launch {
                        delay(1200)
                        handlePlaybackEnded()
                    }
                }
            }
        })
    }

    @Volatile
    private var isPreparingNewVideo = false

    @Volatile
    private var lastPlaybackEndedTimestamp: Long = 0L

    fun handlePlaybackEnded(fromStateEnded: Boolean = false) {
        cancelBufferingWatchdog()
        if (isPreparingNewVideo) return

        val now = System.currentTimeMillis()
        if (now - lastPlaybackEndedTimestamp < 1000L) {
            return
        }

        val controller = mediaController ?: return
        if (controller.currentMediaItem == null) return

        // Jika bukan dari STATE_ENDED (misal dari polling watchdog), lakukan verifikasi posisi
        if (!fromStateEnded) {
            val pos = controller.currentPosition.coerceAtLeast(0L)
            val dur = controller.duration.coerceAtLeast(0L)
            val isReallyEnded = dur > 5_000L && (pos >= dur - 3_500L || (dur > 0 && pos >= (dur * 0.95f).toLong()))
            if (!isReallyEnded) {
                return
            }
        }

        lastPlaybackEndedTimestamp = now
        acquireWakeLocks(3 * 60 * 1000L)
        isTransitioningAutoplay = true
        val currentVideoId = _currentStreamBundle.value?.videoId.orEmpty()
        if (currentVideoId.isNotEmpty()) {
            val durMs = _durationMs.value
            onPlaybackProgress?.invoke(currentVideoId, durMs, durMs, true)
        }
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                isTransitioningAutoplay = false
                mediaController?.seekTo(0)
                mediaController?.play()
            }
            RepeatMode.ALL -> {
                val videos = _queue.value
                if (videos.isNotEmpty()) {
                    val nextIndex = (_currentIndex.value + 1) % videos.size
                    playQueue(videos, nextIndex, audioOnly = _isAudioOnly.value)
                } else if (isAutoplayEnabled.value) {
                    playAutoplayNext()
                } else {
                    isTransitioningAutoplay = false
                    mediaController?.seekTo(0)
                    mediaController?.play()
                }
            }
            RepeatMode.OFF -> {
                val videos = _queue.value
                val currentVideoId = _currentStreamBundle.value?.videoId.orEmpty()
                val activeIdx = videos.indexOfFirst { it.id == currentVideoId }
                val nextIndex = if (activeIdx >= 0) activeIdx + 1 else _currentIndex.value + 1
                if (videos.isNotEmpty() && nextIndex in videos.indices) {
                    playNextInQueue()
                } else if (isAutoplayEnabled.value) {
                    playAutoplayNext()
                } else {
                    isTransitioningAutoplay = false
                    releaseWakeLocks()
                }
            }
        }
    }

    
    fun addToQueue(video: id.katube.data.model.VideoItem) {
        val currentList = _queue.value.toMutableList()
        val currentBundle = _currentStreamBundle.value

        // Jika antrean kosong namun ada video yang sedang aktif diputar, masukkan video aktif sebagai item pertama antrean
        if (currentList.isEmpty() && currentBundle != null && currentBundle.videoId.isNotBlank()) {
            val currentItem = pendingPreviewItem?.takeIf { it.id == currentBundle.videoId }
                ?: id.katube.data.model.VideoItem(
                    id = currentBundle.videoId,
                    url = "https://www.youtube.com/watch?v=${currentBundle.videoId}",
                    title = currentBundle.title,
                    uploaderName = currentBundle.uploaderName,
                    uploaderAvatarUrl = currentBundle.uploaderAvatarUrl,
                    thumbnailUrl = currentBundle.thumbnailUrl,
                    durationSeconds = currentBundle.durationSeconds,
                    viewCount = currentBundle.viewCount,
                    uploadDateText = currentBundle.uploadDateText,
                    isLive = currentBundle.isLive
                )
            currentList.add(currentItem)
            _currentIndex.value = 0
        }

        currentList.add(video)
        _queue.value = currentList

        // Selaraskan PlaylistItem virtual agar UI antrean selalu sinkron dan tidak hilang
        val existingPl = _currentPlaylist.value
        _currentPlaylist.value = if (existingPl != null) {
            existingPl.copy(
                videos = currentList,
                streamCount = currentList.size.toLong()
            )
        } else {
            id.katube.data.model.PlaylistItem(
                id = "KATUBE_QUEUE",
                url = "https://www.youtube.com/playlist?list=KATUBE_QUEUE",
                name = "Daftar Antrean",
                uploaderName = "${currentList.size} video",
                streamCount = currentList.size.toLong(),
                videos = currentList
            )
        }

        // Jika player belum memutar apapun, mulai video ini seketika
        if (currentBundle == null || (currentBundle.selectedVideoUrl.isNullOrEmpty() && mediaController?.currentMediaItem == null)) {
            playInBackground(video.id, preview = video)
        }
    }

    fun addToQueueNext(video: id.katube.data.model.VideoItem) {
        val currentList = _queue.value.toMutableList()
        val currentBundle = _currentStreamBundle.value

        if (currentList.isEmpty() && currentBundle != null && currentBundle.videoId.isNotBlank()) {
            val currentItem = pendingPreviewItem?.takeIf { it.id == currentBundle.videoId }
                ?: id.katube.data.model.VideoItem(
                    id = currentBundle.videoId,
                    url = "https://www.youtube.com/watch?v=${currentBundle.videoId}",
                    title = currentBundle.title,
                    uploaderName = currentBundle.uploaderName,
                    uploaderAvatarUrl = currentBundle.uploaderAvatarUrl,
                    thumbnailUrl = currentBundle.thumbnailUrl,
                    durationSeconds = currentBundle.durationSeconds,
                    viewCount = currentBundle.viewCount,
                    uploadDateText = currentBundle.uploadDateText,
                    isLive = currentBundle.isLive
                )
            currentList.add(currentItem)
            _currentIndex.value = 0
        }

        val insertIndex = (_currentIndex.value + 1).coerceAtMost(currentList.size)
        currentList.add(insertIndex, video)
        _queue.value = currentList

        // Selaraskan PlaylistItem virtual
        val existingPl = _currentPlaylist.value
        _currentPlaylist.value = if (existingPl != null) {
            existingPl.copy(
                videos = currentList,
                streamCount = currentList.size.toLong()
            )
        } else {
            id.katube.data.model.PlaylistItem(
                id = "KATUBE_QUEUE",
                url = "https://www.youtube.com/playlist?list=KATUBE_QUEUE",
                name = "Daftar Antrean",
                uploaderName = "${currentList.size} video",
                streamCount = currentList.size.toLong(),
                videos = currentList
            )
        }

        if (currentBundle == null || (currentBundle.selectedVideoUrl.isNullOrEmpty() && mediaController?.currentMediaItem == null)) {
            playInBackground(video.id, preview = video)
        }
    }

    fun playInBackground(videoId: String, preview: id.katube.data.model.VideoItem? = null) {
        if (preview != null) {
            pendingPreviewItem = preview
            _isMiniPlayerVisible.value = true
            isPreparingNewVideo = true
        }
        scope.launch {
            val maxHeight = getEffectiveMaxHeight()
            repository.getStreamInfo(videoId, maxHeight = maxHeight).onSuccess { bundle ->
                playVideo(bundle, audioOnly = true)
            }.onFailure {
                isPreparingNewVideo = false
            }
        }
    }

    fun setQueue(videos: List<id.katube.data.model.VideoItem>, startIndex: Int = 0) {
        _queue.value = videos
        _currentIndex.value = startIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
    }

    fun setPlaylistQueue(playlist: id.katube.data.model.PlaylistItem, startIndex: Int = 0) {
        val cleanVideos = playlist.videos
            .filter { it.id.length == 11 && !it.id.startsWith("RD") && !it.isMix }
            .map { it.copy(isMix = false, playlistId = null) }
        val cleanPlaylist = playlist.copy(videos = cleanVideos)
        _currentPlaylist.value = cleanPlaylist
        _queue.value = cleanVideos
        _currentIndex.value = startIndex.coerceIn(0, (cleanVideos.size - 1).coerceAtLeast(0))
        _isShuffleEnabled.value = false
    }

    fun clearPlaylistQueue() {
        _currentPlaylist.value = null
        _queue.value = emptyList()
        _currentIndex.value = 0
        _isShuffleEnabled.value = false
    }

    fun removeVideoFromQueue(videoId: String) {
        val currentQ = _queue.value.toMutableList()
        val removeIndex = currentQ.indexOfFirst { it.id == videoId }
        if (removeIndex >= 0) {
            currentQ.removeAt(removeIndex)
            _queue.value = currentQ

            val pl = _currentPlaylist.value
            if (pl != null) {
                val updatedVideos = pl.videos.filter { it.id != videoId }
                _currentPlaylist.value = pl.copy(
                    videos = updatedVideos,
                    streamCount = updatedVideos.size.toLong()
                )
            }

            val curIdx = _currentIndex.value
            if (removeIndex < curIdx) {
                _currentIndex.value = (curIdx - 1).coerceAtLeast(0)
            } else if (removeIndex == curIdx) {
                if (currentQ.isNotEmpty()) {
                    val nextIdx = curIdx.coerceAtMost(currentQ.size - 1)
                    _currentIndex.value = nextIdx
                } else {
                    clearPlaylistQueue()
                }
            }
        }
    }

    fun toggleShuffle() {
        val nextShuffle = !_isShuffleEnabled.value
        _isShuffleEnabled.value = nextShuffle
        val playlist = _currentPlaylist.value
        if (nextShuffle && _queue.value.size > 1) {
            val currentVideo = _queue.value.getOrNull(_currentIndex.value)
            val others = _queue.value.filterIndexed { index, _ -> index != _currentIndex.value }.shuffled()
            if (currentVideo != null) {
                _queue.value = listOf(currentVideo) + others
                _currentIndex.value = 0
            }
        } else if (!nextShuffle && playlist != null) {
            val currentVideo = _queue.value.getOrNull(_currentIndex.value)
            _queue.value = playlist.videos
            val origIndex = if (currentVideo != null) playlist.videos.indexOfFirst { it.id == currentVideo.id } else 0
            _currentIndex.value = if (origIndex >= 0) origIndex else 0
        }
    }

    fun setCurrentIndex(index: Int) {
        _currentIndex.value = index.coerceIn(0, (_queue.value.size - 1).coerceAtLeast(0))
    }

    suspend fun getEffectiveMaxHeight(): Int {
        return try {
            val defaultMaxHeight = preferencesRepository.defaultQuality.first()
            val cellularMaxHeight = preferencesRepository.cellularMaxQuality.first()
            val isCellular = id.katube.core.util.NetworkUtils.isCellular(context)
            if (isCellular && cellularMaxHeight > 0) {
                if (defaultMaxHeight <= 0 || defaultMaxHeight > cellularMaxHeight) cellularMaxHeight else defaultMaxHeight
            } else {
                defaultMaxHeight
            }
        } catch (_: Exception) {
            0
        }
    }

    fun playQueue(videos: List<id.katube.data.model.VideoItem>, startIndex: Int = 0, audioOnly: Boolean = _isAudioOnly.value) {
        setQueue(videos, startIndex)
        if (videos.isNotEmpty() && startIndex in videos.indices) {
            val video = videos[startIndex]
            isTransitioningAutoplay = true
            acquireWakeLocks(3 * 60 * 1000L)
            val cached = streamBundleCache[video.id]
            if (cached != null) {
                playVideo(cached, audioOnly = audioOnly)
                return
            }
            scope.launch {
                val maxHeight = getEffectiveMaxHeight()
                setIsAutoQuality(maxHeight <= 0)
                repository.getStreamInfo(video.id, maxHeight = maxHeight).fold(
                    onSuccess = { bundle ->
                        playVideo(bundle, audioOnly = audioOnly)
                    },
                    onFailure = {
                        isTransitioningAutoplay = false
                        playNextInQueue()
                    }
                )
            }
        }
    }

    var activeArtistKeyword: String? = null

    // Cache in-memory StreamBundle terbaru agar lompat ke Sebelumnya / Berikutnya instan 0 ms
    val streamBundleCache = java.util.concurrent.ConcurrentHashMap<String, StreamBundle>()

    fun cacheStreamBundle(bundle: StreamBundle) {
        if (bundle.videoId.isNotEmpty()) {
            streamBundleCache[bundle.videoId] = bundle
        }
    }

    fun playNextInQueue() {
        val videos = _queue.value
        val currentVideoId = _currentStreamBundle.value?.videoId.orEmpty()
        val activeIdx = videos.indexOfFirst { it.id == currentVideoId }
        val nextIndex = if (activeIdx >= 0) activeIdx + 1 else _currentIndex.value + 1
        if (videos.isNotEmpty() && nextIndex in videos.indices) {
            isTransitioningAutoplay = true
            acquireWakeLocks(3 * 60 * 1000L)
            _currentIndex.value = nextIndex
            val nextVideo = videos[nextIndex]

            // 1. Segera tetapkan StreamBundle preview seketika (0 ms) agar bilah notifikasi & UI sinkron dengan lagu berikutnya
            _currentStreamBundle.value = StreamBundle(
                videoId = nextVideo.id,
                title = nextVideo.title,
                uploaderName = nextVideo.uploaderName,
                uploaderAvatarUrl = nextVideo.uploaderAvatarUrl,
                thumbnailUrl = nextVideo.thumbnailUrl,
                durationSeconds = nextVideo.durationSeconds,
                viewCount = nextVideo.viewCount,
                uploadDateText = nextVideo.uploadDateText,
                isLive = nextVideo.isLive,
                selectedVideoUrl = null,
                selectedAudioUrl = "",
                selectedHeight = 0,
                relatedVideos = emptyList()
            )
            _isMiniPlayerVisible.value = true

            // 2. Segera pasang judul, artis, & cover baru di lock screen & bilah status agar tidak tertinggal judul lagu lama!
            val pendingMetadata = MediaMetadata.Builder()
                .setTitle(nextVideo.title)
                .setArtist(nextVideo.uploaderName)
                .setDisplayTitle(nextVideo.title)
                .setSubtitle(nextVideo.uploaderName)
                .setArtworkUri(nextVideo.thumbnailUrl?.let { Uri.parse(it) })
                .build()
            mediaController?.setPlaylistMetadata(pendingMetadata)

            val cached = streamBundleCache[nextVideo.id]
            if (cached != null) {
                playVideo(cached, audioOnly = _isAudioOnly.value)
                return
            }

            scope.launch {
                val maxHeight = getEffectiveMaxHeight()
                setIsAutoQuality(maxHeight <= 0)
                repository.getStreamInfo(nextVideo.id, maxHeight = maxHeight).fold(
                    onSuccess = { bundle ->
                        playVideo(bundle, audioOnly = _isAudioOnly.value)
                    },
                    onFailure = {
                        // Jika video gagal diambil, coba video berikutnya di antrean
                        playNextInQueue()
                    }
                )
            }
        } else if (isAutoplayEnabled.value) {
            playAutoplayNext()
        } else {
            isTransitioningAutoplay = false
            releaseWakeLocks()
        }
    }

    fun playAutoplayNext() {
        if (!isAutoplayEnabled.value) {
            isTransitioningAutoplay = false
            releaseWakeLocks()
            return
        }
        acquireWakeLocks(3 * 60 * 1000L)
        isTransitioningAutoplay = true

        val currentBefore = _currentStreamBundle.value
        val currentVideoId = currentBefore?.videoId.orEmpty()
        // Catat video saat ini ke riwayat pemutaran agar MUTLAK tidak pernah diputar ulang oleh putar otomatis!
        if (currentVideoId.isNotEmpty()) {
            recordHistory(currentVideoId)
        }

        // Simpan daftar video terkait saat ini sebelum transisi
        val existingRelated = currentBefore?.relatedVideos.orEmpty()

        val best = pickBestNextAutoplayCandidate()
        if (best != null && isValidVideoId(best.id) && best.id != currentVideoId) {
            // 1. Segera tetapkan StreamBundle preview seketika (0 ms) agar bilah notifikasi & UI sinkron dengan lagu berikutnya
            _currentStreamBundle.value = StreamBundle(
                videoId = best.id,
                title = best.title,
                uploaderName = best.uploaderName,
                uploaderAvatarUrl = best.uploaderAvatarUrl,
                thumbnailUrl = best.thumbnailUrl,
                durationSeconds = best.durationSeconds,
                viewCount = best.viewCount,
                uploadDateText = best.uploadDateText,
                isLive = best.isLive,
                selectedVideoUrl = null,
                selectedAudioUrl = "",
                selectedHeight = 0,
                relatedVideos = existingRelated.filter { it.id != best.id }
            )
            _isMiniPlayerVisible.value = true

            // 2. Langsung perbarui judul, artis, & cover di lock screen & status bar dengan kandidat autoplay teratas
            val pendingMetadata = MediaMetadata.Builder()
                .setTitle(best.title)
                .setArtist(best.uploaderName)
                .setDisplayTitle(best.title)
                .setSubtitle(best.uploaderName)
                .setArtworkUri(best.thumbnailUrl?.let { Uri.parse(it) })
                .build()
            mediaController?.setPlaylistMetadata(pendingMetadata)

            val cached = streamBundleCache[best.id]
            if (cached != null) {
                playVideo(cached, audioOnly = _isAudioOnly.value, addToHistory = true)
                return
            }
        }

        scope.launch {
            try {
                val maxHeight = getEffectiveMaxHeight()
                setIsAutoQuality(maxHeight <= 0)

                val candidateList = mutableListOf<String>()
                if (best != null && isValidVideoId(best.id) && best.id != currentVideoId) {
                    candidateList.add(best.id)
                }

                // Tambahkan kandidat cadangan dari daftar rekomendasi video saat ini
                existingRelated.forEach { rel ->
                    if (isValidVideoId(rel.id) && rel.id != currentVideoId && !candidateList.contains(rel.id) && !_playbackHistory.contains(rel.id)) {
                        candidateList.add(rel.id)
                    }
                }

                // Jika kandidat masih kosong, ambil langsung rekomendasi via YouTube InnerTube
                if (candidateList.isEmpty() && currentVideoId.isNotEmpty()) {
                    val nextData = syncRepository.fetchWatchNextRelated(currentVideoId).getOrNull()
                    nextData?.relatedVideos?.forEach { rel ->
                        if (isValidVideoId(rel.id) && rel.id != currentVideoId && !candidateList.contains(rel.id) && !_playbackHistory.contains(rel.id)) {
                            candidateList.add(rel.id)
                        }
                    }
                }

                var playedSuccessfully = false
                // Coba putar kandidat yang tersedia secara berurutan hingga berhasil
                for (targetId in candidateList.take(5)) {
                    val cached = streamBundleCache[targetId]
                    if (cached != null) {
                        playVideo(cached, audioOnly = _isAudioOnly.value, addToHistory = true)
                        playedSuccessfully = true
                        break
                    }

                    val result = repository.getStreamInfo(targetId, maxHeight = maxHeight)
                    if (result.isSuccess) {
                        val nextBundle = result.getOrNull()
                        if (nextBundle != null && (nextBundle.selectedVideoUrl != null || nextBundle.selectedAudioUrl.isNotEmpty())) {
                            playVideo(nextBundle, audioOnly = _isAudioOnly.value, addToHistory = true)
                            playedSuccessfully = true
                            break
                        }
                    }
                }

                // Jika semua kandidat related gagal, cari lagu lain dari artis yang sama (JANGAN PERNAH CARI JUDUL LAGU SAMA!)
                if (!playedSuccessfully) {
                    val query = when {
                        !currentBefore?.uploaderName.isNullOrBlank() -> currentBefore?.uploaderName.orEmpty()
                        !currentBefore?.title.isNullOrBlank() -> currentBefore?.title.orEmpty()
                        else -> ""
                    }
                    if (query.isBlank()) {
                        isTransitioningAutoplay = false
                        if (mediaController?.isPlaying != true) releaseWakeLocks()
                        return@launch
                    }
                    val searchResult = repository.search(query)
                    if (searchResult.isSuccess) {
                        val items = searchResult.getOrNull().orEmpty()
                        // MUTLAK: Jangan pernah masukkan video yang sedang diputar ini (currentVideoId)!
                        val candidatesFromSearch = items.filter { isValidVideoId(it.id) && it.id != currentVideoId && !_playbackHistory.contains(it.id) }
                            .ifEmpty { items.filter { isValidVideoId(it.id) && it.id != currentVideoId } }

                        for (searchCandidate in candidatesFromSearch.take(3)) {
                            val streamRes = repository.getStreamInfo(searchCandidate.id, maxHeight = maxHeight)
                            if (streamRes.isSuccess) {
                                val nextBundle = streamRes.getOrNull()
                                if (nextBundle != null && (nextBundle.selectedVideoUrl != null || nextBundle.selectedAudioUrl.isNotEmpty())) {
                                    playVideo(nextBundle, audioOnly = _isAudioOnly.value, addToHistory = true)
                                    playedSuccessfully = true
                                    break
                                }
                            }
                        }
                    }
                }

                if (!playedSuccessfully) {
                    isTransitioningAutoplay = false
                    if (mediaController?.isPlaying != true) {
                        releaseWakeLocks()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isTransitioningAutoplay = false
                if (mediaController?.isPlaying != true) {
                    releaseWakeLocks()
                }
            }
        }
    }

    fun pickBestNextAutoplayCandidate(): id.katube.data.model.VideoItem? {
        val current = _currentStreamBundle.value ?: return null
        val currentVideoId = current.videoId
        val allCandidates = current.relatedVideos
            .filter { isValidVideoId(it.id) && it.id != currentVideoId }
            .distinctBy { it.id }

        if (allCandidates.isEmpty()) return null

        val playedHistory = _playbackHistory.toSet()

        // Murni urutan rekomendasi nomor 1 resmi YouTube InnerTube yang belum diputar di sesi ini
        return allCandidates.firstOrNull { !playedHistory.contains(it.id) }
            ?: allCandidates.firstOrNull()
    }

    private fun calculateFreshnessScore(uploadDateText: String?): Int {
        if (uploadDateText.isNullOrBlank()) return 50
        val text = uploadDateText.lowercase()
        return when {
            text.contains("detik") || text.contains("second") ||
            text.contains("menit") || text.contains("minute") ||
            text.contains("jam") || text.contains("hour") -> 1000
            text.contains("hari") || text.contains("day") -> {
                val num = text.filter { it.isDigit() }.toIntOrNull() ?: 1
                (900 - (num * 10)).coerceAtLeast(600)
            }
            text.contains("minggu") || text.contains("week") -> {
                val num = text.filter { it.isDigit() }.toIntOrNull() ?: 1
                (550 - (num * 20)).coerceAtLeast(400)
            }
            text.contains("bulan") || text.contains("month") -> {
                val num = text.filter { it.isDigit() }.toIntOrNull() ?: 1
                (350 - (num * 15)).coerceAtLeast(100)
            }
            text.contains("tahun") || text.contains("year") -> {
                val num = text.filter { it.isDigit() }.toIntOrNull() ?: 1
                (80 - (num * 10)).coerceAtLeast(0)
            }
            else -> 50
        }
    }

    fun extractArtistKeyword(title: String, uploader: String): String? {
        val cleanTitle = title
            .replace(Regex("""(?i)\[.*?\]|\(.*?\)|【.*?】"""), "")
            .replace(Regex("""(?i)\b(official|music|video|lirik|lyric|audio|clip|klip|mv|live|karaoke|full album|hq|hd|4k)\b"""), "")
            .trim()

        val parts = cleanTitle.split(Regex("""\s*[-–|:]\s*""")).map { it.trim() }.filter { it.isNotEmpty() }
        var artist: String? = null

        val labelKeywords = listOf(
            "aneka safari", "perdana record", "fortuna", "mega record", "khatulistiwa",
            "pelita utama", "sumber kencana", "global musik", "chgb record", "aglis",
            "teta record", "samudra record", "bw record", "dasa studio", "sakura", "music", "records"
        )

        if (parts.size >= 2) {
            val p0 = parts[0]
            val isP0Label = labelKeywords.any { p0.contains(it, ignoreCase = true) }
            artist = if (isP0Label && parts.size >= 3) {
                parts[1]
            } else if (isP0Label) {
                parts[1]
            } else {
                p0
            }
        } else if (uploader.isNotBlank()) {
            val cleanUploader = uploader
                .replace(Regex("""(?i)\b(official|music|channel|records?|record|entertainment|studio|media|production|tv|topic)\b"""), "")
                .trim()
            if (cleanUploader.length >= 3) {
                artist = cleanUploader
            }
        }

        if (artist != null) {
            artist = artist.split(Regex("""(?i)\s+(?:ft\.?|feat\.?|x|with)\s+"""))[0].trim()
            if (artist.length >= 3 && !artist.equals("lagu", ignoreCase = true)) {
                return artist
            }
        }
        return null
    }

    private fun isValidVideoId(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        return id.length == 11 && !id.startsWith("RD") && !id.startsWith("PL") && !id.startsWith("VL") && !id.startsWith("UC")
    }

    fun updateRelatedVideos(videos: List<id.katube.data.model.VideoItem>) {
        val current = _currentStreamBundle.value ?: return
        val merged = (current.relatedVideos + videos).distinctBy { it.id }
        _currentStreamBundle.value = current.copy(relatedVideos = merged)
    }

    // Riwayat riil video sebelumnya yang diputar pengguna (untuk tombol Sebelumnya / SkipPrevious)
    private val _playbackHistory = mutableListOf<String>()
    val playbackHistory: List<String> get() = _playbackHistory

    fun recordHistory(videoId: String) {
        if (videoId.isNotEmpty() && _playbackHistory.lastOrNull() != videoId) {
            _playbackHistory.add(videoId)
        }
    }

    fun hasPreviousVideo(): Boolean {
        return _playbackHistory.isNotEmpty() || (_queue.value.isNotEmpty() && _currentIndex.value > 0)
    }

    fun popPreviousVideoId(): String? {
        if (_playbackHistory.isNotEmpty()) {
            return _playbackHistory.removeAt(_playbackHistory.lastIndex)
        }
        return null
    }

    fun playPreviousInQueue() {
        playPreviousInternal()
    }

    fun playPreviousInternal() {
        val currentPos = mediaController?.currentPosition ?: 0L
        if (currentPos > 3000L) {
            seekTo(0L)
            mediaController?.play()
            return
        }

        val prevId = popPreviousVideoId()
        if (prevId != null) {
            val cached = streamBundleCache[prevId]
            if (cached != null) {
                playVideo(cached, audioOnly = _isAudioOnly.value, addToHistory = false)
                return
            }

            scope.launch {
                val maxHeight = getEffectiveMaxHeight()
                setIsAutoQuality(maxHeight <= 0)
                repository.getStreamInfo(prevId, maxHeight = maxHeight).onSuccess { bundle ->
                    playVideo(bundle, audioOnly = _isAudioOnly.value, addToHistory = false)
                }
            }
            return
        }
        val videos = _queue.value
        val prevIndex = _currentIndex.value - 1
        if (prevIndex in videos.indices) {
            isTransitioningAutoplay = true
            acquireWakeLocks(3 * 60 * 1000L)
            _currentIndex.value = prevIndex
            val prevVideo = videos[prevIndex]

            // 1. Segera tetapkan StreamBundle preview seketika (0 ms)
            _currentStreamBundle.value = StreamBundle(
                videoId = prevVideo.id,
                title = prevVideo.title,
                uploaderName = prevVideo.uploaderName,
                uploaderAvatarUrl = prevVideo.uploaderAvatarUrl,
                thumbnailUrl = prevVideo.thumbnailUrl,
                durationSeconds = prevVideo.durationSeconds,
                viewCount = prevVideo.viewCount,
                uploadDateText = prevVideo.uploadDateText,
                isLive = prevVideo.isLive,
                selectedVideoUrl = null,
                selectedAudioUrl = "",
                selectedHeight = 0,
                relatedVideos = emptyList()
            )
            _isMiniPlayerVisible.value = true

            // 2. Segera pasang judul, artis, & cover di lock screen & status bar
            val pendingMetadata = MediaMetadata.Builder()
                .setTitle(prevVideo.title)
                .setArtist(prevVideo.uploaderName)
                .setDisplayTitle(prevVideo.title)
                .setSubtitle(prevVideo.uploaderName)
                .setArtworkUri(prevVideo.thumbnailUrl?.let { Uri.parse(it) })
                .build()
            mediaController?.setPlaylistMetadata(pendingMetadata)

            val cached = streamBundleCache[prevVideo.id]
            if (cached != null) {
                playVideo(cached, audioOnly = _isAudioOnly.value, addToHistory = false)
                return
            }

            scope.launch {
                val maxHeight = getEffectiveMaxHeight()
                setIsAutoQuality(maxHeight <= 0)
                repository.getStreamInfo(prevVideo.id, maxHeight = maxHeight).onSuccess { bundle ->
                    playVideo(bundle, audioOnly = _isAudioOnly.value, addToHistory = false)
                }
            }
        } else {
            seekTo(0L)
            mediaController?.play()
        }
    }

    private val inFlightExtractions = java.util.concurrent.ConcurrentHashMap<String, Deferred<Result<StreamBundle>>>()

    fun preloadStream(videoId: String): Deferred<Result<StreamBundle>> {
        val cached = streamBundleCache[videoId]
        if (cached != null) {
            return scope.async { Result.success(cached) }
        }
        return inFlightExtractions.computeIfAbsent(videoId) { id ->
            scope.async(Dispatchers.IO) {
                try {
                    val maxHeight = getEffectiveMaxHeight()
                    val resolvedId = if (id.length != 11 && (id.startsWith("RD") || id.startsWith("PL") || id.startsWith("VL"))) {
                        syncRepository.fetchPlaylistDetails(id).getOrNull()?.videos?.firstOrNull {
                            it.id.length == 11 && !it.id.startsWith("RD") && !it.title.startsWith("Mix -", ignoreCase = true) && !it.title.startsWith("Mix –", ignoreCase = true)
                        }?.id ?: id
                    } else {
                        id
                    }
                    val res = repository.getStreamInfo(resolvedId, maxHeight = maxHeight)
                    res.onSuccess { bundle ->
                        cacheStreamBundle(bundle)
                    }
                    res
                } finally {
                    inFlightExtractions.remove(id)
                }
            }
        }
    }

    fun prepareForNewVideo(targetVideoId: String, previewItem: id.katube.data.model.VideoItem? = null) {
        val current = _currentStreamBundle.value
        if (current == null || current.videoId != targetVideoId) {
            isPreparingNewVideo = true
            cancelBufferingWatchdog()
            try {
                // Batalkan seketika semua koneksi download video lama di level soket OkHttp!
                streamingClient.dispatcher.cancelAll()
            } catch (_: Exception) {}
            mediaController?.stop()
            mediaController?.clearMediaItems()
            _isPlaying.value = false
            _currentPositionMs.value = 0L
            _bufferedPositionMs.value = 0L
            _playbackState.value = Player.STATE_BUFFERING

            // Simpan previewItem ke pendingPreviewItem untuk WatchScreen & transisi instan
            val preview = previewItem ?: pendingPreviewItem?.takeIf { it.id == targetVideoId }
            if (preview != null) {
                pendingPreviewItem = preview
            }

            // Tetapkan preview StreamBundle seketika agar MiniPlayer & WatchScreen memiliki data visual (0 ms)
            _currentStreamBundle.value = StreamBundle(
                videoId = targetVideoId,
                title = preview?.title ?: "Memuat...",
                uploaderName = preview?.uploaderName ?: "",
                uploaderAvatarUrl = preview?.uploaderAvatarUrl,
                thumbnailUrl = preview?.thumbnailUrl,
                durationSeconds = preview?.durationSeconds ?: 0L,
                viewCount = preview?.viewCount ?: 0L,
                uploadDateText = preview?.uploadDateText,
                isLive = preview?.isLive ?: false,
                selectedVideoUrl = null,
                selectedAudioUrl = "",
                selectedHeight = 0,
                relatedVideos = emptyList()
            )
            _isMiniPlayerVisible.value = true

            scope.launch {
                delay(800)
                isPreparingNewVideo = false
            }

            // Segera mulai preload stream info dan otomatis putar di scope singleton PlayerController
            // sehingga jika user menekan tombol kembali ke Home/MiniPlayer, streaming tidak terbunuh oleh lifecycle ViewModel
            if (targetVideoId.isNotBlank()) {
                scope.launch {
                    val res = preloadStream(targetVideoId).await()
                    res.onSuccess { bundle ->
                        // Jika video ini masih aktif dan belum diputar oleh WatchScreen
                        if (_currentStreamBundle.value?.videoId == targetVideoId &&
                            (_currentStreamBundle.value?.selectedVideoUrl.isNullOrEmpty() || mediaController?.currentMediaItem == null)) {
                            playVideo(bundle)
                        }
                    }
                }
            }
        }
    }

    fun playVideo(bundle: StreamBundle, audioOnly: Boolean = false, addToHistory: Boolean = true, startPositionMs: Long = 0L) {
        // 1. Sesuaikan otomatis resolusi stream dengan preferensi pengguna di Pengaturan
        val effectiveMaxHeight = runBlocking {
            try { getEffectiveMaxHeight() } catch (_: Exception) { 0 }
        }
        val targetBundle = if (effectiveMaxHeight > 0 && bundle.availableQualities.isNotEmpty() && bundle.selectedHeight != effectiveMaxHeight) {
            val exactStream = bundle.availableQualities.firstOrNull { it.height == effectiveMaxHeight && it.url.isNotEmpty() }
                ?: bundle.availableQualities.filter { it.height in 1..effectiveMaxHeight && it.url.isNotEmpty() }.maxByOrNull { it.height }
            if (exactStream != null) {
                val audioUrlToUse = if (exactStream.isMuxed) "" else bundle.selectedAudioUrl.ifEmpty { bundle.fallbackAudioUrl.orEmpty() }
                bundle.copy(
                    selectedVideoUrl = exactStream.url,
                    selectedAudioUrl = audioUrlToUse,
                    selectedHeight = exactStream.height,
                )
            } else bundle
        } else bundle

        setIsAutoQuality(effectiveMaxHeight <= 0)

        // 2. Sesuaikan otomatis mode audio-only dengan preferensi pengguna
        val effectiveAudioOnly = audioOnly || runBlocking {
            try { preferencesRepository.defaultAudioOnly.first() } catch (_: Exception) { false }
        }

        cacheStreamBundle(targetBundle)
        startPlaybackService()
        acquireWakeLocks()
        isTransitioningAutoplay = false
        isPreparingNewVideo = false
        val previousBundle = _currentStreamBundle.value
        if (previousBundle != null && previousBundle.videoId.isNotEmpty() && previousBundle.videoId != targetBundle.videoId) {
            try {
                streamingClient.dispatcher.cancelAll()
            } catch (_: Exception) {}
            if (addToHistory) {
                recordHistory(previousBundle.videoId)
            }
        }

        // Catat video yang baru diputar ini ke riwayat (Room lokal & akun YouTube jika login)
        if (targetBundle.videoId.isNotEmpty()) {
            val playedVideo = id.katube.data.model.VideoItem(
                id = targetBundle.videoId,
                url = "https://www.youtube.com/watch?v=${targetBundle.videoId}",
                title = targetBundle.title,
                uploaderName = targetBundle.uploaderName,
                uploaderAvatarUrl = targetBundle.uploaderAvatarUrl,
                uploaderUrl = targetBundle.uploaderUrl,
                durationSeconds = targetBundle.durationSeconds,
                viewCount = targetBundle.viewCount,
                uploadDateText = targetBundle.uploadDateText,
                thumbnailUrl = targetBundle.thumbnailUrl,
                playbackPositionMs = startPositionMs,
                playbackPercent = if (targetBundle.durationSeconds > 0) (startPositionMs.toFloat() / (targetBundle.durationSeconds * 1000f)).coerceIn(0f, 1f) else 0f
            )
            syncRepository.recordLocalWatchHistory(playedVideo)
        }

        // Sinkronkan indeks antrean aktif jika video yang diputar ada di antrean
        val qIdx = _queue.value.indexOfFirst { it.id == targetBundle.videoId }
        if (qIdx >= 0) {
            _currentIndex.value = qIdx
        } else {
            val pIdx = _currentPlaylist.value?.videos?.indexOfFirst { it.id == targetBundle.videoId } ?: -1
            if (pIdx >= 0) {
                _currentIndex.value = pIdx
            }
        }

        val mergedRelated = if (targetBundle.relatedVideos.isEmpty() && previousBundle != null && previousBundle.videoId == targetBundle.videoId && previousBundle.relatedVideos.isNotEmpty()) {
            previousBundle.relatedVideos
        } else {
            targetBundle.relatedVideos
        }
        val finalBundle = if (mergedRelated.isNotEmpty() && targetBundle.relatedVideos.isEmpty()) {
            targetBundle.copy(relatedVideos = mergedRelated)
        } else {
            targetBundle
        }
        _currentStreamBundle.value = finalBundle
        _isAudioOnly.value = effectiveAudioOnly
        _isMiniPlayerVisible.value = true
        pendingSeekPositionMs = startPositionMs

        val detected = extractArtistKeyword(targetBundle.title, targetBundle.uploaderName)
        if (!detected.isNullOrBlank()) {
            activeArtistKeyword = detected
        }

        val controller = mediaController
        if (controller == null) {
            pendingPlayBundle = Triple(targetBundle, effectiveAudioOnly, startPositionMs)
            return
        }

        val subUrls = ArrayList<String>()
        val subLangs = ArrayList<String>()
        val subLabels = ArrayList<String>()
        val subFormats = ArrayList<String>()
        targetBundle.availableSubtitles.forEach { sub ->
            subUrls.add(sub.url)
            subLangs.add(sub.languageCode)
            subLabels.add(sub.displayLabel)
            subFormats.add(sub.format)
        }

        val effectiveAudioUrl = if (targetBundle.selectedAudioUrl.isNotEmpty()) targetBundle.selectedAudioUrl else targetBundle.fallbackAudioUrl
        val extras = Bundle().apply {
            putString("video_url", if (effectiveAudioOnly) null else targetBundle.selectedVideoUrl)
            putString("audio_url", if (effectiveAudioOnly) effectiveAudioUrl else targetBundle.selectedAudioUrl.ifEmpty { null })
            putBoolean("is_live", targetBundle.isLive)
            putString("hls_url", targetBundle.hlsUrl)
            putStringArrayList("sub_urls", subUrls)
            putStringArrayList("sub_langs", subLangs)
            putStringArrayList("sub_labels", subLabels)
            putStringArrayList("sub_formats", subFormats)
        }

        val thumbUrl = targetBundle.thumbnailUrl
        var initialArtworkData: ByteArray? = null
        if (!thumbUrl.isNullOrEmpty()) {
            val memoryBitmap = (coil.Coil.imageLoader(context).memoryCache?.get(coil.memory.MemoryCache.Key(thumbUrl)))?.bitmap
            if (memoryBitmap != null) {
                val stream = java.io.ByteArrayOutputStream()
                memoryBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
                initialArtworkData = stream.toByteArray()
            }
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(targetBundle.title)
            .setArtist(targetBundle.uploaderName)
            .setDisplayTitle(targetBundle.title)
            .setSubtitle(targetBundle.uploaderName)
            .setArtworkUri(thumbUrl?.let { Uri.parse(it) })
            .setExtras(extras)

        if (initialArtworkData != null) {
            metadataBuilder.setArtworkData(initialArtworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }
        val metadata = metadataBuilder.build()

        val subtitleConfigs = targetBundle.availableSubtitles.map { sub ->
            val mimeType = if (sub.format.contains("ttml", ignoreCase = true)) {
                MimeTypes.APPLICATION_TTML
            } else {
                MimeTypes.TEXT_VTT
            }
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                .setMimeType(mimeType)
                .setLanguage(sub.languageCode)
                .setLabel(sub.displayLabel)
                .build()
        }

        val mediaUri = if (effectiveAudioOnly) effectiveAudioUrl else (targetBundle.selectedVideoUrl ?: effectiveAudioUrl)
        val mediaItem = MediaItem.Builder()
            .setMediaId(targetBundle.videoId)
            .setUri(mediaUri)
            .setSubtitleConfigurations(subtitleConfigs)
            .setMediaMetadata(metadata)
            .build()

        // Pasang metadata lagu baru ke playlistMetadata agar Lock Screen & Notifikasi Sistem segera berganti seketika!
        controller.setPlaylistMetadata(metadata)
        controller.playWhenReady = true
        if (startPositionMs > 0L) {
            controller.setMediaItem(mediaItem, startPositionMs)
        } else {
            controller.setMediaItem(mediaItem, /* resetPosition = */ true)
        }
        controller.prepare()
        controller.play()

        _currentPositionMs.value = startPositionMs
        _bufferedPositionMs.value = 0L
        startProgressTracking()

        // Terapkan kecepatan default dari preferensi
        scope.launch {
            try {
                val speed = preferencesRepository.defaultSpeed.first()
                if (speed > 0f) {
                    controller.setPlaybackSpeed(speed)
                    _playbackSpeed.value = speed
                }
            } catch (_: Exception) {}
        }

        // Sinkronisasi preferensi teks / subtitle pengguna
        scope.launch {
            val mode = try { preferencesRepository.subtitleMode.first() } catch (_: Exception) { "Otomatis" }
            when (mode) {
                "Nonaktif" -> {
                    setSubtitleLanguage("OFF")
                }
                "Bahasa Indonesia" -> {
                    val indonesian = targetBundle.availableSubtitles.firstOrNull { it.languageCode.equals("id", ignoreCase = true) || it.languageCode.startsWith("id-", ignoreCase = true) }
                    if (indonesian != null) setSubtitleLanguage(indonesian.languageCode) else setSubtitleLanguage("OFF")
                }
                "English" -> {
                    val english = targetBundle.availableSubtitles.firstOrNull { it.languageCode.equals("en", ignoreCase = true) || it.languageCode.startsWith("en-", ignoreCase = true) }
                    if (english != null) setSubtitleLanguage(english.languageCode) else setSubtitleLanguage("OFF")
                }
                else -> {
                    // Otomatis / Selalu aktif: prioritaskan Bahasa Indonesia, lalu Bahasa Inggris, lalu bahasa pertama
                    val indonesian = targetBundle.availableSubtitles.firstOrNull { it.languageCode.equals("id", ignoreCase = true) || it.languageCode.startsWith("id-", ignoreCase = true) }
                    val english = targetBundle.availableSubtitles.firstOrNull { it.languageCode.equals("en", ignoreCase = true) || it.languageCode.startsWith("en-", ignoreCase = true) }
                    val defaultChoice = indonesian ?: english ?: targetBundle.availableSubtitles.firstOrNull()
                    if (defaultChoice != null) {
                        setSubtitleLanguage(defaultChoice.languageCode)
                    } else {
                        setSubtitleLanguage("OFF")
                    }
                }
            }
        }

        // Jika belum ada di memory cache, ambil thumbnail di background & pasang artwork ke playlistMetadata
        if (initialArtworkData == null && !thumbUrl.isNullOrEmpty()) {
            scope.launch(Dispatchers.IO) {
                try {
                    delay(2500) // Tunda agar 100% bandwidth awal dipakai ExoPlayer untuk buffer video tanpa gangguan
                    val loader = coil.Coil.imageLoader(context)
                    val req = coil.request.ImageRequest.Builder(context)
                        .data(thumbUrl)
                        .allowHardware(false)
                        .build()
                    val res = (loader.execute(req) as? coil.request.SuccessResult)?.drawable
                    val bmp = (res as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        val stream = java.io.ByteArrayOutputStream()
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
                        val bytes = stream.toByteArray()
                        withContext(Dispatchers.Main) {
                            val activeItem = controller.currentMediaItem
                            if (activeItem != null && activeItem.mediaId == bundle.videoId) {
                                val fullWithArtwork = MediaMetadata.Builder()
                                    .setTitle(bundle.title)
                                    .setArtist(bundle.uploaderName)
                                    .setDisplayTitle(bundle.title)
                                    .setSubtitle(bundle.uploaderName)
                                    .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                    .setArtworkUri(thumbUrl.let { Uri.parse(it) })
                                    .build()
                                controller.setPlaylistMetadata(fullWithArtwork)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Prefetch video berikutnya di antrean ATAU rekomendasi autoplay agar transisi (terutama di latar belakang / layar mati) berjalan instan 0 ms
        val nextIdx = _currentIndex.value + 1
        val nextVideo = _queue.value.getOrNull(nextIdx) ?: pickBestNextAutoplayCandidate()
        if (nextVideo != null && isValidVideoId(nextVideo.id) && !streamBundleCache.containsKey(nextVideo.id)) {
            scope.launch(Dispatchers.IO) {
                try {
                    val maxHeight = getEffectiveMaxHeight()
                    repository.getStreamInfo(nextVideo.id, maxHeight = maxHeight).onSuccess { nextBundle ->
                        cacheStreamBundle(nextBundle)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun pauseIfPlaying() {
        mediaController?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun seekForward(seconds: Int = 10) {
        val controller = mediaController ?: return
        seekTo((controller.currentPosition + seconds * 1000L).coerceAtMost(controller.duration))
    }

    fun seekBackward(seconds: Int = 10) {
        val controller = mediaController ?: return
        seekTo((controller.currentPosition - seconds * 1000L).coerceAtLeast(0))
    }

    fun seekForward10() = seekForward(10)

    fun seekBackward10() = seekBackward(10)

    fun setPlaybackSpeed(speed: Float) {
        val controller = mediaController ?: return
        controller.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    fun setAudioOnly(audioOnly: Boolean) {
        if (_isAudioOnly.value == audioOnly) return
        val current = _currentStreamBundle.value ?: return
        val savedPosition = mediaController?.currentPosition ?: 0L
        playVideo(current, audioOnly = audioOnly, addToHistory = false, startPositionMs = savedPosition)
    }

    fun setIsAutoQuality(isAuto: Boolean) {
        _isAutoQuality.value = isAuto
    }

    fun selectQuality(quality: VideoQuality) {
        val current = _currentStreamBundle.value ?: return
        if (quality.height <= 0) {
            // Mode Otomatis (Auto): simpan ke preferensi agar semua video berikutnya berstatus Auto
            _isAutoQuality.value = true
            scope.launch {
                try {
                    preferencesRepository.setDefaultQuality(0)
                } catch (_: Exception) {}
            }
            val autoTargetHeight = id.katube.core.util.NetworkUtils.getOptimalAutoResolution(context)
            val autoStream = current.availableQualities
                .filter { it.height in 1..autoTargetHeight && it.url.isNotEmpty() }
                .maxByOrNull { it.height }
                ?: current.availableQualities.filter { it.height > 0 && it.url.isNotEmpty() }.minByOrNull { it.height }

            if (autoStream != null) {
                val updated = current.copy(
                    selectedVideoUrl = autoStream.url,
                    selectedHeight = autoStream.height,
                )
                val savedPosition = mediaController?.currentPosition ?: 0L
                playVideo(updated, audioOnly = _isAudioOnly.value, addToHistory = false, startPositionMs = savedPosition)
            }
            return
        }

        // Simpan kualitas pilihan user (misal 144p, 240p, 360p, 480p, dll) secara permanen ke DataStore
        // sehingga seluruh video yang diputar berikutnya otomatis menyesuaikan ke kualitas ini!
        _isAutoQuality.value = false
        scope.launch {
            try {
                preferencesRepository.setDefaultQuality(quality.height)
            } catch (_: Exception) {}
        }

        val matchedQuality = current.availableQualities.firstOrNull { it.height == quality.height && it.url.isNotEmpty() } ?: quality
        if (matchedQuality.url.isEmpty()) {
            scope.launch {
                repository.getStreamInfo(current.videoId, maxHeight = quality.height).onSuccess { freshBundle ->
                    val savedPosition = mediaController?.currentPosition ?: 0L
                    playVideo(freshBundle, audioOnly = _isAudioOnly.value, addToHistory = false, startPositionMs = savedPosition)
                }
            }
            return
        }

        val audioUrlToUse = if (matchedQuality.isMuxed) {
            ""
        } else if (current.selectedAudioUrl.isNotEmpty()) {
            current.selectedAudioUrl
        } else {
            current.fallbackAudioUrl.orEmpty()
        }
        val updated = current.copy(
            selectedVideoUrl = matchedQuality.url,
            selectedAudioUrl = audioUrlToUse,
            selectedHeight = matchedQuality.height,
        )
        val savedPosition = mediaController?.currentPosition ?: 0L
        playVideo(updated, audioOnly = _isAudioOnly.value, addToHistory = false, startPositionMs = savedPosition)
    }

    fun showMiniPlayer() {
        _isMiniPlayerVisible.value = true
    }

    fun dismissMiniPlayer() {
        _isMiniPlayerVisible.value = false
        mediaController?.stop()
        _currentStreamBundle.value = null
    }

    private fun startProgressTracking() {
        stopProgressTracking()
        progressTrackingJob = scope.launch {
            var lastReportedSec = 0L
            while (isActive) {
                mediaController?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    val buf = player.bufferedPosition.coerceAtLeast(0L)
                    _currentPositionMs.value = pos
                    _durationMs.value = dur
                    _bufferedPositionMs.value = buf
                    if (_isPlaying.value != player.isPlaying) {
                        _isPlaying.value = player.isPlaying
                    }
                    if (_playbackState.value != player.playbackState) {
                        _playbackState.value = player.playbackState
                    }

                    // Jika video sudah mencapai durasi akhir namun tidak otomatis berpindah
                    if (dur > 5_000L && pos >= dur && !isTransitioningAutoplay) {
                        handlePlaybackEnded()
                    }

                    val currentVideoId = _currentStreamBundle.value?.videoId.orEmpty()
                    if (currentVideoId.isNotEmpty()) {
                        val posSec = pos / 1000L
                        if ((posSec in 5L..7L && lastReportedSec < 5L) || (posSec > 0L && posSec - lastReportedSec >= 15L)) {
                            lastReportedSec = posSec
                            onPlaybackProgress?.invoke(currentVideoId, pos, dur, false)
                        }
                    }
                }
                delay(250)
            }
        }
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerMinutes.value = null
            return
        }
        _sleepTimerMinutes.value = minutes
        sleepTimerJob = scope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60_000)
                remaining--
                _sleepTimerMinutes.value = if (remaining > 0) remaining else null
            }
            mediaController?.pause()
            _sleepTimerMinutes.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = null
    }

    private fun stopProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }
}

enum class RepeatMode {
    OFF, ONE, ALL
}

