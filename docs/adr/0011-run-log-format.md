---
status: proposed
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
| `end` | `k`, `outcome` (`win`, `ascended`, `score`, `depth`, `turns`, `cause`, `bosses`), `verifiable`, `chain` (final) | yes |

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
