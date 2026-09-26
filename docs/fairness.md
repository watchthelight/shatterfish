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
| The Belief summary (Overlay) | `BeliefSummary` (`api`) carries only what `Beliefs` (above) already computed from the Observation the Brain was handed: an unidentified item's label, its single most likely candidate's name and the Brain's own probability for it, never a true identity, plus already-formatted floor-fact and chapter-counter lines. Built by `BeliefSummaries` (`brain`, a pure function of a `Beliefs`) from `BrainDecider`'s own Beliefs, exposed through a new `Deliberator.beliefSummary()` the same way `lastDecision()` already is; no new door from game state, only a reshaping of a door story 4.2 already opened | done: `BeliefSummaryTest.never_names_a_true_identity` (drives a real `Brain` and `Observation`, holds the candidate is always one of the Codex's own named candidates); a `fairness-reviewer` subagent pass was also requested (E5 story 5.4) |
| The worst-case check | `SafeTest` scores an unidentified item over the identities `Beliefs` says it may have, from what the screen shows (the hero's cell and neighbours, the enemies in view, hit points, depth) and a cited table of each identity's worst case; it never reads the item's true class | done: `SafeTestWorstCaseTest` (E4 story 4.3) |
| Exploring | The explore Policy plans over what the map section draws: fog, tiles, heaps, armed traps and transitions the player has seen. A secret door is a wall to it until the game reveals one, and it searches for them rather than walking to them; it remembers only where the hero was seen standing, never what it meant to do | done: `ExplorePolicyTest` (E4 story 4.6) |
| Pick-up and equip | What an item is worth comes from its shown name, the Beliefs' odds for an unidentified appearance, and the Codex; whether a piece of gear is cursed comes only from what the screen shows (`cursedKnown`, `visiblyCursed`), with the generator's three-in-ten for a hidden curse | done: `PickupThresholdTest`, `EquipPolicyTest` (E4 story 4.8) |
| Eating and healing | The eat Policy reads hunger only from the icon's three states, never the value behind it, and knows food by the name the inventory shows; the heal Policy drinks only a potion the screen shows identified as a potion of healing, and measures danger from the enemies in view by story 4.7's threat estimate. It remembers only the wait at which it handed over its last drink | done: `EatPolicyTest`, `HealPolicyTest`, `StarvationRegressionTest` (E4 story 4.9) |
| Testing unknown items | Which appearance to test and whether it is safe come from its shown name, the Beliefs' odds over its candidates, the journal's identified list, the drawn tiles and blobs, the hero's shown buffs and hit points, and the enemies in view; a test the game refused is known only by the next screen still showing the appearance held in the same quantity, never by its true identity; the cells kept out of for fire or gas are the ones the screen drew them on, remembered until drawn clear | done: `TestItemPolicyTest` (E4 story 4.10) |
| Descending | When to leave a floor comes from what the screen shows (the map and fog for what is left to uncover, the hunger icon, the pack, the hit points, the header's sealed flag and the exit's drawn tile) and from the Beliefs' count of guaranteed drops found; what earlier floors held and the hero missed is never read. It remembers only the wait the hero came to the floor and the rests it handed over there | done: `DescendPolicyTest` (E4 story 4.12) |
| Prompts | The prompt section carries a window's title, text and labelled buttons as drawn; the stone of intuition's guess window, whose choices are icons, carries each icon by the name of the type it pictures (general game knowledge: the name its guess button shows after a tap), matched by the frame the icon is drawn with, and listed by name because the screen draws them in an order that changes between processes; the holy tome's spell icons are not carried. The Brain answers from the section, the Beliefs and the hero's class | done: `ItemWindowsTest` (the guess options do not depend on which unknown item is guessed, never name a known type, and come sorted), `GuessOptionsTest` (naming the icons draws nothing from the game's generator), `AnswerRulesTest`, `PromptCoverageTest` (E4 story 4.11) |
| The embedded Run | In the desktop game the Run sees through the same `Observer`, confirms the same Input waits (`WaitGate`, shared with the headless driver) and acts through the same `ActionExecutor`; the Brain runs on a worker thread of its own and is handed only the immutable Observation. An embedded Run's waits equal the headless Run's for the same tuple where the frames are the same, and not otherwise until story 5.13, so its log says `driver: embedded` and the Rig and the Replay refuse it. An oracle Observation reaches only a Run whose log says it is one, and the player's input is closed while a Run plays. An answer that went stale while the Brain thought is dropped and the Brain put back to where it stood (`Rewindable`), so no Belief is built from a screen the Run did not act on. Since story 5.2 the Overlay plays on interface size 1, which changes the log text its Observations carry (the desktop hint lines, and the key or controller button they name), so its log header states `interface` and `controller`, and a Replay at that size waits on #169 (ADR-0013's second exception to non-negotiable 5) | done: `HeaderScreenTest`, `ItemSelectorTest`, `EmbeddedDeterminismTest`, `EmbeddedThreadingTest`, `EmbeddedRunRulesTest`, `EmbeddedEndingsTest`, `OverlayLogsRefusedTest`, `OracleToggleTest`, `InputLockTest` (E5 story 5.1) |
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

Since E5 story 5.1 the Overlay carries its rule: `ShatterfishLauncher`'s `--oracle` is the only
place an `OracleObserver` is constructed in the Overlay (`OverlayOracleGateTest`, which also holds
that nothing in the Overlay depends on the Rig), an oracle Run's window title says `[ORACLE]`,
the Mode strip carries an ORACLE label in the oracle colour (story 5.2, `PanelHudTest.the_oracle_label`),
an oracle Run always opens windowed so fullscreen cannot hide the title bar, and its log header carries
the bit. The launcher's own
spelling, `--oracle`, given to either of the Rig's command lines is refused by name
(`RigOracleGateTest.the_launchers_flag_is_refused_here`). The embedded Run itself cannot make one: it
is handed the observer it sees through, and `OracleGateTest` keeps the harness's classes, the
embedded Run among them, away from the oracle.
