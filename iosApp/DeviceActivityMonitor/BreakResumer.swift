import DeviceActivity
import Foundation

/// Re-applies a Focus Session's store when its Break ends (T20, story 36).
///
/// The app lifts the session's store and registers a DeviceActivity interval named
/// `focal-break-<session id>` that ends at the Break's end. DeviceActivity intervals must last at least
/// 15 minutes, so the app starts it in the past; only its end matters. When it ends, this re-applies
/// what the snapshot says the session blocks and records "Break ended at T" for the app to ingest.
enum BreakResumer {
    /// `breakActivityName` in Kotlin (`IosBlockingSupport.kt`); keep the two identical.
    static let activityPrefix = "focal-break-"

    /// DeviceActivity reports interval ends to the minute, so a Break ending within this much of now
    /// is treated as over.
    static let tolerance: TimeInterval = 60

    static func sessionId(of activity: DeviceActivityName) -> String? {
        let name = activity.rawValue
        guard name.hasPrefix(activityPrefix) else { return nil }
        let id = String(name.dropFirst(activityPrefix.count))
        return id.isEmpty ? nil : id
    }

    static func resume(sessionId: String, now: Date) {
        // Without a snapshot, or once the session has ended, there is nothing to put back: blocking a
        // finished session would outlive it.
        guard let session = FocalSnapshotReader.load()?.runningSessions.first(where: { $0.id == sessionId }),
              session.plannedEnd > now else { return }
        // The app says the session is still on a Break (say, it re-registered the interval for an
        // edited session), so this end is stale.
        if let breakEndsAt = session.breakEndsAt, breakEndsAt > now.addingTimeInterval(tolerance) { return }

        FocalStore.apply(
            apps: session.apps,
            appCategories: session.appCategories,
            domains: session.domains,
            to: FocalStore.named(sessionId: sessionId)
        )
        FocalEventLog.append(type: "breakEnded", at: now, fields: ["sessionId": sessionId])
    }
}
