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
        // Re-applying replaces what this session blocks (additions while it runs).
        FocalStore.apply(
            apps: blocked.apps,
            appCategories: blocked.appCategories,
            domains: blocked.domains,
            to: FocalStore.named(IosBlockingSupportKt.managedSettingsStoreName(session: session))
        )
        completionHandler(nil)
    }

    /// A Break interval left registered here is harmless: the monitor finds the session gone from
    /// the snapshot, or recorded as ended, and does nothing. Stopping it could itself end the
    /// interval early. The session is recorded as ended before its store is cleared (see
    /// `FocalEndedSessions`), so a monitor re-applying it at this moment clears it again.
    func lift(sessionId: Any, completionHandler: @escaping (Error?) -> Void) {
        let name = IosBlockingSupportKt.managedSettingsStoreName(sessionId: sessionId)
        breakTimers.removeValue(forKey: name)?.cancel()
        FocalEndedSessions.record(IosBlockingSupportKt.sessionIdString(sessionId: sessionId))
        FocalStore.named(name).clearAllSettings()
        completionHandler(nil)
    }

    /// A Break lifts this session's blocking until [until] (T20). The DeviceActivityMonitor extension
    /// re-applies it then, even while Focal isn't running. While Focal is open, a timer catches up at
    /// the Break's end too; and Focal re-applies running sessions whenever it comes to the
    /// foreground, in case neither fired.
    func pauseUntil(sessionId: Any, until: KotlinInstant, completionHandler: @escaping (Error?) -> Void) {
        let name = IosBlockingSupportKt.managedSettingsStoreName(sessionId: sessionId)
        FocalStore.named(name).clearAllSettings()
        let end = Date(timeIntervalSince1970: TimeInterval(until.toEpochMilliseconds()) / 1000)
        let activity = DeviceActivityName(IosBlockingSupportKt.breakActivityName(sessionId: sessionId))
        do {
            try Self.monitorBreak(activity, endingAt: end)
        } catch {
            // Registering fails without Screen Time authorisation, when no store blocks anything
            // anyway. The timer below and the foreground re-apply still end the Break.
            NSLog("Focal: couldn't register the Break's end for %@: %@", name, String(describing: error))
        }
        catchUp(at: end, key: name)
        completionHandler(nil)
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
