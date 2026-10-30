---
title: 'Story 10.20: Tengu phase two, the threat map'
type: 'feature'
created: '2026-10-29'
status: 'review'
baseline_commit: 'a0ce37919'
review_loop_iteration: 0
context: []
---

## Intent

**Problem:** With story 10.19 every practice hero brings Tengu through the first half of the fight (240 of 240 reach
his yell) and then dies in the second, with him at about a third of his health. In the second half he throws a smoke
bomb, a cone of fire and a shocker at the hero, and the fight Policy strikes from wherever it stands: on the bomb's
cell as it counts down, in the fire, and in the shocker's reach.

**Approach:** A threat map of the cells the screen draws as about to be struck (`Threats`), built from the
Observation's heaps and blobs and nothing else, priced in hit points by four Playbook numbers. The fight Policy keeps
beside Tengu through it: it strikes where it stands unless a cell within a few steps is cheaper once the strikes lost
on the way are counted, accepts the least bad cell beside him when every one is a threat, and holds clear while a blast or
the fire that passes covers every cell beside him.

## Acceptance criteria and where they are held

1. Phase two confirmed against the pinned code with `path:line`: Rule `tengu-threat` in `docs/brain-rules.md`, the
   phase-two row in `docs/rules/levels.md` (cadence `Tengu.java:450-559`, bomb `:566-743`, fire `:749-925`, shocker
   `:932-1141`).
2. One screen per effect where the Policy of story 10.19 stands on a damage cell and the new one leaves it, and a
   screen where every cell beside him is a threat and the least bad is accepted: `TenguThreatMapTest`.
3. The map is built from the Observation only: `TenguThreatLeakTest` (equal to a model written apart over 300
   random screens; a permuted Brain seed changes neither map nor Decision; a bomb under fog and a blob on a
   remembered cell cannot be put on a screen; the fire's cells not yet drawn are not on the map; the permutation
   control gives different Decisions when one bomb is drawn).
4. The first test fails without the Policy (ADR-0019): see the pull request.
5. Four new numbers in the Playbook (`bosses.tengu`: `bomb_cost`, `fire_cost`, `shock_cost`, `adjacent_cost`) and in
   the tunables registry; `TunableRegistryTest`, `PlaybookTest`.
6. A CoachLine sentence: `tengu/dodge`, `tengu/hold` in `CoachLine`, `coach-words.txt`, `CoachLineTest`,
   `docs/coach-line.md`.
7. `fairness-reviewer` run on the diff.
8. Practice table (6 classes x 40, before and after) and the off-floor direction check:
   `docs/results/e10-practice-kit.md`.

## Design notes

- **Micro-brainstorm.** Options for what the map is and how it steers: (a) a per-cell cost map with a short plan over
   it (chosen); (b) a one-ply "never stand on a marked cell" filter; (c) a retreat out of Tengu's reach whenever anything
   is drawn; (d) a search over the Observation's successor states; (e) a timer in the Memory that counts the bomb's
   waits. (b) cannot leave a blast two steps wide (every step from the middle is still marked) and cannot accept the
   least bad. (c) gives up the strikes that are the whole point of the preference for staying adjacent. (d) is the
   tactical search ADR-0010 closes at this budget. (e) needs a turn counter the Observation does not carry and leaves a
   wrong guess as the failure; the cost of not having it is a few turns of standing clear per bomb, parked as an idea
   if the practice table shows it matters.
- **The countdown is not on the screen.** The "3...", "2...", "1..." float over the bomb as text
  (`Tengu.java:621-628`) and the Observation carries no floating text, so the whole of a bomb's life counts as the turn it
  lands, and the hero holds clear for it to pass.
- **The cone is drawn a tick ahead, no more.** Only cells holding a fire or shocker blob are on the map; the cells the
  cone reaches next are not drawn, so they are not on it.
- **A step costs a strike.** The preference for staying adjacent is `adjacent_cost` hit points for every step off a
  strike, and again for every step from a cell not beside him back to one beside him, so a walk that only moves away from
  a cost puts it off. Without the second term a permanent threat made the hero oscillate on the edge of its field.
- **The shocker does not pass.** The hero holds only for a threat that ends (a bomb, the fire) and never for the
  shocker, whose machine stays for the fight.
- **Information parity.** No Observer change: the heaps and the two blobs were already carried (ADR-0006). The map is
  stateless: no Memory, no countdown, no read of the game.

## Verification

See the pull request: the practice table, the direction columns and the test that fails without the Policy.
