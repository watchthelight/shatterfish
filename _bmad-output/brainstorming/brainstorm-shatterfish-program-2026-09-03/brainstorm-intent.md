---
title: Shatterfish brainstorm intent
date: 2026-09-03
source: .memlog.md (131 entries; autonomous session; Checkpoint B answered by the product owner)
consumers: bmad-product-brief, bmad-prd, bmad-architecture
status: approved
---

# Shatterfish brainstorm intent

Seed: `docs/BOOTSTRAP-PROMPT.md`. The non-negotiables in its section 1 were not re-litigated
and bind everything below. Five questions were pushed; five directions came out; the product
owner kept all five and promoted three parked items.

## Through-line

Three of the five questions have one answer: **measurement**. Winning is a measured ladder,
trust is measurement made verifiable, and most stall risk is measurement arriving too late (win
rate near zero) or too slow (throughput). The fairness question is the one real architecture
fork. The audience question is settled by sequencing, not by choice.

## D1. Winning is a ladder, not a number

- **Now (E1-E4)**: survival curve over depth (Kaplan-Meier; brains compared by curve dominance)
  and the boss-kill staircase (Goo, Tengu, DM-300, Dwarf King, Yog) as milestone metrics.
- **Canonical (E4 onward)**: win rate on default settings, per class, over a fixed public seed
  set. Warrior first; all four classes reported separately.
- **Between versions**: relative strength by SPRT against the previous version, Stockfish-style;
  the chain is published, there is no absolute rating.
- **Far horizon**: ascension rate; win rate with challenges enabled (up to 9).
- **Once search exists**: strength reported per think budget.
- **Never the headline**: in-game score (auxiliary only), turns per second (a rig property).
- **Milestones with names**: "beats the median human on default" (calibrated against published
  human win rates; tier-3 lore until sourced), "wins on 9 challenges".
- **Solved-seed set**: per-seed "can the bot win this seed"; grows monotonically; doubles as a
  regression suite that must stay won.

## D2. Overlay audience by sequence

1. **v1 (E5)**: the developer debugging the bot. Pause, step, run N, speed, take over, explain,
   path highlight. The overlay is the debugger for everything after it.
2. **v2 (E8)**: learners. Coach mode (bot advises with reasons, human plays) and
   autoexplore-with-brains (human hands quiet stretches to the bot).
3. **Later**: spectator layout (big text for streams), researcher exports (run logs, brain
   plug-in without touching the overlay).
- Contract: explanations use the game's own vocabulary from the Codex, never internal jargon.
- The overlay cannot become a cheat tool: it shows only what the Observation holds; oracle mode
  draws a red border and label and is never available in the rig.

## D3. Trust kit for the rig

- Public, fixed, versioned seed sets: `smoke` (~20), `standard` (~1,000), `holdout` (~1,000,
  never run during development), `bosses` (early boss variety).
- **Pre-registration**: SPRT hypotheses and bounds fixed before a run; every results file
  carries a hypothesis id; the rig refuses an unregistered comparison. One test per change.
- **Verifiable runs**: JSONL logs with a hash chain of Observation hashes and actions; anyone
  can replay from observations only; nightly replays a random published run.
- **Reproduced elsewhere**: baselines re-run by CI on GitHub runners, not the laptop; every
  published number carries tag, seed set, commit, brain config, SPRT params, and the command.
- **Honest statistics**: confidence intervals and run counts, never point estimates; failed
  tests published like Fishtest; a methodology page (SPRT choice, Bernoulli win model, depth as
  ordinal with higher power early, power analysis, partial-seeding caveat).
- **Two-stage tests** (short set then long set) like Fishtest STC/LTC.
- **Cheat detector**: the same brain on two hidden-state variants of one visible world must act
  identically until observations diverge (the differential test, generalized to behaviour).
- Fairness tests on every PR; a README badge derived from them.

## D4. Search under fairness (needs its own ADR with criteria)

- The brain may not import game code (#1) and may not reimplement rules (#4). Therefore
  lookahead has exactly two fair sources of rules:
  1. **Codex-derived tactical tables** (expected damage, hit chance by level difference,
     speeds, turns-to-live): the E6 MVP, one-ply lookahead, no simulation.
  2. **An engine-backed fair simulator in `harness`**: the real engine run on a redetermined
     reconstruction (either a world built from Observation plus belief sample, or a scrubbed
     snapshot with all hidden state re-sampled). Fair only if scrubbing is complete;
     differential tests are mandatory. Later, with criteria recorded in the ADR.
- **Beliefs** are a first-class fairness object: `Beliefs.update(Observation)` is a pure
  function with its own leak tests; candidate identities weighted from Codex spawn weights and
  identification history.
- **Search leak test**: replace true hidden state with random alternates; decisions must be
  identical.
- Strategy-fusion pitfall: averaging over determinizations pretends hidden info will be known;
  limit horizon (2-4 turns) or use information-set search.
- Search budget: visible-enemy turns and boss fights; scripted policy handles quiet turns.
- **Runtime enforcement**: Observer and ActionExecutor assert the render thread; the brain
  thread can never hold a game object.
- Learned eval (E9) is fair if inputs are Observations and training seeds are disjoint from
  evaluation seeds.

## D5. Stall-proofing

- First E1 story: **headless touchpoint audit** (every static rendering call reachable from
  game logic: GameScene, Sample, GLog, Camera, Game.scene) before writing the driver.
- E1 spike: **classloader isolation** to run N games per JVM despite static singletons;
  measure throughput early (target arithmetic: 1,000 runs x 2,000 turns = 2M turns; 10k
  turns/s gives 200 s).
- Abstract tactical model before snapshot/restore; the tar pit is deferred with criteria.
- Ordinal metrics (depth, boss milestones) from day one, because SPRT on win rate is futile
  while win rate is near zero.
- Publish a GIF of the bot playing as soon as E1 or E5 allows; celebrate first Goo, first Tengu.
- One upstream upgrade after the E3 baseline (a 4.0.0-beta tag already exists); hooks capped
  at a handful.
- Determinism test on every PR; nightly Windows-vs-Linux Observation hash comparison.
- Holdout seeds against overfitting; bmad-loop for unattended stories once psmux is installed;
  spec-drift check in every retrospective.

## Promoted from the parked list

- **SPSA tuning** of eval weights on the rig (E6+): Stockfish's tuning transfers directly.
- **Playbooks as data** (E7): expert-editable, versioned, rig-tested; the openings-book analogy
  and the low-code contribution path.
- **Death replay gallery** (E3): worst deaths auto-published with decision logs; a bug source.

## Lessons carried from failure analysis

- NetHack Challenge 2021: symbolic first (AutoAscend-style strategy-priority arbitration plus
  a tactical solver); learned parts optional.
- Pre-Fishtest Stockfish: rig before brain (E3 before E4).
- DCSS `qw`: pin the tag, diff the Codex, upgrade by procedure; expert lore matters.
- Hidden-info bot scandals: verifiable runs or nothing.
- Headless libGDX: never construct scenes headless; drive `Dungeon` and `Actor.process`.
- Takeover desync: E5 acceptance test interleaves scripted human actions headlessly.
- Singleton-engine refactors: never refactor upstream; isolate per JVM or classloader.
- p-hacking: one registered SPRT per change.

## Open questions for the product brief

- Which think budgets to publish (and whether v1 has any budget at all before E6).
- The exact human win-rate source for "median human" calibration (blog, in-game stats).
- Whether coach mode (D2 v2) belongs in E8 or becomes its own epic.
