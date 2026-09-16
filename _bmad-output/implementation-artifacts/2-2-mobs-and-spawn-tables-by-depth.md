---
story: 2.2
key: 2-2-mobs-and-spawn-tables-by-depth
title: "Mobs and spawn tables by depth"
epic: 2
issue: 36
type: 'feature'
status: 'review'
created: '2026-09-16'
updated: '2026-09-16'
review_loop_iteration: 0
baseline_commit: 'aae848473'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Brain cannot judge a fight: no table says what a mob is, and no table says
which mobs a floor spawns. The Codex skeleton (2.1) has a manifest and two tables; this is the
first table the Brain will read.

**Approach:** Two tables added to `:codex:generate`. `mobs.json`: every concrete mob class,
constructed by the game's own initialisers under a stated depth and challenge mask, with hit
points, defense skill, experience, maximum level, alignment, properties, loot and loot chance
read from the instance, and the damage roll, attack skill and damage reduction read from the
class's own source as cited expressions with the distribution parsed where it is a plain
`Random.NormalIntRange`/`IntRange` call. Variants: every depth 1 to 26 and every challenge flag
under which any field differs from the base, so the depth-scaled mobs and the Stronger Bosses
variants fall out of the game's constructors rather than a transcription. `spawn-rotation.json`:
the standard rotation per depth read from the spawner's own list literal, the random families
(shaman, elemental) with their odds, the rare-mob additions, the rare alternates from the
spawner's map, and the champion roll's rule, every value cited. `CodexCompletenessTest`
enumerates every concrete mob class of the game on the test side and holds the table names each.

## Boundaries & Constraints

**Always:** The generator constructs mobs; it never calls a method that draws (`damageRoll`,
`getMobRotation`, `Shaman.random`). A value that depends on a draw at construction (the vault
boss elemental's element, a `Random.oneOf` loot) is named as random and not dumped. One class,
`GameContext`, is the only generator class that touches `Dungeon.depth` and
`Dungeon.challenges`, sets them around a construction and restores what was there; the gate of
2.1 exempts it for those two fields and nothing else, and `CodexLeakTest`'s live Run holds that
a generation leaves the Run's values as they were. `Class` is admitted to the gate for the
game's class-keyed tables, while `forName`, class loaders, resources and reflection stay
banned; ADR-0017 is amended. Every entry and every rule carries a citation read at generation.
Mob classes are listed as constructors in the generator, compile-checked; the completeness
test, not the generator, enumerates the game. The Codex version is bumped to 2.

**Ask First:** A hook (the spawner's rotation is private; it is read from source, not hooked);
any measurement by running combat (2.5); any reading of `Dungeon` beyond the two fields.

**Never:** No display names or descriptions (2.7); no combat outcomes (2.5); no item tables
(2.3); no level, room or trap tables (2.6); no Brain reading (E4).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| A plain mob | `Rat` at depth 1, no challenges | HT 8, defense 2, EXP 1, max level 5, ENEMY, no properties, damage `NormalIntRange(1, 4)`, attack 8, DR `NormalIntRange(0, 1)`, each cited | N/A |
| A depth-scaled mob | `Piranha`, `Statue`, `ArmoredStatue`, `PhantomPiranha` | Base at depth 1 and a variant per depth 2 to 26 with the fields that differ | N/A |
| A boss under Stronger Bosses | `Goo`, `Tengu`, `DM300`, `DwarfKing`, `Pylon` | A variant under `STRONGER_BOSSES` with the fields that differ; every other flag absent | N/A |
| A random facet | `VaultBossElemental`'s properties; `Random.oneOf` loot | The facet is `random: true` with no value, cited to the draw | N/A |
| A roll that is not a plain call | A formula, a superclass call | `kind: OTHER`, the expression's line as text, cited; 2.5 measures it | N/A |
| The rotation | Depth 1 to 26 | The counted class list per depth from the spawner's literal, families expanded with odds, rare additions and alternates with their chance, the champion rule | A depth with no list is an error naming it |
| Completeness | The game's concrete mob classes, enumerated on the test side | Every one named in the table, none extra | The test names the missing or extra class |
| The Run's context | A live Run at depth 3 under a challenge | After a generation, `Dungeon.depth` and `Dungeon.challenges` are what they were | N/A |

</frozen-after-approval>

## Code Map

- `core/.../actors/mobs/Mob.java:130-133` -- `defenseSkill`, `EXP`, `maxLvl`; `:1098-1099` `loot`, `lootChance`; `:1038` `lootChance()` override point; `:1102` `createLoot()`; `Char.java:172-173` `HT`/`HP`, `:189` `alignment`, `:1404` `properties()`, `:689` `attackSkill`, `:693` `defenseSkill(Char)`, `:701` `drRoll`, `:709` `damageRoll`, `Char.Property` `:1413-1439`.
- `core/.../actors/mobs/Rat.java` -- the shape of a mob class: an initialiser block, `damageRoll()`, `attackSkill(Char)`, `drRoll()` overrides; the anchors for the source-read expressions.
- `core/.../actors/mobs/MobSpawner.java:62-68` -- `getMobRotation` (draws); `:71-212` `standardMobRotation`, private, the per-depth literals to read; `:215-241` `addRareMobs` (depth, class, `0.025f`); `:244-255` `swapMobAlts` (`1/50f`, a trinket multiplier); `:257-275` `RARE_ALTS`, a public `HashMap` keyed by class (sort by name when dumping).
- `core/.../actors/mobs/Shaman.java:184-193`, `Elemental.java:604-618` -- the random families and their odds (`0.4f`, `0.8f`, the chaos alternate at `1/50f`).
- `core/.../actors/buffs/ChampionEnemy.java:94-124` -- the champion roll: six buffs at equal odds, the `CHAMPION_ENEMIES` gate, the four exclusions by depth, the `8 - min(20, depth-1)/10` counter.
- `core/.../actors/mobs/Goo.java:54` -- `HP = HT = Dungeon.isChallenged(STRONGER_BOSSES) ? 120 : 100`: the shape the context makes readable without a transcription; `Piranha.java`, `Statue.java` for depth scaling.
- `core/.../actors/mobs/**` including `npcs/` and `quest/vault/` -- 115 concrete classes probed in the mobs package and 14 more found by the completeness test elsewhere, 129 in all: all construct bare at any depth; nested minions (`DwarfKing.DK*`, `YogDzewa.*`, `YogFist.*`, `Elemental.*`, `Shaman.*`, `Necromancer.NecroSkeleton`).
- `shatterfish/codex/.../Generate.java`, `Citations.java` -- the tables map, the anchor reader; `shatterfish/api/.../Codex.java`, `CodexJson.java` -- records and rendering to extend; `CodexJsonTest` goldens.
- `shatterfish/codex/src/test/.../CodexLeakTest.java:44-66` -- the gate to amend (`Class` admitted, `GameContext` exempt for two `Dungeon` fields); its live Run for the context check; `CodexSeedFreeTest` unchanged in shape.
- `docs/adr/0017-codex-generation-and-citations.md` -- the amendment; `docs/codex/index.md` -- the tables list.

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/api/.../Codex.java`, `CodexJson.java` -- `VERSION = 2`; records `Roll(kind, min, max, expression, citation)`, `Loot(kind, name, chance, random, customLoot, citation)`, `MobEntry(className, alignment, properties, ht, defenseSkill, exp, maxLvl, damage, attack, dr, loot, variants, citation)`, `Variant(depth, challenge, fields)`, `RotationDepth(depth, entries)`, `RotationEntry(className, count, family, odds)`, `RareMob`, `RareAlt`, `ChampionRule`, `SpawnRotation`; rendering one entry per line -- api-typed tables.
- [x] `shatterfish/api/src/test/.../CodexJsonTest.java` -- goldens for one mob entry with a variant and one rotation depth; refusals (a variant with no field, a count under 1) -- the text held.
- [x] `shatterfish/codex/.../GameContext.java` -- `under(depth, challenges, supplier)`: save, set, run, restore `Dungeon.depth`/`Dungeon.challenges` -- the one door to the two fields.
- [x] `shatterfish/codex/.../Mobs.java` -- the 129 constructors in one list; `RANDOM_AT_CONSTRUCTION` facets named; `entry(root, supplier)` building a `MobEntry` from the instance at depth 1 and its variants over depths 2 to 26 and each challenge flag; rolls read via `Citations` anchors on the class's own file, walking up to the superclass that declares the method -- the mobs table.
- [x] `shatterfish/codex/.../Rotation.java` -- reads `standardMobRotation`'s literal per depth, the families, `addRareMobs`, `swapMobAlts`, `RARE_ALTS`, the champion rule, each with its citation -- the spawn table.
- [x] `shatterfish/codex/.../Generate.java` -- `mobs.json` and `spawn-rotation.json` in the tables map -- the task extended.
- [x] `shatterfish/codex/src/test/.../CodexCompletenessTest.java` -- ArchUnit over `com.shatteredpixel.shatteredpixeldungeon.actors.mobs..`: every concrete `Mob` subclass named once in `mobs.json`, none extra; every rotation class and alternate is a mob in the table -- the story's enumeration.
- [x] `shatterfish/codex/src/test/.../CodexLeakTest.java` -- the gate amended (`Class` admitted, `forName`/loaders/resources/reflection banned, `GameContext` exempt for `Dungeon`); the live Run at depth 3 under a challenge keeps its values through a generation; the depth and boss variants asserted for the matrix's classes -- NFR-1 held.
- [x] `codex/v4.0.0/` regenerated; `docs/adr/0017-...md` amendment; `docs/codex/index.md`; `docs/glossary.md` (variant) -- NFR-6.

**Acceptance Criteria:**
- Given the game's mob classes, when the generator runs, then every concrete one appears once with its cited fields, and the completeness test names any missing or extra (`CodexCompletenessTest`).
- Given depth 1 to 26 and each challenge flag, when a mob's fields differ from its base, then a variant carries the differing fields (`CodexLeakTest`, the matrix's classes).
- Given the spawner's source, when the rotation is read, then depths 1 to 26 each carry a counted list, the rare and alternate rules and the champion rule, each cited (`CodexCompletenessTest`, `CodexJsonTest`).
- Given a live Run, when a generation runs, then the Run's depth and challenges are unchanged and the bytes equal a cold generation's (`CodexLeakTest`, `CodexSeedFreeTest`).

## Spec Change Log

## Design Notes

Micro-brainstorm. Stats: (a) transcribe from source by regex; (b) construct the mob and read
the instance, chosen: the game's initialisers are the truth, and the depth and boss variants
come from the same constructors under a context; (c) measure by play, which is 2.5's. Rolls:
the instance's `damageRoll()` draws, so the expression is read from the class's source (the
declaring class up the hierarchy) and parsed only where it is one plain call; anything else is
`OTHER` with the line, for 2.5. The rotation: `standardMobRotation` is private and
`getMobRotation` draws, so the literal is read from source per `case`; a hook was refused. The
context class is the one exception to the 2.1 gate and is named in it. Pre-mortem: a mob whose
constructor draws would make two generations differ, which the seed-free test catches; the
named random facets are the ones the probe found (the vault boss elemental's element,
`Random.oneOf` loot). `RARE_ALTS` iterates in identity-hash order and is sorted by name. The
115-entry constructor list is long and compile-checked; the completeness test is what keeps it
whole across a tag upgrade.

## Verification

**Commands:**
- `./gradlew :codex:generate -Pshatterfish.mobile=off && git status --short codex/` -- expected: nothing changed after the commit.
- `./gradlew :codex:test :api:test -Pshatterfish.mobile=off` -- expected: green, `CodexCompletenessTest` among them.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- expected: green.

## Dev notes

Implemented on `story/2-2-mobs-and-spawn-tables-by-depth` from `aae848473`. Two tables joined
`:codex:generate`: `mobs.json` (129 concrete mob classes) and `spawn-rotation.json`. The
generator gained `GameContext` (the one door to `Dungeon.depth` and `Dungeon.challenges`),
`Sources` (a class's body, a nested class's block, a method's declaring class up the hierarchy,
the returning lines), `Mobs` (the constructor list, the entry builder, the roll and loot readers)
and `Rotation` (the spawner's literals, the families, the rare rules, the alternates, the
champion rule). The api gained the mob and rotation records and their rendering; Codex version
2. No hook, no upstream file, no change to the fair path or to the Observation schema.

## Acceptance criteria and how each was met

- **Every concrete mob class appears once with its cited fields, none extra**:
  `CodexCompletenessTest` enumerates the game's classes with ArchUnit and compares (two tests).
- **A variant carries the differing fields per depth and per challenge**: `CodexLeakTest`'s
  variants test holds the four depth-scaled mobs (25 depth variants each), the five Stronger
  Bosses variants (that flag and no other), Goo at 120, and the rat unchanged.
- **The rotation carries depths 1 to 26 counted, the rare and alternate rules and the champion
  rule, each cited**: `CodexCompletenessTest` (every named class is a mob), `CodexLeakTest`
  (every citation opens to its case, put or declaration), `CodexJsonTest` (the golden text).
- **A live Run's depth and challenges are unchanged by a generation and the bytes equal a cold
  generation's**: `CodexLeakTest`'s live Run at depth 3 under three challenges, `CodexSeedFreeTest`.

## What was built

- `api`: `Codex.Roll`, `RollKind`, `Loot`, `LootKind`, `Field`, `Variant`, `MobEntry`, `Odds`,
  `RotationEntry`, `RotationDepth`, `RareMob`, `RareAlt`, `Exclusion`, `ChampionRule`,
  `SpawnRotation`; `CodexJson.mobs` and `spawnRotation`; `VERSION = 2`; goldens and refusals in
  `CodexJsonTest`; the helper allowlist extended.
- `codex`: `GameContext`, `Sources`, `Mobs`, `Rotation`; `Generate` extended; the gate in
  `CodexLeakTest` amended and its live Run made to hold the context; `CodexCompletenessTest`.
- `codex/v4.0.0/mobs.json`, `spawn-rotation.json`, the manifest at version 2.
- ADR-0017's amendment; the Codex index; the glossary (variant).

## What the story found

- **Fourteen mobs live outside the mobs package.** The completeness test found the heroes'
  summons, the wands' and artifacts' allies, a room's sentry and a trap's guardian, all with
  public no-argument constructors; they are in the table.
- **A hero's talent reaches a constructor.** The rogue's decoy multiplies its hit points by a
  talent when a hero exists; the live Run found the table differing by one byte. The field is
  named run-dependent, dumped as zero and not compared.
- **A base class's method hides the field.** `Mob.lootChance()` declares a local of the field's
  name; the first regex read it and every mob without its own loot chance got -1. The field's
  form (modifier and type, or a bare assignment) is what is read now.
- **A nested class's method is not its enclosing class's.** The elemental family's file declares
  `damageRoll()` in the abstract class and in a nested one; the source reader now searches a
  class's own lines, skipping nested type blocks.
- **The game hands over hash-ordered collections.** `properties()` is a `HashSet` and
  `RARE_ALTS` a `HashMap`; the gate's rule became "make none" rather than "touch none", and what
  is read is sorted before it is written.
- **All 129 construct bare.** No mob's constructor needs a level, a hero or the toolkit.
- **Two mobs chain their damage reduction onto a parent's that is not zero.** The first reader
  dropped `super.drRoll()` and wrote the fetid rat's and the gnoll exile's reduction as the
  child's alone; the reader now accepts the prefix only over `Char`'s zero and says OTHER
  otherwise (the verification-gap and edge-case reviews).
- **A return can span lines, and carry a comment.** The brutes' damage roll is a ternary over
  three lines and was recorded as its first line; a light ally's plain roll was lost to a
  trailing comment. Returns are whole statements now (the adversarial and edge-case reviews).
- **An alternate can be unreachable.** The elemental family's alternate is keyed by the abstract
  class, which no rotation lists as itself, so the swap never looks it up; the chaos elemental's
  odds are the family's and the alternate is marked unreachable (the fairness and adversarial
  reviews).
- **Constructors draw from the live generator.** Seventeen constructors read the hero, the
  statistics, the seed or a generator; a generation inside a Run advanced the Run's generator by
  hundreds of draws. The context now runs each construction under the Codex's own generator,
  the live Run's next draw is held unchanged, and the seventeen are enumerated from bytecode
  and named with their reasons (the fairness review, all four on the readers).
- **The door was a denylist.** The context could have read any other `Dungeon` static or
  method; it is held by allowlist now, two fields and two calls (the fairness and
  verification-gap reviews).

## Decisions taken inside the story

- **Stats from constructed instances, rolls from source.** The instance is the truth for what
  the initialiser sets; a roll draws, so its expression is read and parsed only where plain.
- **One door to two fields**, `GameContext`, exempted by name in the gate and held to reach
  nothing else; the alternative, a transcription of `Dungeon.isChallenged` branches, was refused.
- **The rotation from the literal**, since the method is private and the public one draws; a
  hook was refused.
- **`Class` admitted.** The 2.1 gate denied it; the game's tables are keyed by class. Names and
  member reflection stay banned.
- **Families as thousandths with the remainder absorbing the rounding**, so the odds sum to
  1000 and no float is written.

## Evidence

- `:api:test` green, 340 tests, with `CodexJsonTest` (6); `:codex:test` green, 10 tests
  (`CodexSeedFreeTest` 3, `CodexLeakTest` 5, `CodexCompletenessTest` 2).
- `./gradlew :codex:generate` twice: `git status --short codex/` empty after the commit.
- Mutation battery, nine mutations of the generator's classes, each run against the codex tests:
    - M1 no variant is ever taken: the depths and the challenges are not compared: caught by CodexLeakTest, CodexSeedFreeTest.
    - M2 the context does not restore the depth it set: caught by CodexLeakTest.
    - M3 a uniform roll is read as a normal one: caught by CodexLeakTest, CodexSeedFreeTest.
    - M4 the hero-dependent field is dumped: caught by CodexLeakTest, CodexSeedFreeTest.
    - M5 the drawn properties are dumped: caught by CodexLeakTest, CodexSeedFreeTest.
    - M6 a mob is dropped from the list: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest.
    - M7 a nested class's method is taken for its enclosing class's: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest.
    - M8 the context reads the hero: caught by CodexLeakTest.
    - M9 the rotation counts every class once: caught by CodexLeakTest, CodexSeedFreeTest.

- The battery rerun after the review patch, fourteen mutations, five of them of the review's own
  fixes (a chained parent's roll dropped, a comment kept, an unreachable alternate marked
  reachable, the Run's generator drawn from, a count not checked):
    - M1 no variant is ever taken: the depths and the challenges are not compared: caught by CodexLeakTest, CodexSeedFreeTest.
    - M2 the context does not restore the depth it set: caught by CodexLeakTest.
    - M3 a uniform roll is read as a normal one: caught by CodexLeakTest, CodexSeedFreeTest, SourcesTest.
    - M4 the hero-dependent field is dumped: caught by CodexLeakTest, CodexSeedFreeTest.
    - M5 the drawn properties are dumped: caught by CodexLeakTest, CodexSeedFreeTest.
    - M6 a mob is dropped from the list: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest.
    - M7 a nested class's method is taken for its enclosing class's: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest, SourcesTest.
    - M8 the context reads the hero: caught by CodexLeakTest.
    - M9 the rotation counts every class once: caught by CodexLeakTest, CodexSeedFreeTest, RotationTest.
    - M10 a chained parent's damage reduction is dropped: caught by CodexLeakTest, CodexSeedFreeTest.
    - M11 a trailing comment stays in a return: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest, SourcesTest.
    - M12 every alternate is marked reachable: caught by CodexLeakTest, CodexSeedFreeTest.
    - M13 the context draws from the Run's generator: caught by CodexLeakTest.
    - M14 the champion rule's exclusions are not counted: **survived**, as expected: a count check
      bites only when the source stops having what the reader reads, and the pinned source has it.

## Deviations

- None from the spec's tasks.

## Known limitations, handed forward

- **A roll that is a formula is text.** 34 damage rolls, 33 attack skills and 54 damage
  reductions are `OTHER`; story 2.5 measures them.
- **Loot chances that are expressions** (a generation-scaled chance) are text with -1.
- **The random and run-dependent sets are written by hand**, by class, found by the tests; a
  new such class fails the seed-free or the live-Run comparison, which is where it is found.
- **Depth 27 and beyond** are not in the rotation; the spawner lists up to 26.

## Follow-ups for later stories

- 2.3: items and decks, the first Run-mutable statics; its leak test arranges a Run that has drawn.
- 2.5: the combat tables that measure what `OTHER` names.
- 2.9: the drift check in CI and the generated index page listing these tables.

## Review

Four reviewers on `git diff main...HEAD` from the committed state: the fairness reviewer (six
findings, one proved by a diff of a live generation), the adversarial lens (twenty-five), the
edge-case hunter (thirty-four) and the verification-gap lens (nine). One patch commit,
`4e74e72a9`.

**Taken.**

- The door by allowlist: exactly `Dungeon.depth` and `Dungeon.challenges`, exactly
  `pushGenerator` and `popGenerator`, no other game class; constructions under the Codex's own
  generator; the live Run's generator held untouched; the seed-free test moving that generator's
  seed (fairness 1, 2; gap 1; edge 15; adversarial 8).
- The construction-time readers enumerated from bytecode and named with reasons, seventeen; the
  random and hero-dependent sets held against them (fairness 3; adversarial 7; edge 16).
- The readers: braces outside comments and literals; own lines skipping nested and anonymous
  types by the class's own name; member lines skipping method bodies; constructor lines for
  draws; returns as whole statements, joined, comments stripped, an inline return counted; a
  chained parent's roll only over `Char`; `Random.Int` exclusive; `Random.IntRange` inclusive;
  anchors widened; a non-core class refused; a declaration that is missing named (adversarial
  1, 2, 3, 4, 5, 14, 15, 16; edge 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13; gap 3, 4).
- The facets: `statsSetLater` for the seventeen classes whose stats the game sets later;
  `customDefense`; the loot's declaration text; the draws cited, the properties' draw included;
  run-dependent fields restricted to numbers and held at zero; a construction that fails named
  by class, depth and challenge; the alignment through the api helper; a depth's and a
  challenge's variant held to compose (adversarial 6, 17, 18, 19, 22; edge 14, 17, 18, 19; gap 5).
- The rotation: the default arm; families as records with odds summing to a thousand, each
  member's expression and the family's citation; canonical names everywhere; alternates marked
  reachable or not; every count checked (the literals against the case groups, the rare adds,
  the champion's draw range and `instanceof` lines, thresholds rising, a chance stated or
  refused, a shape the reader does not know refused); a stray return in the rotation method
  refused; thousandths rounded half up and a nonzero chance that rounds to zero refused
  (fairness 4, 5; adversarial 9, 10, 11, 12, 13, 21, 24, 25; edge 20, 21, 22, 23, 24, 25, 26, 27,
  28, 29, 30, 31, 32, 34; gap 2, 6).
- Tests: `SourcesTest` and `RotationTest` on fixtures; the rolls of the matrix's classes, the
  chained pair, the brutes, the light ally, the great crab's declaration; the champion's buffs
  and exclusions, depth 11, the rare mobs, the alternate's reachability; the families' odds
  against 40,000 draws under a seeded generator; the rotation's top-level keys in order; the
  live Run's generator; local classes excluded (fairness 6; adversarial 20; edge 32, 33; gap 2,
  3, 7).
- Documents: ADR-0017's amendment rewritten for all of it; the story's numbers (129, the cited
  lines) corrected; the glossary (gap 8; adversarial 23).

**Not taken, with reasons.**

- Calling the mimics' and wraiths' setters under the context (adversarial 6): the setter's
  argument is a level, not a depth; the classes are flagged `statsSetLater` and the setter is
  a later story's table if the Brain needs it.
- A live Run with talent points or an ascending hero (adversarial 7, edge 16): the bytecode
  enumeration names every constructor that reads the hero, whatever its value.
- Typed variant fields (adversarial 20): the values are text by design, one shape for numbers,
  names and lists; the schema says so.
- Combined depth-and-challenge variants (adversarial 19, edge 18): the generator holds that
  the two compose and would fail if they did not; a combined variant is not needed at this tag.
- Running the completeness test in its own JVM for a bare-classpath claim (gap 9): the claim is
  held by the static ban on the toolkit, as ADR-0017 says.
- `RotationEntry` distinct names at depth (edge 22): taken as a record invariant; a literal
  naming a class and its family is not refused, since the spawner lists none.

## Suggested review order

1. [`CodexLeakTest.java`](https://github.com/watchthelight/shatterfish/blob/4e74e72a9/shatterfish/codex/src/test/java/org/shatterfish/codex/CodexLeakTest.java),
   the gate's allowlist for the door and the live Run.
2. [`GameContext.java`](https://github.com/watchthelight/shatterfish/blob/4e74e72a9/shatterfish/codex/src/main/java/org/shatterfish/codex/GameContext.java),
   the door and its generator.
3. [`Mobs.java`](https://github.com/watchthelight/shatterfish/blob/4e74e72a9/shatterfish/codex/src/main/java/org/shatterfish/codex/Mobs.java),
   the entry builder, the roll readers, the named facets and readers.
4. [`Sources.java`](https://github.com/watchthelight/shatterfish/blob/4e74e72a9/shatterfish/codex/src/main/java/org/shatterfish/codex/Sources.java)
   and [`SourcesTest.java`](https://github.com/watchthelight/shatterfish/blob/4e74e72a9/shatterfish/codex/src/test/java/org/shatterfish/codex/SourcesTest.java),
   what a class's own lines, member lines and returns are.
5. [`Rotation.java`](https://github.com/watchthelight/shatterfish/blob/4e74e72a9/shatterfish/codex/src/main/java/org/shatterfish/codex/Rotation.java)
   and [`RotationTest.java`](https://github.com/watchthelight/shatterfish/blob/4e74e72a9/shatterfish/codex/src/test/java/org/shatterfish/codex/RotationTest.java),
   the spawner's literals and the checks.
6. [`docs/adr/0017-codex-generation-and-citations.md`](https://github.com/watchthelight/shatterfish/blob/4e74e72a9/docs/adr/0017-codex-generation-and-citations.md),
   the story 2.2 amendment.
7. [`CodexCompletenessTest.java`](https://github.com/watchthelight/shatterfish/blob/4e74e72a9/shatterfish/codex/src/test/java/org/shatterfish/codex/CodexCompletenessTest.java)
   and [`codex/v4.0.0/`](https://github.com/watchthelight/shatterfish/tree/4e74e72a9/codex/v4.0.0).
