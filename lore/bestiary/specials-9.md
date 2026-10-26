---
id: specials-9
topic: bestiary
group: specials
subject: "Wraith"
subject_class: actors.mobs.Wraith
source_url: https://pixeldungeon.fandom.com/wiki/User_blog:108.184.82.95/How_to_kill_Wraiths
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "old wiki page (flagged oldVersion)"
tier: F
graded_against: v4.0.0
status: needs-review
claim: "“Wraiths have very high evasion, so melee attacks miss often; they're described as weak to magic-type damage (including fire and poison), and this source claims a Wraith only has 1 HP so any connecting hit — including a guaranteed surprise attack performed by retreating behind a door — kills it outright. Treat the '1 HP' figure with caution since it's stated without a version and may not reflect the current build's stats.”"
cites:
  - core/…/mobs/Wraith.java:49
  - core/…/mobs/Wraith.java:85-88
  - core/…/mobs/Wraith.java:57
  - core/…/actors/Char.java:1421-1422
implemented_in: []
card: docs/bestiary/specials.md#wraith
---

## Verdict against `v4.0.0`

1 HP, very high evasion (5x accuracy), and a guaranteed surprise hit all confirmed in v4.0.0; fire works (no Burning immunity). Poison is wrong: wraiths are INORGANIC and immune to Poison and ToxicGas.
