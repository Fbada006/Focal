package io.disruption.focal.core.repository

import kotlinx.coroutines.flow.Flow

/** Small app preferences (ADR 0009: DataStore, not the database). */
interface AppPreferences {
    val onboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)
}
