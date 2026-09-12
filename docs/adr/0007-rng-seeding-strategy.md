---
status: accepted
date: 2026-09-04
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0007: RNG seeding strategy and the other sources of nondeterminism

## Context and problem statement

A Run must be fully determined by (Upstream tag, hero class, challenge flags, seed, Action list)
(non-negotiable #5, PRD FR-2). Session 10 read the game's random-number code
(`docs/rules/rng.md`) and found: the base generator is unseeded (`SPD-classes/…/utils/Random.java:37-45`);
`Dungeon.init` seeds labels, room orders and Generator decks under `seed+1` and then discards the
whole stack with `Random.resetGenerators()` (`…/Dungeon.java:242-254`); level generation pushes a
per-floor seed (`…/levels/Level.java:217`); combat, loot, detection, wandering and respawns draw
from the base generator; guide pages on floors 1 and 2 use an unseeded `pushGenerator()`
(`…/levels/rooms/standard/entrance/EntranceRoom.java:102-132`); actor tie-breaks follow `HashSet`
identity-hash order (`…/actors/Actor.java:255-265`); `Random.chances(HashMap<Class,Float>)` iterates
in identity-hash order inside seeded level generation (`Random.java:202-229`;
`…/levels/rooms/secret/SecretLaboratoryRoom.java:91-97`); the render and audio threads draw from
the top of the same stack (`SPD-classes/…/noosa/particles/Emitter.java:154-155`); and
`bones.dat`, `journal.dat` and `settings.xml` feed generation. Decide how the Harness seeds and
what else it controls.

Non-negotiables touched: #3 (hooks minimal), #5 (reproducible).

## Decision drivers

- The same tuple must give identical Observation hashes on Windows and Linux (NFR-2).
- The Overlay's Runs should be Replay-verifiable too (FR-24), which means render-thread draws must
  not perturb game draws.
- Hooks are a budget of eight (PRD §10); every hook here must earn its place.
- The seeded Run should behave like a human's seeded Run wherever possible, so the Rig's numbers
  describe the game people play.

## Considered options

**Seeding the base generator**

1. Do nothing beyond `Dungeon.seed`. Rejected: combat is unseeded; two Runs diverge at the first
   hit roll.
2. Seed the base generator once, before `Dungeon.init`. Rejected: `init` calls
   `resetGenerators()` afterwards and the seed is lost.
3. **Seed once after `Dungeon.init`, by pushing a generator seeded from `mix(seed, 0)` that is
   never popped by the Harness** (the game's own push/pop pairs nest above it and pop back to
   it). Kept as the fallback; superseded by 4.
4. **Re-seed at every Input wait: pop the Harness generator and push one seeded from
   `mix(salt, inputWaitIndex)`, where `salt` is a per-Run 64-bit value drawn by the runner at Run
   start, recorded in the Run log header and the Results page, and never placed in the
   Observation.** Chosen. Deriving the stream from the *seed* was rejected by the red-team pass:
   the seed is on the HUD and in every published Seed set, and `java.util.Random`, the MX3
   scramble (`Random.java:57-66`) and SplitMix64 are all reproducible with no game imports, so a
   Brain could predict every combat roll; a salt it never sees cannot be predicted. Every draw
   between two Input waits is then a function of the salt and the wait index, so a Replay that
   reaches wait `k` with the same Observation reproduces the same next turn regardless of how
   many draws happened earlier. The Run tuple becomes (tag, class, challenges, seed, salt, Action
   list). The salt is drawn by the runner when the pair executes, from a per-invocation secret,
   and written to both Run logs; a Registration does **not** fix salts in advance, because a
   published salt lets a Brain's author precompute the roll table as pure data in `brain` (the
   session 12 red team). A Replay reads the salt from the log header, so reproduction is
   unaffected.
5. Replace `com.watabou.utils.Random` wholesale with a Shatterfish implementation. Rejected: a
   hook over the whole class; option 4 needs no upstream edit.
6. Per-actor generators (each `Char` has its own stream). Rejected: a second implementation of a
   rule the game does not have, and a large hook.

**The seed itself**

7. **The Shatterfish seed is the game's seed: a long in `[0, 26^9)`; the Harness sets
   `SPDSettings.customSeed` to its nine-letter code so the game treats the Run as seeded** (no
   bones item, no badges, rankings sorted as seeded) (`…/utils/DungeonSeed.java:41-49`;
   `…/Bones.java:89-93`). Chosen. Seed sets are lists of such longs; the Results page prints the
   code.
8. A 64-bit Shatterfish seed hashed down to the game's range. Rejected: two Shatterfish seeds
   could collide on one game seed and the published code would not be what the game shows.

**Identity-hash order**

9. Accept and measure (the E1 determinism test runs two JVMs; if ties never bite, no hook).
   Rejected as the only plan: the mechanism is certain even if the frequency is unknown, and a
   flaky determinism test is worse than a hook.
10. **Hook: `Actor.all` and `Actor.chars` become `LinkedHashSet`, `Level.mobs` a
    `LinkedHashSet` and `Level.blobs` a `LinkedHashMap` (so that `Actor.init`, which re-inserts
    from both on every level load, `…/actors/Actor.java:194-212`, `…/levels/Level.java:182-184`,
    sees insertion order), and `Random.chances(HashMap)` and `Random.element(Collection)` iterate
    keys sorted by `Class.getName()` when the elements are classes (`Random.java:202-229`,
    `:249-254`; the `ChaoticCenser` calls `element` on a class-keyed map,
    `…/items/trinkets/ChaoticCenser.java:164-173`).** Chosen: one hook row of six one-line type
    changes, all semantically neutral for a human game (no code depends on hash order). Listed in
    `docs/UPSTREAM.md` when the E1 pull request lands them.
11. Give every `Actor` a deterministic `hashCode` from its `id`. Rejected: `id` is assigned on
    `add`, and overriding `hashCode` on a class hierarchy this wide risks equality bugs.

**Unseeded guide-page generator and cross-Run files**

12. Hook `EntranceRoom.placeEarlyGuidePages` to push a seeded generator. Rejected: a hook for a
    tutorial feature.
13. **A standard Shatterfish Profile per Run: a fresh directory with `language=en`, `intro` off,
    every `Document` page marked read, no `bones.dat`, no rankings, no badges; the Profile has a
    version that is part of the Run tuple.** Chosen. With every page read the guide-page branches
    do not run and the tutorial's forced hidden entrance doors on floors 1 and 2 are not forced
    (the regular secret-door roll applies instead, so those floors have the normal number of
    secret doors, not fewer); `placeEarlyGuidePages` still pushes and pops its unseeded
    generator but draws nothing from it (`…/levels/RegularLevel.java:569-583`;
    `…/levels/painters/RegularPainter.java:215-270`; `…/levels/rooms/standard/entrance/EntranceRoom.java:102-132`).
    The language pin matters because every string in the Observation is a `Messages.get` lookup
    that falls back to `Locale.getDefault()` (`…/SPDSettings.java:426-432`). This is "an
    experienced player's profile", which is the player Shatterfish models. `Bones.get` returns no
    item for a seeded Run and the class remains still drops, deterministically
    (`…/Bones.java:197-200`).

**Render-thread draws (Overlay only)**

14. Ignore. Rejected: an Overlay Run would not Replay.
15. **Route `Emitter`, `Music` and `EmoIcon` draws to the base generator through the `false`
    overload of `Random.Float`** (one hook row). Chosen, and required rather than conditional:
    `EmoIcon` constructors draw on the actor thread (`…/effects/EmoIcon.java:89`, `:113`, `:137`,
    `:160`, from `Mob.act`, `…/actors/mobs/Mob.java:229-237`) and `Emitter` draws per particle
    (`SPD-classes/…/noosa/particles/Emitter.java:92`, `:154-168`), so a headless Run and an Overlay
    Run consume different draw counts within a turn and the Observation at wait `k+1` already
    differs; per-wait reseeding cannot restore that, only the routing can. The headless scene
    must also create sprites and emotes exactly as the game does, so that the remaining
    actor-thread draws match.

## Decision outcome

- `RngControl` in `harness` owns the game's generator stack after `Dungeon.init`: it pushes a
  generator seeded from `mix(salt, k)` at Input wait `k`, where `mix` is defined exactly as
  `splitmix64_finalize(salt + k * 0x9E3779B97F4A7C15)` with the standard SplitMix64 finalizer
  (`z ^= z >>> 30; z *= 0xBF58476D1CE4E5B9; z ^= z >>> 27; z *= 0x94D049BB133111EB; z ^= z >>> 31`),
  a definition with a published test vector on the methodology page so a skeptic can recompute it;
  the Run log records the salt and `k`; the Observation carries neither the salt nor the
  seed. `hero.live()` and `initHero` run inside `init` after `resetGenerators()`
  (`…/Dungeon.java:254`, `:281-286`); the E1 determinism story verifies they draw nothing, and if
  they do, the first push moves into a one-line hook after line 254.
- The seed is the game's seed; the Run is a seeded game (`customSeedText` set).
- A fresh standard Profile per Run (option 13), created by the Harness in the Run's working
  directory; the Profile's contents are part of the Run's definition and are versioned in
  `harness` resources.
- Hooks: the identity-hash row (six one-line type changes, option 10) in E1; the render-thread
  routing row (option 15) in E5. Each becomes a row in `docs/UPSTREAM.md` in the pull request
  that lands it, with its reason.
- Tests: the determinism test runs the same (tag, class, challenges, seed, salt, Action list) in
  two fresh JVMs and compares every Observation hash (FR-2); a second test replays from a mid-Run
  snapshot and expects the same hashes from wait `k` on, which is what option 4 buys.
- Headless Runs have no render or audio thread; the driver thread draws nothing, and the
  headless scene creates the same sprites and emotes as the game so the actor thread's own
  draws match an Overlay Run's.

### Consequences

- Good: reproducibility no longer depends on counting draws; a Replay is stable against
  draw-count drift between versions of the Overlay.
- Good: the Rig's numbers describe seeded games as the game defines them.
- Bad: two hook rows (E1 identity order, E5 render-thread routing) out of eight; both are
  semantically neutral.
- Bad: the standard Profile makes Shatterfish's world slightly different from a first-time
  human's (no guidebook, no tutorial door hiding, English strings); recorded in the Codex and in
  every Results page.
- Bad: reseeding per Input wait from a salt changes the game's random stream relative to a
  human's seeded Run: same dungeon and items, different combat rolls. The Rig compares Brains
  against each other under the same regime, so this does not bias comparisons.
- Bad: the Run tuple grows by the salt; Seed sets stay lists of seeds, and a Registration
  records the salts it used.

## Pre-mortem

*If this is wrong in six months, why?*

- A game system keeps state across the Harness's pop/push (a nested generator the game pushed
  before the Input wait and pops after). Mitigation: the Harness only swaps its own generator
  when the stack depth equals what it pushed, and asserts otherwise; `Level.create` is the only
  long-lived push and it completes inside a level change, never spanning an Input wait.
- Identity-hash order hides in a place the hook row does not cover. Mitigation: the two-JVM
  determinism test on the `smoke` set with the random agent; any further site is one more
  one-line type change under the same row.
- The standard Profile drifts from upstream's profile format on an upgrade. Mitigation: the
  Profile is generated by the game's own `Bundle` writers at Run start, not stored as a binary
  fixture.
- The Overlay still diverges within a turn after the routing hook (a draw site not routed).
  Mitigation: the E5 Replay test names the wait; the Overlay can pause emitters while the actor
  thread runs as a last resort.
- A Brain obtains the salt anyway (reading the Run log from disk). Mitigation: AD-1 adds an
  ArchUnit rule that `brain` uses no `java.io`, `java.nio.file`, `java.net` or reflection; the
  Brain has no channel but the Observation.

## Amendment: story 1.15 (2026-09-12)

**What the generator stack turned out to allow.** `Random.resetGenerators()`, `pushGenerator(long)`
and `popGenerator()` are public and static, and the deque behind them is private with no accessor
(`SPD-classes/.../utils/Random.java:37-73`). So the harness can drive the stack and cannot read its
depth, and this ADR's pre-mortem — "the Harness only swaps its own generator when the stack depth
equals what it pushed, and asserts otherwise" — cannot be written as an assert without a hook row.
The row is not this story's to spend: this ADR reserves the next one for identity order, which is
story 1.16's.

What stands in its place is two things, and the review of this story corrected the first claim made
for them. `MixTestVectorTest` checks that the numbers drawn at a wait are exactly those of a
generator seeded `mix(salt, k)`, and that a Run leaves the stack as deep as it found it, by putting
a generator with a known stream underneath and asking it afterwards. Neither of those would notice a
generator the game pushed and did not pop *between* two waits, because the reseed pops one and
pushes one whatever is underneath.

What settles the question is a reading of the tag, done in the review: every `pushGenerator` in
`core/` and `SPD-classes/` is matched by a `popGenerator` in the same method. `Level.create` pushes
at `…/levels/Level.java:221` and pops at `:324`; the vault mirror, Yog's initialiser and the item
generator's decks are balanced the same way; `Dungeon.java:243`'s push is discarded wholesale by
`Random.resetGenerators()` at `:254`. So pop-then-push is correct at `v4.0.0` by inspection, not by
test. One place is worth remembering: `Level.create` has no `try`/`finally`, so an exception during
level generation would orphan a generator.

**Where the reseed happens.** At the wait itself, inside `HeadlessDriver.stepToInputWait`, as it
confirms one and before it returns. This ADR and ADR-0013 put the reseed at the head of the wait,
before the Observation is read; doing it in the driver's own `run` loop would have left every other
caller — the agent's loop, a test, a Replay — drawing from whatever the last Run left behind. A Run
is a function of its tuple however it is driven, or it is not one.

**The published vector.** `docs/methodology.md` carries the mix, its definition in words, a six-row
test vector and the same function in Python, so a skeptic can check the numbers without running
anything of ours. The vector is held from two directions: the implementation is checked against the
table, and the table against a second implementation written from this ADR's text.

**The Profile is version 1.** English strings, the intro off, the support prompt answered, the
compact interface, and an empty history. A directory carrying another version's stamp is refused
before the Run starts rather than compared after it. Two of those choices have reasons worth
repeating: the compact interface is the one whose item selector is a window an Action can answer
(story 1.14), and the empty history matters because the game reads it — a snake stops dodging after
four misses only once the first boss has been slain (`…/actors/mobs/Snake.java:66`).

**The stack is owned from `Dungeon.init` onward, not only at waits.** This ADR says so and the first
draft of this story read it as "at every wait", which left the first floor being built on the
generator the game's own init leaves behind — `Random.resetGenerators()` at `…/Dungeon.java:254`
seeds that one from the system. Wait zero's generator is pushed immediately after init and before
the floor is generated.

**The salt has no default, and that is the point.** The first draft gave `HeadlessDriver.start` a
two-argument overload that meant salt zero, and `RunLoop` — the loop that drives the Brain seam —
used it. This ADR rejected exactly that at `:55-62`: the mix is published, so a salt anyone can
predict lets a Brain compute the coming draws as pure data. The review of this story found it. There
is no salt-free way to start a Run now; a runner without a salt draws one from `Salt.draw()` and the
outcome records what it drew.

**What this story did not close.** Two Runs of one tuple now draw the same numbers at the same
waits, which `ProfileTest.one_tuple_one_stream` holds; that is the randomness half of issue #70.
They still do not see the same screens. With the salt controlling every draw from init onward and
the history emptied per Run, one floor-one item lands a cell apart between two Runs, and the cell
creeps by one with each Run in a process — a value that creeps is counted rather than drawn.
`ProfileTest.one_tuple_two_screens_for_now` asserts that divergence deliberately, so that the story
cannot be read as having closed #70, and so that story 1.16 sees a failing test the day it succeeds.
