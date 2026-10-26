---
id: bosses-2
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
claim: "“Goo's regular melee hit has roughly a 1-in-3 chance to inflict ~20 turns of Caustic Ooze; step into water to wash the debuff off, so fighting Goo near or in the flooded room helps a lot.”"
cites:
  - core/…/mobs/Goo.java:154-160
  - core/…/buffs/Ooze.java:32
  - core/…/buffs/Ooze.java:90-120
  - core/…/mobs/Goo.java:109-132
implemented_in: []
card: docs/bestiary/bosses.md#goo
---

## Verdict against `v4.0.0`

Confirmed: Random.Int(3)==0 per landed hit (the pumped strike too), Ooze for 20 turns, 1 damage per turn at depth 5, and standing in water washes it off. Caveat: only YOU in water is good; Goo standing in water heals every turn.
