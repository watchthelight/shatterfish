# Roadmap

Epics are milestones on GitHub and this page mirrors them: one milestone per epic, one issue
per epic, and one issue per story for the current epic and the next. The `sync-issues` skill keeps
the mirror exact and is safe to re-run. The epics and their stories come from
[the epic breakdown](bmad/planning-artifacts/epics.md); the goals and done-when below are its
words, which supersede the starting position in the [bootstrap prompt](BOOTSTRAP-PROMPT.md).

Story issues exist for E1 and E2 today. The rest are created when their epic becomes current or
next, so that a story is mirrored close to when it is worked and not months before.

| Epic | Milestone | Issue | Stories | Goal | Done when | Status |
|---|---|---|---|---|---|---|
| **E0 Bootstrap** | [E0](https://github.com/watchthelight/shatterfish/milestone/1) | [#1](https://github.com/watchthelight/shatterfish/issues/1) | 14 sessions | Repository, pinned upstream, module skeleton with the boundary enforced, docs, CI and the full planning artifact set | Bootstrap section 11 checklist complete | in progress |
| **E1 Harness** | [E1](https://github.com/watchthelight/shatterfish/milestone/2) | [#45](https://github.com/watchthelight/shatterfish/issues/45) | 21 stories | The game runs headlessly, reproducibly, and behind a fair Observation | A seeded Warrior Run completes headlessly, the same tuple twice is identical across two JVMs, and the whole fairness suite is green | next |
| **E2 Codex** | [E2](https://github.com/watchthelight/shatterfish/milestone/3) | [#46](https://github.com/watchthelight/shatterfish/issues/46) | 10 stories | Every fact the Brain needs about the game, generated from the pinned code and never drifting | One Gradle task regenerates `codex/<tag>/` and CI fails on drift | planned |
| **E3 Rig** | [E3](https://github.com/watchthelight/shatterfish/milestone/4) | [#47](https://github.com/watchthelight/shatterfish/issues/47) | 12 stories | Any claim about a Brain can be measured, published and reproduced by a stranger | A random-agent Baseline is published and a deliberately worse Brain is rejected by the Sequential test | planned |
| **E4 Baseline brain** | [E4](https://github.com/watchthelight/shatterfish/milestone/5) | [#48](https://github.com/watchthelight/shatterfish/issues/48) | 14 stories | A hand-built bot that plays the sewers competently | The Warrior kills Goo on at least 75% of the `goo` Seed set with a lower bound of at least 70% | planned |
| **E5 Overlay v1** | [E5](https://github.com/watchthelight/shatterfish/milestone/6) | [#49](https://github.com/watchthelight/shatterfish/issues/49) | 16 stories | A person can watch the bot think, step it, and take the controls mid-fight | A full sewers Run watched end to end with a mid-fight takeover and no desync | planned |
| **E6 Tactical search** | [E6](https://github.com/watchthelight/shatterfish/milestone/7) | [#50](https://github.com/watchthelight/shatterfish/issues/50) | titles only | Better fighting, if and only if the numbers say so | A candidate design is accepted against the one-ply model under the Sequential test, or the measurements are published and the question is closed | deferred |
| **E7 Strategy and lore** | [E7](https://github.com/watchthelight/shatterfish/milestone/8) | [#51](https://github.com/watchthelight/shatterfish/issues/51) | titles only | Whole-Run planning and a cited knowledge base | A measured win rate, and every heuristic linked to a Rule or a Lore entry with a Tier | deferred |
| **E8 Overlay v2** | [E8](https://github.com/watchthelight/shatterfish/milestone/9) | [#52](https://github.com/watchthelight/shatterfish/issues/52) | titles only | The instrument becomes a teacher | Feature-complete per bootstrap §5 | deferred |
| **E9 Learned evaluation (optional)** | [E9](https://github.com/watchthelight/shatterfish/milestone/10) | [#53](https://github.com/watchthelight/shatterfish/issues/53) | titles only | A learned value function that plays fair | Beats the hand-tuned Evaluation under the Sequential test | optional |

## Bootstrap sessions (E0)

Tracked as a task list on [#1](https://github.com/watchthelight/shatterfish/issues/1).

| Session | Content | State |
|---|---|---|
| 1 | Environment, tooling, BMAD, repository | done |
| 2 | Build without the Android SDK, module skeleton, ArchUnit, CI ([#2](https://github.com/watchthelight/shatterfish/pull/2)) | done |
| 3 | Documentation system | done |
| 4 | `CLAUDE.md`, project skills, subagents | done |
| 5 | BMAD phase 1: brainstorming ([intent](bmad/brainstorming/brainstorm-shatterfish-program-2026-09-03/brainstorm-intent.md)) | done, Checkpoint B answered |
| 6 | BMAD phase 1: research ([report](bmad/planning-artifacts/research/technical-shatterfish-engine-foundations-2026-09-03/research.md)) | done |
| 7 | BMAD phase 1: product brief ([brief](bmad/planning-artifacts/briefs/brief-shatterfish-2026-09-03/brief.md)) | done |
| 8 | BMAD phase 2: PRD ([prd](bmad/planning-artifacts/prds/prd-shatterfish-2026-09-03/prd.md)) | done |
| 9 | BMAD phase 2: PRD validation ([report](bmad/planning-artifacts/prds/prd-shatterfish-2026-09-03/validation-report.md)), UX spec ([experience](bmad/planning-artifacts/ux-designs/ux-shatterfish-2026-09-03/EXPERIENCE.md), [design](bmad/planning-artifacts/ux-designs/ux-shatterfish-2026-09-03/DESIGN.md)) | done, Checkpoint C approved |
| 10 | Upstream documentation ([codebase map](codebase-map.md), [rules](rules/index.md), 275 rows) | done |
| 11 | Architecture spine draft, [ADR-0005](adr/0005-observation-schema-and-hashing.md) to [ADR-0008](adr/0008-hook-guarding-and-tracking.md), fairness red team | done |
| 12 | [ADR-0009](adr/0009-snapshot-restore-and-redetermination.md) to [ADR-0015](adr/0015-headless-scene-and-input-wait-detection.md), reviewer gate, [architecture](architecture.md) spine final | done, Checkpoint D approved |
| 13 | Epics and stories ([epics](bmad/planning-artifacts/epics.md), 73 stories), two readiness checks | done, Checkpoint E pending |
| 14 | Sprint planning and issue mirroring (40 issues) | done, Checkpoint F pending |

## After bootstrap

From session 15 the program runs one story per turn: `/next-story` picks it up from the sprint
status, a branch and a pull request carry it, an isolated review reads it, and the story file, the
sprint status and the issue are updated together. The first story is [#14](https://github.com/watchthelight/shatterfish/issues/14), the touchpoint
audit.
