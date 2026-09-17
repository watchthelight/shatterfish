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
