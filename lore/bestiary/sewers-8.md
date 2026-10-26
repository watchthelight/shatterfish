---
id: sewers-8
topic: bestiary
group: sewers
subject: "Sewer crab"
subject_class: actors.mobs.Crab
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Sewer_Crab
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "unspecified (SPD wiki, no version stated)"
tier: 1
graded_against: v4.0.0
status: needs-review
claim: "“Because a Crab moves 2 tiles for every 1 of yours, plan positioning: if you're one tile away, back up one more tile so the crab is forced to spend its whole turn closing distance and can't attack you that turn.”"
cites:
  - core/…/mobs/Crab.java:36
  - core/…/mobs/Mob.java:1355
  - core/…/mobs/Mob.java:753-757
implemented_in: []
card: docs/bestiary/sewers.md#crab
---

## Verdict against `v4.0.0`

Follows from move cost 0.5 and attack cost 1: if you end your turn 3+ tiles away, the crab spends its whole turn closing and you strike first. It costs you your action too, so it trades tempo for the first hit. Keep doing it until it is adjacent at the start of your turn.
