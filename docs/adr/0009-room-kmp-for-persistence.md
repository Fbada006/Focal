# Room (KMP) for persistence

Shared code persists Block Lists, Schedules, Focus Sessions (with outcomes, Breaks and each running session's own copy of what it blocks) and ingested iOS extension records with Room's Kotlin Multiplatform support, behind repository interfaces (ADR 0003). Room is familiar to the team from Android, fits the relational, query-heavy stats, and offers migrations and Flow integration. Small preferences go in DataStore. The iOS extensions never open the database; they read a separate small snapshot file (ADR 0008).

## Considered Options

- **SQLDelight**: longer multiplatform track record and very stable on iOS, but hand-written SQL and less familiar.
- **DataStore only**: too limited for session history and stats queries.
