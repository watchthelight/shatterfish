# Skip a frontier past a piranha's shore when the exit is already in view

Issue #219 (the Goo rate). On main e70381b9e (standard WARRIOR, tuning slice salt 1000, 40 Runs) 12 Runs end before
depth 5. This page is the per-death diagnosis of the crab and piranha deaths and the one change that came out of it.

## Diagnosis: what the Brain did in the last waits

Read from the debug records (`--debug-record`) of the tuning slice.

Piranha deaths (3):

- **XVX-XDX, depth 2, 28 health to 5 in six waits.** The hero had made its one allowed walk out of the piranhas' reach
  (`retreat: leave water 1`, wait 539). After that `Brain.seen` hides the water-bound enemies from the Policies. At wait 569
  the explore Policy walked `frontier 4`, `frontier 3`, `search-spot 2`, `search-spot 1` along the shore past three giant
  piranhas, then issued `search 4/12` standing in the water with all three adjacent. Bites of 3 to 12 a wait.
- **IIU-BNU, depth 4, 32 to 15 in four waits, dead the next.** The same shape: one earlier walk out, then `frontier 4` four
  times through water beside three piranhas, then `search 3/12` in place between two of them.
- **KKO-SIP, depth 3.** `approach` toward a gnoll-side snake across a shore the piranha reached, after a `leave water`
  at the previous wait had lifted the piranha from the screen (the hero then stood out of reach, so it was shown again as
  scenery and the snake was still an enemy). 25 to 8 in three waits. Not covered by the change below.

Crab deaths (4, plus one gnoll scout finishing a hero a crab had reached): DLR-YWZ, NSP-PPL, OPG-ARJ, WUQ-AKJ. In all four
the hero meets the crab at under half health after earlier fights (a snake it was cornered by, a slime, a gnoll scout that
took 5 to 7, or starvation), logs `hold: no-way` for the two or three waits the crab needs to close, then `cornered: sewer
crab` and fights at 3 to 18 health against a monster that costs about 15. No retreat paid in any of them (the stairs were far
or the crab is faster, `docs/brain-rules.md#faster-enemy-no-outrunning`). No single avoidable mistake at the crab: the death
is decided one to three fights earlier, which `2026-10-26-stairs-ahead-of-a-crab.md`, `rest-by-need` and
`rest-when-hurt-and-fed` already found. Nothing new is proposed for crabs.

## The mistake chosen

Both fixable piranha deaths walk to a frontier or a search spot that lies past the shore of a piranha's water, after the
single walk out has hidden the piranhas, and one of them searches in place beside three. The hero had already seen the way
down on at least the floor it was leaving; nothing past the shore is worth three bites a wait.

## The change

`Explore.through` replaces the plain "through the regions" walk in `uncover` and `frontier`. When the screen shows a regular
exit (`Descend.known`), the one-cell regions the Brain sets around a piranha's water (`Fight.shore`, radius 0) stay blocked in
that walk, so there is no frontier or search spot to walk to there and the floor reads as spent; the descend Policy then takes
the hero to the exit, and its own walk may still cross the shore. With no exit in view the old behaviour stands (the shore is
the way on). Uses only the screen and the Memory: no change to `Observer`. Test `WaterBoundTest.known_exit_leaves_a_shore_frontier`
fails on main and passes with the change.

## Numbers (against origin/main, `--jobs 2`)

| Slice | reach depth 5 main / change | Goo kills main / change | deaths by depth 1..8 main / change |
|---|---|---|---|
| tuning 40, salt 1000 | 28 / 30 | 25 / 27 | 0/1/7/4/3/19/4/2 / 0/0/7/3/3/18/6/3 |
| confirmation 80, salt 3000 | 44 / 46 | 41 / 41 | 5/9/10/12/7/19/14/4 / 5/9/9/11/9/18/15/4 |
| 120 Runs together | 72 / 76 | 66 / 68 | |

The tuning-slice deaths XVX-XDX and IIU-BNU are the two Runs that no longer die before depth 5, which is the mechanism
working on exactly the Runs it was written from. The totals are inside the noise of about 5 Goo kills per 120 Runs, so this
is a small, mechanistically traced gain and not a measured rate change. Median turns 2077.0 to 2187.5 and 1974 to 1987.

## What the table cannot show

A hero that skips the shore also skips whatever lay past it (items, a secret door the exit's floor needed); the 80 slice shows
heaps taken rising by 48, so nothing was lost there. KKO-SIP (approaching a land enemy across a shore) and the crab deaths remain.
