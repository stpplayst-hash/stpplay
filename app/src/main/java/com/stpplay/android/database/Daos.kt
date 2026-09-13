package com.stpplay.android.database

import androidx.room.*
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT COUNT(*) FROM channels")
    fun getChannelsCount(): Flow<Int>

    @Query("SELECT * FROM channels WHERE streamId IN (:ids)")
    suspend fun getChannelsByStreamIds(ids: List<String>): List<ChannelEntity>

    @Query("""
        SELECT * FROM channels 
        WHERE type != 'LIVE' 
        AND streamId NOT IN (SELECT streamId FROM favorites)
        ORDER BY RANDOM() LIMIT :limit
    """)
    suspend fun getRandomSafeChannels(limit: Int): List<ChannelEntity>

    @Query("SELECT DISTINCT `group` FROM channels WHERE type = :type AND `group` IS NOT NULL")
    suspend fun getDistinctCategoriesByType(type: com.stpplay.android.data.ContentType): List<String>

    @Query("""
        SELECT c.* FROM channels c 
        INNER JOIN favorites f ON c.streamId = f.streamId 
        WHERE f.profileId = :profileId
    """)
    fun getFavoriteChannels(profileId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE type = :type ORDER BY name ASC")
    fun getChannelsByTypePaging(type: com.stpplay.android.data.ContentType): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels LIMIT :limit")
    fun getChannelsPaged(limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE type = :type")
    fun getChannelsByType(type: com.stpplay.android.data.ContentType): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE type = :type LIMIT :limit")
    fun getChannelsByTypeLimited(type: com.stpplay.android.data.ContentType, limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE categoryId = :catId OR `group` = :groupName")
    fun getChannelsByCategory(catId: String?, groupName: String?): Flow<List<ChannelEntity>>

    @Query("""
        SELECT * FROM channels 
        WHERE type = :type 
        AND (:groupName IS NULL OR `group` = :groupName)
        ORDER BY 
            CASE WHEN :sortBy = 'Nome' THEN name END ASC,
            CASE WHEN :sortBy = 'IMDb' THEN CAST(rating AS FLOAT) END DESC,
            CASE WHEN :sortBy = 'Ano' THEN CAST(year AS INTEGER) END DESC,
            CASE WHEN :sortBy = 'Adicionado' THEN CAST(streamId AS INTEGER) END DESC,
            name ASC
    """)
    fun getChannelsByTypeAndGroupPaging(
        type: com.stpplay.android.data.ContentType, 
        groupName: String?, 
        sortBy: String = "Adicionado"
    ): PagingSource<Int, ChannelEntity>

    @Query("""
        SELECT c.* FROM channels c 
        INNER JOIN favorites f ON c.streamId = f.streamId 
        WHERE f.profileId = :profileId AND c.type = :type
        ORDER BY 
            CASE WHEN :sortBy = 'Nome' THEN c.name END ASC,
            CASE WHEN :sortBy = 'IMDb' THEN CAST(c.rating AS FLOAT) END DESC,
            CASE WHEN :sortBy = 'Ano' THEN CAST(c.year AS INTEGER) END DESC,
            CASE WHEN :sortBy = 'Adicionado' THEN CAST(c.streamId AS INTEGER) END DESC,
            c.name ASC
    """)
    fun getFavoriteChannelsByTypePaging(
        profileId: String, 
        type: com.stpplay.android.data.ContentType, 
        sortBy: String = "Adicionado"
    ): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE categoryId = :catId OR `group` = :groupName ORDER BY name ASC")
    fun getChannelsByCategoryPaging(catId: String?, groupName: String?): PagingSource<Int, ChannelEntity>

    @Query("""
        SELECT * FROM channels 
        WHERE streamId IN (SELECT streamId FROM channels_fts WHERE name MATCH :query || '*') 
        LIMIT :limit
    """)
    fun searchChannels(query: String, limit: Int): Flow<List<ChannelEntity>>

    @Query("""
        SELECT * FROM channels 
        WHERE streamId IN (SELECT streamId FROM channels_fts WHERE name MATCH :query || '*') 
        ORDER BY name ASC
    """)
    fun searchChannelsPaging(query: String): PagingSource<Int, ChannelEntity>

    @Query("""
        SELECT * FROM channels 
        WHERE streamId != :excludeId 
        AND (categoryId = :categoryId OR `group` = :groupName)
        AND type != 'LIVE'
        ORDER BY rating DESC LIMIT :limit
    """)
    fun getSimilarContent(excludeId: String, categoryId: String?, groupName: String?, limit: Int): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannelsFts(fts: List<ChannelFtsEntity>)

    @Query("DELETE FROM channels_fts")
    suspend fun deleteAllFts()

    @Query("SELECT * FROM channels WHERE streamId = :streamId LIMIT 1")
    suspend fun getChannelById(streamId: String): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun deleteAll()
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: String): ProfileEntity?
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE profileId = :profileId")
    fun getAllFavorites(profileId: String): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Delete
    suspend fun deleteFavorite(favorite: FavoriteEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE streamId = :streamId AND profileId = :profileId)")
    suspend fun isFavorite(streamId: String, profileId: String): Boolean
}

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions WHERE streamId = :streamId AND profileId = :profileId")
    suspend fun getPosition(streamId: String, profileId: String): PlaybackPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePosition(position: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE streamId = :streamId AND profileId = :profileId")
    suspend fun deletePosition(streamId: String, profileId: String)

    @Query("""
        SELECT c.*, p.position as position FROM channels c 
        INNER JOIN (
            SELECT streamId, parentId, position, profileId, lastUpdated, MAX(lastUpdated) FROM playback_positions 
            WHERE profileId = :profileId OR :profileId IS NULL
            GROUP BY COALESCE(parentId, streamId)
        ) p ON c.streamId = COALESCE(p.parentId, p.streamId)
        WHERE p.position > 10000
        ORDER BY p.lastUpdated DESC LIMIT 20
    """)
    fun getContinueWatchingWithPosition(profileId: String?): Flow<List<ChannelWithPositionEntity>>

    @Query("""
        SELECT c.* FROM channels c 
        INNER JOIN playback_positions p ON c.streamId = p.streamId 
        WHERE c.type = 'LIVE' AND (:profileId IS NULL OR p.profileId = :profileId)
        ORDER BY p.lastUpdated DESC LIMIT 20
    """)
    fun getRecentlyViewedChannels(profileId: String?): Flow<List<ChannelEntity>>

    @Query("""
        SELECT c.* FROM channels c 
        INNER JOIN playback_positions p ON c.streamId = p.streamId 
        WHERE p.position > 10000 AND (:profileId IS NULL OR p.profileId = :profileId)
        ORDER BY p.lastUpdated DESC LIMIT 1
    """)
    suspend fun getLastUnfinishedContent(profileId: String? = null): ChannelEntity?
}

data class ChannelWithPositionEntity(
    @Embedded val channel: ChannelEntity,
    val position: Long
)

@Dao
interface EpgDao {
    @Query("""
        SELECT * FROM epg_programs 
        WHERE (
            streamId = :streamId 
            OR (:epgId IS NOT NULL AND LOWER(epgChannelId) = LOWER(:epgId))
            OR (LOWER(streamId) = LOWER(:epgId))
            OR (epgChannelId = :streamId)
        ) 
        AND stopTimestamp > :currentTime 
        ORDER BY startTimestamp ASC
    """)
    fun getUpcomingPrograms(streamId: String, epgId: String?, currentTime: Long): Flow<List<EpgProgramEntity>>

    @Query("""
        SELECT * FROM epg_programs 
        WHERE (
            streamId = :streamId 
            OR (:epgId IS NOT NULL AND LOWER(epgChannelId) = LOWER(:epgId))
            OR (LOWER(streamId) = LOWER(:epgId))
            OR (epgChannelId = :streamId)
        ) 
        AND startTimestamp <= :currentTime AND stopTimestamp >= :currentTime 
        LIMIT 1
    """)
    suspend fun getCurrentProgram(streamId: String, epgId: String?, currentTime: Long): EpgProgramEntity?

    @Query("""
        SELECT * FROM epg_programs 
        WHERE stopTimestamp > :startTime AND startTimestamp < :endTime
        ORDER BY startTimestamp ASC
    """)
    fun getProgramsInRange(startTime: Long, endTime: Long): Flow<List<EpgProgramEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE stopTimestamp < :currentTime")
    suspend fun deleteOldPrograms(currentTime: Long)

    @Query("DELETE FROM epg_programs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM epg_programs")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM epg_programs WHERE streamId = :id OR epgChannelId = :id")
    suspend fun getCountForChannel(id: String): Int
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE profileId = :profileId")
    fun getAllReminders(profileId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    fun getAllRemindersForReschedule(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE startTimestamp < :currentTime")
    suspend fun deleteOldReminders(currentTime: Long)
    
    @Query("SELECT EXISTS(SELECT 1 FROM reminders WHERE streamId = :streamId AND startTimestamp = :startTime AND profileId = :profileId)")
    suspend fun hasReminder(streamId: String, startTime: Long, profileId: String): Boolean
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT 10")
    fun getRecentSearches(profileId: String): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE profileId = :profileId")
    suspend fun clearHistory(profileId: String)
}
