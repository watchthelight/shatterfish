# Architecture decision records

One file per decision, MADR format, numbered, immutable once accepted (a later ADR supersedes
it). [ADR-0001](0001-record-architecture-decisions.md) explains the practice. The `adr` project
skill drafts new ones; add the new file to this list and to `mkdocs.yml`.

| ADR | Title | Status | Date |
|---|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | accepted | 2026-09-03 |
| [0002](0002-ci-shape.md) | CI shape: PR gate, nightly rig, results PR | accepted | 2026-09-03 |
| [0003](0003-module-layout.md) | Module layout, Java level, brain boundary | accepted | 2026-09-03 |
| [0004](0004-documentation-system.md) | Documentation system | accepted | 2026-09-03 |
| [0005](0005-observation-schema-and-hashing.md) | Observation schema and hashing | proposed | 2026-09-04 |
| [0006](0006-observer-visibility-rules.md) | How the Observer handles each visibility rule | proposed | 2026-09-04 |
| [0007](0007-rng-seeding-strategy.md) | RNG seeding strategy and the other sources of nondeterminism | proposed | 2026-09-04 |
| [0008](0008-hook-guarding-and-tracking.md) | How hooks are guarded and tracked | proposed | 2026-09-04 |
| [0009](0009-snapshot-restore-and-redetermination.md) | Snapshot, restore, and redetermination | proposed | 2026-09-04 |
| [0010](0010-tactical-search-deferral-criteria.md) | Abstract tactical model versus engine rollouts, deferred to E6 with criteria | proposed | 2026-09-04 |
| [0011](0011-run-log-format.md) | Run-log format | proposed | 2026-09-04 |
| [0012](0012-rig-statistics.md) | The Rig's statistics: the Per-pair GSPRT | proposed | 2026-09-04 |
| [0013](0013-overlay-threading-model.md) | Threading model for the Overlay and the drivers | proposed | 2026-09-04 |
| [0014](0014-action-schema-and-executor-contract.md) | The Action type and the ActionExecutor contract | proposed | 2026-09-04 |
| [0015](0015-headless-scene-and-input-wait-detection.md) | The headless scene, the main loop, and Input-wait detection | proposed | 2026-09-04 |

## Decisions still to make

Each gets a micro-brainstorm (bootstrap prompt, section 2.2) and an ADR when its epic comes:

- The tactical search design itself (E6, per the criteria of ADR-0010)
- GSPRT bounds, burn-in and the e-process comparison (E3 calibration story, per ADR-0012)
- Classloader isolation versus process per Run (E1 spike report)
- The Codex generation mechanics (E2)
