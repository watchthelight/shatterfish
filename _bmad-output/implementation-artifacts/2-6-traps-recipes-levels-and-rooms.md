---
story: 2.6
key: 2-6-traps-recipes-levels-and-rooms
title: "Traps, recipes, levels and rooms"
epic: 2
issue: 40
type: 'feature'
status: 'review'
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
- [x] `shatterfish/api/.../Codex.java`, `CodexJson.java` -- `VERSION = 6`; records `TrapEntry(className, name, nameCitation, canBeHidden, canBeSearched, activateExpression, citation)`, `TrapPool(levelClass, weights, citation)`, `Ingredient(className, quantity)`, `RecipeEntry(className, simple, inputs, output, outQuantity, cost, expressions, citation)`, `LevelEntry(depth, branch, levelClass, shop, boss, sealed, citation)`, `FeelingEntry(name, expression, citation)`, `Structure(levels, feelings, sealedCitation, shopExpression, shopCitation)`; rendering one entry per line.
- [x] `shatterfish/api/src/test/.../CodexJsonTest.java` -- goldens for a trap, a pool, a simple and a non-simple recipe, a level and a feeling; refusals -- the text held.
- [x] `shatterfish/codex/.../Traps.java` -- the 34 constructors, the flags from the instances, the names, the `activate` text and the per-level pools with their weights -- the trap table.
- [x] `shatterfish/codex/.../Recipes.java` -- the three registries read from source, each recipe's class named as a literal, the simple ones' fields read from their initialisers, the others' methods as text -- the recipe table.
- [x] `shatterfish/codex/.../Structure.java` -- every branch's depth map, the shop and boss rules, the sealed floors, the feelings -- the level table.
- [x] `shatterfish/codex/.../Generate.java` -- the three tables in the map -- the task extended.
- [x] `shatterfish/codex/src/test/.../CodexCompletenessTest.java` -- every concrete trap class is in the table; every recipe the registries hold is in the table; every depth of every branch is named -- the enumeration.
- [x] `shatterfish/codex/src/test/.../CodexLeakTest.java` -- the matrix's rows pinned, every citation resolved, the live Run unchanged -- NFR-1.
- [x] `codex/v4.0.0/` regenerated; `docs/adr/0017-...md` amendment; `docs/codex/index.md`; `docs/glossary.md` -- NFR-6.

**Acceptance Criteria:**
- Given the trap classes, when the table is read, then each carries its effect's text, whether it can be hidden and whether it can be searched, cited, and every concrete trap class is present (`CodexCompletenessTest`, `CodexLeakTest`).
- Given the recipes the game registers, when the table is read, then each carries its inputs, output and cost where it states them and its methods' text where it does not (`CodexLeakTest`).
- Given the level structure, when the table is read, then it carries the depth and branch map, the boss and shop depths, the sealing rule and the feelings, and cites the tables that carry the room pools (`CodexLeakTest`).
- Given two generations with the Codex's seed moved between them, when the bytes are compared, then they are identical (`CodexSeedFreeTest`).

## Spec Change Log

The records shipped differ from the shapes the Tasks section sketched, and three statements in the
frozen sections are contradicted by the pinned tree. Both are recorded here rather than by editing
the frozen text.

- `TrapEntry` gained `active`, `nameFrom`, `activateFrom`, `activateCitation` and `alsoOnTheCell`.
  The first four say what was read and whose it is; the last says what else stands on the cell,
  without which two entries would have told a reader that a burning cell is decoration.
- `TrapPool` gained `condition`, `declaredBy`, `nTrapsExpression` and `nTrapsCitation`. A level can
  return two pools under a condition, can inherit them, and can draw from a pool while laying no
  traps; the sketch had room for none of that.
- `FeelingEntry` replaced the plain rule the sketch named, to carry a chance and the places a
  feeling is read.
- `LevelEntry` gained `shopCitation`, `sealedBy` and `sealCitation`, so that no boolean column of
  `levels.json` is a mechanics claim without evidence.
- `Structure` gained the boss rule and its citation, the feeling roll's gate and citation, the
  assignments that are not a literal feeling, what an unnamed depth builds and its citation, and
  the citation of where the game draws a floor's rooms, which is the acceptance criterion's last
  clause.
- The matrix's row "A hidden trap | `ChasmTrap`" names a class the pinned tree does not have; the
  pinned tree has 33 classes under `levels/traps/` and no chasm trap. The row's own escape ("or
  another with the defaults") is satisfied by `AlarmTrap`, which is what the tests hold.
- The matrix's row "A simple recipe | `Scroll.ScrollToStone`" names a recipe that states no fixed
  inputs; the pinned tree makes it the *non-simple* case, which is the row below it. The tests hold
  `BlizzardBrew.Recipe` as the simple case and both `ScrollToStone` and `SeedToPotion` as
  non-simple ones.
- The Code Map says "34 classes under `levels/traps/`; six set a flag false". The tree has 33
  concrete classes and `Trap.java`; five cannot be hidden and one cannot be searched, and
  `GnollRockfallTrap` is among those that cannot be hidden and is not in the Code Map's list.


## Dev Notes

**What was built.** Three readers and their api records. `Traps.java` constructs every concrete
trap class through the door and reads the two flags a player can act on from the instance the
game's own initialiser built; the effect is the text of the trap's own `activate`, cited to the
class that declares it. `Recipes.java` reads the alchemy pot's registries from the method that
tries them and resolves each constructed entry to a source path through the file's own imports.
`Structure.java` reads the `newLevel` switch whole, the rules the game states about shops and boss
floors, the sealing of each level class, and the feelings with their chances and their effects.

**Two traps live where they are used.** `VaultLevel.VaultFlameTrap` and `ToxicGasRoom.ToxicVent`
are declared inside the classes that place them. A player meets them like any other trap, so the
table carries them like any other. Both are deactivated by their own initialiser and override
their effect with an empty body, and the first draft therefore called them decoration. The
fairness and edge-case reviews both showed that is false: the placing class seeds a blob on the
same cell, so the vault's jets burn and the vent gasses whatever stands there. The entry now
carries every place the placing class reaches the cell, cited.

**A pool with two arms is two pools.** `SewerLevel.trapClasses()` returns one list on the first
floor and another after it. Reading the method's text as one list mixed the two and read the
literal `1` of `Dungeon.depth == 1` as a weight. Each array literal is now read separately, closed
by its matching brace, and each arm is its own pool. The condition is read from both methods and
they must agree, since the arms are paired by position.

**A pool belongs to the floor, not to the file.** Three floor families inherit their trap methods,
and reading only a class's own file left them with no pool, which reads as "no traps" and is
false. The reader now walks to the declaring class, names it, and carries how many traps the floor
lays; and whether a floor lays traps at all is read from the painter it actually uses, so the
mining floors, which build their own painter and never ask it for traps, correctly have no pool.

**Sealing took three predicates before the right one.** Reading the level base's own source said
every floor seals, because the base is where the flag is set. Reading "the class declares
`public void seal()`" missed the prison's boss floor, which calls the inherited one
(`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/PrisonBossLevel.java:431`).
The predicate is now the disjunction of the two, over a class's own lines only, and the entry says
which of them applied and cites the line.

**A shop is placed by a room list, not by a depth.** The first draft ANDed the depth rule with
"this is the main branch", a conjunct that appears in neither pinned rule. The adversarial and
fairness reviews both caught it. The reader now walks each level's room list to the base's
placement or to the class that stops short of it, which is why the mining floors and the vault
hold no shop, and cites that line per floor.

**A feeling is a chance, an arm, and everywhere it is read.** Five of the seven feelings have arms
that do nothing but name them; their work is in the painter and the room counts. The table now
carries each feeling's chance in thousandths, its arm, and every place under the levels of the
game that reads it, plus the gate on the roll and the two trinket assignments that can set a
feeling without the roll naming it.

**What is not here.** No trap is activated, no recipe is brewed, no level is built. The door's
field set is unchanged from story 2.5. The room pools stay story 2.4's table; this one cites where
the game draws them, which is the acceptance criterion's last clause.

## Design Notes

The shape follows the tables before it: construct what constructs bare and read what does not,
name what cannot be read, cite everything. A trap constructs bare (it is not an `Actor` and its
initialiser only sets fields), so its flags are an instance's; its effect needs a level and the
toolkit, so the table carries the method's text rather than a measurement, as story 2.5's hit
table does. A recipe's fields are protected, so they are read from the subclass's own initialiser
in source, as the decks' weights are; the registries are private, so they are read from source
too, and each recipe class is named as a literal so the compiler checks it. The level structure
extends the reader story 2.4 already wrote for the main branch's switch.

## Review

Four reviews read the branch: the fairness subagent, and the adversarial, edge-case and
verification-gap lenses. No information-parity leak was found. Between them they raised four
findings that made the tables state something the pinned code contradicts, and a long tail of
silent defaults and unfalsifiable tests. Everything below was fixed on the branch.

**Wrong as published, now corrected.**

- Two traps were published as inactive with an empty effect, and the api invariant, the generator
  comment and the architecture decision record all said that meant stepping on the cell does
  nothing. The placing classes seed blobs on those cells that burn and gas whatever stands there.
  The entry now carries those places, and the claim is gone from all three texts.
- The shop and boss columns were ANDed with "this is the main branch", which appears in neither
  pinned rule. The shop is now read from the room list that places it, cited per floor, and the
  boss column is the pinned rule alone.
- Three floor families that draw traps had no pool at all, because the reader looked only in a
  class's own file.
- The tengu's darts carried the poison dart trap's name and effect under their own citation.
- Every `default:` arm of the level switch was dropped and the trailing `else` was attributed to
  branch one. The table now states what an unnamed depth builds and refuses if two such arms differ.
- The "no feeling" row published a sentence the generator wrote, cited to the enum declaration.
- Five of the seven feelings carried no effect, and two trinket assignments were invisible.

**Silent defaults, now refusals.** A fractional trap weight truncated to zero; an array literal
closed at the first brace rather than the matching one; a ternary condition matched the question
mark of `Class<?>`; a third arm would have been labelled like the second; a level declaring one
trap method and inheriting the other was skipped; a scalar quantity took the first integer in its
statement; a resolved class path was never opened; a registry entry built with arguments was
skipped; the registry list was hard-coded while the game tries a fourth; a commented-out statement
read as live. Each now fails the generation by name.

**Tests that could not fail.** Eleven citation assertions checked for the very token the reader
had searched on, the sewers' eleven weights were checked at one index, branch one's floors were
checked with "at least one", and the pool check only looked at traps the reader had already
resolved. All were replaced with assertions derived independently: whole ordered lists, the set of
levels that lay traps, the branch's own depth list, the feelings' chances, and citations checked
for the shape of what they point at. `ReadersRefuseTest` holds four refusals the pinned tree cannot
reach against source written for the test, and `CodexCompletenessTest` gained the trap construction
audit and the join between the recipes and the items.

**Not done.** Nothing was deferred.

## Evidence

**Build and tests.**

- `./gradlew build -Pshatterfish.mobile=off`: green.
- `:api:test` 350 tests, `:codex:test` 63 tests, no failures.
- `./gradlew :codex:generate` then `git status --short codex/`: no drift.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict`: green.

**Tables.**

| Table | Rows |
|---|---|
| `traps.json` | 35 traps, 9 pools over 7 level classes |
| `recipes.json` | 39 recipes, 25 of them stating fixed inputs |
| `levels.json` | 34 floors, 8 feelings |

**Mutation battery, before review.** Fourteen mutations of the three readers; thirteen were caught.
The survivor was not a weak test but a real defect: the recipe reader fell back to an output
quantity of one whenever a recipe named a constant instead of writing a number, which nine of the
pot's recipes do. The aqua brew makes eight and recycling makes twelve. Fixed in `7e1315146`, and
the mutation was replaced with one that is reachable.

**Mutation battery, after review.** Fourteen mutations rerun against the reviewed readers; results
below.

## Verification

**Commands:**
- `./gradlew :codex:generate -Pshatterfish.mobile=off && git status --short codex/` -- expected: nothing changed after the commit.
- `./gradlew :codex:test :api:test -Pshatterfish.mobile=off` -- expected: green.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- expected: green.
