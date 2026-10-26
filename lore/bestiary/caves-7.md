---
id: caves-7
topic: bestiary
group: caves
subject: "Gnoll shaman (purple)"
subject_class: actors.mobs.Shaman.PurpleShaman
source_url: https://pixeldungeon.fandom.com/wiki/Gnoll_shaman
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "old wiki page (flagged oldVersion)"
tier: 1
graded_against: v4.0.0
status: needs-review
claim: "“If a Gnoll Shaman spots you at range, break line of sight behind cover; retreating behind a door makes it chase into melee range, which is the safer place to finish it off.”"
cites:
  - core/…/mobs/Shaman.java:72-76
  - core/…/mechanics/Ballistica.java:130-136
  - core/…/mobs/Mob.java:1342-1356
  - core/…/mobs/Shaman.java:91-108
implemented_in: []
card: docs/bestiary/caves.md#shaman-purpleshaman
---

## Verdict against `v4.0.0`

Tactic confirmed: its ranged attack needs a MAGIC_BOLT path that stops on solid cells (walls, closed doors), so breaking line of sight stops it, and a hunting mob that cannot see you walks to your last seen cell. Adjacent it only uses 5-10 melee, which armor reduces. The 'lightning bolt' wording is outdated (see the next entry).
