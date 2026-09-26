# Story 4.13: a direction check toward the Goo gate

**A direction check, not an acceptance.** Nothing on this page was played under a Registration, and
no sequential test was run: these are the numbers story 4.13 tuned against, published so the
direction is on the record ([methodology: Registration](../methodology.md#registration)). The gate
itself -- SM-3, Goo killed in 75% of the `goo` set, the lower bound at 70% -- is story 4.14's, measured
under a Registration on a set this story never played. This page is hand-made from the Run logs and
the Rig's comparison view (`Gallery --compare`); `./gradlew :rig:results` generates pages for
registered comparisons only, and none exists here.

Story 4.13, [#114](https://github.com/watchthelight/shatterfish/issues/114). Upstream tag `v4.0.0`;
turn cap 20,000; the `shatterfish` Brain with the weights in `weights/shatterfish.json`; Oracle off.

## The two sets

- **The tuning set**: the first 40 Warrior triples of `standard` (entries in the set's order, the
  `i`-th played under salt `1000 + i`). Every tuning step of the story was measured on it.
- **`smoke`**: all 25 triples, the `i`-th under salt `1000 + i`, as a check that the tuning did not
  merely fit the tuning set.

Neither is the `goo` set, and no Run of `goo` or `holdout` was played.

## Main against this branch

`main` is `53de13d73` (the Brain of story 4.11); the branch is `e74ee9f83` (story 4.13's last kept
step, U1). Both Brains were played the same day on the same machine.

| Set | Brain | Median turns | Mean deepest (max) | Deaths by depth 1/2/3/4/5 | Reached depth 5 | Killed Goo | Other endings |
|---|---|---|---|---|---|---|---|
| tuning (40) | main | 967 | 2.48 (4) | 6/10/21/2/0 | 0 | 0 | 1 stalled |
| tuning (40) | 4.13 | 1,614 | 3.23 (5) | 7/7/6/10/10 | 10 | 7 | none |
| `smoke` (25) | main | 1,044 | 2.52 (4) | 3/10/8/4/0 | 0 | 0 | none |
| `smoke` (25) | 4.13 | 1,094 | 2.84 (5) | 2/8/8/6/1 | 1 | 1 | none |

"Killed Goo" counts a Run whose log shows the boss's death; a Run can kill Goo and die afterwards
on depth 5 ([#163](https://github.com/watchthelight/shatterfish/issues/163)).

## Why the Runs end

On the tuning set, 30 of 40 heroes still die before depth 5, 14 of them starving; the rest die in
fights at strength 11 in tier-1 gear. 7 of the 10 that reach Goo kill it. Story 4.13's file among the BMAD implementation artifacts has the assessment, the tuning log step by step and the levers left.

## The comparison view: the tuning set

The baseline has 40 Runs and the candidate 40; 40 triples were played by both and are compared, 0 only by the baseline and 0 only by the candidate. A Run log's ending records how the Run ended and at what depth, not what killed the hero: the log's Outcome has no killer (a mob, a trap, hunger), so this gallery groups by ending and depth. The situation is the last wait's Policy and Safety flags: what the Brain was doing, and what was wrong, when the Run ended.

#### Endings

| Ending | Depth | Baseline | Candidate | Change |
|---|---|---|---|---|
| DEATH | 1 | 6 | 7 | +1 |
| DEATH | 2 | 10 | 7 | -3 |
| DEATH | 3 | 21 | 6 | -15 |
| DEATH | 4 | 2 | 10 | +8 |
| DEATH | 5 | 0 | 10 | +10 |
| STALLED | 2 | 1 | 0 | -1 |

#### Deaths by situation

The endings the game decided, by the Decision on the screen that ended them. Largest in the candidate first.

| Situation | Baseline | Candidate | Change |
|---|---|---|---|
| fight: enemy-in-view, hp-low | 13 | 9 | -4 |
| fight: enemy-in-view, hp-low, starving | 12 | 8 | -4 |
| explore: hp-low, starving | 2 | 6 | +4 |
| fallback: hp-low, starving | 0 | 6 | +6 |
| explore: hp-low | 3 | 2 | -1 |
| pick-up: hp-low | 1 | 2 | +1 |
| descend: hp-low, starving | 1 | 1 | 0 |
| fight: enemy-in-view | 1 | 1 | 0 |
| descend: hp-low | 0 | 1 | +1 |
| eat: hp-low, starving | 0 | 1 | +1 |
| fallback: enemy-in-view, hp-low, starving | 0 | 1 | +1 |
| fallback: hp-low | 0 | 1 | +1 |
| test-item: hp-low | 0 | 1 | +1 |
| fight: enemy-in-view, hp-low, hungry | 3 | 0 | -3 |
| explore: hp-low, hungry | 1 | 0 | -1 |
| pick-up | 1 | 0 | -1 |
| pick-up: hp-low, starving | 1 | 0 | -1 |

#### Every triple both played

21 ended deeper in the candidate, 2 shallower; 25 survived longer, 11 shorter. Worst change first.

| Seed | Class | Baseline | Candidate | Depth | Turns | Baseline situation | Candidate situation |
|---|---|---|---|---|---|---|---|
| TAZ-YXM-MIA | WARRIOR | DEATH at 3, 1451 turns | DEATH at 1, 308 turns | -2 | -1143 | fight: enemy-in-view, hp-low, starving | test-item: hp-low |
| TDR-WKF-GUZ | WARRIOR | DEATH at 4, 1786 turns | DEATH at 2, 666 turns | -2 | -1120 | fight: enemy-in-view, hp-low, starving | fight: enemy-in-view, hp-low |
| ILM-OET-BYZ | WARRIOR | DEATH at 2, 1672 turns | DEATH at 2, 72 turns | 0 | -1600 | pick-up: hp-low, starving | fight: enemy-in-view, hp-low |
| KKO-SIP-SSO | WARRIOR | DEATH at 3, 1546 turns | DEATH at 3, 1001 turns | 0 | -545 | fight: enemy-in-view, hp-low, starving | fight: enemy-in-view, hp-low |
| IIU-BNU-NUS | WARRIOR | DEATH at 2, 889 turns | DEATH at 2, 546 turns | 0 | -343 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| DCS-SHA-XYL | WARRIOR | DEATH at 4, 1985 turns | DEATH at 4, 1751 turns | 0 | -234 | fight: enemy-in-view, hp-low, starving | explore: hp-low, starving |
| IZC-AQV-NAK | WARRIOR | DEATH at 2, 1938 turns | DEATH at 2, 1918 turns | 0 | -20 | descend: hp-low, starving | explore: hp-low, starving |
| SLD-KZY-FFR | WARRIOR | DEATH at 1, 1556 turns | DEATH at 1, 1548 turns | 0 | -8 | explore: hp-low, starving | descend: hp-low, starving |
| KMQ-KCV-KGY | WARRIOR | DEATH at 1, 120 turns | DEATH at 1, 120 turns | 0 | 0 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| NSP-PPL-WOK | WARRIOR | DEATH at 1, 51 turns | DEATH at 1, 51 turns | 0 | 0 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| RVS-QHY-GIN | WARRIOR | DEATH at 2, 11 turns | DEATH at 2, 11 turns | 0 | 0 | fight: enemy-in-view | fight: enemy-in-view |
| YLV-DUF-MFY | WARRIOR | DEATH at 1, 53 turns | DEATH at 1, 53 turns | 0 | 0 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| GIT-LPU-LWW | WARRIOR | DEATH at 2, 283 turns | DEATH at 2, 289 turns | 0 | +6 | explore: hp-low | descend: hp-low |
| IMD-WTI-GQM | WARRIOR | DEATH at 1, 237 turns | DEATH at 1, 363 turns | 0 | +126 | explore: hp-low | explore: hp-low |
| OPG-ARJ-BZG | WARRIOR | STALLED at 2, 447 turns | DEATH at 2, 591 turns | 0 | +144 | pick-up | pick-up: hp-low |
| LSV-WXG-BAU | WARRIOR | DEATH at 1, 54 turns | DEATH at 1, 199 turns | 0 | +145 | pick-up: hp-low | fallback: hp-low |
| AGH-VZC-PTA | WARRIOR | DEATH at 3, 1562 turns | DEATH at 3, 1714 turns | 0 | +152 | fight: enemy-in-view, hp-low, starving | eat: hp-low, starving |
| DLR-YWZ-ABC | WARRIOR | DEATH at 3, 2032 turns | DEATH at 3, 2243 turns | 0 | +211 | fight: enemy-in-view, hp-low, starving | fallback: enemy-in-view, hp-low, starving |
| EIW-QWY-ISZ | WARRIOR | DEATH at 3, 614 turns | DEATH at 3, 1730 turns | 0 | +1116 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low, starving |
| JJY-MSI-MBN | WARRIOR | DEATH at 3, 2247 turns | DEATH at 4, 1596 turns | +1 | -651 | fight: enemy-in-view, hp-low, starving | explore: hp-low, starving |
| HJO-WWK-PJG | WARRIOR | DEATH at 3, 1501 turns | DEATH at 4, 1049 turns | +1 | -452 | explore: hp-low | pick-up: hp-low |
| XVX-XDX-IKI | WARRIOR | DEATH at 2, 1933 turns | DEATH at 3, 1524 turns | +1 | -409 | fight: enemy-in-view, hp-low, starving | fight: enemy-in-view, hp-low, starving |
| ELH-RAA-OYI | WARRIOR | DEATH at 3, 958 turns | DEATH at 4, 1042 turns | +1 | +84 | fight: enemy-in-view, hp-low, hungry | explore: hp-low |
| ITW-BLO-QQL | WARRIOR | DEATH at 3, 486 turns | DEATH at 4, 1217 turns | +1 | +731 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low, starving |
| EPS-SUK-IGG | WARRIOR | DEATH at 3, 1185 turns | DEATH at 4, 2090 turns | +1 | +905 | fight: enemy-in-view, hp-low, hungry | explore: hp-low, starving |
| XSZ-KDR-YGP | WARRIOR | DEATH at 3, 653 turns | DEATH at 4, 1638 turns | +1 | +985 | explore: hp-low, hungry | fight: enemy-in-view, hp-low, starving |
| ODX-OHE-HTO | WARRIOR | DEATH at 3, 618 turns | DEATH at 4, 2032 turns | +1 | +1413 | fight: enemy-in-view, hp-low | explore: hp-low, starving |
| NPM-FBY-JLE | WARRIOR | DEATH at 2, 371 turns | DEATH at 3, 3151 turns | +1 | +2780 | pick-up | fight: enemy-in-view, hp-low, starving |
| SCY-JTN-AIL | WARRIOR | DEATH at 3, 1596 turns | DEATH at 5, 1770 turns | +2 | +174 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low, starving |
| CIT-ECF-WRK | WARRIOR | DEATH at 2, 306 turns | DEATH at 4, 663 turns | +2 | +357 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| PMA-UHB-YQN | WARRIOR | DEATH at 3, 1003 turns | DEATH at 5, 1632 turns | +2 | +629 | fight: enemy-in-view, hp-low, starving | fight: enemy-in-view, hp-low, starving |
| CWJ-VZE-FLJ | WARRIOR | DEATH at 3, 976 turns | DEATH at 5, 1849 turns | +2 | +873 | fight: enemy-in-view, hp-low, starving | explore: hp-low, starving |
| HBS-HHV-SJW | WARRIOR | DEATH at 3, 1684 turns | DEATH at 5, 2679 turns | +2 | +995 | fight: enemy-in-view, hp-low, starving | fallback: hp-low, starving |
| TBV-IYS-WPP | WARRIOR | DEATH at 3, 1378 turns | DEATH at 5, 2389 turns | +2 | +1010 | fight: enemy-in-view, hp-low, hungry | fallback: hp-low, starving |
| WUQ-AKJ-GFL | WARRIOR | DEATH at 2, 456 turns | DEATH at 4, 1733 turns | +2 | +1277 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low, starving |
| AFW-VUU-CCS | WARRIOR | DEATH at 3, 1476 turns | DEATH at 5, 3189 turns | +2 | +1713 | explore: hp-low, starving | fallback: hp-low, starving |
| PCM-GCM-KSF | WARRIOR | DEATH at 3, 602 turns | DEATH at 5, 2407 turns | +2 | +1805 | fight: enemy-in-view, hp-low | fallback: hp-low, starving |
| MZX-IGB-NNM | WARRIOR | DEATH at 3, 765 turns | DEATH at 5, 2999 turns | +2 | +2234 | fight: enemy-in-view, hp-low | fallback: hp-low, starving |
| LLJ-SFT-ZEP | WARRIOR | DEATH at 3, 794 turns | DEATH at 5, 3079 turns | +2 | +2285 | fight: enemy-in-view, hp-low | fallback: hp-low, starving |
| LXO-FXV-JQT | WARRIOR | DEATH at 2, 1538 turns | DEATH at 5, 2592 turns | +3 | +1054 | fight: enemy-in-view, hp-low, starving | fight: enemy-in-view, hp-low |

## The comparison view: `smoke`

The baseline has 25 Runs and the candidate 25; 25 triples were played by both and are compared, 0 only by the baseline and 0 only by the candidate. A Run log's ending records how the Run ended and at what depth, not what killed the hero: the log's Outcome has no killer (a mob, a trap, hunger), so this gallery groups by ending and depth. The situation is the last wait's Policy and Safety flags: what the Brain was doing, and what was wrong, when the Run ended.

#### Endings

| Ending | Depth | Baseline | Candidate | Change |
|---|---|---|---|---|
| DEATH | 1 | 3 | 2 | -1 |
| DEATH | 2 | 10 | 8 | -2 |
| DEATH | 3 | 8 | 8 | 0 |
| DEATH | 4 | 4 | 6 | +2 |
| DEATH | 5 | 0 | 1 | +1 |

#### Deaths by situation

The endings the game decided, by the Decision on the screen that ended them. Largest in the candidate first.

| Situation | Baseline | Candidate | Change |
|---|---|---|---|
| fight: enemy-in-view, hp-low | 11 | 10 | -1 |
| fight: enemy-in-view, hp-low, starving | 6 | 9 | +3 |
| fallback: hp-low, starving | 0 | 2 | +2 |
| explore: hp-low | 3 | 1 | -2 |
| descend: hp-low | 0 | 1 | +1 |
| fallback: hp-low | 0 | 1 | +1 |
| pick-up: hp-low | 0 | 1 | +1 |
| explore: hp-low, starving | 2 | 0 | -2 |
| fight: enemy-in-view, hp-low, hungry | 2 | 0 | -2 |
| fallback: enemy-in-view, hp-low, starving | 1 | 0 | -1 |

#### Every triple both played

8 ended deeper in the candidate, 2 shallower; 15 survived longer, 7 shorter. Worst change first.

| Seed | Class | Baseline | Candidate | Depth | Turns | Baseline situation | Candidate situation |
|---|---|---|---|---|---|---|---|
| XHX-PDR-IAC | DUELIST | DEATH at 3, 1044 turns | DEATH at 2, 627 turns | -1 | -417 | fight: enemy-in-view, hp-low, hungry | explore: hp-low |
| KEX-UKC-SEI | ROGUE | DEATH at 4, 795 turns | DEATH at 3, 435 turns | -1 | -360 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| OEL-EQM-KLE | MAGE | DEATH at 2, 1225 turns | DEATH at 2, 599 turns | 0 | -626 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| NEW-CZG-JGW | WARRIOR | DEATH at 2, 862 turns | DEATH at 2, 718 turns | 0 | -144 | explore: hp-low | descend: hp-low |
| MPR-RJX-OEI | ROGUE | DEATH at 3, 1042 turns | DEATH at 3, 961 turns | 0 | -81 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| VFL-GIZ-KJD | MAGE | DEATH at 2, 688 turns | DEATH at 2, 643 turns | 0 | -45 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| VJC-WTM-IDL | WARRIOR | DEATH at 1, 56 turns | DEATH at 1, 56 turns | 0 | 0 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| VSH-WJK-PDU | DUELIST | DEATH at 1, 1576 turns | DEATH at 1, 1576 turns | 0 | 0 | fight: enemy-in-view, hp-low, starving | fight: enemy-in-view, hp-low, starving |
| YHN-AFX-KUO | WARRIOR | DEATH at 2, 37 turns | DEATH at 2, 37 turns | 0 | 0 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| OKG-FJD-XVW | WARRIOR | DEATH at 4, 1762 turns | DEATH at 4, 1821 turns | 0 | +59 | fight: enemy-in-view, hp-low, starving | fight: enemy-in-view, hp-low, starving |
| BVH-BOC-AVB | ROGUE | DEATH at 2, 318 turns | DEATH at 2, 398 turns | 0 | +80 | explore: hp-low | pick-up: hp-low |
| QAI-OCF-LGF | WARRIOR | DEATH at 4, 849 turns | DEATH at 4, 936 turns | 0 | +87 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| KOL-INM-GDZ | MAGE | DEATH at 2, 1130 turns | DEATH at 2, 1229 turns | 0 | +99 | fight: enemy-in-view, hp-low, starving | fight: enemy-in-view, hp-low, starving |
| PSK-RXZ-UPO | HUNTRESS | DEATH at 3, 1202 turns | DEATH at 3, 1534 turns | 0 | +332 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| JQO-UJT-ZZQ | CLERIC | DEATH at 4, 1053 turns | DEATH at 4, 1448 turns | 0 | +395 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| QMJ-LOG-TUP | ROGUE | DEATH at 3, 1295 turns | DEATH at 3, 1745 turns | 0 | +450 | fight: enemy-in-view, hp-low, hungry | fight: enemy-in-view, hp-low, starving |
| KTS-LGM-TOD | HUNTRESS | DEATH at 3, 698 turns | DEATH at 3, 1280 turns | 0 | +582 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low, starving |
| GZJ-JVC-CSU | CLERIC | DEATH at 1, 1676 turns | DEATH at 2, 411 turns | +1 | -1265 | fallback: enemy-in-view, hp-low, starving | fallback: hp-low |
| XVW-EVL-RNJ | CLERIC | DEATH at 3, 1198 turns | DEATH at 4, 1352 turns | +1 | +154 | fight: enemy-in-view, hp-low, starving | fight: enemy-in-view, hp-low, starving |
| WRH-CPY-NQG | HUNTRESS | DEATH at 2, 1012 turns | DEATH at 3, 1313 turns | +1 | +301 | fight: enemy-in-view, hp-low, starving | fight: enemy-in-view, hp-low, starving |
| HHZ-UJV-DJV | DUELIST | DEATH at 2, 557 turns | DEATH at 3, 1094 turns | +1 | +537 | explore: hp-low | fight: enemy-in-view, hp-low, starving |
| EPB-DJZ-JVU | DUELIST | DEATH at 3, 1278 turns | DEATH at 4, 1872 turns | +1 | +594 | fight: enemy-in-view, hp-low, starving | fallback: hp-low, starving |
| SGH-UHQ-ZGI | MAGE | DEATH at 2, 360 turns | DEATH at 3, 1094 turns | +1 | +734 | fight: enemy-in-view, hp-low | fight: enemy-in-view, hp-low |
| MGK-YCL-JZR | CLERIC | DEATH at 3, 1337 turns | DEATH at 4, 2102 turns | +1 | +765 | explore: hp-low, starving | fight: enemy-in-view, hp-low, starving |
| WJZ-GWJ-MGI | HUNTRESS | DEATH at 2, 1111 turns | DEATH at 5, 3064 turns | +3 | +1953 | explore: hp-low, starving | fallback: hp-low, starving |

## Reproducing it

Build the jars at the commit (`./gradlew :rig:assemble :harness:jar :brain:jar`), then play each
triple with the Rig's single-Run entry point, one Run per triple, on the Rig's runtime classpath:

```sh
java -cp <rig runtime classpath> org.shatterfish.rig.RunOne --seed <seed> --class <class> --salt <1000+i>   --challenges <flags> --out <dir> --brain shatterfish --commit <40 zeros> --brain-commit <40 zeros>   --codex codex/v4.0.0 --weights weights/shatterfish.json
```

for the `i`-th of the first 40 Warrior entries of `seeds/standard.json` (and every entry of
`seeds/smoke.json`), then compare two folders of logs with

```sh
./gradlew :rig:gallery --args="--compare <main folder> <branch folder> <page.md>"
```

A Run is fully determined by the tag, the seed, the salt and the Brain, so the same commit gives the
same logs and the same page.
