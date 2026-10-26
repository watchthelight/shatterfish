---
id: specials-15
topic: bestiary
group: specials
subject: "Golden bee"
subject_class: actors.mobs.Bee
source_url: https://pixeldungeon.fandom.com/wiki/Honeypot
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "unspecified (current-era source)"
tier: 1
graded_against: v4.0.0
status: current
claim: "“Shattering/throwing a Honeypot spawns a Golden Bee that attacks the first thing in its field of view — including you, if no other enemy is nearby — so only pop a honeypot when an enemy (ideally a boss) is already in the bee's sight, not preemptively.”"
cites:
  - core/…/mobs/Bee.java:174-202
implemented_in: []
card: docs/bestiary/specials.md#bee
---

## Verdict against `v4.0.0`

Gist confirmed: with no other non-neutral mob within 3 tiles of the pot, it targets the hero if the hero is within 3 of the pot. Correction: the choice is by distance to the pot (3 tiles), not 'first thing in its field of view'.
