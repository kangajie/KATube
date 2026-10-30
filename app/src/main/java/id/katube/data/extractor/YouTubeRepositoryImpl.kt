package id.katube.data.extractor

import id.katube.core.util.KATubeException
import id.katube.data.model.ChannelItem
import id.katube.data.model.PlaylistItem
import id.katube.data.model.StreamBundle
import id.katube.data.model.VideoItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import id.katube.core.util.CommentUtils
import id.katube.core.util.TimeAgoUtils
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : YouTubeRepository {

    private val youTubeService = ServiceList.YouTube

    override suspend fun getTrending(): Result<List<VideoItem>> = withContext(ioDispatcher) {
        safeExtract {
            val kioskList = youTubeService.kioskList
            val kioskExtractor = kioskList.defaultKioskExtractor
            kioskExtractor.fetchPage()

            kioskExtractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoItem() }
        }
    }

    override suspend fun search(query: String): Result<List<VideoItem>> = withContext(ioDispatcher) {
        safeExtract {
            val searchExtractor = youTubeService.getSearchExtractor(query)
            searchExtractor.fetchPage()

            searchExtractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideoItem() }
        }
    }

    override suspend fun getSearchSuggestions(query: String): Result<List<String>> = withContext(ioDispatcher) {
        safeExtract {
            val suggestionExtractor = youTubeService.suggestionExtractor
            suggestionExtractor?.suggestionList(query).orEmpty()
        }
    }

    override suspend fun getStreamInfo(videoIdOrUrl: String, maxHeight: Int): Result<StreamBundle> = withContext(ioDispatcher) {
        safeExtract {
            var targetId = videoIdOrUrl.trim()
            if (targetId.startsWith("http://") || targetId.startsWith("https://")) {
                val uri = android.net.Uri.parse(targetId)
                val v = uri.getQueryParameter("v")
                if (!v.isNullOrBlank()) {
                    targetId = v
                }
            }
            if (targetId.contains("v=")) {
                val qv = targetId.substringAfter("v=").substringBefore("&").substringBefore("?")
                if (qv.length == 11) {
                    targetId = qv
                }
            }
            if (targetId.contains("&")) {
                targetId = targetId.substringBefore("&")
            }
            if (targetId.contains("?")) {
                targetId = targetId.substringBefore("?")
            }

            // Jika targetId adalah ID playlist atau Mix (dimulai dengan RD, PL, VL, dsb. dan bukan video ID 11 karakter),
            // cari video pertama dari playlist tersebut agar pemutaran tidak pernah gagal
            if (targetId.length != 11 && (targetId.startsWith("RD") || targetId.startsWith("PL") || targetId.startsWith("VL") || targetId.startsWith("OLAK5uy_"))) {
                try {
                    val plUrl = if (targetId.startsWith("http")) targetId else "https://www.youtube.com/playlist?list=$targetId"
                    val plInfo = PlaylistInfo.getInfo(youTubeService, plUrl)
                    val firstStream = plInfo.relatedItems?.filterIsInstance<StreamInfoItem>()?.firstOrNull()
                    if (firstStream != null) {
                        val realId = if (firstStream.url.contains("v=")) {
                            firstStream.url.substringAfter("v=").substringBefore("&")
                        } else {
                            firstStream.url.substringAfterLast("/")
                        }
                        if (realId.isNotBlank()) {
                            targetId = realId
                        }
                    }
                } catch (_: Exception) {
                    // Abaikan dan coba dengan targetId apa adanya
                }
            }

            val fullUrl = if (targetId.startsWith("http://") || targetId.startsWith("https://")) {
                targetId
            } else {
                "https://www.youtube.com/watch?v=$targetId"
            }

            val info = StreamInfo.getInfo(youTubeService, fullUrl)
            StreamSelector.select(info, maxHeight, context)
        }
    }

    override suspend fun getChannel(channelIdOrUrl: String): Result<ChannelItem> = withContext(ioDispatcher) {
        safeExtract {
            val decoded = try {
                java.net.URLDecoder.decode(channelIdOrUrl.trim(), "UTF-8")
            } catch (_: Exception) {
                channelIdOrUrl.trim()
            }
            val clean = decoded.trim().trimEnd('/')
            val fullUrl = when {
                clean.startsWith("http://") || clean.startsWith("https://") -> clean
                clean.startsWith("@") -> "https://www.youtube.com/$clean"
                clean.startsWith("UC") -> "https://www.youtube.com/channel/$clean"
                clean.contains("youtube.com/channel/") -> "https://www.youtube.com/channel/${clean.substringAfter("youtube.com/channel/")}"
                clean.contains("youtube.com/@") -> "https://www.youtube.com/@${clean.substringAfter("youtube.com/@")}"
                clean.contains("/channel/") -> "https://www.youtube.com/channel/${clean.substringAfter("/channel/")}"
                clean.contains("/@") -> "https://www.youtube.com/@${clean.substringAfter("/@")}"
                else -> "https://www.youtube.com/channel/$clean"
            }

            val info = ChannelInfo.getInfo(youTubeService, fullUrl)
            val channelId = fullUrl.substringAfterLast("/").substringBefore("?")

            val videoTab = info.tabs.firstOrNull { tab ->
                tab.contentFilters.contains(ChannelTabs.VIDEOS) == true || tab.url.contains("/videos")
            } ?: info.tabs.firstOrNull()

            val videos = if (videoTab != null) {
                try {
                    val tabInfo = ChannelTabInfo.getInfo(youTubeService, videoTab)
                    tabInfo.relatedItems
                        ?.filterIsInstance<StreamInfoItem>()
                        ?.map { it.toVideoItem() }
                        .orEmpty()
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            ChannelItem(
                id = channelId,
                url = info.url.orEmpty(),
                name = info.name.orEmpty(),
                avatarUrl = info.avatars.firstOrNull()?.url,
                bannerUrl = info.banners.firstOrNull()?.url,
                subscriberCount = info.subscriberCount,
                description = info.description,
                videos = videos,
            )
        }
    }

    override suspend fun getComments(videoIdOrUrl: String): Result<List<id.katube.data.model.CommentItem>> = withContext(ioDispatcher) {
        safeExtract {
            val fullUrl = if (videoIdOrUrl.startsWith("http")) {
                videoIdOrUrl
            } else {
                "https://www.youtube.com/watch?v=$videoIdOrUrl"
            }
            val info = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(youTubeService, fullUrl)
            info.relatedItems.orEmpty().map { item ->
                id.katube.data.model.CommentItem(
                    id = item.commentId.orEmpty(),
                    authorName = CommentUtils.formatAuthorHandle(item.uploaderName),
                    authorAvatarUrl = item.uploaderAvatars.firstOrNull()?.url,
                    text = CommentUtils.cleanCommentText(item.commentText.content),
                    uploadDateText = TimeAgoUtils.format(item.uploadDate, item.textualUploadDate),
                    likeCount = if (item.likeCount > 0) item.likeCount else 0,
                )
            }
        }
    }

    override suspend fun getPlaylist(playlistIdOrUrl: String): Result<PlaylistItem> = withContext(ioDispatcher) {
        safeExtract {
            val cleanId = playlistIdOrUrl
                .substringAfter("list=")
                .substringBefore("&")
                .removePrefix("VL")
                .trim()
            val fullUrl = "https://www.youtube.com/playlist?list=$cleanId"

            val info = PlaylistInfo.getInfo(youTubeService, fullUrl)
            val playlistId = cleanId
            val videos = info.relatedItems
                ?.filterIsInstance<StreamInfoItem>()
                ?.map { it.toVideoItem() }
                .orEmpty()

            PlaylistItem(
                id = playlistId,
                url = info.url.orEmpty(),
                name = info.name.orEmpty(),
                uploaderName = info.uploaderName,
                thumbnailUrl = info.thumbnails.firstOrNull()?.url,
                streamCount = info.streamCount,
                videos = videos,
            )
        }
    }

    /**
     * Pembungkus aman untuk menangani exception extractor dan memetakannya ke KATubeException.
     */
    private inline fun <T> safeExtract(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: ReCaptchaException) {
            Result.failure(KATubeException.RateLimit(cause = e))
        } catch (e: ContentNotAvailableException) {
            Result.failure(KATubeException.ContentUnavailable(cause = e))
        } catch (e: IOException) {
            Result.failure(KATubeException.NetworkError(cause = e))
        } catch (e: ExtractionException) {
            Result.failure(KATubeException.ParsingError(cause = e))
        } catch (e: Exception) {
            Result.failure(KATubeException.Unknown(message = e.message ?: "Terjadi kesalahan", cause = e))
        }
    }

    private fun StreamInfoItem.toVideoItem(): VideoItem {
        val safeUrl = url.orEmpty()
        val videoId = if (safeUrl.contains("v=")) {
            safeUrl.substringAfter("v=").substringBefore("&")
        } else {
            safeUrl.substringAfterLast("/")
        }
        val isLiveStream = streamType == StreamType.LIVE_STREAM
        return VideoItem(
            id = videoId,
            url = safeUrl,
            title = name.orEmpty(),
            uploaderName = uploaderName.orEmpty(),
            uploaderAvatarUrl = uploaderAvatars?.lastOrNull()?.url ?: uploaderAvatars?.firstOrNull()?.url,
            uploaderUrl = uploaderUrl,
            durationSeconds = if (isLiveStream) 0L else duration,
            isLive = isLiveStream,
            rawDurationText = if (isLiveStream) "LIVE" else null,
            viewCount = viewCount,
            uploadDateText = TimeAgoUtils.format(uploadDate, textualUploadDate),
            thumbnailUrl = thumbnails?.lastOrNull()?.url
                ?: thumbnails?.firstOrNull()?.url
                ?: if (videoId.isNotEmpty()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null,
        )
    }
}
