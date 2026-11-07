# Loose wands are never zapped: 67 of 240 Runs held one

Seen in the 10.40 death reading (`docs/results/2026-10-31-10-40-sewers-killers.md`), slice 1 (standard, six classes, salts
1000 to 1039, `--debug-record`). The only Policy that zaps a wand is `Zap`, and it zaps the Mage's staff of magic missile
only (`Zap.staff`, rule [`staff-zap`](../brain-rules.md#staff-zap)). A wand found on the floor is picked up and carried.

## Counts

- 67 of 240 Runs held a wand at some point (not the staff). 65 of them still held it on the last Decision.
- By kind, Runs that ever held one: disintegration 9, prismatic light 8, corruption 8, warding 8, frost 7, living earth 6,
  magic missile 5, fireblast 3, lightning 3, regrowth 3, blast wave 3, transfusion 2, corrosion 2.
- 13 of the 76 Crab, Gnoll and Swarm deaths held one.

## What the pinned code says (upstream-reader, `v4.0.2`)

- A wand starts full: 2 charges, magic missile 3, plus one per level it was generated with (a level of +1 about a quarter
  of the time, +2 about a fifteenth) (`items/wands/Wand.java:82-83, 433-435, 549-559`).
- `status()` is null until the wand's level is known, so a fresh wand shows no charge count (`items/wands/Wand.java:338-344`);
  the Brain cannot read the charges. Level becomes known by use (10 uses; `Wand.java:458-466`).
- A zap with no charge fizzles and costs no turn (`items/wands/Wand.java:158-163, 718-720`); a recharge takes about 45
  turns for the first missing charge while the wand is carried (`Wand.java:803-809, 846-848`).
- The kind is visible; whether it is cursed is not, and 30% are (`Wand.java:561-564`). A cursed zap runs a random cursed
  effect instead (`Wand.java:753-757`, `items/wands/CursedWand.java`): teleport, gas, an explosion, a summon, an alarm.
- At level 0, magic missile, disintegration and frost deal 2 to 8, lightning 5 to 10 (half of that to a hero beside the
  target), blast wave 1 to 3; frost also chills. Fireblast burns grass at the hero's feet. None kills a 15 HP crab in one
  zap.

## Why it was not built in 10.40

A bolt wand adds about five damage a charge, two charges, against 30% cursed and a hidden charge count. The Mage's staff
zap is worth it because its charges show and it recharges as the hero plays. For a loose wand the rule would need a new
field in `Memory` (zaps since pickup per wand), a class-independent reach in the Playbook, and a `SafeTest` price on the
cursed case, for a gain of one to three Runs in 240, which no 320-Run measure separates from noise.

## If it is built

A paired measure is cleaner than usual: a Run that never picks up a wand plays the same on both sides, so only the 67 differ.
Start with magic missile, disintegration and frost (known kind, plain bolt), zap only while an awake enemy approaches, stop
after the count of charges the level-0 table gives, and treat a no-turn zap as a fizzle that ends the volley.
