---
id: prison-5
topic: bestiary
group: prison
subject: "Spectral necromancer"
subject_class: actors.mobs.SpectralNecromancer
source_url: https://pixeldungeon.fandom.com/wiki/User_blog:108.184.82.95/How_to_kill_Wraiths
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "pre-v4 (source flagged as old version)"
tier: F
graded_against: v4.0.0
status: needs-review
claim: "“Wraiths have very high evasion, so melee attacks miss often; they're described as weak to magic-type damage (including fire and poison), and this source claims a Wraith only has 1 HP so any connecting hit — including a guaranteed surprise attack performed by retreating behind a door — kills it outright.”"
cites:
  - core/…/mobs/Wraith.java:49
  - core/…/mobs/Wraith.java:57
  - core/…/mobs/Wraith.java:85-89
  - core/…/actors/Char.java:1421-1422
implemented_in: []
card: docs/bestiary/prison.md#spectralnecromancer
---

## Verdict against `v4.0.0`

Claim is about Wraith generally; recorded here because the spectral necromancer summons them. Confirmed: HP 1 and evasion = 5 x accuracy (70 for its level-4 wraiths). Contradicted: wraiths are INORGANIC, so poison and toxic gas do nothing. Note summoned wraiths start with enemySeen = true, so the door-retreat surprise only works after they lose sight of you.
