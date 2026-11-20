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
    /// the snapshot and does nothing. Stopping it could itself end the interval early.
    func lift(sessionId: Any, completionHandler: @escaping (Error?) -> Void) {
        FocalStore.named(IosBlockingSupportKt.managedSettingsStoreName(sessionId: sessionId)).clearAllSettings()
        completionHandler(nil)
    }

    /// A Break lifts this session's blocking until [until] (T20). The DeviceActivityMonitor extension
    /// re-applies it then, even while Focal isn't running; Focal also re-applies it whenever it comes
    /// to the foreground, in case the interval didn't fire.
    func pauseUntil(sessionId: Any, until: KotlinInstant, completionHandler: @escaping (Error?) -> Void) {
        FocalStore.named(IosBlockingSupportKt.managedSettingsStoreName(sessionId: sessionId)).clearAllSettings()
        let end = Date(timeIntervalSince1970: TimeInterval(until.toEpochMilliseconds()) / 1000)
        let activity = DeviceActivityName(IosBlockingSupportKt.breakActivityName(sessionId: sessionId))
        // Registering fails without Screen Time authorisation; the foreground re-apply still covers it.
        try? Self.monitorBreak(activity, endingAt: end)
        completionHandler(nil)
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
