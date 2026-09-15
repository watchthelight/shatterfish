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

## What is not yet true

A Run is reproducible in its draws and not yet in its outcome. With the salt controlling every draw
from the game's initialisation onwards and the history emptied per Run, two Runs of one tuple still
diverge: one floor-one item lands a cell apart, and the cell creeps by one with each Run in a
process. A value that creeps is counted rather than drawn, so the cause is not the random stream —
it is iteration order over identity hashes, which the next story removes along with a determinism
test that spans two processes. Until that lands, treat a published Run's numbers as reproducible in
what the game rolled and not in what the bot met. The tracking issue is #70.
