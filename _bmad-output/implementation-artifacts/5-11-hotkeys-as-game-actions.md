---
title: 'Story 5.11: Hotkeys as game actions'
type: 'feature'
created: '2026-10-25'
status: 'review'
baseline_commit: 'ad05a5a58'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Overlay's controls were buttons only. A person watching a Run wants a key, and the game already has a
key-binding system with a settings screen; a second one would be a second thing to learn.

**Approach:** Register the controls as the game's own actions, with the defaults F6 to F11 that the pinned tag leaves
unbound, so the game's key-binding screen lists and rebinds them. Every control stays a button.

## Acceptance Criteria (from epics.md Story 5.11, with the tests that hold them)

1. **The Overlay's controls are registered game actions with defaults F6 to F11.**
   Tests: `KeyBindingConflictTest.the_defaults_are_f6_to_f11`, `HotkeyTest.the_defaults_answer`.
2. **They appear in the game's own key-binding screen and can be rebound there.**
   Tests: `HotkeyTest.the_game_lists_them` (in `GameAction.allActions()`, which the screen walks, with their one
   default key), `.the_screen_has_strings` (the screen's string for each), `.a_rebinding_is_followed`,
   `.the_bindings_file_keeps_it` (save and load by the game's own `SPDAction`).
3. **Every control remains reachable as a button.** The Controls row is unchanged; the Panel toggle, the one control
   that had no button, gets a tap on the Mode strip. Tests: `HotkeyPressTest.the_panel_toggle`, `.a_key_is_its_button`.
4. **No Overlay default shadows an existing game binding, asserted by `KeyBindingConflictTest`.**
   Tests: `.no_default_shadows_a_game_binding` (keyboard defaults, controller defaults, hard bindings),
   `.the_registration_declines_a_conflict`.

Fairness (non-negotiable 1, FR-40): a hotkey is never a hero's input. `InputLockTest.a_hotkey_never_reaches_the_game`
(locked or open, press and release kept from the game), `.a_hotkey_is_no_human_input` (a HUMAN Run is not told of it),
`HotkeyPressTest.a_dim_control_ignores_its_key`, `.a_window_in_front_takes_no_hotkey`. This story changes no Brain
decision, so there is no direction table.

</frozen-after-approval>

## Decisions

The owner is away and delegated; each is recorded in the PR body too. The architectural ones are in ADR-0013's story
5.11 amendment.

- **Hook row 10 is two sites**: `SPDAction.addDefaultBinding` (the defaults are a private map) and six strings in
  `windows.properties` (the screen's label is `Messages.get(WndKeyBindings.class, name)`). Everything else needed
  is open: any subclass can create a `GameAction`, and `allActions()` feeds load, save and the screen.
- **The hook declines rather than throws** when the game binds the key already, so a future upstream tag that binds
  F6 leaves the action unbound and rebindable, and `KeyBindingConflictTest` fails the build so the default is moved.
- **Hotkeys are heard in `InputLock`, before the game, in every Mode**, and are never passed on. They reach the
  Panel's controls through `PanelDock.press` and `ControlsRow.press`, which require that the button would act.
- **Keys: F6 Next Step, F7 cycle speed, F8 Pause/Resume, F9 Take over/Hand back, F10 Explain, F11 Panel toggle**
  (EXPERIENCE.md, Interaction Primitives). Run N and the steppers are button-only, as specified.
- **The Panel toggle's button is the Mode strip**, as EXPERIENCE.md's mouse line says ("click the Mode strip to
  toggle the Panel"). It is a `PointerArea` in `Panel` (about twenty lines, none of them in map rendering).
- **Hotkeys step aside while the game waits for a key to bind**, and while a window of the game's is in front.

## Not here

A controller button bound to an Overlay action (the game's screen lists it; the Overlay does not hear a gamepad,
which writes the key queue past the lock); persisting a rebinding across Runs without a named Profile (a Run's
Profile is deleted at its end); the FR-45 Overlay section of the settings screen (E8).
