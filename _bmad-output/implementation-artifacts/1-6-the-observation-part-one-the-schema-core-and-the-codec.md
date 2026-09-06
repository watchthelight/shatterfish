---
story: 1.6
key: 1-6-the-observation-part-one-the-schema-core-and-the-codec
title: "The Observation, part one: the schema core and the codec"
epic: 1
issue: 19
status: done
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
| `CodecReflectionTest` fails if any record component is not encoded | **Met.** For every record reachable from `Observation` and every component, the test builds a variant that differs in that component alone and asks the codec for different bytes; forty-nine components, checked against the corpus's samples, with width and height read from the layout since they cannot vary with the tiles held fixed, and every string also varied at the same length so a codec writing the length alone cannot pass |
| The header carries the schema version, the Codex version, the sealed flag and the oracle flag, and carries neither the seed, the salt nor a turn counter | **Met.** `HeaderSection(version, upstreamTag, codexVersion, heroClass, challenges, depth, branch, sealed, oracle, prompt)`; the test pins that component list and refuses any name containing seed, salt, turn, wait, index, time or clock |
| No enum has a member naming hidden state, so a secret door has no representation | **Met.** `Tile` has one member per visual the tile sheet distinguishes and `NONE`: no `SECRET_*`, and nothing that is drawn like another terrain; the test scans every enum the schema reaches, with `Feeling.SECRETS` as the one named exception, the floor feeling the game logs on arrival and titles in the menu pane |

## What was built

- `shatterfish/api/src/main/java/org/shatterfish/api/`: `Observation`, `HeaderSection`, `MapSection`, `ActorsSection`, `TrapView`, `HeapView`, `BlobCell`, `TransitionView`, `ActorView`, `BuffView`; `HeroClass`, `Challenge`, `PromptKind`, `Tile`, `Fog`, `HeapKind`, `Feeling`, `TransitionKind`, `Alignment`, `Emote`; `ObservationCodec` (schema version 1, the health-bar constant and `healthPips`), `Encoder`, `Sha256`, `Utf8`, `Canon` (package-private); `ShatterfishApi.SCHEMA_VERSION` now the codec's.
- Tests: `CodecCanonicalTest`, `CodecReflectionTest` (49 dynamic tests plus the enum, float and header checks), `ObservationHashTest` (the hash's composition, equality against hash, the pin, the version refusal, health pips), `SchemaRulesTest` (nine refusals), `Sha256Test`, `Utf8Test`, `Corpus`.
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

**`Tile` is the tile sheet's visuals, not the terrain table.** Raw terrain has more than its two
secret constants that the screen does not tell apart. A trap's floor, active or not, and custom
decoration floor draw as the empty floor (`…/tiles/DungeonTileSheet.java:427-431`), the trap
itself on a layer of its own only while revealed (`…/tiles/TerrainFeaturesTilemap.java:57-62`),
and a door the hero locked draws as a locked door and is named one
(`DungeonTileSheet.java:446-447`; `…/levels/Level.java:1584-1586`). The first draft carried
`TRAP`, `INACTIVE_TRAP`, `CUSTOM_DECO`, `CUSTOM_DECO_EMPTY` and `HERO_LKD_DR`, copied from
`Terrain`; the fairness review found them. `Tile` is now the thirteen direct visuals and the
seventeen flat ones of the sheet's two tables (`DungeonTileSheet.java:414-465`), chasm and water,
which are stitched apart (`:73-84`), and `NONE`; a trap is a `TrapView` and nothing in the tile.

**The screen offers exact health; the schema keeps a coarser convention.** The bar over a sprite
is the sprite's width times four sixths (`…/ui/CharHealthIndicator.java:50-51`), drawn to the
pixel of the camera zoom with the lit part rounded up (`…/ui/HealthBar.java:66-69`), so its
resolution depends on the sprite, ten to twenty-seven units wide at the tag, and on the zoom,
from 1 (`…/scenes/PixelScene.java:144`): eight pixels for a twelve-unit gnoll, eleven for a
sixteen-unit rat. The first draft called eleven pips "the coarsest view any player has"; the
review showed that no constant is the bar, and that examining a character shows a bar about a
hundred UI units wide at a UI zoom of at least two (`…/windows/WndInfoMob.java:58-59`, `:72`,
`:77`; `…/windows/WndTitledMessage.java:32`; `PixelScene.java:133-137`, `:150`), which resolves
every point of health of every character that is not a boss, while a boss's bar prints its health
as a number (`…/ui/BossHealthBar.java:205-206`). So ADR-0006's "never exact HP" is stricter than
the screen. The codec keeps `W = 32/3`, eleven pips, as a convention: a loss to the brain and
never a leak, and story 1.9's to loosen with a version bump.

**A blob is drawn only in view.** The emitter draws a blob where the hero sees and nowhere else
(`…/effects/BlobEmitter.java:62-64`), so a `BlobCell` must stand on a visible cell, where the
first draft allowed any cell the player had seen. Two blobs the game marks always visible, drawn
under the fog of a remembered cell (`…/actors/mobs/Tengu.java:850`, `:1045`;
`…/items/artifacts/SkeletonKey.java:475`), have no representation in this version.

**A container's contents have no way in.** `HeapView` accepts an item name only for a plain or
for-sale heap, a price only for a for-sale heap, a category only for a crystal chest; a map
refuses a trap, heap or transition on a cell never seen, a blob out of view, and a tile on an
unknown cell; an Observation refuses an actor out of view. The whitelist of ADR-0006 is a
whitelist by construction, and the mutation battery shows each rule catches its leak. What the
records cannot check is the free text: a trap's kind, a heap's item, a blob's kind, an actor's or
a buff's name are strings, and the Observer stories' leak tests must pin each to the name the
screen shows.

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

**What `Tile` enumerates.** Alternatives: (a) `Terrain` minus its secret constants, one member
per terrain; (b) one member per visual the tile sheet distinguishes; (c) the visual plus a
separate trap-floor member. Chosen (b), after the review: (a) carries the trap bit and the
hero-locked bit out of raw terrain under another name, which is what ADR-0005's option 11
rejected, and (c) is (a) again for one bit. Pre-mortem: an Observer mapping a terrain the table
does not name; story 1.8's leak test walks every `Terrain` constant through the mapping and
refuses any that is not in the sheet's tables.

**The health constant.** Alternatives: (a) exact health, since the examine window shows it;
(b) the sixteen-unit zoom-1 bar, eleven pips, as a convention; (c) per-sprite widths, which the
Observer cannot know without the sprite. Chosen (b): ADR-0006 says never exact HP, and loosening
an accepted ADR is story 1.9's call with a version bump, not this story's; the schema may see
less than the screen, never more. Pre-mortem: the brain wants finer health; the constant is the
codec's to change with the version.

**The hero outside the actors.** Alternatives: (a) the hero as an actor with quantised health and
again in the hero section; (b) the hero only in the hero section, with its cell there. Chosen
(b): one place for each fact; story 1.7 adds the cell.

**`Feeling.SECRETS`.** Alternatives: (a) drop the member; (b) rename it; (c) keep the game's
name and make it the test's one named exception. Chosen (c): the game logs the feeling's text on
arrival and titles it "secrets floor" in the menu pane's window, and renaming what the player
reads would be a second vocabulary.

**The corpus pin moved under version 1.** The review's `Tile` change moved the pinned corpus hash
from `7d1272…` to `836fbe…` without a version bump: nothing outside this story had encoded an
Observation, so there was no reader to break. From the merge on, a pin move is a version bump, as
the test's message says.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green, 143 tests across 19 suites, 77 of them in
`api`. `mkdocs build --strict`: clean.

**Mutation battery**, fifteen mutations on the committed tree at `42a061cfa`, each applied to a
clean tree, run without `--rerun-tasks`, restored with `git checkout` of the mutated files, and
the tree verified clean after each:

| # | Mutation | Caught by |
|---|---|---|
| M1 | `heap.hidden` is not encoded | `CodecReflectionTest` (`HeapView.hidden`), `ObservationHashTest` (the pin) |
| M2 | the actors are not put in order | `CodecCanonicalTest` (actors in any order), `SchemaRulesTest` (lists canonical) |
| M3 | `Tile` gains `SECRET_DOOR` | `CodecReflectionTest` (no enum names hidden state) |
| M4 | the header carries a seed | compile: `CodecCanonicalTest.java:33` constructs the header positionally |
| M5 | integers written little-endian | `CodecReflectionTest` (the map's layout), `ObservationHashTest` (composition, pin, whole encoding) |
| M6 | the hash omits the version | `ObservationHashTest` (composition, pin) |
| M7 | UTF-8 drops a surrogate pair's fourth byte | `Utf8Test` (random strings against the JDK, the emoji) |
| M8 | one wrong SHA-256 round constant | `Sha256Test` (the FIPS vectors, lengths around the block), `ObservationHashTest` |
| M9 | health pips rounded down | `ObservationHashTest` (health pips) |
| M10 | a heap on an unknown cell is allowed | `SchemaRulesTest` (nothing on an unknown cell) |
| M11 | a chest may show an item | `SchemaRulesTest` (a container shows only itself) |
| M12 | an actor out of view is allowed | `SchemaRulesTest` (a character is drawn only in view) |
| M13 | a buff gains a float | compile: `Corpus.java:67` constructs buffs positionally |
| M14 | the schema version bumps without the pin | `ObservationHashTest` (the pin, under version 2) |
| M15 | a blob on a remembered cell is allowed | `SchemaRulesTest` (a blob stands only in view) |

All fifteen caught. M4 and M13 fail at compile time because the tests construct records
positionally, so the header-name check and the no-float check stand behind them and were not
themselves exercised by the battery.

## The fairness review

Run as an isolated `fairness-reviewer` on commit `afc782524`. Verdict: PASS on parity, five
should-fix findings, none blocking. All five were taken, in commit `42a061cfa`:

1. **Blobs on remembered cells.** The emitter draws a blob only in the hero's field of view, so a
   `BlobCell` on a visited or mapped cell would carry what the screen does not draw. `MapSection`
   now requires `Fog.VISIBLE` for every blob, `SchemaRulesTest` holds it (and M15 shows the check
   catches its removal), and the corpus's extra blob moved to a visible cell. The two blobs the
   game marks always visible have no representation in this version; handed to story 1.8.
2. **`Tile` carried five terrains drawn as others.** `TRAP`, `INACTIVE_TRAP`, `CUSTOM_DECO` and
   `CUSTOM_DECO_EMPTY` draw as the empty floor and `HERO_LKD_DR` as a locked door; a `Tile.TRAP`
   would have been the trap bit read from raw terrain. Dropped; `Tile` is the sheet's visuals, the
   corpus's two trap cells became `EMPTY`, and the pin moved under version 1 (Decisions).
3. **The health justification was false.** Eleven pips is not "the coarsest view any player has":
   sprite bars run from eight pixels up, and the examine window resolves exact health for every
   non-boss while a boss prints its number. The codec's Javadoc, the ADR amendment and the rule
   row are rewritten around that; the constant stays as a convention that loses information and
   leaks none; ADR-0006's exact-HP rule is stricter than the screen, handed to story 1.9, with the
   two drawn bits the schema also drops (the bar hidden at full health,
   `CharHealthIndicator.java:55`; the shield segment, `HealthBar.java:68`).
4. **A codec writing a string's length alone would pass `CodecReflectionTest`.** Every string is
   now also varied at the same length with one letter changed.
5. **`Feeling.SECRETS` was cited to a title on arrival.** The feeling's text is logged on arrival
   (`…/scenes/GameScene.java:663-685`) and its title heads the menu pane's window
   (`…/ui/MenuPane.java:112-115`); `Feeling`, the amendment and the test's message say so.

The review also named two limitations the records cannot close, recorded below: the free-form
strings and the custom tilemap visuals.

## Deviations

- The decision said the codec fixes list order; the records do, for the reason above, and the codec writes what it is given.
- No upstream file is touched; no manual `:desktop:debug` check applies.

## Known limitations, handed forward

- **The schema is three sections.** Story 1.7 adds the hero, the inventory, the journal, the log, the valid Actions and the prompt, bumps the version, and writes the JSON rendering.
- **Health pips are a convention the screen exceeds.** The examine window shows exact health of any non-boss and a boss prints its number; ADR-0006's "never exact HP" is stricter than the screen, and the schema also drops the bar hidden at full health and the shield segment. Story 1.9 decides whether to loosen it, with a version bump.
- **The free-form strings are the smuggling surface.** `TrapView.kind`, `HeapView.item` and `category`, `BlobCell.kinds`, `ActorView.name` and `BuffView.name` are strings the records cannot check; stories 1.8 to 1.11 pin each to the name the screen shows (`name()`, `title()`) in their leak tests.
- **The Observer does the mapping**: from `Terrain` to `Tile` through the sheet's tables, from sprites to emotes, from buffs to `BuffView`; stories 1.8 to 1.11.
- **`Tile` names the tag's visuals.** An upgrade that adds a visual adds a member and bumps the version. Custom tilemap visuals (`…/windows/WndInfoCell.java:50-63`, `:78-97`) have no representation, a fidelity gap for story 1.8 to size.
- **Always-visible blobs are unrepresentable**: the two the game draws under the fog of a remembered cell (Tengu's traps, the skeleton key's) cannot be a `BlobCell` in version 1.

## Follow-ups for later stories

- Story 1.7: the remaining sections, `Observation` gains six components, the version becomes 2, the corpus pin moves, the JSON writer.
- Story 1.8: the `Terrain` to `Tile` mapping from `DungeonTileSheet`'s two tables with a leak test over every `Terrain` constant; the always-visible blobs and the custom visuals.
- Story 1.9: the health rule against the examine window and the boss bar.
- Stories 1.8 to 1.11: the Observer fills the records; each ADR-0006 row gets its leak test against the records' rules, and each string is pinned to the screen's name.
- Story 1.12: the `actions` section.
