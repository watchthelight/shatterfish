package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.knowledge.CodexKnowledge;
import org.shatterfish.harness.knowledge.CodexManifest;
import org.shatterfish.harness.knowledge.PlaybookFile;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moving the strategy constants into the Playbook changed no play (story 7.2).
 *
 * <p>The values the Policies decided by before the move are written out below, in the test, as the
 * Playbook text they were; the committed {@code playbooks/shatterfish.json} must play the {@code smoke}
 * set to Run logs equal, wait for wait, to the ones those values play (the decider's wall-clock time apart, which no hash covers). The control edits values and
 * asserts the Decisions differ, so the equality is not the equality of a Brain that ignores its
 * Playbook. The same set, played on the commit before the story, gave the same Decisions (the PR
 * states both hashes): that comparison needs two checkouts, so it is made by hand and recorded there.
 */
class PlaybookEquivalenceTest {

    /** The turn cap of a Run here: long enough to reach the first unknown potions and scrolls of depth 1 and 2. */
    private static final int CAP = 700;

    /** What the Java constants held before the story: the odds, the cap, the reach and the waits, as the Playbook states them. */
    private static final String THE_CONSTANTS = "{\"bosses\":{},\"classes\":{},\"format\":1,"
            + "\"identify\":{\"escape_waits\":12,\"gain_odds_per_mille\":200,\"healing_odds_per_mille\":200,"
            + "\"inventory_odds_per_mille\":250,\"known_gains\":0,\"potion_hp_per_mille\":500,\"reach\":8,\"rest_waits\":50,\"scroll_hp_per_mille\":1000},"
            + "\"name\":\"shatterfish\",\"upgrade\":{\"tie\":\"WEAPON\",\"weapon_lead\":0},\"version\":1}\n";

    /**
     * Values that change what the Brain does on a screen with an unknown potion or scroll: it drinks only
     * what is surely healing or strength, never walks to a better testing cell, and rests for no wait.
     */
    private static final String CHANGED = "{\"bosses\":{},\"classes\":{},\"format\":1,"
            + "\"identify\":{\"escape_waits\":12,\"gain_odds_per_mille\":1000,\"healing_odds_per_mille\":1000,"
            + "\"inventory_odds_per_mille\":1,\"known_gains\":1,\"potion_hp_per_mille\":1000,\"reach\":0,\"rest_waits\":0,\"scroll_hp_per_mille\":500},"
            + "\"name\":\"shatterfish\",\"upgrade\":{\"tie\":\"ARMOR\",\"weapon_lead\":-5},\"version\":1}\n";

    /** A root holding what a Run reads, with {@code playbook} as the Brain's Playbook. */
    private static Path root(Path into, String playbook) throws IOException {
        Path root = Files.createDirectories(into);
        String tag = org.shatterfish.harness.boot.HeadlessBoot.pinnedTag();
        copy(SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(tag), root.resolve(CodexManifest.FOLDER).resolve(tag));
        copy(SeedSetsTest.ROOT.resolve("seeds"), root.resolve("seeds"));
        copy(SeedSetsTest.ROOT.resolve(WeightsFile.FOLDER), root.resolve(WeightsFile.FOLDER));
        Path bestiary = CodexKnowledge.bestiaryFile(SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(tag));
        Path there = CodexKnowledge.bestiaryFile(root.resolve(CodexManifest.FOLDER).resolve(tag));
        Files.createDirectories(there.getParent());
        Files.copy(bestiary, there);
        Files.createDirectories(root.resolve(PlaybookFile.FOLDER));
        Files.writeString(PlaybookFile.of(root, Brains.SHATTERFISH), playbook, StandardCharsets.UTF_8);
        return root;
    }

    private static void copy(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path source : walk.toList()) {
                Path target = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target);
                }
            }
        }
    }

    /**
     * The smoke set played by the Brain under {@code root}'s Playbook: each log by its file name. The Runner
     * draws a fresh salt per invocation (FR-22), so two of its invocations are not the same game; the
     * children are started here instead, each entry under a salt this test fixes, as the Runner's own do.
     */
    private static Map<String, Path> play(Path root, Path out) throws IOException {
        Files.createDirectories(out);
        String tag = org.shatterfish.harness.boot.HeadlessBoot.pinnedTag();
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path exe = Path.of(java + ".exe");
        List<org.shatterfish.api.SeedSet.Entry> entries = SeedSets.load(root, SeedSets.SMOKE).set().entries();
        List<Process> running = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            org.shatterfish.api.SeedSet.Entry entry = entries.get(i);
            Path working = Files.createDirectories(out.resolve("work").resolve(Integer.toString(i)));
            running.add(new ProcessBuilder(Files.exists(exe) ? exe.toString() : java.toString(),
                    "-cp", System.getProperty("java.class.path"), RunOne.class.getName(),
                    RunOne.SEED, Long.toString(entry.seed()),
                    RunOne.CLASS, entry.heroClass().name(),
                    RunOne.CHALLENGES, Integer.toString(entry.challengeFlags()),
                    RunOne.SALT, Long.toString(1000 + i),
                    RunOne.OUT, out.toAbsolutePath().toString(),
                    RunOne.COMMIT, "0".repeat(40),
                    RunOne.BRAIN, Brains.SHATTERFISH,
                    RunOne.BRAIN_COMMIT, "0".repeat(40),
                    RunOne.MACHINE, "test",
                    RunOne.CAP, Integer.toString(CAP),
                    RunOne.CODEX, root.resolve(CodexManifest.FOLDER).resolve(tag).toAbsolutePath().toString(),
                    RunOne.WEIGHTS, WeightsFile.of(root, Brains.SHATTERFISH).toAbsolutePath().toString(),
                    RunOne.PLAYBOOK, PlaybookFile.of(root, Brains.SHATTERFISH).toAbsolutePath().toString())
                    .directory(working.toFile()).redirectErrorStream(true).redirectOutput(working.resolve("out.txt").toFile())
                    .start());
            if (running.size() == 2) {
                finish(running);
            }
        }
        finish(running);
        Map<String, Path> logs = new TreeMap<>();
        for (Path log : Verify.logs(out)) {
            // The file name carries the configuration hash, which an edited Playbook changes; the Run is the rest.
            logs.put(log.getFileName().toString().replaceFirst("-[0-9a-f]{16}-", "-"), log);
        }
        return logs;
    }

    private static void finish(List<Process> running) {
        for (Process process : running) {
            try {
                assertEquals(0, process.waitFor(), "a Run ended cleanly");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        running.clear();
    }

    /** Every wait of a log: the Action chosen and the Decision behind it, which is what the Brain did. */
    private static List<RunLog.Wait> waits(Path log) {
        RunLogReader.Log read = RunLogReader.of(log);
        assertTrue(read.readable(), log + ": " + read.unreadable());
        List<RunLog.Wait> waits = new ArrayList<>();
        for (RunLog.Wait w : read.waits()) {
            // How long the decider took is the one field the chain leaves out, and the clock's, not the Brain's.
            waits.add(new RunLog.Wait(w.k(), w.turn(), w.depth(), w.branch(), w.obs(), w.sections(), w.action(), w.applied(),
                    w.actor(), w.decision(), w.belief(), w.highlights(), 0));
        }
        return waits;
    }

    @Test
    @DisplayName("the committed Playbook plays the smoke set to the same Decisions as the values the constants held, and an edited one does not")
    @Timeout(value = 40, unit = TimeUnit.MINUTES)
    void the_playbook_changes_no_play(@TempDir Path scratch) throws IOException {
        assertEquals(THE_CONSTANTS, Files.readString(PlaybookFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH), StandardCharsets.UTF_8),
                "the committed Playbook states exactly the values the constants held");

        Map<String, Path> constants = play(root(scratch.resolve("constants"), THE_CONSTANTS), scratch.resolve("constants-out"));
        Map<String, Path> playbook = play(root(scratch.resolve("playbook"),
                Files.readString(PlaybookFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH), StandardCharsets.UTF_8)),
                scratch.resolve("playbook-out"));
        assertEquals(SeedSets.load(SeedSetsTest.ROOT, SeedSets.SMOKE).set().entries().size(), constants.size());
        assertEquals(constants.keySet(), playbook.keySet());
        for (Map.Entry<String, Path> log : constants.entrySet()) {
            // The Runs are told apart by their header's out folder and nothing else of the header; the
            // body, every wait, is what the Brain did.
            assertEquals(waits(log.getValue()), waits(playbook.get(log.getKey())), log.getKey() + ": the same waits");
            assertEquals(RunLogReader.of(log.getValue()).header().brain(), RunLogReader.of(playbook.get(log.getKey())).header().brain(),
                    log.getKey() + ": the same header Brain, configuration hash included, because the Playbook is the same");
        }

        Map<String, Path> changed = play(root(scratch.resolve("changed"), CHANGED), scratch.resolve("changed-out"));
        assertEquals(constants.keySet(), changed.keySet());
        int differing = 0;
        for (Map.Entry<String, Path> log : constants.entrySet()) {
            if (!waits(log.getValue()).equals(waits(changed.get(log.getKey())))) {
                differing++;
            }
            assertNotEquals(RunLogReader.of(log.getValue()).header().brain().configHash(),
                    RunLogReader.of(changed.get(log.getKey())).header().brain().configHash(),
                    "the header carries the Playbook it was played with");
        }
        assertTrue(differing > 0, "a Playbook with other values plays other Runs, or this equality holds for any Brain");
    }
}
