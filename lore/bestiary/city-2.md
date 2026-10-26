---
id: city-2
topic: bestiary
group: city
subject: "Fire elemental"
subject_class: actors.mobs.Elemental.FireElemental
source_url: https://pixeldungeon.fandom.com/wiki/Fire_elemental
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "not stated"
tier: F
graded_against: v4.0.0
status: needs-review
claim: "“Fire Elementals take heavy bonus damage from the Chill effect, to the point that a chill/freeze source can one-hit-kill one; conversely, do not use a Wand of Firebolt or a Potion of Liquid Flame on a Fire Elemental, as fire damage does nothing to it and may slightly heal it instead.”"
cites:
  - core/…/mobs/Elemental.java:71
  - core/…/mobs/Elemental.java:191
  - core/…/mobs/Elemental.java:193
  - core/…/mobs/Elemental.java:237
  - core/…/mobs/Elemental.java:238
  - core/…/actors/Char.java:1423
  - core/…/actors/Char.java:1424
implemented_in: []
card: docs/bestiary/city.md#elemental-fireelemental
---

## Verdict against `v4.0.0`

Frost or Chill deals 30-36 of its 60 HP, a big hit but not a one-shot from full. Fire does not heal it: it is immune to Burning and halves Wand of Fireblast; nothing in the code heals a FIERY mob from fire.
