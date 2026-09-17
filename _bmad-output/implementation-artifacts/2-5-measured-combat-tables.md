---
story: 2.5
key: 2-5-measured-combat-tables
title: "Measured combat tables"
epic: 2
issue: 39
type: 'feature'
status: 'in-progress'
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
- [ ] `shatterfish/api/.../Codex.java`, `CodexJson.java` -- `VERSION = 5`; records `Grid(what, from, to, step, citation)`, `HitCell(accuracy, evasion, samples, hitPerMille)`, `HitTable(method, citation, grids, cells)`, `Spread(min, max, meanPerMille, samples)`, `DamageEntry(className, tier, level, spread, method, citation)`, `Combat(seed, hit, weapons, armours, mobs)`; rendering one cell and one entry per line -- api-typed tables.
- [ ] `shatterfish/api/src/test/.../CodexJsonTest.java` -- goldens for a hit cell, a damage entry and the grids; refusals (a share out of range, a spread whose mean is outside its bounds, a cell twice, an empty grid) -- the text held.
- [ ] `shatterfish/codex/.../Sparring.java` -- two minimal `Char` subclasses (an attacker whose `attackSkill` returns the grid's accuracy, a defender whose `defenseSkill` returns the grid's evasion), `act()` doing nothing, nothing else overridden -- the rig that feeds the engine.
- [ ] `shatterfish/codex/.../GameContext.java` -- a fourth field, `Dungeon.hero`, set to a bare hero around a measurement and restored, since `Char.hit` dereferences it -- the door widened by one, with the gate's allowlist and ADR-0017 updated.
- [ ] `shatterfish/codex/.../Combat.java` -- the measurements: `Char.hit` over the accuracy and evasion grid; each melee weapon's `damageRoll` and each armour's `DRRoll` by tier and level; each mob's `drRoll`; every table cited to the method measured and every cell carrying its sample count -- the combat table.
- [ ] `shatterfish/codex/.../Generate.java` -- `combat.json` in the tables map -- the task extended.
- [ ] `shatterfish/codex/src/test/.../CombatTableStabilityTest.java` -- two generations in one process are identical with the Codex seed moved between them; a sampled cell is re-measured against the engine and agrees within the band its sample count earns; the bounds of every spread are the method's own; the grid covers what the table says -- the measurement held.
- [ ] `shatterfish/codex/src/test/.../CodexLeakTest.java`, `CodexCompletenessTest.java` -- the live Run's hero, depth and generator unchanged by a generation; every weapon and armour of the item table is in the damage tables; every citation resolves -- NFR-1.
- [ ] `codex/v4.0.0/` regenerated; `docs/adr/0017-...md` amendment; `docs/codex/index.md`; `docs/glossary.md` -- NFR-6.

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
