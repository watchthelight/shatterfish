---
id: bosses-27
topic: bestiary
group: bosses
subject: "Burning fist"
subject_class: actors.mobs.YogFist.BurningFist
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
  - core/…/mobs/YogFist.java:281
  - core/…/actors/Char.java:1415
  - core/…/actors/Char.java:1424
implemented_in: []
card: docs/bestiary/bosses.md#yogfist-burningfist
---

## Verdict against `v4.0.0`

Half right for this fist: Paralysis does affect the Burning fist (its immunities are Sleep, Frost, Burning/Blazing, AllyBuff, Dread). But the claim that it is the ONLY fist affected is wrong: no fist is paralysis-immune.
