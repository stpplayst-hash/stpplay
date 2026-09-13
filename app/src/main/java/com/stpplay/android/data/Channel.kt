package com.stpplay.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ContentType {
    LIVE, MOVIE, SERIES, UNKNOWN
}

@Entity(tableName = "channels")
data class Channel(
    @PrimaryKey val url: String, // Usando URL como chave primária pois deve ser única por canal
    val name: String,
    val logo: String? = null,
    val group: String? = null,
    val type: ContentType = ContentType.UNKNOWN,
    val description: String? = null,
    val rating: String? = null,
    val year: String? = null,
    val director: String? = null,
    val cast: String? = null,
    val streamId: String? = null,
    val categoryId: String? = null,
    val epgChannelId: String? = null,
    val genre: String? = null,
    val duration: String? = null,
    val language: String? = null,
    val trailerUrl: String? = null,
    val parentId: String? = null,
    val isFavorite: Boolean = false
) {
    companion object {
        val KIDS_WHITELIST = listOf(
            "Animação", "Infantil", "Kids", "Criança", "Desenho", "Biblic", "Bíblic", "Caminho da Fé", "Gospel"
        )

        fun isKidsContent(channel: Channel): Boolean {
            val group = channel.group ?: ""
            val name = channel.name
            return KIDS_WHITELIST.any { group.contains(it, ignoreCase = true) || name.contains(it, ignoreCase = true) }
        }
    }
}
