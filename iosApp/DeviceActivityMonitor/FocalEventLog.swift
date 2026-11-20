import Foundation

/// The extension event log (`ExtensionEventLog` in shared/core, fixture `iosApp/Fixtures/events-v1.json`):
/// `{"version": 1, "events": [...]}` in the App Group container. Extensions only append; the app
/// ingests it on launch, decides what each event means (ADR 0008), then removes the events it
/// ingested (`IosContract.withoutIngested`), so the log only holds what is still pending.
enum FocalEventLog {
    /// `EXTENSION_EVENTS_FILE` in Kotlin.
    static let fileName = "extension-events.json"
    /// `ExtensionEventLog.CURRENT_VERSION`.
    static let version = 1

    /// Append one event. Existing events, including types this extension doesn't know, are kept as
    /// they are. A log written by a newer version is left alone. The app compacts the log under the
    /// same file coordination, so neither side overwrites the other's change.
    static func append(type: String, at date: Date, fields: [String: String]) {
        guard let url = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: FocalSnapshotReader.appGroup)?
            .appendingPathComponent(fileName) else { return }

        var coordinationError: NSError?
        NSFileCoordinator(filePresenter: nil)
            .coordinate(writingItemAt: url, options: .forMerging, error: &coordinationError) { url in
                append(type: type, at: date, fields: fields, to: url)
            }
    }

    private static func append(type: String, at date: Date, fields: [String: String], to url: URL) {
        var root: [String: Any] = ["version": version, "events": []]
        if let data = try? Data(contentsOf: url),
           let existing = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let existingVersion = existing["version"] as? Int, existingVersion > version { return }
            root = existing
        }
        var events = root["events"] as? [Any] ?? []
        var event: [String: Any] = [
            "type": type,
            "id": UUID().uuidString,
            // ISO 8601 UTC, whole seconds, as the Kotlin side writes and reads it.
            "at": ISO8601DateFormatter().string(from: date),
        ]
        fields.forEach { event[$0.key] = $0.value }
        events.append(event)
        root["events"] = events
        root["version"] = version

        guard let data = try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys]) else { return }
        try? data.write(to: url, options: .atomic)
    }
}
