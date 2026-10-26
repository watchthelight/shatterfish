---
id: city-1
topic: bestiary
group: city
subject: "Fire elemental"
subject_class: actors.mobs.Elemental.FireElemental
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Fire Elementals burn the Hero out of water, so standing in water snuffs the fire immediately (though exposed scrolls in the backpack may still burn on the first hit); fighting a Fire Elemental while on water tiles is the recommended defense.”"
cites:
  - core/…/mobs/Elemental.java:243
  - core/…/mobs/Elemental.java:251
  - core/…/buffs/Burning.java:99
  - core/…/buffs/Burning.java:122
  - core/…/buffs/Burning.java:179
implemented_in: []
card: docs/bestiary/city.md#elemental-fireelemental
---

## Verdict against `v4.0.0`

Confirmed that a target in water is never ignited by either its melee or bolt, and existing Burning ends in water. The caveat about scrolls burning on the first hit is wrong: in water no Burning is applied at all, and item burning needs 4+ burn ticks.
