package io.disruption.focal.ui.blocklists

import androidx.lifecycle.viewModelScope
import io.disruption.focal.core.blocklist.BlockListResult
import io.disruption.focal.core.blocklist.BlockListService
import io.disruption.focal.ui.mvi.MviViewModel
import kotlinx.coroutines.launch

data class BlockListRow(val id: String, val name: String, val summary: String)

data class BlockListsState(
    val loading: Boolean = true,
    val lists: List<BlockListRow> = emptyList(),
    /** The "New Block List" dialog, when open. */
    val newList: NewListDialog? = null,
)

data class NewListDialog(val name: String = "", val error: String? = null)

sealed interface BlockListsIntent {
    data object NewClicked : BlockListsIntent
    data class NewNameChanged(val name: String) : BlockListsIntent
    data object CreateConfirmed : BlockListsIntent
    data object CreateDismissed : BlockListsIntent
    data class Open(val id: String) : BlockListsIntent
}

sealed interface BlockListsEffect {
    data class OpenEditor(val id: String) : BlockListsEffect
}

class BlockListsViewModel(
    private val service: BlockListService,
) : MviViewModel<BlockListsState, BlockListsIntent, BlockListsEffect>(BlockListsState()) {

    init {
        viewModelScope.launch {
            service.observeAll().collect { lists ->
                setState {
                    copy(loading = false, lists = lists.map { BlockListRow(it.id.value, it.name, it.blocked.summary()) })
                }
            }
        }
    }

    override fun onIntent(intent: BlockListsIntent) {
        when (intent) {
            BlockListsIntent.NewClicked -> setState { copy(newList = NewListDialog()) }
            is BlockListsIntent.NewNameChanged -> setState { copy(newList = newList?.copy(name = intent.name, error = null)) }
            BlockListsIntent.CreateDismissed -> setState { copy(newList = null) }
            BlockListsIntent.CreateConfirmed -> create()
            is BlockListsIntent.Open -> emit(BlockListsEffect.OpenEditor(intent.id))
        }
    }

    private fun create() {
        val name = currentState.newList?.name ?: return
        viewModelScope.launch {
            when (val result = service.create(name)) {
                is BlockListResult.Saved -> {
                    setState { copy(newList = null) }
                    emit(BlockListsEffect.OpenEditor(result.blockList.id.value))
                }
                else -> setState { copy(newList = newList?.copy(error = "Give the Block List a name")) }
            }
        }
    }
}
