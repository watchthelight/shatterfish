---
id: caves-2
topic: bestiary
group: caves
subject: "Armored brute"
subject_class: actors.mobs.ArmoredBrute
source_url: https://pixeldungeon.fandom.com/wiki/Gnoll_brute
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "old wiki page (flagged oldVersion)"
tier: F
graded_against: v4.0.0
status: current
claim: "“A Gnoll Brute becomes Enraged when brought to 0 HP, gaining a temporary shield and a big damage buff that can punch through most armor; the safe play is to run away and let the shield/enrage timer expire rather than trading hits with it.”"
cites:
  - core/…/mobs/ArmoredBrute.java:59-68
  - core/…/mobs/ArmoredBrute.java:78-100
implemented_in: []
card: docs/bestiary/caves.md#armoredbrute
---

## Verdict against `v4.0.0`

Applied to the armored variant, the 'run away and let it expire' advice fails: ArmoredRage starts at 21 and loses only 1 shield per 3 turns (~63 turns). Kill the shield or leave the floor instead.
