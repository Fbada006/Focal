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
        // Only the end of the Break the app last recorded for a running session counts. Without a
        // snapshot, once the session has ended, or when this end belongs to another Break (say, an
        // earlier one, or the app re-registered the interval for an edited session), there is nothing
        // to put back: blocking a finished session would outlive it.
        guard let session = FocalSnapshotReader.load()?.runningSessions.first(where: { $0.id == sessionId }),
              session.plannedEnd > now,
              let breakEndsAt = session.breakEndsAt,
              breakEndsAt <= now.addingTimeInterval(tolerance),
              // A Break that runs to the session's planned end ends with the session: nothing resumes,
              // and nothing here would clear the store again once the session is over.
              breakEndsAt < session.plannedEnd,
              !FocalEndedSessions.contains(sessionId) else { return }

        let store = FocalStore.named(sessionId: sessionId)
        FocalStore.apply(
            apps: session.apps,
            appCategories: session.appCategories,
            domains: session.domains,
            to: store
        )
        // The app may have ended the session since the snapshot was read; it records that before it
        // clears the store, so checking after applying never leaves a finished session blocking.
        if FocalEndedSessions.contains(sessionId) {
            store.clearAllSettings()
            return
        }
        FocalEventLog.append(type: "breakEnded", at: now, fields: ["sessionId": sessionId])
    }
}
