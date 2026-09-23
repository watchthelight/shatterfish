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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        return new RunLog.Header(2, "v4.0.0", "abc1234", HeroClass.WARRIOR, 0, SEED,
                SeedSet.code(SEED), 7L, 20_000, 3, 2, 8,
                new RunLog.Brain("random", "def5678", ZERO), "", false,
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

    @Test
    @DisplayName("the escape table on the page is the one the writer follows, character by character")
    void the_published_escapes_are_the_real_ones() throws IOException {
        String page = page();

        // Each row of the page's table, as a pair: what goes in, what must come out. A second
        // implementation that follows the page and differs here computes a different chain for
        // every log holding a tab, a control character or a lone surrogate.
        String[][] rows = {
                {Q_, BS_ + Q_}, {BS_, BS_ + BS_}, {BSP_, BS_ + "b"}, {FF_, BS_ + "f"},
                {LF_, BS_ + "n"}, {CR_, BS_ + "r"}, {TAB_, BS_ + "t"},
                {"" + (char) 1, BS_ + "u0001"}, {"" + (char) 0x1f, BS_ + "u001f"},
                {"" + (char) 0xD800, BS_ + "ud800"}, {"e" + (char) 0xE9, "e" + (char) 0xE9},
                {"/", "/"}, {new String(Character.toChars(0x1F600)), new String(Character.toChars(0x1F600))},
        };
        for (String[] row : rows) {
            RunLog.Unsupported record = new RunLog.Unsupported(0, "x" + row[0] + "y");
            assertTrue(RunLogJson.canonical(record).contains(Q_ + "x" + row[1] + "y" + Q_),
                    "the writer does not escape " + describe(row[0]) + " as the page says ("
                            + row[1] + "): " + RunLogJson.canonical(record));
        }
        assertTrue(page.contains("lower-case"), "the page says which case the hex digits are in");
        assertTrue(page.contains("| `/` | `/` |") || page.contains("including `/`"),
                "the page says the solidus is not escaped");
    }

    private static final String Q_ = String.valueOf((char) 34);

    private static final String BS_ = String.valueOf((char) 92);

    private static final String BSP_ = String.valueOf((char) 8);

    private static final String FF_ = String.valueOf((char) 12);

    private static final String LF_ = String.valueOf((char) 10);

    private static final String CR_ = String.valueOf((char) 13);

    private static final String TAB_ = String.valueOf((char) 9);

    private static String describe(String in) {
        return in.length() == 1 ? "U+" + String.format("%04X", (int) in.charAt(0)) : in;
    }

    @Test
    @DisplayName("the page's per-field table is what the writer does with a field that has nothing to say")
    void absent_or_empty_is_what_the_page_says() {
        RunLog.Wait bare = new RunLog.Wait(0, 1, 1, 0, ZERO, Map.of("map", ZERO), new Action.Step(1),
                true, RunLog.BOT, null, "", List.of(), 5);
        String line = RunLogJson.canonical(bare);

        for (String absent : List.of("decision", "belief", "highlights")) {
            assertFalse(line.contains(Q_ + absent + Q_ + ":"), absent + " is absent when it has nothing to say");
        }
        assertFalse(RunLogJson.line("", published()).contains(Q_ + "prev" + Q_),
                "nothing comes before the header");
        assertTrue(RunLogJson.canonical(published()).contains(Q_ + "registration" + Q_ + ":" + Q_ + Q_),
                "an unregistered Run writes an empty registration rather than omitting it");
        String shadow = RunLogJson.canonical(new RunLog.Shadow(1, new RunLog.Decision("g",
                new RunLog.Choice(new Action.PickUp(), 0, ""), List.of(), List.of(), "p")));
        assertTrue(shadow.contains(Q_ + "alternatives" + Q_ + ":[]"), shadow);
        assertTrue(shadow.contains(Q_ + "flags" + Q_ + ":[]"), shadow);
        assertFalse(line.contains("null"), "no field is ever null");
    }
}
