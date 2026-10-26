---
id: bosses-11
topic: bestiary
group: bosses
subject: "DM-300"
subject_class: actors.mobs.DM300
source_url: https://pixeldungeon.fandom.com/wiki/DM-300
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "unspecified"
tier: F
graded_against: v4.0.0
status: current
claim: "“DM-300 has no ranged attack, so pure kiting works: it releases toxic gas around itself and, every time it moves, drops an avalanche on a random adjacent tile that can damage and stun you. Since it matches your movement speed, step one tile away whenever gas reaches you — it will close back to melee range without getting a bonus attack out of you moving.”"
cites:
  - core/…/mobs/DM300.java:373-402
  - core/…/mobs/DM300.java:238-283
  - core/…/mobs/DM300.java:409-471
  - core/…/mobs/DM300.java:348-351
  - core/…/mobs/DM300.java:256-259
implemented_in: []
card: docs/bestiary/bosses.md#dm300
---

## Verdict against `v4.0.0`

Outdated (original Pixel Dungeon behaviour). In v4.0.0 the gas vent is a ranged zap along a path to your cell (100 gas on you), rockfalls are a cooldown ability centred on you (not a rock on every move), abilities used at range cost it no turn, and it moves at double speed while supercharged. Kiting at range invites free abilities.
