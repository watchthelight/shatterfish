---
id: city-13
topic: bestiary
group: city
subject: "Senior monk"
subject_class: actors.mobs.Senior
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“The Golden Monk is a rare Dwarf Monk variant that hits slightly harder and builds its Focus (special-attack charge) twice as fast as a normal Monk, and always drops a pasty (food item) on death; treat it as a higher-priority, faster-charging version of the regular Monk fight.”"
cites:
  - core/…/mobs/Senior.java:33
  - core/…/mobs/Senior.java:34
  - core/…/mobs/Senior.java:41
  - core/…/mobs/Senior.java:47
  - core/…/mobs/Monk.java:56
  - core/…/mobs/Monk.java:94
  - core/…/mobs/Monk.java:102
  - core/…/mobs/Monk.java:124
implemented_in: []
card: docs/bestiary/city.md#senior
---

## Verdict against `v4.0.0`

Damage 16-25 vs the Monk's 12-25; loot = Pasty with lootChance 1. "Focus twice as fast" is true only for movement: each step cuts the cooldown by 3.33 instead of 1.67; the time-based part and the 6-7 turn reset are the same as a Monk's.
