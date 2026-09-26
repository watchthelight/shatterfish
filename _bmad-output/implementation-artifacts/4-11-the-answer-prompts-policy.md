---
title: 'Story 4.11: The answer-prompts Policy'
type: 'feature'
created: '2026-09-25'
status: 'review'
baseline_commit: 'f262d99bc (main, stories 4.1-4.8)'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A Run ends as `UNKNOWN_WINDOW` whenever an item opens a window the harness does not
classify: the scroll of upgrade's confirmation, the stone of intuition's guess, the Cleric's spell
list. The prompt Policy answers every Prompt it can see with one generic rule (decline, else the
lowest answer), which presses "Sell" at a shopkeeper (the bag opens, and the Run stops on it),
presses "Buy" in a shop, and would take a subclass by position. Nothing makes an unanswerable Prompt
anything but a stall (FR-31).

**Approach:**
- The harness classifies the three windows as Prompts of three new kinds, `UPGRADE`, `GUESS` and
  `SPELL`, carrying what each draws.
- `ValidActions` offers the back key on the Prompts it closes cleanly (shop, guess, spell).
- The prompt Policy picks a rule per kind, with an exhaustive switch, and a Prompt its rule cannot
  answer is a Brain error. The Run ends on it with a new, distinct cause, `BRAIN_ERROR`.

## Boundaries & Constraints

**Always:**
- The prompt section carries only what the window draws: its title, text and button labels. The
  guess window's icons are named because an icon is a type's own picture (general game knowledge),
  and are listed by name because the screen's order changes between processes.
- The executor presses exactly the button the Observer listed, with the tap a person makes, or the
  back key.
- No Wait under a Prompt.

**Ask First:** casting the Cleric's spells, buying in shops, reading a known scroll of upgrade onto
gear (each is a Policy, not an answer).

**Never:** edit an upstream file; read an icon's class from anything but the frame it is drawn with.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Upgrade confirmation | UPGRADE, "Upgrade", "Back" | the "Upgrade" option | no "Upgrade" label: Brain error |
| Guess, odds known | GUESS for "crimson potion", icons named | the likeliest type's icon, then the guess button | N/A |
| Guess, no odds | GUESS for an appearance no family has | DismissPrompt | N/A |
| Spell list | SPELL, no labels | DismissPrompt | N/A |
| Shop | SHOP, "Buy for 10g" or the shopkeeper's options | DismissPrompt | N/A |
| Subclass | SUBCLASS, one button per subclass | the class's chosen subclass, then "Yes, I've made my choice." | are-you-sure for another subclass: Brain error |
| Chasm, harmful potion, item, other | as before | story 4.1's rule, as 4.8 left it | N/A |
| A kind the api gains | new `PromptKind` member | compile error in `Answers.rule` until it has a rule | N/A |

</frozen-after-approval>

## Code Map

- `shatterfish/api/.../PromptKind.java`: `UPGRADE`, `GUESS`, `SPELL`.
- `shatterfish/api/.../ValidActions.java`: `CLOSED_BY_BACK` (shop, guess, spell), where the back key
  is offered beside the buttons.
- `shatterfish/harness/.../driver/Prompts.java`: `WndUpgrade`, `StoneOfIntuition.WndGuess` and
  `WndClericSpells` mapped to the new kinds.
- `shatterfish/harness/.../driver/GuessOptions.java`: the guess window's options (the guess button
  once shown, then the icons named and sorted), shared by the Observer and the executor.
- `shatterfish/harness/.../observer/Observer.java`: `promptOf` takes the guess window's options from
  `GuessOptions`.
- `shatterfish/harness/.../executor/ActionExecutor.java`: `answer` taps the guess window's options
  in the same order.
- `shatterfish/harness/.../agent/RunLoop.java` and `RunOutcome.java`: a Decider that throws ends the
  Run as `BRAIN_ERROR`.
- `shatterfish/brain/.../Answers.java`: the rules; `Policies.answerPrompt(knowledge)` delegates to
  them.

## Tasks & Acceptance

**Execution:**
- [x] The three new kinds, their classification and `GuessOptions`.
- [x] The back key on shop, guess and spell Prompts.
- [x] `BRAIN_ERROR` ending.
- [x] `Answers` and the prompt Policy.
- [x] Docs: five `docs/rules/ui.md` rows (Tier 1), `docs/brain-rules.md` rows 35–39,
  `docs/architecture.md`, `docs/fairness.md`, `docs/ideas.md`.
- [ ] Smoke direction check (the parent runs it).

**Acceptance Criteria:**
- Every Prompt kind has an answer rule, and an unrecognised Prompt is a Brain error rather than a
  stall (`PromptCoverageTest.every_kind_has_a_rule`, `AnswerRulesTest.upgrade`, `.subclass`,
  `BrainErrorTest`).
- `Wait` is never returned while a Prompt is open (`PromptCoverageTest.never_a_wait`).
- `PromptCoverageTest` asserts every Prompt kind the executor supports has a rule.
- Each window the harness now recognises is observed as drawn and answered by a person's input
  (`ItemWindowsTest`: upgrade, guess, spell, trade window), and naming the guess window's icons
  leaks nothing and moves nothing (`ItemWindowsTest.the_guess_options_leak_nothing`,
  `GuessOptionsTest`).
- No smoke Run ends as `UNKNOWN_WINDOW` or `BRAIN_ERROR` (`ShatterfishRunTest`, at its 60-turn cap).
- The PR carries a smoke-set direction check against the previous Brain.

## Design Notes

Constraints restated:
- #1: the Observer adds only what the three windows draw, and ships with the leak tests below.
- #3: no upstream file is edited.
- #5: an Observation must be the same in every process.
- #8: every window's behaviour is cited at `v4.0.0` in `docs/rules/ui.md`.

**How to support the unknown windows. Alternatives:**
1. **Keep the Brain away from them.** Story 4.10 already stops the fallback using items.
   Rejected as the whole answer: any item use a Policy makes can still open them, and a Run would
   still stop dead.
2. **Map them to `ITEM` or `OTHER`.** Their buttons would be read by the existing walk. Rejected:
   - the guess window's choices are icons and the spell list has no labelled button, so neither
     would be answerable;
   - the Brain would have to tell them apart by title, which is fragile.
3. **Three kinds of their own, with fair options.** Chosen:
   - Upgrade reads its two labelled buttons.
   - Guess names its icons.
   - Spell offers only the back key.

**The guess window's icons.**
- **Naming:** an icon is matched by the frame it is drawn with, against every potion, scroll and
  ring type's icon (regular and exotic). The name is the label the guess button itself shows after
  a tap.
- **Order:** the screen draws the icons in `HashSet` order (`Potion.getUnknown()`), which follows
  identity hash codes and differs between JVMs. Listing them by name keeps every Observation of the
  same Run identical across processes. The guess button, once it shows, is listed first.
- **Answering:** the answer takes two waits. The first taps the likeliest type's icon, which leaves
  the window open with the guess button labelled. The second presses that button, which is the
  lowest option with the same label.

**The back key.** It is offered beside the buttons only where it closes the window and nothing
follows:
- **Offered:** the trade window (hide only; the item stays for sale), the guess window (nothing is
  consumed until the guess) and the spell list (nothing is cast).
- **Not offered:** the upgrade window, whose back key reopens the item selector (a `WndBag` no
  Action can answer), and the resurrection window, whose back key does nothing.

**Unrecognised Prompts.**
- **Build time:** `Answers.rule` switches over every `PromptKind`, so a new kind does not compile
  until it has a rule.
- **Run time:** a Prompt whose rule finds nothing it recognises throws `Answers.BrainError`.
  `RunLoop` catches any `RuntimeException` from the Decider and ends the Run as `BRAIN_ERROR`, with
  the wait and the message.
- **Alternatives considered:**
  - Falling through to the fallback, rejected: it would pick an arbitrary button.
  - Letting the exception crash the Run, rejected: the Run's log would be lost and the rig's tally
    would have a hole.

**The subclass choice.** It is a fixed table per class, a judgement for a Brain that fights in melee
and uses no ability:
- Warrior: Berserker
- Mage: Battlemage
- Rogue: Assassin
- Huntress: Warden
- Duelist: Champion
- Cleric: Paladin

The window's buttons are the subclasses' short descriptions, which name them, so the Brain matches
the name as a whole word. The are-you-sure is affirmed only when its title names the chosen
subclass.

**Pre-mortem:**
- **Upgrade targets:** a Policy that reads a known scroll of upgrade onto the worn weapon does not
  exist yet. The upgrade rule only confirms a target someone else chose (docs/ideas.md).
- **Unknown scrolls:** reading an unknown scroll that turns out to be an upgrade still sends the
  selector away (story 4.10's behaviour), losing the scroll. Surfacing the bag as a Prompt is a
  larger change to the executor and is deferred (docs/ideas.md).
- **Exotic guesses:** exotic items have no Beliefs families, so a guess on one is always left.
- **Other windows:** the shop's sell flow and the blacksmith's later windows still open windows no
  Action answers. Leaving the shop avoids the first; the blacksmith is out of the sewers.
- **Catching every `RuntimeException`:** that also turns a Brain bug into a counted ending rather
  than a crash. The detail carries the exception, and `ShatterfishRunTest` fails on any
  `BRAIN_ERROR`.

## Review

**Direction check** (the parent's, `smoke`, against 4.8 at `f262d99bc`):
- the three `UNKNOWN_WINDOW` endings are gone (25 deaths);
- mean turns 595 → 657, median flat at 557;
- deepest 2.12 → 2.16;
- score 637 → 640.

**Fairness reviewer: no violation.** The guess icons are named by each type's own `icon`, not the
appearance. Its three test gaps are closed in `ItemWindowsTest`:
- **Differential:** `the_guess_options_differential` covers every path through the window
  (`StoneOfIntuition.java:159-176`): regular and exotic potions, regular and exotic scrolls, and
  rings. Two true identities give the same options, every icon is named, and the options are sorted.
- **Toggle:** `the_guess_options_toggle` identifies frost mid-Run. Its option goes, and every other
  stays as it was.
- **Determinism:** `the_guess_options_are_pinned` pins the Warrior's first unknown-potion list.

**Lens review: 5 bugs, all fixed with tests.**
1. **HIGH: the Run loop caught every exception from the decider.** That included a diverged
   Replay, which would have ended as `BRAIN_ERROR`.
   - `api` now has `Decider.CannotDecide`. `Answers.BrainError` extends it, and `RunLoop` catches
     only it.
   - `BrainErrorTest.other_exceptions_propagate`; `ReplayRefusalTest` (the forged log still throws
     `Diverged`) and the other Replay tests pass.
2. **HIGH: the Brain error's message never reached the log.**
   - The `end` record gains an optional, chained `detail`, written only for `BRAIN_ERROR` and
     `STALLED`. Every other ending writes the bytes it always did.
   - `RunLogReader` reads it, and `StrategyLog` and `Gallery` print it.
   - The log schema version stays 2. The reasoning is in ADR-0011's new section: the version names
     the header's key set, and optional record members have been added without a bump before.
   - `BrainErrorTest.a_brain_error_is_logged` checks the chain verifies and the reader returns the
     detail.
3. **MEDIUM: the upgrade window the game chains after an upgrade was confirmed too, spending every
   scroll on one item.**
   - Upgrade is confirmed only when the Brain's last Action was the read onto an item
     (`Memory.last()` is `UseItemOn`).
   - The chained window is a Brain error. Its "Back" reopens a selector no Action answers, so no
     answer leaves the Run as a person would.
   - The reason now names the item.
   - Tests: `AnswerRulesTest.chained_upgrade`, `.upgrade_without_a_read`, and
     `BrainAtTheWindowsTest.two_scrolls_upgrade_once` (a real Run: one upgrade, then `BRAIN_ERROR`).
4. **MEDIUM: open-and-leave loops with no time passing.**
   - The Run loop now stops a Run after `WAITS_WITHOUT_A_TURN` (100) waits without a turn, as
     `STALLED`. Before this there was no guard on zero-time applied Actions, only on refusals.
   - `Memory` version 6 carries the windows: the last Action and the item it aimed at, the Action
     that opened the shop, guess or spell list open now, and the openers of windows left.
   - An opener is shunned on its floor once the Brain leaves its window. Only a talk, a purchase or
     an item's use can be shunned, never a Step or a search, and a shunned Action is offered again
     when it is all there is.
   - Tests: `WindowMemoryTest`, `BrainErrorTest.no_time_passing_is_a_stall`, and
     `BrainAtTheWindowsTest.the_shopkeeper_is_left` (a real Run: left, gold unchanged, never talked
     to again).
5. **MEDIUM: the `ValidActions` javadoc overstated the shop's back key.** It is corrected, as is the
   ui Rule: the steal button, and the sell-bag trade window that reopens the bag. The Brain never
   takes "sell", so the back key still suffices for every shop window it meets.

**Low findings:**
- Unmatched guess icons are dropped, not offered with an empty name. The differential test holds
  that every icon the window draws is named.
- A wrong guess is not yet fed to the Beliefs; this is deferred to `docs/ideas.md`.
- The upgrade reason names the item.

**Test changes:**
- `ShatterfishRunTest` asserts every smoke Run ends by death, a win or the cap.
- `BrainAtTheWindowsTest` plays the real Brain through the Run loop from planted states:
  - the Tengu mask for every class, with the class's subclass taken within two waits;
  - the shopkeeper;
  - two upgrade scrolls;
  - a stone of intuition on an unknown potion, left within two waits.
- The harness gains a `testImplementation` dependency on `brain` for this. Main code still cannot
  see it.
- The opener is handed to the Brain as its own, since no Policy opens these windows yet.

**Mutation battery on the fixes: 14 of 14 killed.** The mutants:
- the chained upgrade confirmed;
- the item unnamed in the reason, or forgotten on an answer;
- a left window not shunned;
- a search shunned;
- shunned Actions offered;
- the shun held on every floor;
- an answer recorded as an opener;
- the codec dropping the shunned;
- every exception a Brain error;
- the stall cap never tripping;
- the writer or the reader dropping the detail;
- unnamed icons offered.

Two survived at first and now have tests: `WindowMemoryTest.an_answer_opens_nothing` and
`ItemWindowsTest.an_unknown_icon_is_not_an_option`. The stall-cap mutant loops its test forever, since
no turn passes and the JUnit timeout cannot interrupt the game loop. Its worker was killed, which is
the mutant killed.

Docs: ADR-0011 (the `detail` member), the ui shop Rule, `docs/brain-rules.md` row 40 (the shun),
`docs/architecture.md`, and `docs/ideas.md` (the wrong guess, and the selector as a Prompt).

### Merge with main at `5c0d13993` (stories 4.9, 4.10, 4.12)

**Memory is version 9.** It has main's 36 fields, then `windows`, appended.
- Every helper in `Memory` passes every field: `aiming`, `handed`, `avoiding`, `drinking`, `trying`,
  `resting` and `windowing`. Eight constructor calls were checked by argument count.
- The two constructions in `Beliefs.fold` carry the windows (`opened(...)`, `after.windows()`).
- Story 4.12's 36-argument shape is kept and defaults the windows to none.

**Other resolutions:**
- **Answer rules:** story 4.10's item-confirmation rule moves into `Answers.general`, and its tests are
  kept. The scroll-cancel text is affirmed; any other ITEM or HARMFUL_POTION confirmation is declined by
  "No, I changed my mind". `Policies.SCROLL_CANCEL` and `ITEM_DECLINE` remain as aliases.
- **`Brain.handed`:** main's Step cell, drink, trial and rest bookkeeping, plus the windows.
- **`Brain.decide`:** the shunned Actions are filtered first, then main's rooted filter.
- **Policy order:** answer-prompt, heal, fight, eat, test-item, pick-up, equip, descend, explore,
  fallback.
- **Brain Rules index:** this story's rows are renumbered 59–64, after main's 58.

### Verification of 242586a11

The codec, the STALLED counter, the chained upgrade, the hash chain and information parity are
clean. The smoke direction check on main is identical to main: 25 of 25 Runs take the same Actions,
since story 4.10's fallback already keeps these windows from opening on smoke. Two findings, both
fixed.

**F1: reproducibility (non-negotiable 5).** `BRAIN_ERROR` and `STALLED` Runs were logged
`verifiable: false`, so no Replay ever checked them or their `detail`. Both are now logged verifiable.
- A stall reproduces as it stands: the loop counts before it asks for a decision.
- For a Brain error, Replay's follower runs out of recorded waits where the original Brain threw, and
  now throws `Decider.CannotDecide` with the logged message. The loop then writes the same `end`
  record, byte for byte.
- `ReplayNewEndingsTest` covers both, and each Replay verifies with an identical chain:
  - a stall from the Warrior's broken seal, detached and affixed forever, neither of which takes a
    turn;
  - a Brain error after three searches.
- `BrainErrorTest` now expects the Brain error's `end` record to be verifiable.
- The stale comment in `RunLoop.ending` and ADR-0011's section are updated.

**F2: docs.** The harness's test-only edge to `brain` is now in ADR-0003 (an amendment, following
story 2.1's codex precedent), in the harness row of `docs/architecture.md`, and in the edge comment
of `shatterfish/settings.gradle`.

## Dev Notes

**Tests (one gradle job at a time):**
- `:api:test`: 381 tests, 0 failures, including `ValidActionsTest.some_prompts_close_by_the_back_key`.
- `:brain:test`: 140 tests, 0 failures, including `AnswerRulesTest` (8) and `PromptCoverageTest` (2).
- `:harness:test`:
  - new: `ItemWindowsTest` (5), `GuessOptionsTest` (1), `BrainErrorTest` (1);
  - existing, all passing: `PromptGateTest`, `ActionExecutorTest`, `ActionCompletenessTest`,
    `ActionValidityPropertyTest`, `ObserverGateTest`, `VisibilityChecklistTest`,
    `InputWaitCountTest`, `ItemLeakTest`.
- `:rig:test`: `BrainRulesIndexTest`, `StrategyLogTest`, `LogHeaderTest` and `ShatterfishRunTest` pass.
  `ShatterfishRunTest`'s git-history check is skipped in the worktree, as before.
- `:codex:test` `DocsCitationTest` and `:codex:citations`: no findings.

**Mutation battery: 14 of 14 killed.** Each mutant was planted, its tests run as one gradle job,
and the source restored:

| Mutant | Killed by |
|---|---|
| shop follows the general rule (presses "Buy") | `AnswerRulesTest.shop` |
| upgrade follows the general rule (lowest answer) | `AnswerRulesTest.upgrade` |
| guess is left like a spell list | `AnswerRulesTest.guess` |
| any are-you-sure is affirmed, whatever subclass it names | `AnswerRulesTest.subclass` |
| the Cleric takes the Priest | `AnswerRulesTest.subclass` |
| the least likely identity is guessed | `AnswerRulesTest.guess` |
| the last matching option is pressed (the icon instead of the guess button) | `AnswerRulesTest.guess` |
| a name matches inside a word | `AnswerRulesTest.names_are_words` |
| an unanswerable Prompt is not an error | `AnswerRulesTest.upgrade` |
| a shop cannot be left | `ValidActionsTest.some_prompts_close_by_the_back_key` |
| the guess icons in the screen's order | `ItemWindowsTest` (two cases) |
| the guess window's icons are not options | `ItemWindowsTest.the_guess_window` |
| the spell list is no Prompt | `ItemWindowsTest.the_spell_window` |
| a Brain error propagates instead of ending the Run | `BrainErrorTest` |

A control run with no mutant passed, and one harness mutant was rerun by hand to confirm the
failure was its tests' and not the build's.

**Merge notes for the parent:**
- `docs/brain-rules.md` rows 35–39 collide with 4.9's and 4.10's new rows; renumber on merge.
- Story 4.10's cancel confirmation for an identified-by-use scroll is `ITEM` with "Yes, I'm positive"
  first. The general rule still presses option 0 (`AnswerRulesTest.general` pins it).
- The prompt Policy is now `Policies.answerPrompt(knowledge)`. `Policies.ANSWER_PROMPT` is the name
  string, which `Brain.policyNames()` uses.
- There are no Memory or Weights changes.
