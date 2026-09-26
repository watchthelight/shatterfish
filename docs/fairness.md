# Fairness: information parity

This is the only rule of play, non-negotiable #1 of the
[bootstrap prompt](BOOTSTRAP-PROMPT.md). It is enforced by architecture, not intentions.

## The rule

The bot may use only information a human player at the same screen could have: what the renderer
draws, the game log, the journal, and general game knowledge (the wiki-level facts the Codex
extracts). It never reads

- the true identity of an unidentified item,
- the position of an enemy it cannot currently see,
- hidden traps or secret doors,
- RNG state, or
- the seed.

Mind vision, magic mapping, and similar count only when the in-game effect is active.

## How it is enforced

| Layer | Mechanism | Status |
|---|---|---|
| Classpath | `brain` depends on `api` only; the Gradle dependency graph has no path to `core` | done, [ADR-0003](adr/0003-module-layout.md) |
| Build configuration | `brain/build.gradle` fails configuration if any game module reaches its compile or runtime classpath | done |
| Static test | ArchUnit: no class in `org.shatterfish.brain..` depends on `com.shatteredpixel..` or `com.watabou..` | done, `BrainImportsNoGameCodeTest` |
| Single door | `Observer` in `harness` is the only class that reads game state into `api` types; it builds the Observation from what the game already computes for drawing (`heroFOV`, `visited`, `mapped`, `Trap.visible`, `Heap.seen`, `Item.isIdentified()` and friends), never from raw model fields | E1 |
| Leak tests | Every change to `Observer` ships with leak tests (below) | E1 |
| The Brain's seed | A Brain's stream is seeded from its name and nothing about the Run (`Brains.brainSeed`). The random agents' seed mixes the dungeon seed with the hero class and challenges, which the Observation header states, and the mix is a bijection: a Brain holding it could recover the dungeon seed. The Run log's header and boundary records, which carry the seed and the salt, and seed-set entries are denied types in `brain`; the Decision a Brain hands over for the log is the one part of `RunLog` it may name | done: `ShatterfishRunTest`, `BrainBoundaryRulesBiteTest` (E4 story 4.1) |
| Beliefs | What the Brain believes about an unidentified item is inferred from the appearance's name, the journal's identified list and the Codex's deck weights, never read; floor facts come from what the map draws; remembered enemies are the ones the actors section showed | done: `BeliefConsistencyTest` (E4 story 4.2) |
| The worst-case check | `SafeTest` scores an unidentified item over the identities `Beliefs` says it may have, from what the screen shows (the hero's cell and neighbours, the enemies in view, hit points, depth) and a cited table of each identity's worst case; it never reads the item's true class | done: `SafeTestWorstCaseTest` (E4 story 4.3) |
| Exploring | The explore Policy plans over what the map section draws: fog, tiles, heaps, armed traps and transitions the player has seen. A secret door is a wall to it until the game reveals one, and it searches for them rather than walking to them; it remembers only where the hero was seen standing, never what it meant to do | done: `ExplorePolicyTest` (E4 story 4.6) |
| Pick-up and equip | What an item is worth comes from its shown name, the Beliefs' odds for an unidentified appearance, and the Codex; whether a piece of gear is cursed comes only from what the screen shows (`cursedKnown`, `visiblyCursed`), with the generator's three-in-ten for a hidden curse | done: `PickupThresholdTest`, `EquipPolicyTest` (E4 story 4.8) |
| Prompts | The prompt section carries a window's title, text and labelled buttons as drawn; the stone of intuition's guess window, whose choices are icons, carries each icon by the name of the type it pictures (general game knowledge: the name its guess button shows after a tap), matched by the frame the icon is drawn with, and listed by name because the screen draws them in an order that changes between processes; the holy tome's spell icons are not carried. The Brain answers from the section, the Beliefs and the hero's class | done: `ItemWindowsTest` (the guess options do not depend on which unknown item is guessed, never name a known type, and come sorted), `GuessOptionsTest` (naming the icons draws nothing from the game's generator), `AnswerRulesTest`, `PromptCoverageTest` (E4 story 4.11) |
| Review | Any diff touching `Observer`, `ActionExecutor`, or `brain` gets the `fairness` label and an adversarial review by the `fairness-reviewer` subagent | session 4 |

## The tests

All live in `harness` and run in CI on every pull request.

| Test | What it checks | Status |
|---|---|---|
| Leak tests | An unidentified scroll, a mob behind a wall, a secret door, a hidden trap, an invisible enemy: none may appear in the serialized Observation | done: `MapLeakTest`, `ActorLeakTest`, `ItemLeakTest`, `EnvironmentLeakTest`, `FogParityTest`, `MimicDifferentialTest` (E1 stories 1.8 to 1.11); `VisibilityChecklistTest` holds every row of ADR-0006 to a suite |
| Codex leak test | No Codex value derives from a seed, a Profile or a Run: the generator's classes cannot reach the game's state, the toolkit or the harness, and a generation at a live Run's Input wait equals one before it and the committed folder | done: `CodexLeakTest`, `CodexSeedFreeTest` (E2 story 2.1, ADR-0017) |
| Differential test | Two worlds identical to the player but different in hidden state serialize to byte-identical Observations | done: `HiddenStateDifferentialTest` (E1 story 1.17); the behavioural form is E4 |
| Toggle tests | The same world with and without `MindVision`, `Blindness`, and magic mapping produces exactly the expected differences | done: `VisionToggleTest` (E1 story 1.17) |
| ArchUnit test | `brain` imports nothing from `com.shatteredpixel.*` or `com.watabou.*` | done |
| Determinism test | The same (tag, seed, action list) twice gives identical Observation hashes at every turn | done: `DeterminismTwoJvmTest` (E1 story 1.16), in one process and across two; the cross-platform comparison is story 3.4 |
| Thread-confinement test | The Observer and the executor fail loudly on any thread but the UI-role thread, naming it; no Shatterfish code declares a monitor on a game type | done: `ThreadConfinementTest`, `MonitorConfinementTest` (E1 story 1.19), with the stepper's fence and the headless scene as the two named exemptions; the rule sees monitors Shatterfish declares, not those a synchronized game method takes inside one call |

## Search

Search must not see hidden state either. Two fair designs exist: an abstract tactical model built
only from the Observation and beliefs, or engine rollouts with *redetermination*, where before
each rollout everything hidden (unknown item identities, unseen mob positions, RNG) is re-sampled
from the belief state, as bridge and Scrabble engines do. Rollouts on the raw saved game are
cheating and forbidden. The choice is an E6 decision with its own ADR. What E1 already enforces
(story 1.20): a snapshot's bytes never leave the harness's driver package, held by
`SnapshotBoundaryTest`; `api` carries an id, a wait and a `scrubbed` flag; and `Simulator.simulate`
is final and refuses a handle whose flag is false before any rollout can run. The flag is a claim,
so the rollout host that holds the bytes verifies it by id when it arrives.

## Oracle mode

An `oracle` mode may exist for debugging and for training labels. It is off by default, is
enabled only by an explicit `--oracle` flag, draws a red border and an "ORACLE" label in the
overlay, and cannot be enabled in ranked rig runs. Since E1 story 1.18 it exists in the harness:
`OracleObserver` returns the ordinary Observation with the header's oracle bit set, so its hashes
differ from a fair Run's, and an `OracleView` sidecar beside it that no `api` type can hold; the
harness `Launcher`'s `--oracle` is the only place one is constructed inside the harness, and
`OracleGateTest` holds that by ArchUnit and by reflection over everything an Observation can
reach. The overlay's marking is E5's and the rig's refusal is E3's (story 3.3), both driven by the
same header bit; each of those modules carries its own construction rule when it arrives.
