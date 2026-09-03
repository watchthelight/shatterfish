---
title: 'Reconciliation: BOOTSTRAP-PROMPT.md sections 1, 4, 5, 6 against the PRD'
input: docs/BOOTSTRAP-PROMPT.md (sections 1, 4, 5, 6 only)
prd: prd.md, addendum.md (same folder)
created: '2026-09-03'
---

# Reconciliation: bootstrap prompt vs PRD

**Method.** Every requirement-like statement in bootstrap sections 1 (non-negotiables), 4 (architecture guardrails), 5 (overlay), and 6 (program map) was traced to the PRD or its addendum. Status legend:

- **Carried** — present in substance.
- **Weakened** — present but compressed, made vaguer, demoted (e.g. from requirement to metric), or with a specific detail dropped.
- **Gap** — absent from PRD and addendum.
- **Contradiction** — PRD says something different; sub-tagged *tagged* (PRD carries an `[ASSUMPTION]`) or *untagged*.
- **Research-overturned** — the technical research (research.md, 2026-09-03) invalidated the bootstrap statement; noted whether the PRD carries the overturn and whether it says so.

Line numbers refer to `prd.md` unless prefixed `add.` (addendum.md) or `bs.` (BOOTSTRAP-PROMPT.md). The PRD's section 0 (L18) states that "the eight non-negotiables ... bind every requirement here and are not restated," so section-1 items carried only by that reference are marked *Carried (by reference)*; the substantive checks below say whether the PRD also carries them in its own text.

---

## 1. Non-negotiables (bs. L20-31)

| # | Input phrase | PRD location | Status | Note |
|---|---|---|---|---|
| 1a | "The bot may use only information a human player at the same screen could have: what the renderer draws, the game log, the journal, and general game knowledge (the wiki-level facts the Codex extracts)" | Glossary Observation L55; FR-3 L121; Glossary Codex L72 ("The Brain's only source of general game knowledge") | Carried | Minor internal tension: Codex is "only source of general game knowledge" (L72) while FR-18/FR-35 admit Lore-derived heuristics; bootstrap NN8 permits both, so not a bootstrap contradiction. |
| 1b | "It never reads the true identity of an unidentified item, the position of an enemy it cannot currently see, hidden traps or secret doors, RNG state, or the seed" | FR-3 L124-126; FR-8 L169; FR-9 L176 ("different RNG state") | Weakened | "the seed" is not named in any leak or differential test (FR-8 lists scroll, mob, secret door, trap, invisible enemy; FR-9 names RNG state). Seed itself absent from the leak list. |
| 1c | "Mind vision, magic mapping, and similar count only when the in-game effect is active" | FR-3 L126; FR-10 L180 | Carried | FR-3 adds darkness. |
| 1d | "the `brain` module cannot import game code (build fails if it tries)" | FR-7 L157-163 | Carried (strengthened) | Three layers: declared deps, resolution-time classpath check, ArchUnit bytecode rule. |
| 1e | "a single class named `Observer` is the only door from game state to the bot" | Glossary L60; FR-3 L121 | Carried | |
| 1f | "every change to `Observer` ships with leak tests" | FR-8 L166 | Carried | |
| 1g | "An `oracle` mode may exist for debugging and for training labels; it is off by default, visibly flagged in the UI, and cannot be enabled in ranked rig runs" | Glossary L86; FR-11 L182-188; FR-43 L341; §2.2 L40; §5 L386 | Weakened | "for training labels" (the E9 use) is absent from FR-11 and from the E9 row (add. L25 "none in this PRD"). Everything else carried. |
| 2 | "SPD is GPLv3 ... Keep upstream's LICENSE, add `NOTICE.md`, and state in the README that Shatterfish is unofficial and unaffiliated. Never file Shatterfish bugs against the upstream repository" | §10 L462; §5 L387; §1 L22 | Carried | |
| 3a | "Shatterfish is a permanent downstream repository pinned to a release tag" | §1 L22; FR-48 L360; Glossary L93 | Carried | |
| 3b | "Every edit to an upstream file is a *hook*: minimal, justified, labeled `touches-upstream`, and listed in `docs/UPSTREAM.md`" | Glossary Hook L92; FR-48 L360; NFR-5 L439; add. L42 | Carried | FR-48 says "recorded in one place"; `docs/UPSTREAM.md` named only in the addendum. |
| 3c | "Prefer new modules over edits" | SM-C4 L430 (counter-metric: fewer hooks) | Weakened | Stated as a counter-metric, not as a rule. |
| 3d | "Upgrades happen only by merging a newer upstream *tag* through the documented procedure, never `upstream/master`" | FR-50 L366; NFR-5 L439; §5 L387 | Carried | |
| 4a | "Java, in-process, v1. No Rust, no second implementation of the game's rules in any language, no separate bot process over a socket" | §5 L383-384; §10 L463; §2.1 L36 | Carried | |
| 4b | "Search uses either an abstract tactical model derived from Observations or the real engine with hidden state re-sampled" | Glossary Search L69; FR-34 L298 | Carried (widened) | FR-34 enumerates three designs (one-ply over Codex tables, sampled search over redetermined state, information-set search). All fit the parity rule; the "either/or" of NN4 becomes "chosen on measured properties" per research rec. 4. Not tagged as a deviation; NN4 says it is "not up for re-litigation," but the widening does not violate parity. |
| 5a | "A run is fully determined by (upstream tag, seed, action list)" | Glossary Run L77; FR-2 L113; NFR-2 L436 | Carried | |
| 5b | "Once the rig exists, no brain change merges without rig numbers in the PR" | SM-7 L424; UJ-1 L47; FR-35 L305 (Playbooks only) | Weakened | Carried as a success metric (SM-7) and as an FR only for Playbook changes (FR-35). No FR or guardrail makes it a merge gate for Brain code generally. |
| 6 | "The overlay uses SPD's own toolkit: Chrome nine-patch frames, the pixel font through `PixelScene.renderTextBlock`, `RedButton`, `Icons`, sizes consistent with `StatusPane` and `Toolbar`. No Swing, JavaFX, ImGui, or web views" | §4.6 L312 ("the game's own frames, font, buttons, and sizes"); FR-38 L318-321; §5 L385 | Carried (compressed) | Specific class names (`Icons`, `StatusPane`, `Toolbar`) not named; the addendum's E5 threading row (add. L41) and research rec. 7 hold the concrete list. Acceptable at PRD altitude. |
| 7a | "GitHub Issues say what is open and done. BMAD story files say what each story is and how it went. `docs/` says how the system works and why. `CLAUDE.md` says how to work" | §4.8 L370; FR-53 L379; FR-52 L376 | Carried | `CLAUDE.md` role not mentioned; fine at PRD altitude. |
| 7b | "No TODO comment in code without an issue number" | — | Gap | Absent from PRD and addendum. |
| 8a | "Any claim about game mechanics is settled by reading the pinned code and citing `path:line`, never by memory or a forum post" | FR-17 L219; NFR-6 L440; Glossary Rule L75 | Carried | |
| 8b | "Forum knowledge enters only through the lore pipeline with provenance and a verification tier" | FR-18 L222; Glossary Lore/Tier L73-74 | Carried | |

---

## 4. Architecture guardrails (bs. L90-120)

### 4.1 Module table and dependency edges (bs. L92-103)

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| Six-module table with "May depend on" column (`api`: nothing; `harness`: core, api; `codex`: core; `brain`: api only; `rig`: harness, brain; `overlay`: core, harness, brain) | Glossary L55 (Observation "Lives in the `api` module"), L62 (Harness), L65 (Brain "Depends on the `api` module only"), L72, L76, L87; §6.1 L398 ("module skeleton with the boundary rule"); add. L16 (E0 = "Bootstrap section 11 checklist") | Weakened | Only the `brain -> api` edge is stated. The other five edges, and the count of six modules, are carried solely by reference to bootstrap §11 via the E0 done-when. The addendum (add. L76) claims section 4 modules "landed in Glossary" but the Glossary carries contents, not edges. |
| `api`: "DTOs only: `Observation`, `Action`, `Decision` (chosen action, top alternatives with scores and one-line reasons, current goal, safety flags), run-log records" | Glossary L55-59 (Observation, Action, Decision, Goal, Safety flag) | Weakened | "run-log records" as `api` DTOs absent; Run log is defined (L79) but its module home is not stated. "DTOs only" constraint absent. |
| `harness`: "`Observer` ..., `ActionExecutor` ..., RNG control, snapshot/restore, redetermination, and two drivers: `HeadlessDriver` (libGDX headless backend, no scene) and `EmbeddedDriver`" | Glossary L60-64; FR-2 L116; FR-6 L145 | Carried; **Research-overturned** on "no scene" | Research (rec. 1, contrary evidence A) shows attack/zap/throw/operate resolve inside sprite-animation callbacks; the PRD replaces "no scene" with a "Headless scene" (Glossary L63; FR-1 L109; add. L33). The PRD carries the overturn but does not mark it as a deviation from bootstrap §4. |
| `codex`: "Reflection dump of every `Mob`, `Item`, `Generator` table, mob rotation, traps, recipes, and changelog, parameterized by depth and challenge flags; properties parser; PD-vs-SPD vocabulary diff; writes `codex/<tag>/*.json` and generated docs" | FR-14 L205-210; FR-16 L216; §9 L455 | Carried | "properties parser" -> "player-facing text" (L208). |
| `brain`: "Beliefs, scripted policies, tactical search, strategic playbooks, eval. Identical code runs headless and in the overlay" | Glossary L65; §4.5 L267 | Carried | |
| `rig`: "Parallel runner, seed sets, statistics, SPRT, results, JSONL run logs, replay" | FR-19 to FR-25; Glossary L76 | Carried | SPRT -> GSPRT (research rec. 3). |
| `overlay`: "The in-game UI and a `ShatterfishLauncher` for desktop" | FR-37 L315; Glossary L87 | Carried | Launcher class name not given; fine. |
| "Plus `lore/` (knowledge base, markdown with frontmatter) and `docs/` (MkDocs site, including BMAD's output folder)" | FR-18 L222; FR-51 L373; §11 L471-472 | Carried | |

### 4.2 Observation definition (bs. L105)

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| "immutable, serializable, content-hashed" | Glossary L55; FR-3 L127 | Carried | |
| "the known map as the player sees it (terrain through the tilemap's own mapping, fog memory, discovered traps, seen heaps)" | Glossary L55 ("the known map as drawn"); FR-3 L121, L125 (undiscovered trap absent); add. L35 (Observer ADR lists "FOV, mapped, secret doors, traps, heaps") | Weakened | "seen heaps" and "fog memory" (visited/mapped) not named in PRD body; only the negative form (undiscovered trap absent) and the addendum's ADR row hold them. |
| "visible actors with what the UI shows (name, HP as displayed, visible buffs, sleeping)" | Glossary L55 ("visible actors as displayed") | Weakened | The four listed fields (name, HP as displayed, visible buffs, sleeping) are dropped. The Observation-schema ADR (add. L34) will need to recover them from the bootstrap. |
| "hero stats and buffs" | Glossary L55 | Carried | |
| "inventory with identification status exactly as the UI presents it (an unknown potion is 'turquoise potion')" | Glossary L55; FR-3 L124 | Carried | |
| "equipment and quickslots" | Glossary L55 | Carried | |
| "journal Notes and Catalog state" | Glossary L55 ("journal state") | Weakened | Notes and Catalog not distinguished. |
| "recent game log lines" | Glossary L55 | Carried | |
| "depth, turn" | Glossary L55 | Carried | |

### 4.3 Action definition (bs. L107)

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| "move-to" | Glossary L56 | Carried | |
| "attack" | L56 | Carried | |
| "use / throw / zap / read / drink / equip / drop item at target" | L56 | Carried | All seven named. |
| "rest" | L56 | Carried | |
| "search" | L56 | Carried | |
| "descend / ascend" | L56 | Carried | |
| "talent or ability use" | L56 | Carried | |
| "wait" | L56 | Carried | |
| "Executed through the same code paths the UI uses" | Glossary L56; FR-4 L130-133 | Carried | FR-4 adds validity check and valid-action enumeration. |

### 4.4 Brain principles (bs. L109)

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| "It re-plans from the current Observation every turn and never assumes it made the previous move" | FR-27 L270-273; §4.5 L267 | Carried | Takeover test named as consequence. |
| "This is what makes human takeover in the overlay work without desync" | §4.5 L267; FR-40 L332; SM-6 L423 | Carried | |

### 4.5 Search fairness (bs. L111)

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| "Search must not see hidden state" | Glossary Search L69; FR-34 L298; FR-13 L198 | Carried | |
| "Two fair designs: an abstract tactical model built only from the Observation and beliefs, or engine rollouts with *redetermination*" | FR-34 L298; add. L37-38 | Carried (widened) | See NN4 row: three designs, chosen on measured properties (research rec. 4). |
| "before each rollout, re-sample everything hidden (unknown item identities, unseen mob positions, RNG) from the belief state" | FR-6 L145, L149; Glossary L69 | Carried | |
| "Rollouts on the raw saved game are cheating and forbidden" | — (implicit in Glossary L69 "hidden state is sampled from Beliefs ... before any simulation"; FR-13; §5 L386) | Weakened | The explicit prohibition is not stated; it is only implied by the definition of Search and FR-13. Since search is the highest-risk parity surface, the ban should appear verbatim in FR-34 or section 10. |

### 4.6 Beliefs (bs. L113)

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| "per-unidentified-item candidate sets with probabilities that shrink as types are identified" | FR-29 L279, L283 | Carried | FR-29 adds Codex spawn-weight priors. |
| "floor facts (a pool room seen implies an invisibility potion on this floor)" | FR-29 L279; Glossary L66 | Carried | Example dropped; fine. |
| "chapter counters for guaranteed drops" | FR-29 L279 | Carried | |
| "a `safeTest(item, cell)` that scores the worst case over candidate identities using terrain (water, grass, doors, chasms) and visible enemies" | FR-30 L286; Glossary L71 | Carried | Terrain list compressed to "visible terrain". |
| "the computation is what gets implemented, and it covers unknown potions and scrolls with the same code" | FR-30 L286 ("an unidentified item") | Carried | Generic wording covers wands, potions, scrolls. |
| (PRD addition) "memory of monsters seen and lost" | FR-29 L279 `[ASSUMPTION]`; Glossary L66 | Extension, tagged | Not in bootstrap; research-driven (AutoAscend gap). |

### 4.7 The five fairness tests (bs. L115-120) — "in `harness`, required, run in CI"

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| "Leak tests: an unidentified scroll, a mob behind a wall, a secret door, a hidden trap, an invisible enemy — none may appear in the serialized Observation" | FR-8 L165-170; NFR-1 L435 | Carried | All five cases named; CI on every PR. |
| "Differential test: two worlds identical to the player but different in hidden state must serialize to byte-identical Observations" | FR-9 L172-177; NFR-1 | Carried | Adds behavioral form from E4. |
| "Toggle tests: the same world with and without `MindVision`, `Blindness`, and magic mapping produces exactly the expected differences" | FR-10 L179-180; NFR-1 | Carried | |
| "ArchUnit test: `brain` may import nothing from `com.shatteredpixel.*` or `com.watabou.*`" | FR-7 L163 ("references game packages by bytecode"); NFR-1 ("boundary (ArchUnit and classpath)") | Weakened | The two package roots are not named. `com.watabou.*` (Noosa, `com.watabou.utils.Random`, `PathFinder`, `Bundle`) lives in `SPD-classes`, which a reader could argue is "not a game module"; FR-7's "any class in the game's packages" is vaguer than the bootstrap rule. Name both roots in FR-7. |
| "Determinism test: same (tag, seed, action list) twice gives identical Observation hashes at every turn" | FR-2 L113-118; NFR-1 | Carried | CI on every PR. |
| "(in `harness`, required, run in CI)" | NFR-1 L435 (every PR); §10 L461 (safety-class, release blocker) | Carried (location dropped) | Module home of the tests not stated; acceptable. |
| (PRD additions) thread-confinement test (FR-12), search leak test (FR-13), classpath layer (FR-7) | FR-12, FR-13, FR-7 | Extension | Research rec. 2 and 4. |

---

## 5. The overlay (bs. L122-141)

### 5.1 Threading and driver (bs. L124)

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| "The overlay runs inside the real desktop game via `EmbeddedDriver`" | §4.6 L312; FR-37 L315; Glossary L64, L87 | Carried | |
| "When the hero is waiting for input, the brain thinks on a worker thread using only the immutable Observation; the chosen action is then applied on the render thread through `ActionExecutor`" | §4.6 L312; FR-12 L195; NFR-4 L438; add. L41 | Carried | |
| "The brain never touches game objects" | FR-12 L191 | Carried | |

### 5.2 Panel contents (bs. L126-133)

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| "Panel (docked, native style, respects the game's UI scale setting)" | FR-38 L318 ("docked ... game's own UI toolkit, respects the game's interface-size setting") | Carried | Placement assumption tagged (L318, §14 L495). |
| "Mode line: RUNNING / PAUSED / HUMAN; speed as a turns-per-second cap; turn; depth" | FR-38 L318 ("Mode, speed, turn, depth"); Glossary Mode L89; FR-39 L325 (speed cap) | Carried | |
| "Current goal in plain words ('Explore: guaranteed strength potion still on this floor')" | FR-38; Glossary Goal L58; UJ-2 L48 | Carried | |
| "Chosen action plus the top three alternatives with scores and one-line reasons" | FR-38 L318; FR-32 L292 | Carried | |
| "Belief summary: unknown items with top candidates and probabilities, floor facts, chapter counters" | FR-38 L318 ("a Belief summary"); Glossary Belief L66 | Weakened | FR-38 does not enumerate what the summary shows; Glossary carries the contents. Note bootstrap E8 lists a separate "beliefs view" (see §6 E8 row). |
| "Safety flags ('by water: fireblast-safe; chasm behind target: blast-wave unsafe')" | FR-38; Glossary L59 | Carried | |
| "Scrolling decision log" | FR-38 L318 | Carried | |
| "Map highlights: planned path, target, considered cells" | FR-41 L335 | Carried | Adds "clears them when the plan changes". |

### 5.3 Controls (bs. L135)

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| "Pause / Resume" | FR-39 L325 | Carried | |
| "Step (one action)" | FR-39 | Carried | |
| "Run N" | FR-39 | Carried | |
| "Speed" | FR-39 (turns-per-second cap; does not affect animation speed) | Carried | |
| "Take over / Hand back" | FR-39 | Carried | |
| "Pause-on conditions (before any item use, before testing an unknown item, before stairs, on boss floors, when HP drops below X%, when a new enemy appears)" | FR-45 L347 (E8) | Carried | All six conditions; E8 placement matches bootstrap §6. |
| "Explain (expand the current decision)" | FR-44 L344 (E8) | Carried | |
| "Replay (load a run log and scrub through decisions)" | FR-46 L350 (E8) | Carried | |
| "Hotkeys through `KeyBindings` / `SPDAction` if that is a small hook; otherwise buttons only until it can be" | FR-42 L338 (unconditional; in the E5 range FR-37 to FR-43, §6.1 L403, add. L21) | **Contradiction (untagged); Research-overturned** | Research (section 7, [99]-[101]) confirmed the hook is small (add `SPDAction` constants; settings UI enumerates them), so the bootstrap's conditional is resolved. But the PRD moves hotkeys from E8 (bootstrap §6: "E8 Overlay v2 ... hotkeys") to E5 without an `[ASSUMPTION]` tag, and drops the "buttons only until it can be" fallback. Tag it or move FR-42 to E8. |

### 5.4 Interjection semantics (bs. L137)

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| "while paused or in HUMAN mode, the human plays with the normal controls" | FR-40 L332 | Carried | |
| "The brain keeps observing and updating beliefs after every hero turn regardless of who acted" | FR-40; FR-37 L315; Glossary Belief L66 | Carried | |
| "on resume it re-plans from the current state" | FR-40; FR-27 | Carried | |
| "Human actions are recorded in the run log so replays stay exact" | FR-40; FR-23 L254; Glossary Run log L79 | Carried | |

### 5.5 Oracle overlays (bs. L139)

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| "(true item identities, unseen enemies) exist only when launched with an explicit `--oracle` flag" | FR-43 L341; FR-11 L183 ("explicit launch flag") | Carried | Flag name `--oracle` not fixed; acceptable. |
| "draw a red border and an 'ORACLE' label" | FR-43; FR-11 L187 | Carried | |
| "cannot be enabled in the rig" | FR-11 L188; §9 L454; add. L59 | Carried | PRD narrows to "ranked" rig runs in places (FR-11, Glossary L86) and "cannot be enabled through it" for the CLI (add. L59); bootstrap NN1 also says "ranked rig runs", so consistent. |

### 5.6 Sequencing (bs. L141)

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| "The overlay v1 ships right after the baseline brain, before tactical search, because it is the debugger for everything that follows" | §6.1 L396-403 (E4 then E5 in v1); §6.2 L407-409 (E6 out of MVP; "the v1 Overlay is the debugger") | Carried | |

---

## 6. Program map (bs. L143-160)

### 6.1 Epics: scope and done-when

| Epic | Input scope / done-when | PRD location | Status | Note |
|---|---|---|---|---|
| E0 | Scope: "Repository, upstream pinned, builds without Android SDK, module skeleton with the ArchUnit rule, docs system, CI, `CLAUDE.md`, project skills, BMAD installed, all BMAD planning artifacts through readiness check, issues mirrored" | §6.1 L398; add. L16 (FR-48, 49, 51, 52, 53) | Carried (compressed) | `CLAUDE.md` and "BMAD installed" not named in L398 (covered by §11 reference). Internal wrinkle: L398 puts "the boundary rule" in E0 but the addendum maps FR-7 to E1 (add. L17); bootstrap §11 puts "ArchUnit rule enforced" in E0. Decide which epic owns FR-7. |
| E0 | Done when: "§11 checklist complete" | add. L16 ("Bootstrap section 11 checklist") | Carried (by reference) | |
| E1 | Scope: "Headless boot, `Observer`, `ActionExecutor`, seedable RNG, determinism, fairness tests, random-action agent" | FR-1 to FR-5, FR-7 to FR-12; §6.1 L399 | Carried | Adds FR-6 interface reserved, classloader spike. |
| E1 | Done when: "1,000 seeded random-action Warrior runs complete in seconds; same seed twice is identical; all fairness tests pass" | FR-5 L141-142 `[ASSUMPTION]`; SM-4 L421; NFR-3 L437; add. L17; §14 L493 | **Contradiction (tagged); Research-overturned** | "in seconds" replaced by three measured numbers (turns/s per process, paired-seed correlation on `smoke`, tactics' leaf correlation and disambiguation) per research rec. 2. NFR-3's target (10k turns/s -> ~200 s for 1,000 runs; floor 1k turns/s -> ~2,000 s) means "seconds" is now "minutes". "same seed twice identical" -> determinism green; "all fairness tests pass" -> fairness suite green (add. L17). Soft inconsistency: SM-4 requires the tactics numbers at E1 while open question 3 (L483) says "E1/E6 measurement" and FR-34 says the numbers decide E6's design; the research says they are measurable from random playouts, so E1 is feasible but the PRD should say so. |
| E2 | Scope: "Reflection dump parameterized by depth and challenges; properties parse; guide and lore pages; assets index; changelog dump; PD-vs-SPD vocabulary diff; generated docs; `docs/codebase-map.md`" | FR-14 L205-210; FR-16 L216; §6.1 L400 | Weakened | "guide and lore pages" (the Adventurer's Guide from `Document.java` + `journal.properties`, bs. L83) not named; FR-14's "player-facing text" may or may not cover it. `docs/codebase-map.md` absent from PRD and addendum. |
| E2 | Done when: "`codex/<tag>/` is regenerated by one Gradle task and CI fails if it drifts from the committed version" | FR-14, FR-15 L213; add. L18 | Carried | |
| E3 | Scope: "Parallel runner, seed sets, per-run results (depth, turns, cause of death), SPRT comparison of two brains, JSONL run logs, replay-from-log with hash verification, nightly GitHub Action, `docs/results/`" | FR-19 to FR-25; Glossary Results L85; §6.1 L401 | Carried (refined) | SPRT -> GSPRT over paired-seed differences of a Composite outcome (win, depth, turns) with pre-registration (FR-21, FR-22), per research rec. 3. Adds FR-26 death gallery (promoted from ideas). |
| E3 | Done when: "Random-agent baseline published; a deliberately-worse agent is rejected by SPRT" | FR-21 L246; SM-5 L422; add. L19 | Carried | Sharpened to "on the `standard` set"; adds skeptic reproduction. |
| E4 | Scope: "Scripted explore / fight-in-corridors / eat / descend; beliefs v1; `safeTest`" | FR-31 L289; FR-29; FR-30; §6.1 L402 (FR-27 to FR-32, FR-36) | Carried | Adds FR-32 Decision output, FR-36 strategy log. |
| E4 | Done when: "Kills Goo on a large majority of seeds; numbers in `docs/results/`" | FR-31; SM-3 L418; add. L20 | Carried | Sharpened to `standard` set with survival curve and boss staircase. |
| E5 | Scope: "`EmbeddedDriver`, launcher, panel, pause / resume / step / run N / speed / take over, path highlight, native styling, run logging including human actions" | FR-37 to FR-43; §6.1 L403; add. L21 | Carried (expanded) | PRD E5 also includes hotkeys (FR-42, moved from E8, untagged — see §5.3) and oracle marking (FR-43, not placed by bootstrap; reasonable). Map highlights expanded from "path highlight" to path + target + considered cells (matches bootstrap §5 panel list). |
| E5 | Done when: "The human watches a full sewers run and takes over mid-fight without desync" | SM-6 L423; add. L21 | Carried | Adds "the Replay of that Run verifies". |
| E6 | Scope: "Abstract model or redetermined rollouts; expectimax 2–4 turns when enemies are visible; hand-tuned eval from Codex tables and `Char.hit`; scripted policy still handles quiet turns" | FR-34 L298-302; FR-33 L295; FR-13; FR-6; add. L22 | Weakened / research-refined | (a) "expectimax" not named; FR-34 lists three candidate designs chosen on measured properties (research rec. 4). (b) "hand-tuned eval from Codex tables and `Char.hit`" becomes "weighted function over Observation features whose weights are data, tunable by the Rig (SPSA)" — the Codex-table / `Char.hit` provenance of the terms is dropped and "hand-tuned" becomes "tunable". Neither change is tagged. 2–4 turns, visible-enemy budget, quiet turns to Policies: carried. |
| E6 | Done when: "SPRT-significant improvement over E4" | add. L22 ("Sequential-test-significant improvement over E4") | Carried | |
| E7 | Scope: "Item-ID logic, upgrade allocation, per-boss and per-class playbooks; lore pipeline (claims with provenance, variant classifier from the PD/SPD Codex diff, date gating from the changelog, verification tiers, `lore/` frontmatter); intake for the human's super-search skill" | FR-35 L305 (per-class, per-boss, item-identification); FR-18 L222 (lore pipeline); add. L63 (frontmatter) | Weakened | "upgrade allocation" absent from FR-35 and everywhere else. "intake for the human's super-search skill" absent (the research/lore intake channel is not mentioned in PRD or addendum). Lore pipeline elements all carried. |
| E7 | Done when: "Measured win rate on default settings; every implemented heuristic links to a lore entry with a verification tier" | SM-2 L417; FR-18 L222; add. L23 | Weakened / widened | "on default settings" dropped (SM-2 says `standard` Seed set; challenge-free play is not stated). "links to a lore entry" widened to "a Lore entry or Rule with a Tier" — code-cited Rules become an acceptable target, which is consistent with NN8 but is a change to the done-when and is untagged. |
| E8 | Scope: "Explain panel, pause-on conditions, replay scrubber, beliefs view, hotkeys" | FR-44, FR-45, FR-46 (E8); FR-38 (belief summary, E5); FR-42 (hotkeys, E5); FR-47 coach mode `[ASSUMPTION]` | Contradiction (partly tagged) | "beliefs view" as a distinct E8 deliverable is absorbed into E5's belief summary (FR-38) and the replay scrubber's "Beliefs" display (FR-46) — untagged. "hotkeys" moved to E5 — untagged. Coach mode / autoexplore added to E8 — tagged. |
| E8 | Done when: "Feature-complete per §5" | add. L24 | Carried | Note that "per §5" now includes FR-47 coach mode, which §5 does not describe; the done-when reference is slightly stale. |
| E9 | Scope: "Value function trained on oracle hindsight labels, run on Observations only" | add. L25 ("none in this PRD"); §6.2 L410; §5 L391 | Gap (deliberate) | No FR; the "oracle hindsight labels" use of oracle mode is not reserved in FR-11 (see NN1g). |
| E9 | Done when: "Beats the hand-tuned eval under SPRT" | add. L25 | Carried | |
| All | "Each epic has a measurable 'done when'" | add. L14-25 | Carried | |

### 6.2 GitHub label set (bs. L160)

| Input phrase | PRD location | Status | Note |
|---|---|---|---|
| "`epic:E0`…`epic:E9`" | — | Gap | Carried only by bootstrap §11 reference ("Labels, milestones E0–E9 ... exist"). FR-53 mirrors milestones and issues but says nothing about labels. |
| "`area:{harness,codex,brain,rig,overlay,lore,docs,ci,bmad}`" | — | Gap | |
| "`type:{epic,story,bug,spike,adr}`" | — | Gap | |
| "`touches-upstream`" | FR-48 L360; NFR-5 L439 | Carried | |
| "`fairness`" | — (NFR-1 requires an adversarial fairness review for Observer/ActionExecutor/Brain PRs but names no label) | Gap | The `fairness-reviewer` agent's trigger is "before opening any PR labelled fairness"; the label is the mechanism and should be named in NFR-1. |
| "`good-first-issue`" | — | Gap | |

---

## Summary

### Contradictions

1. **E1 done-when** "1,000 seeded random-action Warrior runs complete in seconds" -> three measured numbers (FR-5, SM-4, §14). **Tagged.** Research-overturned (rec. 2). Residual: SM-4's tactics numbers vs open question 3's "E1/E6".
2. **Hotkeys** moved from E8 (bs. L157) to E5 (FR-42 in FR-37..43) and the "otherwise buttons only" fallback dropped. **Untagged.** Research-overturned (small hook confirmed, [99]-[101]).
3. **E8 "beliefs view"** absorbed into E5 FR-38 / E8 FR-46. **Untagged.**
4. **E6 eval** "hand-tuned eval from Codex tables and `Char.hit`" -> "weights are data, tunable by the Rig (SPSA)" (FR-33); "expectimax" -> three candidate designs (FR-34). **Untagged** (research-driven, rec. 4).
5. **E7 done-when** "links to a lore entry" -> "a Lore entry or Rule"; "on default settings" dropped (add. L23). **Untagged.**
6. **HeadlessDriver "no scene"** (bs. L97) -> Headless scene (Glossary L63, FR-1). Research-overturned (rec. 1); PRD carries the new design without noting the bootstrap deviation.
7. **FR-7 epic ownership**: PRD §6.1 L398 puts the boundary rule in E0, addendum L17 maps FR-7 to E1, bootstrap §11 puts it in E0. Internal.

### Gaps (absent)

- Module dependency edges for `api`, `harness`, `codex`, `rig`, `overlay` (only `brain -> api` stated); `api` = "DTOs only" incl. run-log records.
- GitHub label set: `epic:*`, `area:*`, `type:*`, `fairness`, `good-first-issue` (only `touches-upstream` named).
- E7 "upgrade allocation".
- E7 "intake for the human's super-search skill".
- E2 "guide and lore pages" (Document.java) and `docs/codebase-map.md`.
- NN7 "No TODO comment in code without an issue number".
- Oracle mode "for training labels" (NN1, E9).

### Weakened

- ArchUnit rule: package roots `com.shatteredpixel.*` / `com.watabou.*` not named (FR-7 says "game packages"); `com.watabou.*` in `SPD-classes` is the likely loophole.
- "Rollouts on the raw saved game are cheating and forbidden" only implied (Glossary Search, FR-13), never stated.
- Observation actor fields (name, HP as displayed, visible buffs, sleeping) and map fields (fog memory, seen heaps) compressed to "as displayed" / "as drawn".
- "the seed" absent from leak/differential test cases (FR-8, FR-9).
- NN5 "no brain change merges without rig numbers" is a metric (SM-7) and a Playbook-only FR (FR-35), not a general merge gate.
- NN3 "Prefer new modules over edits" is a counter-metric (SM-C4), not a rule.
- FR-38 does not enumerate Belief-summary contents (Glossary does).
- Journal "Notes and Catalog" -> "journal state".

### Carried cleanly

All Action kinds; all five leak-test cases; differential, toggle, determinism tests; interjection semantics in full; all six pause-on conditions; all seven panel elements; all Overlay controls; oracle red border / ORACLE label / rig exclusion; observation-driven re-planning; safeTest; belief candidate sets, floor facts, chapter counters; overlay v1 before search; every epic's done-when (with the deviations listed above); license, attribution, tag pinning, hook registry, `upstream/master` ban; Codex over folklore and the Lore pipeline with tiers.
