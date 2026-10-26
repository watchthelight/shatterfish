# The candidate comparison: no rollout design fits a registered budget

**ADR-0010's choice rule, applied**, for story 6.6 ([#253](https://github.com/watchthelight/shatterfish/issues/253)). It asks
which of the designs that search by engine rollouts completes the search it was configured for inside a per-Decision budget
that a Registration can carry, and registers those that do against the one-ply model under the Per-pair GSPRT. The answer on
the measurements in hand is **none at the budget registered here**, so no candidate was built, no Rig comparison was run,
and the one-ply model remains the tactical layer. That is an outcome of the rule and not a failure to run it. This page says
what would change it.

No Rig number is quoted because no Rig run was made: ADR-0010 and the story's closing criterion send a story with no fitting
design straight to the amendment. The numbers below are arithmetic on the committed reports of stories 6.4 and 6.5 and on the
Rig's published rates, recomputed by `CandidateFitTest` from the report file and not typed.

## Rule 1 first: the leak test

Choice rule 1 asks that a design pass the search leak test of story 6.3 before it is registered. The only rollout search the
program has is the probe search, a test fixture, and it passes (`SearchLeakTest`, [its page](2026-10-26-6-3-the-search-leak-test.md)).
Options 3, 4 and 5 have no code, so there is nothing to run the test on, and none was registered. Each would have to join the
test through the `Search` seam, with a missing-row control for every scrub row its line reads, before its pull request could
open. The probe's shape is used below only to size the least search that option 3 would be configured for.

## The budget

A Registration carries `budget_ms`; all six committed ones carry 0, and the Overlay has none written down. The ADR leaves the
value to the Registration and names two constraints: the Overlay's budget is wall-clock and the Rig's is throughput. The
Registration this story would have made sets it by the second:

- A Brain Run takes about 14.7 s (1,400 waits at 95 waits a second per process; [throughput](2026-10-26-throughput-7b1212eea.md)).
- The one-ply model ran at about 18.04 waits per floor ([Profile 6 tuning](2026-10-26-profile6-tuning-7b1212eea.md)); a Run
  reaches five or six floors, so about **99 Decisions a Run** are ones a search would take.
- If a search may at most double a Run's wall time, its budget is 14.7 s over 99 Decisions, **149 ms**. The registered value
  rounds down to **140 ms**.

That is the engineer's reading of "throughput", written down so that it can be argued with. Every number in it is a published
rate, and the next section shows the budget at which each design would fit, so the argument can be had with the table open.

## Fit

The costs are story 6.4's run 1 medians at three Actions: a rollout cycle (snapshot 7.516, load 15.236, rules 2.633, restore
18.101) is **43.486 ms**, and a scrub is 3.232 ms per sampled world. The configuration of each design is the least search that
is still a search: at least two sampled worlds, a line of three Actions, and the rollouts the shape needs. Smaller is not a
search; larger only costs more. Reproduce with `./gradlew :harness:candidateFit`.

| Option | Least configured search | Rollouts | Decision time (ms) | Fits 140 ms | Budget that would fit (ms) | Run seconds with that budget |
|---|---|---|---|---|---|---|
| 3 | 3 candidate Actions, one rollout each, in 2 worlds (the probe's own shape) | 6 | 267 | no | 268 | 41 |
| 5 | a tree of 3 Actions to depth 2 is 9 leaves, one rollout each, in 2 worlds | 18 | 789 | no | 790 | 93 |
| 4 | 100 iterations, each a fresh world and one rollout (a floor stated for a tree search, not measured) | 100 | 4,672 | no | 4,672 | 478 |

A Run without a search is 14.7 s. Option 3 at its least is a budget 1.9 times the ceiling and makes a Run 2.8 times as long;
option 5 is 5.6 times the ceiling; option 4's floor of 100 iterations is a number nobody has shown to be enough and is 33 times
the ceiling. On a busy machine every Decision time is about 40% higher (run 3 of story 6.4).

## Recorded as not attempted

| Option | Reason |
|---|---|
| 3 depth-limited sampled search | budget: its least search needs about 270 ms at the median against 140 ms |
| 5 hybrid, abstract tree with one rollout per leaf | budget: about 790 ms |
| 4 information-set search | budget: about 4.7 s at a stated floor of iterations; time besides, since it also needs a tree over information sets that nothing in the program builds |

Properties (story 6.5) are not a reason here and are not used as one: rule 4 puts them beside a candidate's result, and there
is no result. For the record, they do not argue the other way either: with a Brain, siblings agree on survival (leaf
correlation 0.95 to 0.99), so a search has to rank on a finer payoff than alive or dead to find anything, and the
disambiguation factor is 0.02 to 0.07, so a short line learns little about the hidden facts.

## The sets

- **`standard`**: not run. There is no candidate to compare with the one-ply model.
- **`bosses`**: not run, for the same reason.
- **`holdout`**: **not touched.** The story's holdout criterion is stated for the accepted candidate or for the one-ply model
  when none is accepted, as the end of a comparison. With no comparison there is no number to confirm, and the held-out set's
  budget is spent once and cannot be given back, so it is kept for a comparison that exists.
- **`goo`**: not used.

## What would reopen the question

Any of these, each a new Registration with its machine and budget named (ADR-0010's consequences):

1. **A faster simulator.** The swap (snapshot, load, restore) is 40.9 ms of a 43.5 ms rollout cycle. With the swap at zero
   (story 6.4's arithmetic, a different host and not a design) option 3's least search would be about 22 ms and fit; a second
   engine instance in one process needs the classloader-isolation spike that ADR-0009's JNI note describes, a story of its own.
2. **A larger budget, chosen by the product owner.** The table gives the budget at which each design fits. 270 ms makes
   option 3 fit and costs about 2.8 times the Rig time per Run; whether the Overlay and the Rig should pay that is the
   product owner's call, and the ceiling of 149 ms above is the engineer's reading and not a requirement.
3. **A new upstream tag** that changes the cost of a save and load.
4. **A new candidate**: a design that needs fewer rollouts than the least search above, for instance one that scores most
   Decisions on the Codex tables and rolls out only when the one-ply model's top two Actions are close.

## Limits

- The fit uses medians from one machine on a shared day; the report names the load. The 95th percentiles are about a quarter higher.
- The least configurations are the engineer's, stated above, and the fit is only as good as "at least two worlds" and "a line of
  three Actions". The probe's shape is real code; the shapes for options 4 and 5 are not.
- The count of 99 search Decisions a Run is the Profile 6 baseline's, from before later Brain changes.
- Nothing here says that a search would help. It says that none can be tried inside 140 ms with the simulator that exists.
