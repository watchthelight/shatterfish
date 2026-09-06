---
story: 1.10
key: 1-10-the-observer-part-three-inventory-journal-log-and-prompts
title: "The Observer, part three: inventory, journal, log and prompts"
epic: 1
issue: 23
status: review
created: '2026-09-06'
updated: '2026-09-06'
review_loop_iteration: 0
baseline_commit: '3f1883c794b8e2d618b60a269763f45f5174cd11'
---

# Story 1.10: The Observer, part three: inventory, journal, log and prompts

As the bot,
I want my inventory with exactly the identification the player has, plus the log and any prompt,
So that I can reason about unknown items without seeing their identity.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v3.3.8` (commit `7b8b845a`).

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| Given the rows of ADR-0006 for items, known appearances, the log, the journal and prompts, when the Observer builds those sections, then an unidentified potion appears under its appearance name only and its class is not recoverable, wand charges appear only when known, and a curse enchantment only when the curse is known | **Met.** `Observer.inventory()` reads `name()`, `visiblyUpgraded()`, `visiblyCursed()`, `status()` and `actions(hero)`, the values the slot and the item window draw; `ItemLeakTest` holds an unknown potion, scroll and ring to their appearance with the class and the true name absent from the JSON and the bytes, a wand's status empty until its level is known and `?/max` until its charges are, and a curse enchantment's word absent until the curse is known |
| The log section is captured from the game's own message signal rather than the rendered log component, and the Observer re-registers that listener whenever the scene is recreated | **Met.** `GameLogListener` listens on `GLog.update`; hook row 3, a site in `GameScene.create()` right after the pane replaces every listener, re-adds it before the floor's own lines are emitted; `LogListenerTest` changes floor twice with the scene destroyed and recreated as the game does, and holds the section to the descent line each creation emits, to a message after it, and to two listeners on the signal |
| An open Prompt of a recognised kind is exposed with its options, and any other window at an Input wait fails an assertion | **Met.** `Prompts.kind()` classifies the window in front, `Observer.prompt()` flattens it through hook row 4's accessor on `Group`, the header carries the kind, and the gate refuses any other window naming it; `PromptGateTest` holds the chasm prompt, a known harmful potion's warning, an options window of unlisted origin, an untitled one, a busy hero and a plain message window |
| `ItemLeakTest` asserts no true class, level, curse or identification counter appears | **Met.** By absence of the names and classes, and by byte-identical differential pairs over the level, the curse, the charges and the counters of a wand and a ring |
| `LogListenerTest` changes floor twice and asserts the log section still receives messages | **Met.** Two descents the way the loading scene does them, each followed by the new scene's own line and a message dispatched after |

## What was built

- `core/.../scenes/GameScene.java`: hook row 3, the scene seam, an import and six lines after `add( log )`; `core/.../shatterfish/Hooks.java`: the `LogReplaced` point; `SPD-classes/.../noosa/Group.java`: hook row 4's second site, `shatterfishMembers()`; `docs/UPSTREAM.md`: row 3, row 4's second site, the site index and the three digests.
- `shatterfish/harness/.../observer/Observer.java`: `inventory()`, `journal()`, `log()`, `prompt()`, the header's kind and the gate under a Prompt; `GameLogListener` (new); `driver/Prompts.java`: `kind()`, one definition for the driver and the Observer; `driver/Windows.java` (new): the window in front, a window's text blocks and button labels; `driver/HeadlessDriver.java`: the seam armed before the first scene, the capture reset per Run and left on close.
- `shatterfish/api`: `PromptKind.ITEM` and `PromptKind.OTHER`, appended.
- Tests: `ItemLeakTest`, `JournalSectionTest`, `LogListenerTest`, `PromptGateTest`; `Skeleton` around every section.
- Docs: ADR-0006 amendment for story 1.10; two notes in ADR-0005; Test columns and three rows in the rules pages.

## What the story found

**The pane replaces every listener, and the scene speaks before it returns.** `GameLog()` calls
`GLog.update.replace(this)` (`…/ui/GameLog.java:47`), which is `removeAll` then `add`
(`SPD-classes/…/utils/Signal.java:58-61`), and `GameScene.create()` emits the descent line and the
floor's feeling after constructing the pane (`…/scenes/GameScene.java:499-502`, `:596-599`,
`:663-689`). A listener re-added after `create()` returns would miss what the pane shows, which is
why the seam is a hook inside `create()` and not a line after `super.create()` in the headless
scene; the pane's handler returns false (`GameLog.java:149-154`), so a listener after it hears
every message.

**A window's parts are members of members, with no getter.** `Group.members` is protected
(`SPD-classes/…/noosa/Group.java:33`); a title is a text block inside an `IconTitle`
(`…/windows/IconTitle.java:41-42`), a message a text block, an option a `RedButton`, whose label
`StyledButton.text()` returns (`…/ui/StyledButton.java:124`). The accessor returns a copy under
the class's own lock, and walking `Game.scene()`'s members finds the window in front as the scene
does for itself (`…/scenes/GameScene.java:1376-1384`).

**The chasm's window is two anonymous classes deep.** `Chasm.heroJump` declares its `WndOptions`
inside an anonymous `Callback` (`…/levels/features/Chasm.java:59-62`), so the class that opened a
window is the nearest named class enclosing it, not the enclosing class alone; the first run of
`PromptGateTest` read the chasm prompt as `OTHER` and said so.

**A level's construction updates a live scene's map.** Rebuilding a level with the old scene
still alive dereferences `Dungeon.level` while it is null, since `Level.set` reaches
`GameScene.updateMap` and the scene is not null; the game destroys the play scene before the
loading scene runs (`SPD-classes/…/noosa/Game.java:212-220`, `:246-258`), and the test does the
same.

**Every hero starts with a velvet pouch and knows the scroll of identify**
(`…/actors/hero/HeroClass.java:111`, `:117`), besides what the class knows
(`:183-184`); a stone put into the backpack lands in the pouch, and the known appearances at the
first wait are three.

**A default action is kept while it is not offered.** An empty waterskin offers no drink and its
default stays drink (`…/items/Waterskin.java:52`, `:74-78`); the item window colours the default
only when it is among the buttons (`…/windows/WndUseItem.java:72-74`), and the section carries
both as they are.

**A potion opens two windows from one class.** The harmful-drink warning and the beneficial-throw
confirmation are both `Potion`'s anonymous subclasses (`…/items/potions/Potion.java:238-252`,
`:264-280`); the title tells them apart, and only the first is `HARMFUL_POTION`.

**An item's identification fields are not what the screen draws for most items.** `identify()`
sets `levelKnown` and `cursedKnown` (`…/items/Item.java:468-469`), so a potion identified by a
scroll and one of the same known type picked up later differ in the fields while the slot and the
window draw them alike; the review found it, and the flags now follow the item's own
identification predicate where that is what the screen shows, a potion's or scroll's `isKnown()`
and the constant truth of food, keys, stones, bags and the rest, with the raw fields kept only where
the slot and the window draw their effect.

**A titled message draws its title last.** `WndTitledMessage` lays the title bar out first and then
brings it to the front (`…/windows/WndTitledMessage.java:42-68`), so the members' order, which is
the drawing order, ends with the title; the first version of the reading took the first text
block for the title and read a quest window's message as its title, which the review's test
caught. The icon title is now read by type. A shop heap titles itself with its price
(`…/items/Heap.java`, `title()`), and the trade window draws that.

**A window's slots draw bitmap texts.** The resurrection window's two item buttons hold slots whose
status, strength and level texts are members of members (`…/windows/WndResurrect.java:75-93`),
which the review feared the walk would join into the prompt's text; they are bitmap texts
(`…/ui/ItemSlot.java:58-61`), never text blocks, and out of the walk's reach by type, which the
battery showed when a clause skipping slots changed nothing. The walk now skips every member the
group would not draw, and `PromptGateTest` hides a block and holds it unread.

**The pane wipes with no message on the signal.** `GameLog.wipe()` on picking up the guidebook
(`…/items/journal/Guidebook.java:57`) and in the settings (`…/windows/WndSettings.java:1093`) empties
the pane while the section keeps its lines, a loss recorded in ADR-0005 and ADR-0006; and a
message dispatched while the first floor is built reaches the next pane through the signal's static
buffer (`…/ui/GameLog.java:52`, `:57-60`), so the listener joins the signal at every Run's start.

**A holiday reaches the Observation through a pasty.** `Pasty.name()` switches on the calendar
(`…/items/food/Pasty.java:56-90`; `…/utils/Holiday.java:54-59`), so two Runs of one tuple can name
an item differently by date; older than this story, recorded in `docs/ideas.md` for the Profile
story to pin.

## Decisions taken inside the story

**The window is read through an accessor hook, not reflection.** Alternatives: (a) a declared
reflective read of `Group.members` in a second confined class; (b) an accessor on `Window` only;
(c) one on `Group`; (d) no read, rebuilding the labels from the message keys the window used.
Chosen (c): (a) grows the reflection allowance the ledger cannot see, which is what
`HarnessReflectionTest` exists to stop, and row 4 is the ledger's own place for private state the
screen shows; (b) cannot reach a title inside an `IconTitle`; (d) is blind for a window whose
labels are computed. Pre-mortem: the accessor reads a copy under the lock, writes nothing, and is
dead code to the game.

**The seam is a hook in `GameScene.create()`.** Alternatives: (a) re-add the listener in
`HeadlessScene.create()` after `super.create()`; (b) a hook right after the pane; (c) read
`GameLog`'s static buffer by reflection. Chosen (b): (a) misses the lines `create()` emits and
does nothing for the Overlay's scene, which is upstream's own; (c) reads the pane's view, which
ADR-0005 rejects. Row 3 is "the scene seam, including where the Observer re-registers its log
listener" (ADR-0016), spent here for its first site.

**Prompt kinds by class, and for an options window by the class that opened it.** Alternatives:
(a) classify by the title text; (b) by the enclosing class of the anonymous subclass every opener
declares; (c) fail on any options window not in a table. Chosen (b), with two members appended,
`ITEM` for any item's confirmation or choice and `OTHER` for an origin the table does not name,
so a Run never fails on a window the driver already accepts; (a) is brittle and reads the
question to classify it, kept only where one class opens two windows; (c) would stop a Run at the
amulet's prompt or the examine chooser for nothing.

**The title is the icon title's label by type, else the first text block when there are two.**
Alternatives: (a) read the `IconTitle` by type; (b) the first block when a window draws at least
two. First chosen (b), for needing no type; the review's quest-window test showed a titled
message brings its title bar to the front after laying it out
(`…/windows/WndTitledMessage.java:67`), so the drawing order has the title last, and (a) is what
the screen shows, with (b) kept for an options window whose title is a plain block.

**The inventory's actions are identifiers.** Alternatives: (a) the labels the buttons draw; (b)
the identifiers the window executes. Chosen (b): `UseItem` names an action and the executor runs
`item.execute(hero, action)` with it (ADR-0014); the label is static text the Codex carries.

**One definition of a wait's state.** Alternatives: (a) the Observer's own copy of the driver's
condition, as first written; (b) `HeadlessDriver.waitState(hero, window)`, shared, with the
driver's two timing conditions, a window's second frame and an empty render queue, kept in the
driver. Chosen (b), as the review asked: the Observer reads a state, and cannot see frames or the
queue, so the class comment says what it asserts and what it cannot.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green, 435 tests across 33 suites, fifty-two of them the eleven observer suites. `mkdocs build --strict`: clean.
`HooksLedgerTest` and `HooksVanillaTest` green with row 3's digest `25da1e43728094a5` for
`GameScene.java` (twenty-five added, two removed, the two being row 5's earlier edits counted
again as the file's whole diff), row 2's `d83c280bff9d2cce` for `Hooks.java` (one hundred and
fourteen added) and row 4's `76cc92a3ce10f1c6` for `Group.java` (nine added, none removed).

**Mutation battery**, twenty-two mutations of `Observer.java`, `GameLogListener.java`,
`Prompts.java` and `HeadlessDriver.java` on the committed tree at ``d3a5ce879`, then all thirty-one at `bba2ef279``, each applied
to a clean tree, run against `ItemLeakTest`, `JournalSectionTest`, `LogListenerTest` and
`PromptGateTest`, restored with `git checkout`, and the tree verified clean after each; nine
more were added for the review's rules and run on the review commit:

| # | Mutation | Caught by |
|---|---|---|
| M1 | the gate admits any window | `PromptGateTest` (a message window: the section refuses a `NONE` prompt with content, so the read fails, though not with the gate's own exception) |
| M2 | the header carries no Prompt kind | `PromptGateTest` (the chasm prompt's header; the busy hero) |
| M3 | the prompt title is always empty | `PromptGateTest` (the chasm, the harmful potion, the options window) |
| M4 | the prompt options are dropped | `PromptGateTest` (every prompt's labels) |
| M5 | the chasm prompt is `OTHER` | `PromptGateTest` (the chasm's kind) |
| M6 | the harmful potion is an item confirmation | `PromptGateTest` (the harmful potion's kind) |
| M7 | every window is a Prompt | `PromptGateTest` (a message window is `NONE`) |
| M8 | the item name is the true name | `ItemLeakTest` (the appearance names; the ring's slot; the dagger's name) |
| M9 | the level is the true level | `ItemLeakTest` (the record refuses a level with `levelKnown` false) |
| M10 | the curse flag is the true curse | `ItemLeakTest` (the record refuses a curse with `cursedKnown` false) |
| M11 | a wand's charges are shown whether or not known | `ItemLeakTest` (no status until the level is known) |
| M12 | no item is in a slot | `ItemLeakTest` (the belongings' order) |
| M13 | a known appearance names its class | `ItemLeakTest` (the known appearances, twice) |
| M14 | a key record loses its count | `JournalSectionTest` (the counted key) |
| M15 | a written note loses its floor | `JournalSectionTest` (the note written for a floor) |
| M16 | the actions are dropped | `ItemLeakTest` (drop and throw at least) |
| M17 | a positive message reads plain | `LogListenerTest` (the tones; the pane's merge) |
| M18 | the log keeps every line | `LogListenerTest` (the section refuses more than sixty-four) |
| M19 | the listener is not re-added at the seam | `LogListenerTest` (all three: nothing is captured) |
| M20 | the seam is armed after the first scene | `LogListenerTest` (all three: the first scene's creation is missed) |
| M21 | the new-line marker is a line | `LogListenerTest` (five messages and no line for the marker) |
| M22 | the gate ignores a Prompt's waiting hero | `PromptGateTest` (the busy hero) |
| M23 | the front window is the first shown, not the last | `PromptGateTest` (a message over an options window) |
| M24 | every potion window is the harmful kind | `PromptGateTest` (the beneficial throw) |
| M25 | an item's window is not its own kind | `PromptGateTest` (the chalice's warning) |
| M26 | hidden members are read | `PromptGateTest` (a hidden block; first run with a dead item-slot clause, it survived and the clause went) |
| M27 | the flags are the raw fields for every item | `ItemLeakTest` (the potion identified by a scroll and the one picked up known) |
| M28 | the journal lists a floor the tab does not | `JournalSectionTest` (a record beyond the deepest floor) |
| M29 | the listener does not join the signal on reset | `LogListenerTest` (a message while the first floor is built) |
| M30 | an unidentified artifact shows a status | `ItemLeakTest` (the artifact's status) |
| M31 | the title is the first text block whatever the icon title | `PromptGateTest` (the quest window's title) |

Thirty of thirty-one caught at `bba2ef279`; M26 survived there through a clause that skipped item slots, whose texts the walk never reached, and was caught at `71bc11116` once the clause went and a test hid a block. All thirty-one caught.

## The fairness review

Run as an isolated `fairness-reviewer` on commit `d3a5ce879`. Verdict: FINDINGS, none blocking:
no read carries something the screen does not draw, the log does not print or the journal does
not show, and the hooks are minimal, read-only and ledgered with digests the review recomputed by
hand. Ten should-fix findings, all taken in the review commit:

1. **The level and curse flags were raw fields.** `identify()` sets both
   (`…/items/Item.java:468-469`), so a potion identified by a scroll and one of the same known
   type picked up later differed in the fields while the slot and the item window drew them
   alike, against the differential property the epic promises. The flags now follow the item's
   own identification predicate where that is what the screen draws, a potion's or scroll's
   `isKnown()` and the constant truth of food, keys, stones, bags, spells and bombs, and stay the
   raw fields for weapons, armor, wands, rings and artifacts, whose slot and window draw them;
   `ItemLeakTest` holds the two potions to one Observation, and M27 shows the raw fields fail it.
2. **The journal read an invariant rather than the tab's predicate.** Every record satisfied the
   tab's floor range today, but the door should read through the drawing call; the section is now
   built from the tab's own two calls (`…/journal/Notes.java:685-705`), and `JournalSectionTest`
   holds a landmark beyond the deepest floor to be no note, with M28 behind it.
3. **The window walk read what the screen hides.** The walk ignored a member's `exists` and
   `visible`; it now skips a member the group would not draw
   (`SPD-classes/…/noosa/Group.java:72-79`), and `PromptGateTest` hides a block and holds it
   unread, with M26 behind it. The review also feared the resurrection window's two item slots
   would add their texts (`…/windows/WndResurrect.java:75-93`); they are bitmap texts
   (`…/ui/ItemSlot.java:58-61`), never text blocks, and out of reach by type, which the battery
   showed when a clause skipping slots changed nothing, so that clause went and the test reads the
   resurrection window to its message alone. Writing that test found a second
   thing: a titled message brings its title bar to the front after laying it out
   (`…/windows/WndTitledMessage.java:67`), so the drawing order has the title last and the first
   text block was the message; the icon title is now read by type, and M31 holds it.
4. **The gate claimed to be the driver's definition and was not.** The driver confirms a window
   only from its second frame in front and only with the render queue empty; the Observer checks
   neither and cannot. The state condition is now one method, `HeadlessDriver.waitState`, that both
   use, and the Observer's class comment says the two timing conditions are the driver's.
5. **The wall clock reaches the Observation through a pasty**, whose name switches on the
   holiday (`…/items/food/Pasty.java:56-90`; `…/utils/Holiday.java:54-59`): older than this story,
   recorded in `docs/ideas.md` for the Profile story to pin.
6. **The pane wipes with no message on the signal** (`…/items/journal/Guidebook.java:57`;
   `…/windows/WndSettings.java:1093`), so the capture does not equal what the pane shows after a
   wipe; ADR-0005's sentence and the amendment now say so, a loss of memory the human also has.
7. **The seam was armed only through the hook, after the first floor was built.** A message
   dispatched during `Dungeon.init()`, `newLevel()` or `switchLevel()` would reach the next pane
   through the signal's static buffer (`…/ui/GameLog.java:52`, `:57-60`) and miss the Observer;
   `reset()` now joins the signal at once, and `LogListenerTest` holds a message dispatched while
   the first floor is built, with M29 behind it.
8. **Six cites were off**: the descent line is `GameScene.java:596-599`, the feelings `:663-689`,
   `Signal.replace` `:58-61` and `add` `:40-48`, `showingWindow()` `:1376-1384`,
   `interfaceBlockingHero()` `:1386-1396`, the slot's level text `ItemSlot.java:279-283`; fixed in
   the ledger, the amendment, the rules row, the tests and the code.
9. **Row 4's prose miscounted** the `Group` site's comment lines; four, not five.
10. **Tests the amendment's claims lacked**: the front window being the last shown, the
    beneficial throw and an item's own confirmation, the recognised windows by class, an
    artifact's status, the Catalog against the known appearances, every family and every slot; all
    added, and nine mutations with them (M23 to M31).

The review also named the boundary `Class.getEnclosingClass()` and `isAnonymousClass()` sit on,
class metadata rather than reflection into a member, which `HarnessReflectionTest`'s comment now
states, and confirmed that every options window the game opens at the tag is an anonymous
subclass declared by its opener, that `Windows.front()` takes the last window as the scene does,
and that no thread order or hash order reaches the bytes.

## Deviations

- Two upstream files and the registry are edited: labelled `touches-upstream`, ledgered as row 3 (new), row 4 (second site) and row 2 (the point), each with its digest; the files are written with LF as the index holds them.
- `LogListenerTest` changes floor itself, the way the loading scene does, since serving the scene change the game requests is the Run stories' work (ADR-0015).
- No manual `:desktop:debug` check: both hooks are dead code to the game.

## Known limitations, handed forward

- **No `observe()` yet**: blobs, feeling, transitions and the danger count (1.11), then the whole.
- **The blacksmith's later windows and the crown's ability choice are not recognised** (`WndBlacksmith.WndSmith`, `WndReforge`, `WndChooseAbility`); a Run that reaches one stops at the driver.
- **A quest window with no buttons carries no options**; how a Brain dismisses it is ADR-0014's to say when the executor lands (1.13).
- **A lost inventory greys every slot the pane draws**; the section lists the items as drawn and does not carry the greying.
- **Two identical written notes would refuse the section**, since `JournalSection` refuses repeats and `CustomRecord` equality is by id; the bot never writes one.
- **Highlighting marks stay in a prompt's text**, as the text block holds them.
- **The pane's wipe is not mirrored**: after the guidebook is picked up or the settings wipe the pane, the section keeps lines the human saw before.
- **The gate reads a state the driver confirms with two timing conditions besides**, a window's second frame and an empty render queue; a read between an act and the frame that shows its window is a read of the state the frame confirms.
- **A holiday names a pasty by the calendar** (`docs/ideas.md`); the Profile story pins it.

## Follow-ups for later stories

- Story 1.11: blobs, feeling, transitions, the danger count, `observe()`, the checklist over ADR-0006's rows.
- Story 1.12: the valid Actions, `AnswerPrompt` over the options the section carries.
- Story 1.13: the executor's answer to a window with no buttons, and the windows not yet recognised.
