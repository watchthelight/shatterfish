---
id: bosses-1
topic: bestiary
group: bosses
subject: "Goo"
subject_class: actors.mobs.Goo
source_url: https://pixeldungeon.fandom.com/wiki/Goo
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "unspecified (SPD wiki, no version stated)"
tier: 1
graded_against: v4.0.0
status: current
claim: "“When Goo starts its pump-up '!!!' animation, immediately move at least 2 tiles away from it — staying in that radius risks a hit that can one-shot an underleveled hero; the attack can also simply miss if you can't escape in time.”"
cites:
  - core/…/mobs/Goo.java:141-152
  - core/…/mobs/Goo.java:67-89
  - core/…/actors/Char.java:619-685
implemented_in: []
card: docs/bestiary/bosses.md#goo
---

## Verdict against `v4.0.0`

Confirmed with a precision fix: the pumped strike reaches distance 2 with a clear line, so you must END 3+ tiles away (two steps from adjacent) or behind a wall. Ending exactly 2 tiles away is still in range. It deals 3x damage (3-24, 3-36 enraged) at x2 accuracy and uses a normal hit roll, so it can miss.
