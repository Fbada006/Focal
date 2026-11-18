package io.disruption.focal.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    companion object {
        const val FILE_NAME = "focal.preferences_pb"
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        fun createDataStore(path: String): DataStore<Preferences> =
            PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })
    }
}
