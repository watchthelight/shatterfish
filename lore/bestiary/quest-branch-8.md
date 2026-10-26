---
id: quest-branch-8
topic: bestiary
group: quest-branch
subject: "Newborn fire elemental"
subject_class: actors.mobs.Elemental.NewbornFireElemental
source_url: https://pixeldungeon.fandom.com/wiki/Fire_elemental
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "not stated"
tier: F
graded_against: v4.0.0
status: needs-review
claim: "“do not use a Wand of Firebolt or a Potion of Liquid Flame on a Fire Elemental, as fire damage does nothing to it and may slightly heal it instead”"
cites:
  - core/…/actors/Char.java:1423
  - core/…/actors/Char.java:1424
  - core/…/mobs/Elemental.java:235
implemented_in: []
card: docs/bestiary/quest-branch.md#elemental-newbornfireelemental
---

## Verdict against `v4.0.0`

FIERY makes it immune to Burning/Blazing (so Liquid Flame fire does nothing) but only resists Wand of Fireblast (half damage, not zero); no code heals it from fire. "Wand of Firebolt" is the pre-rework name. The advice not to use fire is still sound.
