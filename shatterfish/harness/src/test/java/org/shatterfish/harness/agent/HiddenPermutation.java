package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.ItemStatusHandler;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.watabou.utils.Reflection;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hidden-state permutation of {@code PermutedIdentitiesDifferentialTest} (story 4.14), shared with the
 * Overlay's coach-sentence leak test (story 8.8): in the running game, two unidentified classes of each family
 * (potion, scroll, ring) trade the labels they draw under, and every item of those classes lying on the floor
 * trades classes with its twin, so what is on screen is unchanged and what the game knows is not. It is made by the
 * test in the game and never handed to the Brain.
 */
public final class HiddenPermutation {

    private HiddenPermutation() {
    }

    public static void permute() {
        try {
            for (Pair pair : List.of(rarestTwo(Generator.Category.POTION, Potion.getKnown()),
                    rarestTwo(Generator.Category.SCROLL, Scroll.getKnown()),
                    rarestTwo(Generator.Category.RING, Ring.getKnown()))) {
                swapLabels(pair);
                exchangeOnTheFloor(pair);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private record Pair(Class<?> family, Class<? extends Item> x, Class<? extends Item> y) {
    }

    /** The two unknown classes of the family the generator deals least often, ties by name. */
    @SuppressWarnings("unchecked")
    private static Pair rarestTwo(Generator.Category category, Set<?> known) {
        float[] odds = category.defaultProbs != null ? category.defaultProbs : category.probs;
        List<Class<?>> all = List.of(category.classes);
        List<Class<? extends Item>> unknown = new ArrayList<>();
        for (Class<?> type : all) {
            if (!known.contains(type)) {
                unknown.add((Class<? extends Item>) type);
            }
        }
        // Classes the generator can deal at all (the guaranteed strength and upgrade items have no odds and are
        // placed by schedule), and those lying on this floor first, so that the exchange below is seen at once.
        unknown.removeIf(type -> odds[all.indexOf(type)] <= 0f);
        Set<Class<?>> lying = new java.util.HashSet<>();
        for (Heap heap : Dungeon.level.heaps.valueList()) {
            for (Item item : heap.items) {
                lying.add(item.getClass());
            }
        }
        unknown.sort(Comparator.<Class<? extends Item>>comparingInt(type -> lying.contains(type) ? 0 : 1)
                .thenComparingDouble(type -> odds[all.indexOf(type)])
                .thenComparing(Class::getSimpleName));
        assertTrue(unknown.size() >= 2, "two unknown classes of " + category);
        Class<?> family = category == Generator.Category.POTION ? Potion.class
                : category == Generator.Category.SCROLL ? Scroll.class : Ring.class;
        return new Pair(family, unknown.get(0), unknown.get(1));
    }

    /** As {@code HiddenStateDifferentialTest}: the game draws labels once per Run and has no method for this. */
    private static void swapLabels(Pair pair) throws ReflectiveOperationException {
        Field handlerField = pair.family.getDeclaredField("handler");
        handlerField.setAccessible(true);
        ItemStatusHandler<?> handler = (ItemStatusHandler<?>) handlerField.get(null);
        Field labelsField = ItemStatusHandler.class.getDeclaredField("itemLabels");
        labelsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        LinkedHashMap<Class<?>, String> labels = (LinkedHashMap<Class<?>, String>) labelsField.get(handler);
        String ofX = labels.get(pair.x);
        String ofY = labels.get(pair.y);
        assertTrue(ofX != null && ofY != null && !ofX.equals(ofY), pair + " draw under different labels");
        labels.put(pair.x, ofY);
        labels.put(pair.y, ofX);
    }

    /** Floor items whose class was exchanged, over all Runs of this test. */
    private static int exchanged;

    /** Floor items exchanged so far, over all calls of {@link #permute()}. */
    public static int exchanged() {
        return exchanged;
    }

    private static void exchangeOnTheFloor(Pair pair) {
        for (Heap heap : Dungeon.level.heaps.valueList()) {
            for (int i = 0; i < heap.items.size(); i++) {
                Item item = heap.items.get(i);
                Class<? extends Item> to = item.getClass() == pair.x ? pair.y : item.getClass() == pair.y ? pair.x : null;
                if (to != null) {
                    Item other = Reflection.newInstance(to);
                    other.quantity(item.quantity());
                    heap.items.set(i, other);
                    exchanged++;
                }
            }
        }
    }
}
