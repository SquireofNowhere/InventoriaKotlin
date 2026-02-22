package com.inventoria.app.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
}
