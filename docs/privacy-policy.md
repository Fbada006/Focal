# Focal Privacy Policy

> **DRAFT for a human (and, ideally, a lawyer) to review before publishing.** Replace every placeholder in angle brackets. Not legal advice. Describes Focal v1 as built: on-device only (ADR 0003).

**Effective date:** `<date>`
**Developer:** `<developer name>`
**Contact:** `<support email>`

## The short version

Focal has no accounts and no server. We do not collect, receive, sell or share any information about you. Everything Focal knows stays on your device.

## What Focal stores on your device

- The Block Lists, Schedules and settings you create.
- Your Focus Sessions: when they ran, how long, their Strictness and outcome (Completed, Ended Early or Broken), and the Streak and stats computed from them.

This data never leaves your device through Focal. If you delete Focal, this data is deleted from your device with it. A copy in your own device backup (see Backups) is not: it stays until that backup is replaced or deleted, and may be restored if you install Focal again.

## What Focal looks at to work

**Android.** Focal uses an accessibility service, which you turn on yourself in system settings. While the service is turned on, it notices which app is in the foreground, and in supported browsers it reads only the address bar to see which website is open. It does this whether or not a Focus Session is running; Focal acts on it only to show the Shield over apps and websites a running session blocks. It does not read page content, messages, passwords or anything you type elsewhere. This information is compared with your Block List on the device and then discarded. It is not saved, sent or shared.

**iOS.** Focal uses Apple's Screen Time frameworks (FamilyControls, ManagedSettings, DeviceActivity) with your approval. The apps and App Categories you pick in Apple's picker come back to Focal as opaque tokens that stay on your device; Focal cannot tell which apps they are, or which apps you used or for how long. Websites are different: you type them as domain names (for example `example.com`), so Focal stores those readable domains in your Block Lists, on your device only, and hands them to Screen Time to block.

## Permissions

- **Accessibility (Android):** to detect blocked apps and websites, as above.
- **Notifications (Android):** to show the running Focus Session and tell you if a Locked session was Broken.
- **Notifications (iOS):** only so that tapping "Open Focal" on the Shield can show a notification you tap to return to Focal.
- No marketing notifications on either platform.
- **Exact alarms (Android):** to start and end your Schedules on time.
- **Screen Time access (iOS):** to block the apps and websites you choose.

Focal does not request access to your contacts, location, camera, microphone, photos or files, and does not request network access on Android.

## No third parties

Focal has no analytics, advertising or crash-reporting services and does not use your data to track you.

## Backups

Your phone's operating system may include Focal's on-device data in your own device backup (for example Android's backup to your Google account, or an iCloud/computer backup on iOS), under your device's backup settings. That is controlled by you and your platform provider, not by us. `<Human: confirm after deciding on allowBackup.>`

## Children

Focal is not directed at children and we do not knowingly collect information from anyone, including children. `<Human: confirm target-audience declaration.>`

## Your choices

Turn off the accessibility service or Screen Time access in system settings at any time (Focal will tell you, and a running Locked session will be recorded as Broken). Uninstall Focal to remove all of its data from your device (a copy in your own device backup stays under your backup settings; see Backups).

## Changes

If this policy changes we will update the date above and, for material changes, say so in the app. `<Human: decide how.>`

## Contact

Questions: `<support email>`.
