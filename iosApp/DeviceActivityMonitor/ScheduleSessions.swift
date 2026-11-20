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
/// The session gets the same coordinated record as a session the app applies
/// (`FocalAppliedSessions`), naming the window's activity, so the window's end finds it and checks
/// its current planned end; the Shield reads it too until the app has ingested the session. It also
/// gets its own session-end interval (`SessionEnder`), so switching off or editing its Schedule,
/// which stops the window's activity, never leaves it without an end.
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
              !blockList.isEmpty,
              let window = window(of: schedule, now: now) else { return }
        // A window gets one session (`FocusSessionService.evaluateSchedules`), even one ended early
        // or started before the Schedule's times moved.
        guard !hasSession(scheduleId: scheduleId, in: window, snapshot: snapshot) else { return }

        let sessionId = UUID().uuidString.lowercased()
        let plannedEnd = window.end
        // The record and the store in one coordinated step, as the app's engine applies a session.
        let applied = FocalAppliedSessions.update(sessionId: sessionId) { record in
            record = FocalAppliedSession(
                apps: blockList.apps,
                appCategories: blockList.appCategories,
                domains: blockList.domains,
                plannedEnd: plannedEnd,
                breakEndsAt: nil,
                name: schedule.name,
                scheduleId: schedule.id,
                scheduleActivity: activity.rawValue
            )
            FocalStore.apply(
                apps: blockList.apps,
                appCategories: blockList.appCategories,
                domains: blockList.domains,
                // Uninstall Protection while a Locked session runs (T23), as the app's engine applies it.
                denyAppRemoval: schedule.strictness == "Locked",
                to: FocalStore.named(sessionId: sessionId)
            )
        }
        // Without a record nothing could end the session or tell it apart: start none.
        guard applied else { return }
        // After the record, so the app, if it re-plans meanwhile, keeps this interval for the session.
        SessionEnder.monitor(sessionId: sessionId, plannedEnd: plannedEnd)
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
    /// session. The planned end is read from the session's record inside its coordination, where the
    /// app's engine writes a later end when it extends the session, so an extended session is left
    /// to its own session-end interval.
    static func end(activity: DeviceActivityName, now: Date) {
        let started = FocalAppliedSessions.all().filter { $0.value.scheduleActivity == activity.rawValue }
        for sessionId in started.keys {
            var ended = false
            FocalAppliedSessions.update(sessionId: sessionId) { record in
                guard let session = record,
                      session.scheduleActivity == activity.rawValue,
                      session.plannedEnd <= now else { return }
                record = nil
                FocalStore.named(sessionId: sessionId).clearAllSettings()
                ended = true
            }
            if ended {
                FocalEventLog.append(type: "scheduleSessionEnded", at: now, fields: ["sessionId": sessionId])
            }
        }
    }

    struct Window {
        let start: Date
        let end: Date
    }

    /// The window open at [now], or nil if [now] isn't inside one (this start fired late, or for a
    /// Schedule whose times have changed). The window is worked out on the local calendar, as
    /// `ScheduleWindows` does: its start is the latest time the clock read the Schedule's start, and
    /// its end the next time it reads the Schedule's end after that, which covers overnight windows
    /// and windows that span a daylight-saving change. A time in a skipped hour moves forward by the
    /// hour and keeps its minutes (02:30 becomes 03:30), as kotlinx-datetime resolves it.
    static func window(of schedule: FocalSnapshot.Schedule, now: Date) -> Window? {
        guard let start = minutes(schedule.start), let end = minutes(schedule.end) else { return nil }
        let calendar = Calendar.current
        // The start callback can arrive a moment before the minute it fires for.
        guard let windowStart = calendar.nextDate(
            after: now.addingTimeInterval(BreakResumer.tolerance),
            matching: DateComponents(hour: start / 60, minute: start % 60, second: 0),
            matchingPolicy: .nextTimePreservingSmallerComponents,
            direction: .backward
        ), let windowEnd = calendar.nextDate(
            after: windowStart,
            matching: DateComponents(hour: end / 60, minute: end % 60, second: 0),
            matchingPolicy: .nextTimePreservingSmallerComponents
        ), windowEnd > now else { return nil }
        return Window(start: windowStart, end: windowEnd)
    }

    /// Whether a session of [scheduleId] overlaps [window]: one the app knows of, running or ended
    /// (the snapshot), or one applied that the snapshot doesn't list yet (the records, which the
    /// app's engine and this extension write before anything else).
    private static func hasSession(scheduleId: String, in window: Window, snapshot: FocalSnapshot) -> Bool {
        let schedule = snapshot.schedules.first { $0.id == scheduleId }
        if (schedule?.sessions ?? []).contains(where: { $0.startedAt < window.end && $0.plannedEnd > window.start }) {
            return true
        }
        if snapshot.runningSessions.contains(where: { $0.scheduleId == scheduleId && $0.plannedEnd > window.start }) {
            return true
        }
        return FocalAppliedSessions.all().values.contains {
            $0.scheduleId == scheduleId && $0.plannedEnd > window.start
        }
    }

    /// Minutes since midnight of an `HH:mm` time.
    private static func minutes(_ time: String) -> Int? {
        let parts = time.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2 else { return nil }
        return parts[0] * 60 + parts[1]
    }
}
