package org.shatterfish.harness.determinism;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.harness.driver.HeadlessDriver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #303: {@code Mob.chooseEnemy} collected its candidates in a plain {@code HashSet<Char>},
 * and a tie between equidistant enemies went to whichever the set walked first, which is the
 * identity hash's business and not the Run's. Hook row 6 makes the set a {@code LinkedHashSet}, so
 * the tie goes to the mob the level holds first.
 *
 * <p>Two JVMs started alike give objects the same identity hashes, so the tie is built many times
 * over in one JVM, with fresh objects each time: on a plain {@code HashSet} the walk agrees with
 * insertion order by chance only, and all of the trials agreeing is a coin tossed that many times.
 *
 * <p>The test JVM pins every identity hash to one value ({@code -XX:hashCode=2}, see
 * {@code harness/build.gradle}), which makes a plain {@code HashSet} walk in insertion order and
 * hides the bug here. So the trials run in a fresh JVM under the real hash modes (4, address
 * derived, and 5, the default), where a plain {@code HashSet} fails them.
 */
public class ChooseEnemyOrderTest {

    private static final long RUN_SALT = 0x5A17_5A17L;
    private static final int TRIALS = 40;

    /** An allied rat that lets the test ask who it would fight. */
    private static final class Probe extends Rat {
        Char pick() {
            return chooseEnemy();
        }
    }

    @Test
    @DisplayName("two equidistant enemies are chosen between by the order the level holds them in, under real identity hashes")
    void a_tie_goes_to_the_mob_the_level_holds_first() throws Exception {
        for (int mode : new int[] {4, 5}) {
            List<String> out = inAnotherJvm(mode);
            assertTrue(out.stream().anyMatch(l -> l.equals("TRIALS-OK")),
                    "hashCode=" + mode + ", last lines: " + String.join(" | ", out));
        }
    }

    /** The trials, in whatever JVM runs {@code main}: an AssertionError ends it with a failure. */
    public static void main(String[] args) {
        try (HeadlessDriver driver = HeadlessDriver.start(4242L, HeroClass.WARRIOR, RUN_SALT)) {
            driver.stepToInputWait();
            trials();
        }
        System.out.println("TRIALS-OK");
        System.exit(0);
    }

    private static void trials() {
        Level level = Dungeon.level;

        // Five open cells in a row: the ally in the middle, a candidate two cells off each side. Both
        // are two steps away and neither can be struck from where the ally stands, so nothing in
        // chooseEnemy prefers either and only the walk order is left to decide.
        int width = level.width();
        int middle = -1;
        for (int c = 3; c < level.length() - 3 && middle < 0; c++) {
            if (c % width < 3 || c % width > width - 4) {
                continue;
            }
            boolean open = true;
            for (int d = -2; d <= 2; d++) {
                open &= level.passable[c + d] && Dungeon.hero.pos != c + d;
            }
            if (open) {
                middle = c;
            }
        }
        if (middle < 0) {
            throw new AssertionError("no row of five open cells on the first floor");
        }

        for (int trial = 0; trial < TRIALS; trial++) {
            level.mobs.clear();
            Probe ally = new Probe();
            ally.alignment = Char.Alignment.ALLY;
            ally.pos = middle;
            ally.fieldOfView = new boolean[level.length()];
            java.util.Arrays.fill(ally.fieldOfView, true);

            Rat first = hostile(middle + (trial % 2 == 0 ? -2 : 2));
            Rat second = hostile(middle + (trial % 2 == 0 ? 2 : -2));
            level.mobs.add(ally);
            level.mobs.add(first);
            level.mobs.add(second);

            Char chosen = ally.pick();
            if (chosen != first) {
                throw new AssertionError("trial " + trial + ": the tie went to the mob inserted second");
            }
        }
        level.mobs.clear();
    }

    private static Rat hostile(int cell) {
        Rat rat = new Rat();
        rat.alignment = Char.Alignment.ENEMY;
        rat.pos = cell;
        rat.state = rat.HUNTING;
        return rat;
    }

    private static List<String> inAnotherJvm(int hashCodeMode) throws IOException, InterruptedException {
        Path javaHome = Path.of(System.getProperty("java.home"), "bin");
        Path java = Files.exists(javaHome.resolve("java.exe"))
                ? javaHome.resolve("java.exe") : javaHome.resolve("java");
        ProcessBuilder builder = new ProcessBuilder(java.toString(),
                "-XX:+UnlockExperimentalVMOptions", "-XX:hashCode=" + hashCodeMode,
                "-cp", System.getProperty("java.class.path"),
                ChooseEnemyOrderTest.class.getName());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.trim());
            }
        }
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new AssertionError("the other process did not end in five minutes");
        }
        List<String> tail = lines.subList(Math.max(0, lines.size() - 15), lines.size());
        assertEquals(0, process.exitValue(), "hashCode=" + hashCodeMode + " failed; its last lines: "
                + String.join(" | ", tail));
        return tail;
    }
}
