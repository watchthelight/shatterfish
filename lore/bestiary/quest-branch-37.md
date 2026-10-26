---
id: quest-branch-37
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
tier: F
graded_against: v4.0.0
status: current
claim: "“Skeletons explode on death for damage equal to their normal melee hit, but armor absorption against that explosion is halved compared to normal hits”"
cites:
  - core/…/mobs/Skeleton.java:67
  - core/…/mobs/Skeleton.java:81
  - core/…/mobs/Skeleton.java:124
  - core/…/mobs/Skeleton.java:125
implemented_in: []
card: docs/bestiary/quest-branch.md#vaultskeleton
---

## Verdict against `v4.0.0`

The burst is its own 6-12 roll (melee is 2-10), and your DR is applied twice against it (armour is doubled, not halved).
