# Android website blocking reads the browser address bar via the accessibility service

Android has no system-level website blocking for ordinary apps. We block websites on Android by reading the URL from supported browsers' address bars with the same accessibility service that detects blocked apps, and showing the Shield when the domain is in the active Block List. This keeps Android to one blocking permission, shows a consistent Shield for apps and websites, and does not conflict with a user's own VPN.

## Considered Options

- **Local DNS filter via `VpnService`**: covers every browser and in-app browsers, but Android allows only one VPN at a time, Chrome's secure DNS can bypass it, it can only show a connection error rather than the Shield, and it adds a second sensitive permission. Kept as a possible later opt-in "strict web blocking" mode.

## Consequences

- Only browsers Focal has been taught about are covered. Per-browser address-bar lookup lives in a small, data-like table so new or changed browsers don't touch the engine.
- The accessibility service must be configured to retrieve window content (`canRetrieveWindowContent`), which widens what Play's accessibility declaration has to justify.
- Navigation inside an open browser window (following a link, switching tabs) need not fire a window-state event, so the service also listens for content and text-change events in supported browsers and re-checks the address bar on each. These events are frequent, so they are scoped to browser packages and debounced (see `docs/android-blocking-engine.md`).
