package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Emote;
import org.shatterfish.api.Fog;
import org.shatterfish.api.HeroSection;
import org.shatterfish.api.Hypothesis;
import org.shatterfish.api.Lookahead;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.ObservationCodec;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.Playout;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RolloutEnd;
import org.shatterfish.api.RolloutResult;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.SearchPlan;
import org.shatterfish.api.Weights;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The fight search (story 10.24, ADR-0025, non-negotiable 4 option b): in an engaged fight, the Actions the rules would
 * weigh are each played forward through a {@link Lookahead} in several determinizations of what the hero cannot see, the
 * rules Brain playing the turns after the first, and the Action with the best mean outcome is taken. It is a one-step
 * policy improvement over the rules: an Action other than the rules' own pick is taken only when its mean beats the pick's
 * by a margin ({@link SearchPlan#marginMilli()}), because a few samples are noisy and the rules are not wrong.
 *
 * <p><b>What it reads.</b> The Observation, the Memory, the Codex's knowledge, its own seed and the Observations a look-ahead
 * hands back. The look-ahead is the only way to the engine and takes a {@link Hypothesis} of plain draws and a
 * {@link Playout} that is a function of Observations; it hands back an {@link RolloutResult}. The hypotheses are drawn
 * from the Policy's own stream (a function of the Brain's constant seed and the wait), and the mobs they remember are the
 * Brain's own sightings of this floor ({@link Memory#monsters()}). Nothing of the hidden state is asked for, so nothing of it
 * can be answered.
 *
 * <p><b>When it acts.</b> Prompt closed, an enemy beside the hero or an awake walker within {@link SearchPlan#radius()}
 * cells (the one-ply model's test), not the Goo fight, and, under gate 1, only a dangerous fight: one the fight Policy
 * judges unfavourable, a hero under three quarters of its health, or two enemies in view or more. It needs two distinct
 * candidates to choose between: the fight Policy's own ranking (its pick first) and an attack on each enemy beside the hero,
 * at most {@link SearchPlan#candidates()}.
 *
 * <p><b>What a rollout is worth</b> (thousandths of the hero's maximum hit points): the hit points lost, negative; the
 * enemies' health taken, in enemies (a defeated one counts whole, and one out of view counts as unhurt), times
 * {@link SearchPlan#enemyMilli()}; and the hero's death, {@link SearchPlan#deathMilli()} negative.
 *
 * <p><b>Declines.</b> With no look-ahead (the Overlay's loop lends none), with fewer than two candidates, or when the
 * scrubber declines any sample, the Policy offers nothing and the fight Policy takes the wait. A refusal is never evidence
 * about the hidden state (ADR-0009), so it is never counted, scored or recorded.
 */
final class FightSearch implements Policy {

    /** The Policy's name, as the Decision records it. */
    static final String NAME = "fight-search";

    private final Codex.Knowledge knowledge;
    private final Weights weights;
    private final Playbook playbook;
    private final SearchPlan plan;
    private final long seed;
    private final Supplier<Lookahead> lent;
    private final Fight fight;
    private final Brain rules;

    FightSearch(Codex.Knowledge knowledge, Weights weights, Playbook playbook, SearchPlan plan, long seed,
                Supplier<Lookahead> lent) {
        this.knowledge = knowledge;
        this.weights = weights;
        this.playbook = playbook;
        this.plan = plan;
        this.seed = seed;
        this.lent = lent;
        this.fight = new Fight(knowledge, playbook.bosses().goo());
        // The rules that play the turns after the first: this configuration without the search, built once.
        this.rules = new Brain(knowledge, weights, playbook, seed, Brain.Model.BASELINE);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "fight: look ahead";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        if (lent.get() == null || observation.header().prompt() != PromptKind.NONE) {
            return false;
        }
        List<ActorView> engaged = engaged(observation);
        if (engaged.isEmpty()) {
            return false;
        }
        return plan.gate() == 0 || dangerous(observation, memory, engaged);
    }

    /** The enemies that make this a fight: beside the hero, or awake walkers within the plan's radius. */
    private List<ActorView> engaged(Observation observation) {
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        List<ActorView> engaged = new ArrayList<>();
        for (ActorView enemy : Fight.enemies(observation, knowledge)) {
            int distance = Fight.chebyshev(map, hero, enemy.cell());
            if (distance <= 1 || distance <= plan.radius() && enemy.emote() != Emote.SLEEP
                    && !Bestiary.immobile(knowledge, depth, branch, enemy.name())
                    && !Bestiary.atRange(knowledge, depth, branch, enemy.name())) {
                engaged.add(enemy);
            }
        }
        return engaged;
    }

    private boolean dangerous(Observation observation, Memory memory, List<ActorView> engaged) {
        HeroSection hero = observation.hero();
        if (hero.hp() * 4 < hero.ht() * 3 || engaged.size() >= 2) {
            return true;
        }
        List<ActorView> enemies = Fight.enemies(observation, knowledge);
        return !Fight.favourable(observation, knowledge, Fight.threats(observation, memory, enemies, knowledge));
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        List<RunLog.Choice> ranked = ranked(observation, memory, offered, stream);
        return ranked.isEmpty() ? null : ranked.get(0);
    }

    @Override
    public List<RunLog.Choice> ranked(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        Lookahead lookahead = lent.get();
        List<ActorView> enemies = Fight.enemies(observation, knowledge);
        if (lookahead == null || enemies.isEmpty()
                || Goo.engage(observation, memory, offered, enemies, playbook.bosses().goo()) != null) {
            return List.of();
        }
        List<RunLog.Choice> candidates = candidates(observation, memory, offered, stream, enemies);
        if (candidates.size() < 2) {
            return List.of();
        }
        int n = candidates.size();
        long[] totals = new long[n];
        Brain inner = rules;
        for (int k = 0; k < plan.samples(); k++) {
            Hypothesis hypothesis = hypothesis(observation, memory, stream);
            for (int i = 0; i < n; i++) {
                Action first = candidates.get(i).action();
                RolloutResult result = lookahead.roll(hypothesis, playout(inner, memory.belief(), first), plan.horizon());
                if (result == null) {
                    // The scrubber declined: no sample, and the rules take the wait.
                    return List.of();
                }
                totals[i] += value(observation, result);
            }
        }
        // The rules' pick is index 0. Another candidate must beat it by the margin to be taken.
        int best = 0;
        for (int i = 1; i < n; i++) {
            if (totals[i] > totals[best]) {
                best = i;
            }
        }
        long samples = plan.samples();
        if (best != 0 && totals[best] - totals[0] < plan.marginMilli() * samples) {
            best = 0;
        }
        List<RunLog.Choice> ranked = new ArrayList<>();
        ranked.add(annotated(candidates.get(best), totals[best], samples, best == 0 ? "rules pick" : "beats the rules pick by "
                + (totals[best] - totals[0]) / samples));
        List<Integer> rest = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (i != best) {
                rest.add(i);
            }
        }
        rest.sort((a, b) -> Long.compare(totals[b], totals[a]));
        for (int i : rest) {
            ranked.add(annotated(candidates.get(i), totals[i], samples, "mean " + totals[i] / samples));
        }
        return ranked;
    }

    /** The choice with the look-ahead's mean as its score, and the original reason kept first (the Memory reads it). */
    private RunLog.Choice annotated(RunLog.Choice choice, long total, long samples, String note) {
        return new RunLog.Choice(choice.action(), total / samples, choice.why() + " [search " + plan.samples() + "x" + plan.horizon()
                + " mean " + total / samples + ": " + note + "]");
    }

    /** The fight Policy's own ranking, then an attack on each enemy beside the hero it does not already list. */
    private List<RunLog.Choice> candidates(Observation observation, Memory memory, List<Action> offered, Stream stream,
                                           List<ActorView> enemies) {
        List<RunLog.Choice> out = new ArrayList<>();
        for (RunLog.Choice choice : fight.ranked(observation, memory, offered, stream)) {
            if (out.size() < plan.candidates() && out.stream().noneMatch(other -> other.action().equals(choice.action()))) {
                out.add(choice);
            }
        }
        if (out.isEmpty()) {
            return out;
        }
        for (ActorView enemy : enemies) {
            Action attack = new Action.Attack(enemy.cell());
            if (out.size() < plan.candidates() && offered.contains(attack)
                    && out.stream().noneMatch(other -> other.action().equals(attack))) {
                out.add(new RunLog.Choice(attack, 0, "attack: " + enemy.name()));
            }
        }
        return out;
    }

    /** The Hypothesis for sample draws of this wait: three draws and the sightings the Brain believes are still there. */
    private Hypothesis hypothesis(Observation observation, Memory memory, Stream stream) {
        long dungeonSeed = stream.next();
        long salt = stream.next();
        long draws = stream.next();
        List<Hypothesis.Remembered> remembered = new ArrayList<>();
        MapSection map = observation.map();
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        for (Memory.Seen seen : memory.monsters()) {
            // A sighting from an earlier wait, on this floor, of an enemy that is not in view now, on a cell the player has
            // visited; each is believed still there with one chance in two.
            if (seen.depth() != depth || seen.at() >= memory.waits() || seen.cell() >= map.fog().size()
                    || map.fog().get(seen.cell()) != Fog.VISITED || remembered.size() >= 4) {
                continue;
            }
            Codex.Tactics tactics = knowledge.tactics(seen.name(), depth, branch);
            boolean there = stream.below(2) == 0;
            if (there && tactics != null) {
                remembered.add(new Hypothesis.Remembered(tactics.className(), seen.cell()));
            }
        }
        return new Hypothesis(dungeonSeed, salt, draws, remembered);
    }

    /**
     * The playout of one candidate: the candidate first, then whatever the rules Brain does, carrying the Belief forward
     * from the one this wait has as the rollout's own Observations arrive.
     */
    private static Playout playout(Brain rules, Belief start, Action first) {
        Belief[] belief = {start};
        return (observation, applied) -> {
            if (applied == 0) {
                belief[0] = rules.handed(observation, belief[0], new Brain.Decided(first, null, List.of(), ""));
                return first;
            }
            belief[0] = rules.update(observation, belief[0]);
            Brain.Decided decided = rules.decide(observation, belief[0]);
            // A line that uses an item stops here: what an unidentified item does in a rollout is the scrubber's permuted
            // assignment, not the Run's (ADR-0025 amendment), so no rollout is allowed to depend on it.
            if (decided.action() == null || decided.action() instanceof Action.UseItem || decided.action() instanceof Action.UseItemAt
                    || decided.action() instanceof Action.UseItemOn) {
                return null;
            }
            belief[0] = rules.handed(observation, belief[0], decided);
            return decided.action();
        };
    }

    /** What a rollout is worth, in thousandths of the hero's maximum hit points. */
    private long value(Observation start, RolloutResult result) {
        if (result.end() == RolloutEnd.DEATH) {
            return -plan.deathMilli();
        }
        if (result.end() == RolloutEnd.REFUSED && result.applied() == 0 || result.observations().isEmpty()) {
            // The Action the screen offered was not taken: a candidate that does not work is worth the worst.
            return -2L * plan.deathMilli();
        }
        Observation end = result.observations().get(result.observations().size() - 1);
        long lost = (long) (start.hero().hp() - end.hero().hp()) * 1000 / Math.max(1, start.hero().ht());
        List<ActorView> before = Fight.enemies(start, knowledge);
        List<ActorView> after = Fight.enemies(end, knowledge);
        int defeated = Math.min(before.size(), Math.max(0, defeats(end) - defeats(start)));
        int remaining = before.size() - defeated;
        long pipsBefore = 0;
        for (ActorView enemy : before) {
            pipsBefore += enemy.healthPips();
        }
        long pipsAfter = 0;
        for (ActorView enemy : after) {
            pipsAfter += enemy.healthPips();
        }
        // An enemy neither defeated nor in view is out of sight, not hurt: it keeps the health it was seen with.
        if (remaining > after.size() && !before.isEmpty()) {
            pipsAfter += (long) (remaining - after.size()) * pipsBefore / before.size();
        }
        long taken = Math.max(0, pipsBefore - pipsAfter) * plan.enemyMilli() / ObservationCodec.MAX_HEALTH_PIPS;
        return taken - lost;
    }

    /** How many of "you defeated ..." the log window holds. */
    private static int defeats(Observation observation) {
        int n = 0;
        for (var line : observation.log().lines()) {
            if (line.text().startsWith("You defeated")) {
                n++;
            }
        }
        return n;
    }
}
