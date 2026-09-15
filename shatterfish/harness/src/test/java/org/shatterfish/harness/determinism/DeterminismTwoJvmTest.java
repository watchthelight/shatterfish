package org.shatterfish.harness.determinism;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same tuple, played the same way, is the same Run — in this process twice, and in two other
 * processes that share nothing with this one but the code and the tuple.
 *
 * <p>This is the promise every number Shatterfish publishes rests on (non-negotiable 5). What it can
 * see is anything drawn from the system — an unseeded generator, which is what moved the guidebook
 * between Runs of one tuple — and anything a process accumulates, which is what a preference the
 * game flipped in an earlier Run was. What it cannot see, on one machine, is identity-hash order:
 * two JVMs started the same way give the same objects the same hashes, so a {@code HashSet} walks
 * alike in both, and the mutation battery found that reverting each ordering site of hook row 6
 * left this test green. {@code IdentityOrderTest} holds those sites directly; the first test that
 * could see them by behaviour is the cross-platform comparison.
 *
 * <p>Two processes are compared with each other and with this one, so the assertion is three
 * answers to one question. The cross-platform comparison — the same tuple on Windows and on Linux —
 * is story 3.4's nightly job (ADR-0002), not this test's.
 */
class DeterminismTwoJvmTest {

    private static final long SEED = 4242L;
    private static final long SALT = 0x5A17_5A17L;
    private static final long AGENT = 99L;
    private static final int WAITS = 30;

    /** A fingerprint line: a wait index and a hash, or the reason the Run ended. */
    private static final Pattern LINE = Pattern.compile("^(\\d+ [0-9a-f]{64}|end [A-Z_]+)$");

    @Test
    @DisplayName("the same tuple twice in this process is the same Run, wait by wait")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void the_same_tuple_twice_here() {
        List<String> first = RunFingerprint.of(SEED, HeroClass.WARRIOR, SALT, AGENT, WAITS);
        List<String> second = RunFingerprint.of(SEED, HeroClass.WARRIOR, SALT, AGENT, WAITS);
        assertTrue(first.size() >= 20, "enough waits to mean something: " + first.size());
        assertEquals(first, second, "two Runs of one tuple in one process");
    }

    @Test
    @DisplayName("the same tuple in two other processes is the same Run, and the same as here")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void the_same_tuple_in_two_processes() throws Exception {
        List<String> here = RunFingerprint.of(SEED, HeroClass.WARRIOR, SALT, AGENT, WAITS);
        List<String> there = inAnotherJvm();
        List<String> elsewhere = inAnotherJvm();

        assertTrue(there.size() >= 20, "the other process played a Run: " + there.size() + " lines");
        assertEquals(there, elsewhere, "two processes that share nothing but the tuple");
        assertEquals(here, there, "and this process agrees with them");
    }

    /** {@link RunFingerprint#main} in a fresh JVM on this test's own classpath, its lines returned. */
    private static List<String> inAnotherJvm() throws IOException, InterruptedException {
        Path javaHome = Path.of(System.getProperty("java.home"), "bin");
        Path java = Files.exists(javaHome.resolve("java.exe"))
                ? javaHome.resolve("java.exe") : javaHome.resolve("java");
        ProcessBuilder builder = new ProcessBuilder(java.toString(),
                "-cp", System.getProperty("java.class.path"),
                RunFingerprint.class.getName(),
                Long.toString(SEED), Long.toString(SALT), Long.toString(AGENT), Integer.toString(WAITS));
        builder.redirectErrorStream(true);
        Process process = builder.start();

        List<String> lines = new ArrayList<>();
        List<String> everything = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                everything.add(line);
                if (LINE.matcher(line.trim()).matches()) {
                    lines.add(line.trim());
                }
            }
        }
        boolean ended = process.waitFor(10, TimeUnit.MINUTES);
        if (!ended) {
            process.destroyForcibly();
            throw new AssertionError("the other process did not end in ten minutes; its last lines:\n  "
                    + String.join("\n  ", tail(everything)));
        }
        assertEquals(0, process.exitValue(), "the other process failed; its last lines:\n  "
                + String.join("\n  ", tail(everything)));
        return lines;
    }

    private static List<String> tail(List<String> lines) {
        return lines.subList(Math.max(0, lines.size() - 25), lines.size());
    }
}
