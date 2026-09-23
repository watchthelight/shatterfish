---
title: 'Story 4.1: The Brain skeleton, arbitration and re-planning'
type: 'feature'
created: '2026-09-23'
status: 'review'
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
- [x] `api/Deliberator.java` -- a Decider that also reports its last Decision and Belief.
- [x] `brain/Brain.java`, `Policy.java`, `Stream.java` (seeded arithmetic), `Memory` (Belief bytes), `policies/AnswerPrompt`, `policies/Fallback`, `BrainDecider` (a Deliberator).
- [x] `harness/RunLoop.java` -- log a Deliberator's Decision and Belief hash (fairness label).
- [x] `rig/Brains.java` + `rig/CodexManifest` reading -- the Brain `baseline`, the manifest handed at construction.
- [x] Tests: `ReplanAfterForeignActionTest`, `BrainDeterminismTest`, `PolicyArbitrationTest`, boundary green, a Run-loop test that the log carries the Decision and Belief.

**Acceptance Criteria:**
- Given a Decision and a different Action applied, when the next wait comes, then the Decision is the one computed from the new Observation (`ReplanAfterForeignActionTest`).
- Given the same Observation sequence and seed, then the Decisions and Beliefs are identical (`BrainDeterminismTest`).
- Given the Brain in a real Run, then each Wait record carries its Decision and Belief hash.
- The PR states the Rig-numbers exemption.

## Dev Notes

Four reviews ran on the first commit: blind, edge-case, verification-gap and fairness.

**Fairness: BLOCK, resolved.** The first draft seeded the Brain from `Brains.agentSeed(triple)`, the
seed the random agents use. That is `mix(mix(seed, class), challenges)`: a bijection, with two of its
three inputs in the Observation header. A Brain holding it could recover the dungeon seed, and from
it every unidentified item's identity and every floor's layout; non-negotiable #1 names the seed
among what the bot never reads. Three alternatives were weighed:

1. derive the Brain's seed from the salt through a one-way hash: rejected, because anything the
   Brain can compute from the salt brings it closer to the game's RNG, and a hash of a 64-bit value
   is brute-forceable in principle;
2. key the triple's mix with a secret the Brain never gets: rejected, because it adds a secret
   whose custody is new machinery and still varies the Brain with hidden state;
3. **a constant per Brain** (`Brains.brainSeed`, `mix(BRAIN_STREAM, name.hashCode())`), advanced by
   the waits the Brain has served: chosen. The Brain is then a function of what it has seen and
   nothing else, which is the property FR-27 wants anyway. The cost is that the Brain's stream does
   not vary across seeds; the game does, and the salt varies it again.

The random agents keep `agentSeed`: they never read their seed, and changing it would move every
published E3 number. `docs/fairness.md` and `docs/methodology.md` record the rule.

**Deviation from the frozen intent:** the Brain is named `shatterfish`, not `baseline`. The Baseline
is the random agent throughout the rig and the methodology, and the comparison folder of that name
holds its side.

**Other findings fixed:**
- Replay could not reproduce a Brain's log: its follower was a plain Decider, so the replayed Wait
  records lacked the Decision and Belief hash and the chains differed. `Deliberator.beliefHash()`
  (default from `belief()`); the follower is a Deliberator stating the logged Decision and hash.
  `ShatterfishRunTest` replays one of the Brain's logs to the original chain.
- The Codex is read only for a Brain built on one (`Brains.readsCodex`); `codex/` is a rig test input.
- `configHash` hashes `Brain.configuration()` (Policies, memory version) and the seed, not zeros.
- `sourceOf` adds `api/Deliberator.java` and `codex/`.
- Each Policy draws from its own stream, salted by its place in the list; an alternative that repeats
  the chosen Action, or another alternative, is not recorded.
- The prompt Policy declines when an offered answer is labelled "No", "Cancel", "Never mind" or "Not
  now" (case-blind without a Locale), else takes the lowest answer, else dismisses.
- `BrainDecider.why()` exposes why no Action was returned; `Memory.waits` is documented as the
  Observations folded in.
- `ReplanAfterForeignActionTest.no_stale_action` counts the cases it checked.
- `BrainBoundaryRulesBiteTest` holds the narrowed log ban: the header, the boundary record and a seed
  set's entry are rejected; a `RunLog.Decision` is not.

**Not changed:** arbitration still asks every Policy that enters, because the alternatives are the
Decision's record of what else would have acted, and the two Policies are cheap. The fallback still
acts when a Prompt is open and offers neither an answer nor a dismissal: a null Action would end the
Run, and the executor refuses whatever the Prompt does not accept, which the log records.

Seven of the 25 `smoke` Runs capped at 60 turns end otherwise than on the cap: three deaths and two
unknown windows. An unknown window is the harness's existing limit (the random agent meets it too) and
its log says it is not verifiable, so the replay check takes a verifiable log.

**Rig numbers:** exempt. The Brain plays as the random agent does outside Prompts; its first
measured comparison is story 4.2's.
