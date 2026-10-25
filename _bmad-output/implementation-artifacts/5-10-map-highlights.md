---
title: 'Story 5.10: Map highlights'
type: 'feature'
created: '2026-10-25'
status: 'review'
baseline_commit: 'ad05a5a58'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** The Panel says what the Brain will do in words and a compass direction; the human has to
find the cell on the map themselves.

**Approach:** Outline the Decision on the dungeon (FR-41, UX-DR10): the planned path, the target cell and
the considered cells, in DESIGN.md's colours (Chosen `#FFB347`, Danger `#FF5555`, Alternative `#7FB8FF`),
never filled, never over a sprite. The cells are the Decision record's: the wait record's `highlights`
(path then target, ADR-0011) and the cells the Decision's alternatives point at.

## Acceptance Criteria (from epics.md Story 5.10, with the tests that hold them)

1. **Outlined in their documented colours, never filled and never over sprites.**
   Tests: `MapHighlightTest.outlines_never_fills`, `.layer_is_behind_the_sprites`.
2. **Drawn when the Decision is made and cleared when the hero acts or the plan changes.**
   Test: `MapHighlightTest.cleared_when_the_hero_acts_or_the_plan_changes`.
3. **Never drawn in HUMAN mode.** Test: `MapHighlightTest.nothing_in_human_mode`.
4. **The cells come from the Decision record, not from a second computation.**
   Tests: `MapHighlightTest.roles_come_from_the_record`.
5. **`MapHighlightTest` asserts the drawn cells equal the Decision's highlight cells and that none is
   drawn in HUMAN mode.** Tests: `MapHighlightTest.drawn_cells_equal_the_decisions`, `.nothing_in_human_mode`.

</frozen-after-approval>

## Decisions

The owner is away and delegated; each is recorded in the PR body too.

- **Roles from the record, no new log field.** `Wait.highlights` is a flat cell list, and changing the
  chained wait text would orphan every log (ADR-0011). So the order is the contract, now written on
  `Deliberator.lastHighlights`: the path's cells in walking order, then the target last. Every Policy lists
  its target alone today, so every highlight is a target until a Policy plans a path. Considered cells are
  read from `Decision.alternatives` (the cells their Actions point at; `MapHighlightContent.cellOf` is an
  exhaustive switch, as `ActionText` and the Brain's `Highlights` are). A cell is outlined once, in the
  strongest role: target, then path, then considered.
- **"Drawn when the Decision is made, cleared when the hero acts" means the pending Decision.** A Decision
  is a plan only until its Action runs. A held Run (the Overlay's default, PAUSED with Next Step) shows the
  landed, unexecuted Decision (`Snapshot.awaiting`, story 5.7), and that is when the outline is drawn; the
  frame the hero acts, or a new Decision lands, it goes. A Run playing on without holds executes the
  Decision in the same step it is made, so nothing is drawn for it: there is no plan left to show.
  `EmbeddedRun.Snapshot` gained `highlights`, read from `Deliberator.lastHighlights()` at the point the
  Decision lands, beside the Decision and Belief summary it already carries for the same wait.
- **Native and behind the sprites.** `MapHighlights` is a `Group` of the game's `ColorBlock`s on the world
  camera, four thin edges to a cell. It is slotted into the play scene directly in front of the terrain group
  (found as the first member holding a `DungeonTilemap`), so items, characters, wall tops, fog and the HUD
  all draw over it. The one-time reorder uses `Group.bringToFront` only, so no upstream file is edited and
  no hook is needed.
- **Colour never alone (UX-DR14).** The target is a 2 px outline, the path 1 px, and a considered cell four
  corner brackets, so the three roles differ by shape as well as by colour.
- **HUMAN mode.** The dock checks the Mode it already computes for the Panel (`MapHighlightContent.shown`),
  so a HUMAN Run, a hand-back's notice-only state, and no Run all draw nothing. The Panel class is not
  touched; `PanelDock.frame` owns the layer.
- **Information parity.** Nothing is computed on the map: no path search, no tile lookup, no read of
  `Dungeon`. The width that turns a cell into a position is the Observation's own `map().width()`. A cell
  the record carries was chosen from the Observation, so none outside what the Brain could see appears.

## Deferred

- ADR-0011's `highlights` row says the field holds "the planned path, target and considered cells". The
  Brain writes only the target, and the considered cells are already in the same record as the
  alternatives, so the Overlay reads them there and a considered cell written into `highlights` by a later
  Policy would be drawn as path. The contract on `Deliberator.lastHighlights` (path, then target) is what a
  Policy that plans a path must follow; the ADR row is left as written and this note is the reconciliation.

- Oracle-only outlines of unseen enemies (UX-DR11) are story 5.12's.
- A Policy that plans a path and lists it in `highlights`: none does yet (`Highlights.of` returns the target
  only), so no path is drawn until one does. The drawing and the contract are here.
- Drawing for a Run that plays without holds: nothing to show once the Action has run.
