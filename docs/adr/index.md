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
| [0005](0005-observation-schema-and-hashing.md) | Observation schema and hashing | proposed | 2026-09-03 |
| [0006](0006-observer-visibility-rules.md) | How the Observer handles each visibility rule | proposed | 2026-09-03 |
| [0007](0007-rng-seeding-strategy.md) | RNG seeding strategy and the other sources of nondeterminism | proposed | 2026-09-03 |
| [0008](0008-hook-guarding-and-tracking.md) | How hooks are guarded and tracked | proposed | 2026-09-03 |

## Decisions still to make

Each gets a micro-brainstorm (bootstrap prompt, section 2.2) and an ADR when its session comes:

- Snapshot/restore and redetermination (session 12)
- Abstract tactical model versus engine rollouts (session 12 states the criteria; E6 decides)
- Run-log format (session 12)
- The rig's statistics: the Per-pair GSPRT (session 12; calibration in E3)
- Threading model for the overlay (session 12)
