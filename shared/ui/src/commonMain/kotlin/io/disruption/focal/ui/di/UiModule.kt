package io.disruption.focal.ui.di

import io.disruption.focal.ui.blocklists.BlockListEditorViewModel
import io.disruption.focal.ui.blocklists.BlockListsViewModel
import io.disruption.focal.ui.countdown.ExitCountdownViewModel
import io.disruption.focal.ui.edit.EditSessionViewModel
import io.disruption.focal.ui.home.HomeViewModel
import io.disruption.focal.ui.stats.StatsViewModel
import io.disruption.focal.ui.schedules.ScheduleEditorViewModel
import io.disruption.focal.ui.schedules.SchedulesViewModel
import io.disruption.focal.ui.start.StartSessionViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** View models for the shared screens (ADR 0011). */
val uiModule: Module = module {
    viewModel { BlockListsViewModel(get()) }
    viewModel { (id: String) -> BlockListEditorViewModel(id, get(), get()) }
    viewModel { HomeViewModel(get(), get()) }
    viewModel { (id: String) -> ExitCountdownViewModel(id, get(), get()) }
    viewModel { (id: String) -> EditSessionViewModel(id, get(), get(), get()) }
    viewModel { StartSessionViewModel(get(), get(), get(), get()) }
    viewModel { SchedulesViewModel(get()) }
    viewModel { StatsViewModel(get(), get(), get()) }
    viewModel { (id: String) -> ScheduleEditorViewModel(id.ifEmpty { null }, get(), get()) }
}
