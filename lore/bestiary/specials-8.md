---
id: specials-8
topic: bestiary
group: specials
subject: "Wraith"
subject_class: actors.mobs.Wraith
source_url: https://pixeldungeon.fandom.com/wiki/Wraith
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "old wiki page (flagged oldVersion)"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Before disturbing a Wraith-spawning tomb, stand with your back to a wall, statue, or room corner — wraiths spawn only in the four cardinal tiles around you, so blocking some of those tiles with terrain caps the number that can appear at once to 3 or fewer.”"
cites:
  - core/…/items/Heap.java:86-88
  - core/…/mobs/Wraith.java:106-110
  - core/…/mobs/Wraith.java:120-138
implemented_in: []
card: docs/bestiary/specials.md#wraith
---

## Verdict against `v4.0.0`

Tomb wraiths spawn only on the hero's 4 cardinal neighbours that are not solid and unoccupied (allowAdjacent=false), so walls cap the count. Applies to tombs only; haunted skeletons spawn one wraith at the heap.
