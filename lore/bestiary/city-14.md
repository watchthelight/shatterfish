---
id: city-14
topic: bestiary
group: city
subject: "Golem"
subject_class: actors.mobs.Golem
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: needs-review
claim: "“Golems have the highest attack, HP and armor of the normal Dwarven City enemies; they can't walk through corridors but may teleport to another room while searching for the Hero, and can also teleport the Hero to a nearby tile if the Hero hides in a doorway, so doorway-camping does not fully neutralize them.”"
cites:
  - core/…/mobs/Golem.java:45
  - core/…/mobs/Golem.java:63
  - core/…/mobs/Golem.java:68
  - core/…/mobs/Golem.java:73
  - core/…/mobs/Golem.java:55
  - core/…/mobs/Golem.java:195
  - core/…/mobs/Golem.java:197
  - core/…/mobs/Golem.java:239
  - core/…/mobs/Golem.java:164
  - core/…/mobs/Mob.java:580
implemented_in: []
card: docs/bestiary/city.md#golem
---

## Verdict against `v4.0.0`

Confirmed: HP 120, DR 0-12 and damage 25-30 are the highest of the regular city mobs (Monk accuracy 30 beats its 28). LARGE mobs cannot enter non-openSpace cells; wandering golems self-teleport when they cannot reach their target; hunting golems pull the hero to a cell next to themselves when they see but cannot reach them.
