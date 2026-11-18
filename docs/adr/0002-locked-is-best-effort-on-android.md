# Locked Strictness is best-effort on Android

On iOS, Screen Time enforces a Locked Focus Session at the system level and Uninstall Protection stops the user deleting Focal mid-session. Android offers no equivalent to a non-device-owner app, so on Android Locked only forbids ending, editing or shortening the session inside Focal; if the user revokes Focal's blocking permission or uninstalls it, Focal detects this, prompts to restore it, and records a Broken Focus Session. We deliberately do not block Android Settings screens or uninstallation via the accessibility service, because that is the behaviour most likely to fail Google Play's accessibility-API review, and Opal ships the same trade-off.

## Considered Options

- **Block Settings/uninstall via accessibility service**: stronger, but high Play policy risk and fragile across OEM Settings layouts.
- **Device owner / managed device mode**: real enforcement, but requires provisioning that consumer users won't do.
