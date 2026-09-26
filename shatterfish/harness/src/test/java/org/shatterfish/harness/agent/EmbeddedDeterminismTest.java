package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Codex;
import org.shatterfish.api.Decider;
import org.shatterfish.api.RunLog;
import org.shatterfish.api.Weights;
import org.shatterfish.brain.Brain;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.log.RunLogReader;
import org.shatterfish.harness.log.RunLogVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An embedded Run is the headless Run of the same tuple (story 5.1; ADR-0013's "one per-wait sequence
 * for both drivers, or a Replay of an Overlay Run is not the same code as a Rig Run").
 *
 * <p>The same tuple is played twice: once by the headless Run loop the Rig uses, and once by the
 * embedded Run, driven frame by frame by a host that plays the render thread's part and serves floor
 * changes as the loading scene does. The two logs must carry the same chain, which covers every
 * wait's index, turn, depth, Observation hash, Action, whether it was applied, the Decision and the
 * Belief, and the ending; only the wall-clock fields the chain leaves out may differ.
 *
 * <p>The host gives the Brain its answer within the frame, as the headless loop does. What the
 * desktop game adds, frames drawn while the Brain thinks and frames paced by the wall clock, is the
 * one difference this test does not cover, and why: the render thread's draws in those frames come
 * from the Run's generator until story 5.13 routes them away (EmbeddedRun.frame).
 */
class EmbeddedDeterminismTest {

    private static final long SALT = 0x5A17_5A17L;
    private static final int TURN_CAP = 1_500;

    @Test
    @DisplayName("the random agent: an embedded Run's log chain is the headless Run's")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void the_random_agent(@TempDir Path folder) throws IOException {
        same(folder, 31_415_926L, HeroClass.WARRIOR, () -> new RandomAgent(7L), "random");
    }

    @Test
    @DisplayName("the Brain: an embedded Run's log chain is the headless Run's, Decisions and Beliefs included")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void the_brain(@TempDir Path folder) throws IOException {
        same(folder, 27_182_818L, HeroClass.WARRIOR, () -> new BrainDecider(brain()), "shatterfish");
    }

    @Test
    @DisplayName("across floors: a Run that takes the stairs is the same Run in both drivers")
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void across_floors(@TempDir Path folder) throws IOException {
        // A smoke-set Warrior (seeds/smoke.json) whose Run with this Brain leaves the first floor, so the
        // embedded side's floor change, served by the host as the loading scene serves it and re-attached
        // through the scene seam, is compared too.
        RunOutcome[] played = new RunOutcome[1];
        same(folder, 3_343_871_708_117L, HeroClass.WARRIOR, () -> new BrainDecider(brain()), "shatterfish", played);
        assertTrue(played[0].depth() >= 2, "the Run left the first floor: " + played[0]);
    }

    private static void same(Path folder, long seed, HeroClass heroClass, Supplier<Decider> brain, String name)
            throws IOException {
        same(folder, seed, heroClass, brain, name, new RunOutcome[1]);
    }

    private static void same(Path folder, long seed, HeroClass heroClass, Supplier<Decider> brain, String name,
                             RunOutcome[] out) throws IOException {
        RunLog.Brain who = new RunLog.Brain(name, "0".repeat(40), "0".repeat(64));
        Path headless = Files.createDirectories(folder.resolve("headless"));
        Path embedded = Files.createDirectories(folder.resolve("embedded"));

        // Two Runs in one process: each begins through NewGame.begin, which puts back the upstream
        // statics one Run can leave for the next (RunStatics, issue #167), the snake's dodge counter
        // among them (Snake.java:58-70), which story 5.1 first found here after story 4.13's Brain
        // fought a snake. A Rig Run has a process of its own.
        RunOutcome played = new RunLoop().play(seed, heroClass, SALT, brain.get(), TURN_CAP,
                new RunLoop.Logging(headless, "0".repeat(40), who, "", "headless"));
        out[0] = played;

        RunOutcome attached;
        try (EmbeddedHost host = new EmbeddedHost(seed, heroClass, SALT)) {
            EmbeddedRun run = host.attach(brain.get(), new RunLoop.Logging(embedded, "0".repeat(40), who, "",
                    "embedded"), TURN_CAP);
            assertEquals(EmbeddedRun.State.ENDED, host.play(50_000_000L), "the embedded Run ended");
            attached = run.outcome();
        }

        assertEquals(played.cause(), attached.cause(), "the same ending: " + played + " / " + attached);
        assertEquals(played.waits(), attached.waits());
        assertEquals(played.applied(), attached.applied());
        assertEquals(played.depth(), attached.depth());
        assertEquals(played.turns(), attached.turns());

        Path a = only(headless);
        Path b = only(embedded);
        RunLogVerifier.Verified left = RunLogVerifier.of(a);
        RunLogVerifier.Verified right = RunLogVerifier.of(b);
        assertTrue(left.ok() && left.complete(), left.why());
        assertTrue(right.ok() && right.complete(), right.why());
        List<RunLog.Wait> waitsA = RunLogReader.of(a).waits();
        List<RunLog.Wait> waitsB = RunLogReader.of(b).waits();
        assertEquals(waitsA.size(), waitsB.size(), "as many waits");
        for (int i = 0; i < waitsA.size(); i++) {
            RunLog.Wait x = waitsA.get(i);
            RunLog.Wait y = waitsB.get(i);
            assertEquals(x.k(), y.k(), "wait " + i);
            if (!x.obs().equals(y.obs())) {
                StringBuilder differs = new StringBuilder();
                x.sections().forEach((section, hash) -> {
                    if (!hash.equals(y.sections().get(section))) {
                        differs.append(section).append(' ');
                    }
                });
                assertEquals(x.obs(), y.obs(), "the Observation at wait " + x.k() + " differs in " + differs
                        + "after " + (i > 0 ? waitsA.get(i - 1).action() + " applied=" + waitsA.get(i - 1).applied()
                        + " / " + waitsB.get(i - 1).action() + " applied=" + waitsB.get(i - 1).applied() : "nothing")
                        + "; turns " + x.turn() + " / " + y.turn());
            }
            assertEquals(x.action(), y.action(), "the Action at wait " + x.k());
            assertEquals(x.decision(), y.decision(), "the Decision at wait " + x.k());
            assertEquals(x.belief(), y.belief(), "the Belief at wait " + x.k());
            assertEquals(x.turn(), y.turn(), "the turn at wait " + x.k());
            assertEquals(x.depth(), y.depth(), "the depth at wait " + x.k());
            assertEquals(x.applied(), y.applied(), "applied at wait " + x.k());
            assertEquals(x.sections(), y.sections(), "the sections at wait " + x.k());
        }
        // Everything the chain covers but the driver: the headers' tuples and every wait above, and the
        // endings here. The chains themselves differ by the header's driver field, and must: an
        // Overlay log may never pass for a Rig log (story 5.1).
        RunLogReader.Log readA = RunLogReader.of(a);
        RunLogReader.Log readB = RunLogReader.of(b);
        assertTrue(!readA.header().embedded() && readB.header().embedded(), "each log says which driver played it");
        assertEquals(readA.header().runId(), readB.header().runId(), "one tuple");
        assertEquals(readA.end().outcome(), readB.end().outcome(), "one ending");
        assertEquals(readA.end().k(), readB.end().k());
        assertTrue(!left.chain().equals(right.chain()), "the driver is chained");
        assertTrue(waitsA.size() > 20, "the Run was long enough to mean something: " + waitsA.size() + " waits");
    }

    private static Path only(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            List<Path> logs = files.filter(path -> path.toString().endsWith(".jsonl")).toList();
            assertEquals(1, logs.size(), "one log in " + folder + ": " + logs);
            return logs.get(0);
        }
    }

    /** The committed weights' features, which the Evaluation requires by name (BrainAtTheWindowsTest). */
    private static Weights weights() {
        Map<String, Long> terms = new TreeMap<>(Map.of(
                "act_attack", 0L, "act_descend", 0L, "act_rest_hurt", 0L, "act_search", 0L, "act_wait", 0L,
                "depth", 10000L, "enemies", -3000L, "hp", 10L, "hunger", -5000L, "level", 5000L));
        terms.put("strength", 2000L);
        terms.putAll(Map.of("item", 2000L, "gold", 10L, "turn", -150L, "weapon", 2L, "armor", 4L, "cursed", -10000L));
        return new Weights("shatterfish", 2,
                terms.entrySet().stream().map(term -> new Weights.Term(term.getKey(), term.getValue())).toList());
    }

    /** A Brain on an empty Codex (BrainAtTheWindowsTest). */
    static Brain brain() {
        return new Brain(new Codex.Knowledge(new Codex.Manifest(Codex.VERSION, "v4.0.0", List.of("manifest.json")),
                List.of(), List.of(), List.of()), weights(), 19L);
    }
}
