package io.disruption.focal.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import io.disruption.focal.core.repository.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import kotlin.time.Instant

/** [AppPreferences] in DataStore (ADR 0009). */
class DataStoreFocalPreferences(private val store: DataStore<Preferences>) : AppPreferences {
    override val onboardingCompleted: Flow<Boolean> = store.data.map { it[ONBOARDING_COMPLETED] ?: false }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        store.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    override val brokenWarningsSeenUntil: Flow<Instant?> =
        store.data.map { prefs -> prefs[BROKEN_WARNINGS_SEEN_UNTIL]?.let(Instant::fromEpochMilliseconds) }

    override suspend fun setBrokenWarningsSeenUntil(seenUntil: Instant) {
        store.edit { it[BROKEN_WARNINGS_SEEN_UNTIL] = seenUntil.toEpochMilliseconds() }
    }

    companion object {
        const val FILE_NAME = "focal.preferences_pb"
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val BROKEN_WARNINGS_SEEN_UNTIL = longPreferencesKey("broken_warnings_seen_until")

        fun createDataStore(path: String): DataStore<Preferences> =
            PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })
    }
}
