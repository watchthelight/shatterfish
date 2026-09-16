---
story: 2.4
key: 2-4-item-guarantees-tier-tables-and-limited-drops
title: "Item guarantees, tier tables and limited drops"
epic: 2
issue: 38
type: 'feature'
status: 'in-progress'
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
- [ ] `shatterfish/api/.../Codex.java`, `CodexJson.java` -- `VERSION = 4`; records `ScheduleEntry(depth, count, neededPerMille, placedPerMille, placedNoScrollsPerMille)`, `DropSchedule(name, item, method, once, perSet, expression, citation, placementCitation, entries)`, `Guarantees(bossDepths, bossCitation, placementCitation, drops)`, `TierRow(floorSet, depthFrom, depthTo, weights)`, `TierRule(what, expression, citation)`, `Tiers(rows, citation, armor, weapon, missile)`, `Spawn(className, count, citation)`, `RoomEntry(className, secret, equipment, consumable, crystalKey, potionSpawn, spawns, draws, citation)`, `Rooms(specials, secrets, secretsPerRegionPerMille, secretsCitation, queueExpression, queueCitation)`; rendering one entry per line; `JsonRenderingTest.HELPERS` -- api-typed tables.
- [ ] `shatterfish/api/src/test/.../CodexJsonTest.java` -- goldens for a schedule, a tier row, a room; refusals (a per-mille out of range, a depth outside 1-26, a row not five wide, a room listed twice) -- the text held.
- [ ] `shatterfish/codex/.../Sources.java` -- `file(root, path)`; `Mirror` helper: the pinned normalised text of a method against its source -- the drift guard.
- [ ] `shatterfish/codex/.../Guarantees.java` -- the seven mirrors, each pinned to its method's text; the schedules over depth 1-26 and count 0-max (counted drops: `perSet * 6`; once-only: 0-1; laboratory: 0-5); `placed` zero on boss depths; the Forbidden Runes variation; the enum's constants read from source and held to name every mirrored drop -- the guarantees table.
- [ ] `shatterfish/codex/.../Tiers.java` -- the literal parsed from `Generator.java` (five by five, whole numbers), rows with their depth ranges (`floorSet = depth / 5` gated to 4), the three rules' lines cited -- the tiers table.
- [ ] `shatterfish/codex/.../Rooms.java` -- the class literals of the 19 specials and 12 secrets, held against the five source lists by simple name; per room the `addItemToSpawn(new X(` lines counted and resolved through the file's imports, the `Generator.random` lines as draws; the secrets base array as thousandths; the queue rule -- the rooms table.
- [ ] `shatterfish/codex/.../Generate.java` -- the three tables in the map -- the task extended.
- [ ] `shatterfish/codex/src/test/.../GuaranteeArithmeticTest.java` -- every schedule entry against the game's method sampled 4,000 times under a seeded generator with `Dungeon.depth` and the counter set (exact where the table says 0 or 1000, within 40 per mille otherwise); the tier rows against `randomArmor/randomWeapon/randomMissile` sampled 20,000 times per floor set; the room lists against the game's public `CRYSTAL_KEY_SPECIALS` and the secrets against `runSecrets` after `initForRun` -- the arithmetic held.
- [ ] `shatterfish/codex/src/test/.../CodexLeakTest.java`, `CodexCompletenessTest.java` -- the live Run's counters bumped and held; every citation resolved; every room class the game declares under the two packages is in the table -- NFR-1.
- [ ] `codex/v4.0.0/` regenerated; `docs/adr/0017-...md` amendment; `docs/codex/index.md`; `docs/glossary.md` -- NFR-6.

**Acceptance Criteria:**
- Given the seven limited-drop methods, when the table is read, then each carries its text, its citation and a schedule whose every entry the game's own method reproduces under a sweep of depths and counters (`GuaranteeArithmeticTest`), with the Forbidden Runes variation and the boss floors placed at zero.
- Given the tier literal, when the table is read, then each floor set carries its five weights and its depth range, and the game's draws agree with them (`GuaranteeArithmeticTest`).
- Given the special and secret rooms, when the table is read, then every room the game lists is an entry with the items it adds to the floor and their counts, cited (`CodexCompletenessTest`, `CodexLeakTest`).
- Given a live Run with its counters moved, when a generation runs, then the counters and the bytes are unchanged (`CodexLeakTest`, `CodexSeedFreeTest`).

## Spec Change Log

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
