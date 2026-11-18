package io.disruption.focal.core.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * A named, recurring rule that starts a Focus Session on [days] from [start] to [end]. An end
 * before (or equal to) the start means the window runs overnight into the next day; [days] are
 * the days the window starts on.
 */
data class Schedule(
    val id: ScheduleId,
    val name: String,
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val blockListId: BlockListId,
    val strictness: Strictness,
    val enabled: Boolean = true,
) {
    val isOvernight: Boolean get() = end <= start
}
