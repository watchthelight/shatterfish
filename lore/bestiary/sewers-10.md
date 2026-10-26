---
id: sewers-10
topic: bestiary
group: sewers
subject: "Fetid rat"
subject_class: actors.mobs.FetidRat
source_url: https://pixeldungeon.fandom.com/wiki/Fetid_rat
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "old wiki page (flagged oldVersion)"
tier: 1
graded_against: v4.0.0
status: current
claim: "“In melee, let it move to you, hit once, then back away immediately so its gas cloud doesn't engulf you; don't chase it into the cloud.”"
cites:
  - core/…/mobs/FetidRat.java:78-84
  - core/…/blobs/StenchGas.java:53-65
implemented_in: []
card: docs/bestiary/sewers.md#fetidrat
---

## Verdict against `v4.0.0`

Consistent with code: each landed hit seeds gas on the rat's tile, and paralysis only applies to characters standing in gas cells. Better still, fight it with wands or from range. (Part of a compound claim.)
