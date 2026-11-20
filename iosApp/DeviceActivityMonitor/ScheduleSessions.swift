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
/// the end clears the store the start applied.
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
    /// DeviceActivity's 15 minutes. Only the window's real end clears the store: stopping the
    /// activity mid-window (the Schedule was switched off) must not weaken the running session.
    static func end(activity: DeviceActivityName, now: Date) {
        guard let recorded = recorded(activity),
              recorded.plannedEnd <= now.addingTimeInterval(BreakResumer.tolerance) else { return }
        FocalStore.named(sessionId: recorded.sessionId).clearAllSettings()
        record(activity, nil)
        FocalEventLog.append(type: "scheduleSessionEnded", at: now, fields: ["sessionId": recorded.sessionId])
    }

    /// When the window that started at about [now] ends: the next time the clock reads the
    /// Schedule's end, which also covers overnight windows. Nil if that is further away than the
    /// window is long, which means this start fired after its window was over.
    static func windowEnd(of schedule: FocalSnapshot.Schedule, now: Date) -> Date? {
        guard let start = minutes(schedule.start), let end = minutes(schedule.end) else { return nil }
        var length = end - start
        if length <= 0 { length += 24 * 60 }
        let calendar = Calendar.current
        guard let plannedEnd = calendar.nextDate(
            after: now,
            matching: DateComponents(hour: end / 60, minute: end % 60, second: 0),
            matchingPolicy: .nextTime
        ) else { return nil }
        guard plannedEnd.timeIntervalSince(now) <= TimeInterval(length * 60) + BreakResumer.tolerance else { return nil }
        return plannedEnd
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
