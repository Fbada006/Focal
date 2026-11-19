package io.disruption.focal.ui.blocklists

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.blocklist.BlockListResult
import io.disruption.focal.core.blocklist.BlockListService
import io.disruption.focal.core.blocklist.DeleteResult
import io.disruption.focal.core.blocklist.DomainEditResult
import io.disruption.focal.core.model.BlockListId
import io.disruption.focal.ui.mvi.MviViewModel
import kotlinx.coroutines.launch

enum class EditorTab { Apps, Websites }

data class BlockListEditorState(
    val loading: Boolean = true,
    val name: String = "",
    val nameError: String? = null,
    val tab: EditorTab = EditorTab.Apps,
    val apps: Set<String> = emptySet(),
    val appCategories: Set<String> = emptySet(),
    val domains: List<String> = emptyList(),
    val domainInput: String = "",
    val domainError: String? = null,
    /** The domain being edited in the input field, or null when adding a new one. */
    val editingDomain: String? = null,
    val confirmingDelete: Boolean = false,
    /** Why the list couldn't be deleted, when a running session uses it. */
    val deleteRefusal: String? = null,
    /** Shown after a removal that running Delayed or Locked sessions defer (ADR 0007). */
    val notice: String? = null,
)

sealed interface BlockListEditorIntent {
    data class NameChanged(val name: String) : BlockListEditorIntent
    data object NameCommitted : BlockListEditorIntent
    data class TabSelected(val tab: EditorTab) : BlockListEditorIntent
    data class DomainInputChanged(val input: String) : BlockListEditorIntent
    /** Add the typed domain, or save it over [BlockListEditorState.editingDomain]. */
    data object DomainSubmitted : BlockListEditorIntent
    data class EditDomain(val domain: String) : BlockListEditorIntent
    data object CancelDomainEdit : BlockListEditorIntent
    data class RemoveDomain(val domain: String) : BlockListEditorIntent
    data object DeleteClicked : BlockListEditorIntent
    data object DeleteConfirmed : BlockListEditorIntent
    data object DeleteDismissed : BlockListEditorIntent
    data object DeleteRefusalDismissed : BlockListEditorIntent
    data object NoticeDismissed : BlockListEditorIntent
}

sealed interface BlockListEditorEffect {
    data object Closed : BlockListEditorEffect
}

class BlockListEditorViewModel(
    blockListId: String,
    private val service: BlockListService,
) : MviViewModel<BlockListEditorState, BlockListEditorIntent, BlockListEditorEffect>(BlockListEditorState()) {
    private val id = BlockListId(blockListId)
    private var deleted = false

    init {
        viewModelScope.launch {
            service.observeAll().collect { lists ->
                val list = lists.firstOrNull { it.id == id }
                if (list == null) {
                    if (!deleted && !currentState.loading) emit(BlockListEditorEffect.Closed)
                    return@collect
                }
                setState {
                    copy(
                        loading = false,
                        name = if (loading) list.name else name,
                        apps = list.blocked.apps,
                        appCategories = list.blocked.appCategories,
                        domains = list.blocked.domains.sorted(),
                    )
                }
            }
        }
    }

    override fun onIntent(intent: BlockListEditorIntent) {
        when (intent) {
            is BlockListEditorIntent.NameChanged -> setState { copy(name = intent.name, nameError = null) }
            BlockListEditorIntent.NameCommitted -> rename()
            is BlockListEditorIntent.TabSelected -> setState { copy(tab = intent.tab) }
            is BlockListEditorIntent.DomainInputChanged -> setState { copy(domainInput = intent.input, domainError = null) }
            BlockListEditorIntent.DomainSubmitted -> submitDomain()
            is BlockListEditorIntent.EditDomain ->
                setState { copy(editingDomain = intent.domain, domainInput = intent.domain, domainError = null) }
            BlockListEditorIntent.CancelDomainEdit ->
                setState { copy(editingDomain = null, domainInput = "", domainError = null) }
            is BlockListEditorIntent.RemoveDomain -> viewModelScope.launch {
                val result = service.removeDomain(id, intent.domain)
                if (result is BlockListResult.Saved) showDeferred(result.removalsDeferredFor)
                if (currentState.editingDomain == intent.domain) {
                    setState { copy(editingDomain = null, domainInput = "") }
                }
            }
            BlockListEditorIntent.DeleteClicked -> setState { copy(confirmingDelete = true) }
            BlockListEditorIntent.DeleteDismissed -> setState { copy(confirmingDelete = false) }
            BlockListEditorIntent.DeleteConfirmed -> delete()
            BlockListEditorIntent.DeleteRefusalDismissed -> setState { copy(deleteRefusal = null) }
            BlockListEditorIntent.NoticeDismissed -> setState { copy(notice = null) }
        }
    }

    private fun showDeferred(sessionNames: List<String>) {
        if (sessionNames.isNotEmpty()) setState { copy(notice = deferredMessage(sessionNames)) }
    }

    private fun rename() {
        viewModelScope.launch {
            when (service.rename(id, currentState.name)) {
                is BlockListResult.Saved -> setState { copy(name = name.trim(), nameError = null) }
                BlockListResult.InvalidName -> setState { copy(nameError = "Give the Block List a name") }
                BlockListResult.NotFound -> Unit
            }
        }
    }

    private fun submitDomain() {
        val state = currentState
        viewModelScope.launch {
            val editing = state.editingDomain
            val result = if (editing == null) {
                service.addDomain(id, state.domainInput)
            } else {
                service.editDomain(id, editing, state.domainInput)
            }
            when (result) {
                is DomainEditResult.Saved -> {
                    setState { copy(domainInput = "", domainError = null, editingDomain = null) }
                    showDeferred(result.removalsDeferredFor)
                }
                is DomainEditResult.Invalid -> setState { copy(domainError = result.reason.message()) }
                is DomainEditResult.AlreadyBlocked -> setState { copy(domainError = "${result.hostname} is already blocked") }
                DomainEditResult.NotFound -> Unit
            }
        }
    }

    private fun delete() {
        viewModelScope.launch {
            setState { copy(confirmingDelete = false) }
            when (val result = service.delete(id)) {
                DeleteResult.Deleted, DeleteResult.NotFound -> {
                    deleted = true
                    emit(BlockListEditorEffect.Closed)
                }
                is DeleteResult.InUse -> setState { copy(deleteRefusal = inUseMessage(result.sessionNames)) }
            }
        }
    }
}
