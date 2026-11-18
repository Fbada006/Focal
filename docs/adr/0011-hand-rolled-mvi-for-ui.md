# Hand-rolled MVI for the UI layer

Every screen in `shared/ui` follows MVI (Model–View–Intent): its ViewModel exposes a single immutable **State** as a `StateFlow`, accepts user actions as **Intents** through one `onIntent(intent)` entry point, and emits one-off **Effects** (navigation, transient messages, opening system settings) through a `Channel`-backed flow. Composables render State and send Intents, with no other calls into the ViewModel. We implement this with a small shared base in `shared/ui` rather than a library, because the pattern needs only a few lines on top of `ViewModel`, `StateFlow` and coroutines, and avoids taking on a framework's idioms and upgrade cadence for a one-developer v1.

## Considered Options

- **Orbit MVI**: KMP-ready and concise, but adds a dependency and its DSL for little gain over the base class.
- **MVIKotlin**: strong structure (Store, Executor, Reducer), heavier than v1 needs.

## Consequences

- ViewModels call into the domain (`shared/core`) and repositories (`shared/data`); domain rules never live in reducers.
- Screen State is tested by sending Intents to the ViewModel and asserting the resulting State and Effects.
