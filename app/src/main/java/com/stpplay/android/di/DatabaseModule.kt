package com.stpplay.android.di

import android.content.Context
import com.stpplay.android.database.AppDatabase
import com.stpplay.android.database.ChannelDao
import com.stpplay.android.database.EpgDao
import com.stpplay.android.database.FavoriteDao
import com.stpplay.android.database.PlaybackPositionDao
import com.stpplay.android.database.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideChannelDao(db: AppDatabase): ChannelDao = db.channelDao()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun providePlaybackPositionDao(db: AppDatabase): PlaybackPositionDao = db.playbackPositionDao()

    @Provides
    fun provideEpgDao(db: AppDatabase): EpgDao = db.epgDao()

    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()
}
