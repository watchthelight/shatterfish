# Story 10.17: the power curve, and drinking the strength potion

Issue #363. The Prison diagnosis (story 10.7) left one reading standing: the deaths on depths 5 to 9 are about
fighting strength, not food. This page measures what the hero carries by depth, and keeps the first lever that
raises it: a known potion of strength is drunk at once, when the screen is calm.

## What the hero holds by depth

Per class, the median and the 10th percentile at the first arrival on each depth, from the Runs of slice 1000 on
main (`logs/2026-10-31-10-17-power-curve/curve-main-s1000.txt`) and with the lever on
(`curve-l1-s1000.txt`; both files hold every depth from 1 to 8). Fields in the files: level, strength, hit
points, armour tier, armour upgrades, weapon upgrades, unspent scrolls of upgrade, known potions of strength
held. The Codex supply is 4 potions of strength by depth 9, 2 of them by depth 5 (`Dungeon.java:529-544`,
`PotionOfStrength.java:40-47`), and 6 scrolls of upgrade by depth 9 (the story's figure, not re-read here).

Depth 6, median / p10:

| Class | strength, main | strength, lever | armour tier | armour +N | weapon +N | scrolls held | potions held, main | potions held, lever |
|---|---|---|---|---|---|---|---|---|
| Warrior | 11 / 10 | 11 / 10 | 1 / 1 | 2 / 1 | 1 / 1 | 0 / 0 | 0 / 0 | 0 / 0 |
| Mage | 11 / 11 | 12 / 11 | 1 / 1 | 2 / 1 | 1 / 1 | 0 / 0 | 1 / 0 | 0 / 0 |
| Rogue | 11 / 11 | 11.5 / 11 | 1 / 1 | 2 / 1 | 1 / 1 | 0 / 0 | 0 / 0 | 0 / 0 |
| Huntress | 11 / 11 | 11 / 11 | 1 / 1 | 2 / 1 | 1 / 1 | 0 / 0 | 0 / 0 | 0 / 0 |
| Duelist | 10 / 10 | 11.5 / 11 | 1 / 1 | 2 / 1 | 1 / 1 | 0 / 0 | 2 / 1 | 0 / 0 |
| Cleric | 11 / 11 | 12 / 11 | 1 / 1 | 2 / 1 | 1 / 1 | 0 / 0 | 1 / 0 | 0 / 0 |

- Every class wears the starting cloth armour on every depth. The scrolls of upgrade are spent (+2 on the
  armour, +1 on the weapon by depth 6) and none is held unread, so the upgrade supply is used and the armour
  tier does not change.
- On depth 6 the supply is at least 12 strength (10 plus the 2 potions of depths 1 to 5). On main all six
  classes are below it. With the lever Mage and Cleric reach it, Rogue and Duelist are within half a point,
  and Warrior and Huntress stay at 11: they hold no potion at the first arrival on depth 6, so the potions
  they find come later or not at all. Those two are the classes below supply on depth 6.
- On main the potion sat in the pack: a known potion of strength held and unused was 29 percent of heroes on
  depth 4, 49 percent on depth 5 and 71 percent on depth 7 (the first measurement of this story, taken on the
  10.39 tree).

## The two levers

1. Drink a known potion of strength (and experience) when calm: the Playbook's `known_gains` was 0 and is now
   1. The code already existed (`TestItem.gain`, story 7.3); 7.3 measured it on Warrior alone, before armour
   swaps and the upgrade allocation, and shipped 0.
2. Price the strength an armour asks as the game does: 2 armour a missing point (`Hero.java:652-656`) instead
   of cutting a third. Measured with lever 1, slice 1000, ALL-40 (240 Runs): the pooled table is identical to
   lever 1 alone (134 reach depth 5, 105 Goo kills, the same deaths by depth). Dropped from the pull request.
   Why no swap is decided differently was not established; the likely reading is that no Run holds both a
   heavier armour and the strength for it at a calm moment.

## Lever 1 against main (`fdebf751b`)

Slice 1000, ALL-40, `logs/2026-10-31-10-17-power-curve/S1000-direction.txt`:

| Class | reach d5 | Goo kills | deepest mean |
|---|---|---|---|
| Warrior | 33 to 34 | 28 to 28 | 6.03 to 6.13 |
| Mage | 21 to 21 | 18 to 16 | 4.90 to 4.88 |
| Rogue | 15 to 17 | 9 to 12 | 4.10 to 4.35 |
| Huntress | 26 to 25 | 24 to 20 | 5.18 to 4.95 |
| Duelist | 13 to 21 | 8 to 17 | 4.08 to 4.58 |
| Cleric | 15 to 16 | 11 to 12 | 4.20 to 4.23 |
| Pooled | 123 to 134 | 98 to 105 | 4.75 to 4.85 |

Pooled reach depth 6 is 95 to 101 and depth 8 is 10 to 12. The worst class is Huntress at -4 Goo kills, inside
the keep rule's exception line of 8.

Slice 3000, ALL-40, `logs/2026-10-31-10-17-power-curve/S3000-direction.txt`:

| Class | reach d5 | Goo kills | deepest mean |
|---|---|---|---|
| Warrior | 28 to 30 | 25 to 27 | 5.18 to 5.48 |
| Mage | 16 to 18 | 11 to 14 | 4.25 to 4.63 |
| Rogue | 11 to 13 | 5 to 6 | 3.80 to 3.93 |
| Huntress | 21 to 22 | 18 to 18 | 4.78 to 4.78 |
| Duelist | 18 to 21 | 13 to 16 | 4.40 to 4.83 |
| Cleric | 12 to 16 | 8 to 9 | 4.03 to 4.18 |
| Pooled | 106 to 120 | 80 to 90 | 4.40 to 4.63 |

Pooled reach depth 6 is 78 to 89 and depth 8 is 5 to 13. No class is below its baseline.

## What the tables cannot show

- The strength gain is small in the medians (half a point to one). The reach gain comes from the tail, the Runs
  that held a potion unused, and one point of strength is not the whole of it; the medians do not say which
  fights turned.
- Heavier armour is never worn above cloth in these Runs. Whether it would help at strength 12 or more is not
  measured. It is the next lever, not this one.
- The extra check, Warrior-80 on salt 3000 (`logs/2026-10-31-10-17-power-curve/W80-direction.txt`): reach depth 5
  is 54 to 56 of 80, reach depth 6 is 47 to 48, Goo kills 47 to 48, deepest mean 5.15 to 5.29. It agrees in
  sign and is small; it is not a gate.
