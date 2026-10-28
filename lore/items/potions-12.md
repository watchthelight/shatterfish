---
id: potions-12
topic: items
group: potions
subject: "Scroll of Upgrade schedule"
source_url: https://github.com/00-Evan/shattered-pixel-dungeon/blob/master/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Dungeon.java
source_type: developer
date: unknown
retrieved: 2026-10-28
variant: spd
version_claimed: "current master"
tier: 1
graded_against: v4.0.2
status: current
claim: "Three scrolls of upgrade drop per five floors, with a rising chance on each floor as the set runs out, and never as a random draw."
cites:
  - core/…/Dungeon.java:545-554
  - core/…/items/Generator.java:376-377
implemented_in: []
---

## Verdict against `v4.0.2`

souNeeded() fires when Random.Int(5 - floorThisSet) is below the number still owed in the set (Dungeon.java:545-554); the Generator weights upgrade zero in both scroll decks.
