---
id: prison-1
topic: bestiary
group: prison
subject: "Skeleton"
subject_class: actors.mobs.Skeleton
source_url: https://pixeldungeon.fandom.com/wiki/Skeleton
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "pre-v4 (source flagged as old version)"
tier: F
graded_against: v4.0.0
status: current
claim: "“Skeletons explode on death for damage equal to their normal melee hit, but armor absorption against that explosion is halved compared to normal hits; wear your heaviest available armor in Prison and, if possible, finish Skeletons off with a ranged attack from a distance once they're at very low HP so the explosion can't reach you.”"
cites:
  - core/…/mobs/Skeleton.java:78-81
  - core/…/mobs/Skeleton.java:124-125
  - core/…/mobs/Skeleton.java:65-68
implemented_in: []
card: docs/bestiary/prison.md#skeleton
---

## Verdict against `v4.0.0`

Old-version claim. In v4.0.0 the blast is its own roll of 6-12 (melee is 2-10), and armor DR is applied twice, not halved (two drRoll() calls subtracted). The advice to finish it from 2+ tiles is still correct: only the 8 adjacent cells are hit.
