package io.disruption.focal.core.ios

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Encoding and decoding of the snapshot and the extension event log. */
object IosContract {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
        classDiscriminator = "type"
    }

    fun encodeSnapshot(snapshot: IosSnapshot): String = json.encodeToString(IosSnapshot.serializer(), snapshot)

    /** Null if the text isn't a snapshot this version understands. */
    fun decodeSnapshot(text: String): IosSnapshot? = runCatching {
        val snapshot = json.decodeFromString(IosSnapshot.serializer(), text)
        snapshot.takeIf { it.version <= IosSnapshot.CURRENT_VERSION }
    }.getOrNull()

    fun encodeEvents(log: ExtensionEventLog): String = json.encodeToString(ExtensionEventLog.serializer(), log)

    /**
     * Decode the events this version understands, skipping unknown types and malformed entries
     * rather than failing the whole log.
     */
    fun decodeEvents(text: String): List<ExtensionEvent> {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
        val version = runCatching { root["version"]?.jsonPrimitive?.int }.getOrNull() ?: return emptyList()
        if (version > ExtensionEventLog.CURRENT_VERSION) return emptyList()
        val events = runCatching { root["events"]?.jsonArray }.getOrNull() ?: JsonArray(emptyList())
        return events.mapNotNull { element ->
            if (element !is JsonObject) return@mapNotNull null
            try {
                json.decodeFromJsonElement(ExtensionEvent.serializer(), element)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
