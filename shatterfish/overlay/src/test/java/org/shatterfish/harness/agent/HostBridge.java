package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.shatterfish.api.RunLog;

import java.nio.file.Path;

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
