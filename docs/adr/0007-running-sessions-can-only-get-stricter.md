# A running Focus Session can only get stricter

Edits made while a Focus Session is running may tighten it but never weaken it (except under Normal Strictness, where the user could end the session anyway). Additions to its Block List, a later end time and a higher Strictness apply immediately; removals from the Block List, an earlier end time and a lower Strictness are refused or deferred until the session ends. A Block List cannot be deleted while any running session uses it, and editing or switching off a Schedule only affects sessions it starts in the future. Otherwise editing would be a trivial way around Delayed and Locked, undermining the decision that sessions can't be ended from the Shield.

## Consequences

- A running Delayed or Locked session holds its own copy of what it blocks, taken at start, which can grow but not shrink. It is not a live view of the Block List.
- The UI must explain deferred edits, e.g. "Changes take effect after *Wind down* ends."
