import DeviceActivity
import Foundation

/// Starts and ends Focus Sessions for Schedules while Focal isn't running (T31, stories 55–57).
///
/// The app registers one repeating interval per day a switched-on Schedule runs, named
/// `focal-schedule-<weekday>-<schedule id>` (`DeviceActivityPlan` in shared/core). At its start this
/// applies a new session's store from the snapshot's Schedule and Block List and records
/// `scheduleSessionStarted`; at the window's end it clears that store and records
/// `scheduleSessionEnded`. The app ingests both on launch and decides what they mean (ADR 0008).
///
/// The session id is random and recorded in the App Group's defaults under the activity's name, so
/// the end clears the store the start applied. The session itself goes into `FocalMonitorSessions`
/// for the Shield until the app has ingested it.
enum ScheduleSessions {
    /// `DeviceActivityPlan.SCHEDULE_PREFIX` in Kotlin; keep the two identical.
    static let activityPrefix = "focal-schedule-"

    /// The Schedule's id: the name is the prefix, a weekday digit, `-`, then the id.
    static func scheduleId(of activity: DeviceActivityName) -> String? {
        let name = activity.rawValue
        guard name.hasPrefix(activityPrefix) else { return nil }
        let rest = name.dropFirst(activityPrefix.count)
        guard let dash = rest.firstIndex(of: "-") else { return nil }
        let id = String(rest[rest.index(after: dash)...])
        return id.isEmpty ? nil : id
    }

    static func start(activity: DeviceActivityName, scheduleId: String, now: Date) {
        // Switched off or deleted since the app last registered: nothing to start.
        guard let snapshot = FocalSnapshotReader.load(),
              let schedule = snapshot.schedules.first(where: { $0.id == scheduleId }),
              let blockList = snapshot.blockLists.first(where: { $0.id == schedule.blockListId }),
              !blockList.isEmpty else { return }
        // A window gets one session: the app already started this one (say, right after a save), or
        // this start was already handled.
        if snapshot.runningSessions.contains(where: { $0.scheduleId == scheduleId && $0.plannedEnd > now }) { return }
        if let recorded = recorded(activity), recorded.plannedEnd > now { return }
        guard let plannedEnd = windowEnd(of: schedule, now: now) else { return }

        let sessionId = UUID().uuidString.lowercased()
        FocalStore.apply(
            apps: blockList.apps,
            appCategories: blockList.appCategories,
            domains: blockList.domains,
            // Uninstall Protection while a Locked session runs (T23), as the app's engine applies it.
            denyAppRemoval: schedule.strictness == "Locked",
            to: FocalStore.named(sessionId: sessionId)
        )
        record(activity, RecordedSession(sessionId: sessionId, plannedEnd: plannedEnd))
        // The Shield shows this session's name and time left before the app has ingested it.
        FocalMonitorSessions.record(FocalSnapshot.Session(
            id: sessionId,
            name: schedule.name,
            scheduleId: schedule.id,
            plannedEnd: plannedEnd,
            breakEndsAt: nil,
            apps: blockList.apps,
            appCategories: blockList.appCategories,
            domains: blockList.domains
        ), now: now)
        FocalEventLog.append(type: "scheduleSessionStarted", at: now, fields: [
            "sessionId": sessionId,
            "scheduleId": schedule.id,
            "name": schedule.name,
            "blockListId": blockList.id,
            "strictness": schedule.strictness,
            "plannedEnd": FocalEventLog.timestamp(plannedEnd),
            // The session's own copy of what it blocks (ADR 0007).
            "apps": blockList.apps,
            "appCategories": blockList.appCategories,
            "domains": blockList.domains,
        ])
    }

    /// Called at the interval's end, and at its "will end" warning for a window shorter than
    /// DeviceActivity's 15 minutes. Only the session's real end clears the store: stopping the
    /// activity mid-window (the Schedule was switched off or edited) must not weaken the running
    /// session. Once the app has ingested the session, the snapshot's planned end counts, since the
    /// session may have been extended; the app's session-end interval (`SessionEnder`) then ends it.
    static func end(activity: DeviceActivityName, now: Date) {
        guard let recorded = recorded(activity) else { return }
        if let ingested = FocalSnapshotReader.load()?.runningSessions.first(where: { $0.id == recorded.sessionId }),
           ingested.plannedEnd > now {
            if recorded.plannedEnd <= now {
                // The window is over but the session runs on: it is the app's to end now.
                record(activity, nil)
                FocalMonitorSessions.remove(recorded.sessionId)
            }
            return
        }
        guard recorded.plannedEnd <= now else { return }
        FocalStore.named(sessionId: recorded.sessionId).clearAllSettings()
        record(activity, nil)
        FocalMonitorSessions.remove(recorded.sessionId)
        FocalEventLog.append(type: "scheduleSessionEnded", at: now, fields: ["sessionId": recorded.sessionId])
    }

    /// When the window open at [now] ends, or nil if [now] isn't inside one (this start fired late,
    /// or for a Schedule whose times have changed). The window is worked out on the local calendar,
    /// as `ScheduleWindows` does: its start is the latest time the clock read the Schedule's start, and
    /// its end the next time it reads the Schedule's end after that, which covers overnight windows
    /// and windows that span a daylight-saving change.
    static func windowEnd(of schedule: FocalSnapshot.Schedule, now: Date) -> Date? {
        guard let start = minutes(schedule.start), let end = minutes(schedule.end) else { return nil }
        let calendar = Calendar.current
        // The start callback can arrive a moment before the minute it fires for.
        guard let windowStart = calendar.nextDate(
            after: now.addingTimeInterval(BreakResumer.tolerance),
            matching: DateComponents(hour: start / 60, minute: start % 60, second: 0),
            matchingPolicy: .nextTime,
            direction: .backward
        ), let windowEnd = calendar.nextDate(
            after: windowStart,
            matching: DateComponents(hour: end / 60, minute: end % 60, second: 0),
            matchingPolicy: .nextTime
        ) else { return nil }
        return windowEnd > now ? windowEnd : nil
    }

    /// Minutes since midnight of an `HH:mm` time.
    private static func minutes(_ time: String) -> Int? {
        let parts = time.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2 else { return nil }
        return parts[0] * 60 + parts[1]
    }

    // MARK: - The session a window started

    struct RecordedSession {
        let sessionId: String
        let plannedEnd: Date
    }

    private static var defaults: UserDefaults? { UserDefaults(suiteName: FocalSnapshotReader.appGroup) }

    private static func key(_ activity: DeviceActivityName) -> String { "focal.scheduleSession." + activity.rawValue }

    private static func recorded(_ activity: DeviceActivityName) -> RecordedSession? {
        guard let entry = defaults?.dictionary(forKey: key(activity)),
              let sessionId = entry["sessionId"] as? String,
              let plannedEnd = entry["plannedEnd"] as? Double else { return nil }
        return RecordedSession(sessionId: sessionId, plannedEnd: Date(timeIntervalSince1970: plannedEnd))
    }

    private static func record(_ activity: DeviceActivityName, _ session: RecordedSession?) {
        guard let session else {
            defaults?.removeObject(forKey: key(activity))
            return
        }
        defaults?.set(
            ["sessionId": session.sessionId, "plannedEnd": session.plannedEnd.timeIntervalSince1970],
            forKey: key(activity)
        )
    }
}
