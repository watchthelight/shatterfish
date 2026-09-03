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
| Review | Any diff touching `Observer`, `ActionExecutor`, or `brain` gets the `fairness` label and an adversarial review by the `fairness-reviewer` subagent | session 4 |

## The tests

All live in `harness` and run in CI on every pull request.

| Test | What it checks | Status |
|---|---|---|
| Leak tests | An unidentified scroll, a mob behind a wall, a secret door, a hidden trap, an invisible enemy: none may appear in the serialized Observation | E1 |
| Differential test | Two worlds identical to the player but different in hidden state serialize to byte-identical Observations | E1 |
| Toggle tests | The same world with and without `MindVision`, `Blindness`, and magic mapping produces exactly the expected differences | E1 |
| ArchUnit test | `brain` imports nothing from `com.shatteredpixel.*` or `com.watabou.*` | done |
| Determinism test | The same (tag, seed, action list) twice gives identical Observation hashes at every turn | E1 |

## Search

Search must not see hidden state either. Two fair designs exist: an abstract tactical model built
only from the Observation and beliefs, or engine rollouts with *redetermination*, where before
each rollout everything hidden (unknown item identities, unseen mob positions, RNG) is re-sampled
from the belief state, as bridge and Scrabble engines do. Rollouts on the raw saved game are
cheating and forbidden. The choice is an E6 decision with its own ADR.

## Oracle mode

An `oracle` mode may exist for debugging and for training labels. It is off by default, is
enabled only by an explicit `--oracle` flag, draws a red border and an "ORACLE" label in the
overlay, and cannot be enabled in ranked rig runs.
