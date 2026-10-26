---
id: bosses-29
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
claim: "“The Burning Fist is especially weak against Bleeding, Poison, and Vertigo debuffs; inflict these before or during the fight for extra damage-over-time and crowd control.”"
cites:
  - core/…/mobs/YogFist.java:216
  - core/…/mobs/YogFist.java:218
  - core/…/mobs/YogFist.java:219
  - core/…/mobs/YogFist.java:221
  - core/…/mobs/YogFist.java:222
  - core/…/mobs/YogFist.java:224
  - core/…/mobs/YogFist.java:225
  - core/…/mobs/YogFist.java:227
  - core/…/mobs/YogFist.java:229
  - core/…/mobs/YogFist.java:230
  - core/…/mobs/YogFist.java:231
  - core/…/mobs/YogFist.java:232
  - core/…/mobs/YogFist.java:233
  - core/…/mobs/YogFist.java:235
  - core/…/mobs/YogFist.java:236
  - core/…/mobs/YogFist.java:238
  - core/…/mobs/YogFist.java:239
  - core/…/mobs/YogFist.java:240
  - core/…/mobs/YogFist.java:241
  - core/…/mobs/YogFist.java:242
  - core/…/mobs/YogFist.java:243
  - core/…/mobs/YogFist.java:244
  - core/…/mobs/YogFist.java:245
  - core/…/mobs/YogFist.java:247
  - core/…/mobs/YogFist.java:248
  - core/…/mobs/YogFist.java:249
  - core/…/mobs/YogFist.java:250
  - core/…/mobs/YogFist.java:251
  - core/…/mobs/YogFist.java:252
  - core/…/mobs/YogFist.java:254
  - core/…/mobs/YogFist.java:255
  - core/…/mobs/YogFist.java:257
  - core/…/mobs/YogFist.java:258
  - core/…/mobs/YogFist.java:259
  - core/…/mobs/YogFist.java:261
  - core/…/mobs/YogFist.java:262
  - core/…/mobs/YogFist.java:263
  - core/…/mobs/YogFist.java:264
  - core/…/mobs/YogFist.java:265
  - core/…/mobs/YogFist.java:266
  - core/…/mobs/YogFist.java:267
  - core/…/mobs/YogFist.java:269
  - core/…/mobs/YogFist.java:270
  - core/…/mobs/YogFist.java:271
  - core/…/mobs/YogFist.java:272
  - core/…/mobs/YogFist.java:273
  - core/…/mobs/YogFist.java:274
  - core/…/mobs/YogFist.java:275
  - core/…/mobs/YogFist.java:276
  - core/…/mobs/YogFist.java:278
  - core/…/mobs/YogFist.java:280
  - core/…/mobs/YogFist.java:281
  - core/…/mobs/YogFist.java:283
  - core/…/mobs/YogFist.java:284
  - core/…/mobs/YogFist.java:285
  - core/…/mobs/YogFist.java:287
  - core/…/actors/Char.java:1423
  - core/…/actors/Char.java:1424
implemented_in: []
card: docs/bestiary/bosses.md#yogfist-burningfist
---

## Verdict against `v4.0.0`

The code has no special weakness. BurningFist has no damage multiplier or vulnerability for Bleeding, Poison or Vertigo. They apply normally because it is not immune to them (FIERY only grants Burning/Blazing immunity), but they do no extra damage.
