---
id: caves-9
topic: bestiary
group: caves
subject: "Cave spinner"
subject_class: actors.mobs.Spinner
source_url: https://pixeldungeon.fandom.com/wiki/Cave_spinner
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "unspecified"
tier: 1
graded_against: v4.0.0
status: current
claim: "“A Cave Spinner attacks in melee until it lands a poison hit, then flees; kill it fast before it can poison and flee.”"
cites:
  - core/…/mobs/Spinner.java:119-132
  - core/…/mobs/Spinner.java:248-256
implemented_in: []
card: docs/bestiary/caves.md#spinner
---

## Verdict against `v4.0.0`

Confirmed with a correction: each landed bite has a 50% chance (not a certainty) to poison, and a poisoning bite switches it to FLEEING. Killing it before that happens avoids the flee-and-web phase.
