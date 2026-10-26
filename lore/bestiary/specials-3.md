---
id: specials-3
topic: bestiary
group: specials
subject: "Animated statue"
subject_class: actors.mobs.Statue
source_url: https://pixeldungeon.fandom.com/wiki/Animated_statue
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "old wiki page (flagged oldVersion)"
tier: F
graded_against: v4.0.0
status: current
claim: "“A statue stays inert and won't attack until you first deal damage to it or apply a debuff to it (or trip the Guardian Trap that spawns it); this generally can't happen until you actually try to take the loot behind the locked door, unless you proactively snipe it with a Wand of Disintegration or a Projecting-enchanted thrown weapon from outside its detection.”"
cites:
  - core/…/mobs/Statue.java:117-136
  - core/…/special/StatueRoom.java:43-73
  - core/…/traps/GuardianTrap.java:59-67
implemented_in: []
card: docs/bestiary/specials.md#statue
---

## Verdict against `v4.0.0`

Inert-until-damaged-or-debuffed is correct. But in v4.0.0 the statue vault holds no loot besides the statue itself (and nothing wakes it when you walk in), and Guardian-trap guardians are a different subclass that spawn already WANDERING toward you, not inert.
