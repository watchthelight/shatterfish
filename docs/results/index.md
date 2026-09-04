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

## Published

| Page | What it measures | Epic |
|---|---|---|
| [E1 touchpoint audit](e1-touchpoint-audit.md) | Whether a hero turn resolves with no renderer, and what it costs in hooks | E1, story 1.1 |

The rig's own result files, in the shape described above, begin with the random-agent baseline in
E3. The audit above is a findings page rather than a rig run: it has no seed set and no sequential
test, because there is no Brain to compare yet.
