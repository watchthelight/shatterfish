---
name: Shatterfish Overlay
status: final
sources:
  - _bmad-output/planning-artifacts/prds/prd-shatterfish-2026-09-03/prd.md
  - _bmad-output/planning-artifacts/research/technical-shatterfish-engine-foundations-2026-09-03/research.md
  - docs/BOOTSTRAP-PROMPT.md
created: '2026-09-03'
updated: '2026-09-03'
---

# Shatterfish Overlay — Experience Spine

## Foundation

The Overlay is a desktop Java surface inside the real Shattered Pixel Dungeon window, built only from the game's own UI toolkit (Noosa: `Component`, `Chrome` nine-patches, `renderTextBlock`, `RedButton`, `Icons`, `ScrollPane`, `SPDAction` key bindings). `DESIGN.md` is the visual identity reference; this spine specifies behavior. The game owns the render thread; every Overlay write is posted to it, and the Brain thinks on a worker thread over an immutable Observation (PRD FR-12, FR-38, NFR-4). The Overlay appears only while the game scene is active with a living hero; on the title screen, in menus, and after death it is absent or reduced (see State Patterns). Glossary terms (Observation, Action, Input wait, Prompt, Decision, Goal, Safety flag, Belief summary, Mode, Speed mode, Run log, Oracle mode) are used exactly as the PRD defines them. Panel placement, translucency, and the camera offset are specified in `DESIGN.md` Layout & Spacing; the game's own inventory-pane offset is vertical-only and conditional, so the Overlay applies its own horizontal offset and re-applies it after the game's `layoutTags` (`docs/rules/ui.md`).

The organizing idea, from the product owner: the Overlay is an automated tool-assisted speedrun. The human can advance the bot one Input wait at a time, at a pace a person can follow, or as fast as the engine allows, and can take the controls at any Input wait and hand them back. Watching is the least of what it does.

## Information Architecture

| Surface | Reached from | Purpose |
|---|---|---|
| Panel | Docked at the right edge of the game view whenever a Run is in progress | Everything the bot knows and intends this turn: Mode strip, Goal, Decision card, Safety flags, Belief summary, Decision log, Controls |
| Mode strip | The Panel's first line; the whole Panel when collapsed | Mode, speed mode with its interval, turn, Floor; the toggle that expands or collapses the Panel |
| Explain expansion | The Decision card, via the Explain control or hotkey | The Policy that fired, the alternatives' reasons in full, the Safety flags that applied (v1); Evaluation terms and Search outcomes (v2) |
| Run N and pace settings | The Controls row | The Run N count and the Human play speed interval, set with small steppers on the Panel; no separate settings surface |
| Pause-on conditions (v2) | An Overlay section of the game's own settings screen | The six Pause-on conditions of PRD FR-45 |
| Map highlights | The dungeon view itself | Planned path, target cell, considered cells for the current Decision |
| Key bindings | The game's own settings screen | Every Overlay control is an `SPDAction` and is rebindable there |
| Launcher | Command line | Starts the game with the Overlay; the oracle flag lives here and nowhere else |
| Replay scrubber (v2) | The Panel, when a Run log is loaded instead of a live Run | Steps through recorded Decisions with the Beliefs view |
| Coach mode (v2) | A speed-mode-like toggle in the Controls | The Brain advises; the human acts |

→ Composition reference: `mockups/key-panel-paused.html` (Panel in PAUSED at Flow 1 step 3, with the Explain expansion), `mockups/key-panel-states.html` (RUNNING, HUMAN, THINKING, collapsed, Oracle). The spine wins on conflict.

Closure: every stated need in PRD section 4.6 lands on one of these surfaces, and every surface has a flow below that reaches it. Configuration lives in three places and nowhere else: the launcher flags (seed, class, challenges, oracle), the Panel's own steppers (Run N count, Human play speed interval), and, in v2, an Overlay section of the game's settings screen (Pause-on conditions).

## Stepping model (invented section)

The unit of stepping is one **Input wait**: a hero turn or an open Prompt (a subclass, talent, quest, shop, or alchemy dialog). A Decision is made per Input wait; a Run log line is written per Input wait; Next Step advances exactly one. A multi-cell move the game would carry out from one click is still one Decision per Input wait, re-planned each time, so the human can interrupt at any cell. A Run starts in PAUSED with speed mode Next Step.

| Speed mode | Behavior | Selected by |
|---|---|---|
| **Next Step** | The bot acts only when the human presses Next Step; between presses the Mode strip reads PAUSED and the Decision card shows what the next press will do | F6 (Interaction Primitives) |
| **Run N** | Advances N Input waits at Human play speed, then lands in PAUSED; N defaults to 10 (range 1 to 999) | Button only |
| **Human play speed** | The bot acts on its own, paced so a person can read each Decision before the next. One Input wait per configurable interval (default 1 second, range 0.5 to 5 seconds), shown on the Mode strip. The pace never exceeds the game's animation speed | F7 cycles the modes (Interaction Primitives) |
| **Fast as it can** | Uncapped; the bot acts as soon as the hero is ready; the Panel updates at most a few times per second so it stays readable; the game's animation is the ceiling unless a documented hook bypasses sprite waits (PRD FR-39) | F7, third position |

Switching speed mode never loses a turn: the switch takes effect at the next Input wait.

## Voice and Tone

Microcopy is the instrument's: labels, numbers, one-line reasons in Codex vocabulary. The game's own log keeps its voice; the Overlay never imitates it.

| Do | Don't |
|---|---|
| `Explore: strength potion guaranteed on this floor` | `Let's go find that strength potion!` |
| `read scroll (KHIT)  0.71  by door and water, worst case survivable` | `I think reading this scroll is a good idea because...` |
| `HUMAN  Human 1.0s  turn 412  floor 3` | `You are now in control!` |
| `unsafe: chasm behind target` | `Warning! Danger ahead!` |
| `no valid action; waiting` | `Hmm, I'm stuck` |
| Item and mob names exactly as the game shows them (`turquoise potion`, `gnoll scout`) | Internal class names or IDs |

## Component Patterns

Behavioral. Visual specs live in `DESIGN.md` Components.

| Component | Where | Behavioral rules |
|---|---|---|
| Panel | Game view, right edge | Single column, fixed section order. Collapses to the Mode strip per the Responsive & Platform table or when the human toggles it. Placement per `DESIGN.md` Layout & Spacing. Updates only at Input waits and on Mode changes. |
| Mode strip | Panel, first line | Shows `RUNNING` / `PAUSED` / `HUMAN` in words, the speed mode with its interval (`Human 1.0s`), the turn, and the Floor. Click or hotkey toggles Panel expansion. Carries the `THINKING` and `ORACLE` suffixes (Thinking indicator and Oracle border rows). |
| Goal line | Panel | One Goal in plain words from the Decision. Changes only when the Brain's Goal changes; a change is written to the Decision log. |
| Decision card | Panel | The chosen Action with its score, then up to three alternatives with scores and one-line reasons. In Next Step mode the card shows the Action the next press will execute. Explain expands the card in place; a second press collapses it. |
| Safety flags | Panel | Zero to four chips for the current Decision; each chip's text states the flag; color follows the flag's verdict. Absent when there are none. |
| Belief summary | Panel | Unknown items with their top candidate and probability (top three items by relevance), then floor facts and chapter counters. Updates every Input wait regardless of who acted. Clicking an item shows all its candidates (v1) or opens the Beliefs view (v2). |
| Decision log | Panel | One line per Input wait: turn, actor (bot or human), Action, score; Goal changes and Mode changes as their own lines. Newest at the bottom, auto-scroll while at the bottom, manual scroll pauses auto-scroll. Retains the last 200 lines on screen; the full record is the Run log. |
| Controls | Panel | Buttons in one row, wrapping to two: Pause/Resume, Next Step, Run N (with its count stepper), Speed selector (with the interval stepper), Take over/Hand back, Explain. A control is disabled (dim, non-interactive) whenever its action is impossible in the current state; the enablement matrix is in State Patterns. |
| Speed selector | Controls | Three tabs; the selected tab is the current speed mode; changing it applies at the next Input wait. |
| Map highlights | Dungeon view | Path, target, considered cells for the current Decision; drawn when the Decision is made, cleared when the hero acts or the plan changes; never drawn in HUMAN mode. |
| Oracle border | Whole game view | Drawn only when launched with the oracle flag; cannot be toggled from the Overlay; the Mode strip carries the `ORACLE` label. |
| Thinking indicator | Mode strip | `THINKING` appears when the Brain has been computing longer than the configured per-Input-wait budget; the Panel shows the previous Decision until the new one lands. |

## State Patterns

| State | Where | Treatment |
|---|---|---|
| RUNNING | Mode strip green | Bot acts per the speed mode. Enabled: Pause, speed selector, Take over, Explain, Panel toggle. Next Step is enabled and acts as Pause (the next press steps). Run N disabled. |
| PAUSED | Mode strip amber | Bot does not act; the hero's game input is ignored (the game's own controls do nothing until Take over); Decision card shows the pending Decision. Enabled: Resume, Next Step, Run N, speed selector, Take over, Explain, Panel toggle. Entered at launch, by Pause, by Next Step mode between presses, by Run N completing, by Hand back, by resume from save, and by a Pause-on condition (v2). |
| HUMAN | Mode strip blue | The human plays with the game's normal controls; the Brain observes and updates Beliefs every Input wait; the Decision card shows what the bot *would* do, greyed; map highlights hidden. Enabled: Hand back, Explain, Panel toggle; everything else disabled. Human Actions are logged with actor `human`; an input the Action set cannot express is logged as `human unsupported` and ends Replay-verifiability from that point (PRD FR-4). |
| THINKING | Mode strip suffix | Brain over its per-Input-wait budget; previous Decision stays; a Next Step press is queued and fires when the Decision lands. |
| Brain error | Decision card | The Brain threw; the card reads `brain error: <class>` in danger color, the Decision is `wait`, the log records it, and the Overlay enters PAUSED. The game never crashes because of the Brain. |
| Hero busy | Controls | Between Input waits (animation running, multi-turn action resolving) Mode changes are refused visibly: the pressed control stays dim until the hero is ready, then the change applies. |
| No valid action | Decision card | Card reads `no valid action; waiting`; the bot waits once; if it recurs three times in a row the Overlay enters PAUSED and logs it. |
| Run over (hero dead or victorious) | Panel | Mode strip reads `RUN OVER` with the cause; the Decision log stays readable and scrollable; controls disabled except the Panel toggle; the Run log path is shown. |
| No Run (title, menus, loading) | Panel | Nothing is drawn; the Overlay attaches when the game scene appears with a living hero. |
| Save and resume | Panel | On the game's save-and-quit the Overlay writes the boundary to the Run log; on resume through the launcher it re-attaches, re-plans from the current Observation, and starts PAUSED with speed mode Next Step regardless of the previous Mode and speed mode (stepper values are session-only and do not survive); a save opened without the launcher is not an Overlay Run. |
| Oracle | Border and label | Everything above applies; additionally, oracle data appears as Oracle marking specifies; the Rig is never involved. |
| Panel collapsed | Mode strip | All information except the strip is hidden; hotkeys still work; the strip's toggle restores the Panel. |

## Interaction Primitives

**Keyboard-first, mouse-complete.** Every control is an `SPDAction` with a default key, rebindable in the game's settings screen. Every control is also a button, so if the key-binding Hook turns out larger than expected, v1 ships buttons only and the hotkeys move to E8 (PRD FR-42). Defaults avoid the game's own bindings (WASD and arrows, F1 to F5 bags, number keys for quickslots, I, F). Defaults: F6 Next Step; F7 cycle speed mode; F8 Pause/Resume; F9 Take over/Hand back; F10 Explain; F11 toggle Panel. The pinned tag binds nothing to F6 through F12 and accepts their key codes (`docs/rules/ui.md`).

| Control | Behavior | Default key |
|---|---|---|
| Next Step | One Input wait; in RUNNING it pauses first (one press to pause, the next to step) | F6 |
| Run N | N Input waits at Human play speed, then PAUSED; the count stepper sits beside the button | Button only |
| Pause/Resume | Toggles RUNNING and PAUSED at the next Input wait | F8 |
| Take over/Hand back | Toggles HUMAN; Hand back returns to PAUSED (never straight to RUNNING), so the human sees the bot's next Decision before it acts | F9 |
| Steppers | The Run N count and the Human play speed interval, adjusted with small plus and minus buttons beside their controls; values persist for the session only | Buttons only |
| Speed mode | Cycles Next Step, Human, Fast; the selector tab shows the current one | F7 |
| Explain | Expands or collapses the Decision card | F10 |
| Panel toggle | Collapses or restores the Panel | F11 |

Mouse: click any button; click a Belief row to expand it; scroll the Decision log; click the Mode strip to toggle the Panel.

Banned: any control that acts while the hero is mid-animation; any Overlay key that shadows a game binding; hover-only affordances (the game has none).

## Interjection semantics (invented section)

- In HUMAN, the game's normal controls act for the human and the Overlay never intercepts them. In PAUSED the game's controls do nothing until Take over (State Patterns, PAUSED row); this prevents accidental moves while the human reads a Decision.
- The Brain observes every Input wait regardless of actor and updates Beliefs; it never assumes its previous Decision was executed (PRD FR-27, FR-40).
- Every human Action is written to the Run log with actor `human`, so the Replay reproduces the Run exactly; the Mode strip's turn counter and the log agree at all times.

## Oracle marking (invented section)

Oracle mode is a launcher flag, not an Overlay control. When set: the oracle border and `ORACLE` label (`DESIGN.md` Components, Oracle border); true identities shown in the Belief summary with an `oracle` prefix; unseen enemies outlined on the map in the oracle color (`DESIGN.md` Components, Map highlight). Nothing else in the Overlay changes. Nothing in the Overlay can turn it on or off at runtime, and no Results, Run log, or Rig run produced under it counts as ranked (PRD FR-11, FR-43).

## Accessibility Floor

Behavioral; contrast and color meaning live in `DESIGN.md`.

- Every state and flag is stated in words; color never carries meaning alone.
- Every control is keyboard-operable and rebindable; the mouse is never required.
- The Panel respects the game's interface-scale setting; the collapse triggers are the Responsive & Platform table.
- The Decision log and the Run log are plain text; a screen reader or a script can read a Run without the Panel.

## Responsive & Platform

Desktop only (the game's desktop build; the Overlay is not built for the mobile targets). Within the desktop window:

| View at the right edge | Behavior |
|---|---|
| Uncovered map at least 200 UI px wide and view at least 200 tall, Panel at 160 to 200 wide (`DESIGN.md` Layout) | Full Panel, sections in fixed order; the Decision log flexes to the remaining height, never fewer than three lines |
| Less than that, or the human collapsed it | Mode strip only; hotkeys unchanged |
| Interface size set to mobile layout | Mode strip only; the full Panel needs the full desktop layout |
| 1280 by 720 window at interface scale 3 (427 by 240 UI px) | Mode strip only; the full Panel needs scale 2 or a larger window (mock geometry, Tier 3) |

The Panel never changes the game's own HUD layout; placement, translucency, and the camera offset are in `DESIGN.md` Layout & Spacing.

## Inspiration & Anti-patterns

- **Lifted from tool-assisted speedrun input displays:** the frame-step discipline; the current input and the next input always visible; the human advances time.
- **Lifted from chess engine GUIs:** the evaluation and the top alternatives with scores, always in the same place; the engine never acts on the board without the human's mode allowing it.
- **Lifted from the game's own HUD:** translucent tags, the pixel font, red buttons; the instrument sits beside the game's status pane without competing with it.
- **Rejected: a windowed dialog for the Overlay.** The game's `Window` frame implies modal; the instrument is ambient.
- **Rejected: showing the bot's reasoning as prose.** Labels and numbers scan; sentences do not.
- **Rejected: an Overlay-owned settings screen.** The game's settings and the launcher flags are enough; a second settings surface would be the first thing to drift.

## Key Flows

### Flow 1 — Bash steps the bot through a fight (Next Step)

1. Bash launches the game through the Shatterfish launcher with a seed; the launcher gives the Run its own Profile. The dungeon scene appears; the Panel docks at the right in PAUSED, speed mode Next Step, the first Decision on the card: `move 14,7  0.64  unexplored frontier`.
2. Bash presses F6. The hero moves one turn. The log adds `t1 bot move 14,7 0.64`. The card shows the next Decision.
3. A gnoll scout appears. The Goal line changes to `Fight: gnoll scout, corridor two cells east`; the log records the Goal change; the card's alternatives now include `retreat 12,7  0.51  corridor`; a Safety flag reads `ok: fighting in corridor`.
4. Bash presses Explain. The card expands: Policy `fight-in-corridors`, the three alternatives' full reasons, the flags that applied.
5. **Climax:** Bash presses F6 four times and watches the fight resolve exactly as the card predicted each turn, reading each Decision before it happens.
6. Bash switches the speed selector to Human and presses Resume; the bot continues at one turn per second until Bash pauses again.

**Failure path:** at step 5 the Brain exceeds its budget; the Mode strip shows `THINKING`; the F6 press is queued and fires when the Decision lands; nothing is lost.

### Flow 2 — Bash takes over mid-fight and hands back

1. RUNNING at Human play speed on floor 3. Two gnolls close in; Bash disagrees with the bot's plan.
2. Bash presses F9. The Mode strip turns blue `HUMAN`; map highlights vanish; the Decision card greys and shows what the bot would do.
3. Bash plays three turns with the arrow keys and drinks a potion from the quickslot; each turn the log records `human ...` and the Belief summary updates (the potion identifies; its candidates collapse).
4. Bash presses F9 again. Mode becomes PAUSED with a fresh Decision on the card, re-planned from the current state.
5. **Climax:** Bash presses Resume; the bot continues from the new position with no stale plan and no desync. The Run log replays exactly later.

**Failure path:** Bash presses F9 mid-animation; the button stays dim until the hero is ready, then the Mode changes.

### Flow 3 — Fast run to a death, then reading the log

1. Bash selects Fast as it can and presses Resume. The Panel updates a few times a second; the Decision log streams.
2. The hero dies on floor 5 to Goo. The Mode strip reads `RUN OVER: killed by Goo, turn 1,842, floor 5`.
3. **Climax:** Bash scrolls the Decision log back to the last 20 turns and reads the Decisions that led there; the Run log path is shown for the Replay and the Rig's death gallery.

### Flow 4 — Oracle debugging

1. Bash launches with the oracle flag to debug a Belief bug. The red border and `ORACLE` label appear immediately.
2. The Belief summary shows `oracle: turquoise potion = healing` beside the Brain's own `healing 0.35 / strength 0.20`.
3. **Climax:** Bash steps until the Brain's candidates diverge from the oracle truth in a way that reveals the bug, notes the turn, and quits; nothing from this Run enters the Rig.

### Flow 5 — A learner plays with the coach (v2)

1. A player enables coach mode from the Controls. The bot never acts.
2. Before each of their moves the Decision card shows what the bot would do and why, in the game's words and at most one plain sentence (the coach is the one place the instrument may speak a sentence).
3. **Climax:** they read `read scroll (KHIT)  0.71  by door and water, worst case survivable`, understand the reasoning, and do it themselves.
4. They enable autoexplore for a corridor; the bot walks it and returns control when a new enemy appears.
