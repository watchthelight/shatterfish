# Shatterfish

**An open-source engine for Shattered Pixel Dungeon, in the spirit of Stockfish.**

Shatterfish drives [Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon)'s
own game code headlessly, plays it with a hand-built symbolic bot, measures every change with
Fishtest-style statistical testing, and runs the bot inside the real desktop game where you can
watch it think, pause it, step it, and take over.

!!! warning "Unofficial and unaffiliated"
    Shatterfish is a permanent downstream fork of Shattered Pixel Dungeon by Evan Debenham
    (00-Evan), itself based on Pixel Dungeon by Watabou. It is endorsed by neither author.
    Never report Shatterfish problems to the upstream project. See [Upstream](UPSTREAM.md).

## The four parts

| Part | Module | What it is | Epic |
|---|---|---|---|
| **Engine** | `harness` | SPD's code driven headlessly, fast, reproducibly, through a fair Observation/Action interface | E1 |
| **Brain** | `brain` | Belief state, scripted policies, tactical search, strategic playbooks, evaluation. Cannot import game code; the build enforces it | E4, E6, E7 |
| **Rig** | `rig` | Thousands of seeded runs, SPRT comparisons, published numbers | E3 |
| **Overlay** | `overlay` | The bot inside the real desktop game, in the game's own UI style | E5, E8 |

## The one rule of play

The bot may use only what a human at the same screen could know. One class, `Observer`, is the
only door from game state to the bot, and every change to it ships with leak tests.
[Fairness](fairness.md) states the rule and the tests that enforce it.

## Where things are

- [Bootstrap prompt](BOOTSTRAP-PROMPT.md): the program's seed document; BMAD artifacts supersede it once approved.
- [Roadmap](roadmap.md): epics E0 to E9 and their "done when".
- [Architecture](architecture.md) and [Decisions](adr/index.md): how the system is shaped and why.
- [Rules](rules/index.md): every claim about a game mechanic, cited to `path:line` at the pinned tag.
- [Codex](codex/index.md): generated tables of mobs, items, drops, spawns, recipes.
- [Results](results/index.md): published rig numbers.
- [BMAD artifacts](bmad/index.md): brief, PRD, UX spec, architecture, epics, stories.

## How this documentation works

--8<-- "docs/README.md:body"
