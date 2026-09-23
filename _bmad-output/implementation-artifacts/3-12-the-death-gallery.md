---
title: 'Story 3.12: The death gallery'
type: 'feature'
created: '2026-09-23'
status: 'done'
baseline_commit: '2ea46def8bb4d9de3ad2f84a2af66c5cbb9c95c6'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** FR-26: after a Rig invocation, the developer needs to see how the bot dies, grouped by
cause, to know what to fix next. The Rig writes an index and a summary, neither of which says how
the Runs ended in a form a person can scan.

**Approach:** `Gallery` reads each Run's ending from its own log through the harness reader, groups
the Runs by cause and depth (largest group first), and writes `gallery.md` beside the side's
summary: counts, shares, and per group the seed code, class, turns survived and a link to the log.
The Runner writes it when an invocation completes; `./gradlew :rig:gallery --args="<folder>
[--snapshots N]"` rewrites it for any folder and, on request, writes the last N waits of each Run
into `snapshots/`, which the gallery links. The per-Brain comparison view is E4's.

## Boundaries & Constraints

**Always:** Every Run in the index appears in exactly one group — a Run with no log, no ending, or
an unreadable log is grouped as such, never dropped. Endings come from the logs, not the index.
Plain Markdown (NFR-9). The same folder gives the same page.

**Ask First:** Adding a Runner flag (the flag list is asserted by name).

**Never:** Read a log through `RunLogJson`. Commit Run logs.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Deaths | logs ending DEATH at depths 1 and 2 | groups by (cause, depth), largest first | N/A |
| No log | index line, file absent | group `NO_LOG`, depth — | N/A |
| No ending | log with header only | group `NO_ENDING` | N/A |
| Unreadable | not a log | group `UNREADABLE` | N/A |
| Snapshots | `--snapshots N` | `snapshots/<run id>.md` with the last N waits, linked | N < 1 refused |
| Bad index | no run id, or a log outside the folder | refused | IllegalArgumentException |

</frozen-after-approval>

## Code Map

- `shatterfish/harness/.../log/RunLogReader.java` -- `Log.end()`, `waits()`, `readable()`: the only reader.
- `shatterfish/rig/.../Runner.java` -- after each side's summary: where the gallery is written.
- `shatterfish/rig/.../RunIndex.java` -- `RUNS`, `SUMMARY`; index lines carry `runId`, `log`, `class`.
- `shatterfish/rig/build.gradle` -- JavaExec task pattern (`calibrate`).
- `reference/` -- a committed real Run log, for the snapshot test.

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/rig/.../Gallery.java` -- grouping, page, snapshot, write, main.
- [x] `shatterfish/rig/.../Runner.java` -- write the gallery after each side's summary.
- [x] `shatterfish/rig/build.gradle` -- `:rig:gallery`.
- [x] `shatterfish/rig/src/test/.../GalleryTest.java`; `RunnerComparisonTest` asserts the Runner wrote it.
- [x] `results/2026-09-23-H-0002/gallery.md` -- a real gallery, from the H-0002 logs.
- [x] `docs/methodology.md`; the Baseline Results page links the gallery.

**Acceptance Criteria:**
- Given a Rig invocation, when it completes, then `gallery.md` groups its Runs by cause and depth with counts and seeds (`RunnerComparisonTest`, `GalleryTest`).
- Given `--snapshots N`, then each entry links a snapshot of its last N waits (`GalleryTest`).
- Given the gallery, then it is plain Markdown and says the per-Brain view is deferred to E4.
- Rig numbers in Evidence: the gallery's time on a 500-Run folder.

## Verification

**Commands:**
- `./gradlew build` -- green.
- `./gradlew :rig:gallery --args="<folder> --snapshots 5"` -- writes `gallery.md` and `snapshots/`.

## Evidence

**Rig numbers.** `./gradlew :rig:gallery` on the H-0002 folder (500 Runs of the random Brain on
`standard`, 37 MB of logs): **1,377 ms** for the gallery alone, **2,917 ms** with `--snapshots 5`
(500 snapshot pages), by the task's own clock. The Runner writes the gallery after each side's
summary, so a 500-Run invocation pays about 1.4 s on top of its ~177 s. The gallery counts agree
with the Baseline page: 446 `DEATH` at depth 1, 47 `UNKNOWN_WINDOW` at depth 1, 7 `DEATH` at depth 2.
The published gallery is `results/2026-09-23-H-0002/gallery.md`; its log links name logs that are
not committed.

**Deviations.** No Runner flag: the Runner always writes the plain gallery, and snapshots are
`:rig:gallery --snapshots N`, so `Runner.KNOWN` and the oracle gate's flag count are untouched.
Endings the game did not decide are shown as groups of their own (`NO_LOG`, `NO_ENDING`,
`UNREADABLE`) rather than dropped. The gallery links pinned to the commit that added it, since
`DocsCitationTest` resolves `blob/main` links at `main`, where the file does not yet exist.

**Review.** This story was built by a worker that cannot launch review subagents, so the three
lenses (blind, edge-case, verification-gap) were run by the worker itself over the diff. Found and
fixed: a snapshot's file name came from the index's run id, so a line saying `"runId":"../x"` wrote
outside `snapshots/` (now named for the log, which the index check confines); snapshots of Runs no
longer in the index survived a rewrite (cleared); an exception from the gallery would have cost a
ranked invocation its ledger line (now reported, not thrown); the doc links failed the citation
check. The parent may want an independent review before merging.

**Mutation battery: 16 mutations, 16 killed.** Two survived the first run: `UNREADABLE` without the
readability check (the fixture had no records at all; a log unreadable after its header now tests
it), and the snapshot name, where the test looked in a temp directory shared with other tests and
the mutation's escape left a stray file there -- the test now looks inside its own folder.

**Deferred.** The per-Brain comparison view is E4's half of FR-26, said on every gallery page.

**Second review (independent reviews of #127), fixed in `8038dc98f`.** The page now puts the
endings the game decided (deaths, wins) and the Runs it did not end in separate sections, and only
the first says the largest group is the place to look. It states that the log's `Outcome` records
no killer (no mob, trap or hunger), so the gallery groups by ending and depth; the killer is in
`docs/ideas.md` and `deferred-work.md`. Also fixed:
- A log the reader throws on is one `UNREADABLE` Run, and its snapshot says so.
- An index naming a Run or a log twice is refused.
- `NO_ENDING` takes the depth of its last wait.
- Snapshots are cleared on every rewrite, even without `--snapshots`, and only the gallery's own
  `.md` files; the page is written to a temporary file and moved into place before any stale
  snapshot is deleted.
- Every cell and link is escaped, and turns are whole turns.
- The Runner's guard catches `Error` too, and a ranked-invocation test proves a failing gallery
  keeps the ledger line.
- `build312.log` was committed by mistake and is removed.
- `:rig:gallery` is in CLAUDE.md and the rig skill.
- The H-0002 gallery is regenerated from the final code: 1,962 ms on the 500-Run folder.

**Verification state after the second review.** `GalleryTest` (12) passes and `mkdocs build
--strict` passes on the final code. The full `./gradlew build` and the second mutation battery
(19 mutations on the new guards, `mutations312b.py`) have **not** run on it: Claude Code stopped
the build because the machine ran low on memory, and a stopped build is not restarted without being
asked. Before that, the full build was green on the first review patch (983 tests; its one failure,
the doc citation, was fixed). CI on the PR is the full build for this version.

## Suggested Review Order

- Grouping from each Run's own log, and what is not an ending.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Gallery.java`
- Written after each side's summary, and never at the ledger's expense.
  `shatterfish/rig/src/main/java/org/shatterfish/rig/Runner.java`
- The cases, with hand-written logs and the reference Run.
  `shatterfish/rig/src/test/java/org/shatterfish/rig/GalleryTest.java`
- The method, for a reader.
  `docs/methodology.md`
