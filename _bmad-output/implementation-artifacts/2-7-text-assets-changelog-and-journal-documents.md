---
story: 2.7
key: 2-7-text-assets-changelog-and-journal-documents
title: "Text, assets, changelog and journal documents"
epic: 2
issue: 41
type: 'feature'
status: 'in-progress'
created: '2026-09-17'
updated: '2026-09-17'
review_loop_iteration: 0
baseline_commit: 'cd4ae4b8b'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Codex carries what the game does but not what it says. The overlay has to speak the
game's own words, the lore pipeline needs the journal documents it will quote, and a documentation
claim about a mechanic needs a version and a date to sit against. None of that is generated today,
and the rules page that records it was written at the previous tag and is marked needs-review.

**Approach:** Four tables added to `:codex:generate`. `strings.json`: every key of the game's nine
English bundles with its value, the bundle it came from, the class the game's own key rule resolves
it to, and its citation. `assets.json`: every asset path the game names, from the asset class's
constants and from the literal strings loaded outside it, each cited and each said to exist or not.
`changelog.json`: every entry of the changelist package with its version title, whether it is a
major heading, the date where the entry states one, and the headings under it, plus the pinned
version and the save-compatibility constants. `documents.json`: the journal documents with their
pages in order, each page's title and body as the bundle gives them.

## Boundaries & Constraints

**Always:** Every value is read from the pinned bundles or the pinned source and cited, as every
other table is. English is the only language read, since the Codex is seed-free and language-free
and the harness already proves a generation is identical under another language. A key whose prefix
names no class the game compiles is carried with an empty class and the reason, never guessed at. A
table says what it does not know rather than inventing it: a changelog entry with no date carries
none and says so. Codex version 7.

**Ask First:** A hook; a boot; reading a bundle other than English; committing any file over 8 MB.

**Never:** No translated bundle is read or carried. No string is rendered, and nothing in the
changelist package is constructed, since `ChangeInfo`'s constructor renders text through the
toolkit. No journal page state is read, and nothing here touches a Run's journal.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| A string keyed by a class | `items.armor.platearmor.name` | the value, the bundle, `items.armor.PlateArmor`, cited to the bundle line | N/A |
| A string keyed by no class | a `ui.` or a window key | the value with an empty class and the stated reason | N/A |
| A key the game overrides by superclass | a class with no key of its own | carried once, on the class whose key it is | N/A |
| An asset the class names | `Assets.Interfaces.BANNERS` | its path, its constant, cited, and that the file exists | A named path with no file fails the generation |
| An asset a literal loads | `effects/fireball-tall.png` | carried with the file and line that loads it | N/A |
| A dated changelog entry | the v3.3.0 entry | its title, major flag, date and headings, cited | N/A |
| An undated changelog entry | the pinned version's own entry | the same without a date, and stated to have none | N/A |
| A document | `ADVENTURERS_GUIDE` | lore flag, title, hint, and its pages in order with title and body | A page with no bundle text fails naming it |
| Two generations | the seed moved | identical bytes | N/A |

</frozen-after-approval>

## Code Map

- `core/.../messages/Messages.java:66-76` -- the nine bundles and their search order; `:125-147`
  the key rule (fully qualified name minus the package prefix, lower-cased in `Locale.ENGLISH`,
  plus the key, with a superclass retry and `!!!NO TEXT FOUND!!!`); `:149-159` the missing marker.
  `shatterfish/codex/.../Names.java` already mirrors the key rule and reads a bundle file; extend
  it rather than writing a second reader, and reuse its ASCII `lower()` since `Locale` is banned.
- `core/src/main/assets/messages/<area>/<area>.properties` -- the nine English bundles (4,976 keys:
  actors 1609, items 2073, journal 178, levels 241, misc 235, plants 67, scenes 148, ui 49,
  windows 376). Translated files are `<area>_<code>.properties` and are not read.
- `core/.../Assets.java:24-350` -- one outer class, nested classes (`Effects`, `Environment`,
  `Fonts`, `Interfaces`, `Music`, `Sounds`, `Splashes`, `Sprites`), 256 `static final String`
  constants. The six paths loaded by literal strings elsewhere, which `docs/codebase-map.md:43`
  names: `effects/fireball-tall.png` and `effects/fireball-short.png`
  (`core/.../effects/Fireball.java:40,46`), `gdx/cursor_mouse.png` and `gdx/cursor_controller.png`
  (`SPD-classes/.../noosa/ui/Cursor.java:40-41`), `gdx/textfield.json`
  (`SPD-classes/.../noosa/TextInput.java:79`), `fonts/pixel_font.ttf`
  (`desktop/.../DesktopPlatformSupport.java:129`). Two of those roots are outside
  `Sources.SOURCE_ROOT`, so `Sources` must accept the `SPD-classes` and `desktop` source roots.
- `core/.../ui/changelist/` -- `ChangeInfo.java:31-60` (`title`, `major`, `text`; the constructor
  calls `PixelScene.renderTextBlock`, so nothing here may be constructed), `ChangeButton.java`,
  and eighteen `vN_X_Changes.java` files plus `Pixel_Dungeon_Changes.java`, each with
  `add_*(ArrayList<ChangeInfo>)` methods building `new ChangeInfo("title", major, "text")` and
  `new ChangeButton(icon, "title", "body")`. `v4_X_Changes.java:38-45` is the pinned version's own
  entry. Dates appear inside entry text as "Released <Month> <day>, <year>".
- `core/.../journal/Document.java:37-47` (eight documents, lore flag), `:222-244` (the four key
  shapes: `<DOC>.title`, `.discover_hint`, `.<page>.title`, `.<page>.body`), `:259-332` (the page
  order, as `pagesStates` insertion order). The enum constructs `Icons`/`ItemSpriteSheet`
  constants, so read the enum from source rather than constructing it.
- `build.gradle:17-18` -- `appVersionName`, `appVersionCode`; `Upstream.tag` already reads the
  first. `core/.../ShatteredPixelDungeon.java:37-45` -- the save-compatibility constants.
- `shatterfish/codex/.../Sources.java` -- `file`, `stripped`, `under`, `text`, `citation`;
  `Generate.java` -- the task's table map; `CodexJson`/`Codex` -- the records and the rendering.
- `docs/rules/text-assets.md` -- five rows are `needs-review`, flipped by v4.0.0 (the document
  counts, the changelog tabs and dates, the version constants, the language count, the toolchain).
  This story reads all of them and re-cites them at the pinned tag.

## Tasks & Acceptance

**Execution:**
- [ ] `shatterfish/codex/.../Sources.java` -- accept the `SPD-classes` and `desktop` source roots --
  the six literal assets are not all under `core`.
- [ ] `shatterfish/api/.../Codex.java`, `CodexJson.java` -- `VERSION = 7`; records `StringEntry`,
  `AssetEntry`, `ChangeEntry`, `DocumentEntry`, `DocumentPage`, `VersionRecord`; one entry per line.
- [ ] `shatterfish/codex/.../Text.java` -- read the nine English bundles, resolve each key to the
  class the game's rule names, carry value, bundle, class, reason and citation -- `strings.json`.
- [ ] `shatterfish/codex/.../Assets.java` (generator-side) -- the asset constants and the literal
  loads, each cited, each checked to exist -- `assets.json`.
- [ ] `shatterfish/codex/.../Changelog.java` -- the changelist package read from source: entries,
  major flags, dates where stated, headings, plus the version and the compatibility constants --
  `changelog.json`.
- [ ] `shatterfish/codex/.../Documents.java` -- the eight documents, their pages in order and each
  page's title and body -- `documents.json`.
- [ ] `shatterfish/codex/.../Generate.java` -- the four tables in the map.
- [ ] `shatterfish/api/src/test/.../CodexJsonTest.java` -- goldens and refusals for each new record.
- [ ] `shatterfish/codex/src/test/.../TextReaderTest.java` -- the key rule, the superclass retry,
  a date that is stated and one that is not, and every refusal the pinned tree cannot reach.
- [ ] `shatterfish/codex/src/test/.../CodexCompletenessTest.java` -- every bundle key, every asset
  constant, every changelist class and every document and page is in a table.
- [ ] `shatterfish/codex/src/test/.../CodexLeakTest.java` -- pinned rows, every citation resolved,
  the live Run unchanged.
- [ ] `codex/v4.0.0/` regenerated; `docs/adr/0017-...md` amendment; `docs/codex/index.md`;
  `docs/glossary.md`; `docs/rules/text-assets.md` re-cited at the pinned tag.

**Acceptance Criteria:**
- Given the nine English bundles, when the table is read, then every key appears once with its
  value and the class the game's own key rule resolves it to, or an empty class and a stated
  reason (`CodexCompletenessTest`, `TextReaderTest`).
- Given the asset class and the files that load an asset by a literal string, when the index is
  read, then it carries every constant and the six literal paths the codebase map names, each
  cited, and fails if a named path has no file (`CodexCompletenessTest`, `CodexLeakTest`).
- Given the changelist package, when the changelog is read, then every entry carries its title,
  whether it is a major heading and the date where the entry states one, and the pinned version's
  own entry is recorded as carrying no date (`CodexLeakTest`).
- Given the journal documents, when the table is read, then all eight appear with their lore flag
  and their pages in order, each with its title and body (`CodexCompletenessTest`, `CodexLeakTest`).
- Given two generations with the Codex's seed moved between them, when the bytes are compared, then
  they are identical (`CodexSeedFreeTest`).

## Spec Change Log

## Design Notes

The wall is the same one stories 2.3 and 2.5 met. `ChangeInfo`'s constructor calls
`PixelScene.renderTextBlock`, and `Document`'s constants reference sprite sheets, so neither can be
constructed in a generator that may not boot the toolkit. Both are therefore read from source, as
the decks' weights and the recipe registries are, and the reader fails by name on a shape it does
not know rather than guessing.

A string's class is the game's own rule read backwards: the key's prefix up to the last dot is a
lower-cased class name, so the reader holds the set of compiled class names lower-cased and looks
the prefix up. A prefix that matches nothing is not an error, since the game keys plenty of text to
no class at all (windows, buttons, scene labels); it is carried with the reason. A prefix that
matches more than one class would be, and fails.

A date is the game's own words: the entries write "Released December 4th, 2025" inside the text.
The reader takes that phrase where it appears and carries it verbatim with its citation; it never
parses it into a calendar date, because the Codex carries what the game says.

## Verification

**Commands:**
- `./gradlew :codex:generate -Pshatterfish.mobile=off && git status --short codex/` -- expected:
  nothing changed after the commit.
- `./gradlew :api:test :codex:test -Pshatterfish.mobile=off` -- expected: green.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- expected:
  green.
