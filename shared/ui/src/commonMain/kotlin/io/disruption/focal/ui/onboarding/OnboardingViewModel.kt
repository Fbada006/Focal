package io.disruption.focal.ui.onboarding

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.blocklist.BlockListResult
import io.disruption.focal.core.blocklist.BlockListService
import io.disruption.focal.core.permissions.Permission
import io.disruption.focal.core.permissions.PermissionGateway
import io.disruption.focal.core.repository.AppPreferences
import io.disruption.focal.core.website.SuggestedWebsitesCatalog
import io.disruption.focal.ui.mvi.MviViewModel
import kotlinx.coroutines.launch

sealed interface OnboardingStep {
    data object Intro : OnboardingStep
    data class Grant(val permission: Permission, val granted: Boolean) : OnboardingStep
    data object FirstBlockList : OnboardingStep
}

data class SuggestionChoice(val id: String, val name: String, val selected: Boolean)

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Intro,
    val blockListName: String = "Distractions",
    val suggestions: List<SuggestionChoice> = emptyList(),
    val error: String? = null,
)

sealed interface OnboardingIntent {
    data object Next : OnboardingIntent
    data object GrantClicked : OnboardingIntent

    /** Focal came back to the foreground, perhaps from a settings screen. */
    data object Resumed : OnboardingIntent
    data class BlockListNameChanged(val name: String) : OnboardingIntent
    data class SuggestionToggled(val id: String) : OnboardingIntent
    data object CreateBlockListClicked : OnboardingIntent
}

sealed interface OnboardingEffect {
    /** Onboarding is done: go straight to starting the first Focus Session. */
    data object StartFirstSession : OnboardingEffect
}

/**
 * Explains Focal before asking for anything, then walks through each required permission with its
 * reason, and ends by creating the first Block List and starting the first session (stories 1–5).
 * A permission can be skipped; Home then shows how to fix it and won't start a session (story 10).
 */
class OnboardingViewModel(
    private val permissions: PermissionGateway,
    private val blockLists: BlockListService,
    private val preferences: AppPreferences,
    private val suggested: SuggestedWebsitesCatalog,
) : MviViewModel<OnboardingState, OnboardingIntent, OnboardingEffect>(
    OnboardingState(suggestions = suggested.services.map { SuggestionChoice(it.id, it.name, it.id in DEFAULT_PICKS) }),
) {
    private val steps: List<Permission> = permissions.requiredForSessions

    override fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.Next -> next()
            OnboardingIntent.GrantClicked -> (currentState.step as? OnboardingStep.Grant)?.let { permissions.request(it.permission) }
            OnboardingIntent.Resumed -> refresh()
            is OnboardingIntent.BlockListNameChanged -> setState { copy(blockListName = intent.name, error = null) }
            is OnboardingIntent.SuggestionToggled -> setState {
                copy(suggestions = suggestions.map { if (it.id == intent.id) it.copy(selected = !it.selected) else it })
            }
            OnboardingIntent.CreateBlockListClicked -> createBlockList()
        }
    }

    private fun next() {
        val nextPermission = when (val step = currentState.step) {
            OnboardingStep.Intro -> steps.firstOrNull()
            is OnboardingStep.Grant -> steps.getOrNull(steps.indexOf(step.permission) + 1)
            OnboardingStep.FirstBlockList -> return
        }
        viewModelScope.launch {
            val step = nextPermission?.let { OnboardingStep.Grant(it, permissions.isGranted(it)) }
                ?: OnboardingStep.FirstBlockList
            setState { copy(step = step) }
        }
    }

    private fun refresh() {
        val step = currentState.step as? OnboardingStep.Grant ?: return
        viewModelScope.launch {
            val granted = permissions.isGranted(step.permission)
            setState { copy(step = (this.step as? OnboardingStep.Grant)?.copy(granted = granted) ?: this.step) }
        }
    }

    private fun createBlockList() {
        val state = currentState
        viewModelScope.launch {
            val created = blockLists.create(state.blockListName)
            if (created !is BlockListResult.Saved) {
                setState { copy(error = "Give the Block List a name") }
                return@launch
            }
            val domains = suggested.services
                .filter { service -> state.suggestions.any { it.id == service.id && it.selected } }
                .flatMap { it.domains }
                .toSet()
            if (domains.isNotEmpty()) blockLists.addDomains(created.blockList.id, domains)
            preferences.setOnboardingCompleted(true)
            emit(OnboardingEffect.StartFirstSession)
        }
    }

    companion object {
        private val DEFAULT_PICKS = setOf("instagram", "tiktok", "youtube", "x", "reddit")
    }
}
