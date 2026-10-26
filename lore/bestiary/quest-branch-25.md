---
id: quest-branch-25
topic: bestiary
group: quest-branch
subject: "Shock elemental (vault)"
subject_class: actors.mobs.quest.vault.VaultElemental.Shock
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Shock Elementals are an uncommon elemental variant whose ranged attack blinds the target for 5 turns, and whose melee attack also damages nearby creatures for 40% of the hit and deals 40% extra damage if the target being hit is standing in water; avoid fighting them while standing in water for the same reason as Frost Elementals.”"
cites:
  - core/…/mobs/Elemental.java:523
  - core/…/mobs/Elemental.java:525
  - core/…/mobs/Elemental.java:526
  - core/…/mobs/Elemental.java:530
  - core/…/mobs/Elemental.java:551
  - core/…/buffs/Blindness.java:29
implemented_in: []
card: docs/bestiary/quest-branch.md#vaultelemental-shock
---

## Verdict against `v4.0.0`

Zap blinds for DURATION/2 = 5 turns; melee arcs 40% to nearby characters, and to the target itself only when it stands in water (the "40% extra"). Rarity (20%) matches Elemental.random / VaultElemental.random (VaultElemental.java:31-37).
