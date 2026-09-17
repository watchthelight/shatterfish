---
story: 2.6
key: 2-6-traps-recipes-levels-and-rooms
title: "Traps, recipes, levels and rooms"
epic: 2
issue: 40
type: 'feature'
status: 'in-progress'
created: '2026-09-16'
updated: '2026-09-16'
review_loop_iteration: 0
baseline_commit: '5a5212911'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A floor is more than its mobs and its drops. Nothing in the Codex says which traps a
region can hold, which of them a player can find by searching, what the alchemy pot turns one
thing into, or what the shape of a Run is: which level each depth builds, where the shops are,
which floors seal, and what a level feeling changes.

**Approach:** Three tables added to `:codex:generate`. `traps.json`: every concrete trap class with
whether it can be hidden and whether it can be searched, read from an instance the game's own
initialiser built, its display name from the bundle, the text of its own `activate`, and the
regions whose level classes draw it, with the weight each gives it. `recipes.json`: every recipe
the game registers in its own three tables, with its inputs and their quantities, its output and
quantity and its energy cost where the recipe states them, and the text of its own methods where
it does not. `levels.json`: what each depth of each branch builds, the boss and shop depths, which
floors seal, the level feelings with what each one changes, and the room pools per region, citing
the tables that already carry the special and secret rooms.

## Boundaries & Constraints

**Always:** Every value is read from the pinned classes or their source and cited, as every other
table is; a trap is constructed bare under `GameContext` and never activated; a recipe is read
from the game's own registry and its fields from source, since they are protected. Nothing is
drawn and no Run static is set beyond the door's four. A table says what it does not know rather
than guessing: a recipe whose inputs are not a fixed list carries its methods' text and says so,
as story 2.5's hit table does. Codex version 6.

**Ask First:** Activating a trap; brewing a recipe; building a level; a hook; a boot.

**Never:** No trap effects measured (activating one needs a level and the toolkit); no room
layouts or painters; no quest levels' internals; no lore or description text (story 2.7).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| A trap | `PoisonDartTrap` | `canBeHidden` false, `canBeSearched` true, its bundle name, its `activate` text, cited | A trap that cannot be constructed fails naming it |
| A hidden trap | `ChasmTrap` or another with the defaults | `canBeHidden` true, `canBeSearched` true | N/A |
| A trap pool | the sewers | the trap classes that level draws with the weight each is given, cited to the level's own arrays | A level whose pool and weights differ in length fails naming it |
| A simple recipe | `Scroll.ScrollToStone` | its inputs with quantities, its output with quantity, its cost, cited to the fields' assignment | N/A |
| A recipe that is not simple | `Potion.SeedToPotion` | no fixed inputs; the text of `testIngredients`, `cost` and `sampleOutput`, cited, and a flag saying the inputs are not a list | N/A |
| The level of a depth | depth 6, branch 0 | `PrisonLevel`, a shop floor, not sealed | A depth the branch does not name fails naming it |
| A sealed floor | depth 5 | the boss levels that lock, read from their own source | N/A |
| A feeling | `LARGE` | what the game changes when it rolls it, as cited text | N/A |
| Two generations | the seed moved | identical bytes | N/A |

</frozen-after-approval>

## Code Map

- `core/.../levels/traps/Trap.java:35-68` -- `Trap` is `Bundlable`, not an `Actor`: `color`, `shape`, `visible`, `active`, `canBeHidden`, `canBeSearched` are public fields an initialiser sets; `activate()` is the effect. 34 classes under `levels/traps/`; six set a flag false (`DisintegrationTrap`, `GrimTrap`, `PoisonDartTrap`, `RockfallTrap`, `WornDartTrap` cannot be hidden; `TenguDartTrap` cannot be searched).
- `core/.../levels/SewerLevel.java:120-135` -- `trapClasses()` and `trapChances()`, overridden per level class; `:106` `setTraps(nTraps(), trapClasses(), trapChances())`. Every regular level class declares its own pair.
- `core/.../items/Recipe.java:66-74` -- `testIngredients`, `cost`, `brew`, `sampleOutput` are abstract; `:77-172` `SimpleRecipe` holds `inputs`, `inQuantity`, `output`, `outQuantity`, `cost`, all **protected**, so they are read from the subclass's own initialiser in source, as the decks' weights are; `:174-220` the three registries (`oneIngredientRecipes`, `twoIngredientRecipes`, `threeIngredientRecipes`), private static arrays of constructed recipes, which is the list of what the pot can do.
- `core/.../Dungeon.java:433-435` `shopOnLevel()` (depths 6, 11, 16); `:437-443` `bossLevel`; `:310-360` `newLevel`'s branch switch, which story 2.4's `Guarantees.placements` already reads and which this story extends to the other branches. `core/.../levels/Level.java:126-140` the `Feeling` enum; `:181` `locked`, set by the boss levels' own code.
- `shatterfish/codex/.../Guarantees.java` -- the placements reader to extend; `Rooms.java` -- the room pools already carried, to cite rather than repeat; `Items.java`, `Mobs.java` -- the constructor-list pattern; `Names.java` -- the bundle names; `Sources.java` -- `file`, `body`, `text`, `declared`.
- `shatterfish/codex/src/test/.../CodexCompletenessTest.java` -- the enumeration pattern: every concrete class of a package is in a table or named excluded.

## Tasks & Acceptance

**Execution:**
- [ ] `shatterfish/api/.../Codex.java`, `CodexJson.java` -- `VERSION = 6`; records `TrapEntry(className, name, nameCitation, canBeHidden, canBeSearched, activateExpression, citation)`, `TrapPool(levelClass, weights, citation)`, `Ingredient(className, quantity)`, `RecipeEntry(className, simple, inputs, output, outQuantity, cost, expressions, citation)`, `LevelEntry(depth, branch, levelClass, shop, boss, sealed, citation)`, `FeelingEntry(name, expression, citation)`, `Structure(levels, feelings, sealedCitation, shopExpression, shopCitation)`; rendering one entry per line.
- [ ] `shatterfish/api/src/test/.../CodexJsonTest.java` -- goldens for a trap, a pool, a simple and a non-simple recipe, a level and a feeling; refusals -- the text held.
- [ ] `shatterfish/codex/.../Traps.java` -- the 34 constructors, the flags from the instances, the names, the `activate` text and the per-level pools with their weights -- the trap table.
- [ ] `shatterfish/codex/.../Recipes.java` -- the three registries read from source, each recipe's class named as a literal, the simple ones' fields read from their initialisers, the others' methods as text -- the recipe table.
- [ ] `shatterfish/codex/.../Structure.java` -- every branch's depth map, the shop and boss rules, the sealed floors, the feelings -- the level table.
- [ ] `shatterfish/codex/.../Generate.java` -- the three tables in the map -- the task extended.
- [ ] `shatterfish/codex/src/test/.../CodexCompletenessTest.java` -- every concrete trap class is in the table; every recipe the registries hold is in the table; every depth of every branch is named -- the enumeration.
- [ ] `shatterfish/codex/src/test/.../CodexLeakTest.java` -- the matrix's rows pinned, every citation resolved, the live Run unchanged -- NFR-1.
- [ ] `codex/v4.0.0/` regenerated; `docs/adr/0017-...md` amendment; `docs/codex/index.md`; `docs/glossary.md` -- NFR-6.

**Acceptance Criteria:**
- Given the trap classes, when the table is read, then each carries its effect's text, whether it can be hidden and whether it can be searched, cited, and every concrete trap class is present (`CodexCompletenessTest`, `CodexLeakTest`).
- Given the recipes the game registers, when the table is read, then each carries its inputs, output and cost where it states them and its methods' text where it does not (`CodexLeakTest`).
- Given the level structure, when the table is read, then it carries the depth and branch map, the boss and shop depths, the sealing rule and the feelings, and cites the tables that carry the room pools (`CodexLeakTest`).
- Given two generations with the Codex's seed moved between them, when the bytes are compared, then they are identical (`CodexSeedFreeTest`).

## Spec Change Log

## Design Notes

The shape follows the tables before it: construct what constructs bare and read what does not,
name what cannot be read, cite everything. A trap constructs bare (it is not an `Actor` and its
initialiser only sets fields), so its flags are an instance's; its effect needs a level and the
toolkit, so the table carries the method's text rather than a measurement, as story 2.5's hit
table does. A recipe's fields are protected, so they are read from the subclass's own initialiser
in source, as the decks' weights are; the registries are private, so they are read from source
too, and each recipe class is named as a literal so the compiler checks it. The level structure
extends the reader story 2.4 already wrote for the main branch's switch.

## Verification

**Commands:**
- `./gradlew :codex:generate -Pshatterfish.mobile=off && git status --short codex/` -- expected: nothing changed after the commit.
- `./gradlew :codex:test :api:test -Pshatterfish.mobile=off` -- expected: green.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- expected: green.
