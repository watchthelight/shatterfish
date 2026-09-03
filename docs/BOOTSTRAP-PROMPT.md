# Shatterfish — bootstrap and program prompt (BMAD edition)

*Paste this entire file as the first message in Claude Code, started from an empty folder on the laptop. Once the repository exists, save it verbatim to `docs/BOOTSTRAP-PROMPT.md`. Every later session starts by reading `CLAUDE.md`, which will point back here.*

---

## 0. Mission

You are building **Shatterfish**: an open-source engine for **Shattered Pixel Dungeon (SPD)** in the spirit of Stockfish. It has four parts that ship in this order of dependence:

1. **Engine** — SPD's own game code driven headlessly, fast, reproducibly, through a fair Observation/Action interface.
2. **Brain** — a hand-built symbolic bot: belief state, scripted policies, tactical search, strategic playbooks, an evaluation function. Learned components are a late, optional phase.
3. **Rig** — Fishtest-style statistical testing: thousands of seeded runs, SPRT comparisons, published numbers. Nothing about the brain is believed until the rig says so.
4. **Overlay** — the bot runs inside the real desktop game, in the game's own UI style, and the human watches its live decision-making, pauses it, steps it, hands control back and forth, and reads its explanations.

Why Stockfish's search tricks don't transfer, and what does: chess is deterministic, fully observed, two-player, and a position fits in a few hundred bytes. SPD is stochastic, partially observed, single-player against an environment, and a run is thousands of turns over hidden, mutable, static-singleton state. The nearest real ancestor is the NetHack bot scene, where the 2021 NetHack Challenge was won by a hand-written symbolic bot and the learning-based entries were not close. So Shatterfish copies Stockfish's *infrastructure* — fast engine, clean search/eval split, a protocol to plug into a GUI, statistical testing culture — and borrows its *play* from the symbolic-bot tradition.

You are the sole engineer. The human is product owner, reviewer, and domain expert, working beside you on the same laptop. The program will span many sessions. This document is the seed for a full BMAD run; it is deliberately opinionated so that BMAD's phases have strong inputs, but BMAD's artifacts (brainstorm report, brief, PRD, UX spec, architecture, epics, stories) become the governing documents once they exist. Where a BMAD artifact and this document disagree after the human has approved the artifact, the artifact wins.

## 1. Non-negotiables

These apply to every artifact, every story, every line of code, and they are not up for re-litigation in brainstorming.

1. **Information parity — the only rule of play.** The bot may use only information a human player at the same screen could have: what the renderer draws, the game log, the journal, and general game knowledge (the wiki-level facts the Codex extracts). It never reads the true identity of an unidentified item, the position of an enemy it cannot currently see, hidden traps or secret doors, RNG state, or the seed. Mind vision, magic mapping, and similar count only when the in-game effect is active. This is enforced by architecture, not intentions: the `brain` module cannot import game code (build fails if it tries), a single class named `Observer` is the only door from game state to the bot, and every change to `Observer` ships with leak tests. An `oracle` mode may exist for debugging and for training labels; it is off by default, visibly flagged in the UI, and cannot be enabled in ranked rig runs.
2. **License and attribution.** SPD is GPLv3, by Evan Debenham (00-Evan), based on Pixel Dungeon by Watabou. Keep upstream's LICENSE, add `NOTICE.md`, and state in the README that Shatterfish is unofficial and unaffiliated. Never file Shatterfish bugs against the upstream repository.
3. **Upstream does not accept pull requests.** Shatterfish is a permanent downstream repository pinned to a release tag. Every edit to an upstream file is a *hook*: minimal, justified, labeled `touches-upstream`, and listed in `docs/UPSTREAM.md`. Prefer new modules over edits. Upgrades happen only by merging a newer upstream *tag* through the documented procedure, never `upstream/master`.
4. **Java, in-process, v1.** No Rust, no second implementation of the game's rules in any language, no separate bot process over a socket. The bot runs in the same JVM as the game. Search uses either an abstract tactical model derived from Observations or the real engine with hidden state re-sampled (see §4).
5. **Everything is measured and reproducible.** A run is fully determined by (upstream tag, seed, action list). Once the rig exists, no brain change merges without rig numbers in the PR.
6. **Native UI.** The overlay uses SPD's own toolkit: Chrome nine-patch frames, the pixel font through `PixelScene.renderTextBlock`, `RedButton`, `Icons`, sizes consistent with `StatusPane` and `Toolbar`. No Swing, JavaFX, ImGui, or web views.
7. **Issues track state; stories carry content; docs carry knowledge.** GitHub Issues say what is open and done. BMAD story files say what each story is and how it went. `docs/` says how the system works and why. `CLAUDE.md` says how to work. No TODO comment in code without an issue number.
8. **Codex over folklore.** Any claim about game mechanics is settled by reading the pinned code and citing `path:line`, never by memory or a forum post. Forum knowledge enters only through the lore pipeline with provenance and a verification tier.

## 2. Operating mode

### 2.1 Maximize thinking

The human has said explicitly that tokens and limits are not a concern; quality is. Act accordingly.

- Set `/effort` to the highest level this build of Claude Code offers, and confirm with `/model` that you are on the intended model. Use Plan Mode for every planning step. Add the word *ultrathink* to any step you judge hard (architecture, fairness design, search design, anything touching `Observer`).
- Before any non-trivial step: restate the goal and the constraints that bind it, list at least three alternatives, name the one you'd pick and why, and run a short pre-mortem ("if this is wrong in six months, why?"). Write the outcome into the artifact you are producing (ADR, story file, design note), not just the chat.
- Use subagents with fresh context for anything BMAD marks as validation (validate PRD, check implementation readiness, code review). BMAD recommends a different model for validation to avoid self-confirmation bias; you only have one model, so isolation of context and an adversarial review prompt is the substitute. Give the reviewer the non-negotiables and ask it to find the way the work violates them.
- Read upstream code before asserting anything about it. When two sources disagree, the pinned tag wins and the discrepancy is recorded.

### 2.2 Brainstorm small things too

BMAD's brainstorming workflow is not only for Phase 1. Run a timeboxed micro-brainstorm before any design decision, including small ones: the Observation schema, the run-log format, the SPRT parameters, the panel layout, how pause conditions are expressed, how hooks are guarded. Protocol:

1. State the question in one sentence and the non-negotiables it touches.
2. Generate at least five options using the CIS techniques (first principles, inversion, SCAMPER, analogies from chess engines / NetHack bots / debuggers / game trainers).
3. Score against the non-negotiables and the current epic's goal.
4. Pick one, record the decision and the rejected options in an ADR or a design note in the story file, then move on. Do not reopen it without new information.

New ideas that come up mid-story do not expand the story. They go to `docs/ideas.md` with a one-line rationale, and re-enter through BMAD's correct-course workflow or the next create-story.

### 2.3 Turn discipline

The program will take many sessions. A *turn* is one instruction from the human to one handoff from you.

- **One step per turn.** Each turn does exactly one numbered step from §7, or one BMAD workflow, or one story through its full lifecycle (create → dev → review). Never start a second story in the same turn. Never try to complete the whole bootstrap in one turn.
- If a step is too large for one turn, split it at a clean boundary, finish the first half, and hand off. Never leave the tree unbuildable at a handoff.
- **Every turn ends with a handoff**: what was done (with links), what changed in the artifacts, the exact next step, and any question that blocks it. Update `sprint-status.yaml` (once it exists), the story file, and the GitHub issue before handing off.
- When context grows heavy, run `/compact` with the instruction to preserve decisions, open questions, and the next step.
- Ask the human only at the checkpoints marked in §7 or before irreversible or costly actions: creating the remote repository, enabling GitHub Pages, installing software, deleting anything, merging an upstream tag.

### 2.4 BMAD

Install BMAD Method v6 into the project (§7, Session 1) with the **BMad Method (BMM)** module, the **Creative Intelligence Suite (CIS)** for brainstorming, and the **Test Architect (TEA)** module if the installer offers it. Select Claude Code as the tool. Run the full BMad Method track — not Quick Flow, not Enterprise. The four phases and their artifacts:

| Phase | Workflows | Artifacts |
|---|---|---|
| 1 Analysis | brainstorming, research, product brief | `brainstorming-report.md`, research findings, `product-brief.md` |
| 2 Planning | create PRD, validate PRD, create UX design (the overlay has a UI, so yes) | `PRD.md`, `ux-spec.md` |
| 3 Solutioning | document-project (brownfield, on upstream), create architecture, create epics and stories, check implementation readiness | upstream documentation, `architecture.md`, `epics.md`, readiness gate |
| 4 Implementation | sprint planning, then per story: create story → dev story → code review; correct-course as needed; retrospective per epic | `sprint-status.yaml`, `story-<slug>.md`, code, reviews |

Exact skill names differ between BMAD versions (`/bmad-help`, `/bmad-bmm-create-prd`, `/bmad-brainstorming`, and so on). After installing, run `/bmad-help` and use the names it reports. Whenever unsure what BMAD wants next, ask `/bmad-help`. Follow BMAD's principle that one story moves through its entire lifecycle before the next begins, and that state is determined by artifacts on disk, not by chat history.

This document is the primary input to Phase 1. Sections 3–6 are inputs to Phases 2 and 3: treat them as the product owner's constraints and starting positions, and improve on them where the workflows surface better answers.

## 3. Facts about the upstream game (verify each against the pinned tag before relying on it)

- Java + libGDX with a scene-graph layer called Noosa. Gradle multi-module: `SPD-classes`, `core`, `desktop`, `android`, `ios`, `services`. The Android/iOS modules must not be required for desktop or headless builds; the Android SDK is not installed and must not be needed.
- **There are no data files.** Every enemy, item, drop table, and spawn weight is Java code (a mob's constructor sets HP, defense, EXP; its `damageRoll()` is a method). Player-facing text lives in `core/src/main/assets/messages/**/*.properties`, keyed by class name. `Assets.java` lists every asset path; sprite frame sizes live in each `*Sprite` class. The Adventurer's Guide and lore pages come from `Document.java` plus `journal.properties`. The in-game changelog lives in the `changes` package and dates every version.
- **Seeding is partial.** Level generation and item spawns are seeded; the general-purpose generator that combat rolls use is time-seeded (`com.watabou.utils.Random`, see `resetGenerators()`). The harness must seed it or runs are not reproducible. `Random.NormalIntRange` is triangular (sum of two uniforms), which matters for expected-damage math.
- **Game loop.** `Actor.process()` advances actors; the hero waits for input; input arrives as `Hero.handle(cell)` / `Hero.curAction`; item use is `Item.execute(hero, action)`. Rendering runs on a single render thread; `Game.runOnRenderThread` exists. Game state is static singletons (`Dungeon`, `Actor`) and is not thread-safe.
- **Player visibility is already computed by the game**, because it has to draw it. `Dungeon.observe()` fills `Level.heroFOV`, `visited`, `mapped`, and already accounts for mind vision, blindness, darkness, and magic mapping. Mobs are drawn only if in `heroFOV` and not invisible. Secret doors render as walls until found; traps have a `visible` flag; heaps have a `seen` flag. Items expose `name()`, `isIdentified()`, `levelKnown`, `cursedKnown`. Build the Observation from these, never from raw model fields.
- **Guarantees are in code.** Strength potions: `Dungeon.posNeeded()` (two per chapter, one per floor pair). Upgrade scrolls: `Dungeon.souNeeded()`. Special rooms guarantee their solution item on the same floor via `Level.addItemToSpawn()` (e.g., `TrapsRoom` → levitation, `PoolRoom` → invisibility). Item spawn weights and the guarantee "deck" live in `Generator`. Enemy spawn tables per depth: `Bestiary.getMobRotation(depth)`. Alchemy recipes: `Recipe` subclasses. Hit chance: `Char.hit`.
- **UI toolkit.** `Window`, `Chrome`, `RedButton`, `IconButton`, `Icons`, `PixelScene.renderTextBlock`, `StatusPane`, `Toolbar`, `GameScene`, `KeyBindings`, `SPDAction`. Save/load is `Bundle`.

## 4. Architecture guardrails (inputs to the architecture workflow, not a substitute for it)

Gradle modules added alongside upstream's:

| Module | May depend on | Contents |
|---|---|---|
| `api` | nothing | DTOs only: `Observation`, `Action`, `Decision` (chosen action, top alternatives with scores and one-line reasons, current goal, safety flags), run-log records |
| `harness` | `core`, `api` | `Observer` (the only class allowed to read game state into the bot), `ActionExecutor` (the only class that drives the hero), RNG control, snapshot/restore, redetermination, and two drivers: `HeadlessDriver` (libGDX headless backend, no scene) and `EmbeddedDriver` (inside the real desktop game) |
| `codex` | `core` | Reflection dump of every `Mob`, `Item`, `Generator` table, mob rotation, traps, recipes, and changelog, parameterized by depth and challenge flags; properties parser; PD-vs-SPD vocabulary diff; writes `codex/<tag>/*.json` and generated docs |
| `brain` | `api` only | Beliefs, scripted policies, tactical search, strategic playbooks, eval. Identical code runs headless and in the overlay |
| `rig` | `harness`, `brain` | Parallel runner, seed sets, statistics, SPRT, results, JSONL run logs, replay |
| `overlay` | `core`, `harness`, `brain` | The in-game UI and a `ShatterfishLauncher` for desktop |

Plus `lore/` (knowledge base, markdown with frontmatter) and `docs/` (MkDocs site, including BMAD's output folder).

**Observation** (immutable, serializable, content-hashed): the known map as the player sees it (terrain through the tilemap's own mapping, fog memory, discovered traps, seen heaps), visible actors with what the UI shows (name, HP as displayed, visible buffs, sleeping), hero stats and buffs, inventory with identification status exactly as the UI presents it (an unknown potion is "turquoise potion"), equipment and quickslots, journal Notes and Catalog state, recent game log lines, depth, turn.

**Action**: move-to, attack, use / throw / zap / read / drink / equip / drop item at target, rest, search, descend / ascend, talent or ability use, wait. Executed through the same code paths the UI uses.

**Brain is observation-driven.** It re-plans from the current Observation every turn and never assumes it made the previous move. This is what makes human takeover in the overlay work without desync.

**Search must not see hidden state.** Two fair designs: an abstract tactical model built only from the Observation and beliefs, or engine rollouts with *redetermination* — before each rollout, re-sample everything hidden (unknown item identities, unseen mob positions, RNG) from the belief state, as bridge and Scrabble engines do. Rollouts on the raw saved game are cheating and forbidden.

**Beliefs**: per-unidentified-item candidate sets with probabilities that shrink as types are identified; floor facts (a pool room seen implies an invisibility potion on this floor); chapter counters for guaranteed drops; a `safeTest(item, cell)` that scores the worst case over candidate identities using terrain (water, grass, doors, chasms) and visible enemies. "Stand next to water and a door before zapping an unknown wand" is a human compression of that computation; the computation is what gets implemented, and it covers unknown potions and scrolls with the same code.

**Fairness tests** (in `harness`, required, run in CI):
- Leak tests: an unidentified scroll, a mob behind a wall, a secret door, a hidden trap, an invisible enemy — none may appear in the serialized Observation.
- Differential test: two worlds identical to the player but different in hidden state must serialize to byte-identical Observations.
- Toggle tests: the same world with and without `MindVision`, `Blindness`, and magic mapping produces exactly the expected differences.
- ArchUnit test: `brain` may import nothing from `com.shatteredpixel.*` or `com.watabou.*`.
- Determinism test: same (tag, seed, action list) twice gives identical Observation hashes at every turn.

## 5. The overlay (input to the PRD and UX spec)

The overlay runs inside the real desktop game via `EmbeddedDriver`. When the hero is waiting for input, the brain thinks on a worker thread using only the immutable Observation; the chosen action is then applied on the render thread through `ActionExecutor`. The brain never touches game objects.

**Panel** (docked, native style, respects the game's UI scale setting):
- Mode line: RUNNING / PAUSED / HUMAN; speed as a turns-per-second cap; turn; depth.
- Current goal in plain words ("Explore: guaranteed strength potion still on this floor").
- Chosen action plus the top three alternatives with scores and one-line reasons.
- Belief summary: unknown items with top candidates and probabilities, floor facts, chapter counters.
- Safety flags ("by water: fireblast-safe; chasm behind target: blast-wave unsafe").
- Scrolling decision log.
- Map highlights: planned path, target, considered cells.

**Controls**: Pause / Resume; Step (one action); Run N; Speed; Take over / Hand back; Pause-on conditions (before any item use, before testing an unknown item, before stairs, on boss floors, when HP drops below X%, when a new enemy appears); Explain (expand the current decision); Replay (load a run log and scrub through decisions). Hotkeys through `KeyBindings` / `SPDAction` if that is a small hook; otherwise buttons only until it can be.

**Interjection semantics**: while paused or in HUMAN mode, the human plays with the normal controls. The brain keeps observing and updating beliefs after every hero turn regardless of who acted, and on resume it re-plans from the current state. Human actions are recorded in the run log so replays stay exact.

**Oracle overlays** (true item identities, unseen enemies) exist only when launched with an explicit `--oracle` flag, draw a red border and an "ORACLE" label, and cannot be enabled in the rig.

The overlay v1 ships right after the baseline brain, before tactical search, because it is the debugger for everything that follows.

## 6. Program map (input to the epics workflow)

BMAD's create-epics-and-stories workflow will produce the real epics and stories. This is the product owner's starting position; keep the shape, improve the contents. Each epic has a measurable "done when."

| Epic | Scope | Done when |
|---|---|---|
| **E0 Bootstrap** | Repository, upstream pinned, builds without Android SDK, module skeleton with the ArchUnit rule, docs system, CI, `CLAUDE.md`, project skills, BMAD installed, all BMAD planning artifacts through readiness check, issues mirrored | §11 checklist complete |
| **E1 Harness** | Headless boot, `Observer`, `ActionExecutor`, seedable RNG, determinism, fairness tests, random-action agent | 1,000 seeded random-action Warrior runs complete in seconds; same seed twice is identical; all fairness tests pass |
| **E2 Codex** | Reflection dump parameterized by depth and challenges; properties parse; guide and lore pages; assets index; changelog dump; PD-vs-SPD vocabulary diff; generated docs; `docs/codebase-map.md` | `codex/<tag>/` is regenerated by one Gradle task and CI fails if it drifts from the committed version |
| **E3 Rig** | Parallel runner, seed sets, per-run results (depth, turns, cause of death), SPRT comparison of two brains, JSONL run logs, replay-from-log with hash verification, nightly GitHub Action, `docs/results/` | Random-agent baseline published; a deliberately-worse agent is rejected by SPRT |
| **E4 Baseline brain** | Scripted explore / fight-in-corridors / eat / descend; beliefs v1; `safeTest` | Kills Goo on a large majority of seeds; numbers in `docs/results/` |
| **E5 Overlay v1** | `EmbeddedDriver`, launcher, panel, pause / resume / step / run N / speed / take over, path highlight, native styling, run logging including human actions | The human watches a full sewers run and takes over mid-fight without desync |
| **E6 Tactical search** | Abstract model or redetermined rollouts; expectimax 2–4 turns when enemies are visible; hand-tuned eval from Codex tables and `Char.hit`; scripted policy still handles quiet turns | SPRT-significant improvement over E4 |
| **E7 Strategy and lore** | Item-ID logic, upgrade allocation, per-boss and per-class playbooks; lore pipeline (claims with provenance, variant classifier from the PD/SPD Codex diff, date gating from the changelog, verification tiers, `lore/` frontmatter); intake for the human's super-search skill | Measured win rate on default settings; every implemented heuristic links to a lore entry with a verification tier |
| **E8 Overlay v2** | Explain panel, pause-on conditions, replay scrubber, beliefs view, hotkeys | Feature-complete per §5 |
| **E9 Learned eval (optional)** | Value function trained on oracle hindsight labels, run on Observations only | Beats the hand-tuned eval under SPRT |

Labels for GitHub: `epic:E0`…`epic:E9`, `area:{harness,codex,brain,rig,overlay,lore,docs,ci,bmad}`, `type:{epic,story,bug,spike,adr}`, `touches-upstream`, `fairness`, `good-first-issue`.

## 7. Session plan

One session per numbered step unless the handoff says otherwise. Checkpoints (A–F) are where the human must answer before you continue.

### Session 1 — Environment, tooling inventory, BMAD, repository

1. **Environment.** Detect OS and shell; adapt every command accordingly. Check `git`, `gh` (`gh auth status`, note scopes), Node.js (20+ for BMAD), Python 3 and `uv` (BMAD prerequisites; Python also for MkDocs), and any JDK present. Do not install anything.
2. **Tooling inventory — know your own hands.** You cannot assume what is installed on this laptop, so enumerate it: read the frontmatter of every `~/.claude/skills/*/SKILL.md`, any `~/.claude/agents/*.md`, `~/.claude/CLAUDE.md`, installed plugins (`/plugin` or the plugin directory), MCP servers (`claude mcp list`), and the bundled skills this build lists under `/help`. Write `docs/tooling.md` (create the folder now; it moves into the repo in step 6): a table of each skill, subagent, plugin, and server with one line on what it does and which Shatterfish task it serves. Identify the human's **super-search skill** by its description and record how to invoke it; it is the intake for the lore pipeline in E7 and for the research workflow in Session 5. Flag skills that overlap with BMAD workflows so you use one, not both.
3. **BMAD install.** Run `npx bmad-method install` interactively: directory = this folder; modules = BMM, CIS, and TEA if offered; tool = Claude Code; set the output folder to `docs/bmad/` (record whatever the installer actually uses). Then run `/bmad-help` and confirm the skills are present. Record the BMAD version and skill names in `docs/tooling.md`.
4. **Upstream.** `git init`, add remote `upstream` = `https://github.com/00-Evan/shattered-pixel-dungeon.git`, `git fetch upstream --tags`, list tags newest-first, identify the latest stable release (no beta/rc suffix).
   → **Checkpoint A.** Report: environment findings, the tooling table, the super-search skill you identified, BMAD status, and the proposed tag. Ask for: GitHub username (propose the result of `gh api user`), confirmation that the repository is `shatterfish` and public, the JDK situation (you will read upstream's `docs/` and Gradle files for the required version after checkout and may need the human to install one), and the human's name for BMAD's config.
5. **Pin and create.** `git checkout -b main <tag>` so `main` starts at the pinned release with full upstream history (future upgrades are `git merge <newtag>`). Add `NOTICE.md`, rewrite `README.md` for Shatterfish (keep upstream's build guides linked), create `docs/UPSTREAM.md` (tag, commit, date, the upgrade procedure, an empty hooks table). Move `docs/tooling.md` and this file (`docs/BOOTSTRAP-PROMPT.md`) into place. Commit BMAD's files per BMAD's own guidance on what to commit.
6. **Remote.** `gh repo create <user>/shatterfish --public --source=. --remote=origin --push`, then `gh repo edit` to set the description and topics and confirm Issues are enabled. Create the label set from §6 and milestones E0–E9. Create the E0 epic issue with a task list mirroring Sessions 1–14 so progress is visible from day one.
7. **Handoff.**

### Session 2 — Build without the Android SDK; module skeleton; CI

1. Read upstream's `docs/` guides and Gradle files; determine the required JDK; confirm with the human if it is missing.
2. Make `./gradlew :desktop:run` and a headless build work with no Android SDK. The accepted approach is a guarded `include` in `settings.gradle`; this is hook #1 — document it in `docs/UPSTREAM.md`.
3. Add the six modules from §4 with exactly the dependency edges shown, JUnit 5, and an ArchUnit test that fails if `brain` imports `com.shatteredpixel.*` or `com.watabou.*`. Add a placeholder `HeadlessDriver` that boots the libGDX headless backend and exits.
4. GitHub Actions: build + tests on every PR and on `main`. `./gradlew build` must pass locally and in CI.
5. Micro-brainstorm (§2.2) the CI shape: what runs on PR, what runs nightly, how rig results get published. Record in ADR-0002 (ADR-0001 is "record architecture decisions").
6. Handoff.

### Session 3 — Documentation system

1. MkDocs Material with `mkdocs.yml`; `mkdocs build --strict` in CI; GitHub Pages deployment via Actions (enable Pages with `gh api -X POST repos/<user>/shatterfish/pages -f build_type=workflow`, or ask the human if that fails). If Python is unavailable, plain markdown with a generated index, and an issue to add MkDocs later.
2. Skeleton: `index.md`, `architecture.md` (placeholder until BMAD's architecture exists; then a pointer), `fairness.md`, `upstream.md`, `roadmap.md`, `codebase-map.md`, `glossary.md` (Observation, Belief, Codex, Rig, Lore, Oracle mode, Redetermination, Hook, Story, Epic), `adr/` (MADR), `rules/` (every claim carries a `path:line` citation and a test link), `codex/` (generated, never hand-edited), `results/`, `ideas.md`, `tooling.md`, `BOOTSTRAP-PROMPT.md`, and BMAD's output folder in the nav.
3. `docs/README.md`: how the docs system works, what is generated, what is hand-written, and the rule that docs change in the same PR as the code they describe.
4. Handoff.

### Session 4 — Claude Code setup

1. `CLAUDE.md`, short: what and why in five lines; build, test, run, rig commands; the non-negotiables from §1 verbatim; where docs, stories, and issues live; the session ritual from §8; the turn discipline from §2.3; the pointer to `docs/BOOTSTRAP-PROMPT.md` and `docs/tooling.md`.
2. Project skills under `.claude/skills/`: `next-story` (read `sprint-status.yaml` and the issue list, pick the next story, and open it in BMAD's create-story), `rig` (run N seeds and summarize), `codex` (regenerate and diff), `upstream-sync` (the tag-upgrade procedure: merge, re-verify every hook, regenerate the Codex, rerun fairness and determinism tests, publish a diff of Codex changes), `adr` (draft a MADR), `sync-issues` (mirror epics and stories to GitHub issues and milestones; idempotent), `handoff` (write the end-of-turn handoff in the standard shape).
3. Subagents under `.claude/agents/`: `fairness-reviewer` (reviews any diff touching `Observer`, `ActionExecutor`, or `brain` against §1, adversarially), `upstream-reader` (answers mechanics questions only by reading the pinned code and citing `path:line`).
4. Micro-brainstorm whether any of the human's existing skills should be wrapped or referenced by these project skills rather than duplicated; record in `docs/tooling.md`.
5. Handoff.

### Session 5 — BMAD Phase 1: brainstorming

1. Run BMAD's brainstorming workflow with this document as the seed. Questions to push on: what "winning" means for Shatterfish (win rate on default settings? per class? ascension?), which player audience the overlay serves (learners, speedrunners, the developer debugging the bot), what would make the rig trustworthy to a skeptic, what the fairness rule implies for search, what could make the project stall.
   → **Checkpoint B.** Present the brainstorming report and the three to five directions you recommend keeping; the human chooses.

### Session 6 — BMAD Phase 1: research

1. Run BMAD's research workflow on the technical questions the brainstorm surfaced. At minimum: libGDX headless backend usage and pitfalls; ArchUnit; SPRT as Fishtest uses it and how to adapt it to win-rate and depth metrics; information-set / redetermination search; the NetHack Challenge results and AutoAscend's architecture; existing SPD forks, bots, seed tools, and RL attempts; Noosa UI patterns for docked panels. Use the human's super-search skill where its description fits; record source, date, and what was learned in the research findings.
2. Handoff.

### Session 7 — BMAD Phase 1: product brief

1. Run BMAD's product-brief workflow. Inputs: this document, the brainstorm report, the research findings.
2. Handoff.

### Session 8 — BMAD Phase 2: PRD

1. Run create-PRD. Functional requirements come from §4–§6; non-functional requirements must include: fairness (with the test list), reproducibility, headless throughput target (state one and justify it), overlay responsiveness (thinking never blocks the render thread), upstream-upgrade procedure, and documentation currency.
2. Handoff (the PRD is likely to need a second turn).

### Session 9 — BMAD Phase 2: PRD validation and UX spec

1. Run validate-PRD in an isolated subagent with an adversarial prompt; fix what it finds.
2. Run create-UX-design for the overlay: panel anatomy, states, controls, pause conditions, explain view, replay scrubber, oracle flagging, hotkeys, all within the constraints of the game's toolkit (§1.6).
   → **Checkpoint C.** Present the PRD and UX spec for approval.

### Session 10 — BMAD Phase 3: document the upstream codebase

1. Run BMAD's document-project (brownfield) workflow against upstream, scoped to the areas Shatterfish touches: game loop and hero input, visibility, items and identification, mobs and AI states, level generation and special rooms, `Generator` and guarantees, RNG, buffs, UI toolkit, save/load, changelog. Use parallel subagents. Every statement cites `path:line` in the pinned tag.
2. Fold the result into `docs/codebase-map.md` (file → mechanic) and correct anything in §3 that the code contradicts.
3. Handoff.

### Sessions 11–12 — BMAD Phase 3: architecture

1. Run create-architecture with §4 as the starting position. Decisions that need explicit ADRs with rejected alternatives: module boundaries and the ArchUnit rule; Observation schema and hashing; how `Observer` handles each visibility rule; RNG seeding strategy; snapshot/restore and redetermination; abstract tactical model versus engine rollouts (may be deferred to E6 with criteria); run-log format; the rig's statistics; threading model for the overlay; how hooks are guarded and tracked.
2. Micro-brainstorm each decision (§2.2) before writing it down.
   → **Checkpoint D.** Present the architecture and the ADR set for approval.

### Session 13 — BMAD Phase 3: epics, stories, readiness

1. Run create-epics-and-stories with §6 as the starting position. Stories must be small enough for one turn each (§2.3), with acceptance criteria that name the tests and the rig numbers they require.
2. Run check-implementation-readiness in an isolated subagent.
   → **Checkpoint E.** Present the epics and the first two epics' stories for approval.

### Session 14 — Sprint planning and issue mirroring

1. Run sprint-planning; `sprint-status.yaml` exists after this.
2. Run `/sync-issues`: one milestone per epic, one issue per story with a link to the story file, epics as issues with task lists. From now on, story status changes update the issue.
3. Update `docs/roadmap.md` to mirror the epics.
   → **Checkpoint F.** Confirm the sprint and the first story.

### Session 15 and onward — one story per turn

For each story: create-story → dev-story (branch per story, PR per branch, `Closes #N`) → code-review in an isolated subagent (fairness-reviewer for anything near `Observer`, `ActionExecutor`, or `brain`) → merge when CI is green and, from E3 onward, rig numbers are in the PR → update story file, `sprint-status.yaml`, issue → handoff. Run BMAD's retrospective at the end of each epic and fold its lessons into `CLAUDE.md` and the next epic's stories. Run correct-course whenever a story reveals the plan was wrong; never silently drift.

The first E1 story should be: boot `core` on the libGDX headless backend, stub the static rendering calls that would otherwise fail (`GameScene`, `Sample`, `GLog` paths), start a seeded Warrior run, take random valid actions until death, print depth reached; then make it reproducible by seeding the general RNG so the same seed twice yields an identical run.

## 8. Working agreements

- **Session ritual.** Read `CLAUDE.md`; run `/bmad-help` for where the method thinks you are; read `sprint-status.yaml` and `gh issue list --milestone "<current epic>" --state open`; confirm the one step for this turn; do it; hand off.
- **Branch per story, PR per branch.** Commit messages reference the issue. PR body: a three-line summary first, then what changed, how it was tested, rig numbers when applicable, screenshots for overlay work, and the list of any upstream files touched. CI must be green. Docs and ADRs change in the same PR as the code. No force-push to `main`.
- **Never guess a game mechanic.** Ask the `upstream-reader` subagent or read the code yourself; cite `path:line`. If the pinned tag disagrees with §3, the tag wins and the discrepancy is recorded in `docs/codebase-map.md`.
- **Generated files are never hand-edited.** Codex output and generated docs are rebuilt by Gradle tasks and checked in CI.
- **Rig numbers are published** as `docs/results/<date>-<sha>.md` with seed set, tag, both brains, and the SPRT outcome.
- **Lore intake format** (so the human's super-search skill can feed it): one markdown file per claim in `lore/` with frontmatter `source_url`, `source_type`, `date`, `variant` (`spd` / `pd` / `mod:<name>` / `unknown`), `version_claimed`, `claim`, `tier` (1 = code confirms, 2 = harness confirms, 3 = hypothesis for the rig, F = false or obsolete for a given tag), `implemented_in`. Body: paraphrase, with at most a short quote. Variant is decided by the PD/SPD vocabulary diff, subreddit flairs, mod names, and dates against the changelog. Evan's blog at shatteredpixel.com is the highest-value source for design intent and is unambiguous about variant.
- **Ideas go to `docs/ideas.md`**, not into the current story.

## 9. What not to do

- Don't write the brain before E1–E3 exist, and don't create story issues beyond the current and next epic.
- Don't reimplement game rules anywhere, in any language.
- Don't touch `android` or `ios` beyond excluding them from the build.
- Don't add any UI framework other than the game's own.
- Don't let the brain read game objects "just for now."
- Don't hand-edit generated files, don't merge `upstream/master`, don't file upstream issues, don't commit secrets.
- Don't do two steps in one turn, and don't skip a checkpoint.

## 10. Pointers into the codebase (hints to start from; verify each against the pinned tag)

`Dungeon.java` (`init`, `observe`, `posNeeded`, `souNeeded`, `seed`, `LimitedDrops`), `Actor.java` (`process`), `Hero.java` (`handle`, `curAction`, `ready`, `act`), `Char.java` (`hit`, `invisible`, buffs), `Mob.java` (states, `loot`, `lootChance`, `description`), `Level.java` (`heroFOV`, `visited`, `mapped`, `addItemToSpawn`, `updateFieldOfView`, `feeling`), `Trap.visible`, `Terrain.SECRET_DOOR`, `Heap.seen`, `Item.java` (`name`, `isIdentified`, `levelKnown`, `cursedKnown`, `execute`), `ItemStatusHandler`, `Generator.java`, `Bestiary.java`, `Recipe.java`, `Document.java`, `Notes.java`, `Catalog.java`, `GLog.java`, `MindVision`, `Blindness`, `Assets.java`, `com.watabou.utils.Random`, `com.watabou.noosa.Game` (`runOnRenderThread`), `GameScene.java`, `PixelScene.java`, `Window.java`, `Chrome.java`, `RedButton.java`, `StatusPane.java`, `Toolbar.java`, `KeyBindings.java`, `SPDAction.java`, `Bundle.java`, the `changes` package, the `messages` asset folder.

## 11. Definition of done for E0 (the bootstrap epic)

- [ ] Public repository `shatterfish` exists; `main` is based on the pinned upstream tag; `docs/UPSTREAM.md`, `NOTICE.md`, and the rewritten README are present
- [ ] BMAD installed with BMM and CIS (TEA if offered); `/bmad-help` works; version and skill names recorded in `docs/tooling.md`
- [ ] `docs/tooling.md` inventories every local skill, subagent, plugin, and MCP server, and names the super-search skill
- [ ] `./gradlew build` passes without the Android SDK; six modules exist with the specified dependency edges; ArchUnit rule enforced
- [ ] CI runs build, tests, and docs on every PR; docs site builds with `--strict`; Pages deployment configured
- [ ] `CLAUDE.md`, the seven project skills, and the two subagents exist
- [ ] BMAD artifacts complete and approved: brainstorming report, research findings, product brief, PRD (validated), UX spec, upstream documentation, architecture with ADRs, epics and stories, readiness check passed, sprint plan
- [ ] Labels, milestones E0–E9, epic issues, and story issues for the current and next epic exist; `docs/roadmap.md` mirrors them
- [ ] Every session ended with a handoff, and every checkpoint was answered by the human before proceeding
