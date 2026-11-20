import SwiftUI
import UserNotifications
import Shared

@main
struct iOSApp: App {
    private let blockingEngine = IosBlockingEngine()
    private let notifications = NotificationRouter()

    init() {
        IosScreenTimeBridge.shared.picker = ScreenTimeAppPicker.shared
        UNUserNotificationCenter.current().delegate = notifications
        StartFocalKt.startFocal(
            blockingEngine: blockingEngine,
            permissionGateway: ScreenTimePermissionGateway()
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// Shows the "Open Focal" notification the Shield Action extension posts while the user is already
/// in Focal; tapping it anywhere else opens Focal as usual (T16).
private final class NotificationRouter: NSObject, UNUserNotificationCenterDelegate {
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}
