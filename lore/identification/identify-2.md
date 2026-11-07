---
id: identify-2
topic: identification
group: identify
subject: "Potion of Strength"
source_url: https://pixeldungeon.fandom.com/wiki/Potion_of_Strength
source_type: wiki
date: unknown
retrieved: 2026-10-29
variant: pd
version_claimed: "unspecified"
tier: 1
graded_against: v4.0.2
status: current
claim: "There will always be exactly two Potions of Strength in every region of the dungeon."
cites:
  - core/…/Dungeon.java:529-543
  - core/…/levels/Level.java:228-231
implemented_in: []
---

## Verdict against `v4.0.2`

Holds in Shattered Pixel Dungeon with two corrections. A "region" is the four non-boss floors of a five-floor set (depth / 5), and the quota is placed on a schedule, not at random: with two owed, floor 1 of the set places one with odds one half, floor 2 always does, and after one is placed floor 3 places the second with odds one half and floor 4 always does (`Dungeon.java:529-543`). The item is placed as an ordinary heap before the random items (`Level.java:228-231`), so it can sit in a vault room instead (`LaboratoryRoom.java:129`). The Brain uses this through the schedule in `IdentityOdds`, not through the wiki sentence. The page was read through a search result summary, not whole; the page is for the original Pixel Dungeon.
