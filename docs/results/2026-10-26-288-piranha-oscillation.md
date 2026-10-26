# Issue #288: the shore the plan has just crossed into is not set again

**A direction check, unregistered.** Nothing here ran under a Registration and no sequential test was run. It measures
a bug fix to the shore of a piranha's water (issue #219, step 6). Upstream `v4.0.2`, Profile 6, the `shatterfish` Brain
on `weights/shatterfish.json`, Oracle off. Seeds from the `standard` set only.

Commands (the baseline is `origin/main` at `efbccd998`, built in a worktree by the tool):

```
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 40 --salt-base 1000 --baseline origin/main --jobs 2"
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 80 --salt-base 3000 --baseline origin/main --jobs 2"
```

## The bug

In two tuning-slice Runs (`VX-XDX-IKI` on depth 2, `IIU-BNU-NUS` on depth 4) the hero died to three giant piranhas
after stepping back and forth between two cells beside their water for six waits (`frontier` then `away 1`, over and
over) while the piranhas swam up from four cells away to one. `Brain.handed` lifts the region a Step of the explore,
descend or pick-up Policy enters, and then `Fight.shore` set that cell again, because it is not yet the hero's cell.
The next wait the hero stood inside a region and `Explore.away` walked it out.

## The change

`Fight.shore` takes the cell of the Step just handed over and does not cover it. The test is
`WaterBoundTest.forced_crossing_does_not_oscillate`; it fails on main (`away 1` at round 3, the hero walking 2, 1, 2).

## The tables

Tuning set, 40 Runs, salts 1000 to 1039:

| | origin/main | this change | delta |
|---|---|---|---|
| reach depth 5 | 28/40 | 28/40 | 0 |
| Goo kills | 25/40 | 25/40 | 0 |
| median turns | 2130.5 | 2077.0 | -53.5 |
| deepest mean / max | 5.350 / 8 | 5.300 / 8 | -0.05 |
| deaths by depth 1..8 | 0/1/7/4/3/17/6/2 | 0/1/7/4/3/19/4/2 | |
| oscillation Steps | 439 | 452 | +13 |

Second slice, 80 Runs, salts 3000 to 3079:

| | origin/main | this change | delta |
|---|---|---|---|
| reach depth 5 | 44/80 | 44/80 | 0 |
| Goo kills | 41/80 | 41/80 | 0 |
| median turns | 1974.0 | 1974.0 | 0 |
| deaths by depth 1..8 | 5/9/10/12/7/19/14/4 | 5/9/10/12/7/19/14/4 | |
| oscillation Steps | 1239 | 1216 | -23 |

## What the tables do not show

- The oscillation in the two Runs is gone (their strategy logs no longer alternate `away` and `frontier`), but both
  Runs still end in death on the same floors (the records of this table are not debug records, so the killer is not
  named; before the change it was a piranha). The hero now walks the shore without stopping, past three piranhas. The
  crossing itself is the next cause to look at; this change only removes the wasted waits.
- The 80 slice is unchanged in every column except the oscillation count and heaps: that slice rarely meets the case.
- The 40-slice oscillation count rose by 13 while the 80-slice count fell by 23. Changed trajectories shift the other
  loops (which have other causes); the net over 120 Runs is -10, within the noise of one table. No reach or Goo cost.
