---
story: 2.4
key: 2-4-item-guarantees-tier-tables-and-limited-drops
title: "Item guarantees, tier tables and limited drops"
epic: 2
issue: 38
type: 'feature'
status: 'review'
created: '2026-09-16'
updated: '2026-09-16'
review_loop_iteration: 0
baseline_commit: 'f91085241'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Brain's chapter counters (how many strength potions and upgrade scrolls a floor
set still owes, which floor a stylus or a stone can come on, what tier the floor's equipment is
drawn at, what a special room adds to the floor) have no source but memory; the game decides
them by arithmetic in `Dungeon`, `Generator` and the room classes.

**Approach:** Three tables added to `:codex:generate`. `guarantees.json`: every limited drop the
level's creation decides (`posNeeded`, `souNeeded`, `asNeeded`, the enchantment and intuition
stones, the trinket catalyst, the laboratory room) as the method's cited text plus a schedule,
the exact chance in thousandths for every depth 1-26 and every counter state, that the drop is
needed and that it is placed (boss floors place none; under Forbidden Runes every second upgrade
scroll is withheld); the boss depths and the placement site cited. `tiers.json`: the floor-set
tier table read from the generator's literal, with the rules that an armor picks its class by
tier index and a weapon or missile picks a tier category, cited. `rooms.json`: the special and
secret room lists as the game keeps them (equipment, consumable, crystal-key, potion-spawn,
secret), each room's items added to the floor's spawn list (keys, solution potions) with counts
and citations, its prize draws as cited text, the secrets-per-region base and the queue rule.
`GuaranteeArithmeticTest` holds the schedules to the game's own methods over a sweep of depths
and counter states, and the tier table to the game's own draws.

## Boundaries & Constraints

**Always:** The schedules are computed by a mirror of the game's arithmetic in the generator,
and every mirror is pinned to the exact text of the method it mirrors (comments stripped,
whitespace collapsed): a generation fails naming the method when the pinned source no longer
reads so. The generator reads `Dungeon.java`, `Generator.java`, `Level.java` and the room
classes as source through `Sources`, never as classes; it depends on no `Dungeon` member and
draws nothing. The tier literal is parsed from source and held on the test side against
`Generator.randomArmor/randomWeapon/randomMissile` sampled under a seeded generator. Every
entry cites its declaration. Codex version 4.

**Ask First:** A new field for `GameContext`; a hook; a schedule that would need a level built.

**Never:** No combat numbers (2.5); no trap, recipe or level structure beyond the room lists
(2.6); no room layouts; no prize probabilities (the draws are cited as text); no Brain reading.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Strength potions | depth 1, count 0 | needed 500 (target 2 - 0/2 = 2; `floorThisSet % 2 == 1` halves), placed 500 | N/A |
| Strength potions, set done | depth 3, count 2 | needed 0, placed 0 | N/A |
| Upgrade scrolls under Forbidden Runes | depth 2, count 1 | needed as `Random.Int(3) < 2` = 667; placed 667; placed under NO_SCROLLS 0 (the second scroll is withheld) | N/A |
| Boss floor | depth 5, any count | needed as the method says, placed 0 (`!Dungeon.bossLevel()`) | N/A |
| Enchantment stone | depth 7, not dropped | region 2, floorsVisited 2, `Random.Int(7) == 0` = 143 | N/A |
| Laboratory | depth 8, count 1 | floorThisRegion 3, 500; depth 9: 1000; depth 6: 0 | N/A |
| Tier table | floor set 1 (depths 5-9) | weights 0/25/50/20/5 per tier, armor by `ARMOR.classes[index]`, weapons by `WEP_T<n>` | A literal whose row count or width is not five fails naming it |
| A room's items | `MagicalFireRoom` | spawns `PotionOfFrost` x1 (`:112`), draws cited; `CrystalPathRoom` three crystal keys | A room whose list membership in source differs from the generator's literal fails naming it |
| A mirror's source moves | a later tag edits `souNeeded` | generation fails naming the method and the text expected | N/A |

</frozen-after-approval>

## Code Map

- `core/.../Dungeon.java:104-143` -- `LimitedDrops` enum (public static nested; `count`, `dropped()`); `:437-443` `bossLevel(int)` (5, 10, 15, 20, 25); `:529-543` `posNeeded` (2 per set; `Random.Int(2)` halves on odd floors), `:545-554` `souNeeded` (3 per set; `Random.Int(5 - floorThisSet) < left`), `:556-564` `asNeeded` (1 per set), `:566-577` `enchStoneNeeded` (region > 1; `Random.Int(9 - floorsVisited) == 0`, floor 10 skipped), `:579-582` `intStoneNeeded` and `:584-587` `trinketCataNeeded` (depth < 5, `Random.Int(4 - depth) == 0`), `:589-599` `labRoomNeeded` (region > count; floor 3 at 1/2, floor 4 always).
- `core/.../levels/Level.java:224-257` -- the placement: `!Dungeon.bossLevel() && Dungeon.branch == 0`; a FOOD draw; the six `if (Dungeon.xNeeded())` blocks with `count++`/`drop()`; `:232-240` the Forbidden Runes rule (`count % 2 != 0`); `:278` a second FOOD on a LARGE feeling.
- `core/.../levels/rooms/special/SpecialRoom.java:81-102` -- `EQUIP_SPECIALS` (9), `CONSUMABLE_SPECIALS` (10), `CRYSTAL_KEY_SPECIALS` (4, public), `POTION_SPAWN_ROOMS` (6), all `Arrays.asList` literals; `:107-138` `initForRun` (shuffles) and `initForFloor` (a laboratory first when `labRoomNeeded`); `:160-185` `createRoom` (`Random.chances(6, 3, 1)` over the queue). `levels/rooms/secret/SecretRoom.java:37-41` `ALL_SECRETS` (12); `:47` `baseRegionSecrets` `{2f, 2.25f, 2.5f, 2.75f, 3.0f}`; `:66-88` `secretsForFloor`; `:90-100` `createRoom`. `RegularLevel.java:151-163` the calls.
- Room items: `level.addItemToSpawn(new X(...))` in each room's `paint` (e.g. `MagicalFireRoom.java:112` PotionOfFrost, `PoolRoom.java:91` PotionOfInvisibility, `SentryRoom.java:161` PotionOfHaste, `CrystalPathRoom.java:246-248` three crystal keys, `CrystalVaultRoom.java:86-89` a crystal key and an iron key, most others an iron key); prizes as `Generator.random*(...)` lines (`CryptRoom.java:77` one set deeper). `PitRoom.java:94` drops a crystal key on the remains (a `drop`, not a spawn). Imports resolve a simple name to its class.
- `core/.../items/Generator.java:613-619` -- `floorSetTierProbs` (private static final, five rows of five); `:776-785` `randomArmor(floorSet)`: `Category.ARMOR.classes[Random.chances(floorSetTierProbs[floorSet])]`; `:787-793` `wepTiers`, `:806-819` `randomWeapon`; `:821-827` `misTiers`, `:838-851` `randomMissile`; `GameMath.gate(0, floorSet, 4)`; `:646` `reset()`.
- `shatterfish/codex/.../Sources.java` -- `body(root, Class)` reads by class; add `file(root, path)` for a file by path (the generator may not name `Dungeon`); `returns`, `stripComment`, `braces`; `Decks.java` -- the category class lists the tier rule indexes into (`ARMOR.classes` order: cloth, leather, mail, scale, plate, then the class armors).
- `shatterfish/codex/src/test/.../CodexLeakTest.java` -- the gate (`NO_RUN_STATE` bans `Dungeon` outside `GameContext`; `belongToAnyOf` covers nested classes, so `LimitedDrops` is read from source); the live Run to extend with the counters bumped.

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/api/.../Codex.java`, `CodexJson.java` -- `VERSION = 4`; records `ScheduleEntry(depth, count, neededPerMille, placedPerMille, placedNoScrollsPerMille)`, `DropSchedule(name, item, method, once, perSet, expression, citation, placementCitation, entries)`, `Guarantees(bossDepths, bossCitation, placementCitation, drops)`, `TierRow(floorSet, depthFrom, depthTo, weights)`, `TierRule(what, expression, citation)`, `Tiers(rows, citation, armor, weapon, missile)`, `Spawn(className, count, citation)`, `RoomEntry(className, secret, equipment, consumable, crystalKey, potionSpawn, spawns, draws, citation)`, `Rooms(specials, secrets, secretsPerRegionPerMille, secretsCitation, queueExpression, queueCitation)`; rendering one entry per line; `JsonRenderingTest.HELPERS` -- api-typed tables.
- [x] `shatterfish/api/src/test/.../CodexJsonTest.java` -- goldens for a schedule, a tier row, a room; refusals (a per-mille out of range, a depth outside 1-26, a row not five wide, a room listed twice) -- the text held.
- [x] `shatterfish/codex/.../Sources.java` -- `file(root, path)`; `Mirror` helper: the pinned normalised text of a method against its source -- the drift guard.
- [x] `shatterfish/codex/.../Guarantees.java` -- the seven mirrors, each pinned to its method's text; the schedules over depth 1-26 and count 0-max (counted drops: `perSet * 6`; once-only: 0-1; laboratory: 0-5); `placed` zero on boss depths; the Forbidden Runes variation; the enum's constants read from source and held to name every mirrored drop -- the guarantees table.
- [x] `shatterfish/codex/.../Tiers.java` -- the literal parsed from `Generator.java` (five by five, whole numbers), rows with their depth ranges (`floorSet = depth / 5` gated to 4), the three rules' lines cited -- the tiers table.
- [x] `shatterfish/codex/.../Rooms.java` -- the class literals of the 19 specials and 12 secrets, held against the five source lists by simple name; per room the `addItemToSpawn(new X(` lines counted and resolved through the file's imports, the `Generator.random` lines as draws; the secrets base array as thousandths; the queue rule -- the rooms table.
- [x] `shatterfish/codex/.../Generate.java` -- the three tables in the map -- the task extended.
- [x] `shatterfish/codex/src/test/.../GuaranteeArithmeticTest.java` -- every schedule entry against the game's method sampled 4,000 times under a seeded generator with `Dungeon.depth` and the counter set (exact where the table says 0 or 1000, within 40 per mille otherwise); the tier rows against `randomArmor/randomWeapon/randomMissile` sampled 20,000 times per floor set; the room lists against the game's public `CRYSTAL_KEY_SPECIALS` and the secrets against `runSecrets` after `initForRun` -- the arithmetic held.
- [x] `shatterfish/codex/src/test/.../CodexLeakTest.java`, `CodexCompletenessTest.java` -- the live Run's counters bumped and held; every citation resolved; every room class the game declares under the two packages is in the table -- NFR-1.
- [x] `codex/v4.0.0/` regenerated; `docs/adr/0017-...md` amendment; `docs/codex/index.md`; `docs/glossary.md` -- NFR-6.

**Acceptance Criteria:**
- Given the seven limited-drop methods, when the table is read, then each carries its text, its citation and a schedule whose every entry the game's own method reproduces under a sweep of depths and counters (`GuaranteeArithmeticTest`), with the Forbidden Runes variation and the boss floors placed at zero.
- Given the tier literal, when the table is read, then each floor set carries its five weights and its depth range, and the game's draws agree with them (`GuaranteeArithmeticTest`).
- Given the special and secret rooms, when the table is read, then every room the game lists is an entry with the items it adds to the floor and their counts, cited (`CodexCompletenessTest`, `CodexLeakTest`).
- Given a live Run with its counters moved, when a generation runs, then the counters and the bytes are unchanged (`CodexLeakTest`, `CodexSeedFreeTest`).

## Spec Change Log

- 2026-09-16, after the review: `Guarantees` carries `placements` (the level class of every
  depth of the main branch and whether it empties the floor's spawn list) and the gate's text,
  since the placed column was wrong at the amulet floor; each drop's placement block is pinned
  as the deciding method is. `Spawn` carries `floorDrop` and `conditional`; `Tiers` carries the
  tier arrays; `Rooms.secretsPerRegionPerMille` is `baseSecretsPerRegionThousandths`, which is
  what it always was. The arithmetic test's band is four standard deviations of the sample size,
  not a flat forty-five at 2,000 samples.
- 2026-09-16, during implementation: `RoomEntry` carries no membership flags; the game's lists
  are a `RoomList` each (`EQUIP_SPECIALS`, `CONSUMABLE_SPECIALS`, `CRYSTAL_KEY_SPECIALS`,
  `POTION_SPAWN_ROOMS`, `LABORATORY`, `ALL_SECRETS`) with their citations, and the entry is the
  class, its spawns, its draws and its declaration. `Guarantees` also carries every counter the
  game keeps (`LimitedDrops`, 29 at this tag) and the Forbidden Runes rule's text, and the tier
  and room rules are `Rule(what, expression, citation)` records. The rat king's room is a third
  exclusion beside the shop and the demon spawner.

## Design Notes

Micro-brainstorm. The schedule: (a) call the game's methods from the generator under a widened
door setting `Dungeon.depth` and the counters, sampling the draw, refused: sampling gives a
noisy number and the door would reach a counter, which is Run state; (b) read the text only and
leave the arithmetic to the Brain, refused: the story asks for the schedules; (c) a mirror in the
generator pinned to the method's text, chosen: exact thousandths from enumerating the one
uniform draw each method makes, a generation that fails when the pinned source moves, and a
test that holds the mirror to the game by sampling on the test side, where `Dungeon` may be
set. Rooms: the room classes are named as literals (compile-checked, held against the source
lists), their items read from `paint` as text and resolved through imports, since `Class.forName`
is banned. Pre-mortem: a room that adds an item conditionally (a branch in `paint`) would be
counted as unconditional; the reader refuses an `addItemToSpawn` under a control line as the
actions reader does. A method that draws twice would not be enumerable by one uniform; the
mirror names which draw it enumerates and the text pin catches a change.

## Verification

**Commands:**
- `./gradlew :codex:generate -Pshatterfish.mobile=off && git status --short codex/` -- expected: nothing changed after the commit.
- `./gradlew :codex:test :api:test -Pshatterfish.mobile=off` -- expected: green, `GuaranteeArithmeticTest` among them.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- expected: green.

## Dev notes

Implemented on `story/2-4-item-guarantees-tier-tables-and-limited-drops` from `f91085241`. Three
tables joined `:codex:generate`: `guarantees.json` (seven limited drops as the game's method
text and a schedule over depths 1-26 and every counter state, 1,352 states; the 29 counters the
game keeps; the boss depths; the placement gate and the Forbidden Runes rule), `tiers.json`
(the five floor-set rows with the armor, weapon, missile and gate rules) and `rooms.json` (21
specials and 12 secrets with their spawns and draws, the game's six lists, the secrets per
region, the queue rule). The generator gained `Guarantees` (seven mirrors pinned to their
methods' text), `Tiers` and `Rooms`, and `Sources.file`/`text`. The api gained the schedule,
tier and room records and their rendering; Codex version 4. No hook, no upstream file, no
change to `GameContext`, the fair path or the Observation schema.

## Acceptance criteria and how each was met

- **Each limited-drop method carries its text, its citation and a schedule the game's own
  method reproduces over a sweep, with the Forbidden Runes variation and the boss floors at
  zero**: `GuaranteeArithmeticTest` samples every state 2,000 times under a seeded generator
  (exact at 0 and 1000, within 45 per mille otherwise) and holds the placed and Forbidden Runes
  columns by rule; `CodexLeakTest` pins the matrix's states; a moved pin is shown to fail.
- **Each floor set carries its five weights and its depth range, and the game's draws agree**:
  `GuaranteeArithmeticTest` samples `randomArmor`, `randomWeapon` and `randomMissile` 20,000
  times per set and holds the gated last row; `CodexLeakTest` pins rows 1 and 4 and the rules.
- **Every room the game lists is an entry with the items it adds, cited**:
  `CodexCompletenessTest` enumerates the packages' concrete rooms against the table and the
  three exclusions; `GuaranteeArithmeticTest` holds the lists against the game's queues after a
  seeded shuffle and its public crystal-key list; `CodexLeakTest` resolves every citation.
- **A live Run with its counters moved is unchanged by a generation and the bytes equal a cold
  generation's**: `CodexLeakTest`'s live Run moves all 29 counters; `CodexSeedFreeTest`.

## What was built

- `api`: `Codex.ScheduleEntry`, `DropSchedule`, `Guarantees`, `TierRow`, `Rule`, `Tiers`, `Spawn`,
  `Draw`, `RoomEntry`, `RoomList`, `Rooms`; `CodexJson.guarantees`, `tiers`, `rooms`;
  `VERSION = 4`; goldens and refusals in `CodexJsonTest`; the helper allowlist extended.
- `codex`: `Guarantees`, `Tiers`, `Rooms`; `Sources.file` and `Sources.text`; `Generate`
  extended; `GuaranteeArithmeticTest` (four tests); `CodexCompletenessTest` (the rooms);
  `CodexLeakTest` (the counters held, the tables pinned, the citations).
- `codex/v4.0.0/guarantees.json`, `tiers.json`, `rooms.json`, the manifest at version 4.
- ADR-0017's amendment; the Codex index; the glossary (schedule, tier table); an idea.

## What the story found

- **`Random.Int(n)` is zero for `n <= 0`**, so a stone whose floors-left count runs out is
  guaranteed rather than impossible: the enchantment stone by depth 14, the intuition stone and
  the catalyst on floor 4. The mirrors say so and the game's samples agree.
- **The enchantment stone skips floor 10 in its count** (`floorsVisited--` past four), so the
  chance at depth 13 is one in two and at depth 14 one in one.
- **A nested enum is skipped by the source reader's own-line walk**, since it is another type;
  `LimitedDrops` is found by `declaration`, not `find`.
- **The special queue is never seeded by a rule**: `initForRun` shuffles, and `createRoom` draws
  the front at six, three and one; the same rule for the secrets, held equal.
- **The pit drops its crystal key on the remains rather than spawning it**, so its entry spawns
  nothing and its draws are four; the laboratory is placed by `labRoomNeeded`, not the queue.
- **The rat king's room is a secret room the sewer boss level places**; it, the shop and the
  demon spawner are the three rooms outside the queues.
- **The amulet floor decides and places nothing.** Depth 26 is `LastLevel`, which is not a
  regular level; it runs the gate in `Level.create`, moves the counters and never empties the
  floor's spawn list, since only `RegularLevel.createItems` does. The first table said it placed
  everything it needed. The placed column is now the level class's own answer, read from the
  game's `newLevel` switch and its `createItems` overrides (the fairness, adversarial and
  edge-case reviews).
- **A boss floor is a regular level by type.** `SewerBossLevel extends SewerLevel`, so the type
  alone says it empties the spawn list; it declares its own `createItems`, which places its bones
  and its reward and never the list. The predicate walks the overrides, not the hierarchy.
- **Rooms drop items on their own cells under conditions.** A honeypot at a coin, gold in a loop,
  the chasm room's four golden keys, the artillery room's double bomb: refusing every conditional
  placement would have failed the generation, and counting them as guarantees would have lied.
  They are carried and marked, and only an item added to the level's *spawn list* under a
  condition is refused (the adversarial and edge-case reviews).
- **The secret library and laboratory draw by chances and make by class.** They never name
  `Generator.random`, so the first table said they drew nothing; the reader now also reads
  `Random.chances` and `Reflection.newInstance`, and reads each draw as a whole statement, since
  six of them continue on later lines (the adversarial and edge-case reviews).
- **A flat tolerance cannot separate one in seven from one in eight.** Eighteen thousandths apart,
  against a band of forty-five: the sampled hold could not have caught a mirror wrong by one
  outcome at one state. The band is four standard deviations of the sample size now, and the
  samples are eight thousand (the fairness and adversarial reviews).

## Decisions taken inside the story

- **Mirrors pinned to the source text** rather than a door to the counters or a sampled table:
  exact thousandths, a loud failure when the source moves, the game's own method as the judge
  on the test side.
- **`Dungeon` as a file**, `Sources.file`, since the gate bans the class and its nested enum.
- **Rooms as class literals held against the source lists**, with the items resolved through
  imports; a conditional add refused rather than counted.
- **Prizes as cited draws, not odds**; the odds are an idea.

## Evidence

- `:api:test` green, 345 tests, with `CodexJsonTest` (11); `:codex:test` green, 48 tests
  (`GuaranteeArithmeticTest` 5, `CodexSeedFreeTest` 3, `CodexLeakTest` 9, `CodexCompletenessTest` 10,
  `RoomsReaderTest` 5, `ItemsReaderTest` 5, `SourcesTest` 5, `RotationTest` 3, `NamesTest` 3).
- `./gradlew :codex:generate` twice: `git status --short codex/` empty after the commit.
- Mutation battery, thirteen mutations of the generator's classes, each run against the codex
  tests:
    - M1 a mirror pin does not bite: caught by GuaranteeArithmeticTest.
    - M2 the strength potions' target ignores the pairing of floors: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M3 the upgrade scrolls' floors left are one short: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M4 a boss floor places what is needed: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M5 the Forbidden Runes rule withholds the odd scroll: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M6 the enchantment stone's floor-10 skip is dropped: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M7 the last tier row is not widened past its set: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M8 every tier weight is one more than the literal: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M9 a spawn under a condition is counted: **survived**, as expected: no room adds an item under a condition at this tag, so the refusal has nothing to refuse.
    - M10 a room's repeated spawn counts once: caught by CodexLeakTest, CodexSeedFreeTest.
    - M11 a room is dropped from the literals: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M12 the secrets per region are read as whole numbers: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M13 the counters are cut short at the first: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.

- The battery rerun after the review patch, twenty-three mutations, ten of them of the review's
  own fixes (a floor that places nothing, a level class with its own item placement, the amulet
  floor, a placement block's pin, a placement outside the gate, a conditional drop unmarked, a
  braceless if, a draw made by class, a block comment kept, a list read past its parentheses):
    - M1 a mirror pin does not bite: caught by GuaranteeArithmeticTest.
    - M2 the strength potions' target ignores the pairing of floors: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M3 the upgrade scrolls' floors left are one short: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M4 every floor places what is needed: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M5 the Forbidden Runes rule withholds the odd scroll: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M6 the enchantment stone's floor-10 skip is dropped: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M7 the last tier row is not widened past its set: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M8 every tier weight is one more than the literal: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M9 a spawn-list item under a condition is counted: caught by RoomsReaderTest.
    - M10 a room's repeated item counts once: caught by CodexLeakTest, CodexSeedFreeTest, RoomsReaderTest.
    - M11 a room is dropped from the literals: caught by CodexCompletenessTest, CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest, RoomsReaderTest.
    - M12 the secrets per region are read as whole numbers: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M13 the counters are cut short at the first: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M14 a level class that declares its own createItems is taken to place the spawn list: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M15 the amulet floor is taken to place the spawn list: caught by CodexLeakTest, CodexSeedFreeTest, GuaranteeArithmeticTest.
    - M16 a placement block pin does not bite: **survived**, as expected: nothing at this tag drives it.
    - M17 a placement outside the gate is accepted: **survived**, as expected: nothing at this tag drives it.
    - M18 a drop under a condition is not marked: caught by CodexLeakTest, CodexSeedFreeTest, RoomsReaderTest.
    - M19 a braceless if governs nothing in a room: caught by RoomsReaderTest.
    - M20 a draw made by class is not read: caught by CodexLeakTest, CodexSeedFreeTest, RoomsReaderTest.
    - M21 a block comment is not stripped from a pinned text: **survived**, as expected: nothing at this tag drives it.
    - M22 the tier gate is checked in the armor draw only: **survived**, as expected: nothing at this tag drives it.
    - M23 a list literal is read past its own parentheses: caught by RoomsReaderTest.

## Deviations

- None from the spec's tasks; the record shapes changed as the Spec Change Log says.

## Known limitations, handed forward

- **The schedules are mirrors.** The generator computes what the game computes, pinned to the
  text and held by sampling; a tag that rewrites a method fails the generation until the
  mirror is rewritten and re-pinned.
- **The schedule enumerates states no Run can reach** (a counter at 4 on floor 1). A reachability
  mark is an idea; every state the table carries is the game's answer for that state.
- **A branch is not a column.** The gate is the main branch's, and the table's placed column is
  the main branch's; a mining or vault floor places none of these, and the gate's text says so.
- **The Forbidden Runes column is held by rule, not by a level built**: the rule is one line of
  `Level.create`, cited, and the test re-derives it.
- **Room prizes are text**; their odds and the shop's stock are an idea.
- **A room's floor drops are counted as statements**, not as items a floor gets, wherever a
  condition governs them; the entry marks which.
- **The rules that consume the room lists** (one crystal-key room and one potion room per floor,
  the interleave, the pit's rule, the weak floor before a boss) are not in the table; story 2.6
  owns the level's composition.
- **The food per floor** (one draw, a second on a large floor) is not a schedule; it is
  unconditional and cited nowhere yet.

## Follow-ups for later stories

- 2.5: the combat tables; the tier rows here feed what a floor's equipment is.
- 2.6: the traps, recipes, levels and rooms' layouts.
- 2.9: the drift check in CI and the generated index page listing these tables.

## Review

Four reviewers on `git diff main...HEAD` from the committed state: the fairness reviewer (eight
findings, none blocking), the adversarial lens (twenty-three), the edge-case hunter (sixteen)
and the verification-gap lens (nine). One patch commit, `19b4f8d19`.

**Taken.**

- The placed column from the level class of each depth, read from the game's own switch and its
  `createItems` overrides; the amulet floor and the boss floors place nothing; the placements
  carried in the table; the gate's text carried and every placement held to lie inside it
  (fairness 1, 2; adversarial 1, 2, 3; edge 1, 2; gap 4).
- Each drop's placement block pinned as the deciding method is, so the Forbidden Runes column is
  the source's; the test asks the game's own condition with the counter where the placement
  leaves it (fairness 1; adversarial 4; gap 1).
- The room reader: whole statements, the painting's own brace level, a braceless if, a helper
  method, two items in one statement, a nested class name, a shape it cannot name, a list read
  between its own parentheses; conditional floor drops carried and marked; the pit's key visible;
  draws widened to chances and by-class and read whole (adversarial 8, 9, 10, 11, 17; edge 3, 4,
  5, 9, 10, 14; gap 2).
- `RoomsReaderTest` on synthetic text, since the pinned tree writes only some of those shapes
  (gap 2, 3).
- The sampled band by sample size, eight thousand samples, the tiers drawn from the default decks
  and the room queues restored (fairness 2, 3; adversarial 5, 18; edge 15).
- The record invariants: the grid covered, a placed column all or nothing, a spawn-list item never
  conditional, a family one pool, a tier array once (adversarial 7; edge 12).
- The tier gate held in all three draws and the tier arrays cited; block comments stripped from a
  pinned text; the counters refused rather than dropped when their shape changes (fairness 5;
  adversarial 14, 15, 16, 19; edge 11, 13).
- The names: `baseSecretsPerRegionThousandths` says what it is (adversarial 13).
- The leak test puts the counters back in a finally; the documents and the story's numbers
  corrected (adversarial 23; edge 16; gap 5, 6, 9).

**Not taken, with reasons.**

- A reachability mark on the schedule's states (adversarial 22): every state carries the game's
  answer for that state, which is what a Brain asks; which states a Run can reach is its own
  arithmetic, recorded as an idea and a limitation.
- The rules that consume the room lists, the interleave and the per-floor limits (adversarial 12;
  edge 6, 8): story 2.6 owns the level's composition; the queue rule and the lists are this
  story's boundary.
- A branch column (edge 2): the gate is the main branch's and the table says so; a branch level's
  own guarantees are its own table when a story needs them.
- The placement lookup's shape and the rendering's helpers (adversarial 20, 21): cosmetic, and the
  duplication is one read of a small file.

## Suggested review order

1. [`Guarantees.java`](https://github.com/watchthelight/shatterfish/blob/19b4f8d19/shatterfish/codex/src/main/java/org/shatterfish/codex/Guarantees.java),
   the mirrors, their pins, the placements and the placed column.
2. [`GuaranteeArithmeticTest.java`](https://github.com/watchthelight/shatterfish/blob/19b4f8d19/shatterfish/codex/src/test/java/org/shatterfish/codex/GuaranteeArithmeticTest.java),
   the sampled hold, the band, the placements and the Forbidden Runes question.
3. [`Rooms.java`](https://github.com/watchthelight/shatterfish/blob/19b4f8d19/shatterfish/codex/src/main/java/org/shatterfish/codex/Rooms.java)
   and [`RoomsReaderTest.java`](https://github.com/watchthelight/shatterfish/blob/19b4f8d19/shatterfish/codex/src/test/java/org/shatterfish/codex/RoomsReaderTest.java),
   what a room puts on the floor and what the reader refuses.
4. [`Tiers.java`](https://github.com/watchthelight/shatterfish/blob/19b4f8d19/shatterfish/codex/src/main/java/org/shatterfish/codex/Tiers.java),
   the literal, the gate in all three draws, the arrays.
5. [`Codex.java`](https://github.com/watchthelight/shatterfish/blob/19b4f8d19/shatterfish/api/src/main/java/org/shatterfish/api/Codex.java),
   the records and their invariants.
6. [`CodexLeakTest.java`](https://github.com/watchthelight/shatterfish/blob/19b4f8d19/shatterfish/codex/src/test/java/org/shatterfish/codex/CodexLeakTest.java)
   and [`CodexCompletenessTest.java`](https://github.com/watchthelight/shatterfish/blob/19b4f8d19/shatterfish/codex/src/test/java/org/shatterfish/codex/CodexCompletenessTest.java),
   the live Run's counters and the rooms enumerated.
7. [`0017-codex-generation-and-citations.md`](https://github.com/watchthelight/shatterfish/blob/19b4f8d19/docs/adr/0017-codex-generation-and-citations.md),
   the amendment.
