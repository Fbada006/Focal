import DeviceActivity
import Foundation

/// Focal's DeviceActivityMonitor extension. The system runs it when one of Focal's DeviceActivity
/// intervals starts or ends, even while Focal isn't running (ADR 0008: Swift-only, works from the
/// App Group snapshot and appends `ExtensionEvent`s). Each kind of activity has its own handler,
/// recognised by the activity's name: Breaks (`BreakResumer`, T20), Schedules (`ScheduleSessions`,
/// T31) and session ends (`SessionEnder`, T31).
final class FocalDeviceActivityMonitor: DeviceActivityMonitor {
    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        // Break and session-end intervals start in the past or don't matter at their start.
        if let scheduleId = ScheduleSessions.scheduleId(of: activity) {
            ScheduleSessions.start(activity: activity, scheduleId: scheduleId, now: Date())
        }
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        let now = Date()
        if let sessionId = BreakResumer.sessionId(of: activity) {
            BreakResumer.resume(sessionId: sessionId, now: now)
        } else if ScheduleSessions.scheduleId(of: activity) != nil {
            ScheduleSessions.end(activity: activity, now: now)
        } else if let sessionId = SessionEnder.sessionId(of: activity) {
            SessionEnder.end(sessionId: sessionId, now: now)
        }
    }

    /// Only a Schedule window shorter than 15 minutes registers a warning: it marks the real end.
    override func intervalWillEndWarning(for activity: DeviceActivityName) {
        super.intervalWillEndWarning(for: activity)
        if ScheduleSessions.scheduleId(of: activity) != nil {
            ScheduleSessions.end(activity: activity, now: Date())
        }
    }
}
