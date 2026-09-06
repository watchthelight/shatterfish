---
story: 1.10
key: 1-10-the-observer-part-three-inventory-journal-log-and-prompts
title: "The Observer, part three: inventory, journal, log and prompts"
epic: 1
issue: 23
status: in-progress
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

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Observer builds the header, the map, the actors and the hero (stories 1.8 and
1.9) but not the inventory, the journal, the log or the Prompt, so no Observation can yet be
assembled, and the harness has no listener on the game's message signal, which the game replaces
with its log pane on every scene creation. Items are where a leak would matter most: an
unidentified potion's class, an unknown level or curse, a wand's unknown charges.

**Approach:** Four more Observer methods, each reading only what the bag window, the item window,
the journal's notes tab, the log pane and the open window draw: `inventory()` from the belongings
in the order the bag iterates them, with the item's own display name, status and actions;
`journal()` from the notes records and the per-Run known potions, scrolls and rings; `log()` from
a listener on `GLog.update` that a scene-seam hook (ledger row 3) re-registers right after the
pane replaces it; `prompt()` from the front window, classified into a `PromptKind` and flattened
into its drawn title, text and button labels through a read-only accessor on `Group` (ledger
row 4, second site), since a window's members are protected. The Observer's gate admits a
recognised Prompt window and fails on any other.

## Boundaries & Constraints

**Always:** Information parity by construction: every read is a value the screen draws or the
game's own log signal carries; the true class of an unidentified potion, scroll or ring, a level
or curse the player has not learned, a wand's charges before they are known, and the
identification counters never reach the Observation. Every rule cites `path:line` at the tag. Any
edit to an upstream file is a hook with a marker, a ledger row and a digest in the same PR.
`Observer` stays the one door; `brain` and `api` never import game code. Reflection stays confined
to `SceneStepper`; the window is read through an accessor hook, not reflection. The log is captured
from `GLog.update`, never from `GameLog`'s rendered entries. Reads happen only at an Input wait.

**Ask First:** Anything that would spend a hook row beyond rows 2, 3 and 4 as ADR-0016 lays them
out; any change to the `Observation` record's shape or the codec (adding enum members at the end
is in scope, as story 1.9 did for `HeapKind`); any window kind the driver would have to start
recognising that ADR-0006's Prompt row does not name.

**Never:** No `observe()` (story 1.11), no blobs, feeling, transitions or danger count (1.11), no
valid-Action set (1.12), no serving of the scene change the game requests at the stairs (the Run
stories); `LogListenerTest` performs the floor change itself the way the loading scene does. No
reading of `Catalog` (cross-Run), of guide pages, of the bestiary, of `ItemStatusHandler`'s
unknown set, of `Item.level()` or `Item.cursed` directly, of `Wand.curCharges` when not known. No
change to `Prompts`' list of windows beyond giving each a kind. No Overlay work: the hook fires in
the real game too, but nothing listens there yet.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Unidentified potion in the backpack | `PotionOfInvisibility`, not known this Run | `ItemView(POTION, "<colour> potion", …)`; the class name and the true name absent from JSON and bytes; two Runs whose potion classes share a colour would be indistinguishable here | N/A |
| Unknown level and curse | a weapon at +2, `levelKnown=false`, cursed with a curse enchantment, `cursedKnown=false` | `levelKnown=false`, `visiblyUpgraded=0`, `cursedKnown=false`, `visiblyCursed=false`, name without the curse's word; byte-identical to the same weapon at +0 and uncursed | N/A |
| Wand with unknown charges | fresh wand, `curChargeKnown=false` | `status` is `null`→`""` until `levelKnown`, then `"?/max"`; byte-identical across different `curCharges` | N/A |
| Identification counters | `Wand.usesLeftToID`, `Ring.levelsToID` differ | byte-identical Observations | N/A |
| Equipped items | weapon, armor, artifact, misc, ring, second weapon | first, in slot order, each with its `EquipSlot`; then the backpack with sub-bags before their contents | the record refuses any other order |
| Journal | a landmark, a key record with quantity 2, a custom note | `NoteView`s with kind, depth, title, text (custom only), quantity (keys only) | N/A |
| Known appearances | `PotionOfHealing` and `ScrollOfRage` identified at start (Warrior) | `KnownAppearance(POTION, "Potion of Healing")`, `(SCROLL, "Scroll of Rage")`; an unknown class absent | N/A |
| Log across floors | first wait, then two floor changes the loading scene's way | after each new scene the section carries the "descend" line the scene emitted and any later message; older lines kept up to 64 | N/A |
| Log burst | 100 messages in one wait | the newest 64, oldest dropped, tone from the prefix, the new-line marker dropped | N/A |
| Prompt: chasm | `Chasm.heroJump(hero)` posted and drawn | `PromptSection(CHASM_JUMP, "<chasm title>", "<jump text>", ["Yes", "No"])`; header kind equal | N/A |
| Prompt: harmful potion | known `PotionOfLiquidFlame` drunk | `HARMFUL_POTION` with the harmful title and yes/no | N/A |
| Prompt: an options window of unlisted origin | an anonymous `WndOptions` from the test | `OTHER`, with its title, text and labels | N/A |
| Not a Prompt | a `WndMessage` in front at a wait | every Observer method throws `IllegalStateException` naming the window | the assertion the AC asks for |
| No window | ordinary wait | `PromptSection.NONE`; header kind `NONE` | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/harness/src/main/java/org/shatterfish/harness/observer/Observer.java` -- the door; `atInputWait()` (:546-556) is the gate to relax for a recognised window; `header()` (:142-153) returns `PromptKind.NONE` and must carry the window's kind; new `inventory()`, `journal()`, `log()`, `prompt()`.
- `shatterfish/harness/src/main/java/org/shatterfish/harness/driver/Prompts.java` -- `isRecognised(Window)` over nine window classes; becomes `kind(Window)` returning `PromptKind`, with `isRecognised` as `kind != NONE`.
- `shatterfish/harness/src/main/java/org/shatterfish/harness/driver/HeadlessDriver.java` -- `start()` (:174-186) creates the scene after `newGame`; the log listener must be installed before `switchTo`; `close()` (:423-438) clears `Hooks`; `newGame()` wipes `GameLog` (:250) and must reset the capture.
- `shatterfish/harness/src/main/java/org/shatterfish/harness/scene/HeadlessScene.java` -- `openWindow()` (:74-84) reads its own members; the Observer reads the front window through the `Group` accessor on `Game.scene()` instead, so the Overlay's scene works the same way.
- `shatterfish/api/src/main/java/org/shatterfish/api/{ItemView,InventorySection,JournalSection,NoteView,KnownAppearance,LogLine,LogSection,PromptSection,PromptKind,ItemKind,EquipSlot}.java` -- the records (story 1.7); `PromptKind` gains `ITEM` and `OTHER` at the end; `ItemKind`'s Javadoc names the package mapping.
- `shatterfish/harness/src/test/java/org/shatterfish/harness/observer/Skeleton.java` -- `around(...)` overloads; add one taking every section.
- `core/…/shatterfish/Hooks.java` -- row 2, the registry; gains `LogReplaced` and `logReplaced`, cleared in `clear()`; the digest line in `docs/UPSTREAM.md` changes.
- `core/…/scenes/GameScene.java:499-502` -- `log = new GameLog()` then `add(log)`; the row 3 site goes after `add( log )` and before `:536` where the "descend" message is emitted; `GameLog()` calls `GLog.update.replace(this)` (`…/ui/GameLog.java:47`), `Signal.replace` is `removeAll` then `add` (`SPD-classes/…/utils/Signal.java:56-59`), and `GameLog.onSignal` returns false (`GameLog.java:149-154`) so a listener added after it still hears every message.
- `SPD-classes/src/main/java/com/watabou/noosa/Group.java:33` -- `protected ArrayList<Gizmo> members`, no getter; row 4's second site, `shatterfishMembers()` returning a copy under the class's own `synchronized`.
- `…/utils/GLog.java:32-60` -- the prefixes and the signal; `…/ui/GameLog.java:52-131` -- the pane's prefix-to-colour rule the tone mirrors.
- `…/items/Item.java:110-115` (`actions`), `:179-181` (`defaultAction`), `:433-451` (`visiblyUpgraded`, `visiblyCursed`, `isIdentified`), `:483-499` (`title`, `name`), `:501-503` (`trueName`), `:570-572` (`status`); `…/items/potions/Potion.java:377-379`, `…/items/scrolls/Scroll.java:240-242`, `…/items/rings/Ring.java:172-174` (appearance names); `…/items/wands/Wand.java:336-343` (`status`); `…/items/artifacts/Artifact.java:189-206`; `…/items/weapon/Weapon.java:408-416`, `…/items/armor/Armor.java:573-581` (a curse's name hidden until `cursedKnown`); `…/ui/ItemSlot.java:220-300` (what a slot draws); `…/windows/WndUseItem.java:54-76` (the buttons are `actions(hero)`).
- `…/actors/hero/Belongings.java:422-453` -- the iterator: `weapon, armor, artifact, misc, ring, secondWep` then the backpack; `…/items/bags/Bag.java:216-250` -- a bag then its contents.
- `…/journal/Notes.java:73-100` (`Record`), `:145-290` (`LandmarkRecord`), `:296-372` (`KeyRecord`), `:388-520` (`CustomRecord`), `:685-693` (`getRecords(Class)`); `…/windows/WndJournal.java:474-565` -- the notes tab draws custom records then every floor's records down from `Statistics.deepestFloor`.
- `…/items/potions/Potion.java:402-404`, `…/items/scrolls/Scroll.java:265-267`, `…/items/rings/Ring.java:280-282` -- `getKnown()`.
- `…/windows/WndOptions.java:40-100` -- title as `IconTitle` or a text block, the message block, one `RedButton` per option; `…/windows/IconTitle.java:41-42` (`tfLabel`); `…/ui/StyledButton.java:35`, `:124` (`text()`); `…/ui/RenderedTextBlock.java:96` (`text()`); `…/windows/{WndChooseSubclass,WndQuest,WndTradeItem,WndResurrect,WndSadGhost,WndWandmaker,WndImp,WndBlacksmith}.java` -- the recognised windows, each an `IconTitle`, a message and `RedButton`s.
- Origins of `WndOptions` at the tag: `…/levels/features/Chasm.java:57-96`; `…/items/potions/Potion.java:238-252` (harmful drink), `:264-280` (beneficial throw); `…/ui/TalentsPane.java:189-192`; `…/windows/WndChooseSubclass.java:61`, `:106`; `…/windows/WndResurrect.java:102`; `…/windows/WndTradeItem.java:178`; `…/actors/mobs/npcs/Shopkeeper.java:256`; `…/windows/WndBlacksmith.java:83`, `:146`, `:171`; `…/actors/mobs/npcs/RatKing.java:137`; `…/levels/CavesLevel.java:142`, `…/levels/MiningLevel.java:282`, `…/levels/CityLevel.java:158`; items of every family (`KindofMisc.java:88`, `KindOfWeapon.java:57`, `Armor.java:265`, `BrokenSeal.java:138`, `ScrollOfEnchantment.java:75`, …); `…/scenes/GameScene.java:1694`; `…/levels/HallsBossLevel.java:343`; `…/actors/hero/abilities/mage/WarpBeacon.java:86`.
- `docs/adr/0006-observer-visibility-rules.md:70-77` -- the rows this story implements; `docs/adr/0005-observation-schema-and-hashing.md:250-280` -- the inventory, journal, log and prompt paragraphs; `docs/adr/0016-hook-ledger-corrected-by-story-1-1.md:56-65` -- rows 3 and 4; `docs/UPSTREAM.md:66-75`, `:117-125`, `:153-161` -- the rows, the site index, the diff budget; `docs/rules/identification.md`, `docs/rules/visibility.md`, `docs/rules/ui.md` -- rule rows whose Test column this story fills.
- `_bmad-output/implementation-artifacts/1-9-the-observer-part-two-actors-emotes-buffs-and-the-hero.md` -- the previous story's shape and its handed-forward list.

## Tasks & Acceptance

**Execution:**
- [ ] `shatterfish/api/.../PromptKind.java` -- append `ITEM` (a window of options an item opened: a confirmation or a choice) and `OTHER` (a window of options from an origin the table does not name), with Javadoc; no version bump, as story 1.9 did for `HeapKind` -- the recognised windows need a kind each.
- [ ] `core/.../shatterfish/Hooks.java` -- add `interface LogReplaced { void onLogReplaced(); }`, `public static volatile LogReplaced logReplaced`, null it in `clear()` -- row 2 says a listener point edits this file only.
- [ ] `core/.../scenes/GameScene.java` -- after `add( log );` (:502): marker `// shatterfish-hook:3`, three comment lines, `if (Hooks.logReplaced != null) Hooks.logReplaced.onLogReplaced();` -- the seam fires after the pane replaced the listener and before the messages `create()` emits.
- [ ] `SPD-classes/.../noosa/Group.java` -- marker `// shatterfish-hook:4`, comment, `public synchronized List<Gizmo> shatterfishMembers()` returning `new ArrayList<>(members)` -- the window's title, text and buttons are members of members with no getter.
- [ ] `docs/UPSTREAM.md` -- row 3 (new), row 4 (second site), row 2's digest; site index lines `3 1 GameScene`, `4 1 Group`; diff budget lines for `GameScene.java`, `Hooks.java`, `Group.java` -- read each digest from `HooksLedgerTest`'s failure message.
- [ ] `shatterfish/harness/.../observer/GameLogListener.java` (new) -- `Signal.Listener<String>` and `Hooks.LogReplaced`; synchronized ring of the newest 64 `LogLine`s; tone from the prefix as `GameLog.java:72-87`, new-line marker dropped; `install()` sets `Hooks.logReplaced`, `reset()`, `uninstall()` removes itself from `GLog.update` -- the Observer owns the listener (ADR-0006, Log).
- [ ] `shatterfish/harness/.../driver/HeadlessDriver.java` -- `start()`: `GameLogListener.install()` before `switchTo`; `newGame()`: `GameLogListener.reset()` beside `GameLog.wipe()`; `close()`: `uninstall()` -- the seam must be armed before the first scene is created.
- [ ] `shatterfish/harness/.../driver/Prompts.java` -- `kind(Window)`: `WndChooseSubclass`→`SUBCLASS`, `WndResurrect`→`RESURRECTION`, `WndTradeItem`→`SHOP`, `WndQuest`/`WndSadGhost`/`WndWandmaker`/`WndImp`/`WndBlacksmith`→`QUEST`, `WndOptions` by the enclosing class of its anonymous subclass (`Chasm`→`CHASM_JUMP`; a `Potion` with the harmful title→`HARMFUL_POTION`; any `Item`→`ITEM`; `TalentsPane`→`TALENT`; `WndChooseSubclass`→`SUBCLASS`; `WndResurrect`→`RESURRECTION`; `WndTradeItem`/`Shopkeeper`→`SHOP`; `WndBlacksmith`/`WndSadGhost`/`WndWandmaker`/`WndImp`/`RatKing`/`CavesLevel`/`MiningLevel`/`CityLevel`→`QUEST`; else `OTHER`); `isRecognised` = `kind != NONE` -- one definition for the driver and the Observer.
- [ ] `shatterfish/harness/.../observer/Observer.java` -- gate: front window from `Game.scene()`'s members; none → `heroWaits && !interfaceBlockingHero()`; a window → `Prompts.kind != NONE` and (`heroWaits` or `WndResurrect.instance != null`), else throw naming the window; `header()` carries the kind; `inventory()`, `journal()`, `log()`, `prompt()` with the rules of Design Notes; `ItemKind` by package; class Javadoc updated -- the sections.
- [ ] `shatterfish/harness/src/test/.../observer/ItemLeakTest.java` (new) -- unidentified potion, scroll and ring names and classes absent; unknown level and curse; wand charges; counters; equipped order; identify then present; determinism of two readings; known appearances.
- [ ] `shatterfish/harness/src/test/.../observer/JournalSectionTest.java` (new) -- landmark, key with quantity, custom note; the notes tab's drawing cited; determinism.
- [ ] `shatterfish/harness/src/test/.../observer/LogListenerTest.java` (new) -- first wait's lines; two floor changes the loading scene's way with the scene recreated each time; the "descend" line and a message after it present each time; burst capped at 64; tones; new-line dropped.
- [ ] `shatterfish/harness/src/test/.../observer/PromptGateTest.java` (new) -- no window; chasm prompt through `Chasm.heroJump`; harmful potion through `Potion.execute(hero, AC_DRINK)`; an anonymous `WndOptions` → `OTHER`; a `WndMessage` → every method throws; header kind equals the section's; `Prompts.kind` and `isRecognised` agree for every class in the list.
- [ ] `shatterfish/harness/src/test/.../observer/Skeleton.java` -- `around(header, map, actors, hero, inventory, journal, log, prompt)`.
- [ ] `docs/adr/0006-observer-visibility-rules.md` -- amendment for story 1.10: the item, appearance, journal, log and prompt rules with cites, the kind table, the losses; `docs/rules/identification.md`, `visibility.md`, `ui.md` -- Test columns; `docs/adr/0005-…` -- a note that the prompt's title rule and the log capture point are as decided here.
- [ ] `_bmad-output/implementation-artifacts/sprint-status.yaml` -- `in-progress` now, `review` after the review.

**Acceptance Criteria:**
- Given the rows of ADR-0006 for items, known appearances, the log, the journal and prompts, when the Observer builds those sections, then an unidentified potion appears under its appearance name only and its class is not recoverable, wand charges appear only when known, and a curse enchantment only when the curse is known (`ItemLeakTest`).
- Given a Run whose scene is recreated by a floor change, when messages are emitted after the new scene exists, then the log section carries them, captured from `GLog.update` and never from `GameLog`'s entries (`LogListenerTest`, two floor changes).
- Given a recognised Prompt window in front at an Input wait, when the Observer reads, then the prompt section carries its kind, title, text and option labels, the header carries the kind, and any other window makes every read fail an assertion (`PromptGateTest`).
- Given `ItemLeakTest`, then no true class, level, curse or identification counter appears in the serialized Observation, held by absence and by byte-identical differential pairs.
- Given the whole build, then `./gradlew build` is green, the hooks tests hold rows 2, 3 and 4 with their digests, and the docs build strictly.

## Spec Change Log

## Design Notes

**The window is read through an accessor hook, not reflection.** Alternatives: (a) a declared
reflective read of `Group.members` in a second confined class; (b) a read-only accessor on
`Window` only; (c) one on `Group`; (d) no read, rebuilding the labels from the same message keys
the window used. Chosen (c): (a) grows the reflection allowance the ledger cannot see, which is
what `HarnessReflectionTest` exists to stop, and row 4 is the ledger's own place for "private
state the screen shows"; (b) cannot reach the title, which is a text block inside an `IconTitle`
inside the window; (d) is blind for an anonymous window whose labels are computed. The accessor
returns a copy under the lock every other `Group` method takes and writes nothing. The same
accessor finds the front window in `Game.scene()`, so the Overlay's scene is read the same way.

**The seam is a hook in `GameScene.create()`, not a line in `HeadlessScene`.** The pane replaces
every listener at `:499`, and `create()` emits the "descend" line and the floor's feeling at
`:536-690` before it returns; a listener re-added after `super.create()` would miss what the pane
shows, and the Overlay's scene is not ours to subclass. Row 3 is "the scene seam, including where
the Observer re-registers its log listener" (ADR-0016), spent here for its first site.

**Prompt kinds by class and, for an options window, by the enclosing class of its anonymous
subclass.** Every options window the game opens at the tag is an anonymous subclass declared in the
class that asks the question (Code Map), so the enclosing class names the origin without reading
the message. The harmful drink and the beneficial throw are both `Potion`'s; the title tells them
apart, and only the drink is `HARMFUL_POTION`, the throw an `ITEM` confirmation. `TALENT` is the
pane's random-talent confirmation, the one options window the talents pane opens; `ALCHEMY` is
never produced, since alchemy is a scene. Two members are appended: `ITEM` for any item's
confirmation or choice (the enchantment's three, the seal's transfer, the unequip of a cursed
piece), and `OTHER` for the origins the table does not name (the amulet's ascent prompt, the
examine chooser, the warp beacon), whose labels still carry what the screen shows, so a Run never
fails on a window the driver already accepts.

**Title and text.** The recognised windows draw an `IconTitle` label or a title block first and
one message block after it, then buttons; an untitled options window draws the message alone
(`WndOptions.java:40-66`). So the title is the first text block when there are at least two, the
text is the rest joined by a newline, and the options are the `StyledButton` labels in drawing
order; icon buttons (the info buttons beside a subclass, an option's info) and item slots are not
options. Highlighting marks stay in the text as the block holds them.

**The inventory in the belongings' order** (`Belongings.java:428-429`, `:446-453`): the six slots
then the backpack, a bag before its contents, which `InventorySection` enforces and `ItemRef`
depends on. The name is `item.name()`, what every slot, window and log line prints; the status
`item.status()`; the actions `item.actions(hero)` as identifiers, since the item window's buttons
are exactly those (`WndUseItem.java:54-76`) and `UseItem` names one; the default `defaultAction()`.
`ItemKind` is the item's package, as its Javadoc lays out.

**The journal** is every `Notes` record, since the notes tab draws custom records and then every
floor down from the deepest (`WndJournal.java:497-541`): a landmark's title, a key's title and
count, a custom note's title and body with its depth when it has one. Known appearances are the
three `getKnown()` sets by their true names, `Messages.get(cls, "name")`, which is what a known
item draws (`Item.java:501-503`).

**The log** keeps the newest 64 messages as the signal carries them, tone by prefix, the marker
dropped; a burst beyond 64 in one wait loses its oldest, as ADR-0005 records the pane loses its
own beyond three or five lines.

**Pre-mortem.** The hook in `create()` runs before the Observer exists in the Overlay: the field is
null-checked, so nothing happens. `Class.getEnclosingClass()` is `java.lang`, not
`java.lang.reflect`, so the reflection rule holds; the fairness reviewer will check it reads a
name, never a member. The floor-change test must end the actor thread before `switchTo` or the
headless game refuses; it does what the driver's `close()` does without destroying the game.
`JournalSection` and `ItemView` canonicalise their lists, so a `HashSet` from `getKnown()` is safe
to hand over. The codec writes enums by ordinal, so members appended at the end change no
existing bytes; `PromptKind`'s new members go last.

## Verification

**Commands:**
- `./gradlew build -Pshatterfish.mobile=off` -- expected: green, every module, the four new suites included.
- `./gradlew :harness:test --tests "org.shatterfish.harness.hooks.*"` -- expected: green with the three digests the ledger declares.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` -- expected: clean.
- A mutation battery over `Observer.java`, `GameLogListener.java` and `Prompts.java` against the four new suites -- expected: every mutation caught.
- `fairness-reviewer` on `git diff main...HEAD` before the PR -- expected: findings addressed.
