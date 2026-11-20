package io.disruption.focal

import android.app.Application
import io.disruption.focal.blocking.AndroidBlockingEngine
import io.disruption.focal.blocking.BrokenSessionNotifier
import io.disruption.focal.blocking.ScheduleAlarms
import io.disruption.focal.core.permissions.PermissionGateway
import io.disruption.focal.core.schedule.ScheduleService
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.ui.di.androidUiModule
import io.disruption.focal.ui.permissions.AndroidPermissionGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin

class FocalApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var koin: Koin
    private var restoreJob: Job? = null
    private val alarms by lazy { ScheduleAlarms(this) }

    /**
     * Arming is compute-then-set from several paths (catch-up, Schedule and session changes). Without
     * this, a stale wake-up computed first could be set last and replace a newer, earlier one.
     */
    private val armLock = Mutex()

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
        keepScheduleAlarmArmed()
    }

    /**
     * Complete sessions that ran out while Focal wasn't running and re-apply the rest (T13). Called
     * on every process start, including one started by the boot receiver. A Locked session whose
     * blocking permission was revoked while the process was dead is recorded as Broken first (T22).
     * Then Schedules catch up, so a start missed while the phone was off starts now, and the
     * Schedule alarm is armed again (T30): a reboot clears it.
     */
    fun restoreSessions(): Job? {
        if (!::koin.isInitialized) return null
        // The boot receiver lands right after onCreate; join the restore already in flight.
        restoreJob?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            runCatching { breakIfBlockingRevoked() }
            runCatching { koin.get<FocusSessionService>().restore() }
            runCatching { catchUpAndArm() }
        }.also { restoreJob = it }
    }

    /** Wait for the restore started at process start, without starting another. */
    suspend fun awaitRestore() {
        restoreJob?.join()
    }

    /**
     * The Schedule alarm fired, or the clock, time zone or exact-alarm permission changed: complete
     * sessions that ran out, start Schedule sessions due now, and arm the next alarm (T30).
     */
    fun catchUpSchedules(): Job? {
        if (!::koin.isInitialized) return null
        return scope.launch { runCatching { catchUpAndArm() } }
    }

    private suspend fun catchUpAndArm() {
        val schedules = koin.get<ScheduleService>()
        schedules.catchUp()
        armNextWakeUp(schedules)
    }

    /** Work out the next wake-up and set the alarm as one step, so the latest computation wins. */
    private suspend fun armNextWakeUp(schedules: ScheduleService) = armLock.withLock {
        alarms.arm(schedules.nextWakeUp())
    }

    /** Any change to Schedules, Block Lists or running sessions can move the next wake-up: re-arm on each. */
    private fun keepScheduleAlarmArmed() {
        val schedules = koin.get<ScheduleService>()
        scope.launch {
            schedules.wakeUpChanges().collectLatest { runCatching { armNextWakeUp(schedules) } }
        }
    }

    /**
     * Check whether a permission blocking depends on was revoked, for example when the accessibility
     * service is unbound, and record running Locked sessions as Broken if so (ADR 0002, T22).
     */
    fun checkBlockingPermissions(): Job? {
        if (!::koin.isInitialized) return null
        return scope.launch { runCatching { breakIfBlockingRevoked() } }
    }

    private suspend fun breakIfBlockingRevoked() {
        val missing = koin.get<PermissionGateway>().missing()
        val broken = koin.get<FocusSessionService>().checkPermissions(missing)
        if (broken.isNotEmpty()) BrokenSessionNotifier.notify(this, broken)
    }
}
