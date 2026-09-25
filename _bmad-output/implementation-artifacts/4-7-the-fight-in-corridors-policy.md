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

## Review

**Fairness reviewer: PASS.** Should-fixes, all done:
- `FightPolicyTest.attack_only_shown`: an Attack offered on a cell no shown enemy stands on is never ranked.
- `faint_enemy`: a rat drawn faint is fought as drawn.
- A shared display name: the table keeps the first class with fixed figures, as `Codex.Threat` says.
  `CodexKnowledgeTest.distinct_names` shows no two enemy classes share one at v4.0.0, and that the
  hand-named passive enemies are Codex names.
- The bare-hands javadoc names `Hero.heroDamageIntRange` (`RingOfForce.java:103-106`).

**Lens review: 14 findings, all fixed.** Each was checked against the pinned code first.

1. **Stairs.** The retreat took any ascent the screen offered. At depth 1 that is the surface, which
   only opens a window (`SewerLevel.java:146-156`; `EntranceRoom.java:92-96`), and a branch's stairs
   can refuse. The retreat now uses the regular stairs only (`stairs`).
2. **Stairs ping-pong.** Explore walked straight back down to a floor just fled. The Memory now counts
   flights per floor. On the floor above one fled, explore rests to full health before anything else
   (`no_stairs_ping_pong`), and a floor fled twice is not fled by the stairs again.
3. **Refusals and searches were misread (for stories 4.6 and 4.8 alike).** Two attacks from one cell
   looked like a refused Step, and a stationary Action looked like a search. The Memory (version 4)
   now keeps the kind of the last Action handed over (`Brain.handed`, called by `BrainDecider`):
   - the stuck streak counts only Steps on calm screens;
   - a searched spot counts only after a Search.

   This is generic, keyed on the Action's kind (`attacks_are_not_refusals`). It is the Brain's own
   output, read only to interpret stillness, never as a fact that the Action was applied; `Memory`'s
   javadoc says so. `ReplanAfterForeignActionTest` now compares what two Brains believe
   (`Brain.beliefs`) rather than their bytes, which differ by the kind handed over.
4. **Chokepoint against approach oscillation.** A chokepoint is now taken, and held, only while the
   enemies are closing in, as measured from the hero's current cell to where they stood a wait ago,
   so the hero's own steps do not count. It must admit at most two enemies. Unfavourable fights
   retreat before anything else (`no_chokepoint_oscillation`).
5. **Holding forever.** A hold lasts only while the enemies close in, at most `HOLDS` = 4 waits in a
   row (`bounded_hold`). Enemies the Codex marks IMMOVABLE are not a crowd to wait for
   (`immovable_is_not_waited_for`).
6. **Explore and fight ping-pong at the edge of view.** A retreat by the fight Policy records a region
   around the nearest enemy, one past where it was seen from, for 100 waits. The explore Policy steps
   out of that region and keeps out of it (`retreat_is_remembered`).
7. **Enemies without figures were always beaten.** An enemy the Codex has no figures for now takes a
   pessimistic figure scaled by depth (`Fight.assumed`, an assumption stated as one). Fighting is
   never favourable while the status pane warns of low health (`unknown_enemy`, `hurt_retreats`).
8. **Gear by name.** The weapon and armour are found by the measured name the shown name contains, so
   enchantments and glyphs no longer hide them. A mage's staff maps to the mage's staff. Anything
   worn that the Codex cannot name is the weakest measured item, not bare hands (`gear_names`).
9. **Mean-of-difference.** `E[max(0, X - Y)]` is now exact over the `NormalIntRange` distributions
   (`Random.java:138-140`) (`arithmetic`).
10. **Enemies with their own evasion rule.** Custom-defence enemies (great crab, monks, crystal
    guardian) are left out of the table, so they take the pessimistic figure.
11. **Passive enemies.** The animated and armored statues and the gnoll exile are scenery for both
    Policies (`passive_statue`). Brain Rules index row 17 is corrected and row 25 added.
12. **The stairs path.** It no longer runs beside an enemy, and a first Step never closes on one
    (`stairs_path_keeps_clear`).
13. **Entering and returning nothing.** Whenever the fight Policy enters, it ends its ranking with a
    turn in place (`always_acts`).
14. **The hero's own cell.** The breadth-first search marks it visited.

The test nit is fixed: the row the old test built and overwrote is gone. The fallback's attack
feature is now held on the Evaluation directly (`EvaluationMonotonicityTest`), since the fight Policy
takes every screen with an enemy in view.

**Codex records** (for the union with story 4.8):
- `Codex.Threat(name, ht, attack, defense, damageMin, damageMax, drMin, drMax)`.
- `Codex.Gear(name, level, min, max, meanPerMille)`.
- `Codex.Knowledge` adds `threats`, `weapons`, `armours` and `immovable` (a list of display names),
  with `threat(name)`. Story 4.2's four-argument constructor is kept.

**Docs:**
- `docs/rules/combat.md`: three new rows (`NormalIntRange`, the passive statues and exile, gear names).
- `docs/rules/levels.md`: the surface-entrance row.
- `docs/brain-rules.md`: rows 16 to 28.
