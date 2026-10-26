# Throughput with the Brain attached, at 7b1212eea

**The measurement [E1 throughput](e1-throughput.md) deferred** ("The benchmark with a Brain attached is E4's"), made
for story 4.14. It is a rate on one machine, not an acceptance, and nobody else's machine will give the same figures.

Shatterfish `main` at `7b1212eea`, upstream `v4.0.2`, Profile 6, the `shatterfish` Brain on `weights/shatterfish.json`,
Oracle off, the Run log on (hash chain and JSON written for every wait), the debug record off, turn cap 20,000.

**Machine.** Intel Core Ultra 9 275HX (24 logical processors), 31.4 GB, Windows 11 Pro 10.0.26200, Zulu OpenJDK
21.0.11, Gradle 9.5.0. **The machine was not idle.** Other agent sessions were building and running Rigs of their own
throughout (5 to 8 Java processes were alive at the start and end of the single-process run, one to three of them
Gradle; the processor load read 64% when it started). The figures below are therefore a conservative reading: an
isolated machine should be at least as fast. The rig plays each Run in its own JVM, so every figure includes the
start-up and shutdown of that JVM, the Brain's decisions, the Observer and the log.

## Command

The E1 benchmark's launcher (`:harness:benchmark`) plays only the random agent, so the Brain's rate was taken from the
Rig, which is the loop the Brain is played in. One Run at a time, the first 20 Warriors of the tuning set:

```sh
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 20 --salt-base 1000 --jobs 1 --out build/direction/p6-tp"
```

Waits are the `wait` records of each Run log (an applied Action each, as in E1), summed over the 20 Run logs; the wall
time is from the creation of the first Run's files to the last write of the last log (Gradle's start-up and
compilation excluded; Gradle reported 5 m 12 s for the whole task).

## What the numbers say

| | Brain, 1 process | random agent (E1) |
|---|---|---|
| Runs | 20 | 200 |
| Input waits | 28,915 | 13,230 |
| wall time | 304.4 s | 33.1 s |
| **Input waits per second per process** | **95.0** | 399.5 |
| Runs per minute | 3.94 | 362.3 |
| game turns per second | 135 | 8,609 |
| cost per wait | 10.5 ms | 2.5 ms |
| median Run length | about 1,400 waits (to the death of a Warrior on depths 1 to 7) | 64 waits (floor one) |

- The Brain-attached rate is about **95 waits per second per process**, 24% of the random agent's 399.5. The two are
  not the same workload: a random Run on floor one is 64 waits; a Brain Run goes to a median of about 1,400 waits across
  five or six floors, with a decision (the Policy chain, Belief and, in a fight, the search) at each, plus the log's
  hash and JSON at each (E1 measured that at about +37% over the Observer alone).
- Per process, a Brain Run costs about 15 seconds. The E1 page's rule for sizing seed sets (the wait rate times the
  cores) now has a Brain figure to use: 24 logical processors at 95 waits per second would be about 2,300 waits per
  second if they scaled perfectly. They do not (see below).
- **Six Runs at a time.** The same Runs under `--jobs 6` (the Goo baseline's second batch, 80 Runs, 95,138 waits, 3 m
  16 s of Gradle wall time, start-up included) gave about **485 waits per second across the pool, about 81 per
  process**. The first batch (40 Runs, 57,685 waits, 7 m 50 s) gave only 123 per second across the pool while other
  agents were also running, and the first attempt at it (14 m 24 s) lost a Run to the operating system running out of
  memory to commit. Wall time on this host swings by a factor of four with what else is running, so a PR that quotes
  its minutes should say what else was on the machine.
- **Determinism across job counts.** The 20 Runs played here with `--jobs 1` ended on the same hash chain as the same 20
  Runs played inside the `--jobs 6` batch: 20 of 20 equal.

## Not measured here

- Per-Observation costs (the Observer's read, the codec and the JSON writer) are E1's and were not re-timed.
- The search's own cost is inside these figures, not apart from them; the direction table's "search waits per floor"
  (about 18) says how often it ran.
- An idle machine. The figures above should be repeated once the other sessions are quiet, and the page revised if
  they differ by more than the factor above.
