package org.shatterfish.harness.agent;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.SurfaceScene;
import com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;
import com.watabou.noosa.Game;
import com.watabou.noosa.Scene;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Observation;
import org.shatterfish.harness.driver.RunLogWriter;
import org.shatterfish.harness.driver.UiRole;
import org.shatterfish.harness.driver.WaitGate;
import org.shatterfish.harness.driver.Windows;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.executor.Outcome;
import org.shatterfish.harness.observer.GameLogListener;
import org.shatterfish.harness.rng.RngControl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * A Run played inside a game whose loop someone else owns: the Overlay's (story 5.1, FR-37,
 * ADR-0013).
 *
 * <p>Headlessly the driver owns the loop and steps its own frames ({@code HeadlessDriver}); in the
 * desktop game the render thread owns it, and a Run is handed one frame at a time and must never
 * hold one up (NFR-4). So this class is the Run loop turned inside out: {@link #frame()} is called
 * once per frame, on the render thread, after the game's own frame, and returns at once. Between
 * two frames it asks the same question the headless driver asks, through the same {@link WaitGate};
 * at a confirmed wait it reseeds, observes and hands the Observation to the Brain's own worker
 * thread; at a later frame, when the worker's answer is in, it executes the Action and writes the
 * record, through the same executor and the same record writer the headless loop uses. What the
 * two loops share is the per-wait sequence of ADR-0013, in the same order, so a Run played here is
 * the Run the Rig would play from the same tuple, frame for frame (see {@link #frame()} for the one
 * condition on that).
 *
 * <p><b>Threads.</b> Three roles, as ADR-0013 names them. The game's actor thread is untouched: it
 * reaches this class only through hook row 5's notification, one volatile write in the gate. The
 * UI-role thread is the render thread, claimed for the Run at {@link #attach}: it confirms, reseeds,
 * observes, executes and records, and it never waits on the worker; it asks once per frame whether
 * the answer is in ({@code Future.isDone}) and takes it only then. The Brain's worker is one thread
 * of its own: it receives an immutable Observation and returns an Action, and touches no game
 * object, no scene and no file. A Brain that thinks for a second costs the game nothing: the frames
 * go on drawing, and the Run is {@link State#THINKING} meanwhile.
 *
 * <p><b>Scenes.</b> The real game destroys and recreates its play scene on every floor change
 * ({@code InterlevelScene}), and serves the change itself. This class does not serve it; it
 * re-attaches when the new scene is created, through hook row 3's scene seam
 * ({@code GameScene.create()}, right after the log pane), where the Observer's log listener is
 * re-added before the new floor's first lines. What survives the scene is this object: the wait
 * index in the gate, the salt in the generator control, the Brain with its Belief, and the Run
 * log's writer with its chain (ADR-0015, "Scene lifetime"). {@link #attachments()} counts the
 * scenes it has been attached to.
 *
 * <p><b>What it does not do yet.</b> No Panel, no pause, no stepping, no takeover: those are the
 * rest of epic 5. A Run attached here plays at the speed its Brain decides and ends where the
 * headless loop would end it.
 */
public final class EmbeddedRun implements AutoCloseable {

    /** What only the owner of the loop can say about the frame that just ended. */
    public interface Host {

        /**
         * Runnables posted to the render thread that the next frame runs first. A wait is not
         * confirmed while any are queued, because the queue can put a window in front of the hero
         * before anyone could observe or click (ADR-0015; {@link WaitGate#frame}).
         */
        int pendingRunnables();

        /** The scene the game asked for, meaningful only while {@code Game.switchingScene()}. */
        Class<? extends Scene> requestedScene();
    }

    /** Where the Run stands after a frame. */
    public enum State {
        /** Playing: no decision is pending, and the next wait has not been confirmed. */
        PLAYING,
        /** The Brain is deciding on its worker; the frames go on. */
        THINKING,
        /** The Run has ended; {@link #outcome()} says how. */
        ENDED
    }

    private record Decided(Action action, long thinkMs) {
    }

    private final Host host;
    private final RngControl rng;
    private final Decider brain;
    private final Supplier<Observation> observer;
    private final RunLogWriter log;
    private final boolean oracle;
    private final int turnCap;
    private final WaitGate gate = new WaitGate();
    private final ActionExecutor executor = new ActionExecutor();
    private final ExecutorService worker;
    private final Thread uiThread;
    private final boolean claimedTheRole;
    private final Hooks.InputWait inputWait = gate::noticed;
    private final Hooks.LogReplaced seam = this::sceneCreated;

    private int attachments;
    private long waits;
    private long applied;
    private long refused;
    private int refusalsInARow;
    private String lastRefusal = "";
    private Action lastAction;
    private long lastTurn = -1;
    private int still;
    private Future<Decided> pending;
    private long pendingWait;
    private Observation pendingObservation;
    /** The thread the last decision ran on, for a test to see it was not this one. */
    private volatile Thread decidedOn;
    private RunOutcome outcome;
    private boolean closed;

    private EmbeddedRun(Host host, RngControl rng, Decider brain, Supplier<Observation> observer, RunLogWriter log,
                        boolean oracle, int turnCap, Thread uiThread, boolean claimedTheRole) {
        this.host = host;
        this.rng = rng;
        this.brain = brain;
        this.observer = observer;
        this.log = log;
        this.oracle = oracle;
        this.turnCap = turnCap;
        this.uiThread = uiThread;
        this.claimedTheRole = claimedTheRole;
        this.worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "shatterfish-brain");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Attaches a Run to the game on the calling thread, which becomes the Run's UI-role thread: the
     * render thread in the Overlay. The game has begun ({@code NewGame.begin} with {@code rng}), so the
     * header can say what the game decided; its play scene may exist already or be about to be
     * created, and either way the scene seam attaches the Run to every one that follows.
     *
     * @param host     what only the loop's owner knows about a frame
     * @param seed     the dungeon seed the game was begun with
     * @param heroClass the hero class it was begun with
     * @param rng      the Run's generator control, which reseeds at every wait (ADR-0007)
     * @param brain    what decides; it runs on its own worker thread and holds no game object
     * @param observer the door the Brain sees through: the fair Observer, or the launcher's oracle,
     *                 which this class cannot construct (FR-11; {@code OracleGateTest})
     * @param logging  where the Run's log goes and who played it, or null for a Run with no log
     * @param turnCap  the Run's turn cap, as the headless loop has it
     */
    public static EmbeddedRun attach(Host host, long seed, HeroClass heroClass, RngControl rng, Decider brain, Supplier<Observation> observer,
                                     RunLoop.Logging logging, int turnCap) {
        if (host == null || rng == null || brain == null || observer == null || heroClass == null) {
            throw new IllegalArgumentException("an embedded Run needs a host, a generator control, a Brain, an"
                    + " observer and a hero class");
        }
        if (turnCap < 1) {
            throw new IllegalArgumentException("the turn cap is at least one turn: " + turnCap);
        }
        if (Dungeon.hero == null) {
            throw new IllegalStateException("attach to a game that has begun: NewGame.begin first, so the header"
                    + " can say what the game decided");
        }
        Thread ui = Thread.currentThread();
        boolean claimed = UiRole.owner() != ui;
        UiRole.claim(ui);
        RunLogWriter log = null;
        try {
            if (logging != null) {
                log = RunLoop.openLog(logging, seed, heroClass, rng.salt(), turnCap);
            }
            EmbeddedRun run = new EmbeddedRun(host, rng, brain, observer, log,
                    logging != null && logging.oracle(), turnCap, ui, claimed);
            run.arm();
            return run;
        } catch (RuntimeException | Error failed) {
            if (log != null) {
                log.close();
            }
            if (claimed) {
                UiRole.release(ui);
            }
            throw failed;
        }
    }

    private void arm() {
        Hooks.inputWait = inputWait;
        gate.install();
        Hooks.logReplaced = seam;
        // A scene created before the attach was already attached by whoever created it (the
        // headless test host's driver); counting it is what "attached at scene creation" means for
        // the scene in front now.
        if (Game.scene() instanceof GameScene) {
            attachments = 1;
            GameLogListener.INSTANCE.onLogReplaced();
        }
    }

    /**
     * Hook row 3's seam, on the thread that creates the scene: the render thread, inside
     * {@code GameScene.create()}, right after the log pane replaced every listener on the game's
     * message signal. The Observer's listener goes back on, and whatever the gate heard belongs to
     * the floor that is gone.
     */
    private void sceneCreated() {
        GameLogListener.INSTANCE.onLogReplaced();
        gate.sceneChanged();
        attachments++;
    }

    /**
     * One frame's worth of the Run, called on the UI-role thread after the game's own frame. Never
     * blocks: it takes the Brain's answer only when it is already in.
     *
     * <p>A Run played here is the headless Run of the same tuple when the frames between two waits
     * are the same frames: the gate confirms the same waits and every draw between them comes from
     * the same generator. The desktop game adds frames the headless one does not have, the ones it
     * draws while the Brain thinks and the ones its wall clock paces, and what the render thread
     * draws in them comes from the Run's generator today; routing those draws away from it is
     * story 5.13's hook. Until then an Overlay Run is reproducible from its own log, which records
     * every Action, and equals a Rig Run exactly when no frame was spent waiting; the tests hold the
     * second by driving a Run here with a Brain that answers within its frame.
     */
    public State frame() {
        UiRole.require("EmbeddedRun.frame()");
        if (closed) {
            throw new IllegalStateException("this embedded Run has been closed");
        }
        if (outcome != null) {
            return State.ENDED;
        }
        if (pending != null) {
            if (!pending.isDone()) {
                return State.THINKING;
            }
            serve();
            return outcome != null ? State.ENDED : State.PLAYING;
        }
        // The order is the headless driver's: a requested scene first, then a dead hero, then a wait.
        if (Game.switchingScene()) {
            Class<? extends Scene> asked = host.requestedScene();
            if (asked == SurfaceScene.class) {
                end(RunOutcome.Cause.WIN, "");
            } else if (asked != InterlevelScene.class) {
                end(RunOutcome.Cause.UNSERVED_SCENE, asked == null ? "no scene named" : asked.getSimpleName());
            } else if (InterlevelScene.mode != InterlevelScene.Mode.DESCEND
                    && InterlevelScene.mode != InterlevelScene.Mode.ASCEND
                    && InterlevelScene.mode != InterlevelScene.Mode.FALL) {
                // The game would serve these, but the headless loop does not, and a Run that went on
                // here would be one a Replay under the Rig cannot follow (RunLoop.serve).
                end(RunOutcome.Cause.UNSERVED_SCENE, "the interlevel scene in mode " + InterlevelScene.mode);
            }
            return outcome != null ? State.ENDED : State.PLAYING;
        }
        if (!(Game.scene() instanceof GameScene)) {
            // Between floors: the loading scene is in front, and the play scene's creation is where
            // this Run picks up again.
            return State.PLAYING;
        }
        Hero hero = Dungeon.hero;
        if (hero == null) {
            return State.PLAYING;
        }
        if (!hero.isAlive() && WndResurrect.instance == null) {
            end(RunOutcome.Cause.DEATH, "");
            return State.ENDED;
        }
        long k = gate.frame(hero, Windows.front(), host.pendingRunnables() != 0);
        if (k == 0) {
            return State.PLAYING;
        }
        // The head of the wait, in ADR-0013's order: the index, the reseed, then the rest.
        rng.reseed(k);
        int turn = RunLoop.turns();
        if (turn >= turnCap) {
            end(RunOutcome.Cause.TURN_CAP, "");
            return State.ENDED;
        }
        if (turn == lastTurn) {
            still++;
            if (still >= RunLoop.WAITS_WITHOUT_A_TURN) {
                end(RunOutcome.Cause.STALLED, still + " waits without a turn passing, the last " + lastAction);
                return State.ENDED;
            }
        } else {
            lastTurn = turn;
            still = 0;
        }
        Observation observation = observer.get();
        pendingObservation = observation;
        pendingWait = k;
        pending = worker.submit(() -> {
            decidedOn = Thread.currentThread();
            // The clock is read around the decision and nowhere else, and what it measures goes in
            // the one field the chain leaves out, as the headless loop has it.
            long before = System.nanoTime();
            Action chosen = brain.decide(observation);
            return new Decided(chosen, (System.nanoTime() - before) / 1_000_000L);
        });
        return State.THINKING;
    }

    /** The Brain has answered: execute the answer and write the wait's record, as the headless loop does. */
    private void serve() {
        Future<Decided> done = pending;
        long k = pendingWait;
        Observation observation = pendingObservation;
        pending = null;
        pendingObservation = null;
        if (k != gate.waitIndex()) {
            // No wait is confirmed while a decision is pending, so this is a broken invariant rather
            // than a stale answer to skip; the takeover story is where a skipped decision becomes real.
            throw new IllegalStateException("the Brain answered wait " + k + " and the Run stands at wait "
                    + gate.waitIndex());
        }
        Decided decided;
        if (done.state() == Future.State.SUCCESS) {
            decided = done.resultNow();
        } else {
            Throwable failure = done.state() == Future.State.FAILED ? done.exceptionNow() : null;
            if (failure instanceof Decider.CannotDecide error) {
                // A Brain that cannot decide says so by throwing, and that is a result to count
                // (story 4.11), as the headless loop counts it.
                end(RunOutcome.Cause.BRAIN_ERROR, "at wait " + k + ": " + error.getMessage());
                return;
            }
            throw new IllegalStateException("the Brain failed at wait " + k, failure);
        }
        Action chosen = decided.action();
        if (chosen == null) {
            end(RunOutcome.Cause.NOTHING_OFFERED, "at wait " + k);
            return;
        }
        waits++;
        lastAction = chosen;
        Outcome result = executor.execute(observation, chosen);
        RunLoop.record(log, k, observation, chosen, !(result instanceof Outcome.Rejected), decided.thinkMs(),
                oracle, brain);
        if (result instanceof Outcome.Rejected rejected) {
            refused++;
            refusalsInARow++;
            lastRefusal = rejected.reason() + ": " + rejected.detail();
            if (refusalsInARow >= RunLoop.REFUSALS_IN_A_ROW) {
                end(RunOutcome.Cause.REFUSED, lastRefusal);
            }
        } else {
            applied++;
            refusalsInARow = 0;
        }
    }

    private void end(RunOutcome.Cause cause, String detail) {
        outcome = RunLoop.outcome(cause, rng.salt(), waits, applied, refused, detail);
        RunLoop.ending(log, gate.waitIndex(), outcome);
        if (log != null) {
            log.close();
        }
        worker.shutdownNow();
    }

    /**
     * Detaches the Run: the worker stops, the hooks this Run set are taken back, and the UI role is
     * released if this Run claimed it. A Run closed before it ended leaves a log with no end record,
     * which is what a killed Run's log is and what the Rig counts it as (ADR-0012).
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (pending != null) {
                pending.cancel(true);
                pending = null;
            }
            worker.shutdownNow();
            if (outcome == null && log != null) {
                log.close();
            }
        } finally {
            if (Hooks.inputWait == inputWait) {
                Hooks.inputWait = null;
            }
            if (Hooks.logReplaced == seam) {
                Hooks.logReplaced = null;
            }
            gate.uninstall();
            if (claimedTheRole) {
                UiRole.release(uiThread);
            }
        }
    }

    /** Where the Run stands, without advancing it. */
    public State state() {
        if (outcome != null) {
            return State.ENDED;
        }
        return pending != null ? State.THINKING : State.PLAYING;
    }

    /** How the Run ended, or null while it plays. */
    public RunOutcome outcome() {
        return outcome;
    }

    /** The index of the last wait confirmed; 0 before the first. It survives every floor. */
    public long waitIndex() {
        return gate.waitIndex();
    }

    /** The salt the Run declared, which survives every floor and reaches no Observation. */
    public long salt() {
        return rng.salt();
    }

    /** Play scenes this Run has been attached to: the first, and one more for every floor since. */
    public int attachments() {
        return attachments;
    }

    /** The Brain this Run plays, the same object on every floor. */
    public Decider brain() {
        return brain;
    }

    /** The thread the Brain last decided on, or null before the first decision. */
    public Thread decidedOn() {
        return decidedOn;
    }

    /** The Run's log file, or null for a Run with no log. */
    public java.nio.file.Path logFile() {
        return log == null ? null : log.file();
    }

    /** The UI-role thread this Run was attached on. */
    public Thread uiThread() {
        return uiThread;
    }
}
