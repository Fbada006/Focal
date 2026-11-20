package io.disruption.focal

import io.disruption.focal.core.ios.DeviceActivityPlan
import io.disruption.focal.core.ios.IosContract
import io.disruption.focal.core.ios.IosSnapshot
import io.disruption.focal.core.repository.BlockListRepository
import io.disruption.focal.core.repository.FocusSessionRepository
import io.disruption.focal.core.repository.ScheduleRepository
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.time.Clock

/** The snapshot the Screen Time extensions read, in the App Group container (T16). */
const val SNAPSHOT_FILE = "snapshot.json"

/**
 * Writes [IosSnapshot] to the App Group on every change to Block Lists, Schedules or running
 * sessions (T16, ADR 0008). The extensions make no decisions: they read this file, apply it, and
 * append events the app ingests on launch.
 *
 * After each write it hands the [DeviceActivityPlan] to [activities] when the plan changed (T31):
 * one interval per switched-on Schedule day and one per running session's planned end. The
 * snapshot is written first, so an interval that fires as soon as it is registered reads the
 * snapshot it belongs to.
 *
 * Before a plan stops or replaces a Schedule activity, [ingestPending] ingests what the extensions
 * recorded: a session the monitor started in that window (while Focal stayed open) then has its own
 * session-end interval in the plan, instead of losing its only end callback. A plan the system
 * refused in part is not taken as registered; [retry] registers it again, on each foreground.
 */
class IosSnapshotWriter(
    private val blockLists: BlockListRepository,
    private val schedules: ScheduleRepository,
    private val sessions: FocusSessionRepository,
    private val activities: DeviceActivityScheduler = NoDeviceActivityScheduler(),
    private val clock: Clock = Clock.System,
    private val ingestPending: suspend () -> Unit = {},
) {
    private var latest: DeviceActivityPlan? = null
    private var registered: DeviceActivityPlan? = null

    /** Start observing; the first emission writes the snapshot and registers the plan straight away. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                blockLists.observeAll(),
                schedules.observeAll(),
                sessions.observeRunning(),
            ) { lists, allSchedules, running -> Triple(lists, allSchedules, running) }
                .collect { (lists, allSchedules, running) ->
                    var now = clock.now()
                    var snapshot = IosSnapshot.from(now, lists, allSchedules, running)
                    var plan = DeviceActivityPlan.from(allSchedules, running, now)
                    if (plan != registered && plan.replacesSchedulesOf(registered)) {
                        write(snapshot)
                        ingestPending()
                        now = clock.now()
                        val fresh = sessions.getRunning()
                        snapshot = IosSnapshot.from(now, lists, allSchedules, fresh)
                        plan = DeviceActivityPlan.from(allSchedules, fresh, now)
                    }
                    write(snapshot)
                    latest = plan
                    if (plan != registered) register(plan)
                }
        }
    }

    /** Register the latest plan again if the system refused part of it, say before Screen Time access. */
    fun retry() {
        val plan = latest ?: return
        if (plan != registered) register(plan)
    }

    private fun register(plan: DeviceActivityPlan) {
        registered = if (activities.monitor(plan)) plan else null
    }

    /** Write [snapshot]; without the App Group entitlement there is no container and this is a no-op. */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    internal fun write(snapshot: IosSnapshot) {
        val container = NSFileManager.defaultManager
            .containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID) ?: return
        val path = container.URLByAppendingPathComponent(SNAPSHOT_FILE)?.path ?: return
        val bytes = IosContract.encodeSnapshot(snapshot).encodeToByteArray()
        if (bytes.isEmpty()) return
        bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                .writeToFile(path, atomically = true)
        }
    }
}
