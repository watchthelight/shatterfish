---
id: specials-18
topic: bestiary
group: specials
subject: "Mimic"
subject_class: actors.mobs.Mimic
source_url: https://steamcommunity.com/sharedfiles/filedetails/?id=3225596609
source_type: forum
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "unspecified (current-era source)"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Examining a suspicious chest will explicitly tell you if something is off — the safest ID method. If you'd rather not examine, hit the chest first with a ranged weapon, wand or thrown item from a distance; a Mimic will reveal itself and take damage, while a real chest is unaffected.”"
cites:
  - core/…/mobs/Mimic.java:120-131
  - core/…/trinkets/MimicTooth.java:64-66
  - core/…/mobs/Mimic.java:194-201
implemented_in: []
card: docs/bestiary/specials.md#mimic
---

## Verdict against `v4.0.0`

Examine shows the 'feels off' hint for every chest-shaped mimic unless you carry a Mimic Tooth (then no hint). A ranged or wand hit reveals it (damage calls stopHiding); on a real chest a thrown item just lands.
