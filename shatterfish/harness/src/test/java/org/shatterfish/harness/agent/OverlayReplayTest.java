package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.shatterfish.api.Decider;
import org.shatterfish.api.RunLog;
import org.shatterfish.brain.BrainDecider;
import org.shatterfish.harness.log.Replay;
import org.shatterfish.harness.log.RunLogReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An Overlay Run replays under the Rig (story 5.13, ADR-0007 option 15, ADR-0013).
 *
 * <p>{@link EmbeddedDeterminismTest} holds that an embedded Run is the headless Run of the same tuple when
 * the frames between two waits are the headless driver's. This test holds it where they are not: the
 * desktop game draws frames the headless one does not, a sixtieth of a second apart and as many as the
 * Brain's thinking and the wall clock give it, and what those frames draw (particles, emote icons, sound
 * pitch, the music's track rolls) came from the Run's generator until the draw-routing hook sent it to the
 * base one. So the Run is recorded the way the desktop plays it: frames of a different length each time,
 * some frames drawn on top of the Run's own, the Brain's worker deciding while the game goes on. Then the
 * log is replayed by the headless driver the Rig uses, and every Observation hash must match.
 *
 * <p>The jitter is seeded, so a failure names the same wait on every machine; the frames the worker
 * thread adds while the Brain thinks are the one part that is not, which is what the routing is for.
 */
class OverlayReplayTest {

    private static final long SALT = 0x5A17_5A17L;
    private static final int TURN_CAP = 2_000;

    @Test
    @DisplayName("the Brain, across floors: an Overlay Run's every Observation hash replays under the Rig")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void the_brain_across_floors(@TempDir Path folder) throws IOException {
        Replay.Waits replayed = recordAndReplay(folder, 3_343_871_708_117L, HeroClass.WARRIOR,
                () -> new BrainDecider(EmbeddedDeterminismTest.brain()), "shatterfish", 11L);
        assertTrue(replayed.waits() > 100, "a Run long enough to mean something: " + replayed);
    }

    @Test
    @DisplayName("the random agent, Search included: frames drawn between a Search and the next wait change nothing")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void the_random_agent(@TempDir Path folder) throws IOException {
        Replay.Waits replayed = recordAndReplay(folder, 31_415_926L, HeroClass.WARRIOR,
                () -> new RandomAgent(7L), "random", 23L);
        assertTrue(replayed.waits() > 20, "a Run long enough to mean something: " + replayed);
    }

    @Test
    @DisplayName("other heroes, other seeds: the routing holds beyond the first floor of one Run")
    @Timeout(value = 25, unit = TimeUnit.MINUTES)
    void other_heroes_and_seeds(@TempDir Path folder) throws IOException {
        long[] seeds = {2_718_281L, 1_414_213L, 1_732_050L};
        HeroClass[] heroes = {HeroClass.MAGE, HeroClass.ROGUE, HeroClass.HUNTRESS};
        for (int i = 0; i < seeds.length; i++) {
            Path sub = Files.createDirectories(folder.resolve("run" + i));
            long seed = seeds[i];
            Replay.Waits replayed = recordAndReplay(sub, seed, heroes[i],
                    () -> new BrainDecider(EmbeddedDeterminismTest.brain()), "shatterfish", 100L + i);
            assertTrue(replayed.waits() > 20, heroes[i] + " " + seed + ": " + replayed);
        }
    }

    /**
     * Plays one embedded Run the way the desktop does and replays its log. Returns what the Replay found
     * after asserting that it reproduced every wait.
     */
    static Replay.Waits recordAndReplay(Path folder, long seed, HeroClass heroClass, Supplier<Decider> brain,
                                        String name, long jitterSeed) throws IOException {
        RunLog.Brain who = new RunLog.Brain(name, "0".repeat(40), "0".repeat(64));
        Path recorded = Files.createDirectories(folder.resolve("overlay"));
        try (EmbeddedHost host = new EmbeddedHost(seed, heroClass, SALT)) {
            // The desktop's: the frames go on while the worker thinks, and nobody waits for it.
            host.stepWhileThinking = true;
            EmbeddedRun run = host.attach(brain.get(), new RunLoop.Logging(recorded, "0".repeat(40), who, "",
                    "overlay"), TURN_CAP);
            desktopFrames(host, jitterSeed);
            assertEquals(EmbeddedRun.State.ENDED, run.state(), "the Run ended");
        }
        Path log = only(recorded);
        RunLogReader.Log read = RunLogReader.of(log);
        assertTrue(read.header().embedded(), "an Overlay log says which driver played it");
        assertTrue(read.complete(), "the Run was played to its ending");

        // The Rig's replay machinery: the headless driver at its own frame, every wait's hash compared.
        // `waitsOf` is the path that reads an Overlay log (the Rig's `of` refuses it until the headless
        // driver can play the Overlay's interface size, issue #169), and it throws at the first wait that
        // differs, naming the sections.
        Replay.Waits replayed = Replay.waitsOf(log, "test");
        assertEquals(read.waits().size(), replayed.waits(), "every wait of the log was replayed");
        assertEquals(replayed.waits(), replayed.verified(), "every Observation hash matched: " + replayed);
        assertEquals(0L, replayed.unverifiableFrom(), "nothing in the log is beyond a Replay");
        return replayed;
    }

    /**
     * Frames as the desktop gives them: each of a different length (a fast machine's, a stalled one's),
     * and between any two of them some more, as the game draws while the Brain is deciding, while the
     * pacing waits and while a window is shown.
     */
    static void desktopFrames(EmbeddedHost host, long jitterSeed) {
        Random jitter = new Random(jitterSeed);
        long frames = 0;
        while (frames++ < 50_000_000L) {
            host.frameTime = 0.002f + jitter.nextFloat() * 0.05f;
            if (host.frame() == EmbeddedRun.State.ENDED) {
                return;
            }
            if (host.waitingOnTheBrain()) {
                Thread.onSpinWait();
                continue;
            }
            int extra = jitter.nextInt(4);
            for (int i = 0; i < extra; i++) {
                host.frameTime = 0.002f + jitter.nextFloat() * 0.05f;
                host.driver.stepper().step(host.frameTime);
            }
        }
        throw new AssertionError("the Run did not end");
    }

    private static Path only(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            List<Path> logs = files.filter(path -> path.toString().endsWith(".jsonl")).toList();
            assertEquals(1, logs.size(), "one log in " + folder + ": " + logs);
            assertFalse(Files.readString(logs.get(0)).isEmpty());
            return logs.get(0);
        }
    }
}
