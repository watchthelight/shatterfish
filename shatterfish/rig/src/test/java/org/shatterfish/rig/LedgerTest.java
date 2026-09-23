package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Registration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The count of attempts behind a published number (story 3.5, FR-25).
 *
 * <p>A number means something different depending on how many times the question was asked before
 * it gave that answer, and the invocations nobody published leave no other trace at all. So the
 * ledger records every one, and these tests are about the two ways a count stops being worth
 * anything: a line quietly rewritten, and a line that cannot be read.
 */
class LedgerTest {

    private static final String ZERO = "0".repeat(64);

    private static final String ONE = "1".repeat(64);

    private static Registrations.Committed committed(String id, boolean releaseLevel) {
        Registration registration = new Registration(id, "the random Brain finishes every Run", null,
                new Registration.Brain("random", "abc1234", ZERO), "holdout", 1, 50, 50, 8, 25, 0,
                "a laptop", releaseLevel);
        return new Registrations.Committed(registration, "f".repeat(40), registration.hash());
    }

    private static Path lines(Path folder) {
        return folder.resolve(Ledger.FILE);
    }

    @Test
    @DisplayName("a ledger that does not exist yet is a ledger with nothing in it")
    void an_absent_ledger_is_empty(@TempDir Path folder) {
        Ledger ledger = new Ledger(folder);

        assertEquals(List.of(), ledger.entries());
        assertEquals(0, ledger.uses("H-0001-anything"));
        assertNull(ledger.holdoutUse("abc1234", ZERO));
    }

    @Test
    @DisplayName("every use is appended, and a use already written is never touched again")
    void uses_are_appended(@TempDir Path folder) throws IOException {
        Ledger ledger = new Ledger(folder);
        ledger.record(committed("H-0001-one", false), "random", "abc1234", ZERO, "smoke",
                Ledger.Outcome.FINISHED, false, "");
        String afterOne = Files.readString(lines(folder), StandardCharsets.UTF_8);

        ledger.record(committed("H-0001-one", false), "random", "abc1234", ZERO, "smoke",
                Ledger.Outcome.FINISHED, false, "");

        String afterTwo = Files.readString(lines(folder), StandardCharsets.UTF_8);
        assertTrue(afterTwo.startsWith(afterOne),
                "the first line is untouched by the second write:\n" + afterTwo);
        assertEquals(2, afterTwo.strip().split("\n").length);
        assertEquals(2, ledger.uses("H-0001-one"));
        assertEquals(2, new Ledger(folder).entries().size(), "and it reads back");
    }

    @Test
    @DisplayName("an invocation that was refused is still an attempt, and is counted as one")
    void a_refused_invocation_is_counted(@TempDir Path folder) {
        // A ledger that recorded only the invocations somebody was happy with would be the exact
        // opposite of the count FR-25 asks for.
        Ledger ledger = new Ledger(folder);
        ledger.record(committed("H-0002-tried", false), "random", "abc1234", ZERO, "smoke",
                Ledger.Outcome.REFUSED, false, "a child died");
        ledger.record(committed("H-0002-tried", false), "random", "abc1234", ZERO, "smoke",
                Ledger.Outcome.FORBIDDEN, false, "no release-level claim");
        ledger.record(committed("H-0002-tried", false), "random", "abc1234", ZERO, "smoke",
                Ledger.Outcome.FINISHED, false, "");

        assertEquals(3, ledger.uses("H-0002-tried"));
        assertEquals(List.of(Ledger.Outcome.REFUSED, Ledger.Outcome.FORBIDDEN, Ledger.Outcome.FINISHED),
                ledger.entries().stream().map(Ledger.Entry::outcome).toList());
    }

    @Test
    @DisplayName("a holdout use is found by the Brain version that spent it, not by the Brain's name")
    void the_budget_is_per_brain_version(@TempDir Path folder) {
        Ledger ledger = new Ledger(folder);
        ledger.record(committed("H-0003-release", true), "random", "abc1234", ZERO, "holdout",
                Ledger.Outcome.FINISHED, true, "");

        assertNotNull(ledger.holdoutUse("abc1234", ZERO));
        assertTrue(ledger.holdoutUse("abc1234", ZERO).contains("H-0003-release"));
        assertNull(ledger.holdoutUse("def5678", ZERO), "a different commit is a different Brain");
        assertNull(ledger.holdoutUse("abc1234", ONE), "and so is a different configuration");
    }

    @Test
    @DisplayName("a use that did not touch the holdout does not spend its allowance")
    void a_development_run_spends_nothing(@TempDir Path folder) {
        Ledger ledger = new Ledger(folder);
        ledger.record(committed("H-0004-smoke", false), "random", "abc1234", ZERO, "smoke",
                Ledger.Outcome.FINISHED, false, "");

        assertEquals(1, ledger.uses("H-0004-smoke"));
        assertNull(ledger.holdoutUse("abc1234", ZERO),
                "running the smoke set does not spend the holdout budget");
    }

    @Test
    @DisplayName("a ledger with a line nobody can read is refused, not skipped past")
    void a_malformed_line_is_refused(@TempDir Path folder) throws IOException {
        // A count with a hole in it is not a smaller count, it is an unknown one -- and appending a
        // true line to a document whose other lines cannot be read is adding to something that has
        // stopped being evidence.
        Ledger ledger = new Ledger(folder);
        ledger.record(committed("H-0005-first", false), "random", "abc1234", ZERO, "smoke",
                Ledger.Outcome.FINISHED, false, "");
        Files.writeString(lines(folder),
                Files.readString(lines(folder), StandardCharsets.UTF_8) + "{\"b\":1,\"a\":2}\n",
                StandardCharsets.UTF_8);

        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> new Ledger(folder));

        assertTrue(refused.getMessage().contains("line 2"), refused.getMessage());
        assertTrue(refused.getMessage().contains("unknown"), refused.getMessage());
    }

    @Test
    @DisplayName("a ledger line is canonical, so it reviews as a diff and reads with any script")
    void a_line_is_canonical(@TempDir Path folder) throws IOException {
        Ledger ledger = new Ledger(folder);
        Ledger.Entry entry = ledger.record(committed("H-0006-shape", true), "random", "abc1234",
                ZERO, "holdout", Ledger.Outcome.FINISHED, true, "the release number");

        String line = Files.readString(lines(folder), StandardCharsets.UTF_8).strip();
        assertTrue(line.startsWith("{") && line.endsWith("}"), line);
        assertEquals("at", line.substring(2, 4), "the keys are sorted, so `at` comes first: " + line);
        assertTrue(line.contains("\"holdout\":true"), line);
        assertTrue(line.contains("\"registration\":\"H-0006-shape\""), line);
        assertTrue(line.contains("\"note\":\"the release number\""), line);
        assertEquals(entry, Ledger.Entry.of(line), "and it reads back to the same record");
    }

    @Test
    @DisplayName("the ledger this repository committed is about Registrations this repository holds")
    void the_committed_ledger_is_real() {
        // Nothing checked it. An unparseable line was caught only incidentally -- every Runner test
        // reads this folder -- and a line pointing at a hash no Registration ever had, or naming a
        // hypothesis that does not exist, was invisible. The count of prior attempts behind a
        // published number is what FR-25 publishes, so it has to be about something.
        Ledger ledger = new Ledger(SeedSetsTest.ROOT.resolve(Registrations.FOLDER));
        List<String> ids = Registrations.ids(SeedSetsTest.ROOT);

        for (Ledger.Entry entry : ledger.entries()) {
            assertTrue(ids.contains(entry.registration()),
                    "the ledger names " + entry.registration() + " and the folder holds " + ids);
            assertEquals(Registrations.read(SeedSetsTest.ROOT, entry.registration()).hash(),
                    entry.hash(),
                    "the line for " + entry.registration() + " names bytes that Registration has");
            assertTrue(entry.at().matches("[0-9a-f]{40}"),
                    "and the commit the hypothesis was fixed by: " + entry.at());
        }
    }
}
