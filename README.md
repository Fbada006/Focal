This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared) holds the shared Kotlin code, split by layer (ADR 0010):
  - [core](./shared/core) is the pure Kotlin domain: rules, model and the `BlockingEngine` interface.
  - [data](./shared/data) is persistence, repositories and curated data.
  - [ui](./shared/ui) is the Compose Multiplatform screens and view models.
  - `shared` itself is the umbrella module that exports all three as the single `Shared` iOS framework.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Domain tests: `./gradlew :shared:core:jvmTest`
- Android host tests: `./gradlew :shared:ui:testAndroidHostTest`
- Repository tests (device or emulator): `./gradlew :shared:data:connectedAndroidDeviceTest`
- iOS tests: `./gradlew :shared:core:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test :shared:ui:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…