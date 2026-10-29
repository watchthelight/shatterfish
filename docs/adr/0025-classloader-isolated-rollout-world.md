---
status: proposed
date: 2026-10-28
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0025: A rollout world in its own class loader, built only from a scrubbed start

## Context and problem statement

ADR-0010 deferred engine-rollout search to the measurements of epic 6, and story 6.6 answered no: the swap-in-place host
of ADR-0009 spends 87% of a rollout (about 43.5 ms of an idle host's cycle) saving and restoring the live world, so the
least search each design needs did not fit a 30 ms budget. The way out named then, and never scheduled, was a second copy
of the game in its own class loader that needs no swap. Epic E10 reopens the question because the product owner set the
budget on 2026-10-28 (the amendment to ADR-0010): there is no fixed think budget, the Overlay may use a capped profile, and
strength is published per think-time column. A faster simulator now buys a column, not a pass or fail.

Story 10.23 built the smallest isolated world that could answer three questions: what does a rollout cost there against
swapping, what does a world cost to hold, and can the Brain's side of it be given only a determinized start. This ADR records
the measurements, the fairness design and the go or no-go for determinized fight rollouts (W4 step 2).

Non-negotiables touched: #1 (parity), #4 (in-process Java, no second implementation of the rules), #5 (measured), #6 (the
Overlay's render thread).

## Decision drivers

- The rollout world can be built from a scrubbed start and from nothing else; no argument, field or ambient state hands it
  the live world's hidden state, the Run seed or the declared salt.
- The game's rules are the game's own code, loaded a second time, not rewritten (#4).
- A rollout is cheaper than the swap by enough to move a think-time column, measured on the same snapshots in the same run.
- The Rig (one thread, taking turns) works first; the Overlay's render thread must not be broken by a second copy of the
  game.

## Considered options

1. **Keep the swap-in-place host (ADR-0009, the status quo).** Fair and proven (`SearchLeakTest`). Costs a snapshot and a
   restore of the live world on every rollout. Parity: meets #1; #5 yes; Overlay safe. Cost: the 87% share.
2. **Classloader-isolated second world, pooled per hero class (built here).** A child-first loader holds the game classes, the
   harness and libGDX's `utils.reflect`; the parent keeps libGDX proper and its natives, the JDK, org.json and
   `org.shatterfish.api` (JNI forbids two loaders loading one native library). Parity: the world is reachable only through
   `ScrubbedStart`, see below. #4 yes (the same game code). The Overlay needs a routing Application (see consequences).
3. **A fresh isolated world per rollout.** The purest hygiene (no state carries over), measured and rejected: opening a
   world is 1.7 s when warm (6 s throttled), 0.5 rollouts per second.
4. **A separate JVM process per world, talking over a socket.** Real isolation of every static, native and thread. Rejected by
   non-negotiable #4 (no second bot process over a socket) and by the cost of shipping a scrubbed start across a pipe for
   every rollout.
5. **An abstract tactical model (ADR-0010 option 2, one-ply expectimax over Codex tables), no engine in the search.** Fair by
   construction, no simulator cost. It is the shipped tactical layer; it cannot represent what the game does that the Codex
   tables do not (terrain, doors, status interplay), which is the reason to want rollouts at all. Kept as the baseline every
   rollout design is registered against.
6. **A scene-less or in-memory stepping core (a cheaper load).** Remove the HeadlessScene build and the file round trip so the
   scrubbed copy loads in a few ms. Not built. Attractive because load is now the dominant cost (below), but it edits more of
   the harness and, near the game's own save and scene code, upstream. A follow-up, not this spike.
7. **A learned value function in place of rollouts (E9).** No simulator cost at all, needs a reward signal and training data the
   program does not have yet. Out of scope here.

| Option | #1 parity | #4 Java in-process | #5 measured | #6 Overlay | Speed (ratio to swap) |
|---|---|---|---|---|---|
| 1 swap | holds (tested) | yes | yes | safe | 1.0 |
| 2 pooled isolated | holds by construction, leak tests below | yes | yes | needs routing Application | 1.7 to 2.2 faster |
| 3 fresh per rollout | same as 2 | yes | yes | needs routing | about 40 times slower (1.9 s per rollout) |
| 4 process per world | holds | no | n/a | safe | not built |
| 5 abstract model | holds | yes | yes | safe | no simulator |
| 6 scene-less core | needs its own proof | yes | not yet | unknown | not built |
| 7 learned value | holds | yes | not yet | safe | not built |

## Decision outcome

Chosen: **option 2 as an opt-in Simulator for the Rig, with one world per hero class, kept on a branch of the harness that
the Overlay does not load**, and **go for determinized fight rollouts in the Rig as a registered think-time column, no-go for
the Overlay until a routing Application exists.** The decision stays `proposed` until the product owner has read the table and
the open defect.

### What was measured

The full tables are [the results page](../results/2026-10-28-isolation-spike.md). Two runs of the same tool: one unthrottled
(24 processors) and one while the machine was throttled (8 cores, idle priority, in use). The ratio held (1.8 against
1.7 to 2.2) while the absolute times differ five-fold, so **ratios are the headline** and the milliseconds below are the unthrottled run's,
on this machine, not a promise.

| Per rollout, horizon 3, median | swap in place | isolated, pooled | ratio |
|---|---|---|---|
| snapshot plus restore of the live world | 42 ms | 4 ms (hold, restore) | gone |
| load of the scrubbed copy | 25.5 ms | 28.6 ms | 1.0 (not removed) |
| play | 4.7 ms | 7.2 ms | 0.65 |
| total | 74.4 ms | 40.5 ms | 1.8 times faster (2.2 throttled) |
| a simulated turn | n/a | 1.5 to 2 ms, flat in line length | |
| a world, resident | n/a | about 2 MiB heap, 8 MiB metaspace, 1370 classes | |
| opening a world | n/a | 2.2 s, then 0.27 s for its first rollout | |

What this says: isolation removes the snapshot and the restore and nothing else. The load of a scrubbed copy (write the files,
load the game and floor, build the scene, step to the first wait) is the floor, and it is 70% of a pooled rollout. Story 6.6's
estimate that with no swap the least option 3 search costs about 22 ms assumed a load near 15 ms; measured here the load is
about 29 ms, and the least searches cost 254 ms (option 3), 740 ms (option 5) and 4.6 s (option 4) pooled, against 457, 1351 and
7974 ms for the swap host. Rollouts per process per second: 12.5 swap, 21.8 pooled, 24.3 pooled with one scrub shared by 8.

### Go or no-go for determinized fight rollouts

- **Go for the Rig, as a think-time column.** At about 40 ms a rollout (22 a second per process) a Decision of 250 ms
  affords six rollouts, the least option 3 search, and one of 1 s affords about twenty; fight Decisions are about 400 per Run.
  That is enough to register a width-three, depth-three fight search against option 2 at stated think times under ADR-0012's
  GSPRT. That is W4 step 2. It is a trial of whether sampled fights help, not a
  promise that they do.
- **No-go to make it cheap by more isolation work.** Further gain is in the load, not the swap: rollouts from one
  determinization share a loaded copy (one scrub shared by 8 rollouts saves 10% only, because the load is repeated, not the
  scrub), so the lever is a cheaper load (option 6), which is its own story.
- **No-go for the Overlay for now.** The Overlay's render thread runs libGDX in the same process. Two game copies share one
  set of `Gdx` statics (`app`, `gl`, `graphics`, the posted-runnables queue); the spike makes them take turns by stashing the
  queue around a rollout, which is safe for one thread taking turns (the Rig) and not for a render thread that posts during a
  rollout. The Overlay needs an Application that routes by calling loader. That is a hook-row question (#3) and is not done.

### Fairness: the parity door and its leak tests

`ScrubbedStart` is the one door. It is public and final, has a private constructor and a single factory,
`from(SnapshotStore, SnapshotHandle)`, which refuses a live handle, a handle whose scrubbed flag was forged, and an incomplete
row. It carries the sample's files, log, emotes, hero class, slot and `activeSalt` and no Run seed or declared salt. The isolated
driver builds its snapshot with its own placeholder seed and the sample's salt, so nothing of the live Run's seed enters the
second copy. The shared-by-name classes (`RolloutWorld`, `WorldFactory`, `ScrubbedStart`, `SharedBackend`, `NoOpGL`) are the
only types that cross the loader boundary, and none holds a game object.

Leak tests (1, 2b and 4 are in the build; 2 and 3 are preconditions named below):

1. **Boundary (ArchUnit).** Only the driver, the `isolate` package and `agent.IsolatedWorld` depend on `ScrubbedStart`; the
   `brain` module reaches a rollout only through `api`'s `Simulator` and `SnapshotHandle`; `ScrubbedStart` has no field holding
   a seed, a salt other than `activeSalt`, or a game type.
2. **Fail-closed boot.** `HeadlessBoot` throws when `Gdx.app` is neither absent nor a `SharedBackend`, so a second copy cannot be
   booted over the Overlay's desktop application; a test pins it before any Overlay wiring.
2b. **Refusals.** `from` throws on a live handle, a forged scrubbed flag and an incomplete row; the isolated host refuses a
   handle it did not scrub.
3. **Permutation.** The same sample taken over two live worlds that differ only in hidden state (`HiddenPermutation.permute()`)
   must give equal `ScrubbedStart` files (after the save-form normalisation) and equal isolated results; the unscrubbed start,
   as the negative control, must differ, or the test discriminates nothing. **Not built in this spike.** The layer below it
   is covered today: `ScrubbedStart` holds exactly the files of the scrubbed snapshot, and `SearchLeakTest` already shows
   those equal across eight alternate hidden worlds per snapshot. The isolated-host half (equal results from the world, with a canary seed and a repeat-the-same-rollout determinism check beside it) is a
   precondition of any non-test caller of `IsolatedHost` (the Rig wiring included), not a later story, because it needs the mid-fight fixtures of
   `SearchLeakTest` run through two worlds and the Rig's budget this week is small.
4. **Fully observed equivalence.** In a position with no hidden state to scrub, the isolated step equals the live step
   (`IsolatedWorldTest`, 53 Actions, every Observation equal). Passing.

### Defect found and fixed: the world did not have the Run's history (#390)

Tracked in [#390](https://github.com/watchthelight/shatterfish/issues/390), which blocked story 10.24 (#370). On two of 22
snapshots in the final run, 11 of 207 compared results differed in the `log` section only (same end and length, every other
section equal): one guidebook hint line more in one host than the other, which slid the 64-line window by one.

**Cause.** The suspects the issue named (the static log buffer, the guidebook state a pooled world does not reset) were not
it. A trace of the journal at the start of every rollout showed the isolated world holding an empty journal (no guide page
found, none read) while the live Run, which the swap host borrows, held `Intro` through `Searching` found and `Examining`
read. The profile is not in the save: every Run's profile begins empty (ADR-0007) and grows as the Run plays, and the game
reads it while it plays. Whether a hint is shown is a function of it (`Hero.java:1758`, `Snake.java:65`, `Hunger.java:109`,
`EquipableItem.java:59`, and `flashForDocument` writes the "Your guidebook has advice" log line, `GameScene.java:1321-1335`).
The swap host plays in the live Run's process and so reads the live profile; a world is a Run that has played nothing, and the
profile guard puts it back to that after each rollout. The same holds for the catalogs behind the researcher badges
(`Badges.java:632-660`) and for the statics no save carries (`Snake.dodges` and the others of `RunStatics`). The hint was the
only one of these the sample showed because a rollout is two to four Actions long; the rest were latent.

**Fix, in the harness, no upstream edit.** `RunHistory` is a plain-values holder (journal bundle bytes, badge names, flags and
counters; no game type, so the loader shares it by name like `ScrubbedStart`). `IsolatedHost` takes one from the live Run at
the rollout (`RunHistoryCodec.capture`, the live loader's own copy of the codec), and the world applies it with
`RunHistoryCodec.apply` after it has held its profile and before it loads the copy (the game reads the journal as it loads a
floor). The profile guard and the statics baseline then put the world back to empty, as before. The rankings are not carried:
a Run in progress has none. `RolloutWorld.rollout(start, actions)` stays and means "a Run that has played nothing"
(`RunHistory.NONE`).

**Fairness.** The history is what the swap host already plays on, and each part is something the player's own screen has: the
journal and badges pages, the snake's dodges the hero watched, the last attack's leftovers. It is read from the live Run's
process-wide profile and statics, not from the unseen world, and the scrubbed copy's files are unchanged. The boundary test
holds `RunHistory` to plain-valued fields and to the isolation classes, and `RunStatics.exportTo` is package-private to the
codec. The `fairness` label stays on the change.

**Evidence.** `IsolatedHostLogTest` reproduces both snapshots (the same seed, class and salt the tool used, the real Brain, the
tool's three samples and horizons 2 to 4) and fails without the fix (CLERIC at wait 140: "Observation 4 differs, log lines 15
against 14"; DUELIST at wait 252: "Observation 1 differs, log lines 40 against 41"); it also plays each rollout twice in the
pooled world and requires equal results, the repeat-the-same-rollout check this ADR named. With the fix the standard-set run
(`isolationSpeed --set standard --seeds 8 --per-seed 3 --samples 3`) compares 207 pairs and **0 differ**.

**Still open.** The permutation half of leak test 3 (equal results from two worlds over live Runs that differ only in hidden
state, with a canary seed) is not built; it remains a precondition of the first Brain that reads a rollout through
`IsolatedHost`, and it gains a case from the fairness review of this fix: `RunHistoryCodec.capture()` over two live Runs that
differ only in hidden state (unidentified item identities, unseen monsters, undiscovered traps) must be byte-equal, with a
visible difference (a page read) as the control that it can differ. The hit-or-miss icon is the least plain value carried
(`Char.java:594` clears it when a sprite shows it; it is set for any pair of fighters); it stays because the swap host plays
on it, and the case above is where it would show. A position where the live Run has earned a researcher badge or read more pages than 8 seeds at 3 snapshots
reach is not exercised by a comparison yet; the carried badges and catalogs are covered by the same code path, not by a case.

`HeadlessDriver.loadScrubbed` is public because the agent package calls it; on a live driver it would replace the live Run with
a sampled one. Nothing outside the harness reaches it today (the rig, overlay and desktop modules must repeat the ArchUnit rule
before they depend on `IsolatedHost`), and narrowing it to a driver-side isolated-world marker is part of the same precondition.

### Consequences

- Good: a measured 1.7 to 2.2 times faster simulator, so a think-time column exists; the cost model is now split into snapshot,
  load and play and each is published.
- Good: no game rule is written twice; the isolated world runs the pinned game's own code.
- Bad: the load is the floor and isolation does not move it; the real lever is option 6.
- Bad: a second copy of the game adds a loader boundary to the harness that every upstream tag upgrade must keep working
  (`HarnessReflectionTest` confines reflection to `SceneStepper`, `RunStatics` and `NoOpGL`; the isolation loader adds none
  because the child provider is found by `ServiceLoader`).
- Bad: the Overlay cannot use it yet.

## Pre-mortem

*If this is wrong in six months, why?*

- The load floor makes any rollout search too slow to pay: at 40 ms a rollout, width three and depth three is a quarter of a
  second a Decision and the Rig has about 400 fight Decisions per Run. Mitigation: it is registered at a stated think time and the Rig decides; option 5 stays the shipped layer.
- The `log` divergence was a symptom of a larger leak between worlds that a position-by-position comparison does not find
  (a pooled world carrying state from a previous rollout, or lacking the live Run's). It was the second: the world lacked
  the Run's history (#390). The repeat-the-same-rollout check now runs in the build and shows the world carries nothing from
  one rollout to the next; the permutation test is still to build, and the Brain reads no rollout log section until it is.
- The timings were taken on a shared machine, so the ratio is wrong in a way the absolutes hide. Mitigation: the two hosts
  alternate within one run on the same copies, and the tool was run twice, throttled and not, with the same ratio.
- A new upstream tag changes the save format or a static and the second world silently diverges. Mitigation: the equivalence
  test runs in the build and fails on drift.
- The Overlay requirement is bigger than a routing Application (the posted-runnables queue is only one of several process-wide
  statics). Mitigation: the Overlay stays out until a spike of its own.

## Amendment: the permutation test is built, and the first caller (story 10.24, #370)

- `IsolatedPermutationTest` is the permutation leak test this ADR names. At a mid-fight wait of a DUELIST and a CLERIC Run
  it permutes everything the Observation does not show (an unseen mob hurt, enraged and hasted, a hidden trap, a secret door,
  `HiddenPermutation.permute()`, the dungeon seed) and shows that the Observation hash, the scrubbed start (after one
  normalisation, below), `RunHistoryCodec.capture()` and the isolated rollout's result are all equal across the two worlds,
  for two samples and on a repeat. Three controls: the unscrubbed saves of the two Runs differ; halving the hero's health
  moves the result; reading a guide page moves the captured history. A second test does the same through `HostLookahead`,
  the object a search is lent.
- One finding. The `POTION_SCROLL_RING_CLASSES` row draws a non-identity permutation from the sample and applies it to the
  Run's own label-to-class assignment, so a fixed sample yields different assignments in different hidden worlds. The row is
  distributional, not pointwise, and the assignment it writes is never the truth, so it carries a negligible amount of
  information (about 0.01 bit for five unknown classes), and only about unidentified items. The test masks the labels and the
  unidentified families' class names. Consequence for callers: a rollout must not depend on an unidentified item's class. The
  fight search offers no use of an unidentified item as a first Action; its playout is the rules Brain, which may test an item
  later in a line, and that is the one place the row can matter.
- The Rig is the first non-test caller (`HostLookahead`, lent by `RunLoop` to a decider that says it `searches()`); the
  Overlay still has none. The measurement is in `docs/results/2026-10-29-fight-search.md`: no win, not the default.
