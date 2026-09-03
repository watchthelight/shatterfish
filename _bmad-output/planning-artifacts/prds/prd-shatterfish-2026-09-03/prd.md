---
title: 'PRD: Shatterfish'
status: final
created: '2026-09-03'
updated: '2026-09-03'
inputs:
  - _bmad-output/planning-artifacts/briefs/brief-shatterfish-2026-09-03/brief.md
  - _bmad-output/planning-artifacts/briefs/brief-shatterfish-2026-09-03/addendum.md
  - _bmad-output/planning-artifacts/research/technical-shatterfish-engine-foundations-2026-09-03/research.md
  - docs/BOOTSTRAP-PROMPT.md
addendum: addendum.md
---

# PRD: Shatterfish

## 0. Document purpose

- **Who it is for:** the product owner, the architecture workflow, the UX workflow (the Overlay has a UI), and the epics-and-stories workflow.
- **What it builds on, without repeating it:** the product brief and its addendum (audience, success ladder, non-functional requirement list, technical constraints), the technical research report (feasibility and recommendations), and the bootstrap prompt (non-negotiables, module guardrails, Overlay description, program map).
- **Where things are:** vocabulary in the Glossary (section 3); features with globally numbered functional requirements (FRs) in section 4; cross-cutting requirements in sections 8 to 11; open questions in section 13; the assumptions index in section 14; the sibling addendum for the epic map, deferred decisions, and surface sketches.
- **Conventions:** requirements state capabilities, and how they are built belongs to the architecture and the addendum; the eight non-negotiables in the bootstrap prompt's section 1 bind every requirement and are not restated; where this PRD deliberately changes a bootstrap "done when", the change is called out inline as a `[NOTE FOR PM]`.

## 1. Vision

Shatterfish is an open-source engine for Shattered Pixel Dungeon (SPD) in the spirit of Stockfish. It drives SPD's own code headlessly and reproducibly, plays it with a hand-built symbolic bot, measures every change to that bot with a Fishtest-style statistical Rig over thousands of seeded Runs, and runs the bot inside the real desktop game where a person can watch it think, pause it, step it, and take over. It is a permanent downstream fork of SPD, pinned to a release tag, unofficial and unaffiliated.

Two disciplines define it, both enforced by architecture. Information parity: the bot may use only what a human at the same screen could see, guaranteed by the Observer, the one class that is the only door from game state to the bot, a build that fails if the Brain imports game code, and leak tests on every change. Measurement: nothing about the bot is believed until the Rig says so, with public Seed sets, pre-registered Sequential tests, and verifiable Run logs. Chess search does not transfer to a stochastic, partially observed, single-player game; Stockfish's infrastructure and testing culture do, and the symbolic-bot tradition from NetHack supplies the play.

The goal ladder has two rungs at the top: first beat the final boss, then score as high as possible. The headline is a verified win: the Brain kills Yog-Dzhewa on a public seed, reproducible from its Run log. After that, the highest verified in-game Score becomes the canonical number, with per-class win rate as its check. Everything below (a measured Harness, a published Baseline, a Goo kill, a human taking over mid-fight without desync) is how the program knows it is getting there.

## 2. Target user

### 2.1 Jobs to be done

- **Functional (the developer, v1):** change the Brain, run the Rig, and learn from a small Seed set whether the change helped; watch a Run in the real game and see why the bot did what it did; trust that no result was helped by hidden information.
- **Functional (the community reader):** reproduce a published number from the Seed set, commit, and command it names; Replay a Run from its log.
- **Functional (the learner, v2):** be advised, in the game's own words, what the bot would do and why, while playing.
- **Emotional (the builder):** see the bot reach the next boss; have a public record that the numbers are honest.
- **Contextual:** all of it on one laptop, offline, in one JVM, against a game whose author accepts no contributions.

### 2.2 Non-users (v1)

- Players wanting a cheat, an auto-win, or hidden-information reveals: Oracle mode is a debugging tool, flagged and excluded from ranked Runs.
- Mobile players: Android and iOS are out of scope.
- Researchers wanting a gym-style environment or a learned agent: not v1; learned components are optional and late.
- Upstream: Shatterfish proposes nothing to the SPD project.

### 2.3 Key user journeys

- **UJ-1. The developer changes the Brain and learns from the `smoke` Seed set whether it helped.** The developer edits a scripted Policy on a branch. They run the Rig on the `smoke` Seed set against the current Baseline: the Harness runs both Brains headlessly in parallel processes, the Rig writes Run logs and a summary, and the Sequential test reports accept, reject, or undecided with the log-likelihood trace. Climax: the summary shows the Composite outcome moved and the test accepted. Resolution: the Results file is committed to the branch, the pull request carries the numbers, CI re-runs the fairness suite, and the product owner reviews. Edge case: the test is undecided at the smoke budget; the developer runs the `standard` set or leaves the change unmerged.
- **UJ-2. Bash watches the bot in the real game and takes over mid-fight.** Bash launches the desktop game through the Shatterfish launcher. The Panel docks beside the game in the game's own style: Mode RUNNING, the Goal "Explore: guaranteed strength potion still on this floor", the chosen Action with three alternatives and one-line reasons, the Belief summary, Safety flags, a scrolling decision log, the planned path drawn on the map. A gnoll appears; Bash presses Pause, reads the alternatives, presses Take over, plays two turns with the normal controls, presses Hand back. Climax: the Brain re-plans from the current state and continues without a desync or a stale plan. Resolution: the Run log records the human turns; the Replay reproduces the Run exactly. Edge case: Bash takes over while the bot's Action is mid-animation; the Panel shows PAUSED only once the hero is waiting for input.
- **UJ-3. A skeptic reproduces a number.** A community member reads a Results page: tag, Seed set, commit, both Brains, Hypothesis ID, Sequential-test outcome, and the command. They clone the tag, run the command, and get the same per-seed results and the same Hash chain. Climax: the numbers match byte for byte. Resolution: they open an issue with a seed the bot loses on. Edge case: they run on a different operating system; the nightly cross-platform check has already shown the hashes agree, and the methodology page says what to do if they do not.
- **UJ-4. A learner plays with the coach (v2).** A player who dies on floor 5 every Run turns on coach mode. They play; before each move the Panel says what the bot would do and why, in the game's vocabulary ("Read the unknown scroll here: you are by a door and water, so the worst case is survivable"). Climax: they understand a decision they would not have made. Resolution: they hand a boring corridor to autoexplore and take back control at the next enemy. Lighter scope; realized in E8.
- **UJ-5. The developer upgrades to the next Upstream tag.** Upstream releases 4.0 stable. The upgrade skill fetches tags and measures the change; the product owner approves the target; the merge lands on a branch; every Hook is re-verified; the Codex regenerates and its diff is summarized; the fairness and determinism tests run; the Rig re-baselines and the Sequential-test bounds are recalibrated; the docs update in the same pull request. Climax: the mechanics diff is readable and the Baseline is republished. Resolution: the pinned tag changes in one place.

## 3. Glossary

**Engine**

- **Observation** — Immutable, serializable, content-hashed snapshot of everything a human at the screen could know at one game turn: the known map as drawn, visible actors as displayed, hero stats and buffs, inventory with identification status exactly as the UI shows it, equipment and quickslots, journal state, recent log lines, depth, turn. Produced only by the Observer. Lives in the `api` module.
- **Action** — One thing the bot may do: move to, attack, use, throw, zap, read, drink, equip, drop at a target, rest, search, descend, ascend, talent or ability use, wait. Executed only by the ActionExecutor through the game's own code paths.
- **Decision** — The Brain's output for one turn: the chosen Action, the top alternatives with scores and one-line reasons, the current Goal, Safety flags, and the Policy that fired.
- **Goal** — The Brain's current strategic intent in plain words (for example "Explore: guaranteed strength potion still on this floor").
- **Safety flag** — A short statement about the risk of an Action in the current Observation (for example "by water: fireblast-safe").
- **Observer** — The single class in the Harness that reads game state and produces an Observation. The only door from game state to the Brain.
- **ActionExecutor** — The single class in the Harness that applies an Action to the hero.
- **Harness** — The module that boots SPD headlessly, seeds it, and exposes Observer and ActionExecutor. Contains the two Drivers.
- **Headless scene** — The Harness-owned scene that lets SPD's turn resolution run without a window: it supplies what sprites attach to, a no-op graphics layer, and a fast-forwarded update loop.
- **Driver** — The component that owns the game loop for the bot. The **HeadlessDriver** runs the Headless scene; the **EmbeddedDriver** runs inside the real desktop game.

**Brain**

- **Brain** — The module that turns an Observation into a Decision. Depends on the `api` module only. Comprises Beliefs, Policies, Playbooks, Search, and the Evaluation.
- **Belief** — The Brain's model of what it cannot see: per-unidentified-item candidate sets with probabilities, floor facts, chapter counters, memory of monsters seen and lost. Updated from every Observation regardless of who acted.
- **Belief summary** — The Panel's compact rendering of Beliefs: unknown items with their top candidates and probabilities, floor facts, chapter counters. The full **Beliefs view** (v2) shows every candidate and the evidence behind it.
- **Policy** — A scripted behavior with an entry predicate (for example explore, fight in corridors, eat, descend). The **Arbitration** is the ordered list of Policies the Brain consults each turn.
- **Playbook** — Strategic knowledge as data: per-class, per-boss, item-identification, and upgrade-allocation rules editable without code changes.
- **Search** — Lookahead over Actions that sees only the Observation and Beliefs; hidden state is sampled from Beliefs (redetermination) before any simulation. Rollouts on the raw saved game are forbidden.
- **Evaluation** — The scoring function over Observations that Policies and Search use; hand-tuned from Codex tables (including the hit-chance formula the Codex exports from `Char.hit`), with weights held as data.
- **safeTest** — The Belief computation that scores the worst case of using an unidentified item at a cell, over its candidate identities and the surrounding terrain and enemies.

**Knowledge**

- **Codex** — Tables generated from the pinned upstream code: every mob, item, generator weight, mob rotation, trap, recipe, changelog entry, player-facing text, and asset path, with `path:line` citations. The Brain's only source of general game knowledge.
- **Lore** — Community knowledge admitted through the lore pipeline: one file per claim with provenance, variant, and Tier.
- **Tier** — Verification level of a Lore claim or Rule: 1 code confirms, 2 harness confirms, 3 hypothesis for the Rig, F false or obsolete for a tag.
- **Rule** — A claim about a mechanic that Shatterfish relies on, with a `path:line` citation at the Upstream tag and a link to the test that checks it.

**Rig**

- **Rig** — The module and tooling that runs many seeded games in parallel, compares two Brains with a Sequential test, writes Run logs, Replays them, and publishes Results.
- **Run** — One game from a fresh start to death, victory, or a turn cap, fully determined by (Upstream tag, hero class, challenge flags, seed, Action list).
- **Score** — The in-game score SPD computes for a Run, as the game reports it.
- **Seed set** — A named, versioned, committed list of seeds: `smoke`, `standard`, `holdout` (never run during development), `bosses`.
- **Run log** — The JSONL record of a Run: per turn the Observation hash, the Decision, the Action taken and by whom (bot or human), chained so that any tampering is detectable.
- **Hash chain** — The per-turn Observation hash that includes the previous turn's hash.
- **Composite outcome** — The per-Run result the Sequential test compares, ordered: win, then Score, then depth reached, then turns survived. A difference at an earlier position always dominates a difference at a later one.
- **Sequential test** — The Fishtest-style Generalized SPRT (GSPRT) that decides accept, reject, or undecided over paired-seed differences of the Composite outcome, under pre-registered bounds.
- **Hypothesis ID** — The identifier of a Registration.
- **Registration** — A record committed to the repository before a comparison starts, fixing its bounds, Seed set version, and both Brains' commits; the Hypothesis ID is derived from it.
- **Baseline** — The last published Brain a new Brain is compared against.
- **Results** — A published page under `docs/results/` for one Rig comparison, with everything needed to reproduce it.
- **Oracle mode** — A debugging and labeling mode in which true hidden state is exposed. Off by default, enabled only by an explicit flag, visibly marked, never allowed in a ranked Rig Run.

**Overlay and upstream**

- **Overlay** — The Panel, its controls, and the map highlights, running inside the real desktop game through the EmbeddedDriver.
- **Panel** — The docked, native-style UI element of the Overlay.
- **Mode** — The Overlay state: RUNNING, PAUSED, or HUMAN.
- **Pause-on condition** — A rule that switches the Overlay to PAUSED when it becomes true (for example before any item use, when HP drops below a threshold).
- **Replay** — Loading a Run log and stepping through its Decisions, verifying the Hash chain against a fresh Run.
- **Hook** — A minimal, justified, labeled edit to an upstream file, listed in the hook registry.
- **Upstream tag** — The SPD release tag Shatterfish is pinned to (v3.3.8 at writing).
- **Epic / Story** — Units of the program plan (E0 to E9) and of work; a Story is small enough for one session.

## 4. Features

Each feature has a description that names the user journeys it realizes, then its FRs. An FR's capability sentence is its acceptance test unless a "Consequences (testable)" list follows, in which case the list is. A trailing "Provenance:" line records why an FR sits where it does when that placement changed during planning.

### 4.1 Headless engine

**Description:** The Harness boots SPD's `core` on libGDX's headless backend inside the Headless scene, seeds every random source, and drives a Run turn by turn: the Observer produces an Observation whenever the hero waits for input, a caller supplies an Action, the ActionExecutor applies it, and the loop continues until the Run ends. Everything the Rig, the Brain, and the fairness tests do rests on this. Realizes UJ-1, UJ-3.

**Functional requirements:**

#### FR-1: Boot a Run headlessly
The Harness can start a new game of a given hero class, challenge flags, and seed with no window, no OpenGL context, and no Android SDK, and run it to completion.

**Consequences (testable):**
- A Run completes on a machine with no display and no graphics driver.
- Turn resolution paths that depend on sprite animation (attack, zap, throw, use) complete without a real render loop.
- Boot succeeds with the desktop natives shipped and fails with a message that names the missing natives if they are absent.

#### FR-2: Determinism from (tag, class, challenges, seed, Action list)
The Harness can reproduce a Run exactly: two Runs with the same Upstream tag, hero class, challenge flags, seed, and Action list produce identical Observation hashes at every turn.

**Consequences (testable):**
- Every random source the game uses, including the general-purpose generator used for combat rolls, is seeded by the Harness.
- Wall-clock time, thread scheduling, and hash-map iteration order do not influence any Observation.
- One process hosts one Run at a time; each Run has its own preferences and save directory. If the E1 isolation spike lets a process host several Runs, determinism must hold per isolated instance and the same tests apply.
- The determinism test runs in CI on every pull request.

#### FR-3: Observer produces the Observation
The Observer can build an Observation from what the game computes for drawing and from the game log and journal, never from raw model fields that a player cannot see.

**Consequences (testable):**
- An unidentified item appears in the Observation under its unidentified name only.
- A mob outside the hero's field of view, an undiscovered trap, and an unfound secret door do not appear.
- Mind vision, magic mapping, blindness, and darkness change the Observation exactly as they change the screen.
- The Observation is immutable, serializable, and content-hashed; equal Observations have equal hashes.

#### FR-4: ActionExecutor applies an Action
The ActionExecutor can apply any Action through the same code paths the game's UI uses, on the thread the game requires.

**Consequences (testable):**
- Every Action in the Glossary maps to a game input the UI could produce.
- An Action that is invalid in the current Observation is rejected before touching game state, with a reason.
- The set of valid Actions for an Observation is available to the caller.

#### FR-5: Random-action agent and throughput measurement
The Harness ships a random valid-action agent and a benchmark that reports turns per second and Runs per minute for one process, with and without a Brain attached, on a described machine.

**Consequences (testable):**
- 1,000 seeded random-action Warrior Runs complete unattended.
- The benchmark output is a committed Results page with the machine described and the median Run length in turns recorded.

`[NOTE FOR PM]` The bootstrap prompt's E1 done-when, "1,000 runs in seconds", is replaced: E1 publishes measured turns per second and the tactics' leaf correlation and disambiguation (open question 3); paired-seed correlation needs two Brains and is measured in E3 (SM-5). No throughput number is promised before the measurement (section 8, NFR-3).

#### FR-6: Snapshot, restore, and redetermination
The Harness can snapshot a Run's game state, restore it, and produce a redetermined copy in which every hidden element is re-sampled from a supplied Belief. Deferred to E6; the interface is reserved in E1.

**Consequences (testable):**
- Restoring a snapshot and replaying the same Actions yields the same Observations as the original.
- A redetermined copy differs from the original only in hidden elements; the differential test proves the Observation is unchanged.
- No Search may run on a snapshot that still holds hidden state.

**Feature-specific NFRs:** see section 8 (throughput, reproducibility).

### 4.2 Fairness enforcement

**Description:** Information parity is enforced by build structure and tests, not conventions. This feature is the set of guarantees and their tests; it is what makes every number Shatterfish publishes trustworthy. Realizes UJ-3.

#### FR-7: The Brain cannot depend on game code
The build fails if the Brain module depends, directly or transitively, on any game module (`core`, `SPD-classes`, `services`, `desktop`) or on any class in the packages `com.shatteredpixel.*` or `com.watabou.*`.

**Consequences (testable):**
- No dependency edge from the Brain to game modules is declared; the only permitted edges are those in section 10.
- A resolution-time check fails configuration if a game module reaches the Brain's classpaths.
- An ArchUnit rule fails if any Brain class references those packages by bytecode.

#### FR-8: Leak tests
Every change to the Observer ships with tests proving that hidden state does not appear in the serialized Observation.

**Consequences (testable):**
- An unidentified scroll, a mob behind a wall, a secret door, a hidden trap, an invisible enemy, the seed, and the RNG state are each absent from the Observation in a constructed world.
- The leak test suite runs in CI on every pull request.

#### FR-9: Differential test
Two worlds identical to the player but different in hidden state serialize to byte-identical Observations.

**Consequences (testable):**
- The test constructs at least: different unidentified-item identities, different unseen mob positions, different RNG state.
- A Brain given both worlds produces identical Decisions until the Observations diverge (the behavioral form; from E4).

#### FR-10: Toggle tests
The same world with and without mind vision, blindness, and magic mapping produces exactly the expected differences in the Observation.

#### FR-11: Oracle mode gating
Oracle mode exists only behind an explicit launch flag, marks every surface it touches, and cannot be enabled in a ranked Rig Run. Its two uses are debugging and producing training labels for the optional E9.

**Consequences (testable):**
- Without the flag, no code path can read true item identities or unseen positions into the Brain.
- With the flag in the Overlay, the Panel draws a red border and an "ORACLE" label.
- The Rig refuses to start a ranked comparison when the flag is present, and every Results file records that Oracle mode was off.

#### FR-12: Thread confinement
Game objects are touched only on the threads that own them; the Brain never holds a game object.

**Consequences (testable):**
- Observer and ActionExecutor assert the owning thread and fail fast otherwise.
- The Brain runs on a worker thread with only the immutable Observation.

#### FR-13: Search leak test
When Search exists, replacing the true hidden state with random alternates produces identical Decisions. Deferred to E6.

### 4.3 Codex and knowledge

**Description:** The Codex is the Brain's general game knowledge, generated from the pinned code so that it can never drift from the tag it describes. Rules and Lore are how the docs and the Brain cite mechanics. Realizes UJ-3, UJ-5.

#### FR-14: Generate the Codex from the pinned tag
One build task regenerates the Codex into a tag-named folder and the generated documentation pages.

**Consequences (testable):**
- The Codex covers every mob, item, generator weight and guarantee, mob rotation per depth, trap, recipe, changelog entry, player-facing text, asset path, and the hit-chance formula.
- The generation is parameterized by depth and challenge flags where the game's tables are.
- Every generated entry carries a `path:line` citation into the tag.

#### FR-15: Codex drift check
CI regenerates the Codex and fails if the committed output differs.

#### FR-16: Vocabulary diff
The Codex includes a diff between vanilla Pixel Dungeon and SPD names for items, mobs, and mechanics, used by the lore pipeline's variant classifier.

#### FR-17: Rules with citations and the codebase map
Every mechanics claim the Brain or the docs rely on is a Rule with a `path:line` citation at the Upstream tag, a Tier, and a link to the test that checks it. The file-to-mechanic codebase map is maintained alongside. A citation checker reports citations that no longer resolve.

Provenance: the citation checker is promoted from the ideas ledger into E2 because the upgrade procedure (FR-50) depends on it.

#### FR-18: Lore pipeline
Community knowledge enters as one file per claim with provenance frontmatter (fields in the addendum, Lore claim frontmatter). The product owner's research skill is the intake path. A classifier assigns the variant from the vocabulary diff and the changelog dates. Every Brain heuristic links to a Lore entry or a Rule (a Rule is the stronger form) with a Tier. Deferred to E7.

### 4.4 Rig

**Description:** The Rig turns the Harness into a measurement instrument: many Runs in parallel over versioned Seed sets, a Sequential test between two Brains, Run logs that anyone can Replay, and Results pages that carry everything needed to reproduce them, whatever the outcome. Realizes UJ-1, UJ-3.

#### FR-19: Parallel runner
The Rig can run a Seed set for one or two Brains across parallel processes and collect per-Run results.

**Consequences (testable):**
- Per-Run results include seed, Brain, outcome (win or cause of death), Score, depth reached, turns, boss kills, and the final Observation hash.
- One process per Run by default, each with isolated on-disk state; the runner reports throughput and the number of processes used.

#### FR-20: Seed sets
Seed sets are committed files with initial sizes `smoke` 25, `standard` 500, `holdout` 500, `bosses` 100 (revisable by ADR once throughput is measured). `holdout` is never run during development, may be used only to publish a release-level number, at most once per Brain version, and every use is recorded in the Results.

**Consequences (testable):**
- A Results file names the Seed set and its version.
- The Rig refuses a development comparison on `holdout`.

#### FR-21: Sequential test
The Rig compares two Brains with a Sequential test over paired-seed differences of the Composite outcome, with bounds stated in standardized units, and reports accept, reject, or undecided with the log-likelihood trace.

**Consequences (testable):**
- A deliberately worse Brain is rejected on the `standard` set.
- The test does not stop before a burn-in; realized error rates are validated by simulation on the Rig's own outcome distribution and published on the methodology page.
- The measured paired-seed correlation is reported with every comparison.
- Because the Composite outcome ranks a win above any Score and a Score above any depth, a Brain that dives deeper but wins or scores less cannot be accepted (see SM-C2).

#### FR-22: Pre-registration
Every comparison has a Registration committed before its first Run; the Hypothesis ID is derived from the Registration; the Rig embeds the Registration's commit in the Results and refuses a comparison whose Registration commit postdates its first Run.

#### FR-23: Run logs with a Hash chain
Every Run writes a JSONL Run log: per turn the Observation hash chained to the previous, the Decision, the Action, and the actor (bot or human).

#### FR-24: Replay with verification
The Rig can Replay a Run log and verify that a fresh Run reproduces every Observation hash; a mismatch is reported with the first divergent turn.

#### FR-25: Results publication, including negatives
Every registered comparison publishes a Results page, whatever its outcome (accept, reject, undecided). The page carries:

- Upstream tag and Shatterfish commit;
- Seed set name and version;
- both Brains (name, commit, configuration);
- Hypothesis ID with its Registration commit, bounds, and units;
- the outcome with the log-likelihood trace;
- per-Run distributions: win, Score, depth, turns, cause of death, boss kills;
- the measured paired-seed correlation;
- fairness suite status and confirmation that Oracle mode was off;
- links to the Run logs;
- the command that reproduces it.

Results land through a pull request. A nightly job runs the `smoke` Seed set against the Baseline on GitHub Actions and updates one results pull request; the `standard` Seed set runs on the developer's machine until an ADR decides otherwise (open question 11).

#### FR-26: Death replay gallery
The Rig publishes, per comparison, the Runs with the largest Evaluation drop in their final 10 turns and the shallowest deaths, with their Decision logs, as a bug source.

Provenance: promoted from the ideas ledger to E3.

### 4.5 Brain

**Description:** The Brain is observation-driven: every turn it takes the current Observation, updates its Beliefs, consults its Arbitration of Policies, optionally runs Search, and emits a Decision. It never assumes it made the previous move, which is what lets a human take over. Identical code runs headless and in the Overlay. Realizes UJ-1, UJ-2, UJ-4.

#### FR-27: Re-plan every turn from the Observation
The Brain produces a Decision from the current Observation and its Beliefs alone, with no dependence on its previous Decision having been executed.

**Consequences (testable):**
- Interleaving arbitrary human Actions between bot turns never causes an error or a stale plan (the takeover test).

#### FR-28: Arbitration of Policies
The Brain's behavior is an ordered list of Policies with entry predicates that evaluate without side effects and without simulation; higher-priority Policies interrupt lower ones; the active Policy and its reason are part of the Decision.

#### FR-29: Beliefs
The Brain maintains Beliefs updated from every Observation: candidate identities with probabilities for each unidentified item (weighted from Codex spawn weights and identification history), floor facts, chapter counters for guaranteed drops, and memory of monsters seen and lost.

Provenance: unseen-monster memory is in v1 because the strongest published bot lacked it.

**Consequences (testable):**
- Belief update is a pure function of (previous Beliefs, Observation) with its own leak tests.
- Identifying an item type collapses its candidates everywhere.

#### FR-30: safeTest
The Brain scores the worst case of using an unidentified item at a cell over its candidate identities, using visible terrain and enemies, and uses the score to decide when and where to test unknown items.

#### FR-31: Scripted baseline Policies
The v1 Brain includes Policies for explore, fight in corridors, eat, and descend, sufficient to kill Goo on at least 75% of the `standard` Seed set with a lower confidence bound of at least 70%.

#### FR-32: Decision output
Every Decision contains the chosen Action, at least the top three alternatives with scores and one-line reasons, the Goal, Safety flags, and the Policy that fired, in Codex vocabulary.

#### FR-33: Evaluation
The Evaluation is a hand-tuned weighted function over Observation features derived from Codex tables, including the Codex-exported hit-chance formula; its weights are data so the Rig can tune them.

Provenance: SPSA tuning is promoted from the ideas ledger to E6+.

#### FR-34: Tactical Search
When enemies are visible, the Brain can run a depth-limited expectimax Search of two to four hero turns that sees only the Observation and Beliefs. The Search's model (abstract or engine rollouts on redetermined state) is decided by ADR on the measured properties of open question 3 (addendum, mechanism decisions). Deferred to E6.

**Consequences (testable):**
- FR-13 passes for the chosen design; rollouts on the raw saved game are impossible by construction (FR-6).
- Search is budgeted to visible-enemy turns and boss floors; quiet turns use Policies.

#### FR-35: Playbooks as data
Per-class, per-boss, item-identification, and upgrade-allocation strategy lives in versioned data files the Rig tests; a Playbook change is a pull request with Results. Deferred to E7.

#### FR-36: Strategy log
The Brain records, per turn, which Policy or Playbook fired and why, as a Decision field that the Panel shows and the Run log stores.

### 4.6 Overlay

**Description:** The Overlay runs the Brain inside the real desktop game through the EmbeddedDriver. The Panel is native: the game's own frames, font, buttons, and sizes. Realizes UJ-2, UJ-4.

#### FR-37: EmbeddedDriver and launcher
A launcher starts the desktop game with the Overlay attached; the EmbeddedDriver observes every hero turn regardless of who acted and applies the Brain's Actions through the ActionExecutor. Saving and resuming the game inside a Run continues the same Run log with the boundary recorded (Replay across a resume is verified from E8; see open question 10).

#### FR-38: Native Panel
The Panel is docked beside the game, built from the game's own UI toolkit, and respects the game's interface-size setting. It shows:

- Mode, speed, turn, and depth;
- the Goal;
- the chosen Action with the top three alternatives, scores, and reasons;
- the Belief summary;
- Safety flags;
- a scrolling decision log.

In the full desktop layout the Panel occupies the free column between the menu pane and the inventory pane; the UX workflow decides placement at smaller sizes.

**Consequences (testable):**
- No Swing, JavaFX, ImGui, or web view is on the classpath.
- Every Panel write happens on the render thread; the Brain never touches a Panel object.

#### FR-39: Controls
Pause, Resume, Step (one Action), Run N, Speed (a turns-per-second cap), Explain (expand the current Decision to its Policy, alternatives, and Safety flags), Take over, Hand back.

**Consequences (testable):**
- Mode changes only when the hero is waiting for input.
- Speed caps bot turns without affecting the game's animation speed; the animation speed is the ceiling unless a documented Hook bypasses sprite waits.

#### FR-40: Interjection semantics
In PAUSED or HUMAN Mode the human plays with the normal controls; the Brain keeps observing and updating Beliefs; on Resume it re-plans from the current Observation; human Actions are recorded in the Run log so the Replay stays exact.

#### FR-41: Map highlights
The Overlay draws the planned path, the target, and the considered cells on the map, and clears them when the plan changes.

#### FR-42: Hotkeys
Overlay controls are bindable through the game's own key-binding system and appear in its settings screen.

Provenance: moved from Overlay v2 to v1 because the research verified the binding Hook is small; if the Hook is not small, v1 ships buttons only and hotkeys return to E8.

#### FR-43: Oracle overlay marking
When launched with the Oracle flag, the Overlay draws a red border and an "ORACLE" label and may show true identities and unseen enemies; nothing else changes (see FR-11).

#### FR-44: Explain view (v2)
The Panel can expand the current Decision further into the Evaluation terms and, when Search ran, the alternatives' expected outcomes. Deferred to E8.

#### FR-45: Pause-on conditions (v2)
The user can set conditions that switch the Overlay to PAUSED: before any item use, before testing an unknown item, before stairs, on boss floors, when HP drops below a threshold, when a new enemy appears. Deferred to E8.

#### FR-46: Replay scrubber and Beliefs view (v2)
The Overlay can load a Run log and scrub through its Decisions with the Panel showing each turn's Decision and the full Beliefs view. Deferred to E8.

#### FR-47: Coach mode and autoexplore (v2)
In coach mode the Brain advises before each human move without acting; autoexplore hands quiet stretches to the Brain and returns control when an enemy appears or a Pause-on condition fires. Deferred to E8 (the epics workflow may split coach mode into its own Epic; open question 7).

### 4.7 Upstream management

**Description:** Shatterfish is pinned to an Upstream tag and upgrades only by merging a newer tag through a documented procedure. Realizes UJ-5.

#### FR-48: Pinned tag and hook registry
The pinned Upstream tag, commit, and date are recorded in one place; every Hook is listed with its file, reason, guard, and the tag it was last verified at; every pull request that edits an upstream file carries the `touches-upstream` label; the number of Hooks is reported on the upstream page (SM-C4).

#### FR-49: Mobile modules opt-in
Desktop and headless builds never require the Android SDK or Xcode; the mobile modules build only when explicitly requested.

#### FR-50: Upgrade procedure and timing
An upgrade to a newer tag follows the documented, skill-automated procedure:

1. measure the change (files and commits between tags);
2. the product owner approves the target tag;
3. merge on a branch;
4. re-verify every Hook;
5. build;
6. regenerate the Codex and publish its mechanics diff;
7. run the fairness and determinism tests;
8. re-baseline the Rig and recalibrate the Sequential-test bounds;
9. update the docs in the same pull request.

No upgrade happens before the E3 Baseline is published; afterwards, one upgrade per stable tag, never inside an Epic. `upstream/master` is never merged. The procedure exists in E0; its first use is after E3.

### 4.8 Documentation and program hygiene

**Description:** Issues track state, Stories carry content, docs carry knowledge. The docs site builds strictly, publishes on merge, and mirrors the BMAD artifacts. Realizes UJ-3, UJ-5.

#### FR-51: Docs site
The documentation site builds with warnings as errors on every pull request and deploys from the main branch; BMAD artifacts, Results, Codex pages, ADRs, Rules, and the codebase map are all reachable from its navigation.

#### FR-52: Decisions and ideas are recorded
- Design decisions are ADRs with rejected alternatives and a pre-mortem.
- Ideas not being acted on go to the ideas ledger.
- Every pull request that changes behavior changes docs or says why not.
- No TODO exists in code without an issue number.
- Once a year the learned-agent frontier is reviewed and the review is recorded as an ADR or an ideas entry.

#### FR-53: Issues mirror Epics and Stories
Milestones mirror Epics and issues mirror Stories, idempotently, for the current and next Epic, using the label set `epic:E0` to `epic:E9`, `area:*`, `type:*`, `touches-upstream`, `fairness`, and `good-first-issue`; a Story's status change updates its issue.

## 5. Non-goals (explicit)

- Shatterfish will not reimplement any game rule in any language; the real engine is the only rules engine.
- It will not run the bot as a separate process or over a socket.
- It will not use any UI framework other than the game's own.
- It will not read hidden state into the Brain outside the flagged Oracle mode, and Oracle mode will never be available in a ranked Rig Run.
- It will not propose changes to upstream, file issues there, or merge `upstream/master`.
- It will not build Android or iOS targets.
- It will not become a general roguelike framework or support other games in v1.
- It will not chase turns per second as a product metric; speed serves measurement.
- It will not train a learned Evaluation before the hand-tuned one has a measured Baseline (E9 is optional and last).
- It will not run leaderboards, community challenges, or online services in v1.

## 6. MVP scope

### 6.1 In scope (v1 = E0 to E5)

- E0: FR-48 to FR-53 (repository, build without the Android SDK, module skeleton with the boundary rule, docs system, CI, project skills, planning artifacts, issue mirroring, the upgrade procedure).
- E1: FR-1 to FR-5, FR-7 to FR-12 (the Headless engine and the fairness suite), FR-6 interface reserved.
- E2: FR-14 to FR-17 (Codex, drift check, vocabulary diff, Rules with citations and the codebase map).
- E3: FR-19 to FR-26 (the Rig).
- E4: FR-27 to FR-33, FR-36 (the baseline Brain with its hand-tuned Evaluation).
- E5: FR-37 to FR-43 (Overlay v1).

### 6.2 Out of scope for MVP

- E6 tactical Search (FR-6 realization, FR-13, FR-34, SPSA tuning of FR-33): deferred until the Harness measures the properties the design depends on.
- E7 strategy and Lore (FR-18, FR-35): deferred until a baseline Brain exists to attach heuristics to.
- E8 Overlay v2 (FR-44 to FR-47): deferred; the v1 Overlay is the debugger, v2 is the coach. `[NOTE FOR PM]` coach mode is the feature most likely to bring a second user; revisit its position once E5 ships.
- E9 learned Evaluation: optional.
- Classloader isolation for many Runs per JVM: a spike in E1, not a requirement; processes are the default.

## 7. Success metrics

**Primary**
- **SM-1**: Headline win. The Brain kills Yog-Dzhewa and wins a Run on a public seed, reproducible from its Run log. Validates FR-2, FR-23, FR-24, FR-27 to FR-33.
- **SM-2**: Highest verified Score. The highest in-game Score achieved by a winning Run on a public seed, reproducible from its Run log, published per Brain version; the first target is set when SM-1 is first achieved. Validates FR-19 to FR-25, FR-33, FR-35.
- **SM-3**: E4 rung: Goo killed on at least 75% of the `standard` Seed set (lower confidence bound at least 70%), with the survival curve and boss-kill staircase published. Validates FR-25, FR-31.

**Secondary**
- **SM-4**: E1 rung: turns per second per process and the tactics' leaf correlation and disambiguation published; determinism test green. Validates FR-2, FR-5.
- **SM-5**: E3 rung: a deliberately worse Brain is rejected; the random-agent Baseline and the measured paired-seed correlation are published; a skeptic reproduces a Results page (UJ-3). Validates FR-21, FR-22, FR-25.
- **SM-6**: E5 rung: a full sewers Run watched in the Overlay with a mid-fight takeover and no desync; the Replay of that Run verifies. Validates FR-37 to FR-41.
- **SM-7**: Per-class win rate on the `standard` Seed set with a confidence interval, published per Brain version, as the check on SM-2. Validates FR-19 to FR-25.
- **SM-8**: Every merged Brain change carries Results; every pull request runs the fairness suite. Validates FR-7 to FR-12, FR-25.

**Counter-metrics (do not optimize)**
- **SM-C1**: Turns per second. Faster is not better once the Rig is fast enough; speed must never trade against determinism or parity. Counterbalances SM-4.
- **SM-C2**: Depth reached alone. A Brain that dives and dies deeper is worse; the Composite outcome ranks win and Score above depth, and the survival curve is the view. Counterbalances SM-3.
- **SM-C3**: Score at the cost of wins. A Brain version whose Score rises while its win rate (SM-7) falls is worse; SM-2 counts only winning Runs. Counterbalances SM-2.
- **SM-C4**: Win rate as an early gate. Near zero it carries no information; gating on it stalls the program. Counterbalances SM-7.
- **SM-C5**: Number of Hooks. Each is upgrade cost; fewer is better. Counterbalances FR-48.
- **SM-C6**: Results without fairness status. A number published without the fairness suite green is not a result. Counterbalances SM-8.

## 8. Cross-cutting non-functional requirements

- **NFR-1 Fairness.** The leak, differential, toggle, thread-confinement, boundary (ArchUnit and classpath), and determinism tests run on every pull request; the Search leak test joins them when Search exists; Oracle mode is off by default, flagged, and impossible in ranked Rig Runs. A pull request touching the Observer, the ActionExecutor, or the Brain gets an adversarial fairness review.
- **NFR-2 Reproducibility.** A Run is fully determined by (Upstream tag, hero class, challenge flags, seed, Action list); Run logs are hash-chained. A third party can verify any published Run from the Run log and the pinned tag by re-running it and comparing Observation hashes; a nightly job Replays a random published Run and compares Hash chains across Windows and Linux.
- **NFR-3 Headless throughput.** No turns-per-second figure is promised before it is measured (FR-5). The requirement is that the smoke loop of UJ-1 (two Brains over `smoke`, including the Sequential test's burn-in) fits within a working session on the development laptop. The Rig meets it by choosing the number of parallel processes and the Seed-set sizes (FR-20), not by assuming an engine rate. The measured rate, the median Run length, and the per-Decision cost of the Brain (the ceiling the research expects to matter) are published with the E1 benchmark and restated here by an ADR.
- **NFR-4 Overlay responsiveness.** Brain thinking never blocks the render thread; the game keeps its frame rate while the Brain thinks; Panel updates are posted to the render thread; a per-turn thinking budget is configurable and the Panel shows when it is exceeded.
- **NFR-5 Upstream upgrade.** Tag-only merges by the documented procedure; every Hook re-verified; Codex regenerated and its diff published; fairness and determinism tests, the Rig Baseline, and the Sequential-test bounds re-run and recalibrated; all in one pull request labeled `touches-upstream`; no upgrade before the E3 Baseline, none inside an Epic.
- **NFR-6 Documentation currency.** Docs and ADRs change in the same pull request as the code; generated files are never hand-edited; every mechanics claim cites `path:line`; the docs site builds strictly on every pull request.
- **NFR-7 Portability.** Windows and Linux are supported for the Harness and Rig (CI on Linux, nightly on Windows); macOS is best effort. The desktop game's own platform support bounds the Overlay.
- **NFR-8 Privacy and network.** Shatterfish makes no network calls at runtime and collects no telemetry; the Rig and Overlay work offline.
- **NFR-9 Observability.** Every Run log, Results page, and strategy log is plain text (JSONL or Markdown) that a person can read without tooling.

## 9. Public surfaces and versioning

Shatterfish exposes formats and interfaces that Results, Replays, Playbooks, and downstream tools depend on. Each carries a schema version; a breaking change bumps the version, and a Results page names the versions it was produced with.

- **Observation schema** (FR-3): versioned; changing it invalidates Replay across versions and requires a Baseline re-run.
- **Action set** (FR-4): additions are non-breaking; removals are breaking.
- **Decision and strategy log formats** (FR-32, FR-36): additive changes only within a version.
- **Run log format** (FR-23): JSONL, one record per turn, schema-versioned; the Replay tool refuses a log from an incompatible version with a clear message.
- **Seed set files** (FR-20): versioned by content; a Results page names the version.
- **Registration format** (FR-22): a committed record; its commit is the proof of pre-registration.
- **Rig command line** (FR-19, FR-21): the flags in the `rig` project skill are the contract (`--brain`, `--baseline`, `--seeds`, `--seed-start`, `--threads`, `--out`); Oracle mode cannot be enabled through it.
- **Codex JSON** (FR-14): tag-named folders; consumers read the tag they need.
- **Playbook files** (FR-35): versioned with the Brain; a Playbook change is a pull request with Results.
- **Deprecation policy:** a breaking change to any surface is announced in the changelog, kept readable by the Replay tool for one prior version where feasible, and recorded in an ADR.

## 10. Constraints and guardrails

- **Safety: information parity.** The fairness requirements (section 4.2) are safety-class: a violation is a release blocker regardless of Results.
- **Module boundaries.** Six Shatterfish modules with exactly these permitted dependencies: `api` depends on nothing (DTOs only: Observation, Action, Decision, Run log records); `harness` on `core` and `api`; `codex` on `core`; `brain` on `api` only; `rig` on `harness` and `brain`; `overlay` on `core`, `harness`, and `brain`. The build enforces the edges.
- **License and attribution.** GPL-3.0-or-later; upstream's license kept; `NOTICE.md` names Evan Debenham and Watabou; the README states unofficial and unaffiliated; no Shatterfish issue is ever filed upstream.
- **Runtime.** Java 21; one JVM; the bot in the same process as the game; libGDX at the version the pinned tag uses.
- **Cost.** Public repository, GitHub Actions minutes, one laptop; the smoke loop must fit a working session on that laptop (NFR-3).
- **Human control.** Mode changes only when the hero waits for input; a human can always take over; Oracle mode is visibly marked.

## 11. Integration and dependencies

- **Upstream SPD** at the pinned tag (v3.3.8 at writing; 4.0 stable expected as one large drop): the only rules engine; upgraded by FR-50.
- **libGDX** headless backend and desktop natives at the tag's version; **JUnit 5** and **ArchUnit 1.5** for tests; **Gradle** as the build.
- **MkDocs Material** for the docs site; **GitHub Actions** for CI, the nightly smoke Rig, and Pages.
- **BMAD** artifacts (this PRD, the brief, the research, the architecture, epics, stories) mirrored on the docs site.
- **Project skills** (`next-story`, `rig`, `codex`, `upstream-sync`, `adr`, `sync-issues`, `handoff`) and subagents (`fairness-reviewer`, `upstream-reader`) are part of the delivery process, not the product.

## 12. Why now

The field is empty: no bot, RL agent, gym, or headless harness exists for SPD. Upstream is quiet while 4.0 is built privately, so the hook surface is stable for months. The research shows every foundation has a known shape and a known cost. And the symbolic-bot tradition has a clear, recent verdict (the 2021 NetHack Challenge and everything since) about what works.

## 13. Open questions

1. What turns per second does a fast-forwarded Headless scene reach on SPD, and does the actor thread ever block under it? (E1 spike; NFR-3 restated afterwards.)
2. What is the paired-seed correlation for two Shatterfish Brains, and hence the real sample-size saving? (E3 measurement, SM-5.)
3. What are the leaf correlation, bias, and disambiguation of SPD tactics? (E1 measurement with random playouts; decides FR-34's design.)
4. Does the seed determine anything beyond dungeon generation? (Upstream reader on `Random`, `Dungeon.seed`, generator resets; decides FR-2's seeding work.)
5. Which think budgets, if any, does v1 publish? (Likely none before E6.)
6. Which human win-rate source calibrates "beats the median human"? (E7.)
7. Is coach mode part of E8 or its own Epic? (Epics workflow.)
8. What Score target and which class define the first SM-2 goal after the first Yog-Dzhewa kill? (Set at SM-1.)
9. What are Fishtest's current default bounds, as text, for the E3 statistics ADR's starting values?
10. Does SPD's save file preserve enough RNG state for a Replay to verify across a save-and-resume boundary? (Decides whether FR-37's resumed Runs are Replay-verifiable in v1 or E8.)
11. Where does the `standard` Seed set run once its cost is known: the developer's machine, GitHub Actions, or both? (E3 ADR; running it on Actions un-parks the "Actions as Rig workers" idea.)

## 14. Assumptions index

No `[ASSUMPTION]` tags remain: the product owner resolved the first draft's assumptions or delegated them, and the memlog records each decision.

- `[NOTE FOR PM]` callouts (deliberate changes to bootstrap "done when" statements): FR-5 (E1 done-when becomes measured numbers); section 6.2 (coach mode position).
- Decisions taken under delegation: unseen-monster memory in v1 Beliefs (FR-29); Panel placement in the free column with UX deciding small sizes (FR-38); hotkeys in v1 with a fallback (FR-42); the Goo threshold of 75% with a 70% lower bound (FR-31, SM-3); initial Seed-set sizes (FR-20); the Composite outcome order win, Score, depth, turns (Glossary, FR-21).
