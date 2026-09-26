# Ideas

Things worth doing that are not being done now. One line each with a rationale. New ideas that
come up mid-story go here instead of expanding the story; they re-enter through BMAD's
correct-course workflow or the next create-story.

| Idea | Why | Raised |
|---|---|---|
| Citation checker for `docs/rules/`: a Gradle task (in `codex`) that resolves every `path:line` against the pinned tag and fails if the cited line changed | Rules silently rot on upgrade otherwise; makes step 9 of the upgrade procedure mechanical | session 3 |
| Generate `docs/adr/index.md` from ADR frontmatter with the MkDocs hook | Hand-maintained lists drift; the hook already scans one tree | session 3 |
| Promote the Windows build from nightly to the PR gate if it ever catches something ubuntu did not | Product owner develops on Windows; see ADR-0002 pre-mortem | session 2 |
| Convention plugin (`buildSrc`) instead of `apply from` once the shared Gradle script grows past a screen | IDE support and type safety; see ADR-0003 option 6 | session 2 |
| Public leaderboard of brain versions on the standard seed set | Community visibility once E3 numbers exist | session 5 brainstorm |
| Bot-vs-seed challenge issues: anyone submits a seed, the rig runs it, result published | Cheap engagement and a stream of hard seeds | session 5 brainstorm |
| Decision narration as a text stream (screen-reader-friendly view of a run) | Accessibility tangent; falls out of the decision log | session 5 brainstorm |
| GitHub Actions matrix as distributed rig workers, Fishtest-style | Free compute for public repos; needs sharded seed sets (ADR-0002 pre-mortem) | session 5 brainstorm |
| Headroom metric: oracle-assisted per-seed upper bound, measurement only | Shows how much is left to gain; must never touch play | session 5 brainstorm |
| Strength per think budget (bullet/blitz/classical) once search exists | Makes speed/strength trade-offs explicit | session 5 brainstorm |
| Post per epic on the docs site telling the numbers as a story | Motivation and transparency | session 5 brainstorm |

## The wall clock reaches the Observation through holiday items (found in story 1.10's review)

`Pasty.name()` and its sprite switch on `Holiday.getCurrentHoliday()`
(`core/.../items/food/Pasty.java:56-90`, `:175-193`), which reads the calendar
(`core/.../utils/Holiday.java:54-59`), so a hero holding a pasty, a common food drop, names it
differently on a holiday and two Runs of one tuple hash differently by date, against non-negotiable
5. Predates the Observer; nothing in the harness pins the holiday. The clean fix is a pinned holiday
in the Profile (story 1.15) or a hook that lets the harness fix the date; `Blandfruit` and the
other holiday-dressed sprites are the same question.

## A blob that draws through the terrain, not through an emitter

Story 1.11's Observer carries a blob only where its own `BlobEmitter` draws particles, which is the
game's own drawing rule for a gas. Two of the Cleric's spells lay blobs that draw nothing that way
and change the floor instead: `WallOfLight.LightWall` sets its cells solid and impassable
(`core/.../actors/hero/spells/WallOfLight.java:244`, `:290-300`) and `HallowedGround.HallowedTerrain`
dresses its cells (`core/.../actors/hero/spells/HallowedGround.java:166`). The player sees both; the
Observation shows neither, and the map's tiles still say floor, so a bot would walk into a wall of
its own light. Nothing leaks, and nothing is wrong before a Cleric plays, but the map wants a row
for what a blob does to a cell's terrain, which is a schema change and a story of its own.

## Upstream statics that outlive a Run

Settled by issue #167: `RunStatics` puts back, at every Run start, the eleven statics a Run can leave
for the next, and `docs/architecture.md` ("Statics that outlive a Run") has the sweep of all 358 and
how each is classified. The history, kept for the reasoning: the upgrade to `v4.0.0` first showed it,
two Runs of one tuple in one process logging different numbers of guidebook lines because
`Snake.dodges` is a private counter nothing resets (`core/.../actors/mobs/Snake.java:58-70`); the test
fixtures cleared it by hand, and story 5.1 found `Bones` the same way. The choice this section once
posed, a reset list the harness applies or a rule that a process hosts one Run, went to the reset
list; its private fields are reached by reflection named in `docs/UPSTREAM.md`, as the stepper's are.

What is still open here: a Run restored from a snapshot in the same process starts from the statics
as the process left them, not as the snapshot had them, since only the saved game is restored; and
the statics that read the wall clock (`Holiday.cached`, `DimensionalSundial`'s night check) are issue
#139's.

## What a remembered cell still shows: the always-visible blobs

`v4.0.0` made the emitter's always-visible gate explicit and widened it: such a blob is drawn on any
cell in view, mapped or visited (`core/.../effects/BlobEmitter.java:59-72`), the cell's description
names it outside the field of view too (`core/.../windows/WndInfoCell.java:175-183`), and six blobs
carry the flag rather than three, the alchemy pot and well water among them
(`core/.../actors/blobs/Alchemy.java:31-33`; `core/.../actors/blobs/WellWater.java:37-39`).

The Observation cannot carry them: `MapSection` takes a blob only on a cell in view, which ADR-0005
decided when the flag belonged to two of Tengu's blobs and one wall. It now costs the bot something
a player plainly has — which well is which, and where the alchemy pot is, on a floor already walked.
The map already carries heaps on remembered cells, so the shape of the answer exists: a blob cell
would carry the fog level it was seen at, or the section would take a remembered-blob list beside
the in-view one. Both are schema changes and a version bump, so this is a story, and the story after
it re-reads ADR-0006's Blobs row.

- **A snapshot cannot carry the journal.** `Journal.loadGlobal` runs once per process behind a private
  flag (`…/journal/Journal.java:34-36`), so a restore (story 1.20) leaves the pages found since the
  snapshot found; the next floor generated reads them. A one-line hook row that resets the flag, or a
  Run-start reset list upstream could be asked for, would make a cross-floor restore exact. The
  same static outlives a Run (story 1.16's sweep).

- **An item's value known and unknown, and its actions by hero.** Story 2.3's items table carries
  one value: a constructed instance's, or -1 with the source text where the value depends on
  being known (`return isKnown() ? 50 * quantity : super.value(); where super.value(): return 30
  * quantity;`). A price table for the Brain (a shop, a sell) wants both numbers parsed, and the
  actions a fresh instance offers depend on the hero (a duelist's ring ability, an equipped
  weapon's abilities); a later table could carry the actions per hero class and per equipped
  state, read the same way. Also: the four excluded for lacking a bare constructor (a spirit
  arrow, a scorpio's shot, a bag's placeholder, the armor base) and the wand's two seeds could
  be carried as facts about their owners rather than dropped.

- **A room's prize odds.** Story 2.4's rooms table cites every `Generator.random*` line a room
  makes as text; a table of the prize a room gives (a ring or an artifact from the pit, an
  armor one set deeper from the crypt, a scroll from the library) with its odds, read from those
  lines and the decks, would let the Brain price a key. The shop's stock (`ShopRoom`, placed by
  `Dungeon.shopOnLevel`) is a table of its own.

- **The hit table, measured where a process has booted.** Story 2.5 wrote the rig for accuracy
  against evasion and could not run it: `Char.hit` initialises `FloatingText`, which builds a
  texture film. The harness boots headlessly and already runs measurements (story 1.21's
  benchmark), so a task there could sweep the grid and write `codex/<tag>/combat-hit.json` beside
  the generated tables, with the manifest listing it and CI regenerating both; the alternative is
  a hook that lets `Char.hit` skip the icon when no scene exists, which is an upstream edit and a
  row in the ledger. The grid, the sampling and the rig are written already in `Combat` and
  `Sparring`, and `CombatTableStabilityTest` exercises them.
- **The modified combat paths.** The measured tables are the bare ones: no ring of accuracy or
  evasion, no enchantment or glyph, no champion buff, no hero talent, no encumbrance penalty
  below an armour's strength. Each is a multiplier or a branch the engine applies in the same
  methods, so the same rig measures them with the modifier in place.

- **The composition the Codex cannot carry.** An expected damage is a damage roll less a reduction
  roll, and the engine does that subtraction in `Char.attack`, behind the same toolkit wall as
  `Char.hit` (it plays a sound and writes the log). So story 2.5's three measured tables are the
  parts, not the product. The story that measures behind the wall should measure the composition
  too, and until it does, a consumer doing the subtraction itself should know that the engine
  applies its multipliers between the two rolls.
- Early stopping in the Rig: once the sequential test decides, cancel the pairs not yet dispatched (story 3.6 plays every pair and evaluates in Seed-set order; stopping dispatch must still respect that order, so pairs are dispatched in order and the test consumes only a completed prefix).
- The exact GSPRT in the Rig (found in story 3.6's review). `Gsprt` implements Van den Bergh's eq. 2.1, which is what Fishtest's `sprt.set_state` reports, but Fishtest's server stops on the exact generalized LLR (`LLR_logistic`) over the same counts. The two agree near the bounds in ordinary tests and part in the tails. Adopting the exact form is an ADR-0012 amendment and a new reference fixture from the same pinned Fishtest checkout.
- A lower missing cap (found in story 3.7's calibration). The random Brain ends 8.4% of its Runs at a window the Harness does not know (`UNKNOWN_WINDOW`), so 16% of pairs are missing and the calibrated missing cap is 0.25 -- room for a Brain to crash on a quarter of the seeds it would lose. Teaching the Harness those windows, re-extracting the table and re-running `./gradlew :rig:calibrate` brings the cap down.
- Publishing the Run logs (found in story 3.10). FR-25 asks each Results page to link its Run logs; a side of `standard` is about 37 MB, too large for the repository, so every page says "not published" and carries each Run's chain instead. Candidates: a GitHub Release asset per page, Actions artifacts (which expire), or an orphan branch. It is a storage decision for the product owner; `results.json` already has a `logs` field for the answer.

- The killer in the Run log (found in story 3.12). A log's ending records the cause (`DEATH`, `WIN`,
  a stop the game did not decide) and the depth, but not what killed the hero, so the death gallery
  groups by ending and depth and cannot say "a rat on depth 1" or "hunger". Recording the source of
  the fatal damage in `RunLog.Outcome` -- from what the game shows the player on the death screen, so
  it stays inside information parity -- is a Run-log schema change, and would let the gallery group
  by killer.

- Comparing two weight sets in the Rig (found in story 4.5's review). A comparison's two sides each
  read `weights/<brain>.json` for their Brain, so the Rig cannot run an SPRT of the same Brain under
  two weight sets: the file names one set per Brain. Tuning needs a way to name the second set --
  a flag naming the other file, or a named variant of the Brain whose weights live beside it -- with
  the second set's canonical text in its side's configuration hash and in the Registration. Deferred
  to the story that first tunes a weight.

## From story 4.2 (Beliefs)

- **Kills from the log.** A remembered enemy stays remembered until an enemy of its name is seen again or
  the oldest sightings are forgotten, so an enemy killed in view lingers as a stale sighting. The game log
  reports a kill; reading it (with the message cited from the pinned properties) belongs with the
  fight-in-corridors Policy (story 4.7), which is the first reader of sightings.
- **Exotic appearances.** "Exotic crimson potion" and its kin get no guess: the Codex's exotic swap chance
  is zero without the Exotic Crystals trinket. A Brain that carries the trinket should weigh them.
- **The guaranteed identities' prior.** Strength and upgrade are weighted as the heaviest identity in their
  family's deck, an assumption. The rig can measure the real share of unidentified finds per identity from
  oracle-mode logs, which carry the true identity as a training label.

## From story 4.9 (eat and heal)

- **A Codex food table.** The eat Policy's food energies are a hand-written table in `Eat`, cited line
  by line and held to the Codex's item names by `FoodCodexTest`. The Codex generator could extract
  each food class's `energy` initialiser the way it extracts combat figures, and hand it to the Brain
  through `Codex.Knowledge`, so an upgrade that changes an energy fails the Codex drift check rather
  than a re-read.
- **Healing by deduction.** A potion counts as a potion of healing only when the screen shows it
  identified. When `Beliefs` puts an appearance's odds of healing at one (every other identity
  accounted for), a player would drink it with the same confidence; story 4.10, which tests unknown
  items, is where that belongs.
- **Healing out of combat.** The heal Policy drinks only against enemies in view. A hero poisoned or
  bleeding for more than its hit points, with nothing in view, dies with the potion in the pack; the
  potion cures both (PotionOfHealing.java:76-86). Measuring the poison left from the buff's shown turns
  would give a threshold for that case.
- **The heal in progress.** The game shows a floating heal number over the hero each turn a heal lands,
  and the sprite's healing state (Healing.java:61, :107-111), neither of which is in the Observation.
  The heal Policy instead counts waits since the drink it handed over. Carrying the sprite's healing
  state in the hero section (an Observer change, with its leak tests) would let the Brain see a heal
  running, whoever started it.
- **The Vial of Blood.** The trinket spreads a potion's heal over more turns and caps each turn's heal
  (Healing.java:80-82, :91-93). The heal Policy's first-turn and remaining-heal figures assume it is
  absent; the trinket shows in the inventory, so its level could be read and the figures scaled.
- **Shooters by line of fire.** The heal and eat Policies treat a shooting enemy in view as able to hit
  the hero wherever it stands. The game fires only along a clear bolt or projectile line (Ballistica);
  the Brain could trace the line over the tiles the map draws.
- **Cooking.** Mystery meat is eaten last because of its side effects; frozen carpaccio and chargrilled
  meat have none. Cooking it (fire, frost, the alchemy pot) and brewing blandfruit are left to a later
  story, and the table lists blandfruit as never eaten until then.

## From story 4.10 (the test-item Policy)

- **Read onto a target once the upgrade window is answerable.** An unknown scroll is read plainly, so the
  first scroll of upgrade, remove curse or transmutation read unknown is spent on identifying itself. Once
  the harness answers `WndUpgrade` (story 4.11, or a harness story), reading onto the worn armour or weapon
  would make that first read count.
- **A test's expected payoff.** Potions are drunk by thresholds (half health, healing at least a fifth of
  the odds, a quarter of the hit points kept after the worst case) and scrolls by the item-picker share.
  A model that values a test as the effect's worth now, plus what knowing buys later (a known healing
  potion is one the heal Policy can drink in a fight), less the 30 score an unknown item holds and the
  expected hit points spent, would replace them once the rig can measure what knowing healing is worth.
- **What knowing is worth.** The Policy tests at the first calm moment and ranks appearances by copies held
  times candidates left. An Evaluation of what an identity buys (a known healing potion in a fight, a
  known upgrade on the right item) would let it wait, or test a likely-healing potion only when hurt.
- **Throwing to test.** A thrown potion shatters where it lands (`Potion.java:309-330`, `:336-342`): a harmful one
  identifies itself, a beneficial one splashes harmlessly and is wasted, and that line then rules the harmful
  ones out. Worth it only when the harmful share is high and the hero is too weak to drink.
- **The windows the fallback used to open.** The holy tome's spell window, the upgrade window and the stone
  of intuition's guess window are not Prompts the harness recognises. The fallback now leaves items alone,
  and a Policy that means to use those items needs the windows answerable first.

## From story 4.12 (the descend Policy)

- **Which way to flee.** The fight Policy's retreat takes the nearer regular stairs, up or down, except
  down onto a boss floor or down hurt while the descend Policy is leaving (story 4.12's review). Fleeing
  only up measured worse on story 4.9's Brain (mean deepest floor 2.52 to 2.20). Worth a proper SPRT once
  the Goo gate measures depth and survival together.
- **Traps on the only way to the exit.** The descend Policy never paths through an armed trap. A floor
  whose only way to the exit crosses a known trap in a one-wide corridor leaves the hero searching for
  another; walking the trap as a last resort would need its worst case scored.
- **What an allowance is worth.** The descend Policy's allowance (500 waits, 250 more per guaranteed drop
  expected) is an assumption, not a measurement. Tuning it needs the rig's SPRT, not `smoke`.

## From story 4.11 (answer prompts)

- **Read a known scroll of upgrade onto the worn gear.** The upgrade window's rule only confirms a target
  another Action chose; no Policy yet reads a known scroll of upgrade (or magical infusion) onto the worn
  weapon or armour, which is the obvious target for a Brain that fights in melee.
- **The item selector as a Prompt.** An unknown scroll that identifies itself on reading (upgrade, remove
  curse, identify, transmutation, enchantment) opens the bag, which the executor sends away because no Action
  answers it, and the scroll is lost. Surfacing a selector the game opened after a plain `UseItem` as a Prompt
  whose options are the selectable items would let the Brain choose the target instead.
- **Cast the Cleric's spells.** The spell list is left unanswered; its spells are icons with hover text, which
  the section could carry by name the way the guess window's icons are, once a Policy wants to cast.
- **Buy in shops.** A shop is always left. A Brain with a model of prices and needs (food, a healing potion)
  could buy; the sell flow opens the bag and needs the selector above first.
- **Learn from a wrong guess.** A stone of intuition's wrong guess is logged ("Your guess was incorrect.",
  `items.properties:1442`) and rules one identity out for that appearance. The Beliefs do not read it yet, so
  a second stone could guess the same identity again; a fact "not X" per appearance would narrow the odds.
- **The item selector as a Prompt, again.** The upgrade window the game chains after an upgrade, while more
  upgrade items are held, is answered by a Brain error, because its "Back" reopens the selector no Action
  answers. With the selector as a Prompt, the Brain could back out and keep the scroll.
- **A use for a known scroll of identify (story 4.13).** Unknown scrolls are read onto the worn armour, so a
  scroll of identify among them is spent identifying itself. No Policy reads a known identify today, so
  nothing is lost yet; once one does (an unknown ring or wand worth knowing), the read onto the armour should
  hold back while identify is likely.
- **Unknown scrolls and the Mage's staff (story 4.13).** Unknown scrolls follow the upgrade's target, the weapon
  or the armour, except the Mage's staff, whose wand an unknown transmutation would take
  (ScrollOfTransmutation.java:158-159). For the Mage every unknown upgrade therefore goes onto the armour; once
  the Beliefs can rule transmutation out for an appearance, the staff could take its share.
- **The chained upgrade window shares the stack (story 4.13).** It spends every scroll held on the item the
  first went onto. With the item selector as a Prompt, the Brain could back out after one and share the rest.

## From story 5.1 (the launcher and the embedded driver)

- **The Overlay's Brain knows no Codex.** `--agent brain` attaches `shatterfish` with the committed
  weights but an empty Codex: the Rig's Brain is built on the Codex, which only the rig reads
  (`rig/CodexKnowledge`), and the Overlay may not depend on the rig. Moving the reader to a module
  both can reach (the harness, or a small `codex-read` module on `api`) gives the Overlay the Rig's
  Brain; at the latest story 5.16, whose sewers Run needs it, has to do it.
- **The descend and explore Policies can hold the hero between two cells.** In the desktop launch of
  story 5.1 (seed 12345, the Warrior, the Overlay's Brain), from wait 501 the hero stepped between
  two cells for about 1,100 waits until it starved: the descend Policy chose the exit (`overstayed`),
  and on the next wait the explore Policy's `away` step (a region the fight Policy retreated from
  covers the path) took it back. A test that plays the two against one fled region would show it;
  the fix belongs to the Brain, not the Overlay.
- **Bones is one more upstream static that outlives a Run** (see "Upstream statics that outlive a
  Run" above). The determinism test of story 5.1 found it: a hero who dies in one Run leaves remains
  on the next Run's floor in the same process (`core/.../Bones.java:50-54`, `:154-160`). The Profile
  now clears it through `Bones.leave()`'s daily branch, a public door. The sweep was done under issue
  #167 (`docs/architecture.md`, "Statics that outlive a Run").
- **The frames the desktop adds.** An Overlay Run equals the Rig's Run of its tuple only where the
  frames between two waits are the same; the render thread's draws in the frames the desktop adds
  come from the Run's generator. Story 5.13 routes them away. Until then an Overlay Run is not
  reproducible from its tuple or its Action list, its log says `driver: embedded`, and the Rig refuses
  it; a replay of an Overlay log would part from it at the first moved roll.
- **A gamepad reaches the game while a Run plays.** The Overlay closes the game's input multiplexer
  (`InputLock`), but the controller handler writes the key queue directly
  (`SPD-classes/…/input/ControllerHandler.java:122-134`). Story 5.5's input-gate hook closes it.
- **The region intro.** The Overlay does not click through the story page the loading scene shows on a
  first descent to depths 6, 11, 16 and 21, so a Run reaching depth 6 ends as an unknown window after
  the budget. Clicking it reads a journal page the headless Run does not; if the Overlay should
  play past the sewers, clicking it and reading that page in both drivers is the choice to make.
- **Reading subprocess output.** `Brains`, `Registrations`, `Results` (rig) and `DocsCitations` (codex)
  read a subprocess's output to its end before its errors, which deadlocks once the errors fill a pipe;
  the hook-ledger tests' `Ledger.git` did (story 5.1). Their outputs are small today.

## From story 5.2 (the Panel frame)

- **The inventory pane in the executor.** With the full interface (2) the game hands an item selector
  to the inventory pane (`GameScene.java:1673-1674`), which no Action names, so the Overlay plays on
  the mixed interface (1). Teaching the executor and the Observer the pane's selector would let the
  Overlay play on the layout `DESIGN.md` draws, with the Panel left of the pane.
- **`DESIGN.md` and the full layout.** Its Layout & Spacing section places the Panel "below the status
  pane's row"; with interface size 1 or 2 the status pane is at the bottom (`GameScene.java:487`), and
  the Panel's top is below the menu pane and the boss bar instead. The design text could say so.
- **Screenshots for every story.** `--screenshot` writes one frame; a flag for a frame at a given wait,
  or one per floor, would let a later story's review see the Panel's content without a person at the
  window.
- **A Panel narrower than the strip's minimum.** In the mobile layout on a very narrow window the Mode
  strip is squeezed below 160; it keeps its place but its content (story 5.3) will need to elide.

## From story 5.3 (the Mode strip, Goal line and Decision card)

- **Per-column text for story 5.4's rows too.** The review found that a single padded
  `RenderedTextBlock` per row does not actually align in the pixel font (it is proportional, not
  monospace), and fixed the Decision card's rows with a `RenderedTextBlock` triad per row (action,
  score, reason) whose column edges come from each block's own measured width
  (`DecisionCard.refresh`/`.layout`). Story 5.4's Belief rows and Decision log will want the same
  treatment; nothing here shares the column-measuring code between row kinds yet, so each is its own
  small implementation rather than one shared row component.
- **The Mode strip's turn and floor stay a padded string on purpose.** `Columns.number`, unchanged:
  the strip is one line, so there is no second row to misalign against, which is the property UX-DR5
  is protecting; a future story that puts a second line of numbers beside it (unlikely, since the
  strip is meant to stay one line) would need to revisit this.
- **Plain fallback labels for some Action kinds.** `ActionText` gives `Interact`, `PickUp`,
  `OpenChest`, `Buy`, `Unlock` and `DismissPrompt` short, generic words (`"interact"`, `"open"`, ...)
  since the epic and its review named only Step, Attack, the item-use kinds, Descend/Ascend, Rest,
  Search, Wait and AnswerPrompt explicitly. A later story wanting a chest's or a shop's own detail
  (a cell, a stock item) extends the one exhaustive switch rather than searching for where labels live.
- **Real Mode, speed mode and THINKING.** `ModeState.of` reads a placeholder Mode (always RUNNING) and
  speed mode (`normal`, with a placeholder interval); only the turn, the floor and the live THINKING
  flag are real, from `EmbeddedRun.snapshot()`. Stories 5.5 to 5.7 give PAUSED, HUMAN, Next Step, Run N,
  Human play speed and Fast their own controls; `ModeState` and `ModeStripContent` already handle every
  value, so those stories add a caller rather than a format.
- **Explain while a Run plays.** The Decision card's Explain `RedButton` is inactive
  (`explainButton.active`, not only `.visible`) while `InputLock` is holding (every Run but one that
  has ended), so neither a human's click nor a stray tap can reach it. It works once the Run is over,
  to read the last Decision. Story 5.5's input-gate hook is for hero-directed input, not Panel
  buttons; whichever story gives PAUSED a real click (5.6's controls row) should route one to the
  Panel too, or give the Panel its own listener that the gate does not close.
- **A second, general instance of the bug the fairness review found in Explain.** Any Panel control
  with a `PointerArea` (every future button in 5.6's controls row, the speed selector, the steppers)
  needs the same `active`-while-locked discipline Explain now has, since `ActionExecutor.press`
  bypasses `InputLock` for every window button, not only the ones that happen to overlap the Panel
  today. A shared base (a "Panel button" that gates its own `active` from one flag `PanelDock` sets
  once per frame) would make this a property of the class rather than something each new control has
  to remember.
- **The Decision card's own height can overflow the Panel's.** Nothing below it (Safety flags, the
  Belief summary, the Decision log) exists yet, so there is blank room today; story 5.4 will need the
  Panel to give the sections above it only what they ask for and the Decision log the rest, per
  UX-DR2's "never fewer than three lines," which today's fixed layout does not yet arbitrate.
- **The Goal line's two-line wrap.** `DESIGN.md` says "wrapping to two lines at most"; `GoalLine` wraps
  through the game's own `RenderedTextBlock` but does not cap it at two lines or ellipsize a third, since
  a Brain's goal today is always the short label `DecisionShapeTest` holds it to (at most 40 characters).
  A longer goal from a future Policy would need the cap this story left unenforced.

## From story 5.4 (Safety flags, Belief summary and Decision log)

- **A goal-then-no-decision-then-goal sequence is not pinned by a test.** `DecisionLogContent.of`
  leaves the remembered goal alone on a `Wait` with no `Decision` (a plain, non-`Deliberator` agent),
  rather than clearing it to null and re-emitting a spurious "goal changed" line once a real Decision
  returns. The reasoning is in the story file's design note 4 pre-mortem; no test drives a history with
  exactly that shape (Decision, no-Decision, Decision-with-the-same-goal) to hold it directly.
- **`PanelLayout.MIN_PANEL_HEIGHT`'s worst case is still not every section's worst case.** The revised
  constant (design note 7) adds only the Decision card's own unavoidable single line to story 5.2's
  floor; the Goal line, the Safety flags row and the Belief summary can each still collapse toward
  zero, and the constant does not promise a Panel that clears it looks uncrowded once a Goal line and
  several Safety-flag chips are genuinely showing. A story that wants a tighter guarantee has one
  number to change, with a comment already saying what it does and does not promise.
- **The Belief summary's rows are not real pixel columns.** Unlike the Decision card's action/score/
  reason triads (UX-DR5), each Belief summary line is one `RenderedTextBlock`; this story's own
  acceptance criteria did not ask for a column a human is meant to compare row to row, and inventing
  one seemed the wrong place to spend the story's budget. A future story that wants the probabilities
  to line up visually has `DecisionCard`'s own column-measuring technique to copy.
- **`SafetyFlagVerdict.OK` has no real case yet.** `Safety.java`'s four flags today (`hp-low`,
  `enemy-in-view`, `hungry`, `starving`) are all conditions to watch, never a flag saying a situation
  is fine (`EXPERIENCE.md`'s own aspirational example, `"ok: fighting in corridor"`, is not something
  any Policy raises today). `SafetyFlagVerdict.OK` and its green chip exist and are tested
  (`SafetyFlagsContentTest.verdict_colours_are_designmd`) but nothing in a real Run shows one yet.
- **A second, general instance of the bug the fairness review found in Explain, now doubly confirmed.**
  Story 5.3's `docs/ideas.md` entry asked for a shared base that gates a Panel control's own `active`
  from `InputLock` once per frame, rather than each control re-deriving `pane.active = !inputLocked` or
  `button.active = ... && !inputLocked` for itself. `DecisionLog`'s `ScrollPane` is the second real
  instance of exactly the pattern that entry predicted (the first being Explain); the controls row
  (5.5 to 5.7) will be the third, fourth and fifth, and is a good place to finally build the shared
  base rather than writing the same one-line gate a third time.
- **`EmbeddedRun.history()` never carries a `RunLog.Prompt`.** The `fairness-reviewer` subagent's
  second should-fix: `RunLoop.record` also writes a `Prompt` record (when there is a log and the wait
  answers one), but only the `Wait` reaches `BoundedLog`, so the in-memory Decision log and the
  on-disk Run log can diverge in content once logging is on and a Prompt is answered. Not a fairness
  leak -- both carry the same information, just differently scoped -- and `DecisionLogContent`'s own
  Javadoc already documents the choice ("a Prompt rides beside the wait that answered it"). Widening
  `history()` to a small ordered structure carrying both kinds (or teaching `BoundedLog` to accept a
  `Prompt` beside its `Wait`) is a small, separable follow-up, not folded into story 5.4.

## From story 5.4's review round

- **The word "turn" repeats on every Decision log row.** Considered (the coordinator's own review
  offered a header row or dropping the word, "only if it's cheap"): dropping it is a one-line change
  to `DecisionLogContent.waitLine`, but every already-passing `DecisionLogContentTest`/`DecisionLogTest`
  assertion and the story file's own worked examples spell out the current format, so the real cost is
  in what it touches, not the line itself. Deferred rather than folded into the review round.
- **`PanelLayout.MIN_PANEL_HEIGHT` still assumes the Decision log's own line is `SIZE` (6) UI pixels,
  not the ~6.5 the small font actually measures at.** The review round's third pass found (by
  running the real game, not by reading the constant) that a `RenderedTextBlock`'s real height at
  the small size is not exactly `SIZE`; `DecisionLog` itself no longer assumes otherwise anywhere
  (`rebuild`, `minHeight` and `viewportHeightFor` all work from the real, measured, already-`PixelScene.align`-ed
  row positions, never a nominal pitch), but `PanelLayout.MIN_PANEL_HEIGHT` (story 5.2, extended by
  this story's own first pass) still budgets the log's three-line floor as `3 * (6 + 2)`, which
  slightly under-states the real room three lines need. A Panel that just clears the constant's own
  threshold could still be a few pixels short in practice; a story that wants the constant itself to
  stop guessing has one number to change, once a real measurement is available without booting the
  game to get it (the constant is evaluated at class-load time, before any font exists to measure).

## From story 5.9 (the human half: recording, the shadow, notes)

- **A tap that interrupts a walk or a rest.** The unmodified game stops a long walk or a rest when the
  player taps (`CellSelector.select`'s `GameScene.cancel()` branch). A HUMAN Run's input lock holds
  presses between waits, because that tap is not an input any Action records, so a person cannot
  interrupt. Recording it needs a notification in `GameScene.cancel` (row 11) and an Action kind the
  executor can issue at the next wait; takeover (story 5.8) is the natural home.
- **`MoveTo` in the executor.** A click on a distant cell is the commonest human input and is marked
  unsupported, since the executor offers only single Steps and the walk spans turns with no waits in
  them. An executor that clicks the far cell as the person did would make most human Runs replayable;
  it needs a rule for what a Brain may be offered (a valid set of every known cell is too large).
- **Targets the valid set leaves out.** A throw at an empty cell, an armour or weapon ability, a
  quickslot assignment and resuming an interrupted walk are all marked unsupported. Each is a small
  extension of `ValidActions` and the executor.
- **A window's button the Observer does not list.** The first real HUMAN launch met, after the
  tutorial's journal hint, a window whose button a tap pressed and no Prompt option named; the rule
  that only the wait's own Prompt window can be answered now keeps a window the person opened out, but
  a Prompt whose buttons the Observer reads short would still be marked unsupported rather than read.
- **The notes key is fixed at N.** Story 5.11's hotkeys would make it bindable, with the game's own
  key-binding window.
- **A human's log replays only where the frames are the headless driver's.** On the desktop the
  exceptions of stories 5.1 and 5.2 stand; story 5.13 and issue #169 close them for a human's Run as
  for the Brain's.
