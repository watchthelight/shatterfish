---
id: potions-4
topic: items
group: potions
subject: "Potion price"
source_url: https://github.com/00-Evan/shattered-pixel-dungeon/blob/master/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/Potion.java
source_type: developer
date: unknown
retrieved: 2026-10-28
variant: spd
version_claimed: "current master"
tier: 1
graded_against: v4.0.2
status: current
claim: "A potion's price does not tell what it is: every regular potion is worth 30 gold a unit."
cites:
  - core/…/items/potions/Potion.java:441-443
implemented_in: []
---

## Verdict against `v4.0.2`

Potion.value() is 30 * quantity for every potion that is not an exotic one, so a shop price cannot rank unknown potions.
