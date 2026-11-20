import DeviceActivity
import Foundation
import ManagedSettings
import Shared

/// The iOS `BlockingEngine` (T16, ADR 0006): one named `ManagedSettingsStore` per Focus Session.
/// The system enforces the most restrictive combination of all stores, so lifting one session never
/// unblocks what another still blocks. It applies exactly what the session's own blocked set says
/// and makes no domain decisions.
///
/// Named stores persist across launches and are shared with the extensions, so a store is always
/// looked up by name rather than remembered: a session applied before a relaunch, or started by the
/// DeviceActivityMonitor extension, can still be lifted.
final class IosBlockingEngine: NSObject, BlockingEngine {
    func apply(session: FocusSession, completionHandler: @escaping (Error?) -> Void) {
        let blocked = session.blocked
        let sessionId = IosBlockingSupportKt.sessionIdString(sessionId: session.id)
        let store = FocalStore.named(IosBlockingSupportKt.managedSettingsStoreName(session: session))
        var breakOver = false
        let applied = { (record: inout FocalAppliedSession?) in
            // A Break in progress stays recorded: the session service pauses the session again next.
            // One that is over is forgotten here, in the same step, so its interval can be stopped
            // below: if stopping ends the interval, the monitor then finds no Break and does nothing.
            var breakEndsAt = record?.breakEndsAt
            if let end = breakEndsAt, end <= Date() {
                breakEndsAt = nil
                breakOver = true
            }
            // The record first, so the DeviceActivityMonitor re-applies this set at a Break's end.
            record = FocalAppliedSession(
                apps: Array(blocked.apps),
                appCategories: Array(blocked.appCategories),
                domains: Array(blocked.domains),
                plannedEnd: Self.date(session.plannedEnd),
                breakEndsAt: breakEndsAt
            )
            // Re-applying replaces what this session blocks (additions while it runs).
            FocalStore.apply(
                apps: blocked.apps,
                appCategories: blocked.appCategories,
                domains: blocked.domains,
                to: store
            )
        }
        if !FocalAppliedSessions.update(sessionId: sessionId, applied) {
            // No shared record (no App Group): still block.
            var none: FocalAppliedSession?
            applied(&none)
        }
        if breakOver { Self.stopMonitoringBreak(sessionId: session.id) }
        completionHandler(nil)
    }

    /// Deleting the record and clearing the store happen in one coordinated step
    /// (`FocalAppliedSessions`), so the monitor can't re-apply the session in between. The Break's
    /// interval is stopped after that: stopping can end it early, and the monitor then finds no
    /// record and does nothing. Left registered, every Break would keep one of the system's limited
    /// DeviceActivity slots that Schedules need.
    func lift(sessionId: Any, completionHandler: @escaping (Error?) -> Void) {
        let name = IosBlockingSupportKt.managedSettingsStoreName(sessionId: sessionId)
        let id = IosBlockingSupportKt.sessionIdString(sessionId: sessionId)
        breakTimers.removeValue(forKey: name)?.cancel()
        FocalEndedSessions.record(id)
        FocalAppliedSessions.update(sessionId: id) { record in
            record = nil
            FocalStore.named(name).clearAllSettings()
        }
        // Cleared again outside the coordination, in case it couldn't run.
        FocalStore.named(name).clearAllSettings()
        Self.stopMonitoringBreak(sessionId: sessionId)
        completionHandler(nil)
    }

    /// A Break lifts this session's blocking until [until] (T20). The DeviceActivityMonitor extension
    /// re-applies it then, even while Focal isn't running. While Focal is open, a timer catches up at
    /// the Break's end too; and Focal re-applies running sessions whenever it comes to the
    /// foreground, in case neither fired.
    ///
    /// Reports false, leaving the session blocking, when nothing could bring blocking back with Focal
    /// killed: the Break's interval couldn't be registered, or there is no shared record for the
    /// monitor to re-apply. The session service then refuses the Break.
    func pauseUntil(sessionId: Any, until: KotlinInstant, completionHandler: @escaping (KotlinBoolean?, Error?) -> Void) {
        let name = IosBlockingSupportKt.managedSettingsStoreName(sessionId: sessionId)
        let id = IosBlockingSupportKt.sessionIdString(sessionId: sessionId)
        let end = Self.date(until)
        let activity = DeviceActivityName(IosBlockingSupportKt.breakActivityName(sessionId: sessionId))
        do {
            try Self.monitorBreak(activity, endingAt: end)
        } catch {
            NSLog("Focal: couldn't register the Break's end for %@: %@", name, String(describing: error))
            completionHandler(KotlinBoolean(bool: false), nil)
            return
        }
        var paused = false
        FocalAppliedSessions.update(sessionId: id) { record in
            guard record != nil else { return }
            // The Break is recorded before the store is cleared, so the monitor (and anything reading
            // the record) never sees a cleared store without the Break that explains it.
            record?.breakEndsAt = end
            FocalStore.named(name).clearAllSettings()
            paused = true
        }
        if paused {
            catchUp(at: end, key: name)
        } else {
            // Nothing recorded the Break, so its interval would never be stopped.
            Self.stopMonitoringBreak(sessionId: sessionId)
        }
        completionHandler(KotlinBoolean(bool: paused), nil)
    }

    /// Stop the session's Break interval, once nothing recorded depends on it. The engine's calls are
    /// serialised by the session service, so this never stops a Break this engine registers later.
    private static func stopMonitoringBreak(sessionId: Any) {
        let activity = DeviceActivityName(IosBlockingSupportKt.breakActivityName(sessionId: sessionId))
        DeviceActivityCenter().stopMonitoring([activity])
    }

    private static func date(_ instant: KotlinInstant) -> Date {
        Date(timeIntervalSince1970: TimeInterval(instant.toEpochMilliseconds()) / 1000)
    }

    /// Pending in-app Break ends, by store name. Taking or re-applying a Break replaces its timer.
    private var breakTimers: [String: DispatchWorkItem] = [:]

    /// Run Focal's catch-up (ingest, then re-apply running sessions) just after [end], if Focal is
    /// still running then. A suspended app's timer fires when it resumes, which the foreground
    /// catch-up covers anyway.
    private func catchUp(at end: Date, key: String) {
        breakTimers.removeValue(forKey: key)?.cancel()
        let work = DispatchWorkItem { [weak self] in
            self?.breakTimers.removeValue(forKey: key)
            StartFocalKt.focalDidBecomeActive()
        }
        breakTimers[key] = work
        DispatchQueue.main.asyncAfter(wallDeadline: .now() + max(0, end.timeIntervalSinceNow) + 1, execute: work)
    }

    /// DeviceActivity intervals must last at least 15 minutes, and a Break lasts 5, so the interval
    /// starts in the past and only its end is used (docs/ios-blocking-engine.md). Registering the
    /// same name again replaces the previous interval.
    private static func monitorBreak(_ activity: DeviceActivityName, endingAt end: Date) throws {
        let calendar = Calendar.current
        let components: Set<Calendar.Component> = [.year, .month, .day, .hour, .minute, .second]
        let schedule = DeviceActivitySchedule(
            intervalStart: calendar.dateComponents(components, from: end.addingTimeInterval(-minimumInterval)),
            intervalEnd: calendar.dateComponents(components, from: end),
            repeats: false
        )
        try DeviceActivityCenter().startMonitoring(activity, during: schedule)
    }

    /// DeviceActivity's shortest allowed interval, plus a minute of margin.
    private static let minimumInterval: TimeInterval = 16 * 60
}
