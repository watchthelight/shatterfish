---
id: caves-1
topic: bestiary
group: caves
subject: "Gnoll brute"
subject_class: actors.mobs.Brute
source_url: https://pixeldungeon.fandom.com/wiki/Gnoll_brute
source_type: wiki
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "old wiki page (flagged oldVersion)"
tier: 1
graded_against: v4.0.0
status: needs-review
claim: "“A Gnoll Brute becomes Enraged when brought to 0 HP, gaining a temporary shield and a big damage buff that can punch through most armor; the safe play is to run away and let the shield/enrage timer expire rather than trading hits with it.”"
cites:
  - core/…/mobs/Brute.java:86-112
  - core/…/mobs/Brute.java:58-62
  - core/…/mobs/Brute.java:134-151
  - core/…/mobs/Mob.java:1353-1356
implemented_in: []
card: docs/bestiary/caves.md#brute
---

## Verdict against `v4.0.0`

Confirmed: at 0 HP it gains a BruteRage shield of HT/2+4 (24), deals 15-40 instead of 5-25, and the shield loses 4 per turn, killing it after about 6 turns. A same-speed brute cannot hit a hero who keeps stepping away along an open path, so running it out works.
