---
title: Input reconciliation — Overlay sources vs UX spines
created: '2026-09-03'
sources:
  - prd.md section 4.6 (FR-37 to FR-47), Glossary (Overlay, Panel, Mode, Pause-on condition, Belief summary, Replay, Oracle mode), UJ-2, UJ-4
  - docs/BOOTSTRAP-PROMPT.md section 5
  - research.md section 7 (Noosa building blocks, HUD sizes, threading rule, key bindings)
spines:
  - DESIGN.md
  - EXPERIENCE.md
---

# Reconciliation: what the spines dropped, contradicted, weakened, or invented

Legend for the coverage column: **C** carried, **C+** carried and extended, **W** weakened, **X** contradicted, **G** gap (no home).

## 1. Coverage matrix

### 1.1 PRD section 4.6

| Src | Item | Spine home | Cov | Note |
|---|---|---|---|---|
| FR-37 | Launcher starts the desktop game with the Overlay attached | EXP IA "Launcher" | C | |
| FR-37 | EmbeddedDriver observes every hero turn regardless of actor | EXP Interjection semantics | C | |
| FR-37 | Brain's Actions applied through the ActionExecutor | EXP Foundation (worker thread only) | W | The spine never says where or on which thread the Action is applied; bootstrap says render thread via ActionExecutor. |
| FR-37 | Save and resume continues the same Run log with the boundary recorded | EXP State "Save and resume" | C+ | Spine adds: resume re-attaches, re-plans, and starts PAUSED regardless of previous Mode. |
| FR-37 | Replay across a resume is verified only from E8 (open question 10) | none | G | Spine's Save-and-resume row asserts behavior without flagging that its Replay verification is open. |
| FR-38 | Docked beside the game, game's own toolkit, respects interface-size setting | EXP Foundation, Responsive; DESIGN Layout | C | |
| FR-38 | Shows Mode, speed, turn, depth | EXP/DESIGN Mode strip | W | "speed" is shown as a speed-mode word; in Human play speed the interval value (0.5–5 s) is never displayed. |
| FR-38 | Shows the Goal | Goal line | C+ | Adds: title size, max two lines, change logged. |
| FR-38 | Chosen Action with the top three alternatives, scores, reasons | Decision card | C | DESIGN says "up to three"; PRD says "top three". Harmless but different. |
| FR-38 | Belief summary | Belief row / Belief summary | C+ | Adds "top three items by relevance" and a v1 tap-to-expand (see inventions). |
| FR-38 | Safety flags | Flag chip / Safety flags | C+ | Adds "zero to four", a three-way verdict (ok/warn/danger), verdict-first text. |
| FR-38 | Scrolling decision log | Decision log | C+ | Adds line format, 200-line retention, auto-scroll rule. |
| FR-38 | Free column between menu pane and inventory pane in full desktop layout | DESIGN Layout | C | |
| FR-38 | UX decides placement at smaller sizes | EXP Responsive | W | Only "mobile layout" and "narrower than minimum" are decided. Interface size 1 (full layout without inventory pane, where research says the panel may replace the inventory pane) is not addressed. |
| FR-38 | No Swing/JavaFX/ImGui/web view on the classpath | DESIGN Do's ("the build fails on anything else") | C | Not named as a classpath test; acceptable, it is an architecture consequence. |
| FR-38 | Every Panel write on the render thread; Brain never touches a Panel object | EXP Foundation; DESIGN Do's | C | |
| FR-39 | Pause, Resume | EXP Interaction Primitives | C | |
| FR-39 | Step (one Action) | EXP "Next Step" | X | Redefined as one **hero turn**, not one Action; see Contradiction 2. |
| FR-39 | Run N | none | G | Dropped silently. Not in IA, Controls, Interaction Primitives, or any flow. Bootstrap also lists it. |
| FR-39 | Speed (a turns-per-second cap) | EXP Stepping model, Speed selector | X | Three modes instead of a cap; see Contradiction 1. PRD to be amended. |
| FR-39 | Explain (Policy, alternatives, Safety flags) | EXP IA "Explain expansion" (v1 scope) | C+ | Adds in-place toggle behavior. |
| FR-39 | Take over, Hand back | EXP Interaction Primitives | C+ | Adds "Hand back never lands in RUNNING". |
| FR-39 | Mode changes only when the hero is waiting for input | EXP State "Hero busy"; "at the next hero-input moment" | C+ | Spine adds queuing (press applies when ready). Wording is internally inconsistent: "refused visibly" then "the change applies". |
| FR-39 | Speed caps bot turns without affecting animation speed; animation is the ceiling unless a documented Hook bypasses sprite waits | EXP Stepping model rows Human / Fast | C | |
| FR-40 | Human plays with normal controls in PAUSED or HUMAN; Brain keeps observing; re-plans on Resume; human Actions logged | EXP Interjection semantics | C | |
| FR-41 | Draw planned path, target, considered cells; clear when plan changes | EXP/DESIGN Map highlights | C+ | Adds: also cleared when the hero acts; never in HUMAN; outlines only, never over sprites. |
| FR-42 | Controls bindable through the game's key-binding system, listed in its settings screen | EXP IA "Key bindings", Interaction Primitives | C | |
| FR-42 | Fallback: if the binding Hook is not small, v1 ships buttons only and hotkeys return to E8 | none | G | The spine is "keyboard-first" with no buttons-only fallback described; Accessibility Floor's "every control is rebindable" would be false under the fallback. |
| FR-43 | Red border and ORACLE label when launched with the flag | EXP Oracle marking; DESIGN Oracle border | C+ | Adds 2 px width, a distinct Oracle red vs Danger red, label always with border. |
| FR-43 | May show true identities and unseen enemies | EXP Oracle marking | W/X | Spine confines oracle data to the Belief summary with an `oracle` prefix. Unseen enemies therefore never appear on the map; "oracle overlays" in the bootstrap implies a map overlay. See Contradiction 7. |
| FR-43 | Nothing else changes | EXP State "Oracle": "Everything above applies" | C | |
| FR-44 | Explain view v2: Evaluation terms, Search outcomes | EXP IA "Explain expansion" (v2 clause) | C | |
| FR-45 | Pause-on conditions v2: user can set them; six named conditions | EXP State PAUSED "by a Pause-on condition (v2)" | W/G | The six conditions are not listed; no surface exists to set them. The IA says "no settings surface of the Overlay's own"; the game's settings screen only lists key bindings; so the user-settable conditions have no home. The IA's closure claim ("every stated need in 4.6 lands on a surface") is false here. |
| FR-46 | Replay scrubber: load a Run log, scrub Decisions, full Beliefs view per turn | EXP IA "Replay scrubber (v2)"; Belief summary "opens the Beliefs view (v2)" | W | One IA row. No loading path (launcher flag? file picker?), no scrubber controls, no state row, no flow. Glossary Replay's hash-chain verification against a fresh Run is not surfaced (a scrubber should show verification status). |
| FR-47 | Coach mode: Brain advises before each human move without acting | EXP IA "Coach mode (v2)", Flow 5 | C | Relationship to HUMAN mode (which already shows "what the bot would do, greyed") is not reconciled: coach mode may be HUMAN mode with an ungreyed card. |
| FR-47 | Autoexplore: hands quiet stretches to the Brain; returns control when an enemy appears **or a Pause-on condition fires** | Flow 5 step 4 | W | Only "when a new enemy appears" is carried; the Pause-on trigger is dropped; no control is named. |

### 1.2 PRD Glossary

| Term | Spine home | Cov | Note |
|---|---|---|---|
| Overlay (Panel + controls + map highlights, via EmbeddedDriver) | EXP IA | C | |
| Panel (docked, native-style) | EXP/DESIGN Panel | C | |
| Mode = RUNNING, PAUSED, HUMAN | EXP State Patterns | C+/X | Spine adds `THINKING` (a suffix, fine) and `RUN OVER` (replaces the mode word). Whether RUN OVER is a fourth Mode needs deciding; see Contradiction 6. |
| Pause-on condition | EXP State PAUSED | W | As FR-45. |
| Belief summary (unknown items + top candidates + probabilities, floor facts, chapter counters; full Beliefs view is v2) | EXP Belief summary; DESIGN Belief row | C+ | v1 "tapping an item shows all its candidates" pulls part of the v2 Beliefs view into v1. |
| Replay (load log, step Decisions, verify Hash chain against a fresh Run) | EXP IA "Replay scrubber" | W | Verification aspect absent. |
| Oracle mode (off by default, explicit flag, visibly marked, never in ranked Rig Run) | EXP Oracle marking | C | |

### 1.3 UJ-2 and UJ-4

| Item | Spine home | Cov | Note |
|---|---|---|---|
| UJ-2: Panel docks with Mode **RUNNING** at launch | EXP Flow 1 step 1 | X | Spine launches in PAUSED, speed mode Next Step. See Contradiction 3. |
| UJ-2: Goal string "Explore: guaranteed strength potion still on this floor" | EXP Voice example | W | Reworded to "Explore: strength potion guaranteed on this floor". Cosmetic, but the glossary example is the canonical string. |
| UJ-2: chosen Action + three alternatives + reasons, Belief summary, Safety flags, scrolling log, planned path on map | Flow 1, Flow 2 | C | |
| UJ-2: Pause, read alternatives, Take over, play two turns, Hand back | Flow 2 | C | Flow 2 goes straight from RUNNING to HUMAN without Pause; fine, the primitives allow both. |
| UJ-2 climax: re-plans from current state, no desync, no stale plan | Flow 2 step 5 | C | |
| UJ-2 resolution: Run log records human turns; Replay exact | Interjection semantics | C | |
| UJ-2: after Hand back the Brain "continues" | EXP Hand back -> PAUSED, then Resume | X | Spine requires an explicit Resume. See Contradiction 4. |
| UJ-2 edge: take over mid-animation; Panel shows PAUSED only once hero waits for input | EXP State "Hero busy"; Flow 2 failure path | C+ | Spine queues the press (button dims, then applies) and goes straight to HUMAN rather than showing PAUSED first. Minor difference. |
| UJ-4: coach says what the bot would do and why "in the game's vocabulary", given as a sentence | Flow 5 | X | Spine's Voice section rejects prose; Flow 5 renders the same advice as a terse label. See Contradiction 5. |
| UJ-4: hand a corridor to autoexplore; take back control at the next enemy | Flow 5 step 4 | C | |

### 1.4 Bootstrap section 5

| Item | Spine home | Cov | Note |
|---|---|---|---|
| Brain thinks on a worker thread while the hero waits for input, over the immutable Observation | EXP Foundation | C | |
| Chosen action applied on the render thread through ActionExecutor | none | G | Thread of Action application unstated (only Overlay writes are said to be posted). |
| Mode line: RUNNING/PAUSED/HUMAN; speed as turns-per-second cap; turn; depth | Mode strip | X | Speed cap; Contradiction 1. |
| Goal, chosen + three alternatives, Belief summary, scrolling log, map highlights | Panel components | C | |
| Safety flag example "by water: fireblast-safe; chasm behind target: blast-wave unsafe" | Voice example "unsafe: chasm behind target" | W | Spine flips to verdict-first and drops the item-specific qualifier (which item the flag is about). DESIGN says "the text always states the flag", so the format should be fixed in one place. |
| Controls: Pause/Resume, Step, Run N, Speed, Take over/Hand back, Pause-on conditions, Explain, Replay | Controls | W | Run N gap; Pause-on and Replay weakened (both v2 per PRD, which supersedes the bootstrap's v1 listing). |
| Hotkeys through KeyBindings/SPDAction if a small hook; otherwise buttons only until it can be | Interaction Primitives | G | Fallback dropped (as FR-42). |
| Interjection semantics | EXP Interjection semantics | C | |
| Oracle overlays via explicit `--oracle` flag; red border; ORACLE label; cannot be enabled in the rig | EXP Oracle marking | C | Flag name `--oracle` not carried (spine says "the oracle flag"). |
| "The overlay v1 is the debugger for everything that follows" | EXP Foundation (organizing idea is the TAS) | W | See qualitative ideas. |

### 1.5 Research section 7

| Item | Spine home | Cov | Note |
|---|---|---|---|
| Panel = `Component` with `Chrome.TOAST_TR` background, `renderTextBlock`, `RedButton`, `ScrollPane` | DESIGN Components | C | DESIGN chose `TOAST_TR_HEAVY` for the Panel and `TOAST_TR` for the Mode strip; the research verdict named `TOAST_TR`. A design choice, not a requirement. |
| Added at end of `GameScene.create()`, positioned in `layoutTags()` free column; two hooks + one for sprite-wait bypass | EXP Stepping model (hook reference) | C | Hook names are architecture; fine. |
| All panel writes via `Game.runOnRenderThread`; nothing on the brain thread may touch `RenderedTextBlock`; `RenderedText.measure()` throws from the actor thread | DESIGN Do's; EXP Foundation | C | |
| UI camera at `defaultZoom` from `SPDSettings.scale()`; world zoom separate, so a UI-pixel panel is unaffected by dungeon zoom | DESIGN Layout ("UI pixels before the interface scale") | W | Not stated that map highlights live in the world camera and therefore scale with dungeon zoom while the Panel does not. |
| No layout manager; `layout()` from `Game.width/height`; `PixelScene.align()` snaps | DESIGN Layout | C | |
| `interfaceSize` 0/1/2; default 2 on desktop; forced 0 when window < 2x density-scaled 360 x 200 | EXP Responsive | W | Size 1 unaddressed (see FR-38). The 360 x 200 full-UI minimum is not used to sanity-check the 100/72 px Panel width assumption. |
| 16 `Chrome.Type` skins; `TOAST_TR`/`TOAST_TR_HEAVY`, `WINDOW` (margin 6), `RED_BUTTON`/`GREY_BUTTON` | DESIGN Shapes | C+ | DESIGN also uses `TAG`, `TAB_SET`, `TAB_SELECTED`, `TAB_UNSELECTED`, none named in section 7. Must be verified against the pinned tag's `Chrome.Type`. |
| HUD sizes: MenuPane width 31; StatusPane height 39; Toolbar height 26; InventoryPane 187 x 82 (size 2 only); GameLog 160 UI px minus tag width | DESIGN Layout (31, 187 x 82 only) | W | StatusPane 39, Toolbar 26, GameLog 160 dropped. The Panel's **height** is never specified; the free column's vertical extent needs the menu-pane height (not in research) and the inventory-pane height. |
| `layoutTags()` stacks indicator tags left or right per `flipTags()`; GameLog sized against tag width | none | G | With tags flipped right, the indicator tags occupy the column the Panel wants. Spine never mentions `flipTags`. |
| At interface size 1 the panel may replace the inventory pane | none | G | |
| `ScrollPane` handles drag, wheel, and keyboard scrolling | EXP Decision log | W | Spine lists log scrolling under Mouse only; keyboard scrolling of the log is not in Interaction Primitives, which contradicts the Accessibility Floor's "the mouse is never required". |
| Actor thread: waits on sprite `isMoving`; parks with `wait()`; `GameScene.update()` notifies at most 60/s; `onPause()` waits up to 500 ms before saving | EXP Stepping model (animation is the ceiling) | C/W | The 60/s notify cap bounds "Fast as it can" in-game; the spine gives no ceiling number (fine). The 500 ms save wait is not reflected in Save-and-resume (what happens to an in-flight Brain computation on save-and-quit is unstated). |
| Input seam: `Hero.act()` -> `ready()` -> `GameScene.ready()`; `hero.handle(cell)` then `hero.next()`; `Hero.interrupt()`; `GameScene.cancel()` | EXP "hero-input moment" | C | Named conceptually. But the Stepping model's "one Decision per hero turn even for a multi-cell move" requires the ActionExecutor to issue single-step Actions or call `Hero.interrupt()` each turn; the spine does not say which. Architecture impact; see Contradiction 2. |
| `SPDAction` constants in `defaultBindings`; `keybinds.dat` deltas; settings window enumerates `GameAction.allActions()`; no `addKeyBinding` | EXP Key bindings row | C | |
| Forks: only external companion-app overlays found; full-source fork edit of `GameScene.create()` is the norm | DESIGN Brand (toolkit-only) | C | Implicitly rejects the companion-app pattern. |

## 2. Gaps (no home in either spine)

1. **Run N** (FR-39, bootstrap). Absent entirely. Either it dies (the three-mode model may cover it) or the PRD amendment must say so.
2. **Pause-on condition configuration surface** (FR-45, glossary). The user "can set conditions", but the spine has no Overlay settings surface, and the game's settings screen only carries key bindings. The six conditions are not listed anywhere. Autoexplore's Pause-on return trigger (FR-47) is dropped with it.
3. **Hotkey fallback** (FR-42 provenance, bootstrap): "buttons only until the hook is small" is not designed. If the Hook is not small, the Accessibility Floor and Interaction Primitives are wrong as written.
4. **Human play speed interval configuration**: the spine says "configurable interval, default one per second, range half a second to five seconds" but names no place to configure it (not the game's settings, not the Panel, not a launcher flag).
5. **Interface size 1** (research): full layout without the inventory pane; research says the panel may replace it. Spine's Responsive table has no row for it.
6. **`flipTags()` right-side tags and GameLog** (research): the indicator tags may stack in the right column; the spine's "free column" assumes it is empty.
7. **Panel height** (research HUD sizes): never specified; StatusPane 39, Toolbar 26, GameLog 160 dropped from the layout facts.
8. **Thread of Action application** (bootstrap): the spine only says Overlay writes are posted to the render thread; it never says the Action is applied there through the ActionExecutor.
9. **Map highlights camera**: highlights are drawn "in the dungeon view" but the spine does not say they live in the world camera (and so scale with dungeon zoom), unlike the Panel.
10. **Replay-across-resume is open** (FR-37, open question 10): the Save-and-resume state row asserts re-attach behavior without the caveat.
11. **`--oracle` flag name** (bootstrap): spine says "the oracle flag".
12. **Replay scrubber loading path and controls** (FR-46): the IA row exists; nothing reaches it (no flow, no launcher flag, no state row), and hash-chain verification status is not shown.

## 3. Contradictions

1. **Speed model.** PRD FR-39 and bootstrap define Speed as a turns-per-second cap shown on the Mode line. The spine's Stepping model defines three speed modes: Next Step, Human play speed (0.5–5 s interval, default 1 s), Fast as it can, cycled by one key and shown as a `TAB_SET`. *The PRD is scheduled to be amended to the three-mode model.* Residual: FR-38's "shows speed" becomes "shows speed mode"; the interval value is never displayed.
2. **Unit of stepping.** PRD FR-39: Step = one **Action**; glossary Action includes "move to [a target]", which the game executes over several hero turns. Spine: Next Step = one **hero turn**; a multi-cell move is one Decision per hero turn, re-planned every turn. This changes what an Action is at the ActionExecutor (single-step moves, or `Hero.interrupt()` each turn) and what a Run log line is (per hero turn, not per Action). Must flow back to FR-4, FR-32, glossary Action/Decision, and the Run log definition.
3. **Launch state.** UJ-2: the Panel docks with Mode RUNNING. Spine Flow 1: launches PAUSED in Next Step mode. Defensible (TAS discipline, nothing acts unwatched) but different; the PRD journey must change or the spine must offer a launcher flag for the initial mode.
4. **Hand back.** UJ-2: after Hand back "the Brain re-plans ... and continues". Spine: Hand back always lands in PAUSED; Resume or Next Step is required. The spine's reason (the human sees the bot's next Decision before it acts) is good; the PRD journey should say so.
5. **Coach voice.** UJ-4 has the coach speak a full sentence "in the game's vocabulary" for a learner. Spine Voice bans prose ("Rejected: showing the bot's reasoning as prose") and Flow 5 renders the same advice as `read scroll (KHIT) 0.71 by door and water, worst case survivable`. For UJ-4's learner audience the terse register may be the wrong register; the spine should either exempt coach mode or the PRD should accept labels.
6. **Mode set.** Glossary Mode = RUNNING, PAUSED, HUMAN. Spine's Mode strip also reads `RUN OVER: <cause>` in place of the mode word, and `THINKING` / `ORACLE` as suffixes. Decide whether RUN OVER is a fourth Mode (then FR-40, Run log Mode-change lines, and the glossary change) or a Panel state distinct from Mode.
7. **Oracle data placement.** PRD FR-43 / bootstrap: oracle "overlays" may show true identities and **unseen enemies**. Spine: oracle data appears **only inside the Belief summary** with an `oracle` prefix; nothing is drawn on the map. Unseen enemies as text rows is a narrowing; if the map marking is wanted for debugging, FR-43 should say where.
8. **"Never overlays the map".** EXPERIENCE (Panel row, Responsive): "Never overlays the map; never covers the game's own HUD; never changes the game's own layout". Research: the HUD is drawn over the world; there is no non-map column; full-UI minimum is 360 x 200 UI px. DESIGN agrees with research ("the Panel's frame sits over the dungeon", translucent scrim). The EXPERIENCE statement is unachievable as written and contradicts DESIGN; it should read "overlays the dungeon at HUD opacity, never covers the game's HUD elements".
9. **Hero busy semantics (internal).** EXPERIENCE State "Hero busy": "Mode changes are refused visibly ... then the change applies". Refused and queued are different behaviors; Flow 2's failure path and the THINKING row describe queuing. Pick one word. Also UJ-2's edge case says the Panel shows PAUSED once the hero waits; the spine goes straight to HUMAN.
10. **Panel frame.** Research verdict: `TOAST_TR`. DESIGN: `TOAST_TR_HEAVY` for the Panel, `TOAST_TR` for the strip. Design choice, not a requirement conflict, but the research's Chrome inventory did not include `TAG`, `TAB_SET`, `TAB_SELECTED`, `TAB_UNSELECTED`, which DESIGN relies on; verify they exist at the pinned tag.
11. **Safety flag text format.** Sources: "by water: fireblast-safe" (context: item-verdict). Spine: "unsafe: chasm behind target" (verdict: context, no item). One format should win and land in the glossary.
12. **Keyboard scrolling of the Decision log (internal).** Accessibility Floor: "the mouse is never required". Interaction Primitives lists "scroll the Decision log" under Mouse only. Research says `ScrollPane` already handles keyboard scrolling; add it to the primitives.

## 4. Weakened items

- **Pause-on conditions**: from a user-settable list of six to a one-clause mention (FR-45).
- **Replay scrubber / Beliefs view**: from a described feature to one IA row with no flow, no controls, no verification display (FR-46, glossary Replay).
- **Autoexplore**: from a control with two return triggers to one sentence in a v2 flow with one trigger (FR-47).
- **Speed visibility**: "shows speed" became "shows speed mode"; the interval value is invisible (FR-38).
- **Safety flag example**: item-specific qualifier lost (bootstrap).
- **Smaller-size placement**: only two of the three interface sizes decided (FR-38, research).
- **HUD sizes**: three of five measured sizes dropped; Panel height unspecified (research).
- **Hotkeys**: the buttons-only fallback dropped (FR-42).
- **Goal example string**: reworded (UJ-2, glossary Goal).
- **Save-and-resume**: verification caveat and the 500 ms actor-thread save wait dropped (FR-37, research).
- **Oracle**: unseen enemies confined to text (FR-43).

## 5. Qualitative ideas dropped or reframed

- **"The v1 Overlay is the debugger for everything that follows"** (bootstrap section 5; PRD 6.2 "v1 is the debugger, v2 is the coach"). The spine's organizing idea is "an automated tool-assisted speedrun"; the debugger role survives only implicitly in Flow 3 (reading the log after death) and Flow 4 (oracle). The spine's Foundation should name both roles, or the PRD should adopt the TAS framing.
- **Brain thinks only while the hero waits for input** (bootstrap): the spine's THINKING state allows the Brain to still be computing at the next input moment, which is consistent but adds a per-turn budget concept the sources do not have.
- **Coach mode in the game's vocabulary as sentences** (UJ-4): reframed into the instrument's label register (Contradiction 5).
- **Replay verifies the Hash chain against a fresh Run** (glossary): the Overlay's Replay surface has no verification affordance.
- **Research's external companion-app pattern**: correctly rejected by the toolkit-only rule, but the spine's Anti-patterns list does not record the rejection, so the reason will not be found by the next reader.

## 6. Inventions (new in the spines relative to the sources)

Candidates to flow back into the PRD are marked **[PRD]**.

**Stepping and speed**
1. **[PRD]** Unit of stepping is one hero turn; one Decision, one Run log line, one Next Step per hero turn; multi-cell moves re-planned every turn (Contradiction 2).
2. **[PRD]** Three speed modes: Next Step / Human play speed (interval 0.5–5 s, default 1 s, never faster than animation) / Fast as it can (Panel throttled to a few updates per second). One key cycles them; the `TAB_SET` selector shows the current one. Switching applies at the next hero-input moment and never loses a turn.
3. **[PRD]** Launch state is PAUSED in Next Step mode (Contradiction 3).
4. **[PRD]** Next Step pressed in RUNNING pauses first; the next press steps.
5. **[PRD]** Hand back always lands in PAUSED, never RUNNING (Contradiction 4).

**States**
6. **[PRD]** `THINKING` suffix when the Brain exceeds a configured per-turn budget; previous Decision stays; a Next Step press is queued and fires when the Decision lands. (Introduces a per-turn budget the PRD does not define for the Overlay.)
7. **[PRD]** Hero busy: a control pressed mid-animation dims and applies when the hero is ready (queued, not dropped).
8. **[PRD]** No valid action: card reads `no valid action; waiting`; the bot waits once; three recurrences enter PAUSED and log it.
9. **[PRD]** `RUN OVER: <cause>, turn N, depth D` on the Mode strip; log stays scrollable; controls disabled except Panel toggle; Run log path shown (Contradiction 6).
10. No Run state: Panel hidden on title/menus/loading; attaches when the game scene appears with a living hero.
11. **[PRD]** Save-and-resume: on resume the Overlay re-attaches, re-plans, and starts PAUSED regardless of the previous Mode.
12. Panel collapse to the Mode strip: when the free column is narrower than the minimum, when the human toggles it, at the largest interface scale, and in mobile layout; click on the strip or hotkey toggles; hotkeys keep working collapsed.
13. Desktop only; not built for the mobile targets.

**Panel content rules**
14. Fixed section order: Mode strip, Goal, Decision card, Safety flags, Belief summary, Decision log, Controls.
15. Panel updates only at hero-input moments and on Mode changes.
16. Goal line: title size, max two lines, never ellipsized; a Goal change is written to the Decision log.
17. Decision card: `_highlight_` only for the chosen Action name; "up to three" alternatives; greyed in HUMAN mode showing what the bot would do; Explain expands in place and a second press collapses.
18. **[PRD]** Safety flags: zero to four chips; three-way verdict (ok / warn / danger = safe / conditional / unsafe); text always states the flag; verdict-first format (Contradiction 11).
19. **[PRD]** Belief summary: top three unknown items by relevance; tapping an item shows all its candidates in **v1** (part of the v2 Beliefs view pulled forward).
20. **[PRD]** Decision log: line = turn, actor (bot/human), Action, score; Goal changes and Mode changes as their own lines; newest at bottom; auto-scroll pauses on manual scroll; 200 lines retained on screen.
21. **[PRD]** Mode strip turn counter and Decision log agree at all times; every human Action is logged with actor `human`.
22. Controls: one row wrapping to two, order Pause/Resume, Next Step, Speed selector, Take over/Hand back, Explain; impossible controls are dim and non-interactive.

**Map highlights**
23. **[PRD]** Highlights never drawn in HUMAN mode; cleared when the hero acts as well as when the plan changes; outlines only, never filled, never over sprites; colors chosen / danger / alternative.

**Oracle**
24. **[PRD]** Oracle data shown only in the Belief summary with an `oracle` prefix (Contradiction 7); 2 px border in a dedicated Oracle red distinct from Danger red; the label is never shown without the border and vice versa; the flag cannot be toggled at runtime.

**Input**
25. Default bindings F6 Next Step, F7 cycle speed, F8 Pause/Resume, F9 Take over/Hand back, F10 Explain, F11 toggle Panel (all flagged as assumptions; the sources give no defaults, and the research does not list the game's F-key usage). F11 in particular should be checked against desktop fullscreen conventions.
26. Banned: any Overlay key that shadows a game binding; hover-only affordances; any control acting mid-animation.
27. Mouse: click Belief row to expand; click Mode strip to toggle Panel.

**Visual identity (DESIGN.md)**
28. Colour palette (ink 3 weights, chosen, alternative = human, goal, ok/warn/danger, running = ok, paused = warn, oracle) with the meaning rules "red only for Danger and Oracle", "Alternative is also HUMAN because the human's move is the alternative the Brain did not choose".
29. Type sizes 9 / 8 / 6 UI px; numbers right-aligned in fixed-width columns.
30. Spacing 1/2/4/6/8; padding 4, row gap 2, section gap 6; `PixelScene.align` on everything.
31. Panel width target 100 UI px, minimum 72 (assumption, to be measured in E5); `TOAST_TR_HEAVY` Panel, `TOAST_TR` strip, `TAG` chips, `TAB_SET` selector (Contradiction 10).
32. No animation on the Panel; no solid backgrounds; the game's title and message-log colours forbidden.

**Scope and structure**
33. Rejected: a `Window` dialog for the Overlay; reasoning as prose; an Overlay-owned settings screen (this last one is what leaves FR-45 and the speed interval homeless).
34. Coach mode (v2) as "a speed-mode-like toggle in the Controls"; its relation to HUMAN mode unresolved.
35. Accessibility floor: everything stated in words; keyboard-operable; Run log plain text readable without the Panel.

## 7. Checks on the spines' own claims

- EXPERIENCE IA "Closure: every stated need in PRD section 4.6 lands on one of these surfaces" is false for Run N (FR-39) and for setting Pause-on conditions (FR-45).
- EXPERIENCE Foundation "Glossary terms ... are used exactly as the PRD defines them" is not true for Mode (RUN OVER) or for Action/Decision granularity (hero turn vs Action).
- EXPERIENCE sources frontmatter lists the three inputs; DESIGN has no sources list.
