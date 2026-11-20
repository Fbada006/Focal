package io.disruption.focal.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.disruption.focal.core.model.FocusSessionId
import io.disruption.focal.core.repository.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

/** [AppPreferences] in DataStore (ADR 0009). */
class DataStoreFocalPreferences(private val store: DataStore<Preferences>) : AppPreferences {
    override val onboardingCompleted: Flow<Boolean> = store.data.map { it[ONBOARDING_COMPLETED] ?: false }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        store.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    override val acknowledgedBrokenSessions: Flow<Set<FocusSessionId>> =
        store.data.map { prefs -> prefs[ACKNOWLEDGED_BROKEN_SESSIONS].orEmpty().mapTo(HashSet(), ::FocusSessionId) }

    override suspend fun acknowledgeBrokenSessions(ids: Set<FocusSessionId>) {
        store.edit { it[ACKNOWLEDGED_BROKEN_SESSIONS] = it[ACKNOWLEDGED_BROKEN_SESSIONS].orEmpty() + ids.map { id -> id.value } }
    }

    companion object {
        const val FILE_NAME = "focal.preferences_pb"
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val ACKNOWLEDGED_BROKEN_SESSIONS = stringSetPreferencesKey("acknowledged_broken_sessions")

        fun createDataStore(path: String): DataStore<Preferences> =
            PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })
    }
}
