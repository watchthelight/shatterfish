package org.shatterfish.harness.observer;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.watabou.utils.Reflection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.harness.Launcher;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The floor's identities (story 6.5): an unknown appearance counts toward the denominator of the disambiguation factor only
 * when an unidentified item of this floor shows it, in the pack or in a heap. Planted items, because the first floor's own
 * are not a fixed few.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class OracleFloorIdentitiesTest {

    private HeadlessDriver driver;

    @AfterEach
    void endTheRun() {
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    private static boolean shows(OracleView view, Item item, String family) {
        return view.floorIdentities().stream().anyMatch(i -> i.family().equals(family) && i.appearance().equals(item.name()));
    }

    @Test
    @DisplayName("an unknown potion dropped on the floor and an unknown scroll in the pack join the floor's identities, and identifying one takes it out")
    void the_floor_identities_follow_the_items() {
        driver = HeadlessDriver.start(14_142_135L, HeroClass.WARRIOR, 0x5A17_5A17L);
        driver.stepToInputWait();
        OracleObserver oracle = new OracleObserver();
        OracleView before = oracle.view();

        // An unknown class that no item of the first floor shows yet, of each family.
        Set<String> shownNow = new java.util.HashSet<>();
        before.floorIdentities().forEach(i -> shownNow.add(i.family() + " " + i.appearance()));
        Potion potion = null;
        List<Class<? extends Potion>> potions = new ArrayList<>(Potion.getUnknown());
        potions.sort(Comparator.comparing(Class::getName));
        for (Class<? extends Potion> type : potions) {
            Potion candidate = Reflection.newInstance(type);
            if (!shownNow.contains("potion " + candidate.name())) {
                potion = candidate;
                break;
            }
        }
        Scroll scroll = null;
        List<Class<? extends Scroll>> scrolls = new ArrayList<>(Scroll.getUnknown());
        scrolls.sort(Comparator.comparing(Class::getName));
        for (Class<? extends Scroll> type : scrolls) {
            Scroll candidate = Reflection.newInstance(type);
            if (!shownNow.contains("scroll " + candidate.name())) {
                scroll = candidate;
                break;
            }
        }
        assertTrue(potion != null && scroll != null, "the first floor does not show every appearance");
        String appearance = potion.name();
        assertFalse(shows(before, potion, "potion"));
        assertFalse(shows(before, scroll, "scroll"));
        assertTrue(before.identities().stream().anyMatch(i -> i.family().equals("potion") && i.appearance().equals(appearance)),
                "the Run's list holds it all along: only the floor's list changes");

        Dungeon.level.drop(potion, Dungeon.hero.pos);
        assertTrue(shows(oracle.view(), potion, "potion"), "an unidentified potion in a heap of the floor");
        assertFalse(shows(oracle.view(), scroll, "scroll"));

        assertTrue(scroll.collect(), "the scroll fits in the pack");
        OracleView held = oracle.view();
        assertTrue(shows(held, scroll, "scroll"), "an unidentified scroll in the pack");

        // The launcher's keys: the planted ones are the floor's, and an appearance nothing shows is the Run's elsewhere.
        Set<String> facts = Launcher.facts(held);
        assertTrue(facts.contains("identity potion " + potion.name()), facts.toString());
        assertTrue(facts.contains("identity scroll " + scroll.name()), facts.toString());
        assertTrue(facts.stream().anyMatch(f -> f.startsWith("elsewhere ")), "an appearance nothing on the floor shows");

        potion.identify();
        OracleView after = oracle.view();
        assertFalse(after.identities().stream().anyMatch(i -> i.family().equals("potion") && i.appearance().equals(appearance)),
                "an identified class is no longer a hidden fact at all");
        assertFalse(Launcher.facts(after).contains("identity potion " + appearance));
        assertTrue(shows(after, scroll, "scroll"), "the scroll is still hidden");
    }
}
