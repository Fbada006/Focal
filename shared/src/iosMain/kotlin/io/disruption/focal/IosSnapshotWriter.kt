package io.disruption.focal

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
 */
class IosSnapshotWriter(
    private val blockLists: BlockListRepository,
    private val schedules: ScheduleRepository,
    private val sessions: FocusSessionRepository,
    private val clock: Clock = Clock.System,
) {
    /** Start observing; the first emission writes the snapshot straight away. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                blockLists.observeAll(),
                schedules.observeAll(),
                sessions.observeRunning(),
            ) { lists, allSchedules, running ->
                IosSnapshot.from(clock.now(), lists, allSchedules, running)
            }.collect(::write)
        }
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
