# Story 10.48, the missile, dew and pick-up worth levers at full size

Issue #412, epic E10. Pull request #426 merged nine levers dormant (every number 0) because they played worse than main on
a 240-Run slice, and the re-measure on issue #412 (80 Runs, edc544ade) found "nothing passes" from differences of one or
two Runs. That verdict was weak. This page measures each lever on its own at full size and keeps what passes. One lever
is kept: `ranged.fetch_steps` at 2.

## How it was measured

Each lever alone: `:rig:direction --set standard --class ALL --first 40 --salt-base <1000|3000> --jobs 4`, 240 Runs per
slice, 480 pooled, the standard set only (never `goo`, `holdout` or `tengu`). The denominators are 240 per slice and 480
pooled for every row below, the same on both sides. A control (the current build with no lever) played against the
baseline gave the same numbers on both sides (slice 1000: reach depth 5 137 and Goo kills 117 of 240; slice 3000: 127 and
107), so the paired columns differ only by the lever.

The first batch was played on main `28f4fe38d`. Food worth (#457) and the gear levers (#461) then merged, so the one lever
that passed was played again on main `9cd3af90e`, with the baseline pinned to that SHA. The two levers that were not
played on the first batch (grass and trample) were played on top of the kept lever, against the `current` side of that
confirmation.

Keep rule: pooled over both slices not worse in reach depth 5 and in Goo kills, and no class 8 or more worse in either.
E3 acceptance is not required.

Mechanics behind the levers are in `docs/rules/pickup.md` (15 rows with `path:line` at `v4.0.2`, read for #412).

## First batch, main 28f4fe38d (baseline pooled: reach depth 5 264, Goo kills 224 of 480)

Reach depth 5 / Goo kills, pooled; worst class change in brackets.

| lever (value) | slice 1000 | slice 3000 | pooled | worst class | verdict |
|---|---|---|---|---|---|
| `ranged.fetch_steps` 2 | 137 to 137 / 117 to 117 | 127 to 131 / 107 to 113 | 264 to 268 (+4) / 224 to 230 (+6) | Rogue -6 reach | passes |
| `ranged.min_damage_per_mille` 1500 | 137 to 133 / 117 to 114 | 127 to 123 / 107 to 109 | 264 to 256 (-8) / 224 to 223 (-1) | Rogue -9 reach | dropped |
| `pickup.dew_skin_below` 20 | 137 to 136 / 117 to 119 | 127 to 122 / 107 to 98 | 264 to 258 (-6) / 224 to 217 (-7) | Duelist -8 Goo | dropped |
| `pickup.missile_reach` 20 | 137 to 133 / 117 to 112 | 127 to 127 / 107 to 113 | 264 to 260 (-4) / 224 to 225 (+1) | Cleric -3 | dropped |
| weight `heal` 2000 | 137 to 140 / 117 to 118 | 127 to 130 / 107 to 105 | 264 to 270 (+6) / 224 to 223 (-1) | Cleric -3 Goo | dropped (Goo -1) |
| weight `upgrade` 2000 | 137 to 133 / 117 to 116 | 127 to 126 / 107 to 106 | 264 to 259 (-5) / 224 to 222 (-2) | Cleric -9 Goo | dropped |
| weight `useful` 1000 | 137 to 130 / 117 to 109 | 127 to 131 / 107 to 116 | 264 to 261 (-3) / 224 to 225 (+1) | Cleric -5 Goo | dropped |

The weights are the "pick-up worth" lever: `Evaluation.identity` adds the weight to a heap that is a potion of healing,
a scroll of upgrade, or one of the useful scrolls, on top of the `item` weight 2000 and the `turn` cost 150 a Step. They
were each played at one value, not tuned. `heal` is the near miss (reach depth 5 +6, Goo kills -1, so it fails the rule as
written); the other two lose reach or Goo kills outright. The slice 1000 and slice 3000 columns often disagree in sign
(`useful` loses 8 Goo kills on 1000 and gains 9 on 3000), which is what the noise at 240 Runs looks like, and why pooled
is the test.

Per class, reach depth 5 / Goo kills, pooled over both slices (baseline to lever):

| lever | Warrior | Mage | Rogue | Huntress | Duelist | Cleric |
|---|---|---|---|---|---|---|
| `fetch_steps` 2 | 60 to 69 / 50 to 58 | 41 to 41 / 38 to 37 | 37 to 31 / 22 to 22 | 47 to 47 / 40 to 40 | 51 to 53 / 47 to 47 | 28 to 27 / 27 to 26 |
| `min_damage` 1500 | 60 to 61 / 50 to 52 | 41 to 41 / 38 to 38 | 37 to 28 / 22 to 22 | 47 to 47 / 40 to 40 | 51 to 51 / 47 to 44 | 28 to 28 / 27 to 27 |
| `dew_skin_below` 20 | 60 to 63 / 50 to 53 | 41 to 41 / 38 to 35 | 37 to 32 / 22 to 23 | 47 to 48 / 40 to 42 | 51 to 45 / 47 to 39 | 28 to 29 / 27 to 25 |
| `missile_reach` 20 | 60 to 61 / 50 to 52 | 41 to 41 / 38 to 37 | 37 to 34 / 22 to 27 | 47 to 47 / 40 to 41 | 51 to 52 / 47 to 44 | 28 to 25 / 27 to 24 |
| `heal` 2000 | 60 to 62 / 50 to 54 | 41 to 42 / 38 to 35 | 37 to 35 / 22 to 23 | 47 to 48 / 40 to 41 | 51 to 55 / 47 to 46 | 28 to 28 / 27 to 24 |
| `upgrade` 2000 | 60 to 60 / 50 to 56 | 41 to 46 / 38 to 42 | 37 to 32 / 22 to 21 | 47 to 48 / 40 to 43 | 51 to 47 / 47 to 42 | 28 to 26 / 27 to 18 |
| `useful` 1000 | 60 to 60 / 50 to 51 | 41 to 39 / 38 to 38 | 37 to 38 / 22 to 23 | 47 to 48 / 40 to 45 | 51 to 48 / 47 to 46 | 28 to 28 / 27 to 22 |

## Confirmation, main 9cd3af90e, `ranged.fetch_steps` 2 (baseline pooled: reach depth 5 280, Goo kills 229 of 480)

| | slice 1000 | slice 3000 | pooled |
|---|---|---|---|
| Runs | 240 | 240 | 480 |
| reach depth 5 | 144 to 147 | 136 to 138 | 280 to 285 (+5) |
| Goo kills | 121 to 126 | 108 to 115 | 229 to 241 (+12) |
| deepest mean | 4.983 to 5.088 | 4.771 to 4.829 | 4.877 to 4.959 (+0.082) |

Goo kill rate 47.7% to 50.2% of Runs. Per class (reach depth 5 / Goo kills, pooled): Warrior 61 to 66 / 51 to 57, Mage 50 to
50 / 45 to 45, Rogue 39 to 38 / 24 to 29, Huntress 48 to 48 / 39 to 39, Duelist 51 to 54 / 46 to 47, Cleric 31 to 29 / 24 to
24. Worst class on reach depth 5 is Cleric at -2; no class is worse on Goo kills. Kept.

The lever acts only in a fight with no enemy beside the hero and none able to reach the pick-up first, so it fires
rarely. It moved Goo kills +6 on the first main and +12 on the second, with most of the gain in the Warrior and the other
classes within a few Runs of zero; the size is small, and what supports it is that both mains and both slices of the
second one point the same way (+5 and +7).

## Grass and trample, on top of `fetch_steps` 2 (baseline: the confirmation's `current` side)

| lever (value) | slice 1000 | slice 3000 | pooled | worst class | verdict |
|---|---|---|---|---|---|
| `explore.grass_skin_below` 20 | 147 to 136 / 126 to 117 | 138 to 126 / 115 to 103 | 285 to 262 (-23) / 241 to 220 (-21) | Duelist -9 reach, Mage -9 Goo | dropped |
| `descend.trample_reach` 10 | 147 to 149 / 126 to 122 | 138 to 124 / 115 to 110 | 285 to 273 (-12) / 241 to 232 (-9) | Mage -8 Goo | dropped |

## What the rig does not show

The direction table has no column for missiles recovered, dew collected or items left behind, so these rows say whether
the play was better, not why. The one-value-per-lever design means a different `min_damage`, `missile_reach` or weight
could pass where this one did not; the weights especially were not tuned (`heal` came within one Goo kill). A lever
dropped here is dropped at the value named. They stay in the registry at 0, so a tuning run can still move them.

## Outcome

- `ranged.fetch_steps` committed at 2 (`Ranged.FETCH_STEPS`, `Tunables.RANGED_FETCH_STEPS`).
- Every other #412 lever stays at 0: `pickup.missile_reach`, `pickup.dew_skin_below`, `ranged.min_damage_per_mille`,
  `explore.grass_skin_below`, `descend.trample_reach`, and the weights `heal`, `upgrade`, `useful`.
- Test first (ADR-0019): `MissilesAndDewTest.the_committed_playbook_fetches_a_near_stone` failed on main (13 run, 1 failed)
  and passes with the change.
