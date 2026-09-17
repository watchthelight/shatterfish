---
story: 2.8
key: 2-8-the-vanilla-versus-shattered-vocabulary-diff
title: "The vanilla-versus-Shattered vocabulary diff"
epic: 2
issue: 42
type: 'feature'
status: 'in-progress'
created: '2026-09-17'
updated: '2026-09-17'
review_loop_iteration: 0
baseline_commit: '938619ac9'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The lore pipeline will read forum posts, and most of the internet's Pixel Dungeon
knowledge is about the original game, not this one. A claim about a "wand of magic missile" or a
"marsupial rat" may be true of vanilla and false here, and nothing in the Codex can tell the two
apart. Nothing consumes the diff yet; the variant classifier of epic 7 is what it is for.

**Approach:** One table, `vocabulary.json`, generated from two pinned sources: the game this fork
follows, and vanilla Pixel Dungeon at a tag of its own. For every display name either game gives a
mob or an item, the row says which game has it, what class carries it on each side, and where the
two disagree about the numbers each states plainly about itself. Every row cites both sources by
`path:line` at their own tags; a name only one game has cites the one that has it.

## Boundaries & Constraints

**Always:** The vanilla source is read only and never built, never on a compile path, and never
imported by any Shatterfish module. Both sides are read from source text alone, since the vanilla
tree is a different game whose classes this build must not load. A comparison is made only where
both games state the same kind of fact plainly; anything else is named as a difference the table
does not judge. Every row cites both tags. Codex version 8.

**Ask First:** How the vanilla source reaches the working tree and continuous integration, which
is the one decision this story cannot take for itself; building the vanilla source; adding it to
any compile path; any dependency of a Shatterfish module on it.

**Never:** No vanilla class is loaded, constructed or run. No Shatterfish code imports
`com.watabou.pixeldungeon`. No mechanic is compared by inference: a number the two games state in
different shapes is a difference the row records, not one it resolves.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| A name both games give | "gnoll scout" | one row, both classes, both citations, and the numbers each states | N/A |
| The same name, different numbers | a mob whose health differs | the row records both values and marks the mechanic as differing | N/A |
| A name only this game has | a class the fork added | the row says so and cites this side only | N/A |
| A name only vanilla has | a class the fork dropped | the row says so and cites vanilla only | N/A |
| One name, two classes on a side | a name reused | the row names both classes on that side | A name on one side that cannot be traced to a class fails naming it |
| A number one side states and the other computes | a roll written as a method | the row says the two are not comparable, with the text of each | N/A |
| Two generations | the seed moved | identical bytes | N/A |

</frozen-after-approval>

## Code Map

- **The second source.** `00-Evan/pixel-dungeon-gradle`, the repository the requirements name
  (section 11, "tag to be chosen in E2"). It carries exactly one tag, `archive`, at commit
  `6fffc0768905b5b1f167a05df7274acc10a7ae34`, GPL-3.0, so the tag to choose chooses itself. Its
  mobs live under `core/src/main/java/com/watabou/pixeldungeon/actors/mobs/` and its items under
  `.../items/`.
- **How vanilla states a name.** In the class's own initialiser, not in a bundle:
  `Rat.java` reads `name = "marsupial rat";` with `HP = HT = 8; defenseSkill = 3;` beside it and
  `damageRoll()`, `attackSkill()`, `dr()` as methods. This is the shape story 2.2's reader already
  knows from the fork's own mobs, so `Sources` reads it unchanged once the tree is reachable.
- **How this game states a name.** Through the bundles, which story 2.7 already carries:
  `codex/v4.0.0/strings.json` holds every key with the class it names, so this side of the diff is
  a read of a table the Codex already has rather than a second walk of the source.
- `shatterfish/codex/.../Mobs.java` and `Items.java` -- the fork's side of the numbers, already
  generated: `mobs.json` and `items.json` carry the stats and the display names to compare against.
- `shatterfish/codex/.../Sources.java` -- `SOURCE_ROOTS` is the fork's three modules; the vanilla
  tree needs a root of its own and a tag of its own for citations, which is the one structural
  change this story makes to the reader.
- `shatterfish/codex/.../Upstream.java` -- reads this fork's tag from its build script; the vanilla
  side needs the same for its own pin.
- `docs/UPSTREAM.md` -- the pinned-source table, which gains a second row for the vanilla pin.

## Tasks & Acceptance

**Execution:**
- [ ] The vanilla source reaches the working tree and continuous integration by whichever way the
  human chooses (see Design Notes) -- **this task is blocked on that decision.**
- [ ] `shatterfish/api/.../Codex.java`, `CodexJson.java` -- `VERSION = 8`; records `VocabularyEntry`
  and `MechanicDifference`; one row per line.
- [ ] `shatterfish/codex/.../Sources.java` -- a second source tree with its own root and tag, so a
  citation can name which game it points into.
- [ ] `shatterfish/codex/.../Vanilla.java` -- read vanilla's mobs and items: display name from the
  initialiser, the numbers it states plainly, each cited.
- [ ] `shatterfish/codex/.../Vocabulary.java` -- join the two sides by display name, record what
  each side has and where they differ -- `vocabulary.json`.
- [ ] `shatterfish/codex/.../Generate.java` -- the table in the map.
- [ ] `shatterfish/api/src/test/.../CodexJsonTest.java` -- goldens and refusals for both records.
- [ ] `shatterfish/codex/src/test/.../VocabularyTest.java` -- the join, the four row shapes of the
  matrix, and every refusal the two pinned trees cannot reach.
- [ ] `shatterfish/codex/src/test/.../CodexCompletenessTest.java` -- every vanilla mob and item
  class is in the table or named excluded with a reason.
- [ ] `shatterfish/codex/src/test/.../CodexLeakTest.java` -- the rows pinned, both citations
  resolved at their own tags, no Shatterfish class depending on `com.watabou.pixeldungeon`.
- [ ] `codex/v4.0.0/` regenerated; `docs/adr/0017-...md` amendment; `docs/UPSTREAM.md` second pin;
  `docs/codex/index.md`; `docs/glossary.md`.

**Acceptance Criteria:**
- Given both pinned sources, when the diff is generated, then every display name either game gives
  a mob or an item appears once, saying which games have it (`CodexCompletenessTest`).
- Given a name both games have, when its row is read, then it carries both classes, both citations
  and every number the two state differently (`CodexLeakTest`, `VocabularyTest`).
- Given a mechanic one side states and the other does not state in the same shape, when the row is
  read, then it says the two are not comparable and carries the text of each (`VocabularyTest`).
- Given the table, when a consumer looks for one, then it finds none: the diff is marked as the
  input the epic 7 variant classifier will use and nothing reads it yet (`CodexLeakTest`).
- Given two generations with the Codex's seed moved between them, when the bytes are compared, then
  they are identical (`CodexSeedFreeTest`).

## Spec Change Log

## Design Notes

**The one decision this story cannot take.** The vanilla tree must be on disk for the generator to
read it and cite it, and it must be on disk in continuous integration too, since the drift check
regenerates there. `CLAUDE.md` reserves creating a remote for the human, so the choice is theirs.
Three ways, with what each costs:

- **A git remote and a fetched tag, checked out into an ignored folder.** One remote, one tag, no
  new files in the tree; the workflow fetches the same tag before generating. Closest to how the
  fork already pins its own upstream, and the pin is one line in `docs/UPSTREAM.md`.
- **A submodule.** The pin lives in the tree and clones come with it, at the cost of a submodule
  every checkout must initialise, including the continuous integration one.
- **Vendoring the names.** Commit only what the diff reads — the display names and the numbers —
  as a generated file with its own citations. No second tree anywhere, but the Codex would then
  carry a copy of another project's data rather than reading it, which is the folklore failure this
  epic exists to prevent.

**Why the join is by display name.** The two games share almost no class names, and the thing the
lore pipeline has to classify is a name a person wrote in a forum post. So the key is the words a
player sees, and the classes are what each row cites.

## Verification

**Commands:**
- `./gradlew :codex:generate -Pshatterfish.mobile=off && git status --short codex/` -- expected:
  nothing changed after the commit.
- `./gradlew :api:test :codex:test -Pshatterfish.mobile=off` -- expected: green.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- expected:
  green.
