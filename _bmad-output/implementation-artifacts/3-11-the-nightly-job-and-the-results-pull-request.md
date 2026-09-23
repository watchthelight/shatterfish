---
title: 'Story 3.11: The nightly job and the results pull request'
type: 'feature'
created: '2026-09-23'
status: 'in-progress'
baseline_commit: '34f8f032ee27eb5f37f91c060a9c4ecab221bc5e'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** H-0001-nightly-smoke exists so that a change which breaks the Harness is seen the night
it lands, but nothing runs it at night. ADR-0002 settles the shape — a nightly job that updates one
results pull request, never `main` — and nothing implements it.

**Approach:** A `nightly` workflow plays `smoke` under H-0001 on `main`, then `Nightly` (rig) judges
the night from the Rig's own summary and index and appends it to `results/nightly/history.jsonl`, and
generates `docs/results/nightly.md` from that history. `tools/nightly-pr.sh` rebuilds the branch
`rig/nightly` on `main` each night, carrying unmerged nights forward, force-pushes it and updates the
one pull request. The status line is the job summary, the pull request's title and body and the
page's first column, and a failed night turns the job red after it is published.

## Boundaries & Constraints

**Always:** Every night is labelled a direction check, never an acceptance. The job never pushes
`main`. Commits are made as watchthelight. The Rig and the recording make no network call; only git
and `gh` reach GitHub (NFR-8). The page is generated from the history, never edited.

**Ask First:** Enabling "Allow GitHub Actions to create and approve pull requests" on the
repository (a setting, and currently off). Running comparisons nightly.

**Never:** One pull request per night. A night dropped because the previous one was not merged.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Good night | ranked under H-0001, 25 of 25 finished | PASS, endings counted | N/A |
| Refused or stopped | no summary.json | FAIL "the Rig wrote no summary" | job red |
| Unranked or other Registration | registration not `H-0001-nightly-smoke@…` | FAIL, says so | job red |
| Wrong set or Brain | not random on smoke | FAIL | job red |
| Short or incomplete | started ≠ 25, or incomplete / unaccounted > 0 | FAIL with counts | job red |
| Unmerged previous nights | branch history longer than main's, main's a prefix | carried forward | N/A |

</frozen-after-approval>

## Code Map

- `.github/workflows/replay.yml` -- the existing nightly job's shape; `ReferenceLogTest` reads it.
- `docs/adr/0002-ci-shape.md` -- one results PR on `rig/nightly`, logs as artifacts, a human merges.
- `registrations/H-0001-nightly-smoke.json` -- the standing baseline Registration.
- `shatterfish/rig/.../Runner.java` -- `arguments`, `KNOWN`; the summary and index it writes.
- `shatterfish/rig/.../RunIndex.java` -- `RUNS`, `SUMMARY`, the index's `cause`.

## Tasks & Acceptance

**Execution:**
- [ ] `shatterfish/rig/.../Nightly.java` -- judge a night, its history line, the page; `main` for `night` and `page`.
- [ ] `.github/workflows/nightly.yml` -- schedule, play, record, publish, artifacts, red on failure.
- [ ] `tools/nightly-pr.sh` -- rebuild `rig/nightly`, carry forward, force-push, create or edit the PR.
- [ ] `shatterfish/rig/build.gradle` -- `:rig:nightly`; the workflow, script, history and page as test inputs.
- [ ] `results/nightly/history.jsonl`, `docs/results/nightly.md` -- empty history, generated page.
- [ ] `shatterfish/rig/src/test/.../NightlyTest.java` -- the matrix, the page, the workflow's text.
- [ ] `docs/methodology.md`, `docs/results/index.md`, `mkdocs.yml` -- the nightly job described and linked.

**Acceptance Criteria:**
- Given the workflow, when it runs, then it plays `smoke` under H-0001 with flags the Rig knows and updates one pull request on `rig/nightly` (`NightlyTest`).
- Given any night, then its status says direction check, never acceptance (`NightlyTest`).
- Given a failed night, then its reason is in the job summary, the PR and the page, and the job is red (`NightlyTest`).
- Given the workflow and its script, then no host but GitHub is named (`NightlyTest`).
- Rig numbers in Evidence: a local ranked smoke night, recorded by `Nightly`.

## Verification

**Commands:**
- `./gradlew :rig:run --args="--brain random --seeds smoke --parallel 4 --out build/nightly --registration H-0001-nightly-smoke"` then `./gradlew :rig:nightly --args=". night build/nightly <date> <commit>"` -- PASS.
- `./gradlew build` -- green.
