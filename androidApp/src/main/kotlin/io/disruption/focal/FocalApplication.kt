package io.disruption.focal

import android.app.Application
import io.disruption.focal.ui.di.androidUiModule
import org.koin.android.ext.koin.androidContext

class FocalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            blockingEngine = PendingBlockingEngine(),
            platformModules = listOf(androidUiModule),
        ) {
            androidContext(this@FocalApplication)
        }
    }
}
