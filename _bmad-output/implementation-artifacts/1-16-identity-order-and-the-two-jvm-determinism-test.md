---
story: 1.16
key: 1-16-identity-order-and-the-two-jvm-determinism-test
title: "Identity order and the two-JVM determinism test"
epic: 1
issue: 29
type: 'feature'
status: 'done'
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
- `shatterfish/harness/.../boot/Profile.java` — what a Run inherits, and changed by this story after
  all: version 2 clears the process's preferences per Run, because the game writes its own during
  play and the two-JVM test's first full build found the difference in a quickslot.
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
- [x] Added under review and not in the plan: `IdentityOrderTest`, holding the three ordering
  sites directly because a two-process test on one machine cannot; `Profile` version 2, with
  fresh preferences per Run; the `chances` sort guarded to maps whose order vanilla leaves
  undefined; issue #73 for the two places row 6 does not reach.

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

- **Review, 2026-09-15 (BLOCK, then taken).** The `chances` sort reordered `LinkedHashMap`s whose
  order vanilla defines, so the fork's seed generated a different floor from upstream's; the sort
  now applies only to `Class`-keyed `HashMap`s and the test holds both halves. The battery showed
  the two-JVM test blind to the three ordering sites on one machine; `IdentityOrderTest` holds
  them. `Random.element` and the copies `Actor.all()`/`Actor.chars()` return are not covered and
  are #73. KEEP: the add-only shape, the three-answers comparison, the guidebook seed's offset.

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

## Dev notes

As a skeptic,
I want the same tuple to give the same Run in another process,
So that a Run can be reproduced rather than believed.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v4.0.0`.

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| Given the hook, when the same tuple is played in two separate JVM processes, then every Observation hash matches at every wait | **Met.** `DeterminismTwoJvmTest.the_same_tuple_in_two_processes` runs `RunFingerprint` in this JVM and in two fresh ones and compares all three, thirty waits deep |
| Given the same tuple twice in one process, when both are played the same way, then the hashes match, which story 1.15 could not promise | **Met.** `DeterminismTwoJvmTest.the_same_tuple_twice_here` |
| Given the hook, when the ledger is counted, then row 6 is present in `docs/UPSTREAM.md` with every site and the markers match | **Met.** The table row, four site-index lines and four diff-budget lines; `HooksLedgerTest` and `HooksVanillaTest` both pass |
| Given ADR-0002, when the test is placed, then it runs on the pull-request runner and the cross-platform comparison is named as story 3.4's nightly job rather than attempted here | **Met.** The test is an ordinary JUnit test in `harness`, so the gate runs it; ADR-0007's amendment names the nightly job for Windows against Linux |

## What was built

- Hook row 6, five markers across four upstream files, every site add-only.
- `docs/UPSTREAM.md` row 6, with its why and its guard, the site index and the diff budget.
- `shatterfish/harness/.../determinism/RunFingerprint.java` — one Observation hash per wait for a
  tuple played by the random agent on a fixed seed, as a main a second process can be asked.
- `DeterminismTwoJvmTest` — the same question put to this JVM and two fresh ones.
- `Profile` version 2: the process's preferences cleared per Run, the waterskin's slotting
  declared, and a test that a preference the game flipped in one Run does not reach the next.
- ADR-0016's amendment, widening row 6 and recording its shape; ADR-0007's amendment, recording
  what the row found, explaining story 1.15's one extra draw, and the Profile's new version.

## What the story found

**The last source of drift was not an ordering.** Three of the four sites are what the row was named
for: `Actor.process()` walks `all` to break a tie between actors due at the same moment
(`…/actors/Actor.java:251-265`), and a `HashSet` walks in identity-hash order; `Level.mobs` and
`Level.blobs` are re-inserted on every load and walked wherever the level asks which it reaches
first (`…/levels/Level.java:183`, `:185`); `Random.chances` lays a class-keyed map's slices out in
`keySet().toArray()` order (`SPD-classes/…/utils/Random.java:202-229`). The fourth is
`EntranceRoom.placeEarlyGuidePages`, which pushes an unseeded generator on purpose so that meta
progression cannot shift level generation (`…/levels/rooms/standard/entrance/EntranceRoom.java:102-105`)
and pays for it with a draw from the system. That is why story 1.15 measured the guidebook landing
on a different cell in every Run of one tuple. Row 6 was widened to cover it, and the seed the hook
gives that generator is derived from the floor's own, which keeps upstream's intent — it depends on
the dungeon, not on what any player has read.

**The one extra draw is explained.** Story 1.15 found the first Run in a process consuming exactly
one draw more than every Run after it, in the same stream, whatever the tuple, and called it a
warm-up. It was the guidebook's placement loop, which retries until it finds a cell: an unseeded
stream made the retry count vary, and the first Run's system-seeded generator happened to retry
once more than the others'. What was being counted was retries.

**The test earned its keep on its first full build.** Run alone, the two-JVM test passed; run after
the whole suite, its third assertion failed: two fresh processes agreed with each other and
disagreed with the test worker, which had just played a thousand random Runs. A probe that dirtied
a process with forty Runs and printed the first screen against a fresh process's found one quickslot
empty. The game turns the waterskin's auto-slotting off by itself when a player drags it out of a
slot (`…/ui/QuickSlotButton.java:283`, `:390`), and the hero records the vault's warning
(`…/actors/hero/Hero.java:963`): preferences the game writes during play, which the Profile
inherited from the process. The Profile now clears the process's preferences before declaring its
own, the waterskin's slotting is declared, and the version is 2, because what a Run inherits changed.
Version 1 was never published against.

**A hook that replaces is a hook that deletes.** The row's first shape swapped four vanilla
constructions for their linked forms, and `HooksVanillaTest`'s wrap rule refused it: a hook encloses
vanilla code or relocates it, and never deletes it. The rule was right. Every site is add-only now —
the vanilla initialisers stand and are reassigned on the lines after them; the vanilla unseeded push
stands, is popped, and the seeded push follows — at the cost of an allocation thrown away per
construction and a generator constructed and discarded per floor, none of which draws from the
game's stream. The benefit is the one the rule exists for: an upgrade merge that rewrites a site
keeps vanilla's line and loses ours, which the two-JVM test catches, rather than the reverse, which
nothing would.

## Decisions taken inside the story

**Three answers to one question, not two.** Alternatives: (a) compare two fresh processes; (b)
compare this process with one fresh one; (c) both. Chosen (c): two fresh processes share nothing
with each other but the tuple, which is the strongest statement, and this process must agree with
them too, or the test worker's own state would be a hiding place.

**The fingerprint is played by the random agent, not by pressing one button.** Alternatives: (a) a
Run that only ever waits; (b) the random agent on a fixed seed. Chosen (b): a Run that only waits
explores nothing, meets nothing and changes almost nothing between waits, so most of the sources of
drift never run. The agent on a fixed seed is a fixed function of the Observations it is shown, so
two processes that see the same screens make the same choices, and two that do not diverge at the
first screen that differs — which is what the test wants to see.

**The guidebook's seed carries an offset.** `Level.create` pushes the floor's own seed for its
layout (`…/levels/Level.java:221`); pushing the same seed again would make the guidebook's stream
the layout stream's twin. The offset keeps them distinct. It is a design choice and not a
determinism property, so the battery does not mutate it.

## Evidence

`./gradlew clean build -Pshatterfish.mobile=off`: green, 525 tests across 52 suites.
`mkdocs build --strict`: clean.

**A thousand Runs**, as story 1.14 left them: {DEATH=1000}, deepest floor 2, 1428610 turns and 72055 waits in all.

**Mutation battery**, five mutations, each putting one site of row 6 back to vanilla. The first
run asked only whether `DeterminismTwoJvmTest` notices within thirty waits, and it noticed one of
the five: the guidebook's, which draws from the system. The three ordering sites survived it, and
the reason is worth more than the number. Two JVMs started the same way on one machine give the
same objects the same identity hashes, so a `HashSet` walks alike in both and an ordering reversion
is invisible to any two-process test run here — which is also why the cross-platform comparison is
a separate nightly job. Identity hashes are not a promise, and a different JVM, platform or startup
path gives different ones, so the three sites are held by `IdentityOrderTest` for what they are and
do rather than for what they happen to do on this machine. With that test in the battery every
mutation is caught:

| # | Mutation | Caught by | What failed |
|---|---|---|---|
| Q1 | the actor sets are hash sets again | `IdentityOrderTest` | org.opentest4j.AssertionFailedError: Actor.all is insertion-ordered ==> expected: <true> but was: <f |
| Q2 | the level builds hash collections at create() | `IdentityOrderTest` | org.opentest4j.AssertionFailedError: Level.mobs after create() ==> expected: <true> but was: <false> |
| Q3 | the level builds hash collections at restore() | `IdentityOrderTest` | org.opentest4j.AssertionFailedError: Level.mobs after restore() ==> expected: <true> but was: <false |
| Q4 | the class-keyed choice is walked in hash order again | `IdentityOrderTest` | org.opentest4j.AssertionFailedError: seed 1, class keys ==> expected: <java.lang.String> but was: <j |
| Q5 | the guidebook draws from the system again | `DeterminismTwoJvmTest` | org.opentest4j.AssertionFailedError: two processes that share nothing but the tuple ==> expected: <[ |

## The fairness review

The review returned **BLOCK**, on a finding that was not a leak but a rule change, and it was right.

**The `chances` sort reordered maps whose walk order vanilla defines, so the fork's seed generated a
different floor from upstream's.** The first draft sorted every map's keys by name. The generator's
category maps are `LinkedHashMap`s, filled in `Category.values()` order and drawn from inside the
floor's seeded push (`…/items/Generator.java:622-623`, `:676`, `:695`; `…/levels/RegularLevel.java:388`,
`:688`), so the same seeded draw landed on a different category and every item after it followed.
Type one seed code into upstream and into this fork and floor one's items differed — the one thing
"the seed makes the floor" forbids, and the opposite of what the row's guard, the ADR amendment, the
hook comment and the test all claimed. The sort now applies only where vanilla had no order to keep,
a `HashMap` keyed by `Class`; a `LinkedHashMap` and a map keyed by anything else are left exactly as
vanilla lays them out, and `IdentityOrderTest` holds both halves.

The should-fixes, each taken:

- **Four documents claimed the two-JVM test would catch an ordering reversion**, and the battery
  had shown it cannot on one machine. The ledger row, both ADR amendments, the methodology page and
  the test's own javadoc now say what is shown and by which test: the guidebook site and the
  process's accumulated state by `DeterminismTwoJvmTest`, the three ordering sites by
  `IdentityOrderTest` for what they are, and behaviour across platforms by story 3.4's nightly.
- **Two places row 6 does not reach.** `Random.element(Collection)` is one return expression, so
  the class-keyed collections that go through it still walk by identity; `Actor.all()` and
  `Actor.chars()` return plain `HashSet` copies, so their callers walk by identity too. ADR-0007's
  option 10 promised the `element` half and this story did not deliver it. Both are issue #73 with
  their callers named, and ADR-0007's amendment says so.
- **The methodology page's "what is not yet true" was stale** and contradicted the ADR it sat next
  to; it now says what is shown and what is not.
- **Citations at the tag:** the vault warning is `Hero.java:953-954`, not the working tree's `:963`;
  the quickslot setting goes off at `QuickSlotButton.java:390` and back on at `:283`; the
  reflection list in `docs/UPSTREAM.md` names `Actor.all` and `Actor.chars`; the spec's Code Map no
  longer calls `Profile` unchanged, and its tasks and change log carry what the story grew.
- A stale comment in `ProfileTest` that still called the extra draw a warm-up now names the
  guidebook, and a dead helper is gone.

What the review looked for and did not find is worth the same: no new read of a raw model field, no
path from the fingerprint or the reflection to `api` or a Brain, the guidebook's seed derived from
the game seed that the Brain never holds and colliding with no other derivation, the add-only shape
behaviour-neutral for vanilla (an unseeded `java.util.Random` touches no game generator, and the
vanilla pop never reaches the last-generator branch), and `Profile.prepare` clearing preferences
before the seed is set.

## Deviations

- Four upstream files are touched, under row 6, which ADR-0016 reserves for this story; the row is
  widened by that ADR's amendment rather than by a new row.
- The hook's shape changed during the story, from replace-in-place to add-only, at the wrap rule's
  insistence.

## Known limitations, handed forward

- **Cross-platform reproducibility is not shown here.** The same tuple on Windows and on Linux is
  story 3.4's nightly job (ADR-0002).
- **Thirty waits.** The test compares thirty waits on floor one; a source of drift that first acts
  deeper than that would pass it. The thousand-Run test and the rig's own comparisons are where a
  deeper one would show.

## Follow-ups for later stories

- Story 1.17 (#30): the differential and toggle tests.
- Story 3.4: the nightly cross-platform comparison.
