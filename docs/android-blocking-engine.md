# Android blocking engine: implementation notes

Android has no Screen Time API, so the blocker is assembled from four imperfect pieces: **detect** the foreground app or website, **block** it with the Shield, **stay alive** for the length of a Focus Session or Schedule, and **pass Play policy**. Reliability is measured, not assumed.

Decisions these notes build on: [ADR 0002](adr/0002-locked-is-best-effort-on-android.md) (Locked is best-effort), [ADR 0003](adr/0003-on-device-only-no-backend-in-v1.md) (on-device only), [ADR 0005](adr/0005-android-website-blocking-via-accessibility.md) (websites via accessibility).

## 1. Detect

- **Apps**: an `AccessibilityService` listening for `TYPE_WINDOW_STATE_CHANGED`. The event carries the package name, arrives instantly, and costs nothing when nothing changes.
- Keep the service config narrow: only the event types we need.
- Ignore noise packages that also fire window events: Focal itself, the launcher, System UI, the keyboard (IME).
- **Websites**: same service, with `canRetrieveWindowContent` enabled, reading the address bar of supported browsers via a per-browser lookup table.
- **`UsageStatsManager`** (usage access) is *not* a blocking signal — polling `queryEvents` for `ACTIVITY_RESUMED` is too slow. Possible later uses: fallback when accessibility is off, a cross-check, and per-app screen-time stats. Not needed for v1 unless stats require it.

## 2. Block (the Shield) without flicker

- Draw the Shield as a `TYPE_ACCESSIBILITY_OVERLAY` window from the accessibility service. This needs no extra permission (unlike `SYSTEM_ALERT_WINDOW` / `TYPE_APPLICATION_OVERLAY`).
- Flicker comes mostly from inflating and laying out the overlay on demand. Keep the overlay **attached but hidden** and toggle visibility, so there's no inflate or first-layout cost at block time.
- Show a plain cover on the first frame, then animate the full Shield in.
- For blocks that shouldn't be dismissible, also call `performGlobalAction(GLOBAL_ACTION_HOME)` so the blocked app isn't left running underneath.
- A Compose overlay outside an Activity needs a `LifecycleOwner` and `SavedStateRegistryOwner` attached to the `ComposeView` by hand.
- Measure **time-to-Shield** per device to know whether it works.

## 3. Stay alive

- The system binds the accessibility service itself, so it survives better than a normal service, but time-based work needs more.
- **Running Focus Sessions** run in a **foreground service** with an honest, ongoing notification showing the timer.
  - Android 14+: declare a `foregroundServiceType` (likely `specialUse`) and justify it in Play Console.
  - Android 12+ blocks most background starts: start it while the app is visible or from an allowed trigger (e.g. an exact alarm).
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

Missing required permissions disable starting a session and are shown with a fix-it action. A required permission revoked during a Locked session makes it Broken.

## 6. Play policy

- Accessibility API use needs the Play Console declaration and a review video; app blocking is an accepted use case. Website blocking widens the scope because it reads window content.
- `specialUse` foreground service needs a justification.
- Exact-alarm permission needs to be justified as core functionality (Schedules).
