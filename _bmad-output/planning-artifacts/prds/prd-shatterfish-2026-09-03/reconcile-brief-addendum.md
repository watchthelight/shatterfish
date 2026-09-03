---
title: 'Reconciliation: brief addendum vs PRD'
input: _bmad-output/planning-artifacts/briefs/brief-shatterfish-2026-09-03/addendum.md
against:
  - _bmad-output/planning-artifacts/prds/prd-shatterfish-2026-09-03/prd.md
  - _bmad-output/planning-artifacts/prds/prd-shatterfish-2026-09-03/addendum.md
created: '2026-09-03'
---

# Reconciliation: brief addendum vs PRD

Every substantive item in the brief addendum, with where the PRD carries it. Status codes: **carried** (present as capability, NFR, non-goal, open question, or PRD-addendum entry), **carried (ADR)** (the PRD correctly leaves the technical how to the architecture; not a gap), **weakened**, **contradicted**, **absent**.

## 1. Rejected alternatives

| Input item (quoted) | PRD location | Status | Note |
|---|---|---|---|
| "Rust or any second implementation of the game's rules" | Non-goal 5.1 "will not reimplement any game rule in any language; the real engine is the only rules engine"; section 10 Runtime "one JVM; the bot in the same process as the game" | carried | Rationale not restated; PRD addendum says the brief addendum is the home for rationale. |
| "A separate bot process over a socket" | Non-goal 5.2 "will not run the bot as a separate process or over a socket" | carried | |
| "Learned-first brain (RL, imitation, LLM agents)" | Section 2.2 "learned components are optional and late"; non-goal 5.9 "will not train a learned evaluation before the hand-tuned one has a measured Baseline (E9 is optional and last)" | carried | The brief's placement rule, "enter late at the tactical leaf under symbolic arbitration", is not carried; the PRD says only "late". Architecture-level; acceptable, but E9's shape is unspecified. |
| "Boot the game headlessly with no scene and guard 'a handful' of touchpoints" | Glossary "Headless scene"; FR-1 consequence "Turn resolution paths that depend on sprite animation (attack, zap, throw, use) complete without a real render loop"; PRD addendum ADR row "Headless-scene design" | carried (ADR) | The rejection is implied by requiring a Headless scene, not stated as a non-goal. Fine. |
| "Gate the rig on win rate" | SM-C3 "Win rate as an early gate ... gating on it stalls the program" | carried | |
| "Gate the rig on depth alone" | SM-C2 "Depth reached alone. A Brain that dives and dies deeper is worse" | carried | |
| "Chess-style search (alpha-beta, deep minimax)" | Section 1 "Chess search does not transfer to a stochastic, partially observed, single-player game"; FR-34 lists only expectimax / sampled / information-set designs | carried | Narrative only, not a non-goal bullet. Acceptable. |

## 2. Technical constraints for the architecture

| Input item (quoted) | PRD location | Status | Note |
|---|---|---|---|
| "Non-negotiables ... bind every artifact verbatim" (eight items) | Section 0 "The eight non-negotiables in the bootstrap prompt's section 1 bind every requirement here and are not restated" | carried | By reference, as the brief intends. |
| Headless scene: "A harness-owned `Scene` supplies the Groups that sprites attach to" | Glossary "Headless scene ... supplies what sprites attach to"; PRD addendum ADR row E1 | carried (ADR) | |
| Headless scene: "A no-op `GL20`/`GL30` is installed before any `Texture` class loads" | Glossary "no-op graphics layer"; FR-1 "no OpenGL context"; ADR row | carried (ADR) | |
| Headless scene: "Atlases are decoded through `Pixmap` (native, no GL); desktop natives are shipped" | FR-1 consequence "Boot succeeds with the desktop natives shipped and fails with a clear message if they are missing"; ADR row "Pixmap atlases" | carried (ADR) | |
| Headless scene: "`updatesPerSecond = 0`" | ADR row "fast-forwarded updates" | carried (ADR) | |
| Headless scene: "each instance has its own preferences directory" | **absent** | absent | FR-19 "One process per Run by default" has no consequence that parallel Runs do not share on-disk state (preferences, saves, badges). This is a capability-level isolation need, not just a how; without it parallel Runs can clobber each other. Suggest a consequence under FR-19 or FR-1. |
| Headless scene: "The driver fast-forwards `scene.update()` ... until `Actor.processing()` is false" | Glossary "fast-forwarded update loop"; open question 1 "does the actor thread ever block under it" | carried (ADR) | |
| Headless scene: "Upstream edits are limited to the static helpers that assume a `GameScene` (`spellSprite()`, `CellEmitter`)" | ADR row "which static helpers need hooks"; SM-C4 "Number of Hooks ... fewer is better"; FR-48 hook registry | carried (ADR) | |
| Parallelism: "One JVM process per game by default" | FR-19 consequence "One process per Run by default"; section 6.2 "processes are the default" | carried | |
| Parallelism: "classloader isolation ... libGDX and its natives in a shared parent loader (the JNI specification forbids ...)" | PRD addendum ADR row "libGDX in a shared parent loader if isolation is tried (JNI rule)" | carried (ADR) | |
| Parallelism: "classloader isolation is a spike with measured turns per second as its exit criterion" | Section 6.2 "Classloader isolation for many games per JVM: a spike in E1, not a requirement" | weakened | The spike is carried; its exit criterion (measured turns per second) is not stated anywhere. Add to 6.2 or the ADR row. |
| Threading: "Game objects stay on the thread that owns them; only immutable Observations cross" | FR-12 Thread confinement | carried | |
| Threading: "`RenderedText.measure()` throws on the actor thread; every panel write goes through `Game.runOnRenderThread`" | FR-38 consequence "Every Panel write happens on the render thread"; NFR-4 "Panel updates are posted to the render thread"; ADR row "Threading model for the Overlay" | carried (ADR) | |
| Threading: "The actor thread waits on `sprite.isMoving`, so an embedded driver runs at animation speed unless it controls sprites" | **absent** as a stated consequence | absent | FR-39 defines Speed only as "a turns-per-second cap" and "Speed caps bot turns without affecting the game's animation speed". The PRD is silent on whether the Overlay can ever run faster than animation (relevant to Run N, replay scrubbing, and the brief's "one [hook] for sprite-wait bypass" under Overlay building blocks). Either state that Overlay speed is bounded by animation speed (a v1 non-goal) or add the capability. |
| Statistics: "Port Fishtest's GSPRT: approximation 2.1, regularization to 1e-3, overshoot clamp" | Glossary "Sequential test ... Fishtest-style Generalized SPRT (GSPRT)"; PRD addendum ADR row "Rig statistics: GSPRT port ..." | carried (ADR) | Parameters left to the E3 ADR; correct. |
| Statistics: "Test paired-seed differences of a composite outcome ordered win, depth, turns" | Glossary "Composite outcome"; FR-21 | carried | |
| Statistics: "State bounds in standardized units, pre-registered with a hypothesis ID the rig enforces" | FR-21 "bounds stated in standardized units"; FR-22 Pre-registration "the Rig refuses an unregistered comparison" | carried | |
| Statistics: "Require a burn-in and simulated calibration of realized error rates before any bound is trusted" | FR-21 consequence "does not stop before a burn-in; realized error rates are validated by simulation ... and published on the methodology page" | carried | |
| Statistics: "recalibrate per upstream tag" | **absent** from FR-50 / NFR-5 | absent | The upgrade procedure lists "Rig re-baseline" but not recalibration of the Sequential test's realized error rates on the new tag's outcome distribution. Add to FR-50 and NFR-5. |
| Statistics: "Evaluate an e-process or mixture-SPRT design as the alternative in the E3 ADR" | PRD addendum ADR row "e-process alternative" | carried (ADR) | |
| Search: "Start with one-ply expectimax over Codex tables" | FR-34 "one-ply over Codex tables"; ADR row E6 | carried (ADR) | |
| Search: "Measure the simulator's turns per second and Long et al.'s leaf correlation, bias, and disambiguation" | SM-4; FR-5 assumption; open question 3 | carried | |
| Search: "Choose between depth-limited sampled search (POMCP-style, horizon 2 to 4 hero turns ...) and ISMCTS on those numbers" | FR-34 "depth-limited Search (two to four hero turns) ... chosen on measured properties" | carried | |
| Search: "Keep item identification in belief reasoning, not search" | FR-29 / FR-30 put identification in Beliefs and safeTest; FR-34 says Search "sees only the Observation and Beliefs" | weakened | The positive half is carried; the prohibition (Search must not perform identification reasoning) is not stated. One clause on FR-34 closes it. |
| Search: "Use a search leak test (identical decisions across hidden-state variants) as the fairness gate" | FR-13; FR-34 consequence "FR-13 passes for the chosen design"; NFR-1 | carried | |
| Boundary enforcement: "no declared dependency edge ..., a resolution-time classpath assertion, and an ArchUnit rule against `com.shatteredpixel..` and `com.watabou..`; ArchUnit 1.5.0" | FR-7 three consequences; section 11 "ArchUnit 1.5" | carried | Package names left to architecture; fine. |
| Overlay building blocks: "`Component`, `Chrome.TOAST_TR`, `renderTextBlock`, `RedButton`, `ScrollPane`, and `SPDAction` constants for hotkeys" | FR-38 "built from the game's own UI toolkit"; FR-42 "bindable through the game's own key-binding system"; ADR row Threading/Overlay | carried (ADR) | |
| Overlay building blocks: "added at the end of `GameScene.create()` and placed in the free column in `layoutTags()` between the menu pane and the inventory pane" | FR-38 assumption "the Panel occupies the free column between the menu pane and the inventory pane" | carried | |
| Overlay building blocks: "two hooks plus one for sprite-wait bypass" | SM-C4 (hook count as counter-metric); FR-48 hook registry | weakened | Hook budget for the Overlay not stated; sprite-wait bypass hook not mentioned anywhere (see Threading row). |
| Brain maintenance: "Playbooks and priorities as data" | FR-35 Playbooks as data (E7); FR-28 Arbitration "ordered list of Policies" | weakened | Playbooks-as-data is carried. "Priorities as data" (the Arbitration order editable without code) is not: FR-28 describes the order but not that it is data. |
| Brain maintenance: "a strategy log visible in the overlay" | FR-36 "in a form the Overlay shows and the Run log stores" | carried | |
| Brain maintenance: "an annual review of the learned frontier" | **absent** | absent | A program-hygiene item (section 4.8 or the ideas ledger). Small, but it was the research's mitigation for the symbolic-plateau risk (contrary evidence C). |
| Seeds: "treat it as generation-only until the code says otherwise" | Open question 4; FR-2 consequence "Every random source the game uses, including the general-purpose generator used for combat rolls, is seeded by the Harness" | carried | FR-2 already requires the Harness to seed everything, which is the right capability regardless of the answer. |
| Seeds: "seeds are guaranteed only within a build" | Glossary "Run ... fully determined by (Upstream tag, seed, Action list)"; section 9 Observation schema versioning | carried | Tag stands in for build. |

## 3. Non-functional requirements to carry into the PRD

| Input item (quoted) | PRD location | Status | Note |
|---|---|---|---|
| "Fairness: leak, differential, toggle, ArchUnit, and determinism tests in CI; a search leak test once search exists; oracle mode off by default, flagged, impossible in ranked runs" | NFR-1; FR-7 to FR-13 | carried (strengthened: adds thread-confinement, classpath, adversarial review) | |
| "Reproducibility: a run is (tag, seed, action list); hash-chained run logs replayable by a third party from observations only" | NFR-2; FR-2, FR-23, FR-24 | carried (strengthened: nightly cross-platform hash comparison) | |
| "Throughput: stated as a measured number after E1, not a guess; the brainstorm's arithmetic ... is the target to test, not a requirement" | NFR-3 "Target: at least 10,000 game turns per second ... a target to measure in E1 and restate, not a requirement to design around; the minimum acceptable is 1,000 turns per second, below which the Rig design changes" | **contradicted** (partially) | The brief explicitly declines to make throughput a requirement. NFR-3 keeps that sentence but then introduces a floor ("minimum acceptable is 1,000") that the brief did not set and the research did not source. Either drop the floor or state it as an E1 decision trigger with its source. SM-C1 and non-goal 5.8 are consistent with the brief. |
| "Overlay responsiveness: the brain thinks off-thread; no panel write off the render thread" | NFR-4; FR-38 consequence | carried (strengthened: configurable thinking budget shown when exceeded) | |
| "Upstream upgrade: tag-only merges by the documented procedure; hooks re-verified; Codex regenerated; fairness and rig re-run" | NFR-5; FR-50; UJ-5 | carried | See Statistics row for the missing recalibration step. |
| "Documentation currency: docs and ADRs change in the same pull request as the code; generated files never hand-edited; every mechanics claim cites `path:line`" | NFR-6; FR-17; FR-52 | carried | |

## 4. Audience sequencing and personas

| Input item (quoted) | PRD location | Status | Note |
|---|---|---|---|
| Developer (v1, E5): "pause, step, run N, speed, take over" | FR-39 Controls | carried | |
| Developer (v1, E5): "explain" | FR-32 Decision output (reasons, v1); FR-38 Panel shows reasons (v1); FR-44 Explain view "Deferred to E8" | **weakened** | The brief lists "explain" among the E5 developer controls. The PRD gives v1 only one-line reasons per alternative and moves the Explain view (Policy that fired, Evaluation terms) to E8. FR-36 Strategy log (E4) partly covers "which Policy fired and why", so the remaining gap is the Evaluation-terms breakdown. Either accept the split explicitly (state in 6.2 that v1 "explain" = FR-32 + FR-36) or pull FR-44's Policy half into E5. |
| Developer (v1, E5): "path highlight. The overlay is the debugger" | FR-41 Map highlights; section 6.2 "the v1 Overlay is the debugger" | carried | |
| Learner (v2, E8): "coach mode (bot advises with reasons, human plays) and autoexplore-with-brains" | FR-47; UJ-4 | carried | |
| Learner needs: "explanations in game vocabulary" | FR-32 "in Codex vocabulary"; UJ-4 example | carried | |
| Learner needs: "no cheating" | FR-11; non-goal 5.4 | carried | |
| Learner needs: "no desync when they take over" | FR-27 takeover test; FR-40; SM-6 | carried | |
| Learner success: "they watch a run and understand a decision they would not have made" | UJ-4 climax | carried | No v2 success metric in section 7; acceptable since E8 is out of MVP. |
| Community reader (v1, E3): "public seed sets including a never-run holdout" | FR-20 | carried | |
| Community reader: "methodology page" | FR-21 consequence "published on the methodology page"; UJ-3 | carried (implicitly) | No FR owns the methodology page itself; it is referenced from FR-21 and UJ-3 only. Consider naming it under FR-25 or FR-51. |
| Community reader: "published negatives" | **absent** | absent | FR-25 publishes "a Results page for a comparison" but does not require that rejected or undecided comparisons are published. The brief's point is that negatives are published, not just wins. One clause on FR-25 ("including rejected and undecided outcomes") closes it. |
| Community reader: "replayable logs" | FR-23, FR-24 | carried | |
| Community reader: "a fairness badge" | PRD addendum Results page fields "fairness suite status, oracle mode off"; SM-C5 | carried (weakened form) | Status field on the Results page, not a visible badge on the docs site. Minor. |
| Community reader success: "a skeptic can reproduce a number" | UJ-3; SM-5 | carried | |
| Spectator and researcher (later): "big-text spectator layout" | **absent** | absent | Not mentioned even as parked. Low cost to add to the ideas ledger reference in section 6.2 or the non-goals. |
| Spectator and researcher (later): "run-log exports" | NFR-9 (plain-text JSONL); FR-23 | carried (implicitly) | The Run log already is the export. |
| Spectator and researcher (later): "brain plug-in without touching the overlay" | Section 4.5 "Identical code runs headless and in the Overlay"; FR-7 Brain depends only on `api`; Rig CLI `--brain <name>` | carried (implicitly) | Pluggability is implied by the module boundary and the CLI contract, but no FR states that a new Brain can be selected in the Overlay/launcher without Overlay changes. Section 9 could list "Brain interface" as a public surface. |

## 5. Roadmap items outside the brief's scope

| Input row | PRD location | Status | Note |
|---|---|---|---|
| "SPSA tuning of eval weights on the rig — Promoted to E6+" | FR-33 "Promoted from the ideas ledger to E6+" | carried | |
| "Playbooks as data — Promoted to E7" | FR-35 "Deferred to E7" | carried | |
| "Death replay gallery — Promoted to E3" | FR-26 "Promoted from the ideas ledger to E3" | carried | |
| "Public leaderboard — Parked" | Non-goal 5.10 "will not run leaderboards ... in v1" | carried | |
| "Bot-vs-seed challenge issues — Parked" | Non-goal 5.10 "community challenges" | carried | UJ-3's "open an issue with a seed the bot loses on" is ordinary bug reporting, not the parked feature. |
| "Decision narration as text (screen-reader-friendly view of a run) — Parked" | **absent** | absent | NFR-9 (plain-text logs) is adjacent but not the same. Not mentioned as parked. |
| "GitHub Actions matrix as rig workers — Parked" | FR-25 "a nightly job runs the `standard` set against the Baseline"; NFR-2 "a nightly job replays a random published Run ... across Windows and Linux"; NFR-7 "CI on Linux, nightly on Windows"; section 11 "GitHub Actions for CI, nightly Rig, and Pages" | **contradicted** (silently) | The brief parks CI-hosted Rig workers; section 11 names GitHub Actions as the host of the "nightly Rig", and FR-25's nightly `standard` run is Rig work. Either the nightly runs on the laptop (say so, and drop "nightly Rig" from section 11) or the PRD is un-parking the item and should say so with the cost (section 10 "GitHub Actions minutes"). |
| "Headroom metric via an oracle-assisted upper bound (measurement only) — Parked" | **absent** | absent | Not mentioned. FR-11 and non-goal 5.4 do not forbid an oracle-assisted measurement Run (it would not be "ranked"), so the door is open but unstated. |
| "Strength per think budget — Parked" | Open question 5 "Which think budgets, if any, does v1 publish?" | carried (as open question) | Parked status not recorded. Minor. |
| "A post per epic on the docs site — Parked" | **absent** | absent | Minor. |
| "A `docs/rules` citation checker — Parked" | FR-17 "a citation checker reports citations that no longer resolve" (E2, in MVP scope per section 6.1) | **contradicted** (silently un-parked) | The brief parks the checker; the PRD makes it part of FR-17 in E2 without noting the promotion (FR-26 and FR-33 do note theirs). Either mark it "Promoted from the ideas ledger to E2" or split it out of FR-17 and park it. |

## 6. Open questions for the PRD

| Input item (quoted) | PRD location | Status |
|---|---|---|
| "Which think budgets to publish, and whether v1 has any budget before E6" | Open question 5 | carried |
| "The human win-rate source for 'beats the median human' calibration" | Open question 6 | carried |
| "Whether coach mode belongs in E8 or becomes its own epic" | Open question 7; FR-47 assumption; assumptions index | carried |
| "Fishtest's current default bounds as text ... for the E3 ADR's starting values" | Open question 9 | carried |
| "The target win rate that turns the headline ... into a sustained number, and over which seed set and class" | Open question 8 "and over which class"; SM-2 fixes `standard` | carried (seed set resolved by SM-2) |

## 7. Sizing data

| Input item | PRD location | Status |
|---|---|---|
| "Market sizing is out of scope ...; the relevant 'size' is the SPD community (about 6,500 repository stars ...)" | Section 12 Why now (no sizing figures) | carried (correctly omitted; the brief says sizing is out of scope) |

## 8. Cross-check of the PRD addendum's own claims

- PRD addendum "Inputs and where they landed" says the brief addendum's personas landed in "Sections 8, 10, 11"; they actually land in section 2 (2.1 jobs, 2.3 journeys). Cosmetic; fix the row.
- PRD addendum "The brief's addendum already holds rejected alternatives, technical constraints, and personas; this file does not repeat them" is consistent with what was found: nothing is duplicated, and the ADR table points back to the brief addendum's Statistics section.

## 9. Summary of findings

**Contradictions**
1. NFR-3 introduces a throughput floor (1,000 turns per second) that the brief explicitly declines ("a target to test, not a requirement").
2. The `docs/rules` citation checker is parked in the brief but is in-scope in FR-17 (E2) with no promotion note.
3. The brief parks "GitHub Actions matrix as rig workers"; section 11 names GitHub Actions as host of the "nightly Rig" and FR-25 / NFR-2 / NFR-7 add nightly Rig work with no stated host.

**Weakened**
4. Developer persona's v1 "explain" control is reduced to one-line reasons (FR-32/FR-38); the Explain view (FR-44) is deferred to E8.
5. "Keep item identification in belief reasoning, not search": the prohibition half is not stated on FR-34.
6. "Priorities as data": FR-28 does not say the Arbitration order is data.
7. Classloader-isolation spike carried without its exit criterion (measured turns per second).
8. "Fairness badge" becomes a Results-page status field.

**Absent**
9. "each instance has its own preferences directory": no on-disk isolation consequence on FR-19 / FR-1.
10. "recalibrate per upstream tag": missing from FR-50 / NFR-5.
11. "published negatives": FR-25 does not require rejected/undecided comparisons to be published.
12. Sprite-wait bypass / whether the Overlay can run faster than animation speed: unstated; FR-39 Speed is a cap only.
13. "annual review of the learned frontier": not in section 4.8.
14. Parked items with no mention: spectator layout, decision narration as text, headroom via oracle-assisted upper bound, post per epic.

**Correctly left to architecture (not gaps)**
Headless-scene mechanics (Scene/Groups, no-op GL, Pixmap, `updatesPerSecond`, fast-forward loop, static-helper hooks), GSPRT parameters, e-process alternative, JNI shared-loader rule, Overlay UI building blocks, ArchUnit package patterns, search design choice: all pointed to from the PRD addendum's ADR table with the research recommendation as starting position.
