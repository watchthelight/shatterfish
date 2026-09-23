---
title: 'Run logs with a hash chain'
type: 'feature'
created: '2026-09-22'
status: 'review'
baseline_commit: '5945ad139'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A published number is currently a claim about a Run nobody else can inspect. Nothing
writes a per-Run record, so a result cannot be re-run, cannot be shown to be the Run it says it is,
and could be edited afterwards with nothing to notice.

**Approach:** Each Run writes plain `<run-id>.jsonl`, one canonical-JSON record per line, hash
chained exactly as ADR-0011 states so the chain recomputes from the file's own bytes without the
tool that wrote it. The run id carries the Brain, so a comparison's two Runs never collide. A Run
killed mid-way leaves a readable prefix whose chain still verifies, and the canonicalization rules
and a worked test vector are published so a stranger's own script can agree byte for byte.

## Boundaries & Constraints

**Always:** The chain is `chain_k = SHA-256(chain_{k-1} || canonical(record_k without prev, chain,
think_ms))`, computed over canonical JSON from `api`'s own `JsonWriter` — sorted keys, no
whitespace, integers only. No float ever reaches a hashed field: a turn is fixed-point thousandths,
a score is ten-thousandths. Timing appears only in `think_ms`, which the chain excludes, so a Run
recorded on a slow machine and a fast one chain identically. Every record but the header carries
its wait index `k`, which the driver owns and nothing else assigns. One line is written and flushed
before the next is built, so a kill at any instant leaves whole lines. The log is a record of a
Run, never an input to one: nothing reads it back within this story.

**Ask First:** Nothing here. The format is ADR-0011's and is not renegotiated by this story; where a
field's value comes from is settled below.

**Never:** Do not implement Replay (story 3.4), the runner (3.3), or the Registration (3.5) — this
story writes logs and verifies their chains, nothing more. Do not gzip, and do not add a dependency:
`api` reaches only `java.lang` and `java.util` (ADR-0003). Do not put anything in a log that the
Brain could not have had at that wait. Do not let the chain's verifier share code with the writer:
a check that recomputes by calling the writer again checks nothing.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| An ordinary Run | a seeded Run played to death | `header`, one `wait` per served wait, `end`; every chain verifies | N/A |
| A Run killed mid-way | the file truncated at any line boundary | the prefix reads and its chains verify; the Run is *incomplete* | no `end` record is invented |
| A truncated final line | a partial line at the tail | the whole-lines prefix verifies; the partial line is refused | names the line number |
| An edited byte | one field changed anywhere | verification fails naming the first record whose chain disagrees | N/A |
| A prompt answered | a wait whose Observation carries a Prompt | a `prompt` record beside the `wait`, both chained | N/A |
| Two Brains, one triple | the same (seed, class, flags, salt) | two run ids differing only in the Brain, so two files | a collision is refused |
| A float in a hashed field | a turn or score given as a float | refused where the value is built | names the field and the unit |
| The timing field | two Runs identical but for `think_ms` | the same chain | N/A |

</frozen-after-approval>

## Code Map

- `docs/adr/0011-run-log-format.md` — the decision this story implements: the run id, the eight
  record kinds and their fields, the chain, and what a Replay and the Rig's verification do with
  them. Read it whole; nothing here re-decides it.
- `shatterfish/api/.../JsonWriter.java` — the canonical writer (sorted keys, no whitespace, integers
  only, unpaired surrogates escaped). It is what "canonical" means; use it, do not re-derive it.
- `shatterfish/api/.../Sha256.java` — SHA-256, package-private in `api`, `digest` + `hex`. The chain
  must be computed inside `api` for that reason.
- `shatterfish/api/.../ObservationCodec.java:105,111` — `Observation.hash()` and the per-section
  hashes the `wait` record carries; already exactly the hex the log wants.
- `shatterfish/api/.../ObservationJson.java:105` — renders an `Action` to canonical JSON, privately.
  The log needs the same shape; extract the Action rendering so both call one writer rather than two.
- `shatterfish/api/.../RigJson.java` — the precedent for a per-family canonical writer and the shape
  `RunLogJson` should take. `Canon.java` is where a refusal is stated.
- `shatterfish/api/.../Belief.java:41` — `Belief.hash()`, for the `wait` record's belief field.
- `shatterfish/api/.../SeedSet.java:129,148` — `code(long)`/`seed(String)`; the run id's seed code is
  this one, not a second spelling.
- `shatterfish/api/.../ObservationCodec.java:27` (`SCHEMA_VERSION = 2`), `Codex.java:31`
  (`VERSION = 8`), `Profile.java:38` (`VERSION = 3`) — the three versions the header records.
- `shatterfish/harness/.../agent/RunLoop.java:91-150` — the wait loop: `halt.waitIndex()` is `k`,
  `new Observer().observe()` is the Observation, `agent.decide` is where `think_ms` is measured,
  `executor.execute` is the outcome. This is where records are emitted.
- `shatterfish/harness/.../agent/RunLoop.java:248` — `turns()` returns an `int` from two floats; the
  log needs `Math.round((duration + now) * 1000)` as a long, and the truncation here is why.
- `shatterfish/harness/.../agent/RunOutcome.java` — cause, salt, depth, turns, waits, applied,
  refused: most of the `end` record already exists as a value.
- `shatterfish/harness/.../driver/HeadlessDriver.java` — owns the wait index and the Profile; per the
  epic's ownership rule it owns the log file too.
- `_bmad-output/implementation-artifacts/3-1-seed-sets-as-committed-versioned-files.md` — the
  previous story: its Design Notes carry the "a test must not build its expectation with the reader's
  own predicate" rule, which this story's verifier lives or dies by.

## Tasks & Acceptance

**Execution:**
- [ ] `shatterfish/api/.../RunLog.java` — the eight record kinds (`header`, `wait`, `prompt`, `mode`,
  `shadow`, `boundary`, `unsupported`, `end`) as a sealed interface of records, each refusing a value
  outside its domain; `VERSION` for the log schema; the run id built from its parts and refusing a
  part that would make two Runs collide.
- [ ] `shatterfish/api/.../RunLogJson.java` — one record to one canonical line, and
  `chain(previous, record)` implementing ADR-0011's formula over the record without `prev`, `chain`
  and `think_ms`. The excluded set is named once, here, and read by both the writer and the tests.
- [ ] `shatterfish/api/.../ObservationJson.java` — extract the Action rendering so the log and the
  Observation's readable form write an Action through one writer.
- [ ] `shatterfish/harness/.../driver/RunLogWriter.java` — opens `<run-id>.jsonl` under a given
  directory, appends one line per record with the chain filled in, flushes each line before the next,
  and refuses a file that already exists.
- [ ] `shatterfish/harness/.../agent/RunLoop.java` — emit `header` at the start, a `wait` per served
  wait (with `k`, the fixed-point turn, depth, branch, the Observation hash and section hashes, the
  Action and `think_ms`), a `prompt` when the wait's Observation carries one, and `end` on every
  ordinary ending. Logging is optional: the existing `play` overloads keep working unlogged.
- [ ] `shatterfish/api/src/test/.../RunLogJsonTest.java` — the canonical line of each of the eight
  kinds, the refusals, and that two records differing only in `think_ms` chain identically.
- [ ] `shatterfish/harness/src/test/.../ChainRecomputeTest.java` — plays a Run, then recomputes every
  chain **from the file's bytes alone**, re-parsing each line and re-hashing without calling the
  writer; and holds that one edited byte is caught naming the first record that disagrees.
- [ ] `shatterfish/harness/src/test/.../RunLogPrefixTest.java` — truncating the file at each line
  boundary leaves a verifiable prefix; truncating mid-line is refused naming the line.
- [ ] `docs/methodology.md` — the canonicalization rules and a worked test vector (a short log and
  its chain), with a test holding the published vector against a freshly computed one so the page
  cannot drift.
- [ ] `docs/adr/0011-run-log-format.md` — record what this story settled: where each header field's
  value comes from, and which kinds are defined but not yet written.

**Acceptance Criteria:**
- Given a Run played to an ordinary ending, when its log is read, then the run id includes the Brain
  and the records are those ADR-0011 names, each keyed by its wait index (`ChainRecomputeTest`).
- Given a finished log, when every chain is recomputed from the file alone, then all agree; and when
  any single byte of a chained field is changed, then verification fails naming the first record that
  disagrees (`ChainRecomputeTest`).
- Given a Run killed mid-way, when the prefix is read, then it parses and its chains verify, and the
  Run is reported incomplete rather than repaired (`RunLogPrefixTest`).
- Given two records differing only in `think_ms`, when both are chained, then the chains are equal
  (`RunLogJsonTest`).
- Given the methodology page's published test vector, when it is recomputed, then it matches
  (`RunLogVectorTest`).
- Rig numbers: none. This story writes the record a Run leaves; it changes no Brain and runs no
  comparison. The first numbers arrive with the runner (3.3) and the statistic (3.6).

## Spec Change Log

- **A wait carries `applied` and `actor`.** `actor` is in ADR-0011's own table and the first
  implementation did not write it; adding it in E5 would have changed the chained text of every wait
  ever written and forced a schema bump that orphaned every log this story produced — the exact
  outcome the "defined, rendered and chained now" rule exists to prevent. `applied` is new and not
  in the ADR: the record was written before the executor answered, so a refused Action was logged
  exactly like an applied one, and a Replay applying it would reproduce a different Run.
- **A salt is sixteen lower-case hex digits in a string, not a JSON number** (review). The run id
  already spelt it that way; a salt runs the whole 64-bit range, and any reader built on IEEE
  doubles silently corrupts a number above 2^53.
- **A run id has no separator inside a part, and a Brain's name is lower case** (review). Two
  different tuples could otherwise produce one id, and two ids differing only in case are one file
  on Windows and on macOS.
- **`oracle` is the caller's to state and is re-checked at every wait**; **`verifiable` follows the
  ending** (review). Both had been literals in the only code that wrote them, which makes a flag
  that cannot say otherwise — and `oracle` is the field the Rig's ranked-Run refusal keys on.
- **A Run's score is the game's own whole points.** ADR-0011's "scores are integers in
  ten-thousandths" is about a Decision's score; this story had put that sentence on the Run's field,
  so the published unit was wrong by a factor of ten thousand.
- **The loop has one exit.** See the design note below.

## Design Notes

**Where the header's provenance comes from.** `tag`, `commit`, `brain` and `registration` are not
things the harness can know: the driver has no checkout and no Registration. They are supplied by the
caller as one value the logged `play` requires, and the Rig fills it in (3.5). They are attested, not
verified — the chain proves nobody changed them *after* the Run, not that they were true when it
started, and the ADR should say so rather than imply more.

**Why the verifier may not share code with the writer.** Three stories running, the defect that got
through four reviews was a test that built its expectation with the reader's own predicate. A chain
check that re-renders each record through `RunLogJson` and compares would pass for any pair of
writer and renderer that agree with each other, including a wrong one. So `ChainRecomputeTest` reads
the text, takes each line's bytes as they are, strips the three excluded keys textually, and hashes —
the skeptic's script, in the test suite.

**Which kinds are written now.** `header`, `wait`, `prompt` and `end`. `mode`, `shadow` and
`boundary` belong to the Overlay (ADR-0013, E5) and `unsupported` to a human input the executor
cannot express; nothing in a headless Run produces them. They are defined, rendered and chained now,
so E5 adds a caller and not a format rule, and the tests hold them against hand-built records.

**No Decision yet.** ADR-0011's `wait.decision` is the Brain's (goal, chosen, alternatives, flags,
policy) and no Brain exists. The field is absent for a decider that states none, and a partial
decision is refused, so E4 cannot half-fill it.

**Why the loop has one exit.** The first implementation wrapped each of the loop's seven returns
in the call that writes the `end` record. Six were wrapped; one was not, and nothing could tell,
because a log with no `end` record is a valid log — ADR-0012 reads it as a killed Run and scores its
pair as a tie, so a Brain that failed in that particular way scored a tie instead of a loss. The
general fix is the one epic 2 ended on: make the pairing a production fact rather than a line
somebody remembers. The loop returns its outcome to one caller and that caller writes the ending, so
a return added next year cannot skip it.

**Why the checker may not share code with the writer, restated after the fact.** It held up. The
verification-gap review traced `LogText` and confirmed it imports nothing from `api`'s renderer and
that drift between the two copies of the unchained key set is caught in both directions. What the
reviews then found was the *other* half of the same lesson: the property the whole design rests on —
strip those five keys from a line's text and what is left is the text the chain covers — was
asserted nowhere. The nearest thing to it compared two string lengths, which is true of any
implementation, including one that returned the same value from both.

## Verification

**Review (four reviewers on the working tree).** `fairness-reviewer` returned BLOCK. Between the
four, eleven things in the branch were wrong rather than merely untested, and one was a crash.

- **Every logged Run that *won* threw.** The game sets `Statistics.ascended` at the stairs
  (`core/…/levels/SewerLevel.java:157`) and sets `gameWon` inside `Dungeon.win`
  (`core/…/Dungeon.java:883`), which runs from the surface scene's own callback — and the loop ends
  the Run when the game *asks* for that scene, without serving it. So the end record was built as
  (win=false, ascended=true) and died on its own refusal. The one ending the rig exists to measure
  was the one ending that crashed, and no test reached it because they all cap at a few hundred
  turns.
- **A `REFUSED` ending wrote no end record**, which ADR-0012 scores as a tie (above).
- **The header attested the previous Run's challenges.** `Dungeon.challenges` is assigned in
  `Dungeon.init`, inside the driver's start, and the header was built before it — and the challenges
  are part of the run id, so the file was named after a Run nobody played.
- `k` meant two things in one file; `oracle` and `verifiable` were literals; the run id was not
  injective; the salt was spelt two ways; the score's published unit was wrong by 10⁴; the escape
  rule on the methodology page was too vague to implement against; the page's "absent, never null"
  rule was contradicted by the page's own vector two lines below it; an empty log file verified
  clean; and a log with carriage returns crashed the published reader instead of naming a line.

Every one is fixed. The three commits are `da5b1b7d7` (the production half), `486739204` (the single
exit and the test gaps) and `35c9f9781` (the published rules).

**The mutation battery** (`mutations32.py`, twenty-two mutations, `:api:test` and `:harness:test`
after each). Fourteen were caught. Seven of the eight survivors were rules the review patch itself
had added days earlier and nothing then exercised — the review's own lesson arriving a second time.

| # | Mutation | Caught by |
|---|----------|-----------|
| M1 | the chain stops covering everything before it | six suites |
| M2 | the chain stops covering the Observation hash | `RunLogJsonTest`, `RunLogVectorTest` |
| M3 | the chain starts covering how long the decider took | `RunLogJsonTest` |
| M4 | a line stops repeating the chain before it | five suites |
| M5 | the writer buffers, so a killed Run loses records | **nobody**, then `RunLogRunTest` |
| M6 | the writer overwrites a log already there | `RunLogPrefixTest` |
| M7 | a Prompt the game put up is not recorded | `RunLogRunTest` |
| M8 | every Action is recorded as applied | `RunLogRunTest` |
| M9 | the wait records a hash that is not the Observation's | `RunLogRunTest` |
| M10 | a Run the harness could not follow claims it is replayable | `RunLogRunTest` |
| M11 | the end record is keyed by the loop's count, not the driver's | **nobody** |
| M12 | the header's oracle claim stops being re-checked | **nobody** |
| M13 | a run id's parts may hold the separator again | **nobody** |
| M14 | `fileName` stops checking what it was handed | **nobody** |
| M15 | the salt goes back to a JSON number | three suites |
| M16 | a prompt record accepts an answer that does not answer | **nobody** |
| M17 | the reader calls an empty file a verified one | `RunLogKindsTest` |
| M18 | the reader accepts a key written twice | `RunLogKindsTest` |
| M19 | the chained text keeps the machine and the hour | six suites |
| M20 | a Run that ends badly writes no ending | `RunLogRunTest` |
| M21 | the tag is read from a game that has not been booted | **nobody** |
| M22 | the Run's score is not the game's | **nobody** |

Two are worth more than their row. **M5 was a weak mutation**: `Files.newOutputStream` returns an
unbuffered stream, so deleting `flush()` changes nothing — the guarantee behind "a killed Run leaves
whole lines" is the unbuffered channel and not the call. Re-run with a real `BufferedOutputStream`,
it is caught, so the test does hold the property. **M21 was unreachable**: a JVM that has booted
cannot be un-booted, so no test could arrange the state the guard exists for. The rule moved into
`Observer.tag(version)`, which a test can ask directly — story 2.7's lesson, that a rule the world
cannot be arranged to violate needs a seam. All eight were re-run after the fixes and all eight now
bite.

**Commands:**
- `./gradlew build -Pshatterfish.mobile=off` — green, every module.
- `./gradlew :api:test :harness:test -Pshatterfish.mobile=off` — green.
- `uv run --no-project --with-requirements docs/requirements.txt mkdocs build --strict` — green.
- `./gradlew :codex:citations -Pshatterfish.mobile=off` — no findings; every new `path:line` resolves.

**Manual checks:**
- A log from a real Run opens in a text editor and reads as one record per line; `grep '"t":"wait"'`
  finds the waits; `head -c` truncation at a line boundary still verifies.
