import Foundation
import ManagedSettings
import Shared

/// The iOS `BlockingEngine` (T16, ADR 0006): one named `ManagedSettingsStore` per Focus Session.
/// The system enforces the most restrictive combination of all stores, so lifting one session never
/// unblocks what another still blocks. It applies exactly what the session's own blocked set says
/// and makes no domain decisions.
///
/// Named stores persist across launches and are shared with the extensions, so a store is always
/// looked up by name rather than remembered: a session applied before a relaunch, or started by the
/// DeviceActivityMonitor extension, can still be lifted.
final class IosBlockingEngine: NSObject, BlockingEngine {
    func apply(session: FocusSession, completionHandler: @escaping (Error?) -> Void) {
        let store = Self.store(named: IosBlockingSupportKt.managedSettingsStoreName(session: session))
        // Re-applying replaces what this session blocks (additions while it runs).
        store.clearAllSettings()

        let blocked = session.blocked
        let apps: Set<ApplicationToken> = Self.decode(blocked.apps)
        if !apps.isEmpty {
            store.shield.applications = apps
        }
        let categories: Set<ActivityCategoryToken> = Self.decode(blocked.appCategories)
        if !categories.isEmpty {
            store.shield.applicationCategories = .specific(categories)
        }
        // Focal's websites are typed domains, not picker tokens, so they can't use `shield.webDomains`.
        // Safari's content filter blocks them with the system's own page instead of Focal's Shield.
        let domains = Set(blocked.domains.map { WebDomain(domain: $0) })
        if !domains.isEmpty {
            store.webContent.blockedByFilter = .specific(domains)
        }
        completionHandler(nil)
    }

    func lift(sessionId: Any, completionHandler: @escaping (Error?) -> Void) {
        Self.store(named: IosBlockingSupportKt.managedSettingsStoreName(sessionId: sessionId)).clearAllSettings()
        completionHandler(nil)
    }

    /// A Break lifts this session's blocking. Re-applying it when the Break ends, even while Focal
    /// isn't running, belongs to the DeviceActivityMonitor extension (T20), which reads the session's
    /// `breakEndsAt` from the snapshot.
    func pauseUntil(sessionId: Any, until: KotlinInstant, completionHandler: @escaping (Error?) -> Void) {
        Self.store(named: IosBlockingSupportKt.managedSettingsStoreName(sessionId: sessionId)).clearAllSettings()
        completionHandler(nil)
    }

    private static func store(named name: String) -> ManagedSettingsStore {
        ManagedSettingsStore(named: ManagedSettingsStore.Name(name))
    }

    /// Tokens are stored as base64 of the token's JSON by the app picker (T15).
    private static func decode<T: Decodable & Hashable>(_ encoded: Set<String>) -> Set<T> {
        Set(encoded.compactMap { value in
            guard let data = Data(base64Encoded: value) else { return nil }
            return try? JSONDecoder().decode(T.self, from: data)
        })
    }
}
