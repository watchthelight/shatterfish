# The merge gate regenerates the generated docs itself

Two generated files conflict whenever two PRs that touch the registry or the Results pages are open at once:
`docs/brain-rules.md` (the heuristics table, rewritten by `HeuristicLinkTest` with `SHATTERFISH_WRITE_HEURISTICS=1`)
and `docs/results/2026-10-27-7-10-the-measured-win-rate.md` (the link count, rewritten by `WinRateTest` with
`SHATTERFISH_WRITE_WINRATE=1`). On 2026-10-31, #430 conflicted twice in one hour and needed a regen after each
merge of main, each waiting on the gradle gate.

## The idea

The merge gate (`mpr.sh`), after it merges main into the PR branch and before it builds, takes the main side of
every generated file, runs the two write-mode tests, and commits the result as one "Regenerate generated docs"
commit. A PR then never has to resolve or regenerate them by hand, and CI's drift check still holds because the
files are regenerated from the merged code.

## Why not now

- It changes the gate, which every queued PR depends on, in the middle of a merge run.
- The one-at-a-time merge order already avoids most of the conflicts.
- The write-mode tests must run before the build is trusted; the order inside the gate needs a design (an ADR).

## What to measure first

How many gate runs in a week stop on a generated-doc conflict, and how long the regen waits on the gradle gate.
