import DeviceActivity
import Foundation

/// Re-applies a Focus Session's store when its Break ends (T20, story 36).
///
/// The app lifts the session's store and registers a DeviceActivity interval named
/// `focal-break-<session id>` that ends at the Break's end. DeviceActivity intervals must last at least
/// 15 minutes, so the app starts it in the past; only its end matters. When it ends, this re-applies
/// what the engine last applied for the session (`FocalAppliedSessions`) and records "Break ended at
/// T" for the app to ingest.
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
        var resumed = false
        // Inside the record's coordination, so the app can't apply, pause or lift this session while
        // this runs: what is applied here is the engine's latest, never an older set.
        FocalAppliedSessions.update(sessionId: sessionId) { record in
            // Only the end of the Break the engine last recorded for a running session counts. With
            // no record the session was lifted (or never applied here): blocking it would outlive it.
            // An end belonging to another Break (an earlier one, or one re-registered for an edited
            // session) finds a later `breakEndsAt` and does nothing.
            guard let session = record,
                  session.plannedEnd > now,
                  let breakEndsAt = session.breakEndsAt,
                  breakEndsAt <= now.addingTimeInterval(tolerance),
                  // A Break that runs to the session's planned end ends with the session: nothing
                  // resumes, and nothing here would clear the store again once the session is over.
                  breakEndsAt < session.plannedEnd,
                  !FocalEndedSessions.contains(sessionId) else { return }
            FocalStore.apply(
                apps: session.apps,
                appCategories: session.appCategories,
                domains: session.domains,
                to: FocalStore.named(sessionId: sessionId)
            )
            resumed = true
        }
        if resumed {
            FocalEventLog.append(type: "breakEnded", at: now, fields: ["sessionId": sessionId])
        }
    }
}
