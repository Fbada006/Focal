# Focal

Focal is an Opal-style focus app for Android and iOS: it blocks distracting apps and websites during Focus Sessions, started manually or by Schedules. Kotlin Multiplatform with Compose Multiplatform UI; each platform has its own blocking engine.

## Read first

- `GLOSSARY.md`: the domain language. Use its terms in code, tests, issues and UI copy.
- `docs/adr/`: architecture decisions. Flag any change that contradicts one.
- `docs/specs/`: product specs (v1 lives in `docs/specs/v1.md`).
- `docs/android-blocking-engine.md` and `docs/ios-blocking-engine.md`: platform blocking implementation notes and open risks.

## Layout

- `shared/core`: pure Kotlin domain rules and the `BlockingEngine` interface. No Compose, Room or platform imports.
- `shared/data`: Room (KMP), repositories, DataStore, curated data.
- `shared/ui`: Compose Multiplatform screens and view models, using hand-rolled MVI (State / Intent / Effect, ADR 0011).
- `shared`: umbrella module that exports the single iOS framework.
- `androidApp`: Android app and Android blocking engine.
- `iosApp`: iOS app, SwiftUI pieces and Swift-only Screen Time extensions.

See ADR 0010. `core` has a plain `jvm()` target rather than an Android one, so Android and iOS APIs are not on its classpath; Android modules consume its JVM variant.

## Commands

- Android build: `./gradlew :androidApp:assembleDebug`
- Domain tests (JVM): `./gradlew :shared:core:jvmTest`
- Data and UI tests (Android host): `./gradlew :shared:data:testAndroidHostTest :shared:ui:testAndroidHostTest`
- Shared tests (iOS simulator): `./gradlew :shared:core:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test`
- iOS framework: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` (exports `core`, `data` and `ui` as `Shared`)
- iOS app: open `iosApp/` in Xcode. Screen Time blocking needs a physical device.

## Agent skills

### Issue tracker

Issues and specs are tracked as GitHub issues in `Fbada006/Focal`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default label vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
