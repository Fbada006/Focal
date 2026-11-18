# Shared Compose UI, with SwiftUI only where iOS requires it

All screens are written once in Compose Multiplatform in `shared`. On iOS, two pieces fall outside Compose because Apple gives no alternative. The app/website picker (`FamilyActivityPicker`) is SwiftUI-only and is hosted inside Compose via interop; it is the only SwiftUI in Focal. The Shield is not a view at all: the system renders it from a `ShieldConfiguration` returned by a Swift-only Shield Configuration extension (`ShieldConfigurationDataSource`), which runs outside the app process and only supplies text, icon, colours and button labels (ADR 0008). On Android the Shield is drawn by Focal itself and is Compose like everything else.

## Considered Options

- **Fully native UI per platform** (Jetpack Compose + SwiftUI, shared logic only): more platform-idiomatic, but doubles UI work for a single developer building both platforms in parallel.
