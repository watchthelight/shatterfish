---
id: specials-4
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
tier: 1
graded_against: v4.0.0
status: needs-review
claim: "“Animated Statues in statue-vault rooms carry enchanted (and sometimes armored) weapons and are more dangerous than the plain, unenchanted-weapon Summoned Guardians spawned by Guardian Traps elsewhere — size up gear accordingly before opening a statue vault.”"
cites:
  - core/…/mobs/Statue.java:63-72
  - core/…/mobs/Statue.java:206-217
  - core/…/traps/GuardianTrap.java:83-89
implemented_in: []
card: docs/bestiary/specials.md#statue
---

## Verdict against `v4.0.0`

Vault statues get a random enchantment (never a curse) and 10% are Armored Statues with glyphed armor; Guardians get a +0 unenchanted weapon. HP formula (15+5\*depth) is the same for both.
