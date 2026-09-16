package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.watabou.utils.Reflection;
import org.shatterfish.api.HeaderSection;
import org.shatterfish.api.Observation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The oracle: the fair Observer's read, marked, with a sidecar beside it (FR-11; ADR-0005,
 * ADR-0006). The Observation it returns is the ordinary one with the header's oracle bit set, so an
 * oracle Run's hashes differ from a fair Run's and nothing else does; the {@link OracleView} is a
 * separate value that carries what the screen hides. The fair {@link Observer} is final and
 * unchanged: this class wraps it and never widens it.
 *
 * <p>The gate is who may construct one. The launcher's {@code --oracle} branch is the only caller
 * inside the harness, which {@code OracleGateTest} holds by ArchUnit over the harness's main
 * classes, so a measured Run ({@code RunLoop}) has no path to this class at all; a module built on
 * the harness carries its own rule when it arrives, and the Rig's refusal of an oracle Run, on top
 * of that, is story 3.3's (ADR-0012).
 *
 * <p>Everything read here is a public field or method of the game, at {@code v4.0.0} with
 * {@code …/} for {@code core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/}: the unknown
 * classes of each family ({@code …/items/potions/Potion.java:407-409};
 * {@code …/items/scrolls/Scroll.java:269-271}; {@code …/items/rings/Ring.java:284-286}), a fresh
 * instance's appearance and true name ({@code Potion.java:184-187}, {@code :199-208}, {@code :378-380};
 * {@code …/items/Item.java:499-505}), the floor's mobs ({@code …/levels/Level.java:183}) with their
 * cell and health ({@code …/actors/Char.java:168-173}) and state ({@code …/actors/mobs/Mob.java:118-124}),
 * the terrain ({@code Level.java:153}; {@code …/levels/Terrain.java:47}), the traps
 * ({@code Level.java:187}; {@code …/levels/traps/Trap.java:63-64}) and the seed
 * ({@code …/Dungeon.java:213}). The instances are made through the game's own factory, as the
 * generator makes them ({@code …/items/Generator.java:740}; {@code SPD-classes/…/utils/Reflection.java:38-45}),
 * so there is no reflection into a private upstream member and no hook row. Building the view
 * writes nothing and draws nothing: a Run observed by the oracle is the Run observed fairly.
 */
public final class OracleObserver {

    /** The marked Observation and the sidecar, read together at one wait. */
    public record Read(Observation observation, OracleView view) {
    }

    private final Observer fair = new Observer();

    public OracleObserver() {
    }

    /** The fair read at this wait, its header marked, and the view beside it. */
    public Read observe() {
        Observation read = fair.observe();
        HeaderSection h = read.header();
        HeaderSection marked = new HeaderSection(h.version(), h.upstreamTag(), h.codexVersion(), h.heroClass(),
                h.challenges(), h.depth(), h.branch(), h.sealed(), true, h.prompt());
        Observation observation = new Observation(marked, read.map(), read.actors(), read.hero(), read.inventory(),
                read.journal(), read.log(), read.actions(), read.prompt());
        return new Read(observation, view(Dungeon.level));
    }

    /** What the screen hides on {@code level}, from public game state only. */
    private static OracleView view(Level level) {
        List<OracleView.Identity> identities = new ArrayList<>();
        identities(identities, "potion", Potion.getUnknown());
        identities(identities, "scroll", Scroll.getUnknown());
        identities(identities, "ring", Ring.getUnknown());
        identities.sort(Comparator.comparing(OracleView.Identity::family).thenComparing(OracleView.Identity::appearance)
                .thenComparing(OracleView.Identity::type));

        List<OracleView.Presence> mobs = new ArrayList<>();
        List<Integer> hiddenMimics = new ArrayList<>();
        for (Mob mob : level.mobs) {
            boolean hidden = Observer.hiddenMimic(mob);
            // Drawn as an actor exactly when in view and not a hidden mimic (Observer.actors()).
            boolean seen = level.heroFOV[mob.pos] && !hidden;
            mobs.add(new OracleView.Presence(mob.pos, mob.getClass().getSimpleName(), mob.name(), mob.HP, mob.HT,
                    state(mob), seen));
            if (hidden) {
                hiddenMimics.add(mob.pos);
            }
        }
        mobs.sort(Comparator.comparingInt(OracleView.Presence::cell).thenComparing(OracleView.Presence::type)
                .thenComparing(OracleView.Presence::name).thenComparingInt(OracleView.Presence::hp)
                .thenComparing(OracleView.Presence::state));
        hiddenMimics.sort(Integer::compare);

        List<Integer> secretDoors = new ArrayList<>();
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.map[cell] == Terrain.SECRET_DOOR) {
                secretDoors.add(cell);
            }
        }
        List<OracleView.Secret> hiddenTraps = new ArrayList<>();
        for (Trap trap : level.traps.valueList()) {
            if (!trap.visible) {
                hiddenTraps.add(new OracleView.Secret(trap.pos, trap.getClass().getSimpleName(), trap.name(), trap.active));
            }
        }
        hiddenTraps.sort(Comparator.comparingInt(OracleView.Secret::cell).thenComparing(OracleView.Secret::type));
        return new OracleView(Dungeon.seed, identities, mobs, hiddenMimics, secretDoors, hiddenTraps);
    }

    /**
     * Every unknown class of a family, by the appearance a fresh instance draws under. A class the
     * factory cannot make is an error, never a silent omission: a view that under-reports is worse
     * than none.
     */
    private static void identities(List<OracleView.Identity> into, String family, Set<? extends Class<? extends Item>> unknown) {
        for (Class<? extends Item> type : unknown) {
            Item item = Reflection.newInstance(type);
            if (item == null) {
                throw new IllegalStateException("the oracle could not make a " + type.getName() + " to read its appearance");
            }
            into.add(new OracleView.Identity(family, item.name(), item.trueName(), type.getSimpleName()));
        }
    }

    /** The mob's AI state by the name the game gives its field (Mob.java:118-124), or the state's own class. */
    private static String state(Mob mob) {
        if (mob.state == mob.SLEEPING) {
            return "SLEEPING";
        } else if (mob.state == mob.HUNTING) {
            return "HUNTING";
        } else if (mob.state == mob.INVESTIGATING) {
            return "INVESTIGATING";
        } else if (mob.state == mob.WANDERING) {
            return "WANDERING";
        } else if (mob.state == mob.FLEEING) {
            return "FLEEING";
        } else if (mob.state == mob.PASSIVE) {
            return "PASSIVE";
        }
        return mob.state == null ? "NONE" : mob.state.getClass().getSimpleName();
    }
}
