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
        int fought = 0;
        int explored = 0;
        for (Path log : logs) {
            RunLogReader.Log read = RunLogReader.of(log);
            assertTrue(read.readable(), log + ": " + read.unreadable());
            assertEquals(Brains.SHATTERFISH, read.header().brain().name());
            for (RunLog.Wait wait : read.waits()) {
                assertNotNull(wait.decision(), "a Brain's wait says why: " + log.getFileName() + " at " + wait.k());
                assertTrue(List.of("answer-prompt", "heal", "fight", "eat", "test-item", "pick-up", "equip", "explore", "fallback")
                        .contains(wait.decision().policy()), wait.decision().policy());
                // Story 4.10: a test-item wait drinks or reads the appearance its reason names,
                // plainly, or Steps toward the cell it tests on, or out of its own fire or gas, or
                // rests after a test.
                if ("test-item".equals(wait.decision().policy())) {
                    String why = wait.decision().chosen().why();
                    if (wait.action() instanceof org.shatterfish.api.Action.UseItem use) {
                        assertTrue(List.of("DRINK", "READ").contains(use.action()), why);
                        assertEquals("test: " + use.item().name(), why);
                    } else if (wait.action() instanceof org.shatterfish.api.Action.Step) {
                        assertTrue(why.matches("cell: .+|escape: .+"), why);
                    } else {
                        // After a test, short of full health: a rest.
                        assertEquals("rest: after-test", why);
                    }
                }
                // Story 4.8: a pick-up wait Steps toward an item, naming it and its distance, or
                // takes the one underfoot; an equip wait puts on the piece its reason names.
                if ("pick-up".equals(wait.decision().policy())) {
                    String why = wait.decision().chosen().why();
                    if (wait.action() instanceof org.shatterfish.api.Action.Step) {
                        assertTrue(why.matches("item: .+ [1-9][0-9]*"), why);
                    } else {
                        assertEquals(new org.shatterfish.api.Action.PickUp(), wait.action(), why);
                        assertTrue(why.startsWith("take: "), why);
                    }
                }
                if ("equip".equals(wait.decision().policy())) {
                    org.shatterfish.api.Action.UseItem use = (org.shatterfish.api.Action.UseItem) wait.action();
                    assertEquals("EQUIP", use.action());
                    assertEquals("wear: " + use.item().name(), wait.decision().chosen().why());
                }
                // Story 4.9: the eat Policy eats a named food, and the heal Policy drinks with its hit
                // points and the danger it measured in the reason.
                if ("eat".equals(wait.decision().policy())) {
                    assertTrue(wait.decision().chosen().why().matches("eat: .+"), wait.decision().chosen().why());
                    assertTrue(wait.action() instanceof org.shatterfish.api.Action.UseItem use && use.action().equals("EAT"),
                            wait.action().toString());
                }
                if ("heal".equals(wait.decision().policy())) {
                    assertTrue(wait.decision().chosen().why().matches("heal [0-9]+/[0-9]+"), wait.decision().chosen().why());
                    assertTrue(wait.action() instanceof org.shatterfish.api.Action.UseItem use && use.action().equals("DRINK"),
                            wait.action().toString());
                }
                if ("fight".equals(wait.decision().policy())) {
                    fought++;
                    // Story 4.7: the fight Policy attacks, steps, holds, or takes the stairs, and
                    // its reason names which.
                    String why = wait.decision().chosen().why();
                    assertTrue(why.matches("(attack|cornered): .+|approach [1-9][0-9]*|chokepoint [0-2]|hold: chokepoint"
                            + "|hold: no-way|retreat: stairs|retreat [0-9]+"), why);
                }
                if ("explore".equals(wait.decision().policy())) {
                    explored++;
                    // Story 4.6: one Step, one Search or the Descend per wait, and the reason names
                    // which of its plans the Action serves: a Step goes to a frontier, a search
                    // spot or the exit, n Steps away; a Search is counted against the floor's bound.
                    org.shatterfish.api.Action action = wait.action();
                    String why = wait.decision().chosen().why();
                    if (action instanceof org.shatterfish.api.Action.Step) {
                        assertTrue(why.matches("(frontier|search-spot|exit|away) [1-9][0-9]*"), why);
                    } else if (action instanceof org.shatterfish.api.Action.Search) {
                        assertTrue(why.matches("search ([1-9]|1[0-2])/12|rest: before-descent"), why);
                    } else if (action instanceof org.shatterfish.api.Action.Rest) {
                        // Story 4.7: healing on the floor above one it fled by the stairs.
                        assertEquals("rest: before-descent", why);
                    } else {
                        assertEquals(new org.shatterfish.api.Action.Descend(), action, why);
                        assertEquals("descend", why);
                    }
                }
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
                }
                // Story 4.7: the fight and explore Policies take nearly every wait now, and the
                // fallback's picks are their alternatives; any wait that recorded one counts.
                if (!decision.alternatives().isEmpty()) {
                    withAlternatives++;
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
        assertTrue(withAlternatives > 0, "some wait had a choice, and recorded alternatives");
        assertTrue(explored > 0, "the explore Policy took some waits");
        assertTrue(fought > 0, "the fight Policy took some waits: enemies came into view");
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
