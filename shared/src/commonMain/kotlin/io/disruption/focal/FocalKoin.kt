package io.disruption.focal

import io.disruption.focal.core.blocking.BlockingEngine
import io.disruption.focal.core.blocklist.BlockListService
import io.disruption.focal.core.schedule.ScheduleService
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.core.session.SessionLock
import io.disruption.focal.core.time.TimeZoneProvider
import io.disruption.focal.data.di.dataModule
import io.disruption.focal.ui.di.uiModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import kotlin.time.Clock

/** The domain core's wiring. `core` itself stays free of Koin. */
val coreModule: Module = module {
    single<Clock> { Clock.System }
    single { TimeZoneProvider.System }
    single { SessionLock() }
    single { FocusSessionService(get(), get(), get(), get(), lock = get()) }
    single { BlockListService(get(), get(), get(), lock = get()) }
    single { ScheduleService(get(), get(), get(), get()) }
}

/**
 * Start Koin with every shared module plus the app's [blockingEngine]. Android calls this from its
 * `Application`; iOS through [startFocal].
 */
fun initKoin(
    blockingEngine: BlockingEngine,
    platformModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication = startKoin {
    appDeclaration()
    modules(
        module { single<BlockingEngine> { blockingEngine } },
        coreModule,
        dataModule,
        uiModule,
    )
    modules(platformModules)
}
