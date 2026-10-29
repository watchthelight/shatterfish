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
