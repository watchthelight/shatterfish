# Shatterfish

**An open-source engine for Shattered Pixel Dungeon, in the spirit of Stockfish.**

Shatterfish drives Shattered Pixel Dungeon's own game code headlessly, plays it
with a hand-built symbolic bot, measures every change with Fishtest-style
statistical testing, and runs the bot inside the real desktop game where you can
watch it think, pause it, step it, and take over.

> **Unofficial and unaffiliated.** Shatterfish is a permanent downstream fork of
> [Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon)
> by Evan Debenham (00-Evan), itself based on
> [Pixel Dungeon](https://github.com/00-Evan/pixel-dungeon-gradle) by Watabou.
> It is not endorsed by either author. Never report Shatterfish problems to the
> upstream project. See [`NOTICE.md`](NOTICE.md).

Documentation: **<https://watchthelight.github.io/shatterfish/>**. The
[roadmap](docs/roadmap.md) has every epic, and the
[published results](docs/results/index.md) have every number.

## Where it stands

| Epic | What it delivers | Status |
|---|---|---|
| E1 Harness | The game runs headlessly and reproducibly behind a fair Observation; the same (tag, seed, actions) replays identically across JVMs | done |
| E2 Codex | Every game fact the bot uses, generated from the pinned code with `path:line` citations; CI fails on drift | done |
| E3 Rig | Parallel seeded Runs, hash-chained Run logs, replay, SPRT with calibrated bounds, results pages, a nightly job, a death gallery | done |
| E4 Baseline brain | A hand-built bot that plays the sewers competently; done when the Warrior kills Goo on at least 75% of the `goo` Seed set | **in progress** |
| E5 Overlay v1 | The bot inside the desktop game: watch it think, step it, take the controls mid-fight | **in progress** |
| E6–E9 | Tactical search, strategy and lore, overlay v2, learned evaluation | planned |

E4 so far, story by story (each merged with a fairness review, a code review, a
mutation battery and a direction check on the `smoke` Seed set):

- **4.1** The Brain skeleton: Policies arbitrated in priority order and
  re-planned from each Observation, so a human can take any turn.
- **4.2** Beliefs: odds over what each unidentified potion, scroll and ring can
  be, what a floor is known to hold, the guaranteed drops a set of floors still
  owes, and enemies remembered out of sight.
- **4.3** `SafeTest`: the worst case of trying an unidentified item where the
  hero stands, refused when it could kill.
- **4.4** The Decision output: goal, choice, alternatives, safety flags and map
  highlights on every wait, a plain-text strategy log, and an index of every
  game mechanic the Brain relies on.
- **4.5** The Evaluation, with its weights in a committed data file
  (`weights/shatterfish.json`) that changes play without a recompile.
- **4.6** Exploring: one step at a time toward the nearest unexplored part of
  the floor, then searching walls for secret doors, then taking the stairs down.
- **4.7** Fighting: estimates each fight from the Codex's enemy figures and the
  hero's visible gear and health; fights when it wins with a margin, otherwise
  backs into a corridor or retreats by the stairs.
- **4.8** Picking up and equipping: walks to items worth carrying (valuing
  unidentified ones by their odds, never their true identity) and wears a better
  weapon or armour when the chance of a hidden curse is worth it.
- **4.9** Eating and healing: eats by the hunger icon without wasting food, and
  drinks a known healing potion when the enemies in view could kill the hero and
  there is no retreat.
- **4.10** Testing unknown items: drinks or reads an unidentified item only
  when the worst case is survivable and the knowledge is worth the item, walks
  out of its own gas, and rests after; the random fallback no longer uses items.
- **4.12** Going down: leaves a floor once it is spent, overstayed, or out of
  food, resting to full beside the stairs first, never from a sealed floor and
  never fleeing hurt onto a boss floor.
- **4.11** Answering the game's windows: a rule for every kind of prompt
  (subclass choice, shops, upgrade targets, the stone of intuition's guess),
  and a Run that meets one it can't answer, or stops passing time, ends with
  a logged reason that replays.
- **4.13** Tuning toward the Goo gate, published as a direction check
  ([results](docs/results/2026-09-26-4-13-direction-check.md)) on 40 Warriors
  from the `standard` set, never the registered `goo` set: Goo kills went from
  0 to 7 of 40 and median survival from 967 to 1,614 turns, mostly by reading
  scrolls onto the worn gear. Goo itself is no longer the limit (7 of the 10
  who reach it win); reaching depth 5 is. The death gallery gained a
  side-by-side view of two Brains.
- Fixes since, each measured on the same 40 Warriors: the two-cell shimmy
  (#174) fell from 27% of waits to about 1%; after killing Goo the Brain now
  unlocks the boss floor's exit with the key Goo drops (#163), taking 7 of 40
  heroes to depth 6 where none went before; and it no longer steps onto a
  chasm or says yes to the jump it did not mean (#170). The places it retreated
  from no longer wall off doors and items for 100 turns: it walks round them,
  then through them, before it searches (#183), taking 14 of 40 to depth 5 where
  11 went before. The Brain now reads its enemy tags (passive, ranged, immobile)
  from the bestiary, and the Overlay's Brain plays with the same game knowledge
  as the Rig instead of none.
- Waiting on an owner decision: **4.14**, the registered 400-Warrior `goo` set
  and its 75% gate, which is several stories away at the current rate.

Knowledge the Brain will fight with: the [bestiary](docs/bestiary/index.md), a
cited card for every one of the game's 103 enemies and bosses (speed, attack and
reach, abilities, immunities, surprise and evasion) with derived tactics for each
hero class, and community tips tiered against the code
([`lore/bestiary/`](lore/bestiary/), [`tactics/bestiary.json`](tactics/bestiary.json)).

E5 (the Overlay) so far:

- **5.1** The launcher and the embedded driver: `./gradlew :overlay:launch`
  starts the real desktop game with the Brain (or the random agent) playing in
  a Profile of its own, re-attaching across every floor, with the Brain on its
  own thread so the game never waits on it. Overlay Runs are labelled and kept
  out of every published number until story 5.13 makes them reproducible.
- **5.2** The Panel: an instrument docked beside the dungeon, built from the
  game's own nine-patches, that never covers the HUD and collapses to a strip on
  small windows or the phone layout. The Overlay plays at the mixed interface
  size so the Panel can show; that changes a few hint lines in the game log, a
  second named exception to reproducibility until #169.
- **5.3** The Panel's content: the Mode strip (mode, speed, turn, floor), the
  Brain's goal, and the Decision card with the chosen Action and up to three
  alternatives, their scores in aligned columns and their reasons, plus an
  Explain control that expands the card in place.
- **5.4** The rest of the Panel: Safety flags as chips, a Belief summary of the
  least-certain unknown items with their likeliest identities, and a scrolling
  Decision log built from the Run log itself.
- **5.9 (first slice)** HUMAN mode: you play the real game and every action
  is recorded in the chained Run log, with the Brain's own choice logged beside
  it as a shadow that never runs, and N writes a note at that turn.
  `./gradlew :rig:strategy --args="overlay-runs"` prints your action against the
  Brain's, turn by turn. Taking over mid-Run is story 5.8.
- Fix since: closing the game mid-Run now ends the log with a `CLOSED` record,
  and a watchdog writes every thread's state beside the log when the Overlay goes
  30 seconds without a turn, so a freeze leaves evidence (#175).
- Housekeeping: Brain rules are keyed by name rather than number, and each
  idea is its own file under `docs/ideas/`, so parallel branches stop
  conflicting (#192). CI runs the modules side by side (#188).
- Tooling: `./gradlew :rig:direction` prints the table every Brain PR carries
  (the tuning set is 40 Warriors from `standard`; see CLAUDE.md), and a Run no
  longer leaves its profile folder in the temp directory (#191).
- Fix since: the chasm test no longer fails on a slow CI machine; it waits for
  the Brain's worker instead of racing it (#193).
- Tooling: two opt-in tools replay a Run log wait by wait and dump what the
  Brain saw beside what the game really held, for diagnosis only; an ArchUnit
  test keeps the true game state away from the Brain (#195). See
  `docs/methodology.md`.
- Fixes since: a Codex test that needed a hero in place since v4.0.2 no longer
  fails on PRs that leave the Codex alone (#200), and the chasm test's host now
  waits for the Brain's worker once per frame, so it cannot see the worker
  differently from the Run (#201).
- Fix since: the Brain no longer takes a Step out of an avoided region that
  makes the walk out longer, which it used to undo the next turn; oscillating
  Steps on the tuning set fell from 312 to 289 (#199, for #165).
- Fix since: the Brain opens chests and remains when it can afford a mimic
  or a wraith, and opens a tomb only from a corridor; on the tuning set
  15 of 40 Warriors now reach depth 5, against 10 before, and 9 kill Goo
  (#204, for #181).
- Fix since: near a trap the screen shows, the Brain searches before it steps
  on ground it has not searched, since a burned room's other traps may be
  hidden; on top of #204, 39 of 120 Warriors reach depth 5 against 34
  (#203, for #184).
- Fix since: on a sealed boss floor, where walking away cannot break contact,
  the Brain retreats at most 30 times and then fights, so it can no longer
  walk away from Goo until the turn cap; Goo kills held (#209, for #180).
- The seen-heap change of #207 was reverted after 80 fresh seeds showed depth-5 reach 22 to 18 and
  Goo kills 13 to 11 (#211); #182 stays open.
- The Matt Pocock skills were tested against the current method on four Brain issues and not
  adopted; one habit is kept, a fix's first test fails on main (ADR-0019, #210).
- While the bot is paused, clicks and direction keys do nothing to the hero: an input gate in the
  game's cell selector, open only to the bot's own Actions and to a HUMAN Run (E5.5, #212).
- Tests run in forked JVMs side by side and `:rig:direction` plays a quarter of the cores at
  once; the full local test suite is about four times faster (#213).
- E5.6: the Panel has its controls row (Pause, Next Step, Run N, speed, Explain), and a Brain Run
  opens paused; which control is live in which state is one tested matrix (#214).
- The Brain's two notions of calm (quiet, unengaged) each have one named home; play is
  byte-identical (#216).
- E5.7: Next Step, Run N, Human play speed and Fast work; a paused Run shows the Decision the
  next press executes (#218).
- A Run in a process that already played Runs now starts from a fresh process's history:
  global badges, journal tallies and a first-Run random draw no longer leak (#217). The
  tuning-set baseline moved with it (depth 5 on 18/40, Goo 8/40).
- `:rig:direction` caches the baseline's Runs by commit and host code, so a second run against
  the same baseline takes seconds (#215).
- E5.8: take over and hand back. A person can take the Run at any Input wait and hand it back;
  the Brain resumes paused, with a fresh Decision for the screen in front of it (#221).
- CI can run on a self-hosted runner behind a repository variable; it measured slower than
  GitHub's runners while the machine was busy, so it is off (#222, docs/tooling.md).
- E5.9: a person's turns replay; the "replay unverifiable" notice outlives a hand back and every
  Run, a person's too, has a watchdog (#224).
- Rig Runs can write an opt-in debug record beside each Run log (`--debug-record`), for the Goo
  investigation (#223, #219). The rules index counts are now checked by a test (#225). ADR-0020
  proposes splitting the Brain's Memory into versioned sections (#226).
- Next: map highlights (5.10) and hotkeys (5.11), with Brain changes in parallel.

The Brain is still weak, but it now plays rather than wanders. On the `smoke`
Seed set:

| After | Mean deepest floor (max) | Median turns survived | Mean score |
|---|---|---|---|
| 4.5, the random Brain | 1.04 (2) | 1,374 | 78 |
| 4.7, exploring and fighting | 2.16 (3) | 422 | 405 |
| 4.8, picking up and equipping | 2.12 (3) | 557 | 637 |
| 4.9, eating and healing | 2.44 (4) | 752 | 848 |
| 4.10, testing unknown items | 2.32 (4) | 863 | 918 |
| 4.12, going down | 2.52 (4) | 1,044 | 1,043 |

About 1% of decisions are left to chance. It still dies on nearly every `smoke`
Run; the Goo gate (story 4.14) is where E4 is judged.

Those rows were measured at v4.0.0. Since the upgrade to v4.0.2 (#196) the same
seeds build different dungeons, so the numbers start again: on 40 Warriors from
`standard` the current Brain reaches depth 5 in 10 Runs and kills Goo in 7
(`docs/results/2026-10-25-feb88de55.md`).

The calibration was run again at v4.0.2 (#198). The old sequential test's false-accept rate
rose to 6.06%, past its margin, so ranked comparisons now use the e-process
(`docs/results/calibration-v4.0.2.md`). It needs about half again as many pairs per verdict.

## The four parts

| Part | What it is | Status |
|---|---|---|
| **Engine** (`harness`) | SPD's code driven headlessly, fast, reproducibly, through a fair Observation/Action interface | done (E1) |
| **Brain** (`brain`) | Belief state, scripted policies, tactical search, strategic playbooks, evaluation. No game imports, enforced by the build | in progress (E4); search and strategy later (E6, E7) |
| **Rig** (`rig`) | Thousands of seeded runs, SPRT comparisons, published numbers | done (E3) |
| **Overlay** (`overlay`) | The bot inside the real desktop game, in the game's own UI style | in progress (E5): the launcher, the embedded driver and the Panel with its Decision card are in |

Supporting modules: `api` (data types only), `codex` (every mob, item, table and
recipe at the pinned tag, generated with citations into `codex/<tag>/`), and
`docs/` (the MkDocs site, including the mechanics rules in `docs/rules/`).

## The one rule of play: information parity

The bot may use only what a human at the same screen could know: what the
renderer draws, the game log, the journal, and general game knowledge. It never
reads unidentified item identities, unseen enemies, hidden traps, secret doors,
RNG state, or the seed. A single class, `Observer`, is the only door from game
state to the bot, and every change to it ships with leak tests. The `brain`
module cannot import game code; the build fails if it tries. How each of these is
enforced and tested: [`docs/fairness.md`](docs/fairness.md).

## Upstream

Pinned to upstream tag **v4.0.2** (commit `57a4e06a`, released 2026-10-06).
Every edit to an upstream file is a documented hook; upgrades happen only by
merging a newer upstream tag. Details, hook table, and upgrade procedure:
[`docs/UPSTREAM.md`](docs/UPSTREAM.md).

Upstream's own build guides still apply and are kept verbatim:

- [Compiling for desktop platforms](docs/getting-started-desktop.md)
- [Compiling for Android](docs/getting-started-android.md) (not required for Shatterfish; the Android SDK is never needed)
- [Compiling for iOS](docs/getting-started-ios.md) (not required)
- [Recommended changes for making your own version](docs/recommended-changes.md)

## Building and running

Requires a JDK 21. No Android SDK, no Xcode.

```sh
./gradlew build                            # every module, JUnit 5 + ArchUnit tests
./gradlew :desktop:debug                   # the unmodified game
./gradlew :overlay:launch -Plaunch.args="--agent brain --seed <n> --class WARRIOR"   # the game with the Brain playing (E5)
./gradlew :overlay:launch -Plaunch.args="--agent human --seed <n> --class WARRIOR --window 1600x900"   # you play, recorded, the Brain shadowing you; N writes a note
./gradlew :rig:strategy --args="overlay-runs"   # prints <run-id>.human.txt: your turns beside the Brain's, one line each
sh tools/fetch-vanilla.sh                  # the Codex's second pinned source; `build` needs it
./gradlew :codex:generate                  # regenerate codex/<tag>/
./gradlew :rig:run --args="--brain shatterfish --seeds smoke --parallel 4 --out <dir>"   # play a Seed set
./gradlew :rig:gallery --args="<dir>"      # the death gallery for a Rig folder
./gradlew :rig:strategy --args="<dir>"     # the plain-text strategy log beside each Run log
./gradlew build -Pshatterfish.mobile=on    # also include upstream's android and ios modules
```

The Brains the rig knows are `random` (the Baseline), a few deliberately weakened
variants of it used to test the rig, and `shatterfish`, the Brain E4 is building.
How a comparison is registered, run and published:
[`docs/methodology.md`](docs/methodology.md).

Shatterfish's own modules live under `shatterfish/` (`api`, `harness`, `codex`,
`brain`, `rig`, `overlay`); see `docs/adr/0003-module-layout.md`.

## How this project is run

Shatterfish is built with the [BMAD Method](https://github.com/bmad-code-org/BMAD-METHOD).
Planning artifacts live in `_bmad-output/planning-artifacts/`, stories in
`_bmad-output/implementation-artifacts/`, and the program's seed document is
[`docs/BOOTSTRAP-PROMPT.md`](docs/BOOTSTRAP-PROMPT.md). GitHub Issues track
state; story files carry content; `docs/` carries knowledge.

## License

GPL-3.0-or-later, same as upstream. See [`LICENSE.txt`](LICENSE.txt) and
[`NOTICE.md`](NOTICE.md).
