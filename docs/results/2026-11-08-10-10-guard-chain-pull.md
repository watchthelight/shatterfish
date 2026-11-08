# Story 10.10, the Guard chain pull

Story 10.10 (#356), epic E10. A Prison Guard that is awake pulls the hero one step toward it with its chain when the
hero is 2 to 4 tiles away in a clear line, then cripples the hero (`docs/rules/combat.md`, Guard row). The new fight
rule `guard-chain` ([brain-rules](../brain-rules.md#guard-chain)) steps out of the line when other enemies are awake and
a step exists that stays at least 2 away and is not itself in the pull.

This is a direction check (ADR-0012): the `standard` set, 40 Runs per class, two slices, main at `1e5ddb015` as the
baseline (pinned by SHA), `--jobs 1`. `goo`, `holdout` and `tengu` were not played.

## Result

Every column is identical to the baseline on both slices, for all six classes and pooled: the Runs are the same Runs.

| class | reach d5 (1000) | reach d5 (3000) | reach d10 | Goo kills (1000) | Goo kills (3000) | deaths to a Guard (1000) | deaths to a Guard (3000) |
|---|---|---|---|---|---|---|---|
| Warrior | 32/40 | 27/40 | 0/40 | 32/40 | 21/40 | 3 | 2 |
| Mage | 22/40 | 19/40 | 0/40 | 19/40 | 14/40 | 2 | 2 |
| Rogue | 18/40 | 17/40 | 0/40 | 12/40 | 8/40 | 4 | 1 |
| Huntress | 27/40 | 24/40 | 0/40 | 25/40 | 20/40 | 6 | 0 |
| Duelist | 18/40 | 17/40 | 0/40 | 13/40 | 12/40 | 2 | 0 |
| Cleric | 12/40 | 14/40 | 0/40 | 8/40 | 7/40 | 1 | 1 |
| pooled | 129/240 | 118/240 | 0/240 | 109/240 | 82/240 | 18 | 6 |

Median turns and the deaths by depth are identical too (pooled median 1800.5 and 1540.7). The think time per Decision
rose by 0.09 to 0.56 ms (noise: the rule returns at its first test when no Guard is awake).

## What the table says

- Not worse pooled, and no class drops: the keep rule holds.
- The rule did not fire in any of the 480 Runs. The Guard deaths that exist (24 in the two slices) happen with no
  second awake enemy in sight, or with no step that leaves the line, so the rule's conditions (company, a safe step)
  are not met. The old Policy and the new one chose the same action at every turn.
- So the change is a correctness fix for a screen the unit tests pin (`GuardChainTest`: the old Policy is pulled into a
  worse cell and fails on `main`; where the pull cannot happen the new rule stays out), not a measured gain.
- The 24 Guard deaths are the next question. A follow-up would look at what the Guard fight is with no company (the
  rule leaves it to the melee ranks), which is a different lever from the chain.

## Not done

- No `holdout` or `goo` Run, as the rules say.
- Reach d10 is 0 of 240 on both slices before and after, so that column cannot show an effect of a depth-7 rule.
