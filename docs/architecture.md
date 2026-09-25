# Architecture

!!! note "Placeholder"
    The BMAD architecture document (bootstrap sessions 11-12) becomes the authoritative
    description once the product owner approves it; this page will then point at it under
    [BMAD artifacts](bmad/index.md) and keep only the module map. Until then the module map
    below and [ADR-0003](adr/0003-module-layout.md) are what exists.

## Modules

Shatterfish adds six Gradle modules beside upstream's, all under `shatterfish/`, package root
`org.shatterfish`. The arrows are the only permitted dependency edges; the build fails on any
other.

```mermaid
graph TD
  core[(upstream core)]
  api --> nothing((JDK only))
  harness --> core
  harness --> api
  codex --> core
  codex --> api
  brain --> api
  rig --> harness
  rig --> brain
  overlay --> core
  overlay --> harness
  overlay --> brain
```

| Module | May depend on | Contents |
|---|---|---|
| `api` | nothing | DTOs only: `Observation`, `Action`, `Belief`, `Decision`, run-log records, the `JsonWriter` they share; and ADR-0009's reserved half, `SnapshotHandle`, `RolloutResult`, `BeliefSample`, the `BeliefSampler` interface, and `Simulator` and `Redeterminer`, two abstract classes whose final methods hold the scrubbed contract (story 1.20) |
| `harness` | `core`, `api` | `Observer` (the only class allowed to read game state into the bot), `ActionExecutor` (the only class that drives the hero), RNG control, snapshot/restore, redetermination, `HeadlessBoot` (the backend, the no-op graphics binding, the virtual display `HeadlessGraphics` reports so that text renders, in-memory settings), `HeadlessScene` (the game's own scene, constructed without a graphics context) and `SceneStepper` (one fenced frame at a time), `HeadlessDriver` (owns a Run in `org.shatterfish.harness.driver`: starts a seeded game the way a player does and steps it to each Input wait, a death or a requested scene change), `UiRole` (the thread that observes and executes, claimed by the driver that starts a Run and asserted by the Observer and the executor on entry, story 1.19), `SnapshotStore` (a Run's exact state at a wait, the game's own save held in memory and put back through its own load, handed out as an opaque `SnapshotHandle`, story 1.20), `Launcher` (the harness command line: a seed, `--oracle` and `--benchmark`, the only place an `OracleObserver` is constructed; its nested `Benchmark` measures the E1 numbers and reads the sidecar under `--oracle`, story 1.21), `OracleObserver` and `OracleView` (the marked read and its sidecar, story 1.18), `EmbeddedDriver` |
| `codex` | `core`, `api` | The Codex generator (`Generate`, one task with no Run, ADR-0017): every mob, item, generator table, mob rotation, trap, recipe and changelog entry as `api` records written as canonical JSON to `codex/<tag>/*.json`, each entry cited to the `path:line` read from the pinned source at generation (`Citations`); `CodexSeedFreeTest` and `CodexLeakTest` hold that no value derives from a seed, a Profile or a Run (story 2.1); the vocabulary diff against a second pinned game, read and never built (story 2.8); the site's Codex pages rendered from that same text by the same task (`Pages`, story 2.9), held against the committed folder by the same drift check and against `mkdocs.yml`'s nav by `CodexDocsTest`; the citation checker as E2 adds it. The harness is on its test classpath only, for the leak test's live Run |
| `brain` | `api` only | Beliefs, scripted policies, tactical search, strategic playbooks, evaluation. Identical code runs headless and in the overlay. Since story 4.1: `Brain` (arbitration over a priority list of `Policy`s, re-planned from each Observation), `Memory` (the Belief's bytes: what was seen, never what was intended), `Stream` (the Brain's own SplitMix64 randomness), and `BrainDecider`, the `api` `Deliberator` the Run loop drives and logs the Decision and Belief hash of. The rig names it `shatterfish`. Since story 4.2 it is built on `Codex.Knowledge` (the identifiable families by deck weight, the items special rooms place, the guaranteed drops), which the rig reads from `codex/<tag>/` (`CodexKnowledge`), and `Beliefs` holds what it believes: candidate identities per unidentified appearance, floor facts, the guaranteed drops a set of floors still owes, and enemies remembered out of sight. Since story 4.4: a `Policy` ranks what it would do, and its next ranks are the Decision's alternatives; `Safety` (the Decision's flags, read off the screen); `Highlights` (the cells the chosen Action points at, handed over through `Deliberator.lastHighlights` into the wait record) Since story 4.5: `Evaluation`, one scoring function, a weighted integer sum of features the Observation shows (position features: hit points, depth, level, strength, hunger, enemies in view; action features: resting while hurt, attacking, descending, searching, waiting), with its weights an `api` `Weights` value the rig reads from `weights/<brain>.json` and hands over at construction; the fallback Policy draws uniformly among the offered Actions that score highest Since story 4.6: `Explore`, the first Policy that changes play: one Step per wait toward the nearest frontier over the cells a click steps onto (never a transition, a chest, an armed trap, the chasm or the well), then a bounded round of searches from cells beside a wall, each spot once; `Memory` (version 3) records where the hero has been seen standing still, and each Policy draws from a stream keyed on its name Since story 4.8: `Pickup` and `Equip`, Policies that take an item worth the turns to reach it and put on a weapon or armour the Evaluation prefers (strength, curse risk, never an unsurvivable worst case under `SafeTest`), on the Codex's gear (`Codex.Gear`: tier, strength, mean roll from the combat table) and the Evaluation's item, gold, turn, weapon, armour and curse weights. Since story 4.3: `SafeTest`, the worst-case check on trying an unidentified potion, scroll or wand where the hero stands, over the candidate identities, the cell's water, the enemies in view and the hero's hit points; a lethal worst case, or a disabling one beside an enemy, is refused whatever the mean |
| `rig` | `harness`, `brain`, `api` | Parallel runner, statistics, SPRT, JSONL run logs, replay; the five committed Seed sets and the one task that writes them (`SeedSets`, `Seeds`, one command with no Run, ADR-0018): every triple derived from the set's own name and the triple's index through the published mix function, so a stranger can reproduce a set rather than download it, and `SeedSetsTest` holds the committed bytes against a fresh derivation. `load` refuses the `holdout` set to a development read and `publish` takes the reason it is being opened (FR-20) |
| `overlay` | `core`, `harness`, `brain` | The in-game UI and `ShatterfishLauncher` |

## Invariants that will not change

- The brain re-plans from the current `Observation` every turn and never assumes it made the
  previous move. This is what makes human takeover in the overlay work without desync.
- Search never sees hidden state: either an abstract tactical model built from the Observation
  and beliefs, or engine rollouts with redetermination (re-sample everything hidden before each
  rollout). Rollouts on the raw saved game are forbidden.
- A run is fully determined by (upstream tag, seed, action list).
- The overlay uses the game's own toolkit only.

See [Fairness](fairness.md) for how the first two are tested and [Glossary](glossary.md) for the
terms.
