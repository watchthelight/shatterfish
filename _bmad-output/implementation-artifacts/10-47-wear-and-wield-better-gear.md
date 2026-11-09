---
title: 'Story 10.47: Wear and wield better gear'
type: 'feature'
created: '2026-11-09'
status: 'done'
baseline_commit: '91c24281c'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** story 10.41 found that at depth 5 the worn armour is upgraded cloth in every Run, a heavier armour sits
unworn in 48% and the spare weapon goes unused in 88%. Which of the three levers it left open lifts the Goo kill rate?

**Approach:** measure each lever on its own on the two ALL-40 slices (salt bases 1000 and 3000, 240 Runs each), baseline
pinned to `91c24281c`, and keep what passes the keep rule (pooled not worse in reach depth 5 and Goo kills, no class 8 or
more worse).

## Alternatives weighed (before building)

1. `armour_pick` HEAVIEST in the Playbook: one line, but `TestItem.heaviest` skips armour whose level is hidden.
2. Price armour by the game's own roll and strength rule, and project the scrolls to come, so a heavier armour goes on
   before the first scroll. Chosen as the lever with a mechanism.
3. Rate a worn weapon at its shown level so the spare can replace it.
4. Hold scrolls until depth 4: already measured in 10.41 and much worse. Not retried.

## Pre-mortem

- A worn piece of hidden level weighed against a projected spare swaps back and forth. This happened in the first build
  (276 to 268) and is pinned by `EquipPolicyTest.no_ping_pong`.
- The curse risk is priced at the existing 0.353 of the cursed weight, so no cursed armour is worn that was not before.

## Outcome

- Lever 2 kept: `Equip.armourAbsorbed`, `PROJECTED_UPGRADES`. Reach depth 5 276 to 280, Goo kills 226 to 229, worst class
  change -1.
- Lever 1 inert, lever 3 dropped (Goo 226 to 225). Branches `story/10-47-l1` and `story/10-47-l3` keep the code locally.
- Results: `docs/results/2026-11-09-10-47-wear-and-wield-better-gear.md`.
