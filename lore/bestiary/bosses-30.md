---
id: bosses-30
topic: bestiary
group: bosses
subject: "Rotting fist"
subject_class: actors.mobs.YogFist.RottingFist
source_url: https://pixeldungeon.fandom.com/wiki/Rotting_fist
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "not stated"
tier: F
graded_against: v4.0.0
status: current
claim: "“Isolate and fight the two fists of Yog-Dzewa separately rather than together. Fight the Rotting Fist while standing on water: it inflicts Caustic Ooze, and fighting on water lets tools like Seed of Earthroot / Greaves of Nature tank the damage more easily. Note the Rotting Fist itself also heals 4 HP per turn while it is on/near water, so this is a damage-mitigation choice for the Hero, not a way to deny the fist's own healing.”"
cites:
  - core/…/mobs/YogFist.java:412
  - core/…/mobs/YogFist.java:413
  - core/…/mobs/YogFist.java:414
  - core/…/mobs/YogFist.java:452
  - core/…/mobs/YogFist.java:453
  - core/…/buffs/Ooze.java:93
  - core/…/mobs/YogDzewa.java:388
  - core/…/mobs/YogDzewa.java:411
  - core/…/mobs/YogDzewa.java:419
  - core/…/mobs/YogDzewa.java:421
  - core/…/mobs/YogDzewa.java:422
implemented_in: []
card: docs/bestiary/bosses.md#yogfist-rottingfist
---

## Verdict against `v4.0.0`

Partly right, contradicted on the numbers and setup. Right: its melee oozes (50%) and water washes Ooze off. Wrong: it heals HT/50 = 6 HP per act, not 4, and only while standing ON water, not near it. And normally only one fist exists at a time: a fist spawns at each 300-HP phase and Yog is invulnerable while any fist lives, so there is no pair to separate. Only Stronger Bosses spawns two at once, and then the Rotting fist's partner is the Rusted fist, not the Burning fist. 'Greaves of Nature' is not an item in this codebase.
