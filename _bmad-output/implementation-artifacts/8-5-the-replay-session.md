---
title: 'Story 8.5: The Replay session'
type: 'feature'
created: '2026-10-27'
status: 'review'
baseline_commit: 'ef7b427bb'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** A finished Run, mine or the Rig's, can be checked by the headless Replay but cannot be looked at in the
real game (FR-46).

**Approach:** `--replay <log.jsonl>` starts the Overlay on the log's tuple with a log follower as the Run's agent. The
Run is held and stepped by the existing credits, so Next Step, Run N and Resume are the Controls row's own; the Panel
state REPLAY replaces RUNNING and PAUSED, with Take over off and no Pause-on. A wait whose Observation hash differs
ends the replay at that wait, naming the sections. Stepping back is a restart-and-play seek, measured; keyframes only
if the measurement says so.

## Acceptance Criteria (from epics.md Story 8.5, with the tests that hold them)

1. The launcher takes `--replay <log.jsonl>`; the chain is verified first; a log whose chain, header tag or codex
   version does not match the build is refused with the reason in the Panel and is not played. The Panel has no Load
   action (the native toolkit has no file chooser small enough; the story says so). `ReplayLoadTest`: a good log opens,
   a log with one byte flipped is refused with the verifier's reason, a log of another tag or Codex is refused with the
   field named, and an oracle log is refused. `LaunchOptionsTest.replay_*`. `ReplayRefusalNoticeTest`: the refusal's
   words are drawn.
2. A loaded Run is the Panel state REPLAY: the hero's game input is ignored as in PAUSED, Take over is disabled, the
   Brain does not decide (the log's Actions are applied by the executor), and a Pause-on condition does nothing.
   `ReplayControlsTest` (enablement row, strip words, no pause-on, take over refused), `ControlEnablementTest`.
3. Next Step applies exactly one recorded wait, Run N applies N, Resume plays at the speed mode, landing on the wait
   where the person stopped. `ReplayControlsTest`, and `ReplaySessionTest.steps_by_one_and_by_n`.
4. At each wait the Observation hash is compared with the log's; a mismatch stops the replay at that wait, names the
   differing sections, and does not continue. `LogFollowerTest` (stop, sections, no later wait), 
   `ReplaySessionTest.a_changed_wait_stops_the_replay_and_names_the_section`.
5. `ReplaySessionTest.plays_the_whole_log`: over a log written by the Rig's loop on a `standard` seed, the replay
   in the embedded game plays every wait, ends in the log's recorded outcome and reports zero mismatches.
   `ReplaySessionTest.a_flipped_byte_is_refused`.
6. A seek restarts from the header and plays to the wait. `ReplaySeekCostTest` measures waits 100, 500 and 1000 and
   writes the table to `docs/results/`. Keyframes are added only if a seek is over the budget the results page writes
   down; the page prints the table that lets the owner overrule it.
7. Nothing the replay draws touches the Run's generator (the 5.13 routing is unchanged), and the session leaves no
   file in the profile's save slot and writes no Run log of its own. `ReplaySessionTest.leaves_nothing_behind`.
8. Fairness: labelled `fairness`, with the `fairness-reviewer` verdict in the PR.

</frozen-after-approval>

## Design notes

Alternatives for the replay's agent: (1) a new loop beside `EmbeddedRun`; (2) the existing `Replay.Following`
decider on the embedded Run; (3) a second Overlay process mode. (2) chosen: the executor, gate and credits are the
Run's own, so a replayed wait is the wait the Rig played. `Following` becomes `LogFollower`, public, with a mode that
stops instead of throwing, because the Overlay's Brain-trouble path answers a throw with a wait that would be
executed. A follower that has stopped makes the Run end before anything executes (`Halting`), so a mismatch is
never played past.

Where REPLAY lives: `ControlState` keeps RUNNING/PAUSED underneath as playing/stopped and reports `Mode.REPLAY`, so
credits, speeds and Run N are shared and only Take over, Pause-on and the words differ.

Interface size: a Rig log was played at size 0, the Overlay plays size 1. Measured: a Rig log replayed at size 1
stops with a mismatch at wait 6, so the size does change the hash. The replay plays on the size the log states (an
Overlay log states its own, a Rig log states none and is size 0): `ReplaySession.interfaceSize()`. A game with a
changed Observation is a mismatch, not a crash.

Pre-mortem: a stale answer asks the follower twice (it is `Rewindable`); the follower runs out of waits before the
game ends (the Run ends as closed at that wait, named); a log with an `unsupported` mark ends the replay at the mark
with the reason, as `Replay` does.

## Results

- `ReplaySessionTest` (Rig-written `standard` seed 2713922911738, Warrior, salt 1014): a 500-turn log of 304 waits
  plays every wait with zero mismatches and ends `TURN_CAP` on floor 2 after 539 turns, the recorded outcome. One
  changed wait (300) halts the replay at 300 with the differing section named, 299 waits applied.
- Seek cost at waits 100/500/1000: 307, 935, 1930 ms (median of 3, machine loaded by other jobs). Budget 5 s;
  no keyframes. The table and the reasoning are `docs/results/2026-10-27-replay-seek-cost.md`.
- No upstream file is edited: the launcher flag lives in `shatterfish/overlay`, so there is no `docs/UPSTREAM.md` row.
- Not built: the Panel "Load" action. It needs a file chooser, which the game's toolkit
  does not have, so the flag alone ships, as the story allows.

## Live-session checklist (product owner)

1. `./gradlew :desktop:debug` with the Overlay launcher and `--replay <a Rig or Overlay log>`: the Panel opens in
   REPLAY, stopped at wait 0, with the Mode strip in violet.
2. Next Step applies one wait; Run N applies N and stops; Resume plays at the speed mode; Pause stops on a wait.
3. Click the dungeon and press movement keys: the hero does not move. Take over is dim.
4. `--replay` a log with one byte changed: the notice reads `REPLAY REFUSED: ... does not verify`, no Run starts.
5. Accept or change the violet `#C9A0FF` of REPLAY and the wording of the divergence line on the strip.

## Change log

- 2026-10-27: spec; `LogFollower` and `Halting`; the REPLAY state, `--replay` and the refusal notice; tests; seek
  measurement and results page.
