package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.brain.BrainDecider;

import java.nio.file.Path;

/**
 * A person at the window of a real embedded Run, for the Overlay's tests (story 8.2): the harness tests'
 * {@code EmbeddedHost} and {@code ScriptedHuman} are package-private, so this bridge lives in their package, in
 * the Overlay's test sources, and offers the three things the Explain test needs. The person follows the Brain's
 * shadow Decision at each wait, which plays the Run the Brain's way, so the Decisions on the card are real ones.
 * Nothing in main code sees this class.
 */
public final class ExplainSession implements AutoCloseable {

    private static final long SALT = 0x5A17_5A17L;

    private final EmbeddedHost host;
    private final EmbeddedRun run;

    /** A HUMAN Run of {@code seed} and {@code heroClass}, shadowed by the harness tests' Brain, logged under {@code folder}. */
    public ExplainSession(long seed, HeroClass heroClass, Path folder) {
        host = new EmbeddedHost(seed, heroClass, SALT);
        run = host.attachHuman(new BrainDecider(EmbeddedDeterminismTest.brain()),
                HumanTurnReplayTest.logging(folder), 20_000);
    }

    /** Plays frames until a wait is open for the person's input; false if the Run ended instead. */
    public boolean nextWait() {
        return host.untilOpen(50_000);
    }

    /** The Run's snapshot, as the Panel is handed it. */
    public EmbeddedRun.Snapshot snapshot() {
        return run.snapshot();
    }

    /** Does what the Brain's shadow Decision at this wait says, with the person's own hands ({@code ScriptedHuman}). */
    public void follow() {
        EmbeddedRun.Snapshot snapshot = run.snapshot();
        RunLog.Decision decision = snapshot.decision();
        Observation observation = snapshot.observation();
        ScriptedHuman.follow(run, decision, observation);
    }

    @Override
    public void close() {
        host.close();
    }
}
