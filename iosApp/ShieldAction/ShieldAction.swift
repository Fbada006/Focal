import Foundation
import ManagedSettings
import UserNotifications

/// Shield buttons (T16, stories 62–64). Close returns home. A Shield Action extension can't open
/// its containing app, so Open Focal posts a local notification the user taps to open Focal (see
/// docs/ios-blocking-engine.md). There is no action that ends a session or takes a Break.
final class ShieldActionExtension: ShieldActionDelegate {
    override func handle(
        action: ShieldAction,
        for application: ApplicationToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, completionHandler: completionHandler)
    }

    override func handle(
        action: ShieldAction,
        for category: ActivityCategoryToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, completionHandler: completionHandler)
    }

    override func handle(
        action: ShieldAction,
        for webDomain: WebDomainToken,
        completionHandler: @escaping (ShieldActionResponse) -> Void
    ) {
        respond(to: action, completionHandler: completionHandler)
    }

    private func respond(to action: ShieldAction, completionHandler: @escaping (ShieldActionResponse) -> Void) {
        if action == .secondaryButtonPressed {
            postOpenFocalNotification()
        }
        // Closing the Shield also leaves the blocked app, which is what both buttons do here.
        completionHandler(.close)
    }

    private func postOpenFocalNotification() {
        let content = UNMutableNotificationContent()
        content.title = "Open Focal"
        content.body = "Tap to manage your Focus Sessions."
        content.sound = .default
        // One identifier, so tapping Open Focal twice replaces the notification instead of stacking.
        let request = UNNotificationRequest(
            identifier: "open-focal",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }
}
