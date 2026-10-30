---
title: 'Story 10.7: The Prison diagnosis'
type: 'feature'
created: '2026-10-30'
status: 'review'
baseline_commit: '332473aa4'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** The mob stories 10.8 to 10.16 were planned from folklore. The product owner asked for the killers of depths 6 to 9, per class,
with food and the Goo first, so that the order of work comes from evidence.

**Approach:** Play 160 Runs per class on `standard` with the current Brain, 80 at salt base 1000 and 80 at salt base 3000, one side and no
baseline, with `--debug-record`; extract a committed summary; write the page `docs/results/2026-10-30-10-7-the-prison-diagnosis.md`; check the
page against the summary in a test; add the stories the table shows are missing.

## Decisions

- **What the test reads (the log question).** The full logs are about 1.8 MB a Run, 960 of them about 1.7 GB: not committed. A committed summary
  extracted from them, with its extraction script, is the "logs it names": `docs/results/logs/2026-10-30-10-7-prison-diagnosis/`
  (`runs.tsv` 61 KB, `arrivals.tsv` 80 KB, `moments.tsv` 8 KB), written by `tools/prison-diagnosis.py` from the two direction folders. The
  summary holds one row per Run, so every killer, hunger, reach and Goo figure on the page is an aggregate the test recomputes in Java, apart from
  the Python. The step from full log to summary is the script and is not tested in CI; the test holds the seed and salt of each row to the
  `standard` set (`the_summary_is_the_two_slices`), so a re-run of the two commands reproduces the rows. The 20 dearest Decisions are
  committed as a list and checked for count and order only: recomputing them needs the debug records.
- **HP lost per decision.** The debug record keeps a wait when an enemy is in view or the log gained a line. A Decision's loss is its wait's HP
  minus the next wait's HP when wait `k+1` is in the record too, and for the last wait of a hero that died, the HP it had. The Policy is the Run
  log's `decision.policy`. The strategy log has no HP; the debug record has it.
- **Hunger share.** Three readings, all on the page: the killer the game names (Hunger), STARVING on the last Decision, and HUNGRY on it. The
  second is the one that carries the finding, because a starving hero is credited to the mob that lands the last blow.
- **Reach each story would buy.** No Run arrives on depth 9, so depth 10 has no figure. The page uses a model, `+d8`: the expected Runs more
  reaching depth 8 if the family never killed on depths 5 to 7. It is a ceiling, stated as one on the page. Computed in 40-digit decimals in
  the tool and in the test so the rounding agrees.
- **Correct-course rule.** A killer family that is at least 5% of all 960 deaths and owned by no story gets a story. Goo (13.1%), Crab (13.2%),
  Gnoll (7.0%) and Snake (6.8%) pass; Hunger (11.3%) has a story that is done, whose next lever is new. Added: 10.38 the Goo fight (#422), 10.39
  Prison food, the second pass (#423), 10.40 the Sewers killers (#424). Issues made by hand with `gh` (the sync script would have rewritten 68
  other issues).

## Acceptance criteria and where they are held

1. Diagnosis page with reach d5/d10, deaths by depth and killer per class, level and strength by depth, hunger share, the 20 dearest
   Decisions per class with the Policy: the page.
2. `PrisonDiagnosisPageTest` checks every table against the committed summary, holds the 20 moments per class, holds the seeds and salts, and has a
   control (a planted change to one Run must change the tables and must not match the page).
3. The page ends with the ordered list of the mob stories and the reach each would buy: the last section.
4. Killer families outside 10.8 to 10.16: 10.38 to 10.40 in `epics.md`, `sprint-status.yaml` and three issues.

## Not done

The Run-by-Run reading of the Goo deaths (what each Goo fight lost and where the `fallback` Decisions come from) is story 10.38's. The Duelist's
flat strength of 10.0 on every depth is not explained here; it is the first thing story 10.17 reads.
