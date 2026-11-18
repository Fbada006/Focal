# iOS Screen Time extensions are Swift-only and work from a snapshot

The iOS extensions (Device Activity Monitor, Shield Configuration, Shield Action) do not link the shared Kotlin framework. They run under tight memory limits (the Device Activity Monitor is commonly reported around 6 MB), which a Kotlin/Native framework with its runtime and Compose would likely exceed. Instead, the app (running shared Kotlin code) writes a small snapshot of Schedules, Block Lists and running Focus Sessions to App Group storage; the extensions only apply that snapshot and append records of what they did (e.g. "session for *Work hours* started 09:00"). The shared code ingests those records on next launch and remains the single place where domain rules live: outcomes, Streaks, "can only get stricter".

## Considered Options

- **Link a slim, Compose-free Kotlin module into each extension**: one implementation of the rules, but still risks the memory limit and adds a per-extension build step.

## Consequences

- The snapshot format and the extension event log form the contract between shared code and iOS. They must be versioned and kept small and dumb, with no decisions made in Swift beyond applying what the snapshot says.
