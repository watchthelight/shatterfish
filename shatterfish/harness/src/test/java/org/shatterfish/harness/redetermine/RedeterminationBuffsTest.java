package org.shatterfish.harness.redetermine;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.watabou.utils.Reflection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the allowlist of the hero's hidden buffs ({@code GameRows.heroBuffs}, audit blocker B1). A buff that shows on
 * the status pane has an icon, and the Observation lists it; a buff without an icon carries state the screen never
 * shows, and the scrubber drops it unless it is on {@code GameRows.NO_ICON_KEPT}. A new buff upstream that has no icon
 * is therefore dropped from a scrubbed copy until someone decides, and this test lists the ones decided so far: when
 * the game gains one, this test fails and says which, so that the decision is made in a pull request and not by default.
 */
class RedeterminationBuffsTest {

    private static final String GAME = "com.shatteredpixel.shatteredpixeldungeon.";

    /**
     * Every buff class of the pinned game that has no icon, is not kept by the scrubber, and was reviewed: the scrubber
     * drops these from a scrubbed copy's hero. They are trackers of talents, abilities and enchantments, cooldown and
     * dropped-once flags, and state a Brain cannot see; each is state the Observation does not list.
     */
    private static Set<String> reviewed() {
        try (InputStream in = RedeterminationBuffsTest.class.getResourceAsStream("iconless-buffs-dropped.txt")) {
            return new TreeSet<>(Arrays.stream(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
                    .map(String::trim).filter(line -> !line.isEmpty()).toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("every buff without an icon is either kept by the scrubber or reviewed here")
    void every_iconless_buff_is_decided() {
        Set<String> iconless = new TreeSet<>();
        // Some icons read the hero (a buff's icon may depend on its state), so a Run stands behind the check.
        HeadlessDriver driver = HeadlessDriver.start(3343871708117L, HeroClass.WARRIOR, 1L);
        try {
            driver.stepToInputWait();
            scan(iconless);
        } finally {
            driver.close();
        }
        assertTrue(iconless.containsAll(GameRows.NO_ICON_KEPT), "the kept buffs have no icon, or the allowlist is stale: "
                + GameRows.NO_ICON_KEPT + " of " + iconless);
        Set<String> undecided = new TreeSet<>(iconless);
        undecided.removeAll(GameRows.NO_ICON_KEPT);
        Set<String> reviewed = reviewed();
        undecided.removeAll(reviewed);
        assertEquals(Set.of(), undecided, "buffs without an icon that nobody has reviewed; the scrubber drops them from a copy's"
                + " hero until they are added to iconless-buffs-dropped.txt");
        Set<String> gone = new TreeSet<>(reviewed);
        gone.removeAll(iconless);
        assertEquals(Set.of(), gone, "reviewed buffs the game no longer has without an icon; remove them from the list");
    }

    private static void scan(Set<String> iconless) {
        for (String name : classNames()) {
            Class<?> cls;
            try {
                cls = Class.forName(name, false, Buff.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                continue;
            }
            if (!Buff.class.isAssignableFrom(cls) || cls.isInterface() || Modifier.isAbstract(cls.getModifiers())) {
                continue;
            }
            Object made;
            try {
                made = Reflection.newInstance(cls);
            } catch (RuntimeException | LinkageError e) {
                continue;
            }
            if (!(made instanceof Buff buff)) {
                continue;
            }
            buff.target = Dungeon.hero;
            boolean none;
            try {
                none = buff.icon() == BuffIndicator.NONE;
            } catch (RuntimeException e) {
                none = true;
            }
            if (none) {
                iconless.add(cls.getName());
            }
        }
    }

    /** The names of every class in the game's own code, found where the loader put it. */
    private static List<String> classNames() {
        try {
            Path where = Path.of(Buff.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            List<String> names = new ArrayList<>();
            if (Files.isDirectory(where)) {
                try (Stream<Path> files = Files.walk(where)) {
                    for (Path file : files.toList()) {
                        String relative = where.relativize(file).toString().replace(File.separatorChar, '.');
                        if (relative.endsWith(".class")) {
                            names.add(relative.substring(0, relative.length() - ".class".length()));
                        }
                    }
                }
            } else {
                try (JarFile jar = new JarFile(where.toFile())) {
                    jar.stream().map(ZipEntry::getName).filter(n -> n.endsWith(".class"))
                            .forEach(n -> names.add(n.substring(0, n.length() - ".class".length()).replace('/', '.')));
                }
            }
            names.removeIf(n -> !n.startsWith(GAME));
            return names;
        } catch (IOException | URISyntaxException e) {
            throw new AssertionError(e);
        }
    }
}
