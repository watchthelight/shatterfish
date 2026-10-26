---
id: specials-13
topic: bestiary
group: specials
subject: "Giant piranha"
subject_class: actors.mobs.Piranha
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Special_Rooms
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "unspecified (current-era source)"
tier: F
graded_against: v4.0.0
status: needs-review
claim: "“The Flooded Vault special room is guarded by 3 Giant Piranhas. Piranhas die instantly if kept out of water for a single turn, so luring them onto dry land (or draining the pool) kills them outright; every floor containing a flooded vault also generates a Potion of Invisibility elsewhere on that floor, letting you sneak past the piranhas entirely to reach the vault's chest instead of fighting them in the water where they have the advantage.”"
cites:
  - core/…/special/PoolRoom.java:39
  - core/…/special/PoolRoom.java:91
  - core/…/mobs/Piranha.java:132-146
  - core/…/levels/Level.java:1219-1221
implemented_in: []
card: docs/bestiary/specials.md#piranha
---

## Verdict against `v4.0.0`

Three piranhas and the Invisibility potion are confirmed, and a piranha on land dies at once. But you cannot lure one onto land: its pathing only uses water cells. Only knockback, teleport or terrain change puts it on land, and 1 in 50 is a Phantom Piranha that teleports back to water instead of dying.
