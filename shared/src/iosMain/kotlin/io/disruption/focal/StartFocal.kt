package io.disruption.focal

import io.disruption.focal.core.blocking.BlockingEngine
import io.disruption.focal.core.ios.ingestOnLaunch
import io.disruption.focal.core.permissions.PermissionGateway
import io.disruption.focal.core.session.FocusSessionService
import io.disruption.focal.ui.di.iosUiModule
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/**
 * The App Group the app shares with its Screen Time extensions (ADR 0008). The app and every
 * extension carry this exact id in their entitlements; the Swift side repeats it in
 * `FocalSnapshotReader.appGroup`.
 */
const val APP_GROUP_ID = "group.io.disruption.focal"

/** The log the extensions append to, in the App Group container. */
const val EXTENSION_EVENTS_FILE = "extension-events.json"

/**
 * Entry point for Swift: `StartFocalKt.startFocal(blockingEngine: …, permissionGateway: …)` from
 * `iOSApp.init`. The Swift side supplies the Screen Time engine (T16) and permissions (T15).
 * Starts Koin, then ingests what the extensions did while Focal wasn't running.
 */
fun startFocal(
    blockingEngine: BlockingEngine = PendingBlockingEngine(),
    permissionGateway: PermissionGateway = PendingPermissionGateway(),
) {
    val koin = initKoin(blockingEngine, permissionGateway, platformModules = listOf(iosUiModule)).koin
    val scope = MainScope()
    // Keep the extensions' snapshot current, then ingest what they did while Focal wasn't running.
    IosSnapshotWriter(koin.get(), koin.get(), koin.get()).start(scope)
    val catchUp = { scope.launch { ingestOnLaunch(koin.get<FocusSessionService>(), readExtensionEvents()) } }
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
private fun readExtensionEvents(): String? {
    val container = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID) ?: return null
    val path = container.URLByAppendingPathComponent(EXTENSION_EVENTS_FILE)?.path ?: return null
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
    return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
}
