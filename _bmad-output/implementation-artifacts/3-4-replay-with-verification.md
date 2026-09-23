---
title: 'Replay with verification'
type: 'feature'
created: '2026-09-22'
status: 'in-progress'
baseline_commit: '36cf952c5'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A Run log says what happened. Nothing yet re-runs one and checks that it did. Until
something does, a published number rests on the chain alone — which proves a file was not edited,
not that the Run it describes ever took place.

**Approach:** A Replay is a Run whose decider is the log. It starts the same tuple, hands back each
recorded Action in turn, and at every wait compares the Observation it is given with the hash the
log recorded — naming the differing section, not merely the wait. It writes a log of its own, and
the two chains must be equal: everything the chain covers is everything a reproduction has to get
right, so "the chains match" is the whole comparison in one value.

## Boundaries & Constraints

**Always:** A Replay refuses before it plays anything when the log's schema version, upstream tag,
Observation schema version or Profile version differs from this build's, and says which of the four.
Reproduction is only meaningful between builds that mean the same thing by a Run. At each wait the
Observation's own hash is compared with the record's; on a mismatch the section hashes say which
section moved, because "wait 412 differs" is not something anyone can act on. A Replay stops with
*unverifiable from wait k* at an `unsupported` record and says so rather than continuing past a
point the harness could not express.

**Ask First:** Nothing here. Where the production reader of a log lives is settled below.

**Never:** Do not verify a chain by re-rendering records through `RunLogJson`: a check that recomputes
with the writer agrees with any writer that agrees with itself (story 3.2). Do not make the Replay a
second loop — a Run whose Actions come from a log is a Run, and a second loop is a second set of
rules about what a Run is. Do not let a Replay write into the folder it is replaying from.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| A faithful Replay | a log this build wrote | every hash matches and the two chains are equal | N/A |
| A different tag | a log from another upstream tag | refused before playing | names the tag, both values |
| A different schema | `v`, `obsv` or `profile` differs | refused before playing | names which of the four |
| A section moved | one section's hash differs | fails naming the section and the wait | the other sections are reported too |
| An unsupported record | a log carrying one | stops, *unverifiable from wait k* | the waits before it are still verified |
| A log with no end record | a killed Run's prefix | replays as far as it goes, reports incomplete | not an error |
| A tampered log | one byte changed | the chain check fails before a Run is started | names the first line that disagrees |
| The Replay diverges | the game or a hook changed | the chains differ and the first differing wait is named | N/A |

</frozen-after-approval>

## Code Map

- `docs/adr/0011-run-log-format.md` — the Replay paragraph: refuse a different `v`, `obsv`, `tag` or
  `profile`; apply each `wait`'s Action and compare the fresh Observation hash; name the section on a
  mismatch; stop at an `unsupported` record.
- `shatterfish/api/.../RunLog.java`, `RunLogJson.java` — the records and the one renderer. The
  Replay reads records; nothing here reads.
- `shatterfish/harness/src/test/.../LogText.java` — story 3.2's independent reader: parses the text,
  strips the unchained keys by name, hashes with the JDK. The chain half of this story is this,
  moved into production where the Rig can use it, and it keeps the property that made it worth
  writing: it must not depend on `RunLogJson`.
- `shatterfish/rig/.../LogHeader.java` — story 3.3's reader of a header. It becomes the third
  implementation of one grammar the moment the Replay needs a fourth, so this story consolidates:
  one production reader, used by the Replay and by the Rig.
- `shatterfish/harness/.../agent/RunLoop.java:126-200` — `Logging`, `play`, `playTriple`. A Replay
  is `play` with a `Decider` that reads the log, so the loop is not rewritten.
- `shatterfish/api/.../Decider.java` — an Observation in, an Action out. That is exactly what a log
  offers, which is why a Replay needs no second loop.
- `shatterfish/harness/.../observer/Observer.java` — `observe()`, and `ObservationCodec.hash` /
  `sectionHashes` for the comparison.
- `shatterfish/harness/.../driver/HeadlessDriver.java` — `start(seed, heroClass, salt)`; the tuple
  comes out of the log's header.
- `shatterfish/rig/.../Runner.java` — where a `--verify` would live, and the index whose chain column
  is what a Replay's own chain is compared against.
- `_bmad-output/implementation-artifacts/3-3-the-parallel-runner.md` — the previous story: its
  battery table, and the note that a test written in answer to a review reproduced the defect the
  review had named.

## Tasks & Acceptance

**Execution:**
- [ ] `shatterfish/harness/.../log/RunLogReader.java` — the one production reader: a log's bytes to
  its records, refusing a line the writer would never have produced and naming the line. Story 3.3's
  `LogHeader` becomes a thin caller of this, so there is one grammar in production and not three.
- [ ] `shatterfish/harness/.../log/RunLogVerifier.java` — recomputes every chain from the file's own
  text with the JDK's digest, and **must not depend on `RunLogJson`**, which an ArchUnit rule holds.
- [ ] `shatterfish/harness/.../log/Replay.java` — refuses a mismatched `v`, `tag`, `obsv` or
  `profile` naming which; builds a `Decider` over the log's waits that compares each Observation's
  hash and section hashes before handing back the Action; stops at an `unsupported` record.
- [ ] `shatterfish/harness/.../log/Replay.java` — plays it through `RunLoop`, writing its own log,
  and reports the outcome: verified to wait k, the first differing wait and section, or unverifiable
  from k.
- [ ] `shatterfish/rig/.../Runner.java` — `--verify <dir>`: replay every Run of a finished invocation
  and report how many reproduced, writing the result beside the index.
- [ ] `shatterfish/harness/src/test/.../ReplayRoundTripTest.java` — a full random-agent Run replayed:
  every hash matches, the two chains are equal, and the Actions applied are the same.
- [ ] `shatterfish/harness/src/test/.../ReplayRefusalTest.java` — each of the four version refusals
  names which; a section moved is named; an `unsupported` record stops with the wait; a tampered log
  fails the chain check before a Run starts.
- [ ] `shatterfish/harness/src/test/.../LogReaderAgreementTest.java` — the production reader and
  `LogText` agree on every line of a real log, key by key. Two independent readers agreeing is
  evidence; one reader agreeing with itself is not.
- [ ] `.github/workflows/` — a nightly job that replays a committed reference log on Windows and on
  Linux and compares the chains (NFR-2), with the reference log committed as a fixture.
- [ ] `docs/methodology.md` — what a Replay proves and what it does not, and the command.

**Acceptance Criteria:**
- Given a log from another tag, schema, Observation version or Profile version, when a Replay is
  asked for, then it refuses before starting a Run and names which of the four differs
  (`ReplayRefusalTest`).
- Given a log this build wrote, when it is replayed, then every wait's Observation hash matches the
  record and the Replay's own chain equals the original's (`ReplayRoundTripTest`).
- Given a log whose recorded hash differs at one wait, when it is replayed, then the failure names
  the wait and the section that moved (`ReplayRefusalTest`).
- Given a log carrying an `unsupported` record, when it is replayed, then it stops with
  *unverifiable from wait k* and the waits before it are still reported verified
  (`ReplayRefusalTest`).
- Given the same reference log on Windows and on Linux, when the nightly job replays it, then the
  chains are equal (NFR-2).
- Rig numbers: the cost of verifying an invocation — how long replaying a `smoke` set takes beside
  running it — recorded in the story's Evidence and on the methodology page. A verification that
  costs more than the measurement it checks is a fact worth knowing before 3.11 schedules one nightly.

## Design Notes

**A Replay is a Run whose decider is the log.** `Decider` is an Observation in and an Action out,
which is exactly what a log offers: the Replay needs no second loop, and cannot grow a second set of
rules about what a Run is. It also means a Replay exercises the same executor, the same driver and
the same Profile as the Run it is checking — a reproduction through a different path would prove
less.

**Why the two chains are the comparison.** The chain covers everything about a Run except the clock
and the machine. So a Replay that writes its own log and gets the same chain has reproduced every
Observation, every Action, every section hash, the turn counts and the ending, in one comparison
that a person can check by eye. Comparing hashes wait by wait is what names the first divergence;
comparing chains is what says there was none.

**Why the reader consolidates here.** After 3.3 there were two readers of one grammar in production
(`LogHeader`) and tests (`LogText`), and this story would have added a third. One production reader,
one independent test reader, and a test that they agree — that is two implementations kept honest by
each other, rather than three kept honest by nobody.

## Verification

**Commands:**
- `./gradlew build -Pshatterfish.mobile=off` — green, every module.
- `./gradlew :harness:test :rig:test -Pshatterfish.mobile=off` — green.
- `./gradlew :rig:run --args="--brain random --seeds smoke --out <a>"` then
  `./gradlew :rig:run --args="--verify <a>"` — every Run reproduces.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` — green.
- `./gradlew :codex:citations -Pshatterfish.mobile=off` — no findings.
