package io.disruption.focal.core.ios

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
     * The log [current] without the events the app has already ingested from [ingested] (an
     * earlier read of the same file), so the log only holds what is still pending and stays small.
     * Only events this version understood are dropped: unknown types and malformed entries are kept
     * as they are, as are events appended after [ingested] was read. Returns null when there is
     * nothing to drop, or [current] isn't a log this version may rewrite.
     */
    fun withoutIngested(current: String, ingested: String): String? {
        val ingestedIds = decodeEvents(ingested).mapTo(HashSet()) { it.id }
        if (ingestedIds.isEmpty()) return null
        val root = runCatching { json.parseToJsonElement(current).jsonObject }.getOrNull() ?: return null
        val version = runCatching { root["version"]?.jsonPrimitive?.int }.getOrNull() ?: return null
        if (version > ExtensionEventLog.CURRENT_VERSION) return null
        val events = runCatching { root["events"]?.jsonArray }.getOrNull() ?: return null
        val kept = events.filterNot { element -> element.understoodId() in ingestedIds }
        if (kept.size == events.size) return null
        return json.encodeToString(JsonObject.serializer(), JsonObject(root + ("events" to JsonArray(kept))))
    }

    /** The id of an event this version understands, or null for unknown or malformed entries. */
    private fun JsonElement.understoodId(): String? {
        if (this !is JsonObject) return null
        val event = try {
            json.decodeFromJsonElement(ExtensionEvent.serializer(), this)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return event.id
    }

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
