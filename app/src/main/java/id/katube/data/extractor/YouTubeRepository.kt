package id.katube.data.extractor

import id.katube.data.model.ChannelItem
import id.katube.data.model.PlaylistItem
import id.katube.data.model.StreamBundle
import id.katube.data.model.VideoItem

/**
 * Kontrak repository untuk mengakses data YouTube via NewPipeExtractor.
 * Seluruh fungsi berjalan di background thread (Dispatchers.IO) dan mengembalikan Result.
 */
interface YouTubeRepository {

    /**
     * Mengambil daftar video trending di YouTube Indonesia.
     */
    suspend fun getTrending(): Result<List<VideoItem>>

    /**
     * Melakukan pencarian video di YouTube berdasarkan kata kunci.
     */
    suspend fun search(query: String): Result<List<VideoItem>>

    /**
     * Mengambil saran kata kunci (autocomplete search suggestions).
     */
    suspend fun getSearchSuggestions(query: String): Result<List<String>>

    /**
     * Mengambil informasi stream detail (video/audio URLs) untuk diputar di player.
     * @param videoIdOrUrl ID video (11 karakter) atau URL lengkap YouTube.
     * @param maxHeight Resolusi video maksimal yang diinginkan (default 0 / Auto).
     */
    suspend fun getStreamInfo(videoIdOrUrl: String, maxHeight: Int = 0): Result<StreamBundle>

    /**
     * Mengambil informasi channel YouTube.
     */
    suspend fun getChannel(channelIdOrUrl: String): Result<ChannelItem>

    /**
     * Mengambil informasi playlist YouTube.
     */
    suspend fun getPlaylist(playlistIdOrUrl: String): Result<PlaylistItem>

    /**
     * Mengambil daftar komentar video YouTube (Sesuai Referensi PDF Halaman 4).
     */
    suspend fun getComments(videoIdOrUrl: String): Result<List<id.katube.data.model.CommentItem>>
}
