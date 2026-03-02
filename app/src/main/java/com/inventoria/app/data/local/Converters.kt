package com.inventoria.app.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inventoria.app.data.model.TaskKind
import java.util.Date

/**
 * Type converters for Room database to handle complex data types
 */
class Converters {
    private val gson = Gson()
    
    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }
    
    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }
    
    @TypeConverter
    fun fromMap(map: Map<String, String>?): String? {
        return map?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toMap(value: String?): Map<String, String> {
        if (value == null) return emptyMap()
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(value, mapType) ?: emptyMap()
    }
    
    @TypeConverter
    fun fromList(list: List<String>?): String? {
        return list?.let { gson.toJson(it) }
    }
    
    @TypeConverter
    fun toList(value: String?): List<String> {
        if (value == null) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromTaskKind(kind: TaskKind): String {
        return kind.name
    }

    @TypeConverter
    fun toTaskKind(value: String): TaskKind {
        return try {
            TaskKind.valueOf(value)
        } catch (e: IllegalArgumentException) {
            // Fallback mapping for old enum values
            when (value) {
                "BIG_PRODUCTIVE" -> TaskKind.PEACOCK
                "SMALL_PRODUCTIVE" -> TaskKind.LAVENDER
                "NEUTRAL_WAITING" -> TaskKind.GRAPHITE
                "FREE_TIME" -> TaskKind.GRAPE
                "SMALL_WASTE" -> TaskKind.TANGERINE
                "BIG_WASTE" -> TaskKind.TOMATO
                else -> TaskKind.GRAPHITE
            }
        }
    }
}
