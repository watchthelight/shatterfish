package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Observation;
import org.shatterfish.api.PromptKind;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.TalentView;

import java.util.List;

/**
 * Spend the talent points the hero has earned (issue #219).
 *
 * <p>A hero gets a point in the first tier at each of levels 2 to 6 and one in the second at each of 7 to 12
 * (Talent.java:436, Hero.java:398-407), and a point unspent is not lost but is not working either. Until
 * this Policy only the fallback ever spent one, at random and only on a wait that nothing else wanted: in
 * the 40 Warrior Runs of the tuning slice 19 points were spent in all, against the five a hero at level 6
 * has earned. Spending a point
 * takes no time (Hero.java:377-388 neither spends nor ends a turn), so there is nothing to weigh it
 * against and the Policy takes it whenever the screen offers one, fight or no fight.
 *
 * <p><b>Which talent.</b> A fixed order per class, from what each talent does at the pinned tag. The
 * Warrior's, with every talent at most two points (Talent.java:438-440):
 * <ol>
 *   <li>Iron Will: the Broken Seal's shield holds {@code 3 + 2 * armour tier + points}
 *       (BrokenSeal.java:97), two more on every hit under half health;</li>
 *   <li>Provoked Anger: after that shield is broken the next hit adds {@code 1 + 2 * points} damage
 *       (Talent.java:870-873, ShieldBuff.java:127-129);</li>
 *   <li>Hearty Meal: a meal below a third of full health heals {@code 2 + 2 * points} (Talent.java:588-594);</li>
 *   <li>Veteran's Intuition, which only identifies equipment sooner (Talent.java:689-694), last of the first
 *       tier. Then the second tier: Lethal Momentum (a kill earns a free attack, {@code 0.34 + 0.33 * points}
 *       of the time, Mob.java:1038-1041), Liquid Willpower (a barrier on a potion, Talent.java:711-716), Iron
 *       Stomach (a one-turn meal, Food.java:101-108), Improvised Projectiles (Item.java:670-677) and
 *       Runic Transference (BrokenSeal.java:76-84).</li>
 * </ol>
 *
 * <p>A talent not in the list is not this Policy's: another class's points are still left to the fallback.
 * The talents, their points and the points available are on the talents pane, so the Policy reads the
 * screen and the game's published rules and nothing else.
 */
final class Talents implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "talents";

    /** The Warrior's talents in the order points go to them, by the title the pane shows (lower case). */
    static final List<String> WARRIOR = List.of(
            "iron will", "provoked anger", "hearty meal", "veteran's intuition",
            "lethal momentum", "liquid willpower", "iron stomach", "improvised projectiles", "runic transference");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "talents: spend";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return observation.header().prompt() == PromptKind.NONE && pick(observation) != null;
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        TalentView talent = pick(observation);
        if (talent == null) {
            return null;
        }
        Action take = new Action.Talent(talent.name());
        return offered.contains(take) ? new RunLog.Choice(take, Policies.CERTAIN, "talent: " + talent.name()) : null;
    }

    /**
     * The talent that takes the next point: the first in {@link #WARRIOR} order that the screen offers a
     * point to, or null. A tier without a point to spend, or a talent holding its most, is not offered
     * ({@link org.shatterfish.api.ValidActions}).
     */
    static TalentView pick(Observation observation) {
        List<Action> offered = observation.actions().actions();
        for (String name : WARRIOR) {
            for (TalentView talent : observation.hero().talents()) {
                if (talent.name().equalsIgnoreCase(name)
                        && offered.contains(new Action.Talent(talent.name()))) {
                    return talent;
                }
            }
        }
        return null;
    }
}
