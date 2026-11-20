import DeviceActivity
import Foundation

/// Lifts a Focus Session's store at its planned end while Focal isn't running (T31), so the Shield
/// and Uninstall Protection don't outlast the session until Focal next opens.
///
/// The app registers an interval named `focal-end-<session id>` ending at each running session's
/// planned end (`DeviceActivityPlan` in shared/core); the monitor registers one too for each session
/// it starts for a Schedule (`ScheduleSessions`). Like a Break's, it starts in the past when the
/// session is shorter than DeviceActivity's 15 minutes; only its end matters. This records nothing:
/// on next launch the app finds the session past its end and records it as Completed.
enum SessionEnder {
    /// `DeviceActivityPlan.SESSION_END_PREFIX` in Kotlin; keep the two identical.
    static let activityPrefix = "focal-end-"

    /// `DeviceActivityPlan.SESSION_END_LEAD` in Kotlin.
    private static let lead: TimeInterval = 16 * 60

    static func sessionId(of activity: DeviceActivityName) -> String? {
        let name = activity.rawValue
        guard name.hasPrefix(activityPrefix) else { return nil }
        let id = String(name.dropFirst(activityPrefix.count))
        return id.isEmpty ? nil : id
    }

    /// Never before the session's current planned end. The check and the clearing run inside the
    /// session's record coordination (`FocalAppliedSessions`), where the app's engine also writes a
    /// new end and re-applies the store when the session is extended (`edit`): a callback for the old
    /// end finds the later end there and leaves the session blocking, whichever runs first. With no
    /// record the session was lifted already, and clearing again is harmless.
    static func end(sessionId: String, now: Date) {
        let store = FocalStore.named(sessionId: sessionId)
        let ran = FocalAppliedSessions.update(sessionId: sessionId) { record in
            if let session = record, session.plannedEnd > now { return }
            record = nil
            store.clearAllSettings()
        }
        guard !ran else { return }
        // No shared record to check (no App Group container): fall back to the snapshot.
        if let session = FocalSnapshotReader.load()?.runningSessions.first(where: { $0.id == sessionId }),
           session.plannedEnd > now { return }
        store.clearAllSettings()
    }

    /// Register [sessionId]'s end interval from the extension, as the app's plan would: ending on the
    /// whole minute at or after [plannedEnd]. The app keeps it while the session's record lasts, even
    /// before it has ingested the session (`IosDeviceActivityScheduler`). Returns false if the system
    /// refused it (the activity limit); the window's end then ends the session instead.
    @discardableResult
    static func monitor(sessionId: String, plannedEnd: Date) -> Bool {
        let seconds = plannedEnd.timeIntervalSince1970
        let end = Date(timeIntervalSince1970: (seconds / 60).rounded(.up) * 60)
        let calendar = Calendar.current
        let components: Set<Calendar.Component> = [.year, .month, .day, .hour, .minute, .second]
        let schedule = DeviceActivitySchedule(
            intervalStart: calendar.dateComponents(components, from: plannedEnd.addingTimeInterval(-lead)),
            intervalEnd: calendar.dateComponents(components, from: end),
            repeats: false
        )
        do {
            try DeviceActivityCenter().startMonitoring(DeviceActivityName(activityPrefix + sessionId), during: schedule)
            return true
        } catch {
            NSLog("Focal: couldn't register the end of session %@: %@", sessionId, String(describing: error))
            return false
        }
    }
}
