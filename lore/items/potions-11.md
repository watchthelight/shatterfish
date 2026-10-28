---
id: potions-11
topic: items
group: potions
subject: "Potion of Strength schedule"
source_url: https://github.com/00-Evan/shattered-pixel-dungeon/blob/master/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Dungeon.java
source_type: developer
date: unknown
retrieved: 2026-10-28
variant: spd
version_claimed: "current master"
tier: 1
graded_against: v4.0.2
status: current
claim: "Two potions of strength drop per five floors, never as a random draw: the first is certain by depth 2 and half likely on depth 1, the second certain by depth 4 and half likely on depth 3."
cites:
  - core/…/Dungeon.java:529-543
  - core/…/items/Generator.java:342-343
implemented_in: []
---

## Verdict against `v4.0.2`

posNeeded() allows two per set (depth / 5) and fires when the target left is below the number owed; the target is 2 - floorThisSet/2, less one half the time on odd floors. The Generator weights strength zero in both decks, so a strength potion is always one of these queued drops. Older wiki text that says one every two floors with no detail is incomplete, not wrong.
