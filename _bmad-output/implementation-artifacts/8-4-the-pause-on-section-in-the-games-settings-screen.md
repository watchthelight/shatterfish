---
title: 'Story 8.4: The Pause-on section in the game''s settings screen'
type: 'feature'
created: '2026-10-27'
status: 'review'
baseline_commit: 'ef7b427bb'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** A person sets the Pause-on conditions of story 8.3 where they set everything else, and finds them set
the next time the game opens (FR-45, UX-DR15). Until now nothing in the game could turn a condition on.

**Approach:** One hook adds a button to the game's settings screen. It opens an Overlay window of six check boxes
and a threshold stepper, drawn with the game's own components, with the Overlay's own strings. The window edits an
`OverlaySettings` value; the Overlay keeps it in a libGDX preferences file under Overlay-prefixed keys and gives it
to the Run's controls, which read it at the next Input wait.

## Acceptance Criteria (from epics.md Story 8.4, with the tests that hold them)

1. The story file reads the pinned settings window with `path:line`, lists three ways to add a section, and picks
   the smallest edit to upstream. Done in the Design notes below and in the ledger row.
2. The edit is one hook: row 12 in `docs/UPSTREAM.md` with its marker (three sites in `WndSettings.java`), a
   `Hooks.SettingsSection` listener field so `core` never imports the Overlay, and the PR label `touches-upstream`.
   `HooksLedgerTest` passes with the diff budget it enforces (eleven rows, the limit, not raised);
   `HooksVanillaTest` passes (nothing deleted, `clear()` nulls the new point).
3. The section holds a toggle per condition and a stepper for the HP threshold, drawn with `Window`, `CheckBox` and
   `RedButton`, with the strings in the Overlay's own file `overlay.properties`.
   `PauseOnWindowTest.each_toggle_flips_its_condition`, `the_boxes_open_as_set`, `the_stepper_steps_and_stops`,
   `every_string_is_in_the_message_file` (every key exists, none is unused, none is written in Java).
4. The values persist in a libGDX store under `shatterfish_overlay_` keys and survive a restart; a missing or
   corrupt key or file falls back to the default of `OverlaySettings` for that key and never fails the screen.
   `OverlayStoreTest`: `an_empty_store_is_the_defaults`, `saved_values_come_back_under_prefixed_keys`,
   `a_restart_reads_the_values_back` (a new store over the same file), `a_missing_file_is_the_defaults`,
   `a_corrupt_file_is_the_defaults`, `a_bad_key_falls_back_alone`, `an_out_of_range_threshold_is_the_default`,
   `an_unwritable_store_does_not_throw`; and `PauseOnWindowTest.what_is_set_is_stored`.
5. The section is reachable in a Run and wherever the game shows its settings; a change during a Run takes effect at
   the next Input wait. `PauseOnWindowTest.the_settings_screen_shows_the_button` (a real `WndSettings` shows the
   button only when the Overlay registered a section, and pressing it opens the window) and
   `a_change_takes_effect_at_the_next_wait` (nothing stops the wait in hand; the next one is held).
6. Headless first: the window is built on a `HeadlessScene` and every control is pressed through its own click
   handler (`Toggle.click`, `Step.click` call the components' `onClick`); no agent opens a window. The product
   owner's live checklist is below.
7. No rig numbers: the story changes when the Overlay stops, not how the bot plays (8.3's
   `PauseOnRunTest.the_feature_asks_and_does_not_change_play` holds that and still passes).
8. Fairness: the PR carries the `fairness-reviewer` verdict (the settings drive what the Overlay pauses on).

</frozen-after-approval>

## Design notes

**The pinned screen.** `WndSettings extends WndTabbed` (`core/.../windows/WndSettings.java:56`). Its constructor
builds six `Component` tabs and adds each with an `IconTab` whose `select` shows it (`:81-187`); the Input tab is
added only with a keyboard or a controller (`:109-129`), so the tab count varies and `:193` tests `tabs.size() == 5`
to know whether it is missing. The window is sized once from the tallest tab (`:189`). The Interface tab is
`UITab` (`:359`, title "Interface Settings", `windows.properties:316`). Precedents for a button that opens a window:
`UITab.btnToolbarSettings` (`:421-549`) adds a `new Window()` of check boxes with
`ShatteredPixelDungeon.scene().addToFront` (`:424`), and `InputTab.btnKeyBindings` adds `WndKeyBindings` (`:681-690`).

**Ways to add a section.** (a) A seventh tab: a new `IconTab` and `Component`, an icon from upstream's `Icons`
atlas (nothing fits), a new `last_index` value and a change to the `tabs.size() == 5` test; the biggest edit and
the likeliest to collide when a tag adds a tab. (b) A button on an existing tab that opens an Overlay window, the
label and the click coming from a listener in `Hooks`: a field, a button and a `setRect`, in a pattern the tab
already uses. (c) A listener called with the finished `WndSettings` so the Overlay adds its own components: the
tabs are private and the window's height is fixed at `:189`, so the Overlay would work inside upstream's layout.
(b) chosen: the smallest edit, it leaves the tab table alone, and it opens a window the Overlay owns, so the
strings, toggles and stepper cost upstream nothing. On `UITab` rather than `DataTab` because "Interface" is where a
player looks for how the game behaves, and rather than `InputTab` because that tab is missing without a keyboard.
Pre-mortem: a tag moves `UITab`'s layout (the button is placed by the last `height`, so it follows); a tag adds a
`last_index` shift (no tab is added or renumbered, so nothing here moves); the listener outliving a Run (it is
registered for the process, not the Run, and unset in `dispose()`); `Hooks.clear()` run by the headless driver
(`HooksVanillaTest` holds that it nulls the point, and the Overlay never calls it).

**Where the values live.** The epic says "the game's own settings store". An Overlay Run replaces the game's
preferences with an in-memory set that is thrown away, and never reads or writes the player's `settings.xml`
(FR-37, ADR-0007; `ShatterfishLauncher.main`), so neither can hold a choice across games. Alternatives:
(1) write the Overlay's keys into the player's `settings.xml`, which breaks the promise that a Run never touches it
and risks a half-written file shared with a running game; (2) a file of the same kind the game uses, a libGDX
`Lwjgl3Preferences`, in `~/.shatterfish/overlay-settings.xml` (or the path in the system property
`shatterfish.overlay.settings`), under keys that all begin `shatterfish_overlay_`; (3) a plain `java.util.Properties`
file of our own. (2) chosen: it is the same store type and key discipline as the game's, with none of the risk.
`OverlayStore` reads each key alone and falls back per key.

**Strings.** The game's `Messages` reads a fixed list of bundles (`Messages.java:56-66`) and cannot be given a
seventh, and row 10's precedent costs upstream lines. So `OverlayMessages` reads `overlay.properties` beside the
class. The Mode strip's words for a condition ("before stairs", story 8.3) stay in `PauseOn.Condition.label()`:
they complete "PAUSED: ..." and read differently from a toggle's label.

**Title scene.** The Overlay launches straight into a Run and has no title scene of its own. The section is where
the game's settings screen is, which is every place the game shows it, and the listener is registered from
`OverlayGame.create()`, so it is there from the first frame of the Run. The unmodified game's title scene shows no
button, by design: the section is the Overlay's.

**Live change.** The window's sink is `OverlayGame.settingsChanged`: store the value, then
`ControlState.settings(changed)` (story 8.3: forgets the previous wait so no condition compares with a wait from
before it was on, and holds or releases the Run as the new settings need). It runs on the render thread, where
`ControlState` lives. A HUMAN Run has no controls; the value is only kept.

## Live-session checklist (the product owner's acceptance; no agent has opened a window)

From story 8.3 and from this story. Run `./gradlew :overlay:launch -Plaunch.args="--seed <standard seed> --class warrior"`.

- [ ] Open the game's settings (the menu button, then the gear) and find "Shatterfish Overlay" under the Interface
      tab, beneath the font and vibration boxes. It is not there in the unmodified `:desktop:debug` game.
- [ ] Pressing it opens "Pause the bot": six boxes and an "HP below: 30%" row with `-` and `+`. Each box ticks
      and unticks; `+` and `-` move the number by five and stop at 99 and 1.
- [ ] With "Before stairs" on, press Resume: the bot stops with the strip reading "PAUSED: before stairs" and the
      Decision card showing the descend about to run. Resume again: it descends and does not stop for the same
      stairs twice.
- [ ] With "When HP is low" on at 30%: the bot stops once when HP crosses below, not at every wait under it.
- [ ] Turn a box on while the bot is Running (open settings, tick it, close): the bot is stopped at the next wait
      the condition holds, not the one it was in the middle of.
- [ ] At Fast as it can with a condition on, the Run still plays at speed between stops.
- [ ] Each of the other conditions (item use, unknown item, boss floor, new enemy) stops a real Run as 8.3 says.
- [ ] Close the game and start it again: the section shows the same boxes and the same threshold.
- [ ] Delete `~/.shatterfish/overlay-settings.xml` or put rubbish in it: the game starts, the section shows the
      defaults (all off, 30%).
