---
id: bosses-8
topic: bestiary
group: bosses
subject: "Tengu"
subject_class: actors.mobs.Tengu
source_url: https://pixeldungeon.fandom.com/wiki/Tengu
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "not stated"
tier: F
graded_against: v4.0.0
status: current
claim: "“Phase 1 requires only bringing Tengu to half HP. He plants fading poison-dart traps when he jumps (8 turns of poison, ~17 total damage, plus 1-4 physical) — track where he lands and avoid those tiles. Ranged/burst classes (Rogue's sneak attack, Huntress's boomerang, Mage's staff, or any damaging wand) make phase 1 much easier.”"
cites:
  - core/…/mobs/Tengu.java:179-184
  - core/…/levels/PrisonBossLevel.java:669-729
  - core/…/traps/TenguDartTrap.java:36-43
  - core/…/traps/PoisonDartTrap.java:114-128
  - core/…/hero/HeroClass.java:221-225
implemented_in: []
card: docs/bestiary/bosses.md#tengu
---

## Verdict against `v4.0.0`

Confirmed: phase 1 ends at HP &lt;= HT/2, the traps fade, poison 8 totals 17. Contradicted: the dart's physical part is 4-8 minus DR, not 1-4; traps are re-rolled across 40-90% of the whole cell each jump, not placed where he lands, and a trap-free path to him is always left. The Huntress no longer starts with a boomerang (Spirit Bow).
