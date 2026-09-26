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
