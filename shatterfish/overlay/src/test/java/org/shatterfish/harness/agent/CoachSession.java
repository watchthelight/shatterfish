package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import org.shatterfish.api.Action;
import org.shatterfish.api.Belief;
import org.shatterfish.api.Deliberator;
import org.shatterfish.api.Observation;
import org.shatterfish.api.RunLog;
import org.shatterfish.brain.BrainDecider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

/**
 * A learner at the window of a real HUMAN Run, for the coach's tests (story 8.9): the harness tests'
 * {@code EmbeddedHost} and {@code ScriptedHuman} are package-private, so this bridge lives in their package, in the
 * Overlay's test sources, as {@link ExplainSession} does. The learner's moves are a script the test gives, not the
 * Brain's, so a coached Run and an uncoached one can be played with the same hands. The shadow Brain is the real
 * one; with {@code gated} it answers only once {@link #release} is called, which is how a test holds the advice
 * back while the learner moves on. Nothing in main code sees this class.
 */
public final class CoachSession implements AutoCloseable {

    private static final long SALT = 0x5A17_5A17L;

    /** The real Brain behind a gate: closed, it holds every shadow question until {@link #release}. */
    private static final class Gate implements Deliberator {
        final BrainDecider brain = new BrainDecider(EmbeddedDeterminismTest.brain());
        final CountDownLatch open = new CountDownLatch(1);

        @Override
        public Action decide(Observation observation) {
            try {
                open.await();
            } catch (InterruptedException stop) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(stop);
            }
            return brain.decide(observation);
        }

        @Override
        public RunLog.Decision lastDecision() {
            return brain.lastDecision();
        }

        @Override
        public Belief belief() {
            return brain.belief();
        }

        @Override
        public String beliefHash() {
            return brain.beliefHash();
        }

        @Override
        public List<Integer> lastHighlights() {
            return brain.lastHighlights();
        }
    }

    private final EmbeddedHost host;
    private final EmbeddedRun run;
    private final Gate gate;
    private final Path folder;

    /** A HUMAN Run of {@code seed} and {@code heroClass}, logged under {@code folder}; the shadow is held when {@code gated}. */
    public CoachSession(long seed, HeroClass heroClass, Path folder, boolean gated) {
        this.folder = folder;
        host = new EmbeddedHost(seed, heroClass, SALT);
        gate = gated ? new Gate() : null;
        if (gate != null) {
            run = host.attachHuman(gate, HumanTurnReplayTest.logging(folder), 20_000);
        } else {
            run = host.attachHuman(new BrainDecider(EmbeddedDeterminismTest.brain()), HumanTurnReplayTest.logging(folder), 20_000);
        }
    }

    /** Plays frames until a wait is open for the person's input; false if the Run ended instead. */
    public boolean nextWait() {
        return host.untilOpen(50_000);
    }

    /** Plays frames until the Brain has answered every shadow it was asked, which is when the learner moves in these tests. */
    public void untilShadowsLanded() {
        while (run.state() == EmbeddedRun.State.THINKING) {
            host.frame();
            Thread.onSpinWait();
        }
    }

    /** One frame of the game and of the Run. */
    public EmbeddedRun.State frame() {
        return host.frame();
    }

    /** Lets a gated Brain answer. */
    public void release() {
        if (gate != null) {
            gate.open.countDown();
        }
    }

    /** The Run's snapshot, as the Panel is handed it. */
    public EmbeddedRun.Snapshot snapshot() {
        return run.snapshot();
    }

    /** The Run, for the switch the Panel would press. */
    public EmbeddedRun run() {
        return run;
    }

    /** The learner takes the wait button. */
    public void pressWait() {
        ScriptedHuman.pressWait();
    }

    /** The learner rests until something disturbs them: a move the others in a script are not. */
    public void pressRest() {
        ScriptedHuman.pressRest();
    }

    /** The learner searches. */
    public void pressSearch() {
        ScriptedHuman.pressSearch();
    }

    /** The one log under the folder this session was given, read after {@link #close}. */
    public Path log() throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(path -> path.toString().endsWith(".jsonl")).findFirst().orElseThrow();
        }
    }

    @Override
    public void close() {
        release();
        host.close();
    }
}
