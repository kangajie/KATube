package id.katube.data.local.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WatchHistoryEntity>)

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC")
    fun getAllFlow(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE videoId = :videoId LIMIT 1")
    suspend fun getById(videoId: String): WatchHistoryEntity?

    @Query("UPDATE watch_history SET playbackPositionMs = :positionMs, playbackPercent = :percent, lastWatchedAt = :timestamp WHERE videoId = :videoId")
    suspend fun updateProgress(videoId: String, positionMs: Long, percent: Float, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM watch_history WHERE videoId = :videoId")
    suspend fun deleteById(videoId: String)

    @Query("DELETE FROM watch_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM watch_history")
    suspend fun getCount(): Int
}
