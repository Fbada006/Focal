package io.disruption.focal.ui.schedules

import io.disruption.focal.core.model.Schedule
import io.disruption.focal.core.schedule.ScheduleProblem
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

internal fun DayOfWeek.short(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

internal fun Set<DayOfWeek>.summary(): String = when {
    size == 7 -> "Every day"
    this == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Weekends"
    size == 5 && DayOfWeek.SATURDAY !in this && DayOfWeek.SUNDAY !in this -> "Weekdays"
    else -> sortedBy { it.ordinal }.joinToString(", ") { it.short() }
}

internal fun LocalTime.hhmm(): String = toString().take(5)

internal fun Schedule.summary(): String =
    "${days.summary()} · ${start.hhmm()}–${end.hhmm()}${if (isOvernight) " (overnight)" else ""} · $strictness"

internal fun ScheduleProblem.message(): String = when (this) {
    ScheduleProblem.NoName -> "Give the Schedule a name."
    ScheduleProblem.NoDays -> "Pick at least one day."
    ScheduleProblem.SameStartAndEnd -> "The start and end can't be the same time."
    ScheduleProblem.NoBlockList -> "Pick a Block List."
    ScheduleProblem.EmptyBlockList -> "That Block List is empty. Add apps or websites to it first."
}
