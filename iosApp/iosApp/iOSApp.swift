import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        StartFocalKt.startFocal(blockingEngine: PendingBlockingEngine())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}