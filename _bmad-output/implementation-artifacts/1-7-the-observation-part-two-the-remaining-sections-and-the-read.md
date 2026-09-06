---
story: 1.7
key: 1-7-the-observation-part-two-the-remaining-sections-and-the-read
title: "The Observation, part two: the remaining sections and the readable form"
epic: 1
issue: 20
status: done
created: '2026-09-05'
updated: '2026-09-05'
---

# Story 1.7: The Observation, part two: the remaining sections and the readable form

As the engineer,
I want the hero, inventory, journal, log, actions and prompt sections plus a readable rendering,
So that the schema is complete and a person can read an Observation.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v3.3.8` (commit `7b8b845a`).

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| Given ADR-0005, when the remaining records and the JSON writer are implemented in `api` | **Met.** `HeroSection` (with `TalentView`, `QuickslotView`), `InventorySection` (with `ItemView`), `JournalSection` (with `NoteView`, `KnownAppearance`), `LogSection` (with `LogLine`), `ActionsSection` (over the sealed `Action` and `ItemRef`), `PromptSection`; six enums; `Observation` over nine sections; `JsonWriter` and `ObservationJson` |
| The whole-Observation hash is the digest over the section hashes plus the version | **Met.** `ObservationCodec.hash` is SHA-256 over the version and the nine section hashes in `SECTIONS` order; `ObservationHashTest` recomputes it with the JDK's digest and holds the section list to the decision's order |
| `CodecEqualityTest` asserts over a corpus that two Observations are equal exactly when their hashes are equal | **Met.** The corpus is the two base Observations built twice and every Observation that differs from one in a single accepted component of a single section, several hundred distinct; pairwise, `equals` agrees with the hash, with the bytes and with the JSON |
| The JSON rendering carries the hash as a field and is never parsed back into an Observation | **Met.** `ObservationJson.render` writes `hash` and `sectionHashes` beside the sections; `JsonRenderingTest` reads them back with a strict reader and scans `api` with ArchUnit for any method turning text or bytes into a record of the schema, finding none; the module has no reader |
| The Belief is declared as a separate opaque versioned value, not a field of the Observation, so that `harness` can hash it without depending on `brain` | **Met.** `Belief(version, bytes)`: equal by content, bytes copied in and out, `hash()` over the version and the bytes; `CodecReflectionTest` holds that no record of the schema has a component of that type |

## What was built

- `shatterfish/api/src/main/java/org/shatterfish/api/`: `HeroSection`, `TalentView`, `QuickslotView`, `InventorySection`, `ItemView`, `ItemRef`, `JournalSection`, `NoteView`, `KnownAppearance`, `LogSection`, `LogLine`, `ActionsSection`, `Action` (sealed, twenty records), `PromptSection`; enums `HeroSubclass`, `Hunger`, `ItemKind`, `EquipSlot`, `NoteKind`, `LogTone`; `Observation` with nine sections, `withActions` and `json()`; `ObservationCodec` at schema version 2 with the nine sections; `JsonWriter`, `ObservationJson`; `Belief`; `Canon.positional`.
- Tests: `Variants` (the schema by reflection, shared), `CodecReflectionTest` (every component reaches the bytes and the JSON; one Action record per kind), `CodecEqualityTest`, `JsonRenderingTest` with `StrictJson`, `BeliefTest`, `CodecCanonicalTest` and `SchemaRulesTest` extended, `ObservationHashTest` repinned under version 2, `Corpus` with a second Observation that has the chasm Prompt open.
- Docs: ADR-0005 amendment for story 1.7; ADR-0014 amendment for the Action records; six rule rows in `docs/rules/ui.md`; the `api` line of `docs/architecture.md`.

## What the story found

**The valid-Action set is circular by the decision's own table, and the record resolves it.**
ADR-0005 says the `actions` section is computed from the Observation and is part of it. The
record is built in two steps: `ActionsSection.NONE` first, then `withActions` once story 1.12's
`validActions` has read the rest. What is hashed is the record with the set.

**The Action records belong to the schema, not to story 1.12.** The `actions` section is a list
of them, so ADR-0014's sealed interface is implemented here with one record per kind; item use
became three kinds by the shape of its target, since a record cannot carry "a cell or an item
reference" as one value without an optional field, and `MoveTo` is a record because the Overlay's
human clicks must be logged as they were made (ADR-0011), which the valid set refuses.

**An item's option window is an Input wait of its own, so no Action carries an option index.**
ADR-0014's table allowed an option index as an item use's target; the review found that no
section lists the labels such a window shows, and they cannot be listed: the scroll of
enchantment's three choices are not known before the window opens. Story 1.5 settled that a
recognised window in front is an Input wait, so that window is a Prompt, answered by
`AnswerPrompt` at the next wait; `UseItemOption` was dropped before the pin moved.

**Every Action parameter is checked against the Observation at construction.** ADR-0014 asks that
a parameter be a value the Observation carries; the record refuses a cell off the map, an item
reference whose index, name or quantity the inventory does not list, an item action the item does
not offer, an answer past the prompt's options, a talent the hero does not list, an ability the
hero does not have. That makes an `ItemRef` desync unrepresentable in an Observation, not only
detectable by the executor.

**The hero's health is exact where an actor's is quantised.** The status pane prints
`HP/HT` or `HP+shield/HT` over the bar (`…/ui/StatusPane.java:322-327`), so the hero section
carries the numbers; ADR-0006's quantisation applies to the bars over sprites.

**The bag window prints gold and energy** (`…/windows/WndBag.java:186`, `:179`, `:213-216`), and
the hero window prints the gold collected in the Run, not the gold held
(`…/windows/WndHero.java:199`); the section carries what the bag shows.

**The log's source is the signal, and the pane can drop a message before drawing it.** The pane
takes a frame's messages in one batch, merges, and trims the oldest entries beyond three or five
lines of text before the frame is drawn (`…/ui/GameLog.java:55-131`, `:59`, `:89`, `:107-122`),
so a burst that exceeds the lines in one frame loses its oldest messages unseen. The first draft
said every message had been shown; the review corrected it. The Observation keeps the raw signal
(`…/utils/GLog.java:39`), which non-negotiable 1 names beside the renderer and which is
reproducible where frame timing is not, and the trade-off is recorded in ADR-0005.

**A record over a byte array compares by identity**, so the Belief is a final class with its own
`equals`, copying its bytes in and out.

**Sorted keys and no whitespace are what ADR-0011 needs** for the Run log, so the one `JsonWriter`
writes canonical JSON and the Observation's readable form is canonical too; readability is what
any JSON tool gives, and the text is never hashed.

## Decisions taken inside the story

**Where the Action records live.** Alternatives: (a) a generic `ActionView(kind, parameters)` here
and the sealed interface in story 1.12; (b) the sealed interface here, `validActions` in 1.12;
(c) defer the `actions` section to 1.12. Chosen (b): the section is a list of Actions and the
decision fixes their kinds; a generic view would be a second vocabulary to retire. Pre-mortem:
1.12 or 1.13 finds a kind's parameters wrong; that is a version bump, which the pin enforces.

**Item use as three kinds.** Alternatives: (a) one `UseItem` with nullable targets; (b) a
`Target` sealed interface as a component; (c) one kind per shape of target. Chosen (c): every
component stays a value, every switch is exhaustive, and the reflection tests need no case for an
interface-typed component. The option-index shape went with the review, above.

**The actions' order.** Alternatives: (a) the executor's enumeration order, positional; (b) by
kind then by the action's canonical bytes. Chosen (b): a set enumerated in any order must be one
hash. Pre-mortem: sorting by bytes is not sorting by cell within a kind, since the length prefix
comes first; the order is canonical, not pretty.

**The log's cap.** Alternatives: (a) the pane's own retention, three or five rendered lines,
which depends on UI size, wrapping and frame timing; (b) a fixed count over the signal. Chosen
(b), sixty-four: the pane's retention is a rendering artefact and not reproducible, and the cost,
a line a human may not have read in a burst, is recorded.

**The JSON's shape.** Alternatives: (a) keys in component order with whitespace, for reading;
(b) canonical, sorted keys and no whitespace, as ADR-0011 needs for the log. Chosen (b), one
writer for both.

**The Belief's shape.** Alternatives: (a) a record with a `List<Byte>`; (b) a record with a
`byte[]` and identity equality; (c) a final class over a copied array. Chosen (c).

**What the hero section does not carry.** The danger count (derivable from the actors), the
gold collected (a statistic, not a resource), the item sprite index (the name says it), the
guide's pages and the bestiary (static text and cross-Run state).

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green, 383 tests across 22 suites, 317 of
them in `api`, 263 of those the dynamic tests of `CodecReflectionTest`. `mkdocs build --strict`:
clean. The equality corpus is 470 Observations, 424 of them distinct.

**Mutation battery**, twenty-one mutations on the committed tree at `b89047e0d` (eighteen first
run at `5731279a0`, three added for the review's rules), each applied to a clean tree, run
without `--rerun-tasks`, restored with `git checkout` of the mutated files, and the tree verified
clean after each:

| # | Mutation | Caught by |
|---|---|---|
| M1 | `hero.gold` is not encoded | `CodecReflectionTest` (`HeroSection.gold`, `Observation.hero`), `CodecEqualityTest`, `ObservationHashTest` (the pin) |
| M2 | `hero.gold` is not rendered | `CodecReflectionTest` (rendered), `JsonRenderingTest` (every component a key) |
| M3 | `UseItemAt.cell` is not rendered | `CodecReflectionTest` (rendered), `JsonRenderingTest` (every component a key) |
| M4 | JSON keys keep insertion order | `JsonRenderingTest` (four tests: the strict reader refuses a key out of order) |
| M5 | the JSON carries no hash | `JsonRenderingTest` (the hash field; the root's keys) |
| M6 | a quote is not escaped | `JsonRenderingTest` (escapes) |
| M7 | the codec omits an Action's kind | `CodecReflectionTest` (the bytes open with the kind), `ObservationHashTest` (the pin) |
| M8 | the actions are not put in order | `CodecCanonicalTest` (actions in any order), `SchemaRulesTest` (canonical actions), `ObservationHashTest` (the pin) |
| M9 | an item reference need not match the inventory | `SchemaRulesTest` (Action parameters) |
| M10 | the header and the prompt need not agree | `SchemaRulesTest` (header and prompt) |
| M11 | the hero may stand out of view | `SchemaRulesTest` (the hero in view) |
| M12 | the Belief hash omits the version | `BeliefTest` (the hash's composition) |
| M13 | the Belief hands out its own array | `BeliefTest` (the bytes are copied) |
| M14 | the version bumps without the pin | `ObservationHashTest` (the pin; the sections) |
| M15 | the log is unbounded | `SchemaRulesTest` (the log's bound) |
| M16 | `api` gains a static reader method | `JsonRenderingTest` (no reader) |
| M17 | the inventory accepts any order | `SchemaRulesTest` (the belongings' order) |
| M18 | an unknown level may show | `SchemaRulesTest` (an unidentified item) |
| M19 | hero buffs are ordered by name alone | `CodecCanonicalTest` (two buffs of one name) |
| M20 | `api` gains a nested reader class taking a list of lines | `JsonRenderingTest` (the class pin and the reader scan) |
| M21 | a human's move is accepted as valid | `SchemaRulesTest` (canonical actions) |

All twenty-one caught.

## The fairness review

Run as an isolated `fairness-reviewer` on commit `5731279a0`. Verdict: FINDINGS, none blocking:
no record component, enum member or invariant lets the bot know something a human at the same
screen could not. Seven should-fix findings, all taken in the review commit:

1. **The "never parsed back" test was a heuristic.** It scanned methods only, for a `String`,
   `CharSequence`, `byte[]` or `java.io` parameter; a constructor, an `Object`, a `char[]`, a
   `List<String>` or a `CharBuffer` slipped past. It now pins the exact set of classes in `api`
   (the schema's records and enums, `Action`, and twelve named helpers), so a new class fails until
   it is added after review, and scans constructors as well as methods, treating any array,
   collection, `Object`, `java.io` or `java.nio` parameter as text; M20 shows a nested reader
   caught.
2. **`UseItemOption.option` was not a value the Observation carries.** No section lists the labels
   of the window an item action opens, and they cannot be listed before it opens (the scroll of
   enchantment's three choices), so an index chosen at the item's wait would be blind and the Run
   log would hold a number without a meaning. Since story 1.5 a recognised window in front is an
   Input wait of its own, so that window is a Prompt and its answer an `AnswerPrompt`; the kind is
   dropped and ADR-0014 amended. Twenty kinds remain.
3. **The log claim was false.** "Has seen every message as it arrived" is contradicted by
   `…/ui/GameLog.java:55-131`: the pane takes a frame's messages in one batch, merges, and trims
   the oldest beyond three or five lines before the frame is drawn, so a burst loses its oldest
   messages unseen. The Javadoc, the ADR-0005 amendment, the story and the rule row now say what
   the code does; the signal stays the source, for reproducibility, and the trade-off is recorded.
4. **Hero buffs were ordered by name alone**, so two buffs of one name kept input order and one
   screen could hash two ways. The comparator is now name, timed, turns, as an actor's;
   `CodecCanonicalTest` holds it, and M19 shows the test catches the old comparator.
5. **`MoveTo` was accepted in a valid set**, and the corpus listed it as valid. `ActionsSection`
   refuses it; the corpus keeps it as the human's Action for the Run log only; M21.
6. **Five cites were off.** The wand's status is `Wand.java:336-343`; both status panes print
   `exp/maxExp` (`StatusPane.java:334-345`); the energy number is `WndBag.java:219`; the talents
   pane shows tiers by `TalentsPane.java:75-84`'s gates while the hero holds two tiers from
   creation (`Talent.java:968-970`); `items/journal` and `items/remains` had no family named.
7. **A lone surrogate went through `JsonWriter.quote` raw**, which is not the same UTF-8 on every
   platform; it is escaped as `\uXXXX` now, pairs stay raw, and the test covers both.

The review also noted for later stories that `api` cannot stop a harness from building a `Belief`
out of oracle data, so the harness leak tests must hold that the harness only carries Beliefs.

## Deviations

- ADR-0005's table said "quickslots" and "talents as the pane shows them" without shapes; the shapes here are the ones cited above.
- The `image` field of ADR-0006's items row is not carried.
- No upstream file is touched; no manual `:desktop:debug` check applies.

## Known limitations, handed forward

- **The Prompt's richer windows are flattened**: a trade window's item and price, a subclass choice's two descriptions, become title, text and labels by story 1.10's mapping.
- **The free-form strings grow**: item names, actions, statuses, talent and ability names, note titles and bodies, log text, prompt text. Stories 1.9 to 1.11 pin each to the screen's own text.
- **`validActions` does not exist yet**; until story 1.12 every Observation an Observer builds has `ActionsSection.NONE`.
- **The log may carry a message the pane never drew**, when a burst exceeds three or five lines in one frame; the signal is the source, for reproducibility, and ADR-0005 records the trade-off.
- **An item's option window needs a Prompt kind** that `PromptKind` does not have yet; story 1.10 adds it when it maps the windows.
- **The JSON is canonical, not pretty**; a reader pipes it through a formatter.

## Follow-ups for later stories

- Story 1.9: the hero section's mapping, including `talentPointsAvailable` and the placeholder quickslots.
- Story 1.10: the inventory in belongings order with `ItemKind` by package, the journal, the log listener capturing exactly what the pane receives, the Prompt windows into labels, a kind for an item's option window.
- Story 1.9: the talents pane's tier gates, so the section carries the tiers drawn, not the tiers held.
- Story 1.12: `validActions(Observation)` over the records defined here; `Wait` absent under a Prompt.
- Story 1.13: the executor resolving `ItemRef` by re-walking the belongings.
