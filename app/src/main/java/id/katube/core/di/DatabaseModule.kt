package id.katube.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.katube.data.local.KATubeDatabase
import id.katube.data.local.history.WatchHistoryDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideKATubeDatabase(
        @ApplicationContext context: Context,
    ): KATubeDatabase {
        return Room.databaseBuilder(
            context,
            KATubeDatabase::class.java,
            "katube_database.db",
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideWatchHistoryDao(
        database: KATubeDatabase,
    ): WatchHistoryDao {
        return database.watchHistoryDao()
    }
}
