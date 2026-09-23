---
title: 'Story 4.1: The Brain skeleton, arbitration and re-planning'
type: 'feature'
created: '2026-09-23'
status: 'in-progress'
baseline_commit: 'df12f8e1a1e4e6a19a061f54460a3087c85f61e6'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** E4's Brain has no body. `brain` holds only a marker; the Run loop logs `decision: null`
and `belief: ""` for every wait because no Decider can hand either over; and nothing yet shows a
Brain re-plans from what it sees rather than from what it last intended — the property that lets a
human take a turn at any moment (FR-27, FR-28).

**Approach:** `Brain` in `brain`: `update(Observation, Belief) -> Belief` and `decide(Observation,
Belief) -> Decision` (an `api` value naming the Action, the Policy, the goal and up to three
alternatives), arbitrating a priority list of interruptible `Policy` objects, each with a cheap entry
predicate, re-evaluated from scratch at every wait. The first Policies are the minimum a Run needs:
answer a Prompt, and a seeded fallback over the offered set. The Belief is an opaque `api` value.
The Codex manifest reaches the Brain as an `api` value at construction, read by the caller. A new
`api` interface lets a Decider hand the Run loop its last Decision and Belief, and the loop logs
them. The Rig gains the Brain `baseline`.

## Boundaries & Constraints

**Always:** `brain` imports only `api` and the allowed JDK packages (BrainBoundaryTest); its
randomness is arithmetic on a seed its caller gives it, never a JDK generator. Every Action a Brain
returns is one the Observation offers. A Decision is a pure function of (Observation, Belief,
seed); nothing carries over a wait except through the Belief.

**Ask First:** Anything the Brain reads that is not in the Observation, the Belief or the Codex.

**Never:** A Brain that reads a file, the clock, the game, or its own previous Action. Rig numbers
for this story: it is the first Brain and has nothing to compare against (the epic's rule exempts it).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| A Prompt is up | Observation with an answer offered | the prompt Policy answers it | N/A |
| Ordinary wait | offered set, no Prompt | fallback chooses among the offered | N/A |
| Foreign Action | the Brain's Decision not applied, another Action applied | next Decision computed from the new Observation, not the old plan | N/A |
| Same inputs | same Observations, same seed | same Decisions and Beliefs | N/A |
| Nothing offered | empty offered set | null Action, Decision records why | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/brain/.../BrainModule.java` -- marker; boundary doc.
- `shatterfish/brain/src/test/.../BrainBoundaryTest.java` -- ALLOWED packages; Random/SplittableRandom/java.util.random denied.
- `shatterfish/api/.../Decider.java` -- `decide(Observation)`; the seam.
- `shatterfish/api/.../Belief.java` -- opaque versioned bytes, `hash()`.
- `shatterfish/api/.../RunLog.java:225-260` -- `Choice`, `Decision(goal, chosen, alternatives, flags, policy)`; `Wait(... decision, belief ...)`.
- `shatterfish/harness/.../agent/RunLoop.java:424` -- writes `decision: null`, `belief: ""`.
- `shatterfish/api/.../Codex.java` -- `Manifest(version, upstreamTag, tables)`; `codex/v4.0.0/manifest.json`.
- `shatterfish/rig/.../Brains.java` -- names, sources, `of`, `configHash`.

## Tasks & Acceptance

**Execution:**
- [ ] `api/Deliberator.java` -- a Decider that also reports its last Decision and Belief.
- [ ] `brain/Brain.java`, `Policy.java`, `Stream.java` (seeded arithmetic), `Memory` (Belief bytes), `policies/AnswerPrompt`, `policies/Fallback`, `BrainDecider` (a Deliberator).
- [ ] `harness/RunLoop.java` -- log a Deliberator's Decision and Belief hash (fairness label).
- [ ] `rig/Brains.java` + `rig/CodexManifest` reading -- the Brain `baseline`, the manifest handed at construction.
- [ ] Tests: `ReplanAfterForeignActionTest`, `BrainDeterminismTest`, `PolicyArbitrationTest`, boundary green, a Run-loop test that the log carries the Decision and Belief.

**Acceptance Criteria:**
- Given a Decision and a different Action applied, when the next wait comes, then the Decision is the one computed from the new Observation (`ReplanAfterForeignActionTest`).
- Given the same Observation sequence and seed, then the Decisions and Beliefs are identical (`BrainDeterminismTest`).
- Given the Brain in a real Run, then each Wait record carries its Decision and Belief hash.
- The PR states the Rig-numbers exemption.
