---
id: prison-4
topic: bestiary
group: prison
subject: "Crazy bandit"
subject_class: actors.mobs.Bandit
source_url: https://steamcommunity.com/sharedfiles/filedetails/?id=2786737188
source_type: forum
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "not stated"
tier: F
graded_against: v4.0.0
status: current
claim: "“Crazy Bandits can steal and shatter a Honeypot from your inventory, which releases a hostile Golden Bee, so don't assume a Bandit encounter is contained to just the Bandit if you're carrying honeypots.”"
cites:
  - core/…/mobs/Thief.java:159-163
  - core/…/items/Honeypot.java:97-136
  - core/…/mobs/Bee.java:96-102
  - core/…/mobs/Bee.java:145-205
  - core/…/mobs/Thief.java:99-105
implemented_in: []
card: docs/bestiary/prison.md#bandit
---

## Verdict against `v4.0.0`

The theft and shatter are real, but the released bee targets the pot holder, which is the thief/bandit that stole it. It only turns to the hero after the pot is dropped (the bandit dies), and then only if no other mob is within 3 tiles of the pot and the hero is within 3.
