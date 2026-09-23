---
title: 'The parallel runner'
type: 'feature'
created: '2026-09-22'
status: 'in-progress'
baseline_commit: 'ec82eb443'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A Run takes minutes and a comparison needs hundreds. One process hosts one Run (AD-6),
so the only way a comparison finishes in a working session is to run many processes at once — and
nothing does that yet, nor records what happened to the ones that failed.

**Approach:** `./gradlew :rig:run --args="--brain <name> --seeds <set|N> --parallel P --out <dir>"`.
The rig picks the triples from a committed seed set, draws each Run's salt at execution, spawns one
child JVM per Run with its own Profile and working directory, and writes an index of every Run it
started — including the ones that crashed, hung or were killed, which are recorded as *incomplete*
with whatever log they left. It reports throughput and how many processes it used, and it refuses
any Run whose log header carries the oracle flag.

## Boundaries & Constraints

**Always:** One process hosts one Run (AD-6); the parent plays none. Every Run gets its own Profile
directory and its own working directory, and no two Runs share either. A Run that does not finish is
counted, never lost: its partial log is kept and the index says it is incomplete, because ADR-0012
scores an incomplete Run's pair as a tie and a Run that vanished cannot be scored at all. The salt is
drawn by the runner when the Run executes and written to the Run's log, never pre-registered. The
runner reads back every log header and refuses the whole invocation if one says `oracle` — this is
the E3 half of FR-11, and it has to be a check on the artifact rather than on intentions.

**Ask First:** Nothing here. `--parallel` defaults to a value derived from the machine and is
overridable; the default is stated in the code and on the rig page rather than asked about.

**Never:** Do not run two Brains or pair anything — the comparison is story 3.6, and this story runs
one named Brain over a set. Do not implement Replay (3.4), the Registration (3.5) or any statistic.
Do not give the rig command line an oracle flag, in any spelling: the refusal above is a second
check, not the only one. Do not let the parent process boot the game, and do not reuse a child.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| An ordinary invocation | `--brain random --seeds smoke --parallel 4 --out runs/` | 25 logs, an index naming all 25, throughput and process count reported | N/A |
| A Run that crashes | a child exits non-zero | counted incomplete, its partial log kept and indexed | the index says why; the invocation continues |
| A Run that hangs | a child past the deadline | killed, counted incomplete, partial log kept | named in the index and the summary |
| A log claiming the oracle | a header with `oracle:true` | the invocation fails naming the Run | nothing is published |
| The holdout set | `--seeds holdout` | refused at the door (story 3.1) | names the set and what it is for |
| An out directory that holds logs | a rerun into the same folder | refused rather than mixing two invocations | names the folder |
| `--parallel 0` or absurd | `--parallel -1`, `--parallel 999` | refused, naming the bound | N/A |
| Two Runs at once | any `P > 1` | neither can see the other's Profile or working directory | `RunnerIsolationTest` |

</frozen-after-approval>

## Code Map

- `_bmad-output/planning-artifacts/epics.md` (story 3.3) — the acceptance criteria verbatim.
- `docs/adr/0011-run-log-format.md` — the per-invocation files this story owes: `runs.jsonl` (one
  line per Run: run id, outcome, chain, log path) and `summary.json`. `sprt.json` is 3.6's.
- `docs/adr/0012-rig-statistics.md` — an incomplete Run's pair scores a tie, which is why a lost Run is worse
  than a failed one.
- `shatterfish/harness/.../agent/RunLoop.java:126-190` — `Logging` and the logged `play`. The child
  calls exactly this; `Logging` carries the folder, the commit, the Brain, the registration, the
  machine and the oracle flag, and the loop re-checks that flag at every wait (story 3.2).
- `shatterfish/harness/.../driver/RunLogWriter.java` — refuses a log file that already exists, which
  is what makes a second Run of one id a failure rather than an overwrite.
- `shatterfish/api/.../RunLog.java` — `runId`, `fileName`, `Brain`, and the header the runner reads
  back. `RunLog.Header.oracle()` is the field the refusal keys on.
- `shatterfish/harness/src/test/.../LogText.java` — the independent reader written for story 3.2. The
  runner needs to read a header too; it must not grow a second reader of the same file with a
  different idea of the format.
- `shatterfish/harness/src/test/.../determinism/DeterminismTwoJvmTest.java:73-104` — the existing
  recipe for spawning a JVM on this classpath (`java.home`, `java.class.path`, `waitFor` with a
  deadline, `destroyForcibly`). The runner does the same thing for real.
- `shatterfish/rig/.../SeedSets.java` — `load(root, name)` reads a committed set and refuses
  `holdout`; `names()`, `definition(name)`. The runner inherits that refusal.
- `shatterfish/harness/.../rng/Salt.java:30` — `Salt.draw()`, drawn by the runner per Run.
- `shatterfish/harness/.../agent/RandomAgent.java` — the only Decider that exists; `--brain random`
  resolves to it, and the registry is where a real Brain arrives in E4.
- `shatterfish/rig/build.gradle` — the `seeds` task is the pattern for a new `run` task.
- `_bmad-output/implementation-artifacts/3-2-run-logs-with-a-hash-chain.md` — the previous story: its
  battery table and the note that a guard written in answer to a review is unheld until something
  bites it.

## Tasks & Acceptance

**Execution:**
- [ ] `shatterfish/rig/.../Runner.java` — the command line: `--brain`, `--seeds`, `--parallel`,
  `--out`, and a deadline per Run. Refuses an unknown flag, an unknown Brain, a bad `--parallel`, and
  an `--out` that already holds logs.
- [ ] `shatterfish/rig/.../Runner.java` — the pool: one child JVM per Run, `P` at a time, each with
  its own Profile and working directory, each handed its salt drawn at execution.
- [ ] `shatterfish/rig/.../RunOne.java` — the child's entry point: reads its tuple from the command
  line, calls `RunLoop.play(..., Logging)`, exits non-zero on anything it cannot do.
- [ ] `shatterfish/rig/.../Brains.java` — the named Brains, `random` alone for now, refusing a name
  it does not have and saying which it has.
- [ ] `shatterfish/rig/.../RunIndex.java` — `runs.jsonl` and `summary.json` through `RigJson`: one
  line per Run with its id, outcome, final chain and log path; the summary with throughput, the
  process count, the wall clock and the counts of finished and incomplete Runs.
- [ ] `shatterfish/rig/.../LogHeader.java` — reads a finished log's header far enough to check the
  oracle flag and take the final chain. It reads the text, the way story 3.2's checker does, and
  shares no code with the writer.
- [ ] `shatterfish/rig/build.gradle` — a `run` task, `JavaExec`, passing `--args` through.
- [ ] `shatterfish/rig/src/test/.../RunnerIsolationTest.java` — two concurrent Runs cannot see each
  other's Profile or working directory, and their logs are two files.
- [ ] `shatterfish/rig/src/test/.../RunnerTest.java` — the command line's refusals; a crashed child
  and a hung child are counted incomplete with their partial logs kept; the index names every Run
  started; the oracle refusal fails the invocation naming the Run.
- [ ] `docs/methodology.md` (a Rig section) — the command, what `--parallel`
  defaults to and why, what an incomplete Run means, and the two files an invocation writes.

**Acceptance Criteria:**
- Given a seed set and `--parallel P`, when the runner runs, then each Run had its own process,
  Profile directory and working directory, and no two Runs shared any of the three
  (`RunnerIsolationTest`).
- Given an invocation that finishes, when it reports, then it states the throughput it measured and
  how many processes it used, and `runs.jsonl` names every Run it started (`RunnerTest`).
- Given a child that crashes and a child that hangs, when the invocation finishes, then both are
  counted incomplete, both partial logs are still on disk, and the index says which (`RunnerTest`).
- Given a log whose header carries the oracle flag, when the runner reads it back, then the
  invocation fails naming that Run and publishes nothing (`RunnerTest`).
- Rig numbers: this story produces the first ones — the measured throughput of a `smoke` invocation
  at the default parallelism, recorded in the story's Evidence and on the rig page. It changes no
  Brain, so there is no comparison to publish.

## Design Notes

**Why a child process rather than a thread.** AD-6: the game's state is static and process-wide —
`Dungeon`, `Statistics`, `Badges`, the scene — and story 3.1 found the Profile leaking between Runs
in one process even in the same thread. Threads would share all of it. The cost is a JVM start per
Run, which the throughput number this story publishes will make visible rather than assumed.

**Why the runner reads the log back.** The oracle refusal could be a flag the parent simply never
sets, and that is also true — but a check on what the artifact says is the one that still works when
someone adds a flag in E5. The header is read from the file, by a reader that shares no code with
the writer, for the reason story 3.2 gives.

**Why an incomplete Run is worse than a failed one.** ADR-0012 scores an incomplete Run's pair as a
tie. A Run that is *lost* — no log, no index line — is not scored at all, which silently drops a
measurement and biases whatever is left. So every Run the runner starts gets an index line before it
starts, and that line is updated, never created, when the Run ends.

## Verification

**Commands:**
- `./gradlew build -Pshatterfish.mobile=off` — green, every module.
- `./gradlew :rig:test -Pshatterfish.mobile=off` — green.
- `./gradlew :rig:run --args="--brain random --seeds smoke --parallel 4 --out <tmp>"` — 25 logs, an
  index naming 25, a stated throughput; every chain verifies.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` — green.
- `./gradlew :codex:citations -Pshatterfish.mobile=off` — no findings.
