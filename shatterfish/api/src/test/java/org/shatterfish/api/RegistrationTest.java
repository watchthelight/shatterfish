package org.shatterfish.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hypothesis, fixed before the numbers are seen (story 3.5, FR-22).
 *
 * <p>Two properties matter here and everything else is detail. The canonical text is stable, so the
 * hash a Run log stamps means the same thing to a stranger's script as it does here; and the hash
 * moves when <em>any</em> field moves, so a Registration cannot be quietly adjusted after the fact
 * and still match the logs that cite it. The second is the one this story exists for, and it is
 * checked field by field rather than by eye.
 */
class RegistrationTest {

    private static final String ZERO = "0".repeat(64);

    private static final String ONE = "1".repeat(64);

    private static Registration.Brain brain(String name) {
        return new Registration.Brain(name, "abc1234", ZERO);
    }

    private static Registration baseline() {
        return new Registration("H-0001-a-baseline", "the random Brain finishes every Run",
                null, brain("random"), "smoke", 1, 50, 50, 8, 25, 0, "a laptop", false);
    }

    private static Registration comparison() {
        return new Registration("H-0002-a-comparison", "greedy beats random on the standard set",
                brain("random"), brain("greedy"), "standard", 1, 50, 50, 16, 500, 250,
                "a laptop", false);
    }

    // ---------------------------------------------------------------------------- the two shapes

    @Test
    @DisplayName("a Registration fixes a baseline or a comparison, and says which")
    void a_registration_takes_two_shapes() {
        // ADR-0012 describes comparisons and every field it lists is about one -- but E3's own
        // done-when is that a baseline is published, and the nightly smoke job compares nothing.
        // A form that could not express a baseline would have forced the first Registration this
        // project committed to name a Brain that does not exist.
        assertFalse(baseline().comparison());
        assertTrue(comparison().comparison());
        assertFalse(baseline().canonical().contains("brain_a"),
                "a baseline is not a comparison with a missing half: " + baseline().canonical());
        assertTrue(comparison().canonical().contains("brain_a"));
    }

    @Test
    @DisplayName("a Brain is not compared against itself, which would measure the Seed set")
    void a_brain_is_not_compared_against_itself() {
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0003", "x", brain("random"), brain("random"), "smoke", 1,
                        50, 50, 8, 25, 0, "a laptop", false));
    }

    // ------------------------------------------------------------------- the text and the hash

    @Test
    @DisplayName("the canonical text is one object, keys sorted, no whitespace, no floats")
    void the_text_is_canonical() {
        String text = comparison().canonical();

        assertTrue(text.startsWith("{") && text.endsWith("}"), text);
        assertFalse(text.contains(" \""), "no whitespace between members: " + text);
        assertFalse(text.contains("\n"), "one line");
        assertFalse(text.matches(".*:[0-9]+\\.[0-9].*"), "no floats anywhere a hash is taken: " + text);
        assertEquals(sorted(keysOf(text)), keysOf(text), "keys sorted: " + keysOf(text));
        // The rates are thousandths for that reason, and they read as such.
        assertTrue(text.contains("\"alpha_per_mil\":50") && text.contains("\"beta_per_mil\":50"), text);
    }

    @Test
    @DisplayName("the hash is over the canonical text and is the same on any machine")
    void the_hash_is_over_the_text() {
        assertTrue(comparison().hash().matches("[0-9a-f]{64}"), comparison().hash());
        assertEquals(comparison().hash(), comparison().hash(), "the same value twice");
        assertEquals(comparison().id() + "@" + comparison().hash().substring(0, 16),
                comparison().stamp());
        assertTrue(comparison().stamp().matches(Registration.ID_PATTERN + "@[0-9a-f]{16}"),
                "a Run log's header accepts exactly this: " + comparison().stamp());
    }

    @Test
    @DisplayName("every field moves the hash, so a Registration cannot be adjusted after the fact")
    void every_field_moves_the_hash() {
        // The point of the whole mechanism. A field that did not move the hash would be a field
        // that could be changed once the numbers were in, with every log still agreeing.
        Registration was = comparison();
        List<Registration> changed = List.of(
                new Registration("H-0009-elsewhere", was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel()),
                new Registration(was.hypothesis(), "something else entirely", was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel()),
                new Registration(was.hypothesis(), was.claim(), brain("other"), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(),
                        new Registration.Brain("greedy", "def5678", ZERO), was.seedSet(),
                        was.seedVersion(), was.alphaPerMil(), was.betaPerMil(), was.burnIn(),
                        was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(),
                        new Registration.Brain("greedy", "abc1234", ONE), was.seedSet(),
                        was.seedVersion(), was.alphaPerMil(), was.betaPerMil(), was.burnIn(),
                        was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        "smoke", was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), 2, was.alphaPerMil(), was.betaPerMil(), was.burnIn(),
                        was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), 10, was.betaPerMil(), was.burnIn(),
                        was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), 10, was.burnIn(),
                        was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(), 17,
                        was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), 501, was.budgetMs(), was.machineClass(), was.releaseLevel()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), 251, was.machineClass(), was.releaseLevel()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), "a server", was.releaseLevel()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), was.machineClass(), true));

        assertEquals(13, was.getClass().getRecordComponents().length,
                "a field was added: give it a row above, or it is a field the hash does not cover");
        Set<String> hashes = new LinkedHashSet<>();
        hashes.add(was.hash());
        for (Registration one : changed) {
            assertTrue(hashes.add(one.hash()),
                    "this change did not move the hash: " + one.canonical());
        }
        assertEquals(changed.size() + 1, hashes.size());
        assertNotEquals(baseline().hash(), comparison().hash());
    }

    // --------------------------------------------------------------------------- what it refuses

    @Test
    @DisplayName("a Registration that names a salt is refused, in whichever field it is written")
    void a_salt_is_refused_anywhere() {
        // A Registration is committed before the Runs and is public, so anything in it is something
        // the Brain's author has. ADR-0007 names the attack: the mixing function is published, so a
        // salt known in advance lets an author compute the game's coming draws as data.
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0004", "the salt is 0x5a17", null, brain("random"),
                        "smoke", 1, 50, 50, 8, 25, 0, "a laptop", false),
                "in the claim");
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0005", "x", null, brain("random"), "smoke", 1, 50, 50, 8,
                        25, 0, "the SALT machine", false),
                "in the machine class, in any case");
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0006", "the seedcode is AAA-AAA-AAB", null, brain("random"),
                        "smoke", 1, 50, 50, 8, 25, 0, "a laptop", false),
                "and a seed code, which names one Run of it");
    }

    @Test
    @DisplayName("a Registration refuses the values it could not mean")
    void the_values_are_held() {
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("nightly", "x", null, brain("random"), "smoke", 1, 50, 50, 8,
                        25, 0, "a laptop", false), "a hypothesis id has a shape");
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0007", "", null, brain("random"), "smoke", 1, 50, 50, 8,
                        25, 0, "a laptop", false), "a Registration says what it claims");
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0008", "x", null, brain("random"), "smoke", 1, 0, 50, 8,
                        25, 0, "a laptop", false), "a rate is a rate");
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0009", "x", null, brain("random"), "smoke", 1, 50, 50, 25,
                        25, 0, "a laptop", false), "a maximum is past the burn-in");
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0010", "x", null, brain("random"), "smoke", 1, 50, 50, 8,
                        25, 0, "", false), "a Registration says what it was measured on");
        assertThrows(IllegalArgumentException.class,
                () -> new Registration.Brain("Random", "abc1234", ZERO), "a Brain's name is lower case");
        assertThrows(IllegalArgumentException.class,
                () -> new Registration.Brain("random", "zzz", ZERO), "a commit is a git object name");
        assertThrows(IllegalArgumentException.class,
                () -> new Registration.Brain("random", "abc1234", "nope"), "a config hash is a SHA-256");
    }

    // ------------------------------------------------------------------------------------ helpers

    private static List<String> keysOf(String line) {
        List<String> keys = new ArrayList<>();
        int at = 1;
        while (at < line.length() - 1) {
            int quote = line.indexOf('"', at);
            int end = quote + 1;
            while (line.charAt(end) != '"') {
                end += line.charAt(end) == '\\' ? 2 : 1;
            }
            keys.add(line.substring(quote + 1, end));
            int depth = 0;
            int i = end + 2;
            while (i < line.length() - 1) {
                char c = line.charAt(i);
                if (c == '"') {
                    i++;
                    while (line.charAt(i) != '"') {
                        i += line.charAt(i) == '\\' ? 2 : 1;
                    }
                } else if (c == '{' || c == '[') {
                    depth++;
                } else if (c == '}' || c == ']') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    break;
                }
                i++;
            }
            at = i + 1;
        }
        return keys;
    }

    private static List<String> sorted(List<String> keys) {
        List<String> copy = new ArrayList<>(keys);
        copy.sort(null);
        return copy;
    }
}
