package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The nightly smoke run's record, its page, and the workflow that runs it (story 3.11).
 *
 * <p>The workflow itself runs on GitHub at night, where a mistake is invisible until a night nobody
 * is watching; so its text is read here and held against the Rig's own flags and against the
 * rules ADR-0002 and the story set: one branch, never main, a direction check, a red job on a
 * failed night, and no network beyond GitHub.
 */
class NightlyTest {

    private static final String STAMP = "H-0001-nightly-smoke@a4d7fe89a612e89b";

    /** A folder as the Rig writes one: a summary with these numbers, and an index of these causes. */
    private static Path folder(Path out, String registration, String set, String brain, int started,
                               int finished, int incomplete, String... causes) throws IOException {
        Files.createDirectories(out);
        Files.writeString(out.resolve(RunIndex.SUMMARY), "{\"brain\":\"" + brain + "\",\"ms\":14090,"
                + "\"processes\":4,\"reason\":\"\",\"registration\":\"" + registration + "\","
                + "\"runsFinished\":" + finished + ",\"runsIncomplete\":" + incomplete + ","
                + "\"runsStarted\":" + started + ",\"runsUnaccounted\":0,\"seedSet\":\"" + set + "\"}\n",
                StandardCharsets.UTF_8);
        StringBuilder index = new StringBuilder();
        for (String cause : causes) {
            index.append("{\"cause\":\"").append(cause).append("\",\"state\":\"FINISHED\"}\n");
        }
        Files.writeString(out.resolve(RunIndex.RUNS), index.toString(), StandardCharsets.UTF_8);
        return out;
    }

    /** Every Run finished and none incomplete, but two the index cannot account for. */
    private static Path unaccounted(Path out) throws IOException {
        folder(out, STAMP, "smoke", "random", 25, 25, 0);
        Path summary = out.resolve(RunIndex.SUMMARY);
        Files.writeString(summary, Files.readString(summary, StandardCharsets.UTF_8)
                .replace("\"runsUnaccounted\":0", "\"runsUnaccounted\":2"), StandardCharsets.UTF_8);
        return out;
    }

    /** A summary with no count of unaccounted Runs, which is not a count of zero. */
    private static Path countless(Path out) throws IOException {
        folder(out, STAMP, "smoke", "random", 25, 25, 0);
        Path summary = out.resolve(RunIndex.SUMMARY);
        Files.writeString(summary, Files.readString(summary, StandardCharsets.UTF_8)
                .replace(",\"runsUnaccounted\":0", ""), StandardCharsets.UTF_8);
        return out;
    }

    /** A summary the Rig only half wrote. */
    private static Path garbled(Path out) throws IOException {
        Files.createDirectories(out);
        Files.writeString(out.resolve(RunIndex.SUMMARY), "{\"runsStarted\":\"twenty-five\"}\n",
                StandardCharsets.UTF_8);
        return out;
    }

    private static String[] causes(int deaths, int unknown) {
        List<String> all = new ArrayList<>();
        for (int i = 0; i < deaths; i++) {
            all.add("DEATH");
        }
        for (int i = 0; i < unknown; i++) {
            all.add("UNKNOWN_WINDOW");
        }
        return all.toArray(String[]::new);
    }

    // ----------------------------------------------------------------------------- a night

    @Test
    @DisplayName("a night under H-0001 whose every Run finished passes, with its endings counted")
    void a_good_night(@TempDir Path out) throws IOException {
        folder(out, STAMP, "smoke", "random", 25, 25, 0, causes(23, 2));

        Nightly.Night night = Nightly.night(out, 25, "2026-09-23", "abc1234", "");

        assertTrue(night.pass(), night.why());
        assertEquals("DEATH=23 UNKNOWN_WINDOW=2", night.causes());
        assertEquals("", night.why());
        assertTrue(Nightly.status(night).startsWith("PASS -- nightly smoke 2026-09-23 (a direction check"),
                Nightly.status(night));
        assertTrue(Nightly.status(night).contains("never an acceptance"));
    }

    @Test
    @DisplayName("every way a night can go wrong fails it, and says why in its own words")
    void bad_nights(@TempDir Path root) throws IOException {
        record Case(Path folder, String says) {
        }
        List<Case> cases = List.of(
                new Case(folder(root.resolve("unranked"), "", "smoke", "random", 25, 25, 0),
                        "not ranked under H-0001-nightly-smoke"),
                new Case(folder(root.resolve("other"), "H-0002-random-standard@0123456789abcdef", "smoke",
                        "random", 25, 25, 0), "not ranked under H-0001-nightly-smoke"),
                new Case(folder(root.resolve("prefix"), "H-0001-nightly-smokeX@0123456789abcdef", "smoke",
                        "random", 25, 25, 0), "not ranked under H-0001-nightly-smoke"),
                new Case(folder(root.resolve("set"), STAMP, "standard", "random", 25, 25, 0),
                        "not random on smoke"),
                new Case(folder(root.resolve("brain"), STAMP, "smoke", "greedy", 25, 25, 0),
                        "not random on smoke"),
                new Case(folder(root.resolve("short"), STAMP, "smoke", "random", 20, 20, 0),
                        "20 Runs started of the 25"),
                new Case(folder(root.resolve("incomplete"), STAMP, "smoke", "random", 25, 22, 3),
                        "22 of 25 Runs finished (3 incomplete"),
                new Case(root.resolve("absent"), "the Rig wrote no summary"),
                new Case(unaccounted(root.resolve("unaccounted")), "0 incomplete, 2 unaccounted"),
                new Case(garbled(root.resolve("garbled")), "the Rig's summary could not be read"),
                // A summary that contradicts itself -- every Run finished and one incomplete -- is
                // not a pass: each count is checked on its own, not only through their sum.
                new Case(folder(root.resolve("contradictory"), STAMP, "smoke", "random", 25, 25, 1),
                        "25 of 25 Runs finished (1 incomplete"),
                new Case(countless(root.resolve("countless")), "it states no runsUnaccounted"));
        for (Case c : cases) {
            Nightly.Night night = Nightly.night(c.folder(), 25, "2026-09-23", "abc1234", "");
            assertFalse(night.pass(), c.folder().toString());
            assertTrue(night.why().contains(c.says()), c.folder() + ": " + night.why());
            assertTrue(Nightly.status(night).startsWith("FAIL -- "), Nightly.status(night));
            assertTrue(Nightly.status(night).endsWith(night.why()), Nightly.status(night));
        }
    }

    @Test
    @DisplayName("a night's line reads back as the same night, and a line missing a key is refused")
    void the_history_line(@TempDir Path out) throws IOException {
        folder(out, STAMP, "smoke", "random", 25, 25, 0, causes(25, 0));
        Nightly.Night night = Nightly.night(out, 25, "2026-09-23", "abc1234",
                "https://github.com/watchthelight/shatterfish/actions/runs/1");
        Path history = out.resolve("history.jsonl");
        Files.writeString(history, night.line() + "\n" + night.line() + "\n", StandardCharsets.UTF_8);

        assertEquals(List.of(night, night), Nightly.history(history));
        assertEquals(List.of(), Nightly.history(out.resolve("absent.jsonl")));
        // A key that still sorts last, so the reader accepts the shape and the key check is what refuses.
        String keyless = night.line().replace("\"why\":\"\"", "\"zwhy\":\"\"");
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Nightly.Night.of(keyless));
        assertTrue(refused.getMessage().contains("without \"why\""), refused.getMessage());
    }

    @Test
    @DisplayName("a summary written by the Rig's own writer passes, and a play step that failed fails it anyway")
    void the_rigs_own_summary(@TempDir Path out) throws IOException {
        RunIndex index = new RunIndex(out);
        for (int i = 0; i < 25; i++) {
            String id = "run-" + i;
            index.started(new RunIndex.Entry(id, id + ".jsonl", RunIndex.State.STARTED, "", i,
                    "WARRIOR", 0, i, "", 0, ""));
            index.ended(id, RunIndex.State.FINISHED, "c" + i, i < 23 ? "DEATH" : "UNKNOWN_WINDOW", 10, "");
        }
        index.summary(Brains.RANDOM, SeedSets.SMOKE, 4, 20_000, 14_090, 2_000, STAMP, "");

        Nightly.Night night = Nightly.night(out, 25, "2026-09-23", "abc1234", "");
        assertTrue(night.pass(), night.why());
        assertEquals("DEATH=23 UNKNOWN_WINDOW=2", night.causes());

        Nightly.Night failed = Nightly.night(out, 25, "2026-09-23", "abc1234", "", "failure");
        assertFalse(failed.pass());
        assertTrue(failed.why().contains("the play step ended failure"), failed.why());
    }

    @Test
    @DisplayName("the task records a night end to end: its line, its status, and an exit code that says which")
    void the_task_end_to_end(@TempDir Path out) throws IOException {
        // Run against the repository, into a folder beside it: the task resolves <out> under the
        // root, and an absolute <out> resolves to itself.
        folder(out.resolve("good"), STAMP, "smoke", "random", 25, 25, 0, causes(25, 0));
        folder(out.resolve("bad"), STAMP, "smoke", "random", 25, 22, 3, causes(22, 0));
        String root = SeedSetsTest.ROOT.toString();

        assertEquals(0, Nightly.run(new String[] {root, "night", out.resolve("good").toString(),
                "2026-09-23", "abc1234", "success", "https://github.com/x/y/actions/runs/1/attempts/2"}));
        Nightly.Night night = Nightly.Night.of(Files.readString(out.resolve("good/night.jsonl"),
                StandardCharsets.UTF_8).strip());
        assertTrue(night.pass());
        assertEquals("https://github.com/x/y/actions/runs/1/attempts/2", night.run(), "the attempt is kept");
        assertTrue(Files.readString(out.resolve("good/status.md"), StandardCharsets.UTF_8).startsWith("PASS -- "));

        assertEquals(1, Nightly.run(new String[] {root, "night", out.resolve("bad").toString(),
                "2026-09-23", "abc1234"}), "a failed night answers 1");
        assertTrue(Files.readString(out.resolve("bad/status.md"), StandardCharsets.UTF_8).startsWith("FAIL -- "));
        assertEquals(1, Nightly.run(new String[] {root, "night", out.resolve("good").toString(),
                "2026-09-23", "abc1234", "cancelled"}), "and so does a good folder whose play step was cancelled");
    }

    @Test
    @DisplayName("the task refuses any shape but page, or night with its four to six arguments")
    void the_task_refuses_other_shapes() {
        String root = SeedSetsTest.ROOT.toString();
        for (String[] args : List.of(new String[] {root}, new String[] {root, "pages"},
                new String[] {root, "night", "out", "2026-09-23"},
                new String[] {root, "day", "out", "2026-09-23", "abc1234"},
                new String[] {root, "night", "out", "2026-09-23", "abc1234", "success", "url", "more"})) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> Nightly.run(args), String.join(" ", args));
            assertTrue(refused.getMessage().startsWith("usage: Nightly"), refused.getMessage());
        }
    }

    @Test
    @DisplayName("an exception's message reaches the status and the table on one line, with no markdown it did not mean")
    void messages_are_made_safe() {
        assertEquals("java.lang.IllegalStateException", Nightly.message(new IllegalStateException()));
        assertEquals("said", Nightly.message(new IllegalStateException("said")));
        assertEquals("a / b c 'd'", Nightly.oneLine("a\r\nb\nc `d`"));
        assertEquals("x \\| y z", Nightly.cell("x | y\nz"));
        Nightly.Night bad = new Nightly.Night("2026-09-23", "abc", "", 0, 0, 0, 0, 0, "", false,
                "line one\nline `two` | three", "");
        String status = Nightly.status(bad);
        assertFalse(status.contains("\n") || status.contains("`"), status);
        String page = Nightly.page(List.of(bad));
        assertTrue(page.contains("| line one line 'two' \\| three |"), page);
    }

    // ------------------------------------------------------------------------------ the page

    @Test
    @DisplayName("the page puts the latest night first, a failure's reason in the table, and calls every night a direction check")
    void the_page() {
        Nightly.Night good = new Nightly.Night("2026-09-22", "0123456789abcdef", STAMP, 25, 25, 0, 0,
                14_090, "DEATH=25", true, "", "https://github.com/x/y/actions/runs/1");
        Nightly.Night bad = new Nightly.Night("2026-09-23", "fedcba9876543210", STAMP, 25, 22, 3, 0,
                15_000, "DEATH=22", false, "22 of 25 Runs finished (3 incomplete, 0 unaccounted)", "");

        String page = Nightly.page(List.of(good, bad));

        assertTrue(page.contains("**Latest:** FAIL -- nightly smoke 2026-09-23"), page);
        assertTrue(page.indexOf("| 2026-09-23 |") < page.indexOf("| 2026-09-22 |"), "newest first");
        assertTrue(page.contains("| **FAIL** | 22 of 25 Runs finished (3 incomplete, 0 unaccounted) |"), page);
        assertTrue(page.contains("never an acceptance"));
        assertTrue(page.contains("`fedcba987`"), "the commit, shortened");
        assertTrue(page.contains("[log](https://github.com/x/y/actions/runs/1)"));
        assertTrue(page.contains(Nightly.COMMAND));
        assertTrue(Nightly.page(List.of()).contains("No night has been recorded yet."));
    }

    @Test
    @DisplayName("the committed page is a fresh render of the committed history")
    void the_page_is_generated() throws IOException {
        String committed = Files.readString(SeedSetsTest.ROOT.resolve(Nightly.PAGE), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertEquals(committed, Nightly.page(Nightly.history(SeedSetsTest.ROOT.resolve(Nightly.HISTORY))),
                "regenerate with ./gradlew :rig:nightly");
    }

    // -------------------------------------------------------------------------- the workflow

    @Test
    @DisplayName("the workflow plays the smoke set under H-0001 with flags the Rig knows, and the command the page publishes")
    void the_workflow_plays_what_it_says() throws IOException {
        String workflow = Files.readString(SeedSetsTest.ROOT.resolve(".github/workflows/nightly.yml"),
                StandardCharsets.UTF_8);

        int at = workflow.indexOf(":rig:run --args=");
        assertTrue(at >= 0, "the job plays something");
        int opens = workflow.indexOf('"', at);
        String played = workflow.substring(opens + 1, workflow.indexOf('"', opens + 1));
        Map<String, String> flags = Runner.arguments(played.trim().split("\\s+"));
        for (String flag : flags.keySet()) {
            assertTrue(Runner.KNOWN.contains(flag), flag + " is not a flag the Rig knows: " + played);
        }
        assertEquals(Nightly.REGISTRATION, flags.get(Runner.REGISTRATION), "ranked under H-0001");
        assertEquals(SeedSets.SMOKE, flags.get(Runner.SEEDS));
        assertEquals(Brains.RANDOM, flags.get(Runner.BRAIN));
        assertFalse(flags.containsKey(Runner.AGAINST), "a baseline, not a comparison");
        assertTrue(Nightly.COMMAND.contains("--args=\"" + played + "\""),
                "the page publishes the command the job plays: " + played);
        assertTrue(workflow.indexOf(":rig:run --args=", at + 1) < 0, "and it plays nothing else");

        // The Registration it names is one the repository has committed and the Rig can read.
        assertEquals(Nightly.REGISTRATION,
                Registrations.read(SeedSetsTest.ROOT, Nightly.REGISTRATION).registration().id());
        // What the recording step asks for is the shape Nightly's main reads.
        assertTrue(workflow.contains(":rig:nightly --args=\"$GITHUB_WORKSPACE night build/nightly "),
                "the night is judged from the folder the job played into");
        assertTrue(workflow.contains("--out build/nightly"), "which is this folder");
    }

    @Test
    @DisplayName("the workflow plays with a read-only token, publishes with a writing one, and makes a failed night visible")
    void the_workflow_shape() throws IOException {
        String workflow = Files.readString(SeedSetsTest.ROOT.resolve(".github/workflows/nightly.yml"),
                StandardCharsets.UTF_8);
        int play = workflow.indexOf("\n  play:");
        int publish = workflow.indexOf("\n  publish:");
        assertTrue(play > 0 && publish > play, "two jobs, play then publish");
        String playing = workflow.substring(play, publish);
        String publishing = workflow.substring(publish);

        assertTrue(workflow.contains("- cron: '"), "it runs every night");
        // The job that runs the Rig cannot write, and leaves no credential behind.
        assertTrue(playing.contains("contents: read") && !playing.contains("contents: write"));
        assertTrue(playing.contains("persist-credentials: false"));
        assertTrue(playing.contains("timeout-minutes:") && publishing.contains("timeout-minutes:"));
        assertTrue(publishing.contains("contents: write") && publishing.contains("pull-requests: write"));
        assertTrue(publishing.contains("needs: play") && publishing.contains("if: always()"),
                "a failed night is published too");
        assertFalse(publishing.contains(":rig:run"), "the job that can write plays nothing");
        assertTrue(publishing.contains("actions/download-artifact") && playing.contains("actions/upload-artifact"),
                "the night travels between them as an artifact");

        assertTrue(playing.contains("continue-on-error: true"),
                "a failed play still reaches the steps that say it failed");
        for (String step : List.of("- name: record the night", "- name: keep the night's folder and Run logs")) {
            int at = playing.indexOf(step);
            assertTrue(at >= 0, step);
            assertTrue(playing.substring(at, Math.min(playing.length(), at + 200)).contains("if: always()"),
                    step + " runs whatever happened before it");
        }
        assertTrue(playing.contains("${{ steps.play.outcome }}"), "the play step's outcome is part of the verdict");
        assertTrue(playing.contains("GITHUB_STEP_SUMMARY"), "the status is the job summary");
        assertTrue(playing.contains("rc=$?") && playing.contains("the night could not be recorded"));
        assertTrue(playing.contains("if: steps.record.outputs.pass != 'true'") && playing.contains("exit 1"),
                "a failed night fails the job");
        assertTrue(playing.contains("ledger-tonight.jsonl"), "tonight's ledger lines travel with the night");
        // The date and the run, fixed once and handed on.
        assertTrue(playing.contains("echo \"date=$(date -u +%F)\"") && playing.contains("$GITHUB_RUN_ATTEMPT"));
        assertTrue(publishing.contains("NIGHTLY_DATE: ${{ needs.play.outputs.date }}")
                && publishing.contains("NIGHTLY_RUN_URL: ${{ needs.play.outputs.run }}"));
        assertTrue(workflow.contains("ref: main") && workflow.contains("fetch-depth: 0"),
                "played on main, with the history git answers the Rig's questions from");
    }

    @Test
    @DisplayName("the script updates one open pull request on one branch, never main, with a lease, and checks before it pushes")
    void the_script_shape() throws IOException {
        String script = Files.readString(SeedSetsTest.ROOT.resolve("tools/nightly-pr.sh"), StandardCharsets.UTF_8);

        assertTrue(script.contains("branch=rig/nightly"));
        assertTrue(script.contains("git push --quiet --force-with-lease=\"$branch:$lease\" origin \"$branch\""));
        for (String line : script.split("\n")) {
            if (!line.strip().startsWith("#")) {
                assertFalse(line.matches(".*git push.*\\bmain\\b.*"), "never pushes main: " + line);
            }
        }
        assertTrue(script.contains("git ls-remote --exit-code --heads origin \"$branch\""),
                "a missing branch is told apart from a failed question");
        assertTrue(script.contains("gh pr list --head \"$branch\" --base main --state open"),
                "only the open pull request is edited");
        assertTrue(script.indexOf(":rig:test --tests '*NightlyTest'") < script.indexOf("git push"),
                "the page and history are checked before they are pushed");
        assertTrue(script.contains("git config user.name watchthelight")
                && script.contains("git config user.email admin@watchthelight.org"));
        assertFalse(script.contains("Co-Authored-By") || script.contains("Generated with"));
    }

    @Test
    @DisplayName("the script, run: carries unmerged nights and ledger lines forward, records a lost night, and opens the pull request")
    @org.junit.jupiter.api.Timeout(value = 3, unit = TimeUnit.MINUTES)
    void the_script_runs(@TempDir Path work) throws IOException, InterruptedException {
        String bash = bash();
        assumeTrue(bash != null, "no bash to run the script with");
        Path remote = work.resolve("origin.git");
        Path repo = work.resolve("repo");
        Path stubs = Files.createDirectories(work.resolve("stubs"));
        git(work, "init", "-q", "--bare", "-b", "main", remote.toString());
        git(work, "init", "-q", "-b", "main", repo.toString());
        git(repo, "config", "user.name", "a test");
        git(repo, "config", "user.email", "test@example.invalid");
        git(repo, "remote", "add", "origin", remote.toString());
        Files.createDirectories(repo.resolve("registrations"));
        Files.createDirectories(repo.resolve("results/nightly"));
        Files.createDirectories(repo.resolve("docs/results"));
        Files.createDirectories(repo.resolve("tools"));
        Files.copy(SeedSetsTest.ROOT.resolve("tools/nightly-pr.sh"), repo.resolve("tools/nightly-pr.sh"));
        Files.writeString(repo.resolve("registrations/ledger.jsonl"), "{\"a\":1}\n{\"b\":2}\n");
        Files.writeString(repo.resolve("results/nightly/history.jsonl"), "");
        Files.writeString(repo.resolve("docs/results/nightly.md"), "page\n");
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "main");
        git(repo, "push", "-q", "origin", "main");

        // The branch as a previous night left it: one night, one ledger line of its own.
        git(repo, "checkout", "-q", "-b", "rig/nightly");
        Files.writeString(repo.resolve("registrations/ledger.jsonl"), "{\"a\":1}\n{\"b\":2}\n{\"night\":1}\n");
        Files.writeString(repo.resolve("results/nightly/history.jsonl"), "{\"run\":\"r1\"}\n");
        git(repo, "commit", "-q", "-am", "night one");
        git(repo, "push", "-q", "origin", "rig/nightly");
        // Meanwhile a story pull request appended to main's ledger: main is no longer a prefix.
        git(repo, "checkout", "-q", "main");
        Files.writeString(repo.resolve("registrations/ledger.jsonl"), "{\"a\":1}\n{\"b\":2}\n{\"story\":1}\n");
        git(repo, "commit", "-q", "-am", "a story");
        git(repo, "push", "-q", "origin", "main");

        // Tonight broke before it was recorded: no night.jsonl, no status, one ledger line.
        Files.createDirectories(repo.resolve("build/nightly"));
        Files.writeString(repo.resolve("build/nightly/ledger-tonight.jsonl"), "{\"night\":2}\n");
        Path log = work.resolve("gh.log");
        stub(stubs.resolve("gh"), "echo \"$@\" >> '" + log.toString().replace('\\', '/') + "'\n"
                + "exit 0\n");
        stub(stubs.resolve("gradle"), "exit 0\n");

        ProcessBuilder builder = new ProcessBuilder(bash, "tools/nightly-pr.sh").directory(repo.toFile())
                .redirectErrorStream(true);
        builder.environment().put("PATH", stubs + java.io.File.pathSeparator + builder.environment().get("PATH"));
        builder.environment().put("NIGHTLY_GRADLE", stubs.resolve("gradle").toString().replace('\\', '/'));
        builder.environment().put("NIGHTLY_DATE", "2026-09-24");
        builder.environment().put("NIGHTLY_RUN_URL", "r2");
        builder.environment().put("NIGHTLY_RECORD_EXIT", "7");
        builder.environment().remove("GITHUB_STEP_SUMMARY");
        Process process = builder.start();
        String said = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), said);

        git(repo, "fetch", "-q", "origin");
        assertEquals("{\"a\":1}\n{\"b\":2}\n{\"story\":1}\n{\"night\":1}\n{\"night\":2}\n",
                show(repo, "origin/rig/nightly:registrations/ledger.jsonl"),
                "main's lines, the branch's own, then tonight's: nothing dropped, nothing twice");
        String history = show(repo, "origin/rig/nightly:results/nightly/history.jsonl");
        String[] nights = history.strip().split("\n");
        assertEquals(2, nights.length, history);
        assertEquals("{\"run\":\"r1\"}", nights[0], "last night carried forward");
        Nightly.Night lost = Nightly.Night.of(nights[1]);
        assertFalse(lost.pass());
        assertEquals("2026-09-24", lost.date());
        assertEquals("r2", lost.run());
        assertTrue(lost.why().contains("could not be recorded (recording exit 7)"), lost.why());
        String gh = Files.readString(log, StandardCharsets.UTF_8);
        assertTrue(gh.contains("pr list --head rig/nightly --base main --state open"), gh);
        assertTrue(gh.contains("pr create --head rig/nightly --base main"), "no open pull request, so one is opened: " + gh);
        assertTrue(gh.contains("FAIL on 2026-09-24"), "and its title says the night failed: " + gh);
    }

    /** Git's own bash on Windows, or bash on the path elsewhere; null when there is none. */
    private static String bash() {
        if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            for (String candidate : List.of("C:/Program Files/Git/bin/bash.exe", "C:/Program Files/Git/usr/bin/bash.exe")) {
                if (Files.isRegularFile(Path.of(candidate))) {
                    return candidate;
                }
            }
            return null;
        }
        return Files.isRegularFile(Path.of("/bin/bash")) ? "/bin/bash" : null;
    }

    private static void stub(Path file, String body) throws IOException {
        Files.writeString(file, "#!/usr/bin/env bash\n" + body, StandardCharsets.UTF_8);
        file.toFile().setExecutable(true);
    }

    private static String git(Path dir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String said = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), String.join(" ", command) + ": " + said);
        return said;
    }

    private static String show(Path repo, String object) throws IOException, InterruptedException {
        return git(repo, "show", object).replace("\r\n", "\n");
    }

    @Test
    @DisplayName("nothing in the job reaches beyond GitHub (NFR-8)")
    void no_network_beyond_github() throws IOException {
        for (String file : List.of(".github/workflows/nightly.yml", "tools/nightly-pr.sh")) {
            String text = Files.readString(SeedSetsTest.ROOT.resolve(file), StandardCharsets.UTF_8);
            for (String tool : List.of("curl", "wget", "Invoke-WebRequest", "nc ", "ssh ")) {
                assertFalse(text.contains(tool), file + " calls " + tool);
            }
            for (String line : text.split("\n")) {
                if (line.strip().startsWith("#")) {
                    continue;
                }
                for (String scheme : List.of("http://", "https://")) {
                    int at = line.indexOf(scheme);
                    assertTrue(at < 0 || line.startsWith("https://github.com/", at),
                            file + " names a host other than GitHub: " + line);
                }
            }
        }
    }
}
