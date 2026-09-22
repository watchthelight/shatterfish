# Methodology

How a Shatterfish Run is defined, and how anyone can check that one happened the way it says it did.
Nothing on this page asks you to trust the code: the definitions are written out, and the numbers
are ones you can recompute yourself in any language.

## What a Run is

A Run is determined by its **tuple**:

| Part | What it is |
|---|---|
| Upstream tag | The Shattered Pixel Dungeon release the game code comes from, `v4.0.0` today |
| Hero class | Warrior, Mage, Rogue or Huntress |
| Challenges | The challenge flags the game was started with |
| Seed | The seed a player could type into the custom-seed window; it decides the dungeon |
| Salt | A 64-bit number the runner chooses, which decides what the game draws once play begins |
| Action list | What was done at each Input wait |

Two Runs with the same tuple are the same Run. The seed and the salt do different jobs and this is
the distinction the rest of the page rests on: **the seed makes the floor, the salt makes the
rolls.** The same seed under two salts gives the same first floor and different outcomes from the
first turn onwards. The floors below are generated once a Run is under way, with the salt's
generator on the stack; their layout comes from the seed (the game pushes a seed derived from it for
each floor's shape), but this page does not yet claim more than that about them.

The salt is written down with the Run and is never shown to the bot. A bot that had it could
compute the next roll, so it is treated as the one number that would end the only rule of play —
`SaltLeakTest` looks for it in every Observation a Run produces, in the text a person reads and in
the bytes the hash is taken over.

## The mix

At Input wait `k`, a Run salted `s` draws from a generator seeded:

```
mix(s, k) = splitmix64_finalize(s + k * 0x9E3779B97F4A7C15)
```

where `splitmix64_finalize` is SplitMix64's own finalizer, with **unsigned** right shifts:

```
z ^= z >>> 30;
z *= 0xBF58476D1CE4E5B9;
z ^= z >>> 27;
z *= 0x94D049BB133111EB;
z ^= z >>> 31;
```

All arithmetic is 64-bit and wraps. Nothing here is Shatterfish's invention, which is the point: any
implementation of SplitMix64 computes the same function, so the numbers below can be checked against
something that has never seen this repository.

### Test vector

| Salt | Wait `k` | `mix(salt, k)` |
|---|---|---|
| `0000000000000000` | 0 | `0000000000000000` |
| `0000000000000000` | 1 | `e220a8397b1dcdaf` |
| `0000000000000001` | 0 | `5692161d100b05e5` |
| `0123456789abcdef` | 0 | `b2c058e4ebb5112c` |
| `0123456789abcdef` | 1 | `157a3807a48faa9d` |
| `ffffffffffffffff` | `ffffffffffffffff` | `de0a564cbcd060c4` |

`MixTestVectorTest` holds the implementation to this table, and holds the table to a second
implementation written from the definition above rather than from the code. If the two ever
disagree, one of them is wrong and this page is what a skeptic reads, so the disagreement is settled
before either changes.

Here is the same function in Python, which is enough to check the table:

```python
M = (1 << 64) - 1

def mix(salt, k):
    z = (salt + k * 0x9E3779B97F4A7C15) & M
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & M
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & M
    return (z ^ (z >> 31)) & M
```

### What happens to the value afterwards

The game scrambles every seed it is handed through MX3 before Java's own generator sees it
(`SPD-classes/src/main/java/com/watabou/utils/Random.java:55-65` at the pinned tag). That is the
game's business and not ours: the vector above is of the mix, and stops where the mix stops.

## Why reseed at every wait

A Run could have been made reproducible by counting draws, but then a Replay would break whenever a
version of the game drew a different number of times for the same visible outcome — a change in an
animation, an extra roll behind a decision. Reseeding at each Input wait from a number the Run
declares makes reproducibility independent of how many draws anything makes. The cost, stated
plainly: a Shatterfish Run's combat rolls are not the rolls a human would get from the same seed.
Rigs compare Brains against each other under the same regime, so this does not bias a comparison.

## The Profile

A game depends on more than its seed. Shatterfish therefore gives every Run its own directory and
fills it deliberately; a directory prepared by a different version of this file is refused rather
than played against, because a Run recorded under one Profile and replayed under another is not the
same Run.

**Profile version 2** is: fresh preferences, and on top of them English strings, the intro off, the
support prompt already answered, the compact interface, the waterskin in a quickslot, and an empty
history. The preferences are cleared per Run because the game writes its own during play — dragging
the waterskin out of a quickslot turns off the setting that slots it for every game after, and the
hero records the vault's warning — so a process that has played many Runs would otherwise start the
next one with a different hero screen from a fresh process's; the two-process determinism test found
exactly that. The history is emptied rather than merely unread: the game's own loaders each return
early once a process has called them, so the harness clears the badges, deletes every journal page
and drops the rankings through the loaders' own public calls. Version 1, which inherited a process's
preferences, was never published against.

Two of those deserve their reasons. The **compact interface** is the one a phone player uses; the
full interface hands an item selector to an inventory pane that a headless Run draws nowhere and no
Action can name, so a Run that tried to use such an item would stop there. The **empty history**
matters because the game's own behaviour reads it: a snake stops dodging after four misses only once
the first boss has been slain (`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/actors/mobs/Snake.java:66`).

The Profile is part of the tuple in the sense that matters: change it and the Runs recorded against
the old one are no longer comparable, which is why it carries a version and the version is in every
results page.

## The Seed sets

Every number the Rig publishes is measured on a **Seed set**: a committed, versioned file of
(seed, hero class, challenge flags) triples, with each seed's `@@@-@@@-@@@` code beside the number
because that is what you type into the game's own custom-seed window. A Results page names the set
and its version and nothing else, and that pair fixes the size, the classes and the flags
([ADR-0018](adr/0018-seed-sets.md)).

| Set | Size | Derivation constant | Hero classes | Challenge flags |
|---|---|---|---|---|
| `smoke` | 25 | `495757257573` (`0x736D6F6B65`) | the game's six, cycled | none |
| `standard` | 500 | `8319381538418553444` (`0x7374616E64617264`) | the game's six, cycled | none |
| `holdout` | 500 | `29395908910085492` (`0x686F6C646F7574`) | the game's six, cycled | none |
| `bosses` | 100 | `108230817834355` (`0x626F73736573`) | the game's six, cycled | none |
| `goo` | 400 | `6778735` (`0x676F6F`) | Warrior only | none |

**The sets are derived, not drawn**, and this is the part that does not ask you to trust anything.
A set drawn from an unseeded source and committed is a set whose only evidence is the file, and
nothing in the file says the draw was not repeated until it flattered someone. Instead, the triple
at index `i` of the set named `name` is:

```
constant = the name's ASCII letters, big-endian, in the low bytes of a 64-bit word
seed     = floorMod(mix(constant, i), 5429503678976)
class    = the set's classes, cycled: classes[i mod classes.size()]
flags    = the set's flags, which are 0 at seed-set schema version 1
```

where `mix` is the same function as above and `5429503678976` is `26^9`, the number of seeds the
game has (`core/…/utils/DungeonSeed.java:31`). Two details matter if you are recomputing this:
`mix` returns a **signed** 64-bit value, and `floorMod` is the non-negative remainder, so a
negative mix still gives a seed in range. The constant is the set's own name and nothing else, so
there is no free parameter anybody could have chosen: naming a set fixes its seeds.

### Test vector

The first triple of each set, which is enough to check an implementation of the whole derivation:

| Set | Constant | `i` | Seed | Code | Class |
|---|---|---|---|---|---|
| `smoke` | `495757257573` | 0 | `3343871708117` | `QAI-OCF-LGF` | Warrior |
| `standard` | `8319381538418553444` | 0 | `648322377831` | `DCS-SHA-XYL` | Warrior |
| `holdout` | `29395908910085492` | 0 | `464228844029` | `CFU-TZK-DFP` | Warrior |
| `bosses` | `108230817834355` | 0 | `3315639145122` | `PWV-DWY-CUS` | Warrior |
| `goo` | `6778735` | 0 | `381980784027` | `BVO-NOB-RKL` | Warrior |

A code is nine base-26 digits, most significant first, with `A` for zero: the number a code means
is the game's own `convertFromCode` (`core/…/utils/DungeonSeed.java:52-75`), so `AAA-AAA-AAA` is
0 and `ZZZ-ZZZ-ZZZ` is 5,429,503,678,975.

The committed files under `seeds/` are therefore a convenience rather than an authority. One
command writes all five — `./gradlew :rig:seeds` — and the build compares what is committed with a
fresh derivation, so a hand-edited set fails naming the file and that command.

**`holdout` is different.** It exists to publish a release-level number, at most once per Brain
version, and every use is recorded in the Results. A set that has been run during development is
no longer held out, and there is no way to un-run it, so the refusal is at the only door that
reads a set: the development read refuses `holdout` outright, and the only other way in takes the
reason it is being published and carries that reason onto the page. `standard` and `holdout` are
derived from different constants and are checked to share no triple.

## What is shown, and what is not

The same tuple, played by the same policy, gives the same Observation hash at every wait — twice in
one process, after that process has played a thousand other Runs, and in two other processes that
share nothing with the first but the code and the tuple. `DeterminismTwoJvmTest` asks all three,
thirty waits deep, on every pull request. The item that used to move between Runs of one tuple was
the guidebook, placed from a generator the game deliberately leaves unseeded; hook row 6 seeds it
from the floor's own seed, and issue #70 closed with that.

Two things are not shown here. A two-process test on one machine cannot see identity-hash order at
all: two JVMs started the same way give the same objects the same hashes, so the three ordering
sites of row 6 are held by `IdentityOrderTest` for what they are, and the first test that could see
them by behaviour is the cross-platform comparison, the same tuple on Windows and on Linux, which is
story 3.4's nightly job. And row 6 does not reach everything: `Random.element` over class-keyed
collections and the copies `Actor.all()` and `Actor.chars()` return still walk by identity, which
issue #73 records with the callers named. Treat a published Run's numbers as reproducible on the
machine that produced them, and read the nightly page before treating them as more.
