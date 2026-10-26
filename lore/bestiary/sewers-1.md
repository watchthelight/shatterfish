---
id: sewers-1
topic: bestiary
group: sewers
subject: "Marsupial rat"
subject_class: actors.mobs.Rat
source_url: https://pixeldungeon.fandom.com/wiki/Marsupial_rat
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "old wiki page (flagged oldVersion)"
tier: 1
graded_against: v4.0.0
status: needs-review
claim: "“Don't waste resources on a lone Marsupial Rat unless threatened; save consumables for deeper floors. They're only dangerous in numbers, so if a group is chasing you, retreat to a doorway/corridor and kill them one at a time.”"
cites:
  - core/…/mobs/Rat.java:36-62
  - core/…/mobs/Mob.java:558-568
  - core/…/levels/Level.java:1533-1543
implemented_in: []
card: docs/bestiary/sewers.md#rat
---

## Verdict against `v4.0.0`

Mechanics confirmed: 8 HP, 1-4 damage, adjacent-only attack, so a corridor limits attackers. Correction: the door cell itself is not a one-at-a-time spot (3 room-side neighbours); use a corridor tile. 'Save consumables' is judgment, not code.
