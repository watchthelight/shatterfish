---
status: accepted
date: 2026-09-04
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0011: Run-log format

## Context and problem statement

Every Run writes a log from which a third party can Replay it and verify it (PRD FR-23, FR-24,
NFR-2): one record per Input wait, hash-chained, carrying the Observation hash, the Action and
the Decision. The Rig's Results pages, the Overlay's Decision log and the E8 Replay scrubber all
read it; the death gallery and the skeptic's byte-for-byte reproduction (UJ-3) depend on it.
Decide the container, the record shapes, what the hash chain covers, and how a Replay uses it.

Non-negotiables touched: #4 (Java, no extra runtime), #5 (reproducible).

## Decision drivers

- Streamable: a crash mid-Run must leave a readable prefix.
- Diffable and greppable by a person; loadable by a script without a library.
- Tamper-evident and canonical: the chain is recomputable from the file alone and equal on every
  platform.
- One record per Input wait keyed by `k` (AD-5), aligned with the Observation, Decision, Action
  and RNG reseed.
- Small: thousands of Runs per Rig invocation.

## Considered options

1. **JSON Lines, one file per Run, hand-written canonical JSON (sorted keys, no whitespace,
   integers only) from the `api` `JsonWriter` of ADR-0005, written uncompressed** so that a person
   can read a Run with standard tools (NFR-9); the Rig may gzip archived Runs after a comparison
   completes. Chosen.
2. SQLite database per Rig invocation. Rejected: a native dependency, not diffable, not
   streamable across a crash without care.
3. Protocol Buffers. Rejected: a dependency and code generation for a one-JVM product; not
   greppable.
4. CSV. Rejected: Decisions and Prompts are nested; quoting rules vary.
5. One JSON document per Run. Rejected: unreadable until the Run ends.
6. Upstream's `Bundle` format. Rejected: it serializes game objects, which is exactly what the
   log must not contain.
7. Plain text lines. Rejected: no structure for Replay.

## Decision outcome

**File**: `<run-id>.jsonl`, `run-id = <tag>-<class>-<challenges>-<seedcode>-<salt>-<brain>`; one
file per Run under the Rig's `--out` directory or the Overlay's Profile directory. The Brain is
part of the id because a comparison runs both Brains on the same triple and salt, so without it
the two Runs of a pair would collide on one file (AD-14).

**Records** (the `t` field names the kind; every record carries `k` except the header):

| `t` | Fields | Chained? |
|---|---|---|
| `header` | `v` (log schema version), `tag`, `commit` (Shatterfish), `class`, `challenges`, `seed` (long) and `seedcode`, `salt`, `profile` (version), `obsv` (Observation schema version), `codex` (Codex version, which determines Brain behaviour and is not derivable from the tag alone), `brain` (name, commit, config hash), `registration` (id or null), `oracle` (false unless an Oracle Run), `machine`, `started` | yes, except `machine` and `started` |
| `wait` | `k`, `turn` (fixed-point thousandths), `depth`, `branch`, `obs` (SHA-256 hex), `sections` (the section hashes), `actor` (`bot`, `human`), `action` (canonical Action), `decision` (`goal`, `chosen` {action, score}, `alternatives` (at most three, each {action, score, why}), `flags`, `policy`), `belief` (SHA-256 of the Belief's opaque `api` bytes, which `harness` hashes without knowing their shape, AD-13; the full Belief only with `--log-beliefs`), `highlights` (the planned path, target and considered cells the Panel draws, so the Overlay's map highlights and the v2 Replay scrubber read the log rather than re-deriving them), `think_ms`, `prev`, `chain` | all but `think_ms` |
| `prompt` | `k`, the Prompt kind and the option chosen (an Action of kind `answer`) | yes |
| `mode` | `k`, Mode change (`PAUSED`, `RUNNING`, `HUMAN`) and speed mode, Overlay only | yes |
| `shadow` | `k`, the Decision the Brain would have taken during a human turn, never executed (ADR-0013) | yes |
| `boundary` | `k`, the salt and the chain value at a save-and-quit, so a resumed Run continues the same log (ADR-0013) | yes |
| `unsupported` | `k`, the human input the executor could not express; from here `verifiable` is false | yes |
| `end` | `k`, `outcome` (`win`, `ascended`, `score`, `depth`, `turns`, `cause`, `bosses`), `verifiable`, `detail` (only when not empty; story 4.11), `chain` (final) | yes |

A Run that ends without an `end` record (a crash or a kill) is *incomplete*; a Run that reaches the
turn cap ends with `cause = turn cap`. The Rig counts incomplete Runs separately and scores their
pairs as ties (ADR-0012), so a Brain cannot improve its standing by failing.

Scores are integers in ten-thousandths; strings are the Observation's own display strings.

**Chain**: `chain_k = SHA-256(chain_{k-1} || canonical(record_k without prev, chain, think_ms))`,
with `chain_header` computed over the header's chained fields; `prev` repeats `chain_{k-1}` for
convenience. The final `chain` in `end` is the Run's Hash chain value printed on Results pages.

**Replay** (`ReplayDriver`, E3): read the header, refuse a different `v`, `obsv`, `tag` or
`profile`; start a Run with the same tuple; for each `wait` apply `action` and compare the fresh
Observation hash with `obs`, and the section hashes on mismatch to name the section; stop with
"unverifiable from k" at an `unsupported` record. **Verification** (Rig): recompute every `chain`
from the file alone (tamper check) and Replay (reproduction check); a Results page carries both
results.

**Per Rig invocation**: `runs.jsonl` (one line per Run: run-id, outcome, chain, log path),
`summary.json`, and `sprt.json` when comparing (the `rig` skill's contract), all canonical JSON.

### Consequences

- Good: the chain and the Observation hashes use one writer and one hash, so a Results page,
  a Replay and a skeptic's script agree byte for byte.
- Good: the Overlay's Decision log is a view over the same records, and the v2 scrubber needs no
  second format.
- Bad: an uncompressed log is several times larger on disk; the Rig gzips archives after a
  comparison, and the E1 benchmark reports the writer's cost beside the codec's.
- Bad: a schema bump (`v`) orphans old logs for Replay; they stay readable and their chains stay
  verifiable, which is what a published number needs.

### What story 3.2 settled

The decision above says what the records are; implementing it in story 3.2 settled four things it
left open.

**Where the header's provenance comes from.** `tag`, `commit`, `brain` and `registration` are
supplied by the caller, as one value the logged Run requires. The driver has no checkout and no
Registration, so there was no other honest source. They are attested rather than verified: the
chain shows nobody changed them after the Run, not that they were true when it began. The
Registration (story 3.5) and the Replay (story 3.4) are what make them worth anything, and the
methodology page says so in as many words.

**Which kinds a headless Run writes.** `header`, `wait`, `prompt` and `end`. `mode`, `shadow` and
`boundary` are the Overlay's (ADR-0013) and `unsupported` is a human input the executor could not
express, so nothing in the Rig produces them. All four are defined, rendered and chained now, and
held against hand-built records, so the story that starts writing them adds a caller and not a rule
about the format.

**`wait.decision` stays absent until there is a Brain.** A decider that states no reason carries
none, and a partial decision -- a goal with nothing chosen, a choice with no policy -- is refused,
so E4 cannot half-fill it and call it a Decision.

**`boundary` carries `chainAt`, not `chain`.** The record's own chain value and the envelope's are
two different things; spelling them the same would have made a line that says `chain` twice, and
would have made a textual checker strip the record's own payload along with the envelope.

### What the review then found, and what changed

Four reviews read the branch. Six things in this ADR's own terms turned out to be wrong or missing
in the first implementation, and the decision above is amended by them.

**A `wait` carries `actor` and `applied`.** `actor` (`bot` or `human`) is in the table above and was
not written; adding it in E5 would have changed the chained text of every wait ever written and
forced a schema bump that orphaned every log. `applied` is new and not in the table: the record was
written before the executor answered, so a refused Action -- which changes nothing and leaves the
wait open -- was logged exactly like an applied one, and a Replay applying it would have reproduced
a different Run with nothing in the file to explain the divergence.

**A salt is sixteen lower-case hex digits in a string, not a JSON number.** The run id already spelt
it that way; the header spelt the same value as a signed decimal, and a salt runs the whole 64-bit
range, so any reader built on IEEE doubles silently corrupted it. One spelling, in both places.

**A Run's score is the game's own whole points.** "Scores are integers in ten-thousandths" above is
about a Decision's score, which is a Brain's evaluation; the `end` record's score is what the game's
own scorer returns. The first implementation documented the Decision's unit on the Run's field.

**A run id can be read back.** The six parts are joined with `-`, so no part may contain one: a tag
`v4.0.0-beta` or a Brain `greedy-v2` made an id that split more than one way, and a reviewer built
two different tuples that produce one id. A Brain's name is lower case, because two ids differing
only in case are one file on Windows and on macOS. The seed code contributes two dashes of its own,
so an id holds seven and not five -- stated here and on the methodology page, because a reader who
splits on the separator and counts gets it wrong.

**The `oracle` and `verifiable` fields are derived, not asserted.** Both were literals in the only
code that wrote them, which makes a flag that cannot say otherwise. `oracle` is now the caller's to
state and is re-checked at every wait against the Observation the decider was handed; `verifiable`
follows the ending, because a Replay cannot reproduce a Run that stopped when the harness could not
follow the game.

**Every ending writes an `end` record, structurally.** The first implementation wrapped each of the
loop's seven returns; one was missed, and a log with no `end` is indistinguishable from a killed
Run's, which ADR-0012 scores as a tie -- so a Brain that failed in that particular way scored a tie
instead of a loss. The loop now returns its outcome to one caller and that caller writes the record.

**What the chain does not prove** is now stated on the methodology page rather than implied: it
proves internal consistency, not authorship, and every prefix of a valid log is a valid log. The
final chain value has to be recorded somewhere its author does not control -- the Registration
(story 3.5) and the Rig's index (story 3.3) -- before it is evidence of anything.

Two details the ADR did not state and now does. The previous chain enters the hash as its
thirty-two raw bytes, not as its hex text. And the turn a `wait` records is thousandths -- the
harness's own `turns()` rounds two of the game's floats down to an int for a person to read, and a
chained field holds no float and loses no fraction.

### Story 4.11: the `end` record's `detail`

A Run can now end because the Brain could not decide (`BRAIN_ERROR`: it met a screen it has no rule
for) or because a hundred waits passed without a turn (`STALLED`). The cause alone says neither what
the Brain met nor what looped, and the log is where the Rig reads endings from, so the `end` record
carries an optional `detail`: the wait and the Brain's own message, or the count and the last
Action. It is chained like every other member of the record.

Both endings are logged verifiable, and a Replay checks them like any other. A stall reproduces
because the loop counts waits without a turn before it asks for a decision, so the same Actions stall
at the same wait. A Brain error reproduces because the follower, out of recorded waits where the
original Brain threw, throws the same message from the log, and the loop writes the same `end`
record byte for byte.

It is written only when it is not empty, and only these two causes fill it, so every Run that ends
by death, a win or the cap writes exactly the `end` record it wrote before, byte for byte. A reader
of a log that has none reads an empty `detail`. The log schema version stays 2: the version names
the header's key set (`RunLogJsonTest`), and optional record members have joined the format without
a bump before (`decision` and `belief` on the `wait`, story 4.1). A reader written for version 2 that
refuses unknown members of an `end` record would refuse these logs; the harness's own reader does
not, and nothing else reads them.

## Pre-mortem

*If this is wrong in six months, why?*

- A Decision field grows (Explain's full reasons, search statistics) and bloats the log.
  Mitigation: optional sections behind flags (`--log-beliefs`, `--log-search`), never in the
  chained subset unless they are needed to Replay.
- Canonical JSON drifts between the writer and a hand-written skeptic's script. Mitigation: the
  methodology page publishes the canonicalization rules and a test vector (a small log with its
  chain).
- The Overlay's human Actions cannot all be expressed. Mitigation: `unsupported` is a first-class
  record and `verifiable` is a first-class field; FR-4's completeness test shrinks the set.

## Amendment: story 5.1 (2026-09-26)

The header gains an optional, chained member, `driver`, written only when a Run was played by the
Overlay's embedded driver (`"embedded"`), so every headless log keeps its bytes and the schema version
stays 2, as the `end` record's `detail` did in story 4.11. A reader that meets no `driver` reads the
headless driver. An Overlay Run is not reproducible from its tuple or its Action list until story 5.13
(ADR-0013's story 5.1 amendment names the exception to non-negotiable 5), and the member is how its
log says so: the Rig refuses such a log wherever it reads logs to count, score, calibrate or show them.

## Amendment: story 5.9 (2026-09-26): a human's Run

A human's Run at the Overlay (`--agent human`) is the first writer of four kinds this record defined
and nothing wrote: `mode`, `shadow`, `unsupported`, and a new one, `note`. The schema version stays 2,
as it did for `detail` (story 4.11) and `driver` (story 5.1): every addition is a new kind or an
optional member written only when it has something to say, so every log already written keeps its
bytes.

| `t` | Fields | Chained? |
|---|---|---|
| `note` | `k` (the wait open when it was saved, or the last one confirmed), `text` (one line, control characters as spaces, at most 1,000 characters) | yes |
| `shadow` | as before, and `skipped` (`true` only, written only when true): the Decision landed after its wait stopped being the person's, because they had already acted or a later wait was confirmed; it is kept, since it is still the Brain's answer to that wait's Observation, and it was never shown as current | yes |

**What a human's log holds.** The header's `brain.name` is `human`; its `driver` is `embedded` and the
Rig refuses it as it refuses every Overlay log. A `mode` record at `k` 0 says `HUMAN` with speed
`player`. Each wait the person took is a `wait` record with `actor: human`, `applied: true`, no
`decision` and no `belief` (a person carries neither), its `obs` the Observation confirmed at that
wait, its `turn` read when the wait was confirmed, and its `action` the Action the executor would
issue to do what the person did, so a Replay applies it as it applies a bot's. A `prompt` record rides
beside a wait that answered a Prompt, as for the bot. A `shadow` record carries the Brain's Decision
for the same `k`, written when the Brain's worker answers: before the wait record when it was in time,
after it with `skipped` when it was not. An input the executor cannot express is written as the
wait's Action where it has one (a click on a distant cell is `MoveTo`) with an `unsupported` record at
that `k`; an input nothing heard is an `unsupported` record alone, with no wait record, which is the
case story 3.4's `Following` was written for. The `end` record's `verifiable` is false once any
`unsupported` record was written.

**Chained or not.** Three options were weighed for `note` and `shadow`:
1. **Chained records in the same file, like every other kind.** Chosen. The chain is a statement about
   the whole file; a note edited after the Run would be a claim about the player's reasoning that
   nobody made, and a shadow edited after it a claim about the Brain's.
2. Unchained records in the same file, lines without `prev` and `chain`. Rejected: every checker, the
   stranger's script included, would need a rule for lines the chain skips, and a line the chain
   skips can be added, removed or edited without a trace.
3. A side file (`<run-id>.notes.jsonl`). Rejected: two files for one Run is two things to keep
   together, and the reasoning would no longer sit in order beside the moment it is about.

A Replay passes over `mode`, `shadow` and `note`, as it passes over everything but the waits: they
are not needed to reproduce a Run, and nothing about the chain changes that. `RunLogReader` reads
both additions; the Rig's readers read them through it and refuse the log by its header regardless.
A human's log can never reach the chain a Replay writes (its waits say `human`, and it carries
records a Replay does not write), so `Replay.waitsOf` checks what a human's Run can be checked for:
every wait's Observation hash, up to the first `unsupported` record.
