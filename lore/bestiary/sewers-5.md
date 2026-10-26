---
id: sewers-5
topic: bestiary
group: sewers
subject: "Sewer crab"
subject_class: actors.mobs.Crab
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Sewer_Crab
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "unspecified (SPD wiki, no version stated)"
tier: F
graded_against: v4.0.0
status: needs-review
claim: "“Use surprise attacks from around corners/doors (guaranteed hits) instead.”"
cites:
  - core/…/mobs/Crab.java:36
  - core/…/mobs/Mob.java:1327
  - core/…/mobs/Mob.java:1355
  - core/…/actors/Actor.java:50-52
implemented_in: []
card: docs/bestiary/sewers.md#crab
---

## Verdict against `v4.0.0`

A surprise hit does always land, but the door ambush against a crab is not reliable: at speed 2 it acts again 0.5 turns after stepping into the door, sees you, and is no longer surprised, unless that step fell on a half-turn. Snipe it asleep or before it notices you instead. (Part of a compound claim.)
