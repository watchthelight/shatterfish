---
id: halls-1
topic: bestiary
group: halls
subject: "Succubus"
subject_class: actors.mobs.Succubus
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Succubi will teleport directly to the Hero the moment they notice them if they are at least 3 tiles away, then fight in melee and attempt to Charm the Hero; because they close distance instantly, you cannot simply keep range against them the way you can against other City ranged casters.”"
cites:
  - core/…/mobs/Succubus.java:112
  - core/…/mobs/Succubus.java:114
  - core/…/mobs/Succubus.java:115
  - core/…/mobs/Succubus.java:158
  - core/…/mobs/Succubus.java:97
  - core/…/mobs/Succubus.java:98
implemented_in: []
card: docs/bestiary/halls.md#succubus
---

## Verdict against `v4.0.0`

Code confirms: it blinks when its target cell is visible and more than 2 tiles away (i.e. 3+), then melees and charms on 1/3 of hits. Caveats: it is not literally 'the moment they notice'. The blink happens on its hunting move, has a 4-6 walking-step cooldown, is blocked by roots, and lands beside you along a projectile line (cut short by walls or bodies).
