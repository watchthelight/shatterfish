---
title: 'Story 3.12: The death gallery'
type: 'feature'
created: '2026-09-23'
status: 'in-review'
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
