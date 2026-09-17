---
story: 2.5
key: 2-5-measured-combat-tables
title: "Measured combat tables"
epic: 2
issue: 39
type: 'feature'
status: 'review'
created: '2026-09-16'
updated: '2026-09-16'
review_loop_iteration: 0
baseline_commit: '834b3fee1'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Brain has no honest expectation of combat. Nothing says how often an attack at a
given accuracy lands against a given evasion, or what a weapon of a tier rolls, or what an armour
of a tier absorbs; and FR-14 forbids writing a combat formula out by hand, because a transcribed
formula is a guess that drifts from the engine.

**Approach:** One table, `combat.json`, whose every number is measured by running the engine's own
methods over a stated grid under the Codex's own generator. Accuracy against evasion is measured
by calling `Char.hit` itself, with two minimal sparring characters whose only job is to return the
grid's accuracy and evasion; weapon damage by calling each weapon's own `damageRoll`; armour
absorption by calling each armour's own `DRRoll`; and a mob's damage reduction by its own
`drRoll`. Each measured table names the method it measured with its `path:line`, the grid it
swept, the sample count and the generator seed. `CombatTableStabilityTest` holds that two
generations in one process are identical and that a measured share is the share the method
produces, and CI's drift check holds the bytes across the two platforms.

## Boundaries & Constraints

**Always:** Every number is measured, never derived: no combat arithmetic is written in `codex`
or `brain`, and a measured cell carries the count of samples it came from. Measurement runs under
`GameContext`, so it draws from the Codex's own generator and restores every Run static it sets.
The sparring characters exist only to feed the engine the grid's two numbers; they override
nothing else and carry no buff. The grid is stated in the table, so a consumer knows what was not
measured. Shares are integers in thousandths; no float is written. Codex version 5.

**Ask First:** Any transcription of a combat formula; a hook; a boot; measuring through a live
Run.

**Never:** No hero talents, rings, enchantments or champion buffs in the grid (the bare paths
only; the modified ones are a later story); no mob-versus-mob matrix (the mobs' own rolls are
story 2.2's table); no procs.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Accuracy against evasion | accuracy 10, evasion 5 | the share of `Char.hit` that returned true over the sampled attacks, in thousandths, with the sample count | N/A |
| Evasion of zero | accuracy 10, evasion 0 | 1000: a defender who rolls zero is always hit | N/A |
| Accuracy of zero | accuracy 0, evasion 5 | 0 with the sample count, since the attacker's roll cannot reach a positive roll | N/A |
| Weapon damage | a tier-3 weapon at level 0 | the measured distribution: minimum, maximum, mean in thousandths, over the samples, with the method cited | A weapon whose roll leaves the stated bounds fails naming it |
| Armour absorption | a tier-5 armour at level 0 | the measured distribution of `DRRoll` | N/A |
| A mob's reduction | the rat | the measured distribution of `drRoll`, which story 2.2 carries as an expression | N/A |
| Two generations | the same process, the seed moved between them | identical bytes, since the measurement seeds its own generator per cell | N/A |
| A live Run | a Run at an Input wait | the bytes are a cold generation's, and the Run's generator, hero and depth are what they were | N/A |

</frozen-after-approval>

## Code Map

- `core/.../actors/Char.java:615-684` -- `hit(attacker, defender, accMulti, magic)`: `attackSkill(defender)` against `defenseSkill(attacker)`, the infinite-accuracy and infinite-evasion gates (`:169-170`), `Random.Float(acu)` against `Random.Float(def)` with the bless, hex, daze, champion, ascension and ferret-tuft multipliers; **it reads `Dungeon.hero.heroClass` and `hasTalent` unguarded (`:648-652`, `:668-672`), so a hero must exist**, and `AscensionChallenge.statModifier` (`actors/buffs/AscensionChallenge.java:105-108`) and `FerretTuft.evasionMultiplier` (`items/trinkets/FerretTuft.java:49-59`) return 1 with a bare one. `FloatingText.getHitReasonIcon`/`getMissReasonIcon` (`effects/FloatingText.java:313, :429`) are pure logic over ints and are called on every hit.
- `core/.../actors/Char.java:689-714` -- `attackSkill` and `defenseSkill` return 0 on `Char`; `drRoll()` is barkskin only; `damageRoll()` is 1. `actors/Actor.java:59` -- `act()` is the one abstract method a `Char` subclass must implement.
- `core/.../actors/mobs/Mob.java:782-802` -- a mob's evasion is its public `defenseSkill` field unless surprised, and `surprisedBy` (`:873-877`) is false for an attacker that is not the hero; each mob overrides `attackSkill` with a constant, so a stock mob cannot sweep a grid.
- `core/.../items/weapon/melee/MeleeWeapon.java:250-259` (`min`, `max` by tier and level), `:294-304` `damageRoll(Char owner)`: `augment.damageFactor(super.damageRoll(owner))` plus a strength bonus **only when the owner is a `Hero`**, so a non-hero owner measures the bare roll; `items/weapon/Weapon.java` `damageRoll` is `Random.NormalIntRange(min(), max())`.
- `core/.../items/armor/Armor.java:379-410` -- `DRMax`/`DRMin` by tier and level, with the No Armour challenge branch; `DRRoll()` is the measured method.
- `SPD-classes/.../com/watabou/utils/Random.java:138-140` -- `NormalIntRange` is two floats averaged, so a damage roll is triangular and its mean is not its midpoint; `:115-125` `Float(max)` and `Int(max)`.
- `shatterfish/codex/.../GameContext.java` -- the door: depth, challenges and level today; this story adds the hero, since `Char.hit` dereferences it. `Mobs.java` (the 129 constructors), `Items.java` (the weapon and armour lists), `Sources.java`, `Guarantees.java` (the mirror-pin shape) are the patterns to follow.
- `shatterfish/codex/src/test/.../CodexLeakTest.java` -- the gate and the live Run to extend; `CodexSeedFreeTest` -- the two-generation comparison the stability test builds on.

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/api/.../Codex.java`, `CodexJson.java` -- `VERSION = 5`; records `Grid(what, from, to, step, citation)`, `HitCell(accuracy, evasion, samples, hitPerMille)`, `HitTable(method, citation, grids, cells)`, `Spread(min, max, meanPerMille, samples)`, `DamageEntry(className, tier, level, spread, method, citation)`, `Combat(seed, hit, weapons, armours, mobs)`; rendering one cell and one entry per line -- api-typed tables.
- [x] `shatterfish/api/src/test/.../CodexJsonTest.java` -- goldens for a hit cell, a damage entry and the grids; refusals (a share out of range, a spread whose mean is outside its bounds, a cell twice, an empty grid) -- the text held.
- [x] `shatterfish/codex/.../Sparring.java` -- two minimal `Char` subclasses (an attacker whose `attackSkill` returns the grid's accuracy, a defender whose `defenseSkill` returns the grid's evasion), `act()` doing nothing, nothing else overridden -- the rig that feeds the engine.
- [x] `shatterfish/codex/.../GameContext.java` -- a fourth field, `Dungeon.hero`, set to a bare hero around a measurement and restored, since `Char.hit` dereferences it -- the door widened by one, with the gate's allowlist and ADR-0017 updated.
- [x] `shatterfish/codex/.../Combat.java` -- the measurements: `Char.hit` over the accuracy and evasion grid; each melee weapon's `damageRoll` and each armour's `DRRoll` by tier and level; each mob's `drRoll`; every table cited to the method measured and every cell carrying its sample count -- the combat table.
- [x] `shatterfish/codex/.../Generate.java` -- `combat.json` in the tables map -- the task extended.
- [x] `shatterfish/codex/src/test/.../CombatTableStabilityTest.java` -- two generations in one process are identical with the Codex seed moved between them; a sampled cell is re-measured against the engine and agrees within the band its sample count earns; the bounds of every spread are the method's own; the grid covers what the table says -- the measurement held.
- [x] `shatterfish/codex/src/test/.../CodexLeakTest.java`, `CodexCompletenessTest.java` -- the live Run's hero, depth and generator unchanged by a generation; every weapon and armour of the item table is in the damage tables; every citation resolves -- NFR-1.
- [x] `codex/v4.0.0/` regenerated; `docs/adr/0017-...md` amendment; `docs/codex/index.md`; `docs/glossary.md` -- NFR-6.

**Acceptance Criteria:**
- Given the accuracy and evasion grid, when the table is read, then every cell carries the share of the engine's own `Char.hit` that landed and the samples it came from, and the method is cited (`CombatTableStabilityTest`, `CodexLeakTest`).
- Given each weapon and armour tier and level of the grid, when the table is read, then it carries the measured minimum, maximum and mean of the engine's own roll, cited (`CombatTableStabilityTest`).
- Given two generations in one process with the Codex's seed moved between them, when the bytes are compared, then they are identical (`CodexSeedFreeTest`, `CombatTableStabilityTest`).
- Given a live Run, when a generation runs, then the Run's hero, depth and generator are what they were (`CodexLeakTest`).
- Given the whole of `codex` and `brain`, when they are read, then no combat formula is written out by hand (the story's Review, and the tables carry the citations instead).

## Spec Change Log

- 2026-09-16, during implementation: **the hit table cannot be measured by the generator.**
  `Char.hit` writes the icon of the reason an attack landed, and reaching that code initialises
  `FloatingText`, whose initialiser builds a texture film; a generator that may not boot (ADR-0017)
  cannot run it. The table therefore names the method, cites it, states the grid and carries
  `measured: false` with the reason, exactly as story 2.3's items carry `constructed: false`; the
  rig is written, kept and exercised against the engine by `CombatTableStabilityTest`, which runs
  in a booted process, and the measurement itself is handed forward as an idea (a harness task, or
  a hook that lets the engine skip the icon with no scene). The first acceptance criterion is
  therefore met in part: the method is named, cited and gridded, and the cells are absent with a
  reason rather than transcribed.
- 2026-09-16, during implementation: the armour spread is measured through the engine's own
  `Hero.drRoll` with the armour worn and the wearer's strength at the armour's requirement, since
  the game has no roll of the armour's own; `Codex.RollEntry` replaces the spec's `DamageEntry` and
  carries the method's name, so a reader knows which it was. Four mobs (the two statues, the
  guardian trap's guardian, the transmogrified rat) roll with what the game gives them at spawn and
  are named with their reasons rather than measured. A measurement draws under its own seed, not
  the Codex's construction seed, so that story 2.1's seed-free guarantee still holds.

- 2026-09-16, after the review: the unmeasurable mobs are story 2.2's `STATS_SET_LATER` plus the
  four that throw, not the four alone; seven rows that had shipped as measurements of states the
  game never produces are gone, and the earth guardian's negative row, which the first test
  asserted and explained as a mechanic, was the defect and not the mechanic. The weapon table
  measures every class the game calls a `Weapon`, which adds the spirit bow. The door closes the
  hero (its constructor only) and holds the scene's redraw flag that an item's level write sets.
  The wearer's strength is restored after an armour is measured, the mean is re-measured against
  the engine rather than asserted by nothing, and every combat citation is opened.

## Design Notes

Micro-brainstorm on the rig. (a) Sweep the grid with stock mobs, refused: every mob overrides
`attackSkill` with a constant, so the grid cannot be fed. (b) Transcribe the comparison
(`Random.Float(acu) >= Random.Float(def)`) and sample it, refused by FR-14 and by this story's
own premise. (c) Two minimal `Char` subclasses that return the grid's numbers and nothing else,
chosen: the engine's own `hit` runs, every multiplier and gate inside it runs, and the generator
writes no combat arithmetic. `Char`'s only abstract member is `act()`.

The hero is the one Run static this needs: `Char.hit` dereferences `Dungeon.hero` for a talent
check, so a measurement with no hero throws. A bare hero is a rogue with no talents, so the
branch contributes nothing, and the ferret tuft and ascension multipliers return one. The door
gains that field with the same discipline as the depth, the challenges and the level: set,
measured, restored, and the live Run holds that its own hero is still its own afterwards. This
is the story's one "Ask First" answered in the affirmative on the owner's standing instruction to
keep moving; it is recorded here, in the ADR and in the pull request, and the fairness reviewer
is asked to look at it first.

Sampling: each cell seeds its own generator from the Codex's seed and its coordinates, so a cell
is reproducible on its own and the table does not depend on the order cells are measured in.

## Verification

**Commands:**
- `./gradlew :codex:generate -Pshatterfish.mobile=off && git status --short codex/` -- expected: nothing changed after the commit.
- `./gradlew :codex:test :api:test -Pshatterfish.mobile=off` -- expected: green, `CombatTableStabilityTest` among them.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- expected: green.

## Dev notes

Implemented on `story/2-5-measured-combat-tables` from `834b3fee1`. One table joined
`:codex:generate`: `combat.json`, measured by running the engine's own methods. The generator
gained `Combat` (the measurements and the names of what cannot be measured) and `Sparring` (the
rig: two characters that return an accuracy and an evasion and nothing else); `GameContext`
gained its fourth field and a seeded `measure`. The api gained the grid, cell, spread, roll and
combat records and their rendering; Codex version 5. No hook, no upstream file, no change to the
fair path or the Observation schema.

## Acceptance criteria and how each was met

- **The tables cover accuracy against evasion**: met in part, and the part that is missing says
  so. `Char.hit` cannot run in a generator that may not boot (it initialises `FloatingText`, which
  builds a texture film), so the table names the method, cites it, states the grid and carries
  `measured: false` with the reason, as story 2.3's items carry `constructed: false`. The rig is
  written and `CombatTableStabilityTest` runs it against the engine in a booted process: no
  evasion always lands, no accuracy never does, even stats land about half the time, and more of
  either moves it the way it should.
- **Damage and damage-reduction distributions for each weapon and armour tier**: every class the
  game calls a `Weapon` (372 rows: 62 classes at six levels) and every armour (66 rows) with the
  measured minimum, maximum and mean, plus every mob whose reduction a bare instance can roll
  (110); `CombatTableStabilityTest` holds the bounds against the engine's own and re-measures
  every weapon's mean.
- **Each table names the method it measured with its `path:line`, and the grid it swept**: every
  entry carries the method's name and citation, `CodexLeakTest` opens each one, and the grid and
  the sample counts are in the table.
- **`CombatTableStabilityTest` asserts the tables are reproducible across runs and platforms**:
  two measurements in one process are identical, a measurement draws under its own seed so moving
  the Codex's construction seed changes nothing (`CodexSeedFreeTest`), and CI's comparison of the
  committed bytes on Linux against a generation on Windows is the two-platform check.
- **No combat formula is written out by hand in `codex` or `brain`**: held by reading, and the
  review checked it; the tables carry the citations instead.

## What was built

- `api`: `Codex.Grid`, `HitCell`, `HitTable`, `Spread`, `RollEntry`, `Combat`; `CodexJson.combat`;
  `VERSION = 5`; goldens and refusals in `CodexJsonTest`.
- `codex`: `Combat`, `Sparring`; `GameContext.measure` with the hero and the redraw flag;
  `Generate` extended; `CombatTableStabilityTest`; `CodexLeakTest` extended.
- `codex/v4.0.0/combat.json`, the manifest at version 5.
- ADR-0017's amendment; the Codex index; the glossary (measurement); three ideas.

## What the story found

- **The method that decides a hit cannot be run by the generator.** Every return path of
  `Char.hit` writes the icon of the reason, which initialises `FloatingText`, whose initialiser
  builds a texture film. The wall is one class further down than story 2.3's, and the answer is
  the same: say so in the table.
- **The engine has no armour roll of its own.** The wearer rolls it, so the measurement equips a
  bare hero and runs `Hero.drRoll`, with the wearer's strength at the armour's requirement.
- **A bare hero has no class at all.** `heroClass` is null until a Run chooses one, which is what
  makes the hero safe to measure with, and is not what the first draft of this story's documents
  said (they called it a rogue).
- **A mob the game equips at spawn rolls a placeholder.** Four throw; seven more returned a number
  that looked measured. The earth guardian rolls from a wand level of minus one, and the first
  version of this story shipped that negative row and asserted it in a test as though it were a
  mechanic. The unmeasurable list is story 2.2's own, held against it.
- **The spirit bow is a weapon that is neither melee nor missile**, so a reader that tested for
  those two families dropped it, and a coverage test written with the same test could not see it.
- **Setting an item's level writes a Run static**, the flag that asks a scene to redraw its item
  displays, so the door holds and restores it.

## Decisions taken inside the story

- **The rig over transcription.** Two minimal characters feed the engine its own inputs; the
  generator writes no combat arithmetic.
- **The door gains the hero**, which the story's own "Ask First" named. Taken under the owner's
  standing instruction to keep working, recorded here, in the ADR and in the pull request, and put
  first to the fairness reviewer, who judged the decision correct and the enforcement too loose;
  the gate now closes it.
- **A measurement seed of its own**, so that story 2.1's seed-free guarantee still holds.
- **What cannot be measured is named, not guessed**: the hit table, the four that throw, the seven
  the game equips later.

## Evidence

- `:api:test` green, 347 tests, with `CodexJsonTest` (13); `:codex:test` green, 53 tests
  (`CombatTableStabilityTest` 5, `CodexLeakTest` 9, `CodexCompletenessTest` 10,
  `GuaranteeArithmeticTest` 5, `CodexSeedFreeTest` 3, `RoomsReaderTest` 5, `ItemsReaderTest` 5,
  `SourcesTest` 5, `RotationTest` 3, `NamesTest` 3).
- `./gradlew build` green: 629 tests, 73 suites.
- `./gradlew :codex:generate` twice: `git status --short codex/` empty after the commit.
- Mutation battery, thirteen mutations before the review:
    - M1 a measurement draws under the Codex's construction seed: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest.
    - M2 every cell draws under the same seed: caught by CodexLeakTest, CodexSeedFreeTest.
    - M3 a spread reports the midpoint of its bounds rather than the mean it measured: caught by CodexLeakTest, CodexSeedFreeTest.
    - M4 a spread keeps only the first value it saw: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest, GuaranteeArithmeticTest.
    - M5 a weapon is measured with a hero as its wielder, which adds the hero's strength: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest, GuaranteeArithmeticTest.
    - M6 an armour is measured by a wearer too weak for it: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest.
    - M7 a mob that cannot roll is skipped without being named: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest.
    - M8 the rig's fighters swap their stats: caught by CombatTableStabilityTest.
    - M9 a hit share is rounded down rather than half up: caught by CombatTableStabilityTest.
    - M10 the door leaves its bare hero in place: caught by CodexLeakTest.
    - M11 the samples a spread carries are not the samples it took: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest.
    - M12 the hit table claims it was measured: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest, GuaranteeArithmeticTest.
    - M13 a weapon is measured at one level only: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest.

- The battery rerun after the review patch, seventeen mutations, four of them of the review's own
  fixes (a mob whose stats the game sets later measured anyway, the spirit bow dropped, the
  wearer's strength not put back, the redraw flag left set):
    - M1 a measurement draws under the Codex's construction seed: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest.
    - M2 every cell draws under the same seed: caught by CodexLeakTest, CodexSeedFreeTest.
    - M3 a spread reports the midpoint of its bounds rather than the mean it measured: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest.
    - M4 a spread keeps only the first value it saw: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest, GuaranteeArithmeticTest.
    - M5 a weapon is measured with a hero as its wielder, which adds the hero's strength: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest, GuaranteeArithmeticTest.
    - M6 an armour is measured by a wearer too weak for it: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest.
    - M7 a mob whose stats the game sets later is measured anyway: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest.
    - M8 the rig's fighters swap their stats: caught by CombatTableStabilityTest.
    - M9 a hit share is rounded down rather than half up: caught by CombatTableStabilityTest.
    - M10 the door leaves its bare hero in place: caught by CodexLeakTest.
    - M11 the samples a spread carries are not the samples it took: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest.
    - M12 the hit table claims it was measured: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest, GuaranteeArithmeticTest.
    - M13 a weapon is measured at one level only: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest.
    - M14 only the melee and missile families are measured, so the spirit bow is dropped: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest.
    - M15 the wearer's strength is not put back: **survived**, as expected: every armour is measured under a hero of its own, so the next measurement sets the strength again before it rolls.
    - M16 the scene's redraw flag is left set: **survived**, as expected: no test watches that flag, since a live Run would have to set it before a generation to see it put back.
    - M17 a mob that rolls a placeholder is measured as if it were the game: caught by CodexLeakTest, CodexSeedFreeTest, CombatTableStabilityTest, GuaranteeArithmeticTest.

## Deviations

- The first acceptance criterion is met in part; the Spec Change Log says so and the table says so.
- `Codex.RollEntry` replaces the spec's `DamageEntry`, and the armour rows name `Hero.drRoll`
  rather than a roll of the armour's own, which the game does not have.

## Known limitations, handed forward

- **The hit table is empty.** The rig is written; a story that runs in the harness, where booting
  is ordinary, can fill it. An idea records both routes (a harness task, or a hook that lets the
  engine skip the icon when no scene exists).
- **The tables do not compose.** An expected damage is a damage roll less a reduction roll, and
  the engine's subtraction is behind the same wall; the Codex carries the parts.
- **The measured paths are the bare ones**: no ring, enchantment, glyph, champion buff, talent or
  encumbrance penalty. Each is a branch of the same methods, so the same rig measures them.
- **Two engine statics the door does not hold**: a dart's bow reference and a flail's spin boost
  are read by their own damage rolls, so a Run holding a crossbow would shift the dart rows of a
  generation made inside it. Neither is reachable from the generator's own code, and the live-Run
  test's Run holds neither; a story that measures the modified paths should hold them.
- **The measured means are the bare paths' at the levels the grid names**, six of them; a level
  the grid does not name is not in the table.

## Follow-ups for later stories

- 2.6: the traps, the recipes and the level structure.
- The hit table, measured where a process has booted (the idea).
- 2.9: the drift check in CI and the generated index page listing these tables.

## Review

Four reviewers on `git diff main...HEAD` from the committed state: the fairness reviewer (nine
findings, two blocking), the adversarial lens (twenty-two), the edge-case hunter (nineteen) and
the verification-gap lens (eight). One patch commit, `044c3f56d`.

**Taken.**

- The mobs that cannot be rolled bare: story 2.2's `STATS_SET_LATER` plus the four that throw,
  held against that list, and the wrong test that asserted the earth guardian's placeholder as a
  mechanic removed (fairness 2; edge 1; adversarial 3).
- The gate closed: the hero's constructor only, the belongings type dropped, the scene's redraw
  flag admitted and held by the door (fairness 1; adversarial 18).
- The spirit bow measured, and the coverage test's expectation built from the game's compiled
  hierarchy rather than the reader's own test (adversarial 4, 22; edge 2).
- The wearer's strength restored; the mean re-measured against the engine for every weapon, with a
  check that some mean is not the midpoint a guess would give; every combat citation opened
  (fairness 3; gap 1, 2; adversarial 11).
- The documents: a bare hero has no class, the gate's claim matches the rule, the composition the
  tables do not carry is stated, and the javadoc no longer credits a test that holds nothing of
  the kind (gap 3, 5; adversarial 13, 18).

**Not taken, with reasons.**

- Measuring the modified paths, the composition, or the hit table itself (adversarial 13; edge
  several): each is named as a limitation and an idea; this story's boundary is the bare paths,
  and the wall is documented rather than tunnelled under.
- Holding a dart's bow and a flail's spin boost in the door (edge 3): neither is reachable from
  the generator's own code and the door may not name every static the game keeps; recorded as a
  limitation for the story that measures the modified paths.
- A tighter band on the hit rig's even-stats check (fairness caveat): the rig's table is not
  shipped, so the check is a smoke test of the rig, not of a table.

## Suggested review order

1. [`Combat.java`](https://github.com/watchthelight/shatterfish/blob/044c3f56d/shatterfish/codex/src/main/java/org/shatterfish/codex/Combat.java),
   what is measured, what is named instead, and why.
2. [`GameContext.java`](https://github.com/watchthelight/shatterfish/blob/044c3f56d/shatterfish/codex/src/main/java/org/shatterfish/codex/GameContext.java)
   and the gate in [`CodexLeakTest.java`](https://github.com/watchthelight/shatterfish/blob/044c3f56d/shatterfish/codex/src/test/java/org/shatterfish/codex/CodexLeakTest.java),
   the fourth field and what closes it.
3. [`CombatTableStabilityTest.java`](https://github.com/watchthelight/shatterfish/blob/044c3f56d/shatterfish/codex/src/test/java/org/shatterfish/codex/CombatTableStabilityTest.java),
   the re-measurement, the bounds, the coverage and the rig.
4. [`Sparring.java`](https://github.com/watchthelight/shatterfish/blob/044c3f56d/shatterfish/codex/src/main/java/org/shatterfish/codex/Sparring.java),
   the rig itself.
5. [`0017-codex-generation-and-citations.md`](https://github.com/watchthelight/shatterfish/blob/044c3f56d/docs/adr/0017-codex-generation-and-citations.md),
   the amendment.
