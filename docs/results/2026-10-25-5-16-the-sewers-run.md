# Story 5.16: the full sewers run with a takeover, headless

**A demonstration and a regression guard, not an acceptance.** Nothing here ran under a Registration and no
sequential test was run. It is the automated half of story 5.16 (issue #161, SM-6): a seeded Warrior played by the
real Brain from the first floor to the Goo fight with the Panel's Decision at every Input wait, a scripted person
taking over for three turns mid-fight and handing back, and a log that replays exactly. The live, human-watched
session is not on this page: it has not been run, and its checklist is in the
[story file](https://github.com/watchthelight/shatterfish/blob/main/_bmad-output/implementation-artifacts/5-16-the-full-sewers-run-with-a-takeover.md).

Shatterfish `main` at `3716f2e9b`, upstream `v4.0.2`, Profile 5, the `shatterfish` Brain on
`weights/shatterfish.json`, Oracle off, turn cap 20,000. No game window was opened: the render thread is the test's
`EmbeddedHost` at a sixtieth of a second a frame, and the person is `ScriptedHuman`, which calls the game's own
input entry points.

## The seed

`MZX-IGB-NNM` (`2713922911738`), Warrior, salt `1014` (the Rig's direction salt `1000 + 14`). It is entry 14 of the
`standard` set's first 16 Warriors (the tuning set), and its Rig Run on Profile 5 reaches depth 6 and fights Goo.
It was chosen from that table, never from the `goo` or `holdout` sets. A change to the Brain, the Codex or the
weights can move this Run off Goo; the seed is then chosen again from the same set and written here.

## The session

Command: `./gradlew :harness:test --tests '*SewersRunSmokeTest*' -Pshatterfish.testForks=1`.

| | |
|---|---|
| Waits in the log | 1,500 |
| Deepest floor | 6 |
| Waits with Goo in view (depth 5) | 39 |
| Ending | DEATH on depth 6, 2,352 turns |
| Takeover | at wait 809 (depth 4, an Attack offered) |
| The person's turns | 3 (waits 809, 810, 811) |
| Hand back | at wait 812; the Observation hash differs from the one at the takeover |
| Decision on the Panel before each Brain turn | every one (a window's answer costs no credit and is not held) |
| Replay (`Replay.waitsOf`) | every wait's Observation hash matches, the person's turns included |

The log is attached: [`logs/2026-10-25-5-16-MZX-IGB-NNM-1014.jsonl.gz`](logs/2026-10-25-5-16-MZX-IGB-NNM-1014.jsonl.gz)
(gzip of the Run log, 2.3 MB uncompressed).

### Takeover depth

The takeover is from depth 4. Taking over at the first fight on depth 2 with this seed (wait 213) leaves a log
whose replay diverges later, on depth 3 (an unseen Wandering Gnoll spawns at a different cell, and a door tile then
differs at wait 525). A takeover from depth 4 replays exactly, and so does a Run with no takeover. This is issue
[#245](https://github.com/watchthelight/shatterfish/issues/245); the smoke test keeps `TAKEOVER_DEPTH = 4` until it
is fixed.

## The Overlay does not change outcomes

`OverlayOutcomeComparisonTest` takes the 16 `standard` Warriors of the Rig's direction table (salts 1000 to 1015,
`./gradlew :rig:direction --args="--set standard --class WARRIOR --first 16 --salt-base 1000 --jobs 2 --out build/direction/s516"`,
one JVM per Run, the headless driver) and plays each tuple again in the Overlay's driver, with frames of a different
length, frames drawn while the Brain thinks, and the Brain deciding on its worker. Every wait is compared: its
Observation hash, Action, Decision, Belief, turn and depth, then the ending.

| Run | Rig ending | Rig depth | Rig turns | Rig waits | Overlay ending | Overlay depth | Overlay turns | Overlay waits | same play and ending | every Observation the same |
|---|---|---|---|---|---|---|---|---|---|---|
| CWJ-VZE-FLJ/1001 | DEATH | 5 | 1548 | 1170 | DEATH | 5 | 1548 | 1170 | yes | yes |
| DCS-SHA-XYL/1000 | DEATH | 3 | 875 | 675 | DEATH | 3 | 875 | 675 | yes | yes |
| DLR-YWZ-ABC/1007 | DEATH | 3 | 1183 | 628 | DEATH | 3 | 1183 | 628 | yes | yes |
| EIW-QWY-ISZ/1011 | DEATH | 3 | 1058 | 669 | DEATH | 3 | 1058 | 669 | yes | yes |
| ELH-RAA-OYI/1003 | DEATH | 4 | 1742 | 1401 | DEATH | 4 | 1742 | 1401 | yes | yes |
| HJO-WWK-PJG/1002 | DEATH | 4 | 1335 | 854 | DEATH | 4 | 1335 | 854 | yes | NO (wait 543: obs, 7 waits differ) |
| JJY-MSI-MBN/1009 | DEATH | 3 | 2126 | 1152 | DEATH | 3 | 2126 | 1152 | yes | yes |
| MZX-IGB-NNM/1014 | DEATH | 6 | 2352 | 1500 | DEATH | 6 | 2352 | 1500 | yes | yes |
| NPM-FBY-JLE/1008 | DEATH | 6 | 2037 | 1527 | DEATH | 6 | 2037 | 1527 | yes | yes |
| NSP-PPL-WOK/1005 | DEATH | 1 | 51 | 49 | DEATH | 1 | 51 | 49 | yes | yes |
| OPG-ARJ-BZG/1012 | DEATH | 2 | 641 | 473 | DEATH | 2 | 641 | 473 | yes | yes |
| SCY-JTN-AIL/1010 | DEATH | 4 | 2286 | 1386 | DEATH | 4 | 2286 | 1386 | yes | yes |
| SLD-KZY-FFR/1013 | DEATH | 4 | 1776 | 1230 | DEATH | 4 | 1776 | 1230 | yes | yes |
| TAZ-YXM-MIA/1006 | DEATH | 1 | 333 | 174 | DEATH | 1 | 333 | 174 | yes | yes |
| TBV-IYS-WPP/1004 | DEATH | 6 | 2668 | 1787 | DEATH | 6 | 2668 | 1787 | yes | yes |
| XVX-XDX-IKI/1015 | DEATH | 3 | 1525 | 995 | DEATH | 3 | 1525 | 995 | yes | yes |

16 of 16 Runs played the same and ended the same; 15 of 16 with every Observation the same.

Fifteen of the sixteen Runs are identical wait for wait: same Observations, same Actions, same Decisions, same
ending. One, `HJO-WWK-PJG/1002`, is not the same Run every time it is played in the Overlay's driver. In the play in
the table its Observations differ from wait 543 for 7 waits while the Actions, turns and ending stay the same; in
another play of the same tuple it diverged for real from wait 543 (Actions, Decisions and Beliefs too) and ended on
the same floor earlier, at 1,221 turns against the Rig's 1,335. The frame lengths are fixed by the salt, so the
variation is thread timing. It is the same class of fault as the takeover replay divergence, and both are tracked in
[#245](https://github.com/watchthelight/shatterfish/issues/245).
So the claim this page supports is "the Overlay's driver plays 15 of 16 standard Runs exactly as the Rig does, and
the sixteenth varies with timing", not the unqualified claim. The comparison is gated by an environment variable
(`SHATTERFISH_COMPARE_RIG`) because the Runs take minutes and the Rig numbers are made outside the test, and its
guard allows the one known exception.
