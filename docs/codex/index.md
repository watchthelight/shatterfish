# Codex

!!! info "Generated"

    From `codex/v4.0.0/` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

The Codex is the general game knowledge the Brain is allowed to have (FR-14 to FR-17,
[ADR-0017](../adr/0017-codex-generation-and-citations.md)) and the ground truth the lore
pipeline's variant classifier checks against. One task reads the pinned upstream tag -- no Run,
no seed, no Profile -- and writes both the tables under `codex/v4.0.0/` and the pages under this
section; continuous integration regenerates and fails the build if either has drifted, so a page
can never describe a tag it does not come from. Every value is cited to the `path:line` it was
read from, which is what a claim about the game is settled by
([Fairness](../fairness.md)).

These pages index the tables; they do not repeat them. The entries are already committed,
diffable and cited in the JSON each page links, and one table alone holds 4,976 of them.
What a page adds is what a reader brings to a table: what it holds, how many entries there
are, how a row is shaped, which files of the pinned tree it was read from, and, in full, the
entries whose reader had to name a reason.

## The tables

An entry is an object in one of a table's own lists: the list the file is, where the file is a
list, or the lists the object at its root holds. A number a table states that is not an entry
-- a threshold, an expression, a tag -- is a value, and that table's page names it.

| Table | Entries | Page | JSON |
|---|---:|---|---|
| `assets.json` | 259 | [Assets](assets.md) | [58.5 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/assets.json) |
| `challenges.json` | 9 | [Challenges](challenges.md) | [1.3 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/challenges.json) |
| `changelog.json` | 199 | [Changelog](changelog.md) | [196.7 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/changelog.json) |
| `combat.json` | 548 | [Combat](combat.md) | [150.1 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/combat.json) |
| `decks.json` | 26 | [Decks](decks.md) | [38.8 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/decks.json) |
| `documents.json` | 8 | [Documents](documents.md) | [44.3 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/documents.json) |
| `guarantees.json` | 33 | [Guarantees](guarantees.md) | [130.3 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/guarantees.json) |
| `hero-classes.json` | 6 | [Hero classes](hero-classes.md) | [1.1 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/hero-classes.json) |
| `items.json` | 307 | [Items](items.md) | [185.4 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/items.json) |
| `levels.json` | 44 | [Levels](levels.md) | [21.8 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/levels.json) |
| `mobs.json` | 129 | [Mobs](mobs.md) | [172.8 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/mobs.json) |
| `recipes.json` | 39 | [Recipes](recipes.md) | [29.2 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/recipes.json) |
| `rooms.json` | 39 | [Rooms](rooms.md) | [26.2 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/rooms.json) |
| `spawn-rotation.json` | 43 | [Spawn rotation](spawn-rotation.md) | [14.0 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/spawn-rotation.json) |
| `strings.json` | 4976 | [Strings](strings.md) | [1.7 MB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/strings.json) |
| `tiers.json` | 7 | [Tiers](tiers.md) | [2.4 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/tiers.json) |
| `traps.json` | 44 | [Traps](traps.md) | [60.9 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/traps.json) |
| `vocabulary.json` | 449 | [Vocabulary](vocabulary.md) | [268.8 KB](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/vocabulary.json) |

Codex version 8 names the shape of these tables; it is recorded in a Run
log's header ([ADR-0011](../adr/0011-run-log-format.md)), so a log says which Codex a Run was
played with.
