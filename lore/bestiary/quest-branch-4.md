---
id: quest-branch-4
topic: bestiary
group: quest-branch
subject: "Newborn fire elemental"
subject_class: actors.mobs.Elemental.NewbornFireElemental
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Fire Elementals burn the Hero out of water, so standing in water snuffs the fire immediately; fighting a Fire Elemental while on water tiles is the recommended defense.”"
cites:
  - core/…/mobs/Elemental.java:365
  - core/…/mobs/Elemental.java:366
  - core/…/mobs/Elemental.java:368
  - core/…/mobs/Elemental.java:373
  - core/…/buffs/Burning.java:99
  - core/…/buffs/Burning.java:100
  - core/…/buffs/Burning.java:178
  - core/…/buffs/Burning.java:179
implemented_in: []
card: docs/bestiary/quest-branch.md#elemental-newbornfireelemental
---

## Verdict against `v4.0.0`

Water part confirmed for the newborn: its fireball seeds only 2 fire on water cells (8 elsewhere) and Burning detaches from a non-flying target in water. Note the newborn's melee never ignites (Elemental.java:406-410); only the fireball burns.
