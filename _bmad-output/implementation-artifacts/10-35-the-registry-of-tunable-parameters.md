---
title: 'Story 10.35: The registry of tunable parameters'
type: 'feature'
created: '2026-10-28'
status: 'review'
baseline_commit: '482d537f3'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** The Brain's numeric thresholds live in two places, fields of the Playbook and `static final`
constants of the Policies, with no list of them, no range and no way to play another set. Automatic tuning
(stories 10.36 and 10.37, approved 2026-10-28) needs all three.

**Approach:** One registry, `org.shatterfish.api.Tunables`, lists 31 numbers (14 Playbook fields, 17 Policy
constants), each with name, default, bounds, SPSA step and a one-line reason. Policy constants are read through a
`Tunables.Vector` the Playbook carries in a new `tunables` section; Playbook fields stay in their own sections.
The Rig plays a vector with `RunOne --tunables name=value,...`. The method (SPSA with paired seeds, only numbers
move, `standard` slices only, second-slice confirmation) is ADR-0026.

## Acceptance criteria and where they are held

1. Registry lists each parameter once with owner, default, bounds, step and reason; Brain reads through it:
   `Tunables`, `TunableRegistryTest` (defaults equal the figures written down before the story, bounds contain the
   default, one entry per name, Policy constants still equal their entry, every entry is read by Brain code).
2. ADR with five or more options: `docs/adr/0026-tunable-registry-and-spsa.md`.
3. An unregistered tunable in a Playbook file is refused by the validator: `TunablePlaybookTest`.
4. Brain behaves identically at defaults: `TunableNoChangeTest` (smoke, all classes: a Playbook stating every
   default plays equal Run logs and one config hash; a moved vector plays other Runs, as a control) and the
   by-hand comparison against `origin/main` below.

## Design notes

- A vector equal to the defaults is dropped before the canonical text, so the config hash of every earlier Playbook
  and Registration is unchanged. A Playbook that moves a number carries it in `tunables`, so the header's
  configuration hash names the vector and a Replay plays the same one.
- Policy helpers keep their old signatures as overloads that use the defaults; Brain code passes the vector
  everywhere (the fairness review found no call from main code to a short overload).
- Left out on purpose: `fight.holds` (three Policies share it), the Tengu and DM-300 descent rests (`UNTESTED`),
  `kit.zap_reach` (one class), the evaluation weights, every choice-valued field.
- `RunOne --tunables` is for the Rig. `Runner` and `Direction` do not forward it; story 10.36 adds that with the
  tuner.

## Verification

Baseline `origin/main` (482d537f3) against this branch, same picks and salts, `:rig:direction`:

- `smoke`, 25 entries, salts 1000..1024 (baseline 482d537f3): the PR table has no column that differs, and the 25 Run logs are equal
  after setting aside the header, `chain`, `prev` and `think_ms` (24,162 lines compared).
- `standard`, Warrior, first 40, salt base 1000: no column differs, and the 40 Run logs are equal under the same
  normalisation (64,344 lines compared). The baseline ref had moved to e8c68ba26 by then (no Brain change in between).

`fairness-reviewer`: no violation found.
