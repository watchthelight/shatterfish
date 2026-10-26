---
id: bosses-31
topic: bestiary
group: bosses
subject: "Rotting fist"
subject_class: actors.mobs.YogFist.RottingFist
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: F
graded_against: v4.0.0
status: needs-review
claim: "“A Potion of Paralytic Gas only affects the Burning Fist in this encounter; the Rotting Fist is immune to paralysis, so don't waste a paralytic gas potion trying to lock it down.”"
cites:
  - core/…/mobs/YogFist.java:194
  - core/…/mobs/YogFist.java:404
  - core/…/mobs/YogFist.java:461
  - core/…/actors/Char.java:1415
  - core/…/actors/Char.java:1428
implemented_in: []
card: docs/bestiary/bosses.md#yogfist-rottingfist
---

## Verdict against `v4.0.0`

No Paralysis immunity. Its immunities are Sleep (all fists), ToxicGas (its own), Ooze (ACIDIC) and AllyBuff/Dread (BOSS). Paralytic gas works on it. This may be confused with its immunity to Toxic Gas.
