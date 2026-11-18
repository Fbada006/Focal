# Build Android and iOS in parallel

Both platforms are in scope for v1 and their blocking implementations are built side by side, so the shared contract between common code and platform-specific blocking is designed against both from day one rather than shaped around one platform and retrofitted.

## Consequences

- iOS blocking relies on Apple's Family Controls entitlement. Development uses the *Family Controls (Development)* capability on a physical device (the simulator cannot exercise authorization or shields). TestFlight and App Store builds need the *Distribution* entitlement, which Apple grants on request per bundle ID — the app and each Screen Time extension — so the request must be filed well before the first TestFlight build.
