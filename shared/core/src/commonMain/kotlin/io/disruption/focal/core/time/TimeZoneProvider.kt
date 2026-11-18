package io.disruption.focal.core.time

import kotlinx.datetime.TimeZone

/** The device's current time zone. Read it each time: the user can travel or change it. */
fun interface TimeZoneProvider {
    fun current(): TimeZone

    companion object {
        val System = TimeZoneProvider { TimeZone.currentSystemDefault() }
    }
}
