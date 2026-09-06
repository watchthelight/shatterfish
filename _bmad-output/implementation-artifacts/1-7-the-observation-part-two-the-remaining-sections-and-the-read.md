---
story: 1.7
key: 1-7-the-observation-part-two-the-remaining-sections-and-the-read
title: "The Observation, part two: the remaining sections and the readable form"
epic: 1
issue: 20
status: in-progress
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

- `shatterfish/api/src/main/java/org/shatterfish/api/`: `HeroSection`, `TalentView`, `QuickslotView`, `InventorySection`, `ItemView`, `ItemRef`, `JournalSection`, `NoteView`, `KnownAppearance`, `LogSection`, `LogLine`, `ActionsSection`, `Action` (sealed, twenty-one records), `PromptSection`; enums `HeroSubclass`, `Hunger`, `ItemKind`, `EquipSlot`, `NoteKind`, `LogTone`; `Observation` with nine sections, `withActions` and `json()`; `ObservationCodec` at schema version 2 with the nine sections; `JsonWriter`, `ObservationJson`; `Belief`; `Canon.positional`.
- Tests: `Variants` (the schema by reflection, shared), `CodecReflectionTest` (every component reaches the bytes and the JSON; one Action record per kind), `CodecEqualityTest`, `JsonRenderingTest` with `StrictJson`, `BeliefTest`, `CodecCanonicalTest` and `SchemaRulesTest` extended, `ObservationHashTest` repinned under version 2, `Corpus` with a second Observation that has the chasm Prompt open.
- Docs: ADR-0005 amendment for story 1.7; ADR-0014 amendment for the Action records; six rule rows in `docs/rules/ui.md`; the `api` line of `docs/architecture.md`.

## What the story found

**The valid-Action set is circular by the decision's own table, and the record resolves it.**
ADR-0005 says the `actions` section is computed from the Observation and is part of it. The
record is built in two steps: `ActionsSection.NONE` first, then `withActions` once story 1.12's
`validActions` has read the rest. What is hashed is the record with the set.

**The Action records belong to the schema, not to story 1.12.** The `actions` section is a list
of them, so ADR-0014's sealed interface is implemented here with one record per kind; item use
became four kinds by the shape of its target, since a record cannot carry "a cell, an item
reference or an option index" as one value without an optional field, and `MoveTo` is a record
because the Overlay's human clicks must be logged as they were made (ADR-0011).

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

**The log's cap is the schema's, not the pane's.** The pane keeps three or five lines of rendered
text and drops the oldest entries as new ones arrive (`…/ui/GameLog.java:59`, `:107-122`); every
message was shown as it arrived, so the Observation keeps the last sixty-four as a bound on size.

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

**Item use as four kinds.** Alternatives: (a) one `UseItem` with three nullable targets; (b) a
`Target` sealed interface as a component; (c) four kinds. Chosen (c): every component stays a
value, every switch is exhaustive, and the reflection tests need no case for an interface-typed
component.

**The actions' order.** Alternatives: (a) the executor's enumeration order, positional; (b) by
kind then by the action's canonical bytes. Chosen (b): a set enumerated in any order must be one
hash. Pre-mortem: sorting by bytes is not sorting by cell within a kind, since the length prefix
comes first; the order is canonical, not pretty.

**The log's cap.** Alternatives: (a) the pane's own retention, three or five rendered lines,
which depends on UI size and wrapping; (b) a fixed count. Chosen (b), sixty-four: the pane's
retention is a rendering artefact, and the player has read every message.

**The JSON's shape.** Alternatives: (a) keys in component order with whitespace, for reading;
(b) canonical, sorted keys and no whitespace, as ADR-0011 needs for the log. Chosen (b), one
writer for both.

**The Belief's shape.** Alternatives: (a) a record with a `List<Byte>`; (b) a record with a
`byte[]` and identity equality; (c) a final class over a copied array. Chosen (c).

**What the hero section does not carry.** The danger count (derivable from the actors), the
gold collected (a statistic, not a resource), the item sprite index (the name says it), the
guide's pages and the bestiary (static text and cross-Run state).

## Evidence

Pending.

## The fairness review

Pending.

## Deviations

- ADR-0005's table said "quickslots" and "talents as the pane shows them" without shapes; the shapes here are the ones cited above.
- The `image` field of ADR-0006's items row is not carried.
- No upstream file is touched; no manual `:desktop:debug` check applies.

## Known limitations, handed forward

- **The Prompt's richer windows are flattened**: a trade window's item and price, a subclass choice's two descriptions, become title, text and labels by story 1.10's mapping.
- **The free-form strings grow**: item names, actions, statuses, talent and ability names, note titles and bodies, log text, prompt text. Stories 1.9 to 1.11 pin each to the screen's own text.
- **`validActions` does not exist yet**; until story 1.12 every Observation an Observer builds has `ActionsSection.NONE`.
- **The JSON is canonical, not pretty**; a reader pipes it through a formatter.

## Follow-ups for later stories

- Story 1.9: the hero section's mapping, including `talentPointsAvailable` and the placeholder quickslots.
- Story 1.10: the inventory in belongings order with `ItemKind` by package, the journal, the log listener, the Prompt windows into labels.
- Story 1.12: `validActions(Observation)` over the records defined here; `Wait` absent under a Prompt.
- Story 1.13: the executor resolving `ItemRef` by re-walking the belongings.
