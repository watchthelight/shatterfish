---
title: 'Story 5.14: Run over, save and resume'
type: 'feature'
created: '2026-10-25'
status: 'review'
baseline_commit: 'b0efcfcb8'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** When the hero dies or wins, the Overlay says nothing about it, and a player who quits the game
mid-Run loses the Run's log: nothing ties the save to the log, so the log looks killed and the Run cannot go on.

**Approach:** Show the end in the Mode strip, in the game's own words (UX-DR7), and make save and quit a
boundary in the Run log (ADR-0011, ADR-0013) that a resume through the launcher picks up: the wait index, the salt
and the hash chain continue, and the Run starts PAUSED with Next Step. A save opened without the launcher is not
an Overlay Run, and its log ends at the boundary for good.

## Acceptance Criteria (from epics.md Story 5.14, with the tests that hold them)

1. **When the hero dies or wins the Mode strip reads RUN OVER with the cause read from the game's own record.**
   Tests: `RunOverContentTest.the_strip_reads_run_over`, `.no_ending_no_cause`;
   `EmbeddedResumeTest.a_death_reads_the_games_record`.
2. **The log stays readable and scrollable, every control but the Panel toggle is disabled, and the Run log path
   is shown.** Tests: `RunOverContentTest.controls_are_off`, `.a_path_wraps`.
3. **An Ankh resurrection is answered as an ordinary Prompt rather than treated as the end.**
   Tests: `EmbeddedResumeTest.an_ankh_is_a_prompt_not_the_end`,
   `.the_headless_loop_still_ends_at_a_resurrection`.
4. **On save and quit the driver writes a boundary record with the wait index, salt and chain.**
   Test: `EmbeddedResumeTest.suspend_writes_the_boundary`.
5. **On resume through the launcher the wait index, salt and chain continue, and the Run starts PAUSED with Next
   Step.** Tests: `EmbeddedResumeTest.a_resume_continues_the_log_and_the_game`,
   `RunOverContentTest.resume_flag`.
6. **A save opened without the launcher is not an Overlay Run, and its log ends at the boundary.**
   Tests: `EmbeddedResumeTest.a_touched_save_breaks_the_seal`, `.a_log_that_is_not_suspended_is_refused`.

</frozen-after-approval>

## Decisions

The owner is away and delegated; each is recorded in the PR body too.

- **The cause is the game's own record.** At death or win the game adds a ranking
  (`core/.../Dungeon.java:872-888`); `RunEnding` reads the one this Run added (the ids held at attach tell it from
  earlier ones) through `Rankings.Record.desc()` (`Rankings.java:517-534`), first letter lower-cased: "killed by
  Goo". The read reaches the Panel only, never an Observation. Every other ending (turn cap, stalled, refused,
  Brain error, window closed, saved and quit) gets a few plain words of the Overlay's own.
- **RUN OVER is neutral ink, not red.** DESIGN.md keeps red for Danger and the oracle; the words say what
  happened. The Run log path is shown in the Goal line's slot, hard-wrapped (the text block breaks only at spaces).
- **The Ankh needed one change.** The Prompt (`WndResurrect`) was already a Prompt kind the Brain answers; what
  ended the Run was the loading scene in its resurrection mode that follows, which the headless loop does not
  serve. `EmbeddedRun.Host.servesResurrection()` (default false, so the Rig and the headless host end there as
  before) is true in `OverlayGame`: the desktop game's own loading scene brings the hero back.
- **Save and quit is seen two ways.** The game's own menu (`WndGame` "Main menu") saves and leaves for the title
  scene: `EmbeddedRun` suspends there when the hero lives. Closing the window with a living hero waiting at a wait
  on the play scene saves (the game already does, `GameScene.onPause`) and suspends instead of ending as CLOSED.
  Anything else ends as before. The boundary is written only by an explicit suspend, never from `close()`, so a
  stale floor-change autosave can never make a false boundary.
- **The boundary's `k` is the last served wait.** A Decision still pending was never served, so the resumed game
  confirms that wait again and numbers it afresh: `restoreTo(k + 1)`; no wait has two records, none is skipped
  (an open HUMAN wait the person had not taken is the one case that leaves a one-number gap).
- **The seal.** `shatterfish-resume.txt` in the Profile holds the log path, `k`, the slot, an agent name, whether
  the Profile is temporary, and a SHA-256 over the save folder, written after the boundary. A resume claims it by
  an atomic rename (two launchers never continue one log) and refuses when the save's digest changed (opened and
  played without the launcher), when the log does not verify or does not end at a boundary whose `chainAt` is the
  chain of the line before it, or when the agent's name or configuration hash is not the header's.
- **Resume reads the Run from the Profile.** `--resume <profile>` takes seed, hero, salt, turn cap, oracle flag,
  agent and log folder from the marker and the log header; `--seed/--class/--salt/--turn-cap/--oracle/--agent/
  --out/--profile` are refused with it. The Run starts PAUSED with Next Step (a HUMAN Run resumes HUMAN and states
  its Mode again at the boundary's wait). A temporary Profile survives a suspend (the cleanup hook skips a Profile
  that holds a marker) and is deleted at the eventual real end.
- **What the game does not save is not carried.** The log pane and the sprites' emotes are not in the save, so the
  first Observation after a resume differs from an uninterrupted Run's, and a Brain starts with no Belief. A
  resumed Run's `end` says `verifiable: false`, and the Panel's Replay notice says why. Overlay logs are
  `driver: embedded`, which Replay and the Rig refuse anyway (ADR-0013).
- **Determinism across a save, measured.** `a_resume_continues_the_log_and_the_game` plays a Run with a stateless
  Decider, saves and resumes at wait 6, and compares turn and floor per wait against the same Run never saved. They
  match for the waits compared. The Observation hash is not compared: the log pane section differs by design.
- **No upstream edit.** Every change is in `harness`, `overlay` and docs; no hook, no `docs/UPSTREAM.md` row.

## Not here

- The surface scene (a win) does not host the Panel; a win ends the Run and the console and log say so, but the Mode
  strip cannot be drawn on a scene the Panel dock does not serve. Recorded as a deferral on the PR.
- Carrying the global badges and journal across a resume (ADR-0007: the Profile starts with an empty history).
- A resume of a Run that was killed (no boundary): refused by design.

## Completion

- Tests: `EmbeddedResumeTest`, `RunOverContentTest`; existing `EmbeddedEndingsTest`, `ModeStripContentTest`,
  `ControlStateTest` unchanged and green.
- Docs: ADR-0013 amendment, ADR-0011 note that the boundary is now written.
