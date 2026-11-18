# Locked Strictness is best-effort on Android

On iOS, Screen Time enforces a Locked Focus Session at the system level and Uninstall Protection stops the user deleting Focal mid-session. Android offers no equivalent to a non-device-owner app, so on Android Locked only forbids ending, shortening or otherwise weakening the session inside Focal; making it stricter is still allowed (ADR 0007). While Focal remains installed, if the user revokes a permission blocking depends on, Focal detects this, prompts to restore it, and records the session as Broken. We deliberately do not block Android Settings screens or uninstallation via the accessibility service, because that is the behaviour most likely to fail Google Play's accessibility-API review, and Opal ships the same trade-off.

## Considered Options

- **Block Settings/uninstall via accessibility service**: stronger, but high Play policy risk and fragile across OEM Settings layouts.
- **Device owner / managed device mode**: real enforcement, but requires provisioning that consumer users won't do.

## Consequences

- **Uninstalling Focal is an accepted escape on Android.** An uninstalled app cannot detect, warn about or record its own removal, so no outcome is recorded. Because storage is on-device only (ADR 0003), uninstalling also deletes the user's session history, stats and Streak. Android Auto Backup (`allowBackup`) may restore some data on reinstall, but it runs only periodically, so Focal makes no promise of recovery.
