---
id: bosses-14
topic: bestiary
group: bosses
subject: "DM-300"
subject_class: actors.mobs.DM300
source_url: https://pixeldungeon.fandom.com/wiki/DM-300
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "unspecified"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Slow, Paralyze, or Freeze effects reduce how often it can hit you.”"
cites:
  - core/…/mobs/DM300.java:162-163
  - core/…/mobs/DM300.java:677-684
implemented_in: []
card: docs/bestiary/bosses.md#dm300
---

## Verdict against `v4.0.0`

Paralysis stops its turn (act() returns early while paralysed) and it has no Paralysis resistance. It resists Slow, Chill and Frost, so those last half as long.
