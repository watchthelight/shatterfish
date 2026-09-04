---
name: Reconciliation — UX spines and the codebase map against the architecture spine
type: architecture-review
status: draft
created: '2026-09-03'
inputs:
  - _bmad-output/planning-artifacts/ux-designs/ux-shatterfish-2026-09-03/EXPERIENCE.md
  - _bmad-output/planning-artifacts/ux-designs/ux-shatterfish-2026-09-03/DESIGN.md
  - docs/codebase-map.md (Discrepancies with the bootstrap prompt; PRD open question 12; What the architecture must absorb)
spine:
  - _bmad-output/planning-artifacts/architecture/architecture-shatterfish-2026-09-03/ARCHITECTURE-SPINE.md
  - docs/adr/0005-observation-schema-and-hashing.md .. docs/adr/0013-overlay-threading-model.md
---

# Reconciliation — UX spines and the codebase map against the architecture spine

## Method

Every state, control, speed mode, component rule, flow step, failure path and layout rule in
`EXPERIENCE.md` and `DESIGN.md` that needs engine support is one row below, with the AD or ADR
that supplies it. Every bullet under "What the architecture must absorb" and every row of the
discrepancies table in `docs/codebase-map.md` is one row in Part 3.

Verdicts:

- **Housed** — an AD or an ADR supplies the mechanism, not merely the FR number.
- **Partial** — the intent is bound (usually by AD-12 through FR-37..FR-47) but the mechanism,
  the data field, or the enforcement is unstated; or the mechanism is stated and is incomplete.
- **None** — nothing in the spine or ADR-0005..0013 supplies it.

Binding an FR is not the same as supplying a mechanism: AD-12 binds FR-37 to FR-47 wholesale, so
"AD-12 binds it" alone is recorded as Partial.

## Summary

| Input | Items checked | Housed | Partial | None |
|---|---|---|---|---|
| EXPERIENCE.md | 93 | 63 | 20 | 10 |
| DESIGN.md | 20 | 15 | 4 | 1 |
| **UX total** | **113** | **78** | **24** | **11** |
| codebase-map "What the architecture must absorb" | 10 | 9 | 0 | 1 |
| codebase-map discrepancies table | 21 | 20 | 1 | 0 |
| codebase-map "PRD open question 12" (context only) | 10 | 10 | 0 | 0 |

The eleven UX rows with no home are **nine distinct gaps**: E38 and D14 are the same gap as E11
(map highlights), counted once. Four of the nine are load-bearing for E5 (the multi-cell move,
PAUSED input suppression, the HUMAN card, RUN OVER); one more, from the codebase map, is
load-bearing for E1 (the sealed level, C7); two are v2 questions the v1 architecture does not
preclude but leaves no room for (the settings-screen hook, the Decision's map cells); and one
(E25, the Panel refresh rate in Fast as it can) is minor and needs no engine support.

## Part 1 — EXPERIENCE.md

### Foundation

| # | Item | Needs from the engine | Home | Verdict |
|---|---|---|---|---|
| E1 | Overlay built only from the game's toolkit (`Component`, `Chrome`, `renderTextBlock`, `RedButton`, `Icons`, `ScrollPane`, `SPDAction`) | a Noosa `Component` in the scene | AD-12 | Housed |
| E2 | The game owns the render thread; every Overlay write is posted to it | thread roles and assertions | AD-8; ADR-0013 (roles table); spine Threads convention | Housed |
| E3 | The Brain thinks on a worker thread over an immutable Observation | worker plus a polled future, no game object | AD-7; AD-8; ADR-0013 | Housed |
| E4 | The Overlay appears only while the game scene is active with a living hero; absent on the title screen, in menus, after death | attach and detach on scene create and destroy | ADR-0013 ("the driver re-attaches through the scene seam hook each time"); ADR-0008 row 3 | Housed |
| E5 | Glossary terms used exactly as the PRD defines them | names are types | spine Consistency Conventions (Naming) | Housed |

### Information architecture (surfaces)

| # | Item | Needs from the engine | Home | Verdict |
|---|---|---|---|---|
| E6 | Panel docked at the right edge whenever a Run is in progress | attachment and HUD geometry | AD-12; ADR-0008 rows 3 and 4 | Housed |
| E7 | Mode strip: Mode, speed mode with its interval, turn, Floor | the turn is **not** in the Observation (AD-2 excludes it); the Floor is `header.depth`/`branch` | AD-5 (the Run log carries `Statistics.duration + Actor.now()` in fixed-point thousandths); ADR-0005 header | Housed — the Panel must read the turn from the harness, never from the Observation |
| E8 | Explain expansion: the Policy that fired, the alternatives' reasons in full, the Safety flags that applied | a `Decision` value carrying policy, per-alternative `why`, flags | ADR-0011's `wait.decision` object defines the fields; no ADR defines the `api` `Decision` record beyond AD-7's signature | Partial |
| E9 | Run N count and the Human-play-speed interval as Panel steppers, session-only | scheduling of N waits and of an interval | ADR-0013 Speed modes; the spine's Config row (launcher flags and Rig CLI only) leaves session-local Panel values uncontradicted | Housed |
| E10 | Pause-on conditions in an Overlay section of the game's settings screen (v2) | one upstream hook (FR-45) | nothing: ADR-0008's ledger is full at eight rows and says a ninth forces an ADR | **None** |
| E11 | Map highlights drawn in the dungeon view (path, target, considered cells) | (a) a Decision field carrying cells, (b) a world-space draw layer under the HUD | attachment only, via ADR-0008 row 3; ADR-0011's `decision` object has no cell fields; no AD names a `DungeonTilemap`-space layer | **None** |
| E12 | Every Overlay control is an `SPDAction`, rebindable in the game's own settings screen | `SPDAction` is a `core` enum and `WndKeyBindings` lists its members, so new actions are an upstream edit | AD-12 asserts it; ADR-0008's ledger has no row for it | **None** |
| E13 | Launcher: starts the game with the Overlay; the oracle flag lives here and nowhere else | Profile ownership; `OracleObserver` construction | AD-12; AD-6 and ADR-0007 (a fresh standard Profile per Run); ADR-0006 ("constructed only by the launcher flag"); ADR-0012 (the Rig refuses it) | Housed |
| E14 | Replay scrubber over a loaded Run log (v2) | a replay driver and a per-wait record | ADR-0011 (`ReplayDriver`; "the Overlay's Decision log is a view over the same records"); ADR-0009 (the scrubber in E6) | Housed — v1 does not preclude it |
| E15 | Coach mode as a speed-mode-like toggle (v2) | decide without executing; a fourth mode | AD-7 (`decide` is separate from execution) and AD-4 (execution is the only path to the hero) permit it; ADR-0013's mode set has three modes and its per-wait sequence runs `update` **or** `decide` (see E44) | Partial |

### Stepping model

| # | Item | Needs from the engine | Home | Verdict |
|---|---|---|---|---|
| E16 | An Input wait is a hero turn or an open Prompt (subclass, talent, quest, shop, alchemy) | the wait predicate | AD-5; ADR-0006 Prompt row; ADR-0013 Input-wait detection | Housed |
| E17 | "alchemy dialog" counted as a Prompt | `AlchemyScene` is a scene switch that destroys the `GameScene` and its actor thread (codebase-map, game loop), not a `Window` | ADR-0006 lists "alchemy" among the Prompt kinds; ADR-0013 handles scene destruction only for `InterlevelScene` and RUN OVER | Partial — the two documents disagree on what alchemy is |
| E18 | Exactly one Observation, Decision, Action, Run-log record and RNG reseed per Input wait; Next Step advances exactly one | the `k` primary key | AD-5; ADR-0007; ADR-0011 | Housed |
| E19 | A multi-cell move is still one Decision per Input wait, re-planned each time, so the human can interrupt at any cell | an Input wait between cells | nothing. `Hero.actMove` calls `getCloser` and returns with `curAction` still set, so `ready()` is not reached until the destination or an interrupt (`…/actors/hero/Hero.java:889-890`, `:977-995`), and ADR-0013 detects waits from `Hero.ready()` | **None** |
| E20 | A Run starts in PAUSED with speed mode Next Step | the initial mode | AD-12 binds FR-39; no AD or ADR states the initial mode | Partial |
| E21 | Next Step mode: the bot acts only on the press; the card shows what the next press will do | the future polled, held until the key | ADR-0013 Speed modes | Housed |
| E22 | Run N: N Input waits at Human play speed, then PAUSED (default 10, range 1 to 999) | scheduling and the landing transition | ADR-0013 Speed modes | Housed |
| E23 | Human play speed: one Input wait per interval (default 1 s, 0.5 to 5 s), never exceeding the game's animation speed | a clock on the render thread; the next wait cannot arrive before the hero is ready | ADR-0013 ("schedule the execution `interval` seconds later on the render thread's clock") | Housed |
| E24 | Fast as it can: uncapped, animation the ceiling unless a hook bypasses sprite waits | the sprite-wait bypass | ADR-0013 (executes on the frame the future completes; at most one Action per frame); ADR-0008 row 7 (`CharSprite` motion interval), FR-39 | Housed |
| E25 | In Fast as it can the Panel updates at most a few times per second so it stays readable | a Panel refresh throttle decoupled from the wait rate | nothing (ADR-0013 caps Actions per frame, not Panel writes) | **None** (minor; Overlay-internal, needs no engine support) |
| E26 | Switching speed mode never loses a turn; the switch takes effect at the next Input wait | deferral of the change to the next wait | ADR-0013 states this for Take over and Hand back; AD-5 makes the wait the unit; the speed-mode switch is not named | Partial |
| E27 | Item and mob names exactly as the game shows them | display strings in the Observation | ADR-0005 (`title()` and display names); ADR-0006 Items and Mobs rows | Housed |

### Component patterns

| # | Item | Needs from the engine | Home | Verdict |
|---|---|---|---|---|
| E28 | Panel: fixed section order; updates only at Input waits and on Mode changes; collapses | update cadence tied to `k` | AD-5; AD-12 | Housed |
| E29 | Mode strip carries the `THINKING` and `ORACLE` suffixes | a budget label; the oracle flag | ADR-0013 (a budget overrun sets `THINKING` and nothing else); ADR-0005 `header.oracle`; ADR-0006 | Housed |
| E30 | Goal line changes only when the Brain's Goal changes; the change is written to the Decision log | `goal` per wait | ADR-0011 (`decision.goal`); a change is derivable between consecutive waits | Housed |
| E31 | Decision card: the chosen Action with its score, then up to three alternatives with scores and one-line reasons | scores and reasons | ADR-0011 (`chosen {action, score}`, `alternatives` at most three each with `why`; scores integers in ten-thousandths) | Housed |
| E32 | Safety flags: zero to four chips, colour follows the flag's verdict | a verdict (ok / warn / danger) per flag | ADR-0011 logs `flags`; the spine names the `SafetyFlag` type; no ADR gives a flag a verdict field | Partial |
| E33 | Belief summary: top three unknown items with candidate and probability, floor facts, chapter counters; click to see all candidates | a readable projection of `Belief` in `api` | AD-7 makes `Belief` an `api` value the Overlay may read (it depends on `brain` and `api`); no ADR defines the projection, and ADR-0011 logs only its SHA-256 unless `--log-beliefs` | Partial |
| E34 | The Belief summary updates every Input wait regardless of who acted | `update` on human waits | AD-7 (`Brain.update`); ADR-0013 (per-wait sequence, human branch) | Housed |
| E35 | Decision log: one line per Input wait with turn, actor, Action, score; Goal and Mode changes as their own lines; 200 lines on screen; the full record is the Run log | per-wait and mode records | ADR-0011 (`wait` with `actor`; `mode` records; "the Overlay's Decision log is a view over the same records") | Housed |
| E36 | Controls row with the enablement matrix (a control is dim when its action is impossible) | mode and wait state | ADR-0013 (modes, hero-busy); Overlay-internal otherwise | Housed |
| E37 | Speed selector: three tabs, the change applies at the next Input wait | see E26 | ADR-0013, partially | Partial |
| E38 | Map highlights drawn when the Decision is made, cleared when the hero acts or the plan changes, never in HUMAN | see E11 | — | **None** (same gap as E11) |
| E39 | Oracle border over the whole game view; cannot be toggled from the Overlay | the flag set once at launch | ADR-0006 (`OracleObserver` constructed only by the launcher flag); AD-12 (the launcher owns the flag) | Housed |
| E40 | Thinking indicator when the Brain exceeds the per-Input-wait budget; the previous Decision stays | a label, never a wait; the computation is never cancelled | ADR-0013 (option 6 rejected explicitly); AD-7 ("the Overlay's thinking budget delays a Decision and never changes it") | Housed |
| E41 | The per-Input-wait thinking budget's own configuration in the Overlay | where the number comes from | the spine's Config row lists launcher flags and the Rig CLI; ADR-0012 makes the per-Decision budget a Registration field for the Rig only | Partial |

### State patterns

| # | Item | Needs from the engine | Home | Verdict |
|---|---|---|---|---|
| E42 | RUNNING: the bot acts per the speed mode; Next Step acts as Pause; Run N disabled | the mode machine | ADR-0013 | Housed |
| E43 | PAUSED: the hero's game input is ignored — the game's own controls, including the toolbar and the quickslots, do nothing until Take over | suppression of pointer **and** keyboard input to the hero | ADR-0013 names a mechanism (the Panel installs its own `CellSelector.Listener`; the toolbar and inventory pane set inactive through the accessor row) that is **insufficient**: `CellSelector`'s own key listener calls `Dungeon.hero.handle(cell)` from `moveFromActions` without consulting `listener` or `enabled` (`…/scenes/CellSelector.java:275-330`, `:395-420`), and each `Button` key listener tests its **own** `active` field (`…/ui/Button.java:117-125`), which setting a parent `Group` inactive does not change | **None** (mechanism stated, does not cover the keyboard paths) |
| E44 | HUMAN: the Decision card shows what the bot *would* do, greyed | a Decision on a human wait, alongside the Belief update | nothing: ADR-0013's per-wait sequence submits `decide(obs, belief)` (bot turn) **or** `update(obs, belief)` (human turn); AD-7 defines both but nothing runs both on one wait. The same gap blocks coach mode (E15) | **None** |
| E45 | HUMAN: the game's controls restored; human Actions logged with actor `human`; an inexpressible input logged as `human unsupported`, ending Replay-verifiability | listener restore; `Hero.curAction` after `handle`; the `Item.execute` notification | ADR-0013 (Modes); ADR-0008 row 3 second site; ADR-0011 (`unsupported` record; `verifiable` false from there) | Housed |
| E46 | HUMAN: map highlights hidden; everything but Hand back, Explain and the Panel toggle disabled | Overlay-internal over the mode | ADR-0013 | Housed |
| E47 | THINKING: a Next Step press is queued and fires when the Decision lands | a non-blocking poll and a latch | ADR-0013 (the future is polled each frame, never blocked on, never cancelled); the latch is Overlay-internal | Housed |
| E48 | Brain error: the card reads `brain error: <class>`, the Decision is `wait`, it is logged, the Overlay enters PAUSED; the game never crashes | an exception class in the log record | the spine's Errors convention states the behaviour; ADR-0011's `wait.decision` has no field for the exception class | Partial |
| E49 | Hero busy: Mode changes are refused visibly and applied when the hero is ready | deferral to the next Input wait | ADR-0013 ("Take over and Hand back apply at the next Input wait"); AD-5 | Housed |
| E50 | No valid action: the card reads `no valid action; waiting`; three in a row enters PAUSED and logs it | an empty valid-Action set | AD-4 and ADR-0005 `actions` section; `Decision.wait`; the counting is Overlay-internal | Housed |
| E51 | RUN OVER (hero dead or victorious): the Mode strip reads `RUN OVER` with the cause; controls disabled; the Run log path shown | a run-over transition and a cause string | **partial and wrong**: ADR-0011's `end` record carries `outcome` and `cause`, but ADR-0013 models RUN OVER as "the scene being destroyed and recreated (`InterlevelScene`)", which is not the death path — death shows `WndResurrect` (`…/actors/hero/Hero.java:2169-2176`) or runs `GameScene.gameOver()` (`:2256`), and `WndResurrect` is not one of ADR-0006's Prompt kinds, so observing at that moment is an assertion failure. No ADR says where `cause` is read | **None** |
| E52 | No Run (title, menus, loading): nothing is drawn; the Overlay attaches when the scene appears with a living hero | attach on scene creation | ADR-0013; ADR-0008 row 3 | Housed |
| E53 | Save and resume: the boundary is written to the Run log; a resume through the launcher re-attaches, re-plans, and starts PAUSED with Next Step; steppers are session-only; a save opened without the launcher is not an Overlay Run | a Run-log record kind for the boundary; continuity of `k`, the salt and the hash chain across the resume | nothing. ADR-0011's record kinds are `header`, `wait`, `prompt`, `mode`, `unsupported`, `end` — none is a save boundary — and ADR-0007 reseeds from `mix(salt, k)` with no rule for recovering the salt or `k` on resume. PRD open question 10 defers only the *Replay* question, not the log and reseed continuity | **None** |
| E54 | Oracle: everything above applies plus the oracle marking; the Rig is never involved | the sidecar; the ranked refusal | ADR-0006 (`OracleView`); ADR-0011 (`header.oracle`); ADR-0012 (the Rig refuses `oracle` true) | Housed |
| E55 | Panel collapsed: only the strip is drawn; hotkeys still work; the strip's toggle restores it | Overlay-internal | AD-12 | Housed |

### Interaction primitives

| # | Item | Needs from the engine | Home | Verdict |
|---|---|---|---|---|
| E56 | Defaults F6 Next Step, F7 speed cycle, F8 Pause/Resume, F9 Take over/Hand back, F10 Explain, F11 Panel toggle; the tag binds nothing to F6 through F12 and accepts their key codes | a verified fact | codebase-map "PRD open question 12" row; `docs/rules/ui.md`; AD-12 names the same defaults (registration is E12's gap) | Housed |
| E57 | Fallback: if the key-binding Hook is larger than expected, v1 ships buttons only and hotkeys move to E8 | a sizing decision and a trigger | nothing sizes it; ADR-0008 has no row to size (E12) | Partial |
| E58 | Next Step in RUNNING pauses first (one press to pause, the next to step) | the mode machine | ADR-0013 | Housed |
| E59 | Take over / Hand back toggles HUMAN; Hand back returns to PAUSED, never straight to RUNNING | the transition at the next wait | ADR-0013 (Modes) | Housed |
| E60 | Steppers adjust the Run N count and the interval; values persist for the session only | see E9 | ADR-0013; spine Config | Housed |
| E61 | Banned: any control that acts while the hero is mid-animation | hero-busy deferral | ADR-0013; AD-5 | Housed |
| E62 | Banned: any Overlay key that shadows a game binding | a check that the defaults are free | the fact is verified (E56); no test or enforcement is named in any AD | Partial |
| E63 | Banned: hover-only affordances | not an engine concern | — | Housed (n/a) |
| E64 | Mouse: click a button, click a Belief row to expand it, scroll the Decision log, click the Mode strip | Noosa input; the Belief row needs E33's projection | AD-12; Overlay-internal | Housed |

### Interjection semantics

| # | Item | Needs from the engine | Home | Verdict |
|---|---|---|---|---|
| E65 | In HUMAN the Overlay never intercepts the game's controls; in PAUSED the game's controls do nothing until Take over | see E43 | ADR-0013 (mechanism incomplete) | Partial |
| E66 | The Brain observes every Input wait regardless of actor and never assumes its previous Decision was executed | purity | AD-7 (a pure function of the Observation; a Decision is tagged with `k` and a stale one is never executed); ADR-0013 | Housed |
| E67 | Every human Action is in the Run log with actor `human`; the Mode strip's turn and the log agree at all times | one turn source | ADR-0011 (`actor`); AD-5 (the turn is a Run-log field) | Housed |

### Oracle marking

| # | Item | Needs from the engine | Home | Verdict |
|---|---|---|---|---|
| E68 | Oracle is a launcher flag, not an Overlay control; nothing can toggle it at runtime | construction-time only | ADR-0006; AD-12 | Housed |
| E69 | True identities in the Belief summary with an `oracle` prefix | the sidecar | ADR-0006 (`OracleView`, consumed only by the Overlay's oracle marking and the E9 labelling tool) | Housed |
| E70 | Unseen enemies outlined on the map in the oracle colour | sidecar data plus the map draw layer | ADR-0006 supplies the data; the draw layer is E11's gap | Partial |
| E71 | No Results, Run log or Rig run produced under oracle counts as ranked | the refusal | AD-11; ADR-0012; ADR-0005 (`header.oracle` makes the hashes differ) | Housed |

### Accessibility floor

| # | Item | Needs from the engine | Home | Verdict |
|---|---|---|---|---|
| E72 | Every state and flag is stated in words; colour never carries meaning alone | strings, not colours, in the record | ADR-0011 (strings are the Observation's own display strings); Overlay-internal | Housed |
| E73 | Every control keyboard-operable and rebindable; the mouse is never required | see E12 | AD-12 asserts it; no hook row supplies it | Partial |
| E74 | The Panel respects the game's interface-scale setting | `PixelScene.defaultZoom` | codebase-map ui row; DESIGN Layout | Housed |
| E75 | The Decision log and the Run log are plain text; a screen reader or a script can read a Run without the Panel | a readable log | ADR-0011 (gzip JSONL — plain text after `gunzip`, canonical JSON per record) | Partial (the log is gzipped, not plain text on disk) |

### Responsive and platform

| # | Item | Needs from the engine | Home | Verdict |
|---|---|---|---|---|
| E76 | Desktop only; the Overlay is not built for the mobile targets | module wiring | ADR-0008 row 1 (`settings.gradle`, mobile opt-in); spine Structural Seed | Housed |
| E77 | Full Panel when the uncovered map is at least 200 UI px wide and the view at least 200 tall; Panel 160 to 200 wide; the log flexes, never fewer than three lines | measured geometry | AD-12 (placed per DESIGN Layout); DESIGN carries `[ASSUMPTION: the E5 layout story confirms the collapse thresholds]` | Partial |
| E78 | Below that, or when the human collapsed it: Mode strip only, hotkeys unchanged | Overlay-internal | AD-12 | Housed |
| E79 | Interface size set to mobile layout: Mode strip only | the scale fact | codebase-map ui row | Housed |
| E80 | 1280 by 720 at interface scale 3 (427 by 240 UI px): Mode strip only | verified geometry | codebase-map "PRD open question 12" (`defaultZoom` is 3 there) | Housed |
| E81 | The Panel never changes the game's own HUD layout | AD-12's stated prevention ("an Overlay that edits the HUD") | AD-12 | Housed |

### Key flows

| # | Item | Needs from the engine | Home | Verdict |
|---|---|---|---|---|
| E82 | Flow 1.1 — the launcher gives the Run its own Profile; the Panel docks in PAUSED with the first Decision on the card | a fresh standard Profile per Run | AD-6; ADR-0007 (the Profile versioned in `harness` resources); FR-37's "two Overlay Runs never share a Profile" | Housed |
| E83 | Flow 1.2 to 1.4 — F6 steps one turn; the log line appears; the Goal changes and is recorded; a Safety flag appears; Explain expands in place | E18, E30, E31, E32, E8 | ADR-0011; ADR-0013 | Housed |
| E84 | Flow 1.5 (climax) — four presses resolve a fight exactly as each card predicted | a Decision stable for its wait, never executed stale | AD-7 (a Decision is tagged with `k`; a stale one is logged as skipped and never executed) | Housed |
| E85 | Flow 1.6 — switching the selector to Human and pressing Resume continues at one turn per second | see E23, E26 | ADR-0013 | Housed |
| E86 | Flow 1 failure path — the F6 press under THINKING is queued and nothing is lost | see E47 | ADR-0013 | Housed |
| E87 | Flow 2.2 — Take over mid-fight: the strip turns blue, highlights vanish, the card greys and shows what the bot would do | see E44 for the greyed card's content | ADR-0013 for the mode; nothing for the card | Partial |
| E88 | Flow 2.3 — the human plays three turns and drinks a quickslot potion; each turn is logged and the Beliefs update (the potion identifies, its candidates collapse) | human Action capture through `Item.execute` | ADR-0008 row 3 (second site); ADR-0013 (Modes); AD-7 (`update`) | Housed |
| E89 | Flow 2.4 and 2.5 — Hand back lands in PAUSED with a fresh Decision re-planned from the current state; Resume continues with no stale plan and the Run replays exactly | a bot wait immediately after Hand back; Replay over mixed actors | ADR-0013 (Hand back applies at the next Input wait, which is then a bot wait); ADR-0011 (Replay applies the logged Action whoever produced it) | Housed |
| E90 | Flow 2 failure path — F9 mid-animation stays dim until the hero is ready | see E49 | ADR-0013 | Housed |
| E91 | Flow 3 — Fast run to a death; `RUN OVER: killed by Goo, turn 1,842, floor 5`; the log stays scrollable; the Run log path is shown for the Replay and the Rig's death gallery | see E51 for the transition and the cause | ADR-0011 (log path, `end.cause`); ADR-0009 (death-gallery snapshots on request, FR-25); the transition is E51's gap | Partial |
| E92 | Flow 4 — oracle debugging: the border appears immediately, `oracle:` sits beside the Brain's own candidates, nothing enters the Rig | E68 to E71 | ADR-0006; ADR-0012 | Housed |
| E93 | Flow 5 (v2) — coach mode advises before each human move; autoexplore hands quiet stretches to the bot and returns control when an enemy appears | a Decision on a human wait (E44); a fourth mode | AD-7 permits it; ADR-0013's per-wait sequence and mode set do not carry it | Partial |

### v2, checked for preclusion

- **Pause-on conditions (FR-45)** — not precluded in principle (every condition is readable from the
  Observation and from the Mode machine), but the settings-screen hook has no room in ADR-0008's
  eight-row budget (E10).
- **Replay scrubber and Beliefs view (FR-46)** — not precluded: ADR-0011 makes the Decision log a
  view over the Run log and ADR-0009 puts the scrubber in E6; the Beliefs view still needs the
  `Belief` projection of E33.
- **Coach mode and autoexplore (FR-47)** — not precluded by AD-4 or AD-7, but both need the
  "decide on a human wait" that E44 leaves unhoused, plus a fourth mode in ADR-0013.
- **Explain view (FR-44)** — not precluded: ADR-0010's search outcomes and ADR-0011's `decision`
  object extend without a structural break (the log's `v` bumps, and a Replay refuses a
  different `v`, which is the intended behaviour).

## Part 2 — DESIGN.md

| # | Item | Needs from the engine | Home | Verdict |
|---|---|---|---|---|
| D1 | Toolkit only: "the build fails on anything else"; no Swing, JavaFX, ImGui or web view on the classpath (FR-38) | a dependency or ArchUnit rule over `overlay` | AD-12 states the rule; AD-1's ArchUnit rules cover `brain` only, and no AD assigns the `overlay` check | Partial |
| D2 | Colours applied as tints through the toolkit's hardlight and tint calls; dark surfaces only | Overlay-internal | AD-12 | Housed |
| D3 | One font through `renderTextBlock` at 9 / 8 / 6 UI px | the platform TTF at sizes the game itself uses | codebase-map ui discrepancy row; `docs/rules/ui.md` | Housed |
| D4 | `_highlight_` markup allowed only for the chosen Action's name | `RenderedTextBlock` markup | codebase-map ui row | Housed |
| D5 | Numbers right-aligned in fixed-width columns; alignment by column position, never by padding characters | Overlay-internal | — | Housed |
| D6 | UI-pixel grid 1/2/4/6/8, everything snapped with `PixelScene.align` | Overlay-internal | DESIGN Layout; `docs/rules/ui.md` | Housed |
| D7 | Panel a single column at the right edge, left of the inventory pane's column, below the status pane, ending above the toolbar | the private HUD fields and their sizes | ADR-0008 row 4 (accessors for `GameScene` HUD fields and `cellSelector`); codebase-map "Overlay geometry" | Housed |
| D8 | Drawn translucently over the dungeon, never over the game's HUD | AD-12's prevention | AD-12 | Housed |
| D9 | The Overlay offsets the world camera horizontally and re-applies its offset after every `GameScene.layoutTags` | a per-frame or post-layout re-apply; `Camera.main.setCenterOffset` is public, so no hook is needed | AD-12 only by reference ("placed per `DESIGN.md` Layout"); ADR-0013 does not mention the camera and no AD names the re-apply point | Partial |
| D10 | Width target 200, minimum 160; the log's `ScrollPane` takes the rest, never fewer than three lines | Overlay-internal | AD-12; DESIGN Layout | Housed |
| D11 | Collapse rule: uncovered map narrower than 200 UI px or the view shorter than 200 | measured on the running game | DESIGN carries the `[ASSUMPTION]` for the E5 layout story | Partial |
| D12 | No shadows; depth is translucency (`TOAST_TR_HEAVY` Panel, `TOAST_TR` strip) | those `Chrome` types exist | codebase-map "PRD open question 12" (all six named `Chrome` types confirmed) | Housed |
| D13 | All shapes are the toolkit's nine-patches: `TAG`, `TAB_SET`, `TAB_SELECTED`, `TAB_UNSELECTED`, `RedButton` | same | same row | Housed |
| D14 | Map highlight: cell outlines drawn in the dungeon view, never filled, never over sprites; oracle outlines for unseen enemies | see E11, E38, E70 | — | **None** |
| D15 | Oracle border: a 2 px frame around the whole game view above everything, plus the `ORACLE` label in the strip | the flag; draw order | ADR-0006; AD-12 | Housed |
| D16 | Belief row: item name in ink, top candidate and probability in numeric columns, further candidates muted; floor facts and chapter counters as plain rows | the `Belief` projection | see E33 | Partial |
| D17 | Decision log: small text, muted past lines, ink current line, a `ScrollPane` with the newest at the bottom | the per-wait records | ADR-0011; AD-12 | Housed |
| D18 | Stepper: two tiny `RedButton`s flanking a numeric value | Overlay-internal | AD-12; see E9 | Housed |
| D19 | "Post every visual change to the render thread; the instrument never draws from the Brain's thread" | a thread assertion on every Panel method | AD-8; ADR-0013; the spine's Threads convention | Housed |
| D20 | "Don't animate the Panel; the game animates, the instrument updates" | update cadence at Input waits | AD-5; AD-12 | Housed |

## Part 3 — docs/codebase-map.md

### "What the architecture must absorb" (10 bullets)

| # | Bullet | Absorbed by | Verdict |
|---|---|---|---|
| C1 | Turns resolve on the actor thread and wait on sprites; `Hero.ready` and `Hero.interrupt` dereference `AttackIndicator.instance` and `GameScene.cellSelector`; a headless driver needs a scene with a hero sprite (E1) | AD-8 and ADR-0013 (thread roles, turn ends in sprite callbacks); ADR-0008 rows 3 and 5; spine Structural Seed (`harness.scene`, `HeadlessScene`) | Housed |
| C2 | The turn counter lags: elapsed turns are `Statistics.duration + Actor.now()` (E1) | AD-5 (verbatim, in fixed-point thousandths, in the Run log and not the Observation); ADR-0005; ADR-0011 `wait.turn` | Housed |
| C3 | Seed the base generator after `Dungeon.init`; a fresh Profile; the guide-page generator neutralized; no render or audio thread sharing the generator (E1) | AD-6; ADR-0007 (the `RngControl` decision, the standard Profile with all guide pages read, hook row 8 for `Emitter`, `Music` and `EmoIcon`) | Housed |
| C4 | Identity-hash order is a nondeterminism source; the determinism test must run two JVMs (E1) | AD-6 ("identity-hash order is removed by the identity-order hook row ... The determinism test runs in two JVMs"); ADR-0007; ADR-0008 row 6 | Housed |
| C5 | A floor is (seed, challenges, history), not (seed, depth) (E1, E3) | AD-6 (the Run tuple includes the Action list); ADR-0007; ADR-0011 (Replay applies the Action list) | Housed |
| C6 | The Observer's legal sources and its never-list (E1) | AD-3; ADR-0006's per-rule table — every listed source and prohibition appears, with one leak test per row | Housed |
| C7 | Boss floors lock at the arena trigger: `Level.locked` blocks every transition and interfloor teleport until the boss dies (E4, E7) | **nothing.** `Level.locked` / the sealed state is not a field in ADR-0005's schema and has no row in ADR-0006, so the valid-Action set of AD-4 (computed from the Observation alone) would offer a descend the game refuses, and the Brain cannot see a seal the screen does show | **None** |
| C8 | Overlay geometry: 427 by 240 at 1280 by 720; the free column; private HUD fields; `layoutTags` overwrites `centerOffset`; F6 to F12 unbound; TTF sizes (E5) | AD-12; ADR-0008 row 4; DESIGN Layout — except the camera re-apply, which is D9's Partial | Housed |
| C9 | Prompts are not engine waits: a `Window` blocks the player, not the actor thread; answer windows through their own buttons (E1, E5) | AD-5 (the Input-wait predicate); ADR-0006 Prompt row; AD-4 ("a Prompt window's button"); ADR-0013 | Housed |
| C10 | Score and Win: the Win is `Statistics.gameWon` as `Rankings.submit(true, ...)` records it; Score is `Rankings.calculateScore` with the challenge multiplier, so Scores compare within one challenge set (E3) | AD-11; ADR-0012 (`PairScore`, the lexicographic Composite comparison; a Registration fixes the challenge set) | Housed |

### Discrepancies with the bootstrap prompt (21 rows)

| # | Row | Absorbed by | Verdict |
|---|---|---|---|
| C11 | Rendering is one libGDX thread but game logic runs on a separate actor thread; level changes on an `InterlevelScene` worker; state is static and mostly unsynchronized | AD-8; ADR-0013 (the roles table, the 2020 deadlock, the deadlock rule) | Housed |
| C12 | A `Window` blocks only the player; the hero is already `ready` when it appears; `AlchemyScene` and level changes are scene switches | AD-5; ADR-0006 Prompt row; ADR-0013 (re-attach on scene switch) — see E17 for the alchemy wrinkle | Housed |
| C13 | `Dungeon.observe()` fills `heroFOV` and `visited` only; `mapped` is written elsewhere; magic mapping changes only the fog layer | ADR-0006 Cell-visibility row (`heroFOV`, then `visited`, then `mapped`, then `UNKNOWN`) and Vision-buffs row | Housed |
| C14 | Mobs are drawn iff `heroFOV[mob.pos]`; an invisible char is still drawn at alpha 0.4; a stealthy passive mimic stays drawn in fog | ADR-0006 Mobs and Heaps rows (the `invisible` flag; the mimic emitted as a `CHEST`, with a differential test) | Housed |
| C15 | Unseen enemy existence leaks through the log, and `CellSelector` hit-tests taps against unseen mobs' sprite bounding boxes | ADR-0006 Log row (existence leaks the game itself makes are kept) and the whitelist construction, which admits nothing from the cell selector | Housed — but ADR-0006's never-column does not name the hit test; add it so the reviewer can check it |
| C16 | Blindness gives a 3 by 3 FOV; Foliage's `Shadows` does the same | ADR-0006 Vision-buffs row (all of it acts through `heroFOV` before the Observer reads it) | Housed |
| C17 | Only buffs with an icon are drawn, at most 14 on the HUD; the exact hunger value is never shown | ADR-0006 Hero-buffs row (icons only, uncapped through the hero window; hunger as the three HUD states); ADR-0005 `hero` section | Housed |
| C18 | The function is `MobSpawner.getMobRotation(depth)`; `journal/Bestiary` is the journal's encounter record | AD-1 knowledge layer (`codex`, `docs/rules/combat.md`); ADR-0006 Journal row excludes `Bestiary` | Housed |
| C19 | Hit chance is `Char.hit`, but the hit roll is uniform; only damage and armor are triangular | Codex (AD-1 knowledge layer); `docs/rules/combat.md` | Housed |
| C20 | Yog drops nothing; `LastLevel` places the Amulet; `Dungeon.win` has two callers | AD-11 and ADR-0012 (the Composite Win from `Statistics.gameWon`); `docs/rules/save-score-win.md` | Housed |
| C21 | Special rooms guarantee their solution item on the floor, but another room's `findPrizeItem` can move it | Codex (AD-1); `docs/rules/levels.md` | Housed |
| C22 | The same seed does not alone produce the same layout: challenges, talents, trinkets, quest state, `LimitedDrops`, `SPDSettings.intro()` and journal state all feed generation | see C5; AD-6; ADR-0007 (the standard Profile pins the settings and journal state) | Housed |
| C23 | Weights and decks live in `Generator`; the guarantees are `LimitedDrops` counters and the `posNeeded` / `souNeeded` / `asNeeded` formulas applied in `Level.create` | Codex (AD-1); ADR-0007 (the Action list carries the history) | Housed |
| C24 | Seeding is partial; `Dungeon.init` discards the stack with `Random.resetGenerators()`; item classes stay seed-determined even for mob drops | AD-6; ADR-0007 (push after `Dungeon.init`, with a one-line hook if `initHero` draws) | Housed |
| C25 | Further determinism sources: identity-hash order, `Random.chances(HashMap)`, `bones.dat`, journal page state, `settings.xml`, the unseeded guide-page `pushGenerator`, render and audio draws | AD-6; ADR-0007 (the standard Profile: no bones, all guide pages read; hook rows 6 and 8); the spine's Determinism convention | Housed |
| C26 | The changelog package is `ui.changelist`; v3.3.8 carries no date; no 4.0 entry exists | no architectural consequence; the `docs/UPSTREAM.md` upgrade procedure | Housed (n/a) |
| C27 | Six asset files are loaded by literal strings outside `Assets.java` (two fireball effects, two cursors, the text-field skin, `pixel_font.ttf`) | the headless scene's asset strategy is only sketched in the Structural Seed ("no-op GL, Pixmap atlases"); no AD or ADR names the literal-path files | Partial |
| C28 | Gradle modules plus four service sub-modules; upstream compiles for Java 11 on libGDX 1.14.0; the desktop release bundles JDK 17 | the spine's Stack table (Java 21 for Shatterfish modules, upstream 11, libGDX 1.14.0 with `gdx-backend-headless`); ADR-0003 | Housed |
| C29 | The world camera offset is vertical only and conditional, and `layoutTags` overwrites `centerOffset` | AD-12 by reference to DESIGN Layout — see D9 | Housed (mechanism Partial) |
| C30 | `renderTextBlock` renders a platform TTF; the 3 by 5 bitmap font is only for version and depth numbers | DESIGN typography; AD-12 | Housed |
| C31 | The UI pixel size is `PixelScene.defaultZoom`, not `SPDSettings.scale` | DESIGN Layout; EXPERIENCE Responsive; E80 | Housed |

### PRD open question 12 (context)

All ten Tier 3 statements are confirmed in the map and each is already used by the spine or an
ADR: the boss stair lock (C7 — the *fact* is confirmed, but the Observation does not carry it),
the challenge score multiplier and the Win condition (AD-11, ADR-0012), the six classes and the
branch depths (ADR-0005 `header.branch`), the HUD sizes and the `Chrome` types (AD-12, D12, D13),
the combat generator (ADR-0007), 427 by 240 at scale 3 (E80), and F6 to F12 unbound (E56).

## Recommended dispositions

| Gap | Smallest fix |
|---|---|
| E19 multi-cell move | One sentence in AD-4 or AD-5: the Action set contains only single-step moves (`Hero.handle` on an adjacent cell), so every cell is an Input wait; otherwise a ninth-hook ADR for a per-cell notification |
| E43 PAUSED input | Extend ADR-0013's Modes paragraph: also set each toolbar and quickslot `Button.active = false` (each tests its own field), and either set `CellSelector.enabled = false` or gate `moveFromActions`; add a test that a key press in PAUSED changes no Observation hash |
| E44 HUMAN card and coach mode | Extend ADR-0013's per-wait sequence: on a human wait submit `update` and, when the card is shown or coach mode is on, `decide` as an advisory Decision that is never executed (AD-7 already forbids executing a foreign or stale Decision) |
| E51 RUN OVER | Add the resurrect and game-over windows to ADR-0006's Prompt kinds, or give ADR-0013 an explicit run-over branch that stops observing before them; name where `end.cause` is read |
| E53 save and resume | Add a `save` record kind to ADR-0011 and a rule in ADR-0007 for salt and `k` continuity across a resume (or state that a resume starts a new log with a back-reference) |
| E11, E38, D14 map highlights | Add cells to the `Decision` value (path, target, considered) in ADR-0011's `decision` object, and one sentence in AD-12 for a world-space highlight layer |
| E12 `SPDAction` registration | A ninth hook row (through an ADR, per ADR-0008's own rule) or the FR-42 fallback: buttons only in v1 |
| E10 Pause-on hook (v2) | Note in ADR-0008 that E8 needs a budget revision, so v2 is not surprised by it |
| E25 Panel refresh in Fast | One sentence in AD-12 or ADR-0013: the Panel writes at most N times per second regardless of the wait rate |
| C7 sealed level | Add a `locked` flag to ADR-0005's `map` section with a row in ADR-0006 citing `Level.seal` and the transition refusal, plus a leak test |
| D1 toolkit enforcement | Name the `overlay` dependency or ArchUnit rule (no Swing, JavaFX, ImGui, web view) in AD-12, beside AD-1's `brain` rules |
