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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Committed before the Run", checked against git rather than asserted (story 3.5, FR-22).
 *
 * <p>Every case here builds a real repository and asks the real git. A test that stubbed the answer
 * would be testing that this code believes a stub — and the whole claim of the mechanism is that a
 * stranger can ask the same questions of the same repository and get the same answers. There is no
 * cheaper way to check a claim about time.
 */
class RegistrationsTest {

    private static final String ZERO = "0".repeat(64);

    private static Registration baseline(String id, String seedSet, boolean releaseLevel) {
        return new Registration(id, "the random Brain finishes every Run", null,
                new Registration.Brain("random", "abc1234", ZERO), seedSet, 1, 50, 50, 8, 25, 0,
                "a laptop", releaseLevel);
    }

    /** A repository with one committed Registration in it. */
    private static Path repository(Path root, Registration... registrations) throws IOException {
        run(root, "git", "init", "-q");
        run(root, "git", "config", "user.name", "a test");
        run(root, "git", "config", "user.email", "test@example.invalid");
        Files.createDirectories(root.resolve(Registrations.FOLDER));
        for (Registration registration : registrations) {
            write(root, registration);
        }
        run(root, "git", "add", "-A");
        run(root, "git", "commit", "-q", "-m", "the hypotheses, before the numbers");
        return root;
    }

    private static void write(Path root, Registration registration) throws IOException {
        Files.writeString(file(root, registration.id()), registration.canonical() + "\n",
                StandardCharsets.UTF_8);
    }

    private static Path file(Path root, String id) {
        return root.resolve(Registrations.FOLDER).resolve(id + ".json");
    }

    private static void run(Path root, String... command) {
        try {
            Process process = new ProcessBuilder(command).directory(root.toFile())
                    .redirectErrorStream(true).start();
            String said = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.waitFor(), String.join(" ", command) + ": " + said);
        } catch (IOException | InterruptedException failed) {
            throw new IllegalStateException(String.join(" ", command), failed);
        }
    }

    // ------------------------------------------------------------------------------ what it reads

    @Test
    @DisplayName("a committed Registration reads, and its stamp is over the committed bytes")
    void a_committed_registration_reads(@TempDir Path root) throws IOException {
        Registration was = baseline("H-0001-smoke", "smoke", false);
        repository(root, was);

        Registrations.Committed committed = Registrations.read(root, "H-0001-smoke");

        assertEquals(was, committed.registration());
        assertEquals(was.hash(), committed.hash());
        assertEquals(was.id() + "@" + was.hash().substring(0, 16), committed.stamp());
        assertTrue(committed.at().matches("[0-9a-f]{40}"),
                "the commit the hypothesis was fixed by: " + committed.at());
    }

    @Test
    @DisplayName("the hash is over what is committed, not over what is on disk")
    void an_edit_after_the_commit_changes_nothing(@TempDir Path root) throws IOException {
        // The case the whole mechanism exists for. Somebody runs the Runs, sees the numbers, and
        // adjusts the bounds. Git knows; and because the Rig hashes HEAD's bytes rather than the
        // working copy's, it refuses rather than quietly running under the new ones.
        repository(root, baseline("H-0002-edited", "smoke", false));
        Registration after = new Registration("H-0002-edited", "the random Brain finishes every Run",
                null, new Registration.Brain("random", "abc1234", ZERO), "smoke", 1, 500, 50, 8, 25,
                0, "a laptop", false);
        write(root, after);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Registrations.read(root, "H-0002-edited"));

        assertTrue(refused.getMessage().contains("differs from what is committed"), refused.getMessage());
        assertTrue(refused.getMessage().contains("H-0002-edited"), refused.getMessage());
    }

    @Test
    @DisplayName("a Registration that was never committed is refused, naming FR-22")
    void an_uncommitted_registration_is_refused(@TempDir Path root) throws IOException {
        repository(root, baseline("H-0003-committed", "smoke", false));
        write(root, baseline("H-0004-never", "smoke", false));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Registrations.read(root, "H-0004-never"));

        assertTrue(refused.getMessage().contains("not committed"), refused.getMessage());
        assertTrue(refused.getMessage().contains("FR-22"), refused.getMessage());
    }

    @Test
    @DisplayName("a Registration that does not exist is refused, saying which ones do")
    void a_missing_registration_says_what_exists(@TempDir Path root) throws IOException {
        repository(root, baseline("H-0005-here", "smoke", false));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Registrations.read(root, "H-9999-elsewhere"));

        assertTrue(refused.getMessage().contains("H-9999-elsewhere"), refused.getMessage());
        assertTrue(refused.getMessage().contains("H-0005-here"),
                "a refusal that lists what does exist: " + refused.getMessage());
        assertEquals(List.of("H-0005-here"), Registrations.ids(root));
    }

    @Test
    @DisplayName("a file that is not the canonical text of what it means is refused")
    void a_non_canonical_file_is_refused(@TempDir Path root) throws IOException {
        // The file is what a person edits; the canonical text is what is hashed. A file carrying a
        // member this reader ignores -- a note, a salt, a field from a later schema -- would hash
        // as though it were not there, and every log's stamp would be about a document nobody
        // wrote. So the reader writes back what it understood and compares.
        Registration was = baseline("H-0006-extra", "smoke", false);
        Files.createDirectories(root.resolve(Registrations.FOLDER));
        String text = was.canonical();
        Files.writeString(file(root, "H-0006-extra"),
                text.substring(0, text.length() - 1) + ",\"zzz_note\":\"and a salt of 7\"}\n",
                StandardCharsets.UTF_8);
        run(root, "git", "init", "-q");
        run(root, "git", "config", "user.name", "a test");
        run(root, "git", "config", "user.email", "test@example.invalid");
        run(root, "git", "add", "-A");
        run(root, "git", "commit", "-q", "-m", "a Registration with something extra in it");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Registrations.read(root, "H-0006-extra"));

        assertTrue(refused.getMessage().contains("canonical text"), refused.getMessage());
    }

    @Test
    @DisplayName("a file whose id is not its name is refused, because a hypothesis is named by its file")
    void a_misnamed_registration_is_refused(@TempDir Path root) throws IOException {
        Registration was = baseline("H-0007-inside", "smoke", false);
        Files.createDirectories(root.resolve(Registrations.FOLDER));
        Files.writeString(file(root, "H-0008-outside"), was.canonical() + "\n", StandardCharsets.UTF_8);
        run(root, "git", "init", "-q");
        run(root, "git", "config", "user.name", "a test");
        run(root, "git", "config", "user.email", "test@example.invalid");
        run(root, "git", "add", "-A");
        run(root, "git", "commit", "-q", "-m", "a Registration under the wrong name");

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Registrations.read(root, "H-0008-outside"));

        assertTrue(refused.getMessage().contains("named by the file"), refused.getMessage());
    }

    // ------------------------------------------------------------------------- what it refuses

    @Test
    @DisplayName("a Registration about another Seed set does not govern this invocation")
    void the_seed_set_must_match(@TempDir Path root) throws IOException {
        repository(root, baseline("H-0010-smoke", "smoke", false));
        Registrations.Committed committed = Registrations.read(root, "H-0010-smoke");

        Registrations.Refusal refusal = Registrations.refusal(committed, "standard", "abc1234",
                ZERO, new Ledger(root.resolve(Registrations.FOLDER)));

        assertNotNull(refusal);
        assertTrue(refusal.why().contains("names the Runs it is about"), refusal.why());
    }

    @Test
    @DisplayName("the holdout set is refused to a Registration that claims no release-level result")
    void the_holdout_needs_a_release_level_claim(@TempDir Path root) throws IOException {
        repository(root, baseline("H-0011-holdout", "holdout", false));
        Registrations.Committed committed = Registrations.read(root, "H-0011-holdout");

        Registrations.Refusal refusal = Registrations.refusal(committed, "holdout", "abc1234",
                ZERO, new Ledger(root.resolve(Registrations.FOLDER)));

        assertNotNull(refusal);
        assertTrue(refusal.why().contains("FR-20"), refusal.why());
        assertTrue(refusal.why().contains("release-level"), refusal.why());
    }

    @Test
    @DisplayName("the holdout set is allowed once per Brain version, and refused the second time")
    void the_holdout_budget_is_one_per_brain_version(@TempDir Path root) throws IOException {
        // The half of FR-20 story 3.1 deferred for want of somewhere durable to keep the count
        // (issue #116). The Registration says the claim is release-level; the ledger says whether
        // this Brain version has already spent its one use.
        repository(root, baseline("H-0012-release", "holdout", true));
        Registrations.Committed committed = Registrations.read(root, "H-0012-release");
        Ledger ledger = new Ledger(root.resolve(Registrations.FOLDER));

        assertNull(Registrations.refusal(committed, "holdout", "abc1234", ZERO, ledger),
                "the first use is what the set is for");

        ledger.record(committed, "random", "abc1234", ZERO, "holdout", Ledger.Outcome.FINISHED,
                true, "");

        Registrations.Refusal refusal =
                Registrations.refusal(committed, "holdout", "abc1234", ZERO, ledger);
        assertNotNull(refusal, "the second use is not");
        assertTrue(refusal.why().contains("already been used"), refusal.why());
        assertTrue(refusal.why().contains("H-0012-release"),
                "and it names the earlier use: " + refusal.why());

        // A different Brain version has its own allowance, which is what "per Brain version" means.
        assertNull(Registrations.refusal(committed, "holdout", "def5678", ZERO, ledger),
                "a different commit is a different Brain");
        assertNull(Registrations.refusal(committed, "holdout", "abc1234", "1".repeat(64), ledger),
                "and so is a different configuration");
    }

    // ------------------------------------------------------------- the one this repository holds

    @Test
    @DisplayName("the standing Registration in this repository reads, and is the one it claims to be")
    void the_standing_registration_is_real() {
        // It is committed here, so this asks the real git about the real file -- which is also a
        // drift check: if the record's canonical form ever changes, the committed file stops being
        // the canonical text of what it means and this fails.
        Registrations.Committed committed =
                Registrations.read(SeedSetsTest.ROOT, "H-0001-nightly-smoke");

        Registration registration = committed.registration();
        assertEquals("smoke", registration.seedSet());
        assertEquals(Brains.RANDOM, registration.brainB().name());
        assertEquals(Brains.configHash(Brains.RANDOM), registration.brainB().configHash());
        assertFalse(registration.comparison(), "the nightly job compares nothing; it checks a baseline");
        assertFalse(registration.releaseLevel(), "and it makes no release-level claim");
        assertTrue(committed.stamp().startsWith("H-0001-nightly-smoke@"), committed.stamp());
    }
}
