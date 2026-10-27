---
title: 'Story 8.10: Autoexplore with the bot''s brains'
type: 'feature'
created: '2026-10-27'
status: 'review'
baseline_commit: '4ef201a30'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent, do not modify unless human renegotiates">

## Intent

**Problem:** A learner who has taken the game wants to skip the dull corridors without handing the Run back to a bot
that will fight, descend and drink unknown potions for them (FR-47, UJ-4).

**Approach:** An Autoexplore control, enabled in HUMAN. It asks for the hand back Hand back asks for, then lets the
Brain's Decisions run one wait at a time, each looked at by a pure set of stop rules before a credit executes it. A
rule that holds gives the game back to the person with a takeover, the Decision in hand kept as a shadow and never
executed. Only Decisions of a closed list of Policies run.

## Acceptance Criteria (from epics.md Story 8.10, with the tests that hold them)

1. Started from HUMAN, the Brain decides and the executor applies each Action as in RUNNING, restricted to Actions
   whose Policy is on a closed list the story commits and documents; each wait is recorded with actor `bot` and the
   Mode record's existing kinds, so no log format changes. The list is `explore` (walking, opening doors, searching,
   resting to heal) and `pick-up`; see Design notes. `AutoexploreTest.the_list_names_real_policies`,
   `a_quiet_wait_goes_on`, `only_from_human`, `a_quiet_stretch_plays_on`; and over real Runs
   `AutoexploreRunTest.stretches_over_real_runs` (every credit a stretch spent was a Decision of a listed Policy, the
   Mode records are `HUMAN` and `PAUSED` only, every wait is `bot`, the logs verify).
2. Control returns to the person, in HUMAN, at the first Input wait where a hostile actor is in view, where any
   enabled Pause-on condition holds, where the Decision's Policy is not on the list, where the HP fraction fell
   since the wait before, or where the person presses Pause, and the Mode strip names which one.
   `AutoexploreTest.an_enemy_walks_into_view` (strip reads "HUMAN: autoexplore stopped, enemy in view"),
   `pause_on_conditions_end_it`, `a_boss_floor`, `a_policy_off_the_list`, `a_low_hp_stretch`,
   `pause_returns_control`, `the_other_ways_back`.
3. `AutoexploreTest` covers each stop on a constructed state (an enemy walking into view, a boss floor, a low HP
   stretch, a Policy off the list) and a stretch that runs to the end of the level and stops there
   (`the_end_of_the_level`), and asserts control returned at the wait before the Action the person would have
   refused: the wait is not in the Run's executed list and a takeover was asked for at it. `the_first_wait_is_looked_at`
   holds the first wait of a stretch to the same rules. (The test is on a Run that is a list of calls, as 8.3's
   `PauseOnControlsTest` is; the real Run is `AutoexploreRunTest`, because a constructed state cannot be put in front
   of `EmbeddedHost`.)
4. A stop caused by an enemy at the edge of vision is the same wait on the same Observation with and without the
   Oracle flag. The oracle's sidecar is a separate value that the stop rules cannot name: the oracle bit sits in the
   header and is the only difference. `AutoexploreTest.the_oracle_flag_changes_no_stop` (64+ pairs, every stop kind)
   and `the_rules_cannot_see_the_oracle` (ArchUnit: `Autoexplore` depends on nothing in the harness or the game).
5. The story reports, from `standard` Runs played by a scripted learner who autoexplores whenever it can, the share
   of turns autoexplore covered and the number of times it returned control with an enemy already adjacent, which is
   the failure and is to be near zero. See Results.
6. Autoexplore at Human play speed uses 5.7's speed modes and 5.13's draw routing unchanged: no new draw or sound
   call is added, and `RenderQueueTest` stays green. `AutoexploreTest.the_speed_modes_are_the_same` (the first credit
   at once, the next after the interval).
7. Fairness: the PR is labelled `fairness` and carries the `fairness-reviewer` verdict, for the stop rules and the
   Policy list.
8. No rig numbers: Brain play does not change. The stop rules live in the Overlay, the Brain is untouched, and a
   Run nobody autoexplores has the same Action list as before (`PauseOnRunTest` still passes unchanged).

</frozen-after-approval>

## Design notes

**How a stretch is built from what exists.** The pinned machinery already changes hands at an Input wait: Hand back
lands the Run held, in PAUSED, with a Decision for the wait in front (`EmbeddedRun.tryHandBack`), and Take over keeps
the Decision it has in hand as the wait's shadow without executing it (`EmbeddedRun.takeOverFromPending`). A stretch is
those two with the stop rules between them. `ControlState.autoexplore()` asks for the hand back; when it applies the
stretch begins (RUNNING, the speed mode in force, `Next Step` becoming the readable pace as Resume does it);
`autoexploreStops()` looks at each wait once, after its Decision has landed and before a credit executes it, the place
8.3 looks for Pause-on; a stop calls `returnControl()`, which holds the Run and asks for the takeover. The log then
reads `PAUSED` (the hand back), the bot's waits, `HUMAN` (the takeover): the existing kinds, so no format changes and a
replay of it works as one of a hand back and a takeover does.

**Alternatives for the control.** (1) A new Mode `AUTOEXPLORING`: touches every enablement row and every Mode switch
and the log's Mode record. (2) A flag on RUNNING, hand back and takeover as above: no log change, the matrix keeps its
three Modes. (3) A Run-level feature in `EmbeddedRun`: puts the Overlay's rules in the harness. (2) chosen. A pure
HUMAN Run, which has no Brain's controls (5.9), has no Autoexplore; the control belongs to a Brain's Run the person
has taken over, the case UJ-4 describes.

**The closed list.** `Autoexplore.POLICIES = {explore, pick-up}`, by the name a Decision records
(`Brain.policyNames()`). `explore` is the Policy that walks to unexplored ground, opens doors, searches and rests to
heal when the screen is calm (`Explore.java`); `pick-up` takes what lies on the way. Not on the list, and why: `fight`,
`one-ply`, `ranged`, `zap` (fighting), `descend` and `plunge` (changing floors; a finished level stops the stretch at
the descend it would take), `test-item`, `equip`, `heal`, `eat`, `douse` (using items, unknown ones included),
`open-chest` (a chest can be a mimic or cost a key), `answer-prompt` (the person answers their own windows), `fallback`.
A whitelist, so a Policy a later story adds is off it until a story puts it on. Under it a backstop: an Action that
attacks, changes floors, uses or throws an item, uses an ability or talent, or buys is refused whichever Policy chose
it. `AutoexploreTest.the_backstop_under_the_list`.

**The stop rules and their order.** Enemy in view, an enabled Pause-on condition (8.3's evaluator, unchanged),
Policy off the list, HP fraction fell since the wait before. Pause, Next Step, Take over and a Brain in trouble are
the controls' and return control the same way, with their own words. A rule is looked at on every wait including the
first, so a rat already in view makes a stretch that stops at once; that is the honest answer to pressing Autoexplore
in front of a rat. A boss-floor or low-HP condition the person has on counts the first wait of a stretch as entering,
the same as 8.3 counts a first wait.

**Information parity.** The rules read the Observation, the Decision and the wait before's `Previous`, nothing else.
An actor is in the Observation only if the screen shows it, so an enemy at the edge of vision stops the stretch at the
wait it is drawn at. The oracle header bit is not read.

**Coach mode.** Control returns to HUMAN, where coach mode lives (story 8.9, not built yet); when it is on it stays
on, since Autoexplore touches no setting of it. Nothing here needs 8.9.

**Hotkey.** None. F6 to F11 are taken and `HotkeyTest` probes F12 as an unbound key; a key for Autoexplore is a story
that changes that test, not this one.

## Results

`AutoexploreRunTest` over the first 20 Warriors of the `standard` set (salt 1000 plus the Warrior's place, as the Rig's
`--salt-base 1000`), each to a 3000-turn cap, the learner autoexploring whenever it can and handing every fight and
every Decision off the list to the bot:

| | |
|---|---|
| Runs, turns played | 20, 44,909 |
| Stretches | 2,892 |
| Turns the stretches covered | 18,847 (42.0% of turns) |
| Returns of control | 2,889 (the rest were still under way when the Run ended) |
| Returns with an enemy adjacent | 111 (3.8% of returns) |
| of those, an enemy in view a wait earlier (a stretch that went on past one) | 0 |

Why control came back: descend is next 1,244; enemy in view 754; open-chest is next 349; test-item is next 182; eat
is next 125; HP fell 123; fight is next 50; fallback is next 46; plunge is next 12; douse is next 4.

Reading it. The failure the epic names is a stretch that goes on when it should have stopped, and that is 0: no return
had an enemy in view at the wait before it. The 111 adjacent returns are enemies first seen when already next to the
hero (round a corner, out of the dark, through a door), where the wait they appear at is the first one a rule over
what the screen shows can stop at. They are 3.8% of returns, not "near zero"; the number the story set cannot get lower
without a rule that reads what the screen does not show, which is the leak. The learner takes the descend, the potion
and the fight itself through the bot, so 42% is a floor for a person who plays those with their own hands. Of the
returns, 43% are the end of a level (the stairs) and 26% an enemy; the first is what a finished level does to a
stretch, and the table's "open-chest" and "test-item" rows are the Brain wanting the very Actions the list keeps from it.
To reproduce: `AUTOEXPLORE_RUNS=20 AUTOEXPLORE_TURNS=3000 ./gradlew :overlay:cleanTest :overlay:test --tests "*AutoexploreRunTest" -i`.

A learner that autoexplores at once at a wait where the Decision is off the list loops (each press returns at the same
wait). The scripted learner found this first: it is why it hands such a wait to the bot, and why a stretch's stop is
a reason the strip shows and not a silent no-op. A person sees the reason and plays that wait.

No rig numbers are given: the Brain is untouched.

## Live-session checklist (the product owner's acceptance; no agent has opened a window)

Run `./gradlew :overlay:launch -Plaunch.args="--seed <standard seed> --class warrior"`.

- [ ] Press Take over. The Controls row shows an Autoexplore button, lit in HUMAN and dim in RUNNING and PAUSED.
- [ ] In a corridor with nothing in view press Autoexplore: the strip reads "RUNNING: autoexplore", the hero walks
      and picks things up at the speed mode you set, and nothing is attacked.
- [ ] Let it find a monster: it stops with the strip reading "HUMAN: autoexplore stopped, enemy in view" and the
      monster still some way off, and the game is yours. The reason goes when you make your first move.
- [ ] Press Pause during a stretch: the game is yours, in HUMAN, not in PAUSED.
- [ ] Explore a floor to its end: the stretch stops at the stairs ("descend is next, which is not on the list") and
      does not descend.
- [ ] Turn on "Before item use" in the settings: a stretch stops before it would use an item, and a rest to heal does
      not stop it.
- [ ] Take damage (hunger, a trap, a bite) during a stretch: it stops at the next wait.
