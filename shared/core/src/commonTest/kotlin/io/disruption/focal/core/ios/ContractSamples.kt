package io.disruption.focal.core.ios

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.model.Break
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Schedule
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The data behind the v1 fixtures in `iosApp/Fixtures`. Change both together. */
object ContractSamples {
    val now: Instant = Instant.parse("2026-01-05T09:10:00Z")
    private val start = Instant.parse("2026-01-05T09:00:00Z")

    val social = BlockList(
        BlockListId("bl-social"),
        "Social",
        BlockedSet(apps = setOf("app-token-instagram", "app-token-tiktok"), appCategories = setOf("cat-token-social"), domains = setOf("reddit.com", "x.com")),
    )
    val workHours = Schedule(
        id = ScheduleId("sch-work"),
        name = "Work hours",
        days = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY),
        start = LocalTime(9, 0),
        end = LocalTime(17, 30),
        blockListId = social.id,
        strictness = Strictness.Delayed,
    )
    val windDown = Schedule(
        id = ScheduleId("sch-wind-down"),
        name = "Wind down",
        days = setOf(DayOfWeek.SUNDAY),
        start = LocalTime(22, 0),
        end = LocalTime(7, 0),
        blockListId = social.id,
        strictness = Strictness.Locked,
    )
    val paused = workHours.copy(id = ScheduleId("sch-off"), name = "Off", enabled = false)

    val running = FocusSession(
        id = FocusSessionId("fs-1"),
        blockListId = social.id,
        name = "Work hours",
        strictness = Strictness.Delayed,
        startedAt = start,
        plannedEnd = start + 8.5.hours,
        blocked = social.blocked,
        scheduleId = workHours.id,
        breaks = listOf(Break(now - 2.minutes, now + 3.minutes)),
    )

    val snapshot = IosSnapshot.from(now, listOf(social), listOf(workHours, windDown, paused), listOf(running))

    val events = ExtensionEventLog(
        events = listOf(
            ExtensionEvent.ScheduleSessionStarted(
                id = "ev-1",
                at = start,
                sessionId = "fs-2",
                scheduleId = workHours.id.value,
                name = "Work hours",
                blockListId = social.id.value,
                strictness = "Delayed",
                plannedEnd = start + 8.5.hours,
            ),
            ExtensionEvent.BreakEnded(id = "ev-2", at = start + 1.hours, sessionId = "fs-2"),
            ExtensionEvent.ScheduleSessionEnded(id = "ev-3", at = start + 8.5.hours, sessionId = "fs-2"),
        ),
    )
}
