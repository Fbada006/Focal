# Focal

Focal helps people stay focused by blocking distracting apps and websites on their phone for chosen periods of time.

## Language

### Blocking

**Block List**:
A named, reusable set of apps (individually or by category) and websites (by domain) that are blocked together. Each Focus Session and each Schedule uses exactly one Block List.
_Avoid_: Blocklist profile, app group, filter

**App Category**:
A named group of apps, such as Social or Games, that can be added to a Block List as a whole, so apps installed later in that category are blocked too.
_Avoid_: Group, genre, type

**Shield**:
The screen shown in place of a blocked app or website. It names the Focus Session holding the block and its time left, and offers only two ways forward: close the blocked app, or open Focal. A Focus Session can never be ended or paused from the Shield.
_Avoid_: Overlay, block screen, lock screen

**Suggested Website**:
A domain from Focal's own curated list of popular distracting services, offered for adding to a Block List alongside the user's typed domains.
_Avoid_: Preset, recommended site

### Sessions

**Focus Session**:
A period of time during which a chosen set of apps and websites is blocked.
_Avoid_: Block, timer, lock

**Strictness**:
How hard it is to end a Focus Session before its planned end. Each Focus Session has exactly one Strictness: Normal, Delayed or Locked.
_Avoid_: Mode, difficulty, level

**Normal**:
A Strictness under which the user can end the Focus Session at any time.

**Delayed**:
A Strictness under which ending early requires waiting out an Exit Countdown and then confirming again.

**Exit Countdown**:
The 30-second wait a user must sit through before they can confirm ending a Delayed Focus Session early. Leaving it before it finishes cancels it.
_Avoid_: Cooldown, timeout

**Break**:
A 5-minute pause in blocking during a Delayed Focus Session, after which blocking resumes automatically. A session allows up to three Breaks; they don't move its end time or affect its outcome.
_Avoid_: Pause, snooze, unlock

**Locked**:
A Strictness under which the Focus Session cannot be ended, edited or shortened until it runs out.
_Avoid_: Deep Focus, hardcore

**Schedule**:
A named, recurring rule that automatically starts a Focus Session on chosen days of the week, from a start time to an end time (an end before the start means overnight), using one Block List and one Strictness. A Schedule can be switched on or off.
_Avoid_: Routine, automation, recurring session

### Outcomes and stats

Every Focus Session ends with exactly one outcome: Completed, Ended Early or Broken.

**Completed**:
The outcome of a Focus Session that ran to its planned end, whatever its Strictness.
_Avoid_: Finished, successful

**Ended Early**:
The outcome of a Normal or Delayed Focus Session that the user ended before its planned end.
_Avoid_: Cancelled, stopped, abandoned

**Broken**:
The outcome of a Locked Focus Session whose blocking was defeated from outside Focal before it ran out, for example by revoking Focal's permissions.
_Avoid_: Failed, cancelled

**Streak**:
The number of consecutive local calendar days, each with at least one Completed Focus Session of 15 minutes or more. A session counts for the day it ends. Ended Early sessions neither count nor reset it; a Broken session resets it to zero immediately.
_Avoid_: Chain, run
