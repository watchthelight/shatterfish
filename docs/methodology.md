# Methodology

How a Shatterfish Run is defined, and how anyone can check that one happened the way it says it did.
Nothing on this page asks you to trust the code: the definitions are written out, and the numbers
are ones you can recompute yourself in any language.

## What a Run is

A Run is determined by its **tuple**:

| Part | What it is |
|---|---|
| Upstream tag | The Shattered Pixel Dungeon release the game code comes from, `v4.0.0` today |
| Hero class | Warrior, Mage, Rogue, Huntress, Duelist or Cleric (`core/…/actors/hero/HeroClass.java:87-92`) |
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

The game's six, cycled, are `WARRIOR, MAGE, ROGUE, HUNTRESS, DUELIST, CLERIC` -- the order the
game declares them in (`core/…/actors/hero/HeroClass.java:87-92`) -- taken as `classes[i % 6]`
for the triple at index `i`. Five of them are badge-locked for a profile that has played nothing
(`core/…/actors/hero/HeroClass.java:330-347`), so the Profile grants those badges deliberately
and every Run a set names can be started. That is the menu a player reaches by having played, and
it changes nothing the bot may read.
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

## Running many Runs

```
./gradlew :rig:run --args="--brain random --seeds smoke --parallel 4 --out runs/2026-09-22"
```

One process hosts one Run. The game keeps its state in statics — the dungeon, the statistics, the
badges, the scene — so two Runs in one process share the thing that defines them; the parent plays
nothing and starts a child for each Run, each in a working directory of its own. Each child makes
its own Profile, because a process makes exactly one: that is a consequence of one Run per process
rather than something the Rig arranges, and it is why the Rig can say nothing about a Profile
beyond having put each Run in a process of its own. `--parallel` defaults to one process per core
the machine reports, and whatever was used is written into the summary rather than assumed by a
reader.

The salt is drawn when a Run executes, not before, and written into that Run's log. It is in no
registration and is not derived from the tuple, so a Brain's author cannot precompute the stream
their Brain will face — and it is never handed to the Brain. A Decider that wants randomness is
seeded from the Run's own triple, which is what a person at that screen has; seeding one from the
salt would let it compute the game's coming draws from the mixing function published above.

An invocation writes two files beside the logs (three, if it is refused). `runs.jsonl` holds one line per Run — its id, its
log, its state, its final chain, and why it ended if it did not finish. A Run is written down when
it is **dispatched**, not when it ends: a Run that crashed is a measurement that failed and counts
as *incomplete*, whose pair scores a tie, while a Run that simply vanished would not be counted at
all and would quietly bias whatever was left. `summary.json` holds the counts, the wall clock and
the measured throughput.

A Run that passes its deadline is killed and counted incomplete, and its partial log stays on disk
— a killed Run's evidence is the thing the Rig must not lose. Whatever a failed child printed is
kept beside its log as `<run-id>.err`.

There is no oracle flag on this command line, in any spelling, and an argument the Rig does not
know is refused by name rather than ignored. Nothing in the Rig can build an oracle observer, reach
a class by its name, or read an environment variable, which an architecture test holds. And the
runner reads every finished log's header back and fails the whole invocation if one claims the
oracle. The three are not redundancy: the first two are properties of the code and the last is a
property of the artifact, and each survives a different mistake.

A refused invocation publishes nothing, and the folder says so rather than merely lacking a
summary: every index line is marked `REFUSED` and a `refused.json` is written beside them. The logs
are already on disk by then — they are written as Runs are dispatched, on purpose — so the honest
thing is to mark them, not to pretend they are absent.

### What it costs, measured

Measured on this project's development machine — 24 cores, JDK 21, Windows — with the random agent,
which is the Baseline every Brain is compared against. Both invocations played every Run to its
natural ending.

| Set | Runs | Processes | Wall clock | Runs/s | Waits/s | Waits |
|---|---|---|---|---|---|---|
| `smoke` | 25 | 24 | 13.5 s | 1.85 | 133 | 1,797 |
| `standard` | 500 | 24 | 161 s | 3.10 | 213 | 34,348 |

Two things in those numbers are worth saying plainly.

**A 500-Run acceptance invocation takes under three minutes.** The requirement was that it fit
overnight on the development laptop; it fits in a coffee break. Seed-set sizes are therefore not
constrained by throughput at this Brain's speed, which is what ADR-0018 said to revisit once the
cost was measured rather than guessed.

**The process start dominates a short Run.** A random Warrior dies in about seventy waits, and E1
measured roughly 400 waits/s in a single warm process — yet twenty-four processes deliver 133. The
per-Run JVM start is the floor, which is why the smoke set is *slower* per Run than the standard set
that keeps the pool saturated. A Brain that thinks harder will shift that balance; a Brain that
plays longer Runs will shift it further. The number to plan a comparison from is Runs per second at
the set size being used, not waits per second.

## Registration

**The hypothesis is fixed before the numbers are seen.** A comparison can otherwise be run, looked
at, and then adjusted — a bound, a seed set, a stopping rule — until the answer is the wanted one,
with nothing recording that anything was adjusted. So a Registration is written first, committed to
the repository, and hashed; and every Run played under it carries that hash in its own log.

A Registration lives at `registrations/<id>.json` and fixes:

| | |
|---|---|
| `hypothesis` | the id, which is also the file name and what a Results page cites |
| `claim` | what is being asserted, in one sentence, for a person |
| `brain_b` | the Brain being measured: name, commit, configuration hash |
| `brain_a` | the Brain it is measured against — **absent for a baseline** |
| `seed_set`, `seed_version` | which Runs, and what that set's name meant at the time |
| `alpha_per_mil`, `beta_per_mil` | the error rates, in thousandths |
| `burn_in`, `maximum` | pairs before a stop is allowed, and pairs after which it is undecided |
| `budget_ms` | the per-Decision budget, or 0 when the comparison does not constrain thinking time |
| `machine_class` | what the numbers were measured on, because throughput is not portable |
| `release_level` | whether this claims a release-level result, which is the only kind that may touch the held-out set |

**A baseline is a Registration too.** ADR-0012 describes comparisons and every field it lists is
about one, but E3's own goal is a published *baseline* and the nightly job compares nothing at all —
it runs the smoke set so that a change which breaks the harness is seen the night it lands. So
`brain_a` is optional: one Brain fixes a baseline, two fix a comparison, and both are hypotheses
stated in advance.

**The salt is not in it, and that is the point.** The mixing function is published on this page, so
a salt known in advance lets a Brain's author compute the game's coming draws as pure data — whether
the next attack hits, what the next chest holds, where the next floor puts its stairs (ADR-0007). A
Registration is written before the Runs and is public, so anything in it is something the Brain's
author has. The salts are drawn when each pair executes, written into both Run logs, and appear
nowhere else: late enough to be useless to a Brain, early enough to be replayable.

**What enforces that is structural, and it is worth being precise about which part does what.** A
Registration has no field for a salt; a file carrying a member the record does not have is refused
when the Rig reads it; and the value is drawn from a secret at the moment a Run executes, so there
is nothing to write down in advance even for somebody who wanted to. On top of those, the record
refuses a *shape*: a run of eight or more hex digits in any field that holds prose, because a salt
is sixteen of them and a word like "salt" is not what one looks like. An earlier draft checked for
the word instead, which could not catch the thing it was named for and would have refused a machine
class of `basalt-ci`.

**Git is the authority on "before".** A file cannot make a claim about time about itself — any bytes
on disk could have been written a second ago. The Rig asks git whether the path is tracked and
whether the working copy differs, and then hashes *the committed bytes* — the text `git show
HEAD:<path>` returns — so an edit made after the Runs changes nothing the Rig used. The commit the
Registration was read at goes into the ledger. What a Run log's header carries is the **stamp**: the
id and the first sixteen digits of that hash, like `H-0001-nightly-smoke@a4d7fe89a612e89b`. A header
naming only the id would let the file it names be pointed at different bytes afterwards.

`registration` is a chained field, so **a Run's chain depends on the hypothesis it ran under**: the
same tuple played under two Registrations reaches two different chains. That is deliberate — the
hypothesis is part of what the Run was — and it means a Replay carries the original's stamp through,
which is why it reproduces.

**What a Registration does *not* prove.** A Run log's header is checked for the stamp's *shape* and
nothing more: no reader confirms that the Registration named exists, or that its hash is the hash of
anything committed. A log can therefore claim a hypothesis it was not run under, and would verify
cleanly. What catches that is the Rig's own index and this ledger, both written by the invocation
rather than by the Run — and, for a published number, a reader who checks the three against each
other.

### The held-out set, and its budget

`holdout` is never run during development. `SeedSets.load` refuses it outright and states the whole
of FR-20 in its refusal; the one way past that door is a Registration claiming a release-level
result, which makes the Rig use `publish` instead — and `publish` demands the reason, which is the
hypothesis. **At most one use per Brain version**, where a Brain's version is the commit and
configuration of the Brain that actually ran, not the one the Registration mentions: counting the
budget against a document would let it be spent again by editing the document.

### The ledger

`registrations/ledger.jsonl` records every invocation under a Registration — finished, refused and
forbidden alike. A number means something different depending on how many times the question was
asked before it gave that answer, and the invocations nobody published leave no other trace at all.
A ledger that recorded only the runs somebody was happy with would be the exact opposite of the
count FR-25 asks for.

It is append-only and committed, which makes it tamper-*evident* rather than tamper-proof: a deleted
line shows in a diff. That is what a repository can honestly offer. A ledger with a line nobody can
read is refused rather than appended to — a count with a hole in it is not a smaller count, it is an
unknown one. And what HEAD holds must still be a **prefix** of what is on disk: the working copy is
dirty by design, because appending is the Rig's job, but committed lines may not disappear. Without
that, deleting one file restored every budget the file recorded.

**A held-out use is claimed before the set is read**, not recorded once the Runs finish. `publish`
opening the set is the moment it is spent — those triples have been seen, whatever happens next —
and recording it at the end meant that killing the process left the set played and the ledger
silent. A *refusal* spends nothing, because burning a Brain version's single allowance on a typo
would be a rule punishing the wrong thing.

**A Brain version is the commit that last changed the Brain's own source**, not the repository's
HEAD. Otherwise a README typo mints a fresh allowance for an unchanged Brain, and FR-20's cap of one
is uncapped in practice.

**What it costs.** The pre-flight asks git three questions and reads the ledger: **146 ms**, against
a smoke invocation of 6,438 ms (2.3%) and a standard invocation of about 160,000 ms (0.09%). The Rig
prints the number on every ranked invocation, so it is measured rather than remembered.

```sh
./gradlew :rig:run --args="--brain random --seeds smoke --out <dir> --registration H-0001-nightly-smoke"
```

An invocation that names no Registration still runs — that is the normal case during development —
and says so: its logs carry an empty registration, and its summary records one, so a folder of
numbers can never be quietly adopted as a measurement of something afterwards.

## Comparing two Brains

```sh
./gradlew :rig:run --args="--brain <candidate> --against <baseline> --seeds standard --out <dir> --registration <id>"
```

**A pair is two Runs of one triple under one salt.** The Rig draws the salt once per triple and
hands it to both Brains, so whatever the dungeon does at a given moment it does to both — the
strongest common-random-numbers design available. The two sides write into `<dir>/candidate/` and
`<dir>/baseline/`, each with its own index and summary, and `<dir>/comparison.json` holds the
verdict.

**The pair score** compares the two Composite outcomes in order: Win; then Score, only between two
wins (a losing Run's Score rewards gold picked up on the way to dying); then bosses killed, above
depth so that diving past nothing is not rewarded; then Floor depth; then turns survived. The first
step that differs decides, and the pair scores 1, ½ or 0 from the candidate's side.

**A pair with a missing Run scores ½ and is counted.** Missing means the log is absent, unreadable,
has no ending, or ended by something other than the game: only `DEATH` and `WIN` are endings the
game reached. A Run stopped at the turn cap is the Rig stopping it (ADR-0012), and scored on turns
survived a Brain that stalled until the cap would beat every death at the same depth, so it counts
as missing too. A half is not a free pass, though. The fairness review of story 3.6 found that a
Brain which crashes on exactly the seeds it would lose turns losses into ties, and ties both raise
the mean and cut the variance — enough to buy an accept. So a comparison's Registration states
`missing_per_mil`, the share of consumed pairs that may be missing, and past it the verdict is
`VOID`: a result nobody may cite, whichever way the LLR pointed. A result where every consumed pair
is missing is void whatever the cap.

**The test** is the generalized sequential probability ratio test between two hypotheses about the
mean pair score, H0: `p0` and H1: `p1`, both stated in the Registration. It uses Michel Van den
Bergh's normal approximation to the generalized log-likelihood ratio (*A simple approximation for
the GSPRT*, eq. 2.1):

```
LLR(N) = N · (p1 − p0) · (2μ̂ − p0 − p1) / (2σ̂²)
```

where μ̂ and σ̂² are the mean and variance of the N pair scores, every zero count replaced by a
thousandth of a pair so that a sample with one outcome missing has a variance to divide by. The test
accepts at `log((1−β)/α)`, rejects at `log(β/(1−α))`, never stops before the burn-in `n0`, and is
undecided at the maximum `nmax` or when the Seed set runs out first. An LLR past a bound is reported
as the bound, and `clamped` says when it went more than three percent past.

**Pairs are consumed in the Seed set's order**, never in the order the Runs finished. Completion
order depends on which Runs were slow, which depends on how they went, and a sequential test fed in
that order would be stopping on information it was not supposed to have yet. The Rig currently plays
every pair and then evaluates the test over them in order; `stopped_at` says where the test would
have stopped, and the pairs after it are played but not counted. `consumed_better`,
`consumed_equal` and `consumed_worse` count the pairs up to the stop, which are the counts the
reported LLR is a function of.

**A run of ties is evidence for H0**, not an absence of evidence. Two identical Brains tie every
pair, and a mean of exactly one half with almost no variance says as clearly as data can that the
candidate is not better: the test rejects at the burn-in. `random` against `random` is how the
pairing itself is checked — the Decider is seeded from the triple and the pair shares the salt, so
both halves are the same Run, and `RunnerComparisonTest` holds each pair's two logs to one salt and
one chain, read from what each child wrote rather than from the parent's index.

**`smoke` is a direction check.** ADR-0012 lets only `standard` and `bosses` accept. A comparison on
any other set still runs the test and prints its trace, but `comparison.json` says
`"direction_check":true` and the ledger note says "(direction check)": its ACCEPT is a direction to
look in, not a result.

**Only a Registration can test.** The bounds are the hypothesis, and bounds chosen after the pairs
were seen are what FR-22 exists to prevent. An unranked comparison scores and counts its pairs and
says `"tested":false`.

**Where the implementation comes from.** ADR-0012 chose Fishtest's GSPRT. Fishtest carries no
licence, so its code is all rights reserved and none of it is in this repository. `Gsprt` implements
the published formula, and `tools/gsprt_reference.py` — ours — imports a Fishtest checkout pinned at
`2e540196ed8a72283a17f40793defd0f4a45d9c9` and asks its own `sprt.set_state` for the bounds, the
LLR, the raw LLR and the clamp flag of 435 cases (five hypotheses, three error rates, twenty-nine
sets of counts including tie-heavy small samples); `GsprtReferenceTest` holds `Gsprt` to every one
of them and pins the fixture's SHA-256.

To be precise about what that proves: `Gsprt` matches the approximation `set_state` computes. It is
not what Fishtest's server stops on, which is the exact generalized LLR (`LLR_logistic`) over the
same counts. The two agree closely in the region a test normally stops in and can disagree in the
tails, and ADR-0012 chose eq. 2.1 for being closed-form and reproducible from the counts by hand.
Moving to the exact form is in `docs/ideas.md`.

**What it costs.** The `smoke` set, 24 processes, cap 400: one Brain plays its 25 Runs in 7,538 ms;
a comparison plays 50 Runs in 9,996 ms. Twice the Runs for a third more time, because the pool is
fuller.

`comparison.json` reports what pairing bought — `turns_correlation_micros`, the within-pair
correlation of turns survived over the `correlated_pairs` both Runs of which ended, and
`identical_pairs`, the pairs whose two Runs ended alike in every part of the Composite outcome — and
writes every pair (seed, salt, both run ids, `score_halves`, `missing`), the
Registration stamp, `direction_check`, and — when a Registration stated bounds — the test's
parameters (`p0_micros`, `p1_micros`, `burn_in`, `maximum`, `missing_per_mil`), the bounds, the
LLR and its trace, `clamped`, `stopped_at`, the consumed counts and the `verdict` (`ACCEPT`,
`REJECT`, `UNDECIDED` or `VOID`). Numbers are in millionths: no number this project writes down is a
float. It also records the SHA-256 of each side's `runs.jsonl`, and `--verify <dir>` on a comparison
folder verifies both sides' logs and checks each index still hashes to what the verdict was written
over, so the Runs a verdict rests on cannot be swapped underneath it. The ledger line for a ranked
comparison carries the verdict and the baseline, so the ledger can say how many attempts were
rejected before one was accepted.

## Calibrating the bounds

```sh
./gradlew :rig:calibrate
```

`Gsprt`'s error rates are nominal: its guarantees are asymptotic, and the pair score it runs on is
three-valued, often tied, and sometimes missing. FR-21 asks for the realized rates to be measured on
this project's own outcomes before a bound is trusted, so story 3.7 measured them.

**What it is bootstrapped from.** `calibration/v4.0.0-random-standard.jsonl` holds the Composite
outcome of each of the 500 Runs of the random Brain on `standard` (version 1, turn cap 20,000),
with each Run's chain and, in its first line, the tag, the Brain's name, commit and configuration,
the Seed set, the command and the SHA-256 of the run index it was read from. The 37 MB of logs are
not committed; the table is. `./gradlew :rig:calibrate --args="<root> extract <runs folder>"`
writes a table from a folder the Rig wrote, and refuses a folder whose Runs disagree on the tag,
the Brain or the cap, whose index does not cover its Seed set, or whose index gives a Run a chain
its log does not end on.

**H0 and H1.** A simulated pair is two independent draws from the table, scored by `PairScore`:
two Brains of equal strength, paired as pessimistically as possible, since two different Brains
diverge at their first differing Decision and correlation only shrinks the variance. Its mean is
one half by symmetry, a missing pair scoring one half too. H1 is the same stream with a share of
its *reached* pairs made wins; a missing pair stays missing, because a better Brain does not stop
meeting the windows the Harness cannot handle. With `m` the share of pairs missing, the share
`q = 2(p1 − ½)/(1 − m)` puts the mean at exactly `p1`, and the page prints each cell's simulated
H1 mean beside its `p1` to show it does. Each cell of a grid — `p1` of 0.55, 0.60 and 0.65, `n0`
of 10, 20 and 40, a missing cap of 0.10, 0.20 or 0.25, with `p0` = 0.50, α = β = 0.05 and `nmax`
= 500 — runs `Gsprt` over the same 10,000 sequences per hypothesis, from a `SplittableRandom` with
a fixed seed. The page it writes is a generated file, and `CalibrationTest` fails when a fresh
render differs from the committed one.

**What it found.** Under H0, 18.2% of pairs tie, and almost all of that is missing Runs: 16.1% of
pairs have a Run the game did not end, because the random Brain meets a window the Harness does not
know in 8.4% of its Runs (`UNKNOWN_WINDOW`), and only 2.1% of pairs tie with both Runs reached.
The statistic resolves; what it cannot absorb is a missing cap near its own missing rate: at a cap
of 0.10 nearly every result is void, and at 0.20 about one in six still is. `p1` = 0.55 leaves 30%
of sequences undecided at 500 pairs. And `n0` = 10 is not enough: the normal approximation
overshoots, and the realized false-accept rate is 6.1% at `p1` = 0.60 against a nominal 5%.

**The chosen bounds**, by a rule fixed in code before the grid was first run — of the cells within
the margin and accepting H1 at least 90% of the time, the smallest `p1`, then the smallest missing
cap, then the fewest pairs under H1:

| `p0` | `p1` | α | β | `n0` | `nmax` | missing cap |
|---|---|---|---|---|---|---|
| 0.500 | 0.600 | 0.050 | 0.050 | 20 | 500 | 0.250 |

A cell chosen as the best of twenty-seven would flatter itself if its rates were quoted from the
sequences it was chosen on, so they are quoted from 10,000 fresh ones: **realized false-accept
5.73%, realized false-reject 3.99%**, H1 accepted 92.6% of the time, 108 pairs on average under H0
and 102 under H1. The false-accept rate is above the nominal 5%, by more than the Monte-Carlo error
and less than the margin: the test accepts a Brain that is not better slightly more often than α
says, and this page says so rather than rounding it away. An equal-strength comparison comes out
void about one time in thirty (3.3%), from the missing cap alone. The full grid is on the
[calibration page](results/calibration-v4.0.0.md).

**The margin.** A realized error rate is within calibration when it is at most its nominal rate
plus **0.010**. Ten thousand sequences put the Monte-Carlo standard error of a rate near 0.05 at
about 0.0022, so the margin is four and a half standard errors. It is declared here, before story
3.8's e-process exists, so that the e-process is judged by a number it did not choose. A void
result is neither error, which is why the rule also asks for power: a cell that voids most of its
results is within the margin and useless.

**Which test gates: the e-process beside it.** ADR-0012 kept an alternative in reserve, and story
3.8 measured it on the same fresh sequences. `EProcess` is a betting e-process (Ian Waudby-Smith
and Aaditya Ramdas, *Estimating means of bounded random variables by betting*, JRSS B, 2024): a
gambler starting at wealth 1 bets, before each pair, a fraction λ of it that the pair scores above
`p0`, sized from the pairs already seen by their aGRAPA rule `λ = (μ̂ − p0)/(σ̂² + (μ̂ − p0)²)` — an
approximation to the growth-optimal bet — and clipped to [0, ½/p0], which at `p0` = ½ is [0, 1]. If
the candidate is not better, no such bet makes money on average, and Ville's inequality bounds the
probability that the wealth *ever* reaches 1/α by α — at any stopping time, with no burn-in and no
alternative. A second gambler bets the mean is below `p1`, clipped to [0, ½/(1 − `p1`)], and stops
the test for futility at 1/β; futility is the one place `p1` enters. The e-process reports the log
wealth of whichever gambler is ahead, the futility one negated, so its verdict can be checked
against its trace and its bounds, log(1/α) and −log(1/β), as a GSPRT's can — but in
`comparison.json`, where it lands in `llr_micros` and `trace_micros`, it is a log wealth and not a
likelihood ratio, which is what the `statistic` field is there to say.

At the chosen bounds the two compare like this, on the same 10,000 fresh sequences:

| | false-accept | false-reject | power | mean pairs (H0) | mean pairs (H1) |
|---|---|---|---|---|---|
| GSPRT (`n0` = 20) | 5.73% | 3.99% | 92.6% | 108 | 102 |
| e-process | 2.21% | 2.29% | 93.6% | 163 | 164 |

At the chosen `p1` the e-process keeps its promise with room to spare and loses no power, and it
pays for both in pairs: about 50% more under H0 and 60% more under H1. It is not as strong
everywhere: at `p1` = 0.55 its power is 37% against the GSPRT's 66%, with 59% of H1 sequences still
undecided at 500 pairs — a recalibration toward finer differences would meet that first. The rule ADR-0012 set was that the e-process replaces the GSPRT only
if the GSPRT's realized error exceeds its nominal rate by more than the margin; 5.73% is within 5% +
0.010, so **the GSPRT stays the gate**. `SequentialTest.GATE` says so, `CalibrationTest` holds it to
what the calibration concludes, and a later tag whose calibration goes the other way fails the build
until the gate changes with it. The e-process stays in the tree behind that constant, and every run
of `./gradlew :rig:calibrate` measures both again; `comparison.json` names the `statistic` its
bounds and trace belong to.

**Held to it.** A comparison's Registration states the `statistic` its bounds are for, and the Rig
refuses one registered for a statistic other than the gate. A comparison on a Seed set that may
accept — `standard`, `bosses`, or the held-out set for a release-level claim — must state exactly the calibrated bounds (`p0`, `p1`, α, β, `n0`,
`nmax` and the missing cap above) or it is refused and counted in the ledger as `FORBIDDEN`. A
direction check on `smoke` may state what it likes, since it cannot accept anything. The first
comparisons run under these rules are on the [worse-Brain page](results/2026-09-23-worse-brain.md).

**What the missing cap costs.** A cap of one pair in four is the price of the Harness's unknown
windows: it is what lets a comparison of today's Brains conclude at all, and it is also room for a
Brain to crash on a quarter of the seeds it would lose. When the Harness learns the windows the
random Brain meets, the missing rate falls, the calibration is re-run, and the cap comes down with
it — a new Registration, never an edit.

## How the Runs ended: the death gallery

```sh
./gradlew :rig:gallery --args="<folder> --snapshots 5"
```

When an invocation completes, the Rig writes `gallery.md` beside each side's summary: the Runs
grouped by how they ended and at what depth, largest group first, with each group's count and share
and, for every Run, its seed code, class, turns survived and a link to its log. The ending is read
from the Run's own log through the harness's reader, not from the run index, and nothing is dropped:
a Run with no log is grouped as `NO_LOG`, a log that stops before an ending as `NO_ENDING`, and one
that cannot be read as `UNREADABLE`, so the counts add up to the index's. The biggest group is the
next thing to fix. The command above rewrites the gallery for any folder the Rig wrote (for a
comparison, one per side) and, with `--snapshots N`, writes the last N waits of every Run into
`snapshots/`, linked from the gallery: what the Run was doing when it ended. The same gallery for two
Brains side by side, FR-26's per-Brain view, is E4's. The random Baseline's is in
[`results/2026-09-23-H-0002/gallery.md`](https://github.com/watchthelight/shatterfish/blob/main/results/2026-09-23-H-0002/gallery.md);
its links name logs that are not committed.

## The Run log and its chain

Every Run writes `<run-id>.jsonl`: one record per line, plain text, no compression, readable with
`grep` and a text editor. The run id is
`<tag>-<class>-<challenges>-<seedcode>-<salt>-<brain>`, and the Brain is part of it because a
comparison plays two Brains on the same triple under the same salt -- without it, a pair's two Runs
would agree on every other part and write to one file.

**The header states what the Run was**, which is the tuple -- upstream tag, hero class,
challenges, seed, salt -- and then the three things that decide what this build *meant* by it: the
Observation schema version, the Profile version, and the turn cap. The cap is there because a Run
stopped at turn 20,000 and a Run stopped at turn 150 are different Runs even on one tuple, and a
Replay that did not know which would reproduce every wait and then end differently. That is how it
was found: story 3.4's round-trip test reproduced a Run exactly, disagreed on the ending alone, and
the cap went into the header (schema version 2) rather than into a comment explaining the
disagreement.

Each line carries a chain value over itself and everything before it, so a byte changed anywhere
breaks every chain from that record on. The point of publishing the rules below is that the chain
can be recomputed by something that has never seen this repository: a shell script with `sha256sum`
is enough.

### Canonical JSON

A record is one JSON object on one line, written so that two writers of the same values produce the
same bytes:

- every object's keys are sorted by their UTF-16 code units, whatever order they were given in,
  and no key appears twice in one object;
- there is no whitespace anywhere outside a string;
- every number is a whole number -- no floats, no exponents, no leading `+`, no `-0`. A turn is
  thousandths of a turn; a Decision's score is ten-thousandths; a Run's score is the game's own
  whole points. Two machines agreeing on a float's text is a thing to hope for rather than rely on;
- a **salt** is not a number. It is sixteen lower-case hex digits in a string, the same sixteen the
  file name carries. A salt is drawn across the whole 64-bit range, and a JSON number above 2^53 is
  silently rounded by every reader built on IEEE doubles -- which is most of the scripts this page
  invites you to write;
- the file is UTF-8, line feeds only, and ends with one.

**Strings, exactly.** "The escapes JSON requires" is not a specification -- JSON permits `\u000a`
wherever it permits `\n` -- so here is the whole rule, which is what a second implementation has to
match byte for byte:

| Character | Written as |
|---|---|
| `"` | `\"` |
| `\` | `\\` |
| backspace, form feed, line feed, carriage return, tab | `\b` `\f` `\n` `\r` `\t` |
| any other character below U+0020 | `\u00xx`, **lower-case** hex |
| an unpaired surrogate | `\uxxxx`, lower-case hex -- raw it would not survive as the same UTF-8 |
| a matched surrogate pair | both characters, raw, as UTF-8 |
| everything else, including `/` and every non-ASCII character | raw, as UTF-8 |

**Absent, empty, or always written.** The general rule "a field with nothing to say is absent" was
wrong about this format in both directions, so here is the per-field truth:

| Field | When it has nothing to say |
|---|---|
| `decision`, `belief`, `highlights` (on a wait) | the key is absent |
| `prev` (on the header) | the key is absent -- nothing comes before it |
| `registration` (on the header) | written as `""` |
| `alternatives`, `flags` (inside a decision) | written as `[]` |
| `machine`, `started` | always written, and never chained |
| `cap` (on the header) | always written; a Run has no unbounded form |

No field is ever `null`.

### The chain

```
chained(record) = the record's canonical JSON with these keys removed:
                  prev, chain                 from a record of any kind
                  machine, started            from a header
                  think_ms                    from a wait
chain(header)   = SHA-256( utf8(chained(header)) )
chain(record_k) = SHA-256( bytes(chain_{k-1}) || utf8(chained(record_k)) )
```

**Per kind, and this matters.** `think_ms` is a wait's field and `machine` and `started` are the
header's, so a checker that struck those names from every record alike would leave every other
record in the log with three names' worth of bytes the chain does not cover. A `mode` record
carrying `"think_ms":"anything at all"` would hash as though the member were not there. The first
implementation of this in Shatterfish did exactly that, and the rule is stated this way so that a
third implementation does not. A record carrying an excluded key that its own kind does not have is
not a record this format can chain: refuse it rather than hash the text without it.

A key is matched **as it is written**, not as it decodes. The writer escapes nothing in a key, so
`"\u0070rev"` is not `prev` -- it is a key the writer would never have produced, and a file holding
one is refused rather than stripped. Without that rule a `sed`-based checker and a decoding one
disagree about the same file, which is the one thing a published format may not permit.

`bytes(...)` is the previous chain as its thirty-two raw bytes, not as its sixty-four hex
characters. Every line then carries `chain`, and every line after the header also carries `prev`,
which repeats the line before it -- so a forger who edits a field and recomputes that one line's
own chain is caught by the next line's `prev`.

The five excluded keys are excluded because they say *when* and *where* rather than *what*:
`think_ms` is how long the decider took, `machine` and `started` are the header's own, and `prev`
and `chain` are the envelope. So the same Run recorded on a slow laptop and a fast server chains
identically, and nothing excluded is needed to replay the Run -- which is the test of whether a
field belongs on that list.

**What the chain proves, and what it does not.** It proves the file is internally consistent: no
record was changed, removed from the middle, or reordered after it was written, because every chain
is over everything before it and each line repeats the one before it in `prev`. It does **not**
prove the file was not written from scratch afterwards -- the rules on this page are enough to
forge a whole log that verifies perfectly. A chain becomes evidence only when its final value is
recorded somewhere its author does not control, which is what the Registration committed before the
first Run (story 3.5) and the Rig's own index (story 3.3) are for.

Nor is truncation detectable from the log alone: every prefix of a valid log is itself a valid log.
That is what makes a killed Run readable, and it means "incomplete" is a claim the log cannot
refute -- so a reader has a second question to ask beyond whether the chain verifies, which is
whether the file begins with a header and ends with an `end` record.

### Test vector

One header, alone, with the values below, chains to the value in the last row. Strip the five keys,
hash the remaining text as UTF-8, and you should get the same:

| What | Value |
|---|---|
| The record | `{"brain":{"commit":"def5678","config":"0000000000000000000000000000000000000000000000000000000000000000","name":"random"},"cap":20000,"challenges":0,"class":"WARRIOR","codex":8,"commit":"abc1234","machine":"a laptop","obsv":2,"oracle":false,"profile":3,"registration":"","salt":"0000000000000007","seed":12345,"seedcode":"AAA-AAA-SGV","started":"2026-09-22T12:00:00Z","t":"header","tag":"v4.0.0","v":2}` |
| Chained (the same, without `machine` and `started`) | `{"brain":{"commit":"def5678","config":"0000000000000000000000000000000000000000000000000000000000000000","name":"random"},"cap":20000,"challenges":0,"class":"WARRIOR","codex":8,"commit":"abc1234","obsv":2,"oracle":false,"profile":3,"registration":"","salt":"0000000000000007","seed":12345,"seedcode":"AAA-AAA-SGV","t":"header","tag":"v4.0.0","v":2}` |
| `chain` | `aa72885f80d8f4cad6626e9e85c99390e82d38e86802a4811edfaff1fabb21c2` |

`RunLogVectorTest` recomputes this table from the code on every build, so the page cannot drift
away from what the writer does.

### Replay

A **Replay is a Run whose decider is the log.** At each Input wait it checks that this build is
seeing the Observation the log recorded, hands back the Action that was taken, and writes its own
log as it goes. It plays through the same loop, the same executor, the same driver and the same
Profile as the Run it is checking: a reproduction through a different path would prove less, and a
second loop would be a second set of rules about what a Run is.

**The comparison is the two chains.** A chain covers everything about a Run except how long the
decider took and which machine it ran on, so a Replay that arrives at the same value has reproduced
every Observation, every Action, every section hash, the turn counts and the ending -- in one value
a person can check by eye. Comparing Observation hashes wait by wait is what *names* a divergence;
comparing chains is what says there was none. Both are reported, and they answer different
questions: a log whose Actions were changed reproduces every Observation and then reaches a
different chain, which a wait-by-wait check alone would pass.

**Four things are refused before a Run is started**, because a difference in any of them makes a
comparison meaningless rather than negative: the log schema version, the upstream tag, the
Observation schema version and the Profile version. Each decides what a Run *is*. A log whose chain
does not verify is refused for the same reason -- it describes a Run that never happened, and
replaying it would measure this build against a fiction.

**What a Replay proves** is that this build, on this machine, plays the tuple in the log the way the
log says it was played. It does not prove the log describes a Run anybody performed: the rules on
this page are enough to write one from scratch, and four of this story's own tests do exactly that.
The forgery is caught by playing it.

Two commands, and they cost very different things:

```sh
./gradlew :rig:run --args="--verify <folder>"        # every log in a folder, against its own bytes
./gradlew :rig:run --args="--replay <log> --out <folder>"   # one Run, played again
```

`--verify` recomputes every chain, checks each against the chain the run index published for that
Run, checks that no log's header claims the oracle, that each file is named for the Run its own
header states, and that each log begins with a header and holds exactly one. It plays nothing.

It does **not** require a Run to have finished unless you ask with `--finished`. Every prefix of a
valid log is a valid log, a folder of five hundred Runs normally holds a few that were killed, and
the run index already counts those as incomplete — failing the folder for them would make a busy
machine look like a dishonest one. The nightly job passes `--finished`, because a reference log
truncated to its header verifies perfectly and would then be "replayed" as a one-line Run that
reports success on both platforms.

**A folder with no `runs.jsonl` is refused.** Without the index nothing in the folder is held
against a published value, so the command is comparing each file with itself — and deleting one
file is the cheapest way to launder a folder that was edited. A folder in that state has not been
verified; it has been read, and `--verify` says so rather than printing a clean pass. `--replay` takes one log rather than a folder, because a Replay is a Run and AD-6
gives a Run its own process: the game's state is static and process-wide, so a command that
replayed a folder in one JVM would be measuring the order the logs went in.

**What they cost.** Measured on the standard set, 500 Runs of the random Brain, 24 processes, cap
2000, on the machine the E3 numbers above come from:

| | Time | Per Run |
|---|---|---|
| Playing the 500 Runs | 159,797 ms | 320 ms |
| `--verify` over the folder | 1,402 ms | 2.8 ms |
| `--replay` of one Run (31 waits) | 1,025 ms | -- |

So **verifying a folder costs about one percent of producing it**, single-threaded and with no game
booted, which is what makes it something to run on every folder rather than on a sample. **Replaying
costs what running costs**, because it is running: the same loop, the same game, one process. The
two are not alternatives. Verification answers "was this file changed after it was written" for
everything; a Replay answers "does this build still do what this log describes" for one Run at a
time, and the nightly cross-platform job spends that cost on a committed reference log so that a
difference between Windows and Linux is found by the project rather than by a reader.

### What the chain does not prove

The header's `tag`, `commit`, `brain` and `registration` are supplied by whoever started the Run:
the driver has no checkout to read a commit from and no Registration to read an id from. They are
*attested*, not verified, and the chain shows only that nobody changed them after the Run.

**A Replay does not check three of them.** It refuses a log whose `tag` is not this build's, because
the tag is the game's own rules and a comparison across two of them means nothing. It takes
`commit`, `brain` and `registration` from the log and writes them into its own — it has to, or no
other checkout could ever reach the log's chain, which is the whole of what the cross-platform job
does. So a log whose attestations were rewritten and rechained replays to the same chain and is
reported as reproduced, correctly: *the Run reproduced*. Nothing about who played it was checked,
and a reproduction should never be read as saying otherwise. `--replay` prints the commit of the
build that did the replaying next to the commit the log attests, because those are two different
facts and only one of them is evidence about the machine in front of you.

What makes an attestation worth anything is the Registration committed before the first Run
(story 3.5), which puts the claim somewhere its author does not control. A Replay is evidence about
this build and this machine; it is not evidence about a stranger's.

A Run that ends without an `end` record is *incomplete* -- killed, crashed, or timed out. Its
prefix still reads and still verifies as far as it goes, and the Rig counts it as incomplete and
scores its pair as a tie, so a Brain cannot improve its standing by failing.

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
