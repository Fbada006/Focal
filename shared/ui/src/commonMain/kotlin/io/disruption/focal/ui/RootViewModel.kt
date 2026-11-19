package io.disruption.focal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.repository.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** Decides where Focal opens: onboarding the first time, Home afterwards. */
class RootViewModel(preferences: AppPreferences) : ViewModel() {
    /** Null until known. Read once, so finishing onboarding doesn't rebuild navigation. */
    val onboardingCompleted: StateFlow<Boolean?> = flow { emit(preferences.onboardingCompleted.first()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
