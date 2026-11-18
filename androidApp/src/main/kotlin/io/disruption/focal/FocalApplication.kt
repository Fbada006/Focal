package io.disruption.focal

import android.app.Application
import io.disruption.focal.ui.di.androidUiModule
import io.disruption.focal.ui.permissions.AndroidPermissionGateway
import org.koin.android.ext.koin.androidContext

class FocalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            blockingEngine = PendingBlockingEngine(),
            permissionGateway = AndroidPermissionGateway(this),
            platformModules = listOf(androidUiModule),
        ) {
            androidContext(this@FocalApplication)
        }
    }
}
