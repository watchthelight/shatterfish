---
id: quest-branch-3
topic: bestiary
group: quest-branch
subject: "Crystal wisp"
subject_class: actors.mobs.CrystalWisp
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
  - core/…/mobs/CrystalWisp.java:92
  - core/…/mobs/CrystalWisp.java:93
  - core/…/mobs/CrystalWisp.java:94
  - core/…/mechanics/Ballistica.java:49
  - core/…/levels/Terrain.java:90
implemented_in: []
card: docs/bestiary/quest-branch.md#crystalwisp
---

## Verdict against `v4.0.0`

Applies to crystal wisps: the beam needs a MAGIC_BOLT line, which doors (SOLID+LOS_BLOCKING), walls, crystal and bodies block. Caveat for wisps: they fly through crystal, so crystal cover only denies the beam, not the approach.
