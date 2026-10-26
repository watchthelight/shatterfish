---
id: city-11
topic: bestiary
group: city
subject: "Dwarf warlock"
subject_class: actors.mobs.Warlock
source_url: https://pixeldungeon.fandom.com/wiki/Game_mechanics/Attacking
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: needs-review
claim: "“Physical attacks that would normally require an accuracy check auto-hit as a surprise attack when the target is unaware (e.g. it just walked into view through a door); using a door-luring loop (stand off the door, let the door close, let the enemy open it and thereby come into your view for the first time) lets you land a guaranteed surprise attack on a Warlock or similar enemy before it can use its ranged spell.”"
cites:
  - core/…/actors/Char.java:202
  - core/…/mobs/Mob.java:290
  - core/…/mobs/Mob.java:1327
  - core/…/mobs/Mob.java:875
  - core/…/mobs/Mob.java:876
  - core/…/mobs/Mob.java:796
  - core/…/mobs/Mob.java:801
implemented_in: []
card: docs/bestiary/city.md#warlock
---

## Verdict against `v4.0.0`

Mob FOV and enemySeen are computed at the start of the mob's own act, so right after it steps through a door it has not yet seen you, and the hero's next attack is a surprise (defenseSkill 0). Requires a weapon that allows surprise attacks.
