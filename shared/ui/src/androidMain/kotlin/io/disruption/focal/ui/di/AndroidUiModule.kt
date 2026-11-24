package io.disruption.focal.ui.di

import io.disruption.focal.core.apps.AppUsageSource
import io.disruption.focal.core.apps.InstalledAppsSource
import io.disruption.focal.ui.apps.AndroidAppUsage
import io.disruption.focal.ui.apps.AndroidInstalledApps
import io.disruption.focal.ui.apps.AppPickerViewModel
import io.disruption.focal.ui.help.AndroidDeviceHelp
import io.disruption.focal.ui.help.DeviceHelp
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Android-only screens and their data sources. */
val androidUiModule: Module = module {
    single<InstalledAppsSource> { AndroidInstalledApps(get()) }
    single<AppUsageSource> { AndroidAppUsage(get()) }
    single<DeviceHelp> { AndroidDeviceHelp(get()) }
    viewModel { (id: String) -> AppPickerViewModel(id, get(), get(), get(), get(), get()) }
}
