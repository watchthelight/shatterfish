---
story: 1.17
key: 1-17-the-differential-and-toggle-tests
title: "The differential and toggle tests"
epic: 1
issue: 30
type: 'feature'
status: 'done'
created: '2026-09-16'
updated: '2026-09-16'
review_loop_iteration: 0
baseline_commit: '4dcab0563'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Every leak test so far asks one question of one world: is this hidden thing absent.
Nobody has yet shown the stronger claim non-negotiable 1 makes — that two worlds a player could not
tell apart are one Observation — nor that the three effects which change what a player sees change
the Observation by exactly that much and no more. Until both are tests, the fairness claim is a
promise (FR-9, FR-10).

**Approach:** Two suites in `harness`. A differential test takes one Run at its first wait and, for
each kind of hidden state, reads the Observation, changes the hidden state to something a player
could not tell from the first, reads again, and holds the bytes identical — with a control showing
the hidden state really changed and that the same change in the open flips the bytes. A toggle test
applies blindness, mind vision and magic mapping through the game's own effect and observe, and
holds the Observation's difference from the untoggled read to exactly the sections and cells the
screen changes, blindness closing the field of view to the three-by-three block the game computes.
A deliberate break of the Observer, one per pair, is run once through the mutation battery and
recorded.

## Boundaries & Constraints

**Always:** The tests read the game through the driver and the Observer only. Hidden state is set
through the game's own methods where one exists (`Level.setTrap`, `Level.set`, `Level.discover`,
`Buff.affect`, the scroll's `execute`) and by reflection only where the game has none (the potion,
scroll and ring handlers' labels). Every claim about the screen cites `path:line` at `v4.0.0`. Each
suite claims its ADR-0006 rows in `ADR_0006_ROWS`. ADR-0006 and `docs/rules/visibility.md` change
in the same pull request.

**Ask First:** Any change to `Observer` itself. The story expects none: a pair that fails against
the current Observer is a leak, and closing it is a change to the door the review must see as such.
Any new hook row.

**Never:** No recomputation of visibility in a test — the expectation for blindness is the game's
own `heroFOV` after `Dungeon.observe()`, held to the three-by-three block as a check on the
citation, never as a second implementation. No `OracleObserver` (story 1.18). No behavioral form
(E4). No Brain.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Item identity | Same appearance, unknown: class X, then labels permuted so the same appearance is class Y; a potion, a scroll and a ring, in the backpack and on a seen heap | Byte-identical | Control: true names differ; identifying flips the bytes |
| Unseen mob | A mob out of view at cell A, then at cell B, both out of view | Byte-identical | Control: the same mob moved into view is an actor |
| Hidden trap | A hidden trap under `SECRET_TRAP` at A, then at B, both in view; a revealed trap on unknown cell A, then B | Byte-identical | Control: `Level.discover` on the cell yields a `TrapView` |
| Secret door | `SECRET_DOOR` at wall A, then wall B, both in view | Byte-identical | Control: discovered, a `DOOR` tile |
| Generator state | The generator stack pushed elsewhere, draws consumed, `Dungeon.seed` changed between reads | Byte-identical; a read consumes no draw | Control: the draw after a read is the draw predicted without it |
| Blindness | `Blindness` attached, `Dungeon.observe()` | Diff is exactly: fog (`VISIBLE` only inside the 3x3, the rest `VISITED`), actors (only those inside), blobs outside, hero buffs (+Blindness); detached, the original bytes | A `VISIBLE` cell outside the block names itself |
| Mind vision | `MindVision` attached, `Dungeon.observe()` | Diff is exactly: fog gains `VISIBLE` (radius 2 around the hero, 3x3 around every mob but hidden mimics and objects), tiles and heaps newly seen, actors: every mob now in view, hero buffs; detached, fog and actors return, tiles and heaps stay | N/A |
| Magic mapping | The scroll read the game's way, at the next wait | Diff is exactly: fog `UNKNOWN`→`MAPPED` on every discoverable cell, their tiles, traps and transitions now drawn, secrets discovered, inventory minus the scroll, known appearances plus it, log; actors unchanged | N/A |
| A deliberate break | Observer patched to emit a mob out of view, a hidden trap, a true name, a random draw | The named test fails | Recorded in the story file |

</frozen-after-approval>

## Code Map

Read at `v4.0.0`. Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/`
as `…/`.

- `…/levels/Level.java:1318-1319` — sighted iff no `Blindness`, no `Shadows`, alive; `:1370-1372`
  clears the field of view when not; `:1374`, `:1386-1406` copy `discoverable` back within the
  rounded sense radius, 1 when blind — the three-by-three block; `:1377-1382` mind vision raises
  the radius to `MindVision.distance` (2, `…/actors/buffs/MindVision.java:32`); `:1433-1434`,
  `:1457-1468` the 3x3 around every mob but a stealthy neutral mimic or an object; `:1523-1524` a
  heap in view becomes `seen`, and stays so.
- `…/mechanics/ShadowCaster.java:35-45` — the rounding table; `rounding[1][1] == 1`, so the block
  is exactly `PathFinder.NEIGHBOURS9`, clipped to the map.
- `…/Dungeon.java:914-923`, `:925-940` — `observe()`: FOV, then `visited` ORed within the view
  distance and the nine cells around the hero; `:942-957` — with mind vision, the 3x3 around each
  mob is visited too.
- `…/items/scrolls/ScrollOfMagicMapping.java:44-73` — `doRead`: every discoverable cell mapped,
  every secret cell discovered; `…/levels/Level.java:1108-1114` — `discover` sets the terrain and
  reveals the trap; `…/levels/traps/Trap.java:77-91` — `reveal`, `hide`.
- `…/items/ItemStatusHandler.java:38`, `:179-184` — `itemLabels`, private; the label a class draws.
  `…/items/potions/Potion.java:136`, `:201-203` — the handler and the colour an instance takes from
  it on creation. Scroll and ring mirror it.
- `shatterfish/harness/src/test/java/org/shatterfish/harness/observer/MimicDifferentialTest.java` —
  the shape to follow: one Run, bytes before and after, `floorsInView`, `floorOutOfView`.
- `…/observer/FogParityTest.java` — the three effects applied the game's way, and `repaint()`;
  holds the fog to the paint. The toggle test holds the whole Observation's diff, not the paint.
- `…/observer/EnvironmentLeakTest.java:249-263` — the seed and clock pair; the generator pair joins
  it, in the new suite.
- `…/observer/Skeleton.java` — `everything(observer)`, `Serialized`.
- `…/observer/VisibilityChecklistTest.java` — reads `ADR_0006_ROWS` from every suite of the package.
- `shatterfish/harness/src/main/java/org/shatterfish/harness/observer/Observer.java:248`, `:381`,
  `:455` — `map()`, `actors()`, `inventory()`: the reads the battery breaks.
- `docs/rules/visibility.md` rows 11, 14, 16 — blindness, mind vision, mapping; the test column is
  "none yet" and rows 14-15 are `needs-review` since the tag moved.
- `.github/workflows/build.yml:36` — `./gradlew build` is the pull-request gate (NFR-1).

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/harness/src/test/java/org/shatterfish/harness/observer/ObservationDiff.java` --
  a test helper naming the sections, and for the map the cells, in which two Observations differ --
  so a toggle can be held to an exact set rather than to "something changed".
- [x] `…/observer/HiddenStateDifferentialTest.java` -- the five pairs of the matrix, each with its
  control; claims `Items`, `Known appearances`, `Mobs`, `Traps`, `Terrain`, `Seed and turn` --
  FR-9's differential form.
- [x] `…/observer/VisionToggleTest.java` -- the three toggles of the matrix; blindness holds the
  game's `heroFOV` to `NEIGHBOURS9 ∩ discoverable` and the Observation to it; claims
  `Vision buffs`, `Cell visibility` -- FR-10.
- [x] scratchpad `mutations117.py` -- four breaks of `Observer`, each named with the test that
  catches it; run once on a committed tree; the outcome into the story file -- the acceptance's
  "verified once".
- [x] `docs/adr/0006-observer-visibility-rules.md` -- amendment for story 1.17: what each suite
  holds, the memory a toggle leaves behind, and the losses found.
- [x] `docs/rules/visibility.md` -- the test column of rows 11, 14 and 16; rows 14 and 15 re-read at
  `v4.0.0`, re-cited, and tiered by what the code says.

**Acceptance Criteria:**
- Given one Run at its first wait, when each hidden-state pair is read twice, then the bytes are
  identical and each control flips them — `HiddenStateDifferentialTest`, one test per pair.
- Given blindness attached and observed, when the Observation is read, then the game's field of
  view is the three-by-three block and the diff is exactly the expected set —
  `VisionToggleTest.blindness`; likewise `mind_vision` and `magic_mapping`.
- Given the battery, when each of the four breaks is applied, then the named test fails and the
  tree is restored clean — the story file's evidence section.
- Given `VisibilityChecklistTest`, when the suites claim their rows, then every claim names a row
  of the record.
- Given ADR-0002, when a pull request opens, then both suites run under `./gradlew build`.

## Spec Change Log

- **Review, 2026-09-16 (patches, no loopback).** The matrix's magic-mapping row says "actors
  unchanged"; the scroll is read the game's way, which spends a turn, so the row's one reading,
  that mapping reveals no mob, is held as the drawing rule plus "no actor on a merely mapped cell,
  and such a mob exists" rather than as equality. The review's other findings were patches: the
  item pair now puts all three families on heaps and identifies all three; the unseen mob's pair
  covers its health, state, enemy and a buff; the mind-vision check holds both directions of each
  rule; the exit is pinned absent before mapping and the transitions required in the diff; the
  generator pair holds the stack's depth across a read; a mob and a gas placed outside the block
  so blindness loses both; the heaps, traps and transitions under mind vision bound to cells
  newly known; the mind-vision mimic predicate in the game's own words; six citations corrected
  at the tag, two of them outside the story's files.
  KEEP: one Run mutated in place; the diff against an exact set; the blind block as a check on
  the citation, never the expectation.

## Design Notes

**Why one Run mutated in place, not two Runs.** Two seeds give two floors, so two worlds identical
to the player cannot come from two tuples; a bundle edited and reloaded would work and adds a
save/load round trip the question does not need. One Run, read, changed, read, is what
`MimicDifferentialTest` already does, and every other thing on the screen is shared by
construction. The control assertions keep the pair honest: the raw fields must differ, and the same
change in the open must change the bytes, or the pair holds nothing.

**Why a diff against an expected set, not an expected Observation.** Building the toggled
Observation by hand would re-implement the rules the Observer is being held to. The diff names what
changed; the test says what may change and holds the rest still. Memory is part of it: mind vision
detached leaves tiles and heaps the player now remembers, and a test that expected the original
bytes back would be wrong about the screen.

**Pre-mortem.** A pair that is not player-identical (a moved mob changing the danger count, a heap
becoming seen) fails for the right reason and is re-cut, never weakened. Relabelling through the
handler must permute two unknown classes' labels and touch nothing else, or the item's image and
the handler's known set drift. The blind block on a map edge clips; floor one's start is inside
the map, and the test clips anyway.

## Verification

**Commands:**
- `./gradlew :harness:test -Pshatterfish.mobile=off --tests "org.shatterfish.harness.observer.*"` -- expected: green, the two new suites included. (Under Git Bash on Windows the wrapper jar is run directly; same command.)
- `python -I <scratchpad>/mutations117.py --check` then the battery -- expected: every break caught by the named test, tree clean after. The battery lives in the session scratchpad, as every story's has; committing the batteries is deferred work, see `deferred-work.md`.
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- expected: green.

## Dev notes

Implemented on `story/1-17-the-differential-and-toggle-tests` from `4dcab0563`. Two suites and a
helper in `shatterfish/harness/src/test/java/org/shatterfish/harness/observer/`, no change to the
Observer or to any upstream file, no hook row spent. The fairness review follows below.

## Acceptance criteria and how each was met

- **Each hidden-state pair reads twice to the same bytes, and each control flips them.**
  `HiddenStateDifferentialTest`: `unidentified_item_identities`, `unseen_mob_positions`,
  `hidden_trap_placement`, `secret_door_placement`, `generator_state`. Five tests, green.
- **Blinded, the game's field of view is the three-by-three block and the diff is exactly the
  expected set; likewise mind vision and magic mapping.** `VisionToggleTest.blindness`,
  `mind_vision`, `magic_mapping`. Three tests, green.
- **The battery: each break fails the named test, and the tree is restored clean.** Seven breaks,
  each caught; the output is under Evidence.
- **The checklist accepts the claims.** `VisibilityChecklistTest` green with the two suites'
  `ADR_0006_ROWS`.
- **Both suites run on the pull-request gate.** They are harness tests under `./gradlew build`,
  which `.github/workflows/build.yml:36` runs on every pull request.

## What was built

- `ObservationDiff` — names the sections, and for the map the cells, in which two Observations
  differ; leaves the Actions out, since they are a function of the rest.
- `HiddenStateDifferentialTest` — the five pairs of the matrix, each with its control; claims the
  rows Items, Known appearances, Mobs, Traps, Terrain, Seed and turn.
- `VisionToggleTest` — the three toggles of the matrix; claims Vision buffs and Cell visibility.
- ADR-0006's story 1.17 amendment; `docs/rules/visibility.md` rows 11, 14, 15 and 16, with 14 and
  15 re-read and re-cited at `v4.0.0` and their tier set to 1; `docs/fairness.md`'s test table.

## What the story found

- **Blindness writes to the log.** The first draft expected only the fog, the actors, the blobs
  and the buff to move, and the log moved too: the hero logs a buff's message when it is added
  (`Hero.java:2130-2136`; `Buff.java:118-125`). Taken off, the log keeps it, so the Observation
  after is the original but for the log. The test holds both. The draft's word for it, "announced",
  was wrong: that flag is the floating text over the sprite (`Char.java:1234-1246`), and the
  fairness review read the line.
- **Mapping can move a wall from remembered to mapped.** A wall in view whose far side was unknown
  is painted opaque and emitted at the examine level; once the far side is mapped, the wall's face
  is painted mapped, since the fog paints a wall by the cells beyond it (`FogOfWar.java:210-267`).
  Less is shown, not more; the test holds it as the one exception to "what changed was unknown".
- **Reading a scroll spends a turn.** The magic-mapping toggle cannot hold the actors to equality
  across the read, so it holds them to the drawing rule and to standing on no merely mapped cell,
  and checks that such a mob exists.
- **The schema refuses two of the breaks before any assertion runs.** An actor or a trap on a cell
  the fog hides is an `IllegalArgumentException` from the record itself (ADR-0005), which is the
  whitelist by construction.

## Decisions taken inside the story

- **One Run mutated in place, not two Runs.** Two seeds give two floors. Same shape as
  `MimicDifferentialTest`; the controls keep each pair honest.
- **A diff against an expected set, not an expected Observation.** Building the toggled
  Observation by hand would re-implement the rules under test.
- **The labels are swapped by reflection.** The game has no method for it; the known set and the
  label set are untouched, so both classes stay unknown and every image resolves.
- **The blind block is a check on the citation, not the oracle.** The game's own array is the
  expectation; the block is what the array is held to, cited.

## Evidence

Local, on this branch:

- `build -Pshatterfish.mobile=off` at `c2f806ff5` (after the review's patches): green, 533 tests
  in 54 suites, no failures; `mkdocs build --strict` green.
- `:harness:test --tests "org.shatterfish.harness.observer.*"`: green, the two new suites
  included, before and after the review.
- The battery, `mutations117.py`, on the committed tree, restored clean after each break:

```
=== M1 a mob is drawn out of view: actors() drops the field-of-view gate
  -> caught by: ActorLeakTest, HiddenStateDifferentialTest, VisionToggleTest
=== M2 a hidden trap is drawn: map() drops trap.visible
  -> caught by: HiddenStateDifferentialTest, MapLeakTest, VisionToggleTest
=== M3 a revealed trap under opaque fog is drawn: map() drops the fog gate on traps
  -> caught by: HiddenStateDifferentialTest, MapLeakTest, VisionToggleTest
=== M4 an item is named by its identity: inventory() emits trueName()
  -> caught by: HiddenStateDifferentialTest, ItemLeakTest, VisionToggleTest
=== M5 a secret is drawn as what it is: map() discovers the terrain before the sheet
  -> caught by: HiddenStateDifferentialTest, MapLeakTest, VisionToggleTest
=== M6 a read draws: hero() consumes a random draw and carries it
  -> caught by: HiddenStateDifferentialTest, ObserveTest, VisionToggleTest
=== M7 a remembered cell is in view: fog() treats visited as seen
  -> caught by: FogParityTest, VisionToggleTest
tree restored and clean
```

  Per pair: M1 fails `unseen_mob_positions` (and every test of both suites, by the record's own
  refusal of an actor on a hidden cell); M2 fails `hidden_trap_placement` (the hidden half); M3
  fails `hidden_trap_placement` (the revealed-under-fog half, and every test of both suites by the
  record's refusal of a trap on a hidden cell); M4 fails `unidentified_item_identities`; M5 fails
  `secret_door_placement`; M6 fails `generator_state` and every other pair, since the name carries
  the draw; M7 fails `blindness` (a cell in view outside the block) and `mind_vision`.

## The fairness review

The `fairness-reviewer` subagent read `git diff main...HEAD` and returned **CHANGES**: no path by
which hidden information reaches an Observation, the Observer and every upstream file untouched,
and eight should-fixes, every one taken. Three were citations wrong at `v4.0.0`: the sprite's
visibility, carried from a `v3.3.8` line in `ActorLeakTest` (now `GameScene.java:1089`,
`:1515-1522`; `Char.java:1322-1324`, and the same stale line fixed in `MimicDifferentialTest`); the
secret door's visual (`DungeonTileSheet.java:470`, with the same stale number in the Observer's
javadoc); and the log line under blindness, which the first draft attributed to the buff's
`announced` flag — that flag is the floating text over the sprite (`Char.java:1234-1246`), and the
line comes from the hero's add (`Hero.java:2130-2136`) through `heroMessage()`
(`Buff.java:118-125`). Two were sets bound loosely: the heaps under mind vision were allowed to move
and only held to a superset, and are now held to exactly the seen ones on known cells; and the
blindness test's blob and actor clauses were vacuous, the record refusing an actor or a blob on a
hidden cell before any assertion, so a mob and a gas are now placed in view outside the block and
held to disappear. The rest: the memory outside the block attributed to the observe at the wait
before (`Dungeon.java:930-938`), ADR-0006's Vision buffs row re-cited at the tag, and the generator
pair's prose cut to what the test does.

Three more reviews ran under the build workflow. The verification-gap reviewer found no gap and
two patches, the exit pinned absent before mapping and the same citation. The blind reviewer's
findings that were patches are in the review commit: the item pair on heaps for all three
families and identified for all three, the unseen mob's health, state, enemy and buff, the
generator stack's depth, the cleared cell compared, the trap's name held absent, the mind-vision
converses, the hero compared by named accessors, the preamble and the mob-sprite row of the
visibility page, the fairness page's other rows. Two of its findings were deferred: the batteries'
home, to `deferred-work.md`, and the helper triplication across the differential suites, rejected
as cosmetic. The edge-case reviewer's findings taken: the game's own mimic predicate in the
mind-vision rules, traps and transitions under mind vision bound to cells newly known, the log's
cap, the generator stack's depth; rejected: a door discovered by the scroll opening the view (a
closed door blocks sight), a mob stepping on a trap during the read (the seed is pinned and a part
that moves names itself), and the other vision buffs (FR-10 names three; recorded as not held).

## Deviations

- The spec's matrix named four breaks; seven were run, adding a revealed trap under opaque fog, a
  secret drawn as what it is, and a remembered cell drawn as in view, one per pair and toggle.
- The matrix's magic-mapping row says "actors unchanged"; the turn the read spends means the
  actors are held to the drawing rule and to standing on no merely mapped cell. Logged in the
  Spec Change Log.
- The spec's Always said "the game's own methods where one exists"; `ScrollOfMagicMapping` is read
  through `execute`, as the spec named, and the turn it spends is why the actors are held to the
  rule. Recorded above and in the ADR.

## Known limitations, handed forward

- **The other vision buffs**, shadows, magical sight, awareness and Light, share the tested code
  paths and are not toggled; FR-10 names three.
- **The behavioural form** of the differential test, a Brain given both worlds deciding alike
  until the Observations diverge, is E4's (FR-9).

## Follow-ups for later stories

- Story 1.18 (#31): oracle mode, gated and marked.
- E4: the behavioural differential, and the permuted-seed form for any learned component.

## Suggested Review Order

**The differential pairs: one Run, read twice per kind of hidden state**

- The entry point: the item pair, the one hidden state the game has no method to set, swapped by the handler's labels
  [`HiddenStateDifferentialTest.java:96`](../../shatterfish/harness/src/test/java/org/shatterfish/harness/observer/HiddenStateDifferentialTest.java#L96)

- How the labels move without touching the known set or the images
  [`HiddenStateDifferentialTest.java:339`](../../shatterfish/harness/src/test/java/org/shatterfish/harness/observer/HiddenStateDifferentialTest.java#L339)

- The unseen mob: position, then health, state, enemy and a buff; the control moves it into view
  [`HiddenStateDifferentialTest.java:171`](../../shatterfish/harness/src/test/java/org/shatterfish/harness/observer/HiddenStateDifferentialTest.java#L171)

- Hidden trap and revealed-under-fog trap, each a world identical to no trap; the cleared cell compared
  [`HiddenStateDifferentialTest.java:201`](../../shatterfish/harness/src/test/java/org/shatterfish/harness/observer/HiddenStateDifferentialTest.java#L201)

- A secret door in either wall, identical to a wall
  [`HiddenStateDifferentialTest.java:247`](../../shatterfish/harness/src/test/java/org/shatterfish/harness/observer/HiddenStateDifferentialTest.java#L247)

- The generator: bytes across a pushed generator and a changed seed; a read draws nothing and leaves the stack as deep
  [`HiddenStateDifferentialTest.java:269`](../../shatterfish/harness/src/test/java/org/shatterfish/harness/observer/HiddenStateDifferentialTest.java#L269)

**The toggles: an exact set of parts, the rest held still**

- What "exact" means: the parts two Observations differ in, by name; the Actions left out on purpose
  [`ObservationDiff.java:33`](../../shatterfish/harness/src/test/java/org/shatterfish/harness/observer/ObservationDiff.java#L33)

- Blindness: the game's array held to the block, the diff to five parts, a mob and a gas lost outside the block
  [`VisionToggleTest.java:103`](../../shatterfish/harness/src/test/java/org/shatterfish/harness/observer/VisionToggleTest.java#L103)

- The block, clipped to the map, as a check on the citation
  [`VisionToggleTest.java:381`](../../shatterfish/harness/src/test/java/org/shatterfish/harness/observer/VisionToggleTest.java#L381)

- Mind vision: both directions of each rule on the array; heaps, traps and ways bound to cells newly known; memory after detach
  [`VisionToggleTest.java:186`](../../shatterfish/harness/src/test/java/org/shatterfish/harness/observer/VisionToggleTest.java#L186)

- The mimic the loop skips, in the game's own words
  [`VisionToggleTest.java:409`](../../shatterfish/harness/src/test/java/org/shatterfish/harness/observer/VisionToggleTest.java#L409)

- Magic mapping read the game's way: mapped, secrets discovered, no mob and no heap on a merely mapped cell
  [`VisionToggleTest.java:274`](../../shatterfish/harness/src/test/java/org/shatterfish/harness/observer/VisionToggleTest.java#L274)

**The record, and the rows the suites claim**

- ADR-0006's story 1.17 amendment: what is held, what the screen did unexpectedly, the battery, what is not held
  [`adr/0006-observer-visibility-rules.md:606`](../../docs/adr/0006-observer-visibility-rules.md#L606)

- The Vision buffs row, re-cited at the tag
  [`adr/0006-observer-visibility-rules.md:72`](../../docs/adr/0006-observer-visibility-rules.md#L72)

- The rows each suite claims, which VisibilityChecklistTest reads
  [`HiddenStateDifferentialTest.java:70`](../../shatterfish/harness/src/test/java/org/shatterfish/harness/observer/HiddenStateDifferentialTest.java#L70)

- The visibility rules: blindness, mind vision, mapped and the mob sprite, tested and re-cited
  [`rules/visibility.md:11`](../../docs/rules/visibility.md#L11)

- The fairness page's test table
  [`fairness.md:38`](../../docs/fairness.md#L38)

