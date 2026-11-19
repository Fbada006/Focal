package io.disruption.focal.ui.di

import io.disruption.focal.ui.help.DeviceHelp
import io.disruption.focal.ui.help.IosDeviceHelp
import org.koin.core.module.Module
import org.koin.dsl.module

/** iOS-only screens and their data sources. */
val iosUiModule: Module = module {
    single<DeviceHelp> { IosDeviceHelp() }
}
