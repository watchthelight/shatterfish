---
id: city-4
topic: bestiary
group: city
subject: "Frost elemental"
subject_class: actors.mobs.Elemental.FrostElemental
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Frost Elementals chill the target for 3 turns on a ranged hit (5 turns if the target is standing in water) and have a 33% chance to chill on their melee hit as well; because water extends the chill duration, the community strategy is the opposite of the Fire Elemental fight — avoid fighting a Frost Elemental while standing in water.”"
cites:
  - core/…/mobs/Elemental.java:495
  - core/…/mobs/Elemental.java:503
  - core/…/blobs/Freezing.java:75
implemented_in: []
card: docs/bestiary/city.md#elemental-frostelemental
---

## Verdict against `v4.0.0`

Bolt: +3 turns of Chill, +5 in water. Melee: 1/3 chance, and always when the target stands in water, which the claim omits. Avoiding water follows.
