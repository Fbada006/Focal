import DeviceActivity
import Foundation

/// Lifts a Focus Session's store at its planned end while Focal isn't running (T31), so the Shield
/// and Uninstall Protection don't outlast the session until Focal next opens.
///
/// The app registers an interval named `focal-end-<session id>` ending at each running session's
/// planned end (`DeviceActivityPlan` in shared/core). Like a Break's, it starts in the past when the
/// session is shorter than DeviceActivity's 15 minutes; only its end matters. This records nothing:
/// on next launch the app finds the session past its end and records it as Completed.
enum SessionEnder {
    /// `DeviceActivityPlan.SESSION_END_PREFIX` in Kotlin; keep the two identical.
    static let activityPrefix = "focal-end-"

    static func sessionId(of activity: DeviceActivityName) -> String? {
        let name = activity.rawValue
        guard name.hasPrefix(activityPrefix) else { return nil }
        let id = String(name.dropFirst(activityPrefix.count))
        return id.isEmpty ? nil : id
    }

    /// Never before the snapshot's planned end: the interval ends on the minute at or after it, and
    /// a session extended since (`edit`) gets its interval registered again for the new end, so a
    /// callback for the old end finds the session still running and leaves it blocking.
    static func end(sessionId: String, now: Date) {
        if let session = FocalSnapshotReader.load()?.runningSessions.first(where: { $0.id == sessionId }),
           session.plannedEnd > now { return }
        FocalStore.named(sessionId: sessionId).clearAllSettings()
    }
}
