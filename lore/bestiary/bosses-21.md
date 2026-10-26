---
id: bosses-21
topic: bestiary
group: bosses
subject: "King of Dwarves"
subject_class: actors.mobs.DwarfKing
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Bosses
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: 3
graded_against: v4.0.0
status: needs-review
claim: "“Use a Scroll of Mirror Image early in the fight (rather than saving it) so the summoned mirror images attack the King himself rather than getting wasted on summoned skeletons; a Scroll of Psionic Blast is also considered strong once he has summoned several skeletons, though its damage is inconsistent.”"
cites:
  - core/…/actors/Char.java:1414
implemented_in: []
card: docs/bestiary/bosses.md#dwarfking
---

## Verdict against `v4.0.0`

Mirror-image targeting is AI behaviour not checked here. The King has no skeleton summons, and BOSS resists Scroll of Psionic Blast (half effect on the King).
