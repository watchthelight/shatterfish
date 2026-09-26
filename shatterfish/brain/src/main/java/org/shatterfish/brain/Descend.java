package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.ItemKind;
import org.shatterfish.api.ItemView;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;
import org.shatterfish.api.TransitionKind;
import org.shatterfish.api.TransitionView;

import java.util.List;

/**
 * Go down when this floor has given what it will (story 4.12, FR-31), and not before the hero is fit
 * for the next one.
 *
 * <p><b>When to leave.</b> The floor's remaining value is weighed against the risk of staying, and the
 * Policy leaves when the value no longer covers the risk. There are three ways that happens:
 * <ul>
 *   <li><b>Spent.</b> The explored fraction is whole -- no frontier the hero can reach -- and no search
 *       is worth making ({@link Explore#spent}): nothing left to find, and every wait on the floor
 *       costs food.</li>
 *   <li><b>Hungry with no food.</b> The hunger icon shows hungry or starving and no food the eat Policy
 *       knows is held. Every regular floor of the main branch places a food item when it is built
 *       (Level.java:224-226), so the next floor holds one; this one's known heaps the pick-up Policy,
 *       above this one, has already walked to. A starving hero loses health and regenerates none
 *       (Hunger.java:78-85, Regeneration.java:56).</li>
 *   <li><b>Overstayed.</b> The waits on this floor reach its {@linkplain #allowance allowance}:
 *       {@link #ALLOWANCE} waits, and {@link #PER_DROP} more for each guaranteed drop expected still on
 *       this floor. On every floor but the first, a wandering enemy spawns every 50 turns until the
 *       floor holds its limit (Level.java:149, :764-778; MobSpawner.java:39-52;
 *       RegularLevel.java:206-217), and the hunger clock runs a turn at a time (Hunger.java:40-41). The
 *       allowance is a bound on a floor that has stopped paying, not a pace: the floors of the `smoke`
 *       set take 60 to 630 waits when nothing goes wrong.</li>
 * </ul>
 * A guaranteed drop -- a potion of strength, a scroll of upgrade, an arcane stylus -- is placed when a
 * floor of its set is built (Level.java:224-243; Dungeon.java:529-563), and its set is spent by the
 * last floor before the boss. The Brain counts what it has found ({@link Beliefs.Chapter}); what is
 * owed and not found lies on this floor or the ones left in the set, and the Policy expects
 * {@code owed / floorsLeft} of it here. It cannot know what earlier floors held and it missed.
 *
 * <p><b>Fit for the next floor.</b> The next floor is harder: more enemies and stronger ones
 * (RegularLevel.java:206-217; MobSpawner.java:71). The Policy walks to the regular exit and, standing
 * on it, rests to full health before it descends: a rest regenerates one hit point every ten turns
 * (Regeneration.java:44), the stairs underfoot are the fight Policy's way out if an enemy comes, and a
 * hero that went down at a third of its health met the next floor's first enemy at a third. It rests
 * only when the hunger icon shows nothing -- a hungry hero with food has eaten (the eat Policy stands
 * above), a hungry one without food is going down to find it, and a starving one regenerates nothing
 * -- and at most {@link #RESTS} times on a floor, so a rest the game keeps cutting short does not hold
 * the hero at the exit.
 *
 * <p><b>Never down while sealed.</b> The Policy does not enter while the header says the floor is
 * sealed ({@code Level.locked}, Level.java:180, :657-670), which a boss fight sets; and it takes only a
 * regular exit drawn as the open exit, never the boss floor's locked one (SewerBossExitRoom.java:63).
 *
 * <p>It acts only on a calm screen: no Prompt open, no enemy in view. It yields for one wait when the
 * hero has stood still after {@link Explore#STUCK} - 1 refused Steps, and the cell its Step pointed at
 * is then blocked ({@link Beliefs#fold}).
 */
final class Descend implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "descend";

    /** The waits a floor is worth staying on with no guaranteed drop expected on it. An assumption. */
    static final int ALLOWANCE = 500;

    /** The waits more a floor is worth for each guaranteed drop expected on it. An assumption. */
    static final int PER_DROP = 250;

    /** The most rests the Policy hands over on a floor before going down. */
    static final int RESTS = 20;

    /** The exits a click on travels: the open exit, and the boss floor's once unlocked (Tile.java). */
    private static final java.util.Set<Tile> OPEN_EXITS = java.util.EnumSet.of(Tile.EXIT, Tile.UNLOCKED_EXIT);

    private final Codex.Knowledge knowledge;

    Descend(Codex.Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "descend: floor";
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return Explore.calm(observation) && !observation.header().sealed();
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        if (memory.streak() >= Explore.STUCK - 1) {
            return null;
        }
        return plan(observation, memory, offered, knowledge);
    }

    /**
     * The cell of the Step this Policy's plan takes on {@code observation} under {@code memory}, or
     * null when the plan is not a Step: what the Memory records as blocked when the hero stays put.
     */
    static Integer stepCell(Observation observation, Memory memory, Codex.Knowledge knowledge) {
        if (!Explore.calm(observation) || observation.header().sealed()) {
            return null;
        }
        RunLog.Choice choice = plan(observation, memory, observation.actions().actions(), knowledge);
        return choice != null && choice.action() instanceof Action.Step step ? step.cell() : null;
    }

    /** The plan before the stuck rule: nothing while the floor is worth staying on, else to the exit, rest, down. */
    private static RunLog.Choice plan(Observation observation, Memory memory, List<Action> offered,
                                      Codex.Knowledge knowledge) {
        String why = leaving(observation, memory, knowledge);
        if (why == null) {
            return null;
        }
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        if (exit(map, hero)) {
            if (rests(observation, memory)) {
                for (Action rest : List.of(new Action.Rest(true), new Action.Rest(false), new Action.Search())) {
                    if (offered.contains(rest)) {
                        return new RunLog.Choice(rest, Policies.CERTAIN, "rest: descent");
                    }
                }
            }
            Action descend = new Action.Descend();
            return offered.contains(descend) ? new RunLog.Choice(descend, Policies.CERTAIN, "descend: " + why) : null;
        }
        Explore.Path path = toward(observation, memory, offered, Explore.walkable(observation, memory, true));
        if (path == null) {
            path = toward(observation, memory, offered, Explore.walkable(observation, memory, false));
        }
        return path == null ? null
                : new RunLog.Choice(path.step(), Policies.CERTAIN, "exit: " + why + " " + path.distance());
    }

    /**
     * Why the hero leaves this floor, or null while it is worth staying on: {@code "spent"},
     * {@code "hungry"} or {@code "overstayed"}. See the class comment.
     */
    static String leaving(Observation observation, Memory memory, Codex.Knowledge knowledge) {
        if (Explore.spent(observation, memory)) {
            return "spent";
        }
        if (observation.hero().hunger() != Hunger.NONE && !fed(observation)) {
            return "hungry";
        }
        long stayed = memory.waits() - memory.arrived();
        // The allowance is never under ALLOWANCE, so the drops expected are read only past it.
        if (stayed >= ALLOWANCE && stayed >= allowance(observation, memory, knowledge)) {
            return "overstayed";
        }
        return null;
    }

    /**
     * The waits this floor is worth: {@link #ALLOWANCE}, and {@link #PER_DROP} for each guaranteed
     * drop expected still on it -- of each drop owed in this set of floors, the share the floors left
     * in the set, this one among them, would hold if it were spread over them evenly. The boss floor
     * closing each set places none (Level.java:224), and neither does the surface.
     */
    static long allowance(Observation observation, Memory memory, Codex.Knowledge knowledge) {
        return ALLOWANCE + Math.round(PER_DROP * expectedHere(observation, memory, knowledge));
    }

    /** The guaranteed drops expected still on this floor: see {@link #allowance}. */
    static double expectedHere(Observation observation, Memory memory, Codex.Knowledge knowledge) {
        int depth = observation.header().depth();
        if (observation.header().branch() != 0 || knowledge.guarantees().isEmpty()) {
            return 0;
        }
        double expected = 0;
        Beliefs beliefs = Beliefs.view(memory, observation, knowledge);
        for (Beliefs.Chapter chapter : beliefs.chapters()) {
            int floorsPerSet = knowledge.guarantees().stream().filter(g -> g.counter().equals(chapter.counter()))
                    .findFirst().orElseThrow().floorsPerSet();
            int floor = depth % floorsPerSet;
            if (floor == 0 || chapter.owed() == 0) {
                continue;
            }
            expected += (double) chapter.owed() / (floorsPerSet - floor);
        }
        return expected;
    }

    /** Whether the hero holds a food the eat Policy knows (story 4.9). */
    static boolean fed(Observation observation) {
        for (ItemView item : observation.inventory().items()) {
            if (item.kind() == ItemKind.FOOD && Eat.ENERGY.containsKey(item.name())) {
                return true;
            }
        }
        return false;
    }

    /** Whether to rest on the exit before going down: see the class comment. */
    static boolean rests(Observation observation, Memory memory) {
        return observation.hero().hp() < observation.hero().ht() && observation.hero().hunger() == Hunger.NONE
                && memory.rests() < RESTS;
    }

    /** Whether {@code cell} is a regular exit drawn open. */
    private static boolean exit(MapSection map, int cell) {
        for (TransitionView transition : map.transitions()) {
            if (transition.cell() == cell && transition.kind() == TransitionKind.REGULAR_EXIT
                    && OPEN_EXITS.contains(map.tiles().get(cell))) {
                return true;
            }
        }
        return false;
    }

    /** The first Step toward the nearest open regular exit over {@code walk}, the exit itself allowed. */
    private static Explore.Path toward(Observation observation, Memory memory, List<Action> offered, boolean[] walk) {
        MapSection map = observation.map();
        boolean any = false;
        for (TransitionView transition : map.transitions()) {
            if (exit(map, transition.cell())) {
                walk[transition.cell()] = true;
                any = true;
            }
        }
        return any ? Explore.nearest(map, walk, observation.hero().cell(), offered, cell -> exit(map, cell)) : null;
    }
}
