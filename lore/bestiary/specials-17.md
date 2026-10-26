---
id: specials-17
topic: bestiary
group: specials
subject: "Golden bee"
subject_class: actors.mobs.Bee
source_url: https://steamcommunity.com/sharedfiles/filedetails/?id=2786737188
source_type: forum
date: unknown
retrieved: 2026-09-26
variant: unknown
version_claimed: "unspecified (current-era source)"
tier: F
graded_against: v4.0.0
status: current
claim: "“Crazy Bandits can steal and shatter a Honeypot from your inventory, which releases a hostile Golden Bee, so don't assume a Bandit encounter is contained to just the Bandit if you're carrying honeypots.”"
cites:
  - core/…/mobs/Thief.java:146-168
  - core/…/mobs/Bee.java:150-152
implemented_in: []
card: docs/bestiary/specials.md#bee
---

## Verdict against `v4.0.0`

Bandits (Thief subclass) do shatter a stolen honeypot, but they shatter it with themselves as the pot holder, so the bee targets the bandit, not the hero. It only threatens the hero later, around the dropped pot, if the bandit dies. Claim filed under Crazy Bandit; graded here for the bee behaviour.
