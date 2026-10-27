package org.shatterfish.overlay;

import org.shatterfish.api.Action;
import org.shatterfish.api.ActorView;
import org.shatterfish.api.Alignment;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;

import java.util.Optional;
import java.util.Set;

/**
 * The stop rules of autoexplore (story 8.10, FR-47) as a pure function: given the Observation at an Input
 * wait and the Decision the Brain is about to execute there, should control go back to the person before it
 * does?
 *
 * <p>Autoexplore hands a quiet stretch to the Brain from HUMAN. The Brain decides and the executor applies each
 * Action as in RUNNING, but only Actions whose Policy is on {@link #POLICIES}, the closed list, and only while
 * nothing is going on that a person would want to see. The list is: {@code explore} (walking to unexplored
 * ground, opening doors, searching, and resting to heal when the explore Policy rests) and {@code pick-up}
 * (taking what lies on the way). It is never {@code fight}, {@code one-ply}, {@code ranged}, {@code zap},
 * {@code descend}, {@code plunge}, anything that uses an unknown item ({@code test-item}, {@code equip},
 * {@code heal}, {@code eat}, {@code douse}) or throws. The list is a whitelist: a Policy a later story adds is
 * off it until a story puts it on, and {@code AutoexploreTest.the_list_names_real_policies} fails if a name on
 * it is not a Policy the Brain has.
 *
 * <p>Control returns at the first wait where, in this order, a hostile actor is in view, an enabled Pause-on
 * condition holds ({@link PauseOn}), the Decision's Policy is not on the list (or its Action is one a
 * person would refuse whatever Policy chose it), or the hit-point fraction fell since the wait before. The
 * person pressing Pause is the fifth, and is the controls', not this function's. The check is made before the
 * Decision executes, so the Action that would have been refused is not taken.
 *
 * <p>Information parity (non-negotiable 1): the function reads the {@link Observation}, the
 * {@link RunLog.Decision} and {@link PauseOn.Previous}, which is drawn from the Observation of the wait before.
 * An actor is in the Observation only when the screen shows it, so an enemy at the edge of vision stops the
 * stretch at the wait where it is drawn and not before. The oracle's sidecar is a separate value this class
 * cannot name ({@code AutoexploreTest.the_rules_cannot_see_the_oracle}), and the oracle bit in the header
 * changes nothing here ({@code AutoexploreTest.the_oracle_flag_changes_no_stop}).
 */
final class Autoexplore {

    /**
     * The Policies autoexplore may run, by the name a Decision records. A person who sent the bot to explore
     * would accept each of these without looking.
     */
    static final Set<String> POLICIES = Set.of("explore", "pick-up");

    /** Why control came back, as the Mode strip says it after "autoexplore stopped". */
    record Stop(Kind kind, String why) {
    }

    enum Kind {
        ENEMY_IN_VIEW, PAUSE_ON, OFF_LIST, HP_FELL, PAUSED, TOOK_OVER, NO_DECISION, BRAIN_TROUBLE
    }

    private Autoexplore() {
    }

    /**
     * The stop that holds at this wait, or empty when autoexplore goes on. {@code decision} is null when the
     * Run gave none to look at, which stops the stretch: a step nobody can name is not on the list.
     */
    static Optional<Stop> stop(OverlaySettings settings, Observation observation, RunLog.Decision decision,
                               PauseOn.Previous previous, Set<Integer> bossDepths) {
        if (hostileInView(observation)) {
            return Optional.of(new Stop(Kind.ENEMY_IN_VIEW, "enemy in view"));
        }
        Optional<PauseOn.Condition> condition = PauseOn.first(settings, observation, decision, previous, bossDepths);
        if (condition.isPresent()) {
            return Optional.of(new Stop(Kind.PAUSE_ON, PauseOn.label(condition.get(), settings)));
        }
        if (decision == null) {
            return Optional.of(new Stop(Kind.NO_DECISION, "no decision to look at"));
        }
        if (!onTheList(decision)) {
            return Optional.of(new Stop(Kind.OFF_LIST, decision.policy() + " is next, which is not on the list"));
        }
        if (hpFell(observation, previous)) {
            return Optional.of(new Stop(Kind.HP_FELL, "HP fell"));
        }
        return Optional.empty();
    }

    /** Whether a hostile actor is in the Observation, which holds only what the screen shows. */
    static boolean hostileInView(Observation observation) {
        for (ActorView actor : observation.actors().actors()) {
            if (actor.alignment() == Alignment.ENEMY) {
                return true;
            }
        }
        return false;
    }

    /** Whether the Decision's Policy is on the list and its Action is not one a person would refuse on sight. */
    static boolean onTheList(RunLog.Decision decision) {
        return POLICIES.contains(decision.policy()) && !refused(decision.chosen().action());
    }

    /**
     * Actions that fight, change floors, use or throw items, or answer for the person, whichever Policy chose
     * them. A backstop under the list, so a Policy that is on it cannot slip one of these through.
     */
    private static boolean refused(Action action) {
        return action instanceof Action.Attack || action instanceof Action.Descend || action instanceof Action.Ascend
                || action instanceof Action.UseItem || action instanceof Action.UseItemAt
                || action instanceof Action.UseItemOn || action instanceof Action.Ability
                || action instanceof Action.AbilityAt || action instanceof Action.Talent
                || action instanceof Action.Buy;
    }

    /** Whether the hit-point fraction is lower than at the wait before: {@code hp / ht < hp' / ht'}. */
    static boolean hpFell(Observation observation, PauseOn.Previous previous) {
        if (previous == null || previous.ht() <= 0 || observation.hero().ht() <= 0) {
            return false;
        }
        return (long) observation.hero().hp() * previous.ht() < (long) previous.hp() * observation.hero().ht();
    }
}
