---
title: 'Story 10.51: The hero drinks its waterskin'
type: 'feature'
created: '2026-11-09'
status: 'review'
baseline_commit: '54fc50eaf'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** a death audit of 240 Runs on main (`docs/ideas/2026-11-09-what-still-loses-before-goo.md`, table b) found 106
of 114 losing heroes dying with dew in the waterskin, 60 with five drops or more, and 82% of the Goo-floor losses holding
five or more (mean about nine). Nothing in the Brain drinks the skin: 0 uses in 240 Runs. The Brain already collects dew
(`Dew`, `Pickup`, `Descend`, issue #412).

**Approach:** a new Policy, `drink`, between `escape` and `heal`, and a drink in the descent rest. Mechanics at `v4.0.2`
(rows added to `docs/rules/pickup.md`): a drink takes the drops that bring the hero to full health, at least one and no more
than the skin holds, each healing 5% of the maximum rounded, for one turn (`Waterskin.java:87-134`, `Dewdrop.java:83-84`).
The harness needs no change: the skin offers `DRINK` while it holds dew (`Waterskin.java:74-78`), `ValidActions` lists every
item action, and `UseItem` runs it. Measured on two ALL-40 standard slices (salt bases 1000 and 3000) against the
merge-base `54fc50eaf`, with the epic's keep rule.

## Scope fences

- The `goo`, `holdout` and `tengu` sets are not played. No harness, Observer or executor change.
- No change to how dew is collected (the #412 levers stay off).
- The Vial of Blood and the Shielding Dew talent are not modelled: they only make the real drink larger.

## Design

- In a fight the skin is drunk when the hero is at the heal Policy's danger or under `drink.fight_below_per_mille` of the
  maximum, the drink heals at least `drink.min_gain_per_mille` of the maximum and more than the enemies within two cells deal
  in a turn, and the heal Policy's own gates hold (no drink when a retreat is worth more, none against Goo while a pump can be
  dodged). It sits ahead of `heal`, so it goes before a known potion and before a gamble on an unknown one.
- On a calm screen (no enemy within the engaged range) it drinks under `drink.calm_below_per_mille`.
- The descent rest (`Descend.rest`) drinks first, then rests: one turn instead of many turns of hunger, and the hero arrives
  on the Goo floor topped up.
- All three numbers are in the Tunables registry and linked to rule `drink-waterskin`.

## Alternatives weighed (before building)

1. Put the drink inside `Heal` as one more branch. Rejected: `Heal` enters only with an enemy in view and marks the memory as
   a potion drunk (`Memory.drinking`), which would stop a potion drunk a turn later.
2. A Policy that drinks whenever the hero is below full health. Rejected: a drop is worth about two hit points; a one-drop
   drink costs a turn for little, hence a least gain.
3. Drink only at the heal danger. Kept as the floor of the fight case; the 40% mark adds the audit's "once under a third
   health" case that the danger estimate misses when the enemy is weak but many.
4. A Policy of its own, ahead of `heal`, plus the descent drink. Chosen.

## Pre-mortem

- The hero drinks on the first scratch and has nothing left for the Goo fight. Mitigation: a least gain of 15% of the maximum
  and a fight mark of 40%; the measure is the judge.
- A drink beside a strong enemy costs a turn of hits. Mitigation: the drink must heal more than a turn of the close enemies.
- The calm drink replaces a free regeneration. Accepted: regeneration burns hunger (`Regeneration.java:44`) and the audit
  found hunger the larger killer.

## Outcome

Kept on the first variant, no second variant needed. Two ALL-40 slices (480 Runs pooled, 240 per slice) against baseline `54fc50eaf`: reach depth 5
290 to 300, reach depth 6 236 to 249, reach depth 8 32 to 45, Goo kills 243 to 256. Worst class is 2 worse (Mage and Duelist in Goo kills); no class
is 8 or more worse. The rule drank about 1.9 times per Run (927 drinks: calm 599, fight 191, descent 137), never below the 15% gain floor. Numbers and tables:
`docs/results/2026-11-12-10-51-drink-the-waterskin.md`. The harness, `Observer` and executor are unchanged. ADR-0019: without the Brain
registration, 3 of the 12 `DrinkPolicyTest` tests fail.
