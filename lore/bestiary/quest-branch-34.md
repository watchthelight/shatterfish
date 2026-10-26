---
id: quest-branch-34
topic: bestiary
group: quest-branch
subject: "Captured shaman"
subject_class: actors.mobs.quest.vault.VaultShaman
source_url: https://pixeldungeon.fandom.com/wiki/Gnoll_shaman
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "older version (source flagged oldVersion)"
tier: 1
graded_against: v4.0.0
status: current
claim: "“If a Gnoll Shaman spots you at range, break line of sight behind cover immediately to dodge its lightning bolt; retreating behind a door will make it chase into melee range”"
cites:
  - core/…/mobs/Shaman.java:73
  - core/…/mobs/Shaman.java:74
  - core/…/mobs/Shaman.java:75
  - core/…/mobs/Shaman.java:93
  - core/…/mobs/Shaman.java:94
  - core/…/mobs/Shaman.java:96
  - core/…/mechanics/Ballistica.java:49
implemented_in: []
card: docs/bestiary/quest-branch.md#vaultshaman
---

## Verdict against `v4.0.0`

The bolt needs a MAGIC_BOLT line; behind cover it must approach. Current code calls it an earthen bolt (Shaman.java:111), not lightning.
