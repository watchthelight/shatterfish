---
title: 'Story 6.1: The one-ply expectimax over Codex tables'
type: 'feature'
created: '2026-10-26'
status: 'review'
baseline_commit: '0b7f5a300'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** The fight Policies choose by rules of thumb. ADR-0010 option 2 asks for a model: for each Action the
screen offers, the expected result of one exchange of blows, weighed over the enemies that can reach the hero.

**Approach:** A new Policy, `one-ply`, that scores each offered Action by chance of killing, chance of dying,
expected damage taken and exposure to the enemies in view, using Codex tables only. The hit table the Codex
had recorded as unmeasured is now generated exactly from the rule's closed form (`Char.java:619-683`, `:390-416`),
so the model never carries a second copy of the game's rules.

## Acceptance Criteria (from epics.md Story 6.1, with the tests that hold them)

1. The Codex carries a hit table, generated and not hand-edited. `HitTableTest`.
2. The model reads only Observations and the Codex. `OnePlyModelTest`, the ArchUnit brain boundary, `OnePlyLeakTest`.
3. The same Observation gets the same Action. `OnePlyModelTest`.
4. A direction check on `smoke` and the first 40 seeds of `bosses` and `standard`, labelled a direction check,
   not an acceptance (story 6.6 holds the acceptance). Tables are in the pull request.

</frozen-after-approval>

## Decisions

- **Opt-in.** `Brain.Model.ONE_PLY`, rig brain name `oneply`, weights `weights/oneply.json`. The default Brain is
  unchanged, so no existing table moves. `rig:direction` gained `--brain` so a candidate brain can be measured
  against a baseline ref played by the shatterfish brain.
- **The hit table is exact.** 21 by 21 cells at accuracy and evasion 0..40 step 2, per mille, `samples = 0`
  marking an exact cell. `HitTable.chance` interpolates between cells and scales out-of-grid values to fit.
  `HitTableTest` checks the closed form against the engine's own `Char.hit` at 16 points.
- **Exposure over all awake enemies** and a crowding key with two or more awake: a distance cut made the hero
  kite a lone rat forever.
- **Cornered, it fights**; **a lone coming enemy is waited for** so the first swing is the hero's.

## Deferred

- Weapon and armour accuracy factors, special attacks and status effects are not modelled.
- Consumables other than the healing potion are not weighed.
- The search leak test is story 6.3; `OnePlyLeakTest` is the first evidence only.

## Review notes

- **Fairness review (fairness-reviewer): no violation.** The model imports only `org.shatterfish.api`; every input is an
  Observation field or the Codex; there is no Random, seed or engine snapshot; enemy health is the drawn bar against
  the Codex `ht`.
- **The hit table is a tabulation under non-negotiable 4, not a new rule.** The generator guards on the pinned lines
  of `Char.hit` (the two draws at `Char.java:646` and `:662`, the comparison at `:678`) and tabulates the closed form
  of that comparison; `HitTableTest` checks 16 cells against the engine's own `Char.hit`. The table scopes to the plain
  comparison: it does not cover the infinite thresholds (`:612-613`, `:638-643`), Bless/Hex/Daze multipliers, champion
  modifiers or surprise attacks. `Fight.hitChance` in the brain holds the same formula for the baseline Policies; unifying
  the two is left for later.
- **The regenerated `codex/v4.0.2/combat.json` changes the Codex digest for every Brain,** the baseline included. The
  default Brain's decisions do not change (it never reads the hit table), but its knowledge digest and config hash do.
- **Story 6.3 should add** invisible and disguised enemy cases and mind-vision/darkness toggles to the leak tests;
  `Observer` is untouched here.
