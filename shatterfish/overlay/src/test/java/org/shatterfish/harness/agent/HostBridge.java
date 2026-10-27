package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.shatterfish.api.Decider;
import org.shatterfish.api.RunLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * The harness's package-private test host ({@code EmbeddedHost}, {@code RealBrain}) made reachable from the
 * Overlay's tests (story 8.3), which live in another package. It lives in the harness's package so it can reach
 * what the harness's own tests reach, and adds nothing: every call is the host's.
 */
public final class HostBridge implements AutoCloseable {

    private final EmbeddedHost host;

    /** A host for a seeded Run at the desktop's sixtieth of a second a frame, the frames going on while the Brain thinks. */
    public HostBridge(long seed, HeroClass heroClass, long salt) {
        host = new EmbeddedHost(seed, heroClass, salt);
        host.frameTime = 1f / 60f;
        host.stepWhileThinking = true;
    }

    /** The real Brain of the committed Codex and weights, attached and logging into {@code folder}. */
    public EmbeddedRun attachBrain(Path folder, int turnCap) {
        return host.attach(RealBrain.decider(), new RunLoop.Logging(folder, "0".repeat(40),
                new RunLog.Brain("shatterfish", "0".repeat(40), "0".repeat(64)), "", "overlay"), turnCap);
    }

    /**
     * What the Overlay's own game does and the test host does not by default (story 8.5): a Brain that fails is
     * answered with a wait, and an Ankh's resurrection is served.
     */
    public HostBridge likeTheOverlay() {
        host.recovers = true;
        host.servesResurrection = true;
        return this;
    }

    /** Sets the game's interface size, as the Overlay does (it plays size 1, the Rig's headless driver size 0). */
    public HostBridge interfaceSize(int size) {
        SPDSettings.interfaceSize(size);
        return this;
    }

    /** A Run whose agent is a replay's log follower: no Brain decides, and no log is written (story 8.5). */
    public EmbeddedRun attachReplay(Decider follower, int turnCap) {
        return host.attach(follower, null, turnCap);
    }

    /**
     * A Run played by the Rig's own loop ({@code RunLoop}, the headless driver) with the real Brain, its log written
     * into {@code folder}: the log a replay session is given in story 8.5's tests.
     *
     * @return the log file
     */
    public static Path rigLog(Path folder, long seed, HeroClass heroClass, long salt, int turnCap) {
        try {
            Files.createDirectories(folder);
            new RunLoop().play(seed, heroClass, salt, RealBrain.decider(), turnCap,
                    new RunLoop.Logging(folder, "0".repeat(40),
                            new RunLog.Brain("shatterfish", "0".repeat(40), "0".repeat(64)), "", "rig"));
            try (Stream<Path> files = Files.list(folder)) {
                return files.filter(file -> file.toString().endsWith(".jsonl")).findFirst().orElseThrow();
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** The game time one frame covers (the Overlay gives FAST_FRAME when the Run is unbounded). */
    public void frameTime(float seconds) {
        host.frameTime = seconds;
    }

    /** Whether the frame about to run would only wait on the Brain's worker, as the desktop's frames do. */
    public boolean waitingOnTheBrain() {
        return host.waitingOnTheBrain();
    }

    /** One frame of the game and of the Run. */
    public EmbeddedRun.State frame() {
        return host.frame();
    }

    @Override
    public void close() {
        host.close();
    }
}
