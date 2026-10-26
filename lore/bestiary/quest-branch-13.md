---
id: quest-branch-13
topic: bestiary
group: quest-branch
subject: "Modified DM-100"
subject_class: actors.mobs.quest.vault.VaultDM100
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”"
cites:
  - core/…/mobs/DM100.java:76
  - core/…/mobs/DM100.java:77
  - core/…/mobs/DM100.java:78
  - core/…/mobs/DM100.java:87
  - core/…/mobs/DM100.java:88
  - core/…/mechanics/Ballistica.java:49
  - core/…/levels/Terrain.java:90
implemented_in: []
card: docs/bestiary/quest-branch.md#vaultdm100
---

## Verdict against `v4.0.0`

The DM-100 bolt needs a clear MAGIC_BOLT line; a door or corner forces it to walk. In the vault, losing sight sends it to INVESTIGATING your last cell, not HUNTING.
