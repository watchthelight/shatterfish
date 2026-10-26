---
id: quest-branch-38
topic: bestiary
group: quest-branch
subject: "Dwarven skeleton"
subject_class: actors.mobs.quest.vault.VaultSkeleton
source_url: https://pixeldungeon.fandom.com/wiki/Skeleton
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "older version (source flagged oldVersion)"
tier: 1
graded_against: v4.0.0
status: current
claim: "“finish Skeletons off with a ranged attack from a distance once they're at very low HP so the explosion can't reach you”"
cites:
  - core/…/mobs/Skeleton.java:75
  - core/…/mobs/Skeleton.java:78
  - core/…/mobs/Skeleton.java:79
  - core/…/mobs/Skeleton.java:80
implemented_in: []
card: docs/bestiary/quest-branch.md#vaultskeleton
---

## Verdict against `v4.0.0`

Only adjacent characters (NEIGHBOURS8) are hit; a kill from 2+ tiles avoids it. Death by falling (Chasm) skips the burst.
