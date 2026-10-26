---
id: bosses-28
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
tier: 1
graded_against: v4.0.0
status: needs-review
claim: "“After the Rotting Fist is dead, deal with the Burning Fist by hiding behind a wall or otherwise out of its spell range to force it to close distance before you engage it in melee.”"
cites:
  - core/…/mobs/YogFist.java:105
  - core/…/mobs/YogFist.java:106
  - core/…/mobs/YogFist.java:131
  - core/…/mobs/Mob.java:1328
  - core/…/mobs/Mob.java:1353
implemented_in: []
card: docs/bestiary/bosses.md#yogfist-burningfist
---

## Verdict against `v4.0.0`

The mechanism holds: its ranged zap needs a clear MAGIC_BOLT line and the hero in FOV, so hiding behind a wall forces it to walk to you. Caveats: it still zaps when adjacent if its 8-12 act cooldown is up (canRangedInMelee), and melee means standing in its 3x3 fire. The 'after the Rotting Fist is dead' framing comes from an older two-fist Yog. Now the three fists come one at a time in seeded random order.
