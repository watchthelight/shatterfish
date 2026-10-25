---
title: 'Story 5.13: The speed ceiling and the draw-routing hook'
type: 'feature'
created: '2026-10-25'
status: 'review'
baseline_commit: 'b0efcfcb8'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Fast as it can was limited by the animation interval, and an Overlay Run was not the headless Run of
its tuple: the render thread draws particles, emote icons, sound pitch and music rolls from the Run's random stream,
and how many frames it draws between two waits is the wall clock's.

**Approach:** Send the visual draws to the base generator (hook row 8), make wait confirmation independent of frame
length, and let Fast as it can run animations at one second a frame.

## Acceptance Criteria (from epics.md Story 5.13, with the tests that hold them)

1. **Fast as it can is no longer limited by the animation interval.**
   Tests: `ControlStateTest.unbounded_only_in_fast`; the frame length is `OverlayGame.update` (no upstream edit).
2. **The emitter, music and emote random draws are routed to the base generator; an Overlay Run and a headless Run
   consume the same draws within a turn.**
   Tests: `RandomVisualScopeTest` (routing, nesting and floor, per thread); `OverlayReplayTest` (a control run with
   the routing off fails).
3. **`OverlayReplayTest` records an Overlay Run and replays it under the Rig, asserting every Observation hash.**
   `OverlayReplayTest.the_brain_across_floors`, `.the_random_agent`, `.other_heroes_and_seeds`.
4. **Hook rows are added to `docs/UPSTREAM.md` in the same PR.** Row 8 is added. Row 7 (a sprite-wait bypass in
   `Actor.process`) is not taken: see Decisions.
5. **A smoke-set direction check is in the PR**, since routing changes what the stream consumes.

Handed over from 5.8 and closed here: a Search followed at once by a tap is no longer cleared by the search
animation in a way that depends on the frame length (`WaitGate`).

</frozen-after-approval>

## Decisions

- **Row 8, per-thread scope.** `Random.visualBegin/visualEnd` around the drawing calls in `Emitter` (3), `Music` (2),
  `EmoIcon` (4), `ItemSprite` (1), `SewerLevel` (1). Per thread because the actor thread plays the Run concurrently.
  Combat rolls on the render thread (`onAttackComplete`) stay on the Run's stream.
- **No row 7.** The speed ceiling is `Game.elapsed` set to `ControlState.FAST_FRAME` (one second) by
  `OverlayGame.update` while Fast as it can runs outside a Run N, so a sprite's movement ends in one frame. That
  needs no upstream edit; a bypass of the actor's sprite wait would let the actor race the render thread and
  cannot be registered under the headless stepper, which asserts the actor parks. The ledger stays at ten of eleven.
- **`WaitGate` settles on the sprite.** A hero ready with no window but a sprite still animating carries the wait
  to a frame where the sprite is settled (or `SETTLE_MOST` of game time), so confirmation does not depend on frame
  length. Shared by both drivers; this was the cause of the Search-then-Step divergence at wait 26.
- **Profile version 5**, reference Run regenerated, `ReferenceLogTest.CHAIN` updated, ADR-0007 and ADR-0013
  amended.
- **The Rig still refuses `driver: embedded`** (issue #169: the headless driver at the Overlay's interface size);
  messages and comments now say so. `Replay.waitsOf` replays an Overlay log.

## Tests

`RandomVisualScopeTest`, `OverlayReplayTest`, `ControlStateTest.unbounded_only_in_fast`, `HooksLedgerTest`,
`HooksVanillaTest`, `ProfileTest`, `ReferenceLogTest`.
