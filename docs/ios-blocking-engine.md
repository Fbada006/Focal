# iOS blocking engine: implementation notes

iOS blocking is built on Apple's Screen Time frameworks: FamilyControls (authorization and the app/website picker), ManagedSettings (applying blocks and Shields) and DeviceActivity (time-based triggers that run in an extension even when Focal isn't). See [ADR 0001](adr/0001-build-android-and-ios-in-parallel.md), [ADR 0004](adr/0004-shared-compose-ui-with-swiftui-islands.md), [ADR 0006](adr/0006-concurrent-focus-sessions.md).

Minimum deployment target: **iOS 17**. iOS 16 is the floor for individual (self) FamilyControls authorization and web-domain shields, but 17 avoids the widely reported iOS 16 Screen Time reliability bugs. Android's minimum is API 26.

## Open risks to verify early

- **Breaks while Focal isn't running.** A Break must lift blocking for one session and restore it 5 minutes later without the app. The natural tool is a DeviceActivity interval whose end re-applies the session's store, but DeviceActivity schedules have a minimum interval length (believed to be 15 minutes). Known workaround to prototype: register an interval that started in the past so its end falls at the Break's end. Verify on device before committing.
- **Named store cap.** Each running Focus Session gets its own named `ManagedSettingsStore` (ADR 0006); Apple caps how many an app may use. Confirm the limit.
- **"Open Focal" from the Shield.** A ShieldAction extension cannot open the containing app directly; it can only close the Shield or run code in the background. "Open Focal" will most likely post a local notification the user taps to open Focal. Prototype and decide whether that friction is acceptable.
- **Schedule-started sessions begin in the DeviceActivityMonitor extension.** The extension must write the session record (start, Block List, Strictness) to App Group storage so Focal sees it on next launch, and stats/Streak are computed from it.
