---
id: prison-3
topic: bestiary
group: prison
subject: "Crazy thief"
subject_class: actors.mobs.Thief
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: F
graded_against: v4.0.0
status: current
claim: "“Crazy Thieves attack twice per turn and try to steal one unequipped item from your backpack per hit; they can't steal unique, quest, or upgraded/enchanted items, so keeping valuables equipped or upgraded protects them from being stolen.”"
cites:
  - core/…/mobs/Thief.java:86-89
  - core/…/mobs/Thief.java:129-131
  - core/…/mobs/Thief.java:146-150
implemented_in: []
card: docs/bestiary/prison.md#thief
---

## Verdict against `v4.0.0`

Partly confirmed: attack delay is halved (two attacks per turn), equipped, unique and +1-or-higher items are safe. Contradicted: enchantment gives no protection (the check is level() &lt; 1, so a +0 enchanted item can be stolen), and a thief steals only once; after a successful theft it flees and no longer steals. A hit whose random draw is protected steals nothing.
