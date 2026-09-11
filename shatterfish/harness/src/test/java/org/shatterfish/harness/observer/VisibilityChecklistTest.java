package org.shatterfish.harness.observer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every row of ADR-0006's whitelist is claimed by a suite that tests it. The whitelist is the list
 * of things the Observer may read, and non-negotiable 1 says every change to the Observer ships
 * with leak tests; this suite is what makes that mechanical. It reads the table out of the
 * decision record, collects the rows each observer suite claims in its own {@code ADR_0006_ROWS}
 * field, and holds the two lists equal but for the rows still to be built, each of which names the
 * issue that closes it. A row added to the record with no test fails here, and so does a claim on
 * a row the record does not have.
 *
 * <p>What it cannot hold is the quality of a claim: a suite that lists a row and tests it badly
 * passes here, and the review of the story that adds the row is what says whether the test earns
 * the claim. What this stops is a row arriving with no test at all, which is how a whitelist grows
 * quietly.
 */
class VisibilityChecklistTest {

    /** The record whose table is the whitelist. */
    private static final String ADR = "docs/adr/0006-observer-visibility-rules.md";

    /** The header of the whitelist table, which no other table in the record shares. */
    private static final String HEADER = "| Rule | What the Observer reads |";

    /** The field a suite declares to claim the rows it holds. */
    private static final String CLAIM = "ADR_0006_ROWS";

    /**
     * Rows no test can hold yet, each with the issue that will. Empty since story 1.12 filled the
     * valid-Action set, which was the last one: every row of the whitelist is now claimed by a
     * suite that tests it, and a row added to the record fails here until one claims it.
     */
    private static final Map<String, String> PENDING = Map.of();

    @Test
    @DisplayName("every row of the whitelist is claimed by a leak test, or pending with its issue")
    void every_row_has_a_test() {
        List<String> rows = rows();
        assertTrue(rows.size() >= 18, "the whitelist was read: " + rows);
        Map<String, List<String>> claims = claims();

        for (String claimed : claims.keySet()) {
            assertTrue(rows.contains(claimed), claimed + " is claimed by " + claims.get(claimed)
                    + " and is no row of " + ADR + "'s table; the rows are " + rows);
        }
        for (String row : rows) {
            boolean pending = PENDING.containsKey(row);
            boolean held = claims.containsKey(row);
            assertFalse(pending && held, row + " is claimed by " + claims.get(row) + " and still listed as pending");
            assertTrue(pending || held, "no test claims the row \"" + row + "\" of " + ADR + ". Every row of the"
                    + " whitelist needs a leak test naming it (non-negotiable 1): add the row to the "
                    + CLAIM + " field of the suite that holds it, or list it as pending with its issue");
        }
        for (String pending : PENDING.keySet()) {
            assertTrue(rows.contains(pending), "the record has no row \"" + pending + "\", which is listed as"
                    + " pending on " + PENDING.get(pending) + ": a stale entry");
        }
    }

    @Test
    @DisplayName("a suite that claims a row is a suite with tests in it")
    void a_claim_is_a_test() {
        Map<String, List<String>> claims = claims();
        assertFalse(claims.isEmpty(), "no suite claims a row of the whitelist");
        for (List<String> suites : claims.values()) {
            for (String suite : suites) {
                Class<?> type = type(suite);
                boolean tests = false;
                for (Method method : type.getDeclaredMethods()) {
                    tests |= method.isAnnotationPresent(Test.class);
                }
                assertTrue(tests, suite + " claims a row of the whitelist and declares no test");
            }
        }
    }

    /** The first cell of every row of the whitelist table, in the record's order. */
    private static List<String> rows() {
        List<String> lines = read(repoRoot().resolve(ADR));
        int header = -1;
        for (int i = 0; i < lines.size() && header < 0; i++) {
            if (lines.get(i).startsWith(HEADER)) {
                header = i;
            }
        }
        assertTrue(header >= 0, "no whitelist table in " + ADR + ", looking for " + HEADER);
        List<String> rows = new ArrayList<>();
        for (int i = header + 2; i < lines.size() && lines.get(i).startsWith("|"); i++) {
            rows.add(lines.get(i).split("\\|")[1].trim());
        }
        return rows;
    }

    /** Row to the suites claiming it, read from every {@code ADR_0006_ROWS} field of this package. */
    private static Map<String, List<String>> claims() {
        Map<String, List<String>> claims = new TreeMap<>();
        for (String name : suites()) {
            Field field;
            try {
                field = type(name).getDeclaredField(CLAIM);
            } catch (NoSuchFieldException absent) {
                continue;
            }
            field.setAccessible(true);
            List<?> rows;
            try {
                rows = (List<?>) field.get(null);
            } catch (IllegalAccessException refused) {
                throw new AssertionError(name + "." + CLAIM + " cannot be read", refused);
            }
            for (Object row : rows) {
                claims.computeIfAbsent(String.valueOf(row), r -> new ArrayList<>()).add(name);
            }
        }
        return claims;
    }

    /** Every class of this package's test sources, by simple name. */
    private static List<String> suites() {
        Path dir = repoRoot().resolve("shatterfish/harness/src/test/java")
                .resolve(VisibilityChecklistTest.class.getPackageName().replace('.', '/'));
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(file -> file.endsWith(".java"))
                    .map(file -> file.substring(0, file.length() - ".java".length()))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("the observer test sources are not where they were: " + dir, unreadable);
        }
    }

    private static Class<?> type(String simpleName) {
        String name = VisibilityChecklistTest.class.getPackageName() + "." + simpleName;
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError(name + " has a source file and no class", missing);
        }
    }

    private static List<String> read(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("cannot read " + file, unreadable);
        }
    }

    /** The repository root, as the hook ledger's tests find it: the directory holding {@code docs}. */
    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("docs/adr")) && Files.isRegularFile(dir.resolve("settings.gradle"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new AssertionError("no repository root above " + Path.of("").toAbsolutePath());
    }
}
