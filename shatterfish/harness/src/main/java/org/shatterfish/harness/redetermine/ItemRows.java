package org.shatterfish.harness.redetermine;

import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.ExoticPotion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ExoticScroll;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.watabou.utils.Bundle;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.shatterfish.harness.rng.Mix;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The rows about items: the classes of the unidentified potions, scrolls and rings, the level and curse of an
 * unidentified weapon, armor, ring or wand, and what lies in a container or under the top of a heap. Every fresh
 * value comes from the game's own code ({@code Item.random()}, {@code Generator.random()}) run under a generator the
 * sample seeds, so no rule of the game is written a second time here, and the statics those calls touch are put back
 * by {@link GameStatics}.
 */
final class ItemRows {

    private ItemRows() {
    }

    private static final Map<String, Class<?>> CLASSES = new HashMap<>();

    static Class<?> classOf(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return CLASSES.computeIfAbsent(name, n -> {
            try {
                return Class.forName(n, false, ItemRows.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                return null;
            }
        });
    }

    static boolean isA(String className, Class<?> base) {
        Class<?> cls = classOf(className);
        return cls != null && base.isAssignableFrom(cls);
    }

    // --- POTION_SCROLL_RING_CLASSES ----------------------------------------------------------------------------------

    /**
     * Permutes the unidentified classes of each family among themselves and renames every class-valued string of both
     * bundles accordingly, so that an item still shows the label it showed and is another thing under it. The
     * identified classes are the player's and stay. The exotic of a permuted regular class follows it, as the game
     * pairs them ({@code ExoticPotion.regToExo}, {@code ExoticScroll.regToExo}) and as an exotic shows its regular
     * class's label.
     */
    static void classes(Ctx c) {
        Map<String, String> rename = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        int held = 0;
        int moved = 0;
        for (Family family : families()) {
            List<Class<?>> unknown = new ArrayList<>();
            for (Class<?> cls : family.classes) {
                String key = cls.getSimpleName();
                if (c.game.has(key + "_known") && !c.game.optBoolean(key + "_known") && c.game.has(key + "_label")) {
                    unknown.add(cls);
                }
            }
            unknown.sort(Comparator.comparing(Class::getSimpleName));
            held += unknown.size();
            if (unknown.size() < 2) {
                continue;
            }
            int[] perm = permutation(unknown.size(), c.streams, family.name);
            for (int i = 0; i < perm.length; i++) {
                Class<?> from = unknown.get(i);
                Class<?> to = unknown.get(perm[i]);
                if (from == to) {
                    continue;
                }
                moved++;
                rename.put(from.getName(), to.getName());
                labels.put(to.getSimpleName() + "_label", c.game.getString(from.getSimpleName() + "_label"));
                Class<?> exoFrom = family.exotic.get(from);
                Class<?> exoTo = family.exotic.get(to);
                if (exoFrom != null && exoTo != null) {
                    rename.put(exoFrom.getName(), exoTo.getName());
                }
            }
        }
        c.held(ScrubRow.POTION_SCROLL_RING_CLASSES, held);
        if (rename.isEmpty()) {
            return;
        }
        labels.forEach(c.game::put);
        remap(c.game, rename);
        remap(c.level, rename);
        c.changed(ScrubRow.POTION_SCROLL_RING_CLASSES, moved);
    }

    private record Family(String name, Class<?>[] classes, Map<Class<?>, Class<?>> exotic) {
    }

    private static List<Family> families() {
        List<Family> families = new ArrayList<>();
        Map<Class<?>, Class<?>> potions = new HashMap<>(ExoticPotion.regToExo);
        Map<Class<?>, Class<?>> scrolls = new HashMap<>(ExoticScroll.regToExo);
        families.add(new Family("potion", Generator.Category.POTION.classes, potions));
        families.add(new Family("scroll", Generator.Category.SCROLL.classes, scrolls));
        families.add(new Family("ring", Generator.Category.RING.classes, Map.of()));
        return families;
    }

    /** A permutation of {@code 0..n-1} that is not the identity, drawn from the family's own stream. */
    private static int[] permutation(int n, Streams streams, String family) {
        for (int attempt = 0; ; attempt++) {
            SplittableRandom random = streams.of("class-permutation", family + "#" + attempt);
            int[] perm = new int[n];
            for (int i = 0; i < n; i++) {
                perm[i] = i;
            }
            for (int i = n - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int t = perm[i];
                perm[i] = perm[j];
                perm[j] = t;
            }
            for (int i = 0; i < n; i++) {
                if (perm[i] != i) {
                    return perm;
                }
            }
        }
    }

    /** Renames every string in the tree that is a renamed class's name, in values and in arrays. */
    private static void remap(Object node, Map<String, String> rename) {
        if (node instanceof JSONObject object) {
            for (String key : new ArrayList<>(object.keySet())) {
                Object value = object.get(key);
                if (value instanceof String text) {
                    String to = rename.get(text);
                    if (to != null) {
                        object.put(key, to);
                    }
                } else {
                    remap(value, rename);
                }
            }
        } else if (node instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                Object value = array.get(i);
                if (value instanceof String text) {
                    String to = rename.get(text);
                    if (to != null) {
                        array.put(i, to);
                    }
                } else {
                    remap(value, rename);
                }
            }
        }
    }

    // --- ITEM_LEVEL_AND_CURSE ------------------------------------------------------------------------------------------

    /**
     * Re-rolls what an unidentified item hides: its level when {@code levelKnown} is false and its curse (the flag and
     * the curse enchantment or glyph) when {@code cursedKnown} is false. The new values are those of a fresh item of
     * the same class made by the game's own {@code random()} under the sample's stream, kept only if the item would
     * still look the same: a non-curse enchantment the name shows stays and the item is not cursed, an item that shows
     * no enchantment gets none that the name would show, a level the screen shows is the level it had, a curse the
     * screen shows is the curse it had.
     */
    static void levels(Ctx c) {
        int[] counts = new int[2];
        visit(c.game.optJSONObject("hero"), "hero", c, counts);
        for (JSONObject heap : Ctx.objects(c.level.optJSONArray("heaps"))) {
            visit(heap.optJSONArray("items"), "heap@" + heap.optInt("pos"), c, counts);
        }
        c.held(ScrubRow.ITEM_LEVEL_AND_CURSE, counts[0]);
        c.changed(ScrubRow.ITEM_LEVEL_AND_CURSE, counts[1]);
    }

    private static void visit(Object node, String path, Ctx c, int[] counts) {
        if (node instanceof JSONObject object) {
            if (isRerollable(object)) {
                counts[0]++;
                if (reroll(object, path, c)) {
                    counts[1]++;
                }
            }
            List<String> keys = new ArrayList<>(object.keySet());
            keys.sort(String::compareTo);
            for (String key : keys) {
                Object value = object.get(key);
                if (value instanceof JSONObject || value instanceof JSONArray) {
                    visit(value, path + "/" + key, c, counts);
                }
            }
        } else if (node instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) {
                Object value = array.get(i);
                if (value instanceof JSONObject || value instanceof JSONArray) {
                    visit(value, path + "/" + i, c, counts);
                }
            }
        }
    }

    private static boolean isRerollable(JSONObject item) {
        String name = Ctx.className(item);
        if (!isA(name, Item.class)) {
            return false;
        }
        boolean family = isA(name, Weapon.class) || isA(name, Armor.class) || isA(name, Ring.class) || isA(name, Wand.class);
        return family && (!item.optBoolean("levelKnown") || !item.optBoolean("cursedKnown"));
    }

    private static boolean reroll(JSONObject item, String path, Ctx c) {
        String name = Ctx.className(item);
        Class<?> cls = classOf(name);
        boolean levelKnown = item.optBoolean("levelKnown");
        boolean cursedKnown = item.optBoolean("cursedKnown");
        String enchantKey = isA(name, Armor.class) ? "glyph" : isA(name, Weapon.class) ? "enchantment" : null;
        JSONObject enchant = enchantKey == null ? null : item.optJSONObject(enchantKey);
        boolean enchantShown = enchant != null && !isCurse(enchant);
        boolean cursed = item.optBoolean("cursed");
        int level = item.optInt("level");
        long base = c.streams.seed("item", path);
        for (int attempt = 0; attempt < 400; attempt++) {
            JSONObject fresh = fresh(cls, Mix.mix(base, attempt));
            if (fresh == null) {
                return false;
            }
            JSONObject freshEnchant = enchantKey == null ? null : fresh.optJSONObject(enchantKey);
            boolean freshShown = freshEnchant != null && !isCurse(freshEnchant);
            boolean freshCursed = fresh.optBoolean("cursed");
            if (levelKnown && fresh.optInt("level") != level) {
                continue;
            }
            if (cursedKnown) {
                if (freshCursed != cursed) {
                    continue;
                }
            } else if (enchantShown ? freshCursed : freshShown) {
                continue;
            }
            JSONObject before = Ctx.copy(item);
            if (!levelKnown) {
                item.put("level", fresh.optInt("level"));
            }
            if (!cursedKnown) {
                item.put("cursed", freshCursed);
                if (enchantKey != null && !enchantShown) {
                    if (freshEnchant != null) {
                        item.put(enchantKey, freshEnchant);
                    } else {
                        item.remove(enchantKey);
                    }
                }
            }
            if (isA(name, Wand.class) && !item.optBoolean("curChargeKnown") && fresh.has("curCharges")) {
                item.put("curCharges", fresh.get("curCharges"));
            }
            return !before.toString().equals(item.toString());
        }
        return false;
    }

    private static boolean isCurse(JSONObject enchant) {
        String name = Ctx.className(enchant);
        Class<?> cls = classOf(name);
        if (cls == null) {
            return false;
        }
        Object instance = Reflection.newInstance(cls);
        if (instance instanceof Weapon.Enchantment e) {
            return e.curse();
        }
        if (instance instanceof Armor.Glyph g) {
            return g.curse();
        }
        return false;
    }

    /** A fresh item of the class, rolled by its own {@code random()} under the generator {@code seed}. */
    private static JSONObject fresh(Class<?> cls, long seed) {
        Random.pushGenerator(seed);
        try {
            Object made = Reflection.newInstance(cls);
            if (!(made instanceof Item item)) {
                return null;
            }
            item.random();
            return json(item);
        } finally {
            Random.popGenerator();
        }
    }

    static JSONObject json(Item item) {
        Bundle outer = new Bundle();
        outer.put("item", item);
        return new JSONObject(outer.toString()).getJSONObject("item");
    }

    // --- CONTAINER_CONTENTS and UNSEEN_ITEMS ------------------------------------------------------------------------

    private static final Set<String> REDRAWN = Set.of("SKELETON");

    /**
     * What the screen shows of a heap is its top item, or its container and no item (ADR-0006, Heaps). Everything
     * else a heap holds is hidden: a heap shown by its top item keeps that one, and a container the player has not
     * opened (a skeleton) holds one item the Codex's tables give. A chest-family heap in view never reaches this row;
     * the mimic gate refuses the snapshot first.
     */
    static void containers(Ctx c) {
        int held = 0;
        int changed = 0;
        for (JSONObject heap : Ctx.objects(c.level.optJSONArray("heaps"))) {
            int pos = heap.optInt("pos");
            if (!shown(c, heap)) {
                continue;
            }
            JSONArray items = heap.optJSONArray("items");
            String type = heap.optString("type");
            if (REDRAWN.contains(type)) {
                held++;
                JSONObject item = generated(c.streams.seed("container", pos));
                if (item != null) {
                    JSONArray one = new JSONArray();
                    one.put(item);
                    if (!one.toString().equals(String.valueOf(items))) {
                        changed++;
                    }
                    heap.put("items", one);
                }
            } else if ("HEAP".equals(type) && items != null && items.length() > 1) {
                held++;
                JSONArray top = new JSONArray();
                top.put(items.get(0));
                heap.put("items", top);
                changed++;
            }
        }
        c.held(ScrubRow.CONTAINER_CONTENTS, held);
        c.changed(ScrubRow.CONTAINER_CONTENTS, changed);
    }

    /** One item from the game's generator over a fresh deck, which is the Codex's category and class tables. */
    private static JSONObject generated(long seed) {
        Random.pushGenerator(seed);
        try {
            Generator.fullReset();
            Item item = Generator.random();
            return item == null ? null : json(item);
        } finally {
            Random.popGenerator();
        }
    }

    /** Whether the screen draws this heap: seen, on a known cell, and the Observation holds a heap there. */
    static boolean shown(Ctx c, JSONObject heap) {
        int pos = heap.optInt("pos", -1);
        JSONArray items = heap.optJSONArray("items");
        return pos >= 0 && pos < c.length && heap.optBoolean("seen") && c.known(pos) && c.heapsByCell.containsKey(pos)
                && items != null && items.length() > 0;
    }

    /** Removes the heaps the player has never seen, and clears what a seen heap hides beyond its sprite. */
    static void unseen(Ctx c) {
        List<JSONObject> kept = new ArrayList<>();
        int removed = 0;
        for (JSONObject heap : Ctx.objects(c.level.optJSONArray("heaps"))) {
            if (shown(c, heap)) {
                heap.put("haunted", false);
                kept.add(heap);
            } else {
                removed++;
            }
        }
        c.level.put("heaps", Ctx.arrayOf(kept));
        c.held(ScrubRow.UNSEEN_ITEMS, removed);
        c.changed(ScrubRow.UNSEEN_ITEMS, removed);
    }
}
