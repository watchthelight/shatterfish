---
title: 'Story 3.11: The nightly job and the results pull request'
type: 'feature'
created: '2026-09-23'
status: 'done'
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
- [x] `shatterfish/rig/.../Nightly.java` -- judge a night, its history line, the page; `main` for `night` and `page`.
- [x] `.github/workflows/nightly.yml` -- schedule, play, record, publish, artifacts, red on failure.
- [x] `tools/nightly-pr.sh` -- rebuild `rig/nightly`, carry forward, force-push, create or edit the PR.
- [x] `shatterfish/rig/build.gradle` -- `:rig:nightly`; the workflow, script, history and page as test inputs.
- [x] `results/nightly/history.jsonl`, `docs/results/nightly.md` -- empty history, generated page.
- [x] `shatterfish/rig/src/test/.../NightlyTest.java` -- the matrix, the page, the workflow's text.
- [x] `docs/methodology.md`, `docs/results/index.md`, `mkdocs.yml` -- the nightly job described and linked.

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

## Evidence

**Rig numbers.** A local ranked night, the command the workflow plays: `smoke` under
`H-0001-nightly-smoke@a4d7fe89a612e89b`, 4 processes, **25 of 25 Runs finished in 14,090 ms**
(1.77 Runs/s, 112 waits/s), endings DEATH=23 UNKNOWN_WINDOW=2; recorded by
`./gradlew :rig:nightly --args=". night build/nightly 2026-09-23 <commit>"` as
`PASS -- nightly smoke 2026-09-23 (a direction check under H-0001-nightly-smoke, never an acceptance): 25 of 25 Runs finished`.
Its ledger line was a development run and was not committed. `./gradlew build`: 986 tests, 0
failures; `mkdocs build --strict`: green.

**What cannot be verified before the first night.** The workflow and `tools/nightly-pr.sh` run
only on GitHub; `NightlyTest` holds their text (the command against the Rig's own flags, the
branch, never `main`, `if: always()` on every reporting step, red on failure, no host but GitHub).
The branch rebuild, the carry-forward of unmerged nights and `gh pr create` first run on the first
scheduled night, or on a manual dispatch.

**Needs the owner.** The repository setting "Allow GitHub Actions to create and approve pull
requests" is off (`can_approve_pull_request_reviews: false`), so the first night's `gh pr create`
will fail and the script will say so; the branch `rig/nightly` is still pushed. Turning the
setting on, or opening the pull request from `rig/nightly` by hand once, is the owner's call; every
later night then edits that pull request.

**Review** (three lenses, run by the engineer: the forked worker that built this could not spawn
reviewers). Fixed: the record step read `$?` inside a command substitution; a night the recording
could not judge left an empty summary heading; the script dropped unmerged nights silently when the
branch's history did not extend `main`'s; a half-written summary crashed the step instead of failing
the night.

**Mutation battery: 17 mutations, 17 killed.** Four survived the first run: `incomplete > 0`
(only ever tested with `finished != started` also true), the history key check (the JSON reader
refused the test's unsorted key first), the carry-forward copy, and the job-summary warning. Each
now has a test.

**Second review** (independent reviews of #128). Fixed: `gh pr view` found merged pull requests
(now only the open one is edited); the ledger lost nights once a ranked story appended to `main`'s
(now merged as a set of lines); a night that broke the build or the Rig never reached the page
(the script now writes its failing line, and the play step's outcome is part of the verdict);
multi-line and message-less exceptions broke the status and the table; one job held a writing
token while running the Rig (now `play`, read-only with no stored credentials, and `publish`);
the push had no lease and a failed branch lookup looked like a missing branch; pushes made with
the workflow's token start no CI (the publish job runs `NightlyTest` first, and the page says so);
the date was computed twice and a re-run could append a night twice; the script could not run
outside Actions; a summary without its counts was read as zeros. Tests added: `Nightly.run` end to
end, a summary written by `RunIndex`'s own writer, and the script run against a temporary
repository with a bare remote and a stub `gh` (14 of 14 `NightlyTest`, the script test not
skipped). Docs: the repository setting, no CI on the results pull request, NFR-8 as a text check,
workflow changes exercised only after merge; a dated note on ADR-0002.

**Not re-run after the second review:** the full `./gradlew build` was stopped by the host for low
memory, and the mutation battery on the new guards (16 mutations, `mutations311b.py`, patterns
checked) was not started for the same reason. `NightlyTest` passed 14 of 14 on the committed code
and `mkdocs build --strict` passed; CI on the pull request runs the full build.
