# Profile 6 tuning tables at 7b1212eea

**A direction check, not an acceptance.** Nothing here ran under a Registration and no sequential test was run. Pages
that quoted Profile 4 or Profile 5 numbers were voided when the Run's generator changed (issue #240, then #258 moved
the project to Profile 6); this page gives the two standing tuning slices a Profile 6 table of their own, so that the
next Brain change has a single-side baseline to read against.

Shatterfish `main` at `7b1212eea`, upstream `v4.0.2`, Profile 6, the `shatterfish` Brain on `weights/shatterfish.json`,
Oracle off, turn cap 20,000, `--debug-record` on (the record does not change play,
`RunLogRunTest.the_debug_record_changes_nothing`). Both slices were played on 2026-10-26 on one machine, one checkout,
6 Runs at a time, no baseline side. Seeds are from the `standard` set only; `goo` and `holdout` were not touched.

```sh
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 40 --salt-base 1000 --debug-record --jobs 6 --out build/direction/p6-ga"
./gradlew :rig:direction --args="--set standard --class WARRIOR --first 80 --salt-base 3000 --debug-record --jobs 6 --out build/direction/p6-gb"
```

## Tuning set: 40 Warriors, salts 1000 to 1039

| | current 7b1212eea |
|---|---|
| Runs | 40 |
| reach depth 5 | 24/40 |
| Goo kills | 17/40 |
| median turns | 1940.5 |
| deepest mean / max | 5.025 / 8 |
| deaths by depth 1..8 | 0/2/8/6/7/8/6/3 |
| searches per floor | 12.15 |
| search waits per floor | 18.13 |
| oscillation Steps | 650 |
| heaps taken / seen | 2995/3303 |
| seen to taken, median waits | 8 |
| heaps taking over 100 waits | 117 of 3303 |

## Second slice: 80 Warriors, salts 3000 to 3079

The first 80 Warriors of `standard` under salts 3000 to 3079. Its first 40 seeds are the tuning seeds again under new
salts, and a new salt makes a different Run, so the 80 are distinct from the 40 above.

| | current 7b1212eea |
|---|---|
| Runs | 80 |
| reach depth 5 | 35/80 |
| Goo kills | 27/80 |
| median turns | 1807.0 |
| deepest mean / max | 4.250 / 8 |
| deaths by depth 1..8 | 5/10/14/16/11/15/8/1 |
| searches per floor | 11.95 |
| search waits per floor | 18.04 |
| oscillation Steps | 1049 |
| heaps taken / seen | 5092/5593 |
| seen to taken, median waits | 8 |
| heaps taking over 100 waits | 226 of 5593 |

## Reading these

- These are the same 120 Runs the [Profile 6 Goo baseline](2026-10-26-goo-baseline-7b1212eea.md) analyses, and they
  reproduce the "change" columns of the [no-outrunning page](2026-10-26-219-no-outrunning-fast-enemies.md) exactly
  (24/40 and 17/40; 35/80 and 27/80), which is what that change, merged into `main`, should give.
- A first attempt at the 40-Run slice without the debug record gave 23/40 reach and 16/40 Goo kills, because one Run
  (`AFW-VUU-CCS/1020`) was killed by the operating system: the JVM could not commit memory
  (`There is insufficient memory for the Java Runtime Environment to continue`) while several other agents were
  building on the machine. That is a host failure, not a result. The tool reports it ("Runs that did not finish") so it
  is visible when it happens; the batch was replayed in full and the table above is the replay, in which all 40 Runs
  finished.
- A tuning set of 40 has a wide spread (17/40 Goo kills has a Wilson interval of 29% to 58%). Compare against these
  tables only through the paired `--baseline` mode, or with the 120-Run interval on the Goo page.

## Reproducing

The Run logs and debug records are in `build/direction/p6-ga/current` and `p6-gb/current` of the checkout that played
them (not committed). A Run is determined by tag, seed and action list: replaying the first 20 Runs of the 40-Run slice
with `--jobs 1` (see the [throughput page](2026-10-26-throughput-7b1212eea.md)) gave the same final chain hash in all
20 Run logs.
