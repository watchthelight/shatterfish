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

40 Runs per class, `standard` set, salts 1000..1039, cap 5000 turns, Brain at main `0d087c17e`.

| class | `standard` kit killed | `arrived` kit killed | killers of the dead (`standard`) |
|---|---|---|---|
| WARRIOR | 0/40 | 0/40 | DM100 20, Hunger 8, NecroSkeleton 8, Guard 3, Piranha 1 |
| MAGE | 0/40 | 0/40 | NecroSkeleton 19, DM100 9, Guard 7, Hunger 3, Bleeding 2 |
| ROGUE | 0/40 | 0/40 | Hunger 40 |
| HUNTRESS | 0/40 | 0/40 | NecroSkeleton 24, DM100 6, Hunger 4, Guard 3, Skeleton 2, Bat 1 |
| DUELIST | 0/40 | 0/40 | DM100 14, NecroSkeleton 14, Hunger 5, Guard 4, Piranha 1, Thief 1, Wraith 1 |
| CLERIC | 0/40 | 0/40 | NecroSkeleton 17, DM100 15, Guard 5, Hunger 2, ToxicGas 1 |
| all | 0/240 (0-2%) | 0/240 (0-2%) | |

Zero kills. Every Run sampled ends on depth 10, and the killers named by the debug record (DM100, Guard, Necromancer
skeleton, Hunger) are not Tengu: the Rogue starves in all 40 Runs. Whether these are the prison floor's own wandering mobs, the
Brain failing to find the key and the door at the end of the hallway, or a wrong killer attribution on this floor is not
established here; that is the first thing W3 reads in the `.debug.ndjson` records beside each log.
