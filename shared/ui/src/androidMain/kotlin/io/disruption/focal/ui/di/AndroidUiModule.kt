package io.disruption.focal.ui.di

import io.disruption.focal.core.apps.InstalledAppsSource
import io.disruption.focal.ui.apps.AndroidInstalledApps
import io.disruption.focal.ui.apps.AppPickerViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Android-only screens and their data sources. */
val androidUiModule: Module = module {
    single<InstalledAppsSource> { AndroidInstalledApps(get()) }
    viewModel { (id: String) -> AppPickerViewModel(id, get(), get()) }
}
