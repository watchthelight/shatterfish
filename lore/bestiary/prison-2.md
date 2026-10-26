---
id: prison-2
topic: bestiary
group: prison
subject: "Skeleton"
subject_class: actors.mobs.Skeleton
source_url: https://www.youtube.com/watch?v=TE1zJ1Eh7fg
source_type: video
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "not stated"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Aim to have armor that blocks roughly 4+ damage on average before descending into Prison, specifically to survive the death-explosion reliably if you can't kill a Skeleton before it reaches melee range.”"
cites:
  - core/…/mobs/Skeleton.java:81
  - core/…/mobs/Skeleton.java:124-125
implemented_in: []
card: docs/bestiary/prison.md#skeleton
---

## Verdict against `v4.0.0`

The arithmetic holds in v4.0.0: the blast averages 9 (NormalIntRange 6-12) and DR is subtracted twice, so an average DR roll of 4 absorbs about 8 of it. The exact threshold is advice; the double-DR mechanism is code.
