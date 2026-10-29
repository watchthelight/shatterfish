# What a replay seek costs (story 8.5)

A replay session seeks by restarting from the log's header and playing to the wait. This page measures that cost, says
what budget it is held to, and records why the Overlay has no keyframes.

## Measurement

`ReplaySeekCostTest` (module `overlay`) plays a log written by the Rig's own loop on the `standard`-set seed
`2713922911738` (Warrior, salt 1014, the seed `SewersRunSmokeTest` plays), and times a seek in the embedded game:
from asking for the log to the replay being stopped at the wait with its Decision shown. That includes verifying the
log's chain, starting the game and applying every earlier wait at the Fast frame (`ControlState.FAST_FRAME`) the
Overlay uses for a Resume at Fast. Each seek is taken three times and the median is the number.

| Seek to wait | Median (ms) | The three runs (ms) | Per wait (ms) |
|---:|---:|---|---:|
| 100 | 307 | 301, 307, 374 | 3.1 |
| 500 | 935 | 918, 935, 1015 | 1.9 |
| 1000 | 1930 | 1845, 1930, 2006 | 1.9 |

The cost is linear in the wait after a start-up of about 100 ms, and about 1.9 ms per wait.

Caveat: the machine was loaded by other build and test jobs while this ran (several Gradle workers shared its 24
cores), so these numbers are an upper-ish bound on a quiet machine, and a run on another host will differ. The test
prints the table on every run (`overlay/build/seek-cost.tsv`), so the page can be redone.

## Budget and decision

The budget is **5 seconds for a seek to the farthest wait of a log**, in `ReplaySeekCostTest.BUDGET_MS`. A wall-clock assertion flakes on a loaded runner, so the default build only asserts that a seek is not hung (two minutes); run the test with `SHATTERFISH_SEEK_STRICT=1` to enforce the budget.
The reasoning:

- A seek is a deliberate jump, not a frame-to-frame action. A person who asks to land at wait 1000 will wait a moment;
  one who waits longer than a few seconds without a sign of life will think the game hung.
- At 1.9 ms a wait the budget covers a log of about 2600 waits. The sewers run measured here has more than 1000; a
  Run that reaches depth 5 or 6 is a few thousand, and the Overlay's own cap is 20000 turns.
- Measured at 1930 ms for wait 1000 it is well under the budget, with room for a slower machine.

**Decision: no keyframes in this story.** A keyframe (a `Rewindable` snapshot of the game every N waits) would make a
seek cost the distance to the nearest keyframe, but it is a second way for the replay to be wrong: a snapshot that
restores less than the game holds would hide exactly the divergence the replay exists to find. With the table inside
the budget there is nothing to buy, so the restart-from-the-header seek stays the only seek.

The owner can overrule this by changing `BUDGET_MS`: below about 1.9 s the strict run fails at wait 1000 on this host and
keyframes become the next story. Past about 2600 waits the budget is exceeded by the same arithmetic.
