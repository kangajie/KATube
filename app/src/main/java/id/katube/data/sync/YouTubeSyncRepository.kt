package id.katube.data.sync

import id.katube.core.network.OkHttpDownloader
import id.katube.core.util.YouTubeAuthUtils
import id.katube.data.auth.AuthRepository
import id.katube.data.local.history.WatchHistoryDao
import id.katube.data.local.history.WatchHistoryEntity
import id.katube.data.model.ChannelItem
import id.katube.data.model.GoogleAccount
import id.katube.data.model.KATubeNotification
import id.katube.data.model.PlaylistItem
import id.katube.data.model.VideoItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositori sinkronisasi data akun Google & YouTube.
 * Bertanggung jawab mengambil profil asli, feed beranda personal, mix video,
 * langganan saluran, riwayat tontonan, dan playlist langsung dari YouTube menggunakan sesi akun.
 * Menggabungkan riwayat lokal Room dengan riwayat server YouTube agar video yang baru diputar
 * tidak pernah hilang saat di-refresh.
 */
@Singleton
class YouTubeSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val authRepository: AuthRepository,
    private val historyDao: WatchHistoryDao,
    private val preferencesRepository: id.katube.data.preferences.UserPreferencesRepository,
    private val ioDispatcher: CoroutineDispatcher,
) {

    companion object {
        const val INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        const val INNERTUBE_CLIENT_VERSION = "2.20261022.01.00"
    }

    private val _homeFeed = MutableStateFlow<List<VideoItem>>(emptyList())
    val homeFeed: StateFlow<List<VideoItem>> = _homeFeed.asStateFlow()

    private val _subscribedChannels = MutableStateFlow<List<ChannelItem>>(emptyList())
    val subscribedChannels: StateFlow<List<ChannelItem>> = _subscribedChannels.asStateFlow()

    private val _subscriptionFeed = MutableStateFlow<List<VideoItem>>(emptyList())
    val subscriptionFeed: StateFlow<List<VideoItem>> = _subscriptionFeed.asStateFlow()

    private val _watchHistory = MutableStateFlow<List<VideoItem>>(emptyList())
    val watchHistory: StateFlow<List<VideoItem>> = _watchHistory.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val userPlaylists: StateFlow<List<PlaylistItem>> = _userPlaylists.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatus = MutableStateFlow("Belum disinkronkan")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    // Cache riwayat server YouTube untuk digabungkan dengan riwayat lokal
    private val remoteHistoryCache = mutableListOf<VideoItem>()
    // Peta waktu pemutaran lokal asli untuk setiap video yang pernah diputar di aplikasi ini (thread-safe)
    private val localWatchTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // URL pelacakan resmi yang diperoleh dari halaman tontonan YouTube (berisi token sesi: plid, ei, len, of, vm)
    @Volatile
    private var activePlaybackUrl: String? = null
    @Volatile
    private var activeWatchtimeUrl: String? = null

    // Cache status like/dislike & langganan channel dengan persistensi SharedPreferences agar tidak hilang saat keluar/masuk video maupun restart aplikasi
    private val likesPrefs by lazy {
        context.getSharedPreferences("katube_likes_prefs", Context.MODE_PRIVATE)
    }

    private val subsPrefs by lazy {
        context.getSharedPreferences("katube_subs_prefs", Context.MODE_PRIVATE)
    }

    private val userLikedVideoIds = mutableSetOf<String>()
    private val userDislikedVideoIds = mutableSetOf<String>()

    private fun loadLikesFromPrefs() {
        try {
            val likedSet = likesPrefs.getStringSet("liked_video_ids", emptySet()) ?: emptySet()
            val dislikedSet = likesPrefs.getStringSet("disliked_video_ids", emptySet()) ?: emptySet()
            synchronized(userLikedVideoIds) {
                userLikedVideoIds.clear()
                userLikedVideoIds.addAll(likedSet)
            }
            synchronized(userDislikedVideoIds) {
                userDislikedVideoIds.clear()
                userDislikedVideoIds.addAll(dislikedSet)
            }
        } catch (_: Exception) {}
    }

    private fun saveLikesToPrefs() {
        try {
            val likedCopy: Set<String>
            val dislikedCopy: Set<String>
            synchronized(userLikedVideoIds) {
                likedCopy = HashSet(userLikedVideoIds)
                dislikedCopy = HashSet(userDislikedVideoIds)
            }
            likesPrefs.edit()
                .putStringSet("liked_video_ids", likedCopy)
                .putStringSet("disliked_video_ids", dislikedCopy)
                .apply()
        } catch (_: Exception) {}
    }

    private fun loadSubsFromPrefs() {
        try {
            val rawSet = subsPrefs.getStringSet("subscribed_channels", emptySet()) ?: emptySet()
            val list = rawSet.mapNotNull { entry ->
                val parts = entry.split("|||")
                if (parts.size >= 3) {
                    ChannelItem(
                        id = parts[0],
                        name = parts[1],
                        url = parts[2],
                        avatarUrl = parts.getOrNull(3)?.takeIf { it.isNotEmpty() }
                    )
                } else null
            }
            if (list.isNotEmpty()) {
                val current = _subscribedChannels.value
                val merged = (list + current).distinctBy { it.id.ifEmpty { it.name } }
                _subscribedChannels.value = merged
            }
        } catch (_: Exception) {}
    }

    private fun saveSubsToPrefs() {
        try {
            val set = _subscribedChannels.value.map {
                "${it.id}|||${it.name}|||${it.url}|||${it.avatarUrl.orEmpty()}"
            }.toSet()
            subsPrefs.edit().putStringSet("subscribed_channels", set).apply()
        } catch (_: Exception) {}
    }

    fun isLikedLocally(videoId: String): Boolean = synchronized(userLikedVideoIds) {
        userLikedVideoIds.contains(videoId)
    }

    fun isDislikedLocally(videoId: String): Boolean = synchronized(userLikedVideoIds) {
        userDislikedVideoIds.contains(videoId)
    }

    fun isSubscribedLocally(channelIdOrName: String): Boolean {
        if (channelIdOrName.isBlank()) return false
        val clean = if (channelIdOrName.startsWith("http")) channelIdOrName.substringAfterLast("/") else channelIdOrName
        val cleanName = clean.trimStart('@').lowercase().trim()
        return _subscribedChannels.value.any { sub ->
            sub.id.equals(clean, ignoreCase = true) ||
            sub.name.trimStart('@').lowercase().trim() == cleanName ||
            (cleanName.isNotEmpty() && sub.name.lowercase().contains(cleanName)) ||
            (clean.isNotEmpty() && sub.url.contains(clean, ignoreCase = true))
        }
    }

    // Pengunci konkurensi agar sinkronisasi tidak pernah berjalan ganda/bertabrakan
    private val syncMutex = Mutex()

    init {
        loadLikesFromPrefs()
        loadSubsFromPrefs()

        val scope = CoroutineScope(ioDispatcher)

        // 1. Pantau akun Google (hanya bereaksi jika status login atau cookies benar-benar berubah)
        scope.launch {
            authRepository.account
                .distinctUntilChangedBy { Pair(it.isLoggedIn, it.cookies) }
                .collect { account ->
                    if (account.isLoggedIn && account.cookies.isNotBlank()) {
                        OkHttpDownloader.currentCookies = account.cookies
                        syncAll()
                    } else {
                        OkHttpDownloader.currentCookies = ""
                        _homeFeed.value = emptyList()
                        _subscriptionFeed.value = emptyList()
                        _watchHistory.value = emptyList()
                        _userPlaylists.value = emptyList()
                        remoteHistoryCache.clear()
                        localWatchTimestamps.clear()
                        // Pulihkan data suka dan langganan lokal dari preferensi agar tidak pernah hilang
                        loadLikesFromPrefs()
                        loadSubsFromPrefs()
                        _syncStatus.value = "Belum masuk akun"
                    }
                }
        }

        // 2. Pantau database lokal Room riwayat tontonan
        scope.launch {
            historyDao.getAllFlow().collect { entities ->
                entities.forEach { entity ->
                    localWatchTimestamps.putIfAbsent(entity.videoId, entity.lastWatchedAt)
                }
                val localVideos = entities.map { it.toVideoItem() }
                val merged = (localVideos + remoteHistoryCache).distinctBy { it.id }
                _watchHistory.value = merged
            }
        }
    }

    /**
     * Memperbarui seluruh data akun Google dan konten YouTube pengguna secara paralel dan sangat ringan.
     */
    suspend fun syncAll(): Result<Unit> = withContext(ioDispatcher) {
        val account = authRepository.account.first()
        if (!account.isLoggedIn || account.cookies.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Pengguna belum masuk ke akun Google"))
        }

        // Cegah sinkronisasi ganda: jika sinkronisasi sedang berjalan, lewati langsung tanpa membebani antrean
        if (!syncMutex.tryLock()) {
            return@withContext Result.success(Unit)
        }

        _isSyncing.value = true
        _syncStatus.value = "Menyinkronkan Data"
        try {
            // Jalankan seluruh pemanggilan API YouTube secara simultan / paralel via coroutineScope
            coroutineScope {
                val profileDeferred = async { syncAccountProfile(account.cookies) }
                val homeDeferred = async { fetchHomeFeed(account.cookies) }
                val subDeferred = async { fetchSubscribedChannels(account.cookies) }
                val subFeedDeferred = async { fetchSubscriptionFeed(account.cookies) }
                val histDeferred = async { fetchWatchHistory(account.cookies) }
                val plDeferred = async { fetchUserPlaylists(account.cookies) }
                val likedDeferred = async { fetchPlaylistDetails("LL") }

                profileDeferred.await()
                val homeRes = homeDeferred.await()
                val subRes = subDeferred.await()
                subFeedDeferred.await()
                val histRes = histDeferred.await()
                val plRes = plDeferred.await()
                likedDeferred.await()

                val subsCount = subRes.getOrNull()?.size ?: 0
                val histCount = histRes.getOrNull()?.size ?: _watchHistory.value.size
                val plCount = plRes.getOrNull()?.size ?: 0
                val homeCount = homeRes.getOrNull()?.first?.size ?: 0

                _syncStatus.value = "Tersinkronisasi"
            }
            Result.success(Unit)
        } catch (e: Exception) {
            _syncStatus.value = "Gagal sinkron: ${e.message ?: "Kesalahan jaringan"}"
            Result.failure(e)
        } finally {
            _isSyncing.value = false
            syncMutex.unlock()
        }
    }

    /**
     * Mengambil Feed Beranda Personal akun pengguna dari YouTube (FEwhat_to_watch)
     * termasuk Rekomendasi Video dan Mix Video (radioRenderer).
     */
    suspend fun fetchHomeFeed(cookies: String, continuationToken: String? = null): Result<Pair<List<VideoItem>, String?>> = withContext(ioDispatcher) {
        try {
            val payload = createInnertubeContext().apply {
                if (continuationToken != null) {
                    put("continuation", continuationToken)
                } else {
                    put("browseId", "FEwhat_to_watch")
                }
            }
            val responseJson = postInnertube("browse", payload, cookies)

            val items = mutableListOf<VideoItem>()
            extractFeedItems(responseJson, items)

            // Cari continuation token berikutnya secara komprehensif
            val nextContinuation = extractNextContinuationToken(responseJson)

            val distinctItems = items.distinctBy { it.id }
            if (continuationToken == null && distinctItems.isNotEmpty()) {
                _homeFeed.value = distinctItems
            }
            Result.success(Pair(distinctItems, nextContinuation))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengambil profil akun Google/YouTube (Nama, Handle @, dan Foto Profil Asli).
     */
    suspend fun syncAccountProfile(cookies: String): Result<GoogleAccount> = withContext(ioDispatcher) {
        try {
            var displayName = ""
            var handle = ""
            var email = ""
            var avatarUrl = ""

            // 1. Coba lewat Innertube account/account_menu
            try {
                val payload = createInnertubeContext()
                val responseJson = postInnertube("account/account_menu", payload, cookies)

                val headerList = mutableListOf<JSONObject>()
                findObjectsWithKey(responseJson, "activeAccountHeaderRenderer", headerList)
                if (headerList.isNotEmpty()) {
                    val header = headerList.first()
                    displayName = getTextFromRunsOrSimple(header.optJSONObject("accountName"))
                    handle = getTextFromRunsOrSimple(header.optJSONObject("channelHandle"))
                    email = getTextFromRunsOrSimple(header.optJSONObject("email"))
                    avatarUrl = getHighestResThumbnail(header.optJSONObject("accountPhoto"))
                }
            } catch (_: Exception) {}

            // 2. Coba lewat Innertube guide endpoint jika nama atau foto belum didapat
            if (displayName.isBlank() || avatarUrl.isBlank()) {
                try {
                    val payload = createInnertubeContext()
                    val guideJson = postInnertube("guide", payload, cookies)
                    val guideHeaderList = mutableListOf<JSONObject>()
                    findObjectsWithKey(guideJson, "guideAccountHeaderRenderer", guideHeaderList)
                    findObjectsWithKey(guideJson, "activeAccountHeaderRenderer", guideHeaderList)
                    if (guideHeaderList.isNotEmpty()) {
                        val header = guideHeaderList.first()
                        val title = getTextFromRunsOrSimple(header.optJSONObject("channelTitle"))
                            .ifEmpty { getTextFromRunsOrSimple(header.optJSONObject("accountName")) }
                        val h = getTextFromRunsOrSimple(header.optJSONObject("channelHandle"))
                        val thumb = getHighestResThumbnail(header.optJSONObject("thumbnail"))
                            .ifEmpty { getHighestResThumbnail(header.optJSONObject("accountPhoto")) }
                        if (title.isNotBlank() && displayName.isBlank()) displayName = title
                        if (h.isNotBlank() && handle.isBlank()) handle = h
                        if (thumb.isNotBlank() && avatarUrl.isBlank()) avatarUrl = thumb
                    }
                } catch (_: Exception) {}
            }

            // 3. Fallback scraping jika endpoint Innertube belum terisi
            if (avatarUrl.isBlank() || displayName.isBlank()) {
                val webProfile = fetchWebProfileFallback(cookies)
                if (displayName.isBlank() && webProfile.displayName.isNotBlank() && webProfile.displayName != "@.") {
                    displayName = webProfile.displayName
                }
                if (handle.isBlank() && !webProfile.handle.isNullOrBlank() && webProfile.handle != "@.") {
                    handle = webProfile.handle.orEmpty()
                }
                if (avatarUrl.isBlank() && !webProfile.avatarUrl.isNullOrBlank()) {
                    avatarUrl = webProfile.avatarUrl.orEmpty()
                }
                if (email.isBlank() && webProfile.email.isNotBlank()) {
                    email = webProfile.email
                }
            }

            // Sanitasi ketat: Cegah nilai "@." atau karakter tidak valid
            if (handle == "@." || handle.length < 3 || !handle.startsWith("@")) {
                handle = ""
            }
            if (displayName == "@." || displayName.isBlank()) {
                displayName = if (handle.isNotBlank()) handle.removePrefix("@") else "Pengguna YouTube"
            }
            if (avatarUrl.startsWith("//")) avatarUrl = "https:$avatarUrl"
            avatarUrl = avatarUrl.replace("\\u0026", "&").trimEnd('"', '\'', '\\', ')')

            val finalAccount = GoogleAccount(
                email = email.ifEmpty { "Akun YouTube Terhubung" },
                displayName = displayName,
                handle = handle.ifEmpty { null },
                avatarUrl = avatarUrl.ifEmpty { null },
                cookies = cookies,
                isLoggedIn = true,
            )

            val currentAccount = authRepository.account.first()
            if (currentAccount.displayName != finalAccount.displayName ||
                currentAccount.handle != finalAccount.handle ||
                currentAccount.avatarUrl != finalAccount.avatarUrl ||
                currentAccount.email != finalAccount.email
            ) {
                authRepository.saveLogin(
                    email = finalAccount.email,
                    displayName = finalAccount.displayName,
                    handle = finalAccount.handle,
                    avatarUrl = finalAccount.avatarUrl,
                    cookies = cookies,
                )
            }

            Result.success(finalAccount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengambil daftar saluran yang dilanggani (Subscribed Channels) dari YouTube.
     */
    suspend fun fetchSubscribedChannels(cookies: String): Result<List<ChannelItem>> = withContext(ioDispatcher) {
        try {
            val payload = createInnertubeContext().apply {
                put("browseId", "FEchannels")
            }
            val responseJson = postInnertube("browse", payload, cookies)

            val channelRenderers = mutableListOf<JSONObject>()
            findObjectsWithKey(responseJson, "channelRenderer", channelRenderers)
            findObjectsWithKey(responseJson, "gridChannelRenderer", channelRenderers)

            val channels = channelRenderers.mapNotNull { renderer ->
                val channelId = renderer.optString("channelId").ifEmpty {
                    renderer.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                        .orEmpty()
                }
                if (channelId.isBlank()) return@mapNotNull null

                val title = getTextFromRunsOrSimple(renderer.optJSONObject("title"))
                val avatar = getHighestResThumbnail(renderer.optJSONObject("thumbnail"))
                val subscriberText = getTextFromRunsOrSimple(renderer.optJSONObject("subscriberCountText"))
                    .ifEmpty { getTextFromRunsOrSimple(renderer.optJSONObject("videoCountText")) }

                ChannelItem(
                    id = channelId,
                    url = "https://www.youtube.com/channel/$channelId",
                    name = title.ifEmpty { "Saluran YouTube" },
                    avatarUrl = avatar.ifEmpty { null },
                    description = subscriberText,
                )
            }.distinctBy { it.id }.toMutableList()

            // Ekstrak saluran dari format modern lockupViewModel
            val lockupViewModels = mutableListOf<JSONObject>()
            findObjectsWithKey(responseJson, "lockupViewModel", lockupViewModels)
            for (lvm in lockupViewModels) {
                val cType = lvm.optString("contentType")
                val cId = lvm.optString("contentId")
                if (cType.contains("CHANNEL", ignoreCase = true) || cId.startsWith("UC")) {
                    val metaVm = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                    val title = metaVm?.optJSONObject("title")?.optString("content")
                        ?: getTextFromRunsOrSimple(metaVm?.optJSONObject("title"))
                    val thumbVm = lvm.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")
                    val sources = thumbVm?.optJSONObject("image")?.optJSONArray("sources")
                    val avatar = sources?.optJSONObject(sources.length() - 1)?.optString("url")
                        ?: sources?.optJSONObject(0)?.optString("url")

                    channels.add(
                        ChannelItem(
                            id = cId,
                            url = "https://www.youtube.com/channel/$cId",
                            name = title.ifEmpty { "Saluran YouTube" },
                            avatarUrl = avatar,
                            description = null,
                        )
                    )
                }
            }

            // Jika FEchannels kosong, ambil dari daftar saluran di guide endpoint
            if (channels.isEmpty()) {
                try {
                    val guidePayload = createInnertubeContext()
                    val guideJson = postInnertube("guide", guidePayload, cookies)
                    val guideEntries = mutableListOf<JSONObject>()
                    findObjectsWithKey(guideJson, "guideEntryRenderer", guideEntries)
                    for (entry in guideEntries) {
                        val browseId = entry.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId")
                            .orEmpty()
                        if (browseId.startsWith("UC")) {
                            val title = getTextFromRunsOrSimple(entry.optJSONObject("formattedTitle"))
                                .ifEmpty { entry.optString("title") }
                            val thumb = getHighestResThumbnail(entry.optJSONObject("thumbnail"))
                            channels.add(
                                ChannelItem(
                                    id = browseId,
                                    url = "https://www.youtube.com/channel/$browseId",
                                    name = title.ifEmpty { "Saluran YouTube" },
                                    avatarUrl = thumb.ifEmpty { null },
                                    description = null,
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            val distinctChannels = channels.distinctBy { it.id }
            _subscribedChannels.value = distinctChannels
            Result.success(distinctChannels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengambil feed video terbaru dari saluran yang dilanggani (Subscriptions Feed).
     */
    suspend fun fetchSubscriptionFeed(cookies: String): Result<List<VideoItem>> = withContext(ioDispatcher) {
        try {
            val payload = createInnertubeContext().apply {
                put("browseId", "FEsubscriptions")
            }
            val responseJson = postInnertube("browse", payload, cookies)

            val items = mutableListOf<VideoItem>()
            extractFeedItems(responseJson, items)

            val videos = items.distinctBy { it.id }
            _subscriptionFeed.value = videos
            Result.success(videos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengambil riwayat tontonan akun YouTube pengguna (Watch History).
     * Mengekstrak secara terstruktur & ketat dari respon YouTube (FEhistory) sesuai urutan kronologis asli.
     * Memberikan jarak timestamp deterministik pada database Room agar urutan tontonan selalu persis sama
     * dengan aplikasi YouTube resmi.
     */
    suspend fun fetchWatchHistory(cookies: String): Result<List<VideoItem>> = withContext(ioDispatcher) {
        try {
            val payload = createInnertubeContext().apply {
                put("browseId", "FEhistory")
            }
            val responseJson = postInnertube("browse", payload, cookies)

            val remoteItems = mutableListOf<VideoItem>()
            extractWatchHistoryStrict(responseJson, remoteItems)

            val remoteDistinct = remoteItems.distinctBy { it.id }

            // Ambil riwayat lokal Room saat ini
            val localEntities = historyDao.getRecent(100)
            val localMap = localEntities.associateBy { it.videoId }

            val now = System.currentTimeMillis()

            // 1. Dapatkan video lokal yang baru saja diputar / ditonton di perangkat ini.
            // Video-video ini HARUS berada di urutan paling depan / atas!
            val recentLocalEntities = localEntities.filter { entity ->
                localWatchTimestamps.containsKey(entity.videoId)
            }.sortedByDescending { entity ->
                localWatchTimestamps[entity.videoId] ?: entity.lastWatchedAt
            }

            // 2. Perkaya metadata remote YouTube dengan progres tonton lokal jika ada
            val enrichedRemote = remoteDistinct.map { remote ->
                val local = localMap[remote.id]
                val effectivePercent = when {
                    (local?.playbackPercent ?: 0f) > 0f -> local!!.playbackPercent
                    remote.playbackPercent > 0f -> remote.playbackPercent
                    else -> 0f
                }
                val effectivePos = when {
                    (local?.playbackPositionMs ?: 0L) > 0L -> local!!.playbackPositionMs
                    remote.playbackPositionMs > 0L -> remote.playbackPositionMs
                    effectivePercent > 0f && remote.durationSeconds > 0L -> (effectivePercent * remote.durationSeconds * 1000L).toLong()
                    else -> 0L
                }
                remote.copy(
                    playbackPositionMs = effectivePos,
                    playbackPercent = effectivePercent
                )
            }

            // 3. Bangun daftar gabungan yang menempatkan video yang baru diputar di posisi paling atas / depan:
            val mergedList = mutableListOf<VideoItem>()

            // A. Masukkan SEMUA video lokal yang baru diputar terlebih dahulu (posisi paling depan / atas!)
            for (local in recentLocalEntities) {
                mergedList.add(local.toVideoItem())
            }

            // B. Tambahkan video dari remote YouTube (lewati jika sudah ada di recentLocalEntities agar tidak mundur posisinya)
            val addedIds = mergedList.map { it.id }.toMutableSet()
            for (remote in enrichedRemote) {
                if (!addedIds.contains(remote.id)) {
                    mergedList.add(remote)
                    addedIds.add(remote.id)
                }
            }

            // C. Tambahkan sisa video lokal lama jika ada yang belum masuk
            for (local in localEntities) {
                if (!addedIds.contains(local.videoId)) {
                    mergedList.add(local.toVideoItem())
                    addedIds.add(local.videoId)
                }
            }

            // 4. Simpan ke database Room dengan urutan timestamp mundur SECARA KONSISTEN
            // Video index 0 (yang baru diputar) dijamin mendapatkan timestamp tertinggi (now)!
            // Video index 1 mendapatkan now - 1s, index 2 mendapatkan now - 2s, dst.
            // Dengan demikian, query Room `ORDER BY lastWatchedAt DESC` 100% konsisten dengan mergedList!
            val entitiesToSave = mergedList.mapIndexed { index, video ->
                WatchHistoryEntity.fromVideoItem(video, now - (index * 1000L))
            }
            try {
                historyDao.upsertAll(entitiesToSave)
            } catch (_: Exception) {}

            synchronized(remoteHistoryCache) {
                remoteHistoryCache.clear()
                remoteHistoryCache.addAll(mergedList)
            }
            _watchHistory.value = mergedList
            Result.success(mergedList)
        } catch (e: Exception) {
            // Jika network gagal, tetap tampilkan riwayat lokal Room yang tersimpan
            val localEntities = historyDao.getRecent(100)
            val localVideos = localEntities.map { it.toVideoItem() }
            if (localVideos.isNotEmpty()) {
                _watchHistory.value = localVideos
                Result.success(localVideos)
            } else {
                Result.failure(e)
            }
        }
    }

    // Pelacakan sesi playback YouTube untuk sinkronisasi riwayat tontonan ke server YouTube
    @Volatile
    private var activeCpn: String = ""
    @Volatile
    private var activeVideoId: String = ""
    @Volatile
    private var lastReportedSecond: Long = -1L
    @Volatile
    private var lastLocalDbWriteSecond: Long = -1L

    private fun generateCpn(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        return (1..16).map { chars.random() }.joinToString("")
    }

    /**
     * Mencatat kata kunci pencarian ke akun YouTube jika login.
     */
    suspend fun recordSearchQuery(query: String) = withContext(ioDispatcher) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext
        val account = authRepository.account.first()
        if (!account.isLoggedIn || account.cookies.isBlank()) return@withContext
        try {
            val payload = createInnertubeContext().apply {
                put("query", trimmed)
            }
            postInnertube("search", payload, account.cookies)
        } catch (_: Exception) {
            // Abaikan kesalahan network latar belakang pencatatan
        }
    }

    /**
     * Mengambil daftar notifikasi terbaru dari akun YouTube pengguna.
     */
    suspend fun fetchNotifications(): List<KATubeNotification> = withContext(ioDispatcher) {
        val account = authRepository.account.first()
        if (!account.isLoggedIn || account.cookies.isBlank()) return@withContext emptyList()
        try {
            val payload = createInnertubeContext().apply {
                put("notificationsMenuRequestType", "NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX")
            }
            val res = postInnertube("notification/get_notification_menu", payload, account.cookies)
            val list = mutableListOf<KATubeNotification>()
            fun scan(obj: Any) {
                when (obj) {
                    is JSONObject -> {
                        if (obj.has("notificationRenderer")) {
                            val r = obj.optJSONObject("notificationRenderer")
                            if (r != null) {
                                val notifId = r.optString("notificationId")
                                var title = ""
                                val runs = r.optJSONObject("shortMessage")?.optJSONArray("runs")
                                if (runs != null) {
                                    val sb = StringBuilder()
                                    for (i in 0 until runs.length()) {
                                        sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
                                    }
                                    title = sb.toString().trim()
                                }
                                if (title.isBlank()) {
                                    title = r.optJSONObject("shortMessage")?.optString("simpleText").orEmpty()
                                }

                                val timeText = r.optJSONObject("sentTimeText")?.optString("simpleText")
                                    ?: r.optJSONObject("sentTimeText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()

                                val avatarUrl = r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
                                val thumbUrl = r.optJSONObject("videoThumbnail")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
                                    ?: r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")?.let {
                                        if (it.length() > 1) it.optJSONObject(1)?.optString("url") else null
                                    }

                                var videoId: String? = null
                                val nav = r.optJSONObject("navigationEndpoint")
                                val watch = nav?.optJSONObject("watchEndpoint")
                                if (watch != null) {
                                    videoId = watch.optString("videoId")
                                } else {
                                    val url = nav?.optJSONObject("urlEndpoint")?.optString("url").orEmpty()
                                    if (url.contains("v=")) {
                                        videoId = url.substringAfter("v=").substringBefore("&")
                                    }
                                }

                                val isRead = r.optBoolean("read", false)
                                if (title.isNotBlank()) {
                                    list.add(
                                        KATubeNotification(
                                            id = notifId.ifEmpty { "notif_${list.size}" },
                                            title = title,
                                            timeText = timeText,
                                            avatarUrl = avatarUrl,
                                            thumbnailUrl = thumbUrl,
                                            videoId = videoId,
                                            isRead = isRead,
                                        )
                                    )
                                }
                            }
                        }
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            scan(obj.opt(keys.next()) ?: continue)
                        }
                    }
                    is JSONArray -> {
                        for (i in 0 until obj.length()) {
                            scan(obj.opt(i) ?: continue)
                        }
                    }
                }
            }
            scan(res)
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Mencatat video yang sedang ditonton ke database Room lokal secara instan dan permanen,
     * serta memulai sesi playback resmi ke YouTube agar tercatat di riwayat akun YouTube server.
     */
    fun recordLocalWatchHistory(video: VideoItem) {
        CoroutineScope(ioDispatcher).launch {
            // Cek apakah riwayat tontonan diaktifkan oleh pengguna di Pengaturan
            try {
                val isHistoryEnabled = preferencesRepository.watchHistoryEnabled.first()
                if (!isHistoryEnabled) return@launch
            } catch (_: Exception) {}

            val account = authRepository.account.first()
            val isLoggedIn = account.isLoggedIn && account.cookies.isNotBlank()

            // Pertahankan progres tontonan (playbackPosition & playbackPercent) yang sudah ada sebelumnya
            // agar tidak ter-reset menjadi 0 saat video baru mulai dibuka
            val existingLocal = historyDao.getById(video.id)
            val existingMem = _watchHistory.value.firstOrNull { it.id == video.id }
            val existingRemote = remoteHistoryCache.firstOrNull { it.id == video.id }

            val preservedPos = when {
                video.playbackPositionMs > 0L -> video.playbackPositionMs
                (existingLocal?.playbackPositionMs ?: 0L) > 0L -> existingLocal!!.playbackPositionMs
                (existingMem?.playbackPositionMs ?: 0L) > 0L -> existingMem!!.playbackPositionMs
                (existingRemote?.playbackPositionMs ?: 0L) > 0L -> existingRemote!!.playbackPositionMs
                else -> 0L
            }

            val preservedPercent = when {
                video.playbackPercent > 0f -> video.playbackPercent
                (existingMem?.playbackPercent ?: 0f) > 0f -> existingMem!!.playbackPercent
                (existingRemote?.playbackPercent ?: 0f) > 0f -> existingRemote!!.playbackPercent
                (existingLocal?.playbackPercent ?: 0f) > 0f -> existingLocal!!.playbackPercent
                else -> 0f
            }

            val preservedDuration = if (video.durationSeconds > 0L) {
                video.durationSeconds
            } else {
                existingMem?.durationSeconds ?: existingRemote?.durationSeconds ?: existingLocal?.durationSeconds ?: 0L
            }

            val updatedVideo = video.copy(
                playbackPositionMs = preservedPos,
                playbackPercent = preservedPercent,
                durationSeconds = preservedDuration
            )

            // 1. Simpan permanen ke database Room lokal (posisi paling depan / waktu sekarang)
            val now = System.currentTimeMillis()
            localWatchTimestamps[video.id] = now
            val entity = WatchHistoryEntity.fromVideoItem(updatedVideo, now)
            historyDao.upsert(entity)

            // 2. Perbarui state flow secara instan di UI (posisi paling depan / atas)
            val current = _watchHistory.value.toMutableList()
            current.removeAll { it.id == video.id }
            current.add(0, updatedVideo)
            _watchHistory.value = current

            synchronized(remoteHistoryCache) {
                remoteHistoryCache.removeAll { it.id == video.id }
                remoteHistoryCache.add(0, updatedVideo)
            }

            // 3. Inisialisasi sesi tontonan remote YouTube dengan token otentikasi resmi & signed telemetry
            if (isLoggedIn) {
                startRemoteWatchSession(video.id, account.cookies, initialPositionMs = preservedPos)
            }
        }
    }

    /**
     * Memulai sesi tontonan pada server YouTube menggunakan halaman tontonan resmi dan API telemetry resmi.
     * Mengambil signed URLs (videostatsPlaybackUrl & videostatsWatchtimeUrl) yang memuat token kriptografis
     * (plid, ei, len, of, vm) sehingga server YouTube mencatat video ini secara sah ke Riwayat Akun YouTube.
     */
    private suspend fun startRemoteWatchSession(videoId: String, cookies: String, initialPositionMs: Long = 0L) {
        withContext(ioDispatcher) {
            try {
                if (activeVideoId == videoId && activeCpn.isNotEmpty()) {
                    return@withContext
                }

                val cpn = generateCpn()
                activeCpn = cpn
                activeVideoId = videoId
                lastReportedSecond = -1L
                lastLocalDbWriteSecond = -1L

                val sapisidHash = YouTubeAuthUtils.generateSapisidHash(cookies)

                // 1. Dapatkan URL pelacakan resmi yang sudah ditandatangani secara kriptografis dari YouTube
                var signedPbUrl: String? = null
                var signedWtUrl: String? = null

                try {
                    val watchPageUrl = "https://www.youtube.com/watch?v=$videoId"
                    val watchReq = Request.Builder()
                        .url(watchPageUrl)
                        .get()
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                        .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .addHeader("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
                        .addHeader("Origin", "https://www.youtube.com")
                        .addHeader("Referer", "https://www.youtube.com/")
                        .addHeader("Cookie", cookies)
                        .apply {
                            if (!sapisidHash.isNullOrBlank()) addHeader("Authorization", sapisidHash)
                        }
                        .build()

                    client.newCall(watchReq).execute().use { resp ->
                        val html = resp.body?.string().orEmpty()
                        val pbMatch = Regex(""""videostatsPlaybackUrl"\s*:\s*\{\s*"baseUrl"\s*:\s*"([^"]+)"""").find(html)
                        val wtMatch = Regex(""""videostatsWatchtimeUrl"\s*:\s*\{\s*"baseUrl"\s*:\s*"([^"]+)"""").find(html)
                        if (pbMatch != null) {
                            signedPbUrl = pbMatch.groupValues[1].replace("\\u0026", "&")
                        }
                        if (wtMatch != null) {
                            signedWtUrl = wtMatch.groupValues[1].replace("\\u0026", "&")
                        }
                    }
                } catch (_: Exception) {}

                activePlaybackUrl = signedPbUrl
                activeWatchtimeUrl = signedWtUrl

                // 2. Kirim beacon inisialisasi playback ke server YouTube resmi
                val targetPbUrl = if (!signedPbUrl.isNullOrEmpty()) {
                    "$signedPbUrl&cpn=$cpn&ver=2"
                } else {
                    "https://www.youtube.com/api/stats/playback?ns=yt&el=detailpage&cpn=$cpn&docid=$videoId&ver=2"
                }

                val pbReq = Request.Builder()
                    .url(targetPbUrl)
                    .get()
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .addHeader("Origin", "https://www.youtube.com")
                    .addHeader("Referer", "https://www.youtube.com/watch?v=$videoId")
                    .addHeader("X-YouTube-Client-Name", "1")
                    .addHeader("X-YouTube-Client-Version", INNERTUBE_CLIENT_VERSION)
                    .addHeader("X-Goog-AuthUser", "0")
                    .addHeader("Cookie", cookies)
                    .apply {
                        if (!sapisidHash.isNullOrBlank()) addHeader("Authorization", sapisidHash)
                    }
                    .build()

                try {
                    client.newCall(pbReq).execute().close()
                } catch (_: Exception) {}

                // 3. Kirim beacon awal watchtime ke server YouTube resmi
                val initSec = (initialPositionMs / 1000f).coerceAtLeast(0f)
                val initSecInt = initSec.toLong()
                val cmtStr = String.format(java.util.Locale.US, "%.3f", initSec)
                val targetInitialWtUrl = if (!signedWtUrl.isNullOrEmpty()) {
                    "$signedWtUrl&cpn=$cpn&ver=2&cmt=$cmtStr&state=playing&st=$cmtStr&et=$cmtStr&rt=$initSecInt&lact=${System.currentTimeMillis()}"
                } else {
                    "https://www.youtube.com/api/stats/watchtime?ns=yt&el=detailpage&cpn=$cpn&docid=$videoId&ver=2&cmt=$cmtStr&state=playing&st=$cmtStr&et=$cmtStr&rt=$initSecInt&lact=${System.currentTimeMillis()}"
                }

                val initialWtReq = Request.Builder()
                    .url(targetInitialWtUrl)
                    .get()
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .addHeader("Origin", "https://www.youtube.com")
                    .addHeader("Referer", "https://www.youtube.com/watch?v=$videoId")
                    .addHeader("X-YouTube-Client-Name", "1")
                    .addHeader("X-YouTube-Client-Version", INNERTUBE_CLIENT_VERSION)
                    .addHeader("X-Goog-AuthUser", "0")
                    .addHeader("Cookie", cookies)
                    .apply {
                        if (!sapisidHash.isNullOrBlank()) addHeader("Authorization", sapisidHash)
                    }
                    .build()

                try {
                    client.newCall(initialWtReq).execute().close()
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }

    /**
     * Melaporkan progres tontonan / watchtime ke server YouTube (/api/stats/watchtime).
     * Server YouTube membutuhkan watchtime ini agar video masuk ke dalam Riwayat Tontonan resmi
     * akun Google pengguna dan dapat dilihat di aplikasi YouTube resmi.
     */
    @Suppress("UNUSED_PARAMETER")
    fun reportPlaybackProgress(videoId: String, positionMs: Long, durationMs: Long, isPausedOrEnded: Boolean = false) {
        CoroutineScope(ioDispatcher).launch {
            try {
                val account = authRepository.account.first()
                if (!account.isLoggedIn || account.cookies.isBlank()) return@launch

                if (activeVideoId != videoId || activeCpn.isEmpty()) {
                    startRemoteWatchSession(videoId, account.cookies)
                }

                val cpn = if (activeVideoId == videoId && activeCpn.isNotEmpty()) activeCpn else generateCpn()
                val posSec = (positionMs / 1000f).coerceAtLeast(0f)
                val posSecInt = posSec.toLong()
                val percent = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

                // 1. Update real-time ke _watchHistory StateFlow secara instan di memori (halus & responsif di UI)
                val currentHist = _watchHistory.value
                if (currentHist.any { it.id == videoId }) {
                    _watchHistory.value = currentHist.map { item ->
                        if (item.id == videoId) {
                            item.copy(playbackPositionMs = positionMs, playbackPercent = percent)
                        } else item
                    }
                }

                // 2. Throttle penulisan ke database Room lokal (hanya saat jeda/selesai atau setiap 10 detik)
                // Ini mencegah disk I/O thrashing & query ulang Room yang membebani CPU setiap detik
                val shouldWriteDb = isPausedOrEnded || lastLocalDbWriteSecond < 0L || (posSecInt - lastLocalDbWriteSecond >= 10L)
                if (shouldWriteDb) {
                    lastLocalDbWriteSecond = posSecInt
                    val existing = historyDao.getById(videoId)
                    if (existing != null) {
                        historyDao.updateProgress(videoId, positionMs, percent)
                    }
                }

                // 3. Batasi pengiriman telemetry ke YouTube server: jika sedang memutar, jangan kirim jika selisih < 8 detik
                if (!isPausedOrEnded && lastReportedSecond >= 0L && posSecInt - lastReportedSecond < 8L) {
                    return@launch
                }
                lastReportedSecond = posSecInt

                val cmtFormatted = String.format(java.util.Locale.US, "%.3f", posSec)
                val state = if (isPausedOrEnded) "paused" else "playing"
                val sapisidHash = YouTubeAuthUtils.generateSapisidHash(account.cookies)

                val signedWt = activeWatchtimeUrl
                val watchtimeUrl = if (!signedWt.isNullOrEmpty()) {
                    "$signedWt&cpn=$cpn&ver=2&cmt=$cmtFormatted&state=$state&st=0.000&et=$cmtFormatted&rt=$posSecInt&lact=${System.currentTimeMillis()}"
                } else {
                    "https://www.youtube.com/api/stats/watchtime?" +
                            "ns=yt&el=detailpage&cpn=$cpn&docid=$videoId&ver=2" +
                            "&cmt=$cmtFormatted&state=$state&st=0.000&et=$cmtFormatted" +
                            "&rt=$posSecInt&lact=${System.currentTimeMillis()}"
                }

                val reqBuilder = Request.Builder()
                    .url(watchtimeUrl)
                    .get()
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .addHeader("Origin", "https://www.youtube.com")
                    .addHeader("Referer", "https://www.youtube.com/watch?v=$videoId")
                    .addHeader("X-YouTube-Client-Name", "1")
                    .addHeader("X-YouTube-Client-Version", INNERTUBE_CLIENT_VERSION)
                    .addHeader("X-Goog-AuthUser", "0")
                    .addHeader("Cookie", account.cookies)

                if (!sapisidHash.isNullOrBlank()) {
                    reqBuilder.addHeader("Authorization", sapisidHash)
                }

                client.newCall(reqBuilder.build()).execute().close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Mengambil daftar playlist YouTube pengguna (Tonton Nanti, Disukai, dan Playlist Buatan).
     */
    suspend fun fetchUserPlaylists(cookies: String): Result<List<PlaylistItem>> = withContext(ioDispatcher) {
        try {
            val payload = createInnertubeContext().apply {
                put("browseId", "FElibrary")
            }
            val responseJson = postInnertube("browse", payload, cookies)

            val playlistRenderers = mutableListOf<JSONObject>()
            findObjectsWithKey(responseJson, "playlistRenderer", playlistRenderers)
            findObjectsWithKey(responseJson, "gridPlaylistRenderer", playlistRenderers)
            findObjectsWithKey(responseJson, "compactPlaylistRenderer", playlistRenderers)

            val playlists = playlistRenderers.mapNotNull { renderer ->
                val playlistId = renderer.optString("playlistId")
                if (playlistId.isBlank()) return@mapNotNull null

                val title = getTextFromRunsOrSimple(renderer.optJSONObject("title"))
                val thumb = getHighestResThumbnail(renderer.optJSONObject("thumbnail"))
                val uploader = getTextFromRunsOrSimple(renderer.optJSONObject("shortBylineText"))
                val countText = getTextFromRunsOrSimple(renderer.optJSONObject("videoCountText"))
                    .ifEmpty { renderer.optString("videoCount") }

                PlaylistItem(
                    id = playlistId,
                    url = "https://www.youtube.com/playlist?list=$playlistId",
                    name = title.ifEmpty { "Playlist" },
                    uploaderName = uploader.ifEmpty { "Anda" },
                    thumbnailUrl = thumb.ifEmpty { null },
                    streamCount = countText.filter { it.isDigit() }.toLongOrNull() ?: 0L,
                )
            }.distinctBy { it.id }

            // Ekstrak playlist dari format modern lockupViewModel
            val lockupViewModels = mutableListOf<JSONObject>()
            findObjectsWithKey(responseJson, "lockupViewModel", lockupViewModels)
            val lockupPlaylists = lockupViewModels.mapNotNull { lvm ->
                val cType = lvm.optString("contentType")
                val cId = lvm.optString("contentId")
                if (cType.contains("PLAYLIST", ignoreCase = true) || cId.startsWith("PL") || cId.startsWith("LL") || cId.startsWith("WL")) {
                    val metaVm = lvm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                    val title = metaVm?.optJSONObject("title")?.optString("content")
                        ?: getTextFromRunsOrSimple(metaVm?.optJSONObject("title"))
                    val contentImage = lvm.optJSONObject("contentImage")
                    val thumbVm = contentImage?.optJSONObject("collectionThumbnailViewModel")
                        ?.optJSONObject("primaryThumbnail")
                        ?.optJSONObject("thumbnailViewModel")
                        ?: contentImage?.optJSONObject("thumbnailViewModel")
                    val sources = thumbVm?.optJSONObject("image")?.optJSONArray("sources")
                    val thumb = sources?.optJSONObject(sources.length() - 1)?.optString("url")
                        ?: sources?.optJSONObject(0)?.optString("url")

                    PlaylistItem(
                        id = cId,
                        url = "https://www.youtube.com/playlist?list=$cId",
                        name = title.ifEmpty { "Playlist" },
                        uploaderName = "Anda",
                        thumbnailUrl = thumb,
                        streamCount = 0L,
                    )
                } else null
            }

            val account = authRepository.account.first()
            val userDisplayName = if (account.isLoggedIn && account.displayName.isNotBlank()) {
                account.displayName
            } else {
                "Anda"
            }

            val defaultPlaylists = listOf(
                PlaylistItem(
                    id = "WL",
                    url = "https://www.youtube.com/playlist?list=WL",
                    name = "Tonton nanti",
                    uploaderName = userDisplayName,
                ),
                PlaylistItem(
                    id = "LL",
                    url = "https://www.youtube.com/playlist?list=LL",
                    name = "Video yang disukai",
                    uploaderName = userDisplayName,
                )
            )

            val combined = (defaultPlaylists + playlists + lockupPlaylists).distinctBy { it.id }
            _userPlaylists.value = combined
            Result.success(combined)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengambil detail playlist beserta seluruh daftar videonya langsung via Innertube API
     * (Mendukung Tonton Nanti "WL", Video yang Disukai "LL", dan Playlist Akun maupun Publik).
     */
    suspend fun fetchPlaylistDetails(playlistId: String): Result<PlaylistItem> = withContext(ioDispatcher) {
        try {
            val cleanId = playlistId
                .substringAfter("list=")
                .substringBefore("&")
                .removePrefix("VL")
                .trim()

            // 0. Penanganan Khusus YouTube Mix / Radio (RD..., RDMM...):
            // YouTube Mix bukan playlist browseable statis melainkan stream radio dinamis yang dilayani lewat endpoint 'next'.
            if (cleanId.startsWith("RD")) {
                val account = authRepository.account.first()
                val cookies = if (account.isLoggedIn) account.cookies else ""
                val payload = createInnertubeContext().apply {
                    put("playlistId", cleanId)
                }
                val responseJson = postInnertube("next", payload, cookies)
                val panelList = mutableListOf<JSONObject>()
                findObjectsWithKey(responseJson, "playlistPanelRenderer", panelList)
                val panel = panelList.firstOrNull()

                val mixTitle = panel?.optString("title")
                    ?: getTextFromRunsOrSimple(panel?.optJSONObject("title"))
                        .ifBlank { "YouTube Mix" }

                val mixVideos = mutableListOf<VideoItem>()
                extractFeedItems(panel ?: responseJson, mixVideos)
                val distinctVideos = mixVideos.distinctBy { it.id }.filter { isValidVideoId(it.id) }

                return@withContext Result.success(
                    PlaylistItem(
                        id = cleanId,
                        url = "https://www.youtube.com/playlist?list=$cleanId",
                        name = mixTitle,
                        uploaderName = "YouTube Mix",
                        thumbnailUrl = distinctVideos.firstOrNull()?.thumbnailUrl,
                        streamCount = distinctVideos.size.toLong(),
                        videos = distinctVideos
                    )
                )
            }

            val browseId = when {
                cleanId.equals("WL", ignoreCase = true) -> "VLWL"
                cleanId.equals("LL", ignoreCase = true) -> "VLLL"
                cleanId.startsWith("VL") -> cleanId
                else -> "VL$cleanId"
            }

            val account = authRepository.account.first()
            if (!account.isLoggedIn && (cleanId.equals("WL", ignoreCase = true) || cleanId.equals("LL", ignoreCase = true))) {
                val label = if (cleanId.equals("WL", ignoreCase = true)) "Tonton Nanti" else "Video yang Disukai"
                return@withContext Result.failure(IllegalStateException("Silakan masuk dengan akun Google untuk melihat $label."))
            }

            val cookies = if (account.isLoggedIn) account.cookies else ""
            val payload = createInnertubeContext().apply {
                put("browseId", browseId)
            }

            val responseJson = postInnertube("browse", payload, cookies)

            // 1. Ekstrak Judul Playlist
            var title = ""
            val metaRenderer = responseJson.optJSONObject("metadata")?.optJSONObject("playlistMetadataRenderer")
            if (metaRenderer != null) {
                title = metaRenderer.optString("title")
            }
            if (title.isBlank()) {
                val microformat = responseJson.optJSONObject("microformat")?.optJSONObject("microformatDataRenderer")
                title = microformat?.optString("title").orEmpty()
            }
            if (title.isBlank()) {
                val pageHeader = responseJson.optJSONObject("header")?.optJSONObject("pageHeaderRenderer")
                title = pageHeader?.optString("pageTitle").orEmpty()
            }
            if (title.isBlank()) {
                val plHeader = responseJson.optJSONObject("header")?.optJSONObject("playlistHeaderRenderer")
                title = getTextFromRunsOrSimple(plHeader?.optJSONObject("title"))
            }
            if (title.isBlank()) {
                title = when {
                    cleanId.equals("WL", ignoreCase = true) -> "Tonton nanti"
                    cleanId.equals("LL", ignoreCase = true) -> "Video yang disukai"
                    else -> "Playlist"
                }
            }

            // 2. Ekstrak Uploader / Author (Dinamis menyesuaikan nama profil / channel YouTube pengguna)
            var uploader = ""
            val plHeader = responseJson.optJSONObject("header")?.optJSONObject("playlistHeaderRenderer")
            if (plHeader != null) {
                uploader = getTextFromRunsOrSimple(plHeader.optJSONObject("ownerText"))
                    .ifEmpty { getTextFromRunsOrSimple(plHeader.optJSONObject("bylineText")) }
            }
            if (uploader.isBlank() || uploader.equals("YouTube", ignoreCase = true) || uploader.equals("Anda", ignoreCase = true)) {
                if (account.isLoggedIn && account.displayName.isNotBlank()) {
                    uploader = account.displayName
                } else if (uploader.isBlank()) {
                    uploader = "Anda"
                }
            }

            // 3. Ekstrak Thumbnail Playlist
            var playlistThumb: String? = null
            val microformat = responseJson.optJSONObject("microformat")?.optJSONObject("microformatDataRenderer")
            val microThumb = microformat?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            if (microThumb != null && microThumb.length() > 0) {
                playlistThumb = microThumb.optJSONObject(microThumb.length() - 1)?.optString("url")
            }
            if (playlistThumb.isNullOrBlank()) {
                playlistThumb = getHighestResThumbnail(plHeader?.optJSONObject("playlistHeaderBanner"))
                    .ifEmpty { getHighestResThumbnail(plHeader?.optJSONObject("thumbnail")) }
                    .ifEmpty { null }
            }

            // 4. Ekstrak Video Items
            val videos = mutableListOf<VideoItem>()

            // A. Cek lockupViewModel
            val lockupList = mutableListOf<JSONObject>()
            findObjectsWithKey(responseJson, "lockupViewModel", lockupList)
            for (lvm in lockupList) {
                parseLockupViewModel(lvm)?.let { video ->
                    if (isValidVideoId(video.id)) {
                        videos.add(video)
                    }
                }
            }

            // B. Cek playlistVideoRenderer
            val pvrList = mutableListOf<JSONObject>()
            findObjectsWithKey(responseJson, "playlistVideoRenderer", pvrList)
            for (pvr in pvrList) {
                parsePlaylistVideoRenderer(pvr)?.let { video ->
                    if (isValidVideoId(video.id)) {
                        videos.add(video)
                    }
                }
            }

            // C. Cek videoRenderer standar jika daftar masih kosong
            if (videos.isEmpty()) {
                val vrList = mutableListOf<JSONObject>()
                findObjectsWithKey(responseJson, "videoRenderer", vrList)
                findObjectsWithKey(responseJson, "compactVideoRenderer", vrList)
                for (vr in vrList) {
                    parseVideoRenderer(vr)?.let { video ->
                        if (isValidVideoId(video.id)) {
                            videos.add(video)
                        }
                    }
                }
            }

            val distinctVideos = videos.distinctBy { it.id }
            val finalThumb = playlistThumb ?: distinctVideos.firstOrNull()?.thumbnailUrl

            if (cleanId.equals("LL", ignoreCase = true) && distinctVideos.isNotEmpty()) {
                val ids = distinctVideos.map { it.id }.filter { isValidVideoId(it) }
                synchronized(userLikedVideoIds) {
                    userLikedVideoIds.addAll(ids)
                }
                saveLikesToPrefs()
            }

            val item = PlaylistItem(
                id = cleanId,
                url = "https://www.youtube.com/playlist?list=$cleanId",
                name = title,
                uploaderName = uploader,
                thumbnailUrl = finalThumb,
                streamCount = distinctVideos.size.toLong(),
                videos = distinctVideos,
            )
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Menghapus video dari playlist akun YouTube pengguna (misalnya dari Tonton Nanti "WL",
     * Video yang Disukai "LL", atau Playlist Buatan Sendiri "PL...").
     */
    suspend fun removeVideoFromPlaylist(playlistId: String, videoId: String): Result<Boolean> = withContext(ioDispatcher) {
        try {
            val account = authRepository.account.first()
            if (!account.isLoggedIn || account.cookies.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Silakan masuk dengan akun Google terlebih dahulu."))
            }

            val cleanId = playlistId
                .substringAfter("list=")
                .substringBefore("&")
                .removePrefix("VL")
                .trim()

            if (cleanId.equals("LL", ignoreCase = true)) {
                // Video yang disukai: hapus like melalui like/removelike
                val payload = createInnertubeContext().apply {
                    put("target", JSONObject().put("videoId", videoId))
                }
                val res = postInnertube("like/removelike", payload, account.cookies)
                return@withContext Result.success(!res.has("error"))
            }

            // Tonton Nanti ("WL") atau Playlist Pengguna ("PL...")
            val actionObj = JSONObject().apply {
                put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID")
                put("removedVideoId", videoId)
            }
            val payload = createInnertubeContext().apply {
                put("playlistId", cleanId)
                put("actions", JSONArray().put(actionObj))
            }

            val res = postInnertube("browse/edit_playlist", payload, account.cookies)
            val status = res.optString("status")
            val isSuccess = status.equals("STATUS_SUCCEEDED", ignoreCase = true) ||
                    res.has("actionsResult") ||
                    !res.has("error")

            Result.success(isSuccess)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Memberikan Suka (Like) pada video YouTube menggunakan akun yang terhubung.
     */
    suspend fun likeVideo(videoId: String, cookies: String? = null): Result<Boolean> = withContext(ioDispatcher) {
        try {
            synchronized(userLikedVideoIds) {
                userLikedVideoIds.add(videoId)
                userDislikedVideoIds.remove(videoId)
            }
            saveLikesToPrefs()

            // Perbarui juga daftar playlist LL di memori secara atomik
            val currentPlaylists = _userPlaylists.value.map { pl ->
                if (pl.id.equals("LL", ignoreCase = true)) {
                    val existing = pl.videos.toMutableList()
                    if (existing.none { it.id == videoId }) {
                        val newItem = VideoItem(
                            id = videoId,
                            url = "https://www.youtube.com/watch?v=$videoId",
                            title = "Video yang disukai",
                            uploaderName = "",
                        )
                        existing.add(0, newItem)
                    }
                    pl.copy(videos = existing, streamCount = existing.size.toLong())
                } else {
                    pl
                }
            }
            _userPlaylists.value = currentPlaylists

            val account = authRepository.account.first()
            val effectiveCookies = cookies ?: if (account.isLoggedIn) account.cookies else ""
            if (effectiveCookies.isNotBlank()) {
                val payload = createInnertubeContext().apply {
                    put("target", JSONObject().put("videoId", videoId))
                }
                postInnertube("like/like", payload, effectiveCookies)
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.success(true)
        }
    }

    /**
     * Memberikan Dislike pada video YouTube menggunakan akun yang terhubung.
     */
    suspend fun dislikeVideo(videoId: String, cookies: String? = null): Result<Boolean> = withContext(ioDispatcher) {
        try {
            synchronized(userLikedVideoIds) {
                userDislikedVideoIds.add(videoId)
                userLikedVideoIds.remove(videoId)
            }
            saveLikesToPrefs()

            val currentPlaylists = _userPlaylists.value.map { pl ->
                if (pl.id.equals("LL", ignoreCase = true)) {
                    val filtered = pl.videos.filter { it.id != videoId }
                    pl.copy(videos = filtered, streamCount = filtered.size.toLong())
                } else {
                    pl
                }
            }
            _userPlaylists.value = currentPlaylists

            val account = authRepository.account.first()
            val effectiveCookies = cookies ?: if (account.isLoggedIn) account.cookies else ""
            if (effectiveCookies.isNotBlank()) {
                val payload = createInnertubeContext().apply {
                    put("target", JSONObject().put("videoId", videoId))
                }
                postInnertube("like/dislike", payload, effectiveCookies)
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.success(true)
        }
    }

    /**
     * Menghapus rating Suka atau Dislike pada video YouTube.
     */
    suspend fun removeLikeOrDislike(videoId: String, cookies: String? = null): Result<Boolean> = withContext(ioDispatcher) {
        try {
            synchronized(userLikedVideoIds) {
                userLikedVideoIds.remove(videoId)
                userDislikedVideoIds.remove(videoId)
            }
            saveLikesToPrefs()

            val currentPlaylists = _userPlaylists.value.map { pl ->
                if (pl.id.equals("LL", ignoreCase = true)) {
                    val filtered = pl.videos.filter { it.id != videoId }
                    pl.copy(videos = filtered, streamCount = filtered.size.toLong())
                } else {
                    pl
                }
            }
            _userPlaylists.value = currentPlaylists

            val account = authRepository.account.first()
            val effectiveCookies = cookies ?: if (account.isLoggedIn) account.cookies else ""
            if (effectiveCookies.isNotBlank()) {
                val payload = createInnertubeContext().apply {
                    put("target", JSONObject().put("videoId", videoId))
                }
                postInnertube("like/removelike", payload, effectiveCookies)
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.success(true)
        }
    }

    /**
     * Melakukan Subscribe ke saluran YouTube menggunakan akun yang terhubung atau lokal.
     */
    suspend fun subscribeChannel(
        channelIdOrUrl: String,
        channelName: String? = null,
        channelAvatar: String? = null,
        cookies: String? = null
    ): Result<Boolean> = withContext(ioDispatcher) {
        try {
            var cleanChannelId = if (channelIdOrUrl.startsWith("http")) {
                channelIdOrUrl.substringAfterLast("/")
            } else {
                channelIdOrUrl
            }
            val account = authRepository.account.first()
            val effectiveCookies = cookies ?: if (account.isLoggedIn) account.cookies else ""
            if (effectiveCookies.isNotBlank() && !cleanChannelId.startsWith("UC")) {
                cleanChannelId = resolveChannelId(cleanChannelId, effectiveCookies) ?: cleanChannelId
            }
            val displayName = channelName?.takeIf { it.isNotBlank() } ?: channelIdOrUrl
            val currentSubs = _subscribedChannels.value.toMutableList()
            val existingIndex = currentSubs.indexOfFirst {
                it.id.equals(cleanChannelId, ignoreCase = true) ||
                it.name.equals(displayName, ignoreCase = true) ||
                it.name.equals(channelIdOrUrl, ignoreCase = true)
            }
            if (existingIndex < 0) {
                currentSubs.add(0, ChannelItem(
                    id = cleanChannelId,
                    url = if (cleanChannelId.startsWith("UC")) "https://www.youtube.com/channel/$cleanChannelId" else "https://www.youtube.com/$cleanChannelId",
                    name = displayName,
                    avatarUrl = channelAvatar
                ))
            } else {
                val existing = currentSubs[existingIndex]
                currentSubs[existingIndex] = existing.copy(
                    name = displayName,
                    avatarUrl = channelAvatar ?: existing.avatarUrl
                )
            }
            _subscribedChannels.value = currentSubs
            saveSubsToPrefs()

            if (effectiveCookies.isNotBlank()) {
                val payload = createInnertubeContext().apply {
                    put("channelIds", JSONArray().put(cleanChannelId))
                }
                postInnertube("subscription/subscribe", payload, effectiveCookies)
                CoroutineScope(ioDispatcher).launch { fetchSubscribedChannels(effectiveCookies) }
            }
            Result.success(true)
        } catch (e: Exception) {
            saveSubsToPrefs()
            Result.success(true)
        }
    }

    /**
     * Melakukan Unsubscribe dari saluran YouTube.
     */
    suspend fun unsubscribeChannel(channelIdOrUrl: String, cookies: String? = null): Result<Boolean> = withContext(ioDispatcher) {
        try {
            var cleanChannelId = if (channelIdOrUrl.startsWith("http")) {
                channelIdOrUrl.substringAfterLast("/")
            } else {
                channelIdOrUrl
            }
            val account = authRepository.account.first()
            val effectiveCookies = cookies ?: if (account.isLoggedIn) account.cookies else ""
            if (effectiveCookies.isNotBlank() && !cleanChannelId.startsWith("UC")) {
                cleanChannelId = resolveChannelId(cleanChannelId, effectiveCookies) ?: cleanChannelId
            }
            val cleanName = channelIdOrUrl.trimStart('@').lowercase().trim()
            _subscribedChannels.value = _subscribedChannels.value.filter {
                !it.id.equals(cleanChannelId, ignoreCase = true) &&
                !it.name.equals(channelIdOrUrl, ignoreCase = true) &&
                it.name.trimStart('@').lowercase().trim() != cleanName
            }
            saveSubsToPrefs()

            if (effectiveCookies.isNotBlank()) {
                val payload = createInnertubeContext().apply {
                    put("channelIds", JSONArray().put(cleanChannelId))
                }
                postInnertube("subscription/unsubscribe", payload, effectiveCookies)
                CoroutineScope(ioDispatcher).launch { fetchSubscribedChannels(effectiveCookies) }
            }
            Result.success(true)
        } catch (e: Exception) {
            saveSubsToPrefs()
            Result.success(true)
        }
    }

    suspend fun postComment(videoId: String, text: String, playlistId: String? = null): Result<Boolean> = withContext(ioDispatcher) {
        try {
            val account = authRepository.account.first()
            if (account.isLoggedIn && account.cookies.isNotBlank()) {
                // 1. Ambil createCommentParams dari endpoint next Innertube
                val nextPayload = createInnertubeContext().apply {
                    put("videoId", videoId)
                    if (!playlistId.isNullOrEmpty()) {
                        put("playlistId", playlistId)
                    }

                }
                var commentParams: String? = null
                try {
                    val nextJson = postInnertube("next", nextPayload, account.cookies)
                    val paramList = mutableListOf<JSONObject>()
                    findObjectsWithKey(nextJson, "createCommentParams", paramList)
                    commentParams = paramList.firstOrNull()?.optString("createCommentParams")
                        ?: Regex(""""createCommentParams":\s*"([^"]+)"""").find(nextJson.toString())?.groupValues?.getOrNull(1)
                } catch (_: Exception) {}

                val payload = createInnertubeContext().apply {
                    put("commentText", text)
                    if (!commentParams.isNullOrBlank()) {
                        put("createCommentParams", commentParams)
                    }
                }
                postInnertube("comment/create_comment", payload, account.cookies)
            }
            Result.success(true)
        } catch (_: Exception) {
            Result.success(true)
        }
    }

    suspend fun deleteComment(commentId: String): Result<Boolean> = withContext(ioDispatcher) {
        try {
            val account = authRepository.account.first()
            if (account.isLoggedIn && account.cookies.isNotBlank()) {
                val payload = createInnertubeContext().apply {
                    put("commentId", commentId)
                    put("actions", JSONArray().put(JSONObject().apply {
                        put("action", "ACTION_DELETE_COMMENT")
                        put("commentId", commentId)
                    }))
                }
                postInnertube("comment/perform_comment_action", payload, account.cookies)
            }
            Result.success(true)
        } catch (_: Exception) {
            Result.success(true)
        }
    }

    /**
     * Menambahkan video ke playlist pengguna (Tonton Nanti "WL" atau playlist buatan "PL...").
     */
    suspend fun addVideoToPlaylist(playlistId: String, videoId: String): Result<Boolean> = withContext(ioDispatcher) {
        try {
            val account = authRepository.account.first()
            if (!account.isLoggedIn || account.cookies.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Silakan masuk dengan akun Google untuk menyimpan ke playlist."))
            }
            val cleanId = playlistId.removePrefix("VL").trim()
            val actionObj = JSONObject().apply {
                put("action", "ACTION_ADD_VIDEO")
                put("addedVideoId", videoId)
            }
            val payload = createInnertubeContext().apply {
                put("playlistId", cleanId)
                put("actions", JSONArray().put(actionObj))
            }
            val res = postInnertube("browse/edit_playlist", payload, account.cookies)
            val status = res.optString("status")
            val isSuccess = status.equals("STATUS_SUCCEEDED", ignoreCase = true) ||
                    res.has("actionsResult") ||
                    !res.has("error")
            Result.success(isSuccess)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Data hasil pengambilan video terkait dan metadata video dari InnerTube watch next.
     */
    data class WatchNextData(
        val relatedVideos: List<VideoItem>,
        val nextContinuation: String?,
        val subscriberText: String? = null,
        val isSubscribed: Boolean? = null,
        val channelId: String? = null,
        val likeStatus: String? = null, // "LIKE", "DISLIKE", "INDIFFERENT"
        val channelAvatarUrl: String? = null,
        val commentTeaserText: String? = null,
        val commentTeaserAuthor: String? = null,
        val commentTeaserAvatar: String? = null,
        val commentCountText: String? = null,
        val viewCountText: String? = null,
        val uploadDateText: String? = null,
        val viewCountNumber: Long? = null,
    )

    /**
     * Mengambil daftar video terkait berikutnya (Watch Next Related Videos) dengan paginasi tak terbatas,
     * serta metadata interaktif (subscriber channel, status like/dislike akun, status subscribe).
     */
    suspend fun fetchWatchNextRelated(videoId: String, continuationToken: String? = null, playlistId: String? = null): Result<WatchNextData> = withContext(ioDispatcher) {
        try {
            val account = authRepository.account.first()
            val cookies = if (account.isLoggedIn) account.cookies else ""
            val payload = createInnertubeContext().apply {
                if (continuationToken != null) {
                    put("continuation", continuationToken)
                } else {
                    put("videoId", videoId)
                    if (!playlistId.isNullOrEmpty()) {
                        put("playlistId", playlistId)
                    }

                }
            }
            val responseJson = postInnertube("next", payload, cookies)

            val items = mutableListOf<VideoItem>()
            extractFeedItems(responseJson, items)
            val nextContinuation = extractWatchNextContinuationToken(responseJson)

            var subText: String? = null
            var channelId: String? = null
            var isSubscribed: Boolean? = null
            var likeStatus: String? = null
            var channelAvatarUrl: String? = null
            var commentTeaserText: String? = null
            var commentTeaserAuthor: String? = null
            var commentTeaserAvatar: String? = null
            var commentCountText: String? = null
            var viewCountText: String? = null
            var uploadDateText: String? = null
            var viewCountNumber: Long? = null

            // 1. Cek status like dari cache in-memory lokal
            if (userLikedVideoIds.contains(videoId)) {
                likeStatus = "LIKE"
            } else if (userDislikedVideoIds.contains(videoId)) {
                likeStatus = "DISLIKE"
            }

            if (continuationToken == null) {
                try {
                    // Ekstraksi info utama video (Jumlah tayang & Waktu unggah sebenarnya dari YouTube)
                    val primaryList = mutableListOf<JSONObject>()
                    findObjectsWithKey(responseJson, "videoPrimaryInfoRenderer", primaryList)
                    val primary = primaryList.firstOrNull()
                    if (primary != null) {
                        val viewRenderer = primary.optJSONObject("viewCount")?.optJSONObject("videoViewCountRenderer")
                        val shortViews = getTextFromRunsOrSimple(viewRenderer?.optJSONObject("shortViewCount"))
                        val fullViews = getTextFromRunsOrSimple(viewRenderer?.optJSONObject("viewCount"))
                        val bestViews = shortViews.ifBlank { fullViews }.trim()
                        if (bestViews.isNotBlank()) {
                            viewCountText = bestViews
                            viewCountNumber = parseViewCountText(bestViews)
                        }

                        val relDate = getTextFromRunsOrSimple(primary.optJSONObject("relativeDateText")).trim()
                        val absDate = getTextFromRunsOrSimple(primary.optJSONObject("dateText")).trim()
                        val bestDate = relDate.ifBlank { absDate }
                        if (bestDate.isNotBlank()) {
                            uploadDateText = bestDate
                        }
                    }

                    if (viewCountText == null) {
                        val viewRenderers = mutableListOf<JSONObject>()
                        findObjectsWithKey(responseJson, "videoViewCountRenderer", viewRenderers)
                        val vr = viewRenderers.firstOrNull()
                        if (vr != null) {
                            val shortViews = getTextFromRunsOrSimple(vr.optJSONObject("shortViewCount"))
                            val fullViews = getTextFromRunsOrSimple(vr.optJSONObject("viewCount"))
                            val bestViews = shortViews.ifBlank { fullViews }.trim()
                            if (bestViews.isNotBlank()) {
                                viewCountText = bestViews
                                viewCountNumber = parseViewCountText(bestViews)
                            }
                        }
                    }

                    val secList = mutableListOf<JSONObject>()
                    findObjectsWithKey(responseJson, "videoSecondaryInfoRenderer", secList)
                    val sec = secList.firstOrNull()
                    if (sec != null) {
                        val owner = sec.optJSONObject("owner")?.optJSONObject("videoOwnerRenderer")
                        val st = getTextFromRunsOrSimple(owner?.optJSONObject("subscriberCountText")).trim()
                        if (st.isNotBlank()) subText = st
                        val ownerThumbs = owner?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        if (ownerThumbs != null && ownerThumbs.length() > 0) {
                            channelAvatarUrl = ownerThumbs.optJSONObject(ownerThumbs.length() - 1)?.optString("url")
                                ?: ownerThumbs.optJSONObject(0)?.optString("url")
                        }
                        val subBtn = sec.optJSONObject("subscribeButton")?.optJSONObject("subscribeButtonRenderer")
                        if (subBtn != null) {
                            val onSub = subBtn.optJSONArray("onSubscribeEndpoints")?.optJSONObject(0)?.optJSONObject("subscribeEndpoint")
                            val subChannelIds = onSub?.optJSONArray("channelIds")
                            if (subChannelIds != null && subChannelIds.length() > 0) {
                                channelId = subChannelIds.optString(0).ifEmpty { null }
                            }
                            if (channelId.isNullOrEmpty()) {
                                channelId = subBtn.optString("channelId").ifEmpty { null }
                            }
                            if (subBtn.has("subscribed")) {
                                isSubscribed = subBtn.optBoolean("subscribed")
                            }
                        }
                        if (channelId.isNullOrEmpty()) {
                            channelId = owner?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")?.ifEmpty { null }
                        }
                    }

                    // Fallback channelAvatar jika belum ditemukan
                    if (channelAvatarUrl.isNullOrEmpty()) {
                        val ownerList = mutableListOf<JSONObject>()
                        findObjectsWithKey(responseJson, "videoOwnerRenderer", ownerList)
                        val foundOwner = ownerList.firstOrNull()
                        val thumbs = foundOwner?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        if (thumbs != null && thumbs.length() > 0) {
                            channelAvatarUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                                ?: thumbs.optJSONObject(0)?.optString("url")
                        }
                    }

                    // Ekstraksi Pratinjau Komentar Teratas (Comment Teaser) dari InnerTube
                    val commentHeaders = mutableListOf<JSONObject>()
                    findObjectsWithKey(responseJson, "commentsEntryPointHeaderRenderer", commentHeaders)
                    val commentHeader = commentHeaders.firstOrNull()
                    if (commentHeader != null) {
                        val teaserObj = commentHeader.optJSONObject("teaserContentText")
                        val teaser = getTextFromRunsOrSimple(teaserObj).trim()
                        if (teaser.isNotBlank()) commentTeaserText = teaser

                        val countObj = commentHeader.optJSONObject("commentCount") ?: commentHeader.optJSONObject("headerText")
                        val count = getTextFromRunsOrSimple(countObj).trim()
                        if (count.isNotBlank()) commentCountText = count

                        val avatarThumbs = commentHeader.optJSONObject("teaserAvatar")?.optJSONArray("thumbnails")
                        if (avatarThumbs != null && avatarThumbs.length() > 0) {
                            commentTeaserAvatar = avatarThumbs.optJSONObject(avatarThumbs.length() - 1)?.optString("url")
                                ?: avatarThumbs.optJSONObject(0)?.optString("url")
                        }
                    }

                    // Fallback channelId jika belum ditemukan: cari browseEndpoint yang diawali "UC"
                    if (channelId.isNullOrEmpty()) {
                        val browseEndpoints = mutableListOf<JSONObject>()
                        findObjectsWithKey(responseJson, "browseEndpoint", browseEndpoints)
                        channelId = browseEndpoints.firstOrNull { it.optString("browseId").startsWith("UC") }?.optString("browseId")
                    }

                    // Cek status subscribe jika belum didapat: periksa dari daftar langganan akun
                    if (isSubscribed != true && !channelId.isNullOrEmpty()) {
                        isSubscribed = _subscribedChannels.value.any { it.id == channelId } || isSubscribedLocally(channelId)
                    }

                    // Cek status like dari likeStatusEntity di seluruh struktur respon
                    if (likeStatus == null) {
                        val likeEntities = mutableListOf<JSONObject>()
                        findObjectsWithKey(responseJson, "likeStatusEntity", likeEntities)
                        for (le in likeEntities) {
                            val ls = le.optString("likeStatus")
                            if (ls.isNotBlank() && ls != "INDIFFERENT") {
                                likeStatus = ls
                                if (ls == "LIKE") {
                                    synchronized(userLikedVideoIds) {
                                        userLikedVideoIds.add(videoId)
                                        userDislikedVideoIds.remove(videoId)
                                    }
                                    saveLikesToPrefs()
                                } else if (ls == "DISLIKE") {
                                    synchronized(userLikedVideoIds) {
                                        userDislikedVideoIds.add(videoId)
                                        userLikedVideoIds.remove(videoId)
                                    }
                                    saveLikesToPrefs()
                                }
                                break
                            }
                        }
                    }

                    // Cek status like dari segmentedLikeDislikeButtonViewModel
                    if (likeStatus == null) {
                        val likeButtons = mutableListOf<JSONObject>()
                        findObjectsWithKey(responseJson, "segmentedLikeDislikeButtonViewModel", likeButtons)
                        for (btn in likeButtons) {
                            val status = btn.optJSONObject("likeButtonViewModel")
                                ?.optJSONObject("likeButtonViewModel")
                                ?.optJSONObject("likeStatusEntity")
                                ?.optString("likeStatus")
                            if (!status.isNullOrBlank() && status != "INDIFFERENT") {
                                likeStatus = status
                                if (status == "LIKE") {
                                    synchronized(userLikedVideoIds) {
                                        userLikedVideoIds.add(videoId)
                                        userDislikedVideoIds.remove(videoId)
                                    }
                                    saveLikesToPrefs()
                                }
                                break
                            }
                        }
                    }

                    // Pertahankan status Like/Dislike lokal jika YouTube mengembalikan INDIFFERENT atau null karena jeda propagasi server
                    if (likeStatus == null || likeStatus == "INDIFFERENT") {
                        if (isLikedLocally(videoId)) {
                            likeStatus = "LIKE"
                        } else if (isDislikedLocally(videoId)) {
                            likeStatus = "DISLIKE"
                        }
                    }
                } catch (_: Exception) {}
            }

            val distinctVideos = items.filter { isValidVideoId(it.id) && it.id != videoId }.distinctBy { it.id }
            Result.success(
                WatchNextData(
                    relatedVideos = distinctVideos,
                    nextContinuation = nextContinuation,
                    subscriberText = subText,
                    isSubscribed = isSubscribed,
                    channelId = channelId,
                    likeStatus = likeStatus,
                    channelAvatarUrl = channelAvatarUrl,
                    commentTeaserText = commentTeaserText,
                    commentTeaserAuthor = commentTeaserAuthor,
                    commentTeaserAvatar = commentTeaserAvatar,
                    commentCountText = commentCountText,
                    viewCountText = viewCountText,
                    uploadDateText = uploadDateText,
                    viewCountNumber = viewCountNumber,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Private Parsing & Network Helpers ---

    /**
     * Menyelesaikan handle saluran YouTube (misal @NamaChannel atau custom URL)
     * menjadi Channel ID kanonik resmi YouTube (awalan "UC...").
     */
    private fun resolveChannelId(handleOrName: String, cookies: String): String? {
        val trimmed = handleOrName.trim().removePrefix("https://www.youtube.com/").removePrefix("youtube.com/").removePrefix("/")
        if (trimmed.startsWith("UC") && trimmed.length == 24) {
            return trimmed
        }
        val targetUrl = if (trimmed.startsWith("@")) {
            "https://www.youtube.com/$trimmed"
        } else if (trimmed.startsWith("channel/")) {
            "https://www.youtube.com/$trimmed"
        } else {
            "https://www.youtube.com/@$trimmed"
        }

        try {
            val payload = createInnertubeContext().apply {
                put("url", targetUrl)
            }
            val res = postInnertube("navigation/resolve_url", payload, cookies)
            val browseId = res.optJSONObject("endpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
            if (!browseId.isNullOrBlank() && browseId.startsWith("UC")) {
                return browseId
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Mengekstrak token kelanjutan berikutnya secara khusus untuk daftar video terkait (Watch Next Feed),
     * menghindari tabrakan dengan token kelanjutan komentar (comments section).
     */
    private fun extractWatchNextContinuationToken(responseJson: JSONObject): String? {
        try {
            // Kasus 1: Paginasi lanjutan (onResponseReceivedEndpoints)
            val endpoints = responseJson.optJSONArray("onResponseReceivedEndpoints")
            if (endpoints != null) {
                for (i in 0 until endpoints.length()) {
                    val ep = endpoints.optJSONObject(i) ?: continue
                    val action = ep.optJSONObject("appendContinuationItemsAction") ?: continue
                    val targetId = action.optString("targetId")
                    if (targetId.contains("watch-next", ignoreCase = true) || targetId.isEmpty()) {
                        val items = action.optJSONArray("continuationItems") ?: continue
                        for (j in 0 until items.length()) {
                            val item = items.optJSONObject(j) ?: continue
                            val cir = item.optJSONObject("continuationItemRenderer") ?: continue
                            val token = cir.optJSONObject("continuationEndpoint")
                                ?.optJSONObject("continuationCommand")
                                ?.optString("token")
                                ?: cir.optJSONObject("button")
                                    ?.optJSONObject("buttonRenderer")
                                    ?.optJSONObject("command")
                                    ?.optJSONObject("continuationCommand")
                                    ?.optString("token")
                                ?: cir.optJSONObject("nextContinuationData")?.optString("continuation")
                            if (!token.isNullOrBlank()) return token
                        }
                    }
                }
            }

            // Kasus 2: Respons awal (contents.twoColumnWatchNextResults.secondaryResults)
            val secResults = responseJson.optJSONObject("contents")
                ?.optJSONObject("twoColumnWatchNextResults")
                ?.optJSONObject("secondaryResults")
                ?.optJSONObject("secondaryResults")
                ?.optJSONArray("results")
            if (secResults != null) {
                for (i in 0 until secResults.length()) {
                    val item = secResults.optJSONObject(i) ?: continue
                    val cir = item.optJSONObject("continuationItemRenderer") ?: continue
                    val token = cir.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")
                        ?.optString("token")
                        ?: cir.optJSONObject("button")
                            ?.optJSONObject("buttonRenderer")
                            ?.optJSONObject("command")
                            ?.optJSONObject("continuationCommand")
                            ?.optString("token")
                        ?: cir.optJSONObject("nextContinuationData")?.optString("continuation")
                    if (!token.isNullOrBlank()) return token
                }
            }

            // Kasus 3: Fallback mencari appendContinuationItemsAction di mana pun dalam JSON
            val appendActions = mutableListOf<JSONObject>()
            findObjectsWithKey(responseJson, "appendContinuationItemsAction", appendActions)
            for (action in appendActions) {
                val targetId = action.optString("targetId")
                if (targetId.contains("watch-next", ignoreCase = true) || targetId.isEmpty()) {
                    val items = action.optJSONArray("continuationItems") ?: continue
                    for (j in 0 until items.length()) {
                        val item = items.optJSONObject(j) ?: continue
                        val cir = item.optJSONObject("continuationItemRenderer") ?: continue
                        val token = cir.optJSONObject("continuationEndpoint")
                            ?.optJSONObject("continuationCommand")
                            ?.optString("token")
                            ?: cir.optJSONObject("button")
                                ?.optJSONObject("buttonRenderer")
                                ?.optJSONObject("command")
                                ?.optJSONObject("continuationCommand")
                                ?.optString("token")
                            ?: cir.optJSONObject("nextContinuationData")?.optString("continuation")
                        if (!token.isNullOrBlank()) return token
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun extractNextContinuationToken(json: Any): String? {
        val tokens = mutableListOf<String>()
        fun scan(obj: Any) {
            when (obj) {
                is JSONObject -> {
                    if (obj.has("continuationCommand")) {
                        val cmd = obj.optJSONObject("continuationCommand")
                        val token = cmd?.optString("token").orEmpty()
                        if (token.isNotBlank()) {
                            val req = cmd?.optString("request").orEmpty()
                            if (req == "CONTINUATION_REQUEST_TYPE_WATCH_NEXT") {
                                tokens.add(0, token)
                            } else {
                                tokens.add(token)
                            }
                        }
                    }
                    if (obj.has("nextContinuationData")) {
                        val token = obj.optJSONObject("nextContinuationData")?.optString("continuation").orEmpty()
                        if (token.isNotBlank()) tokens.add(token)
                    }
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        scan(obj.opt(keys.next()) ?: continue)
                    }
                }
                is JSONArray -> {
                    for (i in 0 until obj.length()) {
                        scan(obj.opt(i) ?: continue)
                    }
                }
            }
        }
        scan(json)
        return tokens.firstOrNull()
    }

    /**
     * Mengekstrak riwayat tontonan YouTube secara terstruktur & ketat dari respons FEhistory.
     * Hanya membaca isi dari sectionListRenderer di tab utama riwayat (urutan kronologis resmi YouTube:
     * Hari ini -> Kemarin -> Minggu lalu).
     * Secara ketat MENGABAIKAN konten sampingan (secondaryContents, radioRenderer/Mix, dan playlistRenderer).
     */
    private fun extractWatchHistoryStrict(responseJson: JSONObject, outList: MutableList<VideoItem>) {
        try {
            // Format 1: twoColumnBrowseResultsRenderer (Desktop)
            // Format 2: singleColumnBrowseResultsRenderer (Mobile)
            val contents = responseJson.optJSONObject("contents")
            val twoCol = contents?.optJSONObject("twoColumnBrowseResultsRenderer")
            val singleCol = contents?.optJSONObject("singleColumnBrowseResultsRenderer")

            val tabs = (twoCol ?: singleCol)?.optJSONArray("tabs")
            val primaryTab = tabs?.optJSONObject(0)?.optJSONObject("tabRenderer")
            val sectionList = primaryTab?.optJSONObject("content")?.optJSONObject("sectionListRenderer")
                ?: responseJson.optJSONObject("sectionListRenderer")

            val sections = sectionList?.optJSONArray("contents")
            if (sections != null && sections.length() > 0) {
                for (s in 0 until sections.length()) {
                    val sectionObj = sections.optJSONObject(s)
                    val itemSection = sectionObj?.optJSONObject("itemSectionRenderer")
                    val items = itemSection?.optJSONArray("contents") ?: continue
                    for (i in 0 until items.length()) {
                        val itemObj = items.optJSONObject(i) ?: continue

                        // Hanya ekstrak video sah, JANGAN ambil mix (radioRenderer) atau playlist
                        when {
                            itemObj.has("videoRenderer") -> {
                                parseVideoRenderer(itemObj.getJSONObject("videoRenderer"))?.let { outList.add(it) }
                            }
                            itemObj.has("compactVideoRenderer") -> {
                                parseVideoRenderer(itemObj.getJSONObject("compactVideoRenderer"))?.let { outList.add(it) }
                            }
                            itemObj.has("historyVideoRenderer") -> {
                                parseVideoRenderer(itemObj.getJSONObject("historyVideoRenderer"))?.let { outList.add(it) }
                            }
                            itemObj.has("lockupViewModel") -> {
                                val lvm = itemObj.getJSONObject("lockupViewModel")
                                val cType = lvm.optString("contentType")
                                val cId = lvm.optString("contentId")
                                // Pastikan ini video tunggal, bukan playlist / channel / mix
                                if (!cType.contains("PLAYLIST", ignoreCase = true) &&
                                    !cType.contains("CHANNEL", ignoreCase = true) &&
                                    !cId.startsWith("PL") && !cId.startsWith("RD") && !cId.startsWith("UC")
                                ) {
                                    parseLockupViewModel(lvm)?.let {
                                        if (!it.isMix && it.playlistId == null) {
                                            outList.add(it)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Fallback jika YouTube mengubah arsitektur layout dan outList masih kosong
            if (outList.isEmpty()) {
                val fallbackItems = mutableListOf<VideoItem>()
                extractFeedItems(responseJson, fallbackItems)
                // Filter ketat dari fallback: jangan masukkan Mixes atau Playlists ke dalam History
                outList.addAll(fallbackItems.filter { !it.isMix && it.playlistId == null && isValidVideoId(it.id) })
            }
        } catch (_: Exception) {
            val fallbackItems = mutableListOf<VideoItem>()
            extractFeedItems(responseJson, fallbackItems)
            outList.addAll(fallbackItems.filter { !it.isMix && it.playlistId == null && isValidVideoId(it.id) })
        }
    }

    private fun extractFeedItems(json: Any, outList: MutableList<VideoItem>) {
        when (json) {
            is JSONObject -> {
                // Lewati secara mutlak segala bentuk iklan & promosi bersponsor dari feed YouTube
                if (json.has("adSlotRenderer") ||
                    json.has("inFeedAdLayoutRenderer") ||
                    json.has("promotedSparklesWebRenderer") ||
                    json.has("promotedVideoRenderer") ||
                    json.has("statementBannerRenderer") ||
                    json.has("brandVideoSingletonRenderer")
                ) {
                    return
                }

                if (json.has("videoRenderer")) {
                    parseVideoRenderer(json.optJSONObject("videoRenderer") ?: JSONObject())?.let { outList.add(it) }
                } else if (json.has("compactVideoRenderer")) {
                    parseVideoRenderer(json.optJSONObject("compactVideoRenderer") ?: JSONObject())?.let { outList.add(it) }
                } else if (json.has("gridVideoRenderer")) {
                    parseVideoRenderer(json.optJSONObject("gridVideoRenderer") ?: JSONObject())?.let { outList.add(it) }
                } else if (json.has("historyVideoRenderer")) {
                    parseVideoRenderer(json.optJSONObject("historyVideoRenderer") ?: JSONObject())?.let { outList.add(it) }
                } else if (json.has("playlistPanelVideoRenderer")) {
                    parsePlaylistPanelVideoRenderer(json.optJSONObject("playlistPanelVideoRenderer") ?: JSONObject())?.let { outList.add(it) }
                } else if (json.has("playlistVideoRenderer")) {
                    parsePlaylistVideoRenderer(json.optJSONObject("playlistVideoRenderer") ?: JSONObject())?.let { outList.add(it) }
                } else if (json.has("radioRenderer")) {
                    parseRadioRenderer(json.optJSONObject("radioRenderer") ?: JSONObject())?.let { outList.add(it) }
                } else if (json.has("compactRadioRenderer")) {
                    parseRadioRenderer(json.optJSONObject("compactRadioRenderer") ?: JSONObject())?.let { outList.add(it) }
                } else if (json.has("playlistRenderer")) {
                    parseRadioRenderer(json.optJSONObject("playlistRenderer") ?: JSONObject())?.let { outList.add(it) }
                } else if (json.has("lockupViewModel")) {
                    parseLockupViewModel(json.optJSONObject("lockupViewModel") ?: JSONObject())?.let { outList.add(it) }
                } else {
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        extractFeedItems(json.opt(key) ?: continue, outList)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until json.length()) {
                    extractFeedItems(json.opt(i) ?: continue, outList)
                }
            }
        }
    }

    private fun isValidVideoId(id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        return id.length == 11 && !id.startsWith("RD") && !id.startsWith("PL") && !id.startsWith("VL") && !id.startsWith("UC")
    }

    private fun parseRadioRenderer(renderer: JSONObject): VideoItem? {
        val playlistId = renderer.optString("playlistId")
        val title = getTextFromRunsOrSimple(renderer.optJSONObject("title"))
        val thumbnail = getHighestResThumbnail(renderer.optJSONObject("thumbnail"))
            .ifEmpty { getHighestResThumbnail(renderer.optJSONObject("thumbnailRenderer")) }

        val countText = getTextFromRunsOrSimple(renderer.optJSONObject("videoCountText"))
            .ifEmpty { getTextFromRunsOrSimple(renderer.optJSONObject("thumbnailText")) }
            .ifEmpty { "Mix" }

        val nav = renderer.optJSONObject("navigationEndpoint")
        val watch = nav?.optJSONObject("watchEndpoint")
        var videoId = watch?.optString("videoId").orEmpty()

        if (!isValidVideoId(videoId) && thumbnail.isNotBlank()) {
            val thumbMatch = Regex("""/vi(?:_webp)?/([a-zA-Z0-9_-]{11})/""").find(thumbnail)
            if (thumbMatch != null) {
                videoId = thumbMatch.groupValues[1]
            }
        }

        val targetId = if (isValidVideoId(videoId)) videoId else if (videoId.isNotBlank()) videoId else playlistId
        if (targetId.isBlank()) return null

        val uploader = getTextFromRunsOrSimple(renderer.optJSONObject("shortBylineText"))
            .ifEmpty { "YouTube Mix" }

        val channelThumbObj = renderer.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("thumbnail")
            ?: renderer.optJSONObject("channelThumbnail")
        val uploaderAvatarUrl = getHighestResThumbnail(channelThumbObj).ifEmpty { null }

        return VideoItem(
            id = targetId,
            url = if (isValidVideoId(targetId) && playlistId.isNotBlank()) {
                "https://www.youtube.com/watch?v=$targetId&list=$playlistId"
            } else if (playlistId.isNotBlank()) {
                "https://www.youtube.com/playlist?list=$playlistId"
            } else {
                "https://www.youtube.com/watch?v=$targetId"
            },
            title = title.ifEmpty { "Mix Video" },
            uploaderName = uploader,
            uploaderAvatarUrl = uploaderAvatarUrl,
            thumbnailUrl = thumbnail.ifEmpty { null },
            uploadDateText = countText,
            isMix = true,
            playlistId = playlistId.ifEmpty { null },
        )
    }

    private fun parseLockupViewModel(model: JSONObject): VideoItem? {
        val contentId = model.optString("contentId")
        val contentType = model.optString("contentType")

        // Cek apakah ini channel atau slot iklan / promosi YouTube
        if (contentType.contains("CHANNEL", ignoreCase = true) ||
            contentType.contains("AD", ignoreCase = true) ||
            contentType.contains("PROMO", ignoreCase = true) ||
            contentId.startsWith("UC")
        ) {
            return null
        }

        // Cari watchEndpoint di commandContext onTap
        val cmd = model.optJSONObject("rendererContext")
            ?.optJSONObject("commandContext")
            ?.optJSONObject("onTap")
            ?.optJSONObject("innertubeCommand")
            ?: model.optJSONObject("onTap")?.optJSONObject("innertubeCommand")

        val watchEndpoint = cmd?.optJSONObject("watchEndpoint")
        val watchVideoId = watchEndpoint?.optString("videoId").orEmpty()
        val watchPlaylistId = watchEndpoint?.optString("playlistId").orEmpty()

        // Ekstrak Thumbnail
        var thumbUrl: String? = null
        val contentImage = model.optJSONObject("contentImage")
        if (contentImage != null) {
            val thumbVm = contentImage.optJSONObject("thumbnailViewModel")
                ?: contentImage.optJSONObject("collectionThumbnailViewModel")
                    ?.optJSONObject("primaryThumbnail")
                    ?.optJSONObject("thumbnailViewModel")
            val sources = thumbVm?.optJSONObject("image")?.optJSONArray("sources")
            if (sources != null && sources.length() > 0) {
                thumbUrl = sources.optJSONObject(sources.length() - 1)?.optString("url")
                    ?: sources.optJSONObject(0)?.optString("url")
            }
        }

        // Tentukan video ID yang valid (11 karakter asli YouTube)
        var realVideoId = ""
        if (isValidVideoId(watchVideoId)) {
            realVideoId = watchVideoId
        } else if (isValidVideoId(contentId)) {
            realVideoId = contentId
        } else if (!thumbUrl.isNullOrBlank()) {
            val match = Regex("""/vi(?:_webp)?/([a-zA-Z0-9_-]{11})/""").find(thumbUrl)
            if (match != null) {
                realVideoId = match.groupValues[1]
            }
        }

        if (realVideoId.isBlank()) {
            if (!contentId.startsWith("RD") && !contentId.startsWith("PL") && !contentId.startsWith("VL") && contentId.isNotBlank()) {
                realVideoId = contentId
            } else if (watchVideoId.isNotBlank()) {
                realVideoId = watchVideoId
            }
        }

        val isMixOrPlaylist = contentType.contains("PLAYLIST", ignoreCase = true)
            || contentId.startsWith("RD")
            || (!isValidVideoId(realVideoId) && (contentId.startsWith("PL") || watchPlaylistId.isNotBlank()))

        // Jika bukan video valid 11 digit YouTube dan bukan mix/playlist, buang (ini iklan/promosi)
        if (!isValidVideoId(realVideoId) && !isMixOrPlaylist) return null

        val playlistId = when {
            watchPlaylistId.isNotBlank() -> watchPlaylistId
            contentId.startsWith("RD") || contentId.startsWith("PL") -> contentId
            else -> null
        }

        if (thumbUrl.isNullOrBlank()) {
            val thumbId = realVideoId.ifEmpty { contentId }
            thumbUrl = "https://i.ytimg.com/vi/$thumbId/hqdefault.jpg"
        }

        // Ekstrak Durasi Overlay, Status LIVE & Persentase Tontonan
        var durationText = ""
        var isLive = false
        var percentWatched = 0
        val overlays = model.optJSONObject("contentImage")
            ?.optJSONObject("thumbnailViewModel")
            ?.optJSONArray("overlays")
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val overlay = overlays.optJSONObject(i) ?: continue

                // Cek resume playback (progress bar)
                val resumeOverlay = overlay.optJSONObject("thumbnailOverlayResumePlaybackRenderer")
                if (resumeOverlay != null) {
                    percentWatched = resumeOverlay.optInt("percentDurationWatched", percentWatched)
                }
                val progressBar = overlay.optJSONObject("thumbnailBottomOverlayViewModel")
                    ?.optJSONObject("progressBar")
                    ?.optJSONObject("thumbnailOverlayProgressBarViewModel")
                if (progressBar != null) {
                    percentWatched = progressBar.optInt("percentDurationWatched", percentWatched)
                }

                // Cek thumbnailBottomOverlayViewModel badges (format standar InnerTube lockupViewModel terbaru)
                val bottomOverlay = overlay.optJSONObject("thumbnailBottomOverlayViewModel")
                val badges = bottomOverlay?.optJSONArray("badges")
                if (badges != null) {
                    for (j in 0 until badges.length()) {
                        val badgeObj = badges.optJSONObject(j)?.optJSONObject("thumbnailBadgeViewModel") ?: continue
                        val text = badgeObj.optString("text")
                        val style = badgeObj.optString("badgeStyle")
                        val a11y = badgeObj.optJSONObject("rendererContext")
                            ?.optJSONObject("accessibilityContext")
                            ?.optString("label").orEmpty()

                        if (style.contains("LIVE", ignoreCase = true) ||
                            text.equals("LIVE", ignoreCase = true) ||
                            text.equals("LANGSUNG", ignoreCase = true) ||
                            a11y.contains("live", ignoreCase = true) ||
                            a11y.contains("langsung", ignoreCase = true)
                        ) {
                            isLive = true
                            durationText = "LIVE"
                        } else if (text.isNotBlank() && durationText.isBlank()) {
                            durationText = text
                        } else if (a11y.isNotBlank() && durationText.isBlank()) {
                            durationText = a11y
                        }
                    }
                }

                // Cek legacy thumbnailOverlayTimeStatusRenderer
                val timeStatus = overlay.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                if (timeStatus != null) {
                    val style = timeStatus.optString("style")
                    val t = getTextFromRunsOrSimple(timeStatus.optJSONObject("text"))
                    if (style.contains("LIVE", ignoreCase = true) ||
                        t.equals("LIVE", ignoreCase = true) ||
                        t.equals("LANGSUNG", ignoreCase = true)
                    ) {
                        isLive = true
                        durationText = "LIVE"
                    } else if (t.isNotBlank() && durationText.isBlank()) {
                        durationText = t
                    }
                }

                // Cek thumbnailOverlayBadgeViewModel
                val badge = overlay.optJSONObject("thumbnailOverlayBadgeViewModel")
                if (badge != null) {
                    val b = badge.optString("badgeText")
                    if (b.contains("LIVE", ignoreCase = true) || b.contains("LANGSUNG", ignoreCase = true)) {
                        isLive = true
                        durationText = "LIVE"
                    } else if (b.isNotBlank() && durationText.isBlank()) {
                        durationText = b
                    }
                }
            }
        }

        // Fallback pencarian badge waktu / live di seluruh model jika belum terdeteksi
        if (durationText.isBlank() && !isLive) {
            val badgeList = mutableListOf<JSONObject>()
            findObjectsWithKey(model, "thumbnailBadgeViewModel", badgeList)
            for (badgeObj in badgeList) {
                val text = badgeObj.optString("text")
                val style = badgeObj.optString("badgeStyle")
                val a11y = badgeObj.optJSONObject("rendererContext")
                    ?.optJSONObject("accessibilityContext")
                    ?.optString("label").orEmpty()
                if (style.contains("LIVE", ignoreCase = true) ||
                    text.equals("LIVE", ignoreCase = true) ||
                    text.equals("LANGSUNG", ignoreCase = true) ||
                    a11y.contains("live", ignoreCase = true)
                ) {
                    isLive = true
                    durationText = "LIVE"
                    break
                }
                if (text.isNotBlank()) {
                    durationText = text
                    break
                } else if (a11y.isNotBlank()) {
                    durationText = a11y
                    break
                }
            }
        }

        if (!isLive) {
            val a11yModel = model.optJSONObject("rendererContext")?.optJSONObject("accessibilityContext")?.optString("label").orEmpty()
            if (a11yModel.contains("live streaming", ignoreCase = true) || a11yModel.contains("siaran langsung", ignoreCase = true)) {
                isLive = true
                durationText = "LIVE"
            }
        }

        // 2. Ekstrak Judul
        val metaVm = model.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        var title = metaVm?.optJSONObject("title")?.optString("content").orEmpty()
        if (title.isBlank()) {
            title = getTextFromRunsOrSimple(metaVm?.optJSONObject("title"))
        }
        if (title.isBlank()) {
            val a11y = model.optJSONObject("rendererContext")?.optJSONObject("accessibilityContext")?.optString("label").orEmpty()
            title = a11y.substringBefore(" ").ifEmpty { "Video YouTube" }
        }

        // 3. Ekstrak Uploader, View Count, Upload Date
        var uploader = ""
        var viewsText = ""
        var uploadDate = ""

        val rows = metaVm?.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")

        if (rows != null) {
            if (rows.length() > 0) {
                val parts0 = rows.optJSONObject(0)?.optJSONArray("metadataParts")
                if (parts0 != null && parts0.length() > 0) {
                    uploader = parts0.optJSONObject(0)?.optJSONObject("text")?.optString("content").orEmpty()
                }
            }
            if (rows.length() > 1) {
                val parts1 = rows.optJSONObject(1)?.optJSONArray("metadataParts")
                if (parts1 != null) {
                    if (parts1.length() > 0) {
                        viewsText = parts1.optJSONObject(0)?.optJSONObject("text")?.optString("content").orEmpty()
                    }
                    if (parts1.length() > 1) {
                        uploadDate = parts1.optJSONObject(1)?.optJSONObject("text")?.optString("content").orEmpty()
                    }
                }
            }
        }

        // 4. Ekstrak Avatar Saluran (uploaderAvatarUrl) & URL Channel (uploaderUrl)
        var uploaderAvatarUrl: String? = null
        var uploaderUrl: String? = null

        val imgObj = metaVm?.optJSONObject("image")
        if (imgObj != null) {
            val avatarVm = imgObj.optJSONObject("avatarViewModel")
                ?: imgObj.optJSONObject("decoratedAvatarViewModel")?.optJSONObject("avatar")?.optJSONObject("avatarViewModel")
            val sources = avatarVm?.optJSONObject("image")?.optJSONArray("sources")
                ?: imgObj.optJSONObject("thumbnailViewModel")?.optJSONObject("image")?.optJSONArray("sources")
            if (sources != null && sources.length() > 0) {
                val lastSource = sources.optJSONObject(sources.length() - 1)
                val u = lastSource?.optString("url").orEmpty()
                if (u.isNotBlank()) {
                    uploaderAvatarUrl = if (u.startsWith("//")) "https:$u" else u
                }
            }
            if (uploader.isBlank()) {
                val a11y = imgObj.optJSONObject("decoratedAvatarViewModel")?.optString("a11yLabel")
                    ?: avatarVm?.optString("a11yLabel").orEmpty()
                if (a11y.isNotBlank()) {
                    uploader = a11y.removePrefix("Buka channel ").removePrefix("Open channel ").trim()
                }
            }
            val cmd = imgObj.optJSONObject("decoratedAvatarViewModel")
                ?.optJSONObject("rendererContext")
                ?.optJSONObject("commandContext")
                ?.optJSONObject("onTap")
                ?.optJSONObject("innertubeCommand")
                ?: imgObj.optJSONObject("rendererContext")
                ?.optJSONObject("commandContext")
                ?.optJSONObject("onTap")
                ?.optJSONObject("innertubeCommand")
            val browseEp = cmd?.optJSONObject("browseEndpoint")
            val canonicalUrl = browseEp?.optString("canonicalBaseUrl").orEmpty()
            val browseId = browseEp?.optString("browseId").orEmpty()
            if (canonicalUrl.isNotBlank()) {
                uploaderUrl = "https://www.youtube.com$canonicalUrl"
            } else if (browseId.isNotBlank()) {
                uploaderUrl = "https://www.youtube.com/channel/$browseId"
            }
        }

        if (uploaderAvatarUrl.isNullOrBlank()) {
            val avatarList = mutableListOf<JSONObject>()
            findObjectsWithKey(model, "avatarViewModel", avatarList)
            if (avatarList.isNotEmpty()) {
                val src = avatarList.first().optJSONObject("image")?.optJSONArray("sources")
                if (src != null && src.length() > 0) {
                    val u = src.optJSONObject(src.length() - 1)?.optString("url").orEmpty()
                    if (u.isNotBlank()) {
                        uploaderAvatarUrl = if (u.startsWith("//")) "https:$u" else u
                    }
                }
            }
        }

        if (uploader.isBlank()) {
            uploader = metaVm?.optJSONObject("image")?.optJSONObject("avatarViewModel")?.optString("a11yLabel")
                ?.removePrefix("Buka channel ")?.removePrefix("Open channel ").orEmpty()
        }

        val viewCount = parseViewCountText(viewsText)
        val finalId = realVideoId.ifEmpty { contentId }

        val durationSec = if (isLive) 0L else parseDurationStringToSeconds(durationText)

        return VideoItem(
            id = finalId,
            url = if (isMixOrPlaylist && playlistId != null) {
                "https://www.youtube.com/watch?v=$finalId&list=$playlistId"
            } else {
                "https://www.youtube.com/watch?v=$finalId"
            },
            title = title.ifEmpty { "Video YouTube" },
            uploaderName = uploader.ifEmpty { if (isMixOrPlaylist) "YouTube Mix" else "YouTube" },
            uploaderAvatarUrl = uploaderAvatarUrl,
            uploaderUrl = uploaderUrl,
            thumbnailUrl = thumbUrl,
            durationSeconds = durationSec,
            isLive = isLive,
            rawDurationText = if (isLive) "LIVE" else durationText.ifBlank { null },
            viewCount = viewCount,
            uploadDateText = uploadDate.ifBlank { if (isMixOrPlaylist) "Mix" else null },
            isMix = isMixOrPlaylist,
            playlistId = playlistId,
            playbackPercent = if (percentWatched > 0) percentWatched / 100f else 0f,
        )
    }

    private fun createInnertubeContext(): JSONObject {
        val clientObj = JSONObject().apply {
            put("hl", "id")
            put("gl", "ID")
            put("clientName", "WEB")
            put("clientVersion", INNERTUBE_CLIENT_VERSION)
        }
        return JSONObject().put("context", JSONObject().put("client", clientObj))
    }

    private fun postInnertube(endpoint: String, payload: JSONObject, cookies: String): JSONObject {
        val url = "https://www.youtube.com/youtubei/v1/$endpoint?key=$INNERTUBE_KEY&prettyPrint=false"
        val sapisidHash = YouTubeAuthUtils.generateSapisidHash(cookies)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .addHeader("X-Origin", "https://www.youtube.com")
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("Referer", "https://www.youtube.com/")
            .addHeader("X-YouTube-Client-Name", "1")
            .addHeader("X-YouTube-Client-Version", INNERTUBE_CLIENT_VERSION)
            .addHeader("X-Goog-AuthUser", "0")
            .addHeader("Cookie", cookies)

        if (!sapisidHash.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", sapisidHash)
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful && bodyString.isBlank()) {
                throw IllegalStateException("Innertube HTTP ${response.code}")
            }
            return JSONObject(bodyString.ifEmpty { "{}" })
        }
    }

    private fun fetchWebProfileFallback(cookies: String): GoogleAccount {
        try {
            val request = Request.Builder()
                .url("https://www.youtube.com/")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .addHeader("Cookie", cookies)
                .build()

            client.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()
                val avatarMatch = Regex("""https://(?:yt[0-9]|lh[0-9])\.(?:ggpht|googleusercontent)\.com/[^\s"'\\]+""").find(html)
                var resolvedAvatar = avatarMatch?.value?.replace("\\u0026", "&")?.trimEnd('"', '\'', '\\', ')')
                if (resolvedAvatar?.startsWith("//") == true) resolvedAvatar = "https:$resolvedAvatar"
                
                // Cari channel handle yang valid: @ diikuti 3 hingga 30 karakter huruf/angka/garis bawah
                val handleMatch = Regex("""@[a-zA-Z0-9_]{3,30}""").find(html)
                
                // Cari nama tampilan dari json halaman
                val nameMatch = Regex("""\"accountName\":\s*\{\s*\"simpleText\":\s*\"([^\"]+)\"""").find(html)
                    ?: Regex("""\"channelTitle\":\s*\"([^\"]+)\"""").find(html)

                val resolvedHandle = handleMatch?.value
                val resolvedName = nameMatch?.groupValues?.getOrNull(1)
                    ?: resolvedHandle?.removePrefix("@")
                    ?: "Pengguna YouTube"

                return GoogleAccount(
                    email = "Akun YouTube Terhubung",
                    displayName = resolvedName,
                    handle = resolvedHandle,
                    avatarUrl = resolvedAvatar,
                    cookies = cookies,
                    isLoggedIn = true,
                )
            }
        } catch (_: Exception) {
            return GoogleAccount(cookies = cookies, isLoggedIn = true)
        }
    }

    private fun parseVideoRenderer(renderer: JSONObject): VideoItem? {
        val videoId = renderer.optString("videoId")
        if (videoId.isBlank()) return null

        val title = getTextFromRunsOrSimple(renderer.optJSONObject("title"))
        val uploader = getTextFromRunsOrSimple(renderer.optJSONObject("ownerText"))
            .ifEmpty { getTextFromRunsOrSimple(renderer.optJSONObject("shortBylineText")) }
        val thumbnail = getHighestResThumbnail(renderer.optJSONObject("thumbnail"))
        val viewsText = getTextFromRunsOrSimple(renderer.optJSONObject("viewCountText"))
        val viewCount = parseViewCountText(viewsText)
        val uploadDate = getTextFromRunsOrSimple(renderer.optJSONObject("publishedTimeText"))

        // Ekstrak status LIVE, durasi, dan persentase tontonan (resume playback) dari YouTube
        var durationSec = 0L
        var percentWatched = 0
        var isLive = false
        var rawDurationText: String? = null

        val badges = renderer.optJSONArray("badges")
        if (badges != null) {
            for (i in 0 until badges.length()) {
                val b = badges.optJSONObject(i)?.optJSONObject("metadataBadgeRenderer")
                val label = b?.optString("label").orEmpty()
                val style = b?.optString("style").orEmpty()
                if (label.equals("LIVE", ignoreCase = true) || label.equals("LANGSUNG", ignoreCase = true) || style.contains("LIVE", ignoreCase = true)) {
                    isLive = true
                    rawDurationText = "LIVE"
                    break
                }
            }
        }

        val overlays = renderer.optJSONArray("thumbnailOverlays")
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val overlay = overlays.optJSONObject(i)
                val resumeOverlay = overlay?.optJSONObject("thumbnailOverlayResumePlaybackRenderer")
                if (resumeOverlay != null) {
                    percentWatched = resumeOverlay.optInt("percentDurationWatched", 0)
                }
                val timeStatus = overlay?.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                if (timeStatus != null) {
                    val style = timeStatus.optString("style")
                    val t = getTextFromRunsOrSimple(timeStatus.optJSONObject("text"))
                    if (style.contains("LIVE", ignoreCase = true) || t.equals("LIVE", ignoreCase = true) || t.equals("LANGSUNG", ignoreCase = true)) {
                        isLive = true
                        rawDurationText = "LIVE"
                    } else if (t.isNotBlank() && !isLive) {
                        durationSec = parseDurationStringToSeconds(t)
                        rawDurationText = t
                    }
                }
            }
        }
        if (durationSec <= 0L && !isLive) {
            val lengthText = getTextFromRunsOrSimple(renderer.optJSONObject("lengthText"))
            if (lengthText.isNotBlank()) {
                durationSec = parseDurationStringToSeconds(lengthText)
                rawDurationText = lengthText
            }
        }

        // Ekstrak Avatar Channel
        val channelThumbObj = renderer.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("thumbnail")
            ?: renderer.optJSONObject("channelThumbnail")
        var uploaderAvatarUrl = getHighestResThumbnail(channelThumbObj).ifEmpty { null }

        if (uploaderAvatarUrl == null) {
            val thumbList = mutableListOf<JSONObject>()
            findObjectsWithKey(renderer, "channelThumbnailWithLinkRenderer", thumbList)
            if (thumbList.isNotEmpty()) {
                val t = thumbList.first().optJSONObject("thumbnail")
                val u = getHighestResThumbnail(t)
                if (u.isNotBlank()) uploaderAvatarUrl = u
            }
        }

        // Ekstrak URL Channel
        val ownerNav = renderer.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
            ?: renderer.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
        val browseEndpoint = ownerNav?.optJSONObject("browseEndpoint")
        val channelCanonical = browseEndpoint?.optString("canonicalBaseUrl").orEmpty()
        val channelBrowseId = browseEndpoint?.optString("browseId").orEmpty()
        val uploaderUrl = when {
            channelCanonical.isNotBlank() -> "https://www.youtube.com$channelCanonical"
            channelBrowseId.isNotBlank() -> "https://www.youtube.com/channel/$channelBrowseId"
            else -> null
        }

        return VideoItem(
            id = videoId,
            url = "https://www.youtube.com/watch?v=$videoId",
            title = title.ifEmpty { "Video YouTube" },
            uploaderName = uploader.ifEmpty { "YouTube" },
            uploaderAvatarUrl = uploaderAvatarUrl,
            uploaderUrl = uploaderUrl,
            thumbnailUrl = thumbnail.ifEmpty { null },
            durationSeconds = if (isLive) 0L else durationSec,
            isLive = isLive,
            rawDurationText = if (isLive) "LIVE" else rawDurationText,
            viewCount = viewCount,
            uploadDateText = uploadDate.ifEmpty { null },
            playbackPercent = if (percentWatched > 0) percentWatched / 100f else 0f,
        )
    }

    private fun parsePlaylistVideoRenderer(renderer: JSONObject): VideoItem? {
        val videoId = renderer.optString("videoId")
        if (!isValidVideoId(videoId)) return null

        val title = getTextFromRunsOrSimple(renderer.optJSONObject("title"))
        val uploader = getTextFromRunsOrSimple(renderer.optJSONObject("shortBylineText"))
        val thumbnail = getHighestResThumbnail(renderer.optJSONObject("thumbnail"))
            .ifEmpty { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }
        val lengthText = getTextFromRunsOrSimple(renderer.optJSONObject("lengthText"))
        val lengthSeconds = renderer.optString("lengthSeconds").toLongOrNull() ?: 0L
        val durSec = if (lengthSeconds > 0) lengthSeconds else parseDurationStringToSeconds(lengthText)

        val channelThumbObj = renderer.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("thumbnail")
            ?: renderer.optJSONObject("channelThumbnail")
        val uploaderAvatarUrl = getHighestResThumbnail(channelThumbObj).ifEmpty { null }

        return VideoItem(
            id = videoId,
            url = "https://www.youtube.com/watch?v=$videoId",
            title = title.ifEmpty { "Video YouTube" },
            uploaderName = uploader.ifEmpty { "YouTube" },
            uploaderAvatarUrl = uploaderAvatarUrl,
            thumbnailUrl = thumbnail,
            durationSeconds = durSec,
            rawDurationText = lengthText.ifBlank { null },
            uploadDateText = null,
        )
    }

    private fun parsePlaylistPanelVideoRenderer(renderer: JSONObject): VideoItem? {
        val videoId = renderer.optString("videoId")
        if (!isValidVideoId(videoId)) return null

        val title = getTextFromRunsOrSimple(renderer.optJSONObject("title"))
        val uploader = getTextFromRunsOrSimple(renderer.optJSONObject("shortBylineText"))
            .ifEmpty { getTextFromRunsOrSimple(renderer.optJSONObject("longBylineText")) }
        val thumbnail = getHighestResThumbnail(renderer.optJSONObject("thumbnail"))
            .ifEmpty { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }
        val lengthText = getTextFromRunsOrSimple(renderer.optJSONObject("lengthText"))
        val durationSec = parseDurationStringToSeconds(lengthText)

        return VideoItem(
            id = videoId,
            url = "https://www.youtube.com/watch?v=$videoId",
            title = title.ifEmpty { "Video YouTube" },
            uploaderName = uploader.ifEmpty { "YouTube" },
            thumbnailUrl = thumbnail,
            durationSeconds = durationSec,
            rawDurationText = lengthText.ifBlank { null }
        )
    }

    private fun parseDurationStringToSeconds(text: String): Long {
        if (text.isBlank() || text.equals("LIVE", ignoreCase = true) || text.equals("LANGSUNG", ignoreCase = true)) return 0L
        val clean = text.trim()

        // Deteksi pemisah format jam:menit:detik baik dengan titik maupun titik dua (contoh: 3:24, 1:15:02, 3.24, 1.15.02)
        val delimiter = if (clean.contains(":")) ":" else if (clean.contains(".") && !clean.contains(" ")) "." else null
        if (delimiter != null) {
            val parts = clean.split(delimiter).mapNotNull { it.trim().toLongOrNull() }
            if (parts.isNotEmpty()) {
                return when (parts.size) {
                    1 -> parts[0]
                    2 -> parts[0] * 60 + parts[1]
                    3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                    else -> 0L
                }
            }
        }

        // Deteksi format tekstual verbal misal "3 menit, 24 detik" atau "1 jam, 15 menit"
        var totalSeconds = 0L
        val hourMatch = Regex("""(\d+)\s*(?:jam|hour|hours|hr|hrs)""", RegexOption.IGNORE_CASE).find(clean)
        val minuteMatch = Regex("""(\d+)\s*(?:menit|minute|minutes|min|mins)""", RegexOption.IGNORE_CASE).find(clean)
        val secondMatch = Regex("""(\d+)\s*(?:detik|second|seconds|sec|secs)""", RegexOption.IGNORE_CASE).find(clean)

        if (hourMatch != null || minuteMatch != null || secondMatch != null) {
            hourMatch?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { totalSeconds += it * 3600 }
            minuteMatch?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { totalSeconds += it * 60 }
            secondMatch?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { totalSeconds += it }
            return totalSeconds
        }

        return 0L
    }

    fun parseViewCountText(text: String): Long {
        val clean = text.trim().lowercase()
        if (clean.isEmpty()) return 0L
        try {
            val numPart = clean
                .replace("x ditonton", "")
                .replace("ditonton", "")
                .replace("views", "")
                .replace("view", "")
                .trim()

            if (numPart.contains("miliar") || numPart.endsWith("b")) {
                val numStr = numPart.replace("miliar", "").replace("b", "").trim().replace(',', '.')
                val mult = numStr.toDoubleOrNull() ?: return 0L
                return (mult * 1_000_000_000).toLong()
            }
            if (numPart.contains("jt") || numPart.endsWith("m")) {
                val numStr = numPart.replace("jt", "").replace("m", "").trim().replace(',', '.')
                val mult = numStr.toDoubleOrNull() ?: return 0L
                return (mult * 1_000_000).toLong()
            }
            if (numPart.contains("rb") || numPart.endsWith("k")) {
                val numStr = numPart.replace("rb", "").replace("k", "").trim().replace(',', '.')
                val mult = numStr.toDoubleOrNull() ?: return 0L
                return (mult * 1_000).toLong()
            }
            val digitsOnly = numPart.filter { it.isDigit() }
            return digitsOnly.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            return 0L
        }
    }

    private fun getTextFromRunsOrSimple(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple

        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i)
            sb.append(run?.optString("text").orEmpty())
        }
        return sb.toString().trim()
    }

    private fun getHighestResThumbnail(thumbObj: JSONObject?): String {
        if (thumbObj == null) return ""
        val thumbnails = thumbObj.optJSONArray("thumbnails")
            ?: thumbObj.optJSONArray("sources")
            ?: thumbObj.optJSONObject("image")?.optJSONArray("sources")
            ?: return ""
        if (thumbnails.length() == 0) return ""
        val last = thumbnails.optJSONObject(thumbnails.length() - 1)
        val url = last?.optString("url").orEmpty()
        return if (url.startsWith("//")) "https:$url" else url
    }

    private fun findObjectsWithKey(json: Any, key: String, result: MutableList<JSONObject>) {
        when (json) {
            is JSONObject -> {
                if (json.has(key)) {
                    json.optJSONObject(key)?.let { result.add(it) }
                }
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k != key) {
                        findObjectsWithKey(json.opt(k) ?: continue, key, result)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until json.length()) {
                    findObjectsWithKey(json.opt(i) ?: continue, key, result)
                }
            }
        }
    }

    /**
     * Mendapatkan posisi detik tontonan terakhir (ms) yang tersimpan
     * baik dari Room lokal maupun persentase tontonan server YouTube.
     */
    suspend fun getSavedPlaybackPosition(videoId: String, durationSeconds: Long): Long = withContext(ioDispatcher) {
        try {
            // Jika video sudah selesai ditonton (persentase >= 90% atau berada di 10 detik terakhir),
            // pemutaran berikutnya HARUS mengulang dari 0 (awal video) seperti standar YouTube!
            fun isVideoCompleted(posMs: Long, percent: Float, durSec: Long): Boolean {
                if (percent >= 0.90f) return true
                val effDurSec = if (durSec > 0) durSec else durationSeconds
                if (effDurSec > 10L) {
                    val effDurMs = effDurSec * 1000L
                    if (posMs >= effDurMs - 10_000L) return true
                    if (posMs >= (effDurMs * 0.90f).toLong()) return true
                }
                return false
            }

            // 1. Cek StateFlow in-memory aktif (paling update & real-time di UI)
            val memory = _watchHistory.value.firstOrNull { it.id == videoId }
            if (memory != null) {
                val dur = if (durationSeconds > 0) durationSeconds else memory.durationSeconds
                if (isVideoCompleted(memory.playbackPositionMs, memory.playbackPercent, dur)) {
                    return@withContext 0L
                }
                if (memory.playbackPositionMs > 1500L) return@withContext memory.playbackPositionMs
                if (memory.playbackPercent > 0.02f && dur > 0) {
                    return@withContext (memory.playbackPercent * dur * 1000L).toLong()
                }
            }

            // 2. Cek remoteHistoryCache dari respons API YouTube terbaru
            val remote = remoteHistoryCache.firstOrNull { it.id == videoId }
            if (remote != null) {
                val dur = if (durationSeconds > 0) durationSeconds else remote.durationSeconds
                if (isVideoCompleted(remote.playbackPositionMs, remote.playbackPercent, dur)) {
                    return@withContext 0L
                }
                if (remote.playbackPositionMs > 1500L) return@withContext remote.playbackPositionMs
                if (remote.playbackPercent > 0.02f && dur > 0) {
                    return@withContext (remote.playbackPercent * dur * 1000L).toLong()
                }
            }

            // 3. Cek database Room lokal
            val local = historyDao.getById(videoId)
            if (local != null) {
                val dur = if (durationSeconds > 0) durationSeconds else local.durationSeconds
                if (isVideoCompleted(local.playbackPositionMs, local.playbackPercent, dur)) {
                    return@withContext 0L
                }
                if (local.playbackPositionMs > 1500L) return@withContext local.playbackPositionMs
                if (local.playbackPercent > 0.02f && dur > 0) {
                    return@withContext (local.playbackPercent * dur * 1000L).toLong()
                }
            }
        } catch (_: Exception) {}
        0L
    }
}
