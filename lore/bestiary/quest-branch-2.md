---
id: quest-branch-2
topic: bestiary
group: quest-branch
subject: "Crystal wisp"
subject_class: actors.mobs.CrystalWisp
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Old_Mine
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Wisps are the common respawning enemy of the Crystal Caves branch; they function like other spellcasters (shamans, warlocks), can move directly through the crystal terrain while chasing the Hero, but cannot shoot their ranged attack through crystals — making the crystals themselves useful cover to break their line of sight even though they can walk around/through them to reach you eventually.”"
cites:
  - core/…/levels/MiningLevel.java:159
  - core/…/levels/MiningLevel.java:163
  - core/…/levels/MiningLevel.java:164
  - core/…/levels/MiningLevel.java:173
  - core/…/levels/MiningLevel.java:175
  - core/…/mobs/CrystalWisp.java:48
  - core/…/mobs/CrystalWisp.java:69
  - core/…/mobs/CrystalWisp.java:70
  - core/…/mobs/CrystalWisp.java:71
  - core/…/mobs/CrystalWisp.java:92
  - core/…/mobs/CrystalWisp.java:93
  - core/…/mobs/CrystalWisp.java:94
  - core/…/mechanics/Ballistica.java:49
  - core/…/levels/Terrain.java:126
implemented_in: []
card: docs/bestiary/quest-branch.md#crystalwisp
---

## Verdict against `v4.0.0`

Confirmed: the crystal mine's createMob returns CrystalWisp (3x respawn cooldown); modifyPassable lets it fly through MINE_CRYSTAL; the beam is a MAGIC_BOLT (STOP_SOLID) and crystal is SOLID, so crystal blocks the beam. Crystal is not LOS_BLOCKING, so it still sees you through crystal and will drift through to melee.
