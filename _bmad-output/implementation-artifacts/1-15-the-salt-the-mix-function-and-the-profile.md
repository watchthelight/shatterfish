---
story: 1.15
key: 1-15-the-salt-the-mix-function-and-the-profile
title: "The salt, the mix function and the Profile"
epic: 1
issue: 28
type: 'feature'
status: 'ready-for-dev'
created: '2026-09-12'
updated: '2026-09-12'
review_loop_iteration: 0
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
- [ ] `shatterfish/harness/src/main/java/org/shatterfish/harness/rng/Mix.java` — the mix function
  alone, with the finalizer written as ADR-0007 writes it, and nothing else in the class.
- [ ] `shatterfish/harness/src/main/java/org/shatterfish/harness/rng/RngControl.java` — owns the
  stack for a Run: at wait `k` it replaces its own generator with one seeded `mix(salt, k)`, and
  says what it did so a test can check the stream rather than the depth.
- [ ] `shatterfish/harness/src/main/java/org/shatterfish/harness/boot/Profile.java` — what a Run
  inherits, versioned: the settings, and badges, journal, rankings and remains cleared per Run. A
  directory whose version is not this one is refused.
- [ ] `shatterfish/harness/.../driver/HeadlessDriver.java` — a Run carries its salt, builds its
  Profile, and drives `RngControl` at every wait.
- [ ] `shatterfish/harness/src/test/java/org/shatterfish/harness/rng/MixTestVectorTest.java` — the
  published vector, and the stream a wait actually draws from.
- [ ] `shatterfish/harness/src/test/java/org/shatterfish/harness/rng/SaltLeakTest.java` — the salt
  is in no Observation, in no section, and in none of the bytes the codec writes.
- [ ] `shatterfish/harness/src/test/java/org/shatterfish/harness/boot/ProfileTest.java` — a fresh
  Profile per Run, and a Run refused against a version it does not know.
- [ ] `docs/methodology.md` plus its `mkdocs.yml` nav entry — the mix, its vector, and what the
  Profile is, so a skeptic can recompute both.
- [ ] `docs/adr/0007-rng-seeding-strategy.md` — an amendment recording what the stack
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
