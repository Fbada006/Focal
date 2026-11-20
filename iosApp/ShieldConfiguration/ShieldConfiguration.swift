import UIKit
import ManagedSettings
import ManagedSettingsUI

/// The Shield for app and App Category blocks (T16, stories 58–64). It reads the app's snapshot from
/// the App Group and shows the Focus Session holding the block (+N more), the time left and a
/// motivational line. Its only buttons are Close and Open Focal: nothing here ends a session or
/// takes a Break.
///
/// The system renders the Shield once from this configuration, so the time left is a static
/// "about N min", not a live countdown like Android's.
final class ShieldConfigurationExtension: ShieldConfigurationDataSource {
    override func configuration(shielding application: Application) -> ShieldConfiguration {
        FocalShieldConfiguration.make(app: application.token, category: nil, domain: nil)
    }

    override func configuration(
        shielding application: Application,
        in category: ActivityCategory
    ) -> ShieldConfiguration {
        FocalShieldConfiguration.make(app: application.token, category: category.token, domain: nil)
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        FocalShieldConfiguration.make(app: nil, category: nil, domain: webDomain.domain)
    }

    override func configuration(
        shielding webDomain: WebDomain,
        in category: ActivityCategory
    ) -> ShieldConfiguration {
        FocalShieldConfiguration.make(app: nil, category: category.token, domain: webDomain.domain)
    }
}

private enum FocalShieldConfiguration {
    static func make(app: ApplicationToken?, category: ActivityCategoryToken?, domain: String?) -> ShieldConfiguration {
        let now = Date()
        let content = FocalShieldContent.make(
            // Sessions applied that the snapshot doesn't list yet count too, such as Schedule
            // sessions the monitor started before Focal has ingested them (T31).
            snapshot: FocalSnapshotReader.load()?.withAppliedSessions(now: now),
            app: app,
            category: category,
            domain: domain,
            now: now
        )
        guard let content else {
            return configuration(title: "Blocked by Focal", subtitle: "A Focus Session is blocking this.")
        }

        var lines: [String] = []
        if content.otherSessions > 0 {
            let noun = content.otherSessions == 1 ? "session" : "sessions"
            lines.append("+\(content.otherSessions) more \(noun) also blocking")
        }
        lines.append("\(timeLeft(until: content.plannedEnd, now: now)) left in this session")
        lines.append("")
        lines.append(content.motivationalLine)
        return configuration(title: content.sessionName, subtitle: lines.joined(separator: "\n"))
    }

    private static func configuration(title: String, subtitle: String) -> ShieldConfiguration {
        ShieldConfiguration(
            backgroundBlurStyle: .systemThickMaterial,
            backgroundColor: .systemBackground,
            title: .init(text: title, color: .label),
            subtitle: .init(text: subtitle, color: .secondaryLabel),
            primaryButtonLabel: .init(text: "Close", color: .white),
            primaryButtonBackgroundColor: .systemBlue,
            secondaryButtonLabel: .init(text: "Open Focal", color: .systemBlue)
        )
    }

    /// "About 1 h 20 min", "About 25 min" or "Under a minute", rounded up so it never undersells.
    private static func timeLeft(until end: Date, now: Date) -> String {
        let seconds = max(0, Int(end.timeIntervalSince(now)))
        if seconds < 60 { return "Under a minute" }
        let minutes = (seconds + 59) / 60
        let hours = minutes / 60
        let rest = minutes % 60
        if hours == 0 { return "About \(minutes) min" }
        return rest == 0 ? "About \(hours) h" : "About \(hours) h \(rest) min"
    }
}

private extension FocalSnapshot {
    /// The running sessions plus those applied but not in the snapshot yet, from their coordinated
    /// records (`FocalAppliedSessions`): the monitor's Schedule sessions before the app ingests them
    /// (T31), or one the app just started. A session the snapshot lists is counted once, and one
    /// that has ended (its record is deleted then, or `FocalEndedSessions` has it) not at all.
    func withAppliedSessions(now: Date) -> FocalSnapshot {
        let known = Set(runningSessions.map(\.id))
        let pending = FocalAppliedSessions.all().compactMap { id, record -> FocalSnapshot.Session? in
            guard !known.contains(id), let name = record.name, record.plannedEnd > now,
                  !FocalEndedSessions.contains(id) else { return nil }
            return FocalSnapshot.Session(
                id: id,
                name: name,
                scheduleId: record.scheduleId,
                plannedEnd: record.plannedEnd,
                breakEndsAt: record.breakEndsAt,
                apps: record.apps,
                appCategories: record.appCategories,
                domains: record.domains
            )
        }
        guard !pending.isEmpty else { return self }
        return FocalSnapshot(
            version: version,
            blockLists: blockLists,
            schedules: schedules,
            runningSessions: runningSessions + pending
        )
    }
}
