package id.katube.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import id.katube.data.local.history.WatchHistoryDao
import id.katube.data.local.history.WatchHistoryEntity

@Database(
    entities = [WatchHistoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class KATubeDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
}
