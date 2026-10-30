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
 * rested; and, from story 10.19 (issue #365), the first half of the fight itself ({@link #engage}).
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

    /** The boss's name as the screen shows it (actors.properties: {@code actors.mobs.tengu.name=Tengu}). */
    static final String BOSS = "Tengu";

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
    static boolean adjacent(MapSection map, int cell, int target) {
        int width = map.width();
        int dx = Math.abs(cell % width - target % width);
        int dy = Math.abs(cell / width - target / width);
        return cell != target && dx <= 1 && dy <= 1;
    }

    /**
     * The fight with Tengu on the sealed floor, the first half of it (story 10.19): he is in view, awake,
     * and the hero is to strike him and nothing else. Null when this is not that fight.
     *
     * <p><b>Why melee, and why not a retreat.</b> Tengu never moves: his hunting turn attacks when a
     * clear line of fire reaches the hero and otherwise only spends the turn ({@code Tengu.java:385-429}),
     * and his chance to hit is a skill of 20 from range and 10 beside the hero ({@code Tengu.java:106-113}),
     * against a hero's defence, so the cells between are the dearest and the cell beside him the cheapest.
     * His blow is 6 to 12 ({@code Tengu.java:101-104}). The seal refuses every transition
     * ({@code Level.java:657-662}), a hero out of his sight regains nothing the lock lets it keep
     * (regeneration stops fifty turns after the last blow dealt, {@code LockedFloor.java:36-58}, and each
     * point dealt to him gives a turn back, {@code Tengu.java:154-158}), and he is not the one who has
     * to come. So the fight Policy's dither between an approach and a retreat at two cells and three, which
     * cost the hero a shot at each pass in every Run before this story (docs/results/e10-practice-kit.md),
     * is replaced by a walk that does not turn back: strike when beside him, else step toward the nearest
     * cell beside him. The hit points' answer is the heal Policy's, which ranks above this one. From story 10.20
     * the walk is priced by the threat map ({@link Threats}) whenever the screen draws a bomb, the fire or a
     * shocker, and is this plain walk when it draws none.
     *
     * <p><b>The traps.</b> Past 25 hit points of damage he jumps and lays a patch of dart traps drawn for
     * two turns ({@link Darts}). The walk is over the cells {@link Explore#walkable} allows, which leaves out
     * the cells the Memory holds from that drawing, and a walk with no way round is made over them: a dart is
     * 17 damage in all, and a hero that waits for a clear way is shot at by Tengu every turn it waits. His 200 hit points and the
     * eight brackets are phase one down to 100 ({@code Tengu.java:139-203}); the bar's eleven pips cannot
     * tell 101 from 100, so the Policy plays the same in the second half, whose bombs, fire and shocker
     * are story 10.20's.
     */
    static List<RunLog.Choice> engage(Observation observation, Memory memory, List<Action> offered,
                                      List<org.shatterfish.api.ActorView> enemies, Playbook.Tengu costs) {
        if (!Darts.here(observation)) {
            return null;
        }
        org.shatterfish.api.ActorView boss = enemies.stream().filter(enemy -> enemy.name().equals(BOSS)).findFirst().orElse(null);
        if (boss == null || boss.emote() == org.shatterfish.api.Emote.SLEEP) {
            return null;
        }
        Threats.Map threats = Threats.map(observation, costs);
        if (threats.any()) {
            RunLog.Choice planned = Threats.choose(observation, memory, offered, boss, threats, costs);
            if (planned != null) {
                return List.of(planned);
            }
        }
        Action attack = new Action.Attack(boss.cell());
        if (offered.contains(attack)) {
            return List.of(new RunLog.Choice(attack, Policies.CERTAIN, "tengu: attack"));
        }
        MapSection map = observation.map();
        int hero = observation.hero().cell();
        Explore.Path path = Explore.nearest(map, Explore.walkable(observation, memory, false), hero, offered,
                cell -> adjacent(map, cell, boss.cell()));
        String why = "tengu: approach ";
        if (path == null) {
            // No way round the remembered traps: the walk is made over them.
            boolean[] walk = Explore.walkable(observation, memory, false);
            for (Memory.Spot spot : memory.darts()) {
                if (spot.on(observation.header().depth(), observation.header().branch()) && spot.cell() < walk.length
                        && spot.cell() != boss.cell()) {
                    walk[spot.cell()] = map.fog().get(spot.cell()) != org.shatterfish.api.Fog.UNKNOWN
                            && Explore.WALK.contains(map.tiles().get(spot.cell()));
                }
            }
            path = Explore.nearest(map, walk, hero, offered, cell -> adjacent(map, cell, boss.cell()));
            why = "tengu: across ";
        }
        if (path == null) {
            return null;
        }
        return List.of(new RunLog.Choice(path.step(), Policies.CERTAIN, why + path.distance()));
    }
}
