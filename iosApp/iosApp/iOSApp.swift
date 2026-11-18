import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        StartFocalKt.startFocal(blockingEngine: PendingBlockingEngine(), permissionGateway: PendingPermissionGateway())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}