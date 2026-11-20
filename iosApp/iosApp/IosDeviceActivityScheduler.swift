import DeviceActivity
import Foundation
import Shared

/// Registers the DeviceActivity intervals the shared code plans (`DeviceActivityPlan`, T31): one
/// repeating interval per day a switched-on Schedule runs, and one per running session ending at its
/// planned end. The DeviceActivityMonitor extension starts and ends sessions when they fire.
///
/// It stops Schedule and session-end activities that are no longer planned (a Schedule switched off
/// or deleted, a session that ended), and Break intervals (T20) that are over or whose session was
/// lifted, so they don't use up the activity limit. A session-end interval
/// the monitor registered for a session it started is kept while that session's record lasts
/// (`FocalAppliedSessions`), even before the app has ingested the session: switching off or editing
/// its Schedule then never leaves it without an end. An activity already registered with the same
/// interval is left as it is, so re-planning doesn't restart it.
///
/// iOS caps how many activities an app may monitor (`DeviceActivityPlan.ACTIVITY_LIMIT`). Session
/// ends come first: the plan is cut to what fits beside the activities it doesn't own (Breaks, the
/// monitor's session ends), dropped Schedule activities are stopped before the session ends are
/// registered, and if the system still refuses a session end, registered Schedule activities are
/// stopped, last first, until it fits. What was dropped is logged and reported as not registered,
/// so the shared code registers the plan again on the next foreground; the launch catch-up still
/// starts a dropped Schedule's windows while Focal is open.
final class IosDeviceActivityScheduler: NSObject, DeviceActivityScheduler {
    func monitor(plan: DeviceActivityPlan) -> Bool {
        let center = DeviceActivityCenter()
        let now = Date()
        let schedulePrefix = DeviceActivityPlan.companion.SCHEDULE_PREFIX
        let endPrefix = DeviceActivityPlan.companion.SESSION_END_PREFIX
        let records = FocalAppliedSessions.all()
        stopFinishedBreaks(in: center, records: records, now: now)
        let current = center.activities
        let ours = current.filter { $0.rawValue.hasPrefix(schedulePrefix) || $0.rawValue.hasPrefix(endPrefix) }
        // Session ends the monitor registered for its own sessions, which the plan doesn't know yet.
        let monitorEnds = Set(ours.filter { activity in
            let name = activity.rawValue
            guard name.hasPrefix(endPrefix), !plan.names.contains(name) else { return false }
            return records[String(name.dropFirst(endPrefix.count))].map { $0.plannedEnd > now } ?? false
        }.map(\.rawValue))
        let others = current.count - ours.count + monitorEnds.count
        let fitted = plan.fitting(capacity: Int32(Int(DeviceActivityPlan.companion.ACTIVITY_LIMIT) - others))
        let wanted = fitted.names

        let stale = ours.filter { !wanted.contains($0.rawValue) && !monitorEnds.contains($0.rawValue) }
        if !stale.isEmpty {
            center.stopMonitoring(stale)
        }

        var registeredAll = fitted.schedules.count == plan.schedules.count && fitted.sessionEnds.count == plan.sessionEnds.count
        // Schedule activities still registered from before, lowest priority (last) first: stopped one
        // at a time if a session end doesn't fit otherwise.
        var evictable = fitted.schedules.reversed().map { DeviceActivityName($0.name) }.filter { center.activities.contains($0) }
        var evicted = Set<String>()
        for end in fitted.sessionEnds {
            let name = DeviceActivityName(end.name)
            var registered = register(name, Self.schedule(for: end), in: center)
            while !registered, !evictable.isEmpty {
                let victim = evictable.removeFirst()
                center.stopMonitoring([victim])
                evicted.insert(victim.rawValue)
                registered = register(name, Self.schedule(for: end), in: center)
            }
            registeredAll = registered && registeredAll
        }
        for activity in fitted.schedules where !evicted.contains(activity.name) {
            registeredAll = register(DeviceActivityName(activity.name), Self.schedule(for: activity), in: center) && registeredAll
        }
        let dropped = plan.schedules.map(\.name).filter { !wanted.contains($0) || evicted.contains($0) }
        if !dropped.isEmpty {
            registeredAll = false
            NSLog("Focal: too many DeviceActivity intervals; not monitoring %@", dropped.joined(separator: ", "))
        }
        return registeredAll
    }

    /// Break intervals count against the activity limit while registered, and every Break registers
    /// one. The engine stops them when a session is lifted or its Break is over (T20); this catches
    /// any it missed (`DeviceActivityPlan.finishedBreaks` decides which).
    private func stopFinishedBreaks(in center: DeviceActivityCenter, records: [String: FocalAppliedSession], now: Date) {
        let breakPrefix = DeviceActivityPlan.companion.BREAK_PREFIX
        let breaks = center.activities.filter { $0.rawValue.hasPrefix(breakPrefix) }.map { activity in
            let end = center.schedule(for: activity).flatMap { Calendar.current.date(from: $0.intervalEnd) }
            return DeviceActivitySchedulerKt.registeredBreak(
                name: activity.rawValue,
                endsAtEpochMillis: end.map { KotlinLong(value: Int64($0.timeIntervalSince1970 * 1000)) }
            )
        }
        guard !breaks.isEmpty else { return }
        let finished = DeviceActivitySchedulerKt.finishedBreakActivities(
            registered: breaks,
            recordedSessionIds: Set(records.keys),
            nowEpochMillis: Int64(now.timeIntervalSince1970 * 1000)
        )
        if !finished.isEmpty {
            center.stopMonitoring(finished.map { DeviceActivityName($0) })
        }
    }

    /// Registering fails without Screen Time authorisation or past the activity cap; the launch
    /// and foreground catch-up still covers Schedules then. A changed interval (say, a session's
    /// planned end moved) is stopped and registered again for its new end.
    private func register(_ name: DeviceActivityName, _ schedule: DeviceActivitySchedule, in center: DeviceActivityCenter) -> Bool {
        if let current = center.schedule(for: name) {
            if Self.same(current, schedule) { return true }
            center.stopMonitoring([name])
        }
        do {
            try center.startMonitoring(name, during: schedule)
            return true
        } catch {
            return false
        }
    }

    private static func schedule(for end: SessionEndActivity) -> DeviceActivitySchedule {
        let calendar = Calendar.current
        let components: Set<Calendar.Component> = [.year, .month, .day, .hour, .minute, .second]
        return DeviceActivitySchedule(
            intervalStart: calendar.dateComponents(components, from: date(end.startsAt)),
            intervalEnd: calendar.dateComponents(components, from: date(end.endsAt)),
            repeats: false
        )
    }

    private static func schedule(for activity: ScheduleActivity) -> DeviceActivitySchedule {
        DeviceActivitySchedule(
            intervalStart: time(weekday: activity.weekday, hour: activity.startHour, minute: activity.startMinute),
            intervalEnd: time(weekday: activity.endWeekday, hour: activity.endHour, minute: activity.endMinute),
            repeats: true,
            warningTime: activity.warningMinutes > 0 ? DateComponents(minute: Int(activity.warningMinutes)) : nil
        )
    }

    /// Weekday 0 (`DeviceActivityPlan.EVERY_DAY`) leaves the weekday out, so the interval repeats daily.
    private static func time(weekday: Int32, hour: Int32, minute: Int32) -> DateComponents {
        DateComponents(
            hour: Int(hour),
            minute: Int(minute),
            weekday: weekday == DeviceActivityPlan.companion.EVERY_DAY ? nil : Int(weekday)
        )
    }

    private static func same(_ a: DeviceActivitySchedule, _ b: DeviceActivitySchedule) -> Bool {
        a.intervalStart == b.intervalStart && a.intervalEnd == b.intervalEnd
            && a.repeats == b.repeats && a.warningTime == b.warningTime
    }

    private static func date(_ instant: KotlinInstant) -> Date {
        Date(timeIntervalSince1970: TimeInterval(instant.toEpochMilliseconds()) / 1000)
    }
}
