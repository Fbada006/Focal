import Foundation
import ManagedSettings

/// Writes one Focus Session's blocked set into its named `ManagedSettingsStore` (ADR 0006). Shared by
/// the app's `IosBlockingEngine` and the DeviceActivityMonitor extension, so a store re-applied at a
/// Break's end blocks exactly what the app applied. It applies what it is given and decides nothing.
enum FocalStore {
    /// `managedSettingsStoreName` in Kotlin (`IosBlockingSupport.kt`); keep the two identical.
    static func named(sessionId: String) -> ManagedSettingsStore {
        named("focal-session-" + sessionId)
    }

    /// Stores are always opened by name, never cached: one applied by the app can be cleared by an
    /// extension and the other way round.
    static func named(_ name: String) -> ManagedSettingsStore {
        ManagedSettingsStore(named: ManagedSettingsStore.Name(name))
    }

    /// Replace what [store] blocks. Apps and App Categories are serialised picker tokens (T15).
    static func apply<Apps: Sequence, Categories: Sequence, Domains: Sequence>(
        apps: Apps,
        appCategories: Categories,
        domains: Domains,
        to store: ManagedSettingsStore
    ) where Apps.Element == String, Categories.Element == String, Domains.Element == String {
        let appTokens: Set<ApplicationToken> = decode(apps)
        let categoryTokens: Set<ActivityCategoryToken> = decode(appCategories)
        // Focal's websites are typed domains, not picker tokens, so they can't use `shield.webDomains`.
        // Safari's content filter blocks them with the system's own page instead of Focal's Shield.
        let webDomains = Set(domains.map { WebDomain(domain: $0) })

        // Set each field straight to its new value, never clear first: clearing would briefly
        // unblock what the session still blocks, which a Delayed or Locked session must never do.
        store.shield.applications = appTokens.isEmpty ? nil : appTokens
        store.shield.applicationCategories = categoryTokens.isEmpty ? nil : .specific(categoryTokens)
        store.webContent.blockedByFilter = webDomains.isEmpty ? nil : .specific(webDomains)
    }

    /// Tokens are stored as base64 of the token's JSON by the app picker (T15).
    private static func decode<T: Decodable & Hashable, S: Sequence>(_ encoded: S) -> Set<T> where S.Element == String {
        Set(encoded.compactMap { value in
            guard let data = Data(base64Encoded: value) else { return nil }
            return try? JSONDecoder().decode(T.self, from: data)
        })
    }
}

/// `APP_GROUP_ID` in Kotlin, `FocalSnapshotReader.appGroup` in the extensions.
private let appGroup = "group.io.disruption.focal"

/// What the app's engine last applied for each running Focus Session, and the Break it is on (T20).
/// The DeviceActivityMonitor re-applies a session's store from this at the Break's end, not from the
/// snapshot: the snapshot is rewritten asynchronously after the database changes, so it can be older
/// than what the engine just applied (an addition to the Block List) or miss a Break just taken.
///
/// One file per session in the App Group container. Every change to a session's record and its
/// store happens inside `update`, under `NSFileCoordinator` on that file, which serialises the app
/// and the extension across processes: the monitor never re-applies a set older than the app's
/// latest, nor a session the app has just lifted (lifting deletes the record in the same step).
struct FocalAppliedSession: Codable, Equatable {
    var apps: [String]
    var appCategories: [String]
    var domains: [String]
    var plannedEnd: Date
    /// When the Break in progress (or the last one) ends; the store is cleared until then.
    var breakEndsAt: Date?
}

enum FocalAppliedSessions {
    private static let directory = "applied-sessions"

    private static func url(sessionId: String) -> URL? {
        guard let container = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup) else { return nil }
        let folder = container.appendingPathComponent(directory, isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder.appendingPathComponent(sessionId + ".json")
    }

    /// Run [body] with exclusive access to [sessionId]'s record, across the app and its extensions;
    /// change the store inside it too. Setting the record to nil deletes it. Returns false when it
    /// couldn't run [body] (no App Group container, or coordination failed).
    @discardableResult
    static func update(sessionId: String, _ body: (inout FocalAppliedSession?) -> Void) -> Bool {
        guard let url = url(sessionId: sessionId) else { return false }
        var ran = false
        var coordinationError: NSError?
        NSFileCoordinator(filePresenter: nil)
            .coordinate(writingItemAt: url, options: .forMerging, error: &coordinationError) { url in
                let before = (try? Data(contentsOf: url)).flatMap { try? JSONDecoder().decode(FocalAppliedSession.self, from: $0) }
                var record = before
                body(&record)
                ran = true
                guard record != before else { return }
                if let record, let data = try? JSONEncoder().encode(record) {
                    try? data.write(to: url, options: .atomic)
                } else {
                    try? FileManager.default.removeItem(at: url)
                }
            }
        return ran
    }
}

/// Sessions the app has ended, so the extensions can tell before the snapshot catches up: the app
/// rewrites it only after the session is saved and lifted (T20). One empty marker file per session
/// in the App Group container: creating a file is visible to the other processes at once, and no
/// write reads and rewrites a shared list, so two writers never lose each other's marker.
enum FocalEndedSessions {
    private static let directory = "ended-sessions"
    /// Only a session that ended moments ago can race with an extension; keep the record small.
    private static let limit = 50

    private static var folder: URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroup)?
            .appendingPathComponent(directory, isDirectory: true)
    }

    static func record(_ sessionId: String) {
        guard let folder else { return }
        let fileManager = FileManager.default
        try? fileManager.createDirectory(at: folder, withIntermediateDirectories: true)
        try? Data().write(to: folder.appendingPathComponent(sessionId), options: .atomic)
        // Forget the oldest markers beyond the limit.
        let keys: [URLResourceKey] = [.creationDateKey]
        guard let markers = try? fileManager.contentsOfDirectory(at: folder, includingPropertiesForKeys: keys),
              markers.count > limit else { return }
        let oldestFirst = markers.sorted {
            let a = (try? $0.resourceValues(forKeys: Set(keys)).creationDate) ?? .distantPast
            let b = (try? $1.resourceValues(forKeys: Set(keys)).creationDate) ?? .distantPast
            return a < b
        }
        oldestFirst.prefix(markers.count - limit).forEach { try? fileManager.removeItem(at: $0) }
    }

    static func contains(_ sessionId: String) -> Bool {
        guard let folder else { return false }
        return FileManager.default.fileExists(atPath: folder.appendingPathComponent(sessionId).path)
    }
}
