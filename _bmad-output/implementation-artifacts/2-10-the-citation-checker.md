---
title: 'The citation checker'
type: 'feature'
created: '2026-09-21'
status: 'in-progress'
baseline_commit: '636f66fdc'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The documentation carries 1,834 `path:line` citations, and nothing checks that any of
them still points at what it claims. A citation that has quietly stopped resolving is exactly the
folklore non-negotiable 8 exists to prevent: a claim about the game with an authority it no longer
has. The upgrade procedure says rules are re-verified on a tag merge and has no instrument to do
it with.

**Approach:** A checker that resolves every citation in `docs/` against the tree at the tag the
citation itself names, reports the ones that do not resolve, runs in continuous integration, and is
named in the upgrade procedure's re-verification step. It honours the convention `docs/UPSTREAM.md`
already records: a rules row flipped to `needs-review` keeps its link at the tag where it was true.

## Boundaries & Constraints

**Always:** A citation is checked against the tag it names, not against the working tree, because
that is what makes it a citation. The report names the file, the line and the page it is on, and
says which of the ways it failed. A row whose link is at an older tag is a finding only when the
row does not say `needs-review`; a row that says `needs-review` while its link is at the pin is
stale metadata and is also reported. Reading a tag is `git show <tag>:<path>`, as `HooksLedgerTest`
and `HooksVanillaTest` already do. The 275 bootstrap rules pass unchanged — if they do not, the
row is wrong and gets fixed, never the checker.

**Ask First:** If the sweep finds a citation that is genuinely wrong rather than merely stale,
report it and fix the row in this story; if it finds many, stop and say so before rewriting docs
wholesale.

**Never:** Do not rewrite or re-cite documentation to make the checker pass. Do not check prose
against the game's meaning — this resolves line ranges, nothing more. No new module: the checker
lives in `codex`, which already reads pinned source and already runs in CI. Do not weaken the
existing rules pages' Tier column or the `needs-review` convention.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| A citation that resolves | a link at the pin whose file has those lines | not reported | N/A |
| A line past the end | `Foo.java#L9999` on a 200-line file | reported: the file has 200 lines | names page, file, line |
| A file gone at that tag | a path no longer in the tree | reported: not in the tree at that tag | as above |
| Span and link disagree | code span says `:118-124`, link says `#L118-L130` | reported: the two state different lines | as above |
| A link at an older tag | a rules row at `v3.3.8` marked `needs-review` | not reported: the convention says the link stays until someone re-reads | N/A |
| The same, unmarked | an older tag on a row with no `needs-review` | reported: a citation at a tag nobody flagged | as above |
| `needs-review` at the pin | a row re-cited but still flagged | reported: the flag outlived the re-citation | as above |
| A bare file name | `Random.java:202-229` with no path | resolved when exactly one file in the tree has that name; reported as ambiguous when more than one, missing when none | names the page |
| A tag that is not a tag | a link naming a ref the repository does not have | reported, naming the ref | as above |

</frozen-after-approval>

## Code Map

- `docs/` -- 1,834 citation-like strings: 1,677 carry a full path, 157 are bare file names. The
  heaviest pages are `adr/0006-observer-visibility-rules.md` (240) and `rules/ui.md` (177).
- `docs/rules/*.md` -- twelve pages, ~275 rows, each `| Rule | Cites | Test | Tier | Since |`. A
  row's `Cites` cell holds one or more `[`path:line`](https://github.com/watchthelight/shatterfish/blob/<tag>/<path>#L<a>-L<b>)`.
  About 90 rows read `needs-review` with links left at `v3.3.8`; the rest are at `v4.0.0`.
- `docs/UPSTREAM.md:249` -- the convention in force: "a rules row flipped to needs-review: its link
  stays where it was true until someone re-reads the code". `:66` is the re-verification promise
  this story gives an instrument to; the upgrade procedure's step 10 is where it is wired in.
- `shatterfish/codex/.../Citations.java` -- reads a pinned file and returns the one line matching
  an anchor; this story's reader is its neighbour, not a change to it.
- `shatterfish/codex/.../Upstream.java` -- `tag(root)`, the pin every citation is measured against.
- `shatterfish/codex/build.gradle` -- the `generate` task is the pattern for a task with a `main`;
  the `test` task's `inputs` already name `docs/UPSTREAM.md` and must gain `docs/`.
- `docs/rules/index.md` -- the tail that says tests arrive with their epics; where the Brain's own
  Rules index is named as story 4.4's.

## Tasks & Acceptance

**Execution:**
- [ ] `shatterfish/codex/.../DocsCitations.java` -- read every citation in `docs/`: the code span,
  the link, the tag, the path and the line range; resolve each against `git show <tag>:<path>`;
  return the findings as values, each naming the page, the citation and which rule it broke.
- [ ] `shatterfish/codex/.../DocsCitations.java` -- a `main` that prints the report, so the upgrade
  procedure has one command to run and read.
- [ ] `shatterfish/codex/build.gradle` -- a `citations` task for that `main`, and `docs/` as an
  input of `test` so a doc edit reruns the sweep.
- [ ] `shatterfish/codex/src/test/.../DocsCitationTest.java` -- the checker's own rules against
  source written in the test (a line past the end, a missing file, a disagreeing span, an older
  tag with and without `needs-review`, an ambiguous bare name), then the sweep over the real
  `docs/`, which must report nothing.
- [ ] `docs/UPSTREAM.md` -- the upgrade procedure's re-verification step names the command.
- [ ] `docs/rules/index.md` -- the Brain's own Rules index is named as story 4.4's, since no Brain
  exists yet to rely on anything.
- [ ] `docs/adr/0017-...md` -- the amendment: what a citation is checked against and why the tag
  the citation names, not the pin, is the thing to resolve it at.

**Acceptance Criteria:**
- Given the committed `docs/`, when the checker runs, then it reports nothing and the 275 rules
  pass unchanged (`DocsCitationTest`).
- Given a citation whose lines are past the end of the file at its tag, when the checker runs, then
  it is reported naming the page, the file and the line (`DocsCitationTest`).
- Given a rules row at an older tag, when the checker runs, then it is reported only if the row
  does not say `needs-review` (`DocsCitationTest`).
- Given `./gradlew build` in CI, when the sweep runs, then a citation that stopped resolving fails
  the build (`DocsCitationTest`).
- Given the upgrade procedure, when a reader reaches the re-verification step, then it names the
  command that checks every citation (`docs/UPSTREAM.md`).

## Spec Change Log

## Design Notes

**Why the tag the citation names, not the pin.** A citation is a promise about one tree. A rules
row deliberately left at `v3.3.8` is still true of `v3.3.8`; resolving it against `v4.0.0` would
report ninety rows the project has already decided about, and the report would be noise nobody
reads. What the checker adds is that the decision is now visible: a row at an old tag has to say
so, and a row that says so has to be at an old tag.

**Why a bare name resolves by search.** 157 citations name a file without a path. Refusing them all
would report prose the project writes deliberately; guessing would be folklore. Resolving when
exactly one file in the tree carries that name, and refusing when more than one does, is the same
rule `Stated` uses for a superclass in story 2.8.

## Verification

**Commands:**
- `./gradlew :codex:citations -Pshatterfish.mobile=off` -- expected: a report with no findings.
- `./gradlew :codex:test -Pshatterfish.mobile=off` -- expected: green.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- expected:
  green.
