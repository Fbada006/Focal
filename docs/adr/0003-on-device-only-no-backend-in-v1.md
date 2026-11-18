# On-device only, no backend in v1

v1 has no accounts and no server: all Focus Sessions, Schedules, Block Lists and stats live on the device. This removes sign-up friction and an entire backend to build and secure, keeps sensitive screen-time data private by construction, and avoids a sync feature that iOS would undermine anyway — the apps and websites a user picks on iOS come back as opaque, device-local tokens that cannot be meaningfully synced. Persistence sits behind a repository interface in shared code so accounts and sync can be added later without rewriting domain logic.

## Consequences

- Losing or replacing a phone loses stats and streaks; each device is configured independently.
- Social features (later) will require revisiting this decision.
