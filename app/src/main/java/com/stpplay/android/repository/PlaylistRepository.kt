package com.stpplay.android.repository

import com.stpplay.android.data.Channel
import com.stpplay.android.data.ContentType
import com.stpplay.android.data.PlaylistCredentials
import com.stpplay.android.data.ProfileManager
import com.stpplay.android.data.UserInfo
import com.stpplay.android.database.*
import com.stpplay.android.parser.XtreamParser
import androidx.room.withTransaction
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlaylistRepository @Inject constructor(
    private val db: AppDatabase,
    private val profileManager: ProfileManager
) {

    fun getChannelsCount(): Flow<Int> = db.channelDao().getChannelsCount()

    suspend fun getChannelsByStreamIds(ids: List<String>): List<Channel> =
        db.channelDao().getChannelsByStreamIds(ids).map { it.toChannel() }

    suspend fun getRandomSafeChannels(limit: Int): List<Channel> =
        db.channelDao().getRandomSafeChannels(limit).map { it.toChannel() }

    suspend fun getDistinctCategoriesByType(type: ContentType): List<String> =
        db.channelDao().getDistinctCategoriesByType(type)

    fun getFavoriteChannels(profileId: String): Flow<List<Channel>> =
        db.channelDao().getFavoriteChannels(profileId).map { entities -> entities.map { it.toChannel() } }

    fun getChannelsByTypePaging(type: ContentType): Flow<PagingData<Channel>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = true),
            pagingSourceFactory = { db.channelDao().getChannelsByTypePaging(type) }
        ).flow.map { pagingData -> pagingData.map { it.toChannel() } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getChannels(): Flow<List<Channel>> = profileManager.currentProfile.flatMapLatest { profile ->
        val isKids = profile?.isKids == true
        android.util.Log.d("PlaylistRepository", "Carregando canais para perfil: ${profile?.name} (Kids=$isKids)")
        
        db.channelDao().getAllChannels().map { entities ->
            val channels = entities.asSequence().map { it.toChannel() }
            if (isKids) {
                channels.filter { Channel.isKidsContent(it) }.toList()
            } else {
                channels.toList()
            }
        }
    }

    fun searchChannels(query: String, limit: Int = 500): Flow<List<Channel>> =
        db.channelDao().searchChannels(query, limit).map { entities -> entities.map { it.toChannel() } }

    fun getChannelsByType(type: ContentType): Flow<List<Channel>> =
        db.channelDao().getChannelsByType(type).map { entities -> entities.map { it.toChannel() } }

    fun getChannelsByTypeLimited(type: ContentType, limit: Int): Flow<List<Channel>> =
        profileManager.currentProfile.flatMapLatest { profile ->
            val isKids = profile?.isKids == true
            db.channelDao().getChannelsByTypeLimited(type, if (isKids) 500 else limit).map { entities -> 
                val base = entities.map { it.toChannel() }
                if (isKids) {
                    base.filter { Channel.isKidsContent(it) }.take(limit)
                } else {
                    base
                }
            }
        }

    fun getChannelsByCategory(catId: String?, groupName: String?): Flow<List<Channel>> =
        db.channelDao().getChannelsByCategory(catId, groupName).map { entities -> entities.map { it.toChannel() } }

    fun getChannelsByCategoryPaging(
        type: ContentType, 
        groupName: String?, 
        isFavorites: Boolean = false, 
        profileId: String? = null,
        sortBy: String = "Adicionado"
    ): Flow<PagingData<Channel>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = true),
            pagingSourceFactory = { 
                if (isFavorites && profileId != null) {
                    db.channelDao().getFavoriteChannelsByTypePaging(profileId, type, sortBy)
                } else {
                    db.channelDao().getChannelsByTypeAndGroupPaging(type, groupName, sortBy)
                }
            }
        ).flow.map { pagingData -> pagingData.map { it.toChannel() } }
    }

    fun searchChannelsPaging(query: String): Flow<PagingData<Channel>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = true),
            pagingSourceFactory = { db.channelDao().searchChannelsPaging(query) }
        ).flow.map { pagingData -> pagingData.map { it.toChannel() } }
    }

    suspend fun getChannelById(streamId: String): Channel? =
        db.channelDao().getChannelById(streamId)?.toChannel()

    fun getFavorites(profileId: String): Flow<Set<String>> = db.favoriteDao().getAllFavorites(profileId).map { entities ->
        entities.map { it.streamId }.toSet()
    }

    suspend fun toggleFavorite(streamId: String, profileId: String) {
        if (db.favoriteDao().isFavorite(streamId, profileId)) {
            db.favoriteDao().deleteFavorite(FavoriteEntity(streamId, profileId))
        } else {
            db.favoriteDao().insertFavorite(FavoriteEntity(streamId, profileId))
        }
    }

    suspend fun savePlaybackPosition(streamId: String, profileId: String, position: Long, parentId: String? = null) {
        db.playbackPositionDao().savePosition(PlaybackPositionEntity(streamId, profileId, position, parentId))
    }

    suspend fun deletePlaybackPosition(streamId: String, profileId: String) {
        db.playbackPositionDao().deletePosition(streamId, profileId)
    }

    suspend fun getPlaybackPosition(streamId: String, profileId: String): Long {
        return db.playbackPositionDao().getPosition(streamId, profileId)?.position ?: 0L
    }

    suspend fun loadXtreamPlaylist(creds: PlaylistCredentials): Pair<List<Channel>, UserInfo?> {
        val result = XtreamParser.loadFromXtream(creds)
        
        val liveCount = result.first.count { it.type == ContentType.LIVE }
        val movieCount = result.first.count { it.type == ContentType.MOVIE }
        val seriesCount = result.first.count { it.type == ContentType.SERIES }
        android.util.Log.d("PlaylistRepository", "Dados baixados Xtream: $liveCount Live, $movieCount Filmes, $seriesCount Séries")

        if (result.first.isNotEmpty()) {
            saveChannelsToDb(result.first)
        }
        return result
    }

    suspend fun loadM3uPlaylist(url: String): List<Channel> {
        val channels = com.stpplay.android.parser.M3uParser.loadFromUrl(url)
        if (channels.isNotEmpty()) {
            saveChannelsToDb(channels)
        }
        return channels
    }

    private suspend fun saveChannelsToDb(channels: List<Channel>) = withContext(Dispatchers.IO) {
        val entities = channels.map { ChannelEntity.fromChannel(it) }
        val ftsEntities = channels.map { ChannelFtsEntity(it.streamId ?: it.url, it.name) }
        
        db.withTransaction {
            db.channelDao().deleteAll()
            db.channelDao().deleteAllFts()
            entities.chunked(500).forEach { chunk ->
                db.channelDao().insertChannels(chunk)
            }
            ftsEntities.chunked(500).forEach { chunk ->
                db.channelDao().insertChannelsFts(chunk)
            }
        }
        android.util.Log.d("PlaylistRepository", "Total de ${entities.size} itens salvos com sucesso em blocos (FTS incluído).")
    }

    suspend fun fetchSeriesEpisodes(creds: PlaylistCredentials, seriesId: String): List<Channel> {
        return XtreamParser.fetchSeriesEpisodes(creds, seriesId)
    }

    suspend fun fetchVODInfo(creds: PlaylistCredentials, streamId: String, type: ContentType): Pair<String?, String?> {
        return XtreamParser.fetchVODInfo(creds, streamId, type)
    }

    suspend fun fetchTrailerUrl(creds: PlaylistCredentials, streamId: String, type: ContentType): String? {
        return XtreamParser.fetchTrailerUrl(creds, streamId, type)
    }

    suspend fun loadEpg(creds: PlaylistCredentials, streamId: String, epgChannelId: String? = null) = withContext(Dispatchers.IO) {
        val programs = XtreamParser.fetchShortEpg(creds, streamId, epgChannelId)
        if (programs.isNotEmpty()) {
            val programsWithEpgId = if (epgChannelId != null) {
                programs.map { it.copy(epgChannelId = epgChannelId) }
            } else programs
            android.util.Log.d("EPG_DEBUG", "Salvando ${programsWithEpgId.size} programas para o canal $streamId")
            db.epgDao().insertPrograms(programsWithEpgId)
        }
    }

    suspend fun loadAllEpg(creds: PlaylistCredentials) = withContext(Dispatchers.IO) {
        try {
            val epgMap = XtreamParser.fetchSimpleDataTable(creds)
            if (epgMap.isNotEmpty()) {
                android.util.Log.d("EPG_DEBUG", "Persistindo ${epgMap.size} programas globais...")
                db.epgDao().insertPrograms(epgMap.values.toList())
            }
        } catch (e: Exception) {
            android.util.Log.e("EPG_DEBUG", "Erro carga global: ${e.message}")
        }
    }

    fun getCurrentProgramFlow(streamId: String, epgId: String?): Flow<EpgProgramEntity?> = flow {
        var count = 0
        while (true) {
            emit(db.epgDao().getCurrentProgram(streamId, epgId, System.currentTimeMillis()))
            val delayTime = if (count < 3) 10000L else 60000L
            kotlinx.coroutines.delay(delayTime)
            count++
        }
    }

    suspend fun getCurrentProgram(streamId: String, epgId: String?): EpgProgramEntity? {
        val now = System.currentTimeMillis()
        val program = db.epgDao().getCurrentProgram(streamId, epgId, now)
        return program
    }

    fun getUpcomingPrograms(streamId: String, epgId: String?): Flow<List<EpgProgramEntity>> {
        return db.epgDao().getUpcomingPrograms(streamId, epgId, System.currentTimeMillis())
    }

    fun getEpgProgramsInRange(startTime: Long, endTime: Long): Flow<List<EpgProgramEntity>> =
        db.epgDao().getProgramsInRange(startTime, endTime)

    suspend fun clearPlaylist() {
        db.channelDao().deleteAll()
        db.epgDao().deleteAll()
    }

    fun getAllReminders(profileId: String) = db.reminderDao().getAllReminders(profileId)

    suspend fun toggleReminder(reminder: com.stpplay.android.database.ReminderEntity) {
        if (db.reminderDao().hasReminder(reminder.streamId, reminder.startTimestamp, reminder.profileId)) {
            db.reminderDao().deleteReminder(reminder)
        } else {
            db.reminderDao().insertReminder(reminder)
        }
    }

    suspend fun hasReminder(streamId: String, startTime: Long, profileId: String) =
        db.reminderDao().hasReminder(streamId, startTime, profileId)

    fun getRecentSearches(profileId: String) = db.searchHistoryDao().getRecentSearches(profileId)

    suspend fun saveSearch(query: String, profileId: String) {
        if (query.isNotBlank()) {
            db.searchHistoryDao().insertSearch(com.stpplay.android.database.SearchHistoryEntity(query, profileId))
        }
    }

    suspend fun clearSearchHistory(profileId: String) = db.searchHistoryDao().clearHistory(profileId)

    fun getContinueWatching(profileId: String): Flow<List<Channel>> = 
        db.playbackPositionDao().getContinueWatchingWithPosition(profileId).map { entities ->
            entities.map { it.channel.toChannel() }
        }

    fun getContinueWatchingWithProgress(profileId: String): Flow<List<com.stpplay.android.data.ChannelWithProgress>> =
        db.playbackPositionDao().getContinueWatchingWithPosition(profileId).map { entities ->
            entities.map { entry ->
                val channel = entry.channel.toChannel()
                val durationSecs = channel.duration?.toLongOrNull() ?: 0L
                val progress = if (durationSecs > 0) {
                    (entry.position / 1000f) / durationSecs
                } else 0f
                com.stpplay.android.data.ChannelWithProgress(channel, progress.coerceIn(0f, 1f))
            }
        }

    fun getRecentLiveChannels(profileId: String): Flow<List<Channel>> =
        db.playbackPositionDao().getRecentlyViewedChannels(profileId).map { entities ->
            entities.map { it.toChannel() }
        }

    fun getLastWatchedContent(profileId: String): Flow<Channel?> =
        db.playbackPositionDao().getContinueWatchingWithPosition(profileId).map { list ->
            list.firstOrNull()?.channel?.toChannel()
        }

    fun getSimilarContent(channel: Channel, limit: Int = 15): Flow<List<Channel>> =
        db.channelDao().getSimilarContent(
            excludeId = channel.streamId ?: "",
            categoryId = channel.categoryId,
            groupName = channel.group,
            limit = limit
        ).map { entities -> entities.map { it.toChannel() } }
}
