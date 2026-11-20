import SwiftUI
import FamilyControls
import ManagedSettings
import Shared

/// Presents Apple's FamilyActivityPicker for the Apps tab and hands the serialised tokens back to
/// the shared code (T15). The tokens are opaque to Kotlin; only the Screen Time extensions can
/// turn them back into apps (T16).
///
/// Websites are not picked here: a Block List keeps websites as typed domains in the Websites tab
/// (on both platforms, and the iOS engine blocks them by domain). The picker can't hide its
/// websites section, so the sheet says so and refuses to save website picks.
final class ScreenTimeAppPicker: NSObject, IosScreenTimePicker {
    static let shared = ScreenTimeAppPicker()

    private var blockListId: String?

    func present(blockListId: String, apps: Set<String>, categories: Set<String>) {
        // Onboarding lets the user skip Screen Time access, and the picker shows nothing without it.
        guard AuthorizationCenter.shared.authorizationStatus == .approved else {
            Task { @MainActor in
                do {
                    try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
                    self.showPicker(blockListId: blockListId, apps: apps, categories: categories)
                } catch {
                    self.showAlert(
                        title: "Screen Time access needed",
                        message: "Focal needs Screen Time access to pick apps and App Categories. Allow it when iOS asks, or turn it on for Focal in Settings › Screen Time."
                    )
                }
            }
            return
        }
        showPicker(blockListId: blockListId, apps: apps, categories: categories)
    }

    private func showPicker(blockListId: String, apps: Set<String>, categories: Set<String>) {
        self.blockListId = blockListId
        // Start from what the Block List already holds, so Done keeps earlier picks. A picked
        // App Category stays a category token (not the apps it holds today), so apps installed
        // later in it are blocked too.
        var current = FamilyActivitySelection(includeEntireCategory: true)
        current.applicationTokens = Set(apps.compactMap { Self.deserialise($0, as: ApplicationToken.self) })
        current.categoryTokens = Set(categories.compactMap { Self.deserialise($0, as: ActivityCategoryToken.self) })
        let sheet = FamilyPickerSheet(selection: current) { [weak self] selection in
            self?.store(selection)
        }
        let host = UIHostingController(rootView: sheet)
        host.modalPresentationStyle = .pageSheet
        topViewController()?.present(host, animated: true)
    }

    private func store(_ selection: FamilyActivitySelection) {
        guard let blockListId else { return }
        let apps = Set(selection.applicationTokens.compactMap(Self.serialise))
        let categories = Set(selection.categoryTokens.compactMap(Self.serialise))
        IosScreenTimeBridge.shared.selectionMade(blockListId: blockListId, apps: apps, categories: categories)
    }

    // Tokens are Codable structs, not objects, so JSON is how they round-trip (T16 decodes them).
    private static func serialise(_ token: ApplicationToken) -> String? {
        guard let data = try? JSONEncoder().encode(token) else { return nil }
        return data.base64EncodedString()
    }

    private static func serialise(_ token: ActivityCategoryToken) -> String? {
        guard let data = try? JSONEncoder().encode(token) else { return nil }
        return data.base64EncodedString()
    }

    private static func deserialise<T: Decodable>(_ string: String, as type: T.Type) -> T? {
        guard let data = Data(base64Encoded: string) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        topViewController()?.present(alert, animated: true)
    }

    private func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var top = scene?.windows.first(where: \.isKeyWindow)?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

private struct FamilyPickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var selection: FamilyActivitySelection
    @State private var pickedWebsites = false
    let onDone: (FamilyActivitySelection) -> Void

    init(selection: FamilyActivitySelection, onDone: @escaping (FamilyActivitySelection) -> Void) {
        _selection = State(initialValue: selection)
        self.onDone = onDone
    }

    var body: some View {
        NavigationStack {
            FamilyActivityPicker(
                footerText: "To block websites, add them in the Websites tab.",
                selection: $selection
            )
                .navigationTitle("Choose apps")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") {
                            // Website picks would be lost: Focal keeps websites as typed domains.
                            if !selection.webDomainTokens.isEmpty {
                                pickedWebsites = true
                                return
                            }
                            onDone(selection)
                            dismiss()
                        }
                    }
                }
                .alert("Add websites in the Websites tab", isPresented: $pickedWebsites) {
                    Button("OK", role: .cancel) {}
                } message: {
                    Text("Focal blocks websites by the domains in a Block List's Websites tab. Unselect the websites here, then add their domains there.")
                }
        }
    }
}
