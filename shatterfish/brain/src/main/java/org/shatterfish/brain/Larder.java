package org.shatterfish.brain;

import org.shatterfish.api.Hunger;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.Observation;


/**
 * The food economy (story 4.13): how many turns the hero can go before it starves, and what a floor
 * may spend of them.
 *
 * <p><b>Why.</b> Of 40 Warriors on the tuning set, 19 died starving, none of them holding food. Each
 * regular floor places one food (Level.java:224-226), and the Warrior starts with one ration
 * (HeroClass.java:108); a ration is 300 turns (Food.java:51). The floors took 340 to 365 turns each
 * (median, depths 1 and 2): more than the floor feeds.
 *
 * <p><b>The clock.</b> The hunger value is never on the screen, only its icon's three states
 * (Hunger.java:178-186), and the Observation carries no turn counter. So the Brain keeps its own
 * estimate, {@link Memory#hunger}: hunger rises one a turn (Hunger.java:88-95), so each Action it
 * handed over adds the turns it takes -- a Step, an attack or a wait one (Hero.java:210), a search two
 * and four more hunger (Hero.java:211-212, :2624-2629), a meal three (Food.java:47), a rest ten turns
 * for each hit point it restored (Regeneration.java:44) -- and a meal takes off its food's energy
 * (Hunger.java:146-148). The icon then clamps it to its band: under 300 with no icon, 300 to 449
 * hungry, 450 starving (Hunger.java:40-41). It is an estimate from the Brain's own Actions and the
 * screen, never the game's value.
 *
 * <p><b>The budget.</b> The turns before starving: the food held, known by name ({@link Eat#ENERGY}),
 * plus what is left of the clock. Below {@link #FRUGAL} the explore Policy searches only promising
 * spots, at most {@link Explore#FRUGAL_SEARCHES} a floor, and the test-item Policy does not walk to a
 * better testing cell. The threshold is an assumption, tuned against the rig: resting less and leaving
 * floors earlier measured worse (story 4.13's tuning log, S2 and F1).
 */
final class Larder {

    /** Turns of play per hit point regenerated (Regeneration.java:44, REGENERATION_DELAY). */
    static final int TURNS_PER_HP = 10;

    /** A search: two turns, and four more hunger (Hero.java:211-212, :2624-2629). */
    static final int SEARCH_COST = 6;

    /** A meal: three turns (Food.java:47). */
    static final int MEAL_COST = 3;

    /** Hunger at which the icon shows hungry (Hunger.java:40). */
    static final int HUNGRY = 300;

    /** The budget below which the hero searches only where a secret is plausible and walks no further to test (turns). */
    static final int FRUGAL = 600;

    private Larder() {
    }

    /** The turns the known food in the pack buys, each stack counted in full. */
    static int food(Observation observation) {
        long turns = 0;
        for (ItemView item : observation.inventory().items()) {
            Integer energy = Eat.ENERGY.get(item.name());
            if (item.kind() == ItemKind.FOOD && energy != null) {
                turns += (long) energy * item.quantity();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, turns);
    }

    /**
     * The clock after this screen: {@code before}, plus what the Action handed over last cost (its
     * kind {@code last}; {@code moved} whether the hero left its cell; {@code gained} the hit points it
     * gained; {@code eaten} the turns of food the pack lost), less a meal's energy, clamped to the
     * icon's band.
     */
    static int clock(int before, String last, boolean moved, int gained, int eaten, Hunger icon) {
        int cost = switch (last) {
            case "Step" -> moved ? 1 : 0;
            case "Search" -> SEARCH_COST;
            case "Rest" -> Math.max(1, TURNS_PER_HP * Math.max(0, gained));
            case "" -> 0;
            default -> 1;
        };
        long value = (long) before + cost;
        if (last.equals("UseItem") && eaten > 0) {
            value = Math.max(0, value + MEAL_COST - eaten);
        }
        return switch (icon) {
            case NONE -> (int) Math.min(value, HUNGRY - 1);
            case HUNGRY -> (int) Math.max(HUNGRY, Math.min(value, Memory.HUNGER_STARVING - 1));
            case STARVING -> Memory.HUNGER_STARVING;
        };
    }

    /** The turns before the hero starves: the food held and what is left of the clock. */
    static int budget(Observation observation, Memory memory) {
        return food(observation) + Math.max(0, Memory.HUNGER_STARVING - memory.hunger());
    }

    /** Whether food is tight: the budget under {@link #FRUGAL}. */
    static boolean frugal(Observation observation, Memory memory) {
        return budget(observation, memory) < FRUGAL;
    }
}
