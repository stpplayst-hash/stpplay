package com.stpplay.android.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stpplay.android.data.Channel
import com.stpplay.android.data.ContentType

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["type"]),
        Index(value = ["group"]),
        Index(value = ["categoryId"])
    ]
)
data class ChannelEntity(
    @PrimaryKey val streamId: String,
    val name: String,
    val url: String,
    val logo: String?,
    val group: String?,
    val type: ContentType,
    val description: String?,
    val rating: String?,
    val year: String?,
    val director: String?,
    val cast: String?,
    val categoryId: String?,
    val epgChannelId: String? = null,
    val genre: String? = null,
    val duration: String? = null,
    val language: String? = null,
    val trailerUrl: String? = null,
    val parentId: String? = null
) {
    fun toChannel() = Channel(
        name = name,
        url = url,
        logo = logo,
        group = group,
        type = type,
        description = description,
        rating = rating,
        year = year,
        director = director,
        cast = cast,
        streamId = streamId,
        categoryId = categoryId,
        epgChannelId = epgChannelId,
        genre = genre,
        duration = duration,
        language = language,
        trailerUrl = trailerUrl,
        parentId = parentId
    )

    companion object {
        fun fromChannel(channel: Channel) = ChannelEntity(
            streamId = channel.streamId ?: channel.url,
            name = channel.name,
            url = channel.url,
            logo = channel.logo,
            group = channel.group,
            type = channel.type,
            description = channel.description,
            rating = channel.rating,
            year = channel.year,
            director = channel.director,
            cast = channel.cast,
            categoryId = channel.categoryId,
            epgChannelId = channel.epgChannelId,
            genre = channel.genre,
            duration = channel.duration,
            language = channel.language,
            trailerUrl = channel.trailerUrl,
            parentId = channel.parentId
        )
    }
}

@Fts4
@Entity(tableName = "channels_fts")
data class ChannelFtsEntity(
    val streamId: String,
    val name: String
)

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String, // UUID
    val name: String,
    val iconResId: Int,
    val isKids: Boolean = false,
    val pin: String? = null
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["streamId", "profileId"]
)
data class FavoriteEntity(
    val streamId: String,
    val profileId: String
)

@Entity(
    tableName = "playback_positions",
    primaryKeys = ["streamId", "profileId"]
)
data class PlaybackPositionEntity(
    val streamId: String,
    val profileId: String,
    val position: Long,
    val parentId: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "epg_programs",
    primaryKeys = ["streamId", "startTimestamp"]
)
data class EpgProgramEntity(
    val streamId: String,
    val epgChannelId: String? = null,
    val title: String,
    val startTimestamp: Long,
    val stopTimestamp: Long,
    val description: String? = null
)

@Entity(
    tableName = "reminders",
    primaryKeys = ["streamId", "startTimestamp"]
)
data class ReminderEntity(
    val streamId: String,
    val title: String,
    val startTimestamp: Long,
    val profileId: String
)

@Entity(
    tableName = "search_history",
    primaryKeys = ["query", "profileId"]
)
data class SearchHistoryEntity(
    val query: String,
    val profileId: String,
    val timestamp: Long = System.currentTimeMillis()
)
