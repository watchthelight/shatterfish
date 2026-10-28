---
title: 'Story 7.10: The measured win rate and the epic close'
type: 'feature'
created: '2026-10-27'
status: 'review'
baseline_commit: '583ebac892a50a1103d33dd0a22ba40dbabf8574'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** Epic 7 ends on a number and a count, and neither exists yet. No Run of the Brain has been published over all
six classes of `standard`, the Profile 6 baseline at `7b1212eea` covers only 120 Warrior Runs, and
`HeuristicLinkTest` still carries 31 unlinked heuristics.

**Approach:** Register and play the final Brain over the 500 `standard` triples once, publish wins out of Runs with the
Wilson interval beside a Profile 6 baseline for the same set; close `HeuristicLinkTest` by linking every heuristic that a
real Rule row can honestly account for and leaving the rest on an allowlist that carries a reason and an owning issue;
run the E7 retrospective; update the docs the epic changed.

## Acceptance Criteria (from epics.md Story 7.10, with the tests and numbers that hold them)

1. The final Brain is registered (`registrations/H-0008-shatterfish-standard-e7-close.json`, committed before the Runs)
   and played over the 500 `standard` triples in one pass under the Rig's turn cap; the Results page reports wins out of
   Runs with the Wilson interval, the Score distribution of any wins, deaths by depth, reach depth 5 and Goo kills over
   reach, for all classes and for the Warrior slice, each beside the Profile 6 baseline at `7b1212eea`. Held by
   `ResultsTest` (generated page has not drifted) and `WinRateTest` (the Wilson interval, a zero reported with its upper
   bound, the committed outcomes reproduce the page's figures).
2. A win rate of zero is reported as zero with its upper bound and the page says what the number can and cannot show.
3. `holdout` is used only on the product owner's explicit go-ahead in the pull request thread. None exists, so it is not
   run, the Results page says the release-level number was not taken, and the PR asks for the go-ahead.
4. `HeuristicLinkTest` has the allowlist as a map of constant to reason and owning issue; a reason without an issue
   number fails (`every_unlinked_entry_names_a_reason_and_an_owning_issue`); the count at the head of `docs/brain-rules.md`
   is the allowlist's count (`BrainRulesIndexTest` / `the_table_has_not_drifted`). The Results page reports heuristics
   found, linked to a Rule, to a Lore claim, at each Tier, and unlinked, against story 7.1's baseline; a Tier 3 link on a
   shipped heuristic is allowed only with a Results page as evidence and the page lists them.
5. The E7 retrospective is written (`epic-7-retrospective.md`), its lessons are in `CLAUDE.md`, `docs/roadmap.md`,
   `sprint-status.yaml`, `docs/architecture.md`, `docs/fairness.md` and the `/upstream-sync` skill's re-verification list
   (the lore citation check) are updated. Epic 7 stays `in-progress` until this PR merges.

</frozen-after-approval>

## Decisions (made by the engineer, the product owner having delegated them)

- **No target.** None was set. The measure is the Rig's: a win is a Run whose ending is a win; the measure is not changed to close the epic.
- **Expectation, written before any Run.** The Brain has reached depth 5 in 60% of 40 Warriors and has not been seen to win
  one Run. The expected result is 0 wins in 500, a Wilson upper bound of 0.76%. A non-zero count would be news to check
  for a harness fault first.
- **Baseline.** The Profile 6 page at `7b1212eea` covers 120 Warrior Runs under tuning salts and does not cover the set.
  The all-class baseline is therefore played again, at `7b1212eea`, in a worktree, under no Registration (an unranked
  measurement, said so on the page), and the Warrior slice is read from the same 500-Run pass so both sides are the same triples.
  Salts are drawn per Run (ADR-0007), so the two sides are distributions on the same triples, not paired Runs.
- **Link rule.** A heuristic links only to a Rule row whose claim states its value or the mechanic that sets it, with a
  `path:line` at `v4.0.2`. A tuned value that no Rule states stays unlinked; the Rule for the mechanic it protects does not
  make a margin or a count of waits a Codex fact. Entries left on the allowlist carry the reason and an issue.
- **Holdout and Goo.** Neither is run.

## Verification

- `./gradlew :api:test :brain:test :rig:test -Pshatterfish.testForks=1`, `:codex:test` if docs/rules change.
- `mkdocs build --strict`.
- Fairness: the diff changes `@Heuristic` annotations in `brain` only (no logic); `fairness-reviewer` runs on it.

## Results

See `docs/results/2026-10-27-7-10-the-measured-win-rate.md` (tables generated from the committed outcomes, held by `WinRateTest`).

- **Win rate.** 0 of 500 for the final Brain and 0 of 500 for the Profile 6 baseline played again; Wilson 95% upper bound 0.76% for
  each; the Warrior slice (84 triples) is 0 of 84, upper bound 4.37%. The expectation written above (0 of 500) held.
- **Movement.** Reach of depth 5: 95/500 to 206/500 (Warrior 40/84 to 57/84). Goo kills over reach: 54/95 to 133/206 (Warrior 29/40 to
  49/57). Not a paired test; one machine, one pass each.
- **Ending.** One final-Brain Mage Run ended `UNSERVED_SCENE` after a ranged throw (#328). No Run hit the turn cap.
- **Link count.** 80 heuristics found, 44 linked to a Rule, 0 to a Lore claim, 5 to a measured row (Tier 3), 31 unlinked, against 71,
  35, 0, 5 and 31 at story 7.1. No heuristic could be honestly linked; each unlinked entry carries its reason and an owning issue
  (#312 to #315), held by `every_unlinked_entry_names_a_reason_and_an_owning_issue`.
- **Holdout.** Not run in this story (no go-ahead existed). Run once afterwards, with the go-ahead on #329, as `docs/results/2026-10-27-e7-holdout.md`.
- **Epic close.** `epic-7-retrospective.md` written; lessons in `CLAUDE.md`; `docs/roadmap.md`, `docs/architecture.md`,
  `docs/fairness.md` and the `/upstream-sync` re-verification list updated; epic 7 stays `in-progress` until this PR merges.

## Review

No `brain`, `harness`, `Observer` or `ActionExecutor` file changed (the diff is test sources, Results data, docs and the
Registration), so `fairness-reviewer` was not run; the PR says why. Risks kept, not fixed: the baseline is an unranked re-run;
the Warrior and Mage slices include triples the Brain was tuned on; a Goo kill is "killed at least one boss".
