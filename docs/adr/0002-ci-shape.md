---
status: accepted
date: 2026-09-03
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0002: CI shape — what runs on PR, what runs nightly, how rig results are published

## Context and problem statement

Non-negotiable #5 says everything is measured and reproducible and that, once the rig exists, no brain change merges without rig numbers in the PR. Non-negotiable #1 says the fairness tests run in CI. The working agreements say rig numbers are published as `docs/results/<date>-<sha>.md` and that docs change in the same PR as code. A rig run is thousands of seeded games, far too slow for a PR gate, so the question is how to split fast checks from slow measurement and where the measurements land.

Non-negotiables touched: #1 (fairness tests in CI), #5 (measurement, reproducibility), #7 (issues/stories/docs carry state/content/knowledge).

## Decision drivers

- A PR must get a green/red answer in minutes.
- Rig numbers must end up in the repository, reviewable, attached to a commit SHA.
- One engineer; anything that needs babysitting will rot.
- Public repository: GitHub Actions minutes are free, artifacts are retained 90 days at most.

## Considered options

1. **Everything on PR**, including a reduced rig sample. Rejected: even 100 games × 2 brains is minutes today and hours once search exists; flakiness of a small sample would make the gate meaningless.
2. **PR gate + nightly rig, results only as workflow artifacts.** Rejected: artifacts expire, are not diffable, and never reach `docs/results/`.
3. **PR gate + nightly rig that commits results straight to `main`.** Rejected: unreviewed bot commits on `main` conflict with branch-per-story and "no force-push to main" culture; also breaks "docs change with the code they describe" since nobody reviews the doc.
4. **PR gate + nightly rig that opens (or updates) a single PR** adding `docs/results/<date>-<sha>.md`, with the full JSONL run logs attached as workflow artifacts. Chosen.
5. **Results on a separate `results` branch / Pages data branch.** Rejected: splits knowledge from `docs/`, and the site would need a second source.
6. **Rig only on the laptop, human commits results.** Rejected: nothing is published unless someone remembers; violates #5 in practice.
7. **OS matrix (ubuntu, windows, macos) on every PR.** Rejected for the PR gate: three times the wall time for a Java project whose only native surface is libGDX's desktop natives. Windows is added to the nightly job instead because the product owner develops on Windows (and a Windows-only Gradle daemon problem already appeared in Session 2).

## Decision outcome

Option 4, layered:

| Trigger | Job | What it runs | Gate? |
|---|---|---|---|
| every PR, every push to `main` | `build` | `./gradlew build -Pshatterfish.mobile=off` on ubuntu, JDK 21: compile all modules, JUnit 5, ArchUnit, fairness/differential/toggle/determinism tests once they exist | yes, required to merge |
| every PR, every push to `main` (from Session 3) | `docs` | `mkdocs build --strict`; deploy to GitHub Pages on `main` | yes |
| nightly on `main` (from E3) | `rig` | full seed set on the current brain vs the last published baseline, SPRT; writes `docs/results/<date>-<sha>.md`; JSONL logs as artifacts; opens or updates the PR on branch `rig/nightly`; also runs `build` on windows-latest | no; the human merges the results PR |
| manual (`workflow_dispatch`) or label `rig` on a PR (from E3) | `rig-pr` | same as nightly but on the PR head; posts the summary as a PR comment and uploads the results file as an artifact for the author to commit | required for brain changes from E3 onward |

Codex drift (E2) is a `build`-job check: regenerate and `git diff --exit-code codex/`.

### Consequences

- Good: PR feedback stays fast; every published number is reviewed and tied to a SHA; nothing lands on `main` unreviewed.
- Bad: nightly results PR needs a human click; acceptable, and it keeps the human in the loop on what the numbers say.
- Bad: the rig-on-PR path means a brain PR needs a second commit with the results file; the `rig` project skill will do that locally as the normal path, with the Action as the fallback.

## Pre-mortem

*If this is wrong in six months, why?*

- Nightly results PRs pile up unmerged. Mitigation: one branch (`rig/nightly`) updated in place, never one PR per night; the `handoff` skill lists it as open work.
- The rig outgrows a runner's 6-hour job limit. Mitigation: seed sets sharded across a matrix; the runner writes partial JSONL and a merge step aggregates. Revisit at E3.
- Windows-only failures slip through the ubuntu gate. Mitigation: nightly Windows build; promote to the PR gate if it ever catches something.
