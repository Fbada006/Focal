package io.disruption.focal.data.db

import io.disruption.focal.core.model.BlockList
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.core.model.BlockedSet
import io.disruption.focal.core.model.Break
import io.disruption.focal.core.model.FocusSession
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.model.Outcome
import io.disruption.focal.core.model.ScheduleId
import io.disruption.focal.core.model.Strictness
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
