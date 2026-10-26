---
title: 'Epic 6 retrospective: tactical search'
type: 'retrospective'
created: '2026-10-26'
status: 'done'
---

# Epic 6 retrospective: tactical search

Written at story 6.6, from stories 6.1 to 6.6 and the pull requests behind them (#272 included).

## What the epic delivered

The one-ply expectimax over Codex tables (6.1), the redetermination scrubber and rollout host with a differential test (6.2),
the search leak test with planted leaks (6.3), the simulator-speed measurement (6.4), the properties measurement (6.5), and
the candidate comparison (6.6), which applied ADR-0010's choice rule and found that no rollout design fits a registered
per-Decision budget. The one-ply model stays the tactical layer; the measurements are published with what would reopen the
question.

## What went well

- **The measurements came before the design.** Because ADR-0010 deferred the choice to three published numbers, the epic could
  end in "no" in one story, without a half-built search to throw away.
- **The leak test has controls.** A planted leak in each of several forms is caught, so a pass means something.
- **The exactness bug was found by a measurement.** The properties run on a `standard` seed exposed that the live restore was
  not exact (#271, fixed in #272), because every restore is checked against the snapshot's Observation hash.

## What did not

- **The swap dominates.** 87% of a rollout is moving the Run's state in and out, not the rules. The decision the epic ended
  on is a decision about the host, not about search. The isolation spike (ADR-0009's JNI note) is the lever and was never
  scheduled.
- **The budget was nobody's.** Every Registration carried `budget_ms` 0 and the Overlay had none written down, so story 6.6
  had to set one. A number that decides a story should be asked for when the epic opens.
- **A shared machine.** Timings swung by 40% with what else ran (6.4's run 3). They were kept and labelled, which worked.

## Lessons (landed in CLAUDE.md)

- A rule that needs a number the product owner has not set (a budget, a ceiling) is asked for when the epic opens, or the
  story that applies the rule sets it, writes the reasoning down and gives the table that lets it be overruled.
- When a measurement page is the input to a later decision, the decision's arithmetic is a test that reads the page's
  committed log, not figures typed into prose.

## Open

- The classloader-isolation spike, as the one change that could reopen the search question.
- A budget from the product owner for the Overlay and the Rig.
