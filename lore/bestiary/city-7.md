---
id: city-7
topic: bestiary
group: city
subject: "Shock elemental"
subject_class: actors.mobs.Elemental.ShockElemental
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Shock Elementals are an uncommon elemental variant whose ranged attack blinds the target for 5 turns, and whose melee attack also damages nearby creatures for 40% of the hit and deals 40% extra damage if the target being hit is standing in water; avoid fighting them while standing in water for the same reason as Frost Elementals.”"
cites:
  - core/…/mobs/Elemental.java:523
  - core/…/mobs/Elemental.java:525
  - core/…/mobs/Elemental.java:526
  - core/…/mobs/Elemental.java:530
  - core/…/mobs/Elemental.java:551
  - core/…/buffs/Blindness.java:29
implemented_in: []
card: docs/bestiary/city.md#elemental-shockelemental
---

## Verdict against `v4.0.0`

Bolt: Blindness for DURATION/2 = 5 turns. Melee: arcs deal 40% of the hit to other characters within 2 tiles of the victim, and the victim is also hit only when standing in water (an extra 40%).
