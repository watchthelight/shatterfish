package org.shatterfish.codex;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.shatterfish.api.Codex;

import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Codex's coverage is enumerated, not asserted (story 2.2): every concrete subclass of the
 * game's mob type, found by importing the game's classes, is in the mobs table once and nothing
 * else is; every class the rotation names, and every alternate, is a mob the table has.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES)
class CodexCompletenessTest {

    private static final Path ROOT = CodexSeedFreeTest.ROOT;

    private static final JavaClasses GAME = new ClassFileImporter().importPackages(Sources.GAME);

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
                    && !c.isAnonymousClass()) {
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
    @DisplayName("every class the rotation names, every family member and every alternate is a mob the table has")
    void the_rotation_names_mobs() {
        TreeSet<String> simple = new TreeSet<>();
        for (String name : mobFamilies()) {
            simple.add(name.substring(name.lastIndexOf('.') + 1));
        }
        Codex.SpawnRotation rotation = Rotation.read(ROOT);
        assertEquals(Mobs.MAX_DEPTH, rotation.depths().size());
        for (Codex.RotationDepth depth : rotation.depths()) {
            for (Codex.RotationEntry entry : depth.entries()) {
                if (entry.family().isEmpty()) {
                    assertTrue(simple.contains(entry.className()), "depth " + depth.depth() + " spawns " + entry.className());
                } else {
                    for (Codex.Odds odds : entry.family()) {
                        assertTrue(simple.contains(odds.className()), "the family " + entry.className() + " draws " + odds.className());
                    }
                }
            }
        }
        for (Codex.RareMob rare : rotation.rareMobs()) {
            assertTrue(simple.contains(rare.className()), "the rare mob " + rare.className());
        }
        for (Codex.RareAlt alt : rotation.alternates()) {
            assertTrue(simple.contains(alt.className()), "the alternate's class " + alt.className());
            assertTrue(gameMobs().contains(alt.alternate()), "the alternate " + alt.alternate());
        }
        for (Codex.Exclusion exclusion : rotation.champion().exclusions()) {
            assertTrue(simple.contains(exclusion.className()), "the excluded " + exclusion.className());
        }
    }
}
