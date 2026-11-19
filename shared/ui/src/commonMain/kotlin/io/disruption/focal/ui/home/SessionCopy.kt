package io.disruption.focal.ui.home

import io.disruption.focal.core.model.Strictness
import kotlin.time.Duration

/** "1 h 05 min left", "4 min 59 s left", "Ending…". */
internal fun formatTimeLeft(left: Duration): String {
    if (left <= Duration.ZERO) return "Ending…"
    val totalSeconds = left.inWholeSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "$hours h ${minutes.toString().padStart(2, '0')} min left"
        minutes > 0 -> "$minutes min ${seconds.toString().padStart(2, '0')} s left"
        else -> "$seconds s left"
    }
}

internal fun Strictness.label(): String = name

internal fun Strictness.description(): String = when (this) {
    Strictness.Normal -> "End any time."
    Strictness.Delayed -> "Ending early takes a 30-second wait. Up to three 5-minute Breaks."
    Strictness.Locked -> "Can't be ended until it runs out."
}

/** "45 min", "2 h", "1 h 30 min". */
internal fun formatMinutes(duration: Duration): String {
    val minutes = duration.inWholeMinutes
    return when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0L -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
