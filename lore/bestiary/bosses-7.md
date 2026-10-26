---
id: bosses-7
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
status: needs-review
claim: "“As Rogue, using Cloak of Shadows while Goo is pumping up cancels its attack outright.”"
cites:
  - core/…/mobs/Mob.java:290
  - core/…/mobs/Goo.java:243-250
implemented_in: []
card: docs/bestiary/bosses.md#goo
---

## Verdict against `v4.0.0`

An invisible hero is never in Goo's FOV, so it cannot attack and must move, and getCloser resets pumpedUp to 0. (Part of a compound claim.)
