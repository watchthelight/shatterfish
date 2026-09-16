---
story: 2.3
key: 2-3-items-generator-weights-decks-and-guarantees
title: "Items, generator weights, decks and guarantees"
epic: 2
issue: 37
type: 'feature'
status: 'in-progress'
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

- `core/.../items/Generator.java:221-244` -- the categories with `(firstProb, secondProb, superClass)`; `:256-290` the fields (`classes`, `probs` Run-mutable, `defaultProbs`, `defaultProbs2`, `defaultProbsTotal`, `seed`, `dropped`, `using2ndProbs`); `:324-611` the static block assigning `X.classes` and `X.defaultProbs`; `:621-690` the category decks (`usingFirstDeck`, `categoryProbs`, `random()`); `:698-745` the draw and the exotic swap at `:730-737`.
- `core/.../items/trinkets/ExoticCrystals.java:48-58` -- `consumableExoticChance(level)`: 0 at level -1, else `0.2f + 0.2f*level`; `items/potions/exotic/ExoticPotion.java:50-57` and `items/scrolls/exotic/ExoticScroll.java` -- `regToExo`, public static.
- `core/.../items/potions/Potion.java:91-105` `colors`, `items/scrolls/Scroll.java:73-88` `runes`, `items/rings/Ring.java:55-70` `gems` -- private static label maps, read from source by their `put("key", ...)` lines; `Potion.java:151-157` the handler set up per Run (`initColors`), the identification state a generation must not touch.
- `core/.../messages/Messages.java:125-133` -- the key: the class name without the root package, lower-cased, `$` kept, plus `.name`; `core/src/main/assets/messages/items/items.properties`, `plants/plants.properties` (a seed's name, `plants.sungrass$seed.name`).
- `core/.../items/Item.java:70-71` `AC_DROP`, `AC_THROW`; `:110-115` `actions(Hero)`; `:499-505` `name()`; `:550-552` `value()`; `items/EquipableItem.java:41-42`; `items/potions/Potion.java:84-87, :222-226, :441-443`; `items/scrolls/Scroll.java:69, :278-280`; `items/rings/Ring.java:293-296`.
- `core/.../items/weapon/Weapon.java:352-363` -- `STRReq()` and the static formula `(8 + tier * 2) - (int)(Math.sqrt(8 * lvl + 1) - 1)/2`; `items/weapon/melee/MeleeWeapon.java:261-267`; `items/weapon/missiles/MissileWeapon.java:130-136` (one less); `items/armor/Armor.java:686-700` (`5 * tier + tier*lvl`).
- `core/.../sprites/ItemSpriteSheet.java:822-828` -- `Icons` builds a texture film in its initialiser, which is why the potions, scrolls and rings cannot be constructed without the toolkit (probed: 337 concrete item classes, 273 construct bare, 60 are those families, 4 have no public no-argument constructor).
- `core/.../actors/hero/Hero.java` -- a bare `new Hero()` constructs (probed) and is what `actions(hero)` takes.
- `shatterfish/codex/.../Mobs.java`, `Sources.java`, `GameContext.java`, `CodexLeakTest.java`, `CodexCompletenessTest.java` -- the patterns to extend (a constructor list, source-read members, the door, the gate, the enumeration with named exclusions).

## Tasks & Acceptance

**Execution:**
- [ ] `shatterfish/api/.../Codex.java`, `CodexJson.java` -- `VERSION = 3`; records `Weighted(className, firstDeck, secondDeck)`, `CategoryEntry(name, firstProb, secondProb, superClass, classes, citation)`, `Label(key, name, citation, nameCitation)`, `LabelPool(family, labels)`, `ExoticSwap(pairs, chanceExpression, chanceWithoutTrinket, citation)`, `Decks(categories, labelPools, exotic)`, `Strength(atLevel0, formula, citation)`, `ItemEntry(className, name, nameCitation, category, value, valueExpression, strength, actions, constructed, reason, citation)`; rendering one entry per line -- api-typed tables, weights as the game's integers.
- [ ] `shatterfish/api/src/test/.../CodexJsonTest.java` -- goldens for a category, a label pool, a constructed and a source-read item; refusals -- the text held.
- [ ] `shatterfish/codex/.../Names.java` -- the bundle line for a class's `.name` key by the game's rule, cited -- display names without the toolkit.
- [ ] `shatterfish/codex/.../Items.java` -- the constructors of every constructible catalogue class, the source-read classes by literal, the exclusions with reasons; `entry(root, ...)` constructing under `GameContext` with a bare hero, or reading `value()`'s return and the `actions.add(AC_...)` lines up the hierarchy; the strength from `STRReq()` at level 0 and the static formula's line -- the items table.
- [ ] `shatterfish/codex/.../Decks.java` -- the categories from the enum's public fields with citations of the `X.classes =` and `X.defaultProbs =` lines; the label pools from the `put` lines and the bundle; the exotic pairs from the public maps and the chance from source -- the decks table.
- [ ] `shatterfish/codex/.../Generate.java` -- `items.json` and `decks.json` in the tables map -- the task extended.
- [ ] `shatterfish/codex/src/test/.../CodexCompletenessTest.java` -- every concrete `Item` subclass in the table once or excluded with a reason, none extra; every deck class is in the table; every source-read class is one that fails to initialise without the toolkit (asserted by trying, on the test side) -- the enumeration.
- [ ] `shatterfish/codex/src/test/.../CodexLeakTest.java` -- the live Run's deck state (`Generator.Category.POTION.probs`, `seed`, `dropped`) and identification state (`Potion.getKnown()`) unchanged by a generation; the matrix's items asserted -- NFR-1.
- [ ] `codex/v4.0.0/` regenerated; `docs/adr/0017-...md` amendment; `docs/codex/index.md`; `docs/glossary.md` -- NFR-6.

**Acceptance Criteria:**
- Given the generator's categories, when the table is read, then each carries its two category-deck weights and its classes with both deck weights and their total, cited (`CodexLeakTest`'s decks test against `POTION`, `SCROLL`, `WEP_T1`).
- Given the identifiable families, when the label pools are read, then each key carries its display name from the bundle (`CodexLeakTest`).
- Given every concrete item class, when the generator runs, then each is in the table once with a name, a category, a value, a strength where it has one and its actions, or is excluded with a reason (`CodexCompletenessTest`).
- Given a live Run, when a generation runs, then the deck state, the identification state and the generator are unchanged and the bytes equal a cold generation's (`CodexLeakTest`, `CodexSeedFreeTest`).

## Spec Change Log

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
