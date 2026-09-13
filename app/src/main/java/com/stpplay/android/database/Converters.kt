package com.stpplay.android.database

import androidx.room.TypeConverter
import com.stpplay.android.data.ContentType

class Converters {
    @TypeConverter
    fun fromContentType(value: ContentType): String {
        return value.name
    }

    @TypeConverter
    fun toContentType(value: String): ContentType {
        return try {
            ContentType.valueOf(value)
        } catch (e: Exception) {
            ContentType.UNKNOWN
        }
    }
}
