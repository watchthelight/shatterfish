---
id: specials-2
topic: bestiary
group: specials
subject: "Swarm of flies"
subject_class: actors.mobs.Swarm
source_url: https://pixeldungeon.fandom.com/wiki/Swarm_of_flies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "old wiki page (flagged oldVersion)"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Avoid non-physical damage sources (debuffs, magic, wands) on a Swarm if you want it to keep splitting for extra Potion of Healing drops — those damage types don't trigger the split.”"
cites:
  - core/…/mobs/Swarm.java:88
  - core/…/wands/WandOfMagicMissile.java:62
  - core/…/mobs/Swarm.java:145-155
implemented_in: []
card: docs/bestiary/specials.md#swarm
---

## Verdict against `v4.0.0`

Confirmed that wand/debuff damage bypasses defenseProc and so never splits. Farming caveat: each clone's drop chance is 1/(6\*(generation+1)) times (5 - SWARM_HP drops)/5, so returns shrink fast and stop after 5 drops.
