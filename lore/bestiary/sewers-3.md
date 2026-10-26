---
id: sewers-3
topic: bestiary
group: sewers
subject: "Gnoll scout"
subject_class: actors.mobs.Gnoll
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Gnoll_Scout
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "unspecified (SPD wiki, no version stated)"
tier: 1
graded_against: v4.0.0
status: needs-review
claim: "“Whittle Gnoll Scouts down with darts or wand charges as they approach, before they reach melee range, and avoid fighting two or more at once — even killing them one by one you won't out-heal the combined damage and can die.”"
cites:
  - core/…/mobs/Gnoll.java:29-58
  - core/…/mobs/Mob.java:558-568
  - core/…/missiles/MissileWeapon.java:223-233
implemented_in: []
card: docs/bestiary/sewers.md#gnoll
---

## Verdict against `v4.0.0`

Confirmed: scouts are melee-only (no canAttack override), so every tile they walk toward you is a free ranged shot, and each extra adjacent gnoll adds its own 1-6 attack. 'You won't out-heal two' is a judgment, not a code fact.
