---
story: 1.16
key: 1-16-identity-order-and-the-two-jvm-determinism-test
title: "Identity order and the two-JVM determinism test"
epic: 1
issue: 29
type: 'feature'
status: 'in-review'
created: '2026-09-12'
updated: '2026-09-12'
review_loop_iteration: 1
baseline_commit: 'a1ab2affd'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A Run is reproducible in what it draws and not in what it becomes. Story 1.15 made the
random stream a function of the tuple and measured what was left: two Runs of one tuple still differ —
one floor-one item lands a cell apart, and the first Run in a process consumes one draw more than
every Run after it. Until the same tuple gives the same Run in another process, every number this
program publishes is an anecdote.

**Approach:** Take the two remaining sources of drift, both named in ADR-0007's own context and one
of them found by measurement in story 1.15. Iteration order over identity hashes becomes insertion
order, and the class-keyed random choice is ordered by name. The guidebook's unseeded generator gets
a seed. Then prove it: the same tuple, played the same way, in two separate JVM processes, compared
hash by hash at every wait.

## Boundaries & Constraints

**Always:** Every upstream edit is a hook — minimal, marked at the site, justified, and a row in
`docs/UPSTREAM.md` in this pull request (non-negotiable #3). ADR-0016 row 6 is this story's to
spend. Every claim about the game is a `path:line` at `v4.0.0`.

**Ask First:** Any hook beyond row 6's scope once widened, or any change to a game rule rather than
to the order in which the game asks a question. Row 6 covers ordering; the guidebook's seed is an
unseeded push rather than an ordering problem, so the row is widened by an ADR-0016 amendment in
this story and the widening is recorded rather than assumed.

**Never:** No change that alters what the game decides, only the order in which it considers
things and the stream it draws from. No sorting by anything that varies between processes — a
class's name is stable, its hash is not. No determinism claimed by a test that runs in one process.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Two processes | One tuple, played by the same Action list, in two JVMs | Every Observation hash matches, wait by wait | A mismatch names the wait and both hashes |
| The actors | A floor with several actors whose turn falls at the same time | The tie is broken the same way in both processes | N/A |
| The class choice | A random choice among classes with weights | The same class, in both processes | N/A |
| The guidebook | Floor one of any seed | The same cell in both processes | N/A |
| A Run that dies early | A tuple whose hero dies before the wait budget | Both processes end at the same wait, the same way | The test compares what both produced and says how far they agreed |
| One process | The same tuple twice in one JVM | The same hashes, which story 1.15 could not promise | N/A |

</frozen-after-approval>

## Code Map

Read at `v4.0.0`. `Hero.java` and `GameScene.java` run about nine lines longer in the working tree
than at the tag; the numbers below are the tag's.

- `core/.../actors/Actor.java:153-154` — `all` and `chars` are `HashSet`s, and `process()` at
  `:251-265` walks `all` to pick the next actor, breaking a tie on `actPriority` and then on
  whatever the iterator says. `:398-402` hand those sets out.
- `core/.../levels/Level.java:183` — `mobs` is a `HashSet`; `:185` — `blobs` is a
  `HashMap<Class<? extends Blob>, Blob>`. The level re-inserts from both on every load, so the order
  is whatever the hashes give this process.
- `SPD-classes/.../utils/Random.java:202-229` — `chances(HashMap<K,Float>)` takes
  `chances.keySet().toArray()` and walks it, so a map keyed by `Class` is walked in identity order;
  `:232-236` — `index(Collection)` and `element(Collection)` walk an iterator.
- `core/.../levels/rooms/standard/entrance/EntranceRoom.java:102-132` —
  `placeEarlyGuidePages` pushes an **unseeded** generator, on purpose ("so meta progression doesn't
  affect levelgen"), and places the floor-one guidebook from it. That is entropy in every Run,
  whatever the tuple: story 1.15 measured it as the one item that moved, and issue #70 carries the
  evidence.
- `shatterfish/harness/.../driver/HeadlessDriver.java` — `start(seed, heroClass, salt)`, the reseed
  at each wait, and `main` — the shape a second process needs.
- `shatterfish/harness/.../boot/Profile.java` — what a Run inherits; unchanged by this story but the
  thing that makes two processes comparable at all.
- `docs/adr/0016-hook-ledger-corrected-by-story-1-1.md:61` — row 6, and its wording.
- `docs/UPSTREAM.md:68-75` — the hooks table this row joins, and the "Verified at tag" column.

## Tasks & Acceptance

**Execution:**
- [x] `core/.../actors/Actor.java`, `core/.../levels/Level.java` — the collections whose iteration
  order decides an outcome become insertion-ordered, each marked `// shatterfish-hook:6`.
- [x] `SPD-classes/.../utils/Random.java` — the class-keyed choice is ordered by name before it is
  walked, marked the same way.
- [x] `core/.../levels/rooms/standard/entrance/EntranceRoom.java` — the guidebook's generator is
  seeded from the floor's own seed rather than left unseeded, marked the same way.
- [x] `docs/UPSTREAM.md` — row 6, with its guard and its reason, and every site listed.
- [x] `docs/adr/0016-hook-ledger-corrected-by-story-1-1.md` — an amendment widening row 6 from
  "identity-hash ordering" to what it actually has to cover, and saying why.
- [x] `shatterfish/harness/src/main/java/org/shatterfish/harness/determinism/RunFingerprint.java` —
  a main that plays a tuple by a fixed Action list and prints one hash a wait, so a second process
  can be asked the same question.
- [x] `shatterfish/harness/src/test/java/org/shatterfish/harness/determinism/DeterminismTwoJvmTest.java`
  — runs that main in two JVMs and compares, wait by wait.

**Acceptance Criteria:**
- Given the hook, when the same tuple is played in two separate JVM processes, then every
  Observation hash matches at every wait —
  `DeterminismTwoJvmTest.the_same_tuple_in_two_processes`.
- Given the same tuple twice in one process, when both are played the same way, then the hashes
  match, which story 1.15 could not promise — `DeterminismTwoJvmTest.the_same_tuple_twice_here`.
- Given the hook, when the ledger is counted, then row 6 is present in `docs/UPSTREAM.md` with every
  site and the markers match — the existing `HooksLedgerTest`.
- Given ADR-0002, when the test is placed, then it runs on the pull-request runner and the
  cross-platform comparison is named as story 3.4's nightly job rather than attempted here.

## Spec Change Log

## Design Notes

**Why insertion order rather than sorting.** The actor sets are walked to break a tie between actors
whose turn falls at the same moment. Sorting them by something intrinsic would be a rule change;
insertion order is the order the game itself put them in, which is a function of the Run so far.

**Why the guidebook belongs in this story.** It is not an ordering problem, and row 6 as worded does
not cover it. But the two-JVM test cannot pass while one item is placed from entropy, and a story
that widened the row quietly would be worse than one that says so: the amendment is part of the
work.

## Verification

**Commands:**
- `./gradlew build -Pshatterfish.mobile=off` — expected: green.
- `./gradlew :harness:test --tests "org.shatterfish.harness.determinism.*"` — expected: green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` — clean.
- A mutation battery over the hook sites and the comparison — expected: every mutation caught, or a
  survivor explained in the code beside the branch it removes.
