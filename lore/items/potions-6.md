---
id: potions-6
topic: items
group: potions
subject: "Sentry room"
source_url: https://github.com/00-Evan/shattered-pixel-dungeon/blob/master/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/rooms/special/SentryRoom.java
source_type: developer
date: unknown
retrieved: 2026-10-28
variant: spd
version_claimed: "current master"
tier: 1
graded_against: v4.0.2
status: current
claim: "A floor with a sentry room (a red sentry on a pedestal) also holds a Potion of Haste: the room adds one to the floor's item list whatever the colour is."
cites:
  - core/…/levels/rooms/special/SentryRoom.java:161
  - core/…/levels/rooms/special/SpecialRoom.java:102-104
implemented_in:
  - rule:potion-room-inference
---

## Verdict against `v4.0.2`

The room's paint method calls addItemToSpawn with a new Potion of Haste unconditionally (line 161). The potion is queued and dropped later as a plain heap on a random cell of an ordinary room, not beside the room (RegularLevel.java:450-461), and the room's prize can pull it into the room's chest. So the potion exists on that floor unless it lies in a vault or in unexplored ground. At most one potion-spawning room appears per floor (SpecialRoom.java:146-148).
