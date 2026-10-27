---
title: 'Epic 7 retrospective: strategy and lore'
type: 'retrospective'
created: '2026-10-27'
status: 'done'
---

# Epic 7 retrospective: strategy and lore

Written at story 7.10, from stories 7.1 to 7.9 (story files, their Results sections, the pull requests #289 and #293 to #311) and
7.10's own record. Run headless: the owner's questions were answered from the record, and the assumptions are listed at the end.

## What the epic delivered

- **A count of what the Brain decides by (7.1).** `@Heuristic` on every named threshold, odds and budget, found by reflection and
  checked by `HeuristicLinkTest` against the Rules index; 71 found at the start, 35 linked to a Rule, 5 to a measurement, 31 unlinked.
- **Strategy as data (7.2 to 7.6).** The Playbook (ADR-0023): a typed, validated, canonical file that is part of a Brain's
  configuration hash; item identification fields (7.3), scroll-of-upgrade allocation (7.4), per-boss entries for Goo (7.5) and per-class
  entries, of which only the Mage's zap entry changed play (7.6: reach depth 5 6/40 to 18/40 on the tuning slice, 8/80 to 38/80 on the second).
- **A Lore intake (7.7 to 7.9).** A claim format with five refusal patterns and a citation checker that flags claims whose cited lines
  moved (41 of 144 flagged); a variant classifier from the vocabulary diff (38 of 144 decided, none contradicted); a date gate from the
  game's own changelog (142 of 144 `undated`, 1 `changed-since`, 1 clean).
- **The measured win rate and the link count at the close (7.10).** See its Results page; this document does not repeat the numbers.

## What went well

- **Equivalence before improvement.** Each story that was not meant to change play proved it: 7.1 by the SHA-256 of 40 normalised Run
  logs on both sides, 7.2 by record-for-record equality (39 of 40, one unexplained and said so), 7.5 by 40 of 40 and 80 of 80 equal
  columns. When a story did change play (7.4, 7.6) the table showed it on two slices.
- **Thresholds written before the first run (7.8, 7.9).** 7.9 changed one band after the report was first generated and recorded the
  amendment and the reason in the story instead of hiding it.
- **A gate was given a way to fail.** 7.9 probed the date gate with synthetic anchors (oldest release, `v3.3`, newest) so a gate that
  flags nothing, or everything, would show; 7.1 added a bite test that the annotation opens no channel for the Brain.
- **The fairness review caught real notes** at 7.1, 7.2 and 7.5 (the allowlist widening, the `RunOne` fallback, boss floors citing
  `Dungeon.java:441-443`), none a violation, all closed in the story.

## What did not

- **The unlinked count never fell.** It was 31 at 7.1 and 31 at 7.10: every new heuristic was linked when it was born, and the 31
  old ones had no owner, so a ceiling that "may fall and may not rise" only held still (`HeuristicLinkTest`, 7.1 and 7.2 Results). At the
  close none could be linked honestly: each is an assumption or a tuned value that no Rule states and no Results page played, so the
  allowlist now carries a reason and an owning issue for each (#312 to #315).
- **Parallel stories collided on shared registers.** 7.1 had to merge 7.7 mid-pull-request (a slug may now be a Lore claim id); 7.5
  needed a follow-up commit after 7.6 landed (`4a5a07d4d`, the Playbook constructor's kit argument); the identification row count had
  to be fixed on `main` after 7.3 and 7.4 each added one (`eb36d0e2e`). E5's lesson (merge dependent PRs one at a time and build after
  each) was in CLAUDE.md and still cost three repairs.
- **Most of the Lore is undated and unclassified.** 142 of 144 claims carry no date or version, 106 of 144 have no decidable variant,
  and 41 are flagged by citation. The intake now refuses new claims of the wrong kind; it does not repair the 144 that were migrated,
  which is a person's work.
- **Perfect fixture scores say little.** 7.8's classifier scored precision and recall 1.00 on its 48-claim fixture; the real check was
  the 144, where 45 claims carry `spd` that the classifier cannot confirm.
- **No target and no go-ahead.** The win rate had no target (settled on #289), and the `holdout` set needs an explicit go-ahead in the
  pull request thread, which had not come when this PR opened; the release-level number was not taken.

## Lessons (landed in CLAUDE.md)

- A ceiling on an allowlist holds the count still, it does not lower it: every entry gets its reason and an owning issue when it is
  added, not at the close.
- A story that must not change play proves it with the normalised Run logs (header, chain and wall time set aside), not with a table
  alone; a table with equal columns can hide logs that differ.
- A gate over data that is mostly missing needs a control that shows it discriminates (a synthetic anchor, a planted case), and a
  threshold changed after a first run is written into the story with its reason.
- Registers that every story adds a row to (the Rules index, the Playbook constructor, a count in a header) collide even when the
  stories are independent; merge one at a time, or put the count where it is computed and not typed.
- A number that needs the product owner's word (a win rate to beat, the `holdout` go-ahead) is asked for when the epic opens and again
  when the story that needs it opens; the story states what it did when the answer was missing.

## Open

- The 31 unlinked heuristics: #312 (fight budgets), #313 (explore, descend, larder allowances), #314 (loop backstops), #315 (reach constants).
- The `holdout` confirmation run, on the product owner's go-ahead in the 7.10 pull request.
- Dating and re-reading the 144 migrated claims (a person's work, or a re-collection that records a version at intake).
- The Goo gate (4.14) was not waited for, as settled on #289.

## Assumptions made without the owner (headless)

- Epic 7 was the epic named in the invocation; all of 7.1 to 7.9 are `done` in `sprint-status.yaml`, 7.10 is in review, so the machine
  verdict is not `accepted` yet: **accepted-with-open-items**, to become accepted when the 7.10 pull request merges.
- The aggregate scripts (`git_evidence.py` and the duplication map) were not run; the retrospective is read from the story files, the
  Results sections, the merge log and the issues. The narrowing is recorded here rather than hidden.
- No behaviour check beyond the Rig measurement of 7.10 was run.
- The lessons above are the ones the record shows; the owner has not seen them.
