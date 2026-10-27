---
status: accepted
date: 2026-10-26
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0021: Lore claims are one file each under `lore/<topic>/`, checked and entered by Codex tooling

## Context and problem statement

FR-18 says community knowledge enters as one file per claim, with provenance, and that a claim about
a seed or a layout is rejected at intake. Story 7.7 builds that. What exists is the first batch
without the pipeline: 144 claims graded against `v4.0.0` and written into eight grouped files under
`lore/bestiary/` (31, 12, 15, 8, 5, 39, 10 and 24 claims). They carry a tier and a verdict, but the
format was a convenience of the bestiary work, not a decision. Nothing validates them, a claim cannot
be linked to by an id that is a file, and a forum claim from the product owner's research skill has
nowhere to enter.

Three things make the format matter beyond tidiness:

- **Non-negotiable 8.** Lore enters only with a source and a verification tier, and a claim about a
  mechanic is settled by the pinned code at `path:line`. A Tier 1 that cites nothing is folklore with
  a label, so the format has to make the citation checkable and the checker has to run in the build.
- **Non-negotiable 1.** A claim that names a seed, a seed string, a layout, a map coordinate or a
  level feeling tied to one Run is knowledge of one dungeon, not of the game. A seed code is nine
  letters in three groups (`DungeonSeed.java:51-62`); a Brain that read one would be reading the
  Run's RNG. Such a claim must not be storable, not merely not used.
- **The pin moved.** The 144 verdicts were graded at `v4.0.0` and the pin is `v4.0.2`. A verdict
  that quietly claims the new tag for lines nobody re-read is the failure FR-17 exists to prevent.

The epic fixes a few things (the field names from the PRD addendum, `lore/<topic>/<id>.md`, no new
module, no game code, no network) and leaves the rest to this record.

## Decision drivers

- **One claim, one file, one id.** A heuristic or a Rule must be able to name a claim by something
  that is also a path, and a change to one claim must be one small diff.
- **The checker is cheap to trust.** It runs in `./gradlew build`, reuses the citation resolver of
  FR-17 rather than writing a second one, and tells the writer which file and which rule.
- **No verdict claims a tag it was not graded against.** The tag is a field, and the file says
  whether it still stands at the pin.
- **Intake cannot be talked into a promotion.** A draft enters at Tier 3 unless it carries the
  citation that makes it Tier 1, and the checker, not the writer, says whether the citation holds.
- **No new module and no layout change** (ADR-0003), no game code, no network call (NFR-8).
- **Conversion loses nothing**, and a test can say so.

## Considered options

1. **Leave the grouped files.** Nothing to convert. Rejected on driver 1: a claim has no id a
   heuristic can name except a heading inside a file, a one-line fix to one claim is a diff in a
   400-line file that two parallel pull requests collide on, and the checker would have to parse
   a document format that is not a record. It is also the format that let a Tier 1 with no citation
   exist unnoticed (four of the 144 have none, all unsettled or contradicted).
2. **One Markdown file per claim, frontmatter plus a verdict body, under `lore/<topic>/<id>.md`.**
   The epic's choice. One id is one file stem, a diff is one claim, the body is where a verdict reads
   naturally and the frontmatter is where a checker reads fields.
3. **One generated data file** (`lore.json`) with every claim as a record, edited by a tool. Strong
   for the checker, weak for the people: a reviewer reads a claim and its verdict as prose, and a
   merge of two additions is a conflict in a list. Rejected on drivers 1 and 3.
4. **One GitHub issue per claim.** Provenance and discussion come free and ids are issue numbers.
   Rejected: CLAUDE.md says issues track state and docs carry knowledge, a claim is knowledge, and a
   build cannot check an issue without the network (NFR-8) or a snapshot that is option 2 again.
5. **Option 2 with a new `lore` module** for the checker and the intake. A clean home and a
   dependency the Gradle graph can see. Rejected on driver 5: ADR-0003's layout stands, the citation
   resolver is in the Codex tooling already, and a module for two classes that run git is a layout
   change bought for nothing. The product owner settled this before the story started.

Option 2, with the checker and the intake in the Codex module's test sources.

## Decision outcome

### The file

`lore/<topic>/<id>.md`: a frontmatter between two `---` lines, then a body. `<topic>` is a lowercase
slug (`bestiary` today). `<id>` is `<group>-<n>`, unique across topics, and is the file stem. The 144
claims are all topic `bestiary`; the eight grouped files survive as the `group` field (`sewers`,
`prison`, `caves`, `city`, `halls`, `bosses`, `specials`, `quest-branch`), which is also the id
prefix, so the counts per group before and after are directly comparable.

The frontmatter is a small fixed grammar, not YAML: `key: value`, where the value is a bare token, a
JSON string (the only way free text is written), `[]`, or a key followed by `  - item` lines. No
library, one predictable reading, and `render(parse(x)) == x` is a test.

| Field | Value |
|---|---|
| `id`, `topic`, `group` | slugs; `id` is `<group>-<n>` and names the file |
| `source_url` | an `http` or `https` URL |
| `source_type` | `wiki`, `forum`, `guide`, `video`, `developer`, `other` |
| `date` | when the source says it was written: `yyyy-mm-dd` or `unknown` |
| `retrieved` | when it was read: `yyyy-mm-dd` |
| `variant` | `spd`, `pd`, `mod:<name>` or `unknown` |
| `version_claimed` | what version the source names, as the source says it, or `unspecified` |
| `tier` | `1` the code confirms it, `2` the harness confirms it, `3` not settled, `F` the code contradicts it |
| `graded_against` | the release tag the verdict was written against, such as `v4.0.0` |
| `status` | `current` or `needs-review` |
| `claim` | the paraphrase, at most 600 characters (the longest of the 144 was 533) |
| `quote` | optional, at most 200 characters: the body rule is a paraphrase and at most a short quote |
| `cites` | a list of `path:line` or `path:from-to`, written the way the docs write them, middle elided with `…` allowed |
| `implemented_in` | a list, possibly empty, of `policy:<name>`, `rule:<slug>` and `playbook:<field>` |
| `subject`, `subject_class`, `card` | optional: the mob the claim is about, its class, and its bestiary card |

The PRD addendum's `claim` is the frontmatter `claim`; the body holds the verdict and opens with
`## Verdict against `<graded_against>``, so a verdict cannot be read without the tag it was graded
at. `implemented_in` names are prefixed because an unprefixed name could be a Policy, a Rule slug or a
Playbook field. Until the Playbook exists as data (story 7.2) its fields are the terms of the
`weights/*.json` files, the data the Brain reads at construction today; 7.2 extends the registry.

`source_type` is not in the grouped files. The conversion reads it from the host (`pixeldungeon.fandom.com`
and `tvtropes.org` are `wiki`, `steamcommunity.com` is `forum`, `docs.oldmartijntje.nl` and
`spd-huntress-guide.pages.dev` are `guide`, `www.youtube.com` is `video`), and the script says so.

### What the checker holds

`LoreCheck` validates every file: required fields, the values above, a URL that is a URL, dates that
are dates, an id that matches its group, its file name and its folder, ids unique, lengths, and the
claim text against the intake refusals below. A Tier 1 or Tier 2 claim carries at least one citation.
Every citation is resolved at the tag the claim was graded against by `DocsCitations.resolve`, the
resolver of FR-17, which now has package-level access for the purpose and is otherwise unchanged. Every
`implemented_in` name must exist: Policies from `Brain.policyNames()`, rule slugs from the anchors of
`docs/brain-rules.md`, Playbook fields from the weights files.

### What `status` means

A verdict is graded against one tag and is never rewritten by an upgrade. A claim graded before the
pin is `needs-review` exactly when a line it cites reads differently at the pin than at the tag it
was graded at, compared by the text at the same line numbers, and `current` otherwise. A claim graded
at the pin is `current`. The checker holds both directions, like the rules tables
(`docs/UPSTREAM.md`). `./gradlew :codex:lore --args=review` sets the status to what the lines say, and
is a step of the upgrade procedure after a re-pin; it changes nothing else in a file. When the person
re-reads a flagged claim, the new verdict is a new `## Verdict against` section with a new
`graded_against`, which makes the claim `current` at the new pin.

### Where it lives

In the Codex module's test sources, beside `DocsCitations`, for the same reason: the checker resolves
citations from the repository history, and `CodexLeakTest` denies the generator `Process` and `System`.
The classes are `Lore`, `LoreCheck`, `LoreIntake`, `LoreDocs` and `LoreTool`. `./gradlew :codex:lore
--args="check|review|docs|intake <folder>"` is the command line. The check runs in `./gradlew build` as
`LoreCheckTest`; the module gains one test-only dependency on `brain` (which depends on `api` and
nothing else, so no cycle) to read the Policy names. No game class appears in any of them and none opens
a connection; `LoreIntakeTest` greps their sources for both.

### Intake

`./gradlew :codex:lore --args="intake <folder>"` reads each `*.md` draft in a folder, validates it with
the checker's own rules, assigns `<group>-<next number>`, sets `graded_against` to the pin and
`status` to `current`, and writes the claim under `lore/<topic>/`; the draft file is renamed `<file>.accepted`.
A draft that fails stays where it is and `<file>.rejected.txt` beside it says why, one reason per line;
nothing is written under `lore/`. A draft carries no `id`, `status` or `graded_against`.

Intake never promotes. A draft enters at Tier 3 unless it asks for Tier 1, 2 or F and carries a citation
that resolves at the pin; a draft that asks for a tier with no citation is refused rather than lowered, so
the writer learns the claim was not graded. A citation on a draft that did not ask for a tier does not
raise it.

The five refusals (FR-18, non-negotiable 1), each with a message saying which was found:

1. a seed string (`ABC-DEF-GHI`);
2. a seed named (`seed 4821`, `seed is XQZWVBNMK`) or pointed at (`this seed`, `my seed`);
3. a specific dungeon layout (the words layout, floor plan, or a compass corner of a map);
4. a map coordinate (`(12, 30)`, `x=14`, `cell 1234`, "coordinates");
5. a level feeling tied to one Run (the words "level feeling" with a depth number or "this run").

These are patterns and a pattern misses things; they are the cheap line, not the only one. The checker
applies the same scan to the `claim` and `quote` of every stored claim, never to the verdict, which is
the project's own text and may say "old layout" of a boss arena.

### Consequences

- 41 of the 144 claims are `needs-review` at the pin: a line they cite reads differently at `v4.0.2`.
  Their verdicts are kept as written, graded against `v4.0.0`. Re-reading them is not this story's work;
  the count at the head of the docs page is the measure of it.
- The grouped files' headers (a title with a depth range, a source list, tier counts) are not carried:
  the sources are on every claim, the counts are generated, and the depth ranges are in the bestiary pages.
- `docs/lore/index.md` is generated from the files and compared with the committed copy by `LoreDocsTest`.
- `LoreMigrationTest` holds the conversion to a manifest of SHA-256 hashes of the ten fields of each claim,
  written by `tools/lore-split.py` before it removed the grouped files. Re-grading a converted claim on
  purpose updates its manifest line in the same pull request.
- The heuristic half of FR-18 ("every heuristic links to a Lore entry or a Rule") is story 7.1's
  `@Heuristic` check. A claim id for that check is the file stem; ids are unique across topics. This ADR
  does not add the reverse check because `@Heuristic` is not on `main` yet; the follow-up is named in the
  story file.

## Pre-mortem

It is a year on and the lore pipeline is not trusted. Why?

- **The patterns reject real knowledge or let a seed through.** A claim about plant seeds that says "this
  seed" is refused; a seed written "s e e d" is not. The message names the match so a writer can
  rephrase, and the refusal list is five patterns in one class with one test per kind. If it bites, loosen
  a pattern in a test-first change; do not add a bypass.
- **`needs-review` becomes noise.** At 41 of 144 it is already large, and a flag nobody clears is a
  checker nobody reads. Mitigation: the count heads the docs page, and re-reading is a measured step of
  the upgrade procedure (`review`), not a hope.
- **The comparison by line text is too strict or too weak.** A comment added above a cited range moves it
  and flags a claim that is still true; a changed constant on a line outside the cited range is not seen.
  Flagging too much costs a re-read; flagging too little is the failure the field exists to prevent, and
  FR-17's tier-age rule has the same limit and has held. The range is what the verdict cited.
- **Playbook fields are guessed.** The registry reads weights terms until story 7.2 lands. If 7.2 names them
  differently, the registry changes in that story and the `playbook:` entries (none exist yet) with it.
- **The checker rots because it is in test sources.** It runs in the build, so it cannot rot silently, and
  it is no more hidden than `DocsCitations`.

## Amendment (story 7.9): the release table and the date gate

**One data file is allowed beside the claim folders.** This ADR said nothing but claim files lives under
`lore/`. Story 7.9's epic names `lore/release-dates.json`, a table of release dates whose provenance is the
point. The file is named in `Lore.DATA`, is not a claim, is skipped by every walk over the claims, and is held
by its own test (`ReleaseDatesTest`). Any other file directly under `lore/` is still a finding
(`LoreCheckTest.strays_and_unreadable_files`, `the_release_table_is_not_a_stray`).

**Where the dates come from.** A calendar date is not typed in. The game's own changelog states "Released
<date>" in a heading of the first entry of every release from v0.1.0 to v4.0 (53 releases; v4.0.1 and v4.0.2
are labelled and undated, so they have no row). The table is derived from `codex/<tag>/changelog.json` and the
changelist file each heading cites, one row per release with the version, the ISO date, the date as the game
words it and the `path:line` that holds it. A row with no source is not accepted, the file is compared with a
fresh derivation (`./gradlew :codex:lore --args=dates` rewrites it), and every source is resolved at the tag.

**What the gate does.** `DateGate` (Codex test sources, beside `LoreCheck`) puts a claim at a release (a
version it names, a bound such as `pre-v4`, or its date turned into a release through the table), ties its
subject to changelog headings by word through the vocabulary, and returns `changed-since` (a heading of a later
release names the subject, with the entry class and `path:line`), `undated` (no usable version or date),
`clean`, or `no-entry` (anchored, and no heading names the subject: unflagged, counted). It flags and writes
nothing. A flagged claim with no `*.java:line` citation has a ceiling of Tier 3; a date alone never lifts a
Tier. The Results page (`./gradlew :codex:lore --args=gate`) leads with the finding and is drift-checked.

Options weighed for the table: (1) type the dates from memory, rejected by non-negotiable 8; (2) fetch the
developer's blog, rejected under the no-network rule and because a fetched page is a second thing to go stale;
(3) derive from the changelog, chosen; (4) add a `date` field to the Codex's `ChangeEntry`, rejected as a
change to `api` and the generated pages for a value one table carries; (5) no table, gate on version alone,
rejected because the epic asks for dates and 61 of them are in the repository.
