package io.disruption.focal.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Small collections are stored as JSON text; they are always read and written whole. */
class Converters {
    private val json = Json
    private val stringSet = SetSerializer(String.serializer())
    private val breaks = ListSerializer(BreakRecord.serializer())

    @TypeConverter
    fun fromStringSet(value: Set<String>): String = json.encodeToString(stringSet, value)

    @TypeConverter
    fun toStringSet(value: String): Set<String> = json.decodeFromString(stringSet, value)

    @TypeConverter
    fun fromBreaks(value: List<BreakRecord>): String = json.encodeToString(breaks, value)

    @TypeConverter
    fun toBreaks(value: String): List<BreakRecord> = json.decodeFromString(breaks, value)
}
