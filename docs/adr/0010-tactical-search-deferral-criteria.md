---
status: proposed
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
rollouts through the `api` `Simulator` interface with an opaque `SnapshotHandle`; the snapshot
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
