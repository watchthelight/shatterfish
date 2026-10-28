---
id: potions-2
topic: items
group: potions
subject: "Potion deck weights"
source_url: https://github.com/00-Evan/shattered-pixel-dungeon/blob/master/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/Generator.java
source_type: developer
date: unknown
retrieved: 2026-10-28
variant: spd
version_claimed: "current master"
tier: 1
graded_against: v4.0.2
status: current
claim: "Potions are drawn from two alternating decks of cards, not independent draws: healing 3 and 3, mind vision 2 and 2, frost 1 and 2, liquid flame 2 and 1, toxic gas 1 and 2, the rest 1 and 1, experience 1 and 0, strength 0 and 0."
cites:
  - core/…/items/Generator.java:342-343
implemented_in: []
---

## Verdict against `v4.0.2`

Each draw lowers the drawn type's count by one and the decks swap when empty, so a type can be held at most as many times as its weight in the two decks together (healing 6, haste 2, experience 1). A stack larger than that weight cannot be that type.
