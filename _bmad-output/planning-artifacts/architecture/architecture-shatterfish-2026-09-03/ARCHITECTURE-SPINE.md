---
name: Shatterfish
type: architecture-spine
purpose: build-substrate
altitude: initiative
paradigm: 'ports-and-adapters around one immutable Observation: the game is the outside world, Observer and ActionExecutor are the only two ports, the Brain is a pure function of Observations, and every driver (headless, embedded, replay) is an adapter'
scope: 'Shatterfish v1, epics E1 to E5, with the E6 interfaces reserved; governs every module under shatterfish/ and every hook into upstream'
status: final
created: '2026-09-03'
updated: '2026-09-04'
binds: [FR-1..FR-53, NFR-1..NFR-9]
sources:
  - docs/BOOTSTRAP-PROMPT.md (sections 1 and 4)
  - _bmad-output/planning-artifacts/prds/prd-shatterfish-2026-09-03/prd.md
  - _bmad-output/planning-artifacts/prds/prd-shatterfish-2026-09-03/addendum.md
  - _bmad-output/planning-artifacts/ux-designs/ux-shatterfish-2026-09-03/EXPERIENCE.md
  - _bmad-output/planning-artifacts/research/technical-shatterfish-engine-foundations-2026-09-03/research.md
  - docs/codebase-map.md
  - docs/adr/0003-module-layout.md
companions:
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
---

# Architecture Spine — Shatterfish

## Design Paradigm

Ports-and-adapters around one immutable value. The upstream game (`core`, `SPD-classes`) is the
outside world. Two ports face it: `Observer` (game state → `Observation`) and `ActionExecutor`
(`Action` → game input through the UI's own code paths). The Brain is a pure function
`(Observation, Belief) → (Decision, Belief')`, dependent on `api` alone. Drivers are adapters that
own a game instance and a thread: `HeadlessDriver` (E1), `EmbeddedDriver` (E5), `ReplayDriver`
(E3). The Rig and the Overlay are two callers of the same driver contract.

| Layer | Package | Holds |
|---|---|---|
| Value | `org.shatterfish.api` | `Observation`, `Action` (sealed), `Decision`, opaque `Belief`, `SnapshotHandle`, `Simulator`, Run-log records, `Registration`, Seed set, Codex tables, `ObservationCodec`, `JsonWriter` |
| Ports | `org.shatterfish.harness` | `Observer`, `OracleObserver`, `ActionExecutor`, `RngControl`, `Snapshot` (never leaves the module), `SnapshotStore`, `Redeterminer`, `Profile` |
| Adapters | `org.shatterfish.harness.driver`, `org.shatterfish.overlay`, `org.shatterfish.rig` | drivers, the Panel, the runner |
| Pure | `org.shatterfish.brain` | policies, beliefs, evaluation, search |
| Knowledge | `org.shatterfish.codex`, `codex/<tag>/`, `lore/` | generated tables (JSON), Rules, Lore |

## Invariants & Rules

```mermaid
graph TD
  core[(upstream core)]
  api --> jdk((JDK only))
  harness --> core
  harness --> api
  codex --> core
  brain --> api
  rig --> harness
  rig --> brain
  overlay --> core
  overlay --> harness
  overlay --> brain
```

### AD-1 — Module boundaries and the brain wall [ADOPTED]

- **Binds:** all
- **Prevents:** a second door from game state to the bot; game types inside the Brain
- **Rule:** dependency edges exactly as the graph; `brain` → `api` only, enforced by the declared
  edges, a resolution-time check in `brain/build.gradle`, and an ArchUnit rule against
  `com.shatteredpixel..` and `com.watabou..` (ADR-0003), plus an ArchUnit rule that `brain` uses
  no `java.io`, `java.nio.file`, `java.net` or `java.lang.reflect`: the Observation is its only
  channel, so it cannot read a Run log, the salt or a Seed set. The Codex reaches the Brain as
  JSON data loaded by the caller through `api` types, never as classes and never by the Brain
  itself.

### AD-2 — One Observation type, whitelist by construction

- **Binds:** FR-3, FR-8, FR-9, FR-10, FR-23
- **Prevents:** two components serializing the world differently; a hidden field leaking by
  default
- **Rule:** the Observation is the record tree of ADR-0005; every field is something the screen,
  HUD, log or journal shows; enums have no secret members (`Tile` has no `SECRET_*`); the
  canonical binary codec in `api` is the only encoder and SHA-256 over section hashes is the
  only hash; every list has a canonical order fixed by the codec; the schema version is in the
  header and bumps on any encoding change; neither the seed, the salt, a turn counter nor any
  oracle data is a field of the Observation.

### AD-3 — The Observer reads drawing predicates only

- **Binds:** FR-3, FR-8, FR-10, FR-11
- **Prevents:** an Observer that recomputes visibility or reads model fields the renderer does not
- **Rule:** the per-rule table of ADR-0006 is the whitelist; each row cites the game line the
  renderer uses and has a leak test; the Observer runs only at an Input wait (AD-5) on the
  UI-role thread (AD-8); `OracleObserver` is the only extension and is refused
  by the Rig.

### AD-4 — Actions go through the UI's code paths

- **Binds:** FR-4, FR-40
- **Prevents:** a second way to move the hero that the game's own guards do not see
- **Rule:** the `Action` kinds are the sealed set of ADR-0014, every parameter is a value the
  Observation carries, and one Action is one human input: in particular a move is **one step to an
  adjacent cell**, never a multi-cell target, because the game does not return the hero to ready
  between cells (ADR-0015). `ActionExecutor` dispatches exactly what a click, key or button would
  (`Hero.handle`, `Item.execute`, `Hero.rest`, `Hero.search`, window buttons) on the UI-role
  thread, drives the game's own selector for a targeted Action within the same wait, validates
  against the Observation's `actions` section before touching state, and rejects with a `Reason`;
  `Wait` is invalid while a Prompt is open.

### AD-5 — An Input wait is the unit of everything

- **Binds:** FR-2, FR-23, FR-27, FR-39, FR-40
- **Prevents:** drivers that disagree on when a turn starts; Run logs and Decisions that cannot
  be aligned
- **Rule:** an Input wait is "hero ready and either no `Window` or a recognised Prompt window"
  (`docs/rules/game-loop.md`), detected by the notification at the `Dungeon.observe()` site inside
  `Hero.act()`'s `!ready` branch and confirmed by the UI-role thread (ADR-0015), never by polling
  and never from `Hero.ready()`, which the game calls on every actor-thread wake-up;
  exactly one Observation, one Decision, one Action, one Run-log record and one RNG reseed happen
  per Input wait; the wait index `k` is the primary key across all of them; the Run log, not the
  Observation, records the turn as `Statistics.duration + Actor.now()` in fixed-point thousandths.

### AD-6 — Determinism is owned by the Harness

- **Binds:** FR-2, FR-6, FR-24, NFR-2
- **Prevents:** a Run whose outcome depends on the machine, the profile, or the draw count
- **Rule:** ADR-0007: the seed is the game's seed and the Run is a seeded game; the Run tuple is
  (tag, class, challenges, seed, salt, Action list) with the salt drawn by the runner, logged,
  and never observed; the Harness reseeds the base generator from `mix(salt, k)` at every Input
  wait after `Dungeon.init`; every Run starts in a fresh versioned standard Profile (English,
  intro off, all guide pages read, no bones, badges or rankings) in its own working directory;
  one process hosts one Run; identity-hash order is removed by the identity-order hook row and
  render-thread draws by the routing hook row. The determinism test runs in two JVMs.

### AD-7 — The Brain is a pure function

- **Binds:** FR-27, FR-28, FR-32, FR-33, NFR-4
- **Prevents:** a Brain that remembers what it did instead of what it saw; a Brain that cannot
  be handed a human's turn
- **Rule:** `Brain.decide(Observation, Belief) → (Decision, Belief)` for a bot turn and
  `Brain.update(Observation, Belief) → Belief` for a human turn, both deterministic given the
  generator the caller seeds from `mix(salt, k)`; the Brain holds no game object and no thread;
  state lives only in the returned `Belief`, an `api` value whose hash is in the Run log; a
  Decision is tagged with its `k` and a stale one is never executed; the Overlay's thinking
  budget delays a Decision and never changes it.

### AD-8 — Threads: game-actor, UI-role, brain-worker

- **Binds:** FR-12, FR-38, NFR-4
- **Prevents:** the 2020-style deadlock between render and actor threads; a Panel drawn from the
  wrong thread
- **Rule:** the game's actor thread is never touched by Shatterfish code; the UI-role thread
  (the render thread in the Overlay, the driver thread headless) is the only one that observes,
  executes Actions and writes the Panel; the Brain runs on its own worker with an immutable
  Observation; Observer and ActionExecutor assert the UI-role thread on entry. Detail and
  hand-off rules: ADR-0013.

### AD-9 — Hidden state never enters a rollout

- **Binds:** FR-6, FR-13, FR-34
- **Prevents:** a Search that peeks
- **Rule:** any simulation the Brain uses is either an abstract model built from the Observation
  and Belief in `brain`, or a redetermined `Snapshot` produced by `harness` from a Belief sample,
  whose Observation is proven equal to the original by the differential test (ADR-0009's
  hidden-element table); a `Snapshot` is a **`harness` type and never leaves it** (its bytes are
  the game's own save bundle, which anything holding them could inflate with the JDK alone, so an
  `api` `Snapshot` would be a total parity break), `api` carries only an opaque `SnapshotHandle`
  and the `Simulator` interface the Brain calls; `SnapshotStore` and its restore-and-replay test
  ship in E1, the scrubber in E6; the rollout host asserts the scrubbed flag; the search design is
  chosen by ADR-0010's measurements and the Rig, never by this spine.

### AD-10 — Hooks are one-line, registered, counted

- **Binds:** all code under upstream directories
- **Prevents:** silent upstream edits; a merge that drops one
- **Rule:** ADR-0008: a hook site is one line calling a nullable listener in
  `…/shatterfish/Hooks.java` (or a semantically neutral one-line edit), carries the marker
  `// shatterfish-hook:<id>`, and has a row in `docs/UPSTREAM.md`; a test counts markers against
  rows; the v1 budget is eight.

### AD-11 — Every published number is a Registration plus a Run log

- **Binds:** FR-19 to FR-26, NFR-2
- **Prevents:** a Results page that cannot be reproduced
- **Rule:** a comparison runs only under a committed Registration (Hypothesis id, `p0`, `p1`,
  `α`, `β`, `n0`, `nmax`, Seed set version, the salts, both Brains' commits, budget, machine
  class); every Run writes the gzip JSONL log of ADR-0011 with its hash chain, keyed by `k`; the
  Sequential test is the Per-pair GSPRT of ADR-0012 with the e-process as the calibrated
  alternative; the runner refuses `holdout` for development and any Oracle Run; the Results page
  carries the chain, the trace, the measured pair correlation and the command that reproduces it.

### AD-12 — The Overlay is an instrument built from the game's toolkit

- **Binds:** FR-37 to FR-47, NFR-6
- **Prevents:** a second UI toolkit; an Overlay that edits the HUD
- **Rule:** the Panel is a Noosa `Component` using `Chrome`, `renderTextBlock`, `RedButton`,
  `Icons`, `ScrollPane`, added to the scene through the `GameScene` seam hook, placed per
  `DESIGN.md` Layout; it reads only Observations and Decisions; hotkeys are `SPDAction`s with
  defaults F6 to F11; the launcher owns the Profile and the oracle flag.

### AD-13 — Every value that crosses a module edge is an `api` type with a codec and a version

- **Binds:** FR-3, FR-14, FR-23, FR-29, FR-34
- **Prevents:** a shape agreed by two modules in code and nowhere else; a hash over bytes whose
  producer and consumer disagree
- **Rule:** `Observation`, `Action`, `Decision`, `Belief`, `SnapshotHandle`, the Run-log records,
  the `Registration`, the Seed set and the Codex tables are `api` types with a canonical encoder
  and a schema version in `api`. The `Belief` is **opaque outside `brain`**: `brain` produces and
  consumes it, `api` declares it as a versioned byte-carrying value, and `harness` may only hash,
  log and hand it back, so the Belief crosses the `harness`/`brain` edge without a dependency.
  The Codex reaches the Brain as `api`-typed data loaded by the caller, carries its own version,
  and that version is in the Run-log header and the Registration, because the Codex determines
  Brain behaviour and its combat tables are measured rather than derived.

### AD-14 — One owner per mutable thing

- **Binds:** all
- **Prevents:** two components writing the same state and disagreeing about who won
- **Rule:** the driver owns `k`, the RNG reseed, the Profile directory, the Run log file and the
  snapshot store; the Observer owns the `GLog` listener registration and re-registers it on every
  scene creation (`GameLog`'s constructor replaces it, `…/ui/GameLog.java:47`); the
  ActionExecutor is the only caller of `Hero.handle` and `hero.next()` from Shatterfish code; the
  Panel owns only its own `Component` tree; the Rig owns the Registration, the salts and the
  Results. A Run id is
  `<tag>-<class>-<challenges>-<seedcode>-<salt>-<brain>` so the two Brains of a pair never share
  a log file.

## Consistency Conventions

| Concern | Convention |
|---|---|
| Naming | Glossary terms are class names: `Observation`, `Action`, `Decision`, `Belief`, `Policy`, `Goal`, `SafetyFlag`, `RunLog`, `Registration`, `SeedSet`; packages `org.shatterfish.<module>`; test classes end in `Test`, leak tests in `LeakTest` |
| Identifiers | Input wait index `k` (0-based, long); Run id = `<tag>-<class>-<challenges>-<seedcode>-<salt>`; Hypothesis id = `H-<yyyymmdd>-<slug>`; schema version = int in `Observation.header` |
| Data and formats | `api` values are Java records; canonical binary for hashing (ADR-0005); JSONL for Run logs (ADR-0011); JSON for the Codex; no floats in hashed data (integer pairs); UTF-8 everywhere; times in ISO-8601 UTC only in Results metadata, never in hashed data |
| Errors | The Brain throwing produces `Decision.wait` with the exception class in the Run log and the Panel (EXPERIENCE.md "Brain error"); the game never crashes because of the Brain; an invalid Action is rejected with a `Reason` value, never an exception |
| Logging | Shatterfish code logs through `java.util.logging` with the module as logger name; game log lines are data (part of the Observation), never re-logged; the Run log is plain `.jsonl` a person can read with standard tools (NFR-9), and the Rig may gzip only archived Runs |
| Network | No Shatterfish code opens a network connection and nothing is sent anywhere (NFR-8): an ArchUnit rule bans `java.net` and `java.net.http` in every Shatterfish module, and the launcher disables upstream's `services` news and update checks |
| Portability | Windows and Linux are supported and tested in CI; macOS is best-effort and untested (NFR-7), which the Results pages state |
| Config | Launcher flags (including `--oracle`, which exists only there, FR-11) and the Rig CLI (`--brain --baseline --seeds --seed-start --parallel --out`, never `--oracle`); committed data files are Seed sets, Registrations, Playbooks and Evaluation weights (FR-33, FR-35), each an `api`-typed, versioned file, and nothing else |
| Threads | Every public method of `Observer`, `ActionExecutor` and the Panel starts with a thread assertion (AD-8) |
| Determinism | Every generator used by Shatterfish code is seeded from `mix(salt, k)`; `HashMap`/`HashSet` iteration never decides an outcome in Shatterfish code (use `LinkedHashMap`, sorted keys, or lists) |

## Stack

| Name | Version |
|---|---|
| Java | 21 (Shatterfish modules; upstream compiles for 11) |
| Gradle | 9.4 (wrapper; 9.7.1 is current, pinned deliberately until an upgrade story) |
| libGDX (upstream) | 1.14.0, `gdx-backend-headless` + `gdx-platform:natives-desktop` for the Harness |
| JUnit | 5.11.4 (5.14.4 is current; bump with ArchUnit in E1) |
| ArchUnit | 1.3.0 (1.5.0 is current as of 2026-08-04; bump in E1) |
| Statistics | hand-ported GSPRT in `rig`, no library |
| Android Gradle Plugin | 9.1.0 (upstream's, on the root buildscript classpath; unused unless `-Pshatterfish.mobile=on`) |
| Docs | MkDocs Material per `docs/requirements.txt` |

## Structural Seed

```text
shatterfish/
  api/       org.shatterfish.api          # Observation, sealed Action, Decision, opaque Belief, SnapshotHandle, Simulator, RunLog records, Registration, SeedSet, Codex tables, ObservationCodec, JsonWriter
  harness/   org.shatterfish.harness      # Observer, OracleObserver, ActionExecutor, RngControl, Profile, Snapshot, Redeterminer
             org.shatterfish.harness.scene    # HeadlessScene (harness-owned Scene), no-op GL, Pixmap atlases
             org.shatterfish.harness.driver   # HeadlessDriver, ReplayDriver, the driver contract
             src/test  fairness suite: LeakTest per ADR-0006 row, DifferentialTest, ToggleTest, DeterminismTest (two JVMs), HooksTest
  codex/     org.shatterfish.codex        # generators per table, completeness check, citation checker, vocabulary diff
  brain/     org.shatterfish.brain        # Brain (pure), Beliefs, Policies, Arbitration, Evaluation, safeTest, Playbooks and weights as data; search/ reserved
  harness/   …/agent                      # RandomAgent and the throughput benchmark (FR-5, SM-4)
  rig/       org.shatterfish.rig          # Runner (process per Run), SeedSets, Registration, Gsprt, EProcess, Results, Replay, death gallery (FR-26)
  overlay/   org.shatterfish.overlay      # ShatterfishLauncher, EmbeddedDriver, Panel and components, SPDActions
core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/shatterfish/Hooks.java   # the registry (hook #2)
codex/<tag>/*.json         # generated, CI-checked
docs/rules/, docs/adr/, docs/UPSTREAM.md
```

```mermaid
sequenceDiagram
  participant A as game actor thread
  participant U as UI-role thread (driver or render)
  participant B as brain worker
  A->>A: Hero.act() with no curAction: ready(), returns false, parks
  U->>U: Input wait k detected (hero.ready, no Window)
  U->>U: RngControl.reseed(salt, k); obs = Observer.observe()
  U->>B: decide(obs, belief)
  B-->>U: Decision (or wait after budget, Overlay only)
  U->>U: ActionExecutor.execute(decision.action) via Hero.handle / Item.execute
  U->>A: hero.next() wakes the actor thread
  A->>A: actors run until the hero parks again
```

## Capability → Architecture Map

| Capability / Area | Lives in | Governed by |
|---|---|---|
| 4.1 Headless engine (FR-1..FR-6) | `harness` (`scene`, `driver`, `Observer`, `ActionExecutor`, `RngControl`, `Snapshot`) | AD-2..AD-6, AD-9 |
| 4.2 Fairness (FR-7..FR-13) | `harness/src/test`, `brain/build.gradle`, ArchUnit in `api` and `brain` | AD-1, AD-3, AD-8, AD-9 |
| 4.3 Codex and knowledge (FR-14..FR-18) | `codex`, `codex/<tag>/`, `docs/rules/`, `lore/` | AD-1 (data only), conventions |
| 4.4 Rig (FR-19..FR-26) | `rig` | AD-6, AD-11 |
| 4.5 Brain (FR-27..FR-36) | `brain` | AD-7, AD-9 |
| 4.6 Overlay (FR-37..FR-47) | `overlay`, hooks 3, 7, 8 | AD-4, AD-8, AD-10, AD-12 |
| 4.7 Program and upstream (FR-48..FR-53) | `docs/`, `.github/`, `.claude/` | AD-10, ADR-0002, ADR-0004 |

## Deferred

| Decision | Why it can wait | Revisit |
|---|---|---|
| The tactical search design (sampled search vs information-set search vs the one-ply model) | must be measured: simulator speed, Long et al. properties, the search leak test, then the Rig | E6 per ADR-0010's choice rule |
| GSPRT `p0`, `p1`, `α`, `β`, `n0`, `nmax` and the e-process comparison | calibrated by simulation on the Rig's own outcome distribution | E3 calibration story per ADR-0012 |
| Redetermination scrubber key list | follows `Dungeon.saveGame` at the tag; reviewed by the fairness reviewer before the first E6 search story | E6 per ADR-0009 |
| Classloader isolation (several Runs per JVM, and as the rollout host of ADR-0009) | process per Run is the default; the E1 spike measures | E1 spike report |
| Codex generation mechanics (reflection per table, measured combat tables) | E2 stories; no cross-module divergence risk | E2 |
| Learned components | optional E9; annual review of the learned frontier | 2027 |
| Seed-set sizes and the `goo` set's size | committed files with a version; revised by ADR once throughput is measured | E3, per FR-20 |
| PRD open questions 1, 5, 6, 7, 8, 10 | each is a measurement or a product call, none blocks a module boundary | named epics in the PRD |
| Where `standard` runs (developer machine vs GitHub Actions) and result publication cadence | ADR-0002 fixes the CI shape (PR gate, nightly `smoke`, results PR); the `standard` host is decided when its cost is measured | E3 per PRD open question 11 |
