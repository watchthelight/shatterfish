---
status: accepted
date: 2026-09-04
deciders: watchthelight (product owner), Claude (engineer)
supersedes: ADR-0008 (its expected-ledger table only)
---

# ADR-0016: The hook ledger, corrected by story 1.1

## Context and problem statement

ADR-0008 fixed how hooks are written, marked, counted and budgeted, and offered an **expected**
ledger of ten rows drawn up before any of them had been built. Story 1.1 built the first one and
found the partition wrong in a way that matters for counting.

ADR-0008's row 5 reads "`Hero.act()` Input-wait notification, plus `Hero.ready()` /
`Hero.interrupt()` guards for scene statics under a headless scene", naming `Hero.java`. What
story 1.1 actually needed was three null guards in `GameScene.java`, reached *from*
`Hero.ready()`, `Hero.interrupt()` and `CharSprite.update()`. The third of those, the guard on
`GameScene.add(EmoIcon)`, is reached from a sprite update rather than from the hero at all, and
ADR-0008 had pre-assigned "EmoIcon" to row 8, which is about routing random draws to the base
generator for a quite different reason.

Filing story 1.1's work under either row as written would have made the budget stop meaning
anything, which is the one thing the ledger exists to prevent. An accepted ADR is never edited, so
this record supersedes ADR-0008's table and nothing else.

Non-negotiables touched: #3 (hooks are minimal, justified and listed).

## Decision drivers

- The budget stays at ten. This is not a request for more room.
- A row is one change-set with one reason, and the reason must be the thing a reviewer would
  check, not the file the edit happens to land in.
- Rows land across several stories; the ledger must survive that without renumbering.

## Considered options

1. File the emote guard under row 8. Rejected: row 8's reason is draw routing for replayability in
   E5. Two unrelated reasons under one id is exactly what ADR-0008's own rule forbids.
2. Give the emote guard an eleventh row. Rejected: it breaks the budget for a one-line guard, and
   the budget is a real constraint rather than a formality.
3. **Redefine row 5 by its reason rather than its file: the edits that let the game's actor loop
   run when no `GameScene` exists.** All three guards and the Input-wait notification serve that
   one purpose, so they are one change-set landing across stories 1.1 and 1.5. Chosen.
4. Rewrite the whole ledger from what story 1.1 learned. Rejected: the other nine rows have not
   been built yet, and rewriting them now would be the same guesswork ADR-0008 already did.

## Decision outcome

The v1 ledger is the following ten rows. Only rows 5 and 8 change wording; the budget, the marker
convention, the counting test and every other rule of ADR-0008 stand unchanged.

| Id | Reason | Sites | Epic |
|---|---|---|---|
| 1 | Desktop and headless builds must not need the mobile toolchains | `settings.gradle` | done |
| 2 | The hook registry itself | `Hooks.java` | E1, story 1.2 |
| 3 | The scene seam: creation and destruction notifications, including where the Observer re-registers its log listener | `GameScene` | E1, E5 |
| 4 | Read-only accessors for private state the screen shows | `GameScene` HUD fields and `cellSelector`, `CharSprite.emo` | E1, E5 |
| 5 | **Let the actor loop run with no `GameScene`**: null guards on the scene statics the actor thread and sprite updates reach, and the Input-wait notification at the observe site | `GameScene.selectCell`, `GameScene.resetKeyHold`, `GameScene.add(EmoIcon)` (landed, story 1.1); `Hero.act()` (story 1.5) | E1 |
| 6 | Remove identity-hash ordering from anything that decides an outcome | `Actor`, `Level`, `Random` | E1, story 1.16 |
| 7 | Bypass the sprite wait for the fastest speed mode | `CharSprite` | E5 |
| 8 | **Route render-thread random draws to the base generator** so an Overlay Run replays | `Emitter`, `Music`, `EmoIcon` construction draws | E5 |
| 9 | The input gate that lets PAUSED ignore hero input | `CellSelector.select`, `CellSelector.processKeyHold` | E5 |
| 10 | Register the Overlay's actions and its settings section | `SPDAction`, the settings screen | E5, E8 |

Rows 5 and 8 both touch `EmoIcon`-adjacent code for different reasons, which is legitimate: row 5
stops a null dereference, row 8 changes which generator a draw comes from. A reviewer checking
either has one question to answer, not two.

### Consequences

- Good: the budget still means what it says, and story 1.1's work has an honest home.
- Good: rows are now named by the question a reviewer must answer, which is what makes the
  counting test worth running.
- Bad: a second ADR to read alongside ADR-0008. Mitigated by superseding only the table, so
  everything else stays in one place.
- Bad: rows 5 and 8 land across two epics each, so "the row is complete" is not a single moment.
  The counting test checks ids against rows, not completeness, so this costs nothing mechanically.

## Pre-mortem

*If this is wrong in six months, why?*

- Another row turns out to be partitioned by file rather than by reason, and the same correction
  is needed again. Mitigation: this record sets the principle, so the next correction is a row
  edit in a new ADR rather than a rediscovery.
- The budget of ten is reached and a genuine eleventh need appears. Mitigation: that forces an ADR
  by ADR-0008's own rule, which is the intended behaviour rather than a failure.
- Rows 5 and 8 are confused in review because both touch the emote path. Mitigation: the marker id
  at each site names which, and the table above says what each is for in one line.
