package org.shatterfish.brain;

import org.shatterfish.api.ActorView;
import org.shatterfish.api.Observation;
import org.shatterfish.api.TrapView;

import java.util.ArrayList;
import java.util.List;

/**
 * Tengu's dart traps, remembered from the moment the game draws them (story 10.19, issue #365).
 *
 * <p><b>What the game shows.</b> Each time Tengu's hit points cross a bracket of an eighth of his maximum
 * he jumps within his cell and lays a new patch of {@code TenguDartTrap}, 40 to 90 percent of the 7 by 7
 * floor, hidden ({@code PrisonBossLevel.java:669-728}; {@code Tengu.java:240-279}). The patch is built so a
 * walk from him to the hero over cells with no trap exists ({@code PrisonBossLevel.java:697-701}). The
 * traps are drawn, in their colour at alpha 0.4, for two turns and then not again
 * ({@code PrisonBossLevel.java:724-727}, {@code :849}, {@code :850-874}); the trap's own description
 * says "the trap is visible for a moment when it is being set"
 * ({@code levels.properties:158}). A trap cannot be searched for
 * ({@code canBeSearched = false}, {@code TenguDartTrap.java:31-34}), so after the two turns the screen holds nothing a player could use to find
 * the cells but what he remembered. A trap shoots a poison dart of 8 poison, 17 damage in all, at the
 * nearest thing that is not Tengu ({@code TenguDartTrap.java:36-48}; {@code PoisonDartTrap.java:63-110}).
 *
 * <p><b>What the Brain reads.</b> The Observation's traps, as the Observer carries them: a trap drawn as
 * the patch is laid is in {@code map().traps()} for those two turns, named "poison dart trap", and is not
 * there afterwards. The memory is the one place a hidden thing is remembered from a brief view, so it is
 * kept to the least that is useful and the most that is fair:
 *
 * <ul>
 *   <li>it is written only from a wait whose Observation draws the traps, so a trap the hero never saw
 *       drawn is in no memory (a hero who looks away as the patch is laid has none);</li>
 *   <li>a new drawing replaces the old (the jump clears the old patch before it lays the new,
 *       {@code PrisonBossLevel.java:655-667}), so the memory is never the union of two patches;</li>
 *   <li>it is kept only on the sealed prison boss floor and only while Tengu is in view: the wait after he
 *       is gone (the pause between the phases removes him, {@code PrisonBossLevel.java:468-486}) or the
 *       floor is left, it is dropped, since the arena is a different room;</li>
 *   <li>it holds cells and never reads the game: nothing here is called with the level.</li>
 * </ul>
 *
 * <p><b>What the Brain does with it.</b> {@link #block} takes the cells out of the walkable set, so the
 * walk to Tengu goes round them. A walk with no way round is the fight Policy's to make across a trap;
 * {@link Tengu#engage} falls back to it, since standing still is no answer (the lock stops regeneration,
 * {@code LockedFloor.java:42-58}).
 */
final class Darts {

    /** The traps' name on the screen: {@code TenguDartTrap} has no name of its own and takes its parent's (levels.properties:138). */
    static final String KIND = "poison dart trap";

    private Darts() {
    }

    /** Whether {@code observation} is the sealed prison boss floor, the only floor these traps are laid on. */
    static boolean here(Observation observation) {
        return observation.header().depth() == Tengu.DEPTH && observation.header().branch() == 0
                && observation.header().sealed();
    }

    /** Whether Tengu is in view. */
    static boolean seen(Observation observation) {
        return tengu(observation) != null;
    }

    /** Tengu as the screen draws him, or null. */
    static ActorView tengu(Observation observation) {
        for (ActorView actor : observation.actors().actors()) {
            if (actor.name().equals(Tengu.BOSS) && actor.alignment() == org.shatterfish.api.Alignment.ENEMY) {
                return actor;
            }
        }
        return null;
    }

    /** The cells of the dart traps this screen draws on the boss floor, in cell order; empty off it. */
    static List<Integer> drawn(Observation observation) {
        List<Integer> cells = new ArrayList<>();
        if (!here(observation)) {
            return cells;
        }
        for (TrapView trap : observation.map().traps()) {
            if (trap.kind().equals(KIND)) {
                cells.add(trap.cell());
            }
        }
        return cells;
    }

    /** Whether {@code trap} is one of the patch the boss lays, on a screen of the boss floor. */
    static boolean laid(Observation observation, TrapView trap) {
        return here(observation) && trap.kind().equals(KIND);
    }

    /**
     * The cells remembered after seeing {@code observation}: those it draws, else the ones remembered
     * while Tengu is still in view on the sealed floor, else none.
     */
    static List<Memory.Spot> remember(Memory memory, Observation observation) {
        if (!here(observation)) {
            return List.of();
        }
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        List<Integer> cells = drawn(observation);
        if (!cells.isEmpty()) {
            List<Memory.Spot> spots = new ArrayList<>();
            for (int cell : cells) {
                spots.add(new Memory.Spot(depth, branch, cell));
            }
            return spots;
        }
        return seen(observation) ? memory.darts() : List.of();
    }

    /** Takes the remembered cells out of {@code walk}. */
    static void block(boolean[] walk, Observation observation, Memory memory) {
        if (memory.darts().isEmpty() || !here(observation)) {
            return;
        }
        int depth = observation.header().depth();
        int branch = observation.header().branch();
        for (Memory.Spot spot : memory.darts()) {
            if (spot.on(depth, branch) && spot.cell() < walk.length) {
                walk[spot.cell()] = false;
            }
        }
    }
}
