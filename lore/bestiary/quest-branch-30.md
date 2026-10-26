---
id: quest-branch-30
topic: bestiary
group: quest-branch
subject: "Golem (vault)"
subject_class: actors.mobs.quest.vault.VaultGolem
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: F
graded_against: v4.0.0
status: needs-review
claim: "“but may teleport to another room while searching for the Hero”"
cites:
  - core/…/vault/VaultGolem.java:28
  - core/…/vault/VaultGolem.java:32
  - core/…/mobs/Mob.java:1516
  - core/…/mobs/Mob.java:1517
implemented_in: []
card: docs/bestiary/quest-branch.md#vaultgolem
---

## Verdict against `v4.0.0`

True for the City golem (Golem.java:195-198), but the vault golem swaps its teleporting Wandering state for the stealth patrol state, so it never self-teleports.
