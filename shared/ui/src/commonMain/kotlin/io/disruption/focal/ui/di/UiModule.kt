package io.disruption.focal.ui.di

import io.disruption.focal.ui.blocklists.BlockListEditorViewModel
import io.disruption.focal.ui.blocklists.BlockListsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** View models for the shared screens (ADR 0011). */
val uiModule: Module = module {
    viewModel { BlockListsViewModel(get()) }
    viewModel { (id: String) -> BlockListEditorViewModel(id, get()) }
}
