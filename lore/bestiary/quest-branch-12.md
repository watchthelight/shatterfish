---
id: quest-branch-12
topic: bestiary
group: quest-branch
subject: "Wraith (corpse dust)"
subject_class: items.quest.CorpseDust.DustWraith
source_url: https://pixeldungeon.fandom.com/wiki/User_blog:108.184.82.95/How_to_kill_Wraiths
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "older version (source flagged oldVersion)"
tier: F
graded_against: v4.0.0
status: needs-review
claim: "“they're described as weak to magic-type damage (including fire and poison)”"
cites:
  - core/…/mobs/Wraith.java:57
  - core/…/actors/Char.java:1421
  - core/…/actors/Char.java:1422
  - core/…/wands/WandOfMagicMissile.java:62
implemented_in: []
card: docs/bestiary/quest-branch.md#dustwraith
---

## Verdict against `v4.0.0`

INORGANIC makes wraiths immune to Poison (and ToxicGas/Bleeding). There is no magic or fire weakness in code; magic simply skips the accuracy roll and 1 HP means any damage kills.
