package io.disruption.focal.data.db

import androidx.room.TypeConverter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Small collections are stored as JSON text; they are always read and written whole.
 *
 * Fields carry explicit types: KSP doesn't run the serialization plugin, so it can't infer a type
 * from `BreakRecord.serializer()`, and Room then rejects the whole converter class.
 */
class Converters {
    private val json: Json = Json
    private val stringSet: KSerializer<Set<String>> = SetSerializer(String.serializer())
    private val breaks: KSerializer<List<BreakRecord>> = ListSerializer(BreakRecord.serializer())

    @TypeConverter
    fun fromStringSet(value: Set<String>): String = json.encodeToString(stringSet, value)

    @TypeConverter
    fun toStringSet(value: String): Set<String> = json.decodeFromString(stringSet, value)

    @TypeConverter
    fun fromBreaks(value: List<BreakRecord>): String = json.encodeToString(breaks, value)

    @TypeConverter
    fun toBreaks(value: String): List<BreakRecord> = json.decodeFromString(breaks, value)
}
