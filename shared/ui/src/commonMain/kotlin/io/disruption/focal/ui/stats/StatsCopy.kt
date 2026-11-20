package io.disruption.focal.ui.stats

import io.disruption.focal.ui.home.formatMinutes
import kotlin.time.Duration

/**
 * How focus time and the Streak read wherever stats are shown: the Stats screen and the Android
 * home-screen widget (stories 66, 68, 75). Focus time is shown in whole minutes, rounded down.
 */
object StatsCopy {
    /** "0 min", "45 min", "1 h 15 min". */
    fun focusTime(duration: Duration): String = formatMinutes(duration)

    /** "0 days", "1 day", "12 days". */
    fun streak(days: Int): String = if (days == 1) "1 day" else "$days days"
}
