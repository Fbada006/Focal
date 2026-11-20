import Foundation
import ManagedSettings

/// The part of the app's snapshot (`IosSnapshot` in shared/core, fixture `iosApp/Fixtures/snapshot-v2.json`)
/// the extensions need (T16, T31, ADR 0008). The extensions are Swift-only and read the file
/// directly; everything they show or apply was decided by the app. Unknown fields are ignored.
struct FocalSnapshot: Decodable {
    struct Session: Codable {
        let id: String
        let name: String
        /// The Schedule that started it, if any (T31).
        let scheduleId: String?
        let plannedEnd: Date
        let breakEndsAt: Date?
        /// Serialised tokens from Apple's picker: base64 of the token's JSON (T15).
        let apps: [String]
        let appCategories: [String]
        let domains: [String]

        /// Running and not on a Break, so its store is applied (mirrors `BlockingState`).
        func isBlocking(at now: Date) -> Bool {
            guard plannedEnd > now else { return false }
            if let breakEndsAt, breakEndsAt > now { return false }
            return true
        }

        func blocks(app: ApplicationToken?, category: ActivityCategoryToken?, domain: String?) -> Bool {
            if let app, apps.contains(where: { FocalTokenCoding.decode($0) == app }) { return true }
            if let category,
               appCategories.contains(where: { FocalTokenCoding.decode($0) == category }) { return true }
            if let domain = domain?.lowercased(),
               domains.contains(where: { domain == $0 || domain.hasSuffix("." + $0) }) { return true }
            return false
        }
    }

    /// A switched-on Schedule (`SnapshotSchedule`); the monitor extension starts its sessions (T31).
    struct Schedule: Decodable {
        let id: String
        let name: String
        /// `HH:mm`, local time. An end at or before the start runs overnight.
        let start: String
        let end: String
        let blockListId: String
        /// `Normal`, `Delayed` or `Locked`.
        let strictness: String
        /// The sessions it started lately, running or ended (`SnapshotScheduleSession`). A window
        /// one of them overlaps gets no other session.
        let sessions: [StartedSession]?
    }

    struct StartedSession: Decodable {
        let startedAt: Date
        let plannedEnd: Date
    }

    struct BlockList: Decodable {
        let id: String
        let apps: [String]
        let appCategories: [String]
        let domains: [String]

        var isEmpty: Bool { apps.isEmpty && appCategories.isEmpty && domains.isEmpty }
    }

    let version: Int
    let blockLists: [BlockList]
    let schedules: [Schedule]
    let runningSessions: [Session]

}

enum FocalSnapshotReader {
    /// Shared with the app; matches `APP_GROUP_ID` and `SNAPSHOT_FILE` in Kotlin.
    static let appGroup = "group.io.disruption.focal"
    static let fileName = "snapshot.json"
    /// `IosSnapshot.CURRENT_VERSION`. A newer snapshot is not read.
    static let supportedVersion = 2

    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()

    static func load() -> FocalSnapshot? {
        guard let url = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup)?
            .appendingPathComponent(fileName),
            let data = try? Data(contentsOf: url),
            let snapshot = try? decoder.decode(FocalSnapshot.self, from: data),
            snapshot.version <= supportedVersion else { return nil }
        return snapshot
    }
}

/// The token encoding shared with the app picker (T15): base64 of the token's JSON.
enum FocalTokenCoding {
    static func decode<T: Decodable>(_ value: String) -> T? {
        guard let data = Data(base64Encoded: value) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }
}

/// What the Shield says for one blocked app or website, mirroring `BlockingState` and
/// `ShieldContent` in shared/core so both platforms show the same thing.
struct FocalShieldContent {
    let sessionName: String
    /// Running sessions beyond the holding one that also block this app or website.
    let otherSessions: Int
    let plannedEnd: Date
    let motivationalLine: String

    /// The soonest-ending blocking session holds the block: it is the first moment this could come back.
    static func make(
        snapshot: FocalSnapshot?,
        app: ApplicationToken?,
        category: ActivityCategoryToken?,
        domain: String?,
        now: Date
    ) -> FocalShieldContent? {
        guard let snapshot else { return nil }
        let running = snapshot.runningSessions.filter { $0.isBlocking(at: now) }
        let blocking = running.filter { $0.blocks(app: app, category: category, domain: domain) }
        // A token the snapshot doesn't know (say, a stale snapshot) still deserves an explanation,
        // so fall back to the soonest-ending running session, without claiming "+N more".
        let holding = (blocking.isEmpty ? running : blocking).min { $0.plannedEnd < $1.plannedEnd }
        guard let holding else { return nil }
        return FocalShieldContent(
            sessionName: holding.name,
            otherSessions: max(0, blocking.count - 1),
            plannedEnd: holding.plannedEnd,
            motivationalLine: motivationalLine(sessionId: holding.id)
        )
    }

    /// `BlockingState.MOTIVATIONAL_LINES`; keep the two lists identical.
    static let motivationalLines = [
        "This is the work that counts.",
        "You chose this. Stay with it.",
        "Future you says thanks.",
        "One thing at a time.",
        "The pull is the point.",
        "Keep going, you're doing it.",
    ]

    /// Picks the same line as Android: Kotlin's `String.hashCode()` (UTF-16, 32-bit wrapping) `mod` count.
    static func motivationalLine(sessionId: String) -> String {
        var hash: Int32 = 0
        for unit in sessionId.utf16 {
            hash = hash &* 31 &+ Int32(unit)
        }
        let count = Int32(motivationalLines.count)
        let index = ((hash % count) + count) % count
        return motivationalLines[Int(index)]
    }
}
