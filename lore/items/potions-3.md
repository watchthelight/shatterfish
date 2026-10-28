---
id: potions-3
topic: items
group: potions
subject: "Queued potions are dropped away from their room"
source_url: https://github.com/00-Evan/shattered-pixel-dungeon/blob/master/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/RegularLevel.java
source_type: developer
date: unknown
retrieved: 2026-10-28
variant: spd
version_claimed: "current master"
tier: 1
graded_against: v4.0.2
status: current
claim: "The potion a special room queues is not placed beside the room: it is dropped on a random cell of an ordinary room, or taken as the room's own prize."
cites:
  - core/…/levels/RegularLevel.java:450-461
implemented_in:
  - rule:potion-room-inference
---

## Verdict against `v4.0.2`

Every item in the floor's spawn list gets randomDropCell() and a plain heap (RegularLevel.java:450-461). Whether the queued potion ended up in the room's chest depends on findPrizeItem(), which the pool, sentry, storage, magical fire and traps rooms call, so a hunt for the potion beside the puzzle is wrong, and a potion not found loose may be in the vault.
