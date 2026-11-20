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
            let approved = AuthorizationCenter.shared.authorizationStatus == .approved
            completionHandler(KotlinBoolean(bool: approved), nil)
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

    func request(permission: Permission) {
        switch permission.name {
        case Permission.screentime.name:
            Task {
                try? await AuthorizationCenter.shared.requestAuthorization(for: .individual)
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
