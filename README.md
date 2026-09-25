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
| E5 Overlay v1 | The bot inside the desktop game: watch it think, step it, take the controls mid-fight | planned |
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
- In review: **4.8** picking up and equipping items.

The Brain is still weak, but it now plays rather than wanders. On the `smoke`
Seed set, exploring and fighting together take the mean deepest floor from 1.04
to 2.16 and the mean score from 78 to 405, and leave only 2% of decisions to
chance (PR #137 has the table). It still dies on every `smoke` Run; the Goo gate
(story 4.14) is where E4 is judged.

## The four parts

| Part | What it is | Status |
|---|---|---|
| **Engine** (`harness`) | SPD's code driven headlessly, fast, reproducibly, through a fair Observation/Action interface | done (E1) |
| **Brain** (`brain`) | Belief state, scripted policies, tactical search, strategic playbooks, evaluation. No game imports, enforced by the build | in progress (E4); search and strategy later (E6, E7) |
| **Rig** (`rig`) | Thousands of seeded runs, SPRT comparisons, published numbers | done (E3) |
| **Overlay** (`overlay`) | The bot inside the real desktop game, in the game's own UI style | planned (E5, E8) |

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

Pinned to upstream tag **v4.0.0** (commit `2bb34a4e`, released 2026-09-09).
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
