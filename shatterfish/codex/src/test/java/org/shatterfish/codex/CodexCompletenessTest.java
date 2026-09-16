package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Generator;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaStaticInitializer;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Codex's coverage is enumerated, not asserted (story 2.2): every concrete subclass of the
 * game's mob type, found by importing the game's classes, is in the mobs table once and nothing
 * else is; every class the rotation names, and every alternate, is a mob the table has; and
 * every constructor or initialiser that reads the hero, the statistics, the seed or a generator
 * is named in the generator with its reason, so that a new read is reviewed before it can reach
 * a table. The items likewise (story 2.3): every concrete item class is constructed, read from
 * source or excluded with a reason, and nothing else is; every class read from source touches
 * the icon film at construction and no constructed class does, so that the two lists are the
 * classpath's fact and not a choice; and every class a deck weights is an item the table has.
 */
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class CodexCompletenessTest {

    private static final Path ROOT = CodexSeedFreeTest.ROOT;
    private static final JavaClasses GAME = new ClassFileImporter().importPackages(Sources.GAME);
    private static final String ICONS = Sources.GAME + ".sprites.ItemSpriteSheet$Icons";

    /** The game's concrete item classes, as the table names them. */
    static TreeSet<String> gameItems() {
        TreeSet<String> names = new TreeSet<>();
        for (JavaClass c : GAME) {
            if (c.isAssignableTo(Item.class) && !c.getModifiers().contains(JavaModifier.ABSTRACT) && !c.isInterface()
                    && !c.isAnonymousClass() && !c.isLocalClass()) {
                names.add(c.getName().substring(Sources.ROOT_PACKAGE_PREFIX.length()).replace('$', '.'));
            }
        }
        return names;
    }

    /** Whether a constructor, an instance initialiser or the static initialiser of {@code c} reaches the icon film's class. */
    private static boolean touchesIcons(JavaClass c) {
        List<JavaCodeUnit> units = new java.util.ArrayList<>(c.getConstructors());
        c.getStaticInitializer().ifPresent(units::add);
        for (JavaCodeUnit unit : units) {
            for (JavaFieldAccess access : unit.getFieldAccesses()) {
                if (access.getTargetOwner().getName().equals(ICONS)) {
                    return true;
                }
            }
            for (JavaMethodCall call : unit.getMethodCallsFromSelf()) {
                if (call.getTargetOwner().getName().equals(ICONS)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    @DisplayName("every concrete item class of the game is constructed, read from source or excluded with a reason, once, and nothing else is")
    void every_item_is_listed_or_excluded() {
        TreeSet<String> expected = gameItems();
        assertTrue(expected.size() >= 300, "the game has its items: " + expected.size());
        TreeSet<String> listed = new TreeSet<>();
        int constructed = 0;
        for (Codex.ItemEntry entry : Items.entries(ROOT)) {
            assertTrue(listed.add(entry.className()), entry.className() + " is listed twice");
            constructed += entry.constructed() ? 1 : 0;
        }
        assertEquals(Items.CONSTRUCTED.size(), constructed);
        assertEquals(Items.CONSTRUCTED.size() + Items.SOURCE_READ.size(), listed.size());
        TreeSet<String> excluded = new TreeSet<>();
        for (Map.Entry<Class<? extends Item>, String> exclusion : Items.EXCLUDED) {
            String name = Sources.name(exclusion.getKey());
            assertTrue(excluded.add(name), name + " is excluded twice");
            assertFalse(exclusion.getValue().isBlank(), name + " is excluded for a reason");
            assertFalse(listed.contains(name), name + " is excluded and listed");
        }
        TreeSet<String> covered = new TreeSet<>(listed);
        covered.addAll(excluded);
        TreeSet<String> missing = new TreeSet<>(expected);
        missing.removeAll(covered);
        TreeSet<String> extra = new TreeSet<>(covered);
        extra.removeAll(expected);
        assertEquals(new TreeSet<>(), missing, "items the game has and the generator does not name; add them to Items.CONSTRUCTED, SOURCE_READ or EXCLUDED");
        assertEquals(new TreeSet<>(), extra, "classes the generator names and the game has not as concrete items; remove them");
    }

    @Test
    @DisplayName("every class read from source touches the icon film at construction, and no constructed class or superclass of one does")
    void the_source_read_classes_need_the_toolkit() {
        for (Class<? extends Item> type : Items.SOURCE_READ) {
            assertTrue(touchesIcons(GAME.get(type)), type.getName() + " is read from source, and could be constructed instead");
        }
        for (java.util.function.Supplier<Item> make : Items.CONSTRUCTED) {
            for (Class<?> c = GameContext.under(1, 0, make).getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                assertFalse(touchesIcons(GAME.get(c)), c.getName() + " touches the icon film at construction");
            }
        }
        for (Map.Entry<Class<? extends Item>, String> exclusion : Items.EXCLUDED) {
            if (exclusion.getValue().equals(Items.NO_CONSTRUCTOR)) {
                boolean bare = false;
                for (JavaCodeUnit constructor : GAME.get(exclusion.getKey()).getConstructors()) {
                    bare |= constructor.getRawParameterTypes().isEmpty() && constructor.getModifiers().contains(JavaModifier.PUBLIC);
                }
                assertFalse(bare, exclusion.getKey().getName() + " has a public no-argument constructor and is excluded for lacking one");
            }
        }
    }

    @Test
    @DisplayName("every category is a deck, every class a deck weights and every exotic pair is an item the table has, and the three label pools are the identifiable families")
    void the_decks_name_items() {
        TreeSet<String> listed = new TreeSet<>();
        for (Codex.ItemEntry entry : Items.entries(ROOT)) {
            listed.add(entry.className());
        }
        Codex.Decks decks = Decks.read(ROOT);
        assertEquals(Generator.Category.values().length, decks.categories().size());
        for (Codex.CategoryEntry category : decks.categories()) {
            for (Codex.Weighted weighted : category.classes()) {
                assertTrue(listed.contains(weighted.className()), category.name() + " weights " + weighted.className());
            }
        }
        for (Codex.ExoticPair pair : decks.exotic().pairs()) {
            assertTrue(listed.contains(pair.regular()) && listed.contains(pair.exotic()), pair.toString());
        }
        assertEquals(List.of("Potion", "Ring", "Scroll"), decks.labelPools().stream().map(Codex.LabelPool::family).toList());
    }

    /** The game's concrete mob classes, as the table names them. */
    static TreeSet<String> gameMobs() {
        return mobs(false);
    }

    /** The game's mob classes, the abstract families included: what a rotation or an alternate may name. */
    static TreeSet<String> mobFamilies() {
        return mobs(true);
    }

    private static TreeSet<String> mobs(boolean abstractToo) {
        TreeSet<String> names = new TreeSet<>();
        for (JavaClass c : GAME) {
            if (c.isAssignableTo(Mob.class) && (abstractToo || !c.getModifiers().contains(JavaModifier.ABSTRACT)) && !c.isInterface()
                    && !c.isAnonymousClass() && !c.isLocalClass()) {
                names.add(c.getName().substring(Sources.ROOT_PACKAGE_PREFIX.length()).replace('$', '.'));
            }
        }
        return names;
    }

    @Test
    @DisplayName("every concrete mob class of the game is in the table once, and nothing else is")
    void every_mob_is_listed() {
        TreeSet<String> expected = gameMobs();
        assertTrue(expected.size() >= 100, "the game has its mobs: " + expected.size());
        List<Codex.MobEntry> entries = Mobs.entries(ROOT);
        TreeSet<String> listed = new TreeSet<>();
        for (Codex.MobEntry entry : entries) {
            assertTrue(listed.add(entry.className()), entry.className() + " is listed twice");
        }
        TreeSet<String> missing = new TreeSet<>(expected);
        missing.removeAll(listed);
        TreeSet<String> extra = new TreeSet<>(listed);
        extra.removeAll(expected);
        assertEquals(new TreeSet<>(), missing, "mobs the game has and the table does not; add their constructors to Mobs.ALL");
        assertEquals(new TreeSet<>(), extra, "mobs the table has and the game does not; remove them from Mobs.ALL");
    }

    @Test
    @DisplayName("every class the rotation names, every family and its members, every alternate and every exclusion is a mob the table has")
    void the_rotation_names_mobs() {
        TreeSet<String> concrete = gameMobs();
        TreeSet<String> familySimple = new TreeSet<>();
        for (String name : mobFamilies()) {
            familySimple.add(name.substring(name.lastIndexOf('.') + 1));
        }
        Codex.SpawnRotation rotation = Rotation.read(ROOT, Mobs.canonicalNames());
        assertEquals(Mobs.MAX_DEPTH, rotation.depths().size());
        for (Codex.RotationDepth depth : rotation.depths()) {
            for (Codex.RotationEntry entry : depth.entries()) {
                if (entry.family()) {
                    assertTrue(familySimple.contains(entry.className()), "depth " + depth.depth() + " draws from the family " + entry.className());
                } else {
                    assertTrue(concrete.contains(entry.className()), "depth " + depth.depth() + " spawns " + entry.className());
                }
            }
        }
        for (Codex.Family family : rotation.families()) {
            assertTrue(familySimple.contains(family.className()), "the family " + family.className());
            for (Codex.Odds odds : family.odds()) {
                assertTrue(concrete.contains(odds.className()), "the family " + family.className() + " draws " + odds.className());
            }
        }
        for (Codex.RareMob rare : rotation.rareMobs()) {
            assertTrue(concrete.contains(rare.className()), "the rare mob " + rare.className());
        }
        for (Codex.RareAlt alt : rotation.alternates()) {
            assertTrue(concrete.contains(alt.className()) || familySimple.contains(alt.className()), "the alternate's class " + alt.className());
            assertTrue(concrete.contains(alt.alternate()), "the alternate " + alt.alternate());
        }
        for (Codex.Exclusion exclusion : rotation.champion().exclusions()) {
            assertTrue(concrete.contains(exclusion.className()), "the excluded " + exclusion.className());
        }
    }

    @Test
    @DisplayName("every constructor or initialiser that reads the hero, the statistics, the seed or a generator is named in the generator, and nothing named is clean")
    void every_construction_read_is_named() {
        TreeMap<String, String> found = new TreeMap<>();
        for (JavaClass c : GAME) {
            if (!c.isAssignableTo(Mob.class) || c.getModifiers().contains(JavaModifier.ABSTRACT) || c.isInterface() || c.isAnonymousClass() || c.isLocalClass()) {
                continue;
            }
            StringBuilder reads = new StringBuilder();
            for (JavaCodeUnit unit : c.getConstructors()) {
                for (JavaFieldAccess access : unit.getFieldAccesses()) {
                    String owner = access.getTargetOwner().getName();
                    String field = access.getTarget().getName();
                    if (owner.equals(Dungeon.class.getName()) && (field.equals("hero") || field.startsWith("seed") || field.equals("level"))
                            || owner.equals(Statistics.class.getName())) {
                        reads.append(' ').append(owner.substring(owner.lastIndexOf('.') + 1)).append('.').append(field);
                    }
                }
                for (JavaMethodCall call : unit.getMethodCallsFromSelf()) {
                    String owner = call.getTargetOwner().getName();
                    String method = call.getTarget().getName();
                    if (owner.equals(Dungeon.class.getName()) && (method.startsWith("seed") || method.equals("scalingDepth"))
                            || owner.equals(Statistics.class.getName())
                            || owner.equals(com.watabou.utils.Random.class.getName())) {
                        reads.append(' ').append(owner.substring(owner.lastIndexOf('.') + 1)).append('.').append(method).append("()");
                    }
                }
            }
            if (reads.length() > 0) {
                found.put(c.getName().substring(Sources.ROOT_PACKAGE_PREFIX.length()).replace('$', '.'), reads.toString().trim());
            }
        }
        TreeSet<String> named = new TreeSet<>();
        for (Class<?> type : Mobs.CONSTRUCTOR_READS.keySet()) {
            named.add(Sources.name(type));
        }
        assertEquals(named, new TreeSet<>(found.keySet()), "the constructors that read a Run, a Profile or a generator, against Mobs.CONSTRUCTOR_READS: " + found);
        for (Class<?> type : Mobs.HERO_DEPENDENT.keySet()) {
            assertTrue(Mobs.CONSTRUCTOR_READS.containsKey(type), type.getName() + " is hero-dependent and named as a reader");
        }
        for (Class<?> type : Mobs.RANDOM_PROPERTIES) {
            assertTrue(Mobs.CONSTRUCTOR_READS.containsKey(type), type.getName() + " draws and is named as a reader");
        }
    }
}
