package io.disruption.focal.core.ios

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.Schedule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * What the iOS Screen Time extensions need, written by the app to App Group storage (ADR 0008).
 *
 * The extensions are Swift-only and make no domain decisions: they apply what this says and append
 * [ExtensionEvent]s describing what they did. Keep it small and dumb. Any change to its shape bumps
 * [CURRENT_VERSION] and updates `iosApp/Fixtures/snapshot-v<N>.json` for the Swift tests.
 */
@Serializable
data class IosSnapshot(
    val version: Int = CURRENT_VERSION,
    @Serializable(IsoInstantSerializer::class)
    val generatedAt: Instant,
    val blockLists: List<SnapshotBlockList>,
    val schedules: List<SnapshotSchedule>,
    val runningSessions: List<SnapshotSession>,
) {
    companion object {
        /** 2 added [SnapshotSession.scheduleId] (T31). */
        const val CURRENT_VERSION = 2

        fun from(
            generatedAt: Instant,
            blockLists: List<BlockList>,
            schedules: List<Schedule>,
            runningSessions: List<FocusSession>,
        ): IosSnapshot = IosSnapshot(
            generatedAt = generatedAt,
            blockLists = blockLists.sortedBy { it.id.value }.map { list ->
                SnapshotBlockList(
                    id = list.id.value,
                    name = list.name,
                    apps = list.blocked.apps.sorted(),
                    appCategories = list.blocked.appCategories.sorted(),
                    domains = list.blocked.domains.sorted(),
                )
            },
            schedules = schedules.filter { it.enabled }.sortedBy { it.id.value }.map { schedule ->
                SnapshotSchedule(
                    id = schedule.id.value,
                    name = schedule.name,
                    days = schedule.days.sortedBy { it.ordinal }.map { it.name },
                    start = schedule.start.toString().take(5),
                    end = schedule.end.toString().take(5),
                    blockListId = schedule.blockListId.value,
                    strictness = schedule.strictness.name,
                )
            },
            runningSessions = runningSessions.filter { it.isRunning }.sortedBy { it.id.value }.map { session ->
                SnapshotSession(
                    id = session.id.value,
                    name = session.name,
                    strictness = session.strictness.name,
                    scheduleId = session.scheduleId?.value,
                    startedAt = session.startedAt,
                    plannedEnd = session.plannedEnd,
                    apps = session.blocked.apps.sorted(),
                    appCategories = session.blocked.appCategories.sorted(),
                    domains = session.blocked.domains.sorted(),
                    breakEndsAt = session.breaks.lastOrNull()?.endsAt?.takeIf { it > generatedAt },
                )
            },
        )
    }
}

@Serializable
data class SnapshotBlockList(
    val id: String,
    val name: String,
    /** Serialised `ApplicationToken`s from Apple's picker. */
    val apps: List<String>,
    /** Serialised `ActivityCategoryToken`s. */
    val appCategories: List<String>,
    val domains: List<String>,
)

/** An enabled Schedule. Disabled ones are left out, so the extensions never start them. */
@Serializable
data class SnapshotSchedule(
    val id: String,
    val name: String,
    /** `MONDAY` … `SUNDAY`: the days the window starts on. */
    val days: List<String>,
    /** `HH:mm`, local time. An end at or before the start means overnight. */
    val start: String,
    val end: String,
    val blockListId: String,
    /** `Normal`, `Delayed` or `Locked`. */
    val strictness: String,
)

/**
 * A running Focus Session and its own copy of what it blocks. Its id is the name of its
 * `ManagedSettingsStore`.
 */
@Serializable
data class SnapshotSession(
    val id: String,
    val name: String,
    val strictness: String,
    /**
     * The Schedule that started it, if any. The monitor extension doesn't start a second session
     * for a Schedule window the app already started (T31).
     */
    val scheduleId: String? = null,
    @Serializable(IsoInstantSerializer::class)
    val startedAt: Instant,
    @Serializable(IsoInstantSerializer::class)
    val plannedEnd: Instant,
    val apps: List<String>,
    val appCategories: List<String>,
    val domains: List<String>,
    /** Set while the session is on a Break: when the extension must re-apply its store. */
    @SerialName("breakEndsAt")
    @Serializable(NullableIsoInstantSerializer::class)
    val breakEndsAt: Instant? = null,
)
