---
title: 'Epic 5 retrospective: the Overlay'
type: 'retrospective'
created: '2026-10-25'
status: 'done'
---

# Epic 5 retrospective: the Overlay

Written at story 5.16, from the story files 5.1 to 5.14, the merged pull requests #171 to #244 and the issues they
closed. Story 5.15 (the remaining Panel states, issue #160) is still open, so this retrospective covers what the
epic delivered; 5.15's lessons are added to this file when it merges. The live, human-watched session of 5.16 is the
product owner's and is also open (see the story file); nothing below depends on it.

## What the epic delivered

The desktop game now runs with the Brain attached: the launcher and the embedded driver (5.1), the Panel, Mode strip,
Goal line, Decision card, safety flags, Belief summary and Decision log (5.2 to 5.4), PAUSED that ignores the hero
(5.5), the controls row and its enablement matrix (5.6), the speed modes and stepping unit (5.7), take over and hand
back (5.8), the person's turns recorded beside the Brain's shadow (5.9), map highlights (5.10), hotkeys (5.11), oracle
marking (5.12), the speed ceiling and the draw-routing hook (5.13), and run over, save and resume (5.14). Story 5.16
adds the headless sewers run with a takeover and the Overlay-versus-Rig comparison that make "the Overlay changes
nothing about a Run" a test rather than a sentence.

## What went well

- **The one per-wait sequence held.** ADR-0013's rule that both drivers share the wait sequence is what let
  `EmbeddedDeterminismTest`, `OverlayReplayTest` and now `OverlayOutcomeComparisonTest` compare an Overlay Run with a
  Rig Run hash for hash. Every Overlay bug that mattered showed up as a hash divergence at a named wait, not as a
  vague "the bot behaved oddly on screen".
- **Scripting the person.** `ScriptedHuman` calls what the game's own input calls, so the takeover, the recording and
  the replay of human turns could all be tested headless. No story in the epic needed a window to be verified, and 5.16
  proves it for the whole scenario.
- **Small hooks.** The epic's upstream edits stayed minimal and each has a ledger row in `docs/UPSTREAM.md` (the input
  gate of 5.5 is row 9, the draw routing of 5.13 is row 8); the ledger tests caught every drift.
- **Fairness review earned its keep.** It found real bypasses: the key-release and stick-release moves in 5.5, and the
  search-sight question behind #229 (fixed in #244 by judging a search by the screen it was issued on). Each was a
  path the author of the change had not walked.

## What went badly, and what it cost

1. **Draw-time randomness.** Particles, emote icons, sound pitch and music rolls drew from the Run's generator, so how
   many frames the desktop drew changed the dungeon (found in 5.1, fixed in 5.13 by hook row 8 after two stories of
   working around it). The cost was an Overlay that could not be replayed for most of the epic.
2. **Races in tests, not in code.** `ChasmGuardTest` and the chasm hold test raced the Brain's worker (#193, #194,
   #201), and `RandomAgentRunTest` flaked on a scroll's cancel prompt (#238). Each was a test that depended on how
   fast a thread was scheduled on a loaded machine. They were found by CI on someone else's pull request.
3. **Measuring the wrong tree.** `:rig:direction` plays the baseline on the current host code, so a harness or core
   change shows identical columns (found after #217). Brain changes that looked like wins lost depth-5 reach on the
   next run (#207 was reverted in #211).
4. **Batched merges.** Merging four brain pull requests that each merged cleanly broke compilation, because one
   changed a signature another called (#186 against #189). Nothing tested the combination before `main` moved.
5. **The Rig still refuses `driver: embedded`** (#169): the headless driver does not play the Overlay's interface
   size. Overlay logs replay (`Replay.waitsOf`) but are never a Rig number. 5.16's comparison is the evidence that
   this refusal costs nothing, not a reason to lift it.
6. **Two AC items outlive the epic's last story:** 5.15 and the human-watched session. Both are open in the sprint
   file; neither blocks the other.

## Lessons that change how we work

These are in `CLAUDE.md` under "Lessons from E5".

1. A test that waits on the Brain's worker waits on a condition with a deadline, never a frame or iteration count.
2. A change that touches the harness or the game cannot be seen by `:rig:direction`; compare against the previous
   published table, or the Overlay-versus-Rig comparison, and say so in the pull request.
3. Merge dependent pull requests one at a time and build after each; never batch.
4. Anything the render thread draws must not touch the Run's generator. A new draw call near a sprite, emitter,
   sound or music is a hook-row question first.
5. Verify the interactive scenario headless first (`ScriptedHuman` plus `EmbeddedHost`); a window is for the product
   owner's acceptance, never for the engineer's debugging, and an agent never opens one.
6. The fairness reviewer runs before the pull request opens for Overlay, executor and takeover changes, not after CI;
   it found a bypass in 5.5 and a sight leak behind #229.
7. A chosen test seed comes from the `standard` (tuning) set and its page names the set; `goo` and `holdout` are
   never used to pick or tune anything.

## Open items carried out of the epic

- 5.15, the remaining Panel states (#160).
- The live session of 5.16 (checklist in the story file and the pull request).
- #169, the headless driver at the Overlay's interface size, which would let the Rig play the Overlay's logs.
- E4.14, the Goo gate, deferred while E5 ran (reach of the Goo fight in the sewers run depends on it).
