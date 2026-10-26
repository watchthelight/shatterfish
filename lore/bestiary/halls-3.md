---
id: halls-3
topic: bestiary
group: halls
subject: "Scorpio"
subject_class: actors.mobs.Scorpio
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Scorpios shoot a ranged spike attack and behave similarly to the Gnoll Trickster: they have no melee attack and will try to flee if the Hero gets adjacent. Their ranged hits deal physical damage (reducible by armor) and have a chance to inflict Cripple. They are described as very manageable if you fight from a doorway, though repeated door use can eventually break the door.”"
cites:
  - core/…/mobs/Scorpio.java:74
  - core/…/mobs/Scorpio.java:75
  - core/…/mobs/Scorpio.java:81
  - core/…/mobs/Scorpio.java:82
  - core/…/mobs/Scorpio.java:90
  - core/…/mobs/Scorpio.java:91
  - core/…/actors/Char.java:388
  - core/…/actors/Char.java:390
implemented_in: []
card: docs/bestiary/halls.md#scorpio
---

## Verdict against `v4.0.0`

Core mechanics confirmed: no attack when adjacent, retreats (getFurther) while hunting, the shot is a normal attack so armor DR applies, and 50% Cripple. It backs away whenever it sees you, not only when you are adjacent. The door caveat is wrong: doors have no durability and only toggle DOOR/OPEN_DOOR (core/…/levels/features/Door.java:36,54). They are removed only by fire or destroy. See the split entry below.
