---
story: 1.15
key: 1-15-the-salt-the-mix-function-and-the-profile
title: "The salt, the mix function and the Profile"
epic: 1
issue: 28
type: 'feature'
status: 'done'
created: '2026-09-12'
updated: '2026-09-12'
review_loop_iteration: 1
baseline_commit: 'e71a7de47'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A Run is not a function of what it declares. Story 1.14 measured it: the same seed and
the same chooser give one Run, then a different one, then a third in a fresh process (issue #70). A
thousand Runs are a thousand anecdotes until the tuple determines them, and every number this
program publishes afterwards rests on that.

**Approach:** The two halves ADR-0007 names. The random stream becomes a function of the salt and
the wait index through one mix function with a published vector, pushed at every Input wait; and the
Profile — the settings, badges, journal, rankings and remains a Run inherits — is rebuilt per Run
from a versioned definition rather than loaded once per process. The salt joins the Run's own
record and appears in no Observation.

## Boundaries & Constraints

**Always:** The mix is exactly ADR-0007's: `splitmix64_finalize(salt + k * 0x9E3779B97F4A7C15)`,
with the finalizer written out, and a vector on the methodology page a skeptic can recompute. The
reseed happens at the wait, in ADR-0013's order, after the game's own initialisation has finished
resetting the generator stack. Every claim about the game is a `path:line` at `v4.0.0`.

**Ask First:** Any hook row. ADR-0016 gives this epic five and it has spent five; ADR-0007 reserves
the next for identity order, which is story 1.16's. If the generator stack cannot be driven from
public calls, stop and say so rather than taking that row early.

**Never:** The salt never reaches an Observation, an Action, or anything a Brain can hold — it is
the one number that would let a bot predict the dungeon. No reseeding from wall-clock time, thread
identity, or anything not in the tuple. No Profile written outside the Run's own directory, and no
Run continued against a Profile it does not recognise.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| The vector | The salt and `k` of the published vector | The mix returns the published value, bit for bit | N/A |
| A wait | Any Input wait `k` of a Run with salt `s` | Every draw until the next wait comes from a generator seeded `mix(s, k)` | N/A |
| Two Runs, one tuple | The same tag, class, seed and salt, played the same way | The same Runs, in one process and in two | N/A |
| Two salts | The same seed, different salts | The same dungeon; different rolls | N/A |
| A stale Profile | A Profile directory written by another version | The Run is refused, naming both versions | The refusal is an exception before the Run starts |
| The salt in the open | Any Observation of any Run | The salt is absent from the section tree and from the bytes the codec writes | A leak test fails if it appears |

</frozen-after-approval>

## Code Map

Read at `v4.0.0`; `Hero.java` and `GameScene.java` run about nine lines longer in the working tree
than at the tag, and the numbers below are the tag's.

- `SPD-classes/src/main/java/com/watabou/utils/Random.java:37-73` — the generator stack.
  `resetGenerators()`, `pushGenerator(long)` and `popGenerator()` are public and static; the deque
  itself is private, so the harness can drive the stack and cannot read its depth. A push scrambles
  the seed through MX3 (`:55-65`) before `java.util.Random` sees it, which the vector must account
  for: the vector is of the mix, and the scramble is the game's business after it.
- `core/.../Dungeon.java:254` — `Random.resetGenerators()` inside `init`, with `hero.live()` at
  `:282` and `initHero` at `:286` after it. ADR-0007 asks whether those two draw anything; the
  determinism test is what answers it, and the answer decides whether the first push can sit where
  this story puts it.
- `shatterfish/harness/.../driver/HeadlessDriver.java` — `run(WaitSequence, maxWaits)` already calls
  `sequence.reseed(k)` first at each wait, which is ADR-0013's order and the seam this story fills.
  `newGame` calls `Badges.loadGlobal()` and `Journal.loadGlobal()`, both of which return early when
  their static is already set (`core/.../Badges.java:315-323`), which is the leading candidate for
  issue #70.
- `shatterfish/harness/.../boot/HeadlessBoot.java:118-140` — the settings a Run declares today
  (English, intro off, support nagged, compact interface) and `profile(Path)`, which is where a
  Profile's directory is set. This story is what the comment there has been waiting for.
- `shatterfish/harness/.../agent/RunLoop.java` — the loop that plays a Run; the salt belongs in what
  it reports, beside the seed.
- `shatterfish/harness/src/test/java/org/shatterfish/harness/observer/*LeakTest.java` — the shape a
  leak test takes here.
- `docs/adr/0007-rng-seeding-strategy.md:126-150` — the decision this story implements,
  word for word.

## Tasks & Acceptance

**Execution:**
- [x] `shatterfish/harness/src/main/java/org/shatterfish/harness/rng/Mix.java` — the mix function
  alone, with the finalizer written as ADR-0007 writes it, and nothing else in the class.
- [x] `shatterfish/harness/src/main/java/org/shatterfish/harness/rng/RngControl.java` — owns the
  stack for a Run: at wait `k` it replaces its own generator with one seeded `mix(salt, k)`, and
  says what it did so a test can check the stream rather than the depth.
- [x] `shatterfish/harness/src/main/java/org/shatterfish/harness/boot/Profile.java` — what a Run
  inherits, versioned: the settings, and badges, journal, rankings and remains cleared per Run. A
  directory whose version is not this one is refused.
- [x] `shatterfish/harness/.../driver/HeadlessDriver.java` — a Run carries its salt, builds its
  Profile, and drives `RngControl` at every wait.
- [x] `shatterfish/harness/src/test/java/org/shatterfish/harness/rng/MixTestVectorTest.java` — the
  published vector, and the stream a wait actually draws from.
- [x] `shatterfish/harness/src/test/java/org/shatterfish/harness/rng/SaltLeakTest.java` — the salt
  is in no Observation, in no section, and in none of the bytes the codec writes.
- [x] `shatterfish/harness/src/test/java/org/shatterfish/harness/boot/ProfileTest.java` — a fresh
  Profile per Run, and a Run refused against a version it does not know.
- [x] `docs/methodology.md` plus its `mkdocs.yml` nav entry — the mix, its vector, and what the
  Profile is, so a skeptic can recompute both.
- [x] `docs/adr/0007-rng-seeding-strategy.md` — an amendment recording what the stack
  turned out to allow and what the reseed does at a wait.

**Acceptance Criteria:**
- Given ADR-0007, when `RngControl` is implemented, then the generator every draw comes from at wait
  `k` is seeded `mix(salt, k)`, pushed after the game's own initialisation has reset the stack —
  `MixTestVectorTest.the_stream_at_a_wait_is_the_mix`.
- Given the published vector, when the mix is computed, then it matches bit for bit —
  `MixTestVectorTest.the_published_vector`.
- Given two Runs of one tuple in one process, when both are played the same way, then they are the
  same Run — `ProfileTest.one_tuple_one_run`.
- Given a Profile directory of another version, when a Run is started against it, then it is refused
  naming both versions — `ProfileTest.a_profile_of_another_version_is_refused`.
- Given any Observation of any Run, when it is searched and encoded, then the salt is absent from
  both — `SaltLeakTest.the_salt_is_nowhere_in_an_observation`.

## Spec Change Log

## Design Notes

**Why the stream is checked and not the stack depth.** ADR-0007's pre-mortem asks the harness to
assert that the stack is as deep as it left it. The deque is private and there is no accessor, and
the row that would add one is not this story's to spend. The stronger check needs no hook: at wait
`k` the first draws must be exactly those of a generator seeded `mix(salt, k)`, and a generator the
game pushed and did not pop would change them. The test computes the expected draws itself.

**Why the Profile is rebuilt rather than loaded.** Badges and the journal load once per process and
return early afterwards, so a second Run in one process inherits the first Run's unlocks — which is
one of the things issue #70 is made of. A Profile that is rebuilt per Run is also the only way the
version check means anything.

## Verification

**Commands:**
- `./gradlew build -Pshatterfish.mobile=off` — expected: green.
- `./gradlew :harness:test --tests "org.shatterfish.harness.rng.*"` — expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` — clean.
- A mutation battery over the mix, the reseed and the Profile — expected: every mutation caught, or
  a survivor explained in the code beside the branch it removes.

## Dev notes

As a skeptic,
I want the random stream to be a function of things the Run declares,
So that a Run can be reproduced rather than believed.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v4.0.0`.

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| Given ADR-0007, when `RngControl` and the Profile are implemented, then the base generator is reseeded from the mix of the salt and the wait index at every Input wait, after the game's own initialization has finished resetting the generator stack | **Met**, and more than the first draft understood: the reseed happens at every wait *and* immediately after `Dungeon.init`, because init ends by seeding the base generator from the system (`…/Dungeon.java:254`) and the first floor is built before any wait. `ProfileTest.every_wait_of_a_run_draws_from_its_own_seed` and `.the_stack_is_owned_from_the_start` |
| And the mix function is implemented exactly as ADR-0007 specifies, and `MixTestVectorTest` asserts it against the vector published on the methodology page | **Met.** `Mix.mix` is the SplitMix64 finalizer of `salt + k·γ`; `docs/methodology.md` carries the definition, a six-row vector and the same function in Python. The test holds the code to the table and the table to a second implementation written from the ADR's words |
| And each Run gets a fresh versioned Profile with English text, the intro off, all guide pages read, and no remains, rankings or badges | **Met with one correction to the criterion.** The guide pages are *unread*, not read: a floor scatters the pages the player has not found (`…/levels/RegularLevel.java:561-589`), so "all pages read" would mean a Run inherits a different dungeon than a first-time player's. The Profile empties badges, every document's pages, and the rankings, and declares the settings per Run. `ProfileTest.the_history_is_empty`, `.the_settings_are_the_run_s` |
| And the salt appears in the Run log and in no Observation, asserted by a leak test | **Met.** The salt is in `RunOutcome` and in what the driver prints, and `SaltLeakTest` searches every Observation of a Run — the rendered form and the codec's bytes — for the salt, the seed in force at that wait, and the game's seed, in every shape a 64-bit number takes |
| And a Run started against a different Profile version is refused rather than silently compared | **Met.** `ProfileTest.a_profile_of_another_version_is_refused`, and a stamp naming no version is refused too rather than guessed at |

## What was built

- `shatterfish/harness/.../rng/`: `Mix` (the function alone), `RngControl` (the stack, for one Run),
  `Salt` (where a salt comes from).
- `shatterfish/harness/.../boot/Profile.java`: what a Run inherits, versioned and stamped.
- `HeadlessDriver`: a Run carries a salt, owns the stack from `Dungeon.init`, reseeds as it confirms
  each wait, and releases at the end. There is no way to start a Run without naming a salt.
- `RunLoop` and `RunOutcome`: the loop draws a salt when the caller has none, and the outcome
  records it.
- `docs/methodology.md` and its nav entry; ADR-0007's story 1.15 amendment.

## What the story found

**The reseed belongs at the wait, not in the driver's loop.** `run(WaitSequence, maxWaits)` was the
obvious home, and it would have left every other caller — the agent's loop, a test, a Replay —
drawing from whatever the last Run left behind. It happens inside `stepToInputWait` as a wait is
confirmed, so a Run is a function of its tuple however it is driven.

**The stack has to be owned from `Dungeon.init`, which is what ADR-0007 always said.** The first
draft read it as "at every wait". The game's init ends by replacing the base generator with one
seeded from the system, and the first floor is generated before the first wait, so the floor was the
moment's rather than the tuple's.

**The journal changes the dungeon.** A floor scatters the guide pages the player has not found, and
the game's loaders each return early once a process has called them, so the second Run in a process
inherited the first Run's journal and generated a different floor from the same seed. Clearing it is
not what it looks like either: `Document.restore` adds what a bundle holds and never takes anything
away (`…/journal/Document.java:357-375`), so restoring from an empty bundle changes nothing. Every
page is deleted one by one, through the only public call that puts one back.

**ADR-0007's stack-depth assert cannot be written, and the thing that replaced it was oversold.**
The deque is private upstream with no accessor. `MixTestVectorTest` checks what the stack *gives* —
the draws at a wait, and a sentinel generator underneath proving a Run leaves the stack as deep as
it found it — but neither notices a generator the game pushed and did not pop between two waits,
because the reseed pops one and pushes one whatever is underneath. What settles it is a reading of
the tag, done in the fairness review: every push in `core/` and `SPD-classes/` is matched by a pop in
the same method. ADR-0007's amendment says that, rather than claiming a test does it.

## Decisions taken inside the story

**No default salt, at any layer.** Alternatives: (a) a zero default so a caller need not think
about it; (b) a default drawn at random; (c) no default at all. Chosen (c). The first draft chose
(a) and the fairness review blocked on it, correctly: ADR-0007 rejected a predictable salt in
advance (`:55-62`) because the mix is published, so a Brain's author could compute the coming draws
as pure data — no game code, no Observation, just a counter and fourteen lines of arithmetic. (b)
hides the same hole behind a coin flip; a caller that never records what it drew cannot replay. So
the salt is an argument everywhere, and a runner without one asks `Salt.draw()` and writes down what
it got.

**The published vector is checked from two directions.** The implementation is held to the table and
the table to a second implementation written from the ADR's text, so a change to one has to be a
change to the other. A vector computed by the code it certifies certifies nothing.

**A defect gets written down, not asserted.** A test that asserted two Runs of one tuple diverge
would have made story 1.16 meet a failing test the day it succeeded, which is a pleasing idea; the
battery caught it flaking, because the two Runs sometimes agree. The divergence is in issue #70, in
the test file's own note, in the ADR and on the methodology page instead.

## Evidence

`./gradlew clean build -Pshatterfish.mobile=off`: green, 520 tests across 50 suites.
`mkdocs build --strict`: clean.

The thousand random Warriors of story 1.14 still run, now under the salted stream: `{DEATH=1000}`,
deepest floor 3, 1,426,993 turns and 72,520 waits.

**Mutation battery**, twelve mutations of the mix, the control, the Profile, the driver and the
salt, each applied to a committed clean tree:

| # | Mutation | Caught by | What failed |
|---|---|---|---|
| P1 | the mix ignores the golden gamma | `MixTestVectorTest` | org.opentest4j.AssertionFailedError: the mix and the published vector disagree. One of them is wrong and  |
| P2 | the first shift is arithmetic, not logical | `MixTestVectorTest` | org.opentest4j.AssertionFailedError: the mix and the published vector disagree. One of them is wrong and  |
| P3 | the last shift is off by one | `MixTestVectorTest` | org.opentest4j.AssertionFailedError: the mix and the published vector disagree. One of them is wrong and  |
| P4 | the stack grows by one a wait | `MixTestVectorTest` | org.opentest4j.AssertionFailedError: after a Run releases, the generator underneath is the one being draw |
| P5 | every wait draws from the same seed | `MixTestVectorTest` | org.opentest4j.AssertionFailedError: the draws at wait 1 ==> expected: <[394833, 739452, 29275, 956625, 1 |
| P6 | a wait does not reseed at all | `ProfileTest` | org.opentest4j.AssertionFailedError: the draws at wait 1 are the mix's, not the last wait's ==> expected: |
| P7 | a Profile of another version is played anyway | `ProfileTest` | a directory another version prepared is refused, naming both versions |
| P8 | a Run does not declare its interface | `ProfileTest`, `SaltLeakTest` | java.lang.IllegalStateException: a Run plays on the compact interface, and this process has interface siz |
| P9 | a Run inherits the last Run's badges | `ProfileTest` | org.opentest4j.AssertionFailedError: and the next Run starts with nothing ==> expected: <0> but was: <1> |
| P10 | a Run inherits the last Run's journal | `ProfileTest` | org.opentest4j.AssertionFailedError: and with none of the pages the last Run found ==> expected: <true> b |
| P11 | the stack is not owned until the first wait | `ProfileTest` | org.opentest4j.AssertionFailedError: two Runs of one tuple, before either has been asked for an Action == |
| P12 | a salt drawn for a Run is always the same one | `MixTestVectorTest` | org.opentest4j.AssertionFailedError: sixty-four draws, sixty-four salts ==> expected: <64> but was: <1> |

Every one is caught. Four were not when they were first run — the reseed at a wait, the journal, the
ownership of the stack from init, and the drawn salt — and each gap was a test that did not exist
rather than a mutation that could not be caught.

## The fairness review

The review returned **BLOCK**, on one finding, and it was right.

**A Run could be started without naming a salt, and that meant salt zero.** `HeadlessDriver.start`
had a two-argument overload defaulting to `0L`, and `RunLoop` — the one loop in the tree that drives
a `Decider`, which is the seam a Brain arrives at — called exactly that. ADR-0007 rejected this in
advance and named the attack (`:55-62`): the mix is published on the methodology page, so a salt
anyone can predict lets a Brain's author compute the game's coming draws as pure data. The review
spelled out the path, and it is short: the loop calls `decide` once per confirmed wait and the wait
index increments by one each time, so a stateful decider knows `k` without ambiguity; fourteen lines
of arithmetic and the game's own scramble give it the exact draw sequence for the turn it is about
to commit to — whether the attack hits, what the chest holds. No game import, no Observation, and
`SaltLeakTest` cannot see it, because the leak is a compile-time constant rather than a field.

There is no salt-free way to start a Run now. The loop draws one when the caller has none, the
outcome records it, and every test declares the salt it runs under.

The should-fixes, each taken:

- **The Profile's comment contradicted the lines it cited.** It claimed `Badges.reset()` reloads the
  global set from the directory; `Badges.loadGlobal` returns immediately when the set is already
  there. Chasing that honestly is what turned up the journal, which does change the dungeon.
- **`the_history_is_empty` asserted only the local badge set**, which `Badges.reset()` clears by
  definition, so the test could not fail for the reason its comment gave.
- **`one_tuple_one_run` was vacuous.** It read the game's draws at the head of each wait, which is
  the mix's own output by construction; it would have passed for two Runs with different seeds,
  different heroes and different floors. Rewritten to play both Runs with the same Action list and
  compare what a Brain would see, it failed — so the claim was withdrawn rather than the test
  weakened, and the ADR and the methodology page were corrected to match.
- **ADR-0007's amendment oversold what replaced the stack-depth assert.** The review checked the tag
  instead and found every push in `core/` and `SPD-classes/` matched by a pop in the same method,
  which is what the amendment says now.
- **`WaitSequence.reseed`'s javadoc** still described a hook the driver had taken over, in a way that
  would have made a caller who implemented it as documented fight the Run's own control.
- **The leak test searched the wrong wait index** — waits are confirmed from one, so it looked for a
  seed that was never in force and skipped the one that was.
- **The methodology page claimed more than the code does**, about the floors below the first and
  about what the empty history empties.
- **`close()` swallowed every exception from releasing the generator**, which is the one place a
  corrupted stack would surface.

The review also answered a question I could not settle myself: whether the game ever pushes a
generator that spans an Input wait. It read every push site at the tag and found each matched by a
pop in the same method, with `Dungeon.java:243`'s discarded wholesale by the reset at `:254`. So
pop-then-push is correct here by inspection — and `Level.create` has no `try`/`finally`, which is
worth remembering the day level generation throws.

## Deviations

- One acceptance criterion was corrected rather than met as written: "all guide pages read" would
  give a Run a different dungeon than a first-time player's, because a floor scatters the pages the
  player has not found. The Profile leaves them unread and the criterion's table row says so.
- The salt reaches `RunOutcome` and the driver's printed line, which is this epic's stand-in for the
  Run log that story 3.2 owns.

## Known limitations, handed forward

- **A Run is reproducible in its draws and not yet in its outcome** (#70). Two Runs of one tuple
  draw the same numbers at the same waits and still do not always see the same screens.
- **The first Run in a process consumes exactly one draw more than every Run after it**, in the same
  stream, whatever the tuple. One position in one stream is a warm-up rather than entropy, and it is
  the most concrete lead issue #70 has.
- **The catalog and the bestiary cannot be emptied through public calls** — they have `setSeen` and
  no unset — so what a Run adds to them stays for the process. Neither is read by level generation,
  which is what would make it matter.
- **`Level.create` has no `try`/`finally`** upstream, so an exception during level generation would
  orphan a generator on the stack; noticed in the review, recorded here.

## Follow-ups for later stories

- Story 1.16 (#29): identity order and the two-JVM determinism test — the rest of #70.
- Story 3.2 (#..): the Run log with a hash chain, where the salt belongs permanently.
