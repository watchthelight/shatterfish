---
id: halls-4
topic: bestiary
group: halls
subject: "Scorpio"
subject_class: actors.mobs.Scorpio
source_url: https://pixeldungeon.fandom.com/wiki/Shattered_Pixel_Dungeon/Enemies
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: spd
version_claimed: "not stated"
tier: F
graded_against: v4.0.0
status: needs-review
claim: "“Split from the claim above: 'repeated door use can eventually break the door.'”"
cites:
  - core/…/features/Door.java:36
  - core/…/features/Door.java:54
  - core/…/levels/Level.java:928
  - core/…/levels/Level.java:929
  - core/…/levels/Level.java:930
  - core/…/levels/Level.java:931
  - core/…/levels/Level.java:932
  - core/…/levels/Level.java:933
  - core/…/levels/Level.java:934
implemented_in: []
card: docs/bestiary/halls.md#scorpio
---

## Verdict against `v4.0.0`

Doors only switch between DOOR and OPEN_DOOR. There is no use counter. A door is removed only when flammable terrain is destroyed (fire, the Eye/Yog beams), which leaves EMBERS.
