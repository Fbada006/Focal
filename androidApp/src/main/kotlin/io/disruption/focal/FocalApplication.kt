package io.disruption.focal

import android.app.Application
import org.koin.android.ext.koin.androidContext

class FocalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(blockingEngine = PendingBlockingEngine()) {
            androidContext(this@FocalApplication)
        }
    }
}
