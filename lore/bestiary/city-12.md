---
id: city-12
topic: bestiary
group: city
subject: "Dwarf warlock"
subject_class: actors.mobs.Warlock
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
  - core/…/mobs/Warlock.java:80
  - core/…/mobs/Warlock.java:86
  - core/…/mechanics/Ballistica.java:49
  - core/…/mechanics/Ballistica.java:130
  - core/…/levels/Terrain.java:90
  - core/…/mobs/Mob.java:1342
  - core/…/mobs/Mob.java:1401
  - core/…/mobs/Mob.java:1403
implemented_in: []
card: docs/bestiary/city.md#warlock
---

## Verdict against `v4.0.0`

For Warlocks: the bolt needs a MAGIC_BOLT line ending on your cell; closed doors are solid and LOS-blocking, and a hunter that loses sight walks to your last known cell. It will still walk after you.
