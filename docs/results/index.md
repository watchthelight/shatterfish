# Results

Published rig numbers. Nothing about the brain is believed until it appears here.

## Format

One file per published run, `results/<date>-<sha>.md`, produced by the rig (E3) and merged
through a reviewed pull request ([ADR-0002](../adr/0002-ci-shape.md)). Each file states:

- the upstream tag and the Shatterfish commit `<sha>`,
- the seed set (name, size, how it was drawn),
- both brains under comparison (name, commit, configuration),
- the SPRT parameters (hypotheses, error rates, the margin) and its outcome (accept / reject /
  undecided, with the log-likelihood ratio trace),
- per-run aggregates: depth reached, turns, cause of death, and their distributions,
- a link to the JSONL run logs (workflow artifact) so any run can be replayed and its
  Observation hashes verified.

From E3 onward no brain change merges without a results file in the pull request.

_No results yet. The first entry is the random-agent baseline (E3)._
