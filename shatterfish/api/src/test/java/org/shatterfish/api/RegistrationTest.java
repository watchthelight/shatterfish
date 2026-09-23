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
                "a laptop", false, 500, 550, 50);
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

    /**
     * The canonical text of {@link #baseline()}, written out.
     *
     * <p>Pinned rather than derived, because a test that builds its expectation with the method it
     * is testing agrees with whatever that method currently does. Rename a key here and the hash
     * every Run log stamps changes meaning; this is the line that says so out loud.
     */
    private static final String BASELINE_TEXT =
            "{\"alpha_per_mil\":50,\"beta_per_mil\":50,\"brain_b\":{\"commit\":\"abc1234\","
                    + "\"config\":\"" + ZERO + "\",\"name\":\"random\"},\"budget_ms\":0,"
                    + "\"burn_in\":8,\"claim\":\"the random Brain finishes every Run\","
                    + "\"hypothesis\":\"H-0001-a-baseline\",\"machine_class\":\"a laptop\","
                    + "\"maximum\":25,\"release_level\":false,\"seed_set\":\"smoke\","
                    + "\"seed_version\":1}";

    @Test
    @DisplayName("the canonical text is the one this repository committed to, key for key")
    void the_text_is_the_pinned_one() {
        assertEquals(BASELINE_TEXT, baseline().canonical(),
                "the shape of a Registration is what every published hash is over; changing it is a"
                        + " decision, and this is where it is made");
    }

    @Test
    @DisplayName("a baseline and the comparison it came from differ in the text and the hash")
    void a_baseline_is_not_a_comparison() {
        // The row `every_field_moves_the_hash` could not have: everything identical except the
        // presence of the baseline Brain. The assertion that used to stand in for it compared two
        // Registrations differing in seven fields.
        Registration was = comparison();
        Registration without = new Registration(was.hypothesis(), was.claim(), null, was.brainB(),
                was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(), was.burnIn(),
                was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel(), 0, 0, 0);

        assertNotEquals(was.hash(), without.hash(),
                "a comparison and the baseline of the same Brain are different hypotheses");
        assertTrue(was.canonical().contains("brain_a"));
        assertFalse(without.canonical().contains("brain_a"));
    }

    @Test
    @DisplayName("a comparison states its hypotheses and a baseline states none")
    void the_hypotheses_belong_to_a_comparison() {
        assertThrows(IllegalArgumentException.class, () -> new Registration("H-0050", "x",
                brain("random"), brain("greedy"), "smoke", 1, 50, 50, 8, 25, 0, "a laptop", false),
                "a comparison with no hypotheses cannot be tested");
        assertThrows(IllegalArgumentException.class, () -> new Registration("H-0051", "x",
                brain("random"), brain("greedy"), "smoke", 1, 50, 50, 8, 25, 0, "a laptop", false,
                550, 500, 50), "H1 is above H0");
        assertThrows(IllegalArgumentException.class, () -> new Registration("H-0052", "x",
                brain("random"), brain("greedy"), "smoke", 1, 50, 50, 8, 25, 0, "a laptop", false,
                500, 1000, 50), "a mean below one");
        assertThrows(IllegalArgumentException.class, () -> new Registration("H-0053", "x", null,
                brain("random"), "smoke", 1, 50, 50, 8, 25, 0, "a laptop", false, 500, 550, 50),
                "a baseline has nothing to test between");
        assertThrows(IllegalArgumentException.class, () -> new Registration("H-0054", "x",
                brain("random"), brain("greedy"), "smoke", 1, 50, 50, 8, 25, 0, "a laptop", false,
                400, 480, 50), "accepting has to mean better, so H1 is above one half");
        assertThrows(IllegalArgumentException.class, () -> new Registration("H-0055", "x",
                brain("random"), brain("greedy"), "smoke", 1, 600, 400, 8, 25, 0, "a laptop", false,
                500, 550, 50), "error rates together below one, or the bounds cross");
        assertThrows(IllegalArgumentException.class, () -> new Registration("H-0056", "x", null,
                brain("random"), "smoke", 1, 50, 50, 8, 25, 0, "a laptop", false, 0, 0, 50),
                "a baseline states no missing fraction either");
        // Absent from a baseline's text, so a baseline written before they existed keeps its hash.
        assertFalse(baseline().canonical().contains("p0_per_mil"));
        assertTrue(comparison().canonical().contains("\"p0_per_mil\":500,\"p1_per_mil\":550"));
    }

    @Test
    @DisplayName("a Brain has three components, and all three are in the hash")
    void a_brain_is_wholly_hashed() {
        // The guard `Registration` has and `Brain` did not. A fourth component -- a weights hash,
        // say -- would be silently outside everything the chain and the ledger cover.
        assertEquals(3, Registration.Brain.class.getRecordComponents().length,
                "a component was added to Brain: give it a row below, or it is outside the hash");
        Registration was = comparison();
        assertNotEquals(was.hash(), new Registration(was.hypothesis(), was.claim(),
                new Registration.Brain("elsewhere", "abc1234", ZERO), was.brainB(), was.seedSet(),
                was.seedVersion(), was.alphaPerMil(), was.betaPerMil(), was.burnIn(), was.maximum(),
                was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()).hash(), "the name");
        assertNotEquals(was.hash(), new Registration(was.hypothesis(), was.claim(),
                new Registration.Brain("random", "def5678", ZERO), was.brainB(), was.seedSet(),
                was.seedVersion(), was.alphaPerMil(), was.betaPerMil(), was.burnIn(), was.maximum(),
                was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()).hash(), "the commit");
        assertNotEquals(was.hash(), new Registration(was.hypothesis(), was.claim(),
                new Registration.Brain("random", "abc1234", ONE), was.brainB(), was.seedSet(),
                was.seedVersion(), was.alphaPerMil(), was.betaPerMil(), was.burnIn(), was.maximum(),
                was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()).hash(), "the configuration");
    }

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
                        was.burnIn(), was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), "something else entirely", was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), was.claim(), brain("other"), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(),
                        new Registration.Brain("greedy", "def5678", ZERO), was.seedSet(),
                        was.seedVersion(), was.alphaPerMil(), was.betaPerMil(), was.burnIn(),
                        was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(),
                        new Registration.Brain("greedy", "abc1234", ONE), was.seedSet(),
                        was.seedVersion(), was.alphaPerMil(), was.betaPerMil(), was.burnIn(),
                        was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        "smoke", was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), 2, was.alphaPerMil(), was.betaPerMil(), was.burnIn(),
                        was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), 10, was.betaPerMil(), was.burnIn(),
                        was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), 10, was.burnIn(),
                        was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(), 17,
                        was.maximum(), was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), 501, was.budgetMs(), was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), 251, was.machineClass(), was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), "a server", was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), was.machineClass(), true, was.p0PerMil(), was.p1PerMil(), was.missingPerMil()),
                // The two hypotheses, which story 3.5 left out and story 3.6 needs: a test whose H0
                // could be moved after the numbers are in is a test that accepts whatever it likes.
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), was.machineClass(),
                        was.releaseLevel(), 490, was.p1PerMil(), was.missingPerMil()),
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), was.machineClass(),
                        was.releaseLevel(), was.p0PerMil(), 560, was.missingPerMil()),
                // The missing fraction: a cap that could be raised after the fact is a cap that
                // lets a Brain crash its way to a tie on the seeds it would lose.
                new Registration(was.hypothesis(), was.claim(), was.brainA(), was.brainB(),
                        was.seedSet(), was.seedVersion(), was.alphaPerMil(), was.betaPerMil(),
                        was.burnIn(), was.maximum(), was.budgetMs(), was.machineClass(),
                        was.releaseLevel(), was.p0PerMil(), was.p1PerMil(), 999));

        assertEquals(16, was.getClass().getRecordComponents().length,
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
    @DisplayName("a value shaped like a salt is refused, whatever it is called")
    void a_salt_shaped_value_is_refused() {
        // The guard that matters. A salt is sixteen hex digits and contains neither of the words a
        // blacklist holds, so the first draft of this class could not catch the thing it was named
        // for -- which is what the fairness review said in as many words.
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0020", "the stream begins 5a17c9f3e1b20d48", null,
                        brain("random"), "smoke", 1, 50, 50, 8, 25, 0, "a laptop", false),
                "sixteen hex digits in the claim");
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0021", "x", null, brain("random"), "smoke", 1, 50, 50, 8,
                        25, 0, "rig-deadbeef01", false),
                "and in the machine class");

        // And a word that merely contains one of the banned ones is not a refusal. The first draft
        // used `contains`, which would have turned a real machine class into an argument.
        assertEquals("basalt-ci", new Registration("H-0022", "the Brain finishes every Run", null,
                brain("random"), "smoke", 1, 50, 50, 8, 25, 0, "basalt-ci", false).machineClass());
    }

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
        // The baseline Brain's name, which had no test at all: deleting the branch that checks it
        // passed the whole suite.
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0023", "x", new Registration.Brain("salt", "abc1234", ZERO),
                        brain("random"), "smoke", 1, 50, 50, 8, 25, 0, "a laptop", false),
                "in the baseline Brain's name");
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
                () -> new Registration("H-0011", "   ", null, brain("random"), "smoke", 1, 50, 50, 8,
                        25, 0, "a laptop", false), "a blank claim is not a claim");
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0012", "x".repeat(2001), null, brain("random"), "smoke", 1,
                        50, 50, 8, 25, 0, "a laptop", false),
                "a claim is a sentence, and it is concatenated into a published reason");
        // The rates at their limits, which nothing exercised: only 0 was refused.
        assertEquals(998, new Registration("H-0013", "x", null, brain("random"), "smoke", 1, 998, 1,
                8, 25, 0, "a laptop", false).alphaPerMil());
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0015", "x", null, brain("random"), "smoke", 1, 999, 1, 8,
                        25, 0, "a laptop", false), "together at one, the bounds cross");
        assertThrows(IllegalArgumentException.class,
                () -> new Registration("H-0014", "x", null, brain("random"), "smoke", 1, 1000, 50, 8,
                        25, 0, "a laptop", false), "a rate below one");
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
