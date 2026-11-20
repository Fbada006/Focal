# Android blocking engine: implementation notes

Android has no Screen Time API, so the blocker is assembled from four imperfect pieces: **detect** the foreground app or website, **block** it with the Shield, **stay alive** for the length of a Focus Session or Schedule, and **pass Play policy**. Reliability is measured, not assumed.

Decisions these notes build on: [ADR 0002](adr/0002-locked-is-best-effort-on-android.md) (Locked is best-effort), [ADR 0003](adr/0003-on-device-only-no-backend-in-v1.md) (on-device only), [ADR 0005](adr/0005-android-website-blocking-via-accessibility.md) (websites via accessibility).

## 1. Detect

- **Apps**: an `AccessibilityService` listening for `TYPE_WINDOW_STATE_CHANGED`. The event carries the package name, arrives instantly, and costs nothing when nothing changes.
- Keep the service config narrow: only the event types we need.
- Handle noise packages that also fire window events. Focal itself and the launcher mean the user left the app, so the Shield lifts; only a Focal Activity counts as "came to Focal", and events from Focal's own Shield window are ignored so the Shield doesn't hide itself. System UI (shade, dialogs) and the keyboard (IME) open over the app without leaving it, so they are ignored and the app underneath stays the foreground app (a Break ending while the user types still finds it).
- Never Shield Settings, the permission controller or the package installer (AOSP, Google and the device's resolved Settings/uninstall handlers), even if a Block List picks them (ADR 0002).
- **Websites**: same service, with `canRetrieveWindowContent` enabled, reading the address bar of supported browsers via a per-browser lookup table (package name → address-bar view ID).
  - `canRetrieveWindowContent` only grants access to the view tree; it doesn't decide *when* we look. Following a link, switching tabs or typing a new URL happens inside the same browser window and need not fire `TYPE_WINDOW_STATE_CHANGED`.
  - So for supported browser packages, also listen for `TYPE_WINDOW_CONTENT_CHANGED` and `TYPE_VIEW_TEXT_CHANGED`, and re-check the address bar on each. Scope these to the browser packages (via `packageNames` or an early filter) and debounce them, since content-change events are frequent.
  - Only act on the committed URL, not partial text while the user is typing in the address bar (e.g. check when the bar loses focus or the page's content changes).

### Websites (T26)

- `FocalAccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED` and `TYPE_VIEW_TEXT_CHANGED` with `canRetrieveWindowContent` and `flagReportViewIds`. `packageNames` stays unset because app detection needs every package; content and text events from anything but a supported browser are dropped on arrival.
- A browser coming to the foreground is checked at once and again after 250 ms, once its views are laid out. Content and text changes schedule a check at most every 250 ms (a throttle, so a page that changes constantly still gets checked). A check only runs while that browser is the active window. A browser window event that arrives once another app owns the active window is ignored, so a late event can't replace the app really in front (or its Shield); only when there is no active window yet, or the Shield is the active window, is the browser taken at its word (`BrowserWindow` in shared/core).
- `CommittedSite` (`shared/core`) decides which site counts: text in a focused bar is the user typing and is ignored; a bar that isn't on screen (scrolled away, full-screen video, Firefox's separate edit view) keeps the last committed site; text that isn't a hostname (search terms, `about:blank`, an empty new tab) means no website. Hostnames come from `Domains`, so matching is the Block List's (T8).
- The engine checks the app first, then the site: `BlockingState.forApp`, then `forWebsite`, over the union of running sessions. The Shield, Close and Open Focal are the same as for apps.

Supported browsers (`SupportedBrowsers` in `shared/core`; a new browser or renamed view is a change there only):

| Browser | Package | Address-bar view ID |
| --- | --- | --- |
| Chrome (stable, Beta, Dev, Canary) | `com.android.chrome`, `com.chrome.beta`, `com.chrome.dev`, `com.chrome.canary` | `<package>:id/url_bar` |
| Microsoft Edge | `com.microsoft.emmx` | `com.microsoft.emmx:id/url_bar` |
| Brave | `com.brave.browser` | `com.brave.browser:id/url_bar` |
| Vivaldi | `com.vivaldi.browser` | `com.vivaldi.browser:id/url_bar` |
| Kiwi | `com.kiwibrowser.browser` | `com.kiwibrowser.browser:id/url_bar` |
| Samsung Internet | `com.sec.android.app.sbrowser` | `…:id/location_bar_edit_text`, then `…:id/custom_tab_toolbar_url_bar_text` |
| Firefox (stable, Beta, Nightly) | `org.mozilla.firefox`, `org.mozilla.firefox_beta`, `org.mozilla.fenix` | `<package>:id/mozac_browser_toolbar_url_view` |
| Opera | `com.opera.browser` | `com.opera.browser:id/url_field` |
| DuckDuckGo | `com.duckduckgo.mobile.android` | `com.duckduckgo.mobile.android:id/omnibarTextInput` |

Known gaps and risks:

- View IDs are browser internals and change without notice; each needs checking on a device per release. Firefox is moving its toolbar to Compose, which may drop the view ID.
- Chrome can show search terms instead of the URL on Google results pages ("query in omnibox"), so a blocked `google.com` isn't caught there.
- In-app browsers (a WebView inside another app) aren't covered. Custom Tabs run under the browser's package and are caught only where their toolbar uses an ID in the table (Samsung's is listed; Chrome's needs checking on a device).
- **`UsageStatsManager`** (usage access) is *not* a blocking signal — polling `queryEvents` for `ACTIVITY_RESUMED` is too slow. Possible later uses: fallback when accessibility is off, a cross-check, and per-app screen-time stats. Not needed for v1 unless stats require it.

### App Categories

- A running session's `BlockedSet.appCategories` holds App Category keys. For each foreground package, the engine resolves its category with `AppCategoryResolver` (Focal's curated list in `shared/data`, falling back to Android's `ApplicationInfo.category`) and checks `BlockedSet.blocksApp(packageName, category)`. Because this runs per foreground event, apps installed after the session started are covered too.

### Package visibility

- The Apps tab lists launchable apps via a `<queries>` entry for the `MAIN`/`LAUNCHER` intent. Every app a user can open from the launcher is visible this way, and those are the only apps worth blocking, so Focal does **not** request `QUERY_ALL_PACKAGES` (which Play restricts to apps whose core purpose needs it).
- Accessibility events carry the foreground package name regardless of package visibility, so detection does not depend on it.

## 2. Block (the Shield) without flicker

- Draw the Shield as a `TYPE_ACCESSIBILITY_OVERLAY` window from the accessibility service. This needs no extra permission (unlike `SYSTEM_ALERT_WINDOW` / `TYPE_APPLICATION_OVERLAY`).
- Flicker comes mostly from inflating and laying out the overlay on demand. Keep the overlay **attached but hidden** and toggle visibility, so there's no inflate or first-layout cost at block time.
- Show a plain cover on the first frame, then animate the full Shield in.
- For blocks that shouldn't be dismissible, also call `performGlobalAction(GLOBAL_ACTION_HOME)` so the blocked app isn't left running underneath.
- A Compose overlay outside an Activity needs a `LifecycleOwner` and `SavedStateRegistryOwner` attached to the `ComposeView` by hand.
- Measure **time-to-Shield** per device to know whether it works.
- What is blocked changes on its own when a Break ends and at a session's planned end, with no new foreground event. The engine refreshes the Shield at both moments itself (waiting on the wall clock, so a clock set back doesn't wake it early), so it reappears after a Break and lifts at the planned end even if the foreground service could not start.

## 3. Stay alive

- The system binds the accessibility service itself, so it survives better than a normal service, but time-based work needs more.
- **Running Focus Sessions** run in a **foreground service** with an honest, ongoing notification showing the timer.
  - Android 14+: declare a `foregroundServiceType` (likely `specialUse`) and justify it in Play Console.
  - Android 12+ blocks most background starts: start it while the app is visible or from an allowed trigger (e.g. an exact alarm).
- **Breaks** (T19) need no alarm. A Break only lifts blocking inside Focal's own process, so it can only end while that process is alive or on its next start:
  - Every foreground event checks Breaks against the wall clock (`BlockingState`), so switching apps after the Break's end is blocked even if a timer ran late.
  - For a user who stays in a blocked app, the engine refreshes the Shield at the Break's end, and the foreground service wakes then to put the notification back from "On a Break until …" to "Blocking until …". With several sessions the notification names the one it describes: the soonest-ending session that is blocking, or, when all are on a Break, the one whose Break ends first (`BlockingState.summary`). A Break that runs to the session's end doesn't promise blocking afterwards.
  - The service's wake-up time is `BlockingState.nextChange`; a session already past its planned end counts as due now, so it is completed before anything else is waited for.
  - After process death, Android restarts the accessibility service (and the sticky foreground service), and `FocusSessionService.restore` re-pauses a Break still in progress or re-applies the session if it has ended.
  - An exact alarm would add a permission (`SCHEDULE_EXACT_ALARM`, asked only with the first Schedule) without blocking anything sooner.
- **Schedules** use exact alarms: `setExactAndAllowWhileIdle` with `SCHEDULE_EXACT_ALARM`. Users can revoke this on Android 13+, so check `canScheduleExactAlarms()` and guide them back.
- Re-register all alarms on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
- **Persist active session state on disk** (Room or DataStore) so a restarted process resumes the active Focus Session instead of silently ending it. On-device only, per ADR 0003; no server sync in v1.

## 4. OEM battery killers

- Xiaomi, Samsung, Huawei and OnePlus kill apps even with a foreground service.
- Send users to the battery-optimisation settings screen via the settings intent. Don't request the exemption directly: Play policy restricts `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Show OEM-specific guidance only on those devices: autostart on Xiaomi, "sleeping apps" on Samsung.

## 5. Permissions, asked just in time

1. **Accessibility service**: required before the first Focus Session, behind an in-app explanation screen (Play requires a prominent disclosure).
2. **Notifications** (`POST_NOTIFICATIONS`, Android 13+): required; drives the foreground-service notification.
3. **Exact alarms**: only when the first Schedule is created.
4. **Battery optimisation + OEM steps**: after the first session, and from a "Why did my session stop?" help entry.

Missing required permissions disable starting a session and are shown with a fix-it action.

### Broken on revoke (T22, ADR 0002)

Revoking a permission blocking depends on during a Locked session makes it Broken. On Android that is the accessibility service: without it nothing is detected or Shielded. Notifications and exact alarms are required or useful but don't defeat blocking (the foreground service runs without the notification permission), so losing them only shows the fix-it banner. The rule is `Permission.blockingDependsOn` and `FocusSessionService.checkPermissions` in `shared/core`; Normal and Delayed sessions keep running, since they could have been ended anyway.

Focal checks whenever it might notice a revocation:

- **The service is unbound** (`FocalAccessibilityService.onUnbind`): Android unbinds it right after the user switches it off. The check reads the setting again, so an unbind for another reason doesn't break anything.
- **Focal's process starts** (`FocalApplication.restoreSessions`, before re-applying sessions): catches a revocation while the process was dead. The sticky foreground service brings the process back, and so does opening Focal or a reboot.
- **Home comes to the foreground**, alongside the fix-it banner.

Home warns about every Broken session whose warning the user hasn't dismissed, whichever check found it (`FocusSessionService.brokenToAcknowledge`). Dismissing acknowledges exactly the sessions the warning named, stored by session id in preferences (`acknowledgedBrokenSessions`), so a warning survives Focal's process dying before Home opens (say, with notifications off), a session broken while the warning was showing still gets its own, and a device clock set back can't hide one. The unbind and process-start paths also post a "Locked session Broken" notification that opens Home. A session that ran out before the check finds the revocation is Completed, not Broken.

**Uninstalling is an accepted escape.** An uninstalled app can't detect or record its own removal, so no outcome is recorded and on-device history goes with it (ADR 0002, ADR 0003).

## 6. Play policy

- Accessibility API use needs the Play Console declaration and a review video; app blocking is an accepted use case. Website blocking widens the scope because it reads window content.
- `specialUse` foreground service needs a justification.
- Exact-alarm permission needs to be justified as core functionality (Schedules).
