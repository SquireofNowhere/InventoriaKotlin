package com.inventoria.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** A problem the caller can fix: shown to the model as the tool's error text, not a crash. */
class ToolError(message: String) : Exception(message)

/**
 * The zone "a day" means. A todo's deadline is the start of a day on the device that made it, so
 * this has to match the phone; set INVENTORIA_TZ (e.g. Europe/Berlin) if the machine running the
 * server is not in the same zone.
 */
val zone: ZoneId = System.getenv("INVENTORIA_TZ")?.takeIf { it.isNotBlank() }
    ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
    ?: ZoneId.systemDefault()

fun parseDay(text: String): Long = try {
    LocalDate.parse(text.trim()).atStartOfDay(zone).toInstant().toEpochMilli()
} catch (e: DateTimeException) {
    throw ToolError("'$text' is not a date; use YYYY-MM-DD")
}

fun dayString(startOfDayMillis: Long): String =
    Instant.ofEpochMilli(startOfDayMillis).atZone(zone).toLocalDate().toString()

fun parseMinuteOfDay(text: String): Int {
    val parts = text.trim().split(":")
    val h = parts.getOrNull(0)?.toIntOrNull()
    val m = parts.getOrNull(1)?.toIntOrNull()
    if (parts.size != 2 || h == null || m == null || h !in 0..23 || m !in 0..59) {
        throw ToolError("'$text' is not a time; use HH:MM (24-hour)")
    }
    return h * 60 + m
}

fun minuteString(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

/** Local wall-clock text for an epoch-millis timestamp, e.g. 2026-10-03T14:05:00. */
fun isoString(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().withNano(0).toString()

/** Accepts 2026-10-03T14:05[:00] (read in [zone]), a full offset timestamp, or epoch millis. */
fun parseInstant(text: String): Long {
    val t = text.trim()
    t.toLongOrNull()?.let { return it }
    runCatching { return OffsetDateTime.parse(t).toInstant().toEpochMilli() }
    runCatching { return LocalDateTime.parse(t).atZone(zone).toInstant().toEpochMilli() }
    runCatching { return LocalDate.parse(t).atStartOfDay(zone).toInstant().toEpochMilli() }
    throw ToolError("'$text' is not a timestamp; use 2026-10-03T14:05 (local time) or epoch milliseconds")
}

/** Typed access to a tool call's arguments. Absent and explicit null are told apart on purpose. */
class Args(val json: JsonObject) {
    fun has(key: String): Boolean = json.containsKey(key)

    /** True when the key was sent as an explicit null, which update tools read as "clear this". */
    fun isNull(key: String): Boolean = json[key] is JsonNull

    private fun prim(key: String): JsonPrimitive? = (json[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

    fun str(key: String): String? = prim(key)?.contentOrNull

    fun reqStr(key: String): String =
        str(key)?.takeIf { it.isNotBlank() } ?: throw ToolError("'$key' is required")

    fun long(key: String): Long? {
        val p = prim(key) ?: return null
        return p.longOrNull ?: p.doubleOrNull?.takeIf { it % 1.0 == 0.0 }?.toLong()
            ?: throw ToolError("'$key' must be a whole number")
    }

    fun reqLong(key: String): Long = long(key) ?: throw ToolError("'$key' is required")

    fun int(key: String): Int? = long(key)?.let {
        if (it < Int.MIN_VALUE || it > Int.MAX_VALUE) throw ToolError("'$key' is out of range")
        it.toInt()
    }

    fun double(key: String): Double? {
        val p = prim(key) ?: return null
        return p.doubleOrNull ?: throw ToolError("'$key' must be a number")
    }

    fun bool(key: String): Boolean? {
        val p = prim(key) ?: return null
        return p.booleanOrNull ?: throw ToolError("'$key' must be true or false")
    }

    fun strList(key: String): List<String>? {
        val el = json[key] ?: return null
        if (el is JsonNull) return null
        val arr = el as? JsonArray ?: throw ToolError("'$key' must be an array")
        return arr.map { (it as? JsonPrimitive)?.contentOrNull ?: throw ToolError("'$key' must hold text") }
    }

    fun longList(key: String): List<Long>? {
        val el = json[key] ?: return null
        if (el is JsonNull) return null
        val arr = el as? JsonArray ?: throw ToolError("'$key' must be an array")
        return arr.map { (it as? JsonPrimitive)?.longOrNull ?: throw ToolError("'$key' must hold whole numbers") }
    }

    /** Case-insensitive enum lookup, listing the valid names when the value is wrong. */
    inline fun <reified E : Enum<E>> enum(key: String): E? {
        val raw = str(key) ?: return null
        return enumValues<E>().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
            ?: throw ToolError("'$key' must be one of ${enumValues<E>().joinToString { it.name }}")
    }
}

// --- JSON Schema helpers: just enough to describe tool inputs without repeating boilerplate. ---

fun prop(type: String, description: String, nullable: Boolean = false, enum: Collection<String>? = null): JsonObject =
    buildJsonObject {
        if (nullable) put("type", JsonArray(listOf(JsonPrimitive(type), JsonPrimitive("null"))))
        else put("type", type)
        put("description", description)
        if (enum != null) put("enum", JsonArray(enum.map { JsonPrimitive(it) }))
    }

fun strP(description: String, nullable: Boolean = false, enum: Collection<String>? = null) =
    prop("string", description, nullable, enum)

fun intP(description: String, nullable: Boolean = false) = prop("integer", description, nullable)
fun numP(description: String, nullable: Boolean = false) = prop("number", description, nullable)
fun boolP(description: String) = prop("boolean", description)

fun arrP(itemType: String, description: String): JsonObject = buildJsonObject {
    put("type", "array")
    put("description", description)
    put("items", buildJsonObject { put("type", itemType) })
}

fun schema(vararg props: Pair<String, JsonObject>, required: List<String> = emptyList()): JsonObject =
    buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { props.forEach { (name, def) -> put(name, def) } })
        if (required.isNotEmpty()) put("required", JsonArray(required.map { JsonPrimitive(it) }))
    }

fun JsonObject.withField(key: String, value: JsonElement): JsonObject = JsonObject(this + (key to value))

fun jsonOf(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is String -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to jsonOf(v) })
    is Iterable<*> -> JsonArray(value.map { jsonOf(it) })
    else -> JsonPrimitive(value.toString())
}

/** Builds a JSON object from key/value pairs, converting plain Kotlin values. */
fun obj(vararg pairs: Pair<String, Any?>): JsonObject =
    JsonObject(pairs.associate { (k, v) -> k to jsonOf(v) })
