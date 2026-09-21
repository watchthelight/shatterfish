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
