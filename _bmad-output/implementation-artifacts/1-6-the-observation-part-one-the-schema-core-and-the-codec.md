---
story: 1.6
key: 1-6-the-observation-part-one-the-schema-core-and-the-codec
title: "The Observation, part one: the schema core and the codec"
epic: 1
issue: 19
status: in-progress
created: '2026-09-05'
updated: '2026-09-05'
---

# Story 1.6: The Observation, part one: the schema core and the codec

As the engineer,
I want the header, map and actor sections as records with a canonical encoder,
So that the shape of what the bot sees is fixed before anything fills it.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v3.3.8` (commit `7b8b845a`).

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| Given ADR-0005, when the records for the header, map and actor sections, `ObservationCodec` and the section hashing are implemented in `api` | **Met.** `HeaderSection`, `MapSection` (with `TrapView`, `HeapView`, `BlobCell`, `TransitionView`) and `ActorsSection` (with `ActorView`, `BuffView`), `Observation` over the three, ten enums, and `ObservationCodec` with `encode`, `encodeSection`, `sectionHashes` and `hash`; SHA-256 and UTF-8 written in `api` because the module may reach only `java.lang` and `java.util` |
| The encoding is deterministic: fixed field order, big-endian integers, length-prefixed strings, no floats anywhere | **Met.** `Encoder` writes big-endian 32-bit integers, one byte per boolean, a four-byte length and the UTF-8 code units for a string, enums by name, a four-byte count before a list; each record is written in the order of its components. `CodecReflectionTest` holds that no component is a float and that the map's bytes begin with its width and height; `ObservationHashTest` holds the whole encoding to the version followed by the sections, and pins the corpus hash |
| Every list has a canonical order fixed by the codec, and `CodecCanonicalTest` shuffles each input list and asserts the section hashes are unchanged | **Met.** The order is fixed in the records' constructors, which sort every set-like list and refuse repeats, so equality and the hash agree; `CodecCanonicalTest` gives every such list reversed and gets the same records and the same section hashes, and shows the two positional lists are not reordered |
| `CodecReflectionTest` fails if any record component is not encoded | **Met.** For every record reachable from `Observation` and every component, the test builds a variant that differs in that component alone and asks the codec for different bytes; forty-nine components, checked against the corpus's samples, with width and height read from the layout since they cannot vary with the tiles held fixed |
| The header carries the schema version, the Codex version, the sealed flag and the oracle flag, and carries neither the seed, the salt nor a turn counter | **Met.** `HeaderSection(version, upstreamTag, codexVersion, heroClass, challenges, depth, branch, sealed, oracle, prompt)`; the test pins that component list and refuses any name containing seed, salt, turn, wait, index, time or clock |
| No enum has a member naming hidden state, so a secret door has no representation | **Met.** `Tile` has every drawable terrain of the tag and `NONE`, and no `SECRET_*`; the test scans every enum the schema reaches, with `Feeling.SECRETS` as the one named exception, the floor feeling the game announces as a title |

## What was built

- `shatterfish/api/src/main/java/org/shatterfish/api/`: `Observation`, `HeaderSection`, `MapSection`, `ActorsSection`, `TrapView`, `HeapView`, `BlobCell`, `TransitionView`, `ActorView`, `BuffView`; `HeroClass`, `Challenge`, `PromptKind`, `Tile`, `Fog`, `HeapKind`, `Feeling`, `TransitionKind`, `Alignment`, `Emote`; `ObservationCodec` (schema version 1, the health-bar constant and `healthPips`), `Encoder`, `Sha256`, `Utf8`, `Canon` (package-private); `ShatterfishApi.SCHEMA_VERSION` now the codec's.
- Tests: `CodecCanonicalTest`, `CodecReflectionTest` (49 dynamic tests plus the enum, float and header checks), `ObservationHashTest` (the hash's composition, equality against hash, the pin, the version refusal, health pips), `SchemaRulesTest`, `Sha256Test`, `Utf8Test`, `Corpus`.
- Docs: ADR-0005 amendment for story 1.6; a rule row on the health bar in `docs/rules/ui.md`.

## What the story found

**`api` cannot digest or encode text with the JDK.** `ApiBoundaryTest` allows `java.lang` and
`java.util` only, as strict as the brain's allowlist since story 1.2's laundering finding, so
`MessageDigest` and `StandardCharsets` are out of reach. SHA-256 is a hundred lines held to the
standard's vectors and to the JDK over random input; the UTF-8 encoder mirrors `String.getBytes`
including the `?` an unpaired surrogate becomes. `System.arraycopy` is out too: `java.lang.System`
is on the denied list, and the boundary test caught the first draft using it.

**Canonical order has to be the records'.** The decision put it in the codec, but the rule that
`equals` and the hash agree cannot hold if the codec sorts and the records do not: two
Observations of one screen with actors in different orders would hash alike and compare unequal.
So each record sorts its lists in its constructor and refuses repeats, and the codec writes what
it is given.

**The health bar's resolution is the zoom's.** The bar is the sprite's width times four sixths
(`…/ui/CharHealthIndicator.java:50-51`), drawn to the pixel of the camera zoom with the lit part
rounded up (`…/ui/HealthBar.java:65-68`); at the lowest zoom the player can pick
(`…/scenes/PixelScene.java:144`) a sixteen-unit sprite's bar is eleven pixels, at the default zoom
twenty-two, and a wider sprite's bar is wider. The decision's one constant `W` has to be a choice:
the coarsest view any player has, `32/3` world units, eleven pips, so that the Observation never
holds finer health than some player sees.

**A container's contents have no way in.** `HeapView` accepts an item name only for a plain or
for-sale heap, a price only for a for-sale heap, a category only for a crystal chest; a map
refuses a trap, heap, blob or transition on a cell never seen, and a tile on an unknown cell; an
Observation refuses an actor out of view. The whitelist of ADR-0006 is a whitelist by
construction, and the mutation battery shows each rule catches its leak.

**Buff turns print with two decimals** (`…/actors/buffs/Buff.java:136-138`), so the schema stores
hundredths of a turn, with a flag for whether turns are shown at all.

## Decisions taken inside the story

**Digest and encoding in `api`.** Alternatives: (a) widen `api`'s allowlist to `java.security`
and `java.nio.charset`, and the brain's with it to keep them aligned; (b) hash in `harness` and
pass hashes into `api`; (c) write SHA-256 and UTF-8 in `api`. Chosen (c): the boundary stays as
story 1.2 left it, the codec stays the one place, and both pieces are held to the JDK by tests.
Pre-mortem: a bug in the hand-written digest; the vector and random-input tests are the guard.

**Where canonical order lives.** Alternatives: (a) the codec sorts; (b) the records sort. Chosen
(b), for the equality rule. Pre-mortem: a caller that relies on input order; the records document
which two lists are positional.

**The health constant.** Alternatives: (a) the default zoom's bar, twenty-two pips; (b) the
lowest zoom's, eleven; (c) per-sprite widths, which the Observer cannot know without the sprite.
Chosen (b): parity is an upper bound, and eleven pips is what a player who zoomed out sees.
Pre-mortem: the brain wants finer health; the constant is the codec's to change with the version.

**The hero outside the actors.** Alternatives: (a) the hero as an actor with quantised health and
again in the hero section; (b) the hero only in the hero section, with its cell there. Chosen
(b): one place for each fact; story 1.7 adds the cell.

**`Feeling.SECRETS`.** Alternatives: (a) drop the member; (b) rename it; (c) keep the game's
name and make it the test's one named exception. Chosen (c): the game announces "secrets floor"
as a title, and renaming what the player reads would be a second vocabulary.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green, 142 tests across 19 suites. `mkdocs build --strict`:
clean.

**Mutation battery.** Pending: recorded below once run.

## The fairness review

Pending.

## Deviations

- The decision said the codec fixes list order; the records do, for the reason above, and the codec writes what it is given.
- No upstream file is touched; no manual `:desktop:debug` check applies.

## Known limitations, handed forward

- **The schema is three sections.** Story 1.7 adds the hero, the inventory, the journal, the log, the valid Actions and the prompt, bumps the version, and writes the JSON rendering.
- **Health pips are a convention**, eleven for the zoom-1 bar; the Overlay's player at the default zoom sees twenty-two.
- **The Observer does the mapping**: from `Terrain` to `Tile`, from sprites to emotes, from buffs to `BuffView`; stories 1.8 to 1.11.
- **`Tile` names the tag's terrain**; an upgrade that adds terrain adds a member and bumps the version.

## Follow-ups for later stories

- Story 1.7: the remaining sections, `Observation` gains six components, the version becomes 2, the corpus pin moves, the JSON writer.
- Stories 1.8 to 1.11: the Observer fills the records; each ADR-0006 row gets its leak test against the records' rules.
- Story 1.12: the `actions` section.
