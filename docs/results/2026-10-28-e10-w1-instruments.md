# E10 W1: the new instruments on a real slice

The direction check gains the boss staircase (reach of depth 5, 10 and 15; Goo and Tengu kills by name from `Statistics.bossScores` indexes 0 and 1), a think-time row, a deaths-by-depth killer table and `--class ALL` (E10 W1; the ADR-0011 and ADR-0018 amendments of 2026-10-28). The numbers below are the first the new rows show: a direction check on the `standard` set, unranked, with the Brain unchanged. `Goo kills` and `bosses killed` agree because no Run reached the Tengu floor.

## Warrior, the tuning set

`./gradlew :rig:direction --args="--set standard --class WARRIOR --first 40 --salt-base 1000"`

| | current 0ac42531b |
|---|---|
| Runs | 40 |
| reach depth 5 | 33/40 |
| reach depth 10 | 0/40 |
| reach depth 15 | 0/40 |
| bosses killed | 32/40 |
| Goo kills | 32/40 |
| Tengu kills | 0/40 |
| median turns | 2240.5 |
| deepest mean / max | 5.875 / 7 |
| deaths by depth 1..7 | 0/1/3/3/2/15/16 |
| searches per floor | 11.69 |
| search waits per floor | 17.45 |
| oscillation Steps | 595 |
| heaps taken / seen | 3334/3676 |
| seen to taken, median waits | 8 |
| heaps taking over 100 waits | 126 of 3676 |
| upgrade scrolls picked up by depth 5 | 51 |
| upgrades read by depth 5 | 89 |
| think ms per Decision, mean / p95 | 1.5 / 5 |

Deaths by depth, top killers (the game's own cause of death, read after the Run; a death with no ending record is "not recorded"):

| depth | deaths | top killers |
|---|---|---|
| 2 | 1 | Piranha 1 |
| 3 | 3 | Crab 2, Hunger 1 |
| 4 | 3 | Crab 2, Slime 1 |
| 5 | 2 | Goo 1, Poison 1 |
| 6 | 15 | Skeleton 10, Hunger 2, Poison 2, Thief 1 |
| 7 | 16 | DM100 5, Skeleton 5, Guard 3, Bleeding 1, +2 more |

## Every class, 20 each

`./gradlew :rig:direction --args="--set standard --class ALL --first 20 --salt-base 1000"`

| | WARRIOR | MAGE | ROGUE | HUNTRESS | DUELIST | CLERIC | pooled |
|---|---|---|---|---|---|---|---|
| Runs | 20 | 20 | 20 | 20 | 20 | 20 | 120 |
| reach depth 5 | 15/20 | 10/20 | 4/20 | 12/20 | 7/20 | 4/20 | 52/120 |
| reach depth 10 | 0/20 | 0/20 | 0/20 | 0/20 | 0/20 | 0/20 | 0/120 |
| reach depth 15 | 0/20 | 0/20 | 0/20 | 0/20 | 0/20 | 0/20 | 0/120 |
| bosses killed | 15/20 | 5/20 | 2/20 | 9/20 | 4/20 | 4/20 | 39/120 |
| Goo kills | 15/20 | 5/20 | 2/20 | 9/20 | 4/20 | 4/20 | 39/120 |
| Tengu kills | 0/20 | 0/20 | 0/20 | 0/20 | 0/20 | 0/20 | 0/120 |
| median turns | 2280.0 | 1484.5 | 1252.0 | 1784.5 | 1622.0 | 1191.0 | 1604.5 |
| deepest mean / max | 5.750 / 7 | 4.600 / 7 | 3.500 / 6 | 4.550 / 7 | 4.050 / 8 | 3.700 / 7 | 4.358 / 8 |
| deaths by depth 1..8 | 0/0/2/3/0/8/7/0 | 0/0/5/5/6/1/3/0 | 1/2/9/4/2/2/0/0 | 1/2/5/0/3/7/2/0 | 2/0/5/6/4/2/0/1 | 1/2/9/4/0/2/2/0 | 5/6/35/22/15/22/14/1 |
| searches per floor | 11.45 | 13.14 | 11.80 | 12.14 | 14.16 | 11.99 | 12.41 |
| search waits per floor | 17.43 | 21.95 | 16.47 | 18.69 | 21.58 | 18.57 | 19.12 |
| oscillation Steps | 297 | 314 | 172 | 270 | 237 | 220 | 1510 |
| heaps taken / seen | 1612/1760 | 1114/1272 | 1020/1132 | 942/1046 | 1257/1358 | 786/883 | 6731/7451 |
| seen to taken, median waits | 7 | 8 | 8 | 8 | 7 | 8 | 8 |
| heaps taking over 100 waits | 64 of 1760 | 36 of 1272 | 50 of 1132 | 71 of 1046 | 41 of 1358 | 34 of 883 | 296 of 7451 |
| upgrade scrolls picked up by depth 5 | 25 | 39 | 9 | 18 | 18 | 14 | 123 |
| upgrades read by depth 5 | 43 | 39 | 24 | 33 | 33 | 30 | 202 |
| think ms per Decision, mean / p95 | 1.3 / 4 | 2.0 / 7 | 2.4 / 8 | 1.2 / 4 | 2.1 / 7 | 2.8 / 10 | 1.9 / 6 |

Deaths by depth, top killers (the game's own cause of death, read after the Run; a death with no ending record is "not recorded"):

#### WARRIOR

| depth | deaths | top killers |
|---|---|---|
| 3 | 2 | Crab 1, Hunger 1 |
| 4 | 3 | Crab 2, Slime 1 |
| 6 | 8 | Skeleton 4, Hunger 2, Poison 1, Thief 1 |
| 7 | 7 | DM100 2, Guard 2, Skeleton 2, Bleeding 1 |

#### MAGE

| depth | deaths | top killers |
|---|---|---|
| 3 | 5 | Swarm 2, Crab 1, Hunger 1, Piranha 1 |
| 4 | 5 | Crab 2, Gnoll 1, Poison 1, ToxicGas 1 |
| 5 | 6 | Goo 3, Ooze 2, Poison 1 |
| 6 | 1 | Hunger 1 |
| 7 | 3 | DM100 1, Electricity 1, Guard 1 |

#### ROGUE

| depth | deaths | top killers |
|---|---|---|
| 1 | 1 | Rat 1 |
| 2 | 2 | Gnoll 1, Hunger 1 |
| 3 | 9 | Swarm 3, HermitCrab 2, Crab 1, Gnoll 1, +2 more |
| 4 | 4 | Crab 3, Hunger 1 |
| 5 | 2 | Goo 2 |
| 6 | 2 | Hunger 1, Skeleton 1 |

#### HUNTRESS

| depth | deaths | top killers |
|---|---|---|
| 1 | 1 | Snake 1 |
| 2 | 2 | Gnoll 1, Hunger 1 |
| 3 | 5 | Crab 2, Gnoll 1, Mimic 1, Swarm 1 |
| 5 | 3 | Goo 2, Ooze 1 |
| 6 | 7 | Skeleton 4, Ooze 1, Thief 1, ToxicGas 1 |
| 7 | 2 | DM100 1, Skeleton 1 |

#### DUELIST

| depth | deaths | top killers |
|---|---|---|
| 1 | 2 | Snake 2 |
| 3 | 5 | Crab 1, Gnoll 1, Piranha 1, Snake 1, +1 more |
| 4 | 6 | Crab 2, Gnoll 1, Hunger 1, Piranha 1, +1 more |
| 5 | 4 | Goo 3, Hunger 1 |
| 6 | 2 | Ooze 1, Poison 1 |
| 8 | 1 | Guard 1 |

#### CLERIC

| depth | deaths | top killers |
|---|---|---|
| 1 | 1 | Snake 1 |
| 2 | 2 | Gnoll 1, Hunger 1 |
| 3 | 9 | Crab 3, Gnoll 3, Swarm 3 |
| 4 | 4 | Crab 1, Hunger 1, Slime 1, ToxicGas 1 |
| 6 | 2 | Hunger 1, Thief 1 |
| 7 | 2 | DM100 1, Skeleton 1 |

#### pooled

| depth | deaths | top killers |
|---|---|---|
| 1 | 5 | Snake 4, Rat 1 |
| 2 | 6 | Gnoll 3, Hunger 3 |
| 3 | 35 | Swarm 10, Crab 9, Gnoll 6, Piranha 3, +4 more |
| 4 | 22 | Crab 10, Hunger 3, Gnoll 2, Slime 2, +4 more |
| 5 | 15 | Goo 10, Ooze 3, Hunger 1, Poison 1 |
| 6 | 22 | Skeleton 9, Hunger 5, Thief 3, Ooze 2, +2 more |
| 7 | 14 | DM100 5, Skeleton 4, Guard 3, Bleeding 1, +1 more |
| 8 | 1 | Guard 1 |
