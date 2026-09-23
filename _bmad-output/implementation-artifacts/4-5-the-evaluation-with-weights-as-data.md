---
title: 'Story 4.5: The Evaluation with weights as data'
type: 'feature'
created: '2026-09-23'
status: 'review'
baseline_commit: '4e95c0ec0'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Brain has no way to prefer one position or one Action over another, and whatever
preference later stories write would be constants in `brain`, so the Rig could only tune them by
editing code, and a Registration could not tell two tunings apart (FR-33).

**Approach:** One `Evaluation` in `brain`: a weighted sum of integer features read from the
Observation. Its weights are an `api` value (`Weights`: name, version, one integer per feature) that
the Rig reads from a committed file, `weights/<brain>.json`, and hands to the Brain at construction,
as it does the Codex manifest. The canonical text of the weights goes into the Brain's configuration
and so into the configuration hash in every log header and Registration. The fallback Policy uses
the Evaluation to pick among the offered Actions. The committed weights give the Action features
no weight, so play is unchanged from story 4.1 until the Rig tunes a weight.

## Boundaries & Constraints

**Always:**
- Every feature is read from the Observation: hit points, depth, level, strength, hunger, and
  enemies in view.
- The arithmetic is integers only.
- The Brain never opens the file.
- The weight set must weight exactly the Evaluation's features.
- The file is refused unless it is canonical, is for the named Brain, and is in the format this
  build reads.

**Ask First:** A feature that is not on the screen, such as a Belief-derived probability. That
arrives with 4.2 and is added to the Evaluation in the story that needs it.

**Never:**
- Weights as constants in `brain`.
- A weight set whose change the configuration hash does not see.
- Floating point in the score.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Committed weights | `weights/shatterfish.json` | fallback uniform over the offered Actions, the same draws as 4.1 | N/A |
| A weight on an Action feature | e.g. `act_search` 5 | fallback takes the top-scoring Actions only | N/A |
| Missing or extra feature | weight set does not match the features | refused at Brain construction | IllegalArgumentException |
| Non-canonical file, other Brain, other format | file on disk | refused by `WeightsFile.read` | IllegalArgumentException |
| Brain run without weights | no `--weights` | `Brains.of` refuses, naming the flag | IllegalArgumentException |

</frozen-after-approval>

## Design

**Constraints restated.**
- Non-negotiable #1: the Evaluation reads only what the Observation carries. No seed, salt or RNG
  reaches `brain`, and the weights are data about the Brain, not about the Run.
- AD-1/BrainBoundaryTest: `brain` imports only `api` and the whitelisted JDK packages, so the file
  must be read by the Rig.
- Non-negotiable #5: a weight set is part of what a Run is, so it has to be in the configuration hash.

**Alternatives weighed.**
1. **Weights as a `Map<String, Double>` in a properties file.** Rejected. Doubles have more than one
   spelling, so the configuration hash would depend on formatting. Floating-point sums are
   deterministic in Java, but they read worse in a log.
2. **Weights inside the Registration.** Rejected. A Registration fixes a hypothesis about a Brain; it
   is not the Brain's configuration. Keeping them there would mean a development run has no weights.
3. **A committed, versioned, canonical JSON file per Brain under `weights/`, read by the Rig into an
   `api` record, with integer weights.** Chosen. It follows the Codex manifest pattern, gives one
   spelling per set, and sits beside `seeds/`, `registrations/` and `calibration/` at the repository
   root, where the Rig's committed data lives.

**How the Evaluation is used now.** The fallback Policy scores every offered Action and draws
uniformly among the top scorers.
- The position features are the same for every Action at one wait, so they never change a choice.
  They are what a later search compares positions by, and what `EvaluationMonotonicityTest` holds
  to the obvious orderings.
- The committed file gives every Action feature weight 0, so every Action ties and the draw is
  exactly 4.1's (`EvaluationMonotonicityTest.the_same_draws`).
- A non-zero Action weight changes behaviour with no recompilation
  (`EvaluationMonotonicityTest.weights_are_behaviour`).
- Tuning a weight is a Rig experiment, not part of this story.

**Pre-mortem.**
- A later story adds a feature and forgets the file. The Evaluation refuses a set that does not
  weight exactly its features, so every Run fails at construction rather than silently weighting it 0.
- Someone reformats the file. The reader refuses a non-canonical file.
- The configuration hash misses a weight change. `WeightsFileTest.configured_by_the_weights` holds
  that a changed weight and a changed version both change the hash.
- The held-out budget does not move with a weight change. `Brains.sourceOf` includes the weights
  file, so the Brain's version moves with it.

## Code Map

- `shatterfish/api/.../Weights.java`: the record, `FORMAT`, `canonical()`, `weight(feature)`.
- `weights/shatterfish.json`: the committed set, version 1.
- `shatterfish/rig/.../WeightsFile.java`: `FOLDER`, `of(root, brain)`, `read(file, brain)`.
- `shatterfish/brain/.../Evaluation.java`: features, `position`, `of(observation, action)`.
- `shatterfish/brain/.../Brain.java`:
  - `Brain(codex, weights, seed)`;
  - `configuration(Weights)`;
  - `weights()`.
- `shatterfish/brain/.../Policies.java`: `fallback(Evaluation)`.
- `shatterfish/rig/.../Brains.java`:
  - `readsWeights`;
  - `of(name, triple, codex, weights)`;
  - `configHash(name, weights)` and `configHash(root, name)`;
  - `configHash(name)` refuses a weighted Brain;
  - `sourceOf` covers the file.
- `shatterfish/rig/.../RunOne.java`: `--weights`, 13 flags.
- `shatterfish/rig/.../Runner.java`:
  - passes `--weights` for a weighted Brain;
  - the Registration checks hash with the weights.

## Tasks & Acceptance

**Execution:**
- [x] `api/Weights.java` and `weights/shatterfish.json`.
- [x] `rig/WeightsFile.java`, `Brains`, `RunOne`, `Runner`: read by the caller, handed at construction, hashed into the configuration.
- [x] `brain/Evaluation.java`; the fallback scores by it.
- [x] Tests: `EvaluationMonotonicityTest`, `WeightsTest`, `WeightsFileTest`; `ShatterfishRunTest`, `RigOracleGateTest`, `JsonRenderingTest` updated.

**Acceptance Criteria:**
- Given the weights file, it is a committed, versioned `api`-typed value that the Rig reads, not
  constants in code (`WeightsFileTest`).
- Given a changed weight, the Brain's behaviour changes with no recompilation of `brain`
  (`EvaluationMonotonicityTest.weights_are_behaviour`).
- Given two weight sets, the configuration hashes differ, as they do for two versions
  (`WeightsFileTest.configured_by_the_weights`).
- More hit points at equal depth score higher, along with the other obvious orderings
  (`EvaluationMonotonicityTest`).
- The PR carries a `smoke` direction check against the 4.1 Brain. The parent runs it; the committed
  weights predict equal play.

## Dev Notes

- The features are all screen-derived. No feature is "derived from Codex tables" yet: the Brain
  receives only the Codex manifest until story 4.2 gives it table contents. The first Codex-derived
  feature comes with the story that needs one.
- `Brains.configHash(name)` now refuses the weighted Brain. Callers that have the repository use
  `configHash(root, name)`; the child uses `configHash(name, weights)` with the set it read.
- **Units.** The Evaluation's integer is read in ten-thousandths, like every Decision score
  (ADR-0011, `RunLog.Choice`), and the fallback logs it as its Choice's score. The committed weights
  are scaled to that unit:
  - hp 10, so a full bar (1000 thousandths) is one point;
  - depth 10000, level 5000, strength 2000, hunger -5000, enemies -3000;
  - the Action features 0.

**Review (lens and fairness).** Fairness PASS. Fixes:
- `EvaluationMonotonicityTest.the_committed_weights` holds `Screens.WEIGHTS` to the committed file,
  so the orderings are checked under the weights the Brain plays with.
- `every_action_feature` checks each of the five Action features against a neutral Step or Attack.
- Units, as above, in `Evaluation`'s and `Weights`' javadoc and on the methodology page.
- `RunOne` refuses a weighted Brain without `--weights`, naming the flag.
- `Runner.weighed` reads and checks each weighted side's file once, before any child starts. The
  children read the same file, and the header's configuration hash shows they agreed. A parent-side
  comparison of the children's header hashes is not added.
- `Weights.Term` bounds a weight at 10^9 either way, and the Evaluation sums with `Math.addExact`
  and `Math.multiplyExact`.
- Hunger is an exhaustive switch, not `ordinal()`.
- `.gitattributes` gives `weights/**` line feeds, and `WeightsFile` refuses a leading byte-order mark.

**Not done here.** The Rig cannot SPRT two weight sets of one Brain, because both sides of a
comparison read `weights/<brain>.json`. Recorded in `docs/ideas.md` and deferred to the tuning story.

**Rig numbers: direction check.** `smoke`, 25 triples, main `4e95c0ec0` (story 4.1's Brain)
against this branch, the same fixed salts through `RunOne` with `--weights`: 25 of 25 Action
sequences identical, 1,688 waits. The committed weights leave play unchanged, as designed.

- Mutation battery: 6 of 6 killed (hp weight ignored, enemy alignment swapped, fallback ignores
  scores, feature set unchecked, weights left out of the configuration, canonical check off).
