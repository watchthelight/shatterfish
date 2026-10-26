---
id: bosses-20
topic: bestiary
group: bosses
subject: "King of Dwarves"
subject_class: actors.mobs.DwarfKing
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: F
graded_against: v4.0.0
status: needs-review
claim: "“Throw a Potion of Toxic Gas into the middle of the throne room at the start of the fight, because the King consistently moves to that central spot, and the gas deals roughly 10 HP per turn to him. Do NOT use a Potion of Paralytic Gas on him — he is immune to many potions and you risk paralyzing yourself instead.”"
cites:
  - core/…/blobs/ToxicGas.java:40
  - core/…/mobs/DwarfKing.java:451
  - core/…/mobs/DwarfKing.java:473
  - core/…/mobs/DwarfKing.java:498
  - core/…/actors/Char.java:1414
  - core/…/actors/Char.java:1415
  - core/…/blobs/ParalyticGas.java:51
  - core/…/blobs/ParalyticGas.java:52
implemented_in: []
card: docs/bestiary/bosses.md#dwarfking
---

## Verdict against `v4.0.0`

Toxic gas deals 1 + depth/5 = 5 per turn on depth 20, not about 10; the King is invulnerable to it in phase 2 (when he sits on the central throne), and in phase 3 the damage is deferred. He is not immune to Paralytic Gas: his BOSS immunities are only AllyBuff and Dread. The warning about gassing yourself is fair.
