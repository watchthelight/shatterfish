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
 *   <li><b>Hungry with no food.</b> The hunger icon shows hungry or starving, no food the eat Policy
 *       knows is held, no frontier is left to reach, and the next floor is not a boss floor. Every
 *       regular floor of the main branch places a food item when it is built, and a boss floor places
 *       none (Level.java:224-226; the boss depths, Dungeon.java:441-443): so the next regular floor
 *       holds one, and this floor's own is in the part still to uncover while a frontier is left (the
 *       pick-up Policy, above this one, has walked to every heap drawn). The searches are skipped: a
 *       hungry hero regenerates, but only for 150 turns before it starves (Hunger.java:40-41), and a
 *       starving one regenerates nothing and loses health (Regeneration.java:56, Hunger.java:78-85).</li>
 *   <li><b>Overstayed.</b> The waits on this floor reach its {@linkplain #allowance allowance}:
 *       {@link #ALLOWANCE} waits, and {@link #PER_DROP} more for each guaranteed drop expected still on
 *       this floor. On every floor but the first, a wandering enemy spawns every 50 turns until the
 *       floor holds its limit (Level.java:149, :764-778; MobSpawner.java:39-52;
 *       RegularLevel.java:206-217), and hunger rises a point a turn (Hunger.java:40-41). The allowance
 *       counts waits, not turns: a wait is at least a turn, so it is a bound on a floor that has stopped
 *       paying, not a pace. The floors of the `smoke` set take 60 to 630 waits when nothing goes
 *       wrong.</li>
 * </ul>
 * A guaranteed drop -- a potion of strength, a scroll of upgrade, an arcane stylus -- is placed when a
 * floor of its set is built (Level.java:224-243; Dungeon.java:529-563), and its set is spent by the
 * last floor before the boss. The Brain counts what it has found ({@link Beliefs.Chapter}), and what
 * it picked up under an appearance that is still held, unidentified, and may still be the drop, as
 * found too, so a floor is not held for a potion already in the pack; what is owed and not found lies on this floor or the ones
 * left in the set, and the Policy expects {@code owed / floorsLeft} of it here. It cannot know what
 * earlier floors held and it missed.
 *
 * <p><b>Fit for the next floor.</b> The next floor is harder: more enemies and stronger ones
 * (RegularLevel.java:206-217; MobSpawner.java:71). The last Step onto the exit travels -- a click on a
 * transition cell with no enemy in view takes the stairs (Hero.java:2000-2007) -- so the Policy walks
 * to the cell beside the exit and rests there to full health first, then takes that Step. A rest
 * regenerates one hit point every ten turns (Regeneration.java:44). If an enemy comes meanwhile the
 * fight Policy does not flee down the exit hurt ({@link Fight#down}); it fights, steps away or takes
 * the stairs up. A hero standing on the exit already -- come up from the
 * floor below -- rests there and then takes the {@code Descend}. It does not rest while starving, which
 * regenerates nothing, and rests at most {@link #RESTS} times on a floor, so a rest the game keeps
 * cutting short does not hold the hero.
 *
 * <p><b>Never down while sealed.</b> The Policy does not enter while the header says the floor is
 * sealed ({@code Level.locked}, Level.java:181, :657-670), which a boss fight sets; and it takes only a
 * regular exit drawn as the open exit, never the boss floor's locked one (SewerBossExitRoom.java:63).
 *
 * <p><b>An exit never seen.</b> A regular floor's exit room joins the rest by regular doors, never a
 * locked one (ExitRoom.java:60; only special rooms lock theirs); but a regular door may be hidden, and
 * on a secrets floor a standard room may be reachable only through hidden doors
 * (RegularPainter.java:221-263). So when the Policy would leave and the screen has never shown an
 * exit, it searches on past the explore Policy's budget, up to {@link #SEARCHES} spots on the floor.
 *
 * <p><b>What it leaves to others.</b> It acts only on a calm screen: no Prompt open, no enemy in view.
 * It stands aside while the explore Policy owes a rest before going back to a floor it fled, and while
 * the hero stands inside a region the fight Policy retreated from with a Step farther out of it (story
 * 4.7): those plans come first.
 * It yields for one wait when the hero has stood still after {@link Explore#STUCK} - 1 refused Steps,
 * and the cell of the Step is then blocked ({@link Beliefs#fold}).
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

    /** The most spots searched on a floor while looking for an exit never seen: three times explore's. */
    static final int SEARCHES = 3 * Explore.SEARCHES;

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
        if (memory.streak() == Explore.STUCK - 1) {
            return null;
        }
        return plan(observation, memory, offered, knowledge);
    }

    /** The plan before the stuck rule: nothing while the floor is worth staying on, else toward the exit. */
    private static RunLog.Choice plan(Observation observation, Memory memory, List<Action> offered,
                                      Codex.Knowledge knowledge) {
        if (Explore.restOwed(observation, memory) || Explore.away(observation, memory, offered) != null) {
            return null;
        }
        String why = leaving(observation, memory, knowledge);
        if (why == null) {
            return null;
        }
        MapSection map = observation.map();
        if (exit(map, observation.hero().cell())) {
            // Come up from below onto the exit: rest here, then down.
            RunLog.Choice rest = rest(observation, memory, offered);
            if (rest != null) {
                return rest;
            }
            Action descend = new Action.Descend();
            return offered.contains(descend) ? new RunLog.Choice(descend, Policies.CERTAIN, "descend: " + why) : null;
        }
        Explore.Path path = toward(observation, offered, Explore.walkable(observation, memory, true));
        if (path == null) {
            path = toward(observation, offered, Explore.walkable(observation, memory, false));
        }
        if (path == null) {
            if (known(map)) {
                return null;
            }
            // No exit on the screen yet: search on for it, past explore's budget.
            RunLog.Choice search = Explore.searchOn(observation, memory, offered, SEARCHES);
            return search == null ? null
                    : new RunLog.Choice(search.action(), Policies.CERTAIN, "no exit: " + search.why());
        }
        // Beside the exit, the next Step travels: rest to full first.
        if (path.distance() == 1) {
            RunLog.Choice rest = rest(observation, memory, offered);
            if (rest != null) {
                return rest;
            }
        }
        return new RunLog.Choice(path.step(), Policies.CERTAIN, "exit: " + why + " " + path.distance());
    }

    /** A rest before going down, when {@link #rests} allows one and the screen offers it; else null. */
    private static RunLog.Choice rest(Observation observation, Memory memory, List<Action> offered) {
        if (!rests(observation, memory)) {
            return null;
        }
        for (Action rest : List.of(new Action.Rest(true), new Action.Rest(false), new Action.Search())) {
            if (offered.contains(rest)) {
                return new RunLog.Choice(rest, Policies.CERTAIN, "rest: descent");
            }
        }
        return null;
    }

    /**
     * Why the hero leaves this floor, or null while it is worth staying on: {@code "spent"},
     * {@code "hungry"} or {@code "overstayed"}. See the class comment.
     */
    static String leaving(Observation observation, Memory memory, Codex.Knowledge knowledge) {
        if (Explore.spent(observation, memory)) {
            return "spent";
        }
        if (observation.hero().hunger() != Hunger.NONE && !fed(observation) && !bossNext(observation, knowledge)
                && !Explore.frontier(observation, memory)) {
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
     * Whether the floor below is a boss floor: the last of a set of floors, as the Codex counts them
     * (a set ends at a boss, Dungeon.java:441-443; CodexKnowledge reads the set's length from the boss
     * depths). With no set in the Codex, every floor below may be one.
     */
    static boolean bossNext(Observation observation, Codex.Knowledge knowledge) {
        if (knowledge.guarantees().isEmpty()) {
            return true;
        }
        return observation.header().branch() == 0
                && (observation.header().depth() + 1) % knowledge.guarantees().get(0).floorsPerSet() == 0;
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
            Codex.Guarantee guarantee = knowledge.guarantees().stream()
                    .filter(g -> g.counter().equals(chapter.counter())).findFirst().orElseThrow();
            int floor = depth % guarantee.floorsPerSet();
            // What is owed, less what was picked up under an appearance of the drop's family and not
            // yet identified: it may be the drop.
            int owed = Math.max(0, chapter.owed() - unidentified(observation, memory, knowledge, guarantee, chapter.set()));
            if (floor == 0 || owed == 0) {
                continue;
            }
            expected += (double) owed / (guarantee.floorsPerSet() - floor);
        }
        return expected;
    }

    /**
     * How many items were picked up in {@code set} under an appearance that may still be {@code guarantee}'s
     * item: held now, still unidentified, and with the item among what the Beliefs say it may be
     * ({@link Memory#pending}). An appearance identified as something else, or no longer held, stops
     * counting, so a potion that turned out to be healing does not stand for the potion of strength.
     */
    private static int unidentified(Observation observation, Memory memory, Codex.Knowledge knowledge,
                                    Codex.Guarantee guarantee, int set) {
        int count = 0;
        for (Beliefs.Guess guess : Beliefs.identities(observation, knowledge)) {
            if (guess.odds().stream().anyMatch(odds -> odds.name().equals(guarantee.name()) && odds.probability() > 0)) {
                count += Memory.count(memory.pending(), guess.label(), set);
            }
        }
        return count;
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

    /** Whether to rest before going down: hurt, not starving, and under {@link #RESTS} on this floor. */
    static boolean rests(Observation observation, Memory memory) {
        return observation.hero().hp() < observation.hero().ht() && observation.hero().hunger() != Hunger.STARVING
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

    /** Whether the screen shows a regular exit at all, open or not. */
    private static boolean known(MapSection map) {
        return map.transitions().stream().anyMatch(t -> t.kind() == TransitionKind.REGULAR_EXIT);
    }

    /** The first Step toward the nearest open regular exit over {@code walk}, the exit itself allowed. */
    private static Explore.Path toward(Observation observation, List<Action> offered, boolean[] walk) {
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
