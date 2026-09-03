---
title: 'PRD Addendum: Shatterfish'
status: final
created: '2026-09-03'
updated: '2026-09-03'
---

# PRD Addendum: Shatterfish

This addendum holds the depth that downstream documents consume without reprocessing. The brief's addendum already holds rejected alternatives, technical constraints, and personas; this file does not repeat them. It holds what is specific to the PRD: the requirement-to-epic map, the mechanism decisions the PRD deliberately leaves to ADRs, format sketches for the public surfaces, the options considered for the headline metric, and where each input landed.

## Requirement-to-epic map

Derived from PRD section 6.1, which is the binding scope statement; keep the two in sync.

| Epic | Requirements | Done when (brief and bootstrap) |
|---|---|---|
| E0 Bootstrap | FR-48 to FR-53 (FR-50's first use is after E3) | Bootstrap section 11 checklist |
| E1 Harness | FR-1 to FR-5, FR-7 to FR-12; FR-6 interface reserved | Turns per second and tactics properties published (SM-4); determinism green; fairness suite green |
| E2 Codex | FR-14 to FR-17 | One task regenerates the Codex; CI fails on drift |
| E3 Rig | FR-19 to FR-26 | Baseline and paired-seed correlation published; worse Brain rejected (SM-5) |
| E4 Baseline brain | FR-27 to FR-33, FR-36 | Goo on at least 75% of `standard`, lower bound 70% (SM-3) |
| E5 Overlay v1 | FR-37 to FR-43 | Full sewers run with mid-fight takeover, no desync (SM-6) |
| E6 Tactical search | FR-6 realization, FR-13, FR-34, SPSA tuning of FR-33 | Sequential-test-significant improvement over E4 |
| E7 Strategy and lore | FR-18, FR-35 | Measured win rate; every heuristic links to a Lore entry or Rule with a Tier |
| E8 Overlay v2 | FR-44 to FR-47 | Feature-complete per bootstrap section 5 |
| E9 Learned eval (optional) | none in this PRD | Beats the hand-tuned Evaluation under the Sequential test |

## Mechanism decisions deferred to ADRs

The PRD states capabilities; each decision in the table needs an ADR with rejected alternatives and a pre-mortem before or during the epic named in its row.

| Decision | Epic | Starting position (from research) |
|---|---|---|
| Headless-scene design: harness-owned scene, no-op GL, Pixmap atlases, fast-forwarded updates; which static helpers need hooks | E1 | Research recommendation 1; round-4 digest is the inventory |
| Observation schema and hashing | E1 | Content hash of a canonical serialization; schema version field |
| How the Observer handles each visibility rule (FOV, mapped, secret doors, traps, heaps, identification, mind vision, blindness, magic mapping) | E1 | Build from what the game computes for drawing, never raw fields |
| RNG seeding strategy (which generators, reset points, per-Run isolation) | E1 | Open question 4 |
| How hooks are guarded and tracked beyond the `settings.gradle` pattern | E1 | `docs/UPSTREAM.md` registry; guard flag per hook |
| Parallelism: processes versus classloader isolation | E1 spike | Processes by default; libGDX in a shared parent loader if isolation is tried (JNI rule) |
| Run log format | E3 | JSONL, one record per turn, Hash chain, schema version |
| Rig statistics: GSPRT port, Composite outcome (win, Score, depth, turns), bounds, burn-in, calibration, recalibration per tag, e-process alternative; Seed-set sizes; where `standard` runs | E3 | Research recommendation 3; brief addendum, Statistics; PRD FR-20, FR-21, open question 11 |
| Threading model for the Overlay | E5 | Brain on a worker thread over the immutable Observation; all Panel writes on the render thread |
| Snapshot/restore and redetermination | E6 | Two fair designs: world built from Observation plus Belief sample, or scrubbed snapshot with differential tests |
| Abstract tactical model versus engine rollouts versus information-set search | E6 | One-ply over Codex tables first; measure Long et al.'s properties and simulator speed |

## Public surface sketches (non-binding; the ADRs decide)

**Run log record (one JSONL line per turn):**

```json
{"v":1,"turn":412,"depth":3,"actor":"bot","obs":"sha256:...","prev":"sha256:...","action":{"kind":"move","target":1234},"decision":{"goal":"Explore: guaranteed strength potion still on this floor","chosen":{"action":"move:1234","score":0.71,"why":"unexplored frontier, no visible enemies"},"alternatives":[{"action":"read:scroll-of-KHIT","score":0.40,"why":"safeTest worst case survivable"}],"flags":["by water"],"policy":"explore"}}
```

**Rig command line (contract from the `rig` project skill):**

```
./gradlew :rig:run --args="--brain <name> [--baseline <name>] --seeds <set|N> [--seed-start K] [--threads T] --out <dir>"
```

The command writes `runs.jsonl`, `summary.json`, and `sprt.json` (when comparing). Oracle mode cannot be enabled through it.

**Results page fields:** Upstream tag, Shatterfish commit, Seed set name and version, both Brains (name, commit, configuration), Hypothesis ID with the Registration commit, bounds and units, outcome (accept / reject / undecided) with the log-likelihood trace, per-Run aggregates (win, Score, depth, turns, cause of death, boss kills) with distributions, measured paired-seed correlation, links to the Run logs, fairness suite status, Oracle mode off, and the command that reproduces it.

**Lore claim frontmatter:** `source_url`, `source_type`, `date`, `variant` (`spd` / `pd` / `mod:<name>` / `unknown`), `version_claimed`, `claim`, `tier` (1, 2, 3, F), `implemented_in`.

## Options considered for the headline metric

The brainstorm proposed several definitions of winning; the product owner set the ladder as "first beat the final boss, then the highest score possible".

- Headline: a verified Yog-Dzhewa win (SM-1).
- Canonical follow-on: the highest verified Score of a winning Run (SM-2).
- Check on SM-2: per-class win rate (SM-7).
- Retained for the record: the survival curve and boss staircase (used as rungs); the relative strength chain between versions (the per-version comparison); ascension and challenge win rates (far horizon); turns per second (a Rig property, now a counter-metric).
- Score: initially rejected as gameable; admitted only for winning Runs, with SM-C3 guarding against Score at the cost of wins.

## Inputs and where they landed

| Input | Landed in |
|---|---|
| Brief: audience, success ladder, scope, risks | Sections 2, 6, 7; risks referenced, not repeated |
| Brief addendum: NFR list, technical constraints, personas | Sections 8, 10, 11; constraints pointed to from this addendum |
| Research recommendations 1 to 10 | FR-1, FR-5, FR-7, FR-21, FR-34, FR-38, NFR-3, section 9 |
| Bootstrap section 4 (modules, Observation, Action, fairness tests) | Glossary, section 4.1, section 4.2 |
| Bootstrap section 5 (overlay panel, controls, interjection, oracle) | Section 4.6 |
| Bootstrap section 6 (program map) | Section 6, requirement-to-epic map |
