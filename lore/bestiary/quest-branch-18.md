---
id: quest-branch-18
topic: bestiary
group: quest-branch
subject: "Fire elemental (vault)"
subject_class: actors.mobs.quest.vault.VaultElemental.Fire
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
card: docs/bestiary/quest-branch.md#vaultelemental-fire
---

## Verdict against `v4.0.0`

FIERY: immune to Burning/Blazing, only resists (halves) Wand of Fireblast; no healing from fire in code. "Firebolt" is the old wand name.
