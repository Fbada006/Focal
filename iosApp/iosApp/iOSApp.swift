import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        IosScreenTimeBridge.shared.picker = ScreenTimeAppPicker.shared
        StartFocalKt.startFocal(
            blockingEngine: PendingBlockingEngine(),
            permissionGateway: ScreenTimePermissionGateway()
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
