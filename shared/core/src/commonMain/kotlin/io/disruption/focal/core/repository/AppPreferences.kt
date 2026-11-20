package io.disruption.focal.core.repository

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** Small app preferences (ADR 0009: DataStore, not the database). */
interface AppPreferences {
    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)

    /**
     * The user has dismissed the warning for every session that became Broken at or before this
     * (story 41). Null until the first dismissal.
     */
    val brokenWarningsSeenUntil: Flow<Instant?>
    suspend fun setBrokenWarningsSeenUntil(seenUntil: Instant)
}
