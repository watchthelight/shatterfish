---
title: 'Story 3.10: Results pages and the methodology page'
type: 'feature'
created: '2026-09-23'
status: 'in-progress'
baseline_commit: '2ea46def8a9977d6ac63cadc992455c9f128c919'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** FR-25 asks a published number to carry everything needed to check it. Story 3.9's
pages were written by hand from the runs' own files and lack most of it: distributions, the survival
curve, the boss staircase, the prior-attempt count, the fairness-suite status, the Registration's
commit. Nothing generates a page, so the next page would be written by hand too.

**Approach:** `Results` and `./gradlew :rig:results` read a folder the Rig wrote — a Baseline or a
comparison — with its logs, its Registration and the ledger, and write two things: a small committed
data folder (`results/<slug>/`: the indexes, summaries, `comparison.json`, and one outcome line per
Run from its own log) and a generated page `docs/results/<slug>.md` with every FR-25 field. Story
3.9's runs get generated pages; the hand-written pages keep the narrative and link to them. A real
UNDECIDED result is produced and published on the same terms: `random_twin`, the random agent
reseeded (equal strength by construction), against `random` on `smoke` under a Registration whose
maximum is 25. The methodology page gains what to do when platforms disagree.

## Boundaries & Constraints

**Always:** Every number on a generated page comes from the Rig's own files, read through the
Harness's reader; the page is regenerable from the committed data folder and CI fails on drift.
Commits, tag and Oracle state come from the logs' headers and must agree across Runs.

**Ask First:** Where the Run logs themselves are published (37 MB per side): the page links them only
where a location is recorded; choosing a store is the owner's decision.

**Never:** Commit Run logs. Hand-edit a generated page. Edit a committed Registration.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Baseline folder | runs.jsonl, summary, logs | page with aggregates, no test | N/A |
| Comparison folder | candidate/, baseline/, comparison.json | page with verdict, trace, correlation | N/A |
| Logs disagree | two commits, tags or an oracle Run | refused | IllegalArgumentException |
| Regenerate | committed data folder only | same page bytes | N/A |
| Prior attempts | ledger lines for the Registration | counted, this one excluded | N/A |
| Undecided | twin against random, max 25 | UNDECIDED, published | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/rig/.../Calibration.java` -- the extract/page pattern this follows (table from logs, page from table, drift test).
- `shatterfish/rig/.../Comparison.java` -- `outcome(file)`, `comparison.json` fields.
- `shatterfish/rig/.../Ledger.java` -- `entries()`, `Entry(registration, hash, at, brain, ..., note, when)`.
- `shatterfish/rig/.../Registrations.java` -- reading a Registration by id; git for its commit.
- `shatterfish/harness/.../log/RunLogReader.java` -- header and end of each log.
- `shatterfish/rig/.../Brains.java` -- add `random_twin`.
- `docs/results/2026-09-23-*.md` -- the hand-written 3.9 pages to link to the generated ones.

## Tasks & Acceptance

**Execution:**
- [ ] `rig/Results.java` + `:rig:results` -- extract (folder → data folder) and page (data folder → Markdown); `ResultsTest`.
- [ ] `rig/Brains.java` -- `random_twin`; registration `H-0007-twin-undecided` on `smoke`, maximum 25.
- [ ] Generated pages for H-0002, H-0004, H-0005 (both), H-0006, H-0007; hand-written pages link them.
- [ ] `docs/methodology.md` -- when platforms disagree; how to read and regenerate a Results page.

**Acceptance Criteria:**
- Given a Rig folder, when `:rig:results` runs, then the page carries every FR-25 field and the committed data regenerates it byte for byte (`ResultsTest`).
- Given H-0007, then an UNDECIDED result is published on the same terms as the others.
- Rig numbers in Evidence: the H-0007 invocation and the generator's run time.

## Verification

**Commands:**
- `./gradlew build` -- green; `mkdocs build --strict` -- green.
