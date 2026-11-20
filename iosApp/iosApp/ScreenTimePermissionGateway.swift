import Foundation
import UIKit
import FamilyControls
import UserNotifications
import Shared

/// iOS permissions (T15): Screen Time (FamilyControls) authorisation and notifications. The Screen
/// Time status is Apple's own; there is no settings deep link that can grant it.
final class ScreenTimePermissionGateway: NSObject, PermissionGateway {
    var requiredForSessions: [Permission] {
        [Permission.screentime, Permission.notifications]
    }

    func isGranted(permission: Permission, completionHandler: @escaping (KotlinBoolean?, Error?) -> Void) {
        switch permission.name {
        case Permission.screentime.name:
            Task {
                let approved = await Self.screenTimeApproved()
                completionHandler(KotlinBoolean(bool: approved), nil)
            }
        case Permission.notifications.name:
            UNUserNotificationCenter.current().getNotificationSettings { settings in
                let granted = settings.authorizationStatus == .authorized
                    || settings.authorizationStatus == .provisional
                    || settings.authorizationStatus == .ephemeral
                completionHandler(KotlinBoolean(bool: granted), nil)
            }
        default:
            completionHandler(KotlinBoolean(bool: true), nil)
        }
    }

    /// Right after launch the status can read `.notDetermined` until the system has loaded it. A
    /// revoked Screen Time access makes running Locked sessions Broken (T23), so give it a moment
    /// to settle before reporting it missing.
    private static func screenTimeApproved() async -> Bool {
        for _ in 0..<5 {
            let status = AuthorizationCenter.shared.authorizationStatus
            if status != .notDetermined { return status == .approved }
            try? await Task.sleep(nanoseconds: 200_000_000)
        }
        // One last read after the final wait, so a status that settled during it still counts.
        return AuthorizationCenter.shared.authorizationStatus == .approved
    }

    func request(permission: Permission) {
        switch permission.name {
        case Permission.screentime.name:
            Task {
                try? await AuthorizationCenter.shared.requestAuthorization(for: .individual)
                // DeviceActivity intervals refused without access are registered again now, rather
                // than on the next foreground, which granting from inside Focal may not cause (T31).
                if AuthorizationCenter.shared.authorizationStatus == .approved {
                    await MainActor.run { StartFocalKt.focalDidBecomeActive() }
                }
            }
        case Permission.notifications.name:
            // iOS asks only once; after a denial the only way back is Focal's page in Settings.
            let center = UNUserNotificationCenter.current()
            center.getNotificationSettings { settings in
                switch settings.authorizationStatus {
                case .notDetermined:
                    center.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
                case .denied:
                    DispatchQueue.main.async {
                        guard let url = URL(string: UIApplication.openNotificationSettingsURLString) else { return }
                        UIApplication.shared.open(url)
                    }
                default:
                    break
                }
            }
        default:
            break
        }
    }

    func missing(completionHandler: @escaping ([Permission]?, Error?) -> Void) {
        Task {
            var missing: [Permission] = []
            for permission in requiredForSessions {
                let granted: Bool = await withCheckedContinuation { continuation in
                    isGranted(permission: permission) { value, _ in
                        continuation.resume(returning: value?.boolValue ?? false)
                    }
                }
                if !granted { missing.append(permission) }
            }
            completionHandler(missing, nil)
        }
    }
}
