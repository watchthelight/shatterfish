# Story 10.24: Determinized engine rollouts in fights (#370)

Status: review

## Goal

W4 step 2. Build the permutation leak test through the isolated host first (ADR-0025's precondition), then a fight-only search
Policy in the Rig that samples hidden state from the Brain's own beliefs, rolls candidates out through the engine, and takes the
best mean. Keep it only if it wins.

## Alternatives weighed

1. Search through the swap host (`RolloutHost`): rejected, it runs in the live Run's process and statics (ADR-0025).
2. A list-of-Actions rollout only: rejected, the continuation after the first Action must react to what the rollout shows, so
   `Playout` (a function of Observations) was added next to the list form.
3. The Brain holds a snapshot handle: rejected, the Brain would then name live state. It is lent a `Lookahead` that takes a
   `Hypothesis` (three draws and remembered mobs) and returns a `RolloutResult`; nothing else crosses.

## Pre-mortem

The search could be a no-op that looks like a result. It was, once: Direction's heap-watch wrapper hid the look-ahead, and the
first table showed nothing. The check is the count of `fight-search` Decisions in the logs (1898 for CLERIC alone in the second run).

## Outcome

- `IsolatedPermutationTest` (two classes, three controls) and the same through `HostLookahead`.
- `Hypothesis`, `Lookahead`, `Playout`, `Searching`, `SearchPlan`; `Simulator.simulate(handle, Playout, horizon)`;
  `HostLookahead`; `RunLoop` lends it to a decider that `searches()`; Brain `Model.SEARCH`, Policy `fight-search`; Rig Brain `fightsearch`.
- Measurement: `docs/results/2026-10-29-fight-search.md`. No win (101 of 235 against 100 of 240 reaching depth 5). Not the default.
- Think time: in the Run logs as `think_ms`.
- Decision: the plumbing and the leak test stay (they are the precondition for any later search, including Tengu practice);
  the Policy stays registered as a Rig-only Brain; nothing ships using it.
