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
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each static {@code RunStatics} knows is put back to a fresh process's value, whatever a Run left in
 * it (issue #167). The end-to-end check, a Run played again after another Run in the same process, is
 * {@code InProcessRunsTest}; this one plants the leftovers directly, so each entry is held on its own
 * and a static that no seed happens to reach is still covered.
 */
class RunStaticsTest {

    @Test
    void every_leftover_is_put_back() throws Exception {
        List<Object> planted = new ArrayList<>();
        planted.add("a character knocked back by a rock of the last Run");

        set(Snake.class, "dodges", 1);
        set(GnollGeomancer.class, "rocksInFlight", 3);
        set(GnollGeomancer.class, "knockedChars", planted);
        set(Char.class, "hitMissIcon", 7);
        set(Flail.class, "spinBoost", 12);
        set(RingOfWealth.class, "latestDropTier", 4);
        Chasm.jumpConfirmed = true;
        TippedDart.lostDarts = 5;
        WondrousResin.forcePositive = true;
        Ratmogrify.useRatroicEnergy = true;
        Bestiary.skipCountingEncounters = true;

        RunStatics.reset();

        assertEquals(0, get(Snake.class, "dodges"));
        assertEquals(0, get(GnollGeomancer.class, "rocksInFlight"));
        Object knocked = get(GnollGeomancer.class, "knockedChars");
        assertTrue(knocked instanceof ArrayList<?> list && list.isEmpty(), "an empty list, as declared: " + knocked);
        assertNotSame(planted, knocked, "a fresh list, not the last Run's emptied");
        assertEquals(-1, get(Char.class, "hitMissIcon"));
        assertEquals(0, get(Flail.class, "spinBoost"));
        assertEquals(0, get(RingOfWealth.class, "latestDropTier"));
        assertFalse(Chasm.jumpConfirmed);
        assertEquals(0, TippedDart.lostDarts);
        assertFalse(WondrousResin.forcePositive);
        assertFalse(Ratmogrify.useRatroicEnergy);
        assertFalse(Bestiary.skipCountingEncounters);
    }

    @Test
    void a_capture_puts_back_what_it_found_after_a_reset() throws Exception {
        List<Object> planted = new ArrayList<>();
        planted.add("a character knocked back by a rock");
        set(Snake.class, "dodges", 1);
        set(GnollGeomancer.class, "rocksInFlight", 3);
        set(GnollGeomancer.class, "knockedChars", planted);
        set(Char.class, "hitMissIcon", 7);
        set(Flail.class, "spinBoost", 12);
        set(RingOfWealth.class, "latestDropTier", 4);
        Chasm.jumpConfirmed = true;
        TippedDart.lostDarts = 5;
        WondrousResin.forcePositive = true;
        Ratmogrify.useRatroicEnergy = true;
        Bestiary.skipCountingEncounters = true;

        RunStatics.Held held = RunStatics.capture();
        RunStatics.reset();
        assertEquals(0, get(Snake.class, "dodges"));
        planted.add("changed after the capture");
        held.restore();

        assertEquals(1, get(Snake.class, "dodges"));
        assertEquals(3, get(GnollGeomancer.class, "rocksInFlight"));
        Object knocked = get(GnollGeomancer.class, "knockedChars");
        assertEquals(List.of("a character knocked back by a rock"), knocked, "the list as it was, not as it became");
        assertNotSame(planted, knocked);
        assertEquals(7, get(Char.class, "hitMissIcon"));
        assertEquals(12, get(Flail.class, "spinBoost"));
        assertEquals(4, get(RingOfWealth.class, "latestDropTier"));
        assertTrue(Chasm.jumpConfirmed);
        assertEquals(5, TippedDart.lostDarts);
        assertTrue(WondrousResin.forcePositive);
        assertTrue(Ratmogrify.useRatroicEnergy);
        assertTrue(Bestiary.skipCountingEncounters);
        RunStatics.reset();
    }

    /**
     * The holiday is pinned to none at every Run start, whatever the date the process read (issue #139).
     * The game caches the holiday on first use from the wall clock ({@code core/.../utils/Holiday.java:56-61}),
     * and on a holiday the pasty is another food with other effects
     * ({@code core/.../items/food/Pasty.java:105-120}). A cache holding a holiday is what a process
     * started on that date has; planting one stands in for the date, and the Run must not see it.
     */
    @Test
    void the_holiday_is_none_whatever_the_date() throws Exception {
        for (Holiday onTheDay : Holiday.values()) {
            set(Holiday.class, "cached", onTheDay);
            RunStatics.reset();
            assertEquals(Holiday.NONE, Holiday.getCurrentHoliday(), "a Run begun on " + onTheDay);
        }
    }

    @Test
    void two_resets_hand_out_two_lists() throws Exception {
        RunStatics.reset();
        Object first = get(GnollGeomancer.class, "knockedChars");
        RunStatics.reset();
        assertNotSame(first, get(GnollGeomancer.class, "knockedChars"),
                "each Run gets its own list; a shared one would carry the last Run's entries");
    }

    private static void set(Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Object get(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
}
