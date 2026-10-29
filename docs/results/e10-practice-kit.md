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
