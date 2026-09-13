package com.stpplay.android.data

import com.stpplay.android.database.ChannelDao
import com.stpplay.android.database.ChannelEntity
import com.stpplay.android.database.FavoriteDao
import com.stpplay.android.database.FavoriteEntity
import com.stpplay.android.parser.M3uParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ChannelRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao,
    private val profileManager: ProfileManager
) {
    private val KIDS_WHITELIST = listOf(
        "Animação", "Infantil", "Biblicos", "Desenhos", "Caminho da Fé"
    )

    fun getAllChannels(): Flow<List<Channel>> {
        return profileManager.currentProfile.flatMapLatest { profile ->
            val profileId = profile?.id ?: ""
            val isKids = profile?.isKids ?: false
            
            combine(
                channelDao.getAllChannels(),
                favoriteDao.getAllFavorites(profileId)
            ) { entities, favorites ->
                val favoriteIds = favorites.map { it.streamId }.toSet()
                val channels = entities.map { entity ->
                    entity.toChannel().copy(isFavorite = favoriteIds.contains(entity.streamId))
                }
                
                if (isKids) {
                    channels.filter { channel ->
                        val group = channel.group ?: ""
                        KIDS_WHITELIST.any { keyword -> group.contains(keyword, ignoreCase = true) }
                    }
                } else {
                    channels
                }
            }
        }
    }

    suspend fun refreshChannels(url: String) {
        val remoteChannels = M3uParser.loadFromUrl(url)
        if (remoteChannels.isNotEmpty()) {
            channelDao.deleteAll()
            channelDao.insertChannels(remoteChannels.map { ChannelEntity.fromChannel(it) })
        }
    }

    suspend fun toggleFavorite(channel: Channel) {
        val profileId = profileManager.currentProfile.value?.id ?: return
        val streamId = channel.streamId ?: channel.url
        if (favoriteDao.isFavorite(streamId, profileId)) {
            favoriteDao.deleteFavorite(FavoriteEntity(streamId, profileId))
        } else {
            favoriteDao.insertFavorite(FavoriteEntity(streamId, profileId))
        }
    }

    fun getChannelsByType(type: ContentType): Flow<List<Channel>> {
        return profileManager.currentProfile.flatMapLatest { profile ->
            val profileId = profile?.id ?: ""
            val isKids = profile?.isKids ?: false
            
            combine(
                channelDao.getChannelsByType(type),
                favoriteDao.getAllFavorites(profileId)
            ) { entities, favorites ->
                val favoriteIds = favorites.map { it.streamId }.toSet()
                val channels = entities.map { entity ->
                    entity.toChannel().copy(isFavorite = favoriteIds.contains(entity.streamId))
                }

                if (isKids) {
                    channels.filter { channel ->
                        val group = channel.group ?: ""
                        KIDS_WHITELIST.any { keyword -> group.contains(keyword, ignoreCase = true) }
                    }
                } else {
                    channels
                }
            }
        }
    }
}
