---
title: 'Product Brief Addendum: Shatterfish'
status: ready
created: '2026-09-03'
updated: '2026-09-03'
---

# Product Brief Addendum: Shatterfish

Depth that belongs downstream (PRD, architecture, epics) or earned a place but does not fit a two-page brief. Sources: the bootstrap prompt, the brainstorm intent, and the technical research report; each item names which.

## Rejected alternatives (with rationale)

| Alternative | Why rejected | Source |
|---|---|---|
| Rust or any second implementation of the game's rules | A second rules engine drifts from the real game and every result becomes "not the game players play"; the bot must run the real code in the same JVM | Bootstrap non-negotiable 4 |
| A separate bot process over a socket | Adds serialization cost per decision (the research's throughput finding says policy overhead, not turn cost, is the ceiling) and breaks the human-takeover model | Bootstrap 4; research contrary A |
| Learned-first brain (RL, imitation, LLM agents) | Every benchmark since 2021 has symbolic bots ahead by a wide margin, though symbolic bots also plateau; learned components enter late at the tactical leaf under symbolic arbitration | Research section 5; brainstorm D5 |
| Boot the game headlessly with no scene and guard "a handful" of touchpoints | Overturned at v3.3.8: attack, zap, throw, and operate resolve in sprite-animation callbacks; 969 sprite dereferences in game logic | Research contrary A |
| Gate the rig on win rate | Near-zero win rate carries almost no information per run and makes the sequential test futile early | Research section 3 |
| Gate the rig on depth alone | Whatever is gated becomes the optimization target; a depth-gated bot dives and dies deeper | Research contrary B; NetHack Challenge score gaming |
| Chess-style search (alpha-beta, deep minimax) | Stochastic, partially observed, single-player: the search does not transfer, the testing culture does | Bootstrap section 0 |

## Technical constraints for the architecture

- **Non-negotiables.** Bootstrap prompt section 1 binds every artifact verbatim: information parity, license and attribution, no upstream pull requests, Java in-process, everything measured, native UI, issues/stories/docs roles, Codex over folklore.
- **Headless scene.** (Research recommendation 1.)
  - A harness-owned `Scene` supplies the Groups that sprites attach to.
  - A no-op `GL20`/`GL30` is installed before any `Texture` class loads.
  - Atlases are decoded through `Pixmap` (native, no GL); desktop natives are shipped.
  - `updatesPerSecond = 0`; each instance has its own preferences directory.
  - The driver fast-forwards `scene.update()` with a large elapsed time until `Actor.processing()` is false.
  - Upstream edits are limited to the static helpers that assume a `GameScene` (`spellSprite()`, `CellEmitter`).
- **Parallelism.** One JVM process per game by default. Classloader isolation must keep libGDX and its natives in a shared parent loader (the JNI specification forbids loading one native library into two loaders) and isolate only the game's classes per child; classloader isolation is a spike with measured turns per second as its exit criterion. (Research recommendation 2.)
- **Threading.** Game objects stay on the thread that owns them; only immutable Observations cross. `RenderedText.measure()` throws on the actor thread; every panel write goes through `Game.runOnRenderThread`. The actor thread waits on `sprite.isMoving`, so an embedded driver runs at animation speed unless it controls sprites. (Research section 7.)
- **Statistics.** (Research recommendation 3.)
  - Port Fishtest's GSPRT: approximation 2.1, regularization to 1e-3, overshoot clamp.
  - Test paired-seed differences of a composite outcome ordered win, depth, turns.
  - State bounds in standardized units, pre-registered with a hypothesis ID the rig enforces.
  - Require a burn-in and simulated calibration of realized error rates before any bound is trusted; recalibrate per upstream tag.
  - Evaluate an e-process or mixture-SPRT design as the alternative in the E3 ADR.
- **Search.** (Research recommendation 6.)
  - Start with one-ply expectimax over Codex tables.
  - Measure the simulator's turns per second and Long et al.'s leaf correlation, bias, and disambiguation for SPD tactics.
  - Choose between depth-limited sampled search (POMCP-style, horizon 2 to 4 hero turns, split swept empirically) and ISMCTS on those numbers.
  - Keep item identification in belief reasoning, not search.
  - Use a search leak test (identical decisions across hidden-state variants) as the fairness gate.
- **Boundary enforcement.** Three layers: no declared dependency edge from `brain` to game modules, a resolution-time classpath assertion, and an ArchUnit rule against `com.shatteredpixel..` and `com.watabou..`; ArchUnit 1.5.0. (Research recommendation 8; ADR-0003.)
- **Overlay building blocks.** `Component`, `Chrome.TOAST_TR`, `renderTextBlock`, `RedButton`, `ScrollPane`, and `SPDAction` constants for hotkeys; added at the end of `GameScene.create()` and placed in the free column in `layoutTags()` between the menu pane and the inventory pane; two hooks plus one for sprite-wait bypass. (Research recommendation 7.)
- **Brain maintenance mitigations.** Playbooks and priorities as data, a strategy log visible in the overlay, and an annual review of the learned frontier. (Research recommendation 5; contrary evidence C.)
- **Seeds.** Upstream defines the seed as fixing dungeon generation; treat it as generation-only until the code says otherwise; seeds are guaranteed only within a build. (Research section 6.)

## Non-functional requirements to carry into the PRD

- Fairness: leak, differential, toggle, ArchUnit, and determinism tests in CI; a search leak test once search exists; oracle mode off by default, flagged, impossible in ranked runs. (Bootstrap section 4; research recommendation 4.)
- Reproducibility: a run is (tag, seed, action list); hash-chained run logs replayable by a third party from observations only. (Bootstrap non-negotiable 5; brainstorm D3.)
- Throughput: stated as a measured number after E1, not a guess; the brainstorm's arithmetic (1,000 runs × 2,000 turns at 10,000 turns per second is 200 seconds) is the target to test, not a requirement. (Brainstorm D5; research recommendation 2.)
- Overlay responsiveness: the brain thinks off-thread; no panel write off the render thread. (Research recommendation 4.)
- Upstream upgrade: tag-only merges by the documented procedure; hooks re-verified; Codex regenerated; fairness and rig re-run. (Bootstrap non-negotiable 3; docs/UPSTREAM.md.)
- Documentation currency: docs and ADRs change in the same pull request as the code; generated files never hand-edited; every mechanics claim cites `path:line`. (Bootstrap non-negotiables 7 and 8.)

## Audience sequencing and personas (from brainstorm D2)

1. **Developer (v1, E5):** pause, step, run N, speed, take over, explain, path highlight. The overlay is the debugger.
2. **Learner (v2, E8):** coach mode (bot advises with reasons, human plays) and autoexplore-with-brains (human hands quiet stretches to the bot). Needs: explanations in game vocabulary, no cheating, no desync when they take over. Success: they watch a run and understand a decision they would not have made.
3. **Community reader (v1, E3):** public seed sets including a never-run holdout, methodology page, published negatives, replayable logs, a fairness badge. Success: a skeptic can reproduce a number.
4. **Spectator and researcher (later):** big-text spectator layout; run-log exports; brain plug-in without touching the overlay.

## Roadmap items outside the brief's scope

| Item | Status |
|---|---|
| SPSA tuning of eval weights on the rig | Promoted to E6+ |
| Playbooks as data | Promoted to E7 |
| Death replay gallery | Promoted to E3 |
| Public leaderboard | Parked (ideas ledger) |
| Bot-vs-seed challenge issues | Parked |
| Decision narration as text (screen-reader-friendly view of a run) | Parked |
| GitHub Actions matrix as rig workers | Parked |
| Headroom metric via an oracle-assisted upper bound (measurement only) | Parked |
| Strength per think budget | Parked |
| A post per epic on the docs site | Parked |
| A `docs/rules` citation checker | Parked |

## Open questions for the PRD

- Which think budgets to publish, and whether v1 has any budget before E6.
- The human win-rate source for "beats the median human" calibration (blog, in-game stats).
- Whether coach mode belongs in E8 or becomes its own epic.
- Fishtest's current default bounds as text (research open question), for the E3 ADR's starting values.
- The target win rate that turns the headline (a first verified Yog-Dzhewa kill) into a sustained number, and over which seed set and class.

## Sizing data (community only)

Market sizing is out of scope for an open-source engine; the relevant "size" is the SPD community (about 6,500 repository stars, a Shattered-focused wiki created in 2025, active Lemmy and Steam forums), as recorded in section 6 of the research report.
