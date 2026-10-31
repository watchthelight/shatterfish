# The starving stairs bounce

Most flights up the stairs end with the hero going straight back down. This page records the count on current
`main`, why it is a hunger problem and not a fight-placement one, and where it belongs. It closes story 10.14 with
no code (issue #360).

## What the logs show

Reading the 80 Run logs of standard Warrior, salts 3000 to 3079, played with `:rig:direction` on the Brain of PR
#420 (`Escape` merged, otherwise main at `5fe283e37`):

- 73 of the 80 Runs retreated by the stairs at some point; the logs hold 154 `Ascend` Actions.
- 115 of the 154 are followed by a `Descend` within 8 waits, with a median of 1 wait.
- 101 of the 154 `Ascend`s are taken while the hero is starving, and 97 of the immediate returns say
  `descend: hungry`.
- The 53 `Ascend`s taken while not starving are too few to separate a change from noise on 40 or 80 Runs per
  slice.

## What 10.14 asked for, and what is already built

| Asked | Where it is |
|---|---|
| Fight at a chokepoint when more than one enemy closes in | `Fight`: take and hold a cell at most two enemies can engage, `HOLDS` waits |
| Retreat by the upstairs when the next exchange is a loss | `Fight.retreat`: `retreat: stairs`, regular entrance and exit, at most `FLIGHTS` per floor |
| What an enemy does when the hero leaves the level | Rule `hostiles-stay-on-their-floor` in `docs/brain-rules.md` |
| Rest on the floor above | `Descend`: "come up from below onto the exit: rest here, then down" |

## Why it was not built again

The change that skips the stairs up while starving (`docs/ideas/2026-10-27-no-stairs-flight-while-starving.md`,
issue #202) was built, measured and reverted: reach d5 33/40 to 33/40 and Goo kills 32/40 to 29/40. A hero that
cannot flee up is a hero that fights on, and a starving hero regenerates nothing (`Regeneration.java:56`), so
the bounce is a symptom: the hero is starving, and fleeing was the lesser of two bad choices.

## Where it belongs

Hunger itself, which is story 10.15 (prison food budgeting, done). A starving hero on the Prison depths is the
cause; the bounce is how it shows. If a later food story lowers the starving share, the bounce count falls with
it and this page's numbers are the baseline to compare against.
