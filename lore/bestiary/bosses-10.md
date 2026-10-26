---
id: bosses-10
topic: bestiary
group: bosses
subject: "Tengu"
subject_class: actors.mobs.Tengu
source_url: https://pixeldungeon.fandom.com/wiki/Tengu
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "not stated"
tier: F
graded_against: v4.0.0
status: needs-review
claim: "“Paralysis-inflicting tools (Stunning weapon enchant, Wand of Avalanche, Curare-tipped darts, Potion of Paralytic Gas, bombs) are the best counters to Tengu; Potion of Invisibility lets you close distance in the maze without eating shuriken hits.”"
cites:
  - core/…/mobs/Tengu.java:349-354
  - core/…/mobs/Mob.java:290
  - core/…/mobs/Tengu.java:407-428
  - core/…/mobs/Tengu.java:508
implemented_in: []
card: docs/bestiary/bosses.md#tengu
---

## Verdict against `v4.0.0`

Stunning enchant, Wand of Avalanche and curare darts do not exist in v4.0.0 (paralytic darts and Potion of Paralytic Gas do). Tengu is not immune to paralysis, and invisibility does stop his shurikens (enemyInFOV needs a visible hero), but his abilities still target the hero. There is no maze.
