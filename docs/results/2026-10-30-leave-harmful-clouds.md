# Leaving harmful clouds (issue #408)

The `escape` Policy steps the hero out of fire and harmful gas it did not seed itself. This page records what it
measured, the mechanics it rests on, and what it does not cover.

## The failure it fixes

Watched in the owner's Overlay Run on standard Warrior seed 648322377831, depth 4, turns 1194 to 1202: a trap's toxic
gas filled the hero's corridor and the Brain picked eight fallback Actions at random in it. No Policy owned "I am
standing in a harmful cloud": `test-item` had an escape step but only after its own gas, and `explore` had no path
while the hero stood in a cloud (`Explore.walkable` refused clouded cells).

## Mechanics the Policy uses

| Claim | Citation |
|---|---|
| Toxic gas hurts `1 + depth/5` per turn | `ToxicGas.java:40`, `ToxicGas.java:48-51` |
| A gas acts at the end of the turn, after the hero has moved | `Blob.java:123-126` |
| A blob spreads to its four orthogonal neighbours only; a closed door is solid | `Blob.java:166-186`, `Terrain.java:90` |
| Paralytic gas paralyses for 10 turns, and a paralysed hero gets no input | `ParalyticGas.java:52`, `Paralysis.java:34`, `Hero.java:873-878` |
| Corrosive gas corrodes armour and weapon | `CorrosiveGas.java:58`, `Corrosion.java:73`, `Corrosion.java:106-115` |
| Fire ignites a non-immune character on its cell, burning 8 turns for 1 to `3 + depth/4` | `Fire.java:100-102`, `Burning.java:54`, `Burning.java:105-108` |
| Water ends burning | `Burning.java:99` |

Decision on paralytic gas: it cannot be walked out of once it lands, so the Policy does not wait for it to reach the
hero. It enters when a paralytic gas is drawn on the hero's cell or on an orthogonal neighbour, costs those
neighbours as if they were gas, and never stops to fight beside it. Confusion gas is not in `Explore.HARMFUL`, so it
is out of scope here.

## What the Policy does

It enters when the hero's own cell shows a harmful blob (`Explore.HARMFUL`) or holds a remembered cloud
(`Beliefs.clouds`). It routes by Dijkstra over cells it may cross, each costed by the expected damage of the cloud on
it, to the nearest clear cell. A cell with no cloud beside it is preferred to one that has, and a burning hero
prefers water within reach. An adjacent enemy does not stop it unless Fight says the enemy dies this turn (hit chance
at least one half and the hero's least damage beats the health bar), and never in paralytic gas. `TestItem.escape`
now calls the same search. The decision record and the strategy log carry a new `in-harm` Safety flag.

## Rig numbers

`:rig:direction`, standard set, baseline is the merge base `5fe283e37` of this branch with `main`, so the only
difference is this change. Every column is the Runs' own log except the last four rows, which `DirectionHarm` reads
from the Observations the Brain was handed (so the baseline, which has no `in-harm` flag, is counted by the same
rule). The ending is a guess from the last screen the Brain saw, not the game's account of the death.

First slice: `--first 40 --salt-base 1000`, each class. Confirmation: Warrior `--first 80 --salt-base 3000`.

| Slice | reach d4 | reach d5 | reach d6 | Goo kills | waits in fire or gas | deaths with the last screen in fire or gas |
|---|---|---|---|---|---|---|
| Warrior 40 | 37 to 35 | 35 to 33 | 32 to 31 | 33 to 31 | 112 to 123 | 0 to 1 |
| Mage 40 | 30 to 28 | 18 to 19 | 6 to 8 | 7 to 9 | 126 to 80 | 1 to 0 |
| Rogue 40 | 20 to 19 | 10 to 10 | 5 to 3 | 5 to 3 | 79 to 68 | 1 to 0 |
| Huntress 40 | 25 to 26 | 21 to 21 | 14 to 14 | 16 to 15 | 81 to 88 | 1 to 0 |
| Duelist 40 | 28 to 27 | 13 to 14 | 5 to 7 | 7 to 8 | 85 to 103 | 1 to 2 |
| Cleric 40 | 22 to 21 | 7 to 6 | 6 to 6 | 6 to 6 | 71 to 67 | 1 to 1 |
| All six, 240 Runs | 162 to 156 | 104 to 103 | 68 to 69 | 74 to 72 | 554 to 529 | 5 to 4 |
| Warrior 80 (confirmation) | 59 to 61 | 55 to 56 | 46 to 49 | 48 to 51 | 283 to 198 | 3 to 1 |

Reading it:

- The first slice is within noise on every survival column (reach d5 minus one run in 240, Goo kills minus two). A
  Run that takes a different Step on one floor lands on a different floor afterwards, so a change near the hero's
  feet moves later fights by chance. Warrior 40 is the one cell that is worse (reach d5 35 to 33, Goo 33 to 31).
- The confirmation slice is better on every survival column and cuts the waits spent in fire or gas by 30 percent
  (283 to 198).
- The number of Runs that stood in fire or gas at least once is identical on both sides in every slice (27, 16,
  19, 21, 15, 18 and 54): the Policy does not stop the hero from walking into a cloud, only from staying in it.
- Together the two Warrior slices (120 Runs) read reach d5 90 to 89 and Goo kills 81 to 82.
- The Warrior 80 current side made 174 `escape` Decisions and raised `in-harm` on 249 waits; the baseline raised
  neither.

What the tables cannot show: a death in gas is rare (5 of 240 on the first slice), so the death columns are counts of
one or two. The case that matters most, a cloud with no way out, is covered by the unit tests (`EscapeTest`), not by
the Rig.

## What it does not do

- It reacts to a cloud on the hero's own cell (and to paralytic gas beside it). It does not yet refuse a planned step
  into a cloud: Autoexplore's whitelist excludes `escape`, and a path that crosses gas is still chosen by `explore`.
- Confusion gas is not in `Explore.HARMFUL`.
- `:rig:direction` plays the baseline on the current host code; the harness is the same on both sides.

## Reproducing

```sh
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 40 --salt-base 1000 --baseline 5fe283e37 --jobs 3"
```
