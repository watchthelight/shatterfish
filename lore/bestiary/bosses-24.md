---
id: bosses-24
topic: bestiary
group: bosses
subject: "Yog-Dzewa"
subject_class: actors.mobs.YogDzewa
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: F
graded_against: v4.0.0
status: current
claim: "“Every physical hit the Hero lands on Yog-Dzewa's central eye spawns larvae that attack the Hero; damage the Hero deals to Yog-Dzewa is divided by 4 while both fists are alive, and by 2 once only one fist remains — implying the fists should be killed first before committing to attacking the eye directly.”"
cites:
  - core/…/mobs/YogDzewa.java:297
  - core/…/mobs/YogDzewa.java:333
  - core/…/mobs/YogDzewa.java:388
  - core/…/mobs/YogDzewa.java:397
  - core/…/mobs/YogDzewa.java:407
  - core/…/mobs/YogDzewa.java:408
implemented_in: []
card: docs/bestiary/bosses.md#yogdzewa
---

## Verdict against `v4.0.0`

Old-version mechanics. Summons run on a 10-15 turn timer, not per hit. Damage to Yog only shortens that timer by dmg/10. There is no /4 or /2 damage scaling: Yog is fully invulnerable (isInvulnerable returns true) while ANY fist exists, and normally only one fist exists at a time. The practical conclusion (kill the fist before hitting Yog) is forced by the code anyway.
