# Shared Compose UI, with SwiftUI only where iOS requires it

All screens are written once in Compose Multiplatform in `shared`. On iOS, two pieces are SwiftUI because Apple gives no alternative: the app/website picker (`FamilyActivityPicker`, SwiftUI-only, hosted inside Compose via interop) and the Shield (rendered by a Shield Configuration app extension, which runs outside the app process and only accepts text, icon, colour and button configuration). On Android the Shield is drawn by Focal itself and is Compose like everything else.

## Considered Options

- **Fully native UI per platform** (Jetpack Compose + SwiftUI, shared logic only): more platform-idiomatic, but doubles UI work for a single developer building both platforms in parallel.
