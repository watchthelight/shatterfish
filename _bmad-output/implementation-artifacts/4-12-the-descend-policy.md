---
title: 'Story 4.12: The descend Policy'
type: 'feature'
created: '2026-09-25'
status: 'review'
baseline_commit: '3e3fea87d'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 4.6 pulled forward a minimal descent: the explore Policy goes down only once the
floor is exhausted, whatever state the hero is in. Measured on `smoke` with story 4.9's Brain, heroes
go down the planned way rarely (4 descents in 25 Runs) and at a third to half of their health (an
in-process probe logged 34 descents at a median near half health). Most floors are left instead by the
fight Policy fleeing down the stairs mid-fight (32 of 36 descents), which drops a hurt hero onto a
harder floor. And a floor that has stopped paying keeps the hero as long as its frontier lasts: some
Runs spent 1,600 to 2,000 waits on depth 1 (FR-31).

**Approach:** A `descend` Policy owns the way down. It weighs the floor's remaining value against the
risk of staying, using the explored fraction, the guaranteed drops the set of floors still owes, and
hunger, and leaves when the value no longer covers the risk. It never goes down while the floor is
sealed, and it rests on the exit to full health before it goes down, when resting is possible. The
explore Policy keeps uncovering the floor and no longer descends.

## Boundaries & Constraints

**Always:**
- One Action per Input wait, always one the screen offers.
- Screen data and Beliefs only: explored fraction from the map and fog, guaranteed drops from the
  Beliefs' chapters (story 4.2), hunger from the icon, health from the HUD.
- Every mechanics claim has a row in `docs/brain-rules.md` resting on a cited Rule.
- The PR reports depth beside the survival curve, so a Brain that dives is seen to dive.

**Ask First:** anything that reads what earlier floors held and the hero missed, which the screen
never showed.

**Never:**
- Going down while the header says the floor is sealed.
- Reading game code from `brain`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Floor spent, hero away from the exit | no reachable frontier, searches spent | Step toward the exit, "exit: spent n" | N/A |
| On the exit, hurt, fed | hp below HT, no hunger icon | Rest, "rest: descent" | bounded by `RESTS` |
| On the exit, full health | hp = HT | Descend, "descend: spent" | N/A |
| On the exit, hurt, hungry or starving | hunger icon shows | Descend without resting | N/A |
| Frontier left, hungry, no food held | hungry icon, no known food | Step toward the exit, "exit: hungry n" | N/A |
| Frontier left, hungry, food held | hungry icon, a ration | `eat` takes the wait | N/A |
| Frontier left, overstayed | waits on the floor at or past the allowance | Step toward the exit, "exit: overstayed n" | N/A |
| Frontier left, within the allowance | fed, early on the floor | `explore` takes the wait | N/A |
| Sealed floor | header `sealed` | does not enter | N/A |
| Locked exit | the boss floor's exit tile is `LOCKED_EXIT` | no way down; later Policies act | N/A |
| Step toward the exit refused twice | hero still after two Steps | yields once; the cell is blocked; goes round | N/A |
| Enemy in view or Prompt open | not calm | does not enter | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/brain/.../Descend.java`: the Policy: `leaving` (spent, hungry, overstayed),
  `allowance` and `expectedHere`, the walk to the exit, the rest on it, the Descend.
- `shatterfish/brain/.../Explore.java`: the minimal descent removed; `spent(observation, memory)`
  says when nothing is left to uncover; `nearest` and `Path` shared with `Descend`.
- `shatterfish/brain/.../Memory.java`: `arrived`, the wait the hero came to this floor, and `rests`,
  the rests the descend Policy handed over on it (VERSION 8 after merging story 4.10); story 4.10's
  constructor kept.
- `shatterfish/brain/.../Beliefs.java`: the fold starts `arrived` and `rests` afresh on a floor first
  seen; a refused Step toward the exit is blocked like explore's.
- `shatterfish/brain/.../Brain.java`: the Policy order
  `answer-prompt, heal, fight, eat, test-item, pick-up, equip, descend, explore, fallback`; `handed`
  counts a rest the descend Policy handed over.
- `shatterfish/brain/src/test/.../DescendPolicyTest.java`, `ExplorePolicyTest.java`,
  `PolicyArbitrationTest.java`; `shatterfish/rig/.../ShatterfishRunTest.java`.
- `docs/brain-rules.md`: row 14 now used by `descend`; rows 52 to 55.
- `docs/rules/combat.md`: the respawn row re-read at v4.0.0 and raised to Tier 1.
- `docs/architecture.md`, `docs/fairness.md`, `docs/ideas.md`.

## Tasks & Acceptance

**Execution:**
- [x] `Descend`, `Explore.spent`, `Memory.arrived`/`rests`, and `DescendPolicyTest`.
- [x] The Policy order, `PolicyArbitrationTest`, `ShatterfishRunTest`.
- [x] Rules rows and the Brain's Rules index.
- [x] Smoke direction check with deaths by depth and the survival curve.
- [x] The heal investigation.
- [x] Mutation battery.

**Acceptance Criteria:**
- The bot descends when the floor's remaining value falls below the risk of staying, using the
  explored fraction (`DescendPolicyTest.rest_before_descent`, `ExplorePolicyTest.spent_when_done`),
  the remaining guaranteed drops (`DescendPolicyTest.allowance`, `overstayed`) and hunger
  (`DescendPolicyTest.hungry_without_food`, `no_rest_when_hungry`).
- It does not attempt to descend while the floor is sealed (`DescendPolicyTest.sealed`,
  `ExplorePolicyTest.spent_when_done`), nor through a locked exit (`DescendPolicyTest.locked_exit`).
- It does not loop: rests are bounded (`rests_are_bounded`, `rests_counted_through_the_brain`), and a
  refused Step is blocked and gone round (`refused_step`).
- The PR reports depth beside the survival curve, so it does not reward diving.
- The PR carries a smoke-set direction check.

## Design Notes

**Constraints restated:**
- Non-negotiable #1. What earlier floors held and the hero missed was never on the screen; the
  Policy counts only what the Beliefs say was found. Hunger comes from the icon, the way down from
  the drawn exit and the header's sealed flag.
- Non-negotiable #8. The food placed per floor, the guaranteed drops, the respawn clock,
  regeneration and the seal are cited Rules (rows 43 to 46 of this story, 52 to 55 after the merge).

**Where the descent lives:**
1. Keep it in `explore` and add conditions there. Rejected: `explore` would then own two goals, and
   a floor with frontier left could not be left without also teaching `explore` to stop exploring.
2. Make leaving a fight Policy decision. Rejected: the fight Policy acts only with an enemy in view.
3. **A `descend` Policy between equip and explore, on calm screens; `explore` says only when the
   floor is spent.** Chosen. Every Policy above it (heal, fight, eat, test-item, pick-up, equip)
   finishes what it is doing first; then either the floor is left, or `explore` uncovers more.

**When to leave:**
1. As soon as the exit is known. Rejected: it dives, skips the floor's items and guaranteed drops,
   and reaches harder floors weaker; the acceptance criterion forbids rewarding diving.
2. Only when the floor is spent (story 4.6's rule). Rejected alone: a hungry hero with no food then
   starves looking for the last corner, and a floor that has stopped paying holds the hero for
   thousands of waits while enemies respawn (depth 1 took 1,600 to 2,000 waits in some Runs).
3. **Spent, or hungry with no food, or past an allowance that grows with the guaranteed drops still
   expected on the floor.** Chosen. The allowance, 500 waits and 250 more per expected drop, is an
   assumption to be tuned by the rig, not a measurement (docs/ideas.md).

**The state it goes down in:** the probe below found heroes going down at a third to half health.
Three ways: go down anyway; rest wherever the hero stands; **walk to the exit and rest on it to full
health, fed, at most 20 rests a floor.** Chosen: the stairs underfoot are the fight Policy's way out
if an enemy comes during the rest, and the rest is bounded so a rest the game keeps cutting short
does not hold the hero at the exit.

**Pre-mortem:**
- *It dives.* Only the three reasons above leave a floor; the direction check reports deaths by depth
  and the survival curve beside the mean depth.
- *It never leaves.* The allowance bounds a floor with frontier left; `Explore.spent` covers the
  rest; a refused Step toward the exit is blocked and gone round (`refused_step`).
- *It rests forever on the exit.* Bounded by `RESTS`; never while hungry or starving; the rest is
  `Rest(true)`, which the game ends at full health or on any interruption.
- *A boss floor.* Sealed floors are refused outright, and the boss floor's locked exit is no exit
  until its key opens it.
- *Most descents are still flights.* The fight Policy's retreat down the stairs lands a hurt hero
  on a harder floor. An experiment fleeing only upward measured worse (below), so it is kept, and
  written up in docs/ideas.md.

## Review

**Implementation notes:**
- **Policy order** after merging story 4.10: `answer-prompt, heal, fight, eat, test-item, pick-up,
  equip, descend, explore, fallback`. test-item's escape from its own gas and its rest after a test
  come before leaving the floor; `Explore.spent` plans around remembered clouds and avoided regions
  first, then through them, as `explore` itself does.
- **Memory v8** (after story 4.10's v7): 31 fields, then `long arrived` (the wait the hero was first
  seen on this floor) and `int rests` (the descend Policy's rests handed over on it). Bytes: after
  `refuge`, `number(arrived)` then `integer(rests)`; the reader mirrors it. Story 4.10's 31-argument
  constructor defaults both to 0. `Beliefs.fold` starts both afresh on a floor first seen;
  `Brain.handed` counts a rest the descend Policy handed over (`Memory.resting`).
- **Stuck rule:** when two Steps are refused, the cell blocked is test-item's Step, else pick-up's,
  else descend's Step toward the exit, else explore's.
- **Docs:** `docs/rules/combat.md`'s respawn row re-read at v4.0.0 and raised to Tier 1 (it was
  needs-review); `docs/brain-rules.md` row 14 now used by `descend`, rows 52 to 55 new;
  `docs/architecture.md`, `docs/fairness.md`, `docs/ideas.md`.

**Tests:** `:brain:test` passes (`DescendPolicyTest` 12 cases, several over many waits through the
Brain; `ExplorePolicyTest.spent_when_done` replaces `down_when_done`); rig `BrainRulesIndexTest`,
`ShatterfishRunTest` (explore no longer descends; descend's reasons are `exit: why n`, `rest: descent`
and `descend: why`), `StrategyLogTest`, `StarvationRegressionTest`. `:codex:citations`: see below.

**Mutation battery:** 17 of 17 killed. One survived at first ("always fed": any item counts as food),
because the tests held either food or nothing; `hungry_without_food` now holds a scroll alone and
expects the hero to leave, and a pasty alone and expects it to stay.

**Direction check** (`smoke`, 25 triples, salts 1000+i, `RunOne`), main at story 4.10 (`71a9fa8f6`)
against this branch (`e50be7f98`):

| | main (4.10) | 4.12 |
|---|---|---|
| turns survived, median (mean) | 863 (915) | **892** (910) |
| turns survived, quartiles | 391 / 862 / 1352 | **497** / 891 / 1288 |
| Runs alive at 500 / 750 / 1,000 / 1,500 turns | 18 / 15 / 11 / 4 | 19 / 16 / 10 / 5 |
| deepest floor, mean (max) | 2.32 (4) | **2.44** (4) |
| deaths by depth (1 / 2 / 3 / 4) | 4 / 11 / 8 / 2 | 3 / 11 / 8 / 3 |
| descents per Run | 1.32 | 1.56 |
| score, mean | 917.5 | 901.6 |
| endings | 25 deaths | 25 deaths |

- **Depth and survival together.** The mean depth rises with the lower quartile of survival: fewer
  Runs die early, one more reaches depth 4, and the upper quartile falls a little (Runs that used to
  linger on a floor now move on). Score is flat within noise.
- **The descend Policy takes 3.2% of waits**: mostly walking to the exit of a spent floor (462 of 574
  waits), then hungry with no food (66), then overstayed (23); 9 rests on the exit.
- **Most descents are still flights.** 25 of 39 descents are the fight Policy's retreat down the
  stairs. A variant retreating only upward measured worse: mean deepest floor 2.08, score 803, and one
  Run livelocked on a refused retreat Step (docs/ideas.md). Kept as it is.

**The heal investigation** (an in-process probe over the 25 `smoke` triples, salts 1000+i, on this
Brain before the merge with story 4.10; not committed):
- 0 of 25 Runs ended holding a potion the screen showed identified as a potion of healing.
- The heal Policy entered holding one on 41 screens, all in one Run (a Warrior on depth 4): 39 had
  the hit points above the danger, 1 yielded to the fight Policy's retreat, and 1 drank.
- The Runs die cornered at 1 to 5 hit points with no known potion held.
- So heal is not gated too hard; its input is rare. The Warrior knows the potion from the start but
  the first floors seldom hold one, and the other classes must identify it first (story 4.10, which
  tests unknown potions only when hurt, likely to heal, and calm). No change to `Heal`.

**Deferred** (docs/ideas.md): the refused-Step loop in a fight; which way to flee; tuning the
allowance with the rig.

**Review round** (fairness: no violation; lens review: 6 findings, 3 serious; all fixed with tests
that drive real waits through the Brain):
1. *Critical: the rest before going down was dead code.* The last Step onto the exit travels (a click
   on a transition cell with no enemy in view, Hero.java:2000-2007), so a hero walking to the exit
   went down at whatever health it had; the rests measured were arrivals from below. Now the Policy
   stops on the cell beside the exit and rests there to full, then takes the Step; resting on the
   exit is kept for a hero come up from below. `rests_beside_the_exit` walks a corridor through the
   Brain and asserts the rests come before the final Step; `rests_beside_are_bounded`.
2. *High: hungry could lead into a boss floor.* A boss floor places no food (Level.java:224-226,
   Dungeon.java:441-443) and Goo seals the floor on waking (Goo.java:134-136). "Hungry" now needs no
   food held, no frontier left (this floor's own food lies in the part still to uncover) and a
   regular floor below (`Descend.bossNext`). A hungry hero rests, since it regenerates; only a
   starving one does not (Regeneration.java:56). `hungry_without_food`, `no_rest_when_starving`.
3. *Medium: fleeing down the stairs hurt.* The fight Policy's retreat never takes the stairs down
   onto a boss floor, nor while the hero is hurt and the descend Policy is taking it down
   (`Fight.down`); it takes the stairs up or steps away instead. `never_down_hurt_or_onto_a_boss`.
4. *Medium: descend outranked story 4.7's safety plans.* It stands aside while the explore Policy
   owes a rest before going back to a fled floor, and while the hero is inside an avoided region with
   a Step farther out (`Explore.away`, pulled out of explore's plan). `yields_to_the_fight_plans`.
5. *Low-medium: an exit never seen.* Exit rooms join by regular doors, never locked ones
   (ExitRoom.java:60), but a regular door may be hidden, and on a secrets floor a room may be reached
   only through hidden doors (RegularPainter.java:221-263). With a reason to leave and no exit on the
   screen, the Policy runs explore's frontier and search plan past its budget, up to 36 spots, as
   "no exit: ...": never a Step into the fog. `exit_never_seen`.
6. *Low:*
   - A refused Step is now credited to the Step the Brain handed over, whatever Policy chose it: the
     Memory keeps `stepped`, the last handed Step's cell. The streak counts a still hero after a Step
     on any screen, not only a calm one, so a Step the game refuses in a fight is blocked after two
     tries instead of being handed over forever with no time passing (seen as a 628,000-wait livelock
     in an experiment; `refused_step_in_a_fight`). Stories 4.8 and 4.10 recomputed the refused cell
     from their own plans; that is replaced.
   - The descend Policy yields at `streak == STUCK - 1`, as explore does.
   - An appearance of a drop's family picked up and not yet identified (`Memory.pending`) counts as
     found in the allowance, so depth 4 does not wait 1,750 waits for a potion already in the pack.
   - The docs say the allowance counts waits.
- Fairness should-fixes: `exit_never_seen` covers a spent and an overstayed floor with no exit shown;
  the `Level.locked` citation is :181.

**Memory v8, final:** story 4.10's 31 fields, then `long arrived`, `int rests`, `int stepped`. Bytes
after `refuge`: `number(arrived)`, `integer(rests)`, `integer(stepped)`. `Memory.handed(kind, stepped)`.

**Tests:** `:brain:test` passes (`DescendPolicyTest` 16 cases, `FightPolicyTest` 30); rig
`BrainRulesIndexTest`, `ShatterfishRunTest`, `StrategyLogTest`, `StarvationRegressionTest`,
`DocsCitationTest`; `:codex:citations` no findings. One 4.7 test changed its words: the explore
Policy's own rest still stops for hunger; the descend Policy's rest beside the exit does not.

**Mutation battery (review round):** 24 of 24 killed, covering the rest beside the exit, the hungry
rule's three conditions, starving and hungry rests, the rest bound, both yields, the search for an
exit never seen, pending drops, the boss floor's drops, the locked exit, both of the retreat's
down-stairs rules, the streak on any screen, the blocked Step, the Memory's `stepped` and its codec,
arrival and rests.

**Direction check after the review round** (`smoke`, salts 1000+i), main at story 4.10 against this
branch:

| | main (4.10) | 4.12 |
|---|---|---|
| turns survived, median (mean) | 863 (915) | **1,044** (968) |
| turns survived, quartiles | 391 / 862 / 1352 | **693** / 1044 / 1268 |
| Runs alive at 500 / 750 / 1,000 / 1,500 turns | 18 / 15 / 11 / 4 | 21 / 18 / 15 / 3 |
| deepest floor, mean (max) | 2.32 (4) | **2.52** (4) |
| deaths by depth (1 / 2 / 3 / 4) | 4 / 11 / 8 / 2 | 3 / 10 / 8 / 4 |
| score, mean | 917.5 | **1,041.1** |
| endings | 25 deaths | 25 deaths |

- Survival and depth rise together: the lower quartile of turns survived goes from 391 to 693, four
  Runs die on depth 4 instead of two, and fewer die on depths 1 and 2.
- The descend Policy takes 7.9% of waits: walking to the exit of a spent floor (765), exploring or
  searching for an exit not yet seen (about 700 across the "no exit" plans), overstayed (46), resting
  beside or on the exit (18).
- 32 of the Runs' floor changes are still the fight Policy's flights down the stairs, now never onto
  a boss floor and never hurt while the descend Policy was taking the hero down.
