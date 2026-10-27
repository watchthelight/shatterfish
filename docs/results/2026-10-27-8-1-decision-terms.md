# Evaluation terms in the Decision record

Story 8.1. A `Choice` may now carry `terms` and a `Decision` may carry `search`, both written only when non-empty (ADR-0011,
story 8.1 amendment). This page holds the two checks that show the change is recording only: the committed logs keep their
bytes, and the Brain plays the same Actions. These are direction checks (ADR-0012); nothing here is ranked, and `goo` and
`holdout` were not run.

## The committed logs keep their bytes

`DecisionRecordCompatibilityTest.the_committed_logs_keep_their_bytes` reads every Run log committed under `reference/` and
`docs/results/logs/` with `RunLogReader`, writes each record back with `RunLogJson.line` and compares line by line with the
file, and verifies each file's chain with `RunLogVerifier`, which does not use the writer. Both hold unchanged.

## The Brain plays the same Actions

`:rig:direction --set smoke --first 25 --salt-base 1000 --baseline origin/main --jobs 2`, so both sides play the same 25 smoke
entries under the same salts. The baseline is `origin/main` at `1c43e1ba8`; current is this branch. `:rig:direction` shows
equal columns for any record-only change, so it is not the evidence; the Run logs of both sides are.

| | baseline | current |
|---|---|---|
| Runs | 25 | 25 |
| waits compared | 21,318 | 21,318 |
| Action lists equal, Run by Run | | 25 of 25 |
| Runs equal line by line once `chain`, `prev`, `think_ms`, `machine`, `started`, `commit` and `terms` are removed | | 25 of 25 |
| `terms` members written | 0 | 52,083 |
| log bytes | 32,964,440 | 43,365,796 |

The chain of a fresh Run differs from the baseline's, as it must: `terms` is part of the Decision, which is chained. The
fallback Policy is the only one that scores through the Evaluation, so it is the only one that writes terms. They cost
about 32 percent more log bytes on the smoke set.
