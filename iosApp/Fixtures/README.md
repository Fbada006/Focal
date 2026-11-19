# iOS contract fixtures

Samples of the files shared between the app and the Swift-only Screen Time extensions (ADR 0008):

- `snapshot-v1.json`: what the app writes to App Group storage for the extensions to apply.
- `events-v1.json`: what the extensions append for the app to ingest on launch.

The Kotlin side is `shared/core/src/commonMain/kotlin/io/disruption/focal/core/ios`. `IosFixturesTest` (in `shared/core/src/jvmTest`) fails if these files and the Kotlin encoder drift apart, so Swift tests can decode them as the source of truth.

Rules for the format:

- Instants are ISO 8601 UTC with whole seconds; times of day are local `HH:mm`.
- Readers ignore unknown fields. Unknown event `type`s are skipped.
- A change to the shape bumps `version` and adds a new `-v<N>` fixture.
