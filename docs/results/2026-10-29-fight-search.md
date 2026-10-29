# Determinized fight search, measured (story 10.24, #370)

**Question.** Does rolling a fight's candidate Actions out through the isolated engine, under sampled hidden state, and taking
the best mean beat the rules Brain's own fight Policy?

**Method.** `:rig:direction --set standard --class <C> --first 40 --salt-base 1000 --baseline 0b7ad805c --brain fightsearch
--jobs 10`, once per class. The baseline is the same code without the search (the commit before it), so the engine, harness
and Codex are identical and the only difference is the Policy. Plan: K=4 samples, H=8 Actions, up to 3 candidates, radius 3,
margin 100, gate 1 (dangerous fights only). One table per class, not repeated: this is one measurement.

| Class | reach depth 5 (rules / search) | Goo kills | deepest mean | median turns |
|---|---|---|---|---|
| WARRIOR | 35 / 30 | 33 / 28 | 5.98 / 5.90 | 2274 / 2333 |
| MAGE | 18 / 21 | 7 / 9 | 4.43 / 4.54 | 1477 / 1370 |
| ROGUE | 10 / 7 | 5 / 5 | 3.60 / 3.61 | 1275 / 1250 |
| HUNTRESS | 21 / 17 | 16 / 13 | 4.40 / 4.23 | 1574 / 1323 |
| DUELIST | 10 of 35 / 12 of 40 | 5 / 5 | 3.91 / 3.69 | 1624 / 1486 |
| CLERIC | 7 / 13 | 6 / 7 | 3.73 / 4.13 | 1279 / 1667 |

Reaching depth 5, summed: 101 of 235 for the rules, 100 of 240 for the search. Three classes up, three down, none past noise
at n = 40. The baseline DUELIST column counts 35 Runs, not 40; the table does not say why and I did not chase it, so the DUELIST row is the least trustworthy. The search logs hold 1898 `fight-search` Decisions for CLERIC alone, so the search ran.

**Verdict.** No win. The search is not made the default and no Brain that ships uses it; it stays registered as `fightsearch`
for the Rig only, so that a better value function, a larger K, or Tengu practice (#387) can be tried on a working,
leak-tested path.

**A first table that measured nothing.** The first run of this measurement played the search Brain behind the direction
tool's heap-watch wrapper, which did not pass the look-ahead on, so the search never fired (no `fight-search` Decision in any
log). Its sums were within noise of the baseline, which is what a no-op gives. The wrapper now forwards the look-ahead and
the table above is the second run; the count of `fight-search` Decisions in its logs is the check that the search ran.

**After the table.** The fairness review found that a rollout could depend on an unidentified item's class through the
scrubber's permuted assignment (ADR-0025 amendment). Playouts now stop at the first item use. The table above was measured
before that change and was not repeated, since the verdict is no win either way.

**What the table cannot show.** Think time per Decision (the Run logs carry `think_ms`); the effect on Tengu, where the
fights that matter are; any plan other than the reference one (`SHATTERFISH_SEARCH=k=8,h=12` changes it and is part of the
configuration hash).

**Why it may not help.** The rules' fight Policy already encodes retreat and chokepoint rules for the fights that kill; a
four-sample mean over eight Actions is noisy against a 100-milli margin; and the playout is the same rules Brain, so the
search can only find a first Action that the rules, continued, do better after.
