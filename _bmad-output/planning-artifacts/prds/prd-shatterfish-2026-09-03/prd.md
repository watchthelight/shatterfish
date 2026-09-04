---
title: 'PRD: Shatterfish'
status: final
version: 3
created: '2026-09-03'
updated: '2026-09-03'
inputs:
  - _bmad-output/planning-artifacts/briefs/brief-shatterfish-2026-09-03/brief.md
  - _bmad-output/planning-artifacts/briefs/brief-shatterfish-2026-09-03/addendum.md
  - _bmad-output/planning-artifacts/research/technical-shatterfish-engine-foundations-2026-09-03/research.md
  - docs/BOOTSTRAP-PROMPT.md
  - _bmad-output/planning-artifacts/ux-designs/ux-shatterfish-2026-09-03/EXPERIENCE.md
addendum: addendum.md
---

# PRD: Shatterfish

## 0. Document purpose

- **Who it is for:** the product owner, the architecture workflow, the UX workflow (the Overlay has a UI; its spine is `EXPERIENCE.md` and `DESIGN.md`, listed in the inputs), and the epics-and-stories workflow.
- **What it builds on, without repeating it:** the product brief and its addendum (audience, success ladder, non-functional requirement list, technical constraints), the technical research report (feasibility and recommendations), and the bootstrap prompt (non-negotiables, module guardrails, Overlay description, program map).
- **Where things are:** vocabulary in the Glossary (section 3); features with globally numbered functional requirements (FRs) in section 4; cross-cutting requirements in sections 8 to 11; open questions in section 13; the assumptions index in section 14; the sibling addendum for the epic map, deferred decisions, and surface sketches.
- **Conventions:** requirements state capabilities, and how they are built belongs to the architecture and the addendum; the eight non-negotiables in the bootstrap prompt's section 1 bind every requirement and are not restated; where this PRD deliberately changes a bootstrap "done when", the change is called out inline as a `[NOTE FOR PM]`. Every statement about the game's mechanics or UI in this PRD is Tier 3 (a hypothesis) until a Rule with a `path:line` citation confirms it; such statements are marked "(Tier 3)". Consequences that belong to a later Epic than the FR's own are tagged with that Epic in brackets.

## 1. Vision

Shatterfish is an open-source engine for Shattered Pixel Dungeon (SPD) in the spirit of Stockfish. It drives SPD's own code headlessly and reproducibly, plays it with a hand-built symbolic bot, measures every change to that bot with a Fishtest-style statistical Rig over thousands of seeded Runs, and runs the bot inside the real desktop game where a person can step it one turn at a time, watch it think, and take the controls. It is a permanent downstream fork of SPD, pinned to a release tag, unofficial and unaffiliated.

Two disciplines define it, both enforced by architecture. Information parity: the bot may use only what a human at the same screen could see, guaranteed by the Observer, the one class that is the only door from game state to the bot, a build that fails if the Brain imports game code, and leak tests on every change. Measurement: nothing about the bot is believed until the Rig says so, with public Seed sets, pre-registered Sequential tests, and verifiable Run logs. Chess search does not transfer to a stochastic, partially observed, single-player game; Stockfish's infrastructure and testing culture do, and the symbolic-bot tradition from NetHack supplies the play.

The goal ladder has two rungs at the top: first beat the final boss, then score as high as possible. The headline is a verified Win: the Brain wins a Run on a seed it has never seen, reproducible from its Run log. After that, the highest verified Score of a winning Run becomes the canonical number, with per-class win rate as its check. Everything below (a measured Harness, a published Baseline, a Goo kill, a human taking over mid-fight without desync) is how the program knows it is getting there.

## 2. Target user

### 2.1 Jobs to be done

- **Functional (the developer, v1):** change the Brain, run the Rig, and learn from a small Seed set which way the change points; step the bot through a Run in the real game one turn at a time and see why it did what it did; trust that no result was helped by hidden information.
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

- **UJ-1. The developer changes the Brain and learns from the `smoke` Seed set which way it points.** The developer edits a scripted Policy on a branch and commits a Registration for the comparison. They run the Rig on the `smoke` Seed set against the current Baseline: the Harness runs both Brains headlessly in parallel processes, the Rig writes Run logs and a summary, and the Sequential test reports its running log-likelihood. Climax: the summary shows the Composite outcome moving the right way with the trace still inside the bounds; `smoke` is a direction check, not an acceptance. Resolution: the developer registers and runs the `standard` set, the test accepts, the Results file is committed to the branch, the pull request carries the numbers, CI re-runs the fairness suite, and the product owner reviews. Edge case: the test is undecided at the standard budget; the change stays unmerged.
- **UJ-2. Bash steps the bot through a fight and takes over mid-fight.** Bash launches the desktop game through the Shatterfish launcher, which gives the Run its own Profile. The Panel docks at the right edge of the game view as a separate instrument: Mode PAUSED, speed mode Next Step, the Goal, the chosen Action with three alternatives and one-line reasons, the Belief summary, Safety flags, a decision log, the planned path drawn on the map. Bash presses Next Step repeatedly, reading each Decision before it happens. A gnoll appears; Bash disagrees, presses Take over, plays two turns with the normal controls, presses Hand back. Climax: the Brain re-plans from the current state, shows its next Decision in PAUSED, and continues on Resume without a desync or a stale plan. Resolution: the Run log records the human turns; the Replay reproduces the Run exactly. Edge case: Bash presses Take over mid-animation; the control stays dim until the next Input wait, then the Mode changes.
- **UJ-3. A skeptic reproduces a number.** A community member reads a Results page: tag, Seed set, challenge flags, commit, both Brains, Hypothesis ID, Sequential-test outcome, and the command. They clone the tag, run the command, and get the same per-seed results and the same Hash chain. Climax: the numbers match byte for byte. Resolution: they open an issue with a seed the bot loses on. Edge case: they run on a different operating system; the nightly cross-platform check has already shown the hashes agree, and the methodology page says what to do if they do not.
- **UJ-4. A learner plays with the coach (v2).** A player who dies on floor 5 every Run turns on coach mode. They play; before each move the Panel shows what the bot would do and why, in the game's vocabulary and at most one plain sentence (`read scroll (KHIT): by a door and water, worst case survivable`). Climax: they understand a decision they would not have made. Resolution: they hand a boring corridor to autoexplore and take back control at the next enemy. Lighter scope; realized in E8.
- **UJ-5. The developer upgrades to the next Upstream tag.** Upstream releases 4.0 stable. The upgrade skill fetches tags and measures the change; the product owner approves the target; the merge lands on a branch; every Hook is re-verified; the Codex regenerates and its diff is summarized; the fairness and determinism tests run; the Rig re-baselines and the Sequential-test bounds are recalibrated; the docs update in the same pull request. Climax: the mechanics diff is readable and the Baseline is republished. Resolution: the pinned tag changes in one place.

## 3. Glossary

**Engine**

- **Observation** — Immutable, serializable, content-hashed snapshot of everything a human at the screen could know at one Input wait: the known map as drawn, visible actors as displayed, hero stats and buffs, inventory with identification status exactly as the UI shows it, equipment and quickslots, this-Run journal notes, recent log lines, the Floor, the turn, the open Prompt if any, and the set of valid Actions. Produced only by the Observer. Lives in the `api` module.
- **Action** — One thing the bot may do at an Input wait: move one step toward a cell, attack, use, throw, zap, read, drink, equip, drop at a target, rest one turn, search one turn, descend, ascend, talent or ability use, wait, take the Amulet, end the Run (leave with the Amulet or ascend), or answer the open Prompt with one of its options. Executed only by the ActionExecutor through the game's own code paths. Actions that the game's UI would carry out over many turns (a long move, resting, searching) are issued one Input wait at a time.
- **Prompt** — A game dialog that waits for the hero's choice (subclass, armor ability, talent point, quest reward, shop purchase, alchemy) and blocks play until answered. The Observation carries the open Prompt and its options; "answer the Prompt" is an Action.
- **Input wait** — Any moment the game waits for the hero's decision: a hero turn or an open Prompt. Decisions, Run log lines, and Overlay steps are per Input wait.
- **Floor** — A level identified by depth and branch together (branch 1 exists at depths 11 to 14 and 16 to 19; Rule: `docs/rules/levels.md`).
- **Decision** — The Brain's output for one Input wait: the chosen Action, the top alternatives with scores and one-line reasons, the current Goal, Safety flags, and the Policy that fired.
- **Goal** — The Brain's current strategic intent in plain words (for example "Explore: guaranteed strength potion still on this floor").
- **Safety flag** — A short verdict about the risk of an Action in the current Observation, stated verdict-first: `ok`, `warn`, or `unsafe`, then the reason (for example `ok: by water, fireblast-safe`; `unsafe: chasm behind target`).
- **Observer** — The single class in the Harness that reads game state and produces an Observation, including the valid-Action set. The only door from game state to the Brain.
- **ActionExecutor** — The single class in the Harness that applies an Action to the hero.
- **Harness** — The module that boots SPD headlessly, seeds it, and exposes Observer and ActionExecutor. Contains the two Drivers.
- **Headless scene** — The Harness-owned scene that lets SPD's turn resolution run without a window: it supplies what sprites attach to, a no-op graphics layer, and a fast-forwarded update loop.
- **Driver** — The component that owns the game loop for the bot. The **HeadlessDriver** runs the Headless scene; the **EmbeddedDriver** runs inside the real desktop game.
- **Profile** — The game's per-user persistent state (journal catalog, badges, remains of a previous hero, settings). Every Run, headless or embedded, gets its own fresh Profile so that nothing crosses between Runs.

**Brain**

- **Brain** — The module that turns an Observation into a Decision. Depends on the `api` module only. Comprises Beliefs, Policies, Playbooks, Search, and the Evaluation.
- **Belief** — The Brain's model of what it cannot see: per-unidentified-item candidate sets with probabilities, floor facts, chapter counters, memory of monsters seen and lost. Updated from every Observation regardless of who acted.
- **Belief summary** — The Panel's compact rendering of Beliefs: unknown items with their top candidates and probabilities, floor facts, chapter counters. The full **Beliefs view** (v2) shows every candidate and the evidence behind it.
- **Policy** — A scripted behavior with an entry predicate (for example explore, fight in corridors, eat, descend). The **Arbitration** is the ordered list of Policies the Brain consults each Input wait.
- **Playbook** — Strategic knowledge as data: per-class, per-boss, item-identification, and upgrade-allocation rules editable without code changes. A Playbook may not reference a seed or a specific dungeon layout.
- **Search** — Lookahead over Actions that sees only the Observation and Beliefs; hidden state, including the random generator state, is sampled from Beliefs (redetermination) before any simulation. Rollouts on the raw saved game are forbidden.
- **Evaluation** — The scoring function over Observations that Policies and Search use; hand-tuned from Codex tables, with weights held as data.
- **safeTest** — The Belief computation that scores the worst case of using an unidentified item at a cell, over its candidate identities and the surrounding terrain and enemies.

**Knowledge**

- **Codex** — Tables generated from the pinned upstream code: every mob, item, generator weight, mob rotation, trap, recipe, changelog entry, player-facing text, asset path, and measured combat tables, with `path:line` citations. Static and seed-free: the Codex describes types, never a Run. The Brain's only source of general game knowledge.
- **Lore** — Community knowledge admitted through the lore pipeline: one file per claim with provenance, variant, and Tier. A Lore claim may not reference a seed or a specific dungeon layout.
- **Tier** — Verification level of a Lore claim or Rule: 1 code confirms, 2 harness confirms, 3 hypothesis for the Rig, F false or obsolete for a tag.
- **Rule** — A claim about a mechanic that Shatterfish relies on, with a `path:line` citation at the Upstream tag and a link to the test that checks it.

**Rig**

- **Rig** — The module and tooling that runs many seeded games in parallel, compares two Brains with a Sequential test, writes Run logs, Replays them, and publishes Results.
- **Run** — One game from a fresh Profile to death, Win, or the turn cap (20,000 hero turns `[ASSUMPTION: revisable by the E3 ADR from the measured Run-length distribution]`, recorded as a loss with cause `turn cap`), fully determined by (Upstream tag, hero class, challenge flags, seed, Action list). In the bootstrap prompt's shorter tuple, class and flags are part of what "seed" fixes.
- **Win** — The game's own victory condition as it records it (the Amulet obtained and the Run ended, with or without ascension; Rule: `docs/rules/save-score-win.md`). Killing Yog-Dzhewa is the last obstacle, not the Win; the Amulet lies on depth 26.
- **Score** — The in-game score the game reports for a Run, comparable only between Runs with the same challenge flags (the game multiplies score by `round(1.25^n * 20) / 20` for n active challenges; Rule: `docs/rules/save-score-win.md`).
- **Seed set** — A named, versioned, committed list of (seed, hero class, challenge flags) triples: `smoke`, `standard`, `holdout` (never run during development), `bosses`.
- **Run log** — The JSONL record of a Run: per Input wait the Observation hash, the Decision, the Action taken and by whom (bot or human), chained so that any tampering is detectable.
- **Hash chain** — The per-Input-wait Observation hash that includes the previous hash.
- **Composite outcome** — The per-Run result the Sequential test compares, ordered: Win; then, for winning Runs, Score; then bosses killed; then Floor depth reached; then turns survived. A difference at an earlier position always dominates a later one. For losing Runs, bosses killed ranks above depth so that dying deeper without passing a boss is not rewarded (Rule `docs/rules/combat.md`: the game locks the stairs on boss floors until the boss is dead, so depth and bosses killed move together).
- **Per-pair statistic** — For one seed played by both Brains, the ordinal comparison of their Composite outcomes: better, equal, or worse. The Sequential test runs on these three outcomes as Fishtest's trinomial model does, with bounds in that unit; the E3 statistics ADR may refine the statistic but not the ordinal core.
- **Sequential test** — The Fishtest-style Generalized SPRT (GSPRT) that decides accept, reject, or undecided over Per-pair statistics under pre-registered bounds.
- **Hypothesis ID** — The identifier of a Registration.
- **Registration** — A record committed to the repository before a comparison starts, fixing its bounds, Seed set version (and therefore hero classes and challenge flags), and both Brains' commits; the Hypothesis ID is derived from it. A standing Registration covers a recurring comparison such as the nightly `smoke` run.
- **Baseline** — The last published Brain a new Brain is compared against.
- **Results** — A published page under `docs/results/` for one Rig comparison, with everything needed to reproduce it.
- **Oracle mode** — A debugging and labeling mode in which true hidden state is exposed. Off by default, enabled only by an explicit launcher flag, visibly marked, never allowed in a ranked Rig Run.

**Overlay and upstream**

- **Overlay** — The Panel, its controls, and the map highlights, running inside the real desktop game through the EmbeddedDriver. Its behavior is specified in `EXPERIENCE.md`; its look in `DESIGN.md`.
- **Panel** — The docked, native-style UI element of the Overlay, a visibly separate instrument built from the game's own toolkit.
- **Mode** — The Overlay state: **RUNNING** (the Brain acts per the speed mode), **PAUSED** (the Brain is halted, the hero's game input is ignored, only Overlay controls respond), or **HUMAN** (the human plays with the game's normal controls; the Brain observes and updates Beliefs but never acts). **Run over** is the terminal state after death or Win.
- **Speed mode** — How the Brain advances in RUNNING: **Next Step** (one Input wait per press; the Overlay sits in PAUSED between presses), **Human play speed** (one Input wait per configurable interval), or **Fast as it can** (uncapped). **Run N** advances N Input waits at Human play speed, then pauses.
- **Pause-on condition** — A rule that switches the Overlay to PAUSED when it becomes true (for example before any item use, when HP drops below a threshold).
- **Replay** — Loading a Run log and stepping through its Decisions, verifying the Hash chain against a fresh Run.
- **Hook** — A minimal, justified, labeled edit to an upstream file, listed in the hook registry.
- **Upstream tag** — The SPD release tag Shatterfish is pinned to (v3.3.8 at writing).
- **Epic / Story** — Units of the program plan (E0 to E9) and of work; a Story is small enough for one session.

## 4. Features

Each feature has a description that names the user journeys it realizes, then its FRs. An FR's capability sentence is its acceptance test unless a "Consequences (testable)" list follows, in which case the list is. A trailing "Provenance:" line records why an FR sits where it does when that placement changed during planning. A consequence tagged with an Epic in brackets is owned by that Epic, not by the FR's own.

### 4.1 Headless engine

**Description:** The Harness boots SPD's `core` on libGDX's headless backend inside the Headless scene, seeds every random source, and drives a Run one Input wait at a time: the Observer produces an Observation, a caller supplies an Action, the ActionExecutor applies it, and the loop continues until the Run ends. Everything the Rig, the Brain, and the fairness tests do rests on this. Realizes UJ-1, UJ-3.

**Functional requirements:**

#### FR-1: Boot a Run headlessly
The Harness can start a new game of a given hero class, challenge flags, and seed in a fresh Profile with no window, no OpenGL context, and no Android SDK, and run it to completion.

**Consequences (testable):**
- A Run completes on a machine with no display and no graphics driver.
- Turn resolution paths that depend on sprite animation (attack, zap, throw, use) complete without a real render loop.
- Boot succeeds with the desktop natives shipped and fails with a message that names the missing natives if they are absent.

#### FR-2: Determinism from (tag, class, challenges, seed, Action list)
The Harness can reproduce a Run exactly: two Runs with the same Upstream tag, hero class, challenge flags, seed, and Action list produce identical Observation hashes at every Input wait.

**Consequences (testable):**
- Every random source the game uses, including the general-purpose generator used for combat rolls (Rule: `docs/rules/rng.md`; it must be seeded after `Dungeon.init`, which resets the generator stack), is seeded by the Harness; the seeding strategy is an ADR (open question 4).
- Wall-clock time, thread scheduling, hash-map iteration order, and Profile contents do not influence any Observation.
- One process hosts one Run at a time; each Run has its own Profile and working directory. If the E1 isolation spike lets a process host several Runs, determinism must hold per isolated instance and the same tests apply.
- The Brain is deterministic given the Observation sequence and its own seeded generator: two Runs with the same tuple produce the same Action list, not only the same Observations for a given Action list. The Overlay's thinking budget (NFR-4) never changes a Decision: an overrun delays it, and in the Rig no budget applies. A Replay whose Decision differs from the Run log's at any Input wait is a determinism failure and the Replay test reports it.
- The determinism test runs in CI on every pull request.

#### FR-3: Observer produces the Observation
The Observer can build an Observation from what the game computes for drawing, from the game log, from this-Run journal notes, and from the open Prompt, never from raw model fields that a player cannot see.

**Consequences (testable):**
- An unidentified item appears in the Observation under its unidentified name only.
- A mob outside the hero's field of view, an undiscovered trap, and an unfound secret door do not appear.
- Mind vision, magic mapping, blindness, and darkness change the Observation exactly as they change the screen.
- The valid-Action set is part of the Observation, so the leak and differential tests cover it.
- The Observation is immutable, serializable, and content-hashed; equal Observations have equal hashes; the hash covers the whole Observation and nothing outside it.

#### FR-4: ActionExecutor applies an Action
The ActionExecutor can apply any Action through the same code paths the game's UI uses, on the thread the game requires.

**Consequences (testable):**
- Every Action in the Glossary maps to a game input the UI could produce, including answering every kind of Prompt the game can open.
- An Action that is invalid in the current Observation is rejected before touching game state, with a reason.
- A completeness test enumerates the game's hero-affecting inputs and asserts each maps to an Action or is documented as unsupported; an unsupported input taken by a human ends Replay-verifiability from that Input wait and the Run log records it [E5].

#### FR-5: Random-action agent and throughput measurement
The Harness ships a random valid-action agent and a benchmark that reports Input waits per second and Runs per minute for one process, with and without a Brain attached, on a described machine.

**Consequences (testable):**
- 1,000 seeded random-action Warrior Runs complete unattended.
- The benchmark output is a committed Results page with the machine described and the median Run length recorded.

`[NOTE FOR PM]` The bootstrap prompt's E1 done-when, "1,000 runs in seconds", is replaced: E1 publishes the measured rate and the tactics' leaf correlation and disambiguation (open question 3); paired-seed correlation needs two Brains and is measured in E3 (SM-5). No throughput number is promised before the measurement (section 8, NFR-3).

#### FR-6: Snapshot, restore, and redetermination
The Harness can snapshot a Run's game state, restore it, and produce a redetermined copy in which every hidden element, including the random generator state, is re-sampled from a supplied Belief. Deferred to E6; the interface is reserved in E1.

**Consequences (testable):**
- Restoring a snapshot and replaying the same Actions yields the same Observations as the original.
- A redetermined copy differs from the original only in hidden elements; the differential test proves the Observation is unchanged; its random generator is seeded from the Belief sample, never from the Run.
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
- The Codex reaches the Brain as data only (JSON), never as classes.

#### FR-8: Leak tests
Every change to the Observer ships with tests proving that hidden state does not appear in the serialized Observation.

**Consequences (testable):**
- An unidentified scroll, a mob behind a wall, a secret door, a hidden trap, an invisible enemy, the seed, the random generator state, and validity of an Action that would reveal any of these are each absent from the Observation in a constructed world.
- The leak test suite runs in CI on every pull request.

#### FR-9: Differential test
Two worlds identical to the player but different in hidden state serialize to byte-identical Observations.

**Consequences (testable):**
- The test constructs at least: different unidentified-item identities, different unseen mob positions, different random generator state.
- A Brain given both worlds produces identical Decisions until the Observations diverge (the behavioral form) [E4].
- The behavioral form also runs on real seeds from `standard` with hidden identities permuted per seed, so that no component can have memorized seed-to-identity [E4; required for any learned component, E9].

#### FR-10: Toggle tests
The same world with and without mind vision, blindness, and magic mapping produces exactly the expected differences in the Observation.

#### FR-11: Oracle mode gating
Oracle mode exists only behind an explicit launcher flag, marks every surface it touches, and cannot be enabled in a ranked Rig Run. Its two uses are debugging and producing training labels for the optional E9.

**Consequences (testable):**
- Without the flag, no code path can read true item identities or unseen positions into the Brain.
- With the flag in the Overlay, the Panel draws a red border and an "ORACLE" label [E5].
- The Rig refuses to start a ranked comparison when the flag is present, and every Results file records that Oracle mode was off [E3].
- Label Runs for E9 use seeds disjoint from every committed Seed set [E9].

#### FR-12: Thread confinement
Game objects are touched only on the threads that own them; the Brain never holds a game object.

**Consequences (testable):**
- Observer and ActionExecutor assert the owning thread and fail fast otherwise.
- The Brain runs on a worker thread with only the immutable Observation [E5 for the Overlay; E1 headless].

#### FR-13: Search leak test
When Search exists, replacing the true hidden state with random alternates produces identical Decisions. Deferred to E6.

### 4.3 Codex and knowledge

**Description:** The Codex is the bot's general game knowledge, generated from the pinned code so that it can never drift from the tag it describes; it is static, seed-free data about types and tables, never about a Run, which is why it is not a second door into the Brain. Rules and Lore are how the docs and the Brain cite mechanics. Realizes UJ-3, UJ-5.

#### FR-14: Generate the Codex from the pinned tag
One build task regenerates the Codex into a tag-named folder and the generated documentation pages.

**Consequences (testable):**
- The Codex covers every mob, item, generator weight and guarantee, mob rotation per depth, trap, recipe, changelog entry, player-facing text, and asset path, enumerated by a completeness check against the game's own class lists.
- The generation is parameterized by depth and challenge flags where the game's tables are.
- The Codex is seed-free: generating it twice with different seeds and different Profiles produces byte-identical output, and no Codex value depends on a Run (a Codex leak test).
- Combat behavior enters the Codex as measured tables, not transcribed formulas: the generator runs the engine's own methods (for example hit resolution) over a parameter grid and records the outcomes, with the `path:line` of the method measured.
- Every generated entry carries a `path:line` citation into the tag.

#### FR-15: Codex drift check
CI regenerates the Codex and fails if the committed output differs.

#### FR-16: Vocabulary diff
The Codex includes a diff between vanilla Pixel Dungeon and SPD names for items, mobs, and mechanics, used by the lore pipeline's variant classifier; the vanilla side comes from a second pinned source (section 11).

#### FR-17: Rules with citations and the codebase map
Every mechanics claim the Brain or the docs rely on is a Rule with a `path:line` citation at the Upstream tag, a Tier, and a link to the test that checks it; the set of claims the Brain relies on is enumerated in the Brain's own Rules index so "every" is checkable. The file-to-mechanic codebase map is maintained alongside. A citation checker reports citations that no longer resolve.

Provenance: the citation checker is promoted from the ideas ledger into E2 because the upgrade procedure (FR-50) depends on it.

#### FR-18: Lore pipeline
Community knowledge enters as one file per claim with provenance frontmatter (fields in the addendum, Lore claim frontmatter). The product owner's research skill is the intake path. A classifier assigns the variant from the vocabulary diff and the changelog dates. A claim that references a seed or a specific dungeon layout is rejected at intake. Every Brain heuristic links to a Lore entry or a Rule (a Rule is the stronger form) with a Tier. Deferred to E7.

### 4.4 Rig

**Description:** The Rig turns the Harness into a measurement instrument: many Runs in parallel over versioned Seed sets, a Sequential test between two Brains, Run logs that anyone can Replay, and Results pages that carry everything needed to reproduce them, whatever the outcome. Realizes UJ-1, UJ-3.

#### FR-19: Parallel runner
The Rig can run a Seed set for one or two Brains across parallel processes and collect per-Run results.

**Consequences (testable):**
- Per-Run results include seed, hero class, challenge flags, Brain, outcome (Win or cause of death), Score, bosses killed, Floor reached, turns, and the final Observation hash.
- One process per Run, each with its own Profile and working directory; the `--parallel` flag sets how many processes run at once; the runner reports throughput and the number of processes used.

#### FR-20: Seed sets
Seed sets are committed files of (seed, hero class, challenge flags) triples with initial sizes `smoke` 25, `standard` 500, `holdout` 500, `bosses` 100, and `goo` 400 (Warrior only, no challenge flags; the E4 gate's set, sized so that an observed 75% Goo-kill rate gives a Wilson lower bound above 70%) (revisable by ADR once throughput is measured, together with SM-3's bound). `holdout` is never run during development, may be used only to publish a release-level number or the SM-1 claim, at most once per Brain version, and every use is recorded in the Results.

**Consequences (testable):**
- A Results file names the Seed set and its version, and therefore the classes and flags it fixes.
- The Rig refuses a development comparison on `holdout`.

#### FR-21: Sequential test
The Rig compares two Brains with a Sequential test over Per-pair statistics, with bounds in that unit, and reports accept, reject, or undecided with the log-likelihood trace.

**Consequences (testable):**
- A deliberately worse Brain is rejected on the `standard` set: the E3 reference is the random agent with the descend Action removed, which reaches fewer bosses and less depth by construction; from E4 on it is the Baseline with its heal Policy removed.
- The test does not stop before a burn-in; realized error rates are validated by simulation on the Rig's own outcome distribution and published on the methodology page; `smoke` is sized for direction, not acceptance, and the Results page says which it was.
- The measured paired-seed correlation is reported with every comparison.
- Every Results page shows the survival curve and median death depth beside the outcome, so a Brain that dies deeper without passing bosses is visible.

#### FR-22: Pre-registration
Every comparison has a Registration committed before its first Run; the Hypothesis ID is derived from the Registration; the Rig embeds the Registration's commit in the Results and refuses a comparison whose Registration commit postdates its first Run. The Rig keeps a local ledger of every comparison it has run for a Brain commit on a Seed set, and every Results page states how many prior Runs of that pair the ledger holds, so a register-after-peeking pattern is visible even though it cannot be prevented. Recurring comparisons run under a standing Registration.

#### FR-23: Run logs with a Hash chain
Every Run writes a JSONL Run log: per Input wait the Observation hash chained to the previous, the Decision, the Action, and the actor (bot or human [E5]).

#### FR-24: Replay with verification
The Rig can Replay a Run log and verify that a fresh Run in a fresh Profile reproduces every Observation hash; a mismatch is reported with the first divergent Input wait; a log that records an unsupported human input is verifiable up to that point and says so.

#### FR-25: Results publication, including negatives
Every registered comparison publishes a Results page, whatever its outcome (accept, reject, undecided). The page carries:

- Upstream tag and Shatterfish commit;
- Seed set name and version, with the hero classes and challenge flags it fixes;
- both Brains (name, commit, configuration);
- Hypothesis ID with its Registration commit, bounds, and units;
- the outcome with the log-likelihood trace, and whether the Seed set was sized for direction or acceptance;
- per-Run distributions: Win, Score, bosses killed, Floor, turns, cause of death; the survival curve;
- the measured paired-seed correlation and the ledger count of prior Runs;
- fairness suite status and confirmation that Oracle mode was off;
- links to the Run logs;
- the command that reproduces it.

Results land through a pull request. A nightly job runs the `smoke` Seed set against the Baseline on GitHub Actions under a standing Registration and updates one results pull request; the `standard` Seed set runs on the developer's machine until an ADR decides otherwise (open question 11).

#### FR-26: Death replay gallery
The Rig publishes, per comparison, the shallowest deaths with their Decision logs [E3] and, once an Evaluation exists, the Runs with the largest Evaluation drop in their final 10 Input waits [E4], as a bug source.

Provenance: promoted from the ideas ledger; the shallowest-deaths half is E3, the Evaluation half E4.

### 4.5 Brain

**Description:** The Brain is observation-driven: every Input wait it takes the current Observation, updates its Beliefs, consults its Arbitration of Policies, optionally runs Search, and emits a Decision. It never assumes it made the previous move, which is what lets a human take over. Identical code runs headless and in the Overlay. Realizes UJ-1, UJ-2, UJ-4.

#### FR-27: Re-plan every Input wait from the Observation
The Brain produces a Decision from the current Observation and its Beliefs alone, with no dependence on its previous Decision having been executed.

**Consequences (testable):**
- Interleaving arbitrary human Actions between bot turns never causes an error or a stale plan (the takeover test) [E5].
- A Brain exception yields a Decision of "wait" with the error recorded, never a crash of the game.

#### FR-28: Arbitration of Policies
The Brain's behavior is an ordered list of Policies with entry predicates that evaluate without side effects and without simulation; higher-priority Policies interrupt lower ones; the active Policy and its reason are part of the Decision.

#### FR-29: Beliefs
The Brain maintains Beliefs updated from every Observation: candidate identities with probabilities for each unidentified item (weighted from Codex spawn weights and identification history), floor facts, chapter counters for guaranteed drops, and memory of monsters seen and lost.

**Consequences (testable):**
- Belief update is a pure function of (previous Beliefs, Observation) with its own leak tests.
- Identifying an item type collapses its candidates everywhere.

Provenance: unseen-monster memory is in v1 because the strongest published bot lacked it.

#### FR-30: safeTest
The Brain scores the worst case of using an unidentified item at a cell over its candidate identities, using visible terrain and enemies, and uses the score to decide when and where to test unknown items.

#### FR-31: Scripted baseline Policies
The v1 Brain includes Policies for explore, pick up, equip, eat, heal, test unknown items (through safeTest), fight in corridors, answer Prompts, and descend, sufficient for the Warrior to kill Goo on at least 75% of the `goo` Seed set (400 Warrior triples, FR-20) with a lower confidence bound of at least 70% at that size.

#### FR-32: Decision output
Every Decision contains the chosen Action, at least the top three alternatives with scores and one-line reasons, the Goal, Safety flags, and the Policy that fired, in Codex vocabulary.

#### FR-33: Evaluation
The Evaluation is a hand-tuned weighted function over Observation features derived from Codex tables (including the measured combat tables); its weights are data so the Rig can tune them.

Provenance: SPSA tuning is promoted from the ideas ledger to E6+.

#### FR-34: Tactical Search
When enemies are visible, the Brain can run a depth-limited expectimax Search of two to four hero turns that sees only the Observation and Beliefs. The Search's model (an abstract tactical model derived from Observations, as the bootstrap prompt permits, or engine rollouts on redetermined state) is decided by ADR on the measured properties of open question 3 (addendum, mechanism decisions). Deferred to E6.

**Consequences (testable):**
- FR-13 passes for the chosen design; rollouts on the raw saved game are impossible by construction (FR-6).
- Search is budgeted to visible-enemy Input waits and boss floors; quiet turns use Policies.

#### FR-35: Playbooks as data
Per-class, per-boss, item-identification, and upgrade-allocation strategy lives in versioned data files the Rig tests; a Playbook change is a pull request with Results; a Playbook that references a seed or a layout fails validation. Deferred to E7.

#### FR-36: Strategy log
The Brain records, per Input wait, which Policy or Playbook fired and why, as a Decision field that the Run log stores and, in the Overlay, the Panel shows [E5].

### 4.6 Overlay

**Description:** The Overlay runs the Brain inside the real desktop game through the EmbeddedDriver as a tool-assisted-speedrun instrument: the human can advance the bot one Input wait at a time, run it at a pace a person can follow, or let it run flat out, and can take the controls and hand them back at any Input wait. The Panel is a visibly separate instrument built from the game's own frames, font, and buttons. Its behavior (surfaces, states, transitions, limits, controls) is specified in `EXPERIENCE.md` and its look in `DESIGN.md`; the FRs below bind the capabilities and the spine binds the detail. Realizes UJ-2, UJ-4.

#### FR-37: EmbeddedDriver and launcher
A launcher starts the desktop game with the Overlay attached in a fresh Profile owned by the launcher; the EmbeddedDriver observes every Input wait regardless of who acted and applies the Brain's Actions through the ActionExecutor. Saving and quitting inside a Run records the boundary in the Run log; a resume through the launcher re-attaches in PAUSED (Replay across a resume is verified from E8; open question 10). A save opened without the launcher is not an Overlay Run.

**Consequences (testable):**
- The observation point at the hero's Input wait is a listed Hook (section 10, hook budget).
- Two Overlay Runs never share a Profile.

#### FR-38: Native Panel
The Panel is docked at the right edge of the game view over the dungeon (translucent, as the game's own HUD is), never over the game's own HUD, built from the game's own UI toolkit, and respects the game's interface-size setting; the world camera is offset so the hero stays centered in the uncovered area (the game's own offset is vertical-only and conditional, so the Overlay sets its own; Rule: `docs/rules/ui.md`). It shows:

- the Mode strip: Mode, speed mode, turn, Floor, and the `THINKING` and `ORACLE` markers;
- the Goal (at most two lines);
- the chosen Action with the top three alternatives, scores, and one-line reasons;
- the Belief summary (the three most relevant unknown items, floor facts, chapter counters);
- Safety flags (at most four chips);
- a scrolling decision log (200 lines on screen; the Run log holds the rest).

The Panel sits to the left of the inventory pane's column and between the status pane and the toolbar; when the uncovered map would be narrower than 200 UI pixels, the view shorter than 200, or the game is in its mobile layout, the Panel collapses to the Mode strip (the spine's Layout section carries the sizes; a 1280 by 720 window at UI zoom 3 is 427 by 240 UI pixels and collapses; Rule: `docs/rules/ui.md`). The Panel's states and transitions are the state table in `EXPERIENCE.md` (RUNNING, PAUSED, HUMAN, THINKING, hero busy, no valid action, Brain error, Run over, no Run, save and resume, Oracle, collapsed); every transition happens only at an Input wait, and every control is disabled whenever its transition is impossible.

**Consequences (testable):**
- No Swing, JavaFX, ImGui, or web view is on the classpath.
- Every Panel write happens on the render thread; the Brain never touches a Panel object.
- Each state in the table is reachable in a scripted test and shows the text the table prescribes.

#### FR-39: Controls and speed modes
Pause/Resume, Next Step, Run N (with its count set on the Panel), the speed selector (Next Step, Human play speed, Fast as it can; the Human play speed interval set on the Panel), Take over/Hand back, Explain (expand the current Decision to its Policy, alternatives, and Safety flags), and the Panel toggle. A Run starts in PAUSED with speed mode Next Step.

**Consequences (testable):**
- Mode and speed-mode changes take effect at the next Input wait and never lose one.
- Next Step advances exactly one Input wait; in RUNNING it pauses first.
- Run N advances N Input waits at Human play speed and lands in PAUSED.
- Human play speed paces one Input wait per configurable interval and never exceeds the game's animation speed; Fast as it can is uncapped, with the game's animation as the ceiling unless a documented Hook bypasses sprite waits.
- Hand back always lands in PAUSED with a fresh Decision shown.

Provenance: the bootstrap prompt's "Speed (a turns-per-second cap)" became three speed modes at the product owner's direction in the UX session; Run N is kept from the bootstrap prompt.

#### FR-40: Interjection semantics
In HUMAN Mode the human plays with the normal controls and every human Action is recorded in the Run log with actor `human`; in PAUSED the hero's game input is ignored and only Overlay controls respond; in every Mode the Brain observes each Input wait and updates Beliefs; on Hand back and on Resume it re-plans from the current Observation.

#### FR-41: Map highlights
The Overlay draws the planned path, the target, and the considered cells on the map when a Decision is made, clears them when the hero acts or the plan changes, and never draws them in HUMAN Mode.

#### FR-42: Hotkeys
Overlay controls are bindable through the game's own key-binding system and appear in its settings screen; defaults avoid every binding the game ships.

Provenance: moved from Overlay v2 to v1 because the research verified the binding Hook is small; if the Hook is not small, v1 ships buttons only and hotkeys return to E8.

#### FR-43: Oracle overlay marking
When launched with the Oracle flag, the Overlay draws a red border and an "ORACLE" label, may show true identities inside the Belief summary with an `oracle` prefix, and may mark unseen enemies on the map in the oracle color; nothing else changes (see FR-11).

#### FR-44: Explain view (v2)
The Panel can expand the current Decision further into the Evaluation terms and, when Search ran, the alternatives' expected outcomes. Deferred to E8.

#### FR-45: Pause-on conditions (v2)
The user can set conditions that switch the Overlay to PAUSED: before any item use, before testing an unknown item, before stairs, on boss floors, when HP drops below a threshold, when a new enemy appears; they are set in an Overlay section of the game's own settings screen (one Hook). Deferred to E8.

#### FR-46: Replay scrubber and Beliefs view (v2)
The Overlay can load a Run log and scrub through its Decisions with the Panel showing each Input wait's Decision and the full Beliefs view. Deferred to E8.

#### FR-47: Coach mode and autoexplore (v2)
In coach mode the Brain advises before each human move without acting, in Codex vocabulary and at most one plain sentence; autoexplore hands quiet stretches to the Brain and returns control when an enemy appears or a Pause-on condition fires. Deferred to E8 (the epics workflow may split coach mode into its own Epic; open question 7). Realizes UJ-4.

### 4.7 Upstream management

**Description:** Shatterfish is pinned to an Upstream tag and upgrades only by merging a newer tag through a documented procedure. Realizes UJ-5.

#### FR-48: Pinned tag and hook registry
The pinned Upstream tag, commit, and date are recorded in one place; every Hook is listed with its file, reason, guard, and the tag it was last verified at; every pull request that edits an upstream file carries the `touches-upstream` label; the number of Hooks is reported on the upstream page against the budget in section 10 (SM-C5).

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

- Shatterfish will not reimplement any game rule in any language for play: the real engine is the only rules engine, and the only permitted abstraction is an abstract tactical model derived from Observations and measured Codex tables, as the bootstrap prompt's fourth non-negotiable allows.
- It will not run the bot as a separate process or over a socket.
- It will not use any UI framework other than the game's own.
- It will not read hidden state into the Brain outside the flagged Oracle mode, and Oracle mode will never be available in a ranked Rig Run.
- It will not propose changes to upstream, file issues there, or merge `upstream/master`.
- It will not build Android or iOS targets.
- It will not become a general roguelike framework or support other games in v1.
- It will not chase Input waits per second as a product metric; speed serves measurement.
- It will not train a learned Evaluation before the hand-tuned one has a measured Baseline (E9 is optional and last).
- It will not run leaderboards, community challenges, or online services in v1.

## 6. MVP scope

### 6.1 In scope (v1 = E0 to E5)

- E0: FR-48 to FR-53 (repository, build without the Android SDK, module skeleton with the boundary rule, docs system, CI, project skills, planning artifacts, issue mirroring, the upgrade procedure).
- E1: FR-1 to FR-5, FR-7 to FR-12 (the Headless engine and the fairness suite; consequences tagged E4 and E5 excluded), FR-6 interface reserved.
- E2: FR-14 to FR-17 (Codex, drift check, vocabulary diff, Rules with citations and the codebase map).
- E3: FR-19 to FR-25, the E3 half of FR-26 (the Rig).
- E4: FR-27 to FR-33, FR-36, the E4 half of FR-26, and the E4-tagged consequences of FR-9 (the baseline Brain with its hand-tuned Evaluation).
- E5: FR-37 to FR-43 and the E5-tagged consequences of FR-4, FR-11, FR-12, FR-23, FR-27, FR-36 (Overlay v1).

### 6.2 Out of scope for MVP

- E6 tactical Search (FR-6 realization, FR-13, FR-34, SPSA tuning of FR-33): deferred until the Harness measures the properties the design depends on.
- E7 strategy and Lore (FR-18, FR-35): deferred until a baseline Brain exists to attach heuristics to.
- E8 Overlay v2 (FR-44 to FR-47): deferred; the v1 Overlay is the debugger, v2 is the coach. `[NOTE FOR PM]` coach mode is the feature most likely to bring a second user; revisit its position once E5 ships.
- E9 learned Evaluation: optional; bound by FR-9's permuted-seed behavioral test and FR-11's disjoint label seeds.
- Classloader isolation for many Runs per JVM: a spike in E1, not a requirement; processes are the default.

## 7. Success metrics

**Primary**
- **SM-1**: Headline Win. The Brain wins a Run (the game's own victory condition) on a seed from `holdout` or a fresh set drawn at claim time (one draw of at most 50 seeds per Brain version, every Run in the draw published whether won or lost), with no prior Runs of that Brain on that seed, reproducible from its Run log; the Results page records the ledger count. Validates FR-2, FR-20, FR-23, FR-24, FR-27 to FR-33.
- **SM-2**: Highest verified Score. The highest Score of a winning Run within one registered pass of a named Seed set version, per challenge-flag set (the no-challenge set is canonical) and reported with and without ascension; the first target is set when SM-1 is first achieved. Validates FR-19 to FR-25, FR-33, FR-35.
- **SM-3**: E4 rung: the Warrior kills Goo on at least 75% of the `goo` Seed set (400 Warrior triples; lower confidence bound at least 70% at that size), with the survival curve and boss-kill staircase published. Validates FR-25, FR-31.

**Secondary**
- **SM-4**: E1 rung: Input waits per second per process and the tactics' leaf correlation and disambiguation published; determinism test green. Validates FR-2, FR-5.
- **SM-5**: E3 rung: a deliberately worse Brain is rejected; the random-agent Baseline and the measured paired-seed correlation are published; a skeptic reproduces a Results page (UJ-3). Validates FR-21, FR-22, FR-25.
- **SM-6**: E5 rung: a full sewers Run stepped and watched in the Overlay with a mid-fight takeover and no desync; the Replay of that Run verifies. Validates FR-37 to FR-41.
- **SM-7**: Per-class win rate on the `standard` Seed set (Warrior, Mage, Rogue, Huntress, Duelist, Cleric; Rule: `docs/rules/save-score-win.md`) with a confidence interval, published per Brain version, as the check on SM-2. Validates FR-19 to FR-25.
- **SM-8**: Every merged Brain change carries Results; every pull request runs the fairness suite. Validates FR-7 to FR-12, FR-25.

**Counter-metrics (do not optimize)**
- **SM-C1**: Input waits per second. Faster is not better once the Rig is fast enough; speed must never trade against determinism or parity. Counterbalances SM-4.
- **SM-C2**: Depth reached alone. A Brain that dives and dies deeper is worse; the Composite outcome ranks Win, Score, and bosses killed above depth, and every Results page shows the survival curve. Counterbalances SM-3.
- **SM-C3**: Score at the cost of wins. A Brain version whose Score rises while its win rate (SM-7) falls is worse; SM-2 counts only winning Runs. Counterbalances SM-2.
- **SM-C4**: Win rate as an early gate. Near zero it carries no information; gating on it stalls the program. Counterbalances SM-7.
- **SM-C5**: Number of Hooks. The budget in section 10 is a ceiling; fewer is better. Counterbalances FR-48.
- **SM-C6**: Results without fairness status. A number published without the fairness suite green is not a result. Counterbalances SM-8.

## 8. Cross-cutting non-functional requirements

- **NFR-1 Fairness.** The leak, differential (both forms), toggle, thread-confinement, boundary (ArchUnit and classpath), Codex leak, and determinism tests run on every pull request; the search leak test joins them when Search exists; Oracle mode is off by default, flagged, and impossible in ranked Rig Runs. A pull request touching the Observer, the ActionExecutor, the Brain, the `api` schema, the Codex generator, Playbooks, Lore intake, or the Replay tool gets an adversarial fairness review.
- **NFR-2 Reproducibility.** A Run is fully determined by (Upstream tag, hero class, challenge flags, seed, Action list); Run logs are hash-chained. A third party can verify any published Run from the Run log and the pinned tag by re-running it in a fresh Profile and comparing Observation hashes; a nightly job Replays a random published Run and compares Hash chains across Windows and Linux.
- **NFR-3 Headless throughput.** No rate is promised before it is measured (FR-5). The requirement is that the `smoke` direction check of UJ-1 (two Brains over `smoke`) fits within a working session on the development laptop and the `standard` acceptance run fits overnight. The Rig meets it by choosing the number of parallel processes and the Seed-set sizes (FR-20), not by assuming an engine rate. The measured rate, the median Run length, and the per-Decision cost of the Brain (the ceiling the research expects to matter) are published with the E1 benchmark and restated here by an ADR.
- **NFR-4 Overlay responsiveness.** Brain thinking never blocks the render thread; the game keeps its frame rate while the Brain thinks; Panel updates are posted to the render thread; a per-Input-wait thinking budget is configurable and the Panel shows `THINKING` when it is exceeded.
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
- **Run log format** (FR-23): JSONL, one record per Input wait, schema-versioned; the Replay tool refuses a log from an incompatible version with a clear message.
- **Seed set files** (FR-20): versioned by content; a Results page names the version.
- **Registration format** (FR-22): a committed record; its commit is the proof of pre-registration.
- **Rig command line** (FR-19, FR-21): the flags in the `rig` project skill are the contract (`--brain`, `--baseline`, `--seeds`, `--seed-start`, `--parallel`, `--out`); Oracle mode cannot be enabled through it.
- **Codex JSON** (FR-14): tag-named folders; consumers read the tag they need.
- **Playbook files** (FR-35): versioned with the Brain; a Playbook change is a pull request with Results.
- **Deprecation policy:** a breaking change to any surface is announced in the changelog, kept readable by the Replay tool for one prior version where feasible, and recorded in an ADR.

## 10. Constraints and guardrails

- **Safety: information parity.** The fairness requirements (section 4.2) are safety-class: a violation is a release blocker regardless of Results.
- **Module boundaries.** Six Shatterfish modules with exactly these permitted dependencies: `api` depends on nothing (DTOs only: Observation, Action, Decision, Run log records); `harness` on `core` and `api`; `codex` on `core`; `brain` on `api` only; `rig` on `harness` and `brain`; `overlay` on `core`, `harness`, and `brain`. The build enforces the edges.
- **Hook budget.** At most eight Hooks in v1, each listed in the registry. Expected: the mobile-module guard in `settings.gradle` (exists); the static scene helpers the Headless scene needs; the hero Input-wait observation point for the EmbeddedDriver; the Panel's attachment in the game scene; the key-binding registration; and, only if needed for speed, the sprite-wait bypass. Exceeding eight requires an ADR.
- **License and attribution.** GPL-3.0-or-later; upstream's license kept; `NOTICE.md` names Evan Debenham and Watabou; the README states unofficial and unaffiliated; no Shatterfish issue is ever filed upstream.
- **Runtime.** Java 21; one JVM; the bot in the same process as the game; libGDX at the version the pinned tag uses.
- **Cost.** Public repository, GitHub Actions minutes, one laptop; the smoke direction check fits a working session and the standard run fits overnight on that laptop (NFR-3).
- **Human control.** Mode changes only at an Input wait; a human can always take over; Oracle mode is visibly marked.

## 11. Integration and dependencies

- **Upstream SPD** at the pinned tag (v3.3.8 at writing; 4.0 stable expected as one large drop): the only rules engine; upgraded by FR-50.
- **Vanilla Pixel Dungeon** at a pinned tag of its own (`00-Evan/pixel-dungeon-gradle`, tag to be chosen in E2) as the second source for the vocabulary diff (FR-16); read only, never built into the product.
- **libGDX** headless backend and desktop natives at the tag's version; **JUnit 5** and **ArchUnit 1.5** for tests; **Gradle** as the build.
- **MkDocs Material** for the docs site; **GitHub Actions** for CI, the nightly smoke Rig, and Pages.
- **BMAD** artifacts (this PRD, the brief, the research, the UX spines, the architecture, epics, stories) mirrored on the docs site.
- **Project skills** (`next-story`, `rig`, `codex`, `upstream-sync`, `adr`, `sync-issues`, `handoff`) and subagents (`fairness-reviewer`, `upstream-reader`) are part of the delivery process, not the product.

## 12. Why now

The field is empty: no bot, RL agent, gym, or headless harness exists for SPD. Upstream is quiet while 4.0 is built privately, so the hook surface is stable for months. The research shows every foundation has a known shape and a known cost. And the symbolic-bot tradition has a clear, recent verdict (the 2021 NetHack Challenge and everything since) about what works.

## 13. Open questions

1. What rate does a fast-forwarded Headless scene reach on SPD, and does the actor thread ever block under it? (E1 spike; NFR-3 restated afterwards.)
2. What is the paired-seed correlation for two Shatterfish Brains, and hence the real sample-size saving; is `smoke` at 25 informative even as a direction check? (E3 measurement, SM-5.)
3. What are the leaf correlation, bias, and disambiguation of SPD tactics? (E1 measurement with random playouts; decides FR-34's design.)
4. Does the seed determine anything beyond dungeon generation, and which generators must the Harness seed? (Upstream reader on `Random`, `Dungeon.seed`, generator resets; decides FR-2's seeding ADR.)
5. Which think budgets, if any, does v1 publish? (Likely none before E6.)
6. Which human win-rate source calibrates "beats the median human"? (E7.)
7. Is coach mode part of E8 or its own Epic? (Epics workflow.)
8. What Score target and which class define the first SM-2 goal after the first Win? (Set at SM-1.)
9. What are Fishtest's current default bounds, as text, for the E3 statistics ADR's starting values?
10. Does SPD's save file preserve enough random-generator state for a Replay to verify across a save-and-resume boundary? (Decides whether FR-37's resumed Runs are Replay-verifiable in v1 or E8.)
11. Where does the `standard` Seed set run once its cost is known: the developer's machine, GitHub Actions, or both? (E3 ADR.)
12. Resolved in session 10 (`docs/codebase-map.md`, "PRD open question 12"): boss stair lock, challenge score multiplier, the Win condition and the 2.5x ascension multiplier, the six classes, branches at depths 11 to 14 and 16 to 19, the HUD sizes and the `Chrome` types are confirmed; the camera offset is only partly what the PRD assumed (vertical-only, conditional), so FR-38 now has the Overlay set its own. New facts for the architecture: game logic runs on a separate actor thread and turns resolve through sprite callbacks (FR-1, FR-5); `Dungeon.init` resets the generator stack after seeding (FR-2); actor tie-breaks and `Random.chances(HashMap)` depend on identity hashes (FR-2 tests must span two JVMs); a floor depends on the action history, not the seed alone (FR-23); `GameScene`'s HUD fields are private (FR-38 needs a hook).

## 14. Assumptions index

No `[ASSUMPTION]` tags remain: the product owner resolved the first draft's assumptions or delegated them, and the memlog records each decision. Statements about the game that this PRD relies on were Tier 3 hypotheses until session 10; each now cites its Rule page inline, and open question 12 records the verdicts.

- `[NOTE FOR PM]` callouts (deliberate changes to bootstrap "done when" statements): FR-5 (E1 done-when becomes measured numbers); section 6.2 (coach mode position).
- Decisions taken under delegation: unseen-monster memory in v1 Beliefs (FR-29); Panel at the right edge over the dungeon, left of the inventory pane, with collapse to the Mode strip below 200 UI pixels of map (FR-38); hotkeys in v1 with a fallback (FR-42); the Goo threshold of 75% with a 70% lower bound on a dedicated `goo` set of 400 Warrior triples (FR-20, FR-31, SM-3); the turn cap of 20,000 (Glossary Run); the deliberately worse Brains of FR-21; the SM-1 fresh-draw bound of 50; initial Seed-set sizes (FR-20); the Composite outcome order and the Per-pair statistic (Glossary, FR-21); PAUSED ignores hero input and a Run starts in PAUSED with Next Step (Glossary Mode, FR-39, FR-40); the hook budget of eight (section 10); the vanilla Pixel Dungeon source for the vocabulary diff (section 11).
