---
status: accepted
date: 2026-09-21
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0018: Seed sets are derived, committed and versioned, and `holdout` is guarded at the door

## Context and problem statement

Every number the Rig will publish is measured on a set of Runs (PRD FR-19 to FR-26, NFR-2). A
skeptic who cannot obtain the exact set cannot check the number, so a Results page has to name the
set and a Registration has to version it (AD-11, AD-13). Before this story nothing in the
repository named a set: there was nothing for a Results page to cite and nothing for a
Registration to pin.

FR-20 fixes what the sets are — `smoke` 25, `standard` 500, `holdout` 500, `bosses` 100, `goo` 400
(Warrior only, no challenge flags) — and two testable consequences: a Results file names the set
and its version, and the Rig refuses a development comparison on `holdout`. What FR-20 does not
say is where the triples come from, what a set is as a value, and where the `holdout` refusal
lives. This decides those three, for every set the program will ever add.

Non-negotiables touched: #5 (everything measured and reproducible), #8 (Codex over folklore), and
#1 by omission — a Seed set is a set of tuples a player could type, and holds nothing a Run
produces.

## Decision drivers

- **A stranger can reproduce the set**, not merely download it. The whole argument of a published
  number is that no step of it has to be taken on trust.
- **A set is a type and a version, not a filename.** A Results page cites `standard` at version 1
  and that has to fix the size, the classes and the flags.
- **CI catches a hand edit**, in the file and in the derivation, naming what to run.
- **Every triple is in the game's own domains, read from the pinned code** and not from memory.
- **`holdout` cannot leak into development**, including by accident, including by a path someone
  adds later without reading this page.
- **The rig does not depend on game code.** ADR-0003's edges stand: `rig → harness, brain`, and
  now `api` explicitly, because a Seed set is an `api` value.

## Considered options

1. **Drawn once from an unseeded source and committed.** The obvious thing: draw 1,525 seeds,
   commit them, move on. Rejected on driver 1. The file becomes the only evidence, and nothing in
   it says the draw was not repeated until it flattered someone. This is the same argument the
   Codex makes about its tables (ADR-0017), and it is the reason that folder is generated rather
   than typed.
2. **Drawn from a stated seed with `java.util.Random`, committed.** Reproducible by anyone with a
   JVM, and by nobody else: `Random`'s stream is a Java specification, so a skeptic working in
   Python has to reimplement it before they can check a single triple. Rejected on driver 1, but
   only just — it is the second-best option here.
3. **Drawn from the game's own `randomSeed`** (`core/…/utils/DungeonSeed.java:41-49`). Rejected
   twice over: it needs game code on the rig's classpath, and it draws only from the 21⁹ codes
   that hold no vowel, which is the game's shareability rule for the seeds *it* invents and not a
   property a measurement set should inherit.
4. **The first *n* seeds, or an arithmetic progression.** Maximally checkable and rejected on
   measurement grounds: seeds near each other are not independent in any way anyone has measured,
   `0` is a seed whose dungeon nobody should have to argue about, and a set that is an obvious
   pattern invites the suspicion that the pattern was chosen.
5. **Derived from a named constant and the triple's index through the project's own published mix
   function.** Chosen. See below.
6. **Not committed at all: derived at every invocation.** Rejected on drivers 2 and 3. A set
   nobody has written down cannot be diffed, cannot be versioned by a Registration, and changes
   silently when the derivation does.

For `holdout`, the refusal could live in the runner, in the Registration check, in a naming
convention, or at the read. Chosen: at the read. A guard in the runner is a guard the next runner
does not have; a guard in the Registration check is a guard a smoke run skips; a naming convention
is not a guard. A `load` that refuses is the narrowest place, and it is the only door.

## Decision outcome

**A Seed set is an `api` value with a schema version.** `SeedSet` (name, version, entries) and
`SeedSet.Entry` (seed, hero class, challenge flags, seed code) are records in `api`, written by
`RigJson` — the Rig's canonical writer, beside the Codex's `CodexJson` and using the same
`JsonWriter` — one entry per line, so a drift diff names the triple that changed. `api` has no
JSON reader by rule (story 2.1), so the reader of a committed set is `SeedSets` in `rig`, and it
reads the one canonical shape rather than JSON at large: the file is generated, every deviation
from the shape is a hand edit, and a refusal naming the line is worth more than a parse that
accepts a file the writer would never produce. The same rule puts the resolution of a hero class's
*name* into the rig: a method in `api` that turns text into a value of the schema is a reader, and
only a reader of a committed file can meet a class the game never had. The schema version is 1 and belongs to the Seed
set, not to the Codex and not to the tag; it changes when what a name means changes.

**Every component is in the game's own domain, read from the pinned code.** A seed is at least 0
and under `26⁹ = 5,429,503,678,976` (`core/…/utils/DungeonSeed.java:31`); a hero class is one of
the game's six, in the game's declaration order (`core/…/actors/hero/HeroClass.java:87-92`);
challenge flags are 0 through `Challenges.MAX_VALUE = 511` (`core/…/Challenges.java:30-40`). The
records refuse anything else where the value is built. The bounds live in `api` as mirrors,
because `api` depends on nothing, and `SeedSetsTest` reads the pinned declarations as text and
holds the mirrors against them, so a mirror that drifts at an upgrade fails at the tag that caused
it rather than quietly bounding a set wrongly.

**Each triple carries the seed's code beside the number.** `5429503678975` is what the engine
takes; `ZZZ-ZZZ-ZZZ` is what a person types into the game's own custom-seed window, and that is
what a stranger checking a published number will do. The code is nine base-26 digits with `A` for
zero, which is the inverse of the game's own `convertFromCode`
(`core/…/utils/DungeonSeed.java:52-75`) and agrees with its `convertToCode`
(`core/…/utils/DungeonSeed.java:77-105`) everywhere; both directions and both ends of the range
are tested, the upper end against the example the pinned file states itself.

**The derivation.** For a set named `name` and a triple at index `i`:

```
constant(name) = the name's ASCII bytes, big-endian, in the low bytes of a 64-bit word
seed(name, i)  = floorMod(mix(constant(name), i), 5429503678976)
class(name, i) = the set's classes, cycled: classes[i mod classes.size()]
flags(name, i) = the set's flags, which are 0 in schema version 1
mix(s, k)      = splitmix64_finalize(s + k · 0x9E3779B97F4A7C15)   (ADR-0007)
```

Three properties make this the chosen option. It is **reproducible by anyone**: `mix` is
SplitMix64's step and finalizer, which the methodology page publishes with a test vector, so a
skeptic in any language can recompute all 1,525 triples and check the committed files rather than
believe them. It has **no free parameter**: the constant is the set's own name and nothing else,
so a new set's constant is forced the moment it is named and nobody can shop for one. And it
reuses **the project's own function** rather than inventing a second mixer, so there is one thing
to publish, one test vector, and one thing that can be wrong.

| Set | Size | Constant | Hero classes | Challenge flags |
|---|---|---|---|---|
| `smoke` | 25 | `0x736D6F6B65` (`"smoke"`) | the six, cycled | none |
| `standard` | 500 | `0x7374616E64617264` (`"standard"`) | the six, cycled | none |
| `holdout` | 500 | `0x686F6C646F7574` (`"holdout"`) | the six, cycled | none |
| `bosses` | 100 | `0x626F73736573` (`"bosses"`) | the six, cycled | none |
| `goo` | 400 | `0x676F6F` (`"goo"`) | Warrior only | none |

The sizes are FR-20's. The classes and the flags are this story's: the four general sets cycle the
game's six in its own declaration order, so a comparison is not a claim about one class, and `goo`
is Warrior with no flags because that is the E4 gate FR-20 names. A size that is not a multiple of
six gives the first classes in the cycle one triple more than the last; that is stated rather than
corrected, because the sizes are fixed here and the cycle is the part a reviewer can check.

**One task writes every set.** `./gradlew :rig:seeds` runs `org.shatterfish.rig.Seeds` on the
rig's main classpath and writes `seeds/<name>.json` at the repository root, deleting any file in
that folder it no longer writes and refusing a directory it did not create — the pattern
`:codex:generate` set, for the same reasons (ADR-0017). Regenerating and committing is the only
way a set changes. `SeedSetsTest` compares the committed bytes with a fresh derivation and names
the first differing file and this command; `seeds/` is declared an input of `:rig:test`, so a hand
edit re-runs the check instead of leaving a cached green run standing over a file nothing read;
and `.gitattributes` pins the folder to line feeds, so a Windows checkout does not fail the
comparison for a reason that has nothing to do with the seeds.

**`goo`'s size is the PRD's bound, computed.** An observed 75% Goo-kill rate over 400 Runs has a
95% Wilson lower bound of 0.7053, above the 0.70 the E4 gate requires; over 300 it is 0.6980 and
does not clear it. `SeedSetsTest` computes both from the formula rather than asserting the number,
so a later ADR that shrinks the set has to face the arithmetic.

**`holdout` is guarded at the read.** `SeedSets.load(root, name)` is what development calls and it
refuses `holdout` outright, naming the set and what it is for. The only way to read it is
`SeedSets.publish(root, name, reason)`, which requires the reason it is being published and hands
it back on the value, so the Results page has the record FR-20 requires. A set that has been run
during development is no longer held out, whatever anyone intended by the run, and there is no way
to un-run it — which is why the refusal is at the door rather than in a runner that would have to
remember. `SeedSetsTest` also holds that `holdout` shares no triple with the four sets development
runs.

**The contract E3.5 and E3.10 consume.** A Registration carries the Seed set *name and version*
and never the salts (AD-11); a Results page prints the same pair. Because a name and a version fix
the size, the hero classes and the challenge flags, that pair is enough for a stranger to
reconstruct exactly which Runs a number was measured on — from the committed file, or from the
derivation above if they do not trust the file. Nothing else about a set may be cited in its place.

### Consequences

- Good: a published number can be checked without this repository. The set is a formula, the
  committed file is a convenience, and CI proves they agree.
- Good: one door to a set, so the `holdout` rule is enforced by the type system and not by
  discipline; adding a later reader means either calling `load` and inheriting the refusal or
  writing a second door, which is a visible thing to review.
- Good: a set's identity is (name, version), so a size revision is a version bump and every
  Results page that cited the old pair still says what it measured.
- Bad: the seed code's encoding is written in `api` rather than called from the game, because `api`
  depends on nothing. Two implementations of one mapping can diverge. Mitigated three ways: the
  test pins both directions, the two ends of the range and the pinned file's own example; the
  bounds are read from the pinned declarations at every test run; and a Run is still started
  through the game's own `convertToCode` in the harness, so a divergence would refuse the Run
  rather than play a different one.
- Bad: the sizes are not derived from anything. FR-20 set them before throughput was measured, and
  this ADR does not revise them — E1 measured about 400 Input waits per second per process and
  8,609 turns per second on one machine (`docs/results/e1-throughput.md`), which is the number a
  revision will argue from once E3 has run the sets in parallel.
- Bad: a set whose name is longer than eight ASCII letters has no constant, so the naming rule is
  part of the derivation. The definition refuses one rather than truncating.

## Pre-mortem

*If this is wrong in six months, why?*

- **The sizes are wrong for the machine.** `standard` at 500 pairs may be minutes or hours
  depending on parallelism and Run length. Mitigation: FR-20 already says the sizes are revisable
  by ADR once throughput is measured; the version bump is the mechanism and this page names it.
- **Someone needs challenge flags.** Version 1 has none, and a challenge set is a plausible E4 or
  E7 need. Mitigation: `Definition` already carries the flags and the records already accept them,
  so a challenge set is a new name and a version bump, not a schema change.
- **The cycled classes turn out to be the wrong design** — a per-class comparison wants a
  single-class set, and cycling six classes inside `standard` makes every comparison an average
  over classes with about 83 Runs each. Mitigation: the per-class sets are new names under the same
  derivation; nothing here has to change for them to exist.
- **`holdout` leaks anyway**, through a test fixture, a `--seeds holdout` command line, or a copy
  of the file. Mitigation: the refusal is at the read and the test holds it in both directions;
  the runner story (E3.2) inherits it because there is no other way in, and its own review should
  look for a second door.
- **The derivation's constant collides with something.** Two sets whose names differ give
  unrelated streams, and the committed files are checked for a repeated triple within a set and
  for overlap between `holdout` and the development sets. If a future set overlaps another, the
  test that would have caught it has to be extended to the new pair, which is the kind of thing a
  reviewer forgets. Mitigation: noted here, and the overlap test is written over
  `SeedSets.names()` rather than a hard-coded list.
