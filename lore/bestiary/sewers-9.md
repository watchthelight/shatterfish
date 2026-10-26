---
id: sewers-9
topic: bestiary
group: sewers
subject: "Fetid rat"
subject_class: actors.mobs.FetidRat
source_url: https://pixeldungeon.fandom.com/wiki/Fetid_rat
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "old wiki page (flagged oldVersion)"
tier: F
graded_against: v4.0.0
status: current
claim: "“Fetid Rat surrounds itself with paralytic gas; standing next to it for more than one turn paralyzes you and it will kill you while paralyzed.”"
cites:
  - core/…/mobs/FetidRat.java:78-84
  - core/…/blobs/StenchGas.java:53-65
implemented_in: []
card: docs/bestiary/sewers.md#fetidrat
---

## Verdict against `v4.0.0`

There is no passive aura. The rat seeds 20 Stench Gas on its own tile only when a hit lands on it (defenseProc); an unhit rat emits nothing, and wand or DOT damage releases none. Once gas exists, anyone standing in it gets 2 turns of paralysis per tick, and its bite is only 1-4, so the danger is being locked while it chews. (Part of a compound claim.)
