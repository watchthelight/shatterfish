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

## The four parts

| Part | What it is | Status |
|---|---|---|
| **Engine** (`harness`) | SPD's code driven headlessly, fast, reproducibly, through a fair Observation/Action interface | planned (E1) |
| **Brain** (`brain`) | Belief state, scripted policies, tactical search, strategic playbooks, evaluation. No game imports, enforced by the build | planned (E4, E6, E7) |
| **Rig** (`rig`) | Thousands of seeded runs, SPRT comparisons, published numbers | planned (E3) |
| **Overlay** (`overlay`) | The bot inside the real desktop game, in the game's own UI style | planned (E5, E8) |

Supporting modules: `api` (DTOs only), `codex` (reflection dump of every mob,
item, table, and recipe at the pinned tag), `lore/` (mechanics knowledge with
provenance), `docs/` (MkDocs site).

## The one rule of play: information parity

The bot may use only what a human at the same screen could know: what the
renderer draws, the game log, the journal, and general game knowledge. It never
reads unidentified item identities, unseen enemies, hidden traps, secret doors,
RNG state, or the seed. A single class, `Observer`, is the only door from game
state to the bot, and every change to it ships with leak tests. See
`docs/fairness.md` once it exists.

## Upstream

Pinned to upstream tag **v3.3.8** (commit `7b8b845a`, 2026-03-19). Every edit to
an upstream file is a documented hook; upgrades happen only by merging a newer
upstream tag. Details, hook table, and upgrade procedure:
[`docs/UPSTREAM.md`](docs/UPSTREAM.md).

Upstream's own build guides still apply and are kept verbatim:

- [Compiling for desktop platforms](docs/getting-started-desktop.md)
- [Compiling for Android](docs/getting-started-android.md) (not required for Shatterfish; the Android SDK is never needed)
- [Compiling for iOS](docs/getting-started-ios.md) (not required)
- [Recommended changes for making your own version](docs/recommended-changes.md)

## Building

Requires a JDK 21. No Android SDK, no Xcode.

```sh
./gradlew build                           # every module, JUnit 5 + ArchUnit tests
./gradlew :desktop:run                    # the unmodified game
./gradlew build -Pshatterfish.mobile=on   # also include upstream's android and ios modules
```

Shatterfish's own modules live under `shatterfish/` (`api`, `harness`, `codex`,
`brain`, `rig`, `overlay`); see `docs/adr/0003-module-layout.md`. Rig, codex,
and overlay commands will be listed here as each epic ships.

## How this project is run

Shatterfish is built with the [BMAD Method](https://github.com/bmad-code-org/BMAD-METHOD).
Planning artifacts live in `_bmad-output/planning-artifacts/`, stories in
`_bmad-output/implementation-artifacts/`, and the program's seed document is
[`docs/BOOTSTRAP-PROMPT.md`](docs/BOOTSTRAP-PROMPT.md). GitHub Issues track
state; story files carry content; `docs/` carries knowledge.

## License

GPL-3.0-or-later, same as upstream. See [`LICENSE.txt`](LICENSE.txt) and
[`NOTICE.md`](NOTICE.md).
