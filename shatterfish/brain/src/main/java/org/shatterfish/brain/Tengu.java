package org.shatterfish.brain;

import org.shatterfish.api.Action;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Hunger;
import org.shatterfish.api.MapSection;
import org.shatterfish.api.NoteKind;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Playbook;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Tile;

import java.util.List;

/**
 * Tengu's floor before the fight (story 10.18, issue #364): get the key, open the cell, and go in
 * rested. The fight itself is the fight Policy's until stories 10.19 and 10.20 give it a Policy of
 * its own.
 *
 * <p><b>The floor.</b> Depth 10 is the prison boss level ({@code Dungeon.java:320-322}); the boss
 * floors are 5, 10, 15, 20 and 25 ({@code Dungeon.java:441-443}). It opens in state START with the
 * hero on the entrance stairs, which lead up, and no mob on the level and none that will spawn
 * ({@code PrisonBossLevel.java:160}, {@code :166}, {@code :598-604}). A one-cell-wide hallway runs
 * south from the entrance room; four side cells open off it by plain doors, and an iron key lies on
 * the floor of one of them ({@code PrisonBossLevel.java:168-202}, {@code :621-625}). The hallway ends
 * at a locked door, and Tengu's cell lies behind it ({@code PrisonBossLevel.java:173}, {@code :207}).
 * The iron key opens it ({@code Hero.java:1274-1275}); an {@code Unlock} beside the door uses the key
 * and turns the door into a plain door, and the next Step opens it ({@code Hero.java:2430-2436}).
 *
 * <p><b>The seal.</b> The turn the hero steps past the door, onto a row below it, the level seals: the
 * doors lock, Tengu wakes hunting, and the {@code LockedFloor} buff starts
 * ({@code PrisonBossLevel.java:580-583}, {@code :410-458}; {@code Level.java:657-662}). After that
 * there is no leaving ({@code Level.java:606-609}). So the door is opened once the hero is as strong
 * as it will get: with no mob on the floor and none spawning, a rest here costs only food.
 *
 * <p><b>What the Brain reads.</b> The tile the screen draws as a locked door, the journal's note of
 * an iron key (a key goes into the journal on pickup and not into the pack, {@code Key.java:53-69}),
 * the hero's hit points and hunger, and the header's {@code sealed} flag. It reads no cell number: the
 * door is wherever the screen draws it. Nothing hidden is used; the key's heap is the pick-up Policy's
 * to fetch once the explore Policy has opened the cell doors and the screen draws it.
 *
 * <p><b>What it does.</b> With the key held and a locked door drawn, it rests until the Playbook's
 * {@code enter_per_mille} of the hit points (at most {@code rest_limit} rests, never while starving),
 * walks to the cell beside the door and hands over the Unlock. Once the door is a plain door it has
 * nothing to say: the explore Policy sees the unseen cells behind it as a frontier and walks in, and
 * the seal is that step. Without the key it says nothing, so the pick-up and explore Policies find it.
 *
 * <p><b>The stairs are not the way down.</b> On this floor, before the seal and with no enemy in view,
 * {@link Brain} withholds the stairs up from every Policy ({@link #holds}): the entrance leads back to
 * depth 9, a fresh prison floor, and no Policy of the descend kind may take it for a way down (the
 * diagnosis in {@code docs/results/e10-practice-kit.md}).
 */
final class Tengu implements Policy {

    /** The Policy's name, as the Decision records it and {@link Brain#policyNames()} lists it. */
    static final String NAME = "tengu";

    /** The depth of the prison boss level (Dungeon.java:320-322). */
    static final int DEPTH = 10;

    /** An iron key's title (items.properties: {@code items.keys.ironkey.name=iron key}). */
    static final String IRON_KEY = "iron key";

    private final Codex.Knowledge knowledge;
    private final Playbook.Tengu entry;

    Tengu(Codex.Knowledge knowledge, Playbook.Tengu entry) {
        this.knowledge = knowledge;
        this.entry = entry;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String goal() {
        return "tengu: entry";
    }

    /**
     * Whether the hero stands on the prison boss floor before its seal with nothing in view: the screen on
     * which the stairs up are no choice at all (no mob lives on this floor before the seal,
     * PrisonBossLevel.java:598-604, so an enemy in view came down the stairs behind the hero).
     */
    static boolean holds(Observation observation, Codex.Knowledge knowledge) {
        return observation.header().depth() == DEPTH && observation.header().branch() == 0
                && !observation.header().sealed() && Calm.quiet(observation, knowledge);
    }

    @Override
    public boolean enters(Observation observation, Memory memory) {
        return holds(observation, knowledge) && lockedDoor(observation.map()) >= 0;
    }

    @Override
    public RunLog.Choice choose(Observation observation, Memory memory, List<Action> offered, Stream stream) {
        MapSection map = observation.map();
        int door = lockedDoor(map);
        if (door < 0 || !keyHeld(observation)) {
            return null;
        }
        if (unready(observation, memory)) {
            for (Action rest : List.of(new Action.Rest(true), new Action.Rest(false), new Action.Search())) {
                if (offered.contains(rest)) {
                    return new RunLog.Choice(rest, Policies.CERTAIN, "tengu: rest");
                }
            }
            return null;
        }
        Action.Unlock hand = new Action.Unlock(door);
        if (offered.contains(hand)) {
            return new RunLog.Choice(hand, Policies.CERTAIN, "tengu: unlock");
        }
        Explore.Path path = Explore.nearest(map, Explore.walkable(observation, memory, true),
                observation.hero().cell(), offered, cell -> adjacent(map, cell, door));
        if (path == null) {
            path = Explore.nearest(map, Explore.walkable(observation, memory, false),
                    observation.hero().cell(), offered, cell -> adjacent(map, cell, door));
        }
        return path == null ? null : new RunLog.Choice(path.step(), Policies.CERTAIN, "tengu: door " + path.distance());
    }

    /**
     * Whether the hero should rest before it opens the door: below the Playbook's share of its hit
     * points, not starving (a starving hero regenerates nothing, Regeneration.java:56), and under the
     * entry's rest limit on this floor. The limit and the starving rule keep a rest the game cuts short
     * from holding the hero until it starves.
     */
    boolean unready(Observation observation, Memory memory) {
        return 1000L * observation.hero().hp() < (long) entry.enterPerMille() * observation.hero().ht()
                && observation.hero().hunger() != Hunger.STARVING && memory.rests() < entry.restLimit();
    }

    /** The cell the screen draws as a locked door (the lowest such cell), or -1. */
    static int lockedDoor(MapSection map) {
        for (int cell = 0; cell < map.tiles().size(); cell++) {
            if (map.tiles().get(cell) == Tile.LOCKED_DOOR) {
                return cell;
            }
        }
        return -1;
    }

    /** Whether the journal shows an iron key for this depth (Key.java:53-69, Notes.java:614). */
    static boolean keyHeld(Observation observation) {
        int depth = observation.header().depth();
        return observation.journal().notes().stream().anyMatch(note -> note.kind() == NoteKind.KEY
                && note.depth() == depth && note.title().equals(IRON_KEY));
    }

    /** Whether {@code cell} is one of {@code target}'s eight neighbours. */
    private static boolean adjacent(MapSection map, int cell, int target) {
        int width = map.width();
        int dx = Math.abs(cell % width - target % width);
        int dy = Math.abs(cell / width - target / width);
        return cell != target && dx <= 1 && dy <= 1;
    }
}
