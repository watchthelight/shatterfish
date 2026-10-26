---
id: halls-2
topic: bestiary
group: halls
subject: "Evil eye"
subject_class: actors.mobs.Eye
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“General counter to any enemy with a long-range attack: as soon as it spots you, retreat behind a door or around a corner so it loses line of sight and can't use its ranged attack; this applies to Warlocks, Scorpios, Wisps and similar spellcasters/archers throughout these floors.”"
cites:
  - core/…/mobs/Eye.java:96
  - core/…/mobs/Eye.java:97
  - core/…/mobs/Eye.java:103
  - core/…/mobs/Eye.java:279
implemented_in: []
card: docs/bestiary/halls.md#eye
---

## Verdict against `v4.0.0`

Applied to the Evil Eye, which is a long-ranged Halls caster though the claim doesn't name it. Breaking sight before it charges prevents the charge (canAttack needs the target in FOV). Breaking sight during a charge makes it fire at your old cell instead of re-aiming, which is the only reliable dodge.
