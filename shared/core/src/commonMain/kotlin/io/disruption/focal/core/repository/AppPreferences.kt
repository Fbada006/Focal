package io.disruption.focal.core.repository

import io.disruption.focal.core.model.FocusSessionId
import kotlinx.coroutines.flow.Flow

/** Small app preferences (ADR 0009: DataStore, not the database). */
interface AppPreferences {
    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)

    /**
     * Broken sessions whose warning the user has dismissed (story 41). Kept by session, not by time,
     * so a device clock set back can't hide a later session's warning.
     */
    val acknowledgedBrokenSessions: Flow<Set<FocusSessionId>>

    /** Add [ids] to [acknowledgedBrokenSessions]. */
    suspend fun acknowledgeBrokenSessions(ids: Set<FocusSessionId>)
}
