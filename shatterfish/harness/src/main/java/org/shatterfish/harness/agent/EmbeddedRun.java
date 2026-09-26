package org.shatterfish.harness.agent;

import com.watabou.input.ControllerHandler;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.SurfaceScene;
import com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;
import com.watabou.noosa.Game;
import com.watabou.noosa.Scene;
import org.shatterfish.api.Action;
import org.shatterfish.api.Decider;
import org.shatterfish.api.Deliberator;
import org.shatterfish.api.Observation;
import org.shatterfish.api.Rewindable;
import org.shatterfish.api.RunLog;
import org.shatterfish.harness.driver.HeadlessDriver;
import org.shatterfish.harness.driver.RunLogWriter;
import org.shatterfish.harness.driver.UiRole;
import org.shatterfish.harness.driver.WaitGate;
import org.shatterfish.harness.driver.Windows;
import org.shatterfish.harness.executor.ActionExecutor;
import org.shatterfish.harness.executor.Outcome;
import org.shatterfish.harness.observer.GameLogListener;
import org.shatterfish.harness.rng.RngControl;
import org.shatterfish.harness.scene.SceneStepper;

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

        /**
         * The interface size the Run plays on, stated in its log header (story 5.2). By default the
         * game's own setting as it reads now; the Overlay states the size it declares, since the log is
         * opened in {@code create()}, before the game knows its window, when the setting reads 0
         * ({@code core/.../SPDSettings.java:140-146}).
         */
        default int interfaceSize() {
            return SPDSettings.interfaceSize();
        }

        /** Whether a game controller is connected now, stated in the Run's log header (story 5.2). */
        default boolean controllerConnected() {
            return ControllerHandler.isControllerConnected();
        }
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

    /**
     * What {@link #snapshot()} publishes to the render thread (story 5.3, FR-38): the last served
     * wait's Decision, turn and floor, and the Run's live state -- {@link State#THINKING} exactly
     * when a decision is pending, so the Panel's {@code THINKING} marker is real rather than guessed.
     * {@code decision} is null before the first wait is served, or when the Brain is not a
     * {@link Deliberator}.
     */
    public record Snapshot(RunLog.Decision decision, int turn, int floor, State state) {
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

    /**
     * Seconds of game time a Run may go without a confirmed wait, while not thinking, before it ends
     * as stuck: the headless loop's budget for one wait ({@code RunLoop.FRAME_BUDGET}) at sixty frames
     * a second, five and a half minutes. It is counted in game time, the sum of {@code Game.elapsed}
     * the game itself advances by each frame (capped at a fifth of a second, {@code SPD-classes/…/noosa/
     * Game.java:269-273}), not in frames: the desktop draws as many frames as the monitor refreshes,
     * so a frame count would end the same Run at a different moment on a 240 Hz screen than on a 60 Hz
     * one (story 5.1's review).
     */
    static final double BUDGET_SECONDS = RunLoop.FRAME_BUDGET / 60.0;

    private int attachments;
    private double secondsWithoutAWait;
    /** The wait being confirmed again after a stale answer, which is not a second wait on the same turn. */
    private boolean reconfirming;
    /** The decider's state before it was asked the pending question, when it can be put back. */
    private Object pendingMark;
    /** Stale answers whose decider could not be put back ({@link Rewindable}); none for the Overlay's two agents. */
    private int unrewound;
    /** The play scene and window in front when the pending wait was confirmed. */
    private Scene confirmedScene;
    private Window confirmedWindow;
    /** Answers dropped because the screen changed while the Brain thought, and the last reason. */
    private int staleAnswers;
    private String lastStale = "";
    /** The floor the Run was last attached on, to tell a new floor from a scene rebuilt on the same one. */
    private Object attachedLevel;
    private long waits;
    private long applied;
    private long refused;
    private int refusalsInARow;
    private String lastRefusal = "";
    private Action lastAction;
    private long lastTurn = -1;
    private int still;
    /** The turn a wait was confirmed at, carried from {@link #frame()} to {@link #serve()} (story 5.3). */
    private int pendingTurn;
    /** The last served wait's Decision, turn and floor, for {@link #snapshot()} (story 5.3). */
    private RunLog.Decision lastDecision;
    private int lastDecisionTurn;
    private int lastDecisionFloor;
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
                // The screen the Run plays on, stated in its header (story 5.2): the interface size and a
                // connected controller change the hint lines the log carries (ADR-0013's story 5.2 amendment).
                log = RunLoop.openLog(logging, seed, heroClass, rng.salt(), turnCap, RunLog.Header.EMBEDDED,
                        host.interfaceSize(), host.controllerConnected() ? 1 : 0);
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
            attachedLevel = Dungeon.level;
            GameLogListener.INSTANCE.onLogReplaced();
        }
    }

    /**
     * Hook row 3's seam, on the thread that creates the scene: the render thread, inside
     * {@code GameScene.create()}, right after the log pane replaced every listener on the game's
     * message signal. The Observer's listener goes back on.
     *
     * <p>A new floor re-arms the gate: whatever it heard belongs to the floor that is gone, and the new
     * floor's first wait is announced by the hero's first act on it, as the headless driver has it. A
     * play scene rebuilt on the same floor does not: the desktop game rebuilds its scene whenever the
     * window changes size ({@code SPD-classes/…/noosa/Game.java:136-141}), which it does once at
     * start-up, and a hero who became ready in the scene before it announced his wait there and will
     * not announce it again. Discarding that announcement left the first launch of story 5.1 waiting
     * for a wait that had already come; the headless game never rebuilds a scene on the same floor, so
     * the two drivers still agree.
     */
    private void sceneCreated() {
        GameLogListener.INSTANCE.onLogReplaced();
        if (Dungeon.level != attachedLevel) {
            gate.sceneChanged();
            attachedLevel = Dungeon.level;
        }
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
     * draws in them comes from the Run's generator today. So an Overlay Run is <b>not reproducible
     * from its tuple or its Action list</b> until story 5.13 routes those draws away: a Replay of its
     * log parts from it at the first roll the extra frames moved (wait 15 of the first real launch).
     * That is a named exception to non-negotiable 5 (ADR-0013's story 5.1 amendment), and its log's
     * header says {@code driver: embedded}, which the Rig refuses. The tests hold the equality where
     * it does hold, by driving a Run here with a Brain that answers within its frame.
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
            secondsWithoutAWait = 0;
            return outcome != null ? State.ENDED : State.PLAYING;
        }
        secondsWithoutAWait += Game.elapsed;
        if (secondsWithoutAWait > BUDGET_SECONDS) {
            // The headless loop's rule (RunLoop, FRAME_BUDGET): a Run that reaches no wait is stuck,
            // and a stuck Run is a result to count. The region intro the loading scene shows on a first
            // descent to depths 6, 11, 16 and 21 (core/.../scenes/InterlevelScene.java:279-280,
            // :608-614) waits for a Continue nobody clicks, and ends here; the Overlay does not click
            // through it (story 5.1: the headless game never shows it, and clicking it reads a journal
            // page the headless Run does not).
            end(RunOutcome.Cause.UNKNOWN_WINDOW, "no wait within " + Math.round(BUDGET_SECONDS)
                    + " seconds of game time; in front: " + describeFront());
            return State.ENDED;
        }
        // A request made this frame first, as an early exit; then the scene actually in front, because
        // a request the actor thread makes is usually served by the same frame's step() before this
        // looks (SPD-classes/.../noosa/Game.java:230-243), and the flag is not volatile.
        if (Game.switchingScene() && decideByScene(host.requestedScene(), InterlevelScene.mode)) {
            return outcome != null ? State.ENDED : State.PLAYING;
        }
        Scene front = Game.scene();
        if (!(front instanceof GameScene)) {
            decideByScene(front == null ? null : front.getClass(), InterlevelScene.mode);
            return outcome != null ? State.ENDED : State.PLAYING;
        }
        Hero hero = Dungeon.hero;
        if (hero == null) {
            return State.PLAYING;
        }
        if (!hero.isAlive() && WndResurrect.instance == null) {
            end(RunOutcome.Cause.DEATH, "");
            return State.ENDED;
        }
        if (!SceneStepper.actorThreadParked()) {
            // The hero can be ready while the actor thread is still inside the act that made him so,
            // writing and posting; a wait is confirmed only once it has parked (story 5.1).
            return State.PLAYING;
        }
        Window window = Windows.front();
        long k = gate.frame(hero, window, host.pendingRunnables() != 0);
        if (k == 0) {
            return State.PLAYING;
        }
        secondsWithoutAWait = 0;
        confirmedScene = front;
        confirmedWindow = window;
        // The head of the wait, in ADR-0013's order: the index, the reseed, then the rest.
        rng.reseed(k);
        int turn = RunLoop.turns();
        pendingTurn = turn;
        if (turn >= turnCap) {
            end(RunOutcome.Cause.TURN_CAP, "");
            return State.ENDED;
        }
        if (reconfirming) {
            // The same wait asked again after a stale answer: the headless Run counted it once, and so
            // does this one, so it neither counts toward a stall nor moves the last turn seen.
            reconfirming = false;
        } else if (turn == lastTurn) {
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
        if (observation.header().oracle() != oracle) {
            // An oracle Observation reaches only a Run whose log says it is one: the launcher's
            // --oracle states both. A Run with no log is not an oracle Run, so it is refused too
            // (non-negotiable 1, FR-11), as the headless loop refuses one at its record.
            throw new IllegalStateException("the Run says oracle=" + oracle + " and the Observation at wait " + k
                    + " says " + observation.header().oracle() + "; an oracle Run is stated with its log");
        }
        pendingObservation = observation;
        pendingWait = k;
        // Taken here, on this thread, before the worker is handed the question; the submit is the
        // happens-before edge to the worker, and isDone the one back.
        pendingMark = brain instanceof Rewindable rewindable ? rewindable.mark() : null;
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
        String stale = stale();
        if (stale != null) {
            // The screen the Brain was shown is not the screen in front any more: a scene rebuilt by a
            // resize destroyed the Prompt, or something the Run did not do moved the hero. The answer
            // is dropped unrecorded and the same wait is confirmed again from what is there now, so
            // the log's waits stay one per index (story 5.1). The decider is put back to where it
            // stood before it was asked, so the second asking is answered from one Observation, as
            // the headless Run's is; a decider that cannot be put back keeps what the dropped question
            // changed, and is counted (unrewoundAnswers).
            staleAnswers++;
            lastStale = "wait " + k + ": " + stale;
            if (brain instanceof Rewindable rewindable) {
                rewindable.rewind(pendingMark);
            } else {
                unrewound++;
            }
            pendingMark = null;
            reconfirming = true;
            gate.reconfirm(k);
            return;
        }
        pendingMark = null;
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
        // The same read RunLoop.record makes of the Brain's own reasons, kept here too so the render
        // thread has a Decision to show without reopening the log (story 5.3): both reads happen on
        // this thread, after the worker's Future is done, which is the happens-before edge over
        // whatever decide() set on its own thread (ADR-0013).
        lastDecision = brain instanceof Deliberator deliberator ? deliberator.lastDecision() : null;
        lastDecisionTurn = pendingTurn;
        lastDecisionFloor = observation.header().depth();
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

    /**
     * Why the wait the pending answer is for is no longer the wait in front, or null when it still is:
     * nothing announced or handed over since, the same play scene and window, the hero still waiting,
     * nothing queued for the render thread, and the actor thread parked.
     */
    private String stale() {
        if (!gate.quiet()) {
            return "the hero acted since";
        }
        if (gate.acted()) {
            return "an Action was handed to the game since";
        }
        if (Game.switchingScene() || Game.scene() != confirmedScene) {
            return "the play scene changed";
        }
        Window window = Windows.front();
        if (window != confirmedWindow) {
            return "the window in front changed";
        }
        Hero hero = Dungeon.hero;
        if (hero == null || !HeadlessDriver.waitState(hero, window)) {
            return "the hero no longer waits";
        }
        if (host.pendingRunnables() != 0 || !SceneStepper.actorThreadParked()) {
            return "the game is not at rest";
        }
        return null;
    }

    /**
     * Decides what a scene that is not a play scene means for the Run, by its class, as the headless
     * loop decides by the scene it is asked for ({@code RunLoop.serve}): the surface is the win; the
     * loading scene of a descent, an ascent or a fall is a floor change the game is serving, and the
     * play scene it asks for when done is one being built; any other loading mode (a resurrection, a
     * return) and any other scene end the Run as unserved, because the headless loop ends it there and
     * a Run that went on here would part from the Rig's without a word. No scene at all is the
     * moment between two, in the test host.
     *
     * @return whether the scene settled this frame
     */
    private boolean decideByScene(Class<?> scene, InterlevelScene.Mode mode) {
        if (scene == null || GameScene.class.isAssignableFrom(scene)) {
            return true;
        }
        if (SurfaceScene.class.isAssignableFrom(scene)) {
            end(RunOutcome.Cause.WIN, "");
        } else if (InterlevelScene.class.isAssignableFrom(scene)) {
            if (mode != InterlevelScene.Mode.DESCEND && mode != InterlevelScene.Mode.ASCEND
                    && mode != InterlevelScene.Mode.FALL) {
                end(RunOutcome.Cause.UNSERVED_SCENE, "the interlevel scene in mode " + mode);
            }
        } else {
            end(RunOutcome.Cause.UNSERVED_SCENE, scene.getSimpleName());
        }
        return true;
    }

    private static String describeFront() {
        Scene scene = Game.scene();
        Window window = Windows.front();
        return (scene == null ? "no scene" : scene.getClass().getSimpleName())
                + (scene instanceof InterlevelScene ? " (" + InterlevelScene.mode + ")" : "")
                + (window == null ? "" : ", " + window.getClass().getSimpleName());
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

    /**
     * A snapshot for the render thread to draw (story 5.3): the last served wait's Decision, turn and
     * floor, and the Run's live state. Called on the UI-role thread, every frame if the caller likes --
     * the fields it reads are written only by {@link #serve()}, which runs there too, so this is a
     * same-thread read of the Run's own state, not a cross-thread one (the one cross-thread edge, the
     * Brain worker publishing its Decision through the Future, is already crossed by the time
     * {@link #serve()} stores it).
     */
    public Snapshot snapshot() {
        UiRole.require("EmbeddedRun.snapshot()");
        return new Snapshot(lastDecision, lastDecisionTurn, lastDecisionFloor, state());
    }

    /** The index of the last wait confirmed; 0 before the first. It survives every floor. */
    public long waitIndex() {
        return gate.waitIndex();
    }

    /** The salt the Run declared, which survives every floor and reaches no Observation. */
    public long salt() {
        return rng.salt();
    }

    /** Answers dropped unrecorded because the screen changed while the Brain thought. */
    public int staleAnswers() {
        return staleAnswers;
    }

    /** Stale answers whose decider was not {@link Rewindable}, so kept what the dropped question changed. */
    public int unrewoundAnswers() {
        return unrewound;
    }

    /** Waits in a row confirmed on the same turn, which the stall rule counts; for the tests. */
    int waitsWithoutATurn() {
        return still;
    }

    /** Why the last answer was dropped, or empty. */
    public String lastStale() {
        return lastStale;
    }

    /** Times hook row 5 has notified this Run: acts of the hero that began unready. */
    public long hookNotifications() {
        return gate.notifications();
    }

    /** Play scenes this Run has been attached to: the first, one more for every floor since, and one for every rebuild. */
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
