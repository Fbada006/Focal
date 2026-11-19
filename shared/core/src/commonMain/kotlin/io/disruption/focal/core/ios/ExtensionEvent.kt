package io.disruption.focal.core.ios

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A record appended by an iOS extension describing something it did (ADR 0008). The app ingests
 * them on launch with `FocusSessionService.ingest`; the shared code decides what they mean.
 *
 * Every event has a unique [id] so ingesting the same log twice is harmless. The JSON uses a
 * `type` discriminator; types this version doesn't know are skipped, so newer extensions can't
 * break an older app.
 */
@Serializable
sealed interface ExtensionEvent {
    val id: String
    val at: Instant

    /** The DeviceActivityMonitor started a Focus Session for a Schedule and applied its store. */
    @Serializable
    @SerialName("scheduleSessionStarted")
    data class ScheduleSessionStarted(
        override val id: String,
        @Serializable(IsoInstantSerializer::class)
        override val at: Instant,
        val sessionId: String,
        val scheduleId: String,
        /** Copied from the snapshot's Schedule, in case it was edited or deleted since. */
        val name: String,
        val blockListId: String,
        val strictness: String,
        @Serializable(IsoInstantSerializer::class)
        val plannedEnd: Instant,
        /**
         * What the extension actually applied, copied from the snapshot's Block List when the
         * session started. The session keeps this copy (ADR 0007), even if the list was edited or
         * deleted before the app ingests the event.
         */
        val apps: List<String>,
        val appCategories: List<String>,
        val domains: List<String>,
    ) : ExtensionEvent

    /** The window of a Schedule-started session ended and the extension cleared its store. */
    @Serializable
    @SerialName("scheduleSessionEnded")
    data class ScheduleSessionEnded(
        override val id: String,
        @Serializable(IsoInstantSerializer::class)
        override val at: Instant,
        val sessionId: String,
    ) : ExtensionEvent

    /**
     * A Break ended and the extension re-applied the session's store at [at]. If that was before
     * the Break's recorded end (DeviceActivity fires on its own schedule), the Break is cut short to
     * match what actually happened.
     */
    @Serializable
    @SerialName("breakEnded")
    data class BreakEnded(
        override val id: String,
        @Serializable(IsoInstantSerializer::class)
        override val at: Instant,
        val sessionId: String,
    ) : ExtensionEvent
}

/** The file the extensions append to: a version and the events in order. */
@Serializable
data class ExtensionEventLog(
    val version: Int = CURRENT_VERSION,
    val events: List<ExtensionEvent> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
