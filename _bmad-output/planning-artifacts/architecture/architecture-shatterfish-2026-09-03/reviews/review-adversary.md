---
name: Adversarial review of the Shatterfish architecture spine
type: architecture-review
lens: adversary
target: _bmad-output/planning-artifacts/architecture/architecture-shatterfish-2026-09-03/ARCHITECTURE-SPINE.md
companions: docs/adr/0003, 0005-0013
date: '2026-09-03'
status: draft
verdict: 'The spine is coherent as prose but under-determined as a build substrate: eighteen pairs of units can each obey every AD to the letter and still fail to compose, five of them silently.'
---

# Adversarial review — architecture spine

## Method

The spine is attacked as a *substrate*, not as a document. For each attack I construct two units
one level down — two E1 stories, an E1 story and an E5 story, two modules — give each the most
faithful possible reading of the ADs and their ADRs, and then ask whether the two artifacts
compose. A finding counts only when **both units obey every AD to the letter** and the result is
still broken. Twelve of the eighteen are grounded in the pinned upstream code
(`v3.3.8`); those carry `path:line` citations per non-negotiable #8. Paths abbreviate
`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/` and
`SPD-classes/src/main/java/com/watabou/` as `≈/`.

Severity:

- **S1 — silent**: both units ship green, and something published is wrong (a hash, a number, a
  fairness claim). The worst class, because no test in the suite as specified fails.
- **S2 — loud**: data loss, deadlock, or a lifecycle that cannot complete. Expensive but visible.
- **S3 — rework**: two honest readings that cost a story to reconcile once they meet.

| # | Clash | Units | Sev |
|---|---|---|---|
| 1 | The game log listener is destroyed at every scene creation | E1 Observer `log` / E5 level change | S1 |
| 2 | Input-wait detection fires per actor-thread wake-up; the de-dup rule is vacuous | E1 hook row 3 / E1 driver | S1 |
| 3 | `Belief` crosses a module edge that does not exist and carries floats into a chained hash | E1 `api` / E4 FR-29 | S1 |
| 4 | Both Brains of a pair produce the same Run id and overwrite one log | E3 runner / E3 Registration | S1 |
| 5 | The Codex version is in no tuple, no log header and no Registration | E2 Codex / E3 Registration | S1 |
| 6 | Snapshot-per-Input-wait takes game monitors, mutates the logged turn, and shares the save slot | E5 Take over / E1 thread confinement | S2 |
| 7 | The PAUSED input block is overwritten by the game at every Input wait | E5 PAUSED / upstream `ready()` | S2 |
| 8 | Nobody owns `hero.next()` | E1 ActionExecutor / E5 Prompt and ability actions | S2 |
| 9 | `k` has two owners and rollouts have no counter of their own | E1 RngControl / E6 rollout host | S2 |
| 10 | AD-5 says one Run-log record per wait; ADR-0011 defines five kinds keyed by `k` | E3 Replay / E5 Overlay logging | S2 |
| 11 | Save-and-quit and resume have no record kind, no `k` rule and no chain rule | E5 launcher / E3 Replay | S2 |
| 12 | Two owners of the Profile, and two destinations for the Run log | E1 Harness Profile / E5 launcher | S2 |
| 13 | The `actions` section is circular and has two sources of truth | E1 Observer / E1 ActionExecutor | S2 |
| 14 | `Decision.wait` is not always a valid Action, so the error path deadlocks | E4 Brain error / E1 executor validation | S2 |
| 15 | The classloader-isolation deferral cannot be reached from the units built before it | E1 hook registry / E1 isolation spike | S3 |
| 16 | The `InterlevelScene` generation thread has no role in AD-8 | E1 RngControl / E1 scene seam | S3 |
| 17 | `--oracle` exists on a runner that must always refuse it; "ranked" is undefined | E3 CLI / E3 refusal | S3 |
| 18 | `BeliefSample` is shaped in E1, produced by nobody, and lets the Brain choose a salt | E1 reserved interfaces / E4 Beliefs | S3 |

---

## 1. The game log listener is destroyed at every scene creation — S1

**Unit A — E1, "Observer: the `log` section".** ADR-0006's Log row says the Observer reads "the raw
`GLog` messages (text and color prefix) captured from the `GLog.update` signal on the thread that
emits them, kept in order and capped at N; never `GameLog.entries`". The honest implementation
registers a listener on `GLog.update` once at Run start, into a Harness-owned ring buffer, and the
Observer copies the last N at each Input wait. No hook is needed: `GLog.update` is a public
`Signal<String>` (`…/utils/GLog.java:39`) and `Signal.add` is public API.

**Unit B — E5, "the driver re-attaches at level change".** ADR-0013: "`RUN OVER` and level changes
are seen as the scene being destroyed and recreated (`InterlevelScene`), so the driver re-attaches
through the scene seam hook each time and keeps `k` across floors." The story re-attaches the
driver, the Panel and the Input-wait notification. It has no reason to touch the log listener,
which was registered at Run start and never removed.

**The clash.** `GameLog`'s constructor — one per `GameScene`, so one per level change and one per
resume — calls `GLog.update.replace( this )` (`…/ui/GameLog.java:47`), and `Signal.replace` is
`removeAll()` followed by `add()` (`≈/utils/Signal.java`). The Harness listener is silently
unregistered the first time the hero takes the stairs. From depth 2 onward the Observation's `log`
section is empty in the Overlay. Headless, the `HeadlessScene` of the spine's Structural Seed is
Harness-owned and constructs no `GameLog`, so `replace` is never called and the listener survives
for the whole Run. The two drivers therefore produce **different `log` sections for the same
(tag, class, challenges, seed, salt, Action list)** from depth 2 on, which means different section
hashes, different Observation hashes and a different Hash chain.

Every specified test still passes. The determinism test (AD-6) runs the *same* driver in two JVMs.
The leak tests assert absence, not presence. The differential test compares two worlds under one
driver. Nothing compares the headless log section against the Overlay's. The first symptom is
FR-24 failing on the first Overlay Run anyone tries to Replay — in E5, after the Rig's numbers
have already been published — and the failure will look like an RNG problem, not a listener
problem.

**Fix — tighten AD-3.** Add to the Rule: *"Signal-sourced sections of the Observation (`log`) are
captured by a Harness-owned capture object whose registration is re-asserted at every scene
creation through the seam hook of ADR-0008 row 3, and whose buffer survives scene recreation and
level change. The capture object, not the scene, owns the buffer and its cap N. A
headless-vs-embedded equivalence test — the same Run driven headless and under the Overlay,
compared section hash by section hash across at least one level change — ships in E5 and is part
of the fairness suite."* Also add a Rule row citing `…/ui/GameLog.java:47`, because the same
`replace` will bite any future signal-sourced section.

---

## 2. Input-wait detection fires per actor-thread wake-up, and the de-dup rule is vacuous — S1

**Unit A — E1, "hook row 3: the Input-wait notification".** ADR-0013 specifies it exactly: "the
hook … fires from `Hero.ready()` on the actor thread the first time the hero becomes ready …
setting a volatile `waitPending` flag", with the pre-mortem's mitigation "the hook fires only on
the `!ready` branch". The story adds one line to `Hero.ready()` per AD-10 and moves on.

**Unit B — E1, "the driver loop".** ADR-0013: "the UI-role thread consumes it at its next frame
(Overlay) or loop iteration (headless) and confirms the condition of AD-5 … before observing",
then `k++`, reseed, observe. The pre-mortem's second mitigation: "the UI-role thread also checks
that `k`'s Observation hash changed or an Action was executed since the last wait."

**The clash — two errors, both verified in the pinned code.**

First, there is no `!ready` branch. `Hero.act()` calls `ready()` on *every* pass where
`curAction == null` and the hero is not resting (`…/actors/hero/Hero.java:861-869`), and `ready()`
sets `ready = true` unconditionally (`…/actors/hero/Hero.java:935-946`). `Actor.process` re-runs
the hero on every wake-up, and `GameScene.update()` notifies the actor thread up to sixty times a
second. A hook in `Hero.ready()` therefore fires at frame rate for as long as the hero is parked,
not once. Unit A ships a notification that is true roughly 60 times per Input wait; unit B treats
each as a wait. Taken literally that is `k++`, a reseed, an Observation, a Decision and a Run-log
record per frame — and because `RngControl.reseed(salt, k)` pops and pushes the generator, the
game's random stream is reset sixty times a second while the human reads the Panel.

Second, the specified de-dup cannot save it. ADR-0005 puts the Input wait index `k` in `header`,
and `header` is a hashed section; ADR-0013's sequence is `k++` *then* `observe()`. So the
Observation hash **always** differs from the previous one, by construction, and the check "the
Observation hash changed" is a tautology. Remove `k` from the header and the check flips to the
opposite failure: two genuinely consecutive Input waits can be observationally identical (a
`Hero.search` that finds nothing, a `wait` action with no visible actor and no log line), and the
driver would swallow the second and hang waiting for a wait that has already happened.

**Fix — tighten AD-5 and AD-2.** AD-5 gains: *"An Input wait is an **edge**, not a predicate: the
notification hook fires only on the `false → true` transition of `Hero.ready`, guarded by a
Harness-owned latch that is cleared when the UI-role thread executes an Action or the hero's
`ready` flag goes false. `Hero.act` calls `ready()` on every actor-thread wake-up
(`…/actors/hero/Hero.java:861-869`), so a level-triggered notification is a defect; a test drives a
parked hero for 120 frames and asserts exactly one notification."* AD-2 gains: *"`k` is not a field
of the Observation. It is carried by the Run-log record, the Decision tag and the Snapshot. The
Observation is a function of the world alone, so two waits with the same world have the same hash
— which is what makes the wait-edge test, the differential test and the restore-and-replay test
meaningful."* (This also removes the AD-2/ADR-0006 disagreement, where ADR-0006's "Seed and turn"
row says "the Brain counts Input waits itself" while ADR-0005 hands it `k` in the header.)

---

## 3. `Belief` crosses a module edge that does not exist, and carries floats into a chained hash — S1

**Unit A — E1, "the `api` value types".** The spine's Value row lists "`Observation`, `Action`,
`Decision`, `Belief` interfaces". AD-7: "state lives only in the returned `Belief`, an `api` value
whose hash is in the Run log". The story declares `Belief` as a marker interface in `api` — the
only thing it *can* do in E1, because no Belief content exists yet — and `ObservationCodec` encodes
Observations only, per AD-2's "the canonical binary codec in `api` is the only encoder".

**Unit B — E4, "FR-29 Beliefs".** "The Brain maintains Beliefs updated from every Observation:
candidate identities **with probabilities** for each unidentified item (weighted from Codex spawn
weights and identification history), floor facts, chapter counters, and memory of monsters seen
and lost." The story implements `BeliefImpl` as a record in `org.shatterfish.brain`, which is
where the Brain's state belongs (AD-1: `brain` → `api` only; nothing says Belief content lives in
`api`).

**The clash — two independent breaks.**

*Layering.* ADR-0011 requires every `wait` record to carry `belief` (SHA-256 of the serialized
Belief), and ADR-0013 assigns the Run-log write to the UI-role thread, which in the headless driver
is `harness`. But `harness` depends on `core` and `api` only (ADR-0003's edge list); it cannot
reference a `brain` type. So `harness` holds an object it cannot serialize, and `api` holds a codec
that does not know the shape. `rig` can see both, but `rig` is not the writer. The Overlay can see
both, but the Overlay is E5 and the Run log ships in E1/E3. Three honest resolutions exist —
`Belief` gets an `encode(): byte[]` on the interface; the codec learns reflection over `brain`
records; the log writer moves to `rig` and `overlay` — and each one changes a different AD.

*Floats.* The Conventions table says "no floats in hashed data (integer pairs)" and ADR-0005
repeats it. The `belief` hash is inside ADR-0011's chained subset. FR-29's probabilities are the
canonical float. Unit B writes `Map<Label, Float>`; the hash is then platform- and
JVM-formatting-dependent exactly where the chain must be canonical, and the E4 story has no reason
to know that, because the no-floats rule lives in an ADR about the *Observation*.

**Fix — tighten AD-7.** Add: *"`Belief` is an `api` type, not an interface implemented elsewhere:
it is a record tree in `org.shatterfish.api` encoded by the same canonical codec as the Observation
and under the same rules — no floats (probabilities are integer numerators over a stated
denominator, denominator in the codec), canonical list order, a schema version in its header. The
Brain may hold private derived state inside a single decision call but returns only the `api`
value. `BeliefCodec.hash(Belief)` in `api` is the only Belief hash and is what the Run log records.
Belief's schema version is a Run-log header field and a Replay-refusal condition."* Add to the E1
`api` story's acceptance criteria that the Belief record tree is declared in E1 with at least the
FR-29 sections, so E4 populates a shape rather than inventing one.

---

## 4. Both Brains of a pair produce the same Run id and overwrite one log — S1

**Unit A — E3, "the parallel runner".** FR-19 and ADR-0011: one gzip JSONL file per Run named
`<run-id>.jsonl.gz` under `--out`, where the Conventions table fixes
`run-id = <tag>-<class>-<challenges>-<seedcode>-<salt>`. Runs go to `--parallel` processes; each
writes its file and appends a line to `runs.jsonl`.

**Unit B — E3, "Registration and pairing".** ADR-0012: "for each seed `s` in the Seed set, **both
Brains run the same (seed, salt) pair**"; a Registration commits "the salts (one per seed, drawn
once and committed with the Registration)". Common random numbers is the entire point of the
Per-pair statistic.

**The clash.** The Run id contains no Brain. Pairing guarantees that Brain A's Run and Brain B's
Run on seed `s` share tag, class, challenges, seedcode **and** salt — so they share a filename, and
in a `--parallel` runner they are very likely open at the same time. One log is destroyed, or the
two are interleaved into one corrupt gzip stream, and `runs.jsonl` gets two rows pointing at one
path. A Results page then "links to the Run logs" (FR-25) and links the same file twice; a skeptic
recomputing the chain (AD-11) verifies one Brain's Run and believes it verified both. Nothing in
the spec detects this: the chain of the surviving file is internally consistent, and Replay of it
succeeds.

**A second collision in the same pair of units.** FR-20 makes a Seed set a list of *(seed, hero
class, challenge flags)* triples, while ADR-0012 draws "one salt per **seed**". A Seed set that
contains seed `s` as a Warrior triple and as a Mage triple gives both entries the same salt, and
`PairScore` — which keys on `(seed, salt)` — merges two different comparisons into one pair.

**Fix — tighten the Identifiers convention and AD-11.** *"Run id =
`<tag>-<class>-<challenges>-<seedcode>-<salt>-<brain>` where `<brain>` is the Brain's short name
plus the first 8 hex of its config hash; a Run id is unique within a Rig invocation and the runner
fails fast on a collision rather than opening an existing path. Salts are drawn per **Seed-set
entry** (seed, class, challenges), not per seed; the pair key is the Seed-set entry index, and
`PairScore` refuses a Seed set in which two entries share a key."*

---

## 5. The Codex version is in no tuple, no log header and no Registration — S1

**Unit A — E2, "Codex generation".** FR-14/FR-15: one build task regenerates `codex/<tag>/*.json`;
CI fails on drift. The Codex is keyed by the upstream tag and nothing else — correctly, since it is
derived from the tag. Combat behavior enters as *measured* tables produced by running the engine's
own methods over a parameter grid.

**Unit B — E3, "the Run-log header and the Registration".** ADR-0011's header carries `tag`,
`commit` (Shatterfish), `brain` (name, commit, config hash), `profile`, `obsv`. AD-11's
Registration list carries the Hypothesis id, bounds, Seed set version, salts, "both Brains'
commits", budget, machine class.

**The clash.** AD-1 says the Codex "reaches the Brain as JSON data loaded by the caller through
`api` types". So the Brain's behavior is a function of (Brain commit, config, **Codex bytes**), and
the Codex bytes are not identified anywhere. Three ways they move without any of the recorded
identifiers changing: a measured combat table regenerated on a different machine or JDK (FR-14 says
the generator *runs the engine's methods*, so the table is a measurement, not a pure function of
source); a `codex/` regeneration merged in a PR that does not touch `brain`; a tag bump, which does
change `tag` — but the Registration's "both Brains' commits" would be unchanged, so the two
Results pages look comparable and are not.

`brain`'s config hash could cover it, but nothing says it does, and the natural E4 reading of
"config hash" is the Evaluation weights of FR-33. Meanwhile ADR-0011's `v`/`obsv`/`profile`
refusal conditions on Replay do not include the Codex, so a Replay across a Codex change silently
compares different Brains and reports a determinism failure at a random wait.

**Fix — tighten AD-11 and AD-1.** AD-11 gains `codex` to the Registration list and to the Run-log
header: *"`codex`: the SHA-256 over `codex/<tag>/` in canonical file order, recorded in the Run-log
header, fixed by every Registration, and printed on every Results page. A Replay refuses a log
whose `codex` differs. Any Codex regeneration that changes the digest requires a new Registration,
exactly as an Observation schema bump does."* AD-1 gains: *"the Codex digest is part of the Brain's
identity; a Brain is (module commit, config hash, Codex digest)."* And FR-14's measured tables need
a determinism clause of their own — measured on a stated machine, committed, never regenerated
opportunistically — or the digest changes on every developer's laptop.
---

## 6. Snapshot-per-Input-wait takes game monitors, mutates the logged turn, and shares the save slot — S2

**Unit A — E5, "Take over and Pause need a snapshot".** ADR-0009: "in the Overlay one snapshot per
Input wait lives in memory for Take over and the v2 scrubber", and ADR-0013's per-wait sequence
places `snapshot` on the UI-role thread — the render thread — between `observe()` and `submit`.
ADR-0009's mechanism is "the game's own bundles: `Dungeon.saveGame` and `saveLevel` written to byte
arrays".

**Unit B — E1, "thread confinement and the deadlock rule".** ADR-0013: "Shatterfish code never
takes the scene monitor or any game object's monitor; the only cross-thread primitives are volatile
flags, an immutable Observation handed to the worker, and a future polled without blocking."
FR-12's test asserts thread ownership at the ports.

**The clash — three ways.**

*Monitors.* `Dungeon.saveAll()` calls `Actor.fixTime()` (`…/Dungeon.java:706-709`), and `fixTime` is
`public static synchronized` (`…/actors/Actor.java:170-192`) — it takes the `Actor.class` monitor.
`Actor.process()` takes the same monitor to scan `all` (`…/actors/Actor.java:255-265`) and,
separately, waits on a `CharSprite` monitor (`…/actors/Actor.java:274-286`) which the render thread
notifies. Unit A therefore has the render thread take a game class monitor sixty times a second
against a thread that waits on a monitor the render thread must notify: precisely the 2020 shape
AD-8 exists to prevent. Unit B's rule forbids it in words and its test — a thread assertion at the
ports — does not see it, because `SnapshotStore.take()` is not a port.

*The logged turn.* AD-5 says the Run log records the turn as `Statistics.duration + Actor.now()`.
That sum is invariant under `fixTime` **only** on the branch where the hero is in `all` and the
level is not a `VaultLevel`: `fixTime` does `Statistics.duration += min` under that guard but
`now -= min` unconditionally (`…/actors/Actor.java:180-190`; `…/levels/VaultLevel.java` exists at the
tag). So on a Vault level, or in any window where the hero is not registered, an Overlay Run that
snapshots each wait logs a *different* turn from a headless Run of the same tuple that snapshots
none. `turn` is inside ADR-0011's chained subset, so the Hash chains diverge — the exact quantity
NFR-2's nightly cross-platform job compares.

*The save slot.* ADR-0009's restore "writes the bytes into the Run's Profile slot and calls the
game's `loadGame`/`loadLevel`/`switchLevel`". The game autosaves into that same slot on level
change, and FR-37's save-and-quit writes it too. Three writers, no protocol. Worse, with the E6
swap-in-place rollout host: a crash between "write the scrubbed bundle to the slot" and "restore
the live Run" leaves a **redetermined, fabricated world** in the player's save slot, which the
launcher will happily resume as the real Run. That is a fairness failure reachable by a power cut.

**Fix — tighten AD-8 and AD-9.** AD-8's Rule gains: *"The deadlock rule covers transitively: no
Shatterfish call may enter a `synchronized` game method. `Dungeon.saveAll`/`saveGame` are such
methods (`…/actors/Actor.java:170`), so snapshots are taken **on the game actor thread at the park
point** through the hook of ADR-0008 row 3, or not at all. A static-analysis test lists the game
methods Shatterfish calls and fails on any that is `synchronized`."* AD-9 gains: *"a Snapshot is
taken only when the driver's mode requires it (Take over armed, Pause, or an E6 rollout), never
unconditionally per wait; a snapshot must not change any quantity the Run log records, and a test
asserts `Statistics.duration + Actor.now()` and the Observation hash are unchanged across a
`take()` on a Vault level; the rollout host uses a slot disjoint from the Run's live slot and
clears it on entry, so no scrubbed world is ever resumable."*

---

## 7. The PAUSED input block is overwritten by the game at every Input wait — S2

**Unit A — E5, "PAUSED drops hero input".** ADR-0013: "`PAUSED` … drops hero input (the Panel
installs its own `CellSelector.Listener` that ignores cells while paused, and sets the toolbar and
inventory pane inactive through the accessor row)". EXPERIENCE.md's PAUSED row makes it a
guarantee: "the hero's game input is ignored (the game's own controls do nothing until Take over)",
justified as preventing accidental moves while the human reads a Decision.

**Unit B — E1, "hook row 5: `Hero.ready()`/`Hero.interrupt()` guards for scene statics".** The story
reads the ready path to add its headless guards. It does not change behavior; that is the point.

**The clash.** `Hero.ready()` calls `GameScene.ready()` (`…/actors/hero/Hero.java:945`), and
`GameScene.ready()` begins with `selectCell( defaultCellListener )`
(`…/scenes/GameScene.java:1641-1643`). So the game **reinstalls its own listener at every Input
wait** — which is exactly and only when PAUSED matters. Unit A's listener survives until the next
time the hero becomes ready, i.e. approximately zero Input waits. And `selectCell` is not a plain
assignment: if a non-default listener is installed it first calls `listener.onSelect(null)`
(`…/scenes/GameScene.java:1551-1556`), so the Panel's listener is handed a cancel it never asked
for, at frame rate (see finding 2), on the actor thread.

Both units are faithful. ADR-0013 named a mechanism without reading `GameScene.ready()`; the E1
hook story had no reason to.

**Fix — tighten AD-12.** *"Input suppression in PAUSED is not implemented by owning
`cellSelector.listener`: the game reinstalls `defaultCellListener` at every Input wait
(`…/scenes/GameScene.java:1641-1643`). It is implemented at the one place the Overlay already
controls — `cellSelector.enabled` through the accessor row, re-asserted by the UI-role thread on
the same frame it consumes the wait notification — together with the toolbar and inventory-pane
inactive flags. A scripted test presses a map cell in PAUSED across a level change and asserts the
hero did not move."* Add the citation as a Rule row in `docs/rules/ui.md`.

---

## 8. Nobody owns `hero.next()` — S2

**Unit A — E1, "ActionExecutor: move and interact".** AD-4 says the executor "dispatches exactly
what a click, key or button would"; ADR-0013 spells it out: "`Hero.handle(cell)` then `hero.next()`,
`Item.execute(hero, action)`, `Hero.rest`, `Hero.search`, or a Prompt window's button". The story
writes `hero.handle(cell); hero.next();`.

**Unit B — E5, "Prompt answering and ability Actions".** AD-5 makes an open Prompt an Input wait of
its own; ADR-0006 lists the Prompt kinds; the executor answers by pressing the window's button, and
per AD-4 that is "exactly what a … button would" do.

**The clash.** The game's own listener is *conditional*:
`if (Dungeon.hero.handle( cell )) { Dungeon.hero.next(); }` (`…/scenes/GameScene.java:1750-1756`).
Unit A's unconditional `next()` wakes the actor thread with `curAction == null` whenever `handle`
returns false, so the hero re-runs `ready()` and the driver sees a new Input wait in which nothing
happened and no game time passed — an infinite loop at the first invalid cell, and a stream of
`wait` records with identical worlds (which finding 2's de-dup would then swallow, hanging the
driver instead).

Symmetrically, unit B must **not** call `next()`: dozens of game paths call it themselves after
their own button — `…/actors/buffs/Combo.java:461`, `…/actors/buffs/MonkEnergy.java:388,399,429,515,665`,
`…/actors/buffs/Preparation.java:282,327`, `…/actors/hero/abilities/duelist/Challenge.java:188`,
`…/actors/hero/abilities/duelist/Feint.java:114`, and the ability and spell classes generally. A
uniform "then `hero.next()`" in the executor double-wakes the actor thread there, stepping the
world twice inside one Input wait — an intermittent desync that will look like an RNG bug and will
only appear for the classes whose abilities the Brain learns to use, i.e. in E7, long after E1
shipped green.

**Fix — tighten AD-4.** *"The wake is part of the Action's dispatch contract, not a suffix: every
Action kind declares whether the executor wakes the actor thread, and the executor's move kind
copies the game's own guard — `if (hero.handle(cell)) hero.next();`
(`…/scenes/GameScene.java:1750-1756`). Action kinds whose dispatch path wakes the hero itself
(window buttons, abilities, spells) declare `wakes = false`. A test asserts that after any
executed Action `Actor.now()` advanced or the Action was rejected, and that no Action produces two
wake-ups."*

---

## 9. `k` has two owners, and rollouts have no counter of their own — S2

**Unit A — E1, "the driver loop and `RngControl`".** AD-5 makes `k` "the primary key across all of
them" and AD-6 reseeds from `mix(salt, k)` at every Input wait. The driver owns a monotone counter,
incremented per wait, kept across floors (ADR-0013).

**Unit B — E6, "the swap-in-place rollout host" (interfaces reserved in E1).** ADR-0009: the
scrubber sets "`salt' = sample.seed`, `k` unchanged", the host loads the scrubbed copy, rolls out
two to four hero turns, then restores the live Run. AD-9 requires the rollout to run in `harness`
behind `Redeterminer`.

**The clash.** A rollout's turns are Input waits by AD-5's own definition — the hero is ready and no
window is open. So either the driver's `k` advances during a rollout, in which case the live Run's
`k` is corrupted and the Run log's key sequence has holes that Replay cannot reproduce; or `k` does
not advance, in which case `RngControl.reseed(salt', k)` pushes the *same* seed at every rollout
turn and each simulated turn replays the identical random stream. The second is the reading
ADR-0009 actually states ("`k` unchanged"), and it silently destroys the rollout's value: the
determinizations differ but the stochastic outcomes within each rollout do not — which is exactly
the quantity ADR-0010's leaf-correlation measurement is supposed to estimate, so the deferred
search decision would be made on a corrupted number.

A smaller version of the same gap is already in E1. The Overlay snapshot is taken *after* `k++`,
reseed and `observe()` (ADR-0013's sequence). On restore, has wait `k` been consumed or not? The
restore-and-replay test ("restore at wait `k`, replay the remaining Actions, expect the original
hashes") reads naturally both ways, and the off-by-one is invisible until a rollout or a Take over
lands one wait early or late.

**Fix — tighten AD-5 and AD-9.** AD-5: *"`k` is the live Run's counter and is owned solely by the
driver. A rollout runs under a **rollout clock** `(k0, j)` — the wait it branched from and a
rollout-local step — and reseeds from `mix(sample.seed, j)`. Rollout waits produce no Run-log
record, no Decision log line and no `k` increment."* AD-9: *"A `Snapshot` is defined as the state
**before** the Observation of wait `k`; restore leaves the driver with `k` about to be re-detected
as `k`, and the restore-and-replay test asserts that the first Observation after a restore has the
same hash as the original wait `k`."*

---

## 10. AD-5 says one Run-log record per Input wait; ADR-0011 defines five kinds keyed by `k` — S2

**Unit A — E3, "ReplayDriver".** ADR-0011's Replay reads the header and then, "for each `wait`
apply `action`". AD-5's promise — "exactly one Observation, one Decision, one Action, one Run-log
record and one RNG reseed happen per Input wait; the wait index `k` is the primary key across all
of them" — invites the obvious implementation: index the records by `k` into a map, iterate `k` in
order.

**Unit B — E5, "Overlay logging".** ADR-0011 gives the Overlay `prompt`, `mode` and `unsupported`
records, all carrying `k`. A Prompt wait produces a `wait` record (it is an Input wait, AD-5) *and*
a `prompt` record whose payload is "the option chosen (an Action of kind `answer`)" — the same
Action, twice, in two records with one key. A mode change adds a third.

**The clash.** `k` is not a primary key; it is at best a foreign key. Unit A's map either throws on
a duplicate key or keeps the last write, silently dropping the Prompt answer or the `wait`. And
because `mode` records are chained, *when* the Overlay writes one is chain-visible: EXPERIENCE.md
says "Mode and speed-mode changes take effect at the next Input wait", so a press during wait `k`
can honestly be logged as `k` (when it happened) or `k+1` (when it took effect). Two implementations
produce two different Hash chains for identical user behavior, and AD-11 publishes that chain as
the artifact a skeptic verifies.

**Fix — tighten AD-5 and ADR-0011.** AD-5: *"Per Input wait there is exactly one Observation, one
Decision, one Action and one RNG reseed, and exactly one `wait` record. Other record kinds
(`prompt`, `mode`, `unsupported`, `boundary`) are annotations on a wait, not waits: the primary key
of the Run log is the record's ordinal in the file; `k` is a foreign key and is not unique. A
`wait` record's `action` is authoritative; a `prompt` record carries the Prompt kind and options
for human review and never repeats the Action. A `mode` record carries both `k_pressed` and
`k_effective`."*

---

## 11. Save-and-quit and resume have no record kind, no `k` rule and no chain rule — S2

**Unit A — E5, "launcher, save-and-quit, resume".** FR-37: "Saving and quitting inside a Run
**records the boundary in the Run log**; a resume through the launcher re-attaches in PAUSED."
EXPERIENCE.md's "Save and resume" row repeats it.

**Unit B — E3, "the Run-log format and Replay".** ADR-0011 defines a closed record set —
`header`, `wait`, `prompt`, `mode`, `unsupported`, `end` — one file per Run named by a run-id that
does not include a session, and a Replay that "reads the header, refuses a different `v`, `obsv`,
`tag` or `profile`; … for each `wait` apply `action`".

**The clash.** There is no boundary record kind, so unit A cannot obey FR-37 without inventing one,
and unit B's Replay will not know what to do with it. Then three unowned questions, each with two
honest answers:

- **`k` after resume.** The driver's counter died with the process. Read the last `k` from the
  existing log and continue, or restart at 0? The first makes the Harness a log *reader* — which
  nothing authorizes and which conflicts with the streaming, append-only design; the second breaks
  AD-5's monotone key and produces a file with two `k=0` records.
- **The chain.** `chain_k = SHA-256(chain_{k-1} || …)` requires `chain_{k-1}`, which is in the file
  the resumed process must therefore parse — or the resumed session starts a new chain, and the Run
  has two chains and no single "Hash chain value printed on Results pages".
- **The file.** Same run-id, same filename. Append to a gzip stream written by a dead process, or
  overwrite the Run so far? The same question is asked by a crash mid-Run, which ADR-0011's
  streaming requirement explicitly anticipates ("a crash mid-Run must leave a readable prefix")
  without saying what happens next.

And Replay-across-resume is deferred: FR-37 says "Replay across a resume is verified from E8; open
question 10". A deferral is fine — but E5 must still write *something*, and the Deferred table does
not carry this row, so two E5 stories (launcher, logging) will each guess.

**Fix — extend ADR-0011 and add a Deferred row.** *"Record kind `boundary`: `k`, `reason`
(`save-and-quit`, `crash-recovery`), `chain`. A resumed session opens a new file
`<run-id>.<session>.jsonl.gz` whose `header` carries `resumes` (the previous file's final chain)
and whose `k` continues from the `boundary` record; the Run's Hash chain is the chain of its last
session, and a Results page lists every session file. A Run with more than one session is
`verifiable: false` until E8's cross-resume Replay lands."* Add to the Deferred table: *"Replay
across a resume boundary — revisit E8 per open question 10; until then a resumed Run is marked
unverifiable and may not back a published number."*

---

## 12. Two owners of the Profile, and two destinations for the Run log — S2

**Unit A — E1, "the standard Profile".** AD-6: "every Run starts in a fresh versioned standard
Profile … in its own working directory", and ADR-0007 assigns it: "created by the Harness in the
Run's working directory … the Profile is generated by the game's own `Bundle` writers at Run
start". The Profile version is in the Run tuple and in the log header.

**Unit B — E5, "the launcher".** AD-12: "the launcher owns the Profile and the oracle flag"; FR-37:
"a launcher starts the desktop game with the Overlay attached in a fresh Profile owned by the
launcher".

**The clash.** In the Overlay the Profile cannot be created by the Harness, because the game reads
`SPDSettings` — language, interface size, key bindings, intro flag — during start-up, before any
Shatterfish code beyond the launcher runs. ADR-0007's own reasoning depends on that: "the language
pin matters because every string in the Observation is a `Messages.get` lookup that falls back to
`Locale.getDefault()`". So unit B must write the Profile first, and unit A's Harness code will then
either create a second one, or find one already present and (per its freshness assertion) fail.
Two implementations of "the standard Profile" also means two chances to diverge in a value that is
part of the Run tuple — and a divergence there makes every Overlay Run unreplayable by the Rig,
because ADR-0011's Replay "refuses a different … `profile`".

**And the log destination forks.** ADR-0011: "one file per Run under the Rig's `--out` directory or
the Overlay's Profile directory." The Rig's Replay verification (AD-11) and the death gallery
(FR-26) read `--out`; an Overlay Run's log is somewhere else, under a per-Run Profile the launcher
owns, with no index. Nothing tells the Rig how to find it, and FR-24 promises the Rig can Replay
"a Run log".

**Fix — tighten AD-6 and AD-12.** AD-6: *"`harness` owns one `StandardProfile` writer — the single
implementation of the Profile's contents and version — and exposes
`StandardProfile.materialize(dir)`. The Harness calls it for headless Runs; the launcher calls the
same method before `Game` starts for Overlay Runs. No component writes a Profile any other way, and
a test asserts that a launcher-materialized and a Harness-materialized Profile are byte-identical
for a given version."* AD-12: *"the launcher owns the Profile's **location** and the oracle flag;
its contents are `harness`'s."* AD-11: *"every Run log, whatever wrote it, is registered in the
invocation's or the Profile's `runs.jsonl`; the Rig locates a Run by that index and never by
directory convention."*

---

## 13. The `actions` section is circular and has two sources of truth — S2

**Unit A — E1, "the Observer".** ADR-0006's last rows: item state includes `actions(hero)` read
from the game per item, and the "Valid Actions" row says the set is "computed by
`ActionExecutor.validActions(observation)` from the Observation alone, never from game state, and
then included in the Observation".

**Unit B — E1, "the ActionExecutor".** AD-4: "it validates against the Observation's `actions`
section before touching state and rejects with a reason; the valid-Action set is computed from the
Observation alone."

**The clash — two shapes.**

*Circularity.* `validActions` takes an Observation, but the Observation is not complete until
`actions` is filled. Unit A must therefore build a partial Observation, call unit B, and rebuild —
so `ObservationCodec` encodes a record tree that existed in two states, and ADR-0005's guarantee
"`a.equals(b)` iff `a.hash().equals(b.hash())` over a corpus" holds only if `actions` is a pure,
total function of the other sections. Nothing says it is, nothing tests it, and the codec's
reflection test ("fails when a record component is not encoded") will not notice.

*Two truths.* The `inventory` section carries each item's `actions()` **read from the game**, while
the `actions` section carries the executor's set **derived from the Observation**. They can
disagree in both directions — an item whose `actions()` includes `THROW` at a moment when no cell
is targetable, an executor that admits an Action the item's own list does not. AD-4 says the
executor validates against `actions`; ADR-0006 says the Brain may read `inventory[i].actions`.
FR-4's completeness test ("every valid Action is accepted and every invalid one rejected") does not
say which list defines "valid", so it can be written to pass against either.

**Fix — tighten AD-2 and AD-4.** AD-2: *"`actions` is a **derived** section: `ObservationCodec`
hashes it like any other, but it is computed by `ActionExecutor.validActions` from the
*undecorated* Observation (all sections except `actions`), which is a declared `api` type
(`WorldView`). A test asserts `validActions` is total and pure over the corpus, and that
`equals`/`hash` agreement survives the two-phase construction."* AD-4: *"the Observation's `actions`
section is the sole definition of validity; per-item `actions()` strings in `inventory` are display
data (they name what the item's window would offer) and are never the executor's input. FR-4's
completeness test is stated against the `actions` section."*

---

## 14. `Decision.wait` is not always a valid Action, so the error path deadlocks — S2

**Unit A — E4, "Brain error handling".** The Conventions/Errors row: "The Brain throwing produces
`Decision.wait` with the exception class in the Run log and the Panel …; the game never crashes
because of the Brain." FR-27 repeats it.

**Unit B — E1, "executor validation".** AD-4: the executor "validates against the Observation's
`actions` section before touching state and rejects with a reason"; an invalid Action is "rejected
with a `Reason` value, never an exception" (Conventions/Errors).

**The clash.** At a Prompt Input wait the valid-Action set is the Prompt's options — a subclass
choice, a talent, a shop confirmation. `wait` is not among them, and cannot be: the game offers no
way to decline a subclass dialog. So the Brain's error path produces an Action the executor must
reject, the executor rejects it, and no Action is executed — therefore no next Input wait ever
occurs. Headless the Run hangs until a timeout that nobody has specified; in the Overlay the game
sits at a modal window forever. EXPERIENCE.md even has a state for the symptom — "`no valid action;
waiting`" — without a mechanism that gets out of it.

The same hole swallows `Decision.wait` in ordinary play whenever `wait` is not in `actions`; the
Brain-error case is just the one the Conventions guarantee will happen.

**Fix — tighten AD-4 and the Errors convention.** *"`wait` is an always-valid Action: the
Observation's `actions` section contains `wait` at every non-Prompt Input wait, and at a Prompt
wait the executor resolves a `wait` Decision by taking the Prompt's **default option** (the one the
game's own `WndOptions` would apply on cancel where one exists, otherwise option 0), logging
`decision.flags += forced-prompt-default`. A driver that goes N consecutive waits with no executed
Action ends the Run with `end.cause = stuck`; N is a driver constant recorded in the log header."*
Add the corresponding EXPERIENCE.md transition out of "no valid action".
---

## 15. The classloader-isolation deferral cannot be reached from the units built before it — S3

**Deferred row:** "Classloader isolation (several Runs per JVM, and as the rollout host of
ADR-0009) — process per Run is the default; the E1 spike measures. Revisit: E1 spike report."

**Unit A — E1, "the hook registry".** ADR-0008: `Hooks.java` holds "one nullable **static** listener
field per hook point", `public static volatile`, in `core`.

**Unit B — E1, "the isolation spike".** ADR-0009 option 10 sketches the target: "libGDX and natives
in a shared parent loader, game classes per child".

**The clash — the spike's own success condition is decided by units built before it.** Whatever is
in the shared parent is shared by all Runs, and the natives force libGDX into the parent. But the
per-Run state the design depends on is reachable from there: `Gdx.files`/`Gdx.app` (the Profile
path, per Run), `com.watabou.utils.Random`'s static `ArrayDeque` generator stack
(`≈/utils/Random.java:37-45` — one stack, all Runs, `synchronized` but stack-shaped), `SPDSettings`,
and — if `core` were ever placed in the parent for any reason — the static `Hooks` fields, which
would make one driver's listener receive another Run's notifications. The spike does not get to
choose: unit A has already made the hook plumbing static, and libGDX has already made the file
backend static. So the spike will fail, or it will "succeed" with cross-Run bleed that the
determinism test (which AD-6 runs across two JVMs, not two classloaders) cannot see.

Two Runs sharing one `Random` generator stack while each pops and pushes at its own Input waits is
also a silent reproducibility failure: ADR-0007's guard — "the Harness only swaps its own generator
when the stack depth equals what it pushed" — becomes meaningless when two Harnesses share the
stack, and each Run's Observations then depend on the other Run's timing.

**Fix — tighten the Deferred row.** *"Classloader isolation: the spike's exit criterion is not
throughput but **containment** — an enumerated list of the JVM-global statics the design must give
each Run its own copy of (`Gdx.files`, `Gdx.app`, `com.watabou.utils.Random.generators`
(`≈/utils/Random.java:37-45`), `SPDSettings`, `Dungeon.*`, `Actor.*`, `Hooks.*`) with, for each,
which loader it lands in. The spike fails if any of them is shared. Until it reports, no unit may
assume more than one Run per JVM, and `Hooks` listener fields are documented as process-global (one
Run per process, AD-6)."* Add the same list to ADR-0008 as a consequence.

---

## 16. The `InterlevelScene` generation thread has no role in AD-8 — S3

**Unit A — E1, "`RngControl`".** ADR-0007's pre-mortem: "the Harness only swaps its own generator
when the stack depth equals what it pushed, and asserts otherwise; `Level.create` is the only
long-lived push and it **completes inside a level change, never spanning an Input wait**."

**Unit B — E1/E5, "the scene seam and re-attach".** ADR-0013: "level changes are seen as the scene
being destroyed and recreated (`InterlevelScene`), so the driver re-attaches through the scene seam
hook each time".

**The clash.** AD-8 enumerates three threads and says the actor thread "is never touched by
Shatterfish code". There is a fourth: `InterlevelScene` runs generation on its own static `Thread`
(`…/scenes/InterlevelScene.java:108`, `:413-459`), and that thread is where `Level.create` pushes
and pops the seeded per-floor generator on the shared static stack (`≈/utils/Random.java:37-45`).
Unit A's assertion is therefore made from the UI-role thread about a stack another thread is
mutating; "the stack depth equals what it pushed" is a check with no happens-before edge behind it.
And unit B's re-attach fires from that same unassigned thread, so the driver's re-attach — which per
ADR-0013 keeps `k`, re-registers listeners and rebuilds the Panel — runs off both the UI-role and
actor threads, past every thread assertion AD-8 installs at the ports. Finding 1's log-listener
re-registration would land there too.

**Fix — tighten AD-8.** Add a fourth row to the thread table: *"`InterlevelScene` generation thread
(upstream): runs `Level.create` and the generator pushes it needs. Shatterfish code never runs on
it; the scene seam hook only sets a volatile `sceneChanged` flag that the UI-role thread consumes
at its next iteration, and re-attach happens there. `RngControl` asserts and swaps the generator
stack only while `sceneChanged` is unset and the game is not switching scenes
(`Game.switchingScene()`)."*

---

## 17. `--oracle` exists on a runner that must always refuse it; "ranked" is undefined — S3

**Unit A — E3, "the Rig CLI".** The Conventions/Config row lists the Rig CLI flags as
`--brain --baseline --seeds --parallel --out --oracle`. A flag exists to be used; the honest story
implements Oracle Runs for debugging a Brain over a Seed set.

**Unit B — E3, "the refusal".** AD-11: "the runner refuses `holdout` for development **and any
Oracle Run**"; ADR-0012: "the Rig refuses a Registration whose Seed set is `holdout` and any Run
with `oracle` true"; ADR-0006: "the Rig's runner refuses it".

**The clash.** Unit B refuses categorically; the FR the ADs bind is narrower — FR-11 says Oracle
"cannot be enabled in a **ranked** Rig Run" and names label production for E9 as a legitimate use,
which needs a runner. So the CLI has a flag whose only correct behavior, per the ADs, is to error.
Meanwhile "ranked" is used by FR-11 and never defined anywhere in the spine: a comparison under a
Registration, a `standard`/`holdout` run, anything that produces a Results page? Two engineers will
draw the line differently, and the one who draws it loosely ships an Oracle Run that lands on a
Results page with `oracle: false` inherited from a default.

**Fix — tighten AD-11.** *"A Rig Run is **ranked** iff it runs under a Registration or writes to a
Results page. The runner refuses `--oracle` on any ranked Run and on any `holdout` Seed set, and
refuses a Registration referencing a Seed set used by an Oracle Run. Unranked Oracle Runs are
permitted only with `--out` inside a directory whose name begins `oracle-`, write `oracle: true` in
every log header, and are excluded from `runs.jsonl` aggregation. E9 label Runs use seeds disjoint
from every committed Seed set (FR-11) and the runner enforces the disjointness."*

---

## 18. `BeliefSample` is shaped in E1, produced by nobody, and lets the Brain choose a salt — S3

**Deferred row:** "Redetermination scrubber key list — follows `Dungeon.saveGame` at the tag …
revisit E6 per ADR-0009."

**Unit A — E1, "reserve the redetermination interfaces".** ADR-0009: "`api` (E1): `Snapshot` …,
`BeliefSample` (label-to-kind assignment, remembered mobs with positions, container draws, `seed`),
and the `Redeterminer` interface."

**Unit B — E4, "FR-29 Beliefs".** Beliefs are candidate identities with probabilities, floor facts,
chapter counters, and memory of monsters seen and lost.

**The clash — three, all of them divergence before the deferral's revisit point.**

*Shape.* E1 fixes the shape of a *sample of a Belief* before E4 fixes the Belief. The E1 shape
carries exactly what the E6 scrubber consumes and omits what E4 will hold (floor facts, chapter
counters), so E4 will either bend Beliefs to fit an interface written for a scrubber, or E6 will
find `BeliefSample` cannot express the sample it needs and change an `api` type after E5 has
shipped logs against it.

*Ownership.* AD-9 says a Snapshot is "produced by `harness` from a Belief sample". Who *draws* the
sample? Only the Brain holds the Belief, but the Brain is a pure function with no channel to the
Harness other than a `Decision`; `Decision` has no sample field (ADR-0011's `decision` payload is
goal, chosen, alternatives, flags, policy). So the sample is drawn by `harness` from a `Belief` it
cannot see (finding 3), or by `brain` and returned through a field nobody has declared.

*Salt.* `BeliefSample.seed` is the rollout's random stream. If the Brain draws it, the Brain is
choosing an RNG stream for engine code — the one thing AD-6 keeps away from it — and the value
lands in an `api` record that could be logged. AD-2 says "neither the seed, the salt … is a field
of the Observation", but says nothing about a Belief-adjacent `api` value flowing the other way.

**Fix — tighten AD-9 and the Deferred table.** AD-9: *"`BeliefSample` is produced by `brain` from
the generator the caller seeded with `mix(salt, k)` and returned as a field of `Decision`
(`sample`, null outside search); it never contains a seed or salt — the rollout's stream is
`mix(salt, k, rolloutIndex)`, derived by `harness`, which the Brain never sees. `BeliefSample`'s
shape is declared in E1 as an open, versioned record whose sections mirror the `Belief` record
tree, so E4 extends `Belief` and `BeliefSample` in one story."* Deferred table: *"add a revisit
point at the **start** of E4 — 'confirm `Belief`/`BeliefSample` shapes against FR-29 before the
first Belief story' — so the deferral cannot be discovered after E5 has logged Belief hashes."*

---

## Two further readings that will diverge, below the eighteen

- **AD-2's "no oracle data is a field of the Observation" vs ADR-0005's `header.oracle`.** A boolean
  saying an Oracle exists is not oracle data, but it *is* a field the Brain can read, and a Brain
  that behaves differently when `header.oracle` is true is a fairness hole reachable without
  reading a single hidden value (it can, for instance, learn that Oracle Runs are the ones a human
  is watching, or that they are the E9 label Runs). Proposed: *"`header.oracle` is excluded from
  what the Brain receives — it is a Run-log and hashing field only; `Observer` returns the
  Observation, `OracleObserver` returns `(Observation, OracleView)`, and the Brain's parameter type
  never carries the flag."*
- **Duplicate `depth`/`branch` in `Observation.header` and in ADR-0011's `wait` record.** Two
  writers, two sources; during a level change they can disagree, and the log's copy is chained.
  Proposed: *"every field of a `wait` record that also exists in the Observation is copied from the
  Observation, never re-read from game state; a test asserts equality."*

## What I could not break

- **AD-1.** Three independent enforcements (declared edges, resolution-time check, ArchUnit) plus
  the `java.io`/`nio`/`net`/`reflect` ban leave no pair of units that can smuggle game state into
  `brain`. The Codex-as-data channel is the only crack and it is a *versioning* problem
  (finding 5), not a parity one.
- **AD-10.** The marker-vs-table counting test and the "sites outside `Hooks.java`" rule close the
  obvious games. The budget is at risk — findings 1, 7 and 8 each argue for capability the current
  eight rows do not provide — but no two units can disagree about what a hook *is*.
- **ADR-0006's per-row leak tests.** The table is the strongest artifact in the set: each row names
  its citation, its rule and its test, and the rows compose. Every parity failure I found is in the
  *plumbing* around the Observer (findings 1, 2, 13), never in what it reads.

## Recommended disposition

Findings 1 through 5 should be resolved in the spine before any E1 story is written: each one ships
green and publishes something wrong. Findings 6 through 14 can be resolved as AD wording in the
same pass, since each is a sentence or two. Findings 15 through 18 belong in the Deferred table
with explicit revisit points, which is the cheapest place to hold them.

Findings 1, 2, 6, 7 and 8 turn on facts in the pinned code that the ADRs assert differently; each
should become a Rule row with its citation (`docs/rules/game-loop.md` for `ready()` and `next()`,
`docs/rules/ui.md` for the cell selector, a new `docs/rules/logging.md` for `GLog`), so the upgrade
procedure (FR-50 step 4) re-verifies them at the next tag rather than rediscovering them.

## Verified citations used

All at the pinned tag; `…/` = `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/`,
`≈/` = `SPD-classes/src/main/java/com/watabou/`.

| Claim | Citation |
|---|---|
| `GameLog` replaces every `GLog.update` listener on construction | `…/ui/GameLog.java:47`; `≈/utils/Signal.java` (`replace` = `removeAll` + `add`) |
| `Hero.act` calls `ready()` on every wake-up with no `curAction` | `…/actors/hero/Hero.java:861-869` |
| `Hero.ready()` sets `ready` unconditionally and calls `GameScene.ready()` | `…/actors/hero/Hero.java:935-946` |
| `GameScene.ready()` reinstalls `defaultCellListener` | `…/scenes/GameScene.java:1641-1643` |
| `selectCell` cancels a displaced non-default listener | `…/scenes/GameScene.java:1551-1556` |
| The game wakes the hero only when `handle` succeeds | `…/scenes/GameScene.java:1750-1756` |
| `Actor.fixTime` is `static synchronized`; `Statistics.duration` guarded, `now` not | `…/actors/Actor.java:170-192` |
| `Actor.process` takes `Actor.class` and waits on sprite monitors | `…/actors/Actor.java:244-322` |
| `saveAll` calls `fixTime` | `…/Dungeon.java:706-709` |
| `VaultLevel` exists at the tag (the `fixTime` exception branch is reachable) | `…/levels/VaultLevel.java` |
| `InterlevelScene` generates on its own static thread | `…/scenes/InterlevelScene.java:108`, `:413-459` |
| The generator stack is one static `ArrayDeque` for the JVM | `≈/utils/Random.java:37-45` |
| `GLog.update` is a public static `Signal<String>` | `…/utils/GLog.java:39` |
