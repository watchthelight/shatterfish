---
status: accepted
date: 2026-09-04
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0010: Abstract tactical model versus engine rollouts, deferred to E6 with criteria

## Context and problem statement

Tactical Search (PRD FR-34, E6) may use an abstract tactical model derived from the Observation
and Belief, or engine rollouts on redetermined snapshots (ADR-0009); rollouts on hidden state are
forbidden (non-negotiable #1). The research report found that chess search does not transfer, that
the NetHack symbolic champions used no search, and that Long et al.'s properties predict whether
perfect-information Monte Carlo search helps a game, none of which have been measured for SPD
(research §4, recommendation 6). Two of their three properties transfer: leaf correlation and the
disambiguation factor. The third, bias, is defined in that work as the probability that the game
favours one player over the other, which has no meaning in a single-player game, so it is not
measured here. The bootstrap allows this decision to be
deferred to E6 with criteria. Decide what E4 and E6 build first and what measurement chooses the
rest, without promising a number before it is measured (PRD NFR-3's discipline).

Non-negotiables touched: #1 (parity), #5 (measured), #8 (Codex over folklore).

## Decision drivers

- The Rig, not intuition, decides whether a search helps (non-negotiable #5).
- Fairness must hold for whichever design wins (FR-13).
- The Overlay's per-Decision budget is wall-clock; the Rig's is throughput; both must be honored.
- Item identification belongs to Belief reasoning, not to search (research §4).

## Considered options

1. **No search: scripted Policies with `safeTest` (E4 baseline).** Chosen as the starting point;
   it is what the Goo gate is measured on (SM-3).
2. **One-ply expectimax over Codex tables for fights only: enumerate the hero's abstract actions
   (attack, step, retreat, use a known consumable), score each against every visible enemy's
   expected damage and hit chance from the Codex's measured tables, pick the best.** Chosen as
   the first E6 story; it needs no engine, is fair by construction (Codex plus Observation), and
   gives the Rig a second Brain to compare.
3. Depth-limited sampled search over redetermined engine rollouts (POMCP-style, horizon 2 to 4
   hero turns, a split of the budget between determinizations and rollouts per determinization).
   Deferred: chosen only if the criteria below pass.
4. Information-set MCTS (one tree over information sets). Deferred with 3; the same criteria,
   compared against 3 under the Rig.
5. Hybrid: abstract model for the tree, one engine rollout at each leaf. Deferred; considered
   only if 3 fails the speed criterion but passes the property criteria.
6. Learned evaluation inside the search. Out of scope for v1 (optional E9).

## Decision outcome

E4 ships option 1. E6 begins with option 2 and then runs three measurements on the E1 Harness,
each published as a Results page before the choice is made:

| Measurement | How | What it decides |
|---|---|---|
| Simulator speed | Redetermined rollouts per second per process under the swap-in-place host of ADR-0009, and, if the E1 spike succeeded, under classloader isolation; measured on the `smoke` set's mid-fight snapshots | whether option 3 can fit the Overlay's budget and the Rig's throughput at all |
| Long et al. properties | Leaf correlation and the disambiguation factor, computed with random playouts from the same snapshots, following the recipe in research §4; bias is not measured, being a two-player quantity | whether sampled search is expected to beat the one-ply model; low disambiguation means determinizations mislead |
| Search leak test | FR-13: replacing the true hidden state with random alternates produces identical Decisions over the `smoke` snapshots | whether the scrub of ADR-0009 is complete |

Choice rule, in order:

1. If the search leak test fails, no engine-rollout design ships until it passes; option 2 stays.
2. Among the designs that fit the budget (a design fits when its median Decision time at the
   registered budget completes the search it was configured for on the described machine; the
   budget itself is a Registration field, not a constant in this ADR), the Rig decides: each
   candidate is registered against option 2 on the `standard` set under the Per-pair GSPRT
   (ADR-0012) at equal wall-clock budget per Decision; a candidate ships only on acceptance.
3. If no candidate is accepted, option 2 remains the tactical layer and the strategy epic (E7)
   proceeds; the measurements stay published so the question can be reopened with a better
   simulator or a new tag.
4. Long et al.'s properties are reported beside every candidate's result; they are explanatory,
   not a gate, because the Rig's acceptance is the only number the program trusts.

Fairness rule for any winner: the search consumes `Observation` and `Belief` values and asks for
rollouts through the `api` `Simulator` (an abstract class since story 1.20, its final `simulate` refusing an unscrubbed handle) with an opaque `SnapshotHandle`; the snapshot
bytes stay inside `harness` behind `Redeterminer` (ADR-0009); the Brain never holds a game object
or anything it could inflate into one (AD-7, AD-9).

### Consequences

- Good: no design is promised before the Rig accepts it, and the measurements exist whatever the
  outcome.
- Good: E4 and the first E6 story need no engine rollouts, so E6 can start before the isolation
  spike is resolved.
- Bad: two epics of work may end with option 2 as the answer; the published measurements are the
  value in that case.
- Bad: "equal wall-clock budget" favors cheaper designs on a slow machine; the Registration records
  the machine and the budget, and a later re-run on better hardware is a new Registration.

## Pre-mortem

*If this is wrong in six months, why?*

- Option 2's abstract action set is too coarse to represent what the game allows (positioning,
  doors, terrain), so it loses to option 1 and the comparison baseline is weak. Mitigation: the
  action set is the Observation's valid-Action set filtered to fights, not a hand-picked list.
- The simulator-speed measurement is dominated by snapshot cost, not the rules, and hides a
  viable design. Mitigation: report snapshot cost and rollout cost separately; the classloader
  alternative removes the swap.
- Long et al.'s properties were derived for two-player zero-sum games with imperfect
  information, and SPD is single-player and stochastic, so the properties may not predict what
  they predict there. Mitigation: they are explanatory only; the choice rule never gates on them.
- The Rig cannot resolve a small tactical gain on `standard` at 500 seeds. Mitigation: ADR-0012's
  burn-in and undecided outcome are honest; the `bosses` set targets fights specifically.

## Amendment: story 1.21 (2026-09-16)

Two of the three measurements above have a first reading before E6, on the E1 harness with the
random agent, published at `docs/results/e1-throughput.md`: leaf correlation 0.877 against a
chance agreement of 0.876 at the observed survival (kappa 0.010.), and a disambiguation
factor of 0.000 with the hidden facts and the revealed share given by kind. Both are defined
on that page and measured with random playouts from snapshots (story 1.20) to a horizon of twenty
waits, with the hidden facts counted through the oracle sidecar (story 1.18) from the launcher
under `--oracle`, the one place that may construct it and the one switch that marks it. The
playouts are perfect-information rollouts on live snapshots by design and not a Simulator; they
are the method, not the search. Bias is not measured, as decided.

Neither reading is on the scale the table above compares to, and neither is placed on it. The
leaf correlation is the survival rate's: at a random floor-one wait the hero is nearly always
alive twenty waits on whichever sibling was chosen, so siblings agree by chance and the kappa is
about nothing. The disambiguation factor is the random agent's floor: nothing revealed in twenty
random waits, over surviving playouts, with a denominator the Run-wide unknown appearances
dominate. The readings validate the method and its definitions, and the definitions are what E6
inherits, with two changes it must make before its numbers are compared with these: the
denominator is the facts reachable on the floor of the sample rather than the Run's whole set
of unknown appearances, and the count is taken at a death as well as at the horizon, since on a
floor the events that reveal a fact are the ones that kill. The simulator speed and the search
leak test stay E6's, since no rollout host and no Search exist; when they do, the same page's
method is rerun from the `smoke` set's mid-fight snapshots with a Brain, where survival is
uncertain and the agreement of siblings means something. The harness's own rate, about 399.5
Input waits per second per process on the described machine over three invocations, is the
number the Rig's seed-set sizes (E3) rest on.

## Amendment: story 6.3 (2026-10-26)

Choice rule 1 now has its instrument. `SearchLeakTest` (harness test source set) scrubs each mid-fight snapshot of the
`smoke` set into alternate worlds that show the player the same Observation, runs every search the program has in the
original and in each alternate, and requires the Decisions to be the same canonical bytes. The searches are the one-ply
model and a probe search that rolls out through the `Simulator`; the probe is a test fixture, and a real candidate joins
the test by implementing the same small `Search` seam. A planted leak in each of several forms is caught (a forged handle, a
read of the true seed, a read of an unseen mob, of unidentified classes, of hidden traps and doors, a refusal that tracks the
truth, and a scrubber missing a row), so a pass is not the test's inability to fail. The missing-row control is narrow: a
three-turn rollout from a fight reads two rows of the thirteen on every layout (`MOB_AI_AND_HP`, `MOBS_OUT_OF_VIEW`) and
two more on some, so a search with a longer line adds a control for each row it reads.

What a pass means, and does not: the Decisions do not depend on the hidden state the scrub table lets the test vary, over
the snapshots and the rollout length it uses. A hidden element outside ADR-0009's table is outside the proof, and so are
snapshots the scrub refuses (the gates in `Scrub` (Shatterfish code, `harness/redetermine`, lines 155 to 204): a boss floor, a taken quest, a held trinket, a mimic, a quest giver or a
chest in view). The Run's salt and the generator are one value across the worlds of one driver, so this test cannot see a
search read them; `RedeterminationDifferentialTest` holds those rows. The one-ply half is a regression guard (its input is
the Observation, whose hash the test asserts equal), and the probe half is the search proof. It is a necessary condition for a design to ship under rule 1 and not a sufficient one; the
fairness reviewer reads every search diff. A refusal of the scrub is never evidence: a search treats it as "no sample",
and the probe records the count of refusals in its Decision so that a refusal which tracks the truth is a failure here.
The result on the `smoke` set is at `docs/results/2026-10-26-6-3-the-search-leak-test.md`.

## Amendment: story 6.4 (2026-10-26)

The simulator-speed measurement now has its first reading, on the swap-in-place host of ADR-0009, from the `smoke` set's
mid-fight snapshots: [docs/results/2026-10-26-simulator-speed.md](../results/2026-10-26-simulator-speed.md). About 21
redetermined rollouts a second per process on a lightly loaded machine (about 15 on a busy one) for lines of two to four Actions.
The pre-mortem above happened in the direction it feared: rolling the engine forward costs 1.9 to 3.3 ms and the swap (snapshot,
load, restore) 40.9 ms, 87% of a rollout, so the measurement is dominated by snapshot cost and the page reports the two apart.
No budget is chosen here and none was set before the measurement: no Registration constrains `budgetMs` yet, the page gives the
rollouts and determinizations that fit from 10 ms to 5 s, and the answer is none below about 47 ms. The classloader-isolation
row is not reported (the E1 spike was not completed; the JNI note in ADR-0009 explains what it needs first). Whether a design
at that rate beats the one-ply expectimax is for the properties measurement (6.5) and the candidate comparison (6.6) to say.

## Amendment: story 6.5 (2026-10-26)

The properties measurement now has its second reading, by the method of the story 1.21 amendment with its two changes
(the floor's facts as the disambiguation denominator, the count at a death as well as at the horizon), from the `smoke` set's
mid-fight snapshots, with the random agent, the baseline Brain and the one-ply model driving:
[docs/results/2026-10-26-6-5-the-properties-measurement.md](../results/2026-10-26-6-5-the-properties-measurement.md). With a Brain
driving, survival at 20 waits is 0.95 to 0.97, the leaf correlation 0.95 to 0.98 against a chance agreement of 0.90 to 0.95, and
the disambiguation factor 0.07 (0.02 at the 4-wait tactical horizon); the random agent's 0.67 and 0.25 are not the numbers a
search would meet. A Brain's kappa on alive or dead has an interval that includes zero, so the page adds a second payoff (alive
with at least half the hero's health) on which the kappa is 0.6 to 0.8. A second slice (the first 25 `standard` Runs) reads the
same in shape. The page explains what each predicts for sampled search in the terms of Long et al. and labels that explanatory.
Bias is still not measured (a two-player quantity). No choice was made from these readings, and none follows from them:
rule 4 puts the two properties beside a candidate, and story 6.6 owns the candidate.

The hidden facts were counted through the oracle sidecar behind `Launcher.hiddenFacts`, which refuses without `--oracle` (a convention the ArchUnit rules back); the
measurement is a test-source tool, no driver or search is handed a fact, and `RigOracleGateTest` and `OverlayOracleGateTest`
keep the Rig and the Overlay from asking.

## Amendment: story 6.6 (2026-10-26)

The choice rule was applied to the measurements of stories 6.3 to 6.5 and the answer is option 2: **the one-ply expectimax
remains the tactical layer.** The record is at
[docs/results/2026-10-26-6-6-the-candidate-comparison.md](../results/2026-10-26-6-6-the-candidate-comparison.md).

Rule 1 was checked first: the only rollout search the program has, the probe, passes `SearchLeakTest`; options 3, 4 and 5 have
no code and none was registered. Rule 2 needs a registered budget. The Registration that would have been made sets
`budgetMs` by the Rig's throughput: a Run is about 15.2 s and the fight policy makes 394.8 of its Decisions (counted from 20 Run logs), so a search that
at most doubles a Run has about 39 ms, registered as 30 ms. The least search each design is configured for, costed from story
6.4's medians (a 43.5 ms rollout cycle, a 3.2 ms scrub per world, arithmetic held by `CandidateFitTest` and `./gradlew
:harness:candidateFit`), needs about 270 ms for option 3 (the probe's shape, three Actions in two worlds), 790 ms for option 5
and 4.7 s for option 4 at a stated floor of 100 iterations. None fits, so none was built, no comparison was run on `standard`
or `bosses`, and `holdout` was not touched: it is for a comparison that exists, and its budget cannot be given back.

The decision is provisional on the budget: it is the engineer's reading of rule 2 and is open to the product owner, and a
cost is a sum of component medians, not a timing of a built search. What would reopen the question: a simulator
that does not pay the swap (a classloader-isolated second instance, ADR-0009's JNI note; with no swap the least option 3 search
is about 22 ms), a budget the product owner sets above about 270 ms (the table gives the budget each design needs), a new
upstream tag that changes the cost of a save and load, or a new candidate that needs fewer rollouts. Each is a new Registration
that names its machine and budget. The properties stay beside any future result (rule 4) and were not used as a reason here.

## Amendment: story 10.22 (2026-10-28)

The product owner answered the budget question that story 6.6 left open. On 2026-10-28, asked whether the think time per
Decision should be capped, he said: "Whatever makes the bot as smart as possible." So there is **no fixed think budget** for
the Rig, and rule 2 of the choice rule no longer filters candidates by a registered `budgetMs`. What replaces it:

- A candidate is registered at a stated think time per Decision, and its strength is **published per think-time column** (for
  example 30 ms, 100 ms, 300 ms, 1 s), each column a Registration against option 2 at the same wall-clock budget, ADR-0012
  unchanged. A design that wins only at a long think time is a result, not a rejection.
- The Overlay may use a **capped profile** (a budget that keeps the game playable in front of a person); the cap is the
  Overlay's, is set in its profile, and does not enter the Rig's numbers.
- The 6.6 outcome (option 2 stays) was provisional on a budget, and the budget is gone. The question is reopened for
  option 3 as a column. ADR-0024 records the simulator measurement that decides how cheap a column can be and gives a go for
  determinized fight rollouts in the Rig.
- Rules 1, 3 and 4 and the fairness rule stand; `holdout` is still reserved for a comparison that exists.
