---
id: quest-branch-21
topic: bestiary
group: quest-branch
subject: "Frost elemental (vault)"
subject_class: actors.mobs.quest.vault.VaultElemental.Frost
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Frost Elementals chill the target for 3 turns on a ranged hit (5 turns if the target is standing in water) and have a 33% chance to chill on their melee hit as well; because water extends the chill duration, the community strategy is the opposite of the Fire Elemental fight — avoid fighting a Frost Elemental while standing in water.”"
cites:
  - core/…/mobs/Elemental.java:494
  - core/…/mobs/Elemental.java:495
  - core/…/mobs/Elemental.java:496
  - core/…/mobs/Elemental.java:502
  - core/…/mobs/Elemental.java:503
  - core/…/blobs/Freezing.java:75
implemented_in: []
card: docs/bestiary/quest-branch.md#vaultelemental-frost
---

## Verdict against `v4.0.0`

Ranged proc always freezes; melee proc on 1/3 of hits, or every hit if you stand in water; chill adds 3 turns, 5 on water. Chill at its cap becomes Frost.
