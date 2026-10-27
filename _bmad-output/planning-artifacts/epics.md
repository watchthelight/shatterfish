---
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-shatterfish-2026-09-03/prd.md
  - _bmad-output/planning-artifacts/prds/prd-shatterfish-2026-09-03/addendum.md
  - _bmad-output/planning-artifacts/architecture/architecture-shatterfish-2026-09-03/ARCHITECTURE-SPINE.md
  - _bmad-output/planning-artifacts/ux-designs/ux-shatterfish-2026-09-03/EXPERIENCE.md
  - _bmad-output/planning-artifacts/ux-designs/ux-shatterfish-2026-09-03/DESIGN.md
  - docs/adr/0003-module-layout.md
  - docs/adr/0005-observation-schema-and-hashing.md
  - docs/adr/0006-observer-visibility-rules.md
  - docs/adr/0007-rng-seeding-strategy.md
  - docs/adr/0008-hook-guarding-and-tracking.md
  - docs/adr/0009-snapshot-restore-and-redetermination.md
  - docs/adr/0010-tactical-search-deferral-criteria.md
  - docs/adr/0011-run-log-format.md
  - docs/adr/0012-rig-statistics.md
  - docs/adr/0013-overlay-threading-model.md
  - docs/adr/0014-action-schema-and-executor-contract.md
  - docs/adr/0015-headless-scene-and-input-wait-detection.md
  - docs/codebase-map.md
  - docs/rules/index.md
  - docs/BOOTSTRAP-PROMPT.md
status: draft
created: '2026-09-04'
updated: '2026-09-04'
---

# Shatterfish - Epic Breakdown

## Overview

This document decomposes the PRD (v4), the architecture spine (AD-1 to AD-14 with ADR-0003 and
ADR-0005 to ADR-0015), and the UX design contract into implementable stories.

Two conventions bind every story below and are not repeated in each one:

- **One story is one turn.** A story that cannot be specified, implemented and reviewed in a
  single working session is split at a clean boundary before it is started.
- **Acceptance criteria name their evidence.** Every story names the test class that proves it,
  and from E3 onward every Brain-affecting story names the Rig numbers its pull request must
  carry. "It works" is not an acceptance criterion; a named green test is.

Story keys are `<epic>.<number>`, the form BMAD's sprint parser reads from these headings. It
derives the tracking key `<epic>-<number>-<kebab title>`, which is what `sprint-status.yaml`, the
branch name (`story/1-4-a-random-action-warrior-run-to-death`) and the GitHub issue title use.

## Requirements Inventory

### Functional Requirements

| ID | Requirement |
|---|---|
| FR-1 | Boot a Run headlessly |
| FR-2 | Determinism from (tag, class, challenges, seed, salt, Action list) |
| FR-3 | Observer produces the Observation |
| FR-4 | ActionExecutor applies an Action |
| FR-5 | Random-action agent and throughput measurement |
| FR-6 | Snapshot, restore, and redetermination |
| FR-7 | The Brain cannot depend on game code |
| FR-8 | Leak tests |
| FR-9 | Differential test |
| FR-10 | Toggle tests |
| FR-11 | Oracle mode gating |
| FR-12 | Thread confinement |
| FR-13 | Search leak test |
| FR-14 | Generate the Codex from the pinned tag |
| FR-15 | Codex drift check |
| FR-16 | Vocabulary diff |
| FR-17 | Rules with citations and the codebase map |
| FR-18 | Lore pipeline |
| FR-19 | Parallel runner |
| FR-20 | Seed sets |
| FR-21 | Sequential test |
| FR-22 | Pre-registration |
| FR-23 | Run logs with a Hash chain |
| FR-24 | Replay with verification |
| FR-25 | Results publication, including negatives |
| FR-26 | Death replay gallery |
| FR-27 | Re-plan every Input wait from the Observation |
| FR-28 | Arbitration of Policies |
| FR-29 | Beliefs |
| FR-30 | safeTest |
| FR-31 | Scripted baseline Policies |
| FR-32 | Decision output |
| FR-33 | Evaluation |
| FR-34 | Tactical Search |
| FR-35 | Playbooks as data |
| FR-36 | Strategy log |
| FR-37 | EmbeddedDriver and launcher |
| FR-38 | Native Panel |
| FR-39 | Controls and speed modes |
| FR-40 | Interjection semantics |
| FR-41 | Map highlights |
| FR-42 | Hotkeys |
| FR-43 | Oracle overlay marking |
| FR-44 | Explain view (v2) |
| FR-45 | Pause-on conditions (v2) |
| FR-46 | Replay scrubber and Beliefs view (v2) |
| FR-47 | Coach mode and autoexplore (v2) |
| FR-48 | Pinned tag and hook registry |
| FR-49 | Mobile modules opt-in |
| FR-50 | Upgrade procedure and timing |
| FR-51 | Docs site |
| FR-52 | Decisions and ideas are recorded |
| FR-53 | Issues mirror Epics and Stories |

### NonFunctional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | Fairness: the leak, differential (both forms), toggle, thread-confinement, boundary and Codex-leak tests run on every pull request |
| NFR-2 | Reproducibility: a Run is determined by its tuple; logs are hash-chained; a nightly cross-platform Replay compares chains |
| NFR-3 | Headless throughput: no rate promised before it is measured; the smoke direction check fits a working session and the standard acceptance run fits overnight |
| NFR-4 | Overlay responsiveness: Brain thinking never blocks the render thread; the game keeps its frame rate |
| NFR-5 | Upstream upgrade: tag-only merges by the documented procedure; every Hook re-verified |
| NFR-6 | Documentation currency: docs and ADRs change in the same pull request as the code |
| NFR-7 | Portability: Windows and Linux supported and tested; macOS best-effort and untested |
| NFR-8 | Privacy and network: no network calls at runtime, no telemetry |
| NFR-9 | Observability: every Run log, Results page and strategy log is plain text a person can read with standard tools |

### Additional Requirements (from the architecture)

- **No starter template.** This is a permanent downstream fork of an existing Java codebase pinned
  at `v3.3.8`; the module skeleton already exists from E0 (ADR-0003). E1's first story is a spike
  against that tree, not a scaffold.
- Dependency edges are fixed and enforced three ways (AD-1); `brain` additionally forbids
  `java.io`, `java.nio.file`, `java.net` and reflection.
- Every value crossing a module edge is an `api` type with a codec and a schema version (AD-13);
  the `Belief` is opaque outside `brain`, and a `Snapshot` never leaves `harness` (ADR-0009).
- One owner per mutable thing (AD-14): the driver owns `k`, the reseed, the Profile, the Run log
  and the snapshot store; the executor is the only caller of `Hero.handle` and `hero.next()`.
- The hook ledger is ten rows, counted by a test against `docs/UPSTREAM.md` (ADR-0008, PRD v4 §10).
- Movement is one step per Action (ADR-0014); Input waits are detected at the `observe()` site
  inside `Hero.act()`'s `!ready` branch (ADR-0015).
- The Run tuple carries a per-Run salt drawn at execution and never observed (ADR-0007).
- Process per Run is the default; classloader isolation is an E1 spike.
- Java 21, Gradle 9.4, libGDX 1.14.0 headless backend with desktop natives, JUnit 5, ArchUnit
  (bump to 1.5.0 in E1).

### UX Design Requirements

Extracted from `EXPERIENCE.md` and `DESIGN.md`; each is specific enough to carry a story's
acceptance criteria.

| ID | Requirement |
|---|---|
| UX-DR1 | The Panel is a single column at the right edge of the dungeon view, left of the inventory pane and between the status pane and the toolbar, translucent, never over the game's HUD, with the Overlay applying its own horizontal camera offset and re-applying it after the game's layout pass |
| UX-DR2 | Panel width target 200 UI pixels, minimum 160; the Decision log flexes and never shows fewer than three lines; the Panel collapses to the Mode strip below 200 UI pixels of uncovered map or 200 of view height, in the mobile layout, or when the human toggles it |
| UX-DR3 | Section order is fixed: Mode strip, Goal line, Decision card, Safety flags, Belief summary, Decision log, Controls |
| UX-DR4 | Design tokens: the eleven named colors with one meaning each, three text weights, title 9 / body 8 / small 6 UI pixels through the game's own text renderer, spacing on the 1/2/4/6/8 grid, nine-patch frames only |
| UX-DR5 | Numbers are right-aligned in fixed-width columns; no floats in displayed values |
| UX-DR6 | The stepping model: Next Step, Run N (default 10, range 1 to 999), Human play speed (default 1 second, range 0.5 to 5), Fast as it can; the unit of stepping is one Input wait |
| UX-DR7 | State patterns with an enablement matrix: RUNNING, PAUSED, HUMAN, THINKING, Brain error, Hero busy, No valid action, Run over, No Run, Save and resume, Oracle, Panel collapsed |
| UX-DR8 | PAUSED ignores hero input entirely, including the toolbar, quickslots and key-hold movement |
| UX-DR9 | Hand back lands in PAUSED with a fresh Decision; a Run starts and resumes in PAUSED with Next Step |
| UX-DR10 | Map highlights in the dungeon view: planned path, target cell, considered cells, cleared when the hero acts or the plan changes, never drawn in HUMAN mode |
| UX-DR11 | Oracle marking: a red border around the game view, an ORACLE label in the Mode strip, oracle rows prefixed in the Belief summary, unseen enemies outlined on the map |
| UX-DR12 | Controls are `SPDAction`s with default keys F6 to F11, rebindable in the game's settings screen, and every control is also a button |
| UX-DR13 | Microcopy is labels, numbers and one-line reasons in Codex vocabulary; the Overlay never imitates the game's own log voice |
| UX-DR14 | Accessibility floor: every state and flag stated in words, color never carrying meaning alone, every control keyboard-operable, the Decision log and Run log plain text |
| UX-DR15 | v2 surfaces must not be precluded: Explain expansion, Pause-on conditions in the settings screen, Replay scrubber, Beliefs view, coach mode |

### FR Coverage Map

| Requirement | Epic | Stories |
|---|---|---|
| FR-1 | E1 | 1.3, 1.4 |
| FR-2 | E1 | 1.15, 1.16 |
| FR-3 | E1 | 1.6, 1.7, 1.8, 1.9, 1.10, 1.11 |
| FR-4 | E1 | 1.12, 1.13 |
| FR-5 | E1 | 1.14, 1.21 |
| FR-6 | E1 (interfaces), E6 | 1.20 |
| FR-7 | E1 | 1.2 |
| FR-8 | E1 | 1.8, 1.9, 1.10, 1.11 |
| FR-9 | E1, E4 | 1.17, 4.14 |
| FR-10 | E1 | 1.17 |
| FR-11 | E1, E3, E5 | 1.18, 3.3, 5.12 |
| FR-12 | E1, E5 | 1.19, 5.1 |
| FR-13 | E6 | deferred |
| FR-14 | E2 | 2.1 to 2.7 |
| FR-15 | E2 | 2.9 |
| FR-16 | E2 | 2.8 |
| FR-17 | E2, E4 | 2.10, 4.4 |
| FR-18 | E7 | 7.1, 7.7, 7.8, 7.9, 7.10 |
| FR-19 | E3 | 3.3 |
| FR-20 | E3 | 3.1 |
| FR-21 | E3 | 3.6, 3.7, 3.8 |
| FR-22 | E3 | 3.5 |
| FR-23 | E3, E5 | 3.2, 5.9 |
| FR-24 | E3 | 3.4 |
| FR-25 | E3 | 3.5, 3.9, 3.10 |
| FR-26 | E3, E4 | 3.12, 4.13 |
| FR-27 | E4 | 4.1 |
| FR-28 | E4 | 4.1 |
| FR-29 | E4 | 4.2 |
| FR-30 | E4 | 4.3 |
| FR-31 | E4 | 4.6 to 4.12 |
| FR-32 | E4 | 4.4 |
| FR-33 | E4 | 4.5 |
| FR-34 | E6 | deferred |
| FR-35 | E7 | 7.2 to 7.6 (weights as data landed in 4.5) |
| FR-36 | E4, E5 | 4.4, 5.4 |
| FR-37 | E5 | 5.1 |
| FR-38 | E5 | 5.2, 5.3, 5.4, 5.15 |
| FR-39 | E5 | 5.3, 5.6, 5.7, 5.13 |
| FR-40 | E5 | 5.5, 5.8, 5.9 |
| FR-41 | E5 | 5.10 |
| FR-42 | E5 | 5.11 |
| FR-43 | E5 | 5.12 |
| FR-44 to FR-47 | E8 | deferred |
| FR-48 to FR-53 | E0 | complete |

Every requirement the PRD places in v1 (E0 to E5) has at least one story whose acceptance criteria
deliver it. FR-13, FR-18, FR-34, FR-35 and FR-44 to FR-47 are deferred by the PRD's own scope
statement, and appear under E6 to E8 below.

The story files, `sprint-status.yaml` and the GitHub issues that the session ritual reads do not
exist yet; sprint planning and issue mirroring are the next session's work, and no story above can
start before them.

## Epic List

| Epic | Title | Goal | Done when |
|---|---|---|---|
| E0 | Bootstrap | A repository, a pinned upstream, a module skeleton with the boundary enforced, docs, CI and the full planning artifact set | Bootstrap §11 checklist complete (done) |
| E1 | Harness | The game runs headlessly, reproducibly, and behind a fair Observation | A seeded Warrior Run completes headlessly, the same tuple twice is identical across two JVMs, and the whole fairness suite is green |
| E2 | Codex | Every fact the Brain needs about the game, generated from the pinned code and never drifting | One Gradle task regenerates `codex/<tag>/` and CI fails on drift |
| E3 | Rig | Any claim about a Brain can be measured, published and reproduced by a stranger | A random-agent Baseline is published and a deliberately worse Brain is rejected by the Sequential test |
| E4 | Baseline brain | A hand-built bot that plays the sewers competently | The Warrior kills Goo on at least 75% of the `goo` Seed set with a lower bound of at least 70% |
| E5 | Overlay v1 | A person can watch the bot think, step it, and take the controls mid-fight | A full sewers Run watched end to end with a mid-fight takeover and no desync |
| E6 | Tactical search | Better fighting, if and only if the numbers say so | A candidate design is accepted against the one-ply model under the Sequential test, or the measurements are published and the question is closed |
| E7 | Strategy and lore | Whole-Run planning and a cited knowledge base | A measured win rate, and every heuristic linked to a Rule or a Lore entry with a Tier |
| E8 | Overlay v2 | The instrument becomes a teacher | Feature-complete per bootstrap §5 |
| E9 | Learned evaluation (optional) | A learned value function that plays fair | Beats the hand-tuned Evaluation under the Sequential test |

---

## Epic 1: Harness

Make Shattered Pixel Dungeon run without a window, without a graphics driver and without a human,
so that the same tuple produces the same Run twice, and so that everything the bot ever sees comes
through one class that shows it only what the screen shows. This epic is the foundation of every
number Shatterfish will publish; nothing after it is trustworthy if it is wrong.

Covers FR-1 to FR-12 (FR-6 as reserved interfaces), NFR-1, NFR-2, NFR-3, NFR-7, NFR-8.

Stories are ordered so that no story depends on a later one. The Observation schema precedes the
Observer that fills it; the Observer precedes the Action set that is computed from it; the Action
set precedes the random agent that draws from it.

### Story 1.1: Audit the rendering touchpoints and prove a headless turn resolves

As the engineer,
I want a spike that boots the game headlessly and completes one hero turn,
So that the headless-scene strategy is proven against the real tree before anything rests on it.

**Acceptance Criteria:**

**Given** the pinned tree and the inventory in `docs/codebase-map.md`
**When** the spike boots `core` on the headless backend with a no-op graphics binding installed
before any texture initializes, and drives a harness-owned scene
**Then** one hero melee attack resolves end to end, meaning damage is applied and the hero becomes
ready again, which proves the sprite-callback path completes
**And** the spike lives on the story branch under `shatterfish/harness/src/test/java` as a
throwaway test, and the report says explicitly which parts stories 1.3 and 1.4 inherit and which are
discarded
**And** the report lists every static dereference that had to be guarded, with a `path:line` for
each, and states how many hook rows the full implementation needs
**And** the story fails if that number exceeds the ledger's budget of ten
**And** the findings are written to `docs/results/e1-touchpoint-audit.md`, and each ADR-0015
assumption the spike confirms or refutes is recorded there.

### Story 1.2: The Hooks registry, the counting test, and the boundary rules

As the engineer,
I want one registry class for every upstream edit and the full set of boundary rules,
So that neither an unexplained hook nor a forbidden import can enter the tree.

**Acceptance Criteria:**

**Given** the ledger in ADR-0008 and the boundary rules of AD-1
**When** `Hooks.java` is added under `core` with one nullable listener field per hook point
**Then** `HooksLedgerTest` greps the tree for hook markers and fails if the set of ids differs from
the rows in `docs/UPSTREAM.md`, or if the row count exceeds ten
**And** `HooksVanillaTest` boots with no listener registered and asserts the vanilla branch runs at
every site, and `./gradlew :desktop:run` still launches the unmodified game
**And** `BrainBoundaryTest` asserts that `brain` depends on no game package, no other Shatterfish
module but `api`, and none of `java.io`, `java.nio.file`, `java.net` or `java.lang.reflect`, so the
Observation is its only channel (FR-7, AD-1)
**And** `ApiBoundaryTest` asserts `api` depends only on the JDK
**And** the ArchUnit bump to 1.5.0 lands here with every boundary rule green.

### Story 1.3: The headless graphics stub and the scene

As the engineer,
I want a scene that behaves like the game's own without a graphics context,
So that sprite callbacks fire and turns resolve.

**Acceptance Criteria:**

**Given** ADR-0015's decision that the harness owns the scene
**When** the no-op graphics binding and `HeadlessScene` are implemented
**Then** the binding is installed before any texture class initializes, and atlases load through
the image path that needs no graphics context
**And** the scene creates the same groups, sprites and emote icons the real scene creates, so that
actor-thread random draws match an Overlay Run
**And** `SceneDrawParityTest` asserts the draw counts: a scripted sequence consumes the same number
of random draws headlessly as the same sequence consumes with the real scene, which is the
testable form of parity and does not require a graphics context in the test
**And** no story after this one adds a guard to actor or item code.

### Story 1.4: The driver loop and the first Input wait

As the engineer,
I want the driver thread to own the loop and reach a state where the hero waits for input,
So that everything else has a place to happen.

**Acceptance Criteria:**

**Given** ADR-0015's decision that the driver thread owns the loop
**When** the driver drives the scene with a fixed fast-forward step and drains the posted-runnable
queue itself
**Then** `HeadlessBootTest` starts a seeded Warrior game and reaches the hero's first Input wait
**And** a Prompt window opened by game code appears headlessly and can be closed through its own
button
**And** no library-owned loop thread drives the scene, asserted by the test that the driver's own
step count matches the number of scene updates
**And** a Run that never reaches a wait fails with a diagnostic naming the last actor processed,
rather than hanging.

### Story 1.5: Input-wait detection

As the engineer,
I want exactly one detection per hero turn,
So that the wait index is a reliable key for everything else.

**Acceptance Criteria:**

**Given** ADR-0015's decision that detection happens at the observe site inside the hero's act
method, in the branch guarded by the ready flag
**When** the hook sets a flag and the driver consumes it
**Then** `InputWaitCountTest` asserts that sixty actor-thread wake-ups with the hero parked produce
exactly one wait, not sixty, which is the failure the earlier design would have had
**And** the driver confirms the wait condition, being the hero ready with no window or a recognised
Prompt window, before acting on the flag, and drops the flag otherwise
**And** the per-wait sequence runs in order: increment the index, reseed, observe, decide, execute,
record, with the reseed and record stubbed until their own stories
**And** an interruption or a free search that produces a wait with no preceding Action is counted
correctly.

### Story 1.6: The Observation, part one: the schema core and the codec

As the engineer,
I want the header, map and actor sections as records with a canonical encoder,
So that the shape of what the bot sees is fixed before anything fills it.

**Acceptance Criteria:**

**Given** ADR-0005
**When** the records for the header, map and actor sections, `ObservationCodec` and the section
hashing are implemented in `api`
**Then** the encoding is deterministic: fixed field order, big-endian integers, length-prefixed
strings, no floats anywhere
**And** every list has a canonical order fixed by the codec, and `CodecCanonicalTest` shuffles each
input list and asserts the section hashes are unchanged
**And** `CodecReflectionTest` fails if any record component is not encoded
**And** the header carries the schema version, the Codex version, the sealed flag and the oracle
flag, and carries neither the seed, the salt nor a turn counter
**And** no enum has a member naming hidden state, so a secret door has no representation.

### Story 1.7: The Observation, part two: the remaining sections and the readable form

As the engineer,
I want the hero, inventory, journal, log, actions and prompt sections plus a readable rendering,
So that the schema is complete and a person can read an Observation.

**Acceptance Criteria:**

**Given** ADR-0005
**When** the remaining records and the JSON writer are implemented in `api`
**Then** the whole-Observation hash is the digest over the section hashes plus the version
**And** `CodecEqualityTest` asserts over a corpus that two Observations are equal exactly when
their hashes are equal
**And** the JSON rendering carries the hash as a field and is never parsed back into an Observation
**And** the Belief is declared as a separate opaque versioned value, not a field of the Observation,
so that `harness` can hash it without depending on `brain`.

### Story 1.8: The Observer, part one: map, fog, traps and heaps

As the bot,
I want the terrain, fog, visible traps and seen heaps exactly as the screen draws them,
So that I can navigate without seeing what the player cannot.

**Acceptance Criteria:**

**Given** the rows of ADR-0006 for cell visibility, terrain, traps and heaps
**When** the Observer builds the map section from the game's own drawing predicates
**Then** a secret door reads as a wall and a secret trap as floor, and neither is identifiable
**And** a trap that is visible but sits on a cell whose fog is unknown does not appear, because the
painter reveals traps at generation and mobs trigger them out of sight
**And** a container heap exposes only its container type, except a crystal chest, which names its
category, and a plain heap exposes its current top item
**And** `MapLeakTest` constructs a world with a secret door, a hidden trap, a pre-revealed trap on
an unvisited cell and a locked chest, and asserts none is identifiable in the serialized Observation
**And** each row implemented is added to `docs/rules/visibility.md` with this test named in its Test
column.

### Story 1.9: The Observer, part two: actors, emotes, buffs and the hero

As the bot,
I want the visible characters and my own state as the HUD shows them,
So that I can fight without knowing what the player could not.

**Acceptance Criteria:**

**Given** the rows of ADR-0006 for mobs, mob state, mob buffs and hero buffs
**When** the Observer builds the actor and hero sections
**Then** a character outside the field of view is absent, an invisible character inside it is
present with its flag, and a stealthy passive mimic is emitted as a chest heap rather than an actor
**And** health is quantised to the health bar's pixel resolution, never the exact value
**And** the only AI state exposed is the emote the sprite shows, read through the accessor hook
**And** buffs are every buff with an icon, with the turns their description shows, uncapped, for the
hero and for visible mobs, and the exact hunger value is absent
**And** `ActorLeakTest` asserts that the mob's AI state, target, seen flag, exact hit points and the
hunger value cannot be recovered
**And** `MimicDifferentialTest` asserts a real chest and a stealthy mimic at the same cell produce
byte-identical Observations.

### Story 1.10: The Observer, part three: inventory, journal, log and prompts

As the bot,
I want my inventory with exactly the identification the player has, plus the log and any prompt,
So that I can reason about unknown items without seeing their identity.

**Acceptance Criteria:**

**Given** the rows of ADR-0006 for items, known appearances, the log, the journal and prompts
**When** the Observer builds those sections
**Then** an unidentified potion appears under its appearance name only, and its class is not
recoverable; wand charges appear only when known; a curse enchantment only when the curse is known
**And** the log section is captured from the game's own message signal rather than the rendered log
component, and the Observer re-registers that listener whenever the scene is recreated, since the
game replaces it on every scene creation
**And** an open Prompt of a recognised kind is exposed with its options, and any other window at an
Input wait fails an assertion
**And** `ItemLeakTest` asserts no true class, level, curse or identification counter appears
**And** `LogListenerTest` changes floor twice and asserts the log section still receives messages.

### Story 1.11: The Observer, part four: the remaining rows

As the bot,
I want the environment facts the screen shows and nothing more,
So that no row of the whitelist is left unimplemented and untested.

**Acceptance Criteria:**

**Given** the rows of ADR-0006 not covered by stories 1.8 to 1.10: blobs, the danger count, the level
feeling, the transitions the player has seen, and the boss-floor sealed flag
**When** the Observer builds them
**Then** a gas or fire blob appears as the set of blob kinds present in cells the hero can see, with
no volume, since the game draws one particle per cell regardless of volume
**And** the danger count is the number the indicator shows, which includes invisible enemies in view
**And** the sealed flag reflects the boss lock, so the valid-Action set never offers a descent the
game refuses
**And** `EnvironmentLeakTest` asserts blob volumes and blobs outside the field of view are absent
**And** a checklist test asserts every row of the ADR-0006 table has at least one leak test naming
it, so the whitelist cannot grow a row without a test.

### Story 1.12: The Action type and the valid-Action set

As the bot,
I want a closed set of Actions and to know which are legal right now,
So that anything I do could have been done by a person at the same screen.

**Acceptance Criteria:**

**Given** ADR-0014's sealed kind list
**When** `Action` and `validActions(Observation)` are implemented in `api`
**Then** a move is one step to an adjacent cell and never a multi-cell target
**And** every parameter is a value the Observation carries: a cell it includes, an item reference it
lists, or an option index into its own prompt or action list
**And** the valid set is computed from the Observation alone, with no game access, asserted by a
test that computes it from a deserialized Observation with no game running
**And** `Wait` is absent from the valid set while a Prompt is open.

### Story 1.13: The ActionExecutor

As the bot,
I want my Actions applied through the same paths a human's clicks take,
So that the game's own guards apply to me.

**Acceptance Criteria:**

**Given** ADR-0014 and AD-14's rule that the executor is the only caller of the hero's input methods
**When** the executor is implemented
**Then** a targeted item use drives the game's own selector within the same Input wait
**And** it re-validates against the Observation's action set and rejects with a reason rather than
an exception, before any game state changes
**And** `ActionCompletenessTest` enumerates the game's hero-affecting inputs and asserts each maps
to an Action kind or is listed as unsupported with a reason (FR-4)
**And** `ActionValidityPropertyTest` asserts over random states that every valid Action is accepted
and every invalid one is rejected without mutating state.

### Story 1.14: A random-action Warrior Run to death

As the engineer,
I want an agent that plays a seeded Run by choosing randomly among the legal Actions,
So that the whole loop is exercised before any Brain exists.

**Acceptance Criteria:**

**Given** the Observation, the valid-Action set and the executor
**When** the agent takes a uniformly random valid Action at every Input wait
**Then** the Run ends in death, a Win or the turn cap, and reports the depth reached and the turn
count
**And** `RandomAgentRunTest` completes 1,000 such Runs unattended without an exception or a hang
**And** a Run that reaches the 20,000-turn cap ends with cause turn cap rather than running forever
**And** the agent lives in `harness` under an agent package, since it is a harness tool and not a
Brain, and `brain` remains empty at this point.

### Story 1.15: The salt, the mix function and the Profile

As a skeptic,
I want the random stream to be a function of things the Run declares,
So that a Run can be reproduced rather than believed.

**Acceptance Criteria:**

**Given** ADR-0007
**When** `RngControl` and the Profile are implemented
**Then** the base generator is reseeded from the mix of the salt and the wait index at every Input
wait, after the game's own initialization has finished resetting the generator stack
**And** the mix function is implemented exactly as ADR-0007 specifies, and `MixTestVectorTest`
asserts it against the vector published on the methodology page
**And** each Run gets a fresh versioned Profile with English text, the intro off, all guide pages
read, and no remains, rankings or badges
**And** the salt appears in the Run log and in no Observation, asserted by a leak test
**And** a Run started against a different Profile version is refused rather than silently compared.

### Story 1.16: Identity order and the two-JVM determinism test

As a skeptic,
I want the same tuple to give the same Run on another machine,
So that reproduction is mechanical rather than lucky.

**Acceptance Criteria:**

**Given** ADR-0007's identity-order hook row
**When** the collections whose iteration order decides an outcome are made insertion-ordered, and
the class-keyed random choices are sorted by class name
**Then** the row covers the actor collections, the level's mob and blob collections, and both
random helpers that iterate a map or a collection, since the game re-inserts from those on every
level load
**And** `DeterminismTwoJvmTest` runs the same tuple in two separate JVM processes and asserts every
Observation hash matches at every wait, which identity-hash ordering would break
**And** the test runs on the pull-request runner, and the cross-platform comparison is named as
owned by story 3.4's nightly job, since ADR-0002 puts the second platform in the nightly rig rather
than the pull-request gate
**And** `docs/UPSTREAM.md` gains the identity-order row in the same pull request.

### Story 1.17: The differential and toggle tests

As a skeptic,
I want proof that two worlds a player could not tell apart look identical to the bot,
So that the fairness claim is a test rather than a promise.

**Acceptance Criteria:**

**Given** FR-9 and FR-10
**When** the differential test constructs pairs of worlds identical to the player but different in
hidden state
**Then** the pairs cover at least different unidentified item identities, different unseen mob
positions, different hidden trap placement, and different generator state
**And** each pair serializes to byte-identical Observations, and a deliberate break in the Observer
makes the test fail, verified once in this story
**And** the toggle test asserts mind vision, blindness and magic mapping change the Observation
exactly as they change the screen, with the blind field of view being the three-by-three block the
game actually computes
**And** both tests run on every pull request (NFR-1).

### Story 1.18: Oracle mode, gated and marked

As the engineer,
I want a debugging mode that sees everything and that no fair path can reach,
So that I can diagnose belief bugs without contaminating a measured Run.

**Acceptance Criteria:**

**Given** FR-11 and ADR-0006's decision that oracle data is a sidecar
**When** the oracle observer is implemented behind a launcher flag
**Then** the Observation it returns is the ordinary Observation with the oracle flag set in the
hashed header, so its hashes differ from a fair Run's
**And** the true identities and unseen positions arrive in a separate view that the Brain's
interface never receives, asserted by a test that the Brain-facing type has no path to it
**And** `OracleGateTest` asserts that with no flag there is no code path from true identities or
unseen positions into anything the Brain can hold
**And** the Rig's refusal of oracle Runs is named here as owned by story 3.3.

### Story 1.19: Thread confinement

As the engineer,
I want every port to fail loudly when called from the wrong thread,
So that the Overlay cannot deadlock the way the game once did.

**Acceptance Criteria:**

**Given** AD-8's three roles
**When** the Observer and the executor assert the UI-role thread on entry
**Then** `ThreadConfinementTest` calls each from a foreign thread and asserts a failure naming the
expected role
**And** an ArchUnit rule asserts no Shatterfish code takes a monitor on a game type
**And** the Brain holds no game object, which the boundary rules already assert.

### Story 1.20: Snapshot, restore, and the reserved interfaces

As the engineer,
I want to save a Run's exact state and put it back,
So that later epics have something to roll out from and take over from.

**Acceptance Criteria:**

**Given** ADR-0009
**When** the snapshot store is implemented in `harness` over the game's own save bundles
**Then** the snapshot type is module-private to `harness`, and `api` carries only an opaque handle
and the simulator interface, asserted by an ArchUnit rule, because bundle bytes in `api` would be
inflatable into hidden state by anything holding them
**And** `RestoreReplayTest` snapshots at a wait, replays the remaining Actions from the restore, and
asserts the Observation hashes match the original from that wait onward, which catches the
generator draws the game's own load path consumes
**And** the belief-sample and redeterminer interfaces exist with no implementation, and a test
asserts a handle whose scrubbed flag is false is refused by the interface contract
**And** the rollout host itself is explicitly deferred to E6.

### Story 1.21: Publish the E1 numbers

As the product owner,
I want the measured throughput and tactical properties published,
So that the roadmap rests on numbers instead of a guess.

**Acceptance Criteria:**

**Given** NFR-3's rule that no rate is promised before it is measured
**When** the benchmark runs the random agent on a described machine
**Then** `docs/results/e1-throughput.md` reports Input waits per second per process, Runs per
minute, the median Run length, and the codec and log-writer costs separately
**And** the same page reports leaf correlation and the disambiguation factor for the game's tactics,
measured with random playouts, and explicitly does not report bias, which is a two-player quantity
**And** the benchmark with a Brain attached is named as deferred to E4, since no Brain exists yet
**And** the page states the machine, the tag, the commit and the command that reproduces it
**And** SM-4 is satisfied: the numbers are published and the determinism test is green.

---

## Epic 2: Codex

Turn the pinned source into the bot's general game knowledge: every mob, item, weight, guarantee,
table, recipe and string, generated by one task, cited to `path:line`, and impossible to drift
from the tag it describes. The Codex is what lets the Brain know the game the way a wiki-reading
human does, without a second implementation of the rules and without a second door into hidden
state.

Covers FR-14 to FR-17, NFR-1 (the Codex leak test), NFR-6.

The Codex writes `api`-typed JSON, so `codex` depends on `api` as well as on the game (AD-1 as
corrected in session 13); no Codex value ever reaches the Brain as a class.

### Story 2.1: The generator skeleton and the seed-free guarantee

As the engineer,
I want one Gradle task that writes a tag-named Codex folder, proven independent of any Run,
So that the Brain's knowledge can never smuggle in a seed.

**Acceptance Criteria:**

**Given** FR-14's rule that the Codex is static and seed-free
**When** `./gradlew :codex:generate` writes `codex/<tag>/`
**Then** `CodexSeedFreeTest` generates twice with different seeds and different Profiles and
asserts byte-identical output
**And** `CodexLeakTest` asserts no generated value derives from `Dungeon.seed`, a Profile, or any
Run state, by generating with a live Run in progress and comparing to generation from a cold start
**And** every generated entry carries a `path:line` citation into the tag
**And** the output is `api`-typed JSON with a Codex version, which the Run-log header records
(AD-13).

### Story 2.2: Mobs and spawn tables by depth

As the bot,
I want every mob's stats and the spawn table for each depth,
So that I can judge a fight before I start it.

**Acceptance Criteria:**

**Given** the mob classes and `MobSpawner.getMobRotation(depth)`
**When** the generator dumps them
**Then** each mob carries its hit points, attack and defense skill, damage roll with its
distribution, damage reduction, experience, maximum level, loot chance and loot table, alignment
and abilities, each cited
**And** the spawn table is parameterised by depth and challenge flags, including the rare-mob and
rare-alternate rules and the champion roll
**And** `CodexCompletenessTest` asserts every concrete subclass of the game's mob type appears
**And** boss stats appear with their Stronger Bosses variants.

### Story 2.3: Items, generator weights, decks and guarantees

As the bot,
I want the item catalogue with its spawn weights, deck structure and guarantees,
So that my beliefs about unknown items start from the game's real distribution.

**Acceptance Criteria:**

**Given** the generator's category and item decks and the limited-drop counters
**When** the generator dumps them
**Then** the two category decks, every per-category deck and the exotic swap chance appear with
their weights
**And** each item carries its display name, appearance-label pool, value, strength requirement
formula, and the actions it offers
**And** `CodexCompletenessTest` covers every concrete item subclass.

### Story 2.4: Item guarantees, tier tables and limited drops

As the bot,
I want the schedules that decide what a floor is guaranteed to hold,
So that my chapter counters are the game's own arithmetic rather than a guess.

**Acceptance Criteria:**

**Given** the guaranteed-drop formulas and the limited-drop counters
**When** the generator dumps them
**Then** the strength-potion, upgrade-scroll and stylus schedules appear as the formulas the game
computes, with their citations, including the challenge-flag variation
**And** the tier tables by depth region appear, with the rule that armour picks its class by tier
index rather than by the category weights
**And** the special and secret room solution items appear, so a floor's guarantees are derivable
**And** `GuaranteeArithmeticTest` asserts the dumped schedules reproduce the game's own decisions
over a swept range of depths and counter states.

### Story 2.5: Measured combat tables

As the bot,
I want hit chance and damage as measured tables rather than transcribed formulas,
So that my expectations match what the engine actually does.

**Acceptance Criteria:**

**Given** FR-14's rule that combat behaviour enters the Codex as measurement, not transcription
**When** the generator runs the engine's own hit and damage methods over a parameter grid
**Then** the tables cover accuracy against evasion across the ranges the game produces, and damage
and damage-reduction distributions for each weapon and armour tier
**And** each table names the method it measured with its `path:line`, and the grid it swept
**And** `CombatTableStabilityTest` asserts the tables are reproducible across runs and across the
two supported platforms
**And** no combat formula is written out by hand anywhere in `codex` or `brain`.

### Story 2.6: Traps, recipes, levels and rooms

As the bot,
I want the trap catalogue, the alchemy recipes and the level structure,
So that I can plan a floor and use the alchemy pot.

**Acceptance Criteria:**

**Given** the trap classes, recipe subclasses and level and room classes
**When** the generator dumps them
**Then** each trap carries its effect, whether it can be hidden and whether it can be searched
**And** each recipe carries its inputs, outputs and cost
**And** the level structure carries the depth and branch map, boss depths, shop depths, the room
lists per region, the special and secret room pools with their guaranteed solution items, and the
level feelings
**And** the sealing rule for boss floors is captured, since the Observation exposes it.

### Story 2.7: Text, assets, changelog and journal documents

As the Overlay,
I want the game's own words and the version record,
So that the instrument speaks the game's vocabulary and the docs can date a claim.

**Acceptance Criteria:**

**Given** the message bundles, `Assets`, the changelist package and the journal documents
**When** the generator dumps them
**Then** every player-facing string is keyed by the class it belongs to
**And** the asset index includes the six files loaded by literal strings outside the asset class,
which the codebase map identified
**And** the changelog dump records each version entry and its date where the game states one, and
records that the pinned version's own entry carries no date
**And** the guide and lore pages appear with their identifiers.

### Story 2.8: The vanilla-versus-Shattered vocabulary diff

As the lore pipeline,
I want to know which names mean different things in the two games,
So that a forum claim about the wrong game can be rejected automatically.

**Acceptance Criteria:**

**Given** FR-16 and the second pinned source named in PRD section 11
**When** the diff is generated
**Then** it lists names present in one game only, and names present in both whose mechanics differ
**And** each row cites both sources
**And** the diff is consumed by nothing yet and is marked as the input the E7 variant classifier
will use.

### Story 2.9: The drift check and the generated documentation

As a reviewer,
I want CI to fail if the committed Codex no longer matches the code,
So that the Codex cannot quietly become folklore.

**Acceptance Criteria:**

**Given** FR-15
**When** CI regenerates the Codex
**Then** the build fails if the output differs from the committed version, and the failure message
names the first differing file
**And** the generated documentation pages appear in the docs site under Codex and build under
`--strict`
**And** a deliberate edit to a committed Codex file makes CI fail, verified once in this story.

### Story 2.10: The citation checker

As the engineer,
I want every citation in the documentation checked against the pinned code,
So that a claim cannot quietly stop being true.

**Acceptance Criteria:**

**Given** FR-17
**When** the citation checker is written
**Then** the checker reports any `path:line` citation in `docs/` that no longer resolves at the
pinned tag, and runs in CI
**And** the checker is wired into the upgrade procedure's step that re-verifies rules after a tag
merge (FR-50)
**And** the 275 rules written in bootstrap session 10 pass the checker unchanged
**And** the Brain's own Rules index, which enumerates the claims the Brain relies on, is named as
owned by story 4.4, since no Brain exists yet to rely on anything.

---

## Epic 3: Rig

Make every claim about the bot measurable by a stranger. Thousands of seeded Runs in parallel,
paired comparison under a sequential test whose bounds were calibrated rather than guessed,
hash-chained logs, replay with verification, and published pages that carry the command that
reproduces them. From this epic onward no Brain change merges without numbers.

Covers FR-19 to FR-26, NFR-2, NFR-3, NFR-9, SM-5.

### Story 3.1: Seed sets as committed, versioned files

As a skeptic,
I want the exact seeds a number was measured on,
So that I can run the same set myself.

**Acceptance Criteria:**

**Given** FR-20
**When** the seed sets are created
**Then** `smoke` (25), `standard` (500), `holdout` (500), `bosses` (100) and `goo` (400 Warrior
triples) exist as committed files of (seed, hero class, challenge flags) triples, each with a
version
**And** a Results file names the set and its version, and therefore the classes and flags it fixes
**And** the sizes are revisable by ADR once throughput is known, which this story notes rather than
resolves.

### Story 3.2: Run logs with a hash chain

As a skeptic,
I want a per-Run record I can verify without trusting the tool that wrote it,
So that a published number cannot be quietly edited.

**Acceptance Criteria:**

**Given** ADR-0011
**When** the log writer is implemented
**Then** each Run writes plain `<run-id>.jsonl` whose id includes the Brain, so the two Runs of a
pair never collide
**And** the record kinds are header, wait, prompt, mode, shadow, boundary, unsupported and end,
each keyed by the wait index
**And** the chain is computed exactly as the ADR states, excluding the timing field, and
`ChainRecomputeTest` recomputes every chain from the file alone
**And** a Run killed mid-way leaves a readable prefix whose chain still verifies
**And** the canonicalization rules and a test vector are published on the methodology page.

### Story 3.3: The parallel runner

As the developer,
I want to run many seeded Runs at once,
So that a comparison finishes in a working session rather than a week.

**Acceptance Criteria:**

**Given** AD-6's rule that one process hosts one Run
**When** `./gradlew :rig:run --args="--brain <name> --seeds <set|N> --parallel P --out <dir>"` runs
**Then** each Run gets its own process, Profile directory and working directory
**And** the runner reports throughput and the number of processes used
**And** a crashed or hung Run is recorded as incomplete rather than lost, with its partial log kept
**And** `RunnerIsolationTest` asserts two concurrent Runs cannot see each other's Profile.
**And** the runner refuses any Run whose log header has the oracle flag set, which is the E3 half
of FR-11.

### Story 3.4: Replay with verification

As a skeptic,
I want to re-run a published Run from its log and get the same thing,
So that reproduction is mechanical.

**Acceptance Criteria:**

**Given** FR-24
**When** `ReplayDriver` reads a log
**Then** it refuses a log whose schema, tag, Observation version or Profile version differs, with a
message saying which
**And** it applies each recorded Action and compares the fresh Observation hash against the record,
naming the differing section on a mismatch
**And** it stops with "unverifiable from wait k" at an unsupported record
**And** `ReplayRoundTripTest` replays a full random-agent Run and asserts every hash matches
**And** the nightly cross-platform job replays a published Run on Windows and Linux and compares
chains (NFR-2).

### Story 3.5: Registration and the salt discipline

As the product owner,
I want the hypothesis fixed before the numbers are seen,
So that a result cannot be chosen after the fact.

**Acceptance Criteria:**

**Given** FR-22 and ADR-0012
**When** a comparison is registered
**Then** the Registration commits the hypothesis id, both Brains with their commits and
configuration hashes, the seed set and version, the bounds, the burn-in, the maximum, the budget
and the machine class, and it is committed before the Run
**And** the salts are drawn by the runner when each pair executes, written to both Run logs, and
are **not** in the Registration, so a Brain's author cannot precompute the random stream
**And** the runner refuses to start a comparison with no committed Registration
**And** a standing Registration exists for the nightly smoke job.
**And** the runner refuses a comparison on the `holdout` set unless the Registration declares a
release-level claim, and refuses more than one `holdout` use per Brain version; this is the single
place the rule lives
**And** one comparison ledger records every Registration, its outcome and every `holdout` use, so
that the count of prior attempts behind a published claim is public (FR-22, FR-25).

### Story 3.6: The Per-pair statistic and the sequential test

As the developer,
I want an early-stopping comparison with stated error rates,
So that I learn which way a change points without running forever.

**Acceptance Criteria:**

**Given** ADR-0012
**When** `PairScore` and `Gsprt` are implemented
**Then** the pair statistic compares the two composite outcomes lexicographically and scores one,
a half or zero, and pairs on the (seed, class, flags) triple with a shared salt
**And** a pair with a missing Run is scored as a tie and counted separately
**And** the test reports accept, reject or undecided with its log-likelihood trace, and never stops
before the burn-in
**And** the port is faithful to the two upstream files the ADR names, verified by
`GsprtReferenceTest` against values computed from the reference implementation.

### Story 3.7: Calibrate the bounds

As a skeptic,
I want the error rates measured on this project's own outcomes,
So that the bounds mean what they claim.

**Acceptance Criteria:**

**Given** FR-21's rule that realized error rates are validated by simulation before a bound is
trusted
**When** the calibration simulates the sequential test on outcome distributions bootstrapped from
the random-agent Runs
**Then** the realized false-accept and false-reject rates are reported at the chosen burn-in and
maximum
**And** the chosen bounds are published on the methodology page with the simulation that produced
them
**And** the tie fraction of the pair statistic is reported, since a mostly-tied statistic would not
resolve
**And** the acceptable margin between realized and nominal error is declared here as a number, so
that story 3.8 has a criterion to test against.

### Story 3.8: The e-process alternative

As a skeptic,
I want the sequential test compared against a design that needs no pre-registered alternative,
So that the choice of statistic is itself evidence-based.

**Acceptance Criteria:**

**Given** ADR-0012's decision to evaluate the alternative rather than assume it
**When** the e-process is implemented alongside the sequential test
**Then** both are run on the same simulated distributions from story 3.7, and their realized error
rates and stopping times are reported side by side
**And** the e-process replaces the sequential test as the gate if the sequential test's realized
error exceeds its nominal rate by more than the margin story 3.7 declared
**And** whichever is chosen, the methodology page states which, why, and with what numbers
**And** the losing design stays in the tree behind a flag, so the comparison can be re-run on a
later tag.

### Story 3.9: The baseline and the deliberately worse Brain

As the product owner,
I want proof that the Rig can tell better from worse,
So that its verdicts on real changes mean something.

**Acceptance Criteria:**

**Given** SM-5
**When** the random-agent Baseline is run on the standard set and published
**Then** its Results page exists with a reproducible command
**And** a deliberately worse Brain, defined as the random agent with the descend Action removed, is
**rejected** by the sequential test, and the rejection is published
**And** the measured within-pair correlation is reported, answering the research's open question
about what pairing buys
**And** the E1 throughput numbers are restated beside the Rig's own cost per comparison.

### Story 3.10: Results pages and the methodology page

As a community reader,
I want a published number to carry everything needed to check it,
So that trust is unnecessary.

**Acceptance Criteria:**

**Given** FR-25
**When** a Results page is generated
**Then** it carries the tag, the Shatterfish commit, the seed set and version, both Brains, the
hypothesis id and its Registration commit, the bounds and units, the outcome with its trace, the
per-Run aggregates with distributions, the measured pair correlation, the survival curve, the boss
staircase, links to the Run logs, the fairness suite status, that oracle mode was off, the count
of prior registered attempts behind the claim taken from the comparison ledger, and the command
that reproduces it
**And** the methodology page explains the statistic, the canonicalization, the mix function's test
vector and what to do when platforms disagree
**And** negative and undecided results are published on the same terms as positive ones, which this
story demonstrates with a real undecided run.

### Story 3.11: The nightly job and the results pull request

As the product owner,
I want the smoke set run every night against the baseline,
So that a regression is caught without anyone remembering to look.

**Acceptance Criteria:**

**Given** ADR-0002's CI shape
**When** the nightly workflow runs
**Then** it runs the smoke set under the standing Registration and updates one results pull request
**And** it labels the run a direction check, never an acceptance
**And** a failure or an undecided outcome is visible without opening the logs
**And** the job makes no network call beyond GitHub's own API (NFR-8).

### Story 3.12: The death gallery

As the developer,
I want to see how the bot dies, grouped by cause,
So that I know what to fix next.

**Acceptance Criteria:**

**Given** FR-26
**When** a Rig invocation completes
**Then** the gallery groups Runs by cause of death and depth, with counts and the seeds
**And** each entry links to its Run log and, on request, to a snapshot of the final waits
**And** the gallery is plain text or a generated docs page, readable without tooling (NFR-9)
**And** the E4 half of this requirement, the per-Brain comparison view, is explicitly deferred to
E4 and noted here.

---

## Epic 4: Baseline brain

A hand-built bot that plays the sewers the way a competent human does: explores, fights in
corridors, eats, tests unknown items when the worst case is survivable, and descends. It is a pure
function of what it saw, so a human can take its turn at any moment. This epic ends with the first
number the program actually cares about.

Covers FR-27 to FR-33, FR-36, the E4 consequences of FR-9 and FR-26, SM-3.

Every story in this epic carries Rig numbers in its pull request: a smoke-set direction check
against the Brain as it stood before the change, and for story 4.14 a registered comparison on the
`goo` Seed set. Story 4.1 has no previous Brain to compare against and is exempt, which it states.

### Story 4.1: The Brain skeleton, arbitration and re-planning

As the bot,
I want to decide afresh from what I see at every Input wait,
So that a human can take a turn without desynchronising me.

**Acceptance Criteria:**

**Given** FR-27 and FR-28
**When** `Brain.decide(Observation, Belief)` and `Brain.update(Observation, Belief)` are
implemented with a priority list of interruptible Policies with cheap entry predicates
**Then** the Brain never assumes its previous Decision was executed, asserted by
`ReplanAfterForeignActionTest`, which feeds a Decision, applies a different Action, and asserts the
next Decision is computed from the new Observation
**And** the Belief is an opaque `api` value, so `harness` can hash it without depending on `brain`
**And** the Codex is read from disk by the caller, never by `brain`, which cannot open a file, and
is handed to the Brain as an `api` value at construction, with its version recorded in the Run log
**And** the Brain is deterministic given the Observation sequence and its seeded generator,
asserted by `BrainDeterminismTest`
**And** the boundary rules stay green, including the ban on file, network and reflection access.
**And** this story is exempt from the epic's Rig-numbers rule, since it is the first Brain and has
nothing to compare against; the exemption is stated in the pull request.

### Story 4.2: Beliefs about unknown items and the floor

As the bot,
I want candidate identities with probabilities and the facts a floor implies,
So that I can reason about what I have not identified.

**Acceptance Criteria:**

**Given** FR-29
**When** Beliefs are implemented
**Then** each unidentified item carries a candidate set with probabilities weighted from the Codex
spawn weights and narrowed by identification history
**And** floor facts are recorded, so that seeing a pool room implies an invisibility potion on this
floor
**And** chapter counters track the guaranteed strength potions and upgrade scrolls
**And** monsters seen and then lost are remembered, with their last known position and the fact
that it is stale
**And** `BeliefConsistencyTest` asserts probabilities sum correctly and that identifying one type
removes it from every other candidate set.
**And** the pull request carries a smoke-set direction check against the previous Brain, per this
epic's rule that every Brain-affecting change carries Rig numbers.

### Story 4.3: safeTest, the worst-case check

As the bot,
I want to know the worst thing that can happen if I try an unknown item here,
So that I test items when it is survivable and not when it is not.

**Acceptance Criteria:**

**Given** FR-30
**When** `safeTest(item, cell)` is implemented
**Then** it scores the worst case over the candidate identities using the Codex tables, the
terrain and the visible enemies
**And** it covers unknown potions, scrolls and wands with the same code
**And** `SafeTestWorstCaseTest` asserts that a lethal worst case is refused regardless of the mean
outcome, and that standing next to water changes the verdict for a fire candidate
**And** the function is pure and testable without a running game.
**And** the pull request carries a smoke-set direction check against the previous Brain, per this
epic's rule that every Brain-affecting change carries Rig numbers.

### Story 4.4: The Decision output and the strategy log

As a person watching,
I want to see the goal, the chosen action, the alternatives and why,
So that I can tell whether the bot is thinking or flailing.

**Acceptance Criteria:**

**Given** FR-32 and FR-36
**When** the Decision is produced
**Then** it carries the current Goal in plain words, the chosen Action with its score, up to three
alternatives with scores and one-line reasons, the Safety flags, and the Policy that fired
**And** the reasons use Codex vocabulary and the instrument's voice, labels and numbers rather than
sentences (UX-DR13)
**And** the map highlight cells are part of the Decision, so the Overlay and the Replay scrubber
read them rather than re-deriving them
**And** the strategy log is plain text readable without tooling (NFR-9)
**And** `DecisionShapeTest` asserts every Decision has a Goal and at least one alternative when one
exists.
**And** the pull request carries a smoke-set direction check against the previous Brain, per this
epic's rule that every Brain-affecting change carries Rig numbers.
**And** the Brain's Rules index is created here, enumerating the mechanics claims the Brain relies
on, each pointing at a rule page row, so that "every heuristic is cited" is countable (FR-17).

### Story 4.5: The Evaluation with weights as data

As the developer,
I want one scoring function whose weights live in a file,
So that the Rig can tune it without a code change.

**Acceptance Criteria:**

**Given** FR-33
**When** the Evaluation is implemented over Observation features derived from Codex tables
**Then** the weights are a committed, versioned `api`-typed data file, not constants in code
**And** the file is read by the caller, never by `brain`, which cannot open a file, and is handed
to the Brain as an `api` value at construction
**And** changing a weight changes behaviour with no recompilation of `brain`
**And** the weight file version appears in the Brain's configuration hash, so a Registration
distinguishes two weight sets
**And** `EvaluationMonotonicityTest` asserts obvious orderings, such as more hit points at equal
depth scoring higher.
**And** the pull request carries a smoke-set direction check against the previous Brain, per this
epic's rule that every Brain-affecting change carries Rig numbers.

### Story 4.6: The explore Policy

As the bot,
I want to uncover the floor efficiently,
So that I find the stairs and the items before I starve.

**Acceptance Criteria:**

**Given** FR-31
**When** the explore Policy is implemented
**Then** it moves toward the nearest unexplored frontier reachable through known-passable cells
**And** it issues one step per Input wait, so a human can interrupt at any cell
**And** it searches for secret doors when the floor is otherwise exhausted, with a bounded number
of attempts
**And** it yields to any higher-priority Policy when an enemy becomes visible
**And** the pull request carries a smoke-set direction check showing depth reached moving the right
way.

### Story 4.7: The fight-in-corridors Policy

As the bot,
I want to fight where only one enemy can reach me,
So that I survive fights a human would survive.

**Acceptance Criteria:**

**Given** FR-31
**When** the fight Policy is implemented
**Then** it prefers a cell where the number of adjacent open cells limits how many enemies can
engage, using the Codex threat tables to decide whether to fight or retreat
**And** it never targets a character absent from the Observation
**And** it accounts for the boss-floor seal, so it does not plan a retreat through stairs the game
has locked
**And** `FightPolicyTest` asserts corridor preference on a constructed room-and-corridor map
**And** the pull request carries a smoke-set direction check.

### Story 4.8: The pick-up and equip Policies

As the bot,
I want to take what is worth taking and wear what is better,
So that my equipment improves as the run goes on.

**Acceptance Criteria:**

**Given** FR-31
**When** the Policies are implemented
**Then** the bot picks up an item when its Evaluation contribution exceeds the cost of the turns
to reach and take it, computed from the Codex tables, and `PickupThresholdTest` asserts the
ordering on constructed cases: a strength potion two cells away is taken, a single gold piece
across an explored room is not
**And** it equips a weapon or armour when the Evaluation prefers it, accounting for the strength
requirement and the risk that an unidentified item is cursed
**And** it never equips an item whose worst case under `safeTest` is unsurvivable
**And** the pull request carries a smoke-set direction check.

### Story 4.9: The eat and heal Policies

As the bot,
I want to eat before I starve and heal before I die,
So that I do not lose runs to bookkeeping.

**Acceptance Criteria:**

**Given** FR-31
**When** the Policies are implemented
**Then** the bot eats when the hunger state reaches hungry and food is held, and never wastes food
at full satiety
**And** it drinks a known healing potion when hit points fall below a threshold derived from the
visible threat, not a constant
**And** `StarvationRegressionTest` asserts that a Run with food available never ends with cause
starvation
**And** the pull request carries a smoke-set direction check.

### Story 4.10: The test-unknown-items Policy

As the bot,
I want to identify unknown items by using them when it is safe,
So that I learn what I am carrying without dying to it.

**Acceptance Criteria:**

**Given** FR-31 and FR-30
**When** the Policy is implemented
**Then** it tests an unknown item only where `safeTest` says the worst case is survivable, and
prefers cells that reduce the worst case, such as beside water or a door
**And** it prefers testing when the information is worth most, early on a floor rather than during
a fight
**And** it records the outcome into Beliefs so candidate sets narrow
**And** the pull request carries a smoke-set direction check.

### Story 4.11: The answer-prompts Policy

As the bot,
I want to answer the game's questions sensibly,
So that a subclass choice or a shop does not stall the run.

**Acceptance Criteria:**

**Given** FR-31 and the Prompt kinds in the Observation
**When** the Policy is implemented
**Then** every Prompt kind the game can open has an answer rule, and an unrecognised Prompt is a
Brain error rather than a stall
**And** `Wait` is never returned while a Prompt is open
**And** `PromptCoverageTest` asserts every Prompt kind the executor supports has a rule.
**And** the pull request carries a smoke-set direction check against the previous Brain, per this
epic's rule that every Brain-affecting change carries Rig numbers.

### Story 4.12: The descend Policy

As the bot,
I want to go down when this floor has given me what it will,
So that I make progress instead of grinding.

**Acceptance Criteria:**

**Given** FR-31
**When** the Policy is implemented
**Then** the bot descends when the floor's remaining value falls below the risk of staying, using
explored fraction, remaining guaranteed drops and hunger
**And** it does not attempt to descend while the floor is sealed
**And** the composite outcome does not reward diving, so the pull request reports depth beside the
survival curve
**And** the pull request carries a smoke-set direction check.

### Story 4.13: Reach the Goo gate on the smoke set

As the product owner,
I want the Warrior to beat the first boss reliably,
So that the program has cleared its first real rung.

**Acceptance Criteria:**

**Given** SM-3 and FR-31
**When** the Brain is tuned against the smoke set until the Goo kill rate looks sufficient
**Then** the smoke-set kill rate and the failure causes are published as a direction check, never
as an acceptance
**And** the death gallery's per-Brain comparison view is built here and used to find the dominant
failure cause (the E4 half of FR-26)
**And** no acceptance claim is made from the smoke set, which is a direction check by definition.

### Story 4.14: Pass the Goo gate on the registered set

As the product owner,
I want the first real rung cleared and published,
So that the program has a number it can stand behind.

**Acceptance Criteria:**

**Given** SM-3 and a Registration committed before the Run
**When** the Brain is run on the `goo` Seed set of 400 Warrior triples
**Then** the Warrior kills Goo on at least 75% of the set with a lower confidence bound of at least
70%
**And** the Results page is published with the survival curve, the boss staircase and the command
that reproduces it
**And** the behavioural differential test runs on real standard-set seeds with hidden identities
permuted, and the Brain's Decisions are identical until the Observations diverge (the E4 half of
FR-9)
**And** the throughput benchmark is re-run with the Brain attached, completing the measurement
story 1.21 deferred
**And** the E4 retrospective runs and its lessons land in `CLAUDE.md`.

---

## Epic 5: Overlay v1

Put the bot inside the real game as a visibly separate instrument, so a person can watch it think,
advance it one Input wait at a time, and take the controls mid-fight without breaking anything.
This is the debugger for everything built after it, and the first thing anyone will actually see.

Covers FR-37 to FR-43, the E5 consequences of FR-4, FR-11, FR-12, FR-23, FR-27 and FR-36, NFR-4,
UX-DR1 to UX-DR15, SM-6.

The Overlay does not change what the Brain decides, so most stories here carry no Rig numbers. Two
do, and say so: story 5.13, because routing the render-thread draws changes what the random stream
consumes, and story 5.16, which is the epic's demonstration.

### Story 5.1: The launcher, the Profile and the embedded driver

As the human,
I want to start the game with the bot attached,
So that the Overlay exists at all.

**Acceptance Criteria:**

**Given** FR-37 and ADR-0013
**When** the launcher starts the desktop game with a Run Profile it owns
**Then** the driver attaches at scene creation and re-attaches after every level change, keeping
the wait index, the salt, the Belief and the Run log across the boundary
**And** the render thread is the UI-role thread, the Brain runs on its own worker, and the game's
frame rate is unaffected while the Brain thinks (NFR-4)
**And** the oracle flag exists only on the launcher, never on the Rig
**And** `EmbeddedAttachTest` asserts attachment and re-attachment across two level changes.

### Story 5.2: The Panel frame, layout and collapse

As the human,
I want an instrument docked beside the dungeon that never covers the game's own HUD,
So that it reads as a separate tool rather than a mod.

**Acceptance Criteria:**

**Given** UX-DR1, UX-DR2 and DESIGN.md
**When** the Panel is built as a component from the game's own toolkit
**Then** it docks at the right edge left of the inventory pane, between the status pane and the
toolbar, translucent, never over the HUD
**And** the Overlay applies its own horizontal camera offset and re-applies it after the game's
layout pass, since the game's own offset is vertical only and gets overwritten
**And** it targets 200 UI pixels wide, minimum 160, and collapses to the Mode strip below the
documented thresholds or in the mobile layout
**And** it uses only nine-patch frames, the game's text renderer and the documented sizes
**And** `OverlayToolkitTest` is an ArchUnit rule asserting that `overlay` imports nothing from
Swing, AWT, JavaFX or any web-view package, so the native-UI rule is a check rather than a habit.

### Story 5.3: The Mode strip, Goal line and Decision card

As the human,
I want to see what the bot is about to do and why, before it does it,
So that stepping is meaningful.

**Acceptance Criteria:**

**Given** UX-DR3 and FR-38
**When** the sections are implemented
**Then** the Mode strip shows the mode word in its colour, the speed mode with its interval, the
turn and the floor, and doubles as the collapsed Panel
**And** the Decision card shows the chosen Action with its score and up to three alternatives with
scores and one-line reasons
**And** in Next Step mode the card shows what the next press will execute
**And** numbers are right-aligned in fixed-width columns (UX-DR5)
**And** every state is stated in words, so colour never carries meaning alone (UX-DR14)
**And** `ModeStripContentTest` and `DecisionCardContentTest` assert the content of each section
against a constructed Decision, including the Explain expansion.
**And** the Explain control expands the Decision card in place to show the Policy that fired, the
alternatives' reasons in full and the Safety flags that applied, and a second press collapses it;
this is a v1 control (FR-39), not the v2 Explain view.

### Story 5.4: Safety flags, Belief summary and Decision log

As the human,
I want to see what the bot believes and what it is worried about,
So that I can spot a bad belief before it costs a run.

**Acceptance Criteria:**

**Given** FR-38 and UX-DR3
**When** the sections are implemented
**Then** Safety flags appear as chips whose text states the flag and whose colour follows its
verdict, absent when there are none
**And** the Belief summary shows unknown items with their top candidate and probability, then floor
facts and chapter counters
**And** the Decision log shows one line per Input wait with the turn, actor, Action and score, with
goal and mode changes as their own lines, newest at the bottom, auto-scrolling only while at the
bottom
**And** the log is a view over the Run log records rather than a second source of truth
**And** `BeliefSummaryTest` and `DecisionLogTest` assert the content, ordering and scroll behaviour
against constructed records.

### Story 5.5: PAUSED ignores hero input

As the human,
I want the game to ignore my clicks while the bot is paused,
So that I cannot accidentally move while reading a Decision.

**Acceptance Criteria:**

**Given** UX-DR8 and ADR-0013
**When** the input-gate hook lands
**Then** the gate is consulted by both the cell selector's select path and its key-hold path, since
the key-hold path bypasses listeners and forces the hero ready itself
**And** clicks, direction keys, the toolbar, the quickslots and the inventory pane all do nothing
while PAUSED
**And** `PausedInputTest` drives a click, a held direction key and a quickslot press while PAUSED
and asserts the Run log gains no Action and the hero has not moved
**And** `docs/UPSTREAM.md` gains the input-gate row in the same pull request.

### Story 5.6: The controls row and the enablement matrix

As the human,
I want to advance the bot one step, N steps, at a readable pace, or as fast as it goes,
So that I can watch a fight closely and skip a corridor.

**Acceptance Criteria:**

**Given** UX-DR7 and FR-39
**When** the controls row is implemented
**Then** the row holds Pause and Resume, Next Step, Run N with its count stepper, the speed
selector with its interval stepper, Take over and Hand back, and Explain, wrapping to two rows
**And** a control whose action is impossible in the current state is dim and non-interactive
**And** the Run starts in PAUSED with Next Step
**And** `ControlEnablementTest` asserts the full enablement matrix of UX-DR7 for every state, so no
state leaves a control enabled that would do nothing.

### Story 5.7: The speed modes and the stepping unit

As the human,
I want to advance the bot one step, N steps, at a readable pace, or as fast as it goes,
So that I can watch a fight closely and skip a corridor.

**Acceptance Criteria:**

**Given** UX-DR6 and FR-39
**When** the speed modes are implemented
**Then** Next Step advances exactly one Input wait; Run N advances N and lands in PAUSED; Human
play speed advances one Input wait per configurable interval, default one second, range half a
second to five; Fast as it can advances as soon as the Brain returns
**And** switching speed mode never loses a turn, taking effect at the next Input wait
**And** a Next Step pressed while the Brain is over budget is queued and fires when the Decision
lands, with the Mode strip showing THINKING
**And** the steppers hold their values for the session only, with the documented defaults and ranges
**And** `SpeedModeTest` asserts the advance count for each mode and that a queued Next Step fires
exactly once.

### Story 5.8: Take over and hand back

As the human,
I want to play a few turns myself and give the controls back,
So that I can rescue a run or test an idea.

**Acceptance Criteria:**

**Given** FR-40 and UX-DR9
**When** Take over and Hand back are implemented
**Then** Take over opens the gate at the next Input wait, and Hand back lands in PAUSED with a
fresh Decision computed from the current Observation
**And** a control pressed mid-animation stays dim until the hero is ready, then applies
**And** `TakeoverHandbackTest` drives a takeover and a hand back in a scripted Run and asserts the
Mode transitions and that Hand back lands in PAUSED with a Decision computed after the human's
last turn.

### Story 5.9: Recording human turns and the shadow Decision

As a skeptic,
I want a human's turns recorded as faithfully as the bot's,
So that a Run with a takeover still replays.

**Acceptance Criteria:**

**Given** FR-40, FR-23 and ADR-0013
**When** the human plays in HUMAN mode
**Then** every human Action is recorded with actor human, taken from the hero's current action after
the game resolves the input and from the notification sites the hook covers, which include item
use, resting, searching, talent and ability use and a window's own button
**And** an input the executor cannot express is recorded as unsupported and ends replay
verifiability from that wait, with the Panel saying so
**And** the Brain still updates its Belief at every Input wait and produces a shadow Decision, shown
greyed on the card and written to the log, never executed
**And** a Decision tagged with a wait index that is no longer current is logged as skipped and never
executed
**And** `HumanTurnReplayTest` records a session with three human turns and replays it under the Rig
with every Observation hash matching.

### Story 5.10: Map highlights

As the human,
I want to see the planned path and target on the dungeon itself,
So that I understand the Decision spatially.

**Acceptance Criteria:**

**Given** FR-41 and UX-DR10
**When** highlights are drawn
**Then** the planned path, target cell and considered cells are outlined in their documented
colours, never filled and never over sprites
**And** they are drawn when the Decision is made and cleared when the hero acts or the plan changes
**And** they are never drawn in HUMAN mode
**And** the cells come from the Decision record, not from a second computation
**And** `MapHighlightTest` asserts the drawn cells equal the Decision's highlight cells and that
none is drawn in HUMAN mode.

### Story 5.11: Hotkeys as game actions

As the human,
I want keyboard control that the game's own settings screen can rebind,
So that the instrument behaves like part of the game.

**Acceptance Criteria:**

**Given** FR-42 and UX-DR12
**When** the registration hook lands
**Then** the Overlay's controls are registered game actions with defaults F6 to F11, which the
pinned tag leaves unbound
**And** they appear in the game's own key-binding screen and can be rebound there
**And** every control remains reachable as a button, so the Overlay is usable if the registration
is ever removed
**And** no Overlay default shadows an existing game binding, asserted by `KeyBindingConflictTest`.

### Story 5.12: Oracle marking in the Overlay

As the human,
I want an unmistakable mark when the bot is cheating for debugging,
So that I can never confuse a debug run with a real one.

**Acceptance Criteria:**

**Given** FR-43 and UX-DR11
**When** the game is launched with the oracle flag
**Then** a red border surrounds the game view, the Mode strip carries an ORACLE label, oracle rows
in the Belief summary are prefixed, and unseen enemies are outlined on the map in the oracle colour
**And** nothing in the Overlay can turn oracle mode on or off at runtime
**And** the oracle colour appears nowhere else in the instrument
**And** the Run log header records that the Run was an oracle Run.

### Story 5.13: The speed ceiling and the draw-routing hook

As the human,
I want the fastest mode to actually be fast, and the run to still replay,
So that watching and measuring do not contradict each other.

**Acceptance Criteria:**

**Given** FR-39 and ADR-0007
**When** the sprite-wait bypass and the draw-routing hooks land
**Then** Fast as it can is no longer limited by the animation interval
**And** the emitter, music and emote random draws are routed to the base generator, so an Overlay
Run and a headless Run consume the same draws within a turn
**And** `OverlayReplayTest` records an Overlay Run and replays it under the Rig, asserting every
Observation hash matches
**And** both hook rows are added to `docs/UPSTREAM.md` in the same pull request
**And** the pull request carries a smoke-set direction check, since routing the draws changes what
the random stream consumes and could move outcomes.

### Story 5.14: Run over, save and resume

As the human,
I want the instrument to behave sensibly when the run ends or I quit,
So that nothing is lost and nothing lies.

**Acceptance Criteria:**

**Given** UX-DR7 and ADR-0013
**When** the hero dies or wins
**Then** the Mode strip reads RUN OVER with the cause read from the game's own record, the log stays
readable and scrollable, every control but the Panel toggle is disabled, and the Run log path is
shown
**And** an Ankh resurrection is answered as an ordinary Prompt rather than treated as the end
**And** on save and quit the driver writes a boundary record with the wait index, salt and chain,
and on resume through the launcher it continues them and starts in PAUSED with Next Step
**And** a save opened without the launcher is not an Overlay Run, and its log ends at the boundary.

### Story 5.15: The remaining Panel states

As the human,
I want the instrument to tell me when something has gone wrong rather than freezing,
So that I can trust what it shows.

**Acceptance Criteria:**

**Given** UX-DR7's state list and FR-38
**When** the remaining states are implemented
**Then** a Brain that throws produces a Decision of wait, a card reading brain error with the
exception class, a log line, and the Overlay entering PAUSED, and the game never crashes
**And** no valid action shows its own card text, waits once, and after three consecutive
occurrences enters PAUSED and logs it
**And** hero busy dims the pressed control until the hero is ready, then applies the change
**And** no Run, meaning the title screen, menus and loading, hides the Panel entirely, and the
Overlay attaches when a scene appears with a living hero
**And** `PanelStateTest` reaches each of these states in a scripted test and asserts the Panel's
content and enablement matrix, so every state of UX-DR7 is covered by a test.

### Story 5.16: The full sewers run with a takeover

As the product owner,
I want to watch a whole sewers run and take over mid-fight,
So that the epic's promise is demonstrated rather than asserted.

**Acceptance Criteria:**

**Given** SM-6
**When** a person watches a Warrior run from depth one to the Goo fight
**Then** the run completes with the Panel showing a Decision at every Input wait
**And** the person takes over mid-fight, plays at least three turns, and hands back, with the Brain
re-planning from the current state and no desync
**And** the Run log replays exactly afterwards, including the human turns
**And** the session is recorded on a Results page with the log attached, and a standard-set
comparison confirms the Overlay's presence did not change outcomes
**And** `SewersRunSmokeTest` runs the same scenario headlessly as a regression guard
**And** the E5 epic retrospective runs and its lessons land in `CLAUDE.md`.

---

## Epic 6: Tactical search

*Opened 2026-10-25, after E5: the Overlay is the debugger a search needs, and ADR-0010 made the
epic conditional on measurements that only the finished Harness, Rig and Brain can take.*

Better fighting, if and only if the numbers say so. ADR-0010 fixes the choice rule: the one-ply
model over Codex tables ships first, then the simulator speed, the two transferable Long et al.
properties and the search leak test are measured and published, and only then does the Rig decide
between candidates at equal budget. If no candidate is accepted, the one-ply model stays the
tactical layer, the measurements stay published, and the epic closes honestly.

Covers FR-13, FR-34, and the E6 half of FR-6.

Information parity (non-negotiable 1) binds every story here, and the two fair designs are the
only ones allowed (bootstrap section 4): an abstract tactical model built from Observations,
Beliefs and the Codex, or engine rollouts on a redetermined snapshot reached through the `api`
`Simulator` and an opaque `SnapshotHandle`. There is no third design and no second implementation
of the game's rules (non-negotiable 4). Every story that touches `brain`, the scrubber or the
rollout host carries the `fairness` label, and the `fairness-reviewer` subagent runs before its PR
opens. Every claim about a mechanic is cited to the pinned code at `v4.0.2`.

The mechanics the abstract model reads, stated once so the stories do not repeat them. A swing
lands when a uniform draw below the attacker's accuracy is at least a uniform draw below the
defender's evasion, each scaled by the same list of buff factors, unless either side is at the
infinite sentinel, where infinite evasion beats infinite accuracy (`Char.java:619-683`: the sentinels
at `:612-613` and `:638-643`, the two draws at `:646` and `:662`, the comparison at `:678`). A landed
swing rolls the attacker's damage, subtracts the defender's armour roll and applies the attacker's
multipliers (`Char.java:390-416`). A mob's swing costs `attackDelay()`, one turn unless it is hasted
(`Mob.java:790-794`, spent at `:805` and `:814`). The Codex already measures the damage side
(`codex/v4.0.2/combat.json`: `weapons`, `armours` and `mobs` carry a spread per entry, each with a
`path:line`), but its `hit` entry is recorded as unmeasured, because `Char.hit` initialises a
floating-text class that a headless generator may not boot. Story 6.1 deals with that gap.

### Story 6.1: The one-ply expectimax over Codex tables

As the bot,
I want to pick my fight action by scoring each of my options against the expected damage and hit
chance of every enemy I can see,
So that my fighting is decided by arithmetic on the game's own tables, with no engine, and the
Rig gets a second Brain to compare.

**Acceptance Criteria:**

**Given** ADR-0010 option 2, FR-34 and FR-31
**When** the fight search is implemented in `brain`
**Then** it enumerates the Observation's valid-Action set filtered to fights (attack, step, retreat
along a visible path, use a consumable the Belief says is known), not a hand-picked list
**And** it scores each option for one hero turn plus the enemy replies: for each visible enemy the
hit probability and the expected post-armour damage come from Codex tables only, by the hit rule and
damage rule cited above, and the option with the best expected hero hit points minus expected enemy
hit points is chosen, ties broken by the stable action order
**And** it runs only at Input waits with a visible enemy and on boss floors; quiet turns stay with
the Policies (FR-34)
**And** it reads an enemy's health from the health-bar bucket the Observation shows and never from
a true hit point value, and it never targets a character absent from the Observation.

**Given** the Codex's `hit` entry is unmeasured
**When** the model needs a hit probability
**Then** the Codex generator is extended to emit it from the two uniform draws of `Char.hit`
(`Char.java:646`, `:662`) as an exact table over the accuracy and evasion grid the entry already
declares, with the citation; this is a Codex change under E2's drift rule (regenerate, CI fails on
drift), not a second rules implementation, since it tabulates a cited formula and does not replay
a fight
**And** `HitTableTest` asserts the table against an independent closed form at the grid corners and
against a seeded sampling run within a stated tolerance.

**Given** the Brain boundary
**When** the module is built
**Then** the `brain` ArchUnit rules still pass with no new import from game packages
**And** `OnePlyModelTest` asserts, on constructed rooms, that the model attacks a weak enemy it is
likely to kill, retreats from one it is likely to lose to, prefers the cell the 4.7 Policy prefers
in a corridor, and picks the same Action when the Observation is the same
**And** `OnePlyLeakTest` runs the model on a pair of worlds that differ only in hidden state (the
existing hidden-state differential fixtures) and asserts identical Decisions, so the first fairness
evidence exists before the search leak test of story 6.3.

**Given** the Rig
**When** the model is registered as a Brain next to the 4.x baseline
**Then** the pull request carries the direction table on the `smoke` set and on the first 40 seeds of
`bosses` and `standard` (`:rig:direction`, baseline `main`), and says plainly that it is a direction
check and not an acceptance, which is story 6.6's
**And** the fairness-reviewer subagent has read the diff and its verdict is in the pull request.

### Story 6.2: The redetermination scrubber and its differential test

As the engineer,
I want a scrubber that turns a live snapshot into one a rollout may run on, and a host that rolls
out from it,
So that engine rollouts are possible without a search ever seeing hidden state.

**Acceptance Criteria:**

**Given** ADR-0009 and FR-6
**When** `SnapshotScrubber` is implemented in `harness` as a `Redeterminer`
**Then** every row of the ADR-0009 scrub table is implemented and named in the code: unidentified
potion, scroll and ring classes permuted among unknown classes; unknown item level and curse
re-rolled in range; mobs outside the field of view removed and the sample's remembered mobs re-added
on visited cells; a stealthy passive mimic on a visited cell kept; the AI state and exact hit points
of every mob re-sampled within what the screen shows; unknown cells made solid with their heaps,
traps, blobs and transitions removed; secret traps and doors on known cells turned into floor and
wall; container contents redrawn from the Codex tables; the dungeon seed replaced; generator decks,
limited drops, special and secret room queues, quest state, unshown notes and statistics reset to
defaults or to the sample; and the rollout's own salt taken from the sample
**And** before any code, the fairness reviewer audits the scrubber's bundle key list against
`Dungeon.saveGame` line by line, as ADR-0009's pre-mortem requires, and the audit is written into
the story file
**And** `Redeterminer.scrub` still rejects a result that is unscrubbed, the live snapshot relabelled,
or at another wait (`ReservedInterfacesTest`), and the store refuses a handle that claims to be
scrubbed and is not one it made.

**Given** FR-6's differential requirement
**When** `RedeterminationDifferentialTest` runs over the `smoke` set's mid-fight snapshots
**Then** for each scrub row separately and for all rows together, the scrubbed copy's
`Observation.sectionHashes()` equal the live snapshot's, which is the definition of hidden
**And** a scrubbed copy differs from the live snapshot in at least one hidden element per row the
snapshot actually holds, so the test cannot pass by scrubbing nothing
**And** two different samples produce two different scrubbed copies with equal Observations
**And** a deliberately incomplete scrubber, with one row removed in a test fixture, fails the test on
that row and names it.

**Given** ADR-0009's swap-in-place host
**When** the rollout host implementing `Simulator` is built
**Then** it snapshots the live Run, loads the scrubbed copy into the same process, rolls the Actions
out through the executor's own paths, returns Observations and outcomes only (`RolloutResult`), and
restores the live Run
**And** `RestoreReplayAfterRolloutTest` asserts that after a rollout the live Run's Observation
hashes from the restored wait onward equal those of an undisturbed twin, which is the check ADR-0009
deferred to this story
**And** the host rolls out nothing for a handle it did not make, and a test that descends inside a
rollout asserts the new floor comes from the sample's seed and differs from the floor the live seed
would generate
**And** the pull request is labelled `fairness`, carries `touches-upstream` and a row in
`docs/UPSTREAM.md` only if a hook turns out to be needed (none is expected, since the game's own save
and load paths are used), and carries no Rig numbers because no Brain uses the host yet.

### Story 6.3: The search leak test

As the product owner,
I want a test that fails if a search's decision depends on anything the player cannot know,
So that no search design ships on trust (FR-13).

**Acceptance Criteria:**

**Given** FR-13 and ADR-0010's choice rule 1
**When** `SearchLeakTest` is implemented in the harness test source set
**Then** for each mid-fight snapshot of the `smoke` set (a wait with at least one visible enemy) it
builds N alternate worlds, N at least 8, by scrubbing the live snapshot with N different samples that
differ in every row of the ADR-0009 table, and first asserts that every alternate's Observation hash
equals the original's, so the worlds are identical to the player
**And** it runs the search Brain on the original and on each alternate, with the same Run salt and
wait index, and asserts the Decisions are identical: the same chosen Action, the same alternatives in
the same order, the same scores and the same one-line reasons, compared as the canonical bytes of the
Decision record and not as a hash alone
**And** it names each of the true seed, unseen mob positions, unidentified item classes, hidden traps
and secret doors as its own case, which fails with its own message when that element leaks
**And** it covers every search the program has at that point: the one-ply model of story 6.1, and a
probe search that spends its budget on rollouts through the `Simulator`, a test fixture that exists so
the rollout path is exercised before any real candidate exists.

**Given** that a test which cannot fail proves nothing
**When** the negative controls run
**Then** a planted leak in each of four forms is caught: a search handed the live snapshot's handle
with the scrubbed flag forged, a search that reads the true seed through a test back door, a probe
whose scrubber leaves one row out, and a search that reads an unseen mob's position
**And** the rollout host's refusal of an unscrubbed or foreign handle is asserted separately
(`Simulator.simulate`, `RolloutHostRefusalTest`).

**Given** what the test does not claim
**When** the story file and the test's Javadoc are written
**Then** they state that the test proves independence from the hidden state it can vary, which is the
rows of the ADR-0009 table and nothing beyond; that a hidden element outside the table is outside the
proof until the table grows; and that it is a necessary condition for fairness and not a sufficient
one, which is why the fairness reviewer still reads every search diff
**And** the test runs in CI on every pull request, is added to the guarantees table in
`docs/fairness.md` with its test name, and its result on the `smoke` set is published as a Results
page.

### Story 6.4: The simulator-speed measurement

As the engineer,
I want to know how many redetermined rollouts a second the host can run,
So that ADR-0010 can say whether sampled search fits any budget at all.

**Acceptance Criteria:**

**Given** ADR-0010's measurement table and the E1 throughput page (about 399.5 Input waits per second
per process for the random agent)
**When** the measurement is run on the `smoke` set's mid-fight snapshots under the swap-in-place host
of story 6.2
**Then** it reports, separately, the cost of taking a snapshot, of scrubbing it, of loading the
scrubbed copy, of rolling out a stated horizon (2, 3 and 4 hero turns, the FR-34 range) and of
restoring the live Run, each as a median and a 95th percentile, and the redetermined rollouts per
second per process they imply
**And** the report states the machine, the JDK, the commit, the command and the number of snapshots,
the way `docs/results/e1-throughput.md` does, and the command is run three times
**And** if the E1 classloader-isolation spike can be completed in this story, the same table is
reported for isolated engine instances; if it cannot, the page says why in a sentence and cites
ADR-0009's JNI note rather than leaving the row out silently.

**Given** ADR-0010's pre-mortem (snapshot cost may hide a viable design)
**When** the results are read
**Then** the page says which of the five costs dominates and what the rate would be if that cost were
zero
**And** it states, for the per-Decision budgets the Overlay and the Rig use (the Registration's
`budgetMs` field), how many determinizations times rollouts fit, and whether the answer is none,
which is a valid result
**And** the numbers are not compared to a target chosen in advance (NFR-3: no number is promised
before it is measured).

**Given** reproducibility
**When** a stranger runs the command
**Then** `SimulatorSpeedSmokeTest` runs a tiny configuration and pins its structure (the five cost
rows are present, the counts add up) and not its timings
**And** the page lands in `docs/results/` with the report attached and indexed, and the docs build
passes; the pull request carries no Rig comparison because no Brain changes.

### Story 6.5: The properties measurement

As the engineer,
I want the leaf correlation and the disambiguation factor of SPD's tactics, measured properly,
So that the choice between sampled search and the one-ply model has an explanation beside its
verdict.

**Acceptance Criteria:**

**Given** ADR-0010 and its story 1.21 amendment, which fixed two changes this story must make
**When** the properties are recomputed
**Then** the method is the amendment's, random playouts from snapshots, with its two changes: the
disambiguation denominator is the facts reachable on the floor of the sample and not the Run's whole
set of unknown appearances, and the count is taken at a death as well as at the horizon
**And** the playouts are driven by the baseline Brain and by the one-ply model of story 6.1 as well as
by the random agent, so that survival is uncertain and the agreement of siblings means something,
which is why the E1 reading (kappa 0.010, disambiguation 0.000) could not be placed on the scale
**And** the snapshots are the `smoke` set's mid-fight snapshots, drawn the way the amendment
describes and the way story 6.4 uses them.

**Given** that bias is a two-player quantity
**When** the page is written
**Then** bias is not measured and the page says why in one sentence, citing ADR-0010
**And** the hidden facts are counted through the oracle sidecar from the launcher under `--oracle`, the
one place that may construct it and the one switch that marks it; the page shows the flag, and no
oracle code runs in any ranked Rig run or in any search
**And** `PropertiesDefinitionTest` pins the two definitions on hand-built cases with known answers (a
toy snapshot where every sibling agrees, and one where the revealed share is known), so a later reader
can check the arithmetic.

**Given** ADR-0010's rule 4 (the properties are explanatory, never a gate)
**When** the results are published
**Then** the page reports the two numbers with confidence intervals for each driving Brain, says what
each would predict for sampled search in the sense of Long et al., labels the prediction as
explanatory, and links the speed page from story 6.4
**And** the ADR-0010 amendment records what was measured and that no choice was made from it.

### Story 6.6: The candidate comparison under the Sequential test

As the product owner,
I want every search candidate registered against the one-ply model at equal budget and decided by the
Sequential test,
So that a search ships only if the numbers accept it, and the question is closed with published
measurements if none does.

**Acceptance Criteria:**

**Given** ADR-0010's choice rule and ADR-0012
**When** the candidates are chosen
**Then** rule 1 is checked first: if the search leak test of story 6.3 does not pass for a design,
that design is not registered, and the story file says which and why
**And** among the designs that fit the budget (a design fits when its median Decision time at the
registered budget completes the search it was configured for on the described machine, measured with
story 6.4's harness), at most ADR-0010's options 3 (depth-limited sampled search over redetermined
rollouts), 4 (information-set search) and 5 (hybrid: abstract tree, one rollout per leaf) are
implemented, each in its own pull request with the `fairness` label and a fairness review, and the
rest are recorded as not attempted with the reason (budget, properties, or time)
**And** if none fits the budget, the story goes straight to its closing criterion with no candidate
built.

**Given** Rig discipline (non-negotiable 5)
**When** a candidate is registered
**Then** the Registration names the baseline (the one-ply model of story 6.1 at its merged commit),
the `standard` set at its version, the `budgetMs` and `machineClass` fields, and the Per-pair GSPRT
parameters, with the equal wall-clock budget per Decision enforced by the runner rather than by
convention, and the burn-in and maximum pairs as ADR-0012 and ADR-0018 give them
**And** the `bosses` set is run as well, since it targets fights, and reported beside `standard` and
never instead of it
**And** the `holdout` set is touched exactly once, at the end, on the accepted candidate or on the
one-ply model if none is accepted, and the page records that it was
**And** a candidate ships only on acceptance; undecided and rejected outcomes are published with the
same weight as an acceptance, with the properties from story 6.5 reported beside each result as
explanation and not as a gate.

**Given** the outcomes
**When** the epic closes
**Then** either the accepted candidate becomes the tactical layer, with its Results page, its
Registration, the fairness review, ADR-0010 amended with the decision, and `SearchLeakTest` green on
it, or the one-ply model remains the tactical layer, the measurements stay published, and the
amendment says what would reopen the question (a faster simulator, a new upstream tag, a new
candidate)
**And** `docs/roadmap.md`, `sprint-status.yaml` and the epic issue are updated in the same pull
request, the E6 retrospective runs, and its lessons land in `CLAUDE.md`.

## Epic 7: Strategy and lore

*Opened 2026-10-26, after E6 closed (6.6: no rollout design fits the budget, the one-ply model stays).
E4's last story (4.14, the Goo gate) is still open and E5.16's live session is the product owner's;
the program allows stories for the current and the next epic (bootstrap prompt, section 9), and E7
is the next one.*

Whole-run planning and a knowledge base that admits community claims only with provenance and a
verification tier. The Brain already plays the sewers by Policies (E4) with a one-ply fight model
(E6). What it lacks is a place for whole-run decisions to live as data the Rig can test, and a
record of why each of its heuristics is believed. This epic adds both: Playbooks (FR-35), then the
lore pipeline (FR-18), then the check that ties every heuristic to a Rule or a Lore entry with a Tier.

Covers FR-18 and FR-35, and the Lore half of FR-17's "every claim the Brain relies on".

**Where the Brain stands when the epic opens.** On the Profile 6 baseline at `7b1212eea` the
Warrior reaches depth 5 in 49% of 120 Runs, kills Goo in 37%, and has never won a Run
(`docs/results/2026-10-26-goo-baseline-7b1212eea.md`). Deaths by depth 1 to 8 were 5 / 12 / 22 / 22 /
18 / 23 / 14 / 4, and hunger kills about one Run in six on depths 2 to 4
(`docs/ideas/2026-10-26-rest-when-hurt-and-fed.md`). So the done-when, "a measured win rate", will
measure a rate near zero unless the strategy stories move reach and depth a long way. The closing
story publishes whatever the number is, with its interval, beside the depth distribution, and says
plainly that a win rate of zero is a result. the product
owner settled that no win rate is set to beat (see "Settled with the product owner" at the end of this epic).

**What exists, and what the stories build on.**

- `weights/shatterfish.json` and `weights/oneply.json` are the only strategy data today (FR-33,
  story 4.5). Every other threshold is a Java constant, for example `TestItem.HEALING_ODDS`,
  `TestItem.INVENTORY_ODDS`, the descend allowance of 500 waits, the rest rules in `Explore` and
  `Descend`. Those constants are the first Playbook entries.
- `docs/brain-rules.md` is the Brain's Rules index (story 4.4): one row per claim, a slug, the
  Policies that use it, and a link to a Rule page that carries the Tier. `BrainRulesIndexTest` holds
  it in both directions. It covers claims about mechanics. It does not yet cover thresholds, which
  are choices and not claims, and it has no Lore side.
- `lore/bestiary/*.md` holds 144 graded community claims in eight grouped files (bosses 31, caves 12,
  city 15, halls 8, prison 5, quest-branch 39, sewers 10, specials 24). Each has a source, variant,
  date, retrieved date, tier, a verdict against `v4.0.0` and citations. That is nearly the FR-18 shape,
  but grouped instead of one file per claim, with no checker, no `implemented_in`, and graded at
  `v4.0.0` where the pin is `v4.0.2`. Story 7.7 converts it; it does not start from nothing.
- `codex/v4.0.2/vocabulary.json` (FR-16) pairs 449 names, items and mobs only (no mechanics kind),
  from SPD and the vanilla Pixel Dungeon source, with `shared`, `differences` and citations on both
  sides, and says "nothing reads it yet". 114 are shared by name and 335 are not.
- `codex/v4.0.2/changelog.json` has 195 changelog entries across 14 changelist classes and **no
  calendar date on any of them** (`dates` is empty on all 195; the bootstrap prompt already records
  that the v3.3.8 entry carries no date and that no 4.0 entry exists at the tag). Story 7.9 is written
  around that: gating by version series first, and calendar dates only from a committed, cited table.

Information parity (non-negotiable 1) binds every story. A Playbook is data the Brain reads at
construction, like the Evaluation weights, and may use only what an Observation, a Belief or the
Codex gives it. Lore never reaches the Brain as text: a claim becomes behaviour only through a
heuristic that cites it (non-negotiable 8), and a claim about a seed or a layout is rejected at
intake. Every story that changes `brain` carries the `fairness` label, and the `fairness-reviewer`
subagent runs before its PR opens. Every claim about a mechanic is cited to `v4.0.2` as `path:line`.

**How the stories are measured.** The lessons of E4 to E6 are acceptance rules here, not advice:

- Tuning uses the `standard` set only, Warrior first (`--set standard --class WARRIOR --first 40
  --salt-base 1000`), and a kept change is confirmed on the second slice (`--first 80 --salt-base
  3000`) before it merges. `goo` and `holdout` are never used for tuning, and `:rig:direction`
  refuses them.
- One table is one measurement. The noise on 40 Runs is about 3 Goo kills and on 80 about 4, so a
  difference inside that is "no change". A lever that does not separate from noise on both slices is
  reverted and recorded as an idea (`docs/ideas/`), which is a finished story and not a failed one
  (the rest-when-hurt and crab-stairs levers of #219 are the pattern).
- Every table reports reach depth 5, Goo kills, kills over reach, median turns and deaths by depth,
  so a gain in one factor is not read as a gain in the other. A rate target is split into reach times
  conversion and each factor is stated.
- `:rig:direction` plays the baseline on the current host code. A data-only refactor shows no
  difference in a table, so it is proved by equal Run logs (story 7.2), not by a table.
- A Playbook change that ships is also registered and decided by the Sequential test on `standard`
  against `main` at the story's start (ADR-0012, ADR-0018), with its Results page. Stories 7.3, 7.4,
  7.5 and 7.6 say which. Each story re-takes its baseline at the start, since the Goo gate work (4.14,
  issue #219) is still moving `main`.
- The Goo gate itself belongs to 4.14. Stories here may raise reach, which 4.14 needs, but none runs
  the `goo` set and none claims the gate.

Story order follows the lever, not the FR order. The first story is small, changes no play and
leaves a count that goes down. The data format comes next, because the strategy stories are Playbook
changes (FR-35: strategy lives in data files the Rig tests). Then the strategy that can raise reach:
item identification and resource use (7.3, 7.4), the bosses (7.5) and the classes (7.6). The lore
pipeline (7.7 to 7.9) follows, so that the link check of 7.1 can accept Lore entries, and the closing
story (7.10) measures the win rate and checks that every heuristic is linked.

### Story 7.1: The heuristic-to-Rule link check

As the product owner,
I want a test that fails when a Brain heuristic has no Rule behind it,
So that "every heuristic is cited" is a count that goes to zero and stays there (FR-18, FR-17).

**Acceptance Criteria:**

**Given** `docs/brain-rules.md`, `BrainRulesIndexTest` and the constants the Policies decide by
**When** the check is extended
**Then** a heuristic, meaning a named threshold, odds, budget or ordering choice a Policy decides by
(for example `TestItem.HEALING_ODDS`, `TestItem.INVENTORY_ODDS`, `TestItem.REACH`, the descend
allowance, the rest thresholds), is declared in code with a small `@Heuristic` annotation in `brain`
that carries a slug and nothing else, so the declaration is machine-readable and adds no behaviour
**And** the slug must be a row of `docs/brain-rules.md` (a Rule) or, once story 7.7 lands, a Lore
claim id; a slug that names neither fails `HeuristicLinkTest`, naming the constant
**And** the test finds the annotated constants by reflection over the `brain` classes and not by
grepping source, so a renamed constant cannot slip past
**And** a table on the index page, written by a helper the test also uses and checked for drift the
way the Codex is, lists each heuristic with its slug, its linked Rule, that Rule's Tier, and the
Policies that use it.

**Given** that the Brain has thresholds today that no Rule backs
**When** the first run of the check finds them
**Then** the story does not invent a Rule for each. A threshold that was tuned on the Rig links to the
Results page or idea record that measured it, at Tier 2 only if a test in the repository reproduces the
figure and Tier 3 (a hypothesis for the Rig) otherwise; a threshold nobody can account for goes on a
visible `unlinked` allowlist in the test, with its count at the head of the index page, and the test
fails if that count rises
**And** the PR states the count before and after (heuristics found, linked to a Rule, linked at
Tier 3, unlinked), which is the story's measurement and the baseline story 7.10 closes against.

**Given** the Brain's determinism
**When** the annotation is added
**Then** `BrainDeterminismTest` and the existing differential tests stay green, and the `smoke` set
played on `main` and on the branch produces byte-identical Run logs (the PR gives both hashes),
because this story changes no play
**And** the PR carries the `fairness` label because it touches `brain`, with the fairness-reviewer
verdict in the PR; no Rig table is required and the PR says why.

### Story 7.2: Playbooks as data

As the Rig,
I want strategy thresholds and choices read from a versioned data file,
So that a Playbook change is a pull request with Results and a bad Playbook fails validation (FR-35).

**Acceptance Criteria:**

**Given** FR-35, the architecture spine's line that committed data files are Seed sets,
Registrations, Playbooks and Evaluation weights, and the precedent of `weights/shatterfish.json`
**When** the Playbook format is defined
**Then** an `api`-typed `Playbook` value exists, versioned like `Weights`, read from
`playbooks/<brain>.json` by the Rig and handed to the Brain at construction, with a `format` and a
`version` field and a section per concern (`identify`, `upgrade`, `bosses`, `classes`, each optional),
holding typed numbers and enums only, never free text the Brain would interpret
**And** an ADR (written with `/adr`, five options scored against the non-negotiables, one of them
"keep the constants in Java") records the format, why the Playbook sits beside the weights and not
inside them, and how a Registration pins a Playbook (the Brain's name and the file's hash)
**And** the Run log header records the Playbook's name and hash, as it does for the weights, so a
Replay uses the same one.

**Given** FR-35's validation rule
**When** `PlaybookValidator` is implemented
**Then** it rejects, with a message that names the field: a seed or seed string; a dungeon coordinate,
cell index or floor layout; a hero class not in `hero-classes.json`; an unknown key; a number outside
the range its field declares; and an enum value the Codex does not know
**And** `PlaybookValidatorTest` has one rejecting case per rule and an accepting case, so the
validator cannot pass by rejecting nothing
**And** the validator runs in `./gradlew build` over every committed Playbook and in the Rig before a
Registration is accepted.

**Given** that moving constants into data must change no play
**When** the first Playbook, `playbooks/shatterfish.json`, is written
**Then** it holds exactly the values the Java constants hold today for the `identify` and `upgrade`
sections (the tested-item odds, the inventory-scroll cap, the walk reach, the escape waits), and the
Policies read them through the Playbook
**And** `PlaybookEquivalenceTest` plays the `smoke` set on the constants (the commit before the story)
and on the Playbook and asserts byte-identical Run logs, with a control that edits one value and
asserts the logs differ, so the test cannot hold for any Brain
**And** the heuristics that moved keep their `@Heuristic` slug from story 7.1, now declared on the
Playbook field, and `HeuristicLinkTest` is green
**And** the PR is labelled `fairness`, carries the reviewer's verdict, and says that no Rig table is
shown because the Run logs are equal, which is the stronger statement.

### Story 7.3: Item-identification strategy

As the bot,
I want a Playbook that decides which unknown potions and scrolls to try, when, and where,
So that I find healing, strength and the useful scrolls early without dying to a bad drink, and reach
depth 5 more often.

**Acceptance Criteria:**

**Given** the lever and its evidence
**When** the story starts
**Then** the story file records a micro-brainstorm of at least three alternatives (for example: test
sooner and at full health; stop testing an appearance the deck weights already narrow to one
candidate; throw rather than drink a potion the Beliefs say is mostly harmful; hold unknowns for an
emergency drink when the healing odds are highest), the pre-mortem, and the baseline table for `main`
at the story's start from the `standard` set (reach depth 5, kills over reach, median turns, deaths by
depth, and how many deaths follow a test, counted from the Run logs' test-item Decisions)
**And** the claims about identification it relies on are Rules with `path:line` citations at
`v4.0.2` (`docs/rules/identification.md`, the deck weights in `codex/v4.0.2/decks.json`), each with a
row in `docs/brain-rules.md`, and `upstream-reader` is asked for any mechanic not yet cited.

**Given** the Playbook of story 7.2
**When** the `identify` section is tuned
**Then** the change is a Playbook change plus only the Policy code needed to read a new field, and
the Observer is not touched; the Brain uses the Beliefs' candidate identities and never a true
identity, and `PermutedIdentitiesDifferentialTest` stays green
**And** `IdentifyPlaybookTest` asserts on constructed screens that each new field changes the Decision
it is meant to, and that a Playbook equal to the old values reproduces the old Decision
**And** the strategy log (FR-36) names the Playbook field that fired, so a Run's identification
choices can be read with `:rig:strategy` and no debugger.

**Given** Rig discipline
**When** the step is measured
**Then** the PR carries the tuning-slice direction table and the second-slice table, both against
`main`, with reach and conversion split, and the repeated-cell and wasted-wait check of a real-Run
strategy log that E4 requires
**And** a step that wins on both slices is registered and run under the Sequential test on `standard`
against `main` (ADR-0012), and ships only on acceptance; an undecided or rejected result is published
with the same weight and the Playbook values are reverted
**And** deaths within 100 waits of a test are counted before and after, because the lever can pay in
reach and cost in early deaths and the table must show both
**And** the Results page says what the table cannot show, states that `goo` and `holdout` were not
run, and the PR is labelled `fairness` with the reviewer's verdict.

### Story 7.4: Upgrade allocation

As the bot,
I want a Playbook that decides which item receives each scroll of upgrade,
So that the upgrades the bot finds make it harder to kill instead of sitting in the pack or going to
the item that matters least.

**Acceptance Criteria:**

**Given** that the Brain already reads a known scroll of upgrade onto worn armour (`TestItem`'s
`UseItemOn`, `Answers.upgrade`)
**When** the story starts
**Then** the story file states the allocation as it stands, from the code, and counts from the
`standard` Run logs how many upgrade scrolls a Run finds by depth 5, how many are read and onto what,
so the lever's size is known before any change
**And** the mechanics it relies on (what an upgrade adds to armour, a weapon and a ring at `v4.0.2`,
the cursed-item and strength-penalty interactions, the cost of reading onto an unidentified item) are
Rules with `path:line` citations and rows in the Brain's Rules index.

**Given** the Playbook's `upgrade` section
**When** allocation options are implemented
**Then** the section chooses among the targets the Observation shows (worn armour, worn weapon, the
heaviest armour the hero can wear after the strength it would take, hold for a better item) by
fields, and `UpgradePlaybookTest` asserts each choice on a constructed pack, including that an item the
Beliefs cannot rule out as cursed is never the target of a read unless a field allows it and the worst
case passes `SafeTest`
**And** the story uses the upgrade window through the existing `Answers` path and adds no Action kind;
if a target cannot be reached by the Actions that exist, the story stops and files an issue, since a
new Action is an `ActionExecutor` change and a fairness-reviewed story of its own.

**Given** Rig discipline
**When** the step is measured
**Then** the tables, the second slice, the Sequential-test Registration and the revert-if-not-kept
rule of story 7.3 apply, with one added column, "upgrades read by depth 5"
**And** if the finding is that the upgrades found by depth 5 are too few for any allocation to
separate from noise, the Playbook keeps its default, the count is published, and the story closes as
measured and not kept
**And** the PR is labelled `fairness` with the reviewer's verdict.

### Story 7.5: Per-boss playbooks

As the bot,
I want a Playbook entry per boss that says how to approach the fight, from what the screen shows,
So that Goo and the bosses after it are fought on purpose.

**Acceptance Criteria:**

**Given** `lore/bestiary/bosses.md` (31 graded claims on Goo, Tengu, DM-300 and their parts),
`docs/bestiary/bosses.md`, the `bosses` set and the current `Goo` Policy
**When** the story starts
**Then** the story file lists each boss claim that is Tier 1 or 2 and not yet used by a Policy, and
each Policy decision that rests on a boss claim with the Rule or claim id it cites, so the work is the
gap between the two lists and not a fresh reading of the wiki
**And** the Playbook's `bosses` section holds, per boss, typed fields for the approach (stand off a
pumping Goo by the cited distance, fight from water or off water, rest to what fraction of health
before the stairs down to the boss floor, use a known consumable before the fight) and no layout or
coordinate (the validator of 7.2 enforces it).

**Given** that 4.14 owns the Goo gate
**When** Goo's entry is tuned
**Then** the tuning set is `standard`, not `goo`: the table counts Goo fights and Goo kills inside the
Warrior Runs of `standard` (the Goo-fight definition of the Profile 6 baseline page), and the `bosses`
set is run beside it, reported but not used for a tuning decision that `standard` disagrees with
**And** the story does not claim the gate; its Results page says that the gate Run on `goo` is story
4.14's and that this story's numbers are an input to it
**And** the 12 Goo deaths and 3 Ooze deaths among the 59 baseline fights are the cases the entry is
written for, and the PR reports kills over fights before and after with the Wilson interval.

**Given** the later bosses
**When** Tengu's and DM-300's entries are written
**Then** they are written only as far as the `bosses` set reaches them: the Results page reports how
many of its 100 Runs meet each, and an entry for a boss that fewer than 10 Runs reach is a Playbook
entry with its lore citations and no tuning, marked untested in the file, so no effort goes into a boss
the bot cannot yet reach
**And** every field that changes play is linked from `docs/brain-rules.md` or a Lore claim, and
`HeuristicLinkTest` stays green
**And** the PR is labelled `fairness`, carries the reviewer's verdict, and carries a Registration under
the Sequential test against `main` on `standard` for the Goo entry.

### Story 7.6: Per-class playbooks

As the bot,
I want a Playbook section per hero class,
So that the Mage, Rogue, Huntress and the other classes play to their strengths and the Warrior's
rules are not applied blind to them.

**Acceptance Criteria:**

**Given** `codex/v4.0.2/hero-classes.json`, the `standard` set's class mix, and the Warrior-only
history of every tuning table so far
**When** the story starts
**Then** the story file counts the Brain's Runs by class on the `smoke` set and a 40-Run `standard`
slice (depth reached, deaths by depth, the Policies that fire) and names the classes for which the
Warrior Playbook is plainly wrong (a ranged class held to a melee fight rule, a class whose starting
kit holds items the Brain never uses), from the Run logs and not from the wiki
**And** the claims about class kits are Rules cited to `v4.0.2` with rows in the Brain's Rules index.

**Given** the Playbook's `classes` section
**When** class entries are implemented
**Then** each entry overrides named fields of the base Playbook for one class and nothing else, a
class without an entry plays the base, and `ClassPlaybookTest` asserts the override rule, that an
unknown class is rejected by the validator, and that the Warrior's entry is empty so the Warrior's
Runs are unchanged (equal Run logs on the Warrior slice of `smoke`)
**And** a class's subclass choice, talents and starting-kit use go through the Actions that exist
(`Talent`, `Ability`, `AbilityAt`, `Answers`); a class feature that needs a new Action is filed as its
own issue and left out
**And** the Rig runs `standard` per class (`--class <C> --first 40 --salt-base 1000`, then the second
slice) and the table reports the class's own depth distribution next to reach depth 5, since depth 5
is calibrated on the Warrior.

**Given** Rig discipline
**When** an entry is kept
**Then** it is registered and decided by the Sequential test on the class's slice of `standard`, in
ADR-0012's Per-pair unit
**And** entries that do not separate from noise are reverted and recorded as ideas
**And** the PR is labelled `fairness` with the reviewer's verdict.

### Story 7.7: The lore claim format and intake

As the product owner,
I want community knowledge to enter as one validated file per claim with its provenance,
So that nothing from a forum reaches the Brain without a source, a variant and a Tier (FR-18).

**Acceptance Criteria:**

**Given** the intake format in the bootstrap prompt and the PRD addendum (`source_url`,
`source_type`, `date`, `variant`, `version_claimed`, `claim`, `tier`, `implemented_in`) and the 144
claims already in `lore/bestiary/` in eight grouped files
**When** the format is fixed
**Then** an ADR (with `/adr`, five options including "leave the grouped files") records one file per
claim under `lore/<topic>/<id>.md`; the frontmatter fields and their allowed values (`variant` in
`spd`, `pd`, `mod:<name>`, `unknown`; `tier` in `1`, `2`, `3`, `F`; `implemented_in` a list of Policy
names, Playbook fields or Rule slugs, possibly empty); the body rule (a paraphrase and at most a short
quote); the tag the verdict was graded against; and that the checker lives in the Codex tooling (no new module, no
layout change), with no game code and no network call (NFR-8)
**And** a checker, `LoreCheck`, runs in `./gradlew build` and validates every file: required fields
present, enums valid, `source_url` a URL, `retrieved` a date, a Tier 1 or 2 claim carrying at least one
`path:line` citation that resolves at the pinned tag (the citation checker of FR-17 is reused, not
rewritten), and every `implemented_in` name existing.

**Given** FR-18's rejection rule
**When** intake runs on a candidate claim
**Then** a claim that references a seed, a seed string, a specific dungeon layout, a map coordinate or
a level feeling tied to one Run is rejected with a message saying which it found, and `LoreIntakeTest`
has one rejecting case per kind and an accepting case
**And** the intake command is the entry for the product owner's research skill: it reads a draft claim
file from a folder, validates it, assigns the next id, and either writes it under `lore/` or writes a
rejection note beside it, and never reaches the network
**And** intake never promotes a claim: a new claim enters at Tier 3 unless the file supplies the
citation that makes it Tier 1, which the checker verifies.

**Given** the existing claims
**When** they are converted
**Then** the 144 claims are split into one file each by a script that is committed and run once; the
count per topic before and after is equal (31, 12, 15, 8, 5, 39, 10, 24); every claim's tier and
verdict carry over verbatim; the grouped files are removed in the same PR; the `docs/bestiary/` links
are updated; and `LoreMigrationTest` asserts the counts and that no field was dropped
**And** the old `v4.0.0` verdicts are kept as written and marked `needs-review` against `v4.0.2` where
a cited line moved, using the citation checker, so no verdict silently claims a tag it was not graded
against
**And** the `@Heuristic` check of story 7.1 now accepts a Lore claim id, and a claim's
`implemented_in` and the heuristic's link agree in both directions (`LoreLinkTest`)
**And** the docs site lists the claims by topic and Tier, built from the files and checked for drift,
and `mkdocs build --strict` passes.

### Story 7.8: The variant classifier from the vocabulary diff

As the lore pipeline,
I want a classifier that says whether a claim describes Shattered Pixel Dungeon, vanilla Pixel
Dungeon, a mod, or cannot be told,
So that a forum claim about vanilla is not applied to this game by mistake (FR-18, FR-16).

**Acceptance Criteria:**

**Given** `codex/v4.0.2/vocabulary.json` (449 entries, 114 shared by name and 335 not, items and mobs
only) and the intake format of 7.7
**When** `VariantClassifier` is implemented
**Then** it takes a claim's text, its named subjects and its source type, and returns a variant from
`spd`, `pd`, `mod:<name>` or `unknown` with the evidence that decided it (the names matched, which
side of the diff they sit on, whether they are shared), never a bare label
**And** a name found only on the SPD side points to `spd`; a name found only on the vanilla side
points to `pd`; a shared name whose `differences` include a comparable figure that the claim's figure
matches points to the side it matches; and a shared name with no differing figure points to
`unknown`. The classifier returns `unknown` rather than guess, and the Results page for the story
counts how many of the 144 existing claims it classifies and how many it leaves `unknown`
**And** the vocabulary has no mechanics kind, though FR-16 says "items, mobs, and mechanics"; the story
either extends the Codex generator to emit mechanics names from the cited properties, as a Codex change
under E2's drift rule, or states in an ADR amendment that mechanics claims classify by source type and
date only, and says which it chose and why.

**Given** that a classifier which agrees with itself proves nothing
**When** it is tested
**Then** `VariantClassifierTest` runs a labelled fixture of at least 40 claims whose variant is fixed
by the source (a post by the game's developer about this game is `spd` by construction, a wiki page on
a vanilla-only mob is `pd`), reports precision and recall per variant, and fails below thresholds the
story file states before the first run
**And** the 144 existing claims are classified and compared with the variant each already carries; a
disagreement is listed in the PR and not auto-applied, since a variant a person set stays unless the
file says otherwise
**And** the classifier reads Codex files only, makes no network call, and carries no game code.

### Story 7.9: Date gating from the changelog

As the lore pipeline,
I want a claim's date and the version it names checked against when the thing it describes changed,
So that a claim true for an old release is not carried into this one as if it were current (FR-18).

**Acceptance Criteria:**

**Given** that `codex/v4.0.2/changelog.json` carries 195 entries in 14 changelist classes and no
calendar date on any of them
**When** the gating is designed
**Then** the story's first deliverable is a finding, with counts: how many entries carry a date, how
many sit under a version the class name gives (`v0_1_X` to `v4_X`, `Pixel_Dungeon_Changes`), and how
many name an item, mob or mechanic a claim can be tied to through the vocabulary
**And** gating works by version series first: a claim whose subject a changelist entry of a version
later than the claim's `version_claimed` changed is flagged `changed-since`, with the entry's class and
citation, and does not become Tier 1 on its date alone
**And** calendar dates come from a committed table, `lore/release-dates.json`, one row per release
with a cited source (release notes or the developer's blog page, kept in the repository or cited by
URL with a retrieved date), and a row with no source is not accepted; the table is data whose
provenance is the point, not a date remembered from memory.

**Given** the gate
**When** `DateGateTest` runs
**Then** it asserts that a claim dated before a change to its subject is flagged, that one dated after
is not, that a claim with no date and no version is flagged `undated` and cannot rise above Tier 3
without a code citation, and that a claim whose subject has no changelog entry passes unflagged and is
counted, so the gate cannot pass by flagging nothing or everything
**And** the 144 migrated claims are run through it and the PR reports how many are `changed-since`,
`undated` and clean, and lists the five most consequential flagged claims for the product owner to read
**And** the gate only flags and never changes a Tier; a Tier changes by a person or a citation.

### Story 7.10: The measured win rate and the epic close

As the product owner,
I want the Brain's win rate measured and published, and a check that every heuristic is linked,
So that the epic closes on a number and a count and not on a claim.

**Acceptance Criteria:**

**Given** the epic's done-when
**When** the win rate is measured
**Then** the final `shatterfish` Brain with its committed Playbook is registered and run on the
`standard` set (500 triples, all classes) in one pass with the turn cap the Rig already uses, and the
Results page reports wins out of Runs with the Wilson interval, the Score distribution of any wins,
deaths by depth, reach depth 5, Goo kills over reach, and the same table for the Warrior slice, each
beside the Profile 6 baseline at `7b1212eea`, so the movement of the epic is visible
**And** a win rate of zero is reported as zero with its upper bound (under 1% at 500 Runs) and the page
says what the number can and cannot show; the epic does not close by changing the measure
**And** the `holdout` set is used exactly once, as the final confirmation run after all tuning is
finished and never for tuning, as the product owner allowed, and the use is recorded in the Results
(FR-20, issue #116)
**And** no win rate is set to beat; the Results page reports the rate with its Wilson interval, Warrior
first and then each class that was measured.

**Given** the done-when's second clause
**When** the link check closes
**Then** `HeuristicLinkTest` has an empty `unlinked` allowlist, or the PR lists each remaining entry
with its reason and the issue that owns it, and the count at the head of `docs/brain-rules.md` says so
**And** the Results page reports the final count against story 7.1's baseline: heuristics found,
linked to a Rule, linked to a Lore claim, linked at each Tier, unlinked
**And** a Tier 3 link on a heuristic that shipped is allowed only when a Results page is its evidence,
and the page lists them.

**Given** the epic's close
**When** it ends
**Then** the E7 retrospective runs and its lessons land in `CLAUDE.md`; `docs/roadmap.md`,
`sprint-status.yaml` and the epic issue are updated in the same PR; `docs/architecture.md` and
`docs/fairness.md` describe the Playbook and the Lore intake as they stand; and `/upstream-sync`'s
re-verification list gains the lore citation check.

### Settled with the product owner

Asked when the epic opened, as the E6 retrospective asked, and answered on pull request #289.

1. **No win rate is set to beat.** Story 7.10 publishes the measured rate with its interval, Warrior
   first and per class where a class was measured.
2. **`holdout` may be used once**, by story 7.10, as the final confirmation run and never for tuning.
3. **E7 does not wait for the Goo gate.** Each story re-takes its baseline at its start.
4. **The lore checker lives in the Codex tooling.** There is no new module and no layout change.

## Epic 8: Overlay v2

The instrument becomes a teacher. The v1 Overlay is the debugger: it shows what the bot decided and
lets a person take the controls. v2 adds the surfaces that explain a decision in full, stop the bot
where a person wants it stopped, replay a finished Run with everything the bot believed, and let a
person play with the bot advising or hand it the quiet stretches.

Covers FR-44 to FR-47 and UX-DR15, and realizes UJ-4. The v1 architecture was built not to preclude
any of this, which the E5 stories asserted by keeping the Decision log a view over the Run log and the
highlights part of the Decision record. This epic spends that.

**Done when**, for the product owner: every v2 surface works in a scripted headless session
(`ScriptedHuman` on `EmbeddedHost`) with a test per acceptance line; the product owner's live session
accepts what is visible (the Explain expansion, the Pause-on section of the settings screen, the
scrubber, the Beliefs view, a coached move and an autoexplored corridor), and no agent has opened a
window; every upstream edit has a row in `docs/UPSTREAM.md`; and no Run the Rig plays differs, which
story 8.11 shows with equal Action lists on the `smoke` set before and after the epic.

**What this epic builds on, from the code as it stands.**

- `RunLog.Decision` carries the goal, the chosen `Choice`, up to three alternatives (each an Action,
  a score in ten-thousandths and a one-line `why`), the Safety flags and the Policy name. It carries
  no Evaluation terms and no Search outcome, so the Explain expansion of FR-44 needs a record change
  before it needs a view (8.1). Search has not shipped (E6 closed on the measurements, ADR-0010), so
  its part of the expansion is a field that is absent in every Run today and is exercised by
  constructed records until a Search ships.
- A `Wait` record holds the Observation hash, the section hashes, the Action, the actor, the Decision,
  the Belief hash and the map highlights. It does not hold the Observation or the Beliefs. The full
  Beliefs view of FR-46 over a log is therefore recomputed by playing the log through the Brain, and
  the Belief hash in each wait is the check that the recomputation is the Brain's own (8.5, 8.6, 8.7).
- `Replay` (harness, story 3.4) already plays a log back against the game and compares hashes. Stepping
  forward through a replay in the embedded game is that, paced by the Panel. Stepping backward is a
  seek: restart from the header and play to the wait. Keyframes are a measurement-gated option (8.5),
  not a promise.
- `Mode`, `Shadow` and `Note` records exist, and 5.9 already records the shadow Decision for a human's
  wait. Coach mode and autoexplore are written with those and the existing speed field; a new record
  kind or a new chained field would change the chained text of every log ever written and needs an ADR
  first (the lesson of ADR-0011).
- Hook rows 9 to 11 are the Overlay's input seams. The Pause-on section is a new row (8.4). Nothing
  else in this epic edits an upstream file, and no story adds a sprite, emitter, sound or music call
  (row 8 is the question before any draw).

**Rules that bind every story.** Native UI only (non-negotiable 6): Chrome nine-patch frames,
`PixelScene.renderTextBlock`, `RedButton`, `Icons`, sizes consistent with `StatusPane` and `Toolbar`.
Every Panel write is on the render thread and nothing the render thread draws touches the Run's
generator. Every transition happens at an Input wait and every control is disabled whenever its
transition is impossible. Verify headless first; the live session is the product owner's acceptance of
anything visible, and a story that is visible ends with a checklist for it in the story file and the
PR. A story that changes what the Brain computes or what the Overlay is shown of it is labelled
`fairness` and gets the `fairness-reviewer` run before the PR opens. Oracle mode stays off and flagged
in every new view: nothing new shows a true identity unless the Oracle flag is on, and then only with
the `oracle` prefix (FR-43). A test seed comes from the `standard` set and the page names it.

**How the stories are ordered.** Data first (8.1), then the view that reads it (8.2), then the
Pause-on conditions as logic and as the settings section (8.3, 8.4), because autoexplore needs them.
Then the replay path: the session (8.5), the Beliefs view (8.6) and the scrubber that uses both (8.7).
Coach mode and autoexplore come last (8.8 to 8.10), since they are the two that change how a person
plays, and 8.11 closes the epic.

### Story 8.1: Evaluation terms and Search outcomes in the Decision record

As the Overlay,
I want the Decision record to carry the Evaluation terms behind each score and, when a Search ran,
each alternative's expected outcome,
So that the Explain expansion has something truthful to show (FR-44) and a replayed log shows it too.

**Acceptance Criteria:**

**Given** `RunLog.Choice` (an Action, a score and a `why`) and `Evaluation`'s feature list
**When** the record is extended
**Then** a `Choice` may carry `terms`, a list of (feature name, weighted contribution in
ten-thousandths) in the order `Evaluation` lists its features, and a `Decision` may carry `search`,
a list of (Action, expected outcome in ten-thousandths, sample count) that is empty when no Search ran
**And** a field that is empty is not written, so the canonical JSON and the chain of every existing
log are byte-for-byte what they were, and `RunLogTest` reads the committed logs in `docs/results/` and
the Rig fixtures and verifies their chains unchanged
**And** a `Choice` whose terms do not sum to its score is refused at construction, since a term list
that disagrees with the score it explains is a lie the Panel would then print.

**Given** the lesson that a field added to a chained record changes every wait ever written
**When** the story is designed
**Then** its first deliverable is a short note in the story file stating which of the two ways the
fields enter the format (absent-when-empty inside the existing `Decision`, or a version bump of the
header's `v`), with the alternatives weighed, and the choice is the absent-when-empty form unless the
reading shows it cannot hold
**And** a new record kind is not used, and if one is judged necessary the story stops and writes an
ADR instead.

**Given** the Brain's Policies that score offered Actions through `Evaluation`
**When** a Decision is built
**Then** the Brain fills `terms` for the chosen Choice and each alternative from the same `Evaluation`
call that produced the score, never a second computation
**And** the Brain's Actions over the `smoke` set are equal before and after, shown by comparing the
Action list of each Run, so the change is proved to be recording only (the `:rig:direction` table
would show identical columns for any record change and is not the evidence, per the E5 lesson)
**And** a Policy that scores nothing records no terms, not zeros.

**Given** `docs/fairness.md` and the Observation-only rule
**When** the diff is reviewed
**Then** the terms are features read from the Observation and weights from data, so they carry no
hidden state, and the Observer's leak tests and the brain's import ban stay green
**And** the PR is labelled `fairness` and carries the `fairness-reviewer` verdict.

### Story 8.2: The Explain expansion

As the human,
I want Explain to open the Decision card into its Policy, every alternative's reasons, the flags that
applied, the Evaluation terms and, when a Search ran, the alternatives' expected outcomes,
So that I can see why the bot chose what it chose and what it nearly chose.

**Acceptance Criteria:**

**Given** FR-44, UX-DR15, the Explain control and its F10 hotkey from 5.6 and 5.11, and the data of 8.1
**When** Explain is pressed in PAUSED
**Then** the card expands inside the Panel to show, in this order, the Policy that fired, the chosen
Action's term table (feature, contribution), each alternative with its score, its `why` in full and its
term table, the Safety flags that applied, and the Search block only when the Decision has one
**And** the text is composed by a pure `ExplainContent` function over the Decision, testable without a
game, and is laid out with `PixelScene.renderTextBlock` inside a scrollable region of the Panel, with
no new window and no new asset
**And** a Decision with no terms (the random agent, a Policy that scores nothing) shows the Policy and
reasons it has and says nothing about terms, never an empty table.

**Given** the Panel's states
**When** the expansion is open and the Run advances
**Then** it follows the new Decision at the next Input wait and stays expanded, and it collapses on
Explain again, on the Panel's collapse and on Take over
**And** in the collapsed Mode strip state Explain is disabled with the reason shown, as the
enablement matrix of 5.6 requires
**And** `ExplainContentTest` asserts the order and the text for a constructed Decision with terms, one
without, and one with a Search block, and `ControlEnablementTest` gains the expansion's rows.

**Given** the E5 lessons
**When** the story is verified
**Then** a `ScriptedHuman` test on `EmbeddedHost` presses Explain at a real Decision from the
`standard` set, reads the Panel's content model and asserts the terms sum to the displayed score
**And** the story file carries a checklist for the product owner's live session, which is the
acceptance of how the expansion looks at 1280 by 720 and at a wider window
**And** the PR is labelled `fairness` because it changes what the Overlay shows of the Brain, and
carries the reviewer's verdict.

### Story 8.3: Pause-on conditions

As the human,
I want the Overlay to switch to PAUSED when a condition I set holds,
So that I can let the bot run and still stop it at the moments I care about (FR-45).

**Acceptance Criteria:**

**Given** FR-45's six conditions: before any item use, before testing an unknown item, before stairs,
on boss floors, when HP drops below a threshold, when a new enemy appears
**When** the evaluator is written
**Then** a pure `PauseOn` function takes the set of enabled conditions with the HP threshold, the
current Observation and the Decision about to be executed, and returns the first condition that holds
or none, using only the Observation and the Decision (an unknown item is one the screen shows as
unidentified; a boss floor is read from the Floor as the Codex names it; a new enemy is a visible actor
not seen at the previous wait)
**And** `PauseOnTest` has a case that fires and a case that does not for each of the six, and one for
two conditions holding at once, which reports the first in a fixed documented order.

**Given** the controls and the per-wait sequence of ADR-0013
**When** a condition holds in RUNNING
**Then** the Overlay moves to PAUSED at that Input wait before the Action executes, the Mode strip
names the condition ("paused: before stairs"), the Decision card shows the Decision that was about to
run, and Resume or Next Step then executes it, so the pause never loses an Input wait or changes which
Action is taken
**And** a condition that holds in PAUSED, in HUMAN or in replay does nothing, and a condition does not
fire again for the same wait after Resume
**And** the Mode record is written with the existing record kind and the pause is a Mode change like
any other, so no log format changes.

**Given** the Brain's behaviour
**When** a Run plays with every condition enabled and is resumed each time it stops
**Then** its Action list equals the same Run with none enabled, in a `ScriptedHuman` test on
`EmbeddedHost` over a `standard`-set seed, so the feature is shown to change when the person is asked,
not what the bot does
**And** the settings the evaluator reads come from an `OverlaySettings` value with defaults (all off,
HP threshold 30%), so the settings screen of 8.4 is only its editor
**And** the PR is labelled `fairness`, since the conditions read the Observation and the Decision,
with the reviewer's verdict.

### Story 8.4: The Pause-on section in the game's settings screen

As the human,
I want the Pause-on conditions in the game's own settings screen,
So that I set them where I set everything else and they are still set next time (FR-45, UX-DR15).

**Acceptance Criteria:**

**Given** FR-45's wording ("an Overlay section of the game's own settings screen (one Hook)") and the
hook ledger in `docs/UPSTREAM.md`
**When** the story starts
**Then** the story file reads the pinned settings window and its tab structure with `path:line`,
lists at least three ways to add a section (an extra tab, a button on an existing tab that opens an
Overlay window, a listener the Overlay registers through `Hooks`), picks the smallest edit to upstream
and says why, in the way ADR-0016's rows did
**And** the edit is one hook row with a marker, a `Hooks` listener field so `core` never imports the
Overlay, and a row in `docs/UPSTREAM.md`; the PR is labelled `touches-upstream`, and `HooksLedgerTest`
passes with the diff budget it enforces, or the story amends the budget by the documented ADR route.

**Given** the settings the evaluator of 8.3 reads
**When** the section is built
**Then** it holds a toggle per condition and a stepper for the HP threshold, drawn with the game's own
`RedButton`, check-box and window components, with the strings in the Overlay's own message file
(or the six-string precedent of row 10) and none hard-coded
**And** the values persist in the game's own settings store under Overlay-prefixed keys and survive a
restart, and a missing or corrupt key falls back to the default of `OverlaySettings` instead of
failing the screen
**And** the section is reachable while a Run is in progress and in the title scene, and changing a value
during a Run takes effect at the next Input wait.

**Given** the headless-first rule
**When** the story is verified
**Then** a test builds the settings window against the toolkit fixture (`OverlayToolkitTest`'s
pattern), flips each toggle through the component's own handler and asserts `OverlaySettings`
changed and was stored, and a test restarts the store and reads the values back
**And** the story file carries the checklist for the product owner's live session: the section is
found where a player would look, each toggle pauses a real Run as 8.3 specifies, and a value survives
closing the game
**And** no agent opens the settings screen or any window.

### Story 8.5: The Replay session

As the human,
I want to load a Run log into the Overlay and have the game play it back one Input wait at a time,
So that I can look at a finished Run, mine or the Rig's, in the real game (FR-46).

**Acceptance Criteria:**

**Given** FR-46, `Replay` in the harness and the Run log's hash chain
**When** a log is loaded
**Then** the launcher takes `--replay <log.jsonl>` (and the Panel a Load action, if the story finds one
small enough in the native toolkit; otherwise the launcher flag alone ships and the story says so),
the chain is verified first, and a log whose chain, header tag or codex version does not match the
running build is refused with the reason in the Panel and not played
**And** a loaded Run is its own Panel state, REPLAY, in the state table, with its own enablement row:
the hero's game input is ignored as in PAUSED, Take over is disabled, and the Brain does not decide
(the log's actions are applied by the executor, as `Replay` does).

**Given** the Panel's controls
**When** a replay is stepped
**Then** Next Step applies exactly one recorded wait, Run N applies N, and Resume plays at the speed
mode, all at Input waits, landing on the wait where the person stopped
**And** at each wait the game state's Observation hash is compared with the log's, and a mismatch stops
the replay at that wait, names the section that differs from the section hashes, and does not continue
**And** `ReplaySessionTest`, a `ScriptedHuman`-style test on `EmbeddedHost` over a log written by the
Rig on a `standard` seed, plays the whole log, ends in the recorded outcome and reports zero mismatches,
and a second test flips one byte of the log and shows the refusal.

**Given** that stepping back is not free
**When** the scrubber asks for an earlier wait
**Then** a seek restarts the Run from the header and plays to the wait, and the story measures the
cost of a seek at waits 100, 500 and 1000 of a sewers log on the current host and writes the table to
`docs/results/`
**And** keyframes (a `Rewindable` snapshot every N waits, which `SnapshotHandle` and the Redeterminer
already give the harness) are added only if that table shows a seek over the budget the story writes
down with its reasoning and prints the table that lets the owner overrule it, per the E6 lesson on
numbers the owner has not set
**And** nothing the replay draws touches the Run's generator, and the session leaves no file in the
profile's save slot, so a replay never overwrites a person's saved Run.

### Story 8.6: The Beliefs view

As the human,
I want to see everything the bot believes at an Input wait, live or in a replay, not only the three
most ambiguous items,
So that I can check a belief against what I know and see a bad one before it costs a Run (FR-46).

**Acceptance Criteria:**

**Given** the Belief summary of 5.4 (`BeliefSummary`, at most three items) and the Brain's `Beliefs`
**When** the full view's data is defined
**Then** an `api`-typed `BeliefView` carries, for every unidentified appearance the Brain tracks, its
candidates with probabilities in the Brain's own order, the facts the floor is known to imply with
the reason each holds, the unseen-monster memory (FR-29) with the wait each entry was last seen, and
every guaranteed drop's counter, each already formatted as the summary's lines are
**And** it is built by the brain from its own `Beliefs` and `Memory`, from nothing the Observation did
not carry, and its hash is the Belief hash the `Wait` record already stores, so the view over a log
checks itself against the log
**And** `BeliefViewTest` asserts the contents for a constructed `Beliefs`, and the view contains no
true identity: an appearance's candidates are its own possible identities, never the hidden one.

**Given** the Panel
**When** the Beliefs view is opened
**Then** it is a section of the Panel the person opens from a control with its own hotkey (registered
the way the others are, through row 10 and not a new hook), sorted most ambiguous first, scrollable,
and available in PAUSED, HUMAN and REPLAY
**And** with the Oracle flag on, and only then, a true identity may appear beside the Brain's belief
with the `oracle` prefix and the Oracle border, and `OverlayOracleGateTest` gains the case that
without the flag the same view carries no such line
**And** `BeliefViewContentTest` asserts the order, the scrolling and the oracle gating on constructed
views.

**Given** the E5 threading rule
**When** the view is produced
**Then** the Brain builds it on its worker and hands it to the render thread as an immutable value, as
the summary is, and the Panel never reads the Brain's objects
**And** a test on `EmbeddedHost` takes a view at three waits of a real `standard`-set Run and asserts
each view's hash equals the Belief hash the Run log wrote at that wait
**And** the PR is labelled `fairness`, since it widens what the Overlay is shown of the Brain's state,
with the reviewer's verdict, and the story file carries the checklist for the product owner's live
session.

### Story 8.7: The Replay scrubber

As the human,
I want a scrubber over a loaded Run log that moves to any Decision and shows it with the Beliefs that
went with it,
So that I can go to the wait where a Run went wrong and read why (FR-46, UX-DR15).

**Acceptance Criteria:**

**Given** the Replay session of 8.5 and the Beliefs view of 8.6
**When** a log is loaded
**Then** the Panel shows a scrubber made of a position (wait k of n, turn, Floor), step back and step
forward buttons, jump to the previous and next wait whose actor is human, whose Decision names a
different Policy, or that is a Mode, Note or Boundary record, and a jump to the end, drawn with the
native components and enabled by the same matrix rules as the other controls
**And** the Decision card, the Safety flags, the map highlights and the Explain expansion show the
recorded Decision of the wait under the scrubber, and the Decision log is the log's own records up to
that wait, still a view over the Run log and not a second source
**And** the Beliefs view shows the Beliefs at that wait, recomputed by playing the Brain over the
Observations of the replay, and when the recomputed Belief hash differs from the log's the Panel says
so in place of showing a view it cannot vouch for.

**Given** the cost measured in 8.5
**When** the person jumps backward or forward by more than one wait
**Then** the session seeks as 8.5 specifies and the Panel shows a busy state for the duration, never a
half-seeked position, and a second jump during a seek is ignored until it lands
**And** the recomputed Beliefs are cached per wait for the session so scrubbing back over a wait
already visited is free
**And** `ScrubberTest` drives the scrubber on a real log through a seek forward, a seek back, a jump to
a human wait and a jump to the end, and asserts the shown wait index, Decision and Belief hash each time.

**Given** that a log from the Overlay holds human waits and shadow Decisions
**When** the scrubber reaches one
**Then** a human wait shows the human's Action and the shadow Decision beside it, labelled as the
decision the bot would have taken and did not, and the Notes of the log appear at their wait
**And** a log written by the Rig (no human waits, no Mode records) scrubs the same way with those
parts absent
**And** the story ends with the full demonstration a live session accepts: the checklist for a replay
of the 5.16 sewers run with its takeover is in the story file and the PR, and the product owner runs
it; the agent verifies the same sequence headless and opens no window.

### Story 8.8: The coach's sentence

As a learner,
I want the Brain's choice put as one plain sentence in the game's own words,
So that I understand a decision I would not have made (FR-47, UJ-4).

**Acceptance Criteria:**

**Given** FR-47's rule (Codex vocabulary, at most one plain sentence) and UJ-4's example, `read scroll
(KHIT): by a door and water, worst case survivable`
**When** the composer is written
**Then** a pure `CoachLine` function takes a `RunLog.Decision` and the Observation's visible context
and returns one line that fits the Panel's text width in two wraps, naming the Action in the words
`ActionText` already uses, the reason as one clause chosen from the Policy's own `why` and the Safety
flags, and no number the person has to decode
**And** every name in the sentence is a Codex name for something the screen shows, so an unidentified
item is called by its appearance and never its identity, and a test fails on any word that is not
in the Action vocabulary, the Codex's names or a small closed list of connecting words committed with
the test
**And** a Decision with no usable reason yields the Action alone and not an invented reason.

**Given** the fairness rule that the coach may say only what the bot knows
**When** the composer's inputs are reviewed
**Then** its inputs are the Decision and the Observation and nothing else, the brain does not gain an
import, and a leak test in the style of the Observer's builds two games that differ in hidden state
only and asserts equal sentences
**And** with the Oracle flag on, the sentence is unchanged and an oracle line, if shown at all, is a
separate `oracle`-prefixed line that the flag gates
**And** `CoachLineTest` covers each Policy the Brain has (one case per Policy name in `Policies`) so a
new Policy that has no sentence fails the build instead of silently printing the Action alone.

**Given** the lesson that a rule needs a documented source
**When** the story ends
**Then** the sentence templates are listed in a docs page with the Rule or Lore claim each reason
rests on, and `HeuristicLinkTest` stays green
**And** the PR is labelled `fairness`, with the reviewer's verdict.

### Story 8.9: Coach mode

As a learner,
I want a coach mode where I play and the bot tells me what it would do before each of my moves,
So that I learn from it without it playing for me (FR-47, UJ-4).

**Acceptance Criteria:**

**Given** the HUMAN Mode of 5.8, the shadow Decision of 5.9 and the sentence of 8.8
**When** coach mode is on in HUMAN
**Then** at each Input wait where the human is to move, the Brain decides as it does for the shadow
Decision, the Panel shows the coach's sentence on its own line above the Decision card, and the bot
executes nothing; the hero's input is the person's, as it was in HUMAN
**And** coach mode is a toggle in the Controls with its own hotkey, off by default, and is a setting of
HUMAN and not a fourth Mode: it is disabled in PAUSED, RUNNING and REPLAY with the reason shown
**And** turning it on or off is a Mode record using the existing kind, so no log format changes, and a
log with coach advice replays as an ordinary log.

**Given** that the advice must not slow the person down or change the game
**When** the advice is computed
**Then** it is computed on the Brain's worker while the person thinks, a human move made before it
arrives is taken at once and the late advice is dropped and never shown against the wrong wait, and a
test waits for the sentence on a condition with a deadline, not on a frame count (the E5 lesson)
**And** a coached Run's game state after the same human Actions equals an uncoached Run's: a
`ScriptedHuman` test plays the same scripted moves with and without coach and compares the Observation
hash at every wait, since coach mode must change nothing about the Run
**And** the Brain's updates of its Beliefs and Memory happen exactly as in HUMAN, so a hand back after
a coached stretch re-plans from the same state.

**Given** UJ-4
**When** the story is verified
**Then** a headless session reproduces the journey: the scripted learner takes a move the coach
disagreed with, the log holds the human Action, the shadow Decision and the coach toggle, and the
Decision log and the scrubber show it
**And** the story file carries the checklist for the product owner's live session, whose acceptance is
the feel of it: the advice arrives in time, reads as one sentence, and never covers the map
**And** the PR is labelled `fairness`, with the reviewer's verdict.

### Story 8.10: Autoexplore with the bot's brains

As a learner,
I want to hand a quiet stretch to the bot and get control back when something happens,
So that I skip the dull corridors and keep the fights (FR-47, UJ-4).

**Acceptance Criteria:**

**Given** FR-47's rule that autoexplore returns control when an enemy appears or a Pause-on condition
fires, and the Pause-on evaluator of 8.3
**When** autoexplore is started from HUMAN
**Then** the Brain decides and the executor applies each Action as in RUNNING, restricted to Actions
whose Policy is on a closed list the story commits and documents (exploring, picking up, opening
doors, resting to heal; never fighting, descending, using an unknown item or throwing), and each wait
is recorded with actor `bot` and the Mode record's existing kinds, so no log format changes
**And** control returns to the person, in HUMAN and in coach mode if it was on, at the first Input wait
where a hostile actor is in view, where any enabled Pause-on condition holds, where the Decision's
Policy is not on the list, where the HP fraction fell during the stretch, or where the person presses
Pause, whichever comes first, and the Mode strip names which one.

**Given** that autoexplore must not take a step a person would not
**When** the stop rules are tested
**Then** `AutoexploreTest` on `EmbeddedHost` covers each stop on a constructed state (an enemy walking
into view, a boss floor, a low HP stretch, a Policy off the list) and a stretch that runs to the end
of the level and stops there, and asserts control returned at the wait before the Action that the
person would have refused
**And** a stop caused by an enemy at the edge of vision is the same wait on the same Observation with
and without the Oracle flag, since stopping on an unseen enemy would be a leak
**And** the story reports, from a set of `standard` Runs played by a scripted learner who autoexplores
whenever it can, the share of turns autoexplore covered and the number of times it returned control
with an enemy already adjacent, which the story states is the failure to be near zero.

**Given** the epic's rule on the generator
**When** autoexplore runs at Human play speed
**Then** it uses the speed modes of 5.7 and the draw routing of 5.13 unchanged, no new draw or sound
call is added, and `RenderQueueTest` stays green
**And** the PR is labelled `fairness` for the stop rules and the Policy list, with the reviewer's
verdict, and the story file carries the checklist for the product owner's live session: hand a
corridor to autoexplore, see it stop at the next enemy, take control.

### Story 8.11: The v2 session and the epic close

As the product owner,
I want one scripted demonstration of every v2 surface and a check that the Rig did not move,
So that the epic closes on a session and a number and not on a claim.

**Acceptance Criteria:**

**Given** the epic's done-when
**When** the demonstration is written
**Then** one `ScriptedHuman` test on `EmbeddedHost` over a `standard`-set seed (named on the page)
walks the whole of UJ-4 and the v2 surfaces in order: Pause-on stops a RUNNING bot before stairs, Explain
shows the terms of that Decision, the person takes over with coach mode on and plays a coached move,
autoexplores a corridor and is handed control at an enemy, the Run is saved and its log loaded in a
replay, and the scrubber returns to the Pause-on wait and shows the same Decision and Beliefs
**And** the test asserts the Run log verifies, that each wait's Belief hash equals the recomputed one,
and that no assertion needed a frame count
**And** the product owner's live session is the story file's and the PR's checklist, with one line per
visible surface from 8.2, 8.4, 8.6, 8.7, 8.9 and 8.10, and the story does not claim the live session.

**Given** that v2 must not move the bot
**When** the Rig is run before and after the epic
**Then** the `smoke` set is played at the commit that opened the epic and at the head, and the Action
list of every Run is equal, the logs differ only by the fields 8.1 added (absent when empty, so they do
not differ at all unless a Policy scored), and the Results page says so with the commit hashes
**And** any difference is a defect in the epic, found and fixed or recorded as an issue before the
close, never explained away.

**Given** the epic's close
**When** it ends
**Then** the E8 retrospective runs and its lessons land in `CLAUDE.md`; `docs/roadmap.md`,
`sprint-status.yaml` and the epic issue are updated in the same PR; `docs/architecture.md`,
`docs/fairness.md` and the Overlay's docs describe the new surfaces and the REPLAY state; the state
table in the UX `EXPERIENCE.md` gains REPLAY and coach mode; and `docs/UPSTREAM.md` lists the settings
hook row
**And** the `touches-upstream` rows from this epic are re-verified by `HooksLedgerTest`.

### Settled while planning

Decided by the planner and open to the product owner's override on the pull request that carries these
stories.

1. **Search outcomes are a field, not a feature.** Search has not shipped, so 8.1 adds the field and 8.2
   shows it when present; no story builds a Search to fill it.
2. **Replay recomputes Beliefs instead of storing them.** The log keeps the Belief hash only. Storing
   Beliefs in the log would change every chained wait and make logs large; recomputation costs a seek
   and is checked by the hash that is already there.
3. **Coach mode and autoexplore add no Mode and no record kind.** Coach is a setting of HUMAN and
   autoexplore is a restricted RUNNING; both are logged with the existing kinds. If that cannot hold,
   the story stops for an ADR.
4. **The Pause-on settings edit is the smallest hook that works,** chosen in 8.4 with alternatives, and
   it is the only upstream edit of the epic.
5. **The epic is eleven stories,** with coach mode in two (the sentence, then the mode) and the replay in
   three (session, Beliefs, scrubber), because each of those is more than one turn.

## Epic 9: Learned evaluation (optional, deferred)

A learned value function trained on oracle hindsight labels and run on Observations only. It ships
only if it beats the hand-tuned Evaluation under the Sequential test, and its label Runs must use
seeds disjoint from every committed Seed set, with the seed-permutation differential test as its
precondition.

Covers no PRD requirement; it is the optional far horizon.
