---
title: 'Story 5.16: The full sewers run with a takeover'
type: 'feature'
created: '2026-10-25'
status: 'review'
baseline_commit: '3716f2e9b'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** The Overlay's promise (SM-6) is that a person can watch a Warrior from the first floor to the Goo
fight, take over in the middle of a fight, and hand back, with nothing lost and nothing desynchronised. Until now
each piece is tested alone (attach, hold and advance, takeover, hand back, replay) and nothing runs the whole
sequence.

**Approach:** Run the whole sequence, and keep it running. The story's scope was set by the coordinator because the
product owner was using the machine: no agent session opens a game window or any GUI. Everything that can be
automated is automated here (a headless smoke test, an Overlay-versus-Rig comparison, a Results page); the live,
human-watched session stays an explicit checklist that the product owner runs, and the acceptance criterion that
needs it is **not** marked done.

## Acceptance Criteria (from epics.md Story 5.16, with the tests that hold them)

1. **A person watches a Warrior run from depth one to the Goo fight and the Panel shows a Decision at every Input
   wait.** Headless: `SewersRunSmokeTest` (a Decision on the Panel before every turn the Brain played, 39 Goo waits).
   Live: NOT DONE, see the checklist below.
2. **The person takes over mid-fight, plays at least three turns and hands back; the Brain re-plans from the
   current state with no desync.** Headless: `SewersRunSmokeTest` (takeover at wait 809, three turns, hand back at
   812, a different Observation hash at the hand back, the Brain's first wait after it is the Decision the Panel
   showed). Live: NOT DONE.
3. **The Run log replays exactly afterwards, including the human turns.** `SewersRunSmokeTest` replays the log with
   `Replay.waitsOf`: every wait's Observation hash matches.
4. **The session is recorded on a Results page with the log attached, and a standard-set comparison confirms the
   Overlay's presence did not change outcomes.** `docs/results/2026-10-25-5-16-the-sewers-run.md` (the headless
   session, with the log committed beside it) and `OverlayOutcomeComparisonTest` (16 `standard` Warriors, Rig
   against the Overlay's driver). The live session's page is NOT DONE.
5. **`SewersRunSmokeTest` runs the same scenario headlessly as a regression guard.** Done.
6. **The E5 epic retrospective runs and its lessons land in `CLAUDE.md`.** Done:
   `_bmad-output/implementation-artifacts/epic-5-retrospective.md`, the "Lessons from E5" section of `CLAUDE.md`.

</frozen-after-approval>

## Decisions

- **No window, ever, from an agent session.** The scripted person (`ScriptedHuman`) calls the entry points the
  game's own input calls, on `EmbeddedHost`, a stand-in for the render thread at 60 frames a second with the frames
  going on while the Brain thinks. That is the same machinery `TakeoverHandbackTest` and `OverlayReplayTest` use.
- **The seed** is entry 0 of the `standard` set's Warriors, `MZX-IGB-NNM` (`2713922911738`), salt `1014`
  (`1000 + 14`, the Rig's direction salt). It was chosen from the Rig's 16-Run table of the tuning set on Profile 5:
  the Run reaches depth 5 and fights Goo. Never from `goo` or `holdout`.
- **The takeover is from depth 4**, at the first fight there (an Attack offered). A takeover at the first fight on
  depth 2 leaves a log that diverges on replay later, on depth 3: issue #245 holds the finding. The smoke test keeps
  `TAKEOVER_DEPTH = 4` until that is fixed; the finding does not change what a person at the keyboard sees.
- **A window's answer is not withheld.** The Brain's answer to a window (`AnswerPrompt`) costs no credit
  (`EmbeddedRun.owed`), so the Panel's Decision is checked for every other turn the Brain played, and the count of
  window answers is part of the test's accounting.
- **The comparison plays each Rig log's tuple again in the Overlay's driver**, with frames of a different length,
  frames drawn while the Brain thinks and the Brain deciding on its worker, and compares every wait. Env-gated
  (`SHATTERFISH_COMPARE_RIG`), because the Runs take minutes each and the Rig numbers are made by `:rig:direction`.

## The live session (NOT DONE; the product owner runs it)

This is the part of AC 1 and AC 2 that needs a person at a screen. It is not marked done.

1. From the repository root: `./gradlew :overlay:launch -Plaunch.args="--seed MZX-IGB-NNM --class warrior --salt 3f6 --agent shatterfish --out overlay-runs"`.
   The Run starts PAUSED. (The seed and salt are the smoke test's, so the first floors match its log.)
2. Press Next Step repeatedly, or pick a speed. At each Input wait read the Panel: a Decision (the action, its
   reasons) must be showing before the turn plays. Note any wait where it is blank.
3. Watch to the Goo floor (depth 5). At the first fight (an enemy in reach), press Take over, play at least three
   turns yourself (attack, step, wait), then press Hand back.
4. Check that the Brain's next Decision is for the screen you left (not the one before your turns) and that the
   game does not stutter or desync.
5. Let the Run end or press Save and quit. Record: the seed and salt; the wait where you took over and handed
   back; any blank Decision; any visible desync; frame-rate impressions while the Brain thought; the Run log path
   (`overlay-runs/...jsonl`) and the screenshot. Replay it headless afterwards with
   `Replay.waitsOf` (or the Rig's replay tool) to confirm it replays, human turns included.
6. Write the result as a second Results page under `docs/results/`, link it from `docs/results/index.md` and
   the nav, and tick the live AC 1 and 2 here.

## Tests and results

- `SewersRunSmokeTest.the_full_sewers_run_with_a_takeover`: green, 1500 waits, deepest 6, 39 Goo waits, takeover at
  809, hand back at 812, replays exactly.
- `OverlayOutcomeComparisonTest.the_overlay_does_not_change_outcomes`: see the Results page.
- Helpers: `RealBrain` builds the Rig's Brain the way `RunOne` does.
- Retrospective: `epic-5-retrospective.md`; lessons in `CLAUDE.md`.
