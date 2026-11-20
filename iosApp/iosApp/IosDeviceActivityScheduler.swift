import DeviceActivity
import Foundation
import Shared

/// Registers the DeviceActivity intervals the shared code plans (`DeviceActivityPlan`, T31): one
/// repeating interval per day a switched-on Schedule runs, and one per running session ending at its
/// planned end. The DeviceActivityMonitor extension starts and ends sessions when they fire.
///
/// It stops Schedule and session-end activities that are no longer planned (a Schedule switched off
/// or deleted, a session that ended) and leaves Break intervals alone (T20). An activity already
/// registered with the same interval is left as it is, so re-planning doesn't restart it.
///
/// iOS caps how many activities an app may monitor (reported as 20); the plan lists what matters
/// most first, and whatever the system refuses beyond that is skipped.
final class IosDeviceActivityScheduler: NSObject, DeviceActivityScheduler {
    func monitor(plan: DeviceActivityPlan) {
        let center = DeviceActivityCenter()
        let planned = plan.names
        let stale = center.activities.filter { activity in
            let name = activity.rawValue
            let ours = name.hasPrefix(DeviceActivityPlan.companion.SCHEDULE_PREFIX)
                || name.hasPrefix(DeviceActivityPlan.companion.SESSION_END_PREFIX)
            return ours && !planned.contains(name)
        }
        if !stale.isEmpty {
            center.stopMonitoring(stale)
        }

        for end in plan.sessionEnds {
            register(DeviceActivityName(end.name), Self.schedule(for: end), in: center)
        }
        for activity in plan.schedules {
            register(DeviceActivityName(activity.name), Self.schedule(for: activity), in: center)
        }
    }

    /// Registering fails without Screen Time authorisation or past the activity cap; the launch
    /// and foreground catch-up still covers Schedules then.
    private func register(_ name: DeviceActivityName, _ schedule: DeviceActivitySchedule, in center: DeviceActivityCenter) {
        if let current = center.schedule(for: name), Self.same(current, schedule) { return }
        try? center.startMonitoring(name, during: schedule)
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
