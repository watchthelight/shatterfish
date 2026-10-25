---
title: 'Story 5.5: PAUSED ignores hero input'
type: 'feature'
created: '2026-10-25'
status: 'review'
baseline_commit: '8fea83353'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Overlay keeps a person's hands off the game with `InputLock`, a processor at the front of
the input multiplexer. That cannot be the whole answer: ADR-0013 found that a click reaches the hero by
`CellSelector.select` and a held direction key by `CellSelector.processKeyHold`, which bypasses every
listener and forces the hero ready itself, and a controller writes the key queue past the multiplexer.
A person reading a Decision must not be able to move the hero by any of them.

**Approach:** Hook row 9, the input gate: `Hooks.inputGate`, asked at the selector's `select`, `processKeyHold` and `moveFromActions`. The
harness's `HeroInputGate` answers it: shut for every Run that is not HUMAN (PAUSED and RUNNING), open to
the Run's own `ActionExecutor` for the length of its call, because the executor reaches `select` through
`GameScene.handleCell`.

## Acceptance Criteria (from epics.md Story 5.5, with the tests that hold them)

1. **The gate is consulted by both paths.** `select` and `processKeyHold` (and `moveFromActions`, which every key move ends in) ask `Hooks.inputGate`.
   Tests: `PausedInputTest.paused`, `.open_again`; `HooksVanillaTest` (the wrap rule), `HooksLedgerTest`
   (the row, the site index, the digests).
2. **Clicks, direction keys, the toolbar, the quickslots and the inventory pane do nothing while PAUSED.**
   Clicks and keys: the gate. Toolbar, quickslots, inventory pane: `InputLock` for a mouse or a key (see
   the decision below for a controller).
   Tests: `PausedInputTest.paused`; `InputLockTest.quickslot_toolbar_and_inventory_inputs_stop_here`.
3. **`PausedInputTest` drives a click and a held direction key with the gate shut and asserts the Run log
   gains no Action and the hero has not moved.** The quickslot press is driven at the lock, where it stops
   (`InputLockTest`, above), because a quickslot never reaches the selector.
   Tests: `PausedInputTest.paused`, `.open_again` (the control: the same inputs move the hero),
   `.the_executor_passes` (the Brain's own Actions still go through a shut gate).
4. **`docs/UPSTREAM.md` gains the input-gate row in the same pull request.** Row 9, site index, digests.

</frozen-after-approval>

## Decisions

- **Gate at the selector only, not at the five row-11 entry points.** Gating `Hero.handle`, `rest`,
  `search`, `upgradeTalent` and `Item.execute` would cover a controller on the toolbar and quickslots, but a
  Prompt's button is answered by a pointer event the executor posts and the game handles on a later frame,
  outside the executor's call; a shut gate could drop the Brain's own answer. Not testable without a
  Run per Prompt kind. Recorded in `docs/ideas/2026-10-25-gate-hero-and-item-entry-points.md`.
- **The gate is shut in RUNNING as well as PAUSED.** PAUSED does not exist as a Mode in the Overlay until
  story 5.6; `InputLock` already shuts a person out in RUNNING, so the gate does the same and the gamepad
  path to the selector is closed in both. `EmbeddedRun.humanInput(boolean)` is the switch 5.6 and 5.8 turn.
- **`processKeyHold` also resets the held keys when shut**, so a key still down when input opens does not
  walk the hero.
- **`moveFromActions` is a third site.** My first cut left it out. The fairness review showed the key-release
  and stick-release paths call it without going through `processKeyHold`, so a controller d-pad tap moved
  the hero with the gate shut. `PausedInputTest.paused` now drives that call too.
- **Test home.** `PausedInputTest` lives in the harness (it needs `EmbeddedHost` and `ScriptedHuman`, which
  the overlay module cannot see); the lock half is in the overlay's `InputLockTest`.
- **Row 9, not a new number.** ADR-0013 names it row 9 of ADR-0008; the ledger holds seven rows, eight
  after this, under the budget of eleven.

## Review

Fairness review (the fairness-reviewer subagent) found no information-parity issue and one bypass, the key-release and stick-release moves, now gated. Adversarial self-review: the executor scope is a thread-local depth counter released in a `finally`, so an
exception in `apply` cannot leave the gate open; a person's click handled on a later frame is outside it.
`Hooks.clear()` nulls the point; `EmbeddedRun.close` unregisters its own gate only if it is still the
registered one. No Observer, brain or Observation change.
