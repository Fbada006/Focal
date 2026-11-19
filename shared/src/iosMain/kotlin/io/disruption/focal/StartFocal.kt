package io.disruption.focal

import io.disruption.focal.core.blocking.BlockingEngine
import io.disruption.focal.core.ios.ingestOnLaunch
import io.disruption.focal.core.session.FocusSessionService
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

/**
 * The App Group the app shares with its Screen Time extensions (ADR 0008). The entitlement is
 * added with the extensions (T16); until then there is no container and nothing is read.
 */
const val APP_GROUP_ID = "group.io.disruption.focal"

/** The log the extensions append to, in the App Group container. */
const val EXTENSION_EVENTS_FILE = "extension-events.json"

/**
 * Entry point for Swift: `StartFocalKt.startFocal(blockingEngine: …)` from `iOSApp.init`. Starts
 * Koin, then ingests what the extensions did while Focal wasn't running.
 */
fun startFocal(blockingEngine: BlockingEngine = PendingBlockingEngine()) {
    val koin = initKoin(blockingEngine).koin
    MainScope().launch { ingestOnLaunch(koin.get<FocusSessionService>(), readExtensionEvents()) }
}

@OptIn(ExperimentalForeignApi::class)
private fun readExtensionEvents(): String? {
    val container = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID) ?: return null
    val path = container.URLByAppendingPathComponent(EXTENSION_EVENTS_FILE)?.path ?: return null
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return null
    return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
}
