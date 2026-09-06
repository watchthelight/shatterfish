package org.shatterfish.harness.driver;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.journal.Journal;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.Chasm;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.GameLog;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.utils.DungeonSeed;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndResurrect;
import com.watabou.noosa.Scene;
import org.shatterfish.harness.boot.HeadlessBoot;
import org.shatterfish.harness.boot.HeadlessGame;
import org.shatterfish.harness.observer.GameLogListener;
import org.shatterfish.harness.scene.HeadlessScene;
import org.shatterfish.harness.scene.SceneStepper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/**
 * The headless driver: owns one Run's game and the loop that advances it, on the calling thread.
 *
 * <p>ADR-0015 decided that the driver thread owns the main loop headlessly, as the render thread
 * owns it in the game, and that the driver drains what the game posts to the render thread. The
 * boot ({@link HeadlessBoot}) gives the process a backend whose own loop never runs; the scene
 * ({@link HeadlessScene}) is the game's own; the stepper ({@link SceneStepper}) advances it one
 * fenced frame at a time and returns with the actor thread parked. What this class adds is the
 * Run: it starts a seeded game the way a player does, and it steps the scene until the hero waits
 * for input, the hero is dead, or the game asks for another scene. Nothing else advances the game.
 *
 * <p><b>The Input wait.</b> The game says when something happened: hook row 5's site at the top of
 * the branch of {@code Hero.act()} that runs when the hero begins an act unready
 * ({@code core/.../actors/hero/Hero.java:840-848}) notifies this driver, on the actor thread, with
 * one volatile write. That branch runs once before every transition to ready, because
 * {@code ready()} is reached only later in the same act ({@code :862-870}, {@code :935-946}), and
 * it also runs on each step of a move and each turn of resting ({@code :885-887}); a hero that is
 * already waiting skips it on every wake-up, which is why sixty wake-ups are not sixty waits. So a
 * notification means "confirm", not "wait": between two frames the actor thread is parked and
 * everything it wrote is published, and the driver confirms an Input wait as AD-5 has it, the
 * hero {@code ready} with no action pending and not resting, under no window or a recognised
 * Prompt ({@link Prompts}), and drops a notification that finds the hero mid-action. Three
 * things the game does shape the condition: a Prompt shown this very frame is not yet a wait,
 * because the chasm prompt refuses an answer until it has been updated for more than 0.2 s of
 * frame time ({@code .../levels/features/Chasm.java:77-92}), so a window is a wait from its
 * second frame in front; the inventory pane can be selecting an item with no window while the
 * map refuses clicks ({@code .../scenes/GameScene.java:1386-1395}), which is not a wait; and a
 * window that is not a Prompt, which only the player can dismiss, is not one either. A window
 * changing in front of a waiting hero is a new wait too, with no notification: the answer to a prompt can close it without the
 * hero acting, and the brain must see what is there now. And so is an Action handed to the game,
 * which the driver sees as the hero holding it when stepping resumes: an act can begin ready and
 * end ready in one go, a refused move or a transition refused with a message
 * ({@code .../levels/SewerLevel.java:146-155}), announcing nothing, and AD-5 gives every executed
 * Action its own wait, whatever the game made of it. One more thing must hold, which none of
 * these show: nothing may be waiting in the render thread's queue. The hero's own act can post
 * the window that makes the wait a Prompt, as the chasm jump does
 * ({@code .../levels/features/Chasm.java:57-96}), and the game shows that window at the start of
 * the next frame, before anything could observe or click; a notification with a runnable pending
 * is kept for the frame after, which is the order the render thread gives it. Each confirmed wait
 * gets the next index {@code k}, which this driver owns (AD-14): a count of the waits confirmed,
 * from 1, incremented before the per-wait sequence as ADR-0013 has it.
 *
 * <p><b>Stopping.</b> A dead hero stops the loop, because the scene stops waking the actor thread
 * then ({@code .../scenes/GameScene.java:865}); the game-over banner it posted is run when the
 * scene is destroyed, not in the next Run. Dead means what the game means by it: a hero at zero
 * health who holds an unblessed ankh is offered resurrection instead, through a window his own
 * death posts ({@code .../actors/hero/Hero.java:2141-2190}), and the game is still on while that
 * window exists ({@code .../Dungeon.java:707}); that window is an Input wait here, the one Prompt
 * the hero answers without being ready, and taking it is a scene change
 * ({@code .../windows/WndResurrect.java:125-141}). A requested scene change stops it, because the
 * actor loop picks nobody while one is pending ({@code .../actors/Actor.java:252}) and this driver
 * does not serve it: the stairs, a fall and the end of a game are scene changes, and serving them
 * is the scene-lifetime work of the Run stories. A Run that reaches none of these within its
 * frame budget fails with {@link Stalled}, whose message names the actor the game is waiting on,
 * rather than looping forever; the stepper's own timeout covers a thread that never parks.
 */
public final class HeadlessDriver implements AutoCloseable {

    /**
     * Frames a single {@link #stepToInputWait()} may take before the Run is declared stalled. A
     * turn is a frame or two and a full rest a few hundred; ten thousand is two thousand seconds
     * of frame time and a few seconds of wall clock.
     */
    public static final int DEFAULT_FRAME_BUDGET = 10_000;

    /** What the backend reported about itself after booting. */
    public record Boot(String applicationType, String upstreamVersion) {
    }

    /** Why {@link #stepToInputWait()} returned. */
    public enum Reason {
        /** The hero waits for input, under {@link Halt#window()} when that is not null; a resurrection prompt counts. */
        INPUT_WAIT,
        /** The hero is dead with no resurrection offered; the scene will not wake the actor thread again. */
        HERO_DEAD,
        /** Game code asked for {@link Halt#requestedScene()}; the actor loop picks nobody until it is served. */
        SCENE_SWITCH
    }

    /**
     * Where a call to {@link #stepToInputWait()} stopped: why, how many frames it stepped, the
     * window in front if any, the scene the game asked for if it asked, and the index {@code k} of
     * the wait confirmed, or of the last one confirmed when the reason is another.
     */
    public record Halt(Reason reason, long framesStepped, Window window, Class<? extends Scene> requestedScene,
                       long waitIndex) {
    }

    /**
     * A Run that reached no wait within its frame budget; the message says what it was waiting on.
     * The message is for a person: it names cells and health the player may not be able to see,
     * and must never reach an Observation, a run log the brain reads, or training labels except
     * under the oracle flag.
     */
    public static final class Stalled extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        Stalled(String message) {
            super(message);
        }
    }

    private final HeadlessBoot boot;
    private final HeadlessScene scene;
    private long frames;
    private boolean closed;
    /** Written by the actor thread only, inside {@code Hero.act()}; read here between frames. */
    private volatile long notifications;
    private long seenNotifications;
    private long dropped;
    private boolean acted;
    private long waitIndex;
    private Window lastConfirmedWindow;
    private Window lastSeenWindow;
    private int windowFramesShown;

    private HeadlessDriver(HeadlessBoot boot, HeadlessScene scene) {
        this.boot = boot;
        this.scene = scene;
        Hooks.inputWait = this::noticed;
    }

    /**
     * Hook row 5's listener: on the actor thread, inside {@code Hero.act()}, one volatile write
     * and nothing else (ADR-0013). The hero acts on that thread only, so the count is exact.
     */
    private void noticed() {
        notifications++;
    }

    /** Boots the process, or returns the boot that already happened. */
    public static Boot boot() {
        HeadlessBoot boot = HeadlessBoot.ensure();
        return new Boot(Gdx.app.getType().name(), boot.upstreamVersionName());
    }

    /**
     * Starts a Run of {@code heroClass} with {@code seed} and creates its scene; the hero has not
     * acted yet. {@code seed} is what a player could type: {@code [0, DungeonSeed.TOTAL_SEEDS)}.
     */
    public static HeadlessDriver start(long seed, HeroClass heroClass) {
        HeadlessBoot boot = HeadlessBoot.ensure();
        // A scene left by a Run that was never closed, whose actor thread ended on its own, goes
        // now and in its own profile: destroying a scene writes the badges and the journal
        // (GameScene.java:780-781), and the next profile is the next Run's. With a live thread
        // destroy() refuses, which is the refusal newGame would give.
        if (boot.game().currentScene() != null) {
            boot.game().destroy();
        }
        newGame(seed, heroClass);
        // The Observer's log listener (ADR-0006, Log) is re-added by hook row 3 as the scene is
        // created, so the seam is armed before the scene exists and hears the first floor's lines.
        GameLogListener.install();
        HeadlessScene scene = new HeadlessScene();
        boot.game().switchTo(scene);
        return new HeadlessDriver(boot, scene);
    }

    /**
     * Starts a new game in a fresh profile directory, up to and including its first floor, and
     * creates no scene: the path a player takes from typing a seed into the custom-seed window to
     * pressing start ({@code core/.../scenes/HeroSelectScene.java:157-162}), and then what the
     * loading scene does for a game with no hero yet
     * ({@code .../scenes/InterlevelScene.java:622-649}). Nothing is hand-placed and nothing is
     * skipped. {@link #start} is this plus the scene; a test that brings its own scene calls this.
     */
    public static void newGame(long seed, HeroClass heroClass) {
        if (heroClass == null) {
            throw new IllegalArgumentException("a Run needs a hero class");
        }
        String seedCode = DungeonSeed.convertToCode(seed);
        HeadlessBoot boot = HeadlessBoot.ensure();
        if (SceneStepper.theSceneHasALiveActorThread()) {
            throw new IllegalStateException("a Run is in progress in this process; close it before starting another");
        }
        if (boot.pendingRunnables() != 0) {
            throw new IllegalStateException("the last Run left " + boot.pendingRunnables() + " runnable(s) queued for"
                    + " the render thread, which would run in this Run's scene; HeadlessGame drains the queue when"
                    + " a scene is destroyed, so the last scene was not destroyed");
        }
        if (WndResurrect.instance != null) {
            throw new IllegalStateException("the last Run's resurrection prompt was never destroyed, and the game"
                    + " would take this Run for one still resurrecting (Dungeon.java:707)");
        }
        if (Hooks.inputWait != null) {
            throw new IllegalStateException("a listener from a Run that was not closed is still registered in Hooks;"
                    + " close() clears it");
        }
        // The game clears this only when the hero falls (Chasm.java:101); a Run closed between a
        // confirmed jump and the fall would otherwise jump unasked in this one.
        Chasm.jumpConfirmed = false;
        try {
            boot.profile(Files.createTempDirectory("shatterfish-run"));
        } catch (IOException e) {
            throw new UncheckedIOException("could not create a profile directory for the Run", e);
        }

        // What the hero-select screen loads before any game starts (HeroSelectScene.java:106-107).
        // Both load once per process; story 1.15 owns what a Profile is and when it is reloaded.
        Badges.loadGlobal();
        Journal.loadGlobal();

        // HeroSelectScene.java:157-162, the start button, with the seed typed into the seed window
        // and the class and the slot chosen on the screens before it.
        SPDSettings.customSeed(seedCode);
        GamesInProgress.selectedClass = heroClass;
        GamesInProgress.curSlot = GamesInProgress.firstEmpty();
        if (GamesInProgress.curSlot < 1) {
            throw new IllegalStateException("no free save slot: every slot this process has seen is occupied");
        }
        Dungeon.hero = null;
        Dungeon.daily = Dungeon.dailyReplay = false;
        Dungeon.initSeed();
        ActionIndicator.clearAction();
        InterlevelScene.mode = InterlevelScene.Mode.DESCEND;

        // InterlevelScene.java:622-649, descend() with no hero: a new game and its first floor.
        Mob.clearHeldAllies();
        Dungeon.init();
        GameLog.wipe();
        GameLogListener.INSTANCE.reset();
        Level level = Dungeon.newLevel();
        Dungeon.switchLevel(level, -1);
    }

    /** Steps until the hero waits for input, the hero is dead or a scene change is requested. */
    public Halt stepToInputWait() {
        return stepToInputWait(DEFAULT_FRAME_BUDGET);
    }

    /**
     * Steps at most {@code frameBudget} frames until a new Input wait is confirmed, the hero is dead
     * or a scene change is requested, and says which; always steps at least one frame. A new wait
     * is one the game announced (hook row 5) or a change of the window in front, confirmed as the
     * class comment says; a notification that is not a wait is dropped and counted.
     *
     * @throws Stalled if none of the three happened within the budget
     */
    public Halt stepToInputWait(int frameBudget) {
        requireOpen();
        if (frameBudget < 1) {
            throw new IllegalArgumentException("the frame budget must be at least one frame: " + frameBudget);
        }
        HeadlessGame game = boot.game();
        if (game.sceneSwitchRequested()) {
            throw new IllegalStateException("the game has asked for a change to "
                    + game.requestedSceneClass().getSimpleName() + ", which this driver does not serve; the actor"
                    + " loop picks nobody until it is served, so stepping on would only spend the budget");
        }
        Hero handed = Dungeon.hero;
        if (handed != null && (handed.curAction != null || handed.resting)) {
            // An Action handed to the game by the caller: the hero holds it until its next act,
            // which may begin and end ready in one go and announce nothing.
            acted = true;
        }
        long before = frames;
        while (true) {
            step();
            long stepped = frames - before;
            Hero hero = Dungeon.hero;
            // The change first: a taken resurrection clears the pending mark and asks for the
            // loading scene in one click, with the hero still at zero health.
            if (game.sceneSwitchRequested()) {
                return new Halt(Reason.SCENE_SWITCH, stepped, scene.openWindow(), game.requestedSceneClass(), waitIndex);
            }
            if (hero == null || (!hero.isAlive() && WndResurrect.instance == null)) {
                return new Halt(Reason.HERO_DEAD, stepped, scene.openWindow(), null, waitIndex);
            }
            if (boot.pendingRunnables() == 0) {
                Window window = scene.openWindow();
                if (window != lastSeenWindow) {
                    lastSeenWindow = window;
                    windowFramesShown = 1;
                } else {
                    windowFramesShown++;
                }
                boolean notified = notifications != seenNotifications;
                seenNotifications = notifications;
                boolean heroWaits = heroWaits(hero);
                boolean resurrecting = WndResurrect.instance != null;
                if ((notified || acted || window != lastConfirmedWindow)
                        && isInputWait(hero, window, windowFramesShown)) {
                    waitIndex++;
                    lastConfirmedWindow = window;
                    acted = false;
                    return new Halt(Reason.INPUT_WAIT, stepped, window, null, waitIndex);
                }
                if (notified && !heroWaits && !resurrecting) {
                    dropped++;
                }
            }
            if (stepped >= frameBudget) {
                throw new Stalled(diagnose(frameBudget));
            }
        }
    }

    /**
     * AD-5's condition, with three things the game does. A window is answered only once it has been
     * updated for more than 0.2 s of frame time, the chasm prompt's guard against a click meant for
     * the map ({@code .../levels/features/Chasm.java:77-92}), so a window is a wait from its second
     * frame in front. The inventory pane can be selecting an item with no window at all, and the map
     * refuses clicks meanwhile ({@code .../scenes/GameScene.java:1386-1395}). And a resurrection is
     * offered to a hero who is not ready, through the resurrection window or the warning it stacks
     * over itself when a kept-item slot is empty ({@code .../windows/WndResurrect.java:98-114}).
     */
    /**
     * AD-5's condition on the hero at an Input wait: ready, no action in hand, not resting. The
     * Observer asserts the same condition on entry, so the two share this one definition.
     */
    public static boolean heroWaits(Hero hero) {
        return hero.ready && hero.curAction == null && !hero.resting;
    }

    /**
     * AD-5's condition on the game's state, the one definition the driver and the Observer share:
     * with no window in front, the hero waits and the inventory pane is not selecting
     * ({@code .../scenes/GameScene.java:1386-1396}); with a window in front, it is a Prompt and the
     * hero waits under it or is being offered a resurrection. The driver confirms a wait only when
     * this holds and two timing conditions of its own do, a window's second frame in front and an
     * empty render queue, which a reader of the state cannot see.
     */
    public static boolean waitState(Hero hero, Window window) {
        boolean heroWaits = heroWaits(hero);
        if (window == null) {
            return heroWaits && !GameScene.interfaceBlockingHero();
        }
        return (heroWaits || WndResurrect.instance != null) && Prompts.isRecognised(window);
    }

    private static boolean isInputWait(Hero hero, Window window, int windowFramesShown) {
        return (window == null || windowFramesShown >= 2) && waitState(hero, window);
    }

    /**
     * Runs the per-wait sequence of ADR-0013 at each of the next {@code maxWaits} Input waits, in
     * order: the index, then {@code reseed}, {@code observe}, {@code decide}, {@code execute},
     * {@code record}. Returns the last halt: the last wait served, or the death or scene change
     * that ended the loop early.
     */
    public <O, D> Halt run(WaitSequence<O, D> sequence, int maxWaits) {
        if (maxWaits < 1) {
            throw new IllegalArgumentException("run needs at least one wait to serve: " + maxWaits);
        }
        Halt halt = null;
        for (int served = 0; served < maxWaits; served++) {
            halt = stepToInputWait();
            if (halt.reason() != Reason.INPUT_WAIT) {
                return halt;
            }
            long k = halt.waitIndex();
            sequence.reseed(k);
            O observation = sequence.observe(k);
            D decision = sequence.decide(k, observation);
            sequence.execute(k, decision);
            acted = true;
            sequence.record(k, observation, decision);
        }
        return halt;
    }

    /** One fenced frame; see {@link SceneStepper#step()}. */
    public void step() {
        requireOpen();
        scene.stepper().step();
        frames++;
    }

    /** The index {@code k} of the last Input wait confirmed; 0 before the first. */
    public long waitIndex() {
        return waitIndex;
    }

    /** Times hook row 5 has notified this Run: acts of the hero that began unready. */
    public long hookNotifications() {
        return notifications;
    }

    /** Notifications that found the hero mid-action: the steps of a move but the last, the turns of a rest. */
    public long droppedNotifications() {
        return dropped;
    }

    /** Frames this driver has stepped, counted here and not by the scene or the stepper. */
    public long frames() {
        return frames;
    }

    public HeadlessScene scene() {
        return scene;
    }

    public SceneStepper stepper() {
        return scene.stepper();
    }

    public HeadlessBoot headlessBoot() {
        return boot;
    }

    public boolean closed() {
        return closed;
    }

    /** Ends the actor thread and destroys the scene; the process stays booted for the next Run. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            scene.stepper().endActorThread();
        } finally {
            try {
                boot.game().destroy();
            } finally {
                Hooks.clear();
                GameLogListener.uninstall();
            }
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("this Run has been closed");
        }
    }

    /**
     * What a stalled Run was waiting on. The game records the actor whose turn it is in
     * {@code Actor.current}, which the stepper reads: non-null between frames means an
     * {@code act()} returned false and nothing has called {@code next()} since, which is a turn
     * waiting for an animation callback ({@code Actor.java:293-321}); null means turns were
     * ending and being picked normally and the hero simply never became ready.
     */
    private String diagnose(int frameBudget) {
        SceneStepper stepper = scene.stepper();
        Actor current = stepper.currentActor();
        StringBuilder out = new StringBuilder("no Input wait within ").append(frameBudget)
                .append(" frames of ").append(SceneStepper.FRAME).append(" s. ");
        if (current == null) {
            out.append("No actor is mid-turn: the last actor processed ended its turn with next(), the loop kept"
                    + " picking actors, and the hero never became ready. Next due: ").append(nextDue()).append('.');
        } else if (stepper.parkedOnItsOwnMonitor()) {
            out.append("The last actor processed was ").append(SceneStepper.name(current))
                    .append(": its act() returned false and nothing has called next() since (Actor.java:293-321"
                            + " at the pinned tag), so the actor thread is parked between turns waiting for a"
                            + " callback that has not come.");
        } else {
            out.append("The last actor processed was ").append(SceneStepper.name(current))
                    .append(": the actor thread is waiting for a sprite to stop moving before its turn"
                            + " (Actor.java:274-282), and the frames stepped have not ended the movement.");
        }
        return out.append(' ').append(waitState()).append(' ').append(heroLine())
                .append(" Actor thread: ").append(stepper.describeActorThread()).toString();
    }

    private String waitState() {
        Window window = scene.openWindow();
        Hero hero = Dungeon.hero;
        StringBuilder out = new StringBuilder("Since the Run began: ").append(notifications)
                .append(" notification(s) from the observe site, ").append(dropped).append(" dropped; waits confirmed: ")
                .append(waitIndex).append("; in front: ").append(Prompts.describe(window));
        if (window == null && GameScene.interfaceBlockingHero()) {
            out.append(", and the inventory pane is selecting an item, so the map refuses clicks"
                    + " (GameScene.java:1386-1395)");
        }
        out.append('.');
        if (acted) {
            out.append(" An Action is waiting for its wait: the last one handed to the game has had no wait"
                    + " confirmed since.");
        }
        if (hero != null && hero.ready && hero.curAction == null && !hero.resting && !acted
                && notifications == seenNotifications && window == lastConfirmedWindow) {
            out.append(" The hero has been ready and nothing has happened since wait ").append(waitIndex)
                    .append(": no act of the hero began unready and no window changed, so there is no new Input"
                            + " wait; an Action must change something.");
        }
        return out.toString();
    }

    /** The actor the loop would pick next: least cooldown, ties by id, which is the order they were added in. */
    private static String nextDue() {
        Actor next = null;
        for (Actor actor : Actor.all()) {
            if (next == null || actor.cooldown() < next.cooldown()
                    || (actor.cooldown() == next.cooldown() && actor.id() < next.id())) {
                next = actor;
            }
        }
        return next == null ? "no actor at all" : SceneStepper.name(next) + " in " + next.cooldown() + " turn(s)";
    }

    private static String heroLine() {
        Hero hero = Dungeon.hero;
        if (hero == null) {
            return "No hero.";
        }
        StringBuilder out = new StringBuilder("Hero: ").append(SceneStepper.name(hero))
                .append(", ready=").append(hero.ready)
                .append(", curAction=").append(hero.curAction == null ? "none"
                        : hero.curAction.getClass().getSimpleName() + " to " + hero.curAction.dst)
                .append(", resting=").append(hero.resting)
                .append(", paralysed=").append(hero.paralysed)
                .append(", buffs=[");
        String separator = "";
        for (Buff buff : hero.buffs()) {
            out.append(separator).append(buff.getClass().getSimpleName());
            separator = ", ";
        }
        return out.append("].").toString();
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 31_415_926L;
        Boot boot = boot();
        System.out.println("HeadlessDriver: booted libGDX " + boot.applicationType()
                + " backend for Shattered Pixel Dungeon " + boot.upstreamVersion());
        try (HeadlessDriver driver = start(seed, HeroClass.WARRIOR)) {
            Halt halt = driver.stepToInputWait();
            System.out.println("HeadlessDriver: seed " + seed + " (" + DungeonSeed.convertToCode(seed) + "), "
                    + HeroClass.WARRIOR.name() + ": " + halt.reason() + " after " + halt.framesStepped()
                    + " frame(s); hero at cell " + Dungeon.hero.pos + " on depth " + Dungeon.depth);
        }
        Gdx.app.exit();
    }
}
