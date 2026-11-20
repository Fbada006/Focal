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

/// Sessions the app has ended, so the DeviceActivityMonitor never re-applies one after the app
/// lifted it (T20). The snapshot alone can't say this in time: the app rewrites it only after the
/// session is saved and lifted, and the monitor may read the old one in between.
///
/// Both sides follow an order that closes the race without a lock: the app records the session as
/// ended, then clears its store; the monitor applies the store, then checks the record and clears
/// the store again if the session ended. Whichever runs last leaves the store empty.
enum FocalEndedSessions {
    /// `APP_GROUP_ID` in Kotlin, `FocalSnapshotReader.appGroup` in the extensions.
    private static let appGroup = "group.io.disruption.focal"
    private static let key = "endedSessionIds"
    /// Only a session that ended moments ago can race with the monitor; keep the record small.
    private static let limit = 50

    private static var defaults: UserDefaults? { UserDefaults(suiteName: appGroup) }

    static func record(_ sessionId: String) {
        guard let defaults else { return }
        var ids = defaults.stringArray(forKey: key) ?? []
        guard !ids.contains(sessionId) else { return }
        ids.append(sessionId)
        defaults.set(Array(ids.suffix(limit)), forKey: key)
    }

    static func contains(_ sessionId: String) -> Bool {
        defaults?.stringArray(forKey: key)?.contains(sessionId) ?? false
    }
}
