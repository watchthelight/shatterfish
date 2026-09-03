---
title: 'Product Brief: Shatterfish'
status: ready
created: '2026-09-03'
updated: '2026-09-03'
inputs:
  - docs/BOOTSTRAP-PROMPT.md
  - _bmad-output/brainstorming/brainstorm-shatterfish-program-2026-09-03/brainstorm-intent.md
  - _bmad-output/planning-artifacts/research/technical-shatterfish-engine-foundations-2026-09-03/research.md
addendum: addendum.md
---

# Product Brief: Shatterfish

## Executive summary

Shatterfish is an open-source engine for Shattered Pixel Dungeon (SPD) in the spirit of Stockfish. It is four parts: the game's own code driven headlessly and reproducibly; a hand-built symbolic bot that plays it; a Fishtest-style rig that measures every change over thousands of seeded runs; and an overlay that runs the bot inside the real desktop game, where a person can watch it think, pause it, step it, and take over. It is a permanent downstream fork of SPD, pinned to a release tag, unofficial and unaffiliated.

Nobody can currently say, with evidence, how well a strategy for SPD works. Shatterfish's answer is two disciplines enforced by architecture rather than intentions: information parity (the bot may use only what a human at the same screen could see) and measurement (nothing about the bot is believed until the rig says so). Why now: the field is empty and the community small and active, upstream is quiet while its next major version is built privately, and the research shows every foundation (headless boot, native overlay, statistics, symbolic-bot design) has a known shape and a known cost.

## The problem

- **No ground truth for strategy.** A player who wonders whether to drink unknown potions on floor 2 or save them has forum folklore, a wiki page with no source, and their own deaths. There is no way to run the question a thousand times.
- **Knowledge is unreliable at the source.** The Fandom wiki hosts vanilla Pixel Dungeon and Shattered pages on one site under prefixes; the newer community wiki gives formulas that name code identifiers but cite no file. Claims about mechanics rot across versions and across the two games.
- **Tooling has nowhere to live.** Upstream's repository states it does not accept pull requests. Every SPD tool is therefore a fork, and the existing forks either stopped tracking upstream or never did.
- **No precedent, fair or measured.** The only bot project for SPD is an abandoned course fork and the seedfinders drive one function of the game inside an invisible window: no gym, no RL agent, no headless harness. Elsewhere, the NetHack bots that beat every learned agent consume a privileged semantic feed and the DCSS bot runs inside the game's scripting layer; none publishes a testing culture a skeptic could audit.

## The solution

Four parts, shipped in order of dependence:

1. **Engine (harness).** SPD's `core` booted on libGDX's headless backend inside a harness-owned scene, seeded end to end, driven turn by turn through a fair Observation/Action interface. One class, `Observer`, builds the Observation from what the game already computes for drawing; one class, `ActionExecutor`, drives the hero through the same code paths the UI uses.
2. **Brain.** Belief state, scripted policies, tactical search, strategic playbooks, an evaluation function. It depends on the Observation types alone; the build fails if it imports game code. Identical code runs headless and in the overlay.
3. **Rig.** Thousands of seeded runs in parallel, sequential statistical comparison of two brains (Fishtest's Generalized SPRT, GSPRT), JSONL run logs with a hash chain, replay, published results.
4. **Overlay.** The bot inside the real desktop game, in the game's own UI toolkit: it shows its goal, chosen action with reasons, beliefs, and safety flags, and a human can pause, step, run N, or take over and hand back. The brain re-plans from the current Observation every turn, so a human can act at any time without desync.

Supporting the four parts: a **Codex** generated from the pinned code with `path:line` citations, and a **lore pipeline** that admits community knowledge only with provenance and a verification tier.

## What makes this different

Honestly stated: there is no technical moat. Anyone can fork the same game. What Shatterfish has is a pair of rules that are expensive to keep and easy to drop, and the infrastructure that keeps them:

- **Fairness by architecture.** Parity is enforced by module boundaries, a single Observer, leak and differential tests in CI, and an oracle mode that is off by default, visibly flagged, and impossible in ranked runs. Every strong roguelike bot in the literature skipped this.
- **Stockfish's testing culture, not its search.** SPD is stochastic, partially observed, single-player; chess search does not transfer. GSPRT, paired seeds, pre-registration, and published negatives do.
- **The bot explains itself in the game's own words.** Native UI, Codex vocabulary, a strategy log a human can follow. The overlay is the debugger for everything built after it and, later, a coach.
- **Knowledge from code, not folklore.** The Codex is regenerated by one build task and CI fails on drift; every mechanics claim in the docs cites the pinned tag.

## Who this serves

- **Primary at v1: Shatterfish's developer.** v1 is a one-user product with a public codebase; the product owner is the developer's reviewer and domain expert, not a separate user. Needs: fast headless runs, trustworthy numbers, an overlay that shows why the bot did what it did. Success: they can change the brain, run the rig, and know within an hour whether it got better.
- **Then, in order:** SPD players who want to learn (coach mode and autoexplore-with-brains, overlay v2); the community as readers of published seed sets, results with confidence intervals, and replayable logs; and, later, researchers and spectators, out of scope for v1. Needs and success signals per audience: addendum, "Audience sequencing and personas".

## Success criteria

Winning is a ladder, measured on public seed sets; the headline is a win, and every rung below it is how the program knows it is getting there:

| Stage | Signal |
|---|---|
| **Headline** | **The bot kills the final boss, Yog-Dzhewa, and wins a run**, reproducible from its run log on a public seed; from there, win rate per class on the standard seed set is the canonical number. |
| E1 harness | Three measured numbers replace guesses: fast-forwarded turns per second, paired-seed correlation on a smoke set, and the tactics' leaf correlation and disambiguation. Same seed twice is byte-identical; all fairness tests pass. |
| E3 rig | A reproducible random-agent baseline is published; a deliberately worse agent is rejected by the sequential test; every published number carries tag, seed set, commit, hypothesis ID, and the command that reproduces it. |
| E4 baseline brain | Kills Goo on a large majority of the standard seed set, with the survival curve and boss-kill staircase published. |
| E5 overlay | A human watches a full sewers run and takes over mid-fight without desync. |
| Long run | Win rate on default settings per class (canonical), relative strength chain between versions, ascension and challenges as the far horizon. |

Process criteria: no brain change merges without rig numbers; every fairness test runs on every pull request; docs change with the code.

## Scope

**In for v1 (E0 to E5):** bootstrap and planning; headless-scene harness with Observer, ActionExecutor, seeding, determinism, and the fairness test suite; Codex generation; rig with seed sets, GSPRT, run logs, replay, nightly results; a scripted baseline brain with beliefs and a worst-case item-test check; overlay v1 with pause, step, run N, speed, take over, path highlight, native styling.

**Deferred (E6 to E8):** tactical search (decision gated on measured properties), strategy and lore pipeline, overlay v2 (explain view, pause-on conditions, replay scrubber, beliefs view, hotkeys), coach mode.

**Out:** learned evaluation (optional E9), any second implementation of game rules in any language, any process boundary between bot and game, any UI framework other than the game's own, Android and iOS, leaderboards and community challenges, other games, proposing changes upstream.

**Constraints:** no external deadline; one upstream upgrade after the E3 baseline, when 4.0 stable lands. Java 21; one JVM; GPL-3.0-or-later; every edit to an upstream file is a documented hook.

## Feasibility and risks

The technical research settled the shape of each foundation and left two numbers that cannot be researched, only measured: what paired seeds buy the rig (chess gets 15%) and whether SPD tactics reward search (leaf correlation and disambiguation). Headless is a scene, not a stub; the sequential test is 20 lines to port; search starts at one ply over Codex tables; the brain's cost is maintenance, not feasibility. Detail: addendum, "Technical constraints for the architecture".

| Risk | Mitigation |
|---|---|
| Rendering is coupled to turn resolution more deeply than the audit finds | The first E1 story is the touchpoint audit; the research's inventory is its starting point |
| Pairing buys little and the rig is slow to decide | Depth and boss milestones as early metrics; composite outcome; measure before promising budgets |
| Solo engineer stalls before the first win | Overlay v1 ships right after the baseline brain as motivation and debugger; publish the first Goo kill |
| Upstream 4.0 lands as one large drop | Hooks capped and listed; merge measured with one API call; upgrade only after a baseline exists |
| A parity leak slips in | Three enforcement layers, leak and differential tests, an adversarial fairness review on every relevant change |

## Vision

In two to three years Shatterfish is the reference engine for SPD. It publishes a reproducible strength ladder per class and per version; its coach mode explains a run in the game's own words; community members improve its playbooks with rig numbers attached; and the community treats its Codex and rules corpus as the shared, cited ground truth for how the game works. It upgrades tag by tag behind upstream without ever needing upstream's cooperation, and its fairness guarantee is the reason anyone trusts its numbers.
