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

The upgrade to `v4.0.0` failed the draw-parity test on a difference nobody had seen: two Runs of one
tuple in one process logged different numbers of guidebook lines, because `Snake.dodges` is a
private static counter of the dodges the hero has watched and nothing resets it between Runs
(`core/.../actors/mobs/Snake.java:58-70`). The test fixture clears it now, next to the global badges
and the journal.

It is not alone. A sweep of `actors` and `items` at the tag finds eighty-three mutable statics; most
are tunable constants or state the game's own `Actor.clear()` and `Dungeon.init()` reset, but some
are counters of the same shape (`Combo.furyHitsLeft`, `GnollGeomancer.rocksInFlight`,
`Char.hitMissIcon`, `Blacksmith.type`). One process hosting one Run makes the question moot, which
is what the rig does and what ADR-0007 assumes; it stops being moot the moment anything runs two
Runs in a process, which the tests already do. Story 1.16 (#29) owns the answer: either a reset list
the harness applies at every Run start, with a test that walks the statics and fails on a new one,
or a documented rule that a process hosts one Run and the tests that break it carry their own
resets.

Two things sharpen the choice. The driver cannot simply do what the fixture does: `Snake.dodges` is
private, and harness main code may not reflect into upstream, so a reset list either needs a hook
row of its own or has to be limited to the public fields (`Chasm.jumpConfirmed`, which the driver
already clears, and `TippedDart.lostDarts`). And the review of the v4.0.0 upgrade found a worse one
than the snake: `Badges.global` and `Journal.loaded` load once per process
(`core/.../Badges.java`, `core/.../journal/Journal.java`), so a second Run in a process keeps the
first Run's journal, and level generation reads which guide pages are missing when it decides what
the early floors drop (`core/.../levels/RegularLevel.java:561-575`). That changes the floor, not
the log. The test fixture clears both; `HeadlessDriver.newGame` does not.

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
