---
id: quest-branch-5
topic: bestiary
group: quest-branch
subject: "Newborn fire elemental"
subject_class: actors.mobs.Elemental.NewbornFireElemental
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: F
graded_against: v4.0.0
status: current
claim: "“(though exposed scrolls in the backpack may still burn on the first hit)”"
cites:
  - core/…/buffs/Burning.java:120
  - core/…/buffs/Burning.java:121
  - core/…/buffs/Burning.java:122
implemented_in: []
card: docs/bestiary/quest-branch.md#elemental-newbornfireelemental
---

## Verdict against `v4.0.0`

Item burning needs 4+ consecutive burning turns ((turns-3)/3 chance), so the first hit cannot burn scrolls; water ends Burning after at most one tick.
