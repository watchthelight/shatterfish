# Red-team review 2: ADR-0009 to ADR-0013

Reviewer: `fairness-reviewer` subagent, fresh context.
Date: 2026-09-04. Upstream pinned at `v3.3.8`; working tree equals the tag (verified by reading
every cited file). `…/` abbreviates
`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/`.

## Verdict: BLOCK (confidence: high)

Seven blocking findings, twenty should-fix, one citation error. Two of the blocking findings
are complete bypasses of `Observer` (B1, B2), one is a leak into every rollout (B3), three break
non-negotiable #5 in the Overlay (B4, B5, B6), and one makes non-negotiable #1's "enforced by
architecture, not intentions" a policy statement (B7).

Counts by severity: **Blocking 7**, **Should fix 20**, **Nit / citation 1**.

---

## Blocking

### B1. `Snapshot` is an `api` type that carries the whole game bundle and the salt

ADR-0009, "Decision outcome", line 94:

> `api` (E1): `Snapshot` (opaque bytes plus `salt`, `k`, `profileVersion`, `tag`, `schemaVersion`)

and ADR-0010, "Fairness rule for any winner", line 72:

> the search consumes `Observation`, `Belief` and `Snapshot` values through `api`

`brain` depends on `api` (ADR-0003, `docs/fairness.md` "Classpath" row). Putting `Snapshot` in
`api` and handing it to the search puts the *serialized true world* inside the Brain's reach.
"Opaque bytes" is not a property of a `byte[]`; it is a hope.

What those bytes contain, read at the tag:

- Every item's real class name, written as `__className` by
  `SPD-classes/src/main/java/com/watabou/utils/Bundle.java:365-376`, for the hero's belongings
  (`…/Dungeon.java:637`) and every heap in the level bundle (`…/levels/Level.java:468`). The
  string `ScrollOfUpgrade` is in the bytes whether or not the scroll is identified.
- Every mob's `pos`, `state`, `enemySeen` and `target` (`…/actors/mobs/Mob.java:155-179`), for
  mobs the hero cannot see.
- The complete `map`, including `SECRET_DOOR` and `SECRET_TRAP` cells
  (`…/levels/Level.java:463`), and every trap regardless of `visible` (`:470`).
- `Dungeon.seed` (`…/Dungeon.java:630`).
- And, from the `Snapshot` record itself, `salt`.

The exploit needs no game import, no reflection, no file I/O, so it passes both
`BrainImportsNoGameCodeTest` and ADR-0007's AD-1 rule banning `java.io`, `java.nio.file`,
`java.net` and reflection in `brain`: `java.util.zip.Inflater.setInput(byte[])` /
`inflate(byte[])` inflates the gzip in memory with no stream class, and a byte scan for
`"__className":"` recovers every identity. `Bundle.write` compresses by default
(`SPD-classes/src/main/java/com/watabou/utils/Bundle.java:481`, `:539-543`).

The salt is worse than the bytes. ADR-0007 lines 51-58 state the entire random stream between
Input waits is `mix(salt, k)` and that "`java.util.Random`, the MX3 scramble
(`Random.java:57-66`) and SplitMix64 are all reproducible with no game imports". A Brain holding
a `Snapshot` reimplements `mix` in twenty lines of `api`-only Java and predicts every hit roll,
every drop, every trap trigger for the rest of the turn.

Situation: the hero is at 7 HP beside an unidentified potion and a gnoll. The Brain receives the
`Snapshot` for the search, inflates it, reads `PotionOfHealing` and the salt, computes that the
gnoll's next attack roll misses, and "decides" to drink. Every leak test in `docs/fairness.md`
passes, because none of them looks at `Snapshot`.

**Fix in one sentence:** `Snapshot` and `BeliefSample` live in `harness`; `api` exposes only an
opaque integer handle with no accessor to bytes, no `salt` and no `seed`, and the search API is a
`harness`-side service the Brain calls with `(handle, Action)` and receives an `Observation` back.

### B2. No scrub row for `Dungeon.seed`; a rollout that takes one staircase generates the true floor

ADR-0009, hidden-element table, line 76:

> Generator decks, `LimitedDrops`, quest state not yet shown | Reset to the tag's defaults |
> never observed; rollouts inside a Decision horizon never generate a floor

The table has no row for the run seed. `Dungeon.saveGame` writes it (`…/Dungeon.java:630`) and
`loadGame` restores it (`:730`), so a scrubbed snapshot still holds the true seed. Level
generation is a pure function of it: `Dungeon.seedForDepth` (`…/Dungeon.java:414-430`) and
`Level.create` under `pushGenerator(seedCurDepth())` (`…/levels/Level.java:217`), per
`docs/rules/rng.md` rows 16-17.

"Rollouts inside a Decision horizon never generate a floor" is false at the tag. Descending is a
*single* hero Action: `Hero.handle` on a transition cell produces
`curAction = new HeroAction.LvlTransition(cell)` (`…/actors/hero/Hero.java:1955`), and ADR-0010
sets the horizon at "2 to 4 hero turns" (line 39). Falling into a chasm is also one step
(`Chasm.heroFall`, reached from `Hero.getCloser`, `…/actors/hero/Hero.java:1838-1846`).

Situation: the hero stands on the down staircase of floor 3 with the floor unexplored below.
Search rolls out `Descend` and the engine builds the *real* floor 4 — its layout, its shop, its
Scroll of Upgrade, its mob roster. The search's returned value is a function of information no
human at that screen has. Every determinization produces the *same* floor 4, because the seed is
constant across them, so ADR-0010's disambiguation-factor measurement (line 55) will read as
suspiciously informative and be believed.

**Fix in one sentence:** add a table row that rewrites `SEED` and `CUSTOM_SEED` in the game bundle
to `sample.seed` before load, and have the rollout host abort any rollout that reaches
`InterlevelScene` or `Level.create`.

### B3. Mobs inside FOV keep exact HP, `state`, `enemySeen`, `target` and `enemy`

ADR-0009, table line 72:

> Unseen mobs | Remove every mob outside `heroFOV`; re-add the sample's remembered mobs at
> sampled positions on VISITED cells outside FOV | mobs are present iff in FOV

Correct for mobs the hero cannot see, and silent about the ones it can. `Mob.storeInBundle`
persists `state`, `enemySeen`, `target` and the enemy's id (`…/actors/mobs/Mob.java:155-179`), and
`Char.storeInBundle` persists exact `HP`. ADR-0006's Mob and Mob-state rows
(`docs/adr/0006-observer-visibility-rules.md:66-67`) list precisely these as things the Observer
must never read: "`Mob.state`; `Mob.alerted`; `Mob.enemySeen`; `Mob.target`; `Mob.enemy`" and
"exact `HP`" (health is quantised to the bar's pixel width). They survive the scrub untouched and
drive the rollout's AI (`Mob.act` branches on `state`; `restoreEnemy` is re-run from
`Actor.init`, `…/actors/Actor.java:203-205`).

Situation: a crab in FOV at 9 HP and the same crab at 14 HP render an identical health bar
(`…/ui/HealthBar.java:65-88`). The abstract Observation is byte-identical; the rollout is not. The
search returns "attack, you kill it this turn" in one world and "retreat" in the other. This is
exactly the failure FR-13's search leak test is supposed to catch, and it will not, for the reason
in S7 below.

Second situation: a sleeping skeleton in FOV whose `state` is `SLEEPING` versus one whose `state`
is `HUNTING` with `target` set to the hero's cell. Both draw no emote until the mob's next act
(ADR-0006 line 67 says so explicitly). The rollout distinguishes them on turn one.

**Fix in one sentence:** add a table row that re-samples `HP` within the health-bar bucket and
re-samples `state`, `enemySeen`, `target` and `enemy` from the Belief for every mob that remains
after the FOV filter.

### B4. PAUSED cannot hold hero input: `GameScene.ready()` reinstalls the default listener 60 times a second

ADR-0013, "Modes", line 90:

> `PAUSED` holds the Decision and drops hero input (the Panel installs its own
> `CellSelector.Listener` that ignores cells while paused...)

`Hero.ready()` calls `GameScene.ready()` (`…/actors/hero/Hero.java:945`). `GameScene.ready()` is
`selectCell(defaultCellListener)` (`…/scenes/GameScene.java:1642-1643`), and `selectCell`
*replaces* whatever listener is installed, after calling `listener.onSelect(null)` on it
(`:1552-1555`). ADR-0013's own lines 70-71 say `ready()` is re-run on every actor-thread wake-up,
and `GameScene.update` notifies the actor thread at 60 Hz (`…/scenes/GameScene.java:883-888`). So
the Panel's listener survives at most 17 ms.

The listener it is replaced by is `defaultCellListener`, whose `onSelect` is
`if (Dungeon.hero.handle(cell)) Dungeon.hero.next();` (`…/scenes/GameScene.java:1753-1754`).

Situation: the operator pauses the Overlay to read the Decision log and clicks the map to scroll.
The click lands on a cell, the hero walks there, the driver never observed that wait, no `wait`
record and no `unsupported` record is written, and the Run log's `verifiable` stays true.
Replaying (tag, class, challenges, seed, salt, Action list) does not reproduce the Run:
non-negotiable #5 fails and the log says it did not.

Note also that `selectCell` calls the outgoing listener's `onSelect(null)` from the **actor
thread**, so the Panel's listener — Shatterfish code — is invoked off the UI-role thread, which
ADR-0013's role table (line 61) says never happens.

**Fix in one sentence:** gate input at the source with a guarded hook on `CellSelector.select`
(`…/scenes/CellSelector.java:152-153`) that returns early when the driver is PAUSED, instead of
trying to own `cellSelector.listener`.

### B5. `CellSelector.processKeyHold` bypasses the listener entirely and mutates game state on the render thread

Same ADR-0013 sentence (line 90). Held movement keys and the controller stick never reach
`cellSelector.listener` at all:

```
// …/scenes/CellSelector.java:464-480
public void processKeyHold() {
    if (!directionFromAction(leftStickAction).isZero() && heldDelay < 0) {
        enabled = Dungeon.hero.ready = true;
        Dungeon.observe();
        if (moveFromActions(leftStickAction)) { Dungeon.hero.ready = false; }
    } else if (...) { enabled = Dungeon.hero.ready = true; Dungeon.observe(); ... }
}
```

`moveFromActions` calls `Dungeon.hero.handle(cell)` and `Dungeon.hero.next()` directly
(`…/scenes/CellSelector.java:415-417`). `processKeyHold` runs from `CellSelector.update()`
(`:386`), i.e. on the render thread, inside the scene update.

Three separate breakages, all from ADR-0013's own premises:

1. In PAUSED a human holding an arrow key moves the hero. No listener, no toolbar and no
   inventory pane is consulted, so neither of the ADR's two mechanisms applies.
2. Lines 467 and 474 **write** `Dungeon.hero.ready = true` from the render thread, and lines 468
   and 475 run `Dungeon.observe()` there. ADR-0013's AD-5 check ("hero ready; no window or a
   Prompt window") is therefore reading a value the render thread itself forges, and
   `Dungeon.observe` mutates `Level.visited` (`…/Dungeon.java:1010-1013`) concurrently with the
   actor thread's own `Dungeon.observe` in `Hero.act` (`…/actors/hero/Hero.java:843`).
3. `CellSelector`'s key listener sets `Dungeon.hero.resting = false` on *any* non-direction key
   press while resting (`…/scenes/CellSelector.java:331`, `:344-346`) and returns true. The
   Overlay's own hotkeys (`Next Step`, `Pause`, EXPERIENCE.md's stepping model) therefore cancel a
   bot rest silently — a hero-affecting input that changes the Action stream and appears nowhere
   in the log.

**Fix in one sentence:** the same guarded hook as B4, placed so it also short-circuits
`processKeyHold` and the `resting = false` writes, with a log record for every input the guard
swallows.

### B6. `Dungeon.loadGame` consumes RNG, so restore-and-replay cannot reproduce the original hashes

ADR-0009, "Decision outcome", lines 97-99:

> the restore-and-replay test (restore at wait `k`, replay the remaining Actions, expect the
> original hashes) runs in CI from E1 on

`Dungeon.loadGame` calls `Generator.restoreFromBundle` (`…/Dungeon.java:822`), whose first
statement is `fullReset()` (`…/items/Generator.java:926`), and `fullReset` draws from the current
top generator:

```
// …/items/Generator.java:625-636
public static void fullReset() {
    usingFirstDeck = Random.Int(2) == 0;
    generalReset();
    for (Category cat : Category.values()) {
        cat.using2ndProbs = cat.defaultProbs2 != null && Random.Int(2) == 0;
        reset(cat);
        if (cat.defaultProbs != null) { cat.seed = Random.Long(); cat.dropped = 0; }
    }
}
```

That is one `Int(2)` plus, per `Category`, one `Int(2)` and one `Long()` — several dozen draws —
consumed off the top of the stack on every restore. Under ADR-0007 the top of the stack at wait
`k` is the Harness generator seeded from `mix(salt, k)`. The original Run at wait `k` did not
consume them. The next combat roll after a restore is therefore a different roll, and every
Observation hash from `k` on diverges. ADR-0009 never mentions reseeding after the load, and
ADR-0013's per-wait sequence (lines 73-76) reseeds *before* observing, not after restoring.

Two more restore side effects in the same class, neither in ADR-0009's pre-mortem (which worries
only about badges, journal and `GamesInProgress`):

- `Dungeon.switchLevel` clears `hero.lastAction` as well as `curAction`
  (`…/Dungeon.java:508`). `lastAction` is observable: it is what `Hero.resume()` replays
  (`…/actors/hero/Hero.java:959-963`) and what makes the Resume tag visible
  (`…/scenes/GameScene.java:899`, `:903-906`). A snapshot taken at a wait where a Move was
  interrupted (`Hero.interrupt`, `…/actors/hero/Hero.java:948-953`) restores with the Resume
  action gone, so the restored Observation is *not* byte-identical to the snapshotted one and the
  valid-Action set shrinks. ADR-0009's second decision driver (lines 30-31) is violated by the
  un-scrubbed restore, before any scrub runs.
- `loadGame` calls `Actor.clear()`, which sets `now = 0` (`…/actors/Actor.java:160-168`). Because
  `Actor.fixTime` pulls every actor back by a whole number (`…/actors/Actor.java:183-191`), the
  hero's time after a save is very often exactly 0, so `Actor.now() == 0` after restore — which
  re-triggers `Hero.act`'s "if we just loaded into a level" free `search(false)`
  (`…/actors/hero/Hero.java:872-879`) for any hero holding `Foresight` or the Talisman. A free
  search reveals secret doors and traps: the restored world has *less* hidden state than the
  snapshotted one, and a rollout host that restores per determinization launders secrets into the
  search.

**Fix in one sentence:** the restore path must, after `switchLevel` returns, restore
`hero.lastAction` from the snapshot and re-push a generator seeded from `mix(salt, k)`, and the
restore-and-replay test must include a Foresight hero and an interrupted Move.

### B7. Committed salts plus published seed sets make fairness a policy, not an architecture

ADR-0012, "Decision outcome", line 75:

> A Registration fixes: ... the salts (one per seed, drawn once and committed with the
> Registration)

and its pre-mortem, lines 108-109:

> The salts committed with a Registration let a Brain author tune to them. Mitigation: the Brain
> never sees a salt (AD-1, AD-2); a Results page for a public claim uses `holdout` or a fresh
> draw (SM-1).

The mitigation addresses the wrong actor. The Brain does not need to *see* a salt at run time; the
author reads the committed Registration and compiles a constant table. ADR-0007 lines 51-58 state
that the stream is `mix(salt, k)` and that "`java.util.Random`, the MX3 scramble
(`Random.java:57-66`) and SplitMix64 are all reproducible with no game imports". A `long[]` of
precomputed roll outcomes indexed by `(seedIndex, k)` is pure data in `brain`: no game import, no
`java.io`, no reflection, nothing ArchUnit can see.

The seed half has no mitigation at all. ADR-0006 line 79 withholds `Dungeon.seed` from the
Observation "so a Brain cannot fingerprint published seeds". Floor 1's layout *is* the fingerprint
and is necessarily in the Observation (the Observer emits terrain for every VISITED cell, ADR-0006
lines 62-63). A `standard` set of 500 published seeds is memorisable as a few kilobytes of hashes
of the first Observation, and a Brain can then play book moves.

The ADR also contradicts itself on the escape hatch. Line 88:

> The Rig refuses a Registration whose Seed set is `holdout`

against line 109, which requires a public claim to use `holdout`. Under line 88 no public claim can
be made at all; under line 109 the mitigation for salt-tuning is unreachable. One of the two
sentences is wrong and the whole fairness story for the Rig depends on which.

**Fix in one sentence:** derive each Run's salt inside the Rig from a Registration-level secret
published only *after* the comparison closes, keep per-Run salts out of the committed Registration
and out of the run-id, and resolve the `holdout` contradiction so public claims run on seeds the
Brain author has never seen.

---

## Should fix

### S1. ADR-0009: `Level.mobsToSpawn` and `respawner` are in the bundle and in no table row

`Level.storeInBundle` writes the exact ordered list of the next mob classes to spawn and the
respawn timer:

```
// …/levels/Level.java:476-477
bundle.put( "mobs_to_spawn", mobsToSpawn.toArray(new Class[0]));
bundle.put( "respawner", respawner );
```

`Level.createMob` pops from the head of that list (`…/levels/Level.java:508-516`). A rollout knows
that the next spawn is a Gnoll rather than a Crab, and roughly when it arrives. Not observable at
any screen. **Fix:** add a row that clears `mobs_to_spawn` and re-rolls the `respawner`'s
remaining delay from the Belief.

### S2. ADR-0009: other `saveGame` keys with no row

Read `Dungeon.saveGame` line by line, as the ADR's own pre-mortem promises to do at E6. Missing
from the table: `SpecialRoom.storeRoomsInBundle` and `SecretRoom.storeRoomsInBundle`
(`…/Dungeon.java:668-669`, restored `:779-780`) — the queues of which special and secret rooms
remain to be placed on floors not yet generated; `Statistics.spawnersAlive`
(`…/Statistics.java:198`); `droppedItems` per depth (`…/Dungeon.java:644-646`); `generatedLevels`
(`:675-679`); `chapters` (`:654-659`); `quickslot.storePlaceholders` (`:648`). The table's blanket
row 76 covers only "Generator decks, `LimitedDrops`, quest state". **Fix:** replace the prose row
with an explicit key list mirrored from `Dungeon.saveGame`, and add a coverage test that fails when
the bundle contains a key the scrubber does not name.

### S3. ADR-0009: "no random state is saved" is false

Line 18-20: "no random state is saved". `Dungeon.seed` is saved (`…/Dungeon.java:630`) and every
`Generator` category seed and drop counter is saved (`…/items/Generator.java:918-921`). Per
`docs/rules/rng.md` row 20, `cat.seed` plus `cat.dropped` fix "the sequence of item classes per
category ... regardless of whether the draw happens in levelgen or from a mob drop". So an
un-scrubbed rollout that kills a mob receives the exact item the real game would have dropped.
Row 76's "reset to the tag's defaults" is also ill-defined: `Generator.fullReset` does not restore
a default, it *draws* new seeds from whatever generator is on top
(`…/items/Generator.java:626-634`). **Fix:** correct the context paragraph, and specify that the
scrub writes `sample`-derived values into each `<cat>_seed` key rather than "resetting".

### S4. ADR-0009: `saveGame`/`saveLevel` cannot write to a byte array without a hook

Lines 40-43 choose "The game's own bundles: `Dungeon.saveGame` and `saveLevel` written to byte
arrays (through `Bundle.write(OutputStream)`)", with driver "Zero or one hook" (line 32) and
consequence "no second serialization" (line 110). Neither method exposes its `Bundle`: `saveGame`
ends at `FileUtils.bundleToFile(...)` (`…/Dungeon.java:691`) and `saveLevel` at `…/Dungeon.java:703`.
`Bundle.write(Bundle, OutputStream)` exists
(`SPD-classes/src/main/java/com/watabou/utils/Bundle.java:535-543`) but nothing hands you the
`Bundle` to pass to it. Either a hook splits construction from the write — and it is not budgeted
or listed in `docs/UPSTREAM.md` — or the Harness rebuilds the bundle itself, which is the second
serialization the ADR says it avoids. `saveGame` also swallows `IOException` and calls
`GamesInProgress.setUnknown` (`:693-696`), so a failed snapshot is silent. **Fix:** name the hook
(a `saveGameBundle()` / `saveLevelBundle()` extraction), budget it, and add the `docs/UPSTREAM.md`
row in the same ADR.

### S5. ADR-0009: snapshot bytes carry wall-clock time

`Dungeon.saveGame` writes `bundle.put( LAST_PLAYED, lastPlayed = Game.realTime)`
(`…/Dungeon.java:634`) and `bundle.put( VERSION, version = Game.versionCode )` (`:629`). A
Snapshot is therefore not a function of (tag, class, challenges, seed, salt, Action list). This
matters where FR-25's death gallery persists snapshots and where UJ-3's skeptic reproduces a Run
byte for byte. **Fix:** the snapshot writer zeroes `LAST_PLAYED` before storing or hashing, and the
ADR says so.

### S6. ADR-0009: UNKNOWN → WALL has second-order effects the "Why unchanged" column omits

Row 73 and consequence lines 117-119 argue only about mob pathing. Two further consequences:
`Level.buildFlagMaps` derives `openSpace`, which `Dungeon.findPassable` uses to confine LARGE
characters (`…/Dungeon.java:1044-1046`), so a giant beside the hero can be immobilised in the
rollout but not in reality; and `Terrain.WALL` erases `pit` and `avoid`, so a chasm one cell into
the fog stops existing. Both make the rollout systematically optimistic at the fog boundary. For
the record, the *hero's* pathing is not affected, and the ADR should say so and cite it:
`Hero.getCloser` already restricts the path to `p[i] && (v[i] || m[i])`
(`…/actors/hero/Hero.java:1809-1814`), which is the reason the substitution is defensible at all.
**Fix:** state the two effects in Consequences and cite `Hero.java:1809-1814` for the part that is
sound.

### S7. ADR-0010: the search leak test is circular and cannot detect B2, B3 or S1

Line 56: "Search leak test | FR-13: replacing the true hidden state with random alternates produces
identical Decisions over the `smoke` snapshots | whether the scrub of ADR-0009 is complete".

The "random alternates" are produced by a `BeliefSample` (ADR-0009 line 95), which is also what the
scrubber consumes. Anything not represented in `BeliefSample` — the seed, a visible mob's HP and
`state`, `mobs_to_spawn`, the `SpecialRoom` queues — is *constant* across alternates, so the
Decisions are trivially identical and the test passes. The test measures the scrubber against its
own vocabulary, which is exactly what it was written to check. **Fix:** generate alternates by
re-rolling the whole `Dungeon.saveGame` key set independently of `BeliefSample`, and add a
bundle-key coverage assertion that fails on any key the scrubber does not name.

### S8. ADR-0010: acceptance is gated on wall-clock, so the choice is not reproducible

Line 65: "each candidate is registered against option 2 on the `standard` set under the Per-pair
GSPRT (ADR-0012) at equal wall-clock budget per Decision". Wall clock makes the number of rollouts
a function of machine speed, JIT state and background load; two honest runs of the same
Registration on the same machine can accept and reject. ADR-0012 records the machine class, which
documents the dependence rather than removing it, and ADR-0010's own consequence (lines 84-85)
concedes it. Non-negotiable #5 says a run is fully determined by (tag, seed, action list); under a
wall-clock budget it is not. **Fix:** budget in a deterministic unit (rollouts or node expansions),
record the count in each `wait` record, and report wall-clock beside it.

### S9. ADR-0010: option 2 ships with no leak test and no named module

Lines 33-36 describe the one-ply expectimax as "fair by construction (Codex plus Observation)".
Nothing in the ADR says option 2 is implemented in `brain` over `api` types. "Score each against
every visible enemy's expected damage and hit chance" wants the enemy's HP, and the Observation
carries it only quantised to the health bar (`…/ui/HealthBar.java:65-88`, ADR-0006 line 66). If the
scorer is written in `harness` for speed, `Char.HP` is one field access away and no ArchUnit rule
fires. **Fix:** state that option 2 is a `brain` class consuming `api` types only, and add it to
the leak-test matrix at the same time as option 3.

### S10. ADR-0011: the chain covers wall-clock-dependent fields

Line 61: `chain_k = SHA-256(chain_{k-1} || canonical(record_k without prev, chain, think_ms))`.
Only `think_ms` is excluded, so the chained subset of a `wait` record includes `decision` with
`chosen.score`, `alternatives` and `flags` (line 53). ADR-0013 line 87 makes `THINKING` a
budget-overrun flag, and any anytime search's `score` and `alternatives` depend on how many
rollouts fitted in the budget. `mode` records (line 55) chain human UI timing. The claim at lines
22-23 — "Tamper-evident and canonical: the chain is recomputable from the file alone and equal on
every platform" — then holds only for a fixed-work Brain, which is the one case where it does not
matter. **Fix:** chain only the reproducible subset (`k`, `turn`, `depth`, `branch`, `obs`,
`sections`, `actor`, `action`) and carry `decision`, `flags` and `mode` outside the chain.

### S11. ADR-0011: `turn` is chained and is not stable across a restore or a floor change

Line 53 chains `turn` (fixed-point thousandths), which is `Actor.now()`. `Actor.fixTime` shifts it
by a whole number on every `saveAll` (`…/actors/Actor.java:170-192`, called from
`Dungeon.switchLevel` at `…/Dungeon.java:512` and from `…/scenes/GameScene.java:812`), and
`Actor.clear()` resets it to 0 on every load (`…/actors/Actor.java:162`). A Run replayed from a
mid-Run snapshot — ADR-0007's second determinism test, lines 138-139 — has different `turn` values
at the same `k` than the same Run replayed from the start, so the two produce different chains for
the same (tag, seed, salt, Action list). **Fix:** keep `turn` in the record and out of the chained
subset, or record it as a delta since the last floor change.

### S12. ADR-0011: the Replay refusal list omits the salt

Lines 65-66: "read the header, refuse a different `v`, `obsv`, `tag` or `profile`". `salt` is a
component of the Run tuple (ADR-0007 line 57: "The Run tuple becomes (tag, class, challenges,
seed, salt, Action list)") and it is the component that determines every combat roll. A Replay run
against a different salt will not refuse; it will report a hash mismatch at wait 1 and look like a
reproducibility failure of the engine. `class`, `challenges`, `seed` and `oracle` are missing for
the same reason. **Fix:** refuse on `v`, `obsv`, `tag`, `profile`, `class`, `challenges`, `seed`,
`salt` and `oracle`.

### S13. ADR-0011: the run-id puts the salt in a filename inside the game's Profile directory

Line 45: "`run-id = <tag>-<class>-<challenges>-<seedcode>-<salt>`; one file per Run under the Rig's
`--out` directory or the Overlay's Profile directory". The Profile directory is the directory
ADR-0007 (option 13, lines 95-107) hands to the game as its user directory, so the salt becomes a
filename in a directory the game process reads and writes. The only barrier is AD-1's ArchUnit ban
on `java.io` in `brain`, which is a module rule rather than a data rule, and which B1 already shows
can be routed around. **Fix:** run logs live outside the game's Profile directory and the run-id
uses an opaque id, with the salt only inside the header record.

### S14. ADR-0012: the Composite order rewards diving while wins are near zero

Decision driver line 26: "The gated statistic must not reward diving (SM-C2)". Option 4 is rejected
because "Depth difference ... rewards diving; depth stays inside the Composite order below bosses".
But the order at lines 34-37 is Win; then Score **only when both win**; then bosses killed; then
Floor depth; then turns. FR-21 assumes wins are near zero, and bosses killed ties for any two
Brains that both die in chapter 1 or 2. The operative term is therefore depth, ranked *above*
turns survived. A Brain that runs down the stairs and dies on floor 6 scores 1 against a Brain that
clears floors 1 to 5 carefully and dies on floor 5. The ADR rejects depth as the statistic and then
makes it the statistic. **Fix:** insert a survival or resource term above depth while the win rate
is below a registered threshold, and report the tie fraction per term so the operative term is
visible on every Results page.

### S15. ADR-0012: the GSPRT description is not Fishtest's and carries no citation

Lines 51-54 describe "GSPRT ported from Fishtest's `sprt.py`: the log-likelihood ratio uses the
normal approximation with the sample variance of the pair scores (approximation 2.1 in Van den
Bergh's note), regularized, with the per-pair increment clamped". Fishtest's GSPRT does not use the
raw sample variance; it uses constrained maximum-likelihood distributions under H0 and H1 over the
outcome multinomial. "Regularized" and "clamped" are doing undefined work, and there is no
`path:line` or vendored file to check any of it against — non-negotiable #8's discipline applied to
the one number the whole program trusts. **Fix:** vendor the reference implementation (or a
transcription with provenance) into the repository, cite it by `path:line` the way `docs/rules/`
does, and write the exact LLR formula into the ADR.

### S16. ADR-0013: the deadlock rule is contradicted by the ADR's own headless design

Line 103: "Shatterfish code never takes the scene monitor or any game object's monitor".
Lines 98-99: "the driver thread is the UI-role thread; it drives `scene.update(dt)`".
`GameScene.update()` is `public synchronized void update()` (`…/scenes/GameScene.java:838`), so
calling it takes the scene monitor; in the Overlay the same holds for any Panel drawn as a scene
member, whose `update` runs inside `GameScene.update`. The actor thread contends for that same
monitor in `addMobSprite` (`…/scenes/GameScene.java:1054`) and `sortMobSprites` (`:1063-1066`) —
the exact pairing the ADR's own context paragraph names as the source of the 2020 deadlock.
**Fix:** restate the rule as "takes no monitor the actor thread can block on while holding a
Shatterfish lock", and name `GameScene.update`'s monitor as one the UI-role thread necessarily
holds.

### S17. ADR-0013: making the toolbar and inventory pane inactive does not disable their hotkeys

Lines 90-91: "sets the toolbar and inventory pane inactive through the accessor row". `Button`'s
key listener tests the **raw** `active` field, not the inherited `isActive()`:

```
// …/ui/Button.java:117-137
KeyEvent.addKeyListener( keyListener = new Signal.Listener<KeyEvent>() {
    public boolean onSignal ( KeyEvent event ) {
        if ( active && KeyBindings.getActionForKey( event ) == keyAction()){ ... }
```

`Gizmo.isActive()` walks the parent chain
(`SPD-classes/src/main/java/com/watabou/noosa/Gizmo.java:83-85`); the raw `active` field does not.
Setting `toolbar.active = false` leaves every child button's own `active` true, so `WAIT`
(`…/ui/Toolbar.java:209`, running `Hero.rest(false)` at `:203`), `REST` (`:244`, `Hero.rest(true)`
at `:225`), the search button (`:296`, `:313`), `INVENTORY` (`:339`) and every quickslot
(`…/ui/QuickSlotButton.java:121-122`, `:185`) still fire from the keyboard and controller while
paused. Two controls are in neither container: `ActionIndicator`, which invokes hero abilities
(`…/ui/ActionIndicator.java:50`, `:137-140`), and `StatusPane`'s HERO_INFO button
(`…/ui/StatusPane.java:117-118`), which opens `WndHero` — and an open non-Prompt `Window` at an
Input wait is an assertion failure by ADR-0006 lines 56-58, so one keystroke in PAUSED crashes the
next observation. Pointer clicks *are* correctly blocked, because `PointerArea.onSignal` checks
`isActive()` (`SPD-classes/src/main/java/com/watabou/noosa/PointerArea.java:61`); only the keyboard
and controller paths leak. **Fix:** the accessor row must set each child `Button.active` (or
deregister the key listeners) rather than the container's, and must cover `ActionIndicator` and
`StatusPane`.

### S18. ADR-0013: human-Action recording misses every input that sets no `curAction`

Lines 92-93: "records each human Action from `Hero.curAction` after `handle` and from the
`Item.execute` notification (hook row 3)". The following change hero state without touching
`curAction` and without calling `Item.execute`:

| Input | Code |
|---|---|
| Wait / rest (toolbar or hotkey) | `Hero.rest` sets `resting` and spends time directly, `…/actors/hero/Hero.java:1456-1470`; callers `…/ui/Toolbar.java:203`, `:225`, `:238`, `:267`, `:275` |
| Search | `Dungeon.hero.search(true)`, `…/ui/Toolbar.java:296`, `:313` |
| Hero ability / talent action | `ActionIndicator.action.doAction()`, `…/ui/ActionIndicator.java:137-140` |
| Held movement key or stick | `moveFromActions` calls `handle` then `next` outside any listener, `…/scenes/CellSelector.java:415-417` |
| Cancelling a rest | `Dungeon.hero.resting = false` from the key listener, `…/scenes/CellSelector.java:331`, `:344-346` |
| Chasm jump confirmation | `Chasm.heroJump` from `getCloser`, `…/actors/hero/Hero.java:1838-1846` |

ADR-0013's own `ActionExecutor` row (line 79) lists `Hero.rest` and `Hero.search` as *executable*
Actions, so these are expressible and would not be caught by the `unsupported` record either — they
are simply dropped, and `verifiable` stays true for a Run that cannot be replayed. **Fix:** record
human Actions at the sinks (`Hero.rest`, `Hero.search`, `Hero.handle`, `Item.execute`,
`ActionIndicator.doAction`) rather than by polling `curAction`, and make any hero-affecting call
that reaches no recorder raise `unsupported`.

### S19. ADR-0013: a Decision tagged with `k` can still execute after the human takes over

Lines 128-129: "a Decision is tagged with its `k`; a stale Decision is logged as skipped and never
executed", with line 94: "`Take over` and `Hand back` apply at the next Input wait". If the
operator presses Take over during wait `k`, the take-over applies at `k+1`, so the bot's Decision
for `k` is *not* stale by the `k` test and executes — under `Fast as it can` (lines 86-87), on the
frame the future completes, which may be the same frame as the key press. **Fix:** tag Decisions
with `(k, modeEpoch)`, increment `modeEpoch` on every Take over and Hand back, and discard any
Decision whose epoch does not match.

### S20. ADR-0009: `Snapshot` has no `scrubbed` and no `oracle` field

Line 104: "the rollout host asserts a `scrubbed` flag set only by the scrubber (FR-6, FR-13)". The
`Snapshot` field list at line 94 is "opaque bytes plus `salt`, `k`, `profileVersion`, `tag`,
`schemaVersion`" — no `scrubbed`, no `oracle`. A flag that exists only in prose is not an
assertion. **Fix:** put `scrubbed` and `oracle` in the record, make `Redeterminer` the only
constructor of a `Snapshot` with `scrubbed` true, and add a test that the rollout host rejects a
`Snapshot` built any other way.

---

## Nit / citation

### N1. ADR-0013 cites the wrong lines and the wrong method for the Input-wait hook

Lines 65-67:

> the hook of ADR-0008 row 3 fires from `Hero.ready()` on the actor thread the first time the hero
> becomes ready (the branch that calls `Dungeon.observe()`, `…/actors/hero/Hero.java:935-946`)

`Hero.java:935-946` is `ready()`, which contains no `Dungeon.observe()` call:

```
// …/actors/hero/Hero.java:935-946
private void ready() {
    if (sprite.looping()) sprite.idle();
    curAction = null; damageInterrupt = true; waitOrPickup = false;
    ready = true; canSelfTrample = true;
    AttackIndicator.updateState();
    GameScene.ready();
}
```

The branch that calls `Dungeon.observe()` is `Hero.act`'s `if (!ready)` at
`…/actors/hero/Hero.java:840-848`. The two are not the same branch and do not fire on the same
condition: `!ready` fires on every wake of a hero part-way through a multi-cell Move (the
`curAction != null` path at `:883`), while `ready()` fires on *every* park wake-up because
`Actor.process` re-selects the parked hero and re-enters `act()` (`…/actors/Actor.java:249-299`,
`…/scenes/GameScene.java:883-888`). ADR-0013's pre-mortem (lines 126-128) asserts the first
behaviour while the Decision outcome specifies the second. Under non-negotiable #8 this is a
citation that does not say what the sentence says.

Two consequences to carry into the fix:

- The pre-mortem's fallback dedup — "the UI-role thread also checks that `k`'s Observation hash
  changed or an Action was executed since the last wait" — drops genuine Input waits. The
  Observation carries no turn counter by design (ADR-0006 line 79), so two consecutive waits can
  hash identically (a search that finds nothing, a wait-in-place with nothing visible moving). A
  dropped wait shifts every later `k`, and `k` is the second argument to `mix(salt, k)`
  (ADR-0007 lines 124-127), so the entire remaining random stream changes. Dedup by hash is a
  determinism bug, not a safety net.
- Line 70-71's justification — "While the hero is parked the actor thread only re-runs `ready()`,
  whose writes are idempotent, so the reads are as safe as the game's own HUD reads" — is wrong.
  The actor thread re-enters the whole of `Hero.act()` (`…/actors/hero/Hero.java:831-929`) on every
  1/60 s notify: `checkVisibleMobs()` (`:850`), `BuffIndicator.refreshHero()` and `refreshBoss()`
  (`:851-852`), `Barkskin.conditionallyAppend` (`:925`), and the `Actor.now() == 0` Foresight
  `search(false)` (`:872-879`), which *reveals secret doors and traps*. `Observer.observe()` on the
  UI-role thread therefore runs concurrently with level and hero mutation, and the AD-5 guard reads
  `Hero.ready`, a plain non-volatile field (`…/actors/hero/Hero.java:216`).

**Fix:** place the hook in the `!ready` branch at `…/actors/hero/Hero.java:840-848`, have it
publish a monotonically increasing wait counter rather than a boolean, make `Hero.ready` volatile
under the accessor row, and delete the hash-based dedup.

---

## Looked for and did not find

- **Raw model fields in `Observer`.** No change to ADR-0006's table in this batch; the visibility
  rows still route through `heroFOV`, `visited`, `mapped`, `Trap.visible`, `Heap.seen`,
  `Item.name()`, `levelKnown` and `cursedKnown`. Nothing in ADR-0009 to ADR-0013 widens the
  Observation itself. The leaks found here are all *around* `Observer` — `Snapshot`, rollouts, the
  salt — which is the failure mode a single-door architecture invites.
- **Effects (mind vision, magic mapping, blindness, darkness).** Untouched; ADR-0006 line 72 still
  routes them through `Dungeon.observe` and `Level.updateFieldOfView`, and none of the five ADRs
  recomputes FOV. The one place a rollout perturbs FOV — UNKNOWN → WALL — cannot *add* visibility,
  because any cell on the ray to a visible cell is itself visible.
- **Hero pathing parity under the scrub.** Checked and sound: `Hero.getCloser` already restricts
  the path to `p[i] && (v[i] || m[i])` (`…/actors/hero/Hero.java:1809-1814`), so walling UNKNOWN
  cells takes away no hero movement the real game would have allowed. `Dungeon.findPassable`
  (`…/Dungeon.java:1034-1058`) uses the true `Level.passable` only for callers that pass it, and the
  hero is not one of them.
- **Oracle reachability.** ADR-0006 lines 81-84 keep `OracleObserver` in `harness`, constructed only
  by the launcher flag, with `header.oracle = true` inside the Observation so its hashes differ;
  ADR-0012 line 88 has the Rig refuse `oracle` Runs; ADR-0011 line 52 chains the header's `oracle`
  field. I found no path in these five ADRs by which oracle data reaches a Decision without the
  flag, and none by which the Rig accepts an oracle Run. The red border and "ORACLE" label required
  by `docs/fairness.md` are not restated in ADR-0013's Panel section — a documentation gap, not a
  leak.
- **New module dependencies and ArchUnit evasion.** No `build.gradle` change in this batch; no
  `org.shatterfish.brain..` class naming `com.shatteredpixel` or `com.watabou`; no reflection or
  class-name string in the ADRs' own text. B1 is not an ArchUnit bypass — it is a data channel
  ArchUnit was never asked about.
- **Leak, differential, toggle and determinism coverage for `Observer`.** No `Observer` change is
  proposed here, so no new `Observer` tests are owed. The tests that *are* owed and missing are the
  redetermination coverage in S7 and the restore-and-replay coverage in B6.
- **Hooks.** ADR-0013 reuses ADR-0008 row 3 (Input-wait notification) and row 7 (sprite motion
  interval); ADR-0009 claims zero or one. S4 shows ADR-0009 needs an unlisted hook, and B4 and B5
  show ADR-0013 needs one it has not budgeted (a `CellSelector.select` guard). No ADR in this batch
  adds a `docs/UPSTREAM.md` row, and two of them will have to.
- **Determinism from time, threads, hash order and environment.** Hash-map iteration order is
  handled by ADR-0007 option 10 and unchanged here. Wall clock: found in three places (`LAST_PLAYED`
  at S5, chained `flags` and `score` at S10, chained `turn` at S11). Thread scheduling: found at N1
  (wait detection) and B5 (`processKeyHold`). Environment: the standard Profile of ADR-0007 option
  13 still covers language and `bones.dat`, and `Bones.get` returns nothing for a seeded Run
  (`…/Bones.java:197-200`), which I re-read and confirmed.
