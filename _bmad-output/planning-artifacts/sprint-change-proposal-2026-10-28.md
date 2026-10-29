# Sprint Change Proposal: Epic 10, Through the Prison: Tengu

Date: 2026-10-28. Run headless (`bmad-correct-course`); every decision taken from the approved plan
`resilient-herding-moonbeam` and the product owner's answers of the same day. Scope: moderate (backlog
reorganisation, one new epic, one new success metric).

## 1. Issue summary

The program's goal is a Win (SM-1). After E7, 0 of 1,000 `standard` and `holdout` Runs reached depth 10;
deaths pile up on depths 6 and 7; the Brain has no Prison or Tengu code, and `Playbook.Bosses.tengu` is
`Later`. The Rig cannot see past Goo: `:rig:direction` counts `bosses() >= 1` as "Goo kills", has no
reach-depth-10 or Tengu column, and keeps killers only in `--debug-record`. Six of the seven #219 levers
measured on 2026-10-28 gave nothing, so one-rule tweaks will not close the gap. The product owner set the
next rung: Tengu killed in 95% or more of Runs, for all six classes.

## 2. Impact analysis

- **Epics.** New Epic 10 (37 stories). E9 stays optional and deferred. E8's remaining stories and the 4.14
  Goo gate continue unchanged.
- **PRD.** New success metric SM-9 (the Tengu rung) beside SM-3; FR-20 gains the `tengu` Seed set (600
  triples, 100 per class).
- **Architecture.** Amendments are planned inside stories, not made here: ADR-0011 (killer field, 10.2),
  ADR-0018 (`tengu` set, 10.4), ADR-0010 (no fixed think cap, 10.22), a classloader isolation ADR (10.23)
  and a tuning-method ADR (10.35). No upstream edit is planned; the practice harness and the rollouts are
  designed to need none.
- **UX.** None.

## 3. Recommended approach

Direct adjustment: add the epic. Instruments first (10.1 to 10.6), then Prison survival and the Tengu fight
in parallel on the practice harness, search as a spike with a conditional build (10.22 to 10.24), one story
per class kit (10.25 to 10.30), SPSA tuning once the instruments land (10.35 to 10.37), and the three rung
checks (10.32 to 10.34). The registered `tengu` run is pre-approved by the product owner and plays once.
Risk: reach may not move far enough on lever stories; mitigated by the diagnosis (10.7) ordering the work
and by correct-course follow-ups the checks open. Effort is large; the epic is a program of independent
stories, up to five Gradle jobs at once.

## 4. Change proposals

| Artifact | Change |
|---|---|
| `epics.md` | Epic List row E10; Epic 10 section with 37 stories, the owner's answers, the staircase, the arithmetic and the decisions the plan left open |
| `prd.md` | SM-9 added; FR-20's set list names `tengu` |
| `sprint-status.yaml` | `epic-10` and 37 stories, all `backlog`, with issue numbers |
| `docs/roadmap.md`, `README.md` | E10 row |
| `sync_issues.py` | E10 in scope; epic-list regex accepts two-digit epics |

## 5. Handoff

Moderate scope: product owner and developer. Success: R1 and R2 published, then the registered run met
under the bars in Epic 10. First stories: 10.1 to 10.5 and 10.23 in parallel.
