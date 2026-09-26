---
title: 'Story 5.2: The Panel frame, layout and collapse'
type: 'feature'
created: '2026-09-26'
status: 'review'
baseline_commit: '2134df11e'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 5.1 put a Run inside the real desktop game, but nothing of the Overlay is drawn.
Everything later in epic 5 (the Mode strip's content, the Goal line, the Decision card, the controls)
needs somewhere to live: an instrument docked beside the dungeon that reads as a separate tool, never
covers the game's own HUD, keeps the hero in the uncovered part of the map, and gets out of the way
when the window is too small (UX-DR1, UX-DR2, `DESIGN.md` Layout & Spacing).

**Approach:** A pure layout function computes, from the UI camera's size, the safe insets, the
interface size and the tag side, where the Panel goes, how wide it is, whether it collapses to the
Mode strip, and the horizontal camera offset that centres the hero in the uncovered map. A Panel
component made of the game's own nine-patch frames is added to each play scene and placed by that
function every frame; the offset is re-applied after the game's own layout pass every frame, from the
Overlay's game class, with no upstream edit.

## Acceptance Criteria (from epics.md Story 5.2, with the tests that hold them)

1. **Docking.** The Panel docks at the right edge of the dungeon view, left of the inventory pane
   (or, with no inventory pane, left of the tag column), between the top HUD (the menu pane and the
   boss bar) and the toolbar; it is translucent and never over the game's HUD.
   Tests: `PanelLayoutTest` (the rectangle, over a grid of window sizes, interface sizes, tag sides
   and insets, never intersects any modelled HUD rectangle and stays on screen), `PanelHudTest` (the
   modelled HUD rectangles contain the game's real HUD components, read from a real play scene at
   interface sizes 1 and 2 and several window sizes, and the Panel added to that scene misses them).
2. **Camera offset.** The Overlay applies its own horizontal camera offset, keeping the game's
   vertical one, and re-applies it after the game's layout pass, which resets the offset to
   `(0, y)` (`GameScene.java:993-999`).
   Tests: `PanelCameraTest` (the offset in world units from the layout's UI-pixel offset and the two
   zooms; the vertical component kept), `PanelHudTest.offset_survives_the_layout_pass` (on a real
   play scene: apply, run `GameScene.layoutTags()`, apply again, the horizontal offset is back and the
   vertical one is the game's).
3. **Width and collapse.** Target 200 UI pixels, minimum 160; the Panel collapses to the Mode strip
   when the uncovered map would be narrower than 200 UI pixels or the view shorter than 200, in the
   mobile layout, or when the human collapses it.
   Tests: `PanelLayoutTest` (the width steps from 200 down to 160 and then collapses exactly at the
   documented thresholds; the mobile layout and the human toggle collapse it).
4. **Toolkit.** Only nine-patch frames (`Chrome.Type.TOAST_TR_HEAVY` for the Panel,
   `Chrome.Type.TOAST_TR` for the Mode strip), the game's text renderer and the documented sizes.
   Tests: `OverlayToolkitTest` (ArchUnit: `overlay` imports nothing from Swing, AWT, JavaFX or a
   web-view package; shown to bite on a fixture), `PanelHudTest` (the Panel's frames are the Chrome
   nine-patches).
5. No Rig numbers: the Overlay does not change what the Brain decides (epic 5's rule).

</frozen-after-approval>

## Design notes

### The interface the Overlay plays on

The Run Profile declares the compact interface (`SPDSettings.interfaceSize(0)`,
`harness/.../boot/Profile.java:159-174`) because the full one hands an item selector to the inventory
pane, which no Action can answer (`GameScene.java:1673-1674`). The compact interface is the mobile
layout, where UX-DR2 collapses the Panel, so as built by 5.1 the Panel could never be shown.

- **A. Keep the compact interface.** The Panel is always the Mode strip. Rejected: the story's whole
  point, the docked instrument, never appears on the desktop.
- **B. The full interface (2), the desktop default.** The layout `DESIGN.md` draws. Rejected for now:
  a targeted item Action (reading a scroll onto the armour, story 4.13) opens the inventory pane's
  selector instead of a window, and the executor refuses it, so the Overlay's Brain would play worse
  than the Rig's. Supporting the pane in the executor is its own story (`docs/ideas.md`).
- **C. The mixed interface (1).** Chosen. With interface 1 there is no inventory pane
  (`GameScene.java:547-556`), so selectors stay `WndBag` windows the executor answers, and the layout
  is the desktop one: the large status pane and the toolbar at the bottom (`:487`, `:555`), the large
  boss bar (`BossHealthBar.java:78-80`). The other reads of the setting are layout, the radial menu,
  and tutorial log text (`Guidebook.java:59`, `GameScene.java:760`, `:1317`, `:1353`), which a Run
  already sees differently from a headless one under 5.1's named exception to non-negotiable 5.
  The Overlay declares it after the Profile is prepared; the Rig and the headless driver keep 0.

The layout function supports 2 as well, since `DESIGN.md` draws it and a later story may play on it.

### Where the Panel's re-application of the offset runs

- **A. An upstream hook in `GameScene.layoutTags`.** Rejected: an edit to upstream for something
  reachable from our own subclass.
- **B. A child gizmo of the scene, in its `update()`.** Rejected: `GameScene.update` updates its
  children first and runs `layoutTags` after (`GameScene.java:891`, `:931-958`), so a child would see
  the offset before the game resets it.
- **C. `OverlayGame.update()`, after `super.update()`.** Chosen. `Game.update` runs the scene's update,
  where `layoutTags` runs, then `Camera.updateAll()` (`SPD-classes/.../noosa/Game.java:269-283`), and
  `Game.render` draws before it steps (`:150-171`), so the offset set at the end of one frame's update
  is what the next frame draws. `Camera.setCenterOffset` moves the scroll by the change
  (`Camera.java:241-249`), so putting the horizontal part back after a reset leaves no jump.
  Only `centerOffset.y` is read elsewhere (`GameScene.java:1155`, `:1594`, `PixelScene.java:386`).

### Placement

- **A. Over the right edge, covering the tag column and the space above the inventory pane.**
  Rejected: covers the game's attack, loot, action and resume tags (`GameScene.java:1031-1052`).
- **B. A fixed rectangle per window size, from a table.** Rejected: brittle against insets, the tag
  side and the zoom.
- **C. A function of the HUD's own geometry.** Chosen. The right edge is the inventory pane's left
  (interface 2) or the tag column's (interface 1, tags on the right); the bottom is the toolbar's
  top; the top is below every top HUD element whose columns the Panel's overlap (the menu pane with
  its danger tag, the boss bar). Each HUD rectangle is modelled from the game's layout code, and a
  test holds the model against the real components.

### Collapse thresholds (sources)

- Target width 200, minimum 160, uncovered map at least 200, view at least 200 high: UX-DR2
  (`epics.md:149`) and `DESIGN.md` Layout & Spacing.
- The mobile layout (interface 0) and the human's toggle collapse it: UX-DR2. The toggle's hotkey
  (F11) is story 5.11's; this story gives the Panel the state.
- A full Panel shorter than 50 UI pixels also collapses: its padding (4 and 4), the Mode strip (12),
  a section gap (6) and the Decision log's three lines at the small size 6 with rows 2 apart (24),
  which UX-DR2 requires the log never to go below, cannot fit.

### Pre-mortem

- *The model misses a HUD element and the Panel covers it on a real screen.* `PanelHudTest` reads the
  real components of a real play scene and requires the model to contain them.
- *The game resets the offset and a frame draws with it.* The re-application runs after every update,
  before the next draw; `PanelHudTest` runs the game's own layout pass between applications.
- *Interface 1 changes how a Run plays.* Only selector routing matters to the executor, and it does
  not change without the inventory pane; the rest is layout and tutorial text.
- *The Panel intercepts clicks meant for the dungeon.* It is frames and text only, no pointer areas.

## Tasks

- [x] `PanelLayout`: the pure function, its HUD model and its constants.
- [x] `Panel`: the component (the Panel frame and the empty Mode strip).
- [x] `PanelCamera`: the offset in world units, applied without disturbing the vertical part.
- [x] `OverlayGame`: interface 1, the Panel added to each play scene, placement and offset every frame.
- [x] Tests: `PanelLayoutTest`, `PanelCameraTest`, `PanelHudTest`, `OverlayToolkitTest`.
- [x] A real launch at two window sizes.
- [x] Docs: `docs/architecture.md`, ADR-0013 amendment, `docs/ideas.md`, `docs/rules/ui.md` if a fact
  is added.
- [x] Mutation battery.

## Review

### What was built

- `PanelLayout`: the pure placement function and its model of the game's HUD, with every figure cited.
- `Panel`: the `TOAST_TR_HEAVY` frame and the `TOAST_TR` Mode strip, placed each frame; no pointer area.
- `PanelCamera`: the offset in world units (UI pixels times the UI zoom over the world zoom), set
  without touching the vertical part.
- `PanelDock`: called from `OverlayGame.update()` after the game's update; a new Panel for each play
  scene; logs each change of placement.
- `OverlayGame` declares interface size 1 after the Profile, and writes `fullscreen: false` into the
  Run's settings when `--window` is given.
- Launcher: `--window WxH`, `--screenshot <file>` (the game's own framebuffer through libGDX, alpha
  forced opaque), and `:overlay:launch` now runs from the repository's root, so the default
  `--weights weights/shatterfish.json` resolves (it did not: the task ran in `shatterfish/overlay`, and
  the first launch of this story failed on it).
- No upstream file edited; no hook row.

### Tests

`:overlay:test` in full: 11 classes, 41 tests, all passing.

| Test | Holds |
|---|---|
| `PanelLayoutTest` (11) | the docking against the inventory pane and against the tag column; the tags on the left; the width 200 → 160 → collapse at exactly W−389 < 160 with interface 2; the 200 view height; too short for the strip and three log lines; mobile and the toggle; the offset (with insets); the strip; a grid of 30,000+ screens (three interface sizes, both tag sides, three inset sets, collapsed or not) where the Panel stays on screen, leaves 200 of map when full, and covers no modelled HUD rectangle |
| `PanelCameraTest` (2) | world units; the vertical part kept; an unchanged offset not re-set |
| `PanelHudTest` (5) | on real play scenes at interface sizes 0, 1 and 2 and five window sizes: the model holds every shown HUD component; the Panel misses them all, full and collapsed; the frames are the game's chrome; a new scene gets a new Panel; the offset survives `GameScene.layoutTags()` and keeps a vertical offset |
| `OverlayToolkitTest` (2) | no Swing, AWT, JavaFX, SWT or embeddable browser in `org.shatterfish.overlay..`; the rule bites on a fixture that uses `java.awt.Rectangle` |
| `LaunchOptionsTest` (+1) | `--window WxH`, its absence, and its refusals |

Also: `:codex:citations` and `DocsCitationTest` (the two new Tier 1 rows in `docs/rules/ui.md`) with
no findings; `mkdocs build --strict` passes.

### Mutation battery

A control run passed first; then 15 mutants, each killed by a named test failure.

| Mutant | Killed by |
|---|---|
| M1 target width 220 | `PanelLayoutTest` docking (first it survived: the test compared with the constant; now the literal 200) |
| M2 minimum width 150 | `PanelLayoutTest.width_then_collapse` |
| M3 uncovered map ignores the left inset | `PanelLayoutTest.never_over_the_hud` (the "200 of map" check added for it) |
| M4 view height `<=` | `PanelLayoutTest.view_height` |
| M5 mobile layout not collapsed | `PanelLayoutTest` strip, toggle, grid |
| M6 interface 2 reserves only the tag column | `PanelLayoutTest` docking, width, offset |
| M7 the boss bar not avoided | `PanelLayoutTest` docking, too short, grid |
| M8 the status pane not avoided | `PanelHudTest` real scene, `PanelLayoutTest` grid and tag docking |
| M9 the offset's sign | `PanelLayoutTest.offset`, `PanelHudTest` offset |
| M10 the vertical offset dropped | `PanelCameraTest`, `PanelHudTest` offset |
| M11 world units multiplied | `PanelCameraTest.units` |
| M12 one Panel for every scene | `PanelHudTest.a_panel_per_scene` (added for it) |
| M13 the frame shown when collapsed | `PanelHudTest.the_frames_are_the_games` |
| M14 `--window` sides swapped | `LaunchOptionsTest.the_window` |
| M15 the strip ignores the mobile status pane | `PanelHudTest` real scene, `PanelLayoutTest` grid |

Not covered by a test: `OverlayGame`'s interface size and fullscreen writes (they run in `create()`
of the desktop game), and `Screenshot`; the real launches below exercise them.

### Real launches

`:overlay:launch` with `--agent brain --seed 2000 --class WARRIOR --turn-cap 300 --exit-when-over`:

| Window | UI view | Interface | The Panel | End |
|---|---|---|---|---|
| 1600x900 (first try, no fullscreen fix) | 400x225, then 512x320 | 1 | FULL at (200, 40) 174x144, then FULL at (286, 40) 200x239 after the game went fullscreen at 2560x1600 | death on floor 1 after 239 turns |
| 1600x900 | 400x225 | 1 | FULL at (200, 40) 174x144, offset 100 UI px | turn cap after 450 turns, 53 waits |
| 720x400 | 360x200 | 0 (the game forced the compact interface: the window is below its full-UI minimum at this display's density) | STRIP at (167, 39) 160x12 | turn cap after 300 turns |

The screenshots (`--screenshot`) show the Panel's frame docked at the right edge below the menu pane
and above the toolbar and the status pane, clear of the tag column, the hero drawn at the middle of
the uncovered map, and, at 720x400, the Mode strip alone below the mobile status pane. A capture from
outside the process (GDI and `PrintWindow`) gave only a blank surface for the OpenGL window, which is
why the launcher captures itself.

The two 1600x900 Runs ended differently: an Overlay Run is not reproducible until story 5.13 (5.1's
named exception), and the window size also changes the frames drawn between waits.

### Deferred

To `docs/ideas.md`: the inventory pane in the executor (to play on the full interface), `DESIGN.md`'s
"below the status pane" wording for the full layout, more screenshot points, and eliding the strip's
content on very narrow mobile windows.

## Review round (fairness review and lens review of a2401424e)

Main (8191d80c0) merged in first. Every finding is fixed with a test, or stated where it is not.

### Fairness (no parity leak; four should-fixes)

1. **Interface size 1 is a second, independent exception to non-negotiable 5.**
   - It changes the log text all Run long:
     - the guidebook pickup line, which every Run meets on floor 1;
     - the guide-page hint on every page found;
     - the tutorial lines.
   - The Brain's `Goo` memory hashes the log's text into its Belief, so an Overlay Run's logged Belief
     hash differs from a headless one.
   - Story 5.13's draw routing does not close this, because the headless driver refuses any size but 0.
   - What was done:
     - ADR-0013's story 5.2 amendment lists the lines and names this exception, with its closing plan: #169.
     - The log header gains `interface`, and the Overlay's host states the size it declares, since the
       setting reads 0 before the game knows its window.
     - `api.RunLog.Header`, `RunLogJson`, `RunLogReader`, `RunLoop.openLog` and `EmbeddedRun.Host`
       change. Both members are chained and written only when stated, so every headless log keeps
       its bytes.
     - Tests: `HeaderScreenTest` and `EmbeddedAttachTest`.
2. **A connected controller changes the key names in those lines.**
   - The header gains `controller` (0 or 1, at the Run's start).
   - A controller plugged in mid-Run is not recorded; ADR-0013 says so, with #169.
3. **Item selectors at the Overlay's interface.**
   - `ItemSelectorTest` shows that at `OverlayGame.INTERFACE_SIZE` the armour selector is a `WndBag` on
     the scene. That is where the executor finds it, carrying the item's selector and drawing what the
     same window at size 0 draws.
   - At size 2 no window opens.
   - It asserts `INTERFACE_SIZE != 2`, pointing at the `docs/ideas.md` entry.
   - The selector is no Prompt (the executor answers it, not the Observer), so the comparison is of the
     window, not of an Observation.
4. **The oracle marker.**
   - An oracle Run always opens windowed (`OverlayGame.windowed`).
   - The Mode strip carries an ORACLE label in the oracle colour (`#FF2020`), full or collapsed.
   - The launcher's comment now names story 5.12 for the border.

### Lens review

1. **HIGH: the offset was drawn a frame late.**
   - `OverlayGame.update()` is now the game's update written out in the game's order, with
     `PanelDock.step` placing the Panel between the scene's update and `Camera.updateAll()`.
   - `PanelHudTest.the_offset_is_drawn_this_frame` checks that the matrix drawn next already has the
     offset, and that the old order does not.
2. **The boss bar's buff rows are modelled:** the large bar as (x, y, 133, 48), the small one as
   (x, y, 64, 24).
3. **The cell prompt and the badge banners take precedence.** They are drawn over the Panel, and the
   Panel dims to 0.35 while either shows. `PanelHudTest.dims_under_the_prompt` covers it.
4. **The scene's fade from black is kept in front of the Panel.** Covered by `PanelHudTest.the_fade_stays_in_front`.
5. **`PanelHudTest` checks the real HUD in full:**
   - each component with every child it draws;
   - a boss assigned before the scene;
   - all four tags laid out;
   - the skip condition fixed.

   It found three more gaps in the model, all fixed:
   - the menu pane's depth and challenge icons, 14 left of its background;
   - the mobile status pane's busy indicator, which reaches 45 below its top;
   - on narrow screens, the strip crossing the left tag column or the game log's column.
6. **`Screenshot.write` catches and logs** a frame it cannot write, so the Run plays on. Not tested:
   it needs the desktop's framebuffer.
7. **Launch arguments and ignored output.**
   - `overlay-runs/` is in `.gitignore`. There is no default screenshot directory: `--screenshot`
     takes a path.
   - `-Plaunch.args` keeps a single- or double-quoted value whole. Not tested: it is Gradle script.

### Tests

| Suite | Result |
|---|---|
| `:overlay:test` in full | 12 classes, 49 tests, all passing |
| `:api:test` in full | 385 tests, all passing |
| `:harness:test`: the embedded, log, rewind and Run-loop classes | 57 tests, all passing |
| `:rig:test`: `OverlayLogsRefusedTest` and `LogHeaderTest` | all passing |
| `:codex:citations` and `DocsCitationTest` | no findings |

`mkdocs build --strict` passes.

### Mutation battery, review round

Control runs of the overlay, api and harness tests passed first. Then 15 mutants, all killed:

| Mutants | Killed by |
|---|---|
| N1: the Panel placed after the matrices | `PanelHudTest`, the matrix test |
| N2: the fade left behind the Panel | `PanelHudTest`, the fade test |
| N3 and N4: the prompt not seen, no dimming | `PanelHudTest`, the dimming test |
| N5 and N6: the oracle label never shown, or not given to a new Panel | `PanelHudTest`, the oracle test |
| N7 to N9: the boss buffs, menu icons and busy indicator left out of the model | `PanelHudTest`, against the real HUD |
| N10: the strip over the log column | `PanelLayoutTest`, the grid |
| N11: an oracle Run left fullscreen | `LaunchOptionsTest` |
| N12: `INTERFACE_SIZE = 2` | `ItemSelectorTest` |
| N13 and N14: the interface size not written, or a headless header allowed to state one | `HeaderScreenTest` |
| N15: the reader dropping the interface size | `EmbeddedAttachTest` |

### Real launches (after the fixes)

Both at 1600x900, `--agent brain --seed 2000 --class WARRIOR --turn-cap 300`.

| Launch | The Panel | Header | End |
|---|---|---|---|
| First | FULL at (200, 57), 174x127, on a 400x225 view, below the boss bar's modelled extent | stated `interface: 0`; that is the bug fixed next | `STALLED`: from wait 213 the Brain answered "Yes, I know what I'm doing" (a chasm, harmful-potion or chalice confirmation) 100 times with no turn passing; filed as #170, not a Panel matter |
| Second, after the host states the declared size | the same placement | `interface: 1, controller: 0` | turn cap after 300 turns |

The screenshot of the second launch (``' + S + 's52-1600x900-review.png``) shows:
- the Panel docked right of the map, clear of the menu pane, the toolbar and the status pane;
- the hero drawn at the middle of the uncovered map.
