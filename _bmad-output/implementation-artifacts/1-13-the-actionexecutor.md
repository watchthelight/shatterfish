---
story: 1.13
key: 1-13-the-actionexecutor
title: "The ActionExecutor"
epic: 1
issue: 26
status: done
created: '2026-09-11'
updated: '2026-09-11'
review_loop_iteration: 1
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

## Dev notes

As the bot,
I want my Actions applied through the same paths a human's clicks take,
So that the game's own guards apply to me.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v4.0.0`.

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| Given ADR-0014 and AD-14's rule that the executor is the only caller of the hero's input methods, when the executor is implemented, then a targeted item use drives the game's own selector within the same Input wait | **Met.** `UseItemAt` calls `Item.execute` and then `GameScene.handleCell(cell)`, which is the second click a person makes and which routes to the selector the item just installed; `UseItemOn` answers the bag window through its own `ItemSelector`, hiding first as the window's button does. `ActionExecutorTest` throws an item and holds the pack to having lost it |
| And it re-validates against the Observation's action set and rejects with a reason rather than an exception, before any game state changes | **Met.** `execute` asserts the Input wait, then requires the Action to be in the Observation's own set, and returns an `Outcome.Rejected` carrying a `Reason`; every reason but `NO_SELECTOR` is decided before the game is called. `ActionValidityPropertyTest` holds the screen's hash unchanged across every refusal at forty waits |
| And `ActionCompletenessTest` enumerates the game's hero-affecting inputs and asserts each maps to an Action kind or is listed as unsupported with a reason (FR-4) | **Met.** The test walks `HeroAction`'s own subclasses and the toolbar's buttons; eight map to kinds, two are named unsupported with their reasons, and a kind upstream adds lands in neither column and fails |
| And `ActionValidityPropertyTest` asserts over random states that every valid Action is accepted and every invalid one is rejected without mutating state | **Met.** Forty waits, an Action drawn from the set at each, and three strangers offered at every one of them; the Run walks the whole way and a stall is a failure |

## What was built

- `shatterfish/harness/.../executor/ActionExecutor.java`, with `Outcome` and `Reason` beside it.
- `shatterfish/api/.../Action.java`: `DismissPrompt`, appended; `PromptKind.MESSAGE`, appended;
  `ValidActions` offers the dismissal under a Prompt with no buttons and no longer offers
  `Rest(false)`, which is the wait button twice.
- `shatterfish/harness/.../driver/Prompts.java`: a message window is a Prompt;
  `HeadlessDriver.actionHandedOver()`, said by the executor.
- Tests: `ActionExecutorTest` (twelve), `ActionCompletenessTest` (three),
  `ActionValidityPropertyTest`, and, from the review, `ActionKindCoverageTest` and
  `StaleObservationTest`; `PromptGateTest`, `ObserverGateTest`, `ObserveTest` and
  `InputWaitCountTest` updated to the new contract, and three api-side tests for the dismissal, the
  single wait button and the ability the set no longer offers.
- Docs: the story 1.13 amendments in ADR-0014 and ADR-0006.

## What the story found

**Every human input has a public path, so no hook was spent.** This was the story's first risk:
ADR-0016 assigns action registration to E5, so an executor that needed a hook could not be built in
E1 at all. It does not. A cell Action is the click `GameScene.handleCell` makes, and `Hero.handle`
is what decides by cell whether that click is a step, an attack, an interaction, a pick-up, a
purchase, a chest, an unlock or a transition (`…/actors/hero/Hero.java:1929-2015`). The buttons are
`Hero.rest` and `Hero.search`. A talent is `Hero.upgradeTalent`. An item is `Item.execute` and then
the game's own selector, answered by the same second click or by the bag window's public selector.
And a Prompt's button, which has no public method and no key of its own, takes a `PointerEvent`
posted where the input system posts one (`SPD-classes/…/input/PointerEvent.java:132`) — the human's
tap, delivered the way taps are delivered.

**The driver could not always tell that an Action had been handed over.** ADR-0015 says a new Input
wait follows the hero's own notification, a change of the window in front, or an Action handed to
the game. The driver inferred the third from the hero holding an action or resting, which is true of
a move and false of detaching the broken seal: that plays the operate animation and returns
(`…/items/armor/Armor.java:190-197`), so the hero stayed ready, nothing announced anything, and the
Run stalled waiting for a wait that had already been served. `HeadlessDriver.actionHandedOver()` is
now said by the executor, so no caller has to notice. The mutation that removes it is caught by the
test that detaches the seal.

**A message stopped a Run dead, and story 1.10's reading is why.** The sewers post one when the hero
tries to leave without the amulet (`…/levels/SewerLevel.java:146-155`), and the caves post one when
the hero reaches the blacksmith's entrance without a pickaxe (`…/levels/CavesLevel.java:136-140`);
an ordinary step reaches either. Story 1.10 read ADR-0006's
Prompt row as "a window the game waits on", and a message is not that — the hero is ready
underneath it — so the driver would not call it a wait, the Observer would not read under it, and
the valid set had nothing to offer. The bot could see the window and not send it away. A person
taps it and plays on, so a message is a Prompt now, with no buttons and one Action; ADR-0006's
amendment carries the change and what stays a failure, which is a window the *player* opens and the
game is not waiting on. `WndStory` belongs with those: the review checked every site that opens one
and each is a click in the journal or the menu pane, so it is not a message and the gate still
refuses it.

**`Wait` and `Rest(false)` were two entries for one input.** The wait button is the rest button with
the flag down (`…/ui/Toolbar.java:203`, `:225`), so the set offered the same human input twice; it
offers `Rest(true)` and `Wait` now.

## Decisions taken inside the story

**A rejection is a value.** Alternatives: (a) an exception per refusal; (b) a boolean; (c) an
outcome that is applied or rejected with a reason. Chosen (c): ADR-0014 asks for a reason rather
than an exception, the Run log records one outcome per wait (ADR-0011), and a Brain that asks for
something it cannot have should be answered rather than crashed. `Reason` names six cases and the
rejection carries the words a person would need.

**The pointer, not a hook, for a Prompt's button.** Alternatives: (a) a hook row exposing
`WndOptions.onSelect`; (b) a key event, which those buttons do not bind; (c) the pointer path.
Chosen (c): it is the human's own input, it is public end to end, and ADR-0016 keeps action
registration for E5, so (a) was not this epic's to spend. Pre-mortem: the tap lands at the button's
centre in screen coordinates, so a camera that differs headless would miss it — the mutation that
moves the tap outside the button is in the battery, and the chasm's "no" is the test.

**The set is consulted, not recomputed.** Alternatives: (a) recompute from the game; (b) use the
Observation's own set; (c) both. Chosen (b) with (a) as the fallback when the Observation carries
none: the Decision was made against that set, and re-deriving it from the game would let the
executor accept something the Brain never saw.

## Evidence

`./gradlew clean build -Pshatterfish.mobile=off`: green, 492 tests across 44 suites. The harness
suite was run three more times to be sure nothing here depends on order.
`mkdocs build --strict`: clean.

**Mutation battery**, sixteen mutations of `ActionExecutor.java`, `Prompts.java`, `ValidActions.java`
and `HeadlessDriver.java`, each applied to a committed clean tree, run against the executor's three
suites, `InputWaitCountTest` and `ValidActionsTest`, restored with `git checkout`:

| # | Mutation | Caught by | What failed |
|---|---|---|---|
| M1 | the gate lets an Action through away from a wait | `ActionExecutorTest`, `StaleObservationTest` | org.opentest4j.AssertionFailedError: expected: <NOT_AT_AN_INPUT_WAIT> but was: <CANCELLED_INSTEAD> |
| M2 | the set is not consulted | `ActionExecutorTest`, `ActionValidityPropertyTest` | org.opentest4j.AssertionFailedError: Unexpected type, expected: <org.shatterfish.harness.executor.Outcome.Reje |
| M3 | a step clicks a different cell | `ActionExecutorTest`, `ActionKindCoverageTest` | org.opentest4j.AssertionFailedError: the hero is where the step named ==> expected: <403> but was: <404> |
| M4 | a pick-up clicks somewhere else | `ActionExecutorTest`, `ActionKindCoverageTest` | org.opentest4j.AssertionFailedError: the pack holds what it held ==> expected: <6> but was: <5> |
| M5 | waiting does nothing | `ActionExecutorTest` | org.opentest4j.AssertionFailedError: time moved: 0.0 to 0.0 ==> expected: <true> but was: <false> |
| M6 | the item reference is trusted by index alone | `ActionExecutorTest` | org.opentest4j.AssertionFailedError: waterskin offers [DROP, THROW] and not EAT ==> expected: <ITEM_MOVED> but |
| M7 | a targeted use never answers the selector | `ActionExecutorTest`, `ActionKindCoverageTest`, `ActionValidityPropertyTest` | org.opentest4j.AssertionFailedError: the throw happened: the pack changed ==> expected: <true> but was: <false |
| M8 | the driver is never told an Action was handed over | `ActionExecutorTest` | org.shatterfish.harness.driver.HeadlessDriver$Stalled: no Input wait within 10000 frames of 0.2 s. The last ac |
| M9 | a prompt answer presses the wrong button | `ActionExecutorTest` | java.lang.AssertionError: nowhere beside the hero to put a wall |
| M10 | a prompt answer presses nothing | `ActionExecutorTest` | org.opentest4j.AssertionFailedError: the window the answer closed ==> expected: <null> but was: <com.shattered |
| M11 | the tap lands outside the button | `ActionExecutorTest` | org.opentest4j.AssertionFailedError: the window the answer closed ==> expected: <null> but was: <com.shattered |
| M12 | a message is not a Prompt | `ActionExecutorTest`, `ActionValidityPropertyTest`, `InputWaitCountTest` | org.shatterfish.harness.driver.HeadlessDriver$Stalled: no Input wait within 10000 frames of 0.2 s. The last ac |
| M13 | a message offers no way out | `ActionExecutorTest`, `ActionValidityPropertyTest`, `ValidActionsTest` | org.opentest4j.AssertionFailedError: one Action, which is the tap that sends it away ==> expected: <[DismissPr |
| M14 | the dismissal does nothing | `ActionExecutorTest` | org.opentest4j.AssertionFailedError: and it is gone ==> expected: <null> but was: <com.shatteredpixel.shattere |
| M15 | an unknown item action is applied anyway | `ActionExecutorTest` | java.lang.NullPointerException: Cannot invoke "com.shatteredpixel.shatteredpixeldungeon.items.Item.actions(com |
| M16 | rest and wait are the same call | `ActionKindCoverageTest`, `StaleObservationTest` | org.opentest4j.AssertionFailedError: UseItemOn[item=ItemRef[index=2, name=broken seal, quantity=1], action=AFF |
| M17 | a talent takes a point past its ceiling | `ActionExecutorTest` | org.opentest4j.AssertionFailedError: a talent at its ceiling takes no more: Applied[action=Talent[talent=iron |
| M18 | a talent takes a point the tier does not have | `StaleObservationTest` | org.opentest4j.AssertionFailedError: a point already spent cannot be spent again ==> Unexpected type, expected |
| M19 | resting ignores the cancel the button asks for | **survives** | unreachable in a harness Run; the reason is in the code beside the guard |
| M20 | an item action the item does not offer is executed anyway | **survives** | unreachable in a harness Run; the reason is in the code beside the guard |

Eighteen of twenty are caught at the head of the branch. The battery grew by four during the review,
for the guards the review asked for, and M17 is the blocking finding itself: with the ceiling guard
removed, the talent test drives a talent past its maximum and fails.

Two survive, and the code says why beside each guard rather than leaving a number unexplained. M19
removes the cancel the wait and rest buttons ask for first: its three cases are the hero holding an
action, the hero resting, and an open cell selector, and the first two mean the hero is not at an
Input wait, where the gate has already refused, while the third needs a selector the harness scene
never installs (hook row 5). It stays because the overlay drives this same executor inside the real
game, where a person's selector can be open. M20 removes the check that an item still offers the
action being used: the schema will not carry an Action that contradicts its own Observation
(`Observation.java:120-127`), so the only way in is an Observation the world has moved past, and for
an item every move also moves the reference, which `ITEM_MOVED` answers first. `StaleObservationTest`
drives that case for the talent, where the state is cheap to move, which is what catches M18.

## The fairness review

The review returned **BLOCK** on one finding and it was right.

**`ActionExecutor`'s talent branch skipped the guard the pane's own button carries, and the bot
could push a talent past its ceiling.** `Hero.upgradeTalent` increments without asking anything
(`…/actors/hero/Hero.java:376-383`); every condition lives in `TalentButton`, which offers an
upgrade only while the mode is UPGRADE, the hero is alive, the tier has a point to spend and the
talent is under its maximum (`…/ui/TalentButton.java:114-119`). The executor called the unguarded
method. It was verified against the pinned code before anything was changed, and it survived the
whole story for the reason the review gave: nothing in the diff drove `Action.Talent` through the
executor at all. The branch now asks what the button asks; `ActionExecutorTest` drives a talent to
its ceiling and holds it there, `StaleObservationTest` drives the tier's point being spent twice,
and the two mutations that remove those guards are M17 and M18.

The review's other findings, each taken:

- **Four more guards the human path carries.** An item action is refused unless `item.actions(hero)`
  offers it; a rest cancels first, which is the other half of the toolbar's own guard; a prompt
  button that is inactive or drawn by no camera is refused rather than tapped; `ValidActions` no
  longer offers `Ability` or `AbilityAt`, which the executor always refused and which this story's
  own acceptance said it would not offer.
- **`HeadlessDriver.run` set `acted` after every execute**, so a refusal manufactured a wait out of
  nothing. The sequence's executor says for itself when something was handed over.
- **The examine window's row in ADR-0006 contradicted itself**, and `WndStory` was the reason: it
  was admitted as a message while every site that opens one is a click of the player's own.
- **A heap on the stairs takes the click before the transition does** (`Hero.java:1974`, `:2000`),
  so a `Descend` at such a cell wrote the wrong thing in the Run log; the set no longer offers it.
- **The step onto a sign was folklore.** There is no `Sign` at v4.0.0. Three documents told the
  story with it and now tell it with the two cases that are real.
- **Cites.** `Hero.handle` is `:1920-2008` at the tag; the working tree runs nine lines long in the
  two hooked files, and ADR-0014's Hero rows are re-cited at v4.0.0.
- **The battery's own report truncated to two failures per suite**, which made two attributions
  unreliable; it prints every failure now, and the chasm test says which button was pressed rather
  than leaving a later test to break on the state a wrong press left.

## Deviations

- No upstream file is touched and no hook row is spent, which the story's spec set out to prove
  possible and did.
- ADR-0006's Prompt row changed, which is a decision record amended rather than a story's own
  choice; the amendment says what moved and why.

## Known limitations, handed forward

- **The alchemy pot and mining have no Action kind**, and `ActionCompletenessTest` names both with
  reasons: the pot opens a scene rather than a window, and mining is a click on a wall the valid set
  cannot offer.
- **`Ability` and `AbilityAt` have no path yet**: the executor refuses them as unsupported, since
  the armour ability's own call belongs to the ability stories, and the valid set no longer offers
  them, which the review found contradicted this story's own acceptance.
- **A descent does not cross to the next floor.** The click is taken and the hero's action becomes
  the transition, which is the executor's whole part; the game's part runs in the interlevel scene,
  which this driver stops at. A probe drives the same descent with the game's own two calls and no
  Shatterfish code between them and stops in the same place, so it is the harness: issue #68.
- **A window with buttons that the Prompt table does not name** — the blacksmith's later windows,
  the crown's ability choice — still stops a Run at the driver.
- **A diagonal step into a doorway** is offered and the game routes around it.
- **`NO_SELECTOR` is the one rejection that can follow a change**, since the item was executed
  before the selector was found missing.

## Follow-ups for later stories

- Story 1.14 (#27): the random agent, which is this executor driven from the valid set until the
  hero dies; the property test is its rehearsal.
- The ability stories: `Ability` and `AbilityAt`.
