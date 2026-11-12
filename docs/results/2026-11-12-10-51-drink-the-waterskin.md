# Story 10.51: the hero drinks its waterskin

Before this story the Brain never drank from the waterskin. The new `drink` Policy (issue #469) drinks it in a fight when hurt, on a calm screen
when hurt, and `Descend` drinks once before taking the stairs when the gain is worth it. A drink takes the drops needed to reach full health, each
drop heals 5% of maximum health, and it costs one turn (`docs/rules/pickup.md`, citing `Waterskin.java:74-78, :87-134` and `Dewdrop.java:83-84`).
The rule is `drink-waterskin` in `docs/brain-rules.md`; its three thresholds are in the tunable registry (`drink.min_gain_per_mille` 150,
`drink.fight_below_per_mille` 400, `drink.calm_below_per_mille` 600). The harness, `Observer` and the executor are unchanged: DRINK is already an
offered item action, and the volume comes from the item's displayed status.

## Method

Two ALL-40 slices of the `standard` set (40 Runs per class, 240 Runs per slice), salt bases 1000 and 3000, `--jobs 2` (slice 1000) and `--jobs 4`
(slice 3000), current build `a3a98ecc5` against a baseline built from `54fc50eaf`. Denominators are 240 per slice and 480 pooled in every row
below. No goo, holdout or tengu seed was used. The baseline column plays the baseline's brain on the current host code.

## Pooled over both slices (480 Runs)

| | baseline 54fc50eaf | with drink | delta |
|---|---|---|---|
| reach depth 5 | 290 | 300 | +10 |
| reach depth 6 | 236 | 249 | +13 |
| reach depth 7 | 137 | 173 | +36 |
| reach depth 8 | 32 | 45 | +13 |
| Goo kills | 243 | 256 | +13 |

Slice 1000 alone: reach depth 5 150 to 156, Goo kills 129 to 133. Slice 3000 alone: reach depth 5 140 to 144, Goo kills 114 to 123. Both slices
move the same way. Reach depth 6 to 8 are the Runs that died at that depth or deeper (every Run dies in these sets).

Deaths by depth 1 to 9, pooled: baseline 18/34/70/68/54/99/105/32/0, with drink 18/35/69/58/51/76/128/43/2. Fewer Runs die at depths 4 to 6 and more
reach, and then die at, depths 7 to 9.

## Per class, both slices added (80 Runs each)

| class | reach d5 | Goo kills |
|---|---|---|
| WARRIOR | 61 to 65 (+4) | 53 to 59 (+6) |
| MAGE | 48 to 48 (0) | 44 to 42 (-2) |
| ROGUE | 35 to 35 (0) | 20 to 23 (+3) |
| HUNTRESS | 52 to 54 (+2) | 43 to 47 (+4) |
| DUELIST | 53 to 52 (-1) | 47 to 45 (-2) |
| CLERIC | 41 to 46 (+5) | 36 to 40 (+4) |

The worst class is 2 worse (Mage and Duelist in Goo kills); no class is 8 or more worse in either slice.

## How often the rule fired

In the 480 current Runs the waterskin was drunk about 900 times in the Run logs (927 DRINK actions on the waterskin by a plain count), roughly 1.9 per
Run, in 397 of 480 Runs, at most 6 in one Run. By reason: calm 596, fight 191, before descending 112. The baseline Runs drank none.

Read from the 787 `drink hp/ht +gain` reasons in the Run logs (the descent reasons carry no health): the smallest gain was 15% of maximum health
(the floor), the mean health at a drink was 47% of maximum, and none was at full health. No Run drank more than 6 times, so there is no
drink-every-turn loop.

## Verdict

The keep rule holds: pooled reach depth 5 and Goo kills are not worse (both better), and no class is 8 or more worse. The Policy is kept. Reach
depth 5 is +10 of 480 (about two Runs in a hundred), a small gain that is still within what a pool of this size can show by chance on its own;
the shift of deaths from depths 4 to 6 to depths 7 to 9 and the Goo kills moving the same way in both slices make it more than noise to read, but
the page claims no more than "not worse, and probably better". No sequential test was run.
