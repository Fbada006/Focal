# Store submission prep (T36)

> **DRAFT for a human to review and submit.** Nothing here has been entered into Play Console or App Store Connect. Placeholders in angle brackets (`<support email>`, `<developer name>`, `<privacy policy URL>`) must be filled in by a human. The Apple Family Controls entitlement request (T2) and the submissions themselves are not done by this document.

Everything below is grounded in the code and docs as of this branch: `androidApp/src/main/AndroidManifest.xml`, `androidApp/src/main/res/xml/focal_accessibility_service.xml`, `docs/android-blocking-engine.md`, `docs/ios-blocking-engine.md`, the `iosApp` entitlements and targets, and ADR 0002, 0003 and 0005. Re-check the answers against the code before submitting; if behaviour changed, the code wins.

Related drafts: [privacy policy](privacy-policy.md), [store listing copy](store-listing.md).

## 0. Facts the answers rely on

- Focal has **no backend, no accounts, no analytics, no ads, no third-party SDKs that send data** (ADR 0003). The Android manifest declares no `INTERNET` permission.
- Block Lists, Schedules, Focus Sessions and stats are stored on the device only.
- Android blocking uses one accessibility service (`FocalAccessibilityService`), a `specialUse` foreground service (`RunningSessionService`), exact alarms (`ScheduleAlarms`) and notifications.
- iOS blocking uses FamilyControls, ManagedSettings and DeviceActivity, with three Swift-only extensions sharing an App Group.
- Locked is best-effort on Android (ADR 0002); it is enforced by the system on iOS.

---

## 1. Google Play

### 1.1 Permissions declared in the manifest

| Permission | Used for |
| --- | --- |
| `POST_NOTIFICATIONS` | Running Focus Session notification; "Locked session Broken" notification (Android only; on iOS the only notification is the Shield's "Open Focal", see 2.2) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | `RunningSessionService` |
| `RECEIVE_BOOT_COMPLETED` | Re-apply running Focus Sessions and re-arm Schedule alarms after reboot or app update |
| `SCHEDULE_EXACT_ALARM` | Start and end Schedules on time |
| `PACKAGE_USAGE_STATS` (app-op, optional) | Rank the Apps tab's suggestions by the last 7 days of system usage; the user grants it in Usage access |
| `BIND_ACCESSIBILITY_SERVICE` (on the service) | Foreground app and browser address-bar detection |

Not declared, on purpose: `QUERY_ALL_PACKAGES`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `USE_EXACT_ALARM`, `SYSTEM_ALERT_WINDOW`, `INTERNET`.

### 1.2 Accessibility API declaration (Permissions declaration form)

**Core purpose of the app.** Focal is a focus app. It blocks apps and websites the user chose, during Focus Sessions the user started or scheduled, so the user can concentrate.

**Is the accessibility service essential to that core purpose?** Yes. Android offers no other way for an ordinary app to learn which app or website is in front of the user so it can show the Shield (the screen shown in place of a blocked app or website). Without the service Focal cannot block anything.

**Declared service configuration** (`focal_accessibility_service.xml`):

- Event types: `typeWindowStateChanged`, `typeWindowContentChanged`, `typeViewTextChanged`.
- `canRetrieveWindowContent="true"`, `flagReportViewIds`, feedback type generic, notification timeout 50 ms.
- `packageNames` is unset because foreground app detection needs every package. Content and text events from any package that is not a supported browser are dropped on arrival.

**What the service reads, and why.**

1. *Window state changes (all apps):* the package name of the app that came to the foreground. Used to decide whether a running Focus Session blocks it. Nothing else about the window is read. The service receives these events, and reads supported browsers' address bars (below), whenever it is turned on, not only while a session runs; between sessions nothing matches and nothing is shown.
2. *Supported browsers only (Chrome family, Edge, Brave, Vivaldi, Kiwi, Samsung Internet, Firefox family, Opera, DuckDuckGo; list in `SupportedBrowsers`, `shared/core`):* the text of the browser's **address bar**, found through its view ID, to learn which website is open so a blocked website can be Shielded. Content-change and text-change events are used only to notice navigation inside an open browser window. Text in a focused address bar (the user typing) is ignored; only the committed hostname is used.
3. The service does **not** read page content, form fields, passwords, messages, or text from any other app. It does not read the address bar of browsers that are not on the supported list. Non-hostname text (search terms, an empty tab) is discarded.

**What the service does.** Draws the Shield as an accessibility overlay window over a blocked app or website, and can send the user to the Home screen (`GLOBAL_ACTION_HOME`) so the blocked app is not left running underneath. It does not perform clicks, gestures or text entry in other apps, and (ADR 0002) it deliberately does not block Settings or uninstallation.

**Is data collected, stored or shared?** No. Foreground package names and hostnames are compared with the user's Block Lists in memory and discarded. They are not logged, stored, transmitted or shared. Focal has no network permission and no backend (ADR 0003). The only things stored are what the user entered (Block Lists, Schedules) and the outcome and duration of their Focus Sessions.

**Prominent disclosure and consent.** Before the user is sent to the system accessibility settings, an in-app explanation screen states what the service does and does not read (onboarding, T34). The service description shown in system settings (`accessibility_service_description`) reads: "Focal uses this to notice which app is in the foreground and show the Shield over apps and websites blocked by a running Focus Session. In supported browsers it reads only the address bar, to see which website is open. Nothing leaves your device." Human to verify: the in-app disclosure screen wording matches the above and has an explicit "Continue" / "Not now" choice before the settings intent is launched.

**Short answer for the "describe the functionality" field (paste-ready).**

> Focal blocks distracting apps and websites during Focus Sessions the user starts or schedules. The accessibility service detects which app is in the foreground (package name) and, in supported browsers, reads only the address bar to see which website is open. When the app or website is on the user's Block List for a running session, Focal shows a blocking screen. No accessibility data is stored, transmitted or shared; Focal has no backend and no network permission.

### 1.3 Review video shot list

Play requires a video demonstrating the prominent disclosure and the feature. Record on a physical device, screen recording with audio or captions, about 2 minutes. Suggested shots:

1. Fresh install, open Focal. Show the first-run onboarding up to the accessibility explanation screen.
2. The in-app prominent disclosure: linger on the text so it is readable. Tap "Continue".
3. System Accessibility settings: open "Focal app blocking" and show the service description. Toggle on and accept the system confirmation dialog (shows the permissions the service requests).
4. Back in Focal: create a Block List with one app (for example a social app) and one website (for example a news site) typed as a domain.
5. Start a Normal Focus Session (short, for example 15 minutes) with that Block List. Show the running-session notification.
6. Open the blocked app. Show the Shield: the Focus Session name, time left, and only "Close" and "Open Focal".
7. Open a supported browser (for example Chrome) and navigate to an unblocked site first, to show nothing is blocked and nothing is shown. Then navigate to the blocked website and show the Shield appearing; leave it on screen (it covers the browser, so there is no navigating away from it).
8. On that Shield, tap "Open Focal" and end the Normal session. Show the Shield no longer appears.
9. Show Focal's permissions summary and, to prove the claim, system App info > Permissions with no network or other sensitive permissions requested (or the Play listing's permissions screen).
10. Show turning the service off in Accessibility settings, and Focal's fix-it banner prompting to restore it.

Upload the video as an unlisted YouTube link or per Play's current instructions: `<review video URL>`.

### 1.4 Foreground service: `specialUse` justification

The manifest sets the subtype property on `RunningSessionService`:

> Blocks distracting apps and websites during a Focus Session the user started or scheduled, and keeps its remaining time visible.

**Play Console justification text (paste-ready).**

> Focal's `RunningSessionService` runs while at least one Focus Session is running. A Focus Session is a period the user explicitly started (or a Schedule they created started) in which chosen apps and websites are blocked. The service keeps Focal's process alive so blocking, Break handling and session end are not lost when the system reclaims memory, and it shows an ongoing, user-visible notification with the remaining time and what is blocked. It stops when the last running session ends. None of the standard foreground service types fits: it is not media playback, location, health, data sync or a phone call; it supports a user-initiated, time-boxed blocking feature. The service starts when a Focus Session starts (from a user action or an exact alarm for a user-created Schedule) and, while a session is still running, when Focal restores it: after a reboot or app update (`BootReceiver`), when Focal's process restarts, and when the system restarts the service itself (`START_STICKY`). The user can end Normal and Delayed sessions (Locked sessions only once their time runs out) and thereby stop the service.

Video demo for this: shot 5 above (notification) and shot 8 (session ends, notification disappears).

### 1.5 `SCHEDULE_EXACT_ALARM` justification

Focal declares `SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`, because Play allows the latter only for alarm-clock and calendar apps.

> Schedules are a core feature: the user sets days, a start time and an end time, and Focal starts and ends a Focus Session at exactly those times (for example a work block from 09:00 to 12:00) even when Focal is closed. A late start would let the user into blocked apps during the window they asked to be protected, and a late end would block them after it. `ScheduleAlarms` arms a single exact alarm for the soonest next Schedule start or session end (`setExactAndAllowWhileIdle`). The permission is requested only when the user creates a Schedule, with an explanation and an "Open alarm settings" button; the Schedule is saved either way. If the permission is denied or revoked, Focal falls back to an inexact alarm and blocking still works through the accessibility service, but starts may be delayed.

### 1.6 `POST_NOTIFICATIONS` use

> Focal posts (1) the ongoing notification required for the running Focus Session foreground service, showing what is blocked and the time left, and (2) a one-off "Locked session Broken" notification if the user revokes a permission that a Locked session depends on. It sends no marketing, promotional or engagement notifications. The permission is asked in context, before the first Focus Session.

### 1.7 Data safety form

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (no data leaves the device) |
| Do you provide a way for users to request that their data be deleted? | Not applicable; uninstalling the app deletes all data Focal stores on the device. While `allowBackup` is on, a copy in the user's own Google backup is kept and deleted under their backup settings, and may be restored on reinstall (see below). |
| Data types collected | None |
| Data types shared | None |
| Third-party SDKs that collect data | None (human to confirm by checking the release dependency list, including transitive libraries, before submitting) |

Notes for the human:

- Foreground package names and visited hostnames are processed on the device only and are not "collected" under Play's definition (not transmitted off the device). If Play's reviewer asks, point to ADR 0003 and the absent `INTERNET` permission.
- The manifest has `android:allowBackup="true"`, so Android Auto Backup may copy Focal's app data to the user's own Google account backup. That is an OS feature under the user's control, not collection by the developer; the privacy policy says so. Decide whether to keep it (ADR 0002 notes it may restore data on reinstall).
- Content rating, target audience and ads declarations are separate forms: Focal contains no ads. Do not mark it as designed for children without a human decision.

### 1.8 Permissions Focal does not use, and why

- **`QUERY_ALL_PACKAGES`**: not requested. The Apps tab lists launchable apps through a `<queries>` entry for the `MAIN`/`LAUNCHER` intent. Every app a user can open from the launcher is visible that way, and those are the only apps worth blocking. Accessibility events carry the foreground package name regardless of package visibility. Play restricts `QUERY_ALL_PACKAGES` to apps whose core purpose needs it. Focal also declares narrow `<queries>` for the Settings, app-details and uninstall intents so it can recognise the screens it never Shields.
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`**: not requested. Play restricts it. Focal instead opens the system battery-optimisation settings screen and shows OEM-specific guidance (autostart on Xiaomi, "sleeping apps" on Samsung) in a "Why did my session stop?" help entry (T35). The user decides.
- **`USE_EXACT_ALARM`**: not used (alarm-clock and calendar apps only).
- **`SYSTEM_ALERT_WINDOW`**: not needed; the Shield is an accessibility overlay.
- **`PACKAGE_USAGE_STATS`**: optional and used only to rank Apps-tab suggestions by recent foreground time. It is not a blocking signal; without it the tab lists apps alphabetically. The data stays on the device (story 16a).

### 1.9 Play submission checklist

- [ ] Fill developer name, support email, privacy policy URL (placeholders).
- [ ] Accessibility declaration (1.2) entered, review video (1.3) uploaded.
- [ ] Foreground service declaration for `specialUse` (1.4).
- [ ] Exact alarm declaration (1.5).
- [ ] Data safety form (1.7).
- [ ] Verify the in-app accessibility disclosure matches 1.2.
- [ ] Release build tested on a physical device (time-to-Shield, supported browsers' address-bar view IDs: see "Known gaps" in `docs/android-blocking-engine.md`).
- [ ] Store listing (see [store-listing.md](store-listing.md)); screenshots; feature graphic; icon.
- [ ] Do not describe Locked as unbypassable on Android (ADR 0002).

---

## 2. Apple (App Store)

### 2.1 Targets, bundle ids and App Group

The Xcode project (`iosApp/iosApp.xcodeproj`) builds four targets. Bundle ids come from `iosApp/Configuration/Config.xcconfig`: `PRODUCT_BUNDLE_IDENTIFIER=io.disruption.focal.Focal$(TEAM_ID)`. The extension ids in the project are `io.disruption.focal.Focal$(TEAM_ID).<Extension>`. `TEAM_ID` is empty in the committed config (template default), so the ids the entitlement request needs are, with the team id left out:

| Target | Bundle id | Extension point | Entitlements |
| --- | --- | --- | --- |
| Focal (app) | `io.disruption.focal.Focal` | n/a | Family Controls, App Group |
| ShieldConfiguration | `io.disruption.focal.Focal.ShieldConfiguration` | `com.apple.ManagedSettingsUI.shield-configuration-service` | Family Controls, App Group |
| ShieldAction | `io.disruption.focal.Focal.ShieldAction` | `com.apple.ManagedSettingsUI.shield-action-service` | Family Controls, App Group |
| DeviceActivityMonitor | `io.disruption.focal.Focal.DeviceActivityMonitor` | `com.apple.deviceactivity.monitor-extension` | Family Controls, App Group |

- **App Group:** `group.io.disruption.focal` (all four targets; `APP_GROUP_ID` in `StartFocal.kt`).
- Entitlement key in each `.entitlements` file: `com.apple.developer.family-controls`.
- Human to confirm before requesting: if the final bundle id differs (for example the team id is appended, or the id changes), request the final ids. The `docs/ios-blocking-engine.md` note says deployment target iOS 17, but the project currently sets `IPHONEOS_DEPLOYMENT_TARGET = 18.2`; reconcile before stating a minimum iOS version in the listing.
- Apple requires the Family Controls (Distribution) entitlement to be requested per bundle id (app and each extension). Request it through Apple's Family Controls entitlement request form with the Account Holder's account: `<Apple developer account / team id>`.

### 2.2 Family Controls (Distribution) entitlement request text (draft, per target)

Common preamble to include in each request:

> Focal is a focus app for iPhone. A user chooses apps and App Categories with the system FamilyControls picker and types websites as domain names, groups them into Block Lists, and blocks them during Focus Sessions that the user starts or schedules. Focal uses individual (`.individual`) authorization on the user's own device, with the user's explicit approval in the system Screen Time prompt. It does not monitor children, does not collect data, has no accounts and no backend: all selections and history stay on the device (app and category selections are opaque tokens; websites are the domains the user typed; none of it leaves the device). The app group `group.io.disruption.focal` is used only to share a small local snapshot of the user's Block Lists, Schedules and running sessions between the app and its extensions.

**Focal (app), `io.disruption.focal.Focal`.**

> Requests individual FamilyControls authorization; presents the FamilyActivityPicker so the user selects apps and categories for a Block List (websites are typed as domains in Focal, not picked); applies and clears Shields through one named `ManagedSettingsStore` per Focus Session (`shield.applications`, `shield.applicationCategories`, and `webContent.blockedByFilter` for the typed domains); sets `denyAppRemoval` while a Locked Focus Session runs (Uninstall Protection, which ends with the last Locked session); registers DeviceActivity schedules for Focal's Schedules, session ends and Breaks. Without the entitlement none of the app's blocking can function.

**ShieldConfiguration, `io.disruption.focal.Focal.ShieldConfiguration`.**

> Provides the custom Shield shown in place of a blocked app or an app in a blocked category: it names the Focus Session holding the block, the approximate time left and a motivational line, read from the local snapshot in the App Group. It collects nothing and performs no network access.

**ShieldAction, `io.disruption.focal.Focal.ShieldAction`.**

> Handles the two buttons on the Shield: Close (dismisses the Shield) and Open Focal (closes the Shield and posts a local notification the user taps to open Focal, because a ShieldAction extension cannot open its containing app). It never ends or weakens a Focus Session from the Shield.

**DeviceActivityMonitor, `io.disruption.focal.Focal.DeviceActivityMonitor`.**

> Runs at DeviceActivity interval starts and ends so blocking works on time while Focal is not running: starts a Focus Session when a user-created Schedule's window begins, lifts the session's `ManagedSettingsStore` when its planned end arrives, and re-applies blocking when a user-taken Break (5 minutes, Delayed sessions only) ends. It writes an event log to the App Group for Focal to read next time it opens. It collects and transmits nothing.

### 2.3 App Privacy (App Store Connect "Nutrition label")

- **Data Not Collected.** Select "No, we do not collect data from this app".
- Rationale for the human: no analytics, no accounts, no network calls, no third-party SDKs that collect; app and category selections are opaque tokens kept on the device and never read by Focal as app names or identifiers; website entries are domains the user typed, stored on the device only.
- Human to confirm: no tracking, no data linked to the user, and that the release build links no SDK that collects data.
- Privacy policy URL: `<privacy policy URL>`.

### 2.4 Review notes (paste into App Store Connect "Notes")

> Focal blocks distracting apps and websites during Focus Sessions. It uses Apple's Screen Time APIs (FamilyControls, ManagedSettings, DeviceActivity) with individual authorization on the user's own device. It has no accounts, no backend and collects no data. The app has three extensions that need the Family Controls entitlement: ShieldConfiguration (draws the blocking screen), ShieldAction (Close / Open Focal buttons) and DeviceActivityMonitor (starts and ends Schedules and Breaks while the app is closed).
>
> **How to test (physical device required; Screen Time APIs do not work in the Simulator):**
> 1. Open Focal and complete onboarding. Allow Screen Time access when prompted (the system will ask for Face ID / passcode) and allow notifications.
> 2. Create a Block List: tap to open the app picker, select one or two apps (for example Safari or a game) and, optionally, type a website domain such as `example.com`. Save.
> 3. On the Start screen, pick that Block List, a short duration (for example 15 minutes) and Normal strictness; start the Focus Session.
> 4. Open a blocked app. Focal's Shield appears, naming the Focus Session and the time left, with only Close and Open Focal. Tapping Open Focal shows a notification to tap to open Focal.
> 5. In Focal, end the Normal session. The Shield no longer appears.
> 6. Schedules: create a Schedule (days, start and end a few minutes ahead, a Block List, Normal). With Focal closed, the Shield starts at the start time and ends at the end time.
> 7. Locked: a Locked session cannot be ended or shortened and Focal cannot be deleted from the Home Screen while it runs (Uninstall Protection); it ends when the session runs out. A Delayed session can be ended after a 30-second Exit Countdown.
>
> Focal does not monitor other people, track usage off-device, or bypass any Screen Time restriction; it can only be used by the person holding the phone, on their own apps.

Human to add: a demo video link if useful `<demo video URL>`; contact info `<support email>`, `<phone>`.

### 2.5 TestFlight checklist

- [ ] Apple has granted the Family Controls (Distribution) entitlement for all four bundle ids (T2). Provisioning profiles for all four include it and the App Group `group.io.disruption.focal`.
- [ ] `TEAM_ID` set in `iosApp/Configuration/Config.xcconfig` (or `Config.local.xcconfig` if used), and bundle ids match the entitlement request.
- [ ] Archive on a Release configuration with distribution signing; the three extensions are embedded and signed.
- [ ] Upload to App Store Connect; fill export compliance (no custom encryption beyond system; human to confirm), App Privacy (2.3), age rating and privacy policy URL.
- [ ] Internal testers: install on at least two physical devices (one on the lowest supported iOS version, see 2.1).
- [ ] Verify the "Open risks to verify early" list in `docs/ios-blocking-engine.md` on a device: Uninstall Protection and revocation, Breaks and Schedules firing with Focal closed, named store cap, monitored activity cap (about 20), "Open Focal" notification friction.
- [ ] Verify revoking Screen Time access in Settings breaks a Locked session and Home warns about it.
- [ ] Verify Shield text and time left for apps, categories and websites (websites show the system "restricted" page, not Focal's Shield, per the engine notes; do not promise a custom website Shield on iOS in the listing).
- [ ] External testing: Beta App Review notes (reuse 2.4).
- [ ] Add screenshots (6.7-inch and 6.1-inch sets as required at submission time), app icon, listing copy (see [store-listing.md](store-listing.md)).

---

## 3. Policy docs

- Privacy policy draft: [privacy-policy.md](privacy-policy.md). Host it at `<privacy policy URL>`; both stores need a public URL.
- Store listing copy draft: [store-listing.md](store-listing.md).

## 4. Open questions for the human

1. Developer / company name, support email, website: placeholders throughout.
2. Whether to keep `android:allowBackup="true"` (affects the privacy policy wording and Data safety stance).
3. Final iOS bundle id (and `TEAM_ID` handling) and minimum iOS version (project says 18.2, docs say 17).
4. Which jurisdictions' privacy requirements (GDPR, CCPA) the policy must address; the draft assumes none of that data is collected by the developer.
