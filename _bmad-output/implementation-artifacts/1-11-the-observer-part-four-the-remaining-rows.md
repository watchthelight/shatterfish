---
story: 1.11
key: 1-11-the-observer-part-four-the-remaining-rows
title: "The Observer, part four: the remaining rows"
epic: 1
issue: 24
status: in-progress
created: '2026-09-11'
updated: '2026-09-11'
review_loop_iteration: 0
baseline_commit: 'e8b5339ab4af52039215ed8c8afd6f40885f88b1'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Observer builds every section but the environment facts of the map: blobs, the
floor feeling and the transitions are empty by design, no test names the danger count or the boss
lock, and `observe()` does not exist, so nothing yet produces a whole Observation. Nothing holds
the whitelist of ADR-0006 to having a test per row, so a row could be added without one.

**Approach:** Fill the three empty parts of `Observer.map()` from the game's own drawing
predicates, add `observe()` over the finished sections with no Actions until story 1.12, and add
the leak tests the rows need plus a checklist test that reads ADR-0006's table and fails when a
row has no test claiming it.

## Boundaries & Constraints

**Always:** every read is a predicate the renderer or the HUD uses, cited `path:line` at `v3.3.8`;
`fairness` label and the `fairness-reviewer` subagent before the PR; the map's records already fix
every order, and `Level.blobs` is a `HashMap`, so nothing may let its iteration order reach the
bytes; the Observer reads and never writes game state.

**Ask First:** any hook, any upstream edit, any schema change (a new field, a new enum member, a
codec version bump) — the reading this story needs has none of the three.

**Never:** a blob's volume; a blob, a transition or anything else on a cell the fog paints opaque;
a Shatterfish table that decides what a blob or a transition looks like; the valid-Action set
(story 1.12); `Dungeon.seed`, `Actor.now()` or wall-clock time.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| A gas in view | `ToxicGas` seeded in the hero's field of view through `GameScene.add` | the cell carries the kind `ToxicGas`, with no volume anywhere in the bytes | N/A |
| The same gas, more of it | the same cells at ten times the volume | the Observation is byte-identical to the first | N/A |
| A gas out of view | the same gas on a remembered cell the hero cannot see | no blob cell | N/A |
| A gas the scene never drew | a blob seeded without `GameScene.add`, so it has no emitter | no blob cell until a scene creation gives it one | N/A |
| An always-visible blob under fog | `SkeletonKey.KeyWall` on a visited cell out of view | no blob cell: a recorded loss, less than the screen shows | N/A |
| The floor feeling | a level whose feeling is `WATER` | the map's feeling is `WATER`, as the depth button's icon draws it | N/A |
| Transitions | depth 1 with the entrance seen and the exit not | the surface transition at its cell, the exit absent until its cell is seen | N/A |
| The boss lock | `Dungeon.level.seal()` | the header's `sealed` is true, and false again after `unseal()` | N/A |
| The danger count | an invisible enemy and a visible one in view | the actors section holds exactly `hero.visibleEnemies()` enemies | N/A |
| A whole Observation | any Input wait | `observe()` returns every section with `ActionsSection.NONE`, twice byte-identical | the gate fails the read outside an Input wait, as every section does |
| A row with no test | a row added to ADR-0006's table | `VisibilityChecklistTest` fails, naming the row | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/harness/.../observer/Observer.java` — `map()` (`:190-246`) returns `List.of()`,
  `Feeling.NONE`, `List.of()` for blobs, feeling and transitions; `fog(level, cell)` (`:703-760`)
  is the painted fog every part of the map is gated on; `header()` (`:168`) already carries
  `Dungeon.level.locked` as `sealed`; `atInputWait()` (`:808`) is the gate.
- `core/.../effects/BlobEmitter.java:47-70` — the draw rule: `volume > 0`, and per cell
  `heroFOV[cell] || blob.alwaysVisible` with `cur[cell] > 0`, one particle whatever the amount.
- `core/.../actors/blobs/Blob.java:38-50` (`volume`, `cur`, `emitter`, `area`, `alwaysVisible`),
  `:143-149` (`setupArea`), `:246-248` (`tileDesc`, null by default), `:250-272`
  (`seed`, which does not register a sprite); `com.watabou.noosa.particles.Emitter:46`, `:82-93`,
  `:116-128` — `on` is true exactly when a factory was given, and `update()` emits only then.
- `core/.../scenes/GameScene.java:343-353` — `gases` is added before `fog`, so the fog is drawn
  over the particles; `:1055-1058` and `:1131-1136` — an emitter is made at scene creation and by
  `GameScene.add(Blob)`, the idiom every caller uses (`GameScene.add(Blob.seed(...))`).
- `core/.../windows/WndInfoCell.java:144-153` — the cell info names a blob in view whose
  `tileDesc()` is not null; `core/.../levels/Level.java:172` (`feeling`), `:177` (`transitions`),
  `:180` (`locked`), `:184` (`blobs`, a `HashMap`), `:617-630` (`seal`/`unseal`).
- `core/.../ui/MenuPane.java:88-89`, `:98-101`, `:107-116` and `core/.../ui/Icons.java:478-497` —
  the depth button draws an icon per feeling, its hover text the description and its window the
  title; `core/.../scenes/GameScene.java:670-689` — the arrival line.
- `core/.../levels/features/LevelTransition.java:34-40` (`Type`), `:42-47` (`type`, `centerCell`),
  `:92-94` (`cell()`) — a transition is a rect with one designated cell.
- `core/.../actors/hero/Hero.java:859` and `:1681-1713`, `:1760-1762`;
  `core/.../ui/DangerIndicator.java:87-104` — the indicator's number is the enemies in the field
  of view, refreshed at the top of every hero act, so it is fresh at a wait.
- `shatterfish/api/.../MapSection.java`, `BlobCell.java`, `Feeling.java`, `TransitionView.java`,
  `TransitionKind.java` — the records and their orders; `Observation.java`, `ActionsSection.NONE`.
- `shatterfish/harness/src/test/.../observer/MapLeakTest.java` — the fixture idiom
  (`HeadlessDriver.start`, `stepToInputWait`, `Skeleton.Serialized`); `Skeleton.java:56-59` —
  `everything(observer)`, which `observe()` replaces; `.../hooks/Ledger.java:138-150` —
  `repoRoot()`, the pattern for a test that reads a doc.
- `docs/adr/0006-observer-visibility-rules.md:65-83` — the whitelist table the checklist parses.

## Tasks & Acceptance

**Execution:**
- [ ] `shatterfish/harness/.../observer/Observer.java` — build the blobs, the feeling and the
      transitions in `map()`, each through the drawing predicate cited above, and add
      `observe()`; a blob cell is emitted only where the painted fog is `VISIBLE`, since the fog
      is drawn over the gases.
- [ ] `shatterfish/api/.../MapSection.java` — correct the always-visible note: three blobs, named.
- [ ] `shatterfish/harness/src/test/.../observer/EnvironmentLeakTest.java` (new) — the blob rows
      of the matrix, the danger count, and the seed-and-turn differential.
- [ ] `shatterfish/harness/src/test/.../observer/FloorSectionTest.java` (new) — the feeling, the
      transitions seen and unseen, and the boss lock.
- [ ] `shatterfish/harness/src/test/.../observer/ObserveTest.java` (new) — `observe()` equals the
      sections read one by one, is byte-identical twice, and fails outside an Input wait.
- [ ] `shatterfish/harness/src/test/.../observer/VisibilityChecklistTest.java` (new) — parse
      ADR-0006's table, union the rows every observer test claims in a declared field, and hold
      the two equal but for a pending set naming the row's issue.
- [ ] `shatterfish/harness/src/test/.../observer/*.java` — each existing leak test declares the
      rows it covers; `Skeleton.everything` delegates to `observe()`.
- [ ] `docs/adr/0006-observer-visibility-rules.md` — three rows in the whitelist table (the floor
      feeling, the transitions, the boss lock) and the story 1.11 amendment with every rule, its
      cites and its losses.
- [ ] `docs/rules/buffs.md`, `visibility.md`, `levels.md`, `ui.md` — Test cells on the four rows
      this story proves, and the new rows for the emitter's lifetime, the feeling's HUD and a
      transition's drawn cells.

**Acceptance Criteria:**
- Given the rows of ADR-0006 not covered by stories 1.8 to 1.10, when the Observer builds them,
  then a blob appears as the set of kinds present in cells the hero can see with no volume, the
  danger count is the number the indicator shows including invisible enemies in view, and the
  sealed flag reflects the boss lock; held by `EnvironmentLeakTest` and `FloorSectionTest`.
- Given a constructed floor, when `EnvironmentLeakTest` runs, then blob volumes and blobs outside
  the field of view are absent, by the records and by a search of the serialized Observation.
- Given ADR-0006's table, when `VisibilityChecklistTest` runs, then every row is claimed by at
  least one leak test or listed as pending with the issue that closes it, and a row claimed by no
  test fails the build.
- Given an Input wait, when `observe()` is called, then every section is the one its own method
  builds, the Actions are `ActionsSection.NONE` until story 1.12, and two reads are byte-identical.

## Design Notes

The blob rule is the emitter's own, read rather than reimplemented: a blob draws when it has an
emitter that was given a factory (`Emitter.on`) and its volume is positive, and it draws on a cell
when `cur[cell] > 0` and the hero sees the cell or the blob is always visible. The Observer walks
the cells with `cur[cell] > 0` instead of the emitter's bounding rectangle, which is the same set
and needs no `setupArea()` call, since the Observer writes nothing; the rectangle always contains
those cells, being unioned at every seed.

```java
for (Blob blob : level.blobs.values()) {
    if (blob.emitter == null || !blob.emitter.on || blob.volume <= 0) continue;   // nothing drawn
    for (int cell = 0; cell < cells; cell++) {
        if (blob.cur[cell] > 0 && (level.heroFOV[cell] || blob.alwaysVisible)
                && fog.get(cell) == Fog.VISIBLE) {                                 // the fog is over the gases
            kinds.computeIfAbsent(cell, c -> new ArrayList<>()).add(blob.getClass().getSimpleName());
        }
    }
}
```

A transition is carried at its `centerCell`, the cell the game itself designates and the cell that
carries the stairs at every site in the tag; the extent of a multi-cell boss exit is a recorded
loss. The danger count is no field of the schema (ADR-0005): it is the enemies among the actors,
and the test holds that count to `hero.visibleEnemies()`.

## Verification

**Commands:**
- `./gradlew build -Pshatterfish.mobile=off` — expected: green, every suite, the new tests among them.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` — expected: clean.
- A mutation battery over `Observer.java` against the four new suites — expected: every mutation caught.
