# Boss practice kit and first practice numbers

Story 10.5 (#351), epic E10. The practice harness (`./gradlew :rig:practice`, [ADR-0024](../adr/0024-boss-practice-runs.md))
starts a Run on the boss floor with a kit a profile gives the hero. This page holds where the two profiles' numbers
come from and the first practice tables. These are direction checks (ADR-0012): practice Runs are never ranked,
and `goo` and `holdout` were not played.

## Where the kit comes from

Arrival records of a standard slice (`:rig:direction --set standard --class <C> --first 24 --salt-base 1000`, main at
`0d087c17e`; the rig now writes `.arrivals.tsv` beside each log, one line per depth from the first wait on it), mean per
class on arrival at depth 5:

| class | Runs at depth 5 | level | max HP | strength | armour | weapon | healing potions |
|---|---:|---:|---:|---:|---:|---:|---:|
| WARRIOR | 18 | 6.2 | 45.8 | 10.8 | +1.6 | +1.0 | 2.0 |
| MAGE | 13 | 6.2 | 45.8 | 10.9 | +1.8 | +0.9 | 1.0 |
| ROGUE | 5 | 6.2 | 46.0 | 10.8 | +1.2 | +1.0 | 1.0 |
| HUNTRESS | 14 | 5.9 | 44.3 | 10.9 | +1.5 | +1.0 | 0.3 |
| DUELIST | 7 | 6.3 | 46.4 | 10.0 | +1.7 | +1.0 | 0.0 |
| CLERIC | 4 | 6.5 | 47.5 | 10.2 | +2.0 | +1.0 | 0.2 |

Almost no Run arrives at depth 10 (the deepest of any slice was 8), so the depth-10 kits are not measured.

- `standard`: what a hero that found everything the floors above guarantee arrives with. Two potions of strength
  and three scrolls of upgrade per floor set (`Dungeon.java:529-562`), so 12 strength and 3 upgrades at depth 5 and 14 and 6
  at depth 10; armour takes two thirds of the upgrades. Level 6 at depth 5 (the table), 9 at depth 10
  (experience `5 + 5n` to leave level `n`, `Hero.java:2135-2137`; an estimate, not a record). Two potions of healing.
- `arrived`: the table rounded: depth 5 at level 6, strength 11, armour +2, weapon +1, one healing; depth 10 extrapolated
  along the same line, level 8, strength 12, armour +3, weapon +2, one healing.

## Calibration: Goo practice against real Goo, Warrior

Practice kills the Goo from the boss floor; real conversion is Goo kills over Runs that reached depth 5.

| | Runs | Goo killed | 95% Wilson |
|---|---:|---:|---|
| practice, `standard`, salts 1000..1039 | 40 | 40 | 91-100% |
| practice, `arrived`, salts 1000..1039 | 40 | 40 | 91-100% |
| practice, `standard`, salts 3000..3079 | 80 | 77 | 90-99% |
| practice, `arrived`, salts 3000..3079 | 80 | 71 | 80-94% |
| real, same slice as the arrivals (salts 1000..1023) | 18 reached | 17 | 74-99% |
| real, main (product owner's figure) | | about 91% | |

Practice lands on the real figure within its interval on the pessimistic profile (88.8% against about 91%); the
optimistic profile is higher. The real figure includes Runs that reach the Goo hurt or hungry, which practice does not
(it arrives at full health), so practice is an upper bound on the fight. I did not replay the real Warrior slice
at salts 3000 for a paired row; the real rows are the 24-Run slice and the stated figure.

## Tengu practice, all six classes (the W3 baseline)

40 Runs per class, `standard` set, salts 1000..1039, cap 5000 turns, Brain at main `0d087c17e`, `--jobs 12`.
Endings: killed Tengu / died / left the boss floor by its stairs (`LEFT_BOSS_FLOOR`, new, see below).

| class | `standard` kit | `arrived` kit |
|---|---|---|
| WARRIOR | 0 / 7 starved / 33 left | 0 / 6 starved / 34 left |
| MAGE | 0 / 0 / 40 left | 0 / 0 / 40 left |
| ROGUE | 0 / 40 starved / 0 | 0 / 39 starved / 1 other |
| HUNTRESS | 0 / 3 starved / 37 left | 0 / 3 starved / 37 left |
| DUELIST | 0 / 1 starved / 39 left | 0 / 1 starved / 39 left |
| CLERIC | 0 / 0 / 40 left | 0 / 0 / 40 left |
| all | 0/240 (0-2%) | 0/240 (0-2%) |

**Diagnosis of the first table.** The first table (killers DM100, Guard, Necromancer skeleton) was a Brain behaviour and
not a wrong floor. The start is the right one: depth 10 builds `PrisonBossLevel` (`Dungeon.java:320-322`) in state `START`
(`PrisonBossLevel.java:160`), the hero stands on the entrance, cell `10 + 4*32 = 138` (`PrisonBossLevel.java:166`), and the
level has no regular mob (`PrisonBossLevel.java:598`); `PracticeRunTest.tengu_start_is_the_boss_level` holds all of it. What the
debug records show is the Brain, with nothing to explore but the entrance room and the hallway, choosing "descend: floor"
from the entrance, whose stairs lead up: it climbed to depth 9, a fresh uncleared prison floor, and died to that floor's
DM100, Guards and Necromancer skeletons. The killer labels were right and the floor was not the Tengu floor. A practice Run
that leaves its floor is now ended as `LEFT_BOSS_FLOOR` and counted as the loss it is.

**The Rogue starves in every Run** because it stays on depth 10: it eats both rations (`eat: hunger` twice) and then spends
about 1800 turns on `act: nothing better applies` in the hallway until it starves. The hero arrives with no hunger
(`hunger: NONE` at turn 0) and the kit holds two rations, so neither the kit nor a carried hunger state explains it.

So in none of the 480 Runs did the Brain reach Tengu. The first lever for W3 is getting to the fight: the key lies in one of
the four side cells off the hallway, and the door at the hallway's end needs it (`PrisonBossLevel.java:621-648`). The
`.debug.ndjson` records beside each log hold the waits.

## Tengu practice after story 10.18 (entry, key and seal)

Story 10.18 (#364). The Brain now gets to the fight: a `tengu` Policy opens the cell door once the iron key is held,
and the stairs up are withheld on the unsealed boss floor ([Rule `tengu-entry`](../brain-rules.md#tengu-entry)). Same
slice as the baseline above: 40 Runs per class, `standard` set, salts 1000..1039, kit `standard`, cap 5000 turns,
`--jobs 12`. The table has two new columns, read from the Run's debug record: a key for depth 10 in the journal at any
wait, and the floor sealed at any wait.

| class | Runs | key held | seal reached | Tengu killed | died | left floor / other |
|---|---:|---:|---:|---:|---:|---:|
| WARRIOR | 40 | 40 | 40 | 0 | 40 | 0 |
| MAGE | 40 | 40 | 40 | 0 | 40 | 0 |
| ROGUE | 40 | 40 | 40 | 0 | 40 | 0 |
| HUNTRESS | 40 | 40 | 40 | 0 | 40 | 0 |
| DUELIST | 40 | 40 | 40 | 0 | 40 | 0 |
| CLERIC | 40 | 40 | 40 | 0 | 40 | 0 |
| all | 240 | 240 | 240 | 0 | 240 | 0 |

Before the story the same slice reached the seal in 0 of 240 Runs: 189 left the floor by its stairs and 51 starved on it.
Now every Run holds the key and seals the floor (median about 85 turns of play to the death, `turns` as the table
prints them). What is left is the fight: Tengu killed every hero (Tengu 224, Poison 15, a dart trap 1), and the fight
Policy is generic until stories 10.19 and 10.20. This page reports reach, not conversion; the kills column is not a
claim about the Brain's strength, since the generic fight Policy was never built for this boss.

What the table cannot show: practice heroes arrive at full health with a fixed kit (ADR-0024), so the rest before the
door was never needed here; `TenguEntryTest` covers it on a constructed floor. Real Runs reach depth 10 so rarely that
the entry is untested on them.

The first test of the story, `TenguEntryRunTest`, plays one practice Tengu Run per class and asserts the key, the seal
within 600 waits and no use of the stairs up. It fails on the practice branch before this change (see the pull request
for the run).

## Tengu practice after story 10.19 (phase one, the dart-trap memory)

Story 10.19 (#365). A `tengu` fight Policy now plays the first half of the fight: it strikes when beside Tengu, else
steps to the nearest cell beside him, and does not turn back ([Rule `tengu-fight`](../brain-rules.md#tengu-fight)).
The dart traps the screen draws as he lays a patch are walked round from a Memory that holds only drawn cells
([Rule in levels](../rules/levels.md)). Same slice as above: 40 Runs per class, `standard` set, salts 1000..1039, kit
`standard`, cap 5000 turns, `--jobs 12`. Three columns are new, read from the Run's debug record: Runs whose log shows
Tengu's yell at the start of the second half, patches of dart traps drawn, and the median health pips of Tengu (of 11) at
the last wait he was in view before the hero died.

Before (the Tengu policy switched off in a scratch build, everything else as in this story):

| class | Runs | Tengu killed | died | phase 2 reached | Tengu pips at death (median of 11) | median turns (deaths) | hero died to |
|---|---:|---:|---:|---:|---:|---:|---|
| WARRIOR | 40 | 0 | 40 | 0 | 11 | 88.0 | Tengu 40 |
| MAGE | 40 | 0 | 40 | 0 | 8 | 84.5 | Tengu 33, Poison 7 |
| ROGUE | 40 | 0 | 40 | 0 | 11 | 85.0 | Tengu 39, Poison 1 |
| HUNTRESS | 40 | 0 | 40 | 0 | 11 | 85.0 | Tengu 39, Poison 1 |
| DUELIST | 40 | 0 | 40 | 0 | 11 | 92.5 | Tengu 39, Poison 1 |
| CLERIC | 40 | 0 | 40 | 0 | 11 | 86.0 | Tengu 40 |
| all | 240 | 0 | 240 | 0 | | | |

After:

| class | Runs | Tengu killed | died | phase 2 reached | Tengu pips at death (median of 11) | median turns (deaths) | hero died to |
|---|---:|---:|---:|---:|---:|---:|---|
| WARRIOR | 40 | 0 | 40 | 40 | 4 | 117.5 | Tengu 31, Burning 9 |
| MAGE | 40 | 0 | 40 | 40 | 3 | 113.0 | Tengu 35, Burning 5 |
| ROGUE | 40 | 0 | 40 | 40 | 4 | 121.5 | Tengu 37, Burning 3 |
| HUNTRESS | 40 | 0 | 40 | 40 | 5 | 128.0 | Tengu 36, Burning 4 |
| DUELIST | 40 | 0 | 40 | 40 | 4 | 119.5 | Tengu 30, Burning 10 |
| CLERIC | 40 | 0 | 40 | 40 | 4 | 114.5 | Tengu 35, Burning 5 |
| all | 240 | 0 | 240 | 240 | | | |

Every hero now brings Tengu through the first half (0 of 240 before, 240 of 240 after) and dies in the second, with him
at about a third of his health. Four patches of traps are drawn in each Run, which is what the eight brackets of his
hit points give for the first half. The kills column does not move, because the second half (bombs, fire, the shocker)
is story 10.20's; the Burning deaths are the first sign of it.

What the table cannot show: it is one slice of one kit, and the pips are the bar's eleven steps, which cannot tell 101
hit points from 100, so the second half is detected by his yell and not by the bar. The first test of the story,
`TenguFightTest`, builds the arena and fails on the Brain without the new Policy (its reasons, `tengu: attack` and `tengu: approach`,
do not exist there); `TenguTrapMemoryLeakTest` holds the fairness claims on constructed screens and
`FadingTrapsTest` holds what the Observer shows of a hidden trap.

### Off the boss floor

`:rig:direction --set standard --class <C> --first 40 --salt-base 1000 --baseline 3f446413a` (the commit this story
branched from, so that only this story's change shows), one class at a time, six classes. Every column of every class
is identical to the baseline: reach depth 5 33, 18, 10, 21, 13, 7 of 40 (Warrior, Mage, Rogue, Huntress, Duelist,
Cleric), Goo kills 32, 6, 5, 16, 7, 6, medians and oscillation Steps equal. The Tengu Policy needs depth 10 and a sealed
floor, and the Observer change shows a hidden trap only under Tengu's own tilemap, so no Run in that slice can differ.
Against `origin/main` itself the columns do differ for Warrior, Mage and Duelist, because `main` has moved since this
branch was cut (the Goo work of #344 and others); that is not this story's change and the table above is the one that
isolates it.

## Tengu practice after story 10.20 (phase two, the threat map)

Story 10.20 (#366). The `tengu` fight Policy now keeps a threat map of the cells the screen draws as about to be struck
(a smoke bomb heap and its blast, fire blobs, the shocker heap and its blobs), priced in hit points by the Playbook
(`bomb_cost` 22, `fire_cost` 18, `shock_cost` 12, `adjacent_cost` 6), and keeps beside Tengu through it
([Rule `tengu-threat`](../brain-rules.md#tengu-threat)). Same slice as above: 40 Runs per class, `standard` set, salts
1000..1039, kit `standard`, cap 5000 turns, `--jobs 3`. The "before" columns are the "after" table of story 10.19
above (the same Runs on the merge base `a0ce37919`; a practice Run depends only on the Brain on the boss floor, and
10.19 is the last change to it).

| class | Tengu killed (before / after) | median turns of deaths (before / after) | boss pips at death (before / after) | hero died to, before | hero died to, after |
|---|---|---|---|---|---|
| WARRIOR | 0 / 0 | 117.5 / 136.0 | 4 / 4.0 | Tengu 31, Burning 9 | Tengu 40 |
| MAGE | 0 / 0 | 113.0 / 128.0 | 3 / 2.5 | Tengu 35, Burning 5 | Tengu 37, Burning 3 |
| ROGUE | 0 / 0 | 121.5 / 131.5 | 4 / 5.0 | Tengu 37, Burning 3 | Tengu 39, Burning 1 |
| HUNTRESS | 0 / 0 | 128.0 / 136.5 | 5 / 5.0 | Tengu 36, Burning 4 | Tengu 40 |
| DUELIST | 0 / 0 | 119.5 / 138.5 | 4 / 4.0 | Tengu 30, Burning 10 | Tengu 40 |
| CLERIC | 0 / 0 | 114.5 / 131.0 | 4 / 4.0 | Tengu 35, Burning 5 | Tengu 40 |
| all | 0 / 0 | | | Burning 36 of 240 | Burning 4 of 240 |

Phase 2 reached stays 240 of 240 and dart patches 160 per class. What moved: Burning deaths fall from 36 to 4 of 240,
and the hero lives 10 to 19 turns longer. What did not move: no Tengu kill, and the pips at death are within half a step
of before (the bar's eleven steps cannot tell a few hit points apart). The fire and the bomb are now left, and the
killer is Tengu's own hand (the column names the killer, and the bomb and the shocker are not separate killers in the
debug record, so their damage is not split out here; that needs a damage-by-source column in the Run log, a follow-up).

The Mage and Huntress rows read apart, as the issue asked. The Huntress (spirit bow) died to Burning 4 times before and
0 after, with pips at death 5 and 5: she keeps beside Tengu like the melee classes, because the ranged option has no
branch in this Policy, and the map does not trade her range against adjacency. The Mage (staff) is the one class that
still dies to Burning (5 to 3) and the only one whose pips at death fall (3 to 2.5); her staff attack from range is
the one place the adjacency preference costs the most, and no number in this story tests that trade. It is left open.

What the table cannot show: one slice of one kit; the bomb's countdown is not on the screen, so the whole life of a drawn
bomb is priced as landing now and the hero stands clear for turns it need not; the shocker is priced for its whole
field so the hero accepts the least bad cell beside him. A kill needs more than leaving the damage cells, and the
next step is measured damage per source before another number is tuned.

### Off the boss floor

`:rig:direction --set standard --class <C> --first 10 --salt-base 1000 --baseline a0ce37919 --jobs 3`, one class at a
time. Every column of every class is identical to the baseline: reach depth 5 9, 7, 2, 7, 5, 4 of 10 (Warrior, Mage,
Rogue, Huntress, Duelist, Cleric), Goo kills 8, 2, 2, 5, 4, 4, reach depth 10 and 15 zero. The map needs depth 10 and
a sealed floor, so no Run in that slice can differ.
