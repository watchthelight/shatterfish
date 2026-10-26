---
id: halls-8
topic: bestiary
group: halls
subject: "Acidic scorpio"
subject_class: actors.mobs.Acidic
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: needs-review
claim: "“General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”"
cites:
  - core/…/mobs/Mob.java:1328
  - core/…/mobs/Mob.java:1360
  - core/…/mobs/Mob.java:1401
  - core/…/mobs/Mob.java:1403
  - core/…/mobs/Scorpio.java:75
  - core/…/mobs/Scorpio.java:91
implemented_in: []
card: docs/bestiary/halls.md#acidic
---

## Verdict against `v4.0.0`

Confirmed and stronger than stated for scorpios: attacking needs the target in FOV plus a clear PROJECTILE line, and once you leave its FOV a hunting scorpio gives up immediately (WANDERING) instead of chasing.
