---
id: specials-1
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
claim: "“Lure a Swarm into a hallway before fighting so it can't surround you, and rely on armor rather than your weapon — melee/physical damage causes it to split in two (each half HP) if there is empty cardinal space, so alternate hitting and retreating, and mop up flies that can no longer split.”"
cites:
  - core/…/mobs/Swarm.java:88-120
  - core/…/mobs/Swarm.java:83-85
  - core/…/mobs/Swarm.java:122-125
implemented_in: []
card: docs/bestiary/specials.md#swarm
---

## Verdict against `v4.0.0`

Split needs HP &gt;= damage+2 and a free non-solid cardinal cell; the clone gets (HP-damage)/2 and the original loses the same, so each half is about half the remaining HP. Accuracy 10 and damage 1-4 make armor the key stat. A fly with HP &lt; damage+2 cannot split.
