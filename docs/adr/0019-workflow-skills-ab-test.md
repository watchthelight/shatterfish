---
status: accepted
date: 2026-10-25
deciders: Claude (engineer), under the product owner's overnight delegation of 2026-10-25
---

# ADR-0019: The Matt Pocock skills were tested against the current method and are not adopted

## Context and problem statement

On 2026-10-24 the product owner asked for Matt Pocock's methods to be tried, with the instruction
to "test your performance with them to decide if they're actually good or if they're snake oil".
The `mattpocock-skills` plugin (1.3.1) was installed at user scope for the test. Its skills cover
the same ground as parts of the current method: `grill-with-docs` and `to-spec` against the BMAD
spec step, `tdd` against our test-first briefs, `code-review` against the adversarial review and
the `fairness-reviewer` subagent.

The question is per skill: does it make a Brain fix better, cheaper, or both, enough to justify a
second process beside the one in `CLAUDE.md`?

Non-negotiables touched: #5 (everything measured). The test used the same Rig numbers for both
arms, and both arms got the same fairness review.

## Decision drivers

- **Quality first**: does the fix reproduce the real failure, survive mutation, and move the Rig
  numbers?
- **Cost second**: tokens and wall time per merged fix, rework included.
- **One process**: two methods side by side cost attention on every story; a skill has to earn
  its place.

## Protocol

- Arm A, the current method: a written brief, one implementing agent, tests, a planted mutation
  battery, the fairness review, a pull request.
- Arm B, the plugin: `grill-with-docs`, `to-spec`, `tdd` in vertical slices, `code-review`, then
  the pull request.
- Four Brain issues of similar size. #165 was done by both arms on the same base as a control;
  #184 and #182 went to arm A, #181 to arm B.
- Same base, same `:rig:direction` command, same fairness review for both.
- Decision rule, fixed before the test: keep a skill if quality is at least equal and it costs at
  least 15% less, or if quality is clearly better and it costs at most 15% more. Otherwise drop it.

## Results

| Issue | Arm | PR | Tokens | Diff | Outcome |
|---|---|---|---|---|---|
| #165 (stairs bounce) | A | #197 | about 177k | 6 files, +167/-11 | Could not reproduce the loop; fixed a guessed cause. Depth-5 9/40 against 10 on main, Goo kills 6 against 7. Not merged. |
| #165 (stairs bounce) | B | #199 | about 167k | 3 files, +62/-5 | Its new test reproduced the real loop and fails on main. Oscillation Steps 312 to 289. Merged. |
| #184 (trap rooms) | A | #203 | about 250k, rework included | `Traps` class, hooks in `Brain.decide` | Depth-5 10 to 17/40, Goo kills 7 to 11. Merged. |
| #181 (chests) | B | #204 | about 188k, plus 257k of rework | `Chests` class, the `open-chest` Policy | Depth-5 10 to 15/40, Goo kills 7 to 9. Merged after the mimic-cost margin was added in rework. |
| #182 (pickup valuation) | A | #207 | about 117k | 3 files, +52/-24 | No fairness findings, green on the first CI run, test fails on main. Goo kills 8 to 11 on its base. Merged. |

Mutation battery on #165 (a third agent planted 10 mutants, 9 valid): A killed 3/9 and B 3/9. Counting
only the tests each arm added, A killed 2/9 and B 0/9.

Cost per merged fix, rework included: arm A about 181k tokens over three issues, arm B about 306k
over two.

The sample is small: five pull requests, one control pair. A single rework loop (#204) dominates
arm B's cost, and the Rig deltas are on 40 Runs each, where a swing of 2 or 3 is within noise.

## Decision

Per skill:

- **`tdd`**: dropped as a skill. Its one clear win, on #165, was that the first test reproduced
  the real failure on main before any fix. That practice is cheap and is kept, as a rule of the
  current method: a fix's first test must fail on `main` and its pull request says so. Its new
  tests killed no planted mutants, and arm B did not cost less.
- **`grill-with-docs`**: dropped. Its questions were answered from the issues and docs the brief
  already carries; it added turns without changing any decision. With the owner away it has no
  one to grill.
- **`to-spec`, `to-tickets`**: dropped. They duplicate the BMAD spec step and GitHub Issues, and
  their output does not land where `CLAUDE.md` says content lives.
- **`code-review`**: dropped. It found nothing the fairness review and the adversarial review did
  not, and it does not know information parity.

The plugin is uninstalled.

## Consequences

- One method stays: the brief, the implementing agent, the fairness review, Rig numbers.
- The rule "a fix's first test fails on `main`" joins the turn discipline in `CLAUDE.md`.
- The test can be rerun on a larger sample if a later plugin release changes the skills; the
  protocol above is the one to reuse.

## Pre-mortem

If this turns out wrong, the likeliest reason is sample size: the arm B rework on #181 came from
a domain gap (mimic fights) that either method could have hit. A rerun on ten issues per arm would
settle it at a cost of roughly two million tokens, which is not worth it while the per-fix gap is
this wide.
