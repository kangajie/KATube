package id.katube.data.local.history

import androidx.room.Entity
import androidx.room.PrimaryKey
import id.katube.data.model.VideoItem

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey
    val videoId: String,
    val url: String,
    val title: String,
    val uploaderName: String,
    val uploaderAvatarUrl: String? = null,
    val uploaderUrl: String? = null,
    val durationSeconds: Long = 0,
    val viewCount: Long = 0,
    val uploadDateText: String? = null,
    val thumbnailUrl: String? = null,
    val isMix: Boolean = false,
    val playlistId: String? = null,
    val playbackPositionMs: Long = 0L,
    val playbackPercent: Float = 0f,
    val lastWatchedAt: Long = System.currentTimeMillis(),
) {
    fun toVideoItem(): VideoItem {
        return VideoItem(
            id = videoId,
            url = url,
            title = title,
            uploaderName = uploaderName,
            uploaderAvatarUrl = uploaderAvatarUrl,
            uploaderUrl = uploaderUrl,
            durationSeconds = durationSeconds,
            viewCount = viewCount,
            uploadDateText = uploadDateText,
            thumbnailUrl = thumbnailUrl,
            isMix = isMix,
            playlistId = playlistId,
            playbackPositionMs = playbackPositionMs,
            playbackPercent = playbackPercent,
        )
    }

    companion object {
        fun fromVideoItem(video: VideoItem, timestamp: Long = System.currentTimeMillis()): WatchHistoryEntity {
            return WatchHistoryEntity(
                videoId = video.id,
                url = video.url.ifEmpty { "https://www.youtube.com/watch?v=${video.id}" },
                title = video.title,
                uploaderName = video.uploaderName,
                uploaderAvatarUrl = video.uploaderAvatarUrl,
                uploaderUrl = video.uploaderUrl,
                durationSeconds = video.durationSeconds,
                viewCount = video.viewCount,
                uploadDateText = video.uploadDateText,
                thumbnailUrl = video.thumbnailUrl,
                isMix = video.isMix,
                playlistId = video.playlistId,
                playbackPositionMs = video.playbackPositionMs,
                playbackPercent = video.playbackPercent,
                lastWatchedAt = timestamp,
            )
        }
    }
}
