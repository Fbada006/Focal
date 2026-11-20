package io.disruption.focal.core.time

import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Suspends until this clock reads [at] or later. A plain delay measures elapsed time, so if the
 * wall clock moves backward meanwhile it wakes before [at]; this checks again and keeps waiting.
 */
suspend fun Clock.waitUntil(at: Instant) {
    while (true) {
        val left = at - now()
        if (left <= Duration.ZERO) return
        delay(left)
    }
}
