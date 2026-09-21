---
title: 'The drift check and the generated documentation'
type: 'feature'
created: '2026-09-21'
status: 'done'
updated: '2026-09-21'
baseline_commit: 'a6f0ae28a'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Codex is held to the pinned tag by a JUnit test nobody has watched bite — no
deliberate edit has ever been shown to turn CI red. And the docs site promises pages rendered from
the tables and has none: `docs/codex/index.md` is hand-written prose that narrates each story and
ends by saying story 2.9 replaces it.

**Approach:** The one generator writes the site's Codex pages beside the tables, the drift check
covers both, and a deliberate edit is proved once on a real CI run to fail the build. The pages
index the data rather than repeat it: what each table holds, how many entries, the citations that
define it, and in full the lists its reader had to name a reason for.

## Boundaries & Constraints

**Always:** One task writes everything (`./gradlew :codex:generate`). Every page under the site's
Codex section is generated, states the tag and Codex version it came from, and is never hand
edited. A generated page has a nav entry and a nav entry has a page, both held by a test, since
`--strict` fails a page no nav names. The drift failure names the first differing file and the
command that fixes it. UTF-8, line feeds only, stated order.

**Ask First:** Nothing here. No hook row, no new source read, no second pinned tree.

**Never:** Do not render a table's entries into Markdown — 3.1 MB of JSON, 1.7 MB of it one table
of 4,976 strings, would double what is committed and make pages no one can read, restating bytes
already committed, diffable and cited. Do not generate `mkdocs.yml`; the nav is the site's shape
and stays human-owned. No new Gradle task. Do not weaken the seed-free or leak tests.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Clean checkout | committed files match a fresh generation | drift test passes | N/A |
| Hand-edited table | one byte changed in `mobs.json` | fails naming that file and the regenerating command | names the file, not the diff |
| Hand-edited page | one word changed in a generated page | the same failure, naming the page | as above |
| Table added later | a new file the generator writes | written, in the manifest, given a page; nav test fails until `mkdocs.yml` names it | failure names page and nav |
| Stale page | a page the generator no longer writes | deleted, so the folder is exactly what was generated | N/A |
| Page with no nav entry | `mkdocs.yml` not updated | nav test fails before `--strict`, naming the page | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/codex/.../Generate.java` -- `generate(root)` returns the table files; `main(root,
  [folder])` writes them via `write`, which is flat and deletes what it no longer writes. Gains
  the pages as a second output and destination; `main`'s signature changes.
- `codex/v4.0.0/manifest.json` -- `codexVersion` 8, `upstreamTag`, 18 table names. The index page
  renders from it, so a later table needs no new page rule.
- `shatterfish/codex/.../Upstream.java` -- `tag(root)`, the tag each page states.
- `CodexSeedFreeTest.java:95-113` -- today's drift check: committed folder against a fresh
  generation, already says `"the first differing file is " + file`, asserts no `\r`, no extra
  file. Extend to the pages, keep the message. `:115-135` holds `main` and the stale-file delete.
- `docs/codex/index.md` -- the hand-written page this story replaces; the only file in that folder.
- `mkdocs.yml:142` -- `- Codex: codex/index.md`, the one entry that becomes a section.
- `.github/workflows/build.yml:41` -- `./gradlew build` already runs the drift test on every PR.
  No workflow change expected: confirm it, do not assume it.
- `docs/adr/0017-codex-generation-and-citations.md` -- gains this story's amendment.

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/codex/.../Pages.java` -- render the Codex pages: an index from the manifest
  (tag, version, every table with its entry count and a link to the JSON) and one page per table
  saying what it holds, how a row is shaped, the citations its reader recorded, and in full any
  list the reader named a reason for, those being the judgments a human audits.
- [x] `shatterfish/codex/.../Generate.java` -- `pages(root, tables)` beside `generate(root)`;
  `main` writes tables to `codex/<tag>/` and pages to `docs/codex/`, each with stale-file deletion.
- [x] `docs/codex/*.md` -- generated and committed, replacing the hand-written page.
- [x] `mkdocs.yml` -- the Codex section gains a nav entry per generated page.
- [x] `CodexSeedFreeTest.java` -- the drift check covers the pages as it covers the tables, and
  `main`'s two destinations are held.
- [x] `CodexDocsTest.java` -- every generated page has a nav entry and every Codex nav entry a
  page; the index lists every table the manifest lists; every page states tag and version.
- [x] `docs/adr/0017-...md` -- what the pages are, and why they index rather than repeat.

**Acceptance Criteria:**
- Given a committed Codex matching the tag, when CI runs `./gradlew build`, then the drift test
  passes (`CodexSeedFreeTest`).
- Given a deliberate one-byte edit to a committed Codex file, when CI runs, then the build fails
  naming that file and the regenerating command -- **verified once on a real CI run in this
  story**, linked in the Evidence, then reverted (`CodexSeedFreeTest`).
- Given the generated pages, when the docs build with `--strict`, then it is green and every page
  is reachable from the nav (`CodexDocsTest`, then `mkdocs build --strict`).
- Given a table the generator no longer writes, when the task runs, then its page is deleted too
  (`CodexSeedFreeTest`).
- Given two generations under different seeds and Profiles, when the bytes are compared, then the
  pages are identical as the tables are (`CodexSeedFreeTest`).

## Spec Change Log

**A file the Code Map did not name (2026-09-21, from review).** `Pages.java` reads the generated
JSON back rather than the game, so a page is derived from the bytes a table was written as and a
table added later gets a page with no new rule. The spec named no such reader; it is the same work
done the only way that makes "a page cannot describe a table that is not there" true.

**What "indexes rather than repeats" is, mechanically (2026-09-21, from review).** The spec and
the first implementation asserted a size ratio of the pages to the tables. That is not the rule
and, applied per table, states something false: a page carries a stamp, headings and prose whatever
its table holds, so the page of a 1 KB table is legitimately larger than the table. The rule is
that a page has no row per entry, and that is what the test holds -- a ceiling per page, a bound on
the pages against the tables together, and, for the one table that could not be indexed by
enumeration, that its page's rows are under a tenth of its entries, counted from the JSON.

## Review

Four reviews read the branch over a still tree: the `fairness-reviewer` subagent and three lenses
(adversarial, edge-case hunter, verification gap). No blocking finding, and the story's two
headline results both stand -- the deliberate-edit run that turned continuous integration red, and
the fix to the 227 vanilla citations that had been linked into a tree this repository never
commits. What the reviews found was a third instance of the pattern stories 2.7 and 2.8 each
recorded, plus a set of statements the pages made that were not so.

**The pages said things that are not true.** The index stated "one table alone holds 4,976 of
them" from a literal while deriving 4976 from the same table nine lines below; an upstream tag bump
would have made the prose false and the drift check would have regenerated it faithfully, because
it compares the output with itself. The combat page read "1 of the table's 548 entries name a
reason" about a reason belonging to a value at the table's root, which is not among those 548 --
two populations counted as one, in a table whose page also got the verb wrong because the plural
was keyed to the table's entry count rather than to the number of reasons. One dash stood for four
different facts: the table states no value, it states nothing, the value is too long for a cell, or
a cell would break on it. Every expression in the Codex is longer than a cell, so the tier page --
whose entire subject is four expressions -- rendered four dashes, and the combat page printed a
reason as `--` in one table and in full six lines below.

**The tests mostly compared the renderer with itself.** The drift gate, which is the story's whole
point, was one un-asserted call: deleting the line that compares the pages left every test green,
and the only evidence it bit was a continuous-integration run on a commit that had since been
reverted. The two generations under different seeds and Profiles rendered both sets of pages after
the first Profile had been restored, so a page that read a Profile would have passed. Every number
a page states was derived once and checked against that same derivation. `Pages.COMMAND`, the blob
URL and the page names supplied both sides of their own assertions. The nav test accepted a nav
that called the items page "Mobs", and counted a page filed under any section as navigated.

**What changed.** Every falsehood above is fixed and the fixes are held: the index derives its
number, reasons are reported against what they were recorded on (`Judgment.where`, computed and
never read, is what tells an entry from a value, and is now shown), a value a cell cannot hold is
printed in full beneath its table, and a cell escapes what would break it. The drift check is
driven over a folder edited by hand and has to fail naming that file and the command; its message
names the folder as well as the file, since both hold an `index`. The pages are rendered under each
Profile in turn. The counts, the command, the repository and the site's own extensions are held
against their real sources. A folder under a generated folder is refused rather than passed over.
The JSON reader's refusals got the test they never had, and it found one: a key written twice whose
first value was null was accepted, because the check read what `put` returned.

**Declined, with reasons.** The reviews asked for this repository's own citation links to be
pinned to a commit as the vanilla links are. They cannot be: a commit sha in a generated file
changes on every commit, so the drift check would fail on every commit, and the fork's citations
are read from the working tree, whose hooked files run longer than the tag -- `main` is both the
byte-stable choice and the correct line numbering. A suggestion to exclude the generated pages from
the site's edit button is a change to the whole site's chrome and belongs to whoever owns that, not
to this story; the pages say they are generated in their own first line.

## Design Notes

**Why the pages index the data.** Rendering 4,976 string rows into Markdown would double what the
repository commits and produce a page no reader can use, to restate bytes already committed and
cited. A page answers what a reader brings to a table -- what is in it, how many, read from where,
and what the reader had to judge -- and links the JSON at the pinned commit for the rest.

**Why the nav is hand-owned and tested.** `mkdocs.yml` is the whole site's shape; a generator
rewriting it would make every unrelated nav edit a generated-file conflict. A test in both
directions costs one line per future table and names exactly what to add.

**The deliberate-edit verification is the story's point.** A gate nobody has watched bite is a
gate nobody knows works; the local test is necessary and not sufficient.

## Verification

**Dev notes:**
- **Never run two gradle jobs at once**, and do not `git add` while a mutation battery runs: its
  restore is `git checkout --`, so a commit in the wrong window stages a mutated reader.
- **Write patch scripts with the editor, not with a shell heredoc**, which halves the backslashes
  on the way to Python. It cost two round trips here -- once on a Java regex, once on a probe.
- A branch that already has a pull request open is not a branch to rewind: pushing an older commit
  to it points the open request at that commit. Check for the request before force-pushing, even on
  a story branch where force-pushing is otherwise allowed.
- The site's table of contents slugifies a heading by dropping everything that is not a letter or
  a digit, so `weapon.expression` anchors as `weaponexpression`; `mkdocs build --strict` is what
  catches a link to an anchor that is not there.


**Evidence:**

- `./gradlew build -Pshatterfish.mobile=off` -- **BUILD SUCCESSFUL**, every module; `:codex:test`
  green at 109 tests, up from 100.
- `./gradlew :codex:generate && git status --short codex/ docs/codex/` -- nothing changed: 19
  tables, 19 pages, 19 nav entries.
- `mkdocs build --strict` -- green, 90 pages, every generated page reachable from the nav.
- **The deliberate edit, on a real CI run** -- the acceptance criterion this story exists for. One
  byte of `codex/v4.0.0/mobs.json` changed by hand so a Scorpio's attack roll read `max:37` where
  the pinned class says `return 36;`, pushed as the head of the pull request:
  [run 35650222072](https://github.com/watchthelight/shatterfish/actions/runs/35650222072) **BUILD
  FAILED**, `CodexSeedFreeTest` naming *"the first differing file is mobs.json; run
  `./gradlew :codex:generate` and commit"*, with `CodexLeakTest` catching it independently.
  Reverted in the commit after, and CI green again.
- **CI green on the reviewed head** -- build + test 10m02s, `mkdocs --strict` 21s.

**Mutation battery (12 mutations).** Two survivors were real and both are fixed:

- **M1, the one that mattered**: the drift check stopped covering the pages if one line went, and
  nothing noticed -- the gate this story exists to prove, failing open. The test written for that
  very risk did not close it, because it drove the comparison itself rather than the call the
  committed folders go through. What the task writes is now one value it produces
  (`Generate.outputs`), and the check walks it; dropping the pages is a change to what the
  generator writes. Retargeted at that production fact, M1 is caught.
- **M4**: a folder under a generated folder was refused by the write and by nothing else, because
  no test had put one there. Now one does, and M4 is caught.

Four further survivors are mutations of tests rather than of the code the tests hold, and a
weakened assertion cannot be caught by the suite it lives in -- the battery's reach ends at
production code, which is worth recording rather than papering over. One of them, M5, is a no-op
at this pin: the literal the index used to carry and the count derived from the tables are the same
number today, so nothing can tell them apart until a tag moves, which is exactly when the new
assertion bites.

This is the third story running where the battery found something four independent reviews did
not, and the second where what it found was the story's own headline guarantee.

**Commands:**
- `./gradlew :codex:generate -Pshatterfish.mobile=off && git status --short codex/ docs/codex/` --
  expected: nothing changed after the commit.
- `./gradlew :codex:test -Pshatterfish.mobile=off` -- expected: green.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` --
  expected: green, the Codex section carrying every generated page.
- A deliberate edit pushed to the branch -- expected: CI red, the failure naming the edited file;
  then reverted and CI green.

**Evidence:**

- `./gradlew build -Pshatterfish.mobile=off` -- **BUILD SUCCESSFUL**, 42 tasks, every module.
- `./gradlew :codex:test -Pshatterfish.mobile=off` -- green, including the drift check over both
  folders and the four new `CodexDocsTest` cases.
- `./gradlew :codex:generate -Pshatterfish.mobile=off && git status --short codex/ docs/codex/` --
  nothing changed after the commit; the one task writes both folders.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- green,
  90 artifact pages, and all nineteen Codex pages built into `site/codex/`.
- **The deliberate edit, on a real CI run.** One byte of `codex/v4.0.0/mobs.json` changed by hand
  (a Scorpio's attack roll reading 37 where the pinned class says 36), pushed as commit
  `43c9fa8be`. Continuous integration went red:
  [run 35650222072](https://github.com/watchthelight/shatterfish/actions/runs/35650222072) --
  `BUILD FAILED in 7m 50s`, `Execution failed for task ':codex:test'`, 100 tests completed, 2
  failed. `CodexSeedFreeTest` failed with *"the first differing file is mobs.json; run
  `./gradlew :codex:generate` and commit"*, naming the file and the command exactly as the story
  requires, and `CodexLeakTest`'s live-Run comparison failed on the same file independently. The
  byte was reverted in `877e6855f` and CI is green again. The gate has now been watched to bite.

**What the pages come to.** Nineteen pages, 316 KB of Markdown, against 3.1 MB of JSON: one index
and one page per table, covering 7,165 entries. 85 of those entries name a reason and are listed
in full -- 60 items whose icons need the toolkit, 24 bundle keys naming no class the game compiles,
and one hit table a generator that may not boot cannot measure. `CodexDocsTest` holds the ratio, so
a page that started repeating its table would fail.

**Dev notes:**
- `Pages` is the one place in the repository that reads JSON rather than writing it. That is
  deliberate: a page derived from the bytes that were written cannot disagree with them, and a
  second pass over the game could. The reader is small, refuses floats and duplicate keys, and
  lives beside the renderer that needs it.
- The review catch of this story was a dead link, not a wrong number: the vocabulary table cites
  `vanilla-src/`, which is fetched and never committed, so every one of its 227 vanilla citations
  linked a path this repository does not hold. `CodexDocsTest` now resolves every link a page
  makes back to a file that is there.
