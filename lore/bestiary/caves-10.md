---
id: caves-10
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
tier: F
graded_against: v4.0.0
status: current
claim: "“While fleeing it lays a web trail; stepping on its webs roots you for 5-7 turns.”"
cites:
  - core/…/mobs/Spinner.java:136-209
  - core/…/buffs/Roots.java:29
  - core/…/blobs/Web.java:67-70
implemented_in: []
card: docs/bestiary/caves.md#spinner
---

## Verdict against `v4.0.0`

Webs are not a trail behind the spinner: it shoots a 3-cell web at the cell you are moving into (or between you and it if you stood still), with a 10-turn cooldown. Stepping into a web applies Roots for exactly 5 turns (Roots.DURATION), not 5-7.
