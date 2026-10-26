---
id: specials-6
topic: bestiary
group: specials
subject: "Summoned guardian"
subject_class: levels.traps.GuardianTrap.Guardian
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
  - core/…/traps/GuardianTrap.java:59-67
  - core/…/traps/GuardianTrap.java:74-79
implemented_in: []
card: docs/bestiary/specials.md#guardian
---

## Verdict against `v4.0.0`

Guardians are never inert: they spawn WANDERING and are beckoned to the hero's position when the trap fires.
