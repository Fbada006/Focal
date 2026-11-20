# iOS blocking engine: implementation notes

iOS blocking is built on Apple's Screen Time frameworks: FamilyControls (authorization and the app/website picker), ManagedSettings (applying blocks and Shields) and DeviceActivity (time-based triggers that run in an extension even when Focal isn't). See [ADR 0001](adr/0001-build-android-and-ios-in-parallel.md), [ADR 0004](adr/0004-shared-compose-ui-with-swiftui-islands.md), [ADR 0006](adr/0006-concurrent-focus-sessions.md).

Minimum deployment target: **iOS 17**. iOS 16 is the floor for individual (self) FamilyControls authorization and web-domain shields, but 17 avoids the widely reported iOS 16 Screen Time reliability bugs. Android's minimum is API 26.

## How it is built (T16)

- **Targets.** `iosApp` (the app, linking the shared Kotlin framework) embeds two Swift-only extensions: `ShieldConfiguration` and `ShieldAction`. `iosApp/ShieldShared/FocalSnapshot.swift` is compiled into both; they never link `Shared` (ADR 0008). Every target has the Family Controls entitlement and the App Group `group.io.disruption.focal` (`APP_GROUP_ID` in `StartFocal.kt`). Extension bundle ids are the app's id plus `.ShieldConfiguration` / `.ShieldAction`.
- **Engine.** `IosBlockingEngine.swift` implements the shared `BlockingEngine` and is passed to `startFocal`. Each Focus Session gets its own `ManagedSettingsStore` named `focal-session-<session id>` (`managedSettingsStoreName` in `IosBlockingSupport.kt`). `apply` clears the store and sets its apps (`shield.applications`), App Categories (`shield.applicationCategories`) and websites; `lift` and `pauseUntil` clear it. Stores are always opened by name, never cached, so a session applied before a relaunch, or by an extension, can still be lifted. Re-applying at a Break's end without the app is T20.
- **Websites.** Focal's websites are typed domains, but `shield.webDomains` only takes picker tokens. Domains are blocked through Safari's content filter (`webContent.blockedByFilter = .specific`), which shows the system's "restricted" page, not Focal's Shield. If that matters, the fix is letting the picker choose web domains as tokens.
- **Snapshot.** `IosSnapshotWriter` (shared `iosMain`) writes `snapshot.json` to the App Group container whenever Block Lists, Schedules or running sessions change.
- **Shield.** The configuration extension reads the snapshot, finds the running sessions (not on a Break) that block the app, App Category or domain, and shows the soonest-ending one as the title, then "+N more sessions also blocking", the time left and the same motivational line Android picks (`BlockingState`). The system renders it once, so the time left is "About N min" rather than a live countdown. If no session matches (say, a stale snapshot), it names the soonest-ending running session.
- **Shield buttons.** Close and Open Focal, both closing the Shield. Open Focal posts a local notification ("Open Focal", one at a time) that the user taps to open Focal, since a ShieldAction extension can't open its app. Nothing on the Shield ends a session or takes a Break.

## Open risks to verify early

- **Breaks while Focal isn't running.** A Break must lift blocking for one session and restore it 5 minutes later without the app. The natural tool is a DeviceActivity interval whose end re-applies the session's store, but DeviceActivity schedules have a minimum interval length (believed to be 15 minutes). Known workaround to prototype: register an interval that started in the past so its end falls at the Break's end. Verify on device before committing.
- **Named store cap.** Each running Focus Session gets its own named `ManagedSettingsStore` (ADR 0006); Apple caps how many an app may use. Confirm the limit.
- **"Open Focal" from the Shield.** A ShieldAction extension cannot open the containing app directly; it can only close the Shield or run code in the background. "Open Focal" will most likely post a local notification the user taps to open Focal. Prototype and decide whether that friction is acceptable.
- **Schedule-started sessions begin in the DeviceActivityMonitor extension.** The extension must write the session record (start, Block List, Strictness) to App Group storage so Focal sees it on next launch, and stats/Streak are computed from it.

## Picking apps (T15)

- The Apps tab presents `FamilyActivityPicker` and stores the serialised `ApplicationToken`s and `ActivityCategoryToken`s on the Block List. It asks for Screen Time authorization first, since onboarding lets the user skip it.
- The selection uses `includeEntireCategory: true`, so a picked App Category stays a category token and covers apps installed later.
- Websites are not picked here. A Block List keeps websites as typed domains in the Websites tab on both platforms, so the picker's web-domain tokens have nowhere to go. The picker can't hide its websites section, so the sheet says so in its footer and won't save while websites are selected.
