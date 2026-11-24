package com.focusforge.app.data.local

import androidx.room.TypeConverter
import com.focusforge.app.data.model.SessionType

/**
 * Type converters for Room to safely persist enum values as human-readable strings.
 */
class Converters {
    @TypeConverter
    fun fromSessionType(value: SessionType): String = value.name

    @TypeConverter
    fun toSessionType(value: String): SessionType = runCatching {
        SessionType.valueOf(value)
    }.getOrDefault(SessionType.DEEP)
}
