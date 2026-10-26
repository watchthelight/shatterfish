---
title: 'Story 6.6: The candidate comparison under the Sequential test'
type: 'feature'
created: '2026-10-26'
status: 'review'
baseline_commit: '56620de0c'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** ADR-0010 lets a search by engine rollouts ship only if the Rig accepts it against the one-ply model at equal budget.
Stories 6.3 to 6.5 measured the leak test, the simulator and the properties. Which design, if any, may be registered?

**Approach:** apply the choice rule to the measurements. Check rule 1 first, ask of each of options 3, 4 and 5 whether the
search it is configured for fits a registered per-Decision budget, register and compare only those that do, and close the
epic on the outcome, published whichever it is.

## Acceptance Criteria (from epics.md Story 6.6, with the tests that hold them)

1. Rule 1 is checked first and the page says which design was or was not registered and why. Held by `SearchLeakTest` (the
   probe passes; options 3, 4 and 5 have no code to test).
2. Among designs that fit the budget, at most options 3, 4 and 5 are implemented, each in its own pull request; the rest are
   recorded as not attempted with the reason. If none fits, no candidate is built. Held by `CandidateFitTest`
   (`the_costs_are_read_from_the_committed_report`, `the_ceiling_is_the_budget_at_which_search_doubles_a_run`,
   `no_design_fits_the_registered_budget_and_each_needs_more`, `the_table_names_every_option_and_its_verdict`): the fit
   arithmetic is recomputed from story 6.4's committed report and not typed on the page.
3. The Registration of any candidate would name the baseline, the `standard` set, `budgetMs` and `machineClass`, the Per-pair
   GSPRT parameters and the burn-in and maximum of ADR-0012 and ADR-0018; `bosses` beside it; `holdout` once at the end. **Rig
   numbers: none, because no design fits and no candidate was registered.** The page says so, says that `standard`, `bosses`
   and `holdout` were not run, and why `holdout` was kept.
4. The epic closes on the one-ply model: the Results page, ADR-0010 amended with the decision and what would reopen it,
   `docs/roadmap.md`, `sprint-status.yaml`, the E6 retrospective and the lessons in `CLAUDE.md`, in the same pull request.
   The docs build passes `--strict`.

</frozen-after-approval>

## Code Map

- `shatterfish/harness/src/test/java/org/shatterfish/harness/agent/CandidateFit.java`: the configurations of options 3, 4 and
  5 (the least search that is still a search), the cost model (rollouts times a 43.486 ms cycle plus a scrub per world), the
  Run-time ceiling, and the Markdown. Reads the medians from `docs/results/logs/2026-10-26-simulator-speed-run1.txt`.
- `CandidateFitTest.java` beside it; `:harness:candidateFit` in `shatterfish/harness/build.gradle` prints the table.
- `docs/results/2026-10-26-6-6-the-candidate-comparison.md`, `docs/results/index.md`, `mkdocs.yml`, ADR-0010's story 6.6
  amendment, `docs/roadmap.md`, the E6 retrospective, `CLAUDE.md`.
- No production code, no Brain change, no Observer change.

## Decisions

- **Micro-brainstorm of the question "what is the budget".** (1) 0 as the other Registrations carry it, which makes "fits"
  meaningless. (2) The Overlay's patience, which nobody has written down. (3) A Run-time ceiling from the Rig's throughput
  (ADR-0010's second driver), from a count of 20 Run logs: a search may at most double a Run, 39 ms, registered as 30 ms. (4) The
  least budget at which each design fits, which makes every design fit by construction. (5) Ask the product owner before
  anything, which blocks a story whose answer the table gives for any budget. Chosen: (3), with the table that gives (4) beside
  it so the product owner can overrule the ceiling by reading one column.
- **The least search is the probe's shape for option 3** (three Actions in two worlds, six rollouts), the same two worlds for
  option 5 (nine leaves of a depth-two tree of three Actions), and a stated floor of 100 iterations for option 4. They are the
  engineer's and are labelled so; the probe's is code.
- **No candidate is built.** Building one without a budget it fits would produce a Rig table at a budget nobody registered. A
  larger budget is the product owner's decision (cost: about 8 times the Rig time per Run for option 3).
- **`holdout` untouched**, because there is no comparison to confirm and its budget is spent once. `bosses` unrun for the same
  reason.
- **Alternatives rejected.** Build option 3 at 270 ms anyway (a very long Rig batch on a budget the product owner has not
  chosen, against a quota that is tight); a search that falls back to the one-ply model when its time runs out (a different
  design, recorded as a possible new candidate).

## Pre-mortem

| Cause | Guard |
|---|---|
| The ceiling is argued to be too tight | the page gives each design's fitting budget; the ADR says the ceiling is the engineer's |
| Hand-typed arithmetic drifts from the 6.4 report | `CandidateFitTest` reads the report |
| "None fits" read as "search does not help" | the page and the ADR say it is a statement about this simulator and budget |

## Verification

`./gradlew :harness:test --tests '*CandidateFitTest'`, `:harness:candidateFit`, the strict docs build,
`fairness-reviewer` on the diff before the pull request.
