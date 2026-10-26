---
id: quest-branch-10
topic: bestiary
group: quest-branch
subject: "Wraith (corpse dust)"
subject_class: items.quest.CorpseDust.DustWraith
source_url: https://pixeldungeon.fandom.com/wiki/Wraith
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "older version (source flagged oldVersion)"
tier: F
graded_against: v4.0.0
status: current
claim: "“Before disturbing a Wraith-spawning tomb, stand with your back to a wall, statue, or room corner — wraiths spawn only in the four cardinal tiles around you, so blocking some of those tiles with terrain caps the number that can appear at once to 3 or fewer.”"
cites:
  - core/…/quest/CorpseDust.java:125
  - core/…/quest/CorpseDust.java:126
  - core/…/quest/CorpseDust.java:127
  - core/…/quest/CorpseDust.java:128
  - core/…/quest/CorpseDust.java:129
  - core/…/quest/CorpseDust.java:130
  - core/…/quest/CorpseDust.java:135
  - core/…/mobs/Wraith.java:106
  - core/…/mobs/Wraith.java:107
  - core/…/mobs/Wraith.java:108
implemented_in: []
card: docs/bestiary/quest-branch.md#dustwraith
---

## Verdict against `v4.0.0`

The four-cardinal-tile rule is Wraith.spawnAround (tomb/heap wraiths). Corpse-dust wraiths instead appear on a random empty visible cell more than round(viewDistance/3) tiles away, so standing against a wall does not cap them; standing where no visible cell is 4+ tiles away does.
