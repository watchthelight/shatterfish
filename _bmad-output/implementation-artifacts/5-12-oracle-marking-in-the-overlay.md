---
title: 'Story 5.12: Oracle marking in the Overlay'
type: 'feature'
created: '2026-10-25'
status: 'review'
baseline_commit: '692646e5b'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** Oracle mode lets the bot see what a player could not. The Overlay marks it in the window's
title and the Mode strip only, and fullscreen hides the title; a debugging Run can be mistaken for a real
one, and the oracle's facts are visible nowhere.

**Approach:** Mark it unmistakably (FR-43, UX-DR11, non-negotiable 1): a red border around the whole game
view, the ORACLE label already in the Mode strip, oracle rows prefixed in the Belief summary, and the
unseen enemies outlined on the map in the oracle colour. Nothing in the Overlay can turn the mode on or
off at runtime, the oracle colour appears nowhere else, and the Run log header records the Run as an
oracle Run.

## Acceptance Criteria (from epics.md Story 5.12, with the tests that hold them)

1. **A red border surrounds the game view.** Test: `OracleMarkingTest.the_border`.
2. **The Mode strip carries an ORACLE label.** Story 5.2: `PanelHudTest.the_oracle_label`.
3. **Oracle rows in the Belief summary are prefixed.** Tests: `OracleMarkingTest.the_belief_summary_rows`,
   `.marks_are_prefixed_rows_and_unseen_cells`, `.rows_are_capped_cells_are_not`.
4. **Unseen enemies are outlined on the map in the oracle colour.** Tests:
   `OracleMarkingTest.the_unseen_enemies_are_outlined`, `.the_sidecar_answers_for_its_own_screen`.
5. **Nothing in the Overlay can turn oracle mode on or off at runtime.** Test:
   `OracleMarkingTest.it_cannot_be_toggled` (a second statement that differs is refused; ArchUnit holds
   that only the game's `create()` states it).
6. **The oracle colour appears nowhere else in the instrument.** Test:
   `OracleMarkingTest.the_colour_is_confined` (one literal, three users of the constant, none of the
   Decision highlights' colours equal to it).
7. **The Run log header records that the Run was an oracle Run.** Story 5.1: `OracleToggleTest` (header
   bit, window title and Observations all say oracle when the flag is on and none does when off).
8. **Off by default and absent from a fair Run (non-negotiable 1).** Test:
   `OracleMarkingTest.a_fair_run_has_none`; the existing `OverlayOracleGateTest` (only the launcher makes an
   oracle observer; the Overlay never depends on the Rig) and the Rig's `RigOracleGateTest` (ranked Runs
   refuse the flag by name) are unchanged and still hold.

</frozen-after-approval>

## Decisions

The owner is away and delegated; each is recorded in the PR body too.

- **Builds on story 5.10 (PR #231, not yet merged).** The branch starts at `origin/story/5-10-map-highlights`
  with `origin/main` merged in, because the issue says 5.10 deferred "oracle outlines of unseen enemies" here
  and 5.12 wants its slotting approach for the map layer. The oracle layer reuses `MapHighlights.CELL` only;
  it is a separate class and not a new `Role`, so 5.10's role/colour tests and `Cells` record are untouched.
  Merge 5.10 first.
- **How the Overlay gets the oracle's facts.** `OracleObserver.observe()` returns the Observation and the
  `OracleView` together. The launcher's oracle door (the only place an `OracleObserver` is constructed,
  `OverlayOracleGateTest`) now leaves the view in an `OracleSidecar` and returns the Observation alone to the
  Brain, exactly as before. The sidecar is created only when `--oracle` is given; `OverlayGame` refuses a
  sidecar without the flag or the flag without a sidecar. The view is read on the render thread (the UI
  role), at each Input wait, and consumed on the same thread in the frame the Panel draws.
- **Hidden facts never leave as their own type.** `OracleMarkContent` turns the view into plain strings and
  cell numbers; only the sidecar, that class and the launcher name `OracleView` in the Overlay (ArchUnit).
  The Snapshot, Decision, Run log and Brain input cannot hold it (`OracleGateTest`, unchanged).
- **Marks only for the screen they were read at.** The sidecar keeps the view beside its Observation and
  answers only for the same screen (same instance, or same header, map, actors and hero), so a floor change
  or a turn on shows nothing rather than a stale enemy. The Run may therefore show the marks of the last
  Input wait while it plays on; they go the frame the next wait lands.
- **Which rows.** The Belief summary's own unknown items (three at most) are matched to the oracle's
  identities by appearance (`turquoise potion = potion of healing`), then the unseen enemies by name and
  `x,y` (`gnoll scout at 19,7, unseen`), four rows at most and then `+N more unseen`, so the Panel keeps its
  room. Every enemy is still outlined. "Unseen" is the oracle's own `Presence.seen`: not drawn as an actor by
  the fair Observer, which includes mimics drawn as chests.
- **Words, not the colour, mark the rows.** The oracle colour is DESIGN.md's `#FF2020`, "reserved for the
  oracle border and label"; the map outline is the one other use DESIGN.md names. The rows are in the
  ordinary ink with the `oracle:` prefix (UX-DR14: colour never carries meaning alone).
- **Border: 2 UI px, UI camera, last in the scene.** Four `ColorBlock`s, re-brought to the front each frame it
  is not last, so the game's windows and fade never cover it ("drawn above everything"). It is made only
  when the dock is told the Run is an oracle Run.
- **Outlines: a double frame, in front of the fog.** An unseen enemy stands in the fog, so a layer behind it
  (like 5.10's) would never show it. The layer is slotted directly behind everything after `FogOfWar`
  (found by class name, as the dock finds the fader) with `Group.bringToFront` only; no upstream file is
  edited and no hook is needed. It is edges only (1 px, an outer and an inner frame) so a sprite's body
  shows; the shape differs from the Decision outlines so colour is not alone.
- **Fixed for the Run.** `PanelDock.oracle` is said once, by `OverlayGame.create()` from the launcher's
  flag; a later call with a different value throws. There is no control, hotkey or setting for it, and 5.11
  (hotkeys) is not touched.
- **Every Mode.** The rows and outlines show in HUMAN mode as well: the mode is a debugging one, and the
  border and label are always on. (The Decision highlights are the ones that never show in HUMAN mode.)

## Deferred

- Hidden traps, secret doors and the seed are in the oracle's view and are not marked; UX-DR11 names true
  identities and unseen enemies only.
- A mark for the oracle's identities the Belief summary is not showing (it shows three items at most).
- The marks are refreshed at each Input wait, not each frame; a per-frame read would be a mid-turn read of
  game state the render thread has no reason to make.
- A screenshot of an oracle launch for the PR was not taken (the game window cannot be captured from
  outside the process on this platform; `--screenshot` exists for it). The drawing is held by tests on the
  scene's members and blocks.
