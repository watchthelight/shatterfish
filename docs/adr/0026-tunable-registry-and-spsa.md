---
status: accepted
date: 2026-10-28
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0026: The Brain's numbers are tuned by SPSA through a registry; the rules stay code

## Context and problem statement

The product owner approved Fishtest-style automatic tuning on 2026-10-28: the numeric thresholds of
the Brain are moved by measurement, on `standard` tuning slices, and nothing else moves (stories 10.35
to 10.37). Those numbers live in two places: fields of the Playbook (ADR-0023) and `static final`
constants of the Policies. A tuner needs one list of them with a range, a step and a reason, a way to
play a Brain with another vector, and a method for choosing the next vector. This ADR decides the
registry and override design (story 10.35) and the method (story 10.36). It touches non-negotiables 1
(information parity: a threshold is not a seed, a cell or a hidden fact), 4 (one JVM, no second rules
implementation), 5 (a Run is reproducible from its header) and 8 (a number is an assumption, not
folklore).

## Decision drivers

- Only numbers move; the Policies stay readable rules.
- A vector must be reproducible: a ranked Run names it in its configuration hash.
- Behaviour at the defaults must not change by a single Decision.
- The Rig must read the list without parsing Java.
- Other stories edit `Fight` and the Playbook at the same time, so the diff should be small and local.
- A tuner must never touch `goo`, `holdout` or `tengu`, and must keep a candidate only on a second slice.

## Considered options

Registry and override design:

1. **A registry in `api` (`Tunables`) and a `tunables` Playbook section for Policy constants**, with
   Playbook fields stated in their own sections. Policies read through a vector passed at construction.
2. A thread-local "current vector" that static Policy helpers read, so no signature changes.
3. A second parameter file beside the Playbook, hashed separately.
4. Annotation scanning: tag each `static final` with `@Tunable(min, max, step)` and let the Rig reflect.
5. Generate Java constants from a data file at build time.

Method:

A. **SPSA with paired seeds** (Fishtest's SPSA tuner is the analogy).
B. Tune by hand, as stories 7.3 to 7.6 did.
C. Coordinate search, one parameter at a time.
D. Random search or CMA-ES over the full vector.
E. A learned evaluation or end-to-end reinforcement learning (epic 9, after search).

## Decision outcome

Option 1 and method A.

- Option 1 keeps one source for a number's range and reason, in a file the Rig imports, and carries the
  vector in the Playbook, which the configuration hash already covers (ADR-0023). Its canonical text
  states `tunables` only when a number differs from its default, so every existing hash and registration
  stays valid. Option 2 is the smallest diff but hides state: a Brain worker on another thread, or a
  forgotten binding, would silently play the defaults. Option 3 makes a second thing to hash and to forget.
  Option 4 puts bounds in code the Rig cannot read without loading the Brain, and gives no place for the
  vector to ride. Option 5 moves every constant into a build step for no gain.
- The Policy constants stay as documented defaults; a test holds each equal to its registry entry and
  to the figure written down before the story. Each Policy that reads one takes the vector as a
  constructor argument, and the helpers it calls take it as an overload that defaults to the old
  signature, so existing tests compile unchanged.
- The Rig plays a vector with `RunOne --tunables name=value,...`; the vector becomes part of the Playbook
  the Brain is built on, so the header's configuration hash names it.
- Method A: it needs two evaluations per iteration however many numbers there are (a pair of perturbed
  vectors on the same paired seeds), which suits a 30-number vector and a slice that costs minutes per
  play. B does not scale to 31 numbers and six classes. C needs a pass per number and is fooled by
  interactions. D needs far more evaluations. E mislearns with no reward signal yet and stays in epic 9.
- Guards for stories 10.36 and 10.37: tune only on `standard` slices (never `goo`, `holdout`, `tengu`);
  write a candidate as a Playbook diff and never edit the committed Playbook; keep a candidate only on a
  second slice under the Sequential test with no class's bosses killed falling.

### Consequences

- 31 numbers are registered: 14 Playbook fields and 17 Policy constants. Left out on purpose:
  `fight.holds` (three Policies share it), the Tengu and DM-300 descent rests (their entries are
  `UNTESTED`), the kit's `zap_reach` (one class), the evaluation weights (their own file and tuner
  later), and every choice-valued field.
- A Playbook field override moves the base and every class entry that states the section; a per-class
  tuner would need per-class names, which the registry can add.
- Adding a tunable means adding a constant to `Tunables`, a read in a Policy, and a line in the test's
  list of figures; `TunableRegistryTest` fails on a dead or mistyped entry.

## Pre-mortem

If this is wrong in six months:

- **A Policy reads the old constant and the entry is dead.** The test scans the Brain's code for each
  entry's read. Mitigation: that test, and the control in `TunableNoChangeTest`.
- **A tuner overfits one slice.** Mitigation: the second slice and the Sequential test of story 10.37.
- **Ranges too narrow or too wide.** They are data in one file; a change is a registry edit with its reason.
- **Two vectors that look alike hash differently.** Mitigation: a stated default is dropped before the
  canonical text, tested in `TunablePlaybookTest`.
- **Merge conflict with other Policy stories.** The overloads keep old signatures, so a later story's
  call into a helper keeps compiling and plays the default until it passes the vector.

## Amendment (story 10.36): the objective, the variance choices and the salt reservation

Decided when the tune runner was built (`:rig:tune`, `Tune`, `Spsa`; usage in `docs/methodology.md`).

**The objective.** The score of one Run is 5 per boss killed plus the deepest depth reached, read from
the Run log's ending; a Run with no ending is not scored. A Goo kill is worth the five floors that lead
to it, and every floor deeper counts where nothing was killed, so the tuner has a slope on a Brain that
dies before Goo. The metric is fixed before a run and not tunable by flag. Options weighed: win rate
(near zero, no slope), depth alone (ignores kills, which are the epic's target), kills alone (too
sparse at 20 Runs per class), a learned value (epic 9). A different objective is a new ADR amendment.

**Variance.** The plus and the minus vector play the same triples under the same salts, so the paired
difference cancels a seed's difficulty; a triple with a Run missing on a side is dropped from both; each
class is one voice (the mean of the class means); the move of one iteration is capped at two steps; and
every iteration plays a fresh window of triples under fresh salts so no slice is fitted. The standard
error of the paired difference is written to the trajectory. SPSA credits every number with the whole
score change, so a large difference moves numbers that did not cause it; the cap, the decaying gain and
tuning a subset at a time are the controls, and the second slice is the judge.

**Salt map.** The tuner plays salts 5000 to 6999 and no others, and refuses a run whose base is under
5000 or whose last salt (base + iterations x classes x triples per class - 1) is 7000 or more.

| Salts | Use |
|---|---|
| 1000 up | The tuning slice for lever PRs (`:rig:direction`, first 40 Warriors). Also a confirmation slice for a kept candidate. |
| 3000 up | The confirmation slice. |
| 5000 to 6999 | SPSA only (`:rig:tune`). |
| 7000 and up | Pre-registered confirmation tiebreaks (PR #393 ran Warrior, first 120, salt base 7000). |

This replaces the "tune on the salt-1000 slice" wording of story 10.37's acceptance text: that story tunes on
5000 to 6999 and confirms on the 1000 and 3000 slices. Triples still come from `standard` for all of them, and
the salt (and, in the tuner, the moving window) is what keeps a fitted run from being replayed as its own
confirmation.

**Gain calibration (c_end and r_end).** The first demo run (3 iterations, Warrior, 20 seeds, differences of
-0.55, -0.35 and +0.45 against a standard error of about 0.8) moved `fight.flights` 2 to 3, `identify.reach`
8 to 12 and `fight.margin_per_mille` by 19% on noise alone: a gain of 4 per score point has no relation to how
noisy a score is. The tuner now calibrates as Fishtest does. The score difference of a pair is made a result
`r = clamp(diff / 2, -1, 1)` (two floors of mean paired difference is full scale). The perturbation is
`c_k = c_end * N^0.101 / (k+1)^0.101` steps, so `c_end` (default 1 step) at the last planned iteration `N`,
and the gain is `a_k = r_end * c_end^2 * (A + N)^0.602 / (A + k + 1)^0.602` with `A = 0.1 N`, so a number
moves `a_k / c_k`, about `r_end * c_end` steps at the end and about three times that at the start, for a
full-scale result (`r_end` default 0.2). A move is also capped at one step per iteration (`--max-move`). The
drift a number can have over a run is therefore bounded by the schedule and not by the noise: for N = 10 at
most 2.9 steps even if every iteration pushed one way at full scale (`SpsaTest.the_worst_case_is_bounded`), and
`SpsaTest.noise_alone_cannot_move_a_number_far` plays 300 seeds of pure noise at full scale and holds the
largest drift to 3 steps. The cost is that a real signal needs iterations: 0.2 steps at the end means a number
8 steps from its optimum needs tens of iterations, which is what the first real run (story 10.37) is for; raise
`--r-end` only with the standard error of the trajectory in view. The horizon `N` is fixed when the folder is
made, so a resume that asks for more iterations continues the same decay.

**Defaults.** `--params` defaults to `all` (the epic's wording), but the first runs should name a subset:
with 31 numbers the noise of 20 Runs per class swamps most single slopes.
