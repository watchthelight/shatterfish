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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                        "25 of 25 Runs finished (1 incomplete"));
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
    @DisplayName("the task refuses any shape but page, or night with its four or five arguments")
    void the_task_refuses_other_shapes() {
        String root = SeedSetsTest.ROOT.toString();
        for (String[] args : List.of(new String[] {root}, new String[] {root, "pages"},
                new String[] {root, "night", "out", "2026-09-23"},
                new String[] {root, "day", "out", "2026-09-23", "abc1234"})) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> Nightly.main(args), String.join(" ", args));
            assertTrue(refused.getMessage().startsWith("usage: Nightly"), refused.getMessage());
        }
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
    @DisplayName("the workflow records and publishes even a failed night, goes red on one, and runs on a schedule")
    void a_failed_night_is_visible() throws IOException {
        String workflow = Files.readString(SeedSetsTest.ROOT.resolve(".github/workflows/nightly.yml"),
                StandardCharsets.UTF_8);

        assertTrue(workflow.contains("- cron: '"), "it runs every night");
        assertTrue(workflow.contains("continue-on-error: true"),
                "a failed play still reaches the steps that say it failed");
        for (String step : List.of("- name: record the night", "- name: update the results pull request",
                "- name: keep the night's Run logs")) {
            int at = workflow.indexOf(step);
            assertTrue(at >= 0, step);
            String rest = workflow.substring(at, Math.min(workflow.length(), at + 200));
            assertTrue(rest.contains("if: always()"), step + " runs whatever happened before it");
        }
        assertTrue(workflow.contains("GITHUB_STEP_SUMMARY"), "the status is the job summary");
        assertTrue(workflow.contains("rc=$?") && workflow.contains("the night could not be recorded"),
                "a night the recording could not judge still reports a failure where a person looks");
        assertTrue(workflow.contains("if: steps.record.outputs.pass != 'true'")
                        && workflow.contains("exit 1"),
                "a failed night fails the job, after it is published");
        assertTrue(workflow.contains("contents: write") && workflow.contains("pull-requests: write"));
        assertTrue(workflow.contains("ref: main") && workflow.contains("fetch-depth: 0"),
                "played on main, with the history git answers the Rig's questions from");
    }

    @Test
    @DisplayName("the script updates one branch, never main, as watchthelight, carrying unmerged nights forward")
    void one_branch_never_main() throws IOException {
        String script = Files.readString(SeedSetsTest.ROOT.resolve("tools/nightly-pr.sh"), StandardCharsets.UTF_8);

        assertTrue(script.contains("branch=rig/nightly"));
        assertTrue(script.contains("git push --quiet --force origin \"$branch\""),
                "the one branch, force-updated in place");
        for (String line : script.split("\n")) {
            if (line.strip().startsWith("#")) {
                continue;
            }
            assertFalse(line.matches(".*git push.*\\bmain\\b.*"), "never pushes main: " + line);
        }
        assertTrue(script.contains("git config user.name watchthelight")
                && script.contains("git config user.email admin@watchthelight.org"));
        assertTrue(script.contains("gh pr edit \"$branch\"") && script.contains("gh pr create --head \"$branch\" --base main"),
                "updates the one pull request, or opens it the first night");
        assertTrue(script.contains("base_of \"$ledger\"") && script.contains("base_of \"$history\""),
                "the ledger and the history carry unmerged nights forward");
        assertTrue(script.contains("cmp -s -n \"$size\""), "only when main's copy is a prefix of the branch's");
        assertTrue(script.contains("cp \"$tmp/previous\" \"$out\""),
                "the branch's copy is the one kept when it extends main's");
        assertTrue(script.contains("its unmerged lines were not carried forward.")
                        && script.contains(">> \"${GITHUB_STEP_SUMMARY:-/dev/null}\""),
                "and it says so, in the job summary, when it cannot carry them forward");
        assertFalse(script.contains("Co-Authored-By") || script.contains("Generated with"));
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
