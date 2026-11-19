package io.disruption.focal

import io.disruption.focal.core.blocking.BlockingEngine

/** Entry point for Swift: `StartFocalKt.startFocal(blockingEngine: …)` from `iOSApp.init`. */
fun startFocal(blockingEngine: BlockingEngine = PendingBlockingEngine()) {
    initKoin(blockingEngine)
}
