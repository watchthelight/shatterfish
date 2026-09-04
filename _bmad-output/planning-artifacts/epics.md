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
| FR-18 | E7 | deferred |
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
| FR-35 | E7 | deferred; weights as data land in 4.5 |
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

## Epic 6: Tactical search (deferred)

Better fighting, if and only if the numbers say so. ADR-0010 fixes the choice rule: the one-ply
model over Codex tables ships first, then the simulator speed, the two transferable Long et al.
properties and the search leak test are measured and published, and only then does the Rig decide
between candidates at equal budget.

Story titles, to be specified when the epic opens: the one-ply expectimax over Codex tables; the
redetermination scrubber and its differential test; the search leak test; the simulator-speed
measurement; the properties measurement; the candidate comparison under the Sequential test.

Covers FR-13, FR-34, and the E6 half of FR-6.

## Epic 7: Strategy and lore (deferred)

Whole-run planning and a knowledge base that admits community claims only with provenance and a
verification tier. Story titles: item-identification strategy; upgrade allocation; per-boss
playbooks; per-class playbooks; playbooks as data; the lore claim format and intake; the variant
classifier from the vocabulary diff; date gating from the changelog; the heuristic-to-Rule link
check.

Covers FR-18 and FR-35.

## Epic 8: Overlay v2 (deferred)

The instrument becomes a teacher. Story titles: the Explain expansion; the Pause-on conditions
section in the game's settings screen; the Replay scrubber over a Run log; the Beliefs view; coach
mode; autoexplore with the bot's brains.

Covers FR-44 to FR-47. The v1 architecture must not preclude any of these (UX-DR15), which the E5
stories assert by keeping the Decision log a view over the Run log and the highlights part of the
Decision record.

## Epic 9: Learned evaluation (optional, deferred)

A learned value function trained on oracle hindsight labels and run on Observations only. It ships
only if it beats the hand-tuned Evaluation under the Sequential test, and its label Runs must use
seeds disjoint from every committed Seed set, with the seed-permutation differential test as its
precondition.

Covers no PRD requirement; it is the optional far horizon.
