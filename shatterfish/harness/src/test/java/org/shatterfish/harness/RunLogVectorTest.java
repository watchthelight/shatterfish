package org.shatterfish.harness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.shatterfish.api.Action;
import org.shatterfish.api.HeroClass;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.RunLogJson;
import org.shatterfish.api.SeedSet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The methodology page's published test vector, held against what the code actually does
 * (story 3.2).
 *
 * <p>A page that publishes rules a stranger is invited to implement is a promise, and a promise
 * nothing checks drifts. Epic 2 shipped an index that stated a count from memory beside a derived
 * one, and the drift check regenerated the false prose faithfully because it compared the output
 * with itself. So this reads the page as text, finds the vector's three rows, and compares them
 * with a record built here and a chain computed two independent ways -- the library's, and the
 * JDK's own SHA-256 over the published text.
 *
 * <p>If this fails, the page is wrong or the format changed: fix whichever is not what the project
 * meant, and never fix the page by pasting whatever the code now emits without reading why.
 */
class RunLogVectorTest {

    /** The repository root, found from the working directory by what a checkout holds. */
    private static final Path ROOT = root();

    private static Path root() {
        Path here = Path.of("").toAbsolutePath();
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("docs/UPSTREAM.md"))
                    && Files.isDirectory(p.resolve("core/src/main/java"))) {
                return p;
            }
        }
        throw new IllegalStateException("no Shatterfish checkout above " + here);
    }

    private static final long SEED = 12_345L;

    private static final String ZERO = "0".repeat(64);

    /** The header the page publishes, built here from its stated values. */
    private static RunLog.Header published() {
        return new RunLog.Header(1, "v4.0.0", "abc1234", HeroClass.WARRIOR, 0, SEED, SeedSet.code(SEED),
                7L, 3, 2, 8, new RunLog.Brain("random", "def5678", ZERO), "", false,
                "a laptop", "2026-09-22T12:00:00Z");
    }

    private static String page() throws IOException {
        Path file = ROOT.resolve("docs/methodology.md");
        assertTrue(Files.isRegularFile(file), "the methodology page is at " + file);
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the vector on the methodology page is the record, the chained text and the chain this code produces")
    void the_published_vector_is_the_real_one() throws IOException {
        String page = page();
        RunLog.Header header = published();

        String record = RunLogJson.canonical(header);
        String chained = RunLogJson.chained(header);
        String chain = RunLogJson.chain("", header);

        assertTrue(page.contains("`" + record + "`"),
                "the page's record row is not what the writer writes; it says\n  " + record);
        assertTrue(page.contains("`" + chained + "`"),
                "the page's chained row is not the text the chain covers; it says\n  " + chained);
        assertTrue(page.contains("`" + chain + "`"),
                "the page's chain is not the one this code computes; it is\n  " + chain);
    }

    @Test
    @DisplayName("the chain the page publishes is what the JDK's own SHA-256 gives over the page's own text")
    void a_stranger_recomputes_the_published_chain() throws IOException {
        String page = page();
        String chained = RunLogJson.chained(published());

        // Read the chain out of the page rather than from the library: this is the stranger's
        // path, and it has to work from what is published and nothing else.
        int at = page.indexOf("| `chain` | `");
        assertTrue(at > 0, "the page publishes a chain row");
        String stated = page.substring(at + "| `chain` | `".length(), page.indexOf('`', at + 13));

        assertEquals(stated, LogText.hex(LogText.sha256(LogText.utf8(chained))),
                "the published chain is SHA-256 of the published chained text, and nothing else");
    }

    /** Every unquoted run of number characters in a canonical line, strings skipped. */
    private static List<String> numbers(String line) {
        List<String> found = new java.util.ArrayList<>();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                while (++i < line.length() && line.charAt(i) != '"') {
                    if (line.charAt(i) == '\\') {
                        i++;
                    }
                }
            } else if (c == '-' || Character.isDigit(c)) {
                int from = i;
                while (i + 1 < line.length() && "0123456789.eE+-".indexOf(line.charAt(i + 1)) >= 0) {
                    i++;
                }
                found.add(line.substring(from, i + 1));
            }
        }
        return found;
    }

    @Test
    @DisplayName("the page states the rules the format actually follows")
    void the_published_rules_are_the_real_ones() throws IOException {
        String page = page();

        for (String key : RunLogJson.UNCHAINED) {
            assertTrue(page.contains("`" + key + "`"),
                    "the page lists every key the chain leaves out, and misses " + key);
        }
        assertTrue(page.contains("<tag>-<class>-<challenges>-<seedcode>-<salt>-<brain>"),
                "the page states the run id's shape");

        // The rules are not decoration: each one below is a thing the writer does.
        RunLog.Wait wait = new RunLog.Wait(0, 1, 1, 0, ZERO, Map.of("map", ZERO),
                new Action.Step(1), true, RunLog.BOT, null, "", List.of(), 5);
        String line = RunLogJson.line("", wait);
        assertTrue(!line.contains(" "), "no whitespace outside a string, as the page says");
        assertTrue(!line.contains("null"), "a field with nothing to say is absent, as the page says");
        // "Whole numbers only" is about the numbers, not about the line: the header's own tag is
        // `v4.0.0`, so a line-wide search for a period fails on a legitimate log and passes on the
        // one record that could not have violated the rule. Every unquoted numeric run, in a header
        // and a wait and an end, is checked instead.
        for (RunLog record : List.of(published(), wait,
                new RunLog.End(1, new RunLog.Outcome(false, false, 7, 2, 3, "DEATH", 0), true))) {
            for (String number : numbers(RunLogJson.canonical(record))) {
                assertTrue(number.matches("-?\\d+"), record.t() + " writes " + number
                        + ", and the page says every number is a whole number");
            }
        }

        // And a record after another chains onto its bytes, not onto its text.
        String first = RunLogJson.chain("", published());
        assertEquals(LogText.hex(LogText.sha256(LogText.concat(LogText.unhex(first),
                        LogText.utf8(RunLogJson.chained(wait))))),
                RunLogJson.chain(first, wait),
                "the previous chain enters as its thirty-two bytes, as the page says");
    }
}
