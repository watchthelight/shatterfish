# No flight up the stairs while the hero is starving

Issue #202 (the hero goes straight back down the stairs after retreating up them). The idea: the fight
Policy's retreat by the stairs up skips the stairs while the hero is starving. This page records the change
that was built, measured and reverted, so it is not built again for the same reason.

## What the logs show

Reading the 40 Run logs of main at 58a477dde (standard WARRIOR, salts 1000 to 1039, played with
`:rig:direction`) for an `Ascend` followed by the next stairs Action:

- 76 flights up are followed by a `Descend` before any other stairs Action, and 63 of them by the very next
  Action (gap 1).
- 58 of the 76 `Descend`s say `descend: hungry`. The path is `Fight.retreat` (reason `retreat: stairs`), then
  on the floor above the descend Policy's "hungry with no food" rule (`Descend.leaving`) with the hero on the
  exit: `Descend.plan` rests when `Descend.rests` allows it and otherwise takes the `Descend` at once.
  `Explore.restOwed` does not apply, because it requires the hunger icon to be absent.
- The other bounces are `retreat: stairs` on both floors (a second enemy waiting above) and `descend: spent`
  (a rest that finished).

The mechanics behind it, from the pinned code: a starving hero regenerates nothing (`Regeneration.java:56`),
and the floor left behind is frozen, so the enemy fled from is where it was when the hero comes back down
(see the comment on the issue for `Dungeon.java:826-838`, `Actor.java:212-227`, `InterlevelScene.java:654`).
A flight up with no rest to follow it gains nothing.

## What was built

`Fight.retreat` took the stairs up only when `hunger != STARVING`. The new test
(`no_stairs_flight_up_while_starving`) failed on main and passed with the change.

## What the rig said

`:rig:direction --set standard --class WARRIOR --first 40 --salt-base 1000`, baseline main 58a477dde:

| | main | no flight while starving |
|---|---|---|
| reach depth 5 | 33/40 | 33/40 |
| Goo kills | 32/40 | 29/40 |
| median turns | 2240.5 | 2238.0 |
| deepest mean / max | 5.875 / 7 | 5.900 / 8 |
| deaths by depth 1..8 | 0/1/3/3/2/15/16/0 | 0/1/2/4/5/11/15/2 |
| oscillation Steps | 595 | 565 |
| flights up followed by a Descend | 76 | 24 |
| of those, `descend: hungry` | 58 | 8 |

The change does what it says: the hungry bounces fall from 58 to 8. It does not help the measure the issue
names. Reach of depth 5 is the same, Goo kills fall by three, and deaths move up the dungeon (depths 4 and 5:
5 to 9) while the deepest mean barely moves. At n=40 three Goo kills are inside the noise, but nothing in the
table is better, so the change is not kept (the issue's rule: keep or revert on the numbers).

## Why it may not pay (a guess, not measured)

The flight up lands the hero on the stairs with the enemy adjacent or close, and the hero can leave again at
once; without it the hero fights on the open floor or walks away from a faster enemy. That is a conjecture the
logs were not read for. What is measured is that the earlier attempts on the issue (lingering upstairs,
fleeing up only when hurt) and this one all lost Goo kills or left them level, and the change here cut the
table's oscillation Steps only from 595 to 565.

## If this is reopened

- The remaining lever is the one the issue lists: fight better on arrival at the foot of the stairs, where the
  enemy stands adjacent to or near the arrival cell. A fight there starts on the hero's turn.
- A hungry hero that is not yet starving can rest above (`Descend.rests`), so only the starving case was
  changed. Why the first rest above ends so soon (the logs show one `Rest` and then the `Descend`) was not traced.
- Compare a change by Goo kills on a second slice (salt base 2000) as well, as the Lessons from E4 ask.
