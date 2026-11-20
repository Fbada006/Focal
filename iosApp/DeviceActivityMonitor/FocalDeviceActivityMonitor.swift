import DeviceActivity
import Foundation

/// Focal's DeviceActivityMonitor extension. The system runs it when one of Focal's DeviceActivity
/// intervals starts or ends, even while Focal isn't running (ADR 0008: Swift-only, works from the
/// App Group snapshot and appends `ExtensionEvent`s). Each kind of activity has its own handler,
/// recognised by the activity's name: Breaks (`BreakResumer`, T20). Schedules (T31) add theirs here.
final class FocalDeviceActivityMonitor: DeviceActivityMonitor {
    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        // A Break's interval starts in the past (see `BreakResumer`); nothing happens at its start.
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        if let sessionId = BreakResumer.sessionId(of: activity) {
            BreakResumer.resume(sessionId: sessionId, now: Date())
        }
    }
}
