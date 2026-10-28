---
id: potions-1
topic: items
group: potions
subject: "Potion colours"
source_url: https://github.com/00-Evan/shattered-pixel-dungeon/blob/master/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/ItemStatusHandler.java
source_type: developer
date: unknown
retrieved: 2026-10-28
variant: spd
version_claimed: "current master"
tier: 1
graded_against: v4.0.2
status: current
claim: "Which colour is which potion is a uniform random shuffle made once per run; nothing on a level, such as the traps or the terrain, changes it."
cites:
  - core/…/items/ItemStatusHandler.java:42-62
  - core/…/Dungeon.java:243-247
implemented_in:
  - rule:potion-room-inference
---

## Verdict against `v4.0.2`

Labels are assigned with Random.Int(labelsLeft.size()) in Dungeon.init, under the seed, before any floor exists. No trap or blob code touches the handler, so a trap on a floor says nothing about a colour. The only link between a floor and a potion kind is the six rooms that queue a fixed potion.
