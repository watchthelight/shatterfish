---
id: quest-branch-1
topic: bestiary
group: quest-branch
subject: "Crystal guardian"
subject_class: actors.mobs.CrystalGuardian
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Old_Mine
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Guardians in the Crystal Caves spawn only at floor generation (one per large room, up to 3 per floor), do not respawn, and start asleep surrounded by crystals — they won't notice the Hero unless there's a clear unobstructed path of sight. If they do notice and pursue, they can break crystals blocking their path at the cost of an extra turn. Community note: Guardians reportedly cannot be killed by any means, so the practical approach is avoidance (don't open a sightline to a sleeping Guardian) rather than combat.”"
cites:
  - core/…/levels/MiningLevel.java:95
  - core/…/levels/MiningLevel.java:101
  - core/…/levels/MiningLevel.java:103
  - core/…/quest/MineLargeRoom.java:77
  - core/…/quest/MineLargeRoom.java:84
  - core/…/quest/MineLargeRoom.java:88
  - core/…/quest/MineLargeRoom.java:101
  - core/…/mobs/CrystalGuardian.java:57
  - core/…/mobs/CrystalGuardian.java:58
  - core/…/mobs/CrystalGuardian.java:155
  - core/…/mobs/CrystalGuardian.java:156
  - core/…/mobs/CrystalGuardian.java:201
  - core/…/mobs/CrystalGuardian.java:216
  - core/…/mobs/CrystalGuardian.java:224
  - core/…/mobs/CrystalGuardian.java:231
  - core/…/mobs/CrystalGuardian.java:234
  - core/…/mobs/CrystalGuardian.java:236
  - core/…/mobs/CrystalGuardian.java:255
  - core/…/mobs/CrystalGuardian.java:258
  - core/…/mobs/CrystalGuardian.java:259
  - core/…/mobs/CrystalGuardian.java:260
  - core/…/levels/MiningLevel.java:163
  - core/…/levels/MiningLevel.java:164
implemented_in: []
card: docs/bestiary/quest-branch.md#crystalguardian
---

## Verdict against `v4.0.0`

Confirmed: 3 large rooms per mine, one guardian each, spawningWeight 0 and the mine respawner only makes wisps, so none respawn; it starts SLEEPING sealed in crystal; it stomps crystals at +1 move while hunting; HP is reset to 1 instead of dying. Nuance: the wake check is a walkable-path check (PathFinder over passable cells), not a line-of-sight check, and damage or the spire's 3rd-hit alarm (CrystalSpire.java:423-460) wakes/aggroes it regardless.
