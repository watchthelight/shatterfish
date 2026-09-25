---
title: 'Story 4.7: The fight-in-corridors Policy'
type: 'feature'
created: '2026-09-23'
status: 'review'
baseline_commit: 'story/4-6-the-explore-policy'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** With story 4.6 the Brain walks the floor, and when an enemy comes into view the
explore Policy stands aside and the random fallback takes the wait. On `smoke` that took the
median turns survived from 1,374 (the 4.5 Brain, which rested in place) to 27. The Brain has no
idea whether a fight can be won, where to take it, or when to leave (FR-31).

**Approach:** A `fight` Policy above explore, entering while an enemy is in view and no Prompt is
open. It estimates, from the Codex's combat figures and what the screen shows, whether fighting is
favourable. When it is, it attacks an adjacent enemy (the one it expects to kill soonest),
approaches a lone one, or, with two or more coming, takes the neighbouring cell that fewest of them
can engage and holds there. When it is not, it retreats: by the stairs while the floor is not
sealed, otherwise away from the enemies toward cells fewer of them can reach. Cornered, it fights.
The Codex's `mobs.json`, `combat.json` and `strings.json` reach the Brain through
`Codex.Knowledge`, read by the rig.

## Boundaries & Constraints

**Always:**
- One Action per Input wait, always one the screen offers.
- Every input is the Observation (the actors, the map, the hero's section, what it wears) or the
  Codex's general knowledge.
- A target is a character the screen shows: `Attack` is offered only on a cell a shown enemy stands on.
- Every mechanics claim has a row in `docs/brain-rules.md` resting on a cited Rule.

**Ask First:** a threat model that needs a field the Observation does not carry.

**Never:**
- Targeting a remembered, unseen enemy.
- Planning a retreat through stairs on a sealed floor.
- Reading game code from `brain`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Adjacent weak enemy | a rat beside a healthy hero | Attack it | N/A |
| Adjacent strong enemy | a brute beside the hero | Step away; the attack is an alternative | cornered: attack |
| Several coming, hero in the open | two rats across a room, hero in a doorway | Step into the corridor | N/A |
| Several coming, hero in a corridor | engage count at most 2 | Wait | N/A |
| Unfavourable, stairs reachable, floor open | brute beside, stairs in view | Step toward the stairs, or descend on them | N/A |
| Unfavourable, floor sealed | the same, sealed | Retreat away; never the stairs | N/A |
| Enemy gone from view | seen, then not | the fight Policy stands aside | N/A |
| Enemy the Codex has no figures for | a name not in the table | fought, not fled | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/api/.../Codex.java`: `Threat` (an enemy's figures by display name), `Gear` (a
  weapon's mean damage or an armour's mean damage reduction by display name and level); `Knowledge`
  gains `threats`, `weapons`, `armours` and `threat(name)`, with story 4.2's four-argument
  constructor kept.
- `shatterfish/rig/.../CodexKnowledge.java`: `threats` from `mobs.json` (enemies with a constant
  accuracy and rolled damage and armour, named through `strings.json`), `gear` from `combat.json`.
- `shatterfish/brain/.../Fight.java`: the Policy and its threat estimate.
- `shatterfish/brain/.../Brain.java`: the Policy order `answer-prompt, fight, explore, fallback`.
- `shatterfish/brain/.../Explore.java`: `WALK` package-visible for `Fight.engage`.
- `docs/rules/combat.md`: two new rows at v4.0.0 (damage less damage reduction; bare-handed damage).
- `docs/rules/levels.md`: the `Level.locked` row re-read and re-cited at v4.0.0 (Tier 1; `VaultLevel` added).
- `docs/brain-rules.md`: rows 13 to 21.

## Tasks & Acceptance

**Execution:**
- [x] `Codex.Threat`, `Codex.Gear`, the reader, `CodexKnowledgeTest.combat`.
- [x] `Fight` and the Policy order.
- [x] `FightPolicyTest`.
- [x] Rules rows and the Brain's Rules index.
- [ ] Smoke direction check (the parent's): turns survived, deaths and deepest floor against story 4.6.

**Acceptance Criteria:**
- Given a room-and-corridor map, then the Policy prefers the corridor
  (`FightPolicyTest.corridor_preference`).
- It uses the Codex threat tables to decide whether to fight or retreat (`attacks_when_favourable`,
  `retreats_when_not`, `hurt_retreats`, `hit_chance`).
- It never targets a character absent from the Observation (`never_an_absent_target`).
- It accounts for the boss-floor seal (`sealed_floor`).
- The PR carries a smoke-set direction check.

## Design Notes

**Constraints restated:**
- Non-negotiable #1: the threat estimate reads only what the screen shows (names, health bars,
  the hero's level and hit points, the names and visible upgrades of what it wears) and the Codex.
- Non-negotiable #4: the estimate is an abstract model from Observations, not a second
  implementation of combat. It uses the game's hit rule and means, not a simulation.
- Non-negotiable #8: every claim cites a Rule.

**How the Codex's figures reach the Brain:**
1. The full typed tables (`Codex.MobEntry`, `Codex.Combat`), with a reader for each of their
   records. Rejected, for the reason story 4.2 gave: a reader for dozens of record types, and a
   Brain carrying far more than it reasons from.
2. A hand-written table in `brain`. Rejected: the Codex already measured these figures from the
   pinned code, and a copy would drift.
3. **Two small records on `Codex.Knowledge`, read by the rig: `Threat` by display name, `Gear` by
   display name and level.** Chosen. It follows story 4.2's pattern, and the screen names actors
   and items by display name, which is what the strings table and the items table give.

**How to decide fight or flight:**
1. Always attack an adjacent enemy. Rejected: that is roughly the random fallback's behaviour
   without the randomness, and it is what dies.
2. A search over future turns with the real engine (§4's redetermination). Rejected as far beyond
   this story: that is E6's tactical search.
3. **A closed-form estimate: turns to kill the target against the damage expected from every enemy
   that can engage meanwhile, with a margin of half the hero's hit points.** Chosen. It is cheap,
   testable without a game, and a later story can tune it through the Evaluation's weights.

**Where to fight:**
1. Compute a full "best cell on the floor". Rejected: the Brain acts one step per wait, and a plan
   across many steps is what the next wait recomputes anyway.
2. **Look one step ahead: the neighbouring cell fewest enemies can engage.** Chosen. A doorway
   into a corridor is found within one step, which is where the fight usually starts.
3. Ignore terrain. Rejected: the acceptance criterion asks for the corridor.

**Pre-mortem:**
- *The estimate is wrong for many enemies.* Several enemies' figures are computed at run time and
  are left out of the table, and so is every enemy the strings table names nothing for. The Policy
  fights an enemy it has no figures for, rather than flee from everything it does not know. The
  mean-based estimate ignores special attacks, ranged enemies and speed. The margin (half the hit
  points) is an assumption, stated in `Fight.MARGIN_PER_MILLE`.
- *The hero's accuracy and evasion ignore the weapon's accuracy factor, the armour's evasion
  factor, rings and talents.* Stated in `Fight.favourable`; the screen shows the level, which is
  what the estimate uses.
- *Retreating from an enemy of the same speed buys nothing but distance to stairs.* Hence the stairs
  first while the floor is open, and a fight when cornered.
- *Holding a corridor against a ranged enemy is waiting to be shot.* A known gap for a later story:
  the Codex does not yet tell the Brain which enemies shoot.
- *Waiting in a corridor while enemies wander elsewhere could stall.* The fight Policy enters only
  while an enemy is in view, so explore resumes when none is.
