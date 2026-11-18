# Focus Sessions run concurrently; blocking is the union of all running sessions

Any number of Focus Sessions may run at once (manual and Schedule-started alike). An app or website is blocked while any running session's Block List includes it; each session keeps its own Strictness, timer and outcome, and each counts separately toward stats and the Streak. We rejected "one session at a time" (a silently skipped Schedule surprises users) and "merge overlapping sessions" (it blurs which session earned which outcome).

## Consequences

- The platform blocking contract is per Focus Session — "apply blocking for session X with Block List L" / "lift blocking for session X" — not a single global block set.
- **iOS** maps each running session to its own named `ManagedSettingsStore`; the system itself enforces the most restrictive combination of all stores. This matters because Schedule-started sessions begin inside the DeviceActivityMonitor extension, possibly while Focal isn't running, so there is no single place that could compute a union. Apple caps the number of named stores, which bounds how many sessions can run at once.
- **Android** keeps the set of running sessions and computes the union itself in the accessibility service.
- The Shield should say which running session is holding a block, since ending one session may not unblock an app.
