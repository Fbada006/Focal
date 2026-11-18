package io.disruption.focal.data.db

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.model.Break
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.Schedule
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

internal fun BlockList.toEntity() = BlockListEntity(
    id = id.value,
    name = name,
    apps = blocked.apps,
    appCategories = blocked.appCategories,
    domains = blocked.domains,
)

internal fun BlockListEntity.toModel() = BlockList(
    id = BlockListId(id),
    name = name,
    blocked = BlockedSet(apps, appCategories, domains),
)

internal fun FocusSession.toEntity() = FocusSessionEntity(
    id = id.value,
    blockListId = blockListId.value,
    name = name,
    strictness = strictness.name,
    startedAt = startedAt.toEpochMilliseconds(),
    plannedEnd = plannedEnd.toEpochMilliseconds(),
    apps = blocked.apps,
    appCategories = blocked.appCategories,
    domains = blocked.domains,
    scheduleId = scheduleId?.value,
    breaks = breaks.map { BreakRecord(it.startedAt.toEpochMilliseconds(), it.endsAt.toEpochMilliseconds()) },
    endedAt = endedAt?.toEpochMilliseconds(),
    outcome = outcome?.name,
)

internal fun FocusSessionEntity.toModel() = FocusSession(
    id = FocusSessionId(id),
    blockListId = BlockListId(blockListId),
    name = name,
    strictness = Strictness.valueOf(strictness),
    startedAt = Instant.fromEpochMilliseconds(startedAt),
    plannedEnd = Instant.fromEpochMilliseconds(plannedEnd),
    blocked = BlockedSet(apps, appCategories, domains),
    scheduleId = scheduleId?.let(::ScheduleId),
    breaks = breaks.map { Break(Instant.fromEpochMilliseconds(it.startedAt), Instant.fromEpochMilliseconds(it.endsAt)) },
    endedAt = endedAt?.let(Instant::fromEpochMilliseconds),
    outcome = outcome?.let(Outcome::valueOf),
)

internal fun Schedule.toEntity() = ScheduleEntity(
    id = id.value,
    name = name,
    days = days.map { it.name }.toSet(),
    start = start.toString().take(5),
    end = end.toString().take(5),
    blockListId = blockListId.value,
    strictness = strictness.name,
    enabled = enabled,
)

internal fun ScheduleEntity.toModel() = Schedule(
    id = ScheduleId(id),
    name = name,
    days = days.map(DayOfWeek::valueOf).toSet(),
    start = LocalTime.parse(start),
    end = LocalTime.parse(end),
    blockListId = BlockListId(blockListId),
    strictness = Strictness.valueOf(strictness),
    enabled = enabled,
)
