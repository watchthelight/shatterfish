package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Registration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ranked path, run (story 3.5, FR-22 and FR-20).
 *
 * <p>This file exists because there was nothing here. Every test the story shipped called
 * {@code Registrations.refusal} or {@code Ledger.record} directly, and the integration the story is
 * actually for — a Registration named on a command line, checked, stamped into every child's log
 * and counted in the ledger — was verified by hand once and never again. Three of the four reviews
 * said so, and the holdout exploit below is what that gap was hiding.
 *
 * <p>Every case builds a real repository and runs the real {@code Runner}. Two of them play Runs,
 * which is what a ranked invocation is; the rest refuse before anything is dispatched, which is the
 * property that makes the refusals worth having.
 */
class RunnerRegistrationTest {

    private static final String ZERO = "0".repeat(64);

    /** A repository holding a committed Registration and the seed sets the Rig reads. */
    private static Path repository(Path root, Registration... registrations) throws IOException {
        run(root, "git", "init", "-q");
        run(root, "git", "config", "user.name", "a test");
        run(root, "git", "config", "user.email", "test@example.invalid");
        // The seed sets, copied from this repository: the Rig reads them from `--root`.
        Path seeds = root.resolve("seeds");
        Files.createDirectories(seeds);
        try (var files = Files.list(SeedSetsTest.ROOT.resolve("seeds"))) {
            for (Path file : files.toList()) {
                Files.copy(file, seeds.resolve(file.getFileName()));
            }
        }
        // The files that decide what the random Brain does. `Brains.version` asks git when they
        // last changed, so a repository without them has no Brain version to speak of -- and the
        // release-level rule fails closed on that, which is correct and is why they are here.
        for (String source : List.of(
                "shatterfish/harness/src/main/java/org/shatterfish/harness/agent/RandomAgent.java",
                "shatterfish/rig/src/main/java/org/shatterfish/rig/Brains.java")) {
            Path file = root.resolve(source);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "// a stand-in for the Brain this test does not compile"
                    + System.lineSeparator(), StandardCharsets.UTF_8);
        }
        Files.createDirectories(root.resolve(Registrations.FOLDER));
        for (Registration registration : registrations) {
            Files.writeString(root.resolve(Registrations.FOLDER).resolve(registration.id() + ".json"),
                    registration.canonical() + System.lineSeparator(), StandardCharsets.UTF_8);
        }
        run(root, "git", "add", "-A");
        run(root, "git", "commit", "-q", "-m", "the hypotheses, before the numbers");
        return root;
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

    private static Registration baseline(String id, String seedSet, boolean releaseLevel,
                                         String brainCommit) {
        return new Registration(id, "the random Brain finishes every Run", null,
                new Registration.Brain("random", brainCommit, ZERO), seedSet, 1, 50, 50, 8, 25, 0,
                "a laptop", releaseLevel);
    }

    private static Map<String, String> arguments(Path root, Path out, String... extra) {
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put(Runner.BRAIN, Brains.RANDOM);
        arguments.put(Runner.SEEDS, SeedSets.SMOKE);
        arguments.put(Runner.OUT, out.toString());
        arguments.put(Runner.ROOT, root.toString());
        arguments.put(Runner.PARALLEL, "2");
        arguments.put(Runner.CAP, "40");
        for (int i = 0; i < extra.length; i += 2) {
            arguments.put(extra[i], extra[i + 1]);
        }
        return arguments;
    }

    private static String head(Path root) {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(root.toFile()).start();
            String said = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            return said.strip();
        } catch (IOException | InterruptedException failed) {
            throw new IllegalStateException("HEAD", failed);
        }
    }

    // ------------------------------------------------------------------------------- the ranked run

    @Test
    @DisplayName("a ranked invocation stamps every log, the summary and one ledger line")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void a_ranked_invocation_is_recorded(@TempDir Path root, @TempDir Path out) throws IOException {
        repository(root, baseline("H-0100-ranked", SeedSets.SMOKE, false, "abc1234"));
        Registrations.Committed committed = Registrations.read(root, "H-0100-ranked");

        Runner.run(arguments(root, out, Runner.REGISTRATION, "H-0100-ranked"));

        // Every child's log carries the stamp. Deleting the two lines that pass `--registration` to
        // the child leaves the summary correct and every header empty, and nothing else notices.
        List<Path> logs = Verify.logs(out);
        assertFalse(logs.isEmpty(), "the invocation played Runs");
        for (Path log : logs) {
            assertEquals(committed.stamp(),
                    LogHeader.string(Files.readAllLines(log, StandardCharsets.UTF_8).get(0),
                            "registration"),
                    "the header of " + log.getFileName() + " names the hypothesis it ran under");
        }
        String summary = Files.readString(out.resolve(RunIndex.SUMMARY), StandardCharsets.UTF_8).strip();
        assertEquals(committed.stamp(), LogHeader.string(summary, "registration"), summary);

        Ledger ledger = new Ledger(root.resolve(Registrations.FOLDER));
        assertEquals(1, ledger.uses("H-0100-ranked"));
        Ledger.Entry entry = ledger.entries().get(0);
        assertEquals(Ledger.Outcome.FINISHED, entry.outcome());
        assertEquals(committed.hash(), entry.hash(), "the ledger and the logs name one document");
        assertEquals(head(root), entry.at(), "and the commit that fixed it before the Runs");
        assertFalse(entry.holdout(), "the smoke set spends no allowance");
    }

    @Test
    @DisplayName("a gallery that fails does not cost a ranked invocation its summary or its ledger line")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void a_failing_gallery_is_reported_not_thrown(@TempDir Path root, @TempDir Path out)
            throws IOException {
        repository(root, baseline("H-0121-gallery", SeedSets.SMOKE, false, "abc1234"));
        java.util.function.Consumer<Path> was = Runner.gallery;
        // An Error, not an exception: the ledger path's own guard catches both, and so must this.
        Runner.gallery = folder -> {
            throw new AssertionError("the gallery broke");
        };
        try {
            Runner.run(arguments(root, out, Runner.REGISTRATION, "H-0121-gallery"));
        } finally {
            Runner.gallery = was;
        }

        assertTrue(Files.isRegularFile(out.resolve(RunIndex.SUMMARY)));
        assertFalse(Files.exists(out.resolve(Gallery.FILE)));
        Ledger.Entry entry = new Ledger(root.resolve(Registrations.FOLDER)).entries().get(0);
        assertEquals("H-0121-gallery", entry.registration());
        assertEquals(Ledger.Outcome.FINISHED, entry.outcome());
    }

    @Test
    @DisplayName("an unranked invocation runs, and says in the summary that it is not ranked")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void an_unranked_invocation_says_so(@TempDir Path root, @TempDir Path out) throws IOException {
        repository(root);

        Runner.run(arguments(root, out));

        String summary = Files.readString(out.resolve(RunIndex.SUMMARY), StandardCharsets.UTF_8).strip();
        assertEquals("", LogHeader.string(summary, "registration"), summary);
        for (Path log : Verify.logs(out)) {
            assertEquals("", LogHeader.string(
                    Files.readAllLines(log, StandardCharsets.UTF_8).get(0), "registration"));
        }
        assertFalse(Files.exists(root.resolve(Registrations.FOLDER).resolve(Ledger.FILE)),
                "nothing was recorded, because nothing was claimed");
    }

    // -------------------------------------------------------------------------- the holdout budget

    @Test
    @DisplayName("--commit cannot split the holdout budget in two")
    void the_commit_flag_cannot_spend_the_budget_twice(@TempDir Path root, @TempDir Path out)
            throws IOException {
        // The exploit two reviews found. The budget was checked against `commitOf(root)` and
        // recorded against the `--commit` value, so a run with any other sha was checked against
        // one identity and recorded under another, and the held-out set could be spent without
        // limit -- every ledger line reading as a lawful first use.
        //
        // The allowance is seeded here rather than earned by playing five hundred Runs: what is
        // being tested is which key the budget is counted on, and a test that spends five minutes
        // to reach that question is a test nobody will run.
        repository(root, baseline("H-0101-release", SeedSets.HOLDOUT, true, "0000000"));
        Registration release = baseline("H-0101-release", SeedSets.HOLDOUT, true,
                Brains.version(root, Brains.RANDOM).substring(0, 7));
        Files.writeString(root.resolve(Registrations.FOLDER).resolve("H-0101-release.json"),
                release.canonical() + System.lineSeparator(), StandardCharsets.UTF_8);
        run(root, "git", "add", "-A");
        run(root, "git", "commit", "-q", "-m", "the release hypothesis, naming the Brain");

        Registrations.Committed committed = Registrations.read(root, "H-0101-release");
        Ledger ledger = new Ledger(root.resolve(Registrations.FOLDER));
        ledger.record(committed, Brains.RANDOM, Brains.version(root, Brains.RANDOM), ZERO,
                SeedSets.HOLDOUT, Ledger.Outcome.CLAIMED, true, "the first use");

        // A different `--commit` used to produce a different budget key, so this was allowed.
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Runner.registration(
                        arguments(root, out, Runner.REGISTRATION, "H-0101-release",
                                Runner.SEEDS, SeedSets.HOLDOUT,
                                Runner.COMMIT, "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
                        root, SeedSets.HOLDOUT, Brains.RANDOM,
                        "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));

        assertTrue(refused.getMessage().contains("already been used"), refused.getMessage());
        assertTrue(refused.getMessage().contains("H-0101-release"),
                "and it names the earlier use: " + refused.getMessage());
    }

    @Test
    @DisplayName("a CLAIMED entry spends the allowance, because the set is spent by being read")
    void a_claimed_entry_spends_the_allowance(@TempDir Path root) throws IOException {
        // The set is spent by `publish` opening it, not by the Runs finishing: recording the use at
        // the end meant Ctrl-C left the set played and the ledger silent, and the next invocation's
        // check passed.
        repository(root, baseline("H-0105-release", SeedSets.HOLDOUT, true, "0000000"));
        Registrations.Committed committed = Registrations.read(root, "H-0105-release");
        Ledger ledger = new Ledger(root.resolve(Registrations.FOLDER));

        assertEquals(null, ledger.holdoutUse("abc1234", ZERO));
        ledger.record(committed, Brains.RANDOM, "abc1234", ZERO, SeedSets.HOLDOUT,
                Ledger.Outcome.CLAIMED, true, "about to read the set");

        assertTrue(ledger.holdoutUse("abc1234", ZERO).contains("H-0105-release"),
                "a claim is a use, whatever became of the Runs afterwards");
    }

    @Test
    @DisplayName("a refusal spends no holdout allowance, because a typo should not burn one")
    void a_refusal_spends_nothing(@TempDir Path root, @TempDir Path out) throws IOException {
        repository(root, baseline("H-0102-release", SeedSets.HOLDOUT, true, "abc1234"));

        // The brain name does not match, so this is refused -- and the refusal used to be recorded
        // with `holdout: true`, which permanently burned the Brain version's single allowance.
        assertThrows(IllegalArgumentException.class, () -> Runner.run(
                arguments(root, out, Runner.REGISTRATION, "H-0102-release",
                        Runner.SEEDS, SeedSets.HOLDOUT, Runner.BRAIN, Brains.RANDOM)));

        Ledger ledger = new Ledger(root.resolve(Registrations.FOLDER));
        assertEquals(1, ledger.uses("H-0102-release"), "the attempt is counted: " + ledger.entries());
        assertEquals(Ledger.Outcome.FORBIDDEN, ledger.entries().get(0).outcome());
        assertFalse(ledger.entries().get(0).holdout(), "and it spent nothing");
    }

    // -------------------------------------------------------------------------------- the refusals

    @Test
    @DisplayName("a held-out set is claimed before it is read, so killing the Rig still spends it")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void the_set_is_claimed_before_it_is_read(@TempDir Path root, @TempDir Path out)
            throws IOException {
        // `publish` opening the set is the moment it is spent: those triples have been seen,
        // whatever happens to the Runs afterwards. Recording the use at the end meant Ctrl-C left
        // the set played and the ledger silent, and the next invocation's check passed.
        repository(root, baseline("H-0106-release", SeedSets.HOLDOUT, true, "0000000"));
        Registration release = baseline("H-0106-release", SeedSets.HOLDOUT, true,
                Brains.version(root, Brains.RANDOM).substring(0, 7));
        Files.writeString(root.resolve(Registrations.FOLDER).resolve("H-0106-release.json"),
                release.canonical() + System.lineSeparator(), StandardCharsets.UTF_8);
        run(root, "git", "add", "-A");
        run(root, "git", "commit", "-q", "-m", "the release hypothesis, naming the Brain");

        // The real held-out set is five hundred triples -- `SeedSets.read` refuses a file whose
        // count is not the one the set is defined as, so it cannot be made small for a test. A cap
        // of ten turns and every core makes it about a minute, which is what this property costs.
        Runner.run(arguments(root, out, Runner.REGISTRATION, "H-0106-release",
                Runner.SEEDS, SeedSets.HOLDOUT, Runner.CAP, "10",
                Runner.PARALLEL, Integer.toString(Runner.defaultParallel())));

        List<Ledger.Entry> entries = new Ledger(root.resolve(Registrations.FOLDER)).entries();
        assertEquals(2, entries.size(), "the claim and the outcome: " + entries);
        assertEquals(Ledger.Outcome.CLAIMED, entries.get(0).outcome(),
                "the first line is written before the set is opened");
        assertTrue(entries.get(0).holdout(), "and it is what spends the allowance");
        assertEquals(Ledger.Outcome.FINISHED, entries.get(1).outcome());
        // And the reason `publish` demanded reaches the summary, which is what FR-20 asks for.
        String summary = Files.readString(out.resolve(RunIndex.SUMMARY), StandardCharsets.UTF_8).strip();
        assertTrue(LogHeader.string(summary, "reason").contains("H-0106-release"), summary);
    }

    @Test
    @DisplayName("a Registration fixing another Seed set version does not govern this one")
    void the_seed_set_version_is_enforced(@TempDir Path root, @TempDir Path out) throws IOException {
        // A Registration names a set *and a version* so that "smoke" means the same thing later.
        // Every set in the repository is version 1 and every Registration written so far fixes 1,
        // so nothing could tell whether the field was enforced or merely recorded.
        Registration later = new Registration("H-0107-later", "the random Brain finishes every Run",
                null, new Registration.Brain("random", "abc1234", ZERO), SeedSets.SMOKE, 2, 50, 50,
                8, 25, 0, "a laptop", false);
        repository(root, later);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Runner.run(arguments(root, out, Runner.REGISTRATION, "H-0107-later")));

        assertTrue(refused.getMessage().contains("version 2"), refused.getMessage());
        assertTrue(refused.getMessage().contains("names the Runs it is about"), refused.getMessage());
    }

    @Test
    @DisplayName("a Registration about another Brain, or another set, refuses before a Run starts")
    void a_mismatched_registration_costs_no_run(@TempDir Path root, @TempDir Path out)
            throws IOException {
        repository(root, new Registration("H-0103-other", "the greedy Brain wins", null,
                new Registration.Brain("greedy", "abc1234", ZERO), SeedSets.SMOKE, 1, 50, 50, 8, 25,
                0, "a laptop", false));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Runner.run(arguments(root, out, Runner.REGISTRATION, "H-0103-other")));

        assertTrue(refused.getMessage().contains("greedy"), refused.getMessage());
        assertFalse(Files.exists(out.resolve(RunIndex.RUNS)), "no Run was dispatched");
        // And it is counted, because it is an attempt under a committed hypothesis.
        Ledger ledger = new Ledger(root.resolve(Registrations.FOLDER));
        assertEquals(1, ledger.uses("H-0103-other"));
        assertEquals(Ledger.Outcome.FORBIDDEN, ledger.entries().get(0).outcome());
    }

    @Test
    @DisplayName("an unconfigured Brain does not stop an unranked development run")
    void an_unranked_run_asks_no_brain_for_a_configuration(@TempDir Path root, @TempDir Path out)
            throws IOException {
        // `Brains.configHash` refuses a Brain that has not stated its configuration, which is every
        // Brain except `random`. Asking for it on every invocation would have made the first real
        // Brain unrunnable by the Rig at all -- ranked or not -- and nothing would have noticed
        // until that Brain existed.
        repository(root);
        assertThrows(RuntimeException.class, () -> Brains.configHash("greedy"),
                "this is the method that would have been called");

        Runner.run(arguments(root, out));

        assertTrue(Files.exists(out.resolve(RunIndex.SUMMARY)), "the unranked run happened anyway");
    }

    @Test
    @DisplayName("a corrupt ledger stops a ranked invocation and leaves an unranked one alone")
    void a_corrupt_ledger_stops_only_what_depends_on_it(@TempDir Path root, @TempDir Path out)
            throws IOException {
        repository(root, baseline("H-0104-smoke", SeedSets.SMOKE, false, "abc1234"));
        Files.writeString(root.resolve(Registrations.FOLDER).resolve(Ledger.FILE),
                "{\"b\":1,\"a\":2}\n", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> Runner.run(
                arguments(root, out, Runner.REGISTRATION, "H-0104-smoke")),
                "a count nobody can read is not a count to rely on");

        Runner.run(arguments(root, root.resolve("unranked")));
        assertTrue(Files.exists(root.resolve("unranked").resolve(RunIndex.SUMMARY)),
                "and a development run that never looks at the ledger is unaffected");
    }

    /** A comparison of the random Brain at one commit against itself at another, on the smoke set. */
    private static Registration comparison(String id, String against) {
        return new Registration(id, "the random Brain is not better than itself", 
                new Registration.Brain(against, "aaaaaaa", ZERO),
                new Registration.Brain("random", "abc1234", ZERO), SeedSets.SMOKE, 1, 50, 50, 8, 25,
                0, "a laptop", false, 500, 550, 999);
    }

    @Test
    @DisplayName("a ranked comparison is tested under the Registration's own bounds, and a run of ties rejects")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void a_ranked_comparison_is_tested(@TempDir Path root, @TempDir Path out) throws IOException {
        // Two Registration Brains that differ only in their commit are a legal comparison, and
        // random against random is the same Run on both sides -- so every pair that reaches an ending
        // ties, and a run of ties is evidence for H0: the test rejects at the burn-in.
        repository(root, comparison("H-0110-self", "random"));

        Runner.run(arguments(root, out, Runner.REGISTRATION, "H-0110-self",
                Runner.AGAINST, Brains.RANDOM, Runner.CAP, "20000"));

        String json = Files.readString(out.resolve(Comparison.FILE), StandardCharsets.UTF_8).strip();
        assertEquals("true", LogHeader.value(json, "tested"), json);
        assertEquals("REJECT", LogHeader.string(json, "verdict"), json);
        assertEquals("8", LogHeader.value(json, "stopped_at"), "at the burn-in: " + json);
        assertEquals(String.valueOf(Math.round(Math.log(0.05 / 0.95) * 1_000_000)),
                LogHeader.value(json, "lower_micros"), "the bounds are the Registration's: " + json);
        assertEquals("true", LogHeader.value(json, "direction_check"),
                "the smoke set is a direction check and cannot accept: " + json);
        Ledger.Entry last = new Ledger(root.resolve(Registrations.FOLDER)).entries().get(0);
        assertTrue(last.note().startsWith("REJECT (direction check) after 8"),
                "the ledger says what came of it, so rejected attempts are countable: " + last.note());
        assertTrue(last.note().endsWith("against random"), last.note());
    }

    @Test
    @DisplayName("a baseline's Registration with --against, and a comparison's without it, are refused")
    void the_shape_of_the_registration_is_the_shape_of_the_invocation(@TempDir Path root, @TempDir Path out)
            throws IOException {
        repository(root, baseline("H-0111-base", SeedSets.SMOKE, false, "abc1234"),
                comparison("H-0112-pair", "random"));

        IllegalArgumentException asPair = assertThrows(IllegalArgumentException.class,
                () -> Runner.run(arguments(root, out, Runner.REGISTRATION, "H-0111-base",
                        Runner.AGAINST, Brains.RANDOM)));
        assertTrue(asPair.getMessage().contains("fixes a baseline"), asPair.getMessage());

        IllegalArgumentException alone = assertThrows(IllegalArgumentException.class,
                () -> Runner.run(arguments(root, out, Runner.REGISTRATION, "H-0112-pair")));
        assertTrue(alone.getMessage().contains("fixes a comparison"), alone.getMessage());
    }

    /** A comparison on `standard`, which may accept, with the given bounds and statistic. */
    private static Registration accepting(String id, int p1, int burnIn, int missing, int maximum,
                                          String statistic) {
        return new Registration(id, "the random Brain is not better than itself",
                new Registration.Brain("random", "aaaaaaa", ZERO),
                new Registration.Brain("random", "abc1234", ZERO), SeedSets.STANDARD, 1, 50, 50,
                burnIn, maximum, 0, "a laptop", false, 500, p1, missing, statistic);
    }

    @Test
    @DisplayName("a comparison that may accept runs at the calibrated bounds, every one of them, and nothing else")
    void an_accepting_comparison_is_calibrated(@TempDir Path root, @TempDir Path out)
            throws IOException {
        repository(root, accepting("H-0116-p1", 550, 20, 250, 500, "GSPRT"),
                accepting("H-0117-burn", 600, 10, 250, 500, "GSPRT"),
                accepting("H-0118-cap", 600, 20, 999, 500, "GSPRT"),
                accepting("H-0119-max", 600, 20, 250, 400, "GSPRT"));

        for (String id : List.of("H-0116-p1", "H-0117-burn", "H-0118-cap", "H-0119-max")) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> Runner.run(arguments(root, out, Runner.REGISTRATION, id,
                            Runner.AGAINST, Brains.RANDOM, Runner.SEEDS, SeedSets.STANDARD)), id);
            assertTrue(refused.getMessage().contains("other than the calibrated ones"),
                    id + ": " + refused.getMessage());
        }
        // Counted: a refused attempt is an attempt the ledger has to be able to show.
        List<Ledger.Entry> entries = new Ledger(root.resolve(Registrations.FOLDER)).entries();
        assertEquals(4, entries.stream().filter(e -> e.outcome() == Ledger.Outcome.FORBIDDEN).count());
    }

    @Test
    @DisplayName("a comparison registered for a statistic other than the gate is refused")
    void the_statistic_is_the_registrations(@TempDir Path root, @TempDir Path out) throws IOException {
        repository(root, accepting("H-0120-e", 600, 20, 250, 500, "EPROCESS"));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Runner.run(arguments(root, out, Runner.REGISTRATION, "H-0120-e",
                        Runner.AGAINST, Brains.RANDOM, Runner.SEEDS, SeedSets.STANDARD)));

        assertTrue(refused.getMessage().contains("bounds for the EPROCESS and the Rig's gate is the GSPRT"),
                refused.getMessage());
    }

    @Test
    @DisplayName("a comparison against a Brain the Registration does not name is refused")
    void the_baseline_is_the_registrations(@TempDir Path root, @TempDir Path out) throws IOException {
        repository(root, comparison("H-0113-greedy", "greedy"));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Runner.run(arguments(root, out, Runner.REGISTRATION, "H-0113-greedy",
                        Runner.AGAINST, Brains.RANDOM)));

        assertTrue(refused.getMessage().contains("compares against the Brain greedy"), refused.getMessage());
    }

    @Test
    @DisplayName("a comparison against the right Brain configured otherwise is refused")
    void the_baseline_configuration_is_the_registrations(@TempDir Path root, @TempDir Path out)
            throws IOException {
        repository(root, new Registration("H-0114-config", "the random Brain is not better than itself",
                new Registration.Brain("random", "aaaaaaa", "1".repeat(64)),
                new Registration.Brain("random", "abc1234", ZERO), SeedSets.SMOKE, 1, 50, 50, 8, 25,
                0, "a laptop", false, 500, 550, 999));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Runner.run(arguments(root, out, Runner.REGISTRATION, "H-0114-config",
                        Runner.AGAINST, Brains.RANDOM)));

        assertTrue(refused.getMessage().contains("configured as " + "1".repeat(64)), refused.getMessage());
    }

    @Test
    @DisplayName("a release-level comparison against a baseline version this checkout does not hold is refused")
    void the_baseline_version_is_the_registrations(@TempDir Path root, @TempDir Path out)
            throws IOException {
        // The baseline is checked before the candidate, so the candidate's commit need not match
        // anything for this refusal to be the one that is raised.
        repository(root, new Registration("H-0115-release", "the random Brain is not better than itself",
                new Registration.Brain("random", "aaaaaaa", ZERO),
                new Registration.Brain("random", "abc1234", ZERO), SeedSets.SMOKE, 1, 50, 50, 8, 25,
                0, "a laptop", true, 500, 550, 999));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Runner.run(arguments(root, out, Runner.REGISTRATION, "H-0115-release",
                        Runner.AGAINST, Brains.RANDOM)));

        assertTrue(refused.getMessage().contains("claims a release-level result against random at aaaaaaa"),
                refused.getMessage());
    }
}
