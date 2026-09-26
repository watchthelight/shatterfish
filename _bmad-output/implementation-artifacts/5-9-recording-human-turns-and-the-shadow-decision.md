---
title: 'Story 5.9: Recording human turns and the shadow Decision (the human half, pulled forward)'
type: 'feature'
created: '2026-09-26'
status: 'review'
baseline_commit: '4f1b692ff'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**This is a pulled-forward slice.** The product owner asked for it directly: one command that launches
the real game, lets them play, and records everything they do, so their play can be compared with the
Bot's. So this story builds 5.9's human half ahead of 5.5 to 5.8: **HUMAN mode for a whole Run**
(`--agent human`). Taking over mid-Run and handing back is story 5.8's, and a Run that switches between
the Brain and a person is not built here.

**Problem:** Every Overlay Run so far is the Brain's, and the person's input is locked out. There is no
way for a person to play inside the Overlay and leave a log that says what they did, turn by turn, next
to what the Brain would have done.

**Approach:** The embedded Run confirms each Input wait exactly as it does for the Brain (the same gate,
the reseed, the Observation) and hands the wait to the person instead of the executor. What the person
does is heard where the game handles it (a new hook row, 11, on the owner's decision) and through the
Overlay's input lock, and written as the Action the executor would have issued, so a Replay reproduces
it; what the executor cannot express is marked `unsupported`. The Brain still sees every wait's
Observation, updates its Belief and decides; that Decision is a `shadow` record, greyed on the card and
never executed. A key opens the game's own text window for a `note`. `:rig:strategy` prints the Run as
one line per turn beside the Brain's shadow.

## Boundaries & Constraints

**Always:** Non-negotiable 1: the shadow Brain gets the Observation and nothing else; the Brain imports
only `api`. Non-negotiable 3: the hook sites are minimal, guarded, add-only, row 11 in `docs/UPSTREAM.md`
with its site index and digests, labelled `touches-upstream`. Non-negotiable 5: a human's waits replay
under the executor wherever the frames are the headless driver's; the named exceptions of stories 5.1
and 5.2 stand for the desktop. Non-negotiable 6: the note window is the game's `WndTextInput`, the
Panel's blocker the game's `PointerArea`. ADR-0013's deadlock rule: the render thread never waits on
the Brain.

**Ask First (asked and answered):** the upstream edit. The owner chose, on 2026-09-26, an eleventh hook
row with the budget raised to eleven, over widening row 9.

**Never:** a shadow executed; a person's input recorded as something the executor would not do without
a mark; an Overlay log counted by the Rig; a tap on the Panel reaching the game as a hero's Action.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Launch | `--agent human` | the game, the Run HUMAN, header `brain.name: human`, `mode {HUMAN, player}` at k 0 | an unknown agent is refused by name |
| A wait opens | confirmed, reseeded, observed | input lock opens; the Brain gets a shadow question | the render thread never waits |
| A click, a key, a toolbar button, an item, a talent, a Prompt button, the back key | heard at row 11's sites or by the lock | `wait {actor: human}` with the executor's Action; a `prompt` record beside a Prompt answer | outside the wait's valid set: `wait` + `unsupported` |
| Nothing heard, input between waits, a screen changed with no input | | `unsupported` at the wait | `end.verifiable` false |
| The shadow lands | worker done | `shadow` (before the person acts) or `shadow {skipped: true}` | a Brain failure is counted, the game goes on |
| N | the lock | the note window; Save writes `note {k, text}` | blank or cancelled writes nothing |
| A tap on the Panel | HUMAN, no window in front | the Panel's blocker takes it | with a window in front the blocker is off |

</frozen-after-approval>

## Acceptance Criteria (the story's, for this slice, with the tests that hold them)

1. **Every human Action is recorded with actor human**, taken from the hero's action after the game
   resolves the input (`Hero.handle`) and from the notification sites (item use, rest, search, talent,
   a window's own button). Tests: `HumanActionMappingTest` (rest, wait and search, a talent point,
   eating, a throw at a cell, a Prompt's button tapped, the back key on a message window, a tap on an
   adjacent heap, a window the person opened not read as a Prompt), `HumanTurnReplayTest` (a step by a
   tap).
2. **An input the executor cannot express is recorded as unsupported and ends replay verifiability from
   that wait, with the Panel saying so.** Tests: `HumanUnsupportedTest` (a distant click: `MoveTo` plus
   `unsupported`, the snapshot's `unverifiableFrom`, `end.verifiable` false, `Replay.waitsOf` stopping
   there; a zero-time change nobody heard; a wait that ended with nothing heard),
   `HumanModeContentTest.the_card` and `PanelContentTest.the_card_says_shadow` (the notice's words).
3. **The Brain still updates its Belief at every Input wait and produces a shadow Decision, shown greyed
   on the card and written to the log, never executed.** Tests: `ShadowDecisionTest.in_time` (the shadow
   for wait 1 before the person's wait record, current on the snapshot, equal to the card's; the
   history holds it), `ShadowDecisionTest.too_late` (the Brain shown every wait's Observation, in
   order; only the person's Actions taken), `PanelContentTest.the_card_says_shadow`,
   `HumanModeContentTest.the_log`.
4. **A Decision tagged with a wait index that is no longer current is logged as skipped and never
   executed.** Test: `ShadowDecisionTest.too_late` (a Brain held while the person takes two waits: its
   answers to waits 1 and 2 are `skipped`, wait 3's is not; no frame waited on it).
5. **`HumanTurnReplayTest` records a session with three human turns and replays it with every
   Observation hash matching.** The session is played on the embedded Run with the game's own input
   calls, where the frames are the headless driver's; `Replay.waitsOf` replays it through the executor
   and checks every hash; `Replay.of`, the Rig's path, still refuses the log by its driver.
6. **Notes** (the owner's request): N opens the game's text window and writes a `note` at the open wait.
   Tests: `ShadowDecisionTest.in_time` (the record, cleaned to one line), `RunLogNoteTest`,
   `HumanModeContentTest.the_lock` (N never reaches the game).
7. **A readable export** (the owner's request): `:rig:strategy` writes and prints `<run-id>.human.txt`.
   Test: `HumanPlayTest`.
8. **A tap on the Panel is never recorded as a game Action** (the coordinator, after story 5.4's merge).
   Test: `PanelContentTest.a_human_tap_on_the_panel_stays_on_the_panel`.
9. No Rig numbers: nothing the Brain decides changes (epic 5's rule); the Brain module is untouched.

## Design Notes

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `core/…/`, at v4.0.0.

**Goal and binding constraints.** Record what a person does at each Input wait as the Action the
executor would issue, so a Replay reproduces it; never execute the Brain; never block the render thread;
add as little to upstream as possible.

### Where a person's input is heard

The setup assumed hook row 5 covered item use, rest, search, talents and window buttons. It does not:
row 5 is one notification, at the start of an act the hero began unready (`core/…/actors/hero/Hero.java:846`).
A click chooses the hero's action in `Hero.handle` on the render thread (`core/…/actors/hero/Hero.java:1920-2008`)
inside `inputHandler.processAllEvents()` (`SPD-classes/src/main/java/com/watabou/noosa/Game.java:269-283`),
and the actor thread consumes it later in the same frame, woken from `GameScene.update`
(`core/…/scenes/GameScene.java:895-924` in the working tree); a held key moves the hero from
`CellSelector.update` (`core/…/scenes/CellSelector.java:385-386`, `:395-417`, `:464-480`); the wait,
rest and search buttons (`core/…/ui/Toolbar.java:198-204`, `:223-226`, `:310-314`), a talent
(`core/…/ui/TalentButton.java:251`) and every item window (`core/…/items/Item.java:157`) leave the hero
busy with no action in hand, so what was pressed cannot be read back.

- **A. Infer the input from the state afterwards** (busy with no action: rest or search or an item?).
  Rejected: not faithful; rest and search differ only in the time spent and a status line.
- **B. Reinterpret raw input in the Overlay** (hit-test every toolbar button and slot). Rejected: a
  second implementation of the game's UI dispatch, and still blind to what an item window chose.
- **C. A notification point, `Hooks.heroInput`, at the six methods every hero-directed input converges
  on.** Chosen. Every UI caller was enumerated (`Hero.handle` from `GameScene.java:1828-1834` at the tag,
  `CellSelector.java:415`, `AttackIndicator.java:184`, `LootIndicator.java:52`, `Toolbar.java:256`;
  `rest` and `search(true)` only from the toolbar; `upgradeTalent` only from `TalentButton`; `execute`
  only from item windows, quickslots and `super.execute` chains).

Which row: **A. widen row 9**, **B. file under row 5**, **C. an eleventh row**. The owner chose C on
2026-09-26 (ADR-0016's story 5.9 amendment). A window's own button and the back key need no hook: the
input lock sees the raw tap first, and `HumanTurns` reads it against `ActionExecutor.optionButtons`
(the executor's own list, extracted), only on the wait's own Prompt window.

### From what was heard to an Action

- **A. Record the game's own action object** (`HeroAction.Move` …). Rejected: a Replay drives the
  executor, which speaks `api` Actions.
- **B. Map to an Action by kind alone.** Rejected by the first real launch: a tap on an adjacent heap is
  the game's `PickUp` at that cell, which the executor issues as `Step` onto it.
- **C. Map to the Action whose executor call is the same call, checked against the wait's valid set.**
  Chosen. Every click Action is one `handleCell`, so any offered Action clicking that cell reproduces
  the click; a fixed order picks the name among them, never the game's own choice (the fairness review: it is read from the true level). Outside the set: the Action as the
  person made it (a distant click is `MoveTo`) and `unsupported`. Recording announces the hand-over as
  the executor's `applied()` does, so the gate numbers the next wait as for the Brain.

### When the input may reach the game

- **A. Lock nothing** (input always open). Rejected: a click processed before a wait is confirmed is
  made before its reseed, and the record could not name its wait.
- **B. Confirm the wait at the input** (inside the hook). Rejected: the Observer refuses a hero holding
  an action, and a mid-input reseed moves draws.
- **C. The lock passes presses only while a wait is open; releases always; plus a second look for a
  wait between the game's input and its scene update, for held keys.** Chosen. Cost: a tap no longer
  interrupts a walk or a rest (`docs/ideas.md`).

### The shadow

- **A. Decide only on demand** (when the person asks). Rejected: the Belief must follow every wait.
- **B. One decision at a time, dropping questions while busy.** Rejected: the Brain's Belief would miss
  Observations.
- **C. Every wait's Observation queued to the worker; the Decision read on the worker right after its
  own `decide`; written when done, `skipped` if its wait is no longer the person's.** Chosen.

### Notes, and whether new records are chained

- **A. A side file.** **B. Unchained lines.** **C. Chained records like every other kind.** Chosen C
  (ADR-0011's story 5.9 amendment): an edited note would be a claim nobody made.

### A tap on the Panel (after story 5.4's merge)

In a HUMAN Run the input is open, so 5.4's `active = !inputLocked` makes Explain and the log's scroll
live, and a tap on the Panel's body would fall to the dungeon's cell selector.
- **A. Keep the Panel inactive in HUMAN mode.** Rejected: the body still passes taps through.
- **B. Refuse taps inside the Panel in the lock.** Rejected: the Panel's own controls would never work.
- **C. A `PointerArea` blocker over the Panel, active in a HUMAN Run with no window in front.** Chosen.

### Pre-mortem

- *A second click in the frame after a first, while the hero is busy, calls `GameScene.cancel()` and
  changes the game unheard.* Low: the lock closes once the wait is recorded, but events of one poll pass
  together. The drift check does not see it (the hero is busy). Recorded here and in ideas.
- *An item's `execute` that never calls `super.execute`.* Its wait ends with nothing heard: `unsupported`.
- *A held key moves the hero before the wait is confirmed.* The pre-update look confirms first; if the
  actor thread has not parked, the input is heard outside a wait: `unsupported`.
- *A window's button the Observer does not list* (seen on the first real launch): now not read as an
  answer unless the window is the wait's own Prompt; otherwise `unsupported`.
- *The Panel's blocker steals a window's tap.* Off whenever a window is in front.

## Tasks

- [x] Hook row 11: `Hooks.heroInput` and six sites; UPSTREAM.md row, site index, digests; ADR-0016
  amendment; `HooksLedgerTest` budget eleven.
- [x] api: `note` kind, `shadow.skipped`; reader.
- [x] harness: `HumanTurns`, `EmbeddedRun.attachHuman`/`beforeUpdate`/`note`, `RunLoop.recordHuman`,
  `ActionExecutor.optionButtons`, `Replay.waitsOf`.
- [x] overlay: `--agent human`, `InputLock` human mode, `NoteWindow`, HUMAN strip, greyed shadow and notice,
  Decision log lines, Panel blocker.
- [x] rig: `HumanPlay`, `:rig:strategy`.
- [x] Merge main (story 5.4) and join its history.
- [x] Tests, a real launch, the mutation battery, docs.

## Review

### What was built

- **Hook row 11** (`core`): `Hooks.heroInput` (`HeroInput`: `cellHandled`, `cellSelected`, `itemUsed`,
  `rested`, `searched`, `talentUpgraded`), six add-only guarded sites in `Hero.java` (4), `Item.java`,
  `CellSelector.java`; `docs/UPSTREAM.md` row 11, site index and digests; ADR-0016's amendment raises
  the budget to eleven on the owner's decision; `HooksLedgerTest` pins eleven.
- **api**: `RunLog.Note` (chained, one line, `Note.clean`), `RunLog.Shadow.skipped` (written only when
  true); `RunLogReader` reads both.
- **harness**: `HumanTurns` (the recorder); `EmbeddedRun.attachHuman`, `beforeUpdate`, `note`,
  `pointerUp`/`keyDown`/`inputEvent`, `inputOpen`, `Snapshot.human` (`EmbeddedRun.Human`); shadows
  queued on the worker with the Decision and Belief summary read there; `RunLoop.recordHuman`,
  `ending(…, unverifiable)`; `ActionExecutor.optionButtons` (extracted); `Replay.waitsOf`.
- **overlay**: `--agent human`; `InputLock` human mode; `NoteWindow` (N); `ModeState` HUMAN/`player`;
  the Decision card's greyed shadow, headline and Replay notice; Decision log lines for shadows (greyed,
  "late"), notes and marks; the Panel's `PointerArea` blocker.
- **rig**: `HumanPlay`; `:rig:strategy` writes and prints `<run-id>.human.txt`.
- Merged main (story 5.4): the human Run's snapshot carries 5.4's belief summary and history, and its
  mode, waits, shadows, marks and notes go into that history.

### The master command

```sh
./gradlew :overlay:launch -Plaunch.args="--agent human --seed 2000 --class WARRIOR --window 1600x900"
./gradlew :rig:strategy --args="overlay-runs"
```

Logs land in `overlay-runs/` at the repository root (`--out` to change it), one
`<tag>-<CLASS>-0-<seedcode>-<salt>-human.jsonl` per Run; `:rig:strategy` writes `.strategy.txt` and
`.human.txt` beside each and prints the latter. N writes a note at the current turn.

### The human Action mapping

| What the person does | Recorded as | Replayable |
|---|---|---|
| a tap or a direction key on an adjacent cell (the game's move, attack, interaction, pick-up there, chest, purchase, unlock) | the offered Action clicking that cell (`Step`, `Attack`, `Interact`, `OpenChest`, `Buy`, `Unlock`) | yes |
| a tap on the hero's own cell over a heap, on stairs | `PickUp`, `Descend`, `Ascend` | yes |
| the wait button, the rest key, the search button | `Wait`, `Rest(true)`, `Search` | yes |
| a talent point | `Talent(title)` | yes |
| an item's action; with a cell target; with a bag target | `UseItem`, `UseItemAt`, `UseItemOn` (offered targets only) | yes |
| a Prompt window's button; the back key on it | `AnswerPrompt(i)` (the executor's index); `DismissPrompt` | yes |
| a tap on a distant cell (a walk) | `MoveTo(cell)` + `unsupported` | no, from that wait |
| a throw or zap at a cell the set does not offer; an armour or weapon ability; resume; anything not in the valid set | the Action as made + `unsupported` | no |
| a zero-time change nothing hears (a quickslot, a journal page); an input between waits; a wait ended with nothing heard; a controller connected | `unsupported` | no |
| a tap on the Panel; a window the person opened (the journal) | nothing: not a game Action | n/a |

### Real launch

`:overlay:launch --agent human --seed 2000 --class WARRIOR --window 1600x900 --screenshot s59-human.png`:
the game started, the Mode strip read `HUMAN player turn 0 floor 1`, the card read `shadow, not
executed: the Brain would` over greyed rows, the Decision log showed `wait 1 shadow step W 1.0000`, and
the log opened with the header (`brain.name: human`, `driver: embedded`, `interface: 1`) and the `mode`
record. A person at the machine then played three turns before closing the window, which was the
launch's best finding: the log recorded a tap on the adjacent tome as `MoveTo` (a bug: the executor's
`Step` there makes the same call; fixed, `HumanActionMappingTest.adjacent_heap`) and a tab of the journal
the tutorial pointed at as a Prompt answer (a bug: only the wait's own Prompt window is answered; fixed,
`a_window_of_the_persons_own`, `a_tab_of_the_persons_own_window`). `:rig:strategy` printed the
comparison from that log.

### Fairness review (the `fairness-reviewer` subagent): two blocking, fixed

1. **The recorder named the game's choice for a click** ("read as Interact") in the log and on the
   Panel, and `Hero.handle` chooses from the true level: a hidden mimic, an exit or a heap in unexplored
   fog. Fixed: a click is its cell alone, named from the valid set in a fixed order.
2. **`Replay.waitsOf` wrote a replay log labelled `bot` under a headless header**, a log the Rig would
   count. Fixed: a scratch folder, deleted before it returns.

Should-fixes done: the screen check only with the actor thread parked; a staff's inner wand zap no
longer overrides the staff's use; an item use at the full interface (a pane selector nobody hears) is
marked; a controller connected at the start marks the Run; a failed attach leaves no listener; the
recorder's cross-thread fields are volatile; `skipped`'s timing dependence stated in ADR-0011.

### Tests

`:api:test` (18 classes), `:overlay:test` (21), `:brain:test` (26), the harness's agent, log and executor
packages with the hook ledger, vanilla, kinds, confinement, reflection and oracle gates (39 classes),
the rig's log readers and `HumanPlayTest` (9), `DocsCitationTest`: all green. New: `HumanTurnReplayTest`
(3 turns; and 48 waits of a person following the shadow to a death in a fight, every hash matching),
`HumanActionMappingTest` (11), `HumanUnsupportedTest` (4), `ShadowDecisionTest` (2), `RunLogNoteTest`,
`HumanModeContentTest` (5), `HumanPlayTest` (2), two `PanelContentTest` cases, `RunLogKindsTest` extended.

### Mutation battery (scratch script; control run first; every kill a named test's failure)

| # | Mutant | Killed by |
|---|---|---|
| M1 | an unoffered Action not marked | `HumanUnsupportedTest` |
| M2 | a step mapped to a walk by kind | survives: equivalent (the valid-set fallback finds the same `Step`) |
| M2b | a click reproduced only by a `Step` becomes a walk | `HumanActionMappingTest`, `HumanTurnReplayTest` |
| M3 | rest and wait swapped | `HumanActionMappingTest` |
| M4 | a late shadow not marked skipped | `ShadowDecisionTest` |
| M5 | a human wait not reseeded | `HumanTurnReplayTest` (survived the 3-turn test; killed by the longer session) |
| M6 | a wait with nothing heard not marked | `HumanUnsupportedTest` |
| M7 | a window the person opened read as a Prompt | `HumanActionMappingTest` (survived first; killed by the tab test) |
| M8 | the ending verifiable after a mark | `HumanUnsupportedTest` |
| M9 | the human check refuses the Overlay's log | `HumanTurnReplayTest` |
| M10 | the reader drops `skipped` | `RunLogKindsTest` |
| M11 | the lock swallows a release | `HumanModeContentTest` |
| M12 | the Panel's blocker never on | `PanelContentTest` |
| M13 | a human Run shows RUNNING | `HumanModeContentTest` |
| M14 | the shadow headlines swapped | `HumanModeContentTest` |
| M15 | agreement inverted in the export | `HumanPlayTest` |
| M16 | a note keeps its line breaks | `RunLogNoteTest` |
| M17 | no screen check at a wait | `HumanUnsupportedTest` |
| M18 | the shadow not in the Panel's history | `ShadowDecisionTest` |
| M19 | a controller does not mark the Run | `HumanUnsupportedTest` |

20 mutants: 19 killed, one equivalent.

### Deferred (docs/ideas.md)

A tap that interrupts a walk or a rest (not recordable yet; the lock holds presses between waits);
`MoveTo` in the executor; targets and abilities the valid set leaves out; a Prompt button the Observer
reads short; a bindable notes key (5.11); a human log replaying on the desktop (5.13, #169); takeover
and hand back mid-Run (5.8). Not built: the staff zap is untested headlessly (a Mage fixture).
