package io.disruption.focal

import io.disruption.focal.core.blocking.BlockingEngine
import io.disruption.focal.core.ios.IosContract
import io.disruption.focal.core.ios.ingestOnLaunch
import io.disruption.focal.core.permissions.PermissionGateway
import io.disruption.focal.core.schedule.ScheduleService
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.ui.di.iosUiModule
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.Foundation.NSData
import platform.Foundation.NSFileCoordinator
import platform.Foundation.NSFileCoordinatorWritingForMerging
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToURL

/**
 * The App Group the app shares with its Screen Time extensions (ADR 0008). The app and every
 * extension carry this exact id in their entitlements; the Swift side repeats it in
 * `FocalSnapshotReader.appGroup`.
 */
const val APP_GROUP_ID = "group.io.disruption.focal"

/** The log the extensions append to, in the App Group container. */
const val EXTENSION_EVENTS_FILE = "extension-events.json"

/**
 * Entry point for Swift: `StartFocalKt.startFocal(blockingEngine: …, permissionGateway: …,
 * activityScheduler: …)` from `iOSApp.init`. The Swift side supplies the Screen Time engine (T16),
 * permissions (T15) and DeviceActivity registration (T31). Starts Koin, then ingests what the
 * extensions did while Focal wasn't running.
 */
fun startFocal(
    blockingEngine: BlockingEngine = PendingBlockingEngine(),
    permissionGateway: PermissionGateway = PendingPermissionGateway(),
    activityScheduler: DeviceActivityScheduler = NoDeviceActivityScheduler(),
) {
    val koin = initKoin(blockingEngine, permissionGateway, platformModules = listOf(iosUiModule)).koin
    val scope = MainScope()
    // Keep the extensions' snapshot and DeviceActivity intervals current (T31), then ingest what
    // they did while Focal wasn't running.
    IosSnapshotWriter(koin.get(), koin.get(), koin.get(), activityScheduler).start(scope)
    // Screen Time access revoked while Focal was away makes running Locked sessions Broken (T23).
    val catchUp = {
        scope.launch {
            val missing = runCatching { permissionGateway.missing() }.getOrDefault(emptyList())
            val log = readExtensionEvents()
            // Then start a Schedule window whose DeviceActivity start didn't fire (T31).
            ingestOnLaunch(koin.get<FocusSessionService>(), log, missing, koin.get<ScheduleService>())
            // Ingested events are now in the database; drop them so the log only holds what's pending.
            if (log != null) compactExtensionEvents(ingested = log)
        }
    }
    catchUp()
    onBecameActive = { catchUp() }
}

private var onBecameActive: (() -> Unit)? = null

/**
 * Call from Swift each time Focal comes to the foreground: ingests what the extensions did meanwhile
 * and re-applies running sessions, so a Break that ended while Focal was suspended is blocking again
 * even if the DeviceActivity monitor didn't fire (T20, docs/ios-blocking-engine.md).
 */
fun focalDidBecomeActive() {
    onBecameActive?.invoke()
}

@OptIn(ExperimentalForeignApi::class)
private fun extensionEventsUrl(): NSURL? = NSFileManager.defaultManager
    .containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID)
    ?.URLByAppendingPathComponent(EXTENSION_EVENTS_FILE)

@OptIn(ExperimentalForeignApi::class)
private fun readExtensionEvents(): String? {
    val path = extensionEventsUrl()?.path ?: return null
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
    return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
}

/**
 * Remove the events in [ingested] from the log (`IosContract.withoutIngested`). The extension
 * appends under the same file coordination (`FocalEventLog.append`), so an event it writes while
 * this runs is never lost.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun compactExtensionEvents(ingested: String) {
    val url = extensionEventsUrl() ?: return
    NSFileCoordinator(filePresenter = null).coordinateWritingItemAtURL(
        url,
        options = NSFileCoordinatorWritingForMerging,
        error = null,
    ) { coordinated ->
        val path = coordinated?.path ?: return@coordinateWritingItemAtURL
        val current = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) ?: return@coordinateWritingItemAtURL
        val compacted = IosContract.withoutIngested(current, ingested) ?: return@coordinateWritingItemAtURL
        val bytes = compacted.encodeToByteArray()
        bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
                .writeToURL(coordinated, atomically = true)
        }
    }
}
