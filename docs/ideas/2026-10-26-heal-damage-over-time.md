# Heal counts poison and a piranha's worst bites: no Run changes

Issue #219, the Goo gate (story 4.14). The desperate-potion page (PR #295) found that 4 of 40 tuning deaths held a
known potion of healing unused: two piranha bursts where `Heal.danger` stayed under the hit points until the last
turn, one poisoned at 5 HP with no enemy in view (Heal only enters with an enemy in view and ignores damage over
time), and one bomb at 23 HP. This lever tried the first two.

## The rule that was built and reverted

- Poison counts as damage. The screen shows the `poisoned` buff with its turns; each turn deals
  `(int)(left / 3) + 1` and `left` falls by 1 (`Poison.java:109-117`), so 10 turns left deal 25. The potion ends the
  poison (`PotionOfHealing.java:77`). `Heal` entered with no enemy in view when poisoned, and added the remaining
  poison damage to the danger (so a poisoned hero fighting counted both).
- A water-bound enemy (the piranha, tagged `leave-water`) in the threats made the danger at least two of its worst
  bites (damage max less the smallest armour roll) instead of one.
- Bleeding and burning were left out: the Observation shows bleeding without turns, and burning is `Douse`'s.
- The bomb was left out: the fuse is not in the Observation as a number.
- Tests: `HealPolicyTest` cases for the poison total (25 for 10 turns), a poisoned hero at 5 HP with nobody in view
  drinking, a barely poisoned hero at 19 HP keeping the potion, and the piranha floor. The poison case failed on main
  (commit 397332364, reverted in this branch). `:brain:test` was green with the change.

## Rig, `--baseline origin/main` (e70381b9e), standard WARRIOR, `--jobs 2`

Tuning slice, 40 Runs, salts 1000 to 1039:

| | origin/main | change | delta |
|---|---|---|---|
| reach depth 5 | 28/40 | 28/40 | 0 |
| Goo kills | 25/40 | 25/40 | 0 |
| median turns | 2077.0 | 2077.0 | 0 |
| deepest mean / max | 5.300 / 8 | 5.300 / 8 | 0 |
| deaths by depth 1..8 | 0/1/7/4/3/19/4/2 | 0/1/7/4/3/19/4/2 | |
| heaps taken / seen | 3020/3300 | 3014/3290 | -6 / -10 |

Confirmation slice, 80 Runs, salts 3000 to 3079:

| | origin/main | change | delta |
|---|---|---|---|
| reach depth 5 | 44/80 | 44/80 | 0 |
| Goo kills | 41/80 | 41/80 | 0 |
| median turns | 1974.0 | 1974.0 | 0 |
| deepest mean / max | 4.750 / 8 | 4.750 / 8 | 0 |
| deaths by depth 1..8 | 5/9/10/12/7/19/14/4 | 5/9/10/12/7/19/14/4 | |

Combined Goo kills over 120 Runs: 66 to 66. Not kept.

## What the tables say

The deaths by depth are identical in both slices, and the only column that moved is heaps seen and taken in one tuning
Run (a few turns of difference late in a Run that ends the same way). So the rule fired in at most one Run of 120 and
changed nothing that counts. Poison with no enemy in view, and piranha bursts that the old estimate missed, are
too rare among deaths (4 of 40 held a potion at all, and 2 of those 4 are these cases) for a potion drunk a turn or two
earlier to show in Goo kills; the potion heals a quarter a turn (`Healing.java:62`) and cannot outrun a burst of ten.

## What the tables cannot show

- Whether the rule is wrong: it is a correct reading of the pinned code and costs nothing when it does not fire. It
  is not merged because #219 asks for a gate rate and this does not move it, not because it hurts.
- The two kept-potion deaths left (a bomb at 23 HP; any case the potion is unidentified) need the bomb fuse in the
  Observation or story 4.10's identification, not a Heal threshold.
