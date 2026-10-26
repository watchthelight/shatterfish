package org.shatterfish.harness.driver;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.Ratmogrify;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollGeomancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Snake;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfWealth;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.WondrousResin;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Flail;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.TippedDart;
import com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.Chasm;
import com.shatteredpixel.shatteredpixeldungeon.utils.Holiday;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts back every upstream static that one Run can leave behind for the next, at the start of every
 * Run, so that a Run begun in a process that has already played one is the Run a fresh process would
 * play (issue #167).
 *
 * <p>The game keeps some of its state in statics and resets most of it itself when a game begins:
 * {@code Dungeon.init()} clears the actors, the item generator, the rooms of the run, the quests,
 * the statistics and the notes ({@code core/.../Dungeon.java:233-287}), and the Profile empties what
 * a player's history holds (the journal, the badges, the rankings and the bones, story 1.15 and 5.1).
 * What is left is here: statics the game writes during play and reads later, which nothing resets,
 * so a value left by a Run that ended in the middle of the thing that writes them is read by the next
 * Run. The sweep that found them, and how each of the 358 other gameplay statics is classified, is in
 * {@code docs/architecture.md} ("Statics that outlive a Run"); a Rig Run and an Overlay Run each have a
 * process of their own, so this changes no Run either has recorded, and matters to everything that
 * plays two Runs in one process, which the tests do.
 *
 * <p>Each is put back to the value a fresh process starts with, its declaration's initializer. The
 * public ones are assigned. The private ones are reached by reflection, which harness main code may do
 * only in named classes for named fields: {@code docs/UPSTREAM.md} names each one, and
 * {@code HarnessReflectionTest} holds the set reached here to that list and to one lookup, one opening
 * and one write.
 */
public final class RunStatics {

    /** A private upstream static and the value a fresh process has in it. */
    private record Reset(Field field, Object fresh) {
    }

    private static final List<Reset> PRIVATE = List.of(
            // The dodges the hero has watched a snake make; the guidebook's hint shows at two of them
            // (core/.../actors/mobs/Snake.java:58-70), and it is a log line.
            new Reset(privateStatic(Snake.class, "dodges"), 0),
            // A volley of the geomancer's rocks in flight when the Run ended: the counter stays up, the
            // list of knocked-back characters is never cleared, and the next Run's volleys knock a
            // character back once per Run rather than once per volley
            // (core/.../actors/mobs/GnollGeomancer.java:697-698, :744-761).
            new Reset(privateStatic(GnollGeomancer.class, "rocksInFlight"), 0),
            new Reset(privateStatic(GnollGeomancer.class, "knockedChars"), null),
            // The hit or miss icon a hit set and the attack's callback has not shown yet; shown late, a
            // miss's tuft draws from the game's generator (core/.../actors/Char.java:586-594, :687).
            new Reset(privateStatic(Char.class, "hitMissIcon"), -1),
            // The flail's spin charge, added to its next hit (core/.../items/weapon/melee/Flail.java:57-89).
            new Reset(privateStatic(Flail.class, "spinBoost"), 0),
            // The flare the ring of wealth shows for the next bonus drop
            // (core/.../items/rings/RingOfWealth.java:170-190).
            new Reset(privateStatic(RingOfWealth.class, "latestDropTier"), 0),
            // The holiday the game cached from the wall clock on first use
            // (core/.../utils/Holiday.java:52-61). Unlike the rest this is not a fresh process's value,
            // which is the date's: a Run is determined by (tag, seed, actions) and not by the day it
            // was played, so it is pinned to none (issue #139). The pasty alone is another food on
            // some days (core/.../items/food/Pasty.java:105-120).
            new Reset(privateStatic(Holiday.class, "cached"), Holiday.NONE));

    private RunStatics() {
    }

    /** Puts every static this class knows back to a fresh process's value. Call on the UI-role thread, before the Run begins. */
    public static void reset() {
        // Public statics.
        // A confirmed jump the Run ended before (core/.../levels/features/Chasm.java:54, :88-101).
        Chasm.jumpConfirmed = false;
        // Darts lost to a merge, dropped on the next pick-up or landing
        // (core/.../items/weapon/missiles/darts/TippedDart.java:150-161, core/.../items/Heap.java:182-185).
        TippedDart.lostDarts = 0;
        // Set around a wand or ability's cursed zap and cleared in its callback: a Run that ended in
        // between leaves every cursed effect of the next Run positive
        // (core/.../items/trinkets/WondrousResin.java:53-56, core/.../items/wands/Wand.java:771-777).
        WondrousResin.forcePositive = false;
        // Set by the talent and armour-ability windows and read by the heroic-energy talent's name and
        // icon (core/.../actors/hero/Talent.java:447-450, :477; core/.../ui/TalentsPane.java:60).
        Ratmogrify.useRatroicEnergy = false;
        // Set and cleared around a boss's summons (e.g. core/.../actors/mobs/DwarfKing.java:507-511).
        Bestiary.skipCountingEncounters = false;

        for (Reset reset : PRIVATE) {
            put(reset.field(), reset.fresh() == null ? new ArrayList<>() : reset.fresh());
        }
    }

    /**
     * The values these statics hold now, kept so that a rollout (story 6.2) can leave them as it found them. A rollout
     * runs in the live Run's process and plays on statics no snapshot saves ({@code Snake.dodges} is a count a log line
     * waits on), so the host takes this before it starts and puts it back after, and the live Run goes on as the twin
     * that was never rolled out from does.
     *
     * <p>The one place in this class that reads a field, and the values go into the holder and nowhere else: the holder
     * has no accessor, and {@code HarnessReflectionTest} holds it to that. What it carries cannot be carried out.
     */
    public static Held capture() {
        return new Held();
    }

    /** An opaque copy of what {@link #capture} found, with one use: {@link #restore}. */
    public static final class Held {
        private final boolean chasmJump = Chasm.jumpConfirmed;
        private final int lostDarts = TippedDart.lostDarts;
        private final boolean forcePositive = WondrousResin.forcePositive;
        private final boolean ratroic = Ratmogrify.useRatroicEnergy;
        private final boolean skipCounting = Bestiary.skipCountingEncounters;
        private final Object[] privates = new Object[PRIVATE.size()];

        private Held() {
            for (int i = 0; i < privates.length; i++) {
                Object value = read(PRIVATE.get(i).field());
                // A list is copied, so that a rollout's changes to it are not the ones put back.
                privates[i] = value instanceof List<?> list ? new ArrayList<>(list) : value;
            }
        }

        /** Puts every static back to the value it held when this was taken. */
        public void restore() {
            Chasm.jumpConfirmed = chasmJump;
            TippedDart.lostDarts = lostDarts;
            WondrousResin.forcePositive = forcePositive;
            Ratmogrify.useRatroicEnergy = ratroic;
            Bestiary.skipCountingEncounters = skipCounting;
            for (int i = 0; i < privates.length; i++) {
                Object value = privates[i];
                put(PRIVATE.get(i).field(), value instanceof List<?> list ? new ArrayList<>(list) : value);
            }
        }
    }

    private static Object read(Field field) {
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void put(Field field, Object value) {
        try {
            field.set(null, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field privateStatic(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(owner.getSimpleName() + "." + name + " is not where the pinned"
                    + " upstream had it; RunStatics puts it back at every Run start (see docs/UPSTREAM.md)", e);
        }
    }
}
