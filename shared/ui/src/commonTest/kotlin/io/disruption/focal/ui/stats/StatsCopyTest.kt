package io.disruption.focal.ui.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class StatsCopyTest {
    @Test
    fun focusTimeIsShownInWholeMinutesRoundedDown() {
        assertEquals("0 min", StatsCopy.focusTime(Duration.ZERO))
        assertEquals("0 min", StatsCopy.focusTime(59.seconds))
        assertEquals("45 min", StatsCopy.focusTime(45.minutes + 59.seconds))
        assertEquals("2 h", StatsCopy.focusTime(2.hours))
        assertEquals("1 h 15 min", StatsCopy.focusTime(75.minutes))
    }

    @Test
    fun streakSaysDayOnlyForOne() {
        assertEquals("0 days", StatsCopy.streak(0))
        assertEquals("1 day", StatsCopy.streak(1))
        assertEquals("12 days", StatsCopy.streak(12))
    }
}
