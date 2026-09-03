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

## Decisions still to make

Each gets a micro-brainstorm (bootstrap prompt, section 2.2) and an ADR when its session comes:

- Observation schema and hashing (sessions 11-12)
- How `Observer` handles each visibility rule (sessions 11-12)
- RNG seeding strategy (sessions 11-12)
- Snapshot/restore and redetermination (sessions 11-12)
- Abstract tactical model versus engine rollouts (may be deferred to E6 with criteria)
- Run-log format (sessions 11-12)
- The rig's statistics: SPRT parameters for win rate and depth (E3)
- Threading model for the overlay (sessions 11-12)
- How hooks are guarded and tracked beyond the `settings.gradle` pattern (sessions 11-12)
