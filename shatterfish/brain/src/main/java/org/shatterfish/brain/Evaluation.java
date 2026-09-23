package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Weights;

import java.util.List;

/**
 * The one scoring function (story 4.5, FR-33): a weighted sum of features read from the
 * Observation, with the weights handed over as data.
 *
 * <p>Two kinds of feature. The <em>position</em> features score the screen as it stands -- hit
 * points, depth, level, strength, hunger, enemies in view -- and are the same whichever Action is
 * taken, so they never change a choice at one wait; they are what a later search compares positions
 * by. The <em>action</em> features are read from the screen and the Action together -- rest while
 * hurt, attack, descend, search, wait -- and are what a Policy that scores its offered Actions
 * compares. Every feature is something the screen shows; none reads anything the Observation does
 * not carry.
 *
 * <p>Integer arithmetic only, so every machine scores alike. The weight set must weight exactly the
 * features named here: a set that forgot one, or weighted one this Evaluation does not have, would
 * be a set whose meaning the Brain does not share, and is refused at construction.
 */
final class Evaluation {

    /** Hit points as thousandths of the maximum. */
    static final String HP = "hp";
    /** The floor. */
    static final String DEPTH = "depth";
    /** The hero's level. */
    static final String LEVEL = "level";
    /** The hero's strength, bonus included. */
    static final String STRENGTH = "strength";
    /** 0 when fed, 1 when hungry, 2 when starving. */
    static final String HUNGER = "hunger";
    /** Enemies the screen shows. */
    static final String ENEMIES = "enemies";
    /** Thousandths of hit points missing, when the Action rests. */
    static final String REST_HURT = "act_rest_hurt";
    /** 1 when the Action attacks. */
    static final String ATTACK = "act_attack";
    /** 1 when the Action descends. */
    static final String DESCEND = "act_descend";
    /** 1 when the Action searches. */
    static final String SEARCH = "act_search";
    /** 1 when the Action waits. */
    static final String WAIT = "act_wait";

    /** Every feature, sorted, as a weight set must state them. */
    static final List<String> FEATURES = List.of(REST_HURT, ATTACK, DESCEND, SEARCH, WAIT, DEPTH, ENEMIES,
            HP, HUNGER, LEVEL, STRENGTH).stream().sorted().toList();

    private final Weights weights;

    Evaluation(Weights weights) {
        if (weights == null) {
            throw new IllegalArgumentException("an Evaluation is built on weights its caller read");
        }
        if (!weights.features().equals(FEATURES)) {
            throw new IllegalArgumentException("the weight set " + weights.name() + " weights "
                    + weights.features() + " and this Evaluation has the features " + FEATURES);
        }
        this.weights = weights;
    }

    Weights weights() {
        return weights;
    }

    /** The score of the screen as it stands. */
    long position(Observation observation) {
        var hero = observation.hero();
        long hp = hero.ht() <= 0 ? 0 : 1000L * Math.max(0, hero.hp()) / hero.ht();
        long enemies = 0;
        for (ActorView actor : observation.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY) {
                enemies++;
            }
        }
        return weights.weight(HP) * hp
                + weights.weight(DEPTH) * observation.header().depth()
                + weights.weight(LEVEL) * hero.level()
                + weights.weight(STRENGTH) * (hero.strength() + hero.strengthBonus())
                + weights.weight(HUNGER) * hero.hunger().ordinal()
                + weights.weight(ENEMIES) * enemies;
    }

    /** The score of taking {@code action} from this screen: the position's, plus the Action's own. */
    long of(Observation observation, Action action) {
        long score = position(observation);
        var hero = observation.hero();
        if (action instanceof Action.Rest) {
            long hp = hero.ht() <= 0 ? 1000 : 1000L * Math.max(0, hero.hp()) / hero.ht();
            score += weights.weight(REST_HURT) * (1000 - Math.min(1000, hp));
        } else if (action instanceof Action.Attack) {
            score += weights.weight(ATTACK);
        } else if (action instanceof Action.Descend) {
            score += weights.weight(DESCEND);
        } else if (action instanceof Action.Search) {
            score += weights.weight(SEARCH);
        } else if (action instanceof Action.Wait) {
            score += weights.weight(WAIT);
        }
        return score;
    }
}
