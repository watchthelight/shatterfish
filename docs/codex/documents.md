# Documents

!!! info "Generated"

    From `codex/v4.0.0/documents.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

The journal's guides and lore with their pages in the game's own order and the words of each
(story 2.7).

[`codex/v4.0.0/documents.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/documents.json) holds 8 entries in 44.3 KB. The entries are not repeated here: they are committed, diffable and cited in that file.

## How a row is shaped

8 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `document` | string |
| `hint` | string |
| `lore` | boolean |
| `pages` | list |
| `pages[]` | object |
| `pages[].body` | string |
| `pages[].citation` | object |
| `pages[].citation.line` | number |
| `pages[].citation.path` | string |
| `pages[].page` | string |
| `pages[].title` | string |
| `pages[].titleCitation` | object |
| `pages[].titleCitation.line` | number |
| `pages[].titleCitation.path` | string |
| `title` | string |
| `titleCitation` | object |
| `titleCitation.line` | number |
| `titleCitation.path` | string |
| `hintCitation` | object |
| `hintCitation.line` | number |
| `hintCitation.path` | string |

## Citations

Every value above was read from the pinned tree at the line the entry carries. The reader
recorded 140 citations in 2 files:

| Source | Citations | Lines |
|---|---:|---|
| [`core/src/main/assets/messages/journal/journal.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/journal/journal.properties) | 132 | 36-175 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/journal/Document.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/journal/Document.java) | 8 | 39-47 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
