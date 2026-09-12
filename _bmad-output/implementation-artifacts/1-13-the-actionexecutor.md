---
story: 1.13
key: 1-13-the-actionexecutor
title: "The ActionExecutor"
epic: 1
issue: 26
status: ready-for-dev
created: '2026-09-11'
updated: '2026-09-11'
review_loop_iteration: 0
baseline_commit: '791e9ccef914dc143fdcd9079c1dbc5c3369380c'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Nothing applies an Action. The Observation says what the screen shows and
`ValidActions` says what may be done, and there is no way to do it, so no Run can be played and
story 1.14's random agent has nothing to drive.

**Approach:** One class in `harness` that takes an Action and an Observation, re-validates before
touching anything, and then makes the same call the human's click, key or button makes — never a
model write of its own. A rejection is a value with a reason, never an exception.

## Boundaries & Constraints

**Always:** the executor is the only Shatterfish caller of the hero's input methods (AD-14); it
asserts the Input wait and the UI-role thread on entry; it re-validates against the Observation's
own action set before any game state changes; an `ItemRef` is resolved by re-walking the belongings
order and checking the display name, never by index alone (ADR-0014, option 11).

**Ask First:** any hook row. ADR-0016 assigns action registration to E5, so this story must reach
every input through a path upstream already makes public — and it can: see the Code Map.

**Never:** a second implementation of a game rule; a write to a model field; an exception as a
rejection; an Action applied that the set did not carry.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| A step | `Step(cell)` beside the hero | the hero moves one cell and the next Input wait arrives | N/A |
| An attack | `Attack(cell)` on a visible enemy beside the hero | the hero attacks, as a click on it does | N/A |
| A stale item reference | `UseItem` whose `ItemRef` names an item no longer at that index | rejected, nothing executed | `Reason.ITEM_MOVED` with both names |
| An Action outside the set | `Wait` while a Prompt is open | rejected before any call | `Reason.NOT_OFFERED` |
| Not at a wait | any Action while the hero is acting | rejected, nothing executed | `Reason.NOT_AT_AN_INPUT_WAIT` |
| A targeted use | `UseItemAt(item, THROW, cell)` | the item's own execute opens the cell selector and the cell answers it in the same wait | `Reason.NO_SELECTOR` if the game opened none |
| A use on an item | `UseItemOn(item, action, target)` | the bag window's selector takes the target item | `Reason.NO_SELECTOR` |
| An answer | `AnswerPrompt(i)` with a recognised window in front | the i-th button of the window is clicked | `Reason.NO_SUCH_OPTION` |
| An input with no kind | the alchemy pot, mining with a pickaxe | never in the set, and named in the completeness test as unsupported with a reason | N/A |

</frozen-after-approval>

## Code Map

Every path below was read at `v4.0.0` and needs no hook: each is already public.

- **The cell kinds are one call.** `Hero.handle(cell)` decides by cell what a click means —
  alchemy, interact, attack, mine, pick up, buy, open chest, unlock, transition, move
  (`core/.../actors/hero/Hero.java:1929-2015`). The public way in is
  `GameScene.handleCell(cell)` (`core/.../scenes/GameScene.java:1635-1637`), which is
  `cellSelector.select(cell, LEFT)` and then `GameScene.ready()`
  (`core/.../scenes/CellSelector.java:152-166`). So `Step`, `Attack`, `Interact`, `PickUp`,
  `OpenChest`, `Buy`, `Unlock`, `Descend` and `Ascend` are all `handleCell` of the cell the Action
  names, with `Descend`/`Ascend` naming the hero's own cell. `cellSelector` is not null headlessly:
  the harness scene *is* a `GameScene` and `create()` assigns it.
- **The buttons are one call each.** The wait button is `Hero.rest(false)` and the rest button
  `Hero.rest(true)` (`core/.../ui/Toolbar.java:203`, `:225`); the search button is
  `Hero.search(true)` (`Toolbar.java:296`, `:313`). `Wait` and `Rest(false)` are therefore the same
  input, which the valid set should stop offering twice — see Tasks.
- **A talent is `Hero.upgradeTalent(Talent)`** (`Hero.java:377`), which the pane calls through its
  own button (`core/.../ui/TalentsPane.java:207`, `:236`). The Action names the talent as the hero
  section does, so the executor matches it against the hero's own talent list rather than by a name
  table of ours.
- **A targeted item answers through the same click.** `item.execute(hero, action)` opens the game's
  cell selector (`GameScene.selectCell`, hook row 5's site), and the human's second click is
  `handleCell` again, which routes to whatever listener is installed. So `UseItemAt` is execute
  then `handleCell(cell)`.
- **A bag selector is public.** `GameScene.selectItem(listener)` either sets the inventory pane's
  selector or shows a `WndBag` (`GameScene.java:1668-1684`); headless the pane is built only for
  the desktop UI size (`GameScene.java:547-551`), so the window is what appears.
  `WndBag.getSelector()` is public (`core/.../windows/WndBag.java:145`) and the window's own button
  hides first and then calls `selector.onSelect(item)` (`WndBag.java:288-300`), which is the order
  to copy.
- **A prompt's button has no public method and does not need one.** `WndOptions` builds anonymous
  `RedButton`s whose `onClick` calls the protected `onSelect(index)`
  (`core/.../windows/WndOptions.java:86-100`), and they carry no key action, so neither a method
  call nor a key reaches them. The pointer does: `PointerEvent.addPointerEvent(event)` is public and
  static (`SPD-classes/.../input/PointerEvent.java:132`), a `PointerEvent` has a public constructor
  (`:57-61`), and a button's `PointerArea` handles a DOWN then an UP inside its rectangle
  (`SPD-classes/.../input/PointerArea.java:57`). That is the human's click, delivered the way the
  input system delivers it, and it needs no hook — which matters, because ADR-0016 assigns action
  registration to E5 and this story may not spend a row.
- **The window's buttons are reachable** through hook row 4's accessor, `Group.shatterfishMembers()`,
  which `Windows.read` already walks for the prompt section
  (`shatterfish/harness/.../driver/Windows.java`).
- `shatterfish/api/.../ValidActions.java` is the set to re-validate against;
  `Observation.actions()` carries it, and `Observer.observe()` fills it.
- `shatterfish/harness/.../observer/Observer.java:930-960` is the Input-wait gate to copy, and
  `HeadlessDriver.waitState` is the shared definition of the state.

## Tasks & Acceptance

**Execution:**
- [ ] `shatterfish/harness/.../executor/ActionExecutor.java` (new) — `execute(Observation, Action)`
      returning an outcome that is either applied or a rejection with a reason; the gate, the
      re-validation, the `ItemRef` resolution, and one branch per Action kind over the paths above.
- [ ] `shatterfish/harness/.../executor/Rejection.java`, `Reason.java` (new) — the value and its
      reasons: not at an Input wait, not offered, item moved, no selector, no such option,
      unsupported.
- [ ] `shatterfish/api/.../ValidActions.java` — stop offering `Rest(false)`, which is the same
      human input as `Wait`; record it in ADR-0014's amendment.
- [ ] `shatterfish/harness/src/test/.../executor/ActionExecutorTest.java` (new) — the matrix rows.
- [ ] `shatterfish/harness/src/test/.../executor/ActionCompletenessTest.java` (new) — every branch
      of `Hero.handle` and every toolbar button against the Action kinds, with the alchemy pot and
      mining named as unsupported with a reason (FR-4).
- [ ] `shatterfish/harness/src/test/.../executor/ActionValidityPropertyTest.java` (new) — over
      scripted states, every Action of the set is applied and every Action outside it is rejected
      with the game's state unchanged, compared by the Observation's own hash.
- [ ] `docs/adr/0014-action-schema-and-executor-contract.md` — the story 1.13 amendment: the paths,
      the reasons, the pointer click for a prompt, the unsupported inputs.

**Acceptance Criteria:**
- Given a targeted item use, when it is executed, then the game's own selector is answered within
  the same Input wait, held by `ActionExecutorTest`.
- Given an Action the Observation's set does not carry, when it is executed, then nothing is called
  and the outcome names a reason, held by `ActionExecutorTest` and the property test.
- Given the game's hero-affecting inputs, when `ActionCompletenessTest` runs, then each maps to an
  Action kind or is listed as unsupported with a reason.
- Given random valid Actions over scripted states, when each is applied, then the state changes
  exactly once, and every invalid Action leaves the Observation's hash untouched.

## Design Notes

The executor never writes a model field. Every branch is one of: `GameScene.handleCell(cell)`,
`Hero.rest(flag)`, `Hero.search(true)`, `Hero.upgradeTalent(talent)`, `item.execute(hero, action)`
followed by the game's own selector being answered, or a pointer event posted at a window button.
That is what "the game's own guards apply to me" means: the guards are in those methods, and the
executor is never past them.

```java
// The shape of every branch: resolve from the Observation, then make the human's call.
case Action.Step step -> GameScene.handleCell(step.cell());
case Action.UseItemAt use -> {
    Item item = resolve(use.item());            // re-walks Belongings, checks the name
    item.execute(hero, use.action());           // opens the game's cell selector
    GameScene.handleCell(use.cell());           // the human's second click answers it
}
```

## Verification

**Commands:**
- `./gradlew build -Pshatterfish.mobile=off` — expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` — clean.
- A mutation battery over the executor and the reasons — expected: every mutation caught.
