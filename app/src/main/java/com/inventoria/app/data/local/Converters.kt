package com.inventoria.app.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.inventoria.app.data.model.InventoryCollectionType
import com.inventoria.app.data.model.TaskKind
import java.util.*

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromDate(date: Date?): Long? = date?.time

    @TypeConverter
    fun toDate(timestamp: Long?): Date? = timestamp?.let { Date(it) }

    @TypeConverter
    fun fromMap(map: Map<String, String>?): String? = map?.let { gson.toJson(it) }

    @TypeConverter
    fun toMap(value: String?): Map<String, String> {
        if (value == null) return emptyMap()
        val mapType = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(value, mapType) ?: emptyMap()
    }

    @TypeConverter
    fun fromList(list: List<String>?): String? = list?.let { gson.toJson(it) }

    @TypeConverter
    fun toList(value: String?): List<String> {
        if (value == null) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromTaskKind(kind: TaskKind): String = kind.name

    @TypeConverter
    fun toTaskKind(value: String): TaskKind {
        return try {
            TaskKind.valueOf(value)
        } catch (e: IllegalArgumentException) {
            // Mapping for legacy names found in decompiled code
            when (value) {
                "SMALL_PRODUCTIVE" -> TaskKind.LAVENDER
                "BIG_WASTE" -> TaskKind.TOMATO
                "BIG_PRODUCTIVE" -> TaskKind.PEACOCK
                "NEUTRAL_WAITING" -> TaskKind.GRAPHITE
                "SMALL_WASTE" -> TaskKind.TANGERINE
                "FREE_TIME" -> TaskKind.GRAPE
                else -> TaskKind.GRAPHITE
            }
        }
    }

    @TypeConverter
    fun fromCollectionType(type: InventoryCollectionType): String = type.name

    @TypeConverter
    fun toCollectionType(value: String): InventoryCollectionType {
        return try {
            InventoryCollectionType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            InventoryCollectionType.OTHER
        }
    }
}
