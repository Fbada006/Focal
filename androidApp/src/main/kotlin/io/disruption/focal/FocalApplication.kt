package io.disruption.focal

import android.app.Application
import io.disruption.focal.blocking.AndroidBlockingEngine
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.ui.di.androidUiModule
import io.disruption.focal.ui.permissions.AndroidPermissionGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin

class FocalApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var koin: Koin

    override fun onCreate() {
        super.onCreate()
        val engine = AndroidBlockingEngine(this, scope)
        koin = initKoin(
            blockingEngine = engine,
            permissionGateway = AndroidPermissionGateway(this),
            platformModules = listOf(androidUiModule),
        ) {
            androidContext(this@FocalApplication)
        }.koin
        restoreSessions()
    }

    /**
     * Complete sessions that ran out while Focal wasn't running and re-apply the rest (T13). Called
     * on every process start, including one started by the boot receiver.
     */
    fun restoreSessions() {
        if (!::koin.isInitialized) return
        scope.launch {
            runCatching { koin.get<FocusSessionService>().restore() }
        }
    }
}
