package org.shatterfish.rig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.log.RunLogReader;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first Brain, in real Runs through the Rig (story 4.1): built in the child on the Codex its
 * caller read, and every wait it served is recorded with the Decision behind it and the Belief it
 * held.
 */
class ShatterfishRunTest {

    @Test
    @DisplayName("every wait the Brain serves is logged with its Decision and Belief, and the log replays")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void the_log_says_why(@TempDir Path out) throws IOException {
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put(Runner.BRAIN, Brains.SHATTERFISH);
        arguments.put(Runner.SEEDS, SeedSets.SMOKE);
        arguments.put(Runner.OUT, out.toString());
        arguments.put(Runner.ROOT, SeedSetsTest.ROOT.toString());
        arguments.put(Runner.PARALLEL, "4");
        arguments.put(Runner.CAP, "60");

        Runner.run(arguments);

        List<Path> logs = Verify.logs(out);
        assertEquals(SeedSets.load(SeedSetsTest.ROOT, SeedSets.SMOKE).set().entries().size(), logs.size());
        int waits = 0;
        int highlighted = 0;
        int withAlternatives = 0;
        for (Path log : logs) {
            RunLogReader.Log read = RunLogReader.of(log);
            assertTrue(read.readable(), log + ": " + read.unreadable());
            assertEquals(Brains.SHATTERFISH, read.header().brain().name());
            for (RunLog.Wait wait : read.waits()) {
                assertNotNull(wait.decision(), "a Brain's wait says why: " + log.getFileName() + " at " + wait.k());
                assertTrue(List.of("answer-prompt", "fallback").contains(wait.decision().policy()), wait.decision().policy());
                assertEquals(wait.action(), wait.decision().chosen().action(), "the Action logged is the one decided");
                assertTrue(wait.belief().matches("[0-9a-f]{64}"), wait.belief());
                // Story 4.4: the Decision's shape in real Runs, and the cells on the wait record.
                RunLog.Decision decision = wait.decision();
                String at = log.getFileName() + " at " + wait.k() + ": " + decision;
                assertFalse(decision.goal().isBlank(), at);
                java.util.Set<org.shatterfish.api.Action> distinct = new java.util.HashSet<>();
                distinct.add(decision.chosen().action());
                for (RunLog.Choice alternative : decision.alternatives()) {
                    assertTrue(distinct.add(alternative.action()), "alternatives are distinct from each other and the choice: " + at);
                }
                if ("fallback".equals(decision.policy())) {
                    // The fallback's reason says how many Actions were offered: "uniform 1/n".
                    int offered = Integer.parseInt(decision.chosen().why().substring("uniform 1/".length()));
                    assertEquals(Math.min(offered - 1, RunLog.Decision.ALTERNATIVES), decision.alternatives().size(), at);
                    if (offered > 1) {
                        withAlternatives++;
                    }
                }
                assertEquals(cells(wait.action()), wait.highlights(), at);
                if (!wait.highlights().isEmpty()) {
                    highlighted++;
                }
                waits++;
            }
        }
        assertTrue(waits > 0, "the Runs served waits");
        assertTrue(highlighted > 0, "some wait pointed at a cell, and it was highlighted");
        assertTrue(withAlternatives > 0, "some fallback wait had a choice, and recorded alternatives");
        // A Brain's log replays: the follower states the Decision and Belief hash each wait
        // recorded, so the replayed records, and the chain over them, are the original's.
        // A Run the harness stopped following (an unknown window) says it is not verifiable, which
        // is the harness's limit rather than the Brain's, so the replay takes one that is.
        Path verifiable = logs.stream().filter(log -> RunLogReader.of(log).end().verifiable())
                .findFirst().orElseThrow();
        org.shatterfish.harness.log.Replay.Result replayed = org.shatterfish.harness.log.Replay.of(
                verifiable, out.resolve("replay"), "test");
        assertTrue(replayed.ok(), replayed.why());
        assertEquals(replayed.originalChain(), replayed.chain());
        // And it renders as a strategy log, a line per wait between the two header lines and the end.
        RunLogReader.Log verified = RunLogReader.of(verifiable);
        String strategy = StrategyLog.render(verified);
        assertEquals(verified.waits().size() + 3, strategy.lines().count(), strategy);
        String summary = Files.readString(out.resolve(RunIndex.SUMMARY), StandardCharsets.UTF_8).strip();
        assertEquals("0", LogHeader.value(summary, "runsIncomplete"), summary);
    }

    /**
     * The cells an Action points at, written out here rather than asked of the Brain, so a Brain that
     * highlights the wrong cell for some kind of Action disagrees with something it did not write.
     */
    private static List<Integer> cells(org.shatterfish.api.Action action) {
        return switch (action) {
            case org.shatterfish.api.Action.Step a -> List.of(a.cell());
            case org.shatterfish.api.Action.MoveTo a -> List.of(a.cell());
            case org.shatterfish.api.Action.Attack a -> List.of(a.cell());
            case org.shatterfish.api.Action.Interact a -> List.of(a.cell());
            case org.shatterfish.api.Action.OpenChest a -> List.of(a.cell());
            case org.shatterfish.api.Action.Buy a -> List.of(a.cell());
            case org.shatterfish.api.Action.Unlock a -> List.of(a.cell());
            case org.shatterfish.api.Action.UseItemAt a -> List.of(a.cell());
            case org.shatterfish.api.Action.AbilityAt a -> List.of(a.cell());
            default -> List.of();
        };
    }

    @Test
    @DisplayName("the random agent says nothing and highlights nothing")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void the_random_agent_highlights_nothing(@TempDir Path out) {
        Map<String, String> arguments = new LinkedHashMap<>();
        arguments.put(Runner.BRAIN, Brains.RANDOM);
        arguments.put(Runner.SEEDS, SeedSets.SMOKE);
        arguments.put(Runner.OUT, out.toString());
        arguments.put(Runner.ROOT, SeedSetsTest.ROOT.toString());
        arguments.put(Runner.PARALLEL, "4");
        arguments.put(Runner.CAP, "20");

        Runner.run(arguments);

        int waits = 0;
        for (Path log : Verify.logs(out)) {
            for (RunLog.Wait wait : RunLogReader.of(log).waits()) {
                assertEquals(null, wait.decision(), log.getFileName() + " at " + wait.k());
                assertEquals(List.of(), wait.highlights(), log.getFileName() + " at " + wait.k());
                waits++;
            }
        }
        assertTrue(waits > 0, "the Runs served waits");
    }

    @Test
    @DisplayName("a Run of the Brain states the Codex it is built on, and one that does not is refused")
    void built_on_the_codex() {
        org.shatterfish.api.SeedSet.Entry triple = SeedSets.load(SeedSetsTest.ROOT, SeedSets.SMOKE).set().entries().get(0);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Brains.of(Brains.SHATTERFISH, triple));
        assertTrue(refused.getMessage().contains(RunOne.CODEX), refused.getMessage());

        String tag = org.shatterfish.harness.boot.HeadlessBoot.pinnedTag();
        org.shatterfish.api.Codex.Knowledge codex = CodexKnowledge.read(
                SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(tag), tag);
        assertEquals(org.shatterfish.api.Codex.VERSION, codex.manifest().version());
        org.shatterfish.api.Weights weights = WeightsFile.read(
                WeightsFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH), Brains.SHATTERFISH);
        assertTrue(Brains.of(Brains.SHATTERFISH, triple, codex, weights) instanceof org.shatterfish.brain.BrainDecider);
        IllegalArgumentException unweighted = assertThrows(IllegalArgumentException.class,
                () -> Brains.of(Brains.SHATTERFISH, triple, codex, null));
        assertTrue(unweighted.getMessage().contains(RunOne.WEIGHTS), unweighted.getMessage());
        assertThrows(IllegalArgumentException.class, () -> CodexManifest.read(
                SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(tag), "v0.0.1"),
                "a Codex for another tag is not this build's");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(SeedSetsTest.ROOT.resolve(".git")),
                "a source export has no history to date the Brain by");
        assertFalse(Brains.version(SeedSetsTest.ROOT, Brains.SHATTERFISH).isEmpty(),
                "the Brain's version is the commit that last changed brain/");
    }

    @Test
    @DisplayName("the Brain's stream owes nothing to the dungeon seed: two triples, the same screens, the same Actions")
    void blind_to_the_seed() {
        // Non-negotiable #1: the random agents' seed is a bijection of the dungeon seed and what
        // the header shows, so a Brain seeded from it could recover the seed. Two triples that
        // differ only in the seed must give Brains that are the same function of the screen.
        java.util.List<org.shatterfish.api.SeedSet.Entry> entries =
                SeedSets.load(SeedSetsTest.ROOT, SeedSets.SMOKE).set().entries();
        org.shatterfish.api.SeedSet.Entry one = entries.get(0);
        org.shatterfish.api.SeedSet.Entry other = entries.stream()
                .filter(entry -> entry.seed() != one.seed()).findFirst().orElseThrow();
        assertTrue(Brains.agentSeed(one) != Brains.agentSeed(other), "the two triples seed the random agents apart");
        assertTrue(Brains.brainSeed(Brains.SHATTERFISH) != Brains.agentSeed(one));
        assertTrue(Brains.configHash(SeedSetsTest.ROOT, Brains.SHATTERFISH).matches("[0-9a-f]{64}"));
        assertTrue(!Brains.configHash(SeedSetsTest.ROOT, Brains.SHATTERFISH).equals("0".repeat(64)),
                "the Brain states its configuration");
        assertFalse(Brains.readsCodex(Brains.RANDOM), "the random agent is built on no Codex");
        String tag = org.shatterfish.harness.boot.HeadlessBoot.pinnedTag();
        org.shatterfish.api.Codex.Knowledge codex = CodexKnowledge.read(
                SeedSetsTest.ROOT.resolve(CodexManifest.FOLDER).resolve(tag), tag);
        org.shatterfish.api.Weights weights = WeightsFile.read(
                WeightsFile.of(SeedSetsTest.ROOT, Brains.SHATTERFISH), Brains.SHATTERFISH);
        org.shatterfish.brain.BrainDecider a = (org.shatterfish.brain.BrainDecider) Brains.of(Brains.SHATTERFISH, one, codex, weights);
        org.shatterfish.brain.BrainDecider b = (org.shatterfish.brain.BrainDecider) Brains.of(Brains.SHATTERFISH, other, codex, weights);
        assertEquals(a.brain().seed(), b.brain().seed());
    }
}
