package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FR-20 is written in one place (story 3.5).
 *
 * <p>The story's own acceptance criterion asked for this test in so many words — "this is the single
 * place the rule lives (… and a test that no other production class names `holdout`)" — and it was
 * not written. Two reviews found the same thing: the criterion was stated, the test was skipped, and
 * in the meantime the Runner grew four more sites spelling the comparison itself.
 *
 * <p>That is not pedantry about a constant. The rule has two halves that have to agree — the set may
 * be read only for a release-level claim, and only once per Brain version — and a second spelling is
 * a second thing to keep true. Flip one of the Runner's comparisons to {@code false} and every
 * held-out use is written to the ledger as an ordinary one, which makes the budget unlimited while
 * both halves of the rule still look present.
 *
 * <p>Three classes may name it: the one that owns the set, the one that owns the hypothesis
 * permitting it, and the one that writes the record of a use. Everything else asks them.
 */
class HoldoutRuleTest {

    /**
     * The three classes that may say the word, and why each of them may.
     *
     * <p>{@link SeedSets} owns the set and refuses it at the door story 3.1 built.
     * {@link Registrations} owns the hypothesis that is the one way past that door.
     * {@link Ledger} owns the <em>record</em> of a use, and names a field of its own format — which
     * is not a second copy of the rule, because it decides nothing: it writes down the answer
     * somebody else reached. The first draft of this list had two names and this test found the
     * third, which is the test doing its job rather than a reason to widen it quietly.
     */
    private static final List<String> OWNERS =
            List.of("SeedSets.java", "Registrations.java", "Ledger.java");

    @Test
    @DisplayName("no other production class names the held-out set")
    void the_rule_has_one_home() throws IOException {
        List<String> strangers = new ArrayList<>();
        for (Path file : production()) {
            String name = file.getFileName().toString();
            if (OWNERS.contains(name)) {
                continue;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            // The word in code, not in prose. A comment explaining why a rule lives elsewhere is
            // exactly what this test wants people to write.
            for (String line : text.split("\n")) {
                String code = line.strip();
                if (code.startsWith("*") || code.startsWith("//")) {
                    continue;
                }
                if (code.contains("\"holdout\"") || code.contains("HOLDOUT")) {
                    strangers.add(name + ": " + code.strip());
                }
            }
        }
        assertEquals(List.of(), strangers,
                "FR-20 is written in " + OWNERS + " and asked of them everywhere else; these name"
                        + " it themselves, so the rule now has more than one place to be wrong");
    }

    @Test
    @DisplayName("the two owners agree about what the set is called")
    void the_owners_agree() {
        // One constant, taken from the other, rather than two spellings that happen to match.
        assertEquals(SeedSets.HOLDOUT, Registrations.HOLDOUT);
        assertTrue(Registrations.spendsTheBudget(SeedSets.HOLDOUT));
        assertTrue(!Registrations.spendsTheBudget(SeedSets.SMOKE)
                && !Registrations.spendsTheBudget(SeedSets.STANDARD));
    }

    private static List<Path> production() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String module : List.of("api", "harness", "brain", "rig", "codex")) {
            Path main = SeedSetsTest.ROOT.resolve("shatterfish").resolve(module)
                    .resolve("src/main/java");
            if (!Files.isDirectory(main)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(main)) {
                walk.filter(Files::isRegularFile)
                        .filter(file -> file.getFileName().toString().endsWith(".java"))
                        .forEach(files::add);
            }
        }
        assertTrue(files.size() > 50, "this walked the production sources: " + files.size());
        return files;
    }
}
