---
story: 2.3
key: 2-3-items-generator-weights-decks-and-guarantees
title: "Items, generator weights, decks and guarantees"
epic: 2
issue: 37
type: 'feature'
status: 'done'
created: '2026-09-16'
updated: '2026-09-16'
review_loop_iteration: 0
baseline_commit: 'c7d5fb641'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Brain's beliefs about unknown items have nowhere to start: no table says which
items exist, how the generator weights them, which appearance labels an unidentified potion,
scroll or ring can wear, what an item is worth or what it offers.

**Approach:** Two tables added to `:codex:generate`. `decks.json`: every generator category with
its two category-deck weights, its class list with the first and second deck weights and their
total, read from the generator's public static fields and cited to their declarations; the
appearance-label pools of the three identifiable families; the exotic swap pairs and the chance
expression. `items.json`: every concrete item class a player can meet, with its display name
read from the English bundle by the game's own key rule, its category, its value, its strength
requirement at level 0 with the formula it comes from, and the actions it offers a fresh
instance to a bare hero; constructed by the game's own initialisers where the class constructs
without the toolkit, and read from the declaring class's source where it does not (the potions,
scrolls and rings, whose icons need it). `CodexCompletenessTest` enumerates every concrete
subclass of the game's item type and holds that each is in the table or named excluded with a
reason.

## Boundaries & Constraints

**Always:** The generator constructs items under `GameContext` (the Codex's own generator, the
Run's statics untouched) and a bare `Hero` it makes for the actions; it never calls a method
that draws or that reads a Run (`random()`, `Generator.random`, `isKnown`). A class whose
initialisation needs the toolkit is read from source, flagged, and never constructed. The
generator's own deck state (`probs`, `seed`, `dropped`, `using2ndProbs`) is Run state and is
never read; `defaultProbs`, `defaultProbs2`, `defaultProbsTotal`, `firstProb`, `secondProb` and
`classes` are the tables. Names are read from `core/src/main/assets/messages/**/*.properties`
by the key `Messages` derives (`Messages.java:125-133`), cited to the line. Every entry cites
its class's declaration; every weight cites its assignment. The leak test's live Run holds that
a generation leaves the Run's deck state and identification state as it found them. Codex
version 3.

**Ask First:** Any construction of a potion, scroll or ring in the generator; any reading of the
identification handlers; a hook; a boot.

**Never:** No guarantee schedules, tier tables or limited-drop counters (2.4); no descriptions
or flavour text (2.7); no combat numbers (2.5); no Brain reading (E4).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| A category | `POTION` | first-deck weight 8, second-deck weight 8, twelve classes with `defaultProbs`, `defaultProbs2` and their total, cited | A category whose class count and weight count differ fails naming it |
| A label pool | The potion colours, scroll runes, ring gems | The keys in the game's order with their display names from the bundle, cited to the `put` and the bundle line | A key with no bundle line fails naming it |
| The exotic swap | `ExoticPotion.regToExo`, `ExoticScroll.regToExo` | Every regular-to-exotic pair, the chance expression `0.2f + 0.2f*level` and its value without the trinket (0) | N/A |
| A constructed item | `Sword` | display name, category `WEP_T2` (the deck that lists it), value, strength 12 at level 0 with the formula cited, actions `[DROP, THROW, EQUIP]` | A construction that fails names the class |
| A source-read item | `PotionOfHealing` | display name, category `POTION`, value from `Potion.value()`'s expression (30), actions from the declaring classes' `actions()` (`DROP, THROW, DRINK`), `constructed: false` with the reason | N/A |
| An item in no deck | `Ankh`, a key, a quest item | category empty, the rest as constructed | N/A |
| Completeness | The game's concrete item classes | Each in the table once, or in the generator's named exclusions (no public no-argument constructor; not an item a player meets: placeholders, projectiles, a mob's prop, the base classes); none extra | The test names the missing or extra class |
| The Run | A live Run at an Input wait | The deck state, the identification state and the generator are what they were after a generation | N/A |

</frozen-after-approval>

## Code Map

- `core/.../items/Generator.java:221-252` -- the categories with `(firstProb, secondProb, superClass)`; `:256-273` the fields (`classes`, `probs` Run-mutable, `defaultProbs`, `defaultProbs2`, `defaultProbsTotal`, `seed`, `dropped`, `using2ndProbs`); `:324-611` the static block assigning `X.classes` and `X.defaultProbs`; `:621-690` the category decks (`usingFirstDeck`, `categoryProbs`, `random()`); `:698-745` the draw and the exotic swap at `:730-737`.
- `core/.../items/trinkets/ExoticCrystals.java:48-58` -- `consumableExoticChance(level)`: 0 at level -1, else `0.2f + 0.2f*level`; `items/potions/exotic/ExoticPotion.java:50-57` and `items/scrolls/exotic/ExoticScroll.java` -- `regToExo`, public static.
- `core/.../items/potions/Potion.java:91-105` `colors`, `items/scrolls/Scroll.java:73-88` `runes`, `items/rings/Ring.java:55-70` `gems` -- private static label maps, read from source by their `put("key", ...)` lines; `Potion.java:151-157` the handler set up per Run (`initColors`), the identification state a generation must not touch.
- `core/.../messages/Messages.java:122-139` -- the key: the class name without the root package, lower-cased, `$` kept, plus `.name`, and the superclass fallback; `core/src/main/assets/messages/items/items.properties`, `plants/plants.properties` (a seed's name, `plants.sungrass$seed.name`).
- `core/.../items/Item.java:70-71` `AC_DROP`, `AC_THROW`; `:110-115` `actions(Hero)`; `:499-505` `name()`; `:550-552` `value()`; `items/EquipableItem.java:41-42`; `items/potions/Potion.java:84-87, :222-226, :441-443`; `items/scrolls/Scroll.java:69, :278-280`; `items/rings/Ring.java:293-296`.
- `core/.../items/weapon/Weapon.java:352-363` -- `STRReq()` and the static formula `(8 + tier * 2) - (int)(Math.sqrt(8 * lvl + 1) - 1)/2`; `items/weapon/melee/MeleeWeapon.java:261-267`; `items/weapon/missiles/MissileWeapon.java:130-136` (one less); `items/armor/Armor.java:690-699` (the same shape with `Math.round(tier * 2)`).
- `core/.../sprites/ItemSpriteSheet.java:822-828` -- `Icons` builds a texture film in its initialiser, which is why the potions, scrolls and rings cannot be constructed without the toolkit (probed: 337 concrete item classes, 273 construct bare, 60 are those families, 4 have no public no-argument constructor).
- `core/.../actors/hero/Hero.java` -- a bare `new Hero()` constructs (probed) and is what `actions(hero)` takes.
- `shatterfish/codex/.../Mobs.java`, `Sources.java`, `GameContext.java`, `CodexLeakTest.java`, `CodexCompletenessTest.java` -- the patterns to extend (a constructor list, source-read members, the door, the gate, the enumeration with named exclusions).

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/api/.../Codex.java`, `CodexJson.java` -- `VERSION = 3`; records `Weighted(className, firstDeck, secondDeck)`, `CategoryEntry(name, firstProb, secondProb, superClass, classes, citation)`, `Label(key, name, citation, nameCitation)`, `LabelPool(family, labels)`, `ExoticSwap(pairs, chanceExpression, chanceWithoutTrinket, citation)`, `Decks(categories, labelPools, exotic)`, `Strength(atLevel0, formula, citation)`, `ItemEntry(className, name, nameCitation, category, value, valueExpression, strength, actions, constructed, reason, citation)`; rendering one entry per line -- api-typed tables, weights as the game's integers.
- [x] `shatterfish/api/src/test/.../CodexJsonTest.java` -- goldens for a category, a label pool, a constructed and a source-read item; refusals -- the text held.
- [x] `shatterfish/codex/.../Names.java` -- the bundle line for a class's `.name` key by the game's rule, cited -- display names without the toolkit.
- [x] `shatterfish/codex/.../Items.java` -- the constructors of every constructible catalogue class, the source-read classes by literal, the exclusions with reasons; `entry(root, ...)` constructing under `GameContext` with a bare hero, or reading `value()`'s return and the `actions.add(AC_...)` lines up the hierarchy; the strength from `STRReq()` at level 0 and the static formula's line -- the items table.
- [x] `shatterfish/codex/.../Decks.java` -- the categories from the enum's public fields with citations of the `X.classes =` and `X.defaultProbs =` lines; the label pools from the `put` lines and the bundle; the exotic pairs from the public maps and the chance from source -- the decks table.
- [x] `shatterfish/codex/.../Generate.java` -- `items.json` and `decks.json` in the tables map -- the task extended.
- [x] `shatterfish/codex/src/test/.../CodexCompletenessTest.java` -- every concrete `Item` subclass in the table once or excluded with a reason, none extra; every deck class is in the table; every source-read class is one that fails to initialise without the toolkit (held from bytecode: exactly those touch the icon film at construction) -- the enumeration.
- [x] `shatterfish/codex/src/test/.../CodexLeakTest.java` -- the live Run's deck state (`Generator.Category.POTION.probs`, `seed`, `dropped`) and identification state (`Potion.getKnown()`) unchanged by a generation; the matrix's items asserted -- NFR-1.
- [x] `codex/v4.0.0/` regenerated; `docs/adr/0017-...md` amendment; `docs/codex/index.md`; `docs/glossary.md` -- NFR-6.

**Acceptance Criteria:**
- Given the generator's categories, when the table is read, then each carries its two category-deck weights and its classes with both deck weights and their total, cited (`CodexLeakTest`'s decks test against `POTION`, `SCROLL`, `WEP_T1`).
- Given the identifiable families, when the label pools are read, then each key carries its display name from the bundle (`CodexLeakTest`).
- Given every concrete item class, when the generator runs, then each is in the table once with a name, a category, a value, a strength where it has one and its actions, or is excluded with a reason (`CodexCompletenessTest`).
- Given a live Run, when a generation runs, then the deck state, the identification state and the generator are unchanged and the bytes equal a cold generation's (`CodexLeakTest`, `CodexSeedFreeTest`).

## Spec Change Log

- 2026-09-16, during implementation: the matrix's examples were wrong in three places and are
  corrected in the tests, not in the frozen text. The sword is tier 3 (`WEP_T3`, strength 14 at
  level 0), not tier 2; a potion of healing's value is not `Potion.value()`'s 30 but
  `return isKnown() ? 30 * quantity : super.value();`, so the table carries -1 with that text
  and the superclass's after it; and the count of classes that construct bare is 247 after the
  nine selector placeholders and the regrowth wand's two never-dropped seeds are excluded (273
  construct, 26 of them are not items a player meets). `Strength` carries `present` and
  `tier` beside the spec's three fields; `Weighted` carries the total; `CategoryEntry` a second
  citation for the class list; `LabelPool` a citation. After the review: `CategoryEntry` carries
  `decks` and the weights' citations, `Label` the exotic name, `ItemEntry` `customName` and
  `quantity`; `GameContext` holds a third field, the level.

## Design Notes

Micro-brainstorm. Construction: (a) a boot in the generator for the icons, refused (ADR-0017:
the generator cannot boot); (b) construct in the test JVM, refused (the task writes the Codex);
(c) construct what constructs bare and read the rest from source, chosen: the three families'
values and actions are one-line returns and `actions.add` lines, read as the rolls are, and
the entry says `constructed: false` with the reason. Enumeration: 337 classes by hand as
constructors and literals, compile-checked, with the completeness test holding the list whole,
as the mobs; the four without a public no-argument constructor and the nine helpers outside
the item packages (a backpack, projectiles, a spell's effect, a mob's prop, a window's
placeholder) are excluded by name with reasons the test checks. Names: the bundle, not
`Messages` (which needs the toolkit's files); the key rule is the game's, read from
`Messages.java`. Deck state: the generator reads only the default arrays; the leak test holds
the Run's `probs` and `seed` untouched. Pre-mortem: a class whose initialiser touches the
toolkit at a later tag would fail construction loudly, not silently; a bundle key the game
derives differently for nested classes (the `$` kept) was checked against the file.

## Verification

**Commands:**
- `./gradlew :codex:generate -Pshatterfish.mobile=off && git status --short codex/` -- expected: nothing changed after the commit.
- `./gradlew :codex:test :api:test -Pshatterfish.mobile=off` -- expected: green, `CodexCompletenessTest` among them.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- expected: green.

## Dev notes

Implemented on `story/2-3-items-generator-weights-decks-and-guarantees` from `c7d5fb641`. Two
tables joined `:codex:generate`: `items.json` (307 entries: 247 constructed, 60 read from
source; 30 classes excluded by name with reasons) and `decks.json` (every `Generator.Category`,
the three label pools, the exotic swap). The generator gained `Names` (the bundle line by the
game's key rule, the superclass fallback, ASCII lowering), `Items` (the three lists, the
constructed and source-read entry builders, the action and value readers, the strength) and
`Decks` (the categories from the public defaults, the label pools from the `put` lines, the
exotic swap). The api gained the deck and item records and their rendering; Codex version 3.
No hook, no upstream file, no change to the fair path or to the Observation schema.

## Acceptance criteria and how each was met

- **Each category carries its two category-deck weights and its classes with both deck weights
  and their total, cited**: `CodexLeakTest`'s items-and-decks test holds `POTION` (8, 8, twelve
  classes, the strength potion at 0/0/0 and healing at 3/3/6, the constant at `:246` and the
  list at `:329`), `WEAPON` (2, no classes) and `GOLD` (10, gold); `CodexJsonTest` the golden.
- **Each label key carries its display name from the bundle**: `CodexLeakTest` holds the three
  pools of twelve, crimson at `Potion.java:93` named "crimson potion", KAUNAN, garnet, and that
  every label's citation is its `put` and its name citation ends with the name.
- **Every concrete item class is in the table once with a name, a category, a value, a strength
  where it has one and its actions, or is excluded with a reason**: `CodexCompletenessTest`
  enumerates the game's classes from bytecode against the three lists (three tests).
- **A live Run's deck state, identification state and generator are unchanged and the bytes
  equal a cold generation's**: `CodexLeakTest`'s live Run, its potion deck bumped and its drop
  count set, and `CodexSeedFreeTest`.

## What was built

- `api`: `Codex.Weighted`, `CategoryEntry`, `Label`, `LabelPool`, `ExoticPair`, `ExoticSwap`,
  `Decks`, `Strength`, `ItemEntry`; `CodexJson.items` and `decks`; `VERSION = 3`; goldens and
  refusals in `CodexJsonTest`; the helper allowlist extended.
- `codex`: `Names`, `Items`, `Decks`; `Generate` extended; the gate's `FILES_CONFINED` admits
  `Names`; the live Run holds the decks, the known potions, the hero and the pack;
  `CodexCompletenessTest` (three tests); `NamesTest`.
- `codex/v4.0.0/items.json`, `decks.json`, the manifest at version 3.
- ADR-0017's amendment; the Codex index; the glossary (deck, label pool); an idea.

## What the story found

- **The split is the classpath's fact.** Every identifiable potion, scroll and ring sets
  `icon = ItemSpriteSheet.Icons.X` in its instance initialiser, and `Icons` builds a texture
  film in its own; the completeness test holds from bytecode that exactly the sixty source-read
  classes touch that class at construction and no constructed class or superclass of one does.
- **Every identifiable potion and scroll defers its value.** `return isKnown() ? N * quantity :
  super.value();` is the form of all twenty-four; the first reader wrote -1 with that line and
  nothing else, and the table now carries the superclass's text after it.
- **A name falls back to the superclass.** `Messages.get(Class, key)` walks up when the bundle
  has no line; the conjured bomb, the double bomb and others have none of their own. The first
  reader failed on the first such class; `Names.of` walks as the game does.
- **`Locale` is banned, and the game lower-cases under `Locale.ENGLISH`.** The gate's denied
  list holds `java.util.Locale` whole; the lowering is the ASCII mapping, equal to the English
  locale's on the ASCII names a class or a label key is made of, and refuses anything else.
- **Two seeds are never dropped.** The regrowth wand's dewcatcher and seed pod declare a `Seed`
  each, with the comment "seed is never dropped"; no bundle names them at any level. They are
  excluded with that reason and line.
- **A ring's ability is conditional.** `RingOfForce.actions` adds `AC_ABILITY` under
  `isEquipped(hero) && hero.heroClass == HeroClass.DUELIST`; the reader counts an add only at the
  method body's own level, so the table offers what a fresh instance offers.
- **The spirit bow has no tier field.** It is a `Weapon`, not a `MeleeWeapon`; its own formula
  is `return STRReq(1, lvl);`, so the entry says tier 0 with that text, and the doc says why.
- **Four categories have no defaults, and the first reader read their Run copy.** GOLD, ARMOR,
  WEAPON and MISSILE assign `probs` alone, and the game draws them another way (an armor by
  the floor's tier table); the reader fell back to `probs` against the spec, and the leak test
  bumped only the potion deck. A category now says how many decks it draws by, a category
  with none lists its classes with zero weights, and the live Run moves every category's copy
  (all four reviews).
- **An item's actions can ask what level the hero stands on.** A pickaxe on the mining level
  is neither dropped nor thrown, and the dried rose asks the ghost's quest; the context sets
  the depth and the challenges but left the level, so a generation on that level would have
  differed from a cold one with every test green. The context holds no level now, the leak
  test's Run stands on a mining level, and the item-side reads are enumerated from bytecode
  to a fixpoint over a class's own methods and named: the pickaxe, the rose, the pasty's
  clock through `Holiday`, the spellbook's draw of its scrolls (the fairness and edge-case
  reviews).
- **The actions reader was brace counting.** A one-line `if (x) actions.add(Y);`, a braceless
  `if` over two lines, an Allman brace, a brace in a literal, `actions.clear()`, an add with a
  space, a reversed ternary: the pinned source writes several of these outside the source-read
  chain. The reader now offers nothing on or under a control line, counts braces outside
  literals, and refuses by line what it does not know; `ItemsReaderTest` holds each shape (the
  adversarial, edge-case and verification-gap reviews).
- **An unidentified exotic shows the exotic family's label.** "exotic crimson potion" is the
  bundle line under the exotic family's key; the pools carry it beside the regular name (the
  edge-case review).
- **A value is a stack's.** A fresh stack of throwing stones is three, and its value is the
  stack's; the entry carries the quantity now (the adversarial and edge-case reviews).
- **Twelve anonymous item classes exist**, the stand-ins a slot, a window, a recipe or a chooser
  draws; the completeness test pins them by name (the fairness review).

## Decisions taken inside the story

- **Constructed where the class loads, read from source where it cannot**, with the reason in
  the entry; a boot in the generator was refused (ADR-0017), and so was constructing on the
  test side (the task writes the Codex).
- **The bundle, not `Messages`**, read as a citation is, with the game's key rule and fallback
  mirrored and cited.
- **The public defaults, never `probs`**: the Run-mutable arrays are not read, and the live Run
  holds them.
- **Unconditional actions only, the unequipped branch of a ternary**: what a fresh instance in
  a pack offers; the per-hero and per-equipped-state actions are an idea.
- **A value that is not one literal is -1 with the text**, the superclass's appended where it
  defers; parsing the `isKnown` ternary into two numbers is an idea.
- **ASCII lowering** rather than a locale, since the gate bans `Locale` by class.
- **The brews and elixirs are constructed.** The spec asked first before constructing a potion;
  these are `Potion` subclasses that set no icon from the film and answer `isKnown()` true
  without a handler, so they touch no identification state, and the completeness test holds
  the split from bytecode.
- **The level is a third field of the context**, held to none rather than left, since a cold
  generation has none; the allowlist names the field and the level's type and nothing of it.

## Evidence

- `:api:test` green, 343 tests, with `CodexJsonTest` (9); `:codex:test` green, 36 tests
  (`CodexSeedFreeTest` 3, `CodexLeakTest` 8, `CodexCompletenessTest` 9, `SourcesTest` 5,
  `RotationTest` 3, `NamesTest` 3, `ItemsReaderTest` 5).
- `./gradlew :codex:generate` twice: `git status --short codex/` empty after the commit.
- Mutation battery, thirteen mutations of the generator's classes, each run against the codex
  tests:
    - M1 an item is dropped from the constructed list: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest.
    - M2 an action added under an if is offered: caught by CodexLeakTest, CodexSeedFreeTest.
    - M3 a ternary takes its equipped branch: caught by CodexLeakTest, CodexSeedFreeTest.
    - M4 a name does not fall back to the superclass: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest, NamesTest.
    - M5 the second deck is dropped: caught by CodexLeakTest, CodexSeedFreeTest.
    - M6 the Run's deck is read for the default: caught by CodexLeakTest, CodexSeedFreeTest.
    - M7 the strength is read at level 1: caught by CodexLeakTest, CodexSeedFreeTest.
    - M8 a value that defers to super.value() loses the superclass's text: caught by CodexLeakTest, CodexSeedFreeTest.
    - M9 a label pool skips its first put: caught by CodexLeakTest, CodexSeedFreeTest.
    - M10 the exotic chance without the trinket is read at level 0: caught by CodexLeakTest, CodexSeedFreeTest.
    - M11 the lowering is lost: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest, NamesTest.
    - M12 a source-read item carries another reason: caught by CodexLeakTest, CodexSeedFreeTest.
    - M13 an item that constructs is excluded as one that cannot: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest.

- The battery rerun after the review patch, nineteen mutations, six of them of the review's own
  fixes (the level left in place, a braceless if governing nothing, a brace in a literal
  counted, a read dropped from the named list, the exotic name not read, an add under a
  one-line if offered), and the Run's-deck mutation now bumping a category with no defaults:
    - M1 an item is dropped from the constructed list: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest.
    - M2 an action added under an if is offered: caught by ItemsReaderTest.
    - M3 a ternary takes its equipped branch: caught by CodexLeakTest, CodexSeedFreeTest, ItemsReaderTest.
    - M4 a name does not fall back to the superclass: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest, NamesTest.
    - M5 the second deck is dropped: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest.
    - M6 the Run's deck is read for the default: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest.
    - M7 the strength is read at level 1: caught by CodexLeakTest, CodexSeedFreeTest.
    - M8 a value that defers to super.value() loses the superclass's text: caught by CodexLeakTest, CodexSeedFreeTest.
    - M9 a label pool skips its first put: caught by CodexLeakTest, CodexSeedFreeTest.
    - M10 the exotic chance without the trinket is read at level 0: caught by CodexLeakTest, CodexSeedFreeTest.
    - M11 the lowering is lost: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest, NamesTest.
    - M12 a source-read item carries another reason: caught by CodexLeakTest, CodexSeedFreeTest.
    - M13 an item that constructs is excluded as one that cannot: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest.
    - M14 the context leaves the Run's level in place: caught by CodexLeakTest.
    - M15 a braceless if governs nothing: caught by ItemsReaderTest.
    - M16 a brace in a literal is counted: caught by ItemsReaderTest.
    - M17 a read is dropped from the named list: caught by CodexCompletenessTest.
    - M18 the exotic name is not read: caught by CodexLeakTest, CodexSeedFreeTest.
    - M19 an add under a one-line if is offered: caught by ItemsReaderTest.

## Deviations

- The matrix's three wrong examples, corrected in the tests (the Spec Change Log).

## Known limitations, handed forward

- **A value that depends on being known is text.** The sixty source-read entries carry -1 and
  the expression with the superclass's; a price table parses it (an idea).
- **Actions are a fresh instance's to a bare hero.** A duelist's ring ability, a wand's or an
  artifact's equipped actions are not offered; the reader counts unconditional adds only.
- **The tier of a weapon without a tier field is 0**, with the formula naming the tier.
- **The category is the deck that lists the class**; the tiered weapon and missile categories
  draw a tier, so a sword's category is `WEP_T3`, not `WEAPON`; an exotic has none.
- **The armor category's draw** is the floor's tier table (`floorSetTierProbs`), which is not in
  the Codex yet; its classes are listed with zero weights and the ADR names the table.
- **A construction that fails** surfaces with the game's stack, not the class's name; the
  supplier is a lambda.

## Follow-ups for later stories

- 2.4: the traps and the level feelings, or whatever the epic orders next.
- 2.5: the combat tables; the strength formulas here feed the encumbrance rule.
- 2.9: the drift check in CI and the generated index page listing these tables.

## Review

Four reviewers on `git diff main...HEAD` from the committed state: the fairness reviewer
(seven findings, none blocking, one proved by a generation on the mining level), the
adversarial lens (nineteen), the edge-case hunter (ten) and the verification-gap lens (nine).
One patch commit, `9b5c3f1ce`.

**Taken.**

- The decks from the public defaults only: a category says how many decks it draws by, a
  category with none lists its classes with zero weights, the game's total deck is held
  against the sum, each deck's weights are cited to their assignment, and the live Run moves
  every category's copy, seed, drop count and flag and holds them (fairness 1, 4, 5;
  adversarial 1, 9, 10, 13; edge 2, 3, 4, 10; gap 1, 4, 5).
- The level held to none by the context, the gate widened by exactly that field and the
  level's type, the item-side reads enumerated from bytecode to a fixpoint and named with
  reasons (the pickaxe, the rose, the pasty's clock, the spellbook's draw), the live Run on a
  mining level, the pickaxe's and the rose's cold actions pinned (fairness 2, 3; edge 1).
- The actions reader: braces outside literals, the opening brace wherever it is, nothing on or
  under a control line with or without braces, a clear, an add with a space, a ternary on
  being equipped on either side, `super.actions` seen only at the body's level, every other
  shape refused by line; `ItemsReaderTest` on synthetic text (adversarial 2, 3, 4, 5, 15;
  edge 8, 9; gap 3).
- The exotic names under each label; the exotic's value deferral followed to the regular; the
  quantity and the value at it; `customName` (adversarial 6, 11; edge 5, 6, 7).
- The exclusions' reasons held one by one, the hand-typed line numbers dropped, the anonymous
  stand-ins pinned (fairness 6, 7; adversarial 14; gap 2).
- The scrolls' and rings' identification, the holiday cached, the seed and the flag in the live
  Run's snapshot (fairness 4; adversarial 13; edge 10).
- The record invariants: a family one pool, an action once, a pair two classes, no weight
  without a deck (adversarial 17).
- The documents: the ADR's amendment rewritten for all of it, the spec's code map corrected
  (the armor formula, the line ranges), the task's wording, the Names javadoc's citation
  (adversarial 19; gap 6, 9).

**Not taken, with reasons.**

- The bundle read as libGDX reads it, every file in order with unescaping (adversarial 7): no
  `.name` line at this tag has an escape, a continuation or a key outside its segment's file,
  and a divergence fails loudly; a limitation handed forward.
- The bare hero pinned to a class (adversarial 12): the game constructs a rogue; the ADR says
  so and the actions the entry carries are the unequipped, talentless ones.
- Constructing a potion on the test side to cross-check the reader (gap 3): the reader is
  held on synthetic text for every shape the pinned source writes; the source-read families'
  actions are pinned in the leak test.
- A construction that fails named by class (gap 8): the supplier is a lambda; the failure
  surfaces with the stack, which names the class.
- Parsing the `isKnown` ternary into two values (adversarial 11; edge 6): an idea, recorded.

## Suggested review order

1. [`Items.java`](https://github.com/watchthelight/shatterfish/blob/9b5c3f1ce/shatterfish/codex/src/main/java/org/shatterfish/codex/Items.java),
   the three lists, the named reads, `readActions`, `valueText`, `strength`.
2. [`ItemsReaderTest.java`](https://github.com/watchthelight/shatterfish/blob/9b5c3f1ce/shatterfish/codex/src/test/java/org/shatterfish/codex/ItemsReaderTest.java),
   what the reader offers and refuses, shape by shape.
3. [`Decks.java`](https://github.com/watchthelight/shatterfish/blob/9b5c3f1ce/shatterfish/codex/src/main/java/org/shatterfish/codex/Decks.java),
   the defaults, the deck count, the pools with the exotic names, the swap.
4. [`GameContext.java`](https://github.com/watchthelight/shatterfish/blob/9b5c3f1ce/shatterfish/codex/src/main/java/org/shatterfish/codex/GameContext.java)
   and the gate in [`CodexLeakTest.java`](https://github.com/watchthelight/shatterfish/blob/9b5c3f1ce/shatterfish/codex/src/test/java/org/shatterfish/codex/CodexLeakTest.java),
   the third field and the live Run.
5. [`CodexCompletenessTest.java`](https://github.com/watchthelight/shatterfish/blob/9b5c3f1ce/shatterfish/codex/src/test/java/org/shatterfish/codex/CodexCompletenessTest.java),
   the enumerations: the classes, the split, the reasons, the reads.
6. [`Names.java`](https://github.com/watchthelight/shatterfish/blob/9b5c3f1ce/shatterfish/codex/src/main/java/org/shatterfish/codex/Names.java)
   and [`Codex.java`](https://github.com/watchthelight/shatterfish/blob/9b5c3f1ce/shatterfish/api/src/main/java/org/shatterfish/api/Codex.java),
   the key rule and the records' invariants.
7. [`0017-codex-generation-and-citations.md`](https://github.com/watchthelight/shatterfish/blob/9b5c3f1ce/docs/adr/0017-codex-generation-and-citations.md),
   the amendment.
