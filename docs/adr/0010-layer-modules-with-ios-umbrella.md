# Layer modules with an iOS umbrella module

Shared code is split by layer, following Google's architecture and modularization guidance scaled to a one-developer v1: `shared/core` (pure Kotlin domain rules, model, the `BlockingEngine` interface and the iOS snapshot format, with no Compose, Room or platform imports), `shared/data` (Room, repositories, DataStore, curated data), and `shared/ui` (Compose screens and view models). A top-level `shared` umbrella module depends on all three and exports them as the single iOS framework, because iOS can consume only one Kotlin framework without duplicating the Kotlin runtime and types. Platform blocking engines live in `androidApp` and `iosApp` and implement the `BlockingEngine` interface from `core`.

## Considered Options

- **One `shared` module with packages per layer**: quicker to start, but nothing stops UI or Room leaking into domain rules.
- **Feature modules from day one** (Now in Android style `feature:*` + `core:*`): pays off for teams and large builds, premature for v1. Revisit when a feature grows large.
