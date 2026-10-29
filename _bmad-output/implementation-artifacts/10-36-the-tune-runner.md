---
title: 'Story 10.36: The tune runner'
type: 'feature'
created: '2026-10-29'
status: 'review'
baseline_commit: 'f5fb4ecb8'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** The registry of story 10.35 lists 31 numbers and can play a vector, but nothing chooses the next
vector. Moving them by hand does not scale to 31 numbers and six classes.

**Approach:** `:rig:tune` runs SPSA over the registry, Fishtest style (ADR-0026). Each iteration perturbs the
vector by plus and minus `c_k` steps with a fixed-seed Rademacher draw, plays both vectors on the same triples
under the same salts, scores each Run, and moves the state along the paired difference. The folder it writes is
the whole state (resumable), holds the parameter trajectory and the per-pair scores, and ends with a candidate
Playbook; a committed Playbook is never edited. `Runner` (`--brain` side only) and `Direction` (current side) now
forward `--tunables`, which 10.35 left for this story.

## Acceptance criteria and where they are held

1. `:rig:tune --params <names|all> --class ALL --iterations N --salt-base S`: `Tune`, `Spsa`; usage in
   `docs/methodology.md`, "The tune runner".
2. Trajectory, one line per iteration (values, both scores, the step), beside the logs, and a candidate as a
   Playbook diff, never an edit of the committed Playbook: `trajectory.jsonl`, `candidate.json`,
   `candidate.diff.txt`; `TuneRunnerTest.everything_stays_inside_the_bounds` compares the committed file before and
   after.
3. Refuses `goo`, `holdout` and `tengu`: `TuneRefusalTest`, one case per set, through `Tune.run`.
4. Three iterations are identical on every JVM, the candidate stays inside every bound, and a planted objective
   with a known optimum is approached (with a flat control that moves nothing): `TuneRunnerTest`
   (`three_iterations_are_reproducible` pins iteration 0's vectors and `SpsaTest.signs_are_stable` pins the signs, so a
   different JVM or JDK cannot differ without a test failing), `SpsaTest` (quadratics, noiseless and noisy).
5. Pairing: plus and minus play the identical list of triples and salts, and a triple with a Run missing on a side
   is dropped from both: `TuneRunnerTest.both_vectors_play_the_same_seeds`.
6. Resume equals an uninterrupted run, byte for byte: `TuneRunnerTest.resume_is_equivalent`.
7. Docs: `docs/methodology.md`, `CLAUDE.md`, `.claude/skills/rig/SKILL.md`, ADR-0026 amendment.

## Design notes

- **Objective:** 5 per boss killed plus the deepest depth reached, from the log's ending. Variance: paired seeds
  (common random numbers), the mean of class means, a capped move, fresh triples and salts every iteration. All in the
  ADR amendment.
- **Salt reservation:** tuning salts start at 5000 and a smaller base is refused; 1000 (direction) and 3000
  (confirmation) are not the tuner's. This overrides the "salt-1000 slice" wording of story 10.37 on the product
  owner's instruction for this story.
- **Determinism without two JVMs in the test:** the signs come from a written-out splitmix64 finaliser, the state is
  a double with a stable text form, and nothing in the loop reads a clock or a hash map's order. What is left
  (play) is the harness's reproducibility.
- **SPSA credits every number with the whole difference.** A large difference moves numbers that did not cause it;
  the cap, the decaying gain and subsets are the controls. `--params` defaults to `all` as the epic says; start with
  a subset.
- **`Runner --tunables` is refused with a `--registration`** (a registered Run plays the committed Brain). The parent
  now has 14 flags and the child 15 (`RigOracleGateTest`).

## Verification

See the pull request: the test results, and a 3-iteration demo (Warrior, 20 seeds, 3 numbers). The real first run is
story 10.37.
