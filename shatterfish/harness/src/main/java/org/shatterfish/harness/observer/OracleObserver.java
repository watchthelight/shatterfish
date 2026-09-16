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
 * <p>The gate is who may construct one. The launcher's {@code --oracle} branch is the only caller,
 * which {@code OracleGateTest} holds by ArchUnit over the harness, so a measured Run
 * ({@code RunLoop}) has no path to this class at all; the Rig's refusal of an oracle Run, on top
 * of that, is story 3.3's (ADR-0012). Everything read here is a public field or method of the
 * game, so the view needs no reflection into upstream and no hook.
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
    static OracleView view(Level level) {
        List<OracleView.Identity> identities = new ArrayList<>();
        identities(identities, "potion", Potion.getUnknown());
        identities(identities, "scroll", Scroll.getUnknown());
        identities(identities, "ring", Ring.getUnknown());
        identities.sort(Comparator.comparing(OracleView.Identity::family).thenComparing(OracleView.Identity::appearance));

        List<OracleView.Presence> mobs = new ArrayList<>();
        List<Integer> hiddenMimics = new ArrayList<>();
        for (Mob mob : level.mobs) {
            mobs.add(new OracleView.Presence(mob.pos, mob.name(), mob.HP, mob.HT, state(mob), level.heroFOV[mob.pos]));
            if (Observer.hiddenMimic(mob)) {
                hiddenMimics.add(mob.pos);
            }
        }
        mobs.sort(Comparator.comparingInt(OracleView.Presence::cell).thenComparing(OracleView.Presence::name));
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
                hiddenTraps.add(new OracleView.Secret(trap.pos, trap.name()));
            }
        }
        hiddenTraps.sort(Comparator.comparingInt(OracleView.Secret::cell));
        return new OracleView(Dungeon.seed, identities, mobs, hiddenMimics, secretDoors, hiddenTraps);
    }

    /** Every unknown class of a family, by the appearance a fresh instance draws under. */
    private static void identities(List<OracleView.Identity> into, String family, Set<? extends Class<? extends Item>> unknown) {
        for (Class<? extends Item> type : unknown) {
            Item item = Reflection.newInstance(type);
            if (item != null) {
                into.add(new OracleView.Identity(family, item.name(), item.trueName()));
            }
        }
    }

    /** The mob's AI state by the name the game gives its field, or the state's own class. */
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
